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

package com.eltavine.duckdetector.core.simcard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class SimCardPermissionState(
    val phoneStateGranted: Boolean,
    val fineLocationGranted: Boolean,
) {
    val granted: Boolean
        get() = phoneStateGranted && fineLocationGranted
}

object SimCardPermissions {

    val requestedPermissions: Array<String> = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    fun read(context: Context): SimCardPermissionState {
        return SimCardPermissionState(
            phoneStateGranted = hasPermission(context, Manifest.permission.READ_PHONE_STATE),
            fineLocationGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION),
        )
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
