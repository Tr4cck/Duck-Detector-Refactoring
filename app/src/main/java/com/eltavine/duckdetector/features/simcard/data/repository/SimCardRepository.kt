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

package com.eltavine.duckdetector.features.simcard.data.repository

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.eltavine.duckdetector.features.simcard.domain.CellIdentitySnapshot
import com.eltavine.duckdetector.features.simcard.domain.CellInfoSnapshot
import com.eltavine.duckdetector.features.simcard.domain.CellSignalSnapshot
import com.eltavine.duckdetector.features.simcard.domain.ServiceStateKind
import com.eltavine.duckdetector.features.simcard.domain.SimCardFactLabeler
import com.eltavine.duckdetector.features.simcard.domain.SimCardRat
import com.eltavine.duckdetector.features.simcard.domain.SimCardReport
import com.eltavine.duckdetector.features.simcard.domain.SimCardStage
import com.eltavine.duckdetector.features.simcard.domain.SimCardState
import com.eltavine.duckdetector.features.simcard.domain.SubscriptionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SimCardRepository(
    context: Context,
    private val factLabeler: SimCardFactLabeler = SimCardFactLabeler(),
) {

    private val appContext = context.applicationContext

    suspend fun scan(): SimCardReport = withContext(Dispatchers.Default) {
        try {
            buildReport()
        } catch (throwable: Throwable) {
            SimCardReport.failed(throwable.message ?: "SIM card collection failed.")
        }
    }

    private suspend fun buildReport(): SimCardReport {
        val telephonyManager = appContext.getSystemService(TelephonyManager::class.java)
        val subscriptionManager = appContext.getSystemService(SubscriptionManager::class.java)
        if (telephonyManager == null || subscriptionManager == null) {
            return SimCardReport.failed("Telephony services unavailable.")
        }

        return SimCardReport(
            stage = SimCardStage.READY,
            subscriptions = enumerateSubscriptions(subscriptionManager, telephonyManager),
        )
    }

    private suspend fun enumerateSubscriptions(
        subscriptionManager: SubscriptionManager,
        telephonyManager: TelephonyManager,
    ): List<SubscriptionSnapshot> {
        val activeSubscriptions = runCatching {
            subscriptionManager.activeSubscriptionInfoList
        }.getOrNull()

        if (activeSubscriptions.isNullOrEmpty()) {
            // Framework enumeration can fail without READ_PHONE_STATE; fall back to the default
            // subscription so the card still reports a best-effort snapshot.
            return listOf(
                collectSubscription(
                    telephonyManager = telephonyManager,
                    slotId = null,
                    subId = SubscriptionManager.getDefaultSubscriptionId()
                        .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID },
                ),
            )
        }

        val snapshots = mutableListOf<SubscriptionSnapshot>()
        for (subscriptionInfo in activeSubscriptions) {
            snapshots += collectSubscription(
                telephonyManager = telephonyManager,
                slotId = subscriptionInfo.simSlotIndex,
                subId = subscriptionInfo.subscriptionId,
            )
        }
        return snapshots
    }

    private suspend fun collectSubscription(
        telephonyManager: TelephonyManager,
        slotId: Int?,
        subId: Int?,
    ): SubscriptionSnapshot {
        val subTm = subId?.let { id ->
            runCatching { telephonyManager.createForSubscriptionId(id) }
                .getOrNull()
        } ?: telephonyManager

        val snapshot = SubscriptionSnapshot(
            slotId = slotId,
            subId = subId,
            simState = resolveSimState(runCatching { subTm.simState }.getOrNull()),
            carrierId = runCatching { subTm.simCarrierId }.getOrNull()
                ?.takeIf { it != TelephonyManager.UNKNOWN_CARRIER_ID },
            simOperator = runCatching { subTm.simOperator }.getOrNull().orEmpty(),
            serviceState = resolveServiceState(runCatching { subTm.serviceState }.getOrNull()),
            cells = collectCellInfo(subTm),
        )
        return snapshot.copy(facts = factLabeler.label(snapshot))
    }

    private suspend fun collectCellInfo(subTm: TelephonyManager): List<CellInfoSnapshot> {
        val rawCells = requestCellInfoUpdate(subTm)
        val elapsedRealtime = SystemClock.elapsedRealtime()
        return rawCells.map { cell -> mapCell(cell, elapsedRealtime) }
    }

    private suspend fun requestCellInfoUpdate(subTm: TelephonyManager): List<CellInfo> {
        return withTimeoutOrNull(CELL_INFO_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val executor: ExecutorService = Executors.newSingleThreadExecutor()
                val callback = object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                        executor.shutdown()
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success<List<CellInfo>>(cellInfo))
                        }
                    }

                    override fun onError(errorCode: Int, detail: Throwable?) {
                        executor.shutdown()
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success<List<CellInfo>>(emptyList()))
                        }
                    }
                }
                runCatching { subTm.requestCellInfoUpdate(executor, callback) }
                    .onFailure {
                        executor.shutdown()
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success<List<CellInfo>>(emptyList()))
                        }
                    }
                continuation.invokeOnCancellation { executor.shutdownNow() }
            }
        } ?: emptyList()
    }

    private fun mapCell(cell: CellInfo, elapsedRealtime: Long): CellInfoSnapshot {
        val timestampMillis = runCatching { cell.timestampMillis }.getOrNull()
            ?.takeIf { it > 0L && it != CellInfo.UNAVAILABLE_LONG }
        val ageMillis = timestampMillis?.let { (elapsedRealtime - it).coerceAtLeast(0L) }

        return CellInfoSnapshot(
            rat = resolveRat(cell),
            registered = runCatching { cell.isRegistered }.getOrDefault(false),
            timestampMillis = timestampMillis,
            ageMillis = ageMillis,
            identity = resolveIdentity(cell),
            signal = resolveSignal(cell),
        )
    }

    private fun resolveRat(cell: CellInfo): SimCardRat = when (cell) {
        is CellInfoLte -> SimCardRat.LTE
        is CellInfoNr -> SimCardRat.NR
        is CellInfoWcdma -> SimCardRat.WCDMA
        is CellInfoGsm -> SimCardRat.GSM
        is CellInfoTdscdma -> SimCardRat.TDSCDMA
        is CellInfoCdma -> SimCardRat.CDMA
        else -> SimCardRat.UNKNOWN
    }

    private fun resolveIdentity(cell: CellInfo): CellIdentitySnapshot = when (cell) {
        is CellInfoLte -> {
            val identity = cell.cellIdentity
            CellIdentitySnapshot(
                mcc = identity.mccString,
                mnc = identity.mncString,
                tac = identity.tac.takeIfAvailable()?.toString(),
                ci = identity.ci.takeIfAvailable()?.toString(),
                pci = identity.pci.takeIfAvailable()?.toString(),
            )
        }

        is CellInfoNr -> {
            // CellInfoNr.getCellIdentity() returns the base CellIdentity (no covariant override),
            // unlike the other RATs, so a cast is required to reach the NR identity fields.
            val identity = cell.cellIdentity as? CellIdentityNr
            if (identity == null) {
                CellIdentitySnapshot()
            } else {
                CellIdentitySnapshot(
                    mcc = identity.mccString,
                    mnc = identity.mncString,
                    tac = identity.tac.takeIfAvailable()?.toString(),
                    nci = identity.nci.takeIfAvailableLong()?.toString(),
                    pci = identity.pci.takeIfAvailable()?.toString(),
                )
            }
        }

        is CellInfoWcdma -> {
            val identity = cell.cellIdentity
            CellIdentitySnapshot(
                mcc = identity.mccString,
                mnc = identity.mncString,
                lac = identity.lac.takeIfAvailable()?.toString(),
                cid = identity.cid.takeIfAvailable()?.toString(),
                psc = identity.psc.takeIfAvailable()?.toString(),
            )
        }

        is CellInfoGsm -> {
            val identity = cell.cellIdentity
            CellIdentitySnapshot(
                mcc = identity.mccString,
                mnc = identity.mncString,
                lac = identity.lac.takeIfAvailable()?.toString(),
                cid = identity.cid.takeIfAvailable()?.toString(),
            )
        }

        is CellInfoTdscdma -> {
            val identity = cell.cellIdentity
            CellIdentitySnapshot(
                mcc = identity.mccString,
                mnc = identity.mncString,
                lac = identity.lac.takeIfAvailable()?.toString(),
                cid = identity.cid.takeIfAvailable()?.toString(),
                psc = identity.cpid.takeIfAvailable()?.toString(),
            )
        }

        else -> CellIdentitySnapshot()
    }

    private fun resolveSignal(cell: CellInfo): CellSignalSnapshot {
        val signal: CellSignalStrength = when (cell) {
            is CellInfoLte -> cell.cellSignalStrength
            is CellInfoNr -> cell.cellSignalStrength
            is CellInfoWcdma -> cell.cellSignalStrength
            is CellInfoGsm -> cell.cellSignalStrength
            is CellInfoTdscdma -> cell.cellSignalStrength
            is CellInfoCdma -> cell.cellSignalStrength
            else -> null
        } ?: return CellSignalSnapshot()

        val dbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { signal.dbm }.getOrDefault(CellInfo.UNAVAILABLE)
                .takeIf { it != CellInfo.UNAVAILABLE }
        } else {
            null
        }
        return CellSignalSnapshot(dbm = dbm)
    }

    private fun resolveSimState(code: Int?): SimCardState = when (code) {
        TelephonyManager.SIM_STATE_READY -> SimCardState.READY
        TelephonyManager.SIM_STATE_NOT_READY -> SimCardState.NOT_READY
        TelephonyManager.SIM_STATE_ABSENT -> SimCardState.ABSENT
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> SimCardState.PIN_REQUIRED
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> SimCardState.PUK_REQUIRED
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> SimCardState.NETWORK_LOCKED
        TelephonyManager.SIM_STATE_PERM_DISABLED -> SimCardState.PERM_DISABLED
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> SimCardState.CARD_IO_ERROR
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> SimCardState.CARD_RESTRICTED
        else -> SimCardState.UNKNOWN
    }

    private fun resolveServiceState(state: ServiceState?): ServiceStateKind {
        return when (state?.state) {
            ServiceState.STATE_IN_SERVICE -> ServiceStateKind.IN_SERVICE
            ServiceState.STATE_OUT_OF_SERVICE -> ServiceStateKind.OUT_OF_SERVICE
            ServiceState.STATE_EMERGENCY_ONLY -> ServiceStateKind.EMERGENCY_ONLY
            ServiceState.STATE_POWER_OFF -> ServiceStateKind.POWER_OFF
            else -> ServiceStateKind.UNKNOWN
        }
    }

    private fun Int.takeIfAvailable(): Int? = takeIf { it != CellInfo.UNAVAILABLE }

    private fun Long.takeIfAvailableLong(): Long? = takeIf { it != CellInfo.UNAVAILABLE_LONG }

    private companion object {
        const val CELL_INFO_TIMEOUT_MILLIS = 3_000L
    }
}
