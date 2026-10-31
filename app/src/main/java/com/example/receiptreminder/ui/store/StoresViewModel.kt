package com.example.receiptreminder.ui.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.receiptreminder.domain.model.NotificationTimeRange
import com.example.receiptreminder.domain.model.Store
import com.example.receiptreminder.domain.repository.GeofenceManager
import com.example.receiptreminder.domain.repository.NotificationSettingsRepository
import com.example.receiptreminder.domain.repository.StoreRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 画面に表示する操作のエラー */
enum class StoresError {
    StoreLimit,
    LocationUnavailable,
    GeofenceRegisterFailed,
    GeofenceUnregisterFailed,
    SaveFailed,
    DeleteFailed
}

data class StoresUiState(
    val stores: List<Store> = emptyList(),
    val timeRange: NotificationTimeRange = NotificationTimeRange(),
    /** 店舗と時間帯を最初に読み込み終えたか(空の状態の誤表示を避ける) */
    val isLoaded: Boolean = false,
    /** 保存や削除の実行中 */
    val isBusy: Boolean = false,
    val error: StoresError? = null
) {
    val isEmpty: Boolean get() = isLoaded && stores.isEmpty()
}

class StoresViewModel(
    private val storeRepository: StoreRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository,
    private val geofenceManager: GeofenceManager
) : ViewModel() {

    private data class Transient(val isBusy: Boolean = false, val error: StoresError? = null)

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<StoresUiState> = combine(
        storeRepository.getStoresStream(),
        notificationSettingsRepository.getNotificationTimeRangeStream(),
        transient
    ) { stores, range, t ->
        StoresUiState(
            stores = stores,
            timeRange = range,
            isLoaded = true,
            isBusy = t.isBusy,
            error = t.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StoresUiState())

    fun dismissError() {
        transient.update { it.copy(error = null) }
    }

    /** 登録の開始前に呼ぶ。上限に達していればエラーを表示して false を返す */
    fun canStartRegistration(): Boolean {
        if (uiState.value.stores.size >= StoreLimits.MAX_STORES) {
            setError(StoresError.StoreLimit)
            return false
        }
        return true
    }

    /** 現在地を店舗として登録し、ジオフェンスを登録する(S-1、S-2) */
    fun registerStore(name: String) {
        if (StoreInputValidator.validateName(name) != null) return
        if (!canStartRegistration()) return
        runOperation {
            val store = try {
                storeRepository.registerCurrentLocation(
                    name.trim(),
                    StoreLimits.DEFAULT_RADIUS_METERS
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(StoresError.LocationUnavailable)
                return@runOperation
            }
            // 店舗は保存済み。通知の登録に失敗したときは、保存したことを伝えるエラーを出す
            if (store.notifyEnabled) {
                registerGeofenceOrError(store)
            }
        }
    }

    /** 店名と半径を変更する(S-2)。入力が不正なら何もしない */
    fun updateStore(storeId: String, name: String, radiusText: String) {
        if (StoreInputValidator.validateName(name) != null) return
        if (StoreInputValidator.validateRadius(radiusText) != null) return
        val current = findStore(storeId) ?: return
        val updated = current.copy(
            name = name.trim(),
            radiusMeters = radiusText.trim().toInt()
        )
        saveAndSyncGeofence(updated)
    }

    /** 店舗ごとの通知のオン・オフ(S-3) */
    fun setNotifyEnabled(storeId: String, enabled: Boolean) {
        val current = findStore(storeId) ?: return
        if (current.notifyEnabled == enabled) return
        saveAndSyncGeofence(current.copy(notifyEnabled = enabled))
    }

    /** 店舗の削除。ジオフェンスも解除する(S-1) */
    fun deleteStore(storeId: String) {
        if (findStore(storeId) == null) return
        runOperation {
            try {
                storeRepository.deleteStore(storeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(StoresError.DeleteFailed)
                return@runOperation
            }
            unregisterGeofencesOrError(listOf(storeId))
        }
    }

    /** 通知する時間帯を 1 つ設定する(S-4)。入力が不正なら何もしない */
    fun setTimeRange(startText: String, endText: String) {
        if (StoreInputValidator.validateTimeRange(startText, endText) != null) return
        val start = StoreInputValidator.parseTime(startText) ?: return
        val end = StoreInputValidator.parseTime(endText) ?: return
        val range = NotificationTimeRange(start.hour, start.minute, end.hour, end.minute)
        runOperation {
            try {
                notificationSettingsRepository.setNotificationTimeRange(range)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(StoresError.SaveFailed)
            }
        }
    }

    /** 履歴と店舗を全件削除し、登録済みのジオフェンスも解除する(D-1) */
    fun clearAllData() {
        val storeIds = uiState.value.stores.map { it.id }
        runOperation {
            try {
                storeRepository.clearAllData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(StoresError.DeleteFailed)
                return@runOperation
            }
            unregisterGeofencesOrError(storeIds)
        }
    }

    private fun findStore(storeId: String): Store? =
        uiState.value.stores.firstOrNull { it.id == storeId }

    private fun saveAndSyncGeofence(updated: Store) {
        runOperation {
            try {
                storeRepository.updateStore(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(StoresError.SaveFailed)
                return@runOperation
            }
            if (updated.notifyEnabled) {
                // 半径の変更を反映するため、登録し直す
                registerGeofenceOrError(updated)
            } else {
                unregisterGeofencesOrError(listOf(updated.id))
            }
        }
    }

    private suspend fun registerGeofenceOrError(store: Store) {
        try {
            geofenceManager.registerGeofence(store)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setError(StoresError.GeofenceRegisterFailed)
        }
    }

    private suspend fun unregisterGeofencesOrError(storeIds: List<String>) {
        var failed = false
        for (id in storeIds) {
            try {
                geofenceManager.unregisterGeofence(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
        }
        if (failed) setError(StoresError.GeofenceUnregisterFailed)
    }

    private fun setError(error: StoresError) {
        transient.update { it.copy(error = error) }
    }

    /** 実行中は重ねて操作できないようにする */
    private fun runOperation(block: suspend () -> Unit) {
        if (transient.value.isBusy) return
        transient.update { Transient(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                transient.update { it.copy(isBusy = false) }
            }
        }
    }
}
