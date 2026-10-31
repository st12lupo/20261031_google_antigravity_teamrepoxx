package geofence

import domain.model.Store

/** 前回同期した内容の印を保存する場所 */
interface SyncStateStore {
    fun get(): String?
    fun set(value: String?)
}

class InMemorySyncStateStore : SyncStateStore {
    private var value: String? = null
    override fun get(): String? = value
    override fun set(value: String?) {
        this.value = value
    }
}

/**
 * 起動のたびの再登録を避けるための判定。
 * 登録し直すと、店の中にいる状態で登録した直後の ENTER を取りこぼすため、
 * 店舗・権限・端末の状態が前回の同期から変わらないときは再登録しない。
 * 再起動やアプリ更新は environment(起動回数・更新時刻)に含めて変化として検知する。
 */
class GeofenceSyncGate(private val store: SyncStateStore) {

    fun shouldSync(signature: String): Boolean = store.get() != signature

    fun markSynced(signature: String) {
        store.set(signature)
    }

    /** 同期を始める前に呼ぶ。途中で失敗・中断したら記録なしのまま残り、次回必ず再登録される */
    fun invalidate() {
        store.set(null)
    }

    companion object {
        /** 監視対象(通知オンの店舗の ID・位置・半径)と環境から、変化を比べるための印を作る */
        fun signatureOf(stores: List<Store>, environment: String): String {
            val storePart = stores.filter { it.notifyEnabled }
                .sortedBy { it.id }
                .joinToString("|") { "${it.id},${it.latitude},${it.longitude},${it.radiusMeters}" }
            return "$environment#$storePart"
        }
    }
}
