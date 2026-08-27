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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimCardFactLabelerTest {

    private val labeler = SimCardFactLabeler(freshThresholdMillis = 2_000L)

    @Test
    fun `ready sim with registered fresh cell and in service has no sim not ready`() {
        val snapshot = snapshot(
            simState = SimCardState.READY,
            serviceState = ServiceStateKind.IN_SERVICE,
            cells = listOf(
                cell(registered = true, ageMillis = 100L),
            ),
        )

        val facts = labeler.label(snapshot)

        assertEquals(
            setOf(
                SimFactLabel.HAS_REGISTERED_CELL,
                SimFactLabel.REGISTERED_CELL_FRESH,
                SimFactLabel.SERVICE_IN_SERVICE,
            ),
            facts,
        )
        assertFalse(SimCardFactLabeler.isHighValueContradiction(facts))
    }

    @Test
    fun `not ready sim with registered fresh cell and in service is high value contradiction`() {
        val snapshot = snapshot(
            simState = SimCardState.NOT_READY,
            serviceState = ServiceStateKind.IN_SERVICE,
            cells = listOf(
                cell(registered = true, ageMillis = 100L),
            ),
        )

        val facts = labeler.label(snapshot)

        assertEquals(
            setOf(
                SimFactLabel.SIM_NOT_READY,
                SimFactLabel.HAS_REGISTERED_CELL,
                SimFactLabel.REGISTERED_CELL_FRESH,
                SimFactLabel.SERVICE_IN_SERVICE,
            ),
            facts,
        )
        assertTrue(SimCardFactLabeler.isHighValueContradiction(facts))
    }

    @Test
    fun `stale registered cell does not mark fresh`() {
        val snapshot = snapshot(
            simState = SimCardState.READY,
            serviceState = ServiceStateKind.IN_SERVICE,
            cells = listOf(
                cell(registered = true, ageMillis = 5_000L),
            ),
        )

        val facts = labeler.label(snapshot)

        assertTrue(SimFactLabel.HAS_REGISTERED_CELL in facts)
        assertFalse(SimFactLabel.REGISTERED_CELL_FRESH in facts)
        assertFalse(SimCardFactLabeler.isHighValueContradiction(facts))
    }

    @Test
    fun `not ready sim with no cells only carries sim not ready`() {
        val snapshot = snapshot(
            simState = SimCardState.NOT_READY,
            serviceState = ServiceStateKind.OUT_OF_SERVICE,
            cells = emptyList(),
        )

        val facts = labeler.label(snapshot)

        assertEquals(setOf(SimFactLabel.SIM_NOT_READY), facts)
        assertFalse(SimCardFactLabeler.isHighValueContradiction(facts))
    }

    @Test
    fun `unregistered cells never satisfy has registered cell`() {
        val snapshot = snapshot(
            simState = SimCardState.READY,
            serviceState = ServiceStateKind.IN_SERVICE,
            cells = listOf(
                cell(registered = false, ageMillis = 50L),
            ),
        )

        val facts = labeler.label(snapshot)

        assertFalse(SimFactLabel.HAS_REGISTERED_CELL in facts)
        assertFalse(SimFactLabel.REGISTERED_CELL_FRESH in facts)
    }

    private fun snapshot(
        simState: SimCardState,
        serviceState: ServiceStateKind,
        cells: List<CellInfoSnapshot>,
    ): SubscriptionSnapshot {
        return SubscriptionSnapshot(
            slotId = 0,
            subId = 1,
            simState = simState,
            serviceState = serviceState,
            cells = cells,
        )
    }

    private fun cell(
        registered: Boolean,
        ageMillis: Long?,
    ): CellInfoSnapshot {
        return CellInfoSnapshot(
            rat = SimCardRat.LTE,
            registered = registered,
            ageMillis = ageMillis,
        )
    }
}
