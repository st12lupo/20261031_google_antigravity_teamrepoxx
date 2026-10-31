package com.example.receiptreminder.data.bridge

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CapturedImageCacheTest {
    private lateinit var root: File
    private lateinit var dir: File
    private var now = 10_000_000L
    private val minAge = 15 * 60 * 1000L

    @Before
    fun setUp() {
        root = Files.createTempDirectory("cache").toFile()
        dir = File(root, CapturedImageSweeper.DIRECTORY_NAME).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun image(name: String, ageMs: Long): File =
        File(dir, name).apply {
            writeText("jpeg")
            setLastModified(now - ageMs)
        }

    private fun sweeper(tracker: CapturedImageTracker) =
        CapturedImageSweeper(dir, tracker, minAge) { now }

    @Test
    fun deletesLeftoverImages() {
        val a = image("a.jpg", minAge + 1)
        val nested = File(dir, "sub").apply { mkdirs() }
        val b = File(nested, "b.jpg").apply {
            writeText("x")
            setLastModified(now - minAge - 1)
        }
        assertEquals(2, sweeper(CapturedImageTracker()).sweep())
        assertFalse(a.exists())
        assertFalse(b.exists())
        assertTrue(dir.isDirectory)
    }

    @Test
    fun keepsImagesInUseByAReviewScreen() {
        val tracker = CapturedImageTracker()
        val used = image("used.jpg", minAge + 1)
        val stale = image("stale.jpg", minAge + 1)
        tracker.track(listOf(used.toURI().toString()))
        assertEquals(1, sweeper(tracker).sweep())
        assertTrue(used.exists())
        assertFalse(stale.exists())
        // 確認画面を閉じた(release)あとは消せる
        tracker.release(listOf(used.toURI().toString()))
        assertEquals(1, sweeper(tracker).sweep())
        assertFalse(used.exists())
    }

    @Test
    fun keepsRecentImages_thatAScanInProgressMayStillDeliver() {
        val fresh = image("fresh.jpg", 60_000)
        assertEquals(0, sweeper(CapturedImageTracker()).sweep())
        assertTrue(fresh.exists())
    }

    @Test
    fun missingDirectory_isNotAnError() {
        dir.deleteRecursively()
        assertEquals(0, sweeper(CapturedImageTracker()).sweep())
    }

    @Test
    fun trackerCountsDuplicates_andIgnoresUnknownSchemes() {
        val tracker = CapturedImageTracker()
        val f = image("x.jpg", 0)
        val uri = f.toURI().toString()
        tracker.track(listOf(uri))
        tracker.track(listOf(f.absolutePath))
        tracker.release(listOf(uri))
        assertTrue(tracker.isInUse(f.absolutePath))
        tracker.release(listOf(f.absolutePath))
        assertFalse(tracker.isInUse(f.absolutePath))
        assertNull(capturedImagePathOf("content://media/1"))
    }
}
