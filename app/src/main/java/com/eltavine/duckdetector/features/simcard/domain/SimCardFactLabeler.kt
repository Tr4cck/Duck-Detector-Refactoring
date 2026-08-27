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

package com.eltavine.duckdetector.features.simcard.domain

/**
 * Derives phase-1 fact labels for a single subscription snapshot.
 *
 * Per SPEC.md this stage only produces facts — it does not emit a risk verdict. The facts are
 * later combined (see [isHighValueContradiction]) to highlight the cross-layer contradiction
 * SIM_STATE_NOT_READY + registered + fresh + in-service.
 */
class SimCardFactLabeler(
    private val freshThresholdMillis: Long = DEFAULT_FRESH_THRESHOLD_MILLIS,
) {

    fun label(snapshot: SubscriptionSnapshot): Set<SimFactLabel> = buildSet {
        if (snapshot.simState != SimCardState.READY) {
            add(SimFactLabel.SIM_NOT_READY)
        }
        if (snapshot.cells.any { it.registered }) {
            add(SimFactLabel.HAS_REGISTERED_CELL)
        }
        if (snapshot.cells.any { cell ->
                cell.registered && cell.ageMillis?.let { it < freshThresholdMillis } == true
            }
        ) {
            add(SimFactLabel.REGISTERED_CELL_FRESH)
        }
        if (snapshot.serviceState == ServiceStateKind.IN_SERVICE) {
            add(SimFactLabel.SERVICE_IN_SERVICE)
        }
    }

    companion object {
        const val DEFAULT_FRESH_THRESHOLD_MILLIS = 2_000L

        fun isHighValueContradiction(facts: Set<SimFactLabel>): Boolean {
            return SimFactLabel.SIM_NOT_READY in facts &&
                SimFactLabel.HAS_REGISTERED_CELL in facts &&
                SimFactLabel.REGISTERED_CELL_FRESH in facts &&
                SimFactLabel.SERVICE_IN_SERVICE in facts
        }
    }
}
