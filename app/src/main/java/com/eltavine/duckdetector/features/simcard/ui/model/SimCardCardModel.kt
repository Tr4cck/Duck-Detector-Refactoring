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

package com.eltavine.duckdetector.features.simcard.ui.model

import com.eltavine.duckdetector.core.ui.model.DetectorStatus

data class SimCardCardModel(
    val title: String,
    val subtitle: String,
    val status: DetectorStatus,
    val verdict: String,
    val summary: String,
    val headerFacts: List<SimCardHeaderFactModel>,
    val sections: List<SimCardSectionModel>,
)

data class SimCardHeaderFactModel(
    val label: String,
    val value: String,
)

data class SimCardSectionModel(
    val title: String,
    val rows: List<SimCardRowModel>,
    val facts: List<SimCardFactModel>,
    val cells: List<SimCardCellModel>,
)

data class SimCardRowModel(
    val label: String,
    val value: String,
    val detailMonospace: Boolean = false,
)

data class SimCardFactModel(
    val label: String,
    val active: Boolean,
)

data class SimCardCellModel(
    val rat: String,
    val registered: Boolean,
    val ageMillis: String,
    val dbm: String,
    val identityRows: List<SimCardRowModel>,
)
