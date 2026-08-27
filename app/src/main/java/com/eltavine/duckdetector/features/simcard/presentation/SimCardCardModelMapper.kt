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

import com.eltavine.duckdetector.core.ui.model.DetectorStatus
import com.eltavine.duckdetector.core.ui.model.InfoKind
import com.eltavine.duckdetector.features.simcard.domain.CellIdentitySnapshot
import com.eltavine.duckdetector.features.simcard.domain.CellInfoSnapshot
import com.eltavine.duckdetector.features.simcard.domain.SimCardFactLabeler
import com.eltavine.duckdetector.features.simcard.domain.SimCardReport
import com.eltavine.duckdetector.features.simcard.domain.SimCardStage
import com.eltavine.duckdetector.features.simcard.domain.SimFactLabel
import com.eltavine.duckdetector.features.simcard.domain.SubscriptionSnapshot
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardCardModel
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardCellModel
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardFactModel
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardHeaderFactModel
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardRowModel
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardSectionModel

class SimCardCardModelMapper {

    fun map(report: SimCardReport): SimCardCardModel {
        return SimCardCardModel(
            title = "SIM Card",
            subtitle = buildSubtitle(report),
            status = if (report.stage == SimCardStage.FAILED) {
                DetectorStatus.info(InfoKind.ERROR)
            } else {
                DetectorStatus.info(InfoKind.SUPPORT)
            },
            verdict = buildVerdict(report),
            summary = buildSummary(report),
            headerFacts = buildHeaderFacts(report),
            sections = buildSections(report),
        )
    }

    private fun buildSubtitle(report: SimCardReport): String {
        return when (report.stage) {
            SimCardStage.LOADING -> "slot + subscription + radio + cell info"
            SimCardStage.FAILED -> "subscription snapshot unavailable"
            SimCardStage.READY -> {
                val subscriptionCount = report.subscriptions.size
                val cellCount = report.subscriptions.sumOf { it.cells.size }
                "$subscriptionCount subscription(s), $cellCount cell(s)"
            }
        }
    }

    private fun buildVerdict(report: SimCardReport): String {
        return when (report.stage) {
            SimCardStage.LOADING -> "Collecting subscription-scoped SIM and cell info"
            SimCardStage.FAILED -> "SIM card snapshot unavailable"
            SimCardStage.READY -> buildReadyVerdict(report)
        }
    }

    private fun buildReadyVerdict(report: SimCardReport): String {
        val contradictoryCount = report.subscriptions.count {
            SimCardFactLabeler.isHighValueContradiction(it.facts)
        }
        return if (contradictoryCount > 0) {
            "Cross-layer contradiction on $contradictoryCount subscription(s)"
        } else {
            "Subscription-scoped snapshot collected"
        }
    }

    private fun buildSummary(report: SimCardReport): String {
        return when (report.stage) {
            SimCardStage.LOADING -> "Collecting subscription-scoped SIM and cell info"
            SimCardStage.FAILED -> report.errorMessage ?: "SIM card collection failed."
            SimCardStage.READY -> "Cellular subscription and cell info collected. This card is contextual and does not affect detector severity or ranking."
        }
    }

    private fun buildHeaderFacts(report: SimCardReport): List<SimCardHeaderFactModel> {
        return listOf(
            SimCardHeaderFactModel(
                "Subscriptions",
                report.subscriptions.size.toString(),
            ),
            SimCardHeaderFactModel(
                "Cells",
                report.subscriptions.sumOf { it.cells.size }.toString(),
            ),
            SimCardHeaderFactModel(
                "Registered",
                report.subscriptions.sumOf { sub -> sub.cells.count { it.registered } }.toString(),
            ),
            SimCardHeaderFactModel(
                "Contradictions",
                report.subscriptions.count {
                    SimCardFactLabeler.isHighValueContradiction(it.facts)
                }.toString(),
            ),
        )
    }

