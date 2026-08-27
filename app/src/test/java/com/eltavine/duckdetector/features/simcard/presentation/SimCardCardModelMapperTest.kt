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

package com.eltavine.duckdetector.features.simcard.presentation

import com.eltavine.duckdetector.features.simcard.domain.CellInfoSnapshot
import com.eltavine.duckdetector.features.simcard.domain.ServiceStateKind
import com.eltavine.duckdetector.features.simcard.domain.SimCardRat
import com.eltavine.duckdetector.features.simcard.domain.SimCardReport
import com.eltavine.duckdetector.features.simcard.domain.SimCardStage
import com.eltavine.duckdetector.features.simcard.domain.SimCardState
import com.eltavine.duckdetector.features.simcard.domain.SimFactLabel
import com.eltavine.duckdetector.features.simcard.domain.SubscriptionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class SimCardCardModelMapperTest {

    private val mapper = SimCardCardModelMapper()

    @Test
    fun `contradictory subscription surfaces in verdict and header facts`() {
        val report = SimCardReport(
            stage = SimCardStage.READY,
            subscriptions = listOf(
                SubscriptionSnapshot(
                    slotId = 0,
                    subId = 1,
                    simState = SimCardState.NOT_READY,
                    serviceState = ServiceStateKind.IN_SERVICE,
                    cells = listOf(
                        CellInfoSnapshot(
                            rat = SimCardRat.LTE,
                            registered = true,
                            ageMillis = 100L,
                        ),
                    ),
                    facts = setOf(
                        SimFactLabel.SIM_NOT_READY,
                        SimFactLabel.HAS_REGISTERED_CELL,
                        SimFactLabel.REGISTERED_CELL_FRESH,
                        SimFactLabel.SERVICE_IN_SERVICE,
                    ),
                ),
            ),
        )

        val model = mapper.map(report)

        assertEquals("Cross-layer contradiction on 1 subscription(s)", model.verdict)
        assertEquals("1", model.headerFacts.first { it.label == "Contradictions" }.value)
        assertEquals("1", model.headerFacts.first { it.label == "Registered" }.value)
        assertEquals("1", model.headerFacts.first { it.label == "Subscriptions" }.value)
        assertEquals("1", model.headerFacts.first { it.label == "Cells" }.value)
    }

    @Test
    fun `clean snapshot reports no contradiction`() {
        val report = SimCardReport(
            stage = SimCardStage.READY,
            subscriptions = listOf(
                SubscriptionSnapshot(
                    slotId = 0,
                    subId = 1,
                    simState = SimCardState.READY,
                    serviceState = ServiceStateKind.IN_SERVICE,
                    cells = listOf(
                        CellInfoSnapshot(
                            rat = SimCardRat.NR,
                            registered = true,
                            ageMillis = 100L,
                        ),
                    ),
                    facts = setOf(
                        SimFactLabel.HAS_REGISTERED_CELL,
                        SimFactLabel.REGISTERED_CELL_FRESH,
                        SimFactLabel.SERVICE_IN_SERVICE,
                    ),
                ),
            ),
        )

        val model = mapper.map(report)

        assertEquals("Subscription-scoped snapshot collected", model.verdict)
        assertEquals("0", model.headerFacts.first { it.label == "Contradictions" }.value)
    }
}
