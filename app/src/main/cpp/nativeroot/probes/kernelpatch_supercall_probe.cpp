/*
 * Copyright 2026 Duck Apps Contributor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "nativeroot/probes/kernelpatch_supercall_probe.h"

#include <cerrno>
#include <csignal>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

namespace duckdetector::nativeroot {

#if defined(__aarch64__)

    namespace {

        // KernelPatch reuses __NR3264_truncate as __NR_supercall on arm64.
        constexpr int kSupercallNr = 45;

        // The high bit (bit 63) keeps truncate()'s `length` negative on a stock
        // kernel so it returns -EINVAL without ever reading the pathname, while the
        // before-hook only inspects the low 16 bits (`cmd = ver_xx_cmd & 0xFFFF`).
        //  * kCmdInRange    -> op 0x1008 (SUPERCALL_KERNELPATCH_VER), within
        //                      [SUPERCALL_HELLO=0x1000, SUPERCALL_MAX=0x1200].
        //  * kCmdOutOfRange -> op 0x0000, rejected by the range check.
        constexpr unsigned long long kCmdInRange = 0x8000000000001008ULL;
        constexpr unsigned long long kCmdOutOfRange = 0x8000000000000000ULL;

        constexpr size_t kKeyLen = 128;     // MAX_KEY_LEN in KernelPatch.
        constexpr size_t kPageSize = 4096;
        constexpr int kIterations = 20000;
        constexpr double kLatencyRatioThreshold = 2.0;

        constexpr int kSeccompBlockedExitCode = 125;

        void handle_seccomp_sigsys(int, siginfo_t *, void *) {
            _exit(kSeccompBlockedExitCode);
        }

        bool install_sigsys_handler() {
            struct sigaction action{};
            action.sa_sigaction = handle_seccomp_sigsys;
            action.sa_flags = SA_SIGINFO;
            sigemptyset(&action.sa_mask);
            return sigaction(SIGSYS, &action, nullptr) == 0;
        }

        static inline uint64_t read_cntfrq() {
            uint64_t value;
            asm volatile("mrs %0, cntfrq_el0" : "=r"(value));
            return value;
        }

        static inline uint64_t read_cntvct() {
            uint64_t value;
            asm volatile("isb; mrs %0, cntvct_el0; isb" : "=r"(value));
            return value;
        }

        struct LazyPageResult {
            bool ran = false;
            bool baseline_resident = false;
            bool after_resident = false;
            bool mincore_ok = false;
        };

        // Method 1: lazy-allocation page probe.
        bool run_lazy_page_child(LazyPageResult &out, bool &blocked) {
            int pipe_fds[2] = {-1, -1};
            if (pipe(pipe_fds) != 0) return false;
            fcntl(pipe_fds[0], F_SETFD, FD_CLOEXEC);
            fcntl(pipe_fds[1], F_SETFD, FD_CLOEXEC);

            const pid_t pid = fork();
            if (pid < 0) {
                close(pipe_fds[0]);
                close(pipe_fds[1]);
                return false;
            }

            if (pid == 0) {
                close(pipe_fds[0]);
                if (!install_sigsys_handler()) {
                    _exit(126);
                }

                LazyPageResult child_result{};

                void *page = mmap(nullptr, kPageSize, PROT_READ,
                                  MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
                if (page == MAP_FAILED) {
                    const ssize_t ignored = write(pipe_fds[1], &child_result,
                                                  sizeof(child_result));
                    (void) ignored;
                    close(pipe_fds[1]);
                    _exit(0);
                }

                unsigned char vec = 0;
                if (mincore(page, kPageSize, &vec) == 0) {
                    child_result.mincore_ok = true;
                    child_result.baseline_resident = (vec & 1) != 0;
                }

                // In-range cmd: a stock kernel returns -EINVAL (negative length)
                // without touching the page; KernelPatch reads the "superkey".
                syscall(kSupercallNr, page, static_cast<long>(kCmdInRange));

                vec = 0;
                if (child_result.mincore_ok && mincore(page, kPageSize, &vec) == 0) {
                    child_result.after_resident = (vec & 1) != 0;
                }

                munmap(page, kPageSize);

                child_result.ran = true;
                const ssize_t ignored = write(pipe_fds[1], &child_result,
                                              sizeof(child_result));
                (void) ignored;
                close(pipe_fds[1]);
                _exit(0);
            }

            close(pipe_fds[1]);

            int status = 0;
            pid_t waited_pid;
            do {
                waited_pid = waitpid(pid, &status, 0);
            } while (waited_pid < 0 && errno == EINTR);
            if (waited_pid < 0) {
                close(pipe_fds[0]);
                return false;
            }

            const ssize_t bytes_read = read(pipe_fds[0], &out, sizeof(out));
            close(pipe_fds[0]);

            if (WIFEXITED(status) && WEXITSTATUS(status) == kSeccompBlockedExitCode) {
                blocked = true;
                return false;
            }
            if (WIFSIGNALED(status) && WTERMSIG(status) == SIGSYS) {
                blocked = true;
                return false;
            }

            return WIFEXITED(status) && WEXITSTATUS(status) == 0 &&
                   bytes_read == static_cast<ssize_t>(sizeof(out));
        }

        struct LatencyResult {
            bool ran = false;
            double in_range_us = 0.0;
            double out_of_range_us = 0.0;
            double ratio = 0.0;
        };

        double measure_latency(const char *key, const long cmd) {
            const uint64_t freq = read_cntfrq();
            if (freq == 0) return 0.0;

            // Warm up the hook and caches before measuring.
            syscall(kSupercallNr, key, cmd);

            uint64_t total_ticks = 0;
            for (int i = 0; i < kIterations; i++) {
                const uint64_t start = read_cntvct();
                syscall(kSupercallNr, key, cmd);
                const uint64_t end = read_cntvct();
                total_ticks += (end - start);
            }

            return (static_cast<double>(total_ticks) * 1000000.0) /
                   (static_cast<double>(freq) * kIterations);
        }

        // Method 2: auth-latency probe (in-range vs out-of-range cmd).
        bool run_latency_child(LatencyResult &out, bool &blocked) {
            int pipe_fds[2] = {-1, -1};
            if (pipe(pipe_fds) != 0) return false;
            fcntl(pipe_fds[0], F_SETFD, FD_CLOEXEC);
            fcntl(pipe_fds[1], F_SETFD, FD_CLOEXEC);

            const pid_t pid = fork();
            if (pid < 0) {
                close(pipe_fds[0]);
                close(pipe_fds[1]);
                return false;
            }

            if (pid == 0) {
                close(pipe_fds[0]);
                if (!install_sigsys_handler()) {
                    _exit(126);
                }

                LatencyResult child_result{};

                // A non-NUL key forces KernelPatch to copy all MAX_KEY_LEN bytes on
                // the in-range path, maximizing the measurable timing difference.
                char key[kKeyLen];
                memset(key, 'A', sizeof(key) - 1);
                key[sizeof(key) - 1] = '\0';

                child_result.in_range_us = measure_latency(key, static_cast<long>(kCmdInRange));
                child_result.out_of_range_us = measure_latency(key, static_cast<long>(kCmdOutOfRange));
                child_result.ratio = child_result.out_of_range_us > 0.0
                                     ? child_result.in_range_us / child_result.out_of_range_us
                                     : 0.0;
                child_result.ran = true;

                const ssize_t ignored = write(pipe_fds[1], &child_result,
                                              sizeof(child_result));
                (void) ignored;
                close(pipe_fds[1]);
                _exit(0);
            }

            close(pipe_fds[1]);

            int status = 0;
            pid_t waited_pid;
            do {
                waited_pid = waitpid(pid, &status, 0);
            } while (waited_pid < 0 && errno == EINTR);
            if (waited_pid < 0) {
                close(pipe_fds[0]);
                return false;
            }

            const ssize_t bytes_read = read(pipe_fds[0], &out, sizeof(out));
            close(pipe_fds[0]);

            if (WIFEXITED(status) && WEXITSTATUS(status) == kSeccompBlockedExitCode) {
                blocked = true;
                return false;
            }
            if (WIFSIGNALED(status) && WTERMSIG(status) == SIGSYS) {
                blocked = true;
                return false;
            }

            return WIFEXITED(status) && WEXITSTATUS(status) == 0 &&
                   bytes_read == static_cast<ssize_t>(sizeof(out));
        }

    }  // namespace

    ProbeResult run_kernelpatch_supercall_probe() {
        ProbeResult result;

        // Method 1: lazy-allocation page probe.
        {
            LazyPageResult lazy{};
            bool blocked = false;
            if (run_lazy_page_child(lazy, blocked) && lazy.ran) {
                result.checked_count += 1;
                const bool hit = lazy.mincore_ok &&
                                 !lazy.baseline_resident &&
                                 lazy.after_resident;
                if (hit) {
                    result.hit_count += 1;
                    result.flags.apatch = true;
                    result.extra_numeric_value |= (1L << 0);
                    result.findings.push_back(
                            Finding{
                                    .group = "SYSCALL",
                                    .label = "KernelPatch lazy-page probe",
                                    .value = "Detected",
                                    .detail = "An untouched anonymous page became "
                                              "resident after being passed as the "
                                              "superkey address to __NR_supercall (45).",
                                    .severity = Severity::kDanger,
                            }
                    );
                }
            } else if (blocked) {
                result.denied_count += 1;
                result.findings.push_back(
                        Finding{
                                .group = "SYSCALL",
                                .label = "KernelPatch lazy-page probe",
                                .value = "Blocked",
                                .detail = "mincore()/truncate was blocked by Seccomp "
                                          "in the helper process.",
                                .severity = Severity::kWarning,
                        }
                );
            }
        }

        // Method 2: auth-latency probe.
        {
            LatencyResult latency{};
            bool blocked = false;
            if (run_latency_child(latency, blocked) && latency.ran) {
                result.checked_count += 1;
                const long ratio_milli = static_cast<long>(latency.ratio * 1000.0);
                result.numeric_value = ratio_milli;

                char detail[256];
                snprintf(detail, sizeof(detail),
                         "In-range: %.2f us, Out-of-range: %.2f us, Ratio: %.2f",
                         latency.in_range_us, latency.out_of_range_us, latency.ratio);

                const bool hit = latency.ratio > kLatencyRatioThreshold;
                if (hit) {
                    result.hit_count += 1;
                    result.flags.apatch = true;
                    result.extra_numeric_value |= (1L << 1);
                    result.findings.push_back(
                            Finding{
                                    .group = "SYSCALL",
                                    .label = "KernelPatch auth-latency probe",
                                    .value = "Detected",
                                    .detail = detail,
                                    .severity = Severity::kDanger,
                            }
                    );
                } else {
                    result.findings.push_back(
                            Finding{
                                    .group = "SYSCALL",
                                    .label = "KernelPatch auth-latency probe",
                                    .value = "Clean",
                                    .detail = detail,
                                    .severity = Severity::kInfo,
                            }
                    );
                }
            } else if (blocked) {
                result.denied_count += 1;
                result.findings.push_back(
                        Finding{
                                .group = "SYSCALL",
                                .label = "KernelPatch auth-latency probe",
                                .value = "Blocked",
                                .detail = "__NR_supercall (45) was blocked by Seccomp "
                                          "in the helper process.",
                                .severity = Severity::kWarning,
                        }
                );
            }
        }

        return result;
    }

#else

    ProbeResult run_kernelpatch_supercall_probe() {
        ProbeResult result;
        result.extra_text = "";
        return result;
    }

#endif  // aarch64

}  // namespace duckdetector::nativeroot
