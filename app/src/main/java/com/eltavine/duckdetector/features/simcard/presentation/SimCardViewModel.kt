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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eltavine.duckdetector.features.simcard.data.repository.SimCardRepository
import com.eltavine.duckdetector.features.simcard.domain.SimCardReport
import com.eltavine.duckdetector.features.simcard.domain.SimCardStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SimCardViewModel(
    private val repository: SimCardRepository,
    private val mapper: SimCardCardModelMapper = SimCardCardModelMapper(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SimCardUiState(
            stage = SimCardUiStage.LOADING,
            report = SimCardReport.loading(),
            cardModel = mapper.map(SimCardReport.loading()),
        ),
    )
    val uiState: StateFlow<SimCardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val loading = SimCardReport.loading()
            _uiState.update {
                it.copy(
                    stage = SimCardUiStage.LOADING,
                    report = loading,
                    cardModel = mapper.map(loading),
                )
            }

            val report = repository.scan()
            _uiState.update {
                it.copy(
                    stage = if (report.stage == SimCardStage.FAILED) {
                        SimCardUiStage.FAILED
                    } else {
                        SimCardUiStage.READY
                    },
                    report = report,
                    cardModel = mapper.map(report),
                )
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SimCardViewModel(SimCardRepository(appContext)) as T
                }
            }
        }
    }
}
