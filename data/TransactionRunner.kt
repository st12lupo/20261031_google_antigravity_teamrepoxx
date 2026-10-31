package data

/**
 * 複数の DAO 操作を 1 つのトランザクションで実行する。
 * 本番は Room の withTransaction、単体テストの Fake DAO は何もしない既定の実装を使う。
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T

    companion object {
        /** トランザクションを張らず、そのまま実行する */
        val None: TransactionRunner = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
    }
}
