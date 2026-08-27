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

package com.eltavine.duckdetector.features.simcard.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NetworkCell
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.ui.components.DetectorCardFrame
import com.eltavine.duckdetector.core.ui.components.DetectorSectionFrame
import com.eltavine.duckdetector.core.ui.components.WrapSafeText
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardCardModel
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardCellModel
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardFactModel
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardHeaderFactModel
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardRowModel
import com.eltavine.duckdetector.features.simcard.ui.model.SimCardSectionModel
import com.eltavine.duckdetector.ui.theme.ShapeTokens

@Composable
fun SimCardCard(
    model: SimCardCardModel,
    modifier: Modifier = Modifier,
) {
    DetectorCardFrame(
        title = model.title,
        subtitle = model.subtitle,
        status = model.status,
        verdict = model.verdict,
        summary = model.summary,
        leadingIcon = Icons.Rounded.SimCard,
        modifier = modifier,
        headerFacts = {
            SimCardHeader(model.headerFacts)
        },
    ) {
        model.sections.forEach { section ->
            SimCardSection(model = section)
        }
    }
}

@Composable
private fun SimCardHeader(
    facts: List<SimCardHeaderFactModel>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        facts.chunked(2).forEach { rowFacts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                rowFacts.forEach { fact ->
                    SimCardFactCard(
                        fact = fact,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowFacts.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SimCardFactCard(
    fact: SimCardHeaderFactModel,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ShapeTokens.CornerExtraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WrapSafeText(
                text = fact.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WrapSafeText(
                text = fact.value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SimCardSection(
    model: SimCardSectionModel,
) {
    DetectorSectionFrame(
        title = model.title,
        icon = Icons.Rounded.SimCard,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            model.rows.forEachIndexed { index, row ->
                SimCardRow(row)
                if (index < model.rows.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
                        thickness = 1.dp,
                    )
                }
            }
        }

        if (model.facts.isNotEmpty()) {
            SimCardFacts(model.facts)
        }

        model.cells.forEach { cell ->
            SimCardCell(cell)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimCardFacts(
    facts: List<SimCardFactModel>,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        facts.forEach { fact ->
            SimCardFactChip(fact)
        }
    }
}

@Composable
private fun SimCardFactChip(
    fact: SimCardFactModel,
) {
    val containerColor = if (fact.active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (fact.active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = ShapeTokens.CornerFull,
        color = containerColor,
    ) {
        WrapSafeText(
            text = fact.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun SimCardRow(
    row: SimCardRowModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WrapSafeText(
                text = row.label,
                modifier = Modifier.weight(0.34f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WrapSafeText(
                text = row.value,
                modifier = Modifier.weight(0.66f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (row.detailMonospace) FontFamily.Monospace else FontFamily.Default,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SimCardCell(
    cell: SimCardCellModel,
) {
    Surface(
        shape = ShapeTokens.CornerLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.NetworkCell,
                    contentDescription = null,
                    tint = if (cell.registered) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                WrapSafeText(
                    text = cell.rat,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                WrapSafeText(
                    text = if (cell.registered) "registered" else "not registered",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SimCardCellMeta(label = "Signal", value = cell.dbm, modifier = Modifier.weight(1f))
                SimCardCellMeta(label = "Age", value = cell.ageMillis, modifier = Modifier.weight(1f))
            }

            if (cell.identityRows.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                    thickness = 1.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cell.identityRows.forEach { identityRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            WrapSafeText(
                                text = identityRow.label,
                                modifier = Modifier.weight(0.34f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            WrapSafeText(
                                text = identityRow.value,
                                modifier = Modifier.weight(0.66f),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimCardCellMeta(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WrapSafeText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WrapSafeText(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
