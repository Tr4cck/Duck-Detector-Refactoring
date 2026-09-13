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

#ifndef DUCKDETECTOR_NATIVEROOT_PROBES_KERNELPATCH_SUPERCALL_PROBE_H
#define DUCKDETECTOR_NATIVEROOT_PROBES_KERNELPATCH_SUPERCALL_PROBE_H

#include "nativeroot/common/types.h"

namespace duckdetector::nativeroot {

    // Detects KernelPatch (APatch) by probing the __NR_supercall (45) before-hook
    // with two complementary techniques:
    //   1. Lazy-allocation page probe: pass a freshly mmap'ed anonymous page as the
    //      superkey address; KernelPatch dereferences it while copying the key, which
    //      faults the page into memory; mincore() then reports it resident.
    //   2. Auth-latency probe: time in-range vs out-of-range cmd values. In-range cmd
    //      reaches the superkey read/verify path, out-of-range returns early.
    ProbeResult run_kernelpatch_supercall_probe();

}  // namespace duckdetector::nativeroot

#endif  // DUCKDETECTOR_NATIVEROOT_PROBES_KERNELPATCH_SUPERCALL_PROBE_H
