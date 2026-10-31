package geofence

import domain.model.Store

/** 端末のジオフェンス登録を操作する最小の窓口(Play Services を隠して単体テストできるようにする) */
interface GeofenceBackend {
    suspend fun removeAll()

    /** 指定した ID だけを解除する */
    suspend fun removeIds(ids: List<String>)

    /** 追加する。同じ ID は上書きされ、他の登録は消えない */
    suspend fun add(stores: List<Store>)
}

/** 端末に登録済みのはずの店舗 ID を覚えておく場所(再起動やプロセス終了をまたいで残す) */
interface RegisteredIdStore {
    /** 記録がないときは null */
    fun get(): Set<String>?
    fun set(ids: Set<String>)
}

class InMemoryRegisteredIdStore : RegisteredIdStore {
    private var ids: Set<String>? = null
    override fun get(): Set<String>? = ids
    override fun set(ids: Set<String>) {
        this.ids = ids
    }
}

/**
 * 登録内容を「通知オンの店舗だけ」に揃える。
 * 全解除してから登録し直すと、間で失敗やキャンセルが起きたときに全店舗の監視が止まる。
 * そのため先に add(同じ ID は上書き)し、そのあと不要になった ID だけを解除する。
 * 全店舗が不要なときだけ全解除する。
 *
 * 記録がなく不要な ID を特定できないときは、解除せず add だけ行う。
 * 残った古い ID が発火しても、通知側が店舗を見つけられず何も通知しない。
 */
class GeofenceSynchronizer(
    private val backend: GeofenceBackend,
    private val registered: RegisteredIdStore = InMemoryRegisteredIdStore()
) {
    suspend fun sync(stores: List<Store>) {
        val active = stores.filter { it.notifyEnabled }
        if (active.isEmpty()) {
            clear()
            return
        }
        val activeIds = active.map { it.id.toString() }.toSet()
        val known = registered.get().orEmpty()
        backend.add(active)
        // 解除に失敗しても古い ID を覚えたままにして、次の同期でやり直せるようにする
        registered.set(known + activeIds)
        val stale = known - activeIds
        if (stale.isNotEmpty()) backend.removeIds(stale.toList())
        registered.set(activeIds)
    }

    suspend fun clear() {
        backend.removeAll()
        registered.set(emptySet())
    }
}
