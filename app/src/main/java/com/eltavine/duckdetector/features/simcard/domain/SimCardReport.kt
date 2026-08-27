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

enum class SimCardStage {
    LOADING,
    READY,
    FAILED,
}

/**
 * UICC / SIM application state, normalized away from TelephonyManager.SIM_STATE_* ints so the
 * domain layer stays free of Android framework constants.
 */
enum class SimCardState {
    READY,
    NOT_READY,
    ABSENT,
    PIN_REQUIRED,
    PUK_REQUIRED,
    NETWORK_LOCKED,
    PERM_DISABLED,
    CARD_IO_ERROR,
    CARD_RESTRICTED,
    UNKNOWN,
}

/**
 * Normalized ServiceState (network registration) state.
 */
enum class ServiceStateKind {
    IN_SERVICE,
    OUT_OF_SERVICE,
    EMERGENCY_ONLY,
    POWER_OFF,
    UNKNOWN,
}

enum class SimCardRat {
    LTE,
    NR,
    WCDMA,
    GSM,
    TDSCDMA,
    CDMA,
    UNKNOWN,
}

enum class SimFactLabel {
    SIM_NOT_READY,
    HAS_REGISTERED_CELL,
    REGISTERED_CELL_FRESH,
    SERVICE_IN_SERVICE,
}

data class CellIdentitySnapshot(
    val mcc: String? = null,
    val mnc: String? = null,
    val tac: String? = null,
    val lac: String? = null,
    val ci: String? = null,
    val nci: String? = null,
    val cid: String? = null,
    val pci: String? = null,
    val psc: String? = null,
)

data class CellSignalSnapshot(
    val dbm: Int? = null,
)

data class CellInfoSnapshot(
    val rat: SimCardRat,
    val registered: Boolean,
    val timestampMillis: Long? = null,
    val ageMillis: Long? = null,
    val identity: CellIdentitySnapshot = CellIdentitySnapshot(),
    val signal: CellSignalSnapshot = CellSignalSnapshot(),
)

data class SubscriptionSnapshot(
    val slotId: Int? = null,
    val subId: Int? = null,
    val simState: SimCardState = SimCardState.UNKNOWN,
    val carrierId: Int? = null,
    val simOperator: String = "",
    val serviceState: ServiceStateKind = ServiceStateKind.UNKNOWN,
    val cells: List<CellInfoSnapshot> = emptyList(),
    val facts: Set<SimFactLabel> = emptySet(),
)

data class SimCardReport(
    val stage: SimCardStage,
    val subscriptions: List<SubscriptionSnapshot>,
    val errorMessage: String? = null,
) {
    companion object {
        fun loading(): SimCardReport {
            return SimCardReport(
                stage = SimCardStage.LOADING,
                subscriptions = emptyList(),
            )
        }

        fun failed(message: String): SimCardReport {
            return SimCardReport(
                stage = SimCardStage.FAILED,
                subscriptions = emptyList(),
                errorMessage = message,
            )
        }
    }
}
