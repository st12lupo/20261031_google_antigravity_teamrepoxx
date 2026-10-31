package com.example.receiptreminder.data.bridge

import java.io.File
import java.net.URI

/** 撮影した画像の URI 文字列から、ファイルの絶対パスを得る。file: とパス文字列だけを扱う */
internal fun capturedImagePathOf(uri: String): String? {
    if (uri.isBlank()) return null
    val parsed = runCatching { URI(uri) }.getOrNull()
    return when {
        parsed?.scheme.equals("file", ignoreCase = true) ->
            runCatching { File(parsed!!).absolutePath }.getOrNull()
        parsed?.scheme == null || uri.length > 1 && uri[1] == ':' -> File(uri).absolutePath
        else -> null
    }
}

/**
 * 取り込み中(確認画面が開いていて、まだ削除していない)の撮影画像を覚えておく。
 * 起動時の掃除がこれらを消さないようにする(R-6)。
 */
class CapturedImageTracker {
    private val inUse = mutableMapOf<String, Int>()

    @Synchronized
    fun track(uris: List<String>) {
        uris.mapNotNull(::capturedImagePathOf).forEach { inUse.merge(it, 1, Int::plus) }
    }

    @Synchronized
    fun release(uris: List<String>) {
        uris.mapNotNull(::capturedImagePathOf).forEach { path ->
            val n = (inUse[path] ?: return@forEach) - 1
            if (n <= 0) inUse.remove(path) else inUse[path] = n
        }
    }

    @Synchronized
    fun isInUse(path: String): Boolean = inUse.containsKey(File(path).absolutePath)

    companion object {
        /** アプリ全体で共有する。確認画面の ViewModel と起動時の掃除が同じものを見る */
        val shared = CapturedImageTracker()
    }
}

/**
 * ML Kit ドキュメントスキャナーが cache に残した撮影画像を掃除する(R-6, T-06)。
 * CLEAR_TASK やプロセス終了で ViewModel の onCleared が呼ばれなかった分を、次の起動で消す。
 * 取り込み中の画像は消さない: 確認画面が使っている画像(tracker)と、
 * 作られて間もない画像(スキャン中にプロセスが再起動し、結果がこれから届く場合)は残す。
 */
class CapturedImageSweeper(
    private val directory: File,
    private val tracker: CapturedImageTracker = CapturedImageTracker.shared,
    private val minAgeMs: Long = DEFAULT_MIN_AGE_MS,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** 消したファイル数を返す。失敗しても例外は投げない */
    fun sweep(): Int {
        if (!directory.isDirectory) return 0
        val cutoff = now() - minAgeMs
        var deleted = 0
        // 先にファイルを列挙してから消す。ディレクトリ自体は消さない
        val files = runCatching { directory.walkTopDown().filter { it.isFile }.toList() }.getOrDefault(emptyList())
        for (file in files) {
            if (tracker.isInUse(file.absolutePath)) continue
            if (file.lastModified() > cutoff) continue
            if (runCatching { file.delete() }.getOrDefault(false)) deleted++
        }
        return deleted
    }

    companion object {
        const val DIRECTORY_NAME = "mlkit_docscan_ui_client"
        const val DEFAULT_MIN_AGE_MS = 15 * 60 * 1000L

        fun forCacheDir(cacheDir: File) = CapturedImageSweeper(File(cacheDir, DIRECTORY_NAME))
    }
}