    private fun buildSections(report: SimCardReport): List<SimCardSectionModel> {
        return when (report.stage) {
            SimCardStage.LOADING -> listOf(placeholderSection())
            SimCardStage.FAILED -> listOf(
                SimCardSectionModel(
                    title = "Unavailable",
                    rows = listOf(
                        SimCardRowModel("Reason", report.errorMessage ?: "Unknown error"),
                    ),
                    facts = emptyList(),
                    cells = emptyList(),
                ),
            )

            SimCardStage.READY -> report.subscriptions.map(::buildSubscriptionSection)
        }
    }

    private fun buildSubscriptionSection(
        subscription: SubscriptionSnapshot,
    ): SimCardSectionModel {
        val slotLabel = subscription.slotId?.let { "slot $it" } ?: "slot unknown"
        val subLabel = subscription.subId?.let { "sub $it" } ?: "sub unknown"

        return SimCardSectionModel(
            title = "SIM $slotLabel / $subLabel",
            rows = listOf(
                SimCardRowModel("Slot", subscription.slotId?.toString() ?: "Unavailable"),
                SimCardRowModel("Subscription ID", subscription.subId?.toString() ?: "Unavailable"),
                SimCardRowModel("SIM state", subscription.simState.name),
                SimCardRowModel("Carrier ID", subscription.carrierId?.toString() ?: "Unavailable"),
                SimCardRowModel(
                    "Operator",
                    subscription.simOperator.ifBlank { "Unavailable" },
                ),
                SimCardRowModel("Service state", subscription.serviceState.name),
            ),
            facts = SimFactLabel.entries.map { label ->
                SimCardFactModel(
                    label = factLabelText(label),
                    active = label in subscription.facts,
                )
            },
            cells = subscription.cells.map(::buildCellModel),
        )
    }

    private fun buildCellModel(cell: CellInfoSnapshot): SimCardCellModel {
        return SimCardCellModel(
            rat = cell.rat.name,
            registered = cell.registered,
            ageMillis = cell.ageMillis?.let { "${it}ms" } ?: "Unavailable",
            dbm = cell.signal.dbm?.let { "$it dBm" } ?: "Unavailable",
            identityRows = buildIdentityRows(cell.identity),
        )
    }

    private fun buildIdentityRows(identity: CellIdentitySnapshot): List<SimCardRowModel> {
        return buildList {
            identity.mcc?.let { add(SimCardRowModel("MCC", it, detailMonospace = true)) }
            identity.mnc?.let { add(SimCardRowModel("MNC", it, detailMonospace = true)) }
            identity.tac?.let { add(SimCardRowModel("TAC", it, detailMonospace = true)) }
            identity.lac?.let { add(SimCardRowModel("LAC", it, detailMonospace = true)) }
            identity.ci?.let { add(SimCardRowModel("CI", it, detailMonospace = true)) }
            identity.nci?.let { add(SimCardRowModel("NCI", it, detailMonospace = true)) }
            identity.cid?.let { add(SimCardRowModel("CID", it, detailMonospace = true)) }
            identity.pci?.let { add(SimCardRowModel("PCI", it, detailMonospace = true)) }
            identity.psc?.let { add(SimCardRowModel("PSC", it, detailMonospace = true)) }
        }
    }

    private fun placeholderSection(): SimCardSectionModel {
        return SimCardSectionModel(
            title = "Subscriptions",
            rows = listOf(SimCardRowModel("Loading", "Pending")),
            facts = emptyList(),
            cells = emptyList(),
        )
    }

    private fun factLabelText(label: SimFactLabel): String = when (label) {
        SimFactLabel.SIM_NOT_READY -> "SIM not ready"
        SimFactLabel.HAS_REGISTERED_CELL -> "Has registered cell"
        SimFactLabel.REGISTERED_CELL_FRESH -> "Registered cell fresh"
        SimFactLabel.SERVICE_IN_SERVICE -> "Service in service"
    }
}
