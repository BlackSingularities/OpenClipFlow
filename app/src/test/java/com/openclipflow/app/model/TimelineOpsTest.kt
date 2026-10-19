package com.openclipflow.app.model

import com.openclipflow.app.model.TimelineOps.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineOpsTest {
    private val video = MediaAsset(id = "v", uri = "content://v", name = "a.mp4", kind = MediaKind.VIDEO, durationMs = 10_000, width = 1920, height = 1080)
    private val video2 = MediaAsset(id = "w", uri = "content://w", name = "b.mp4", kind = MediaKind.VIDEO, durationMs = 6_000, width = 1080, height = 1920, rotation = 0)
    private val photo = MediaAsset(id = "p", uri = "content://p", name = "c.jpg", kind = MediaKind.IMAGE, durationMs = 0, width = 400, height = 300)
    private val song = MediaAsset(id = "s", uri = "content://s", name = "d.mp3", kind = MediaKind.AUDIO, durationMs = 30_000)

    private fun base() = Project(assets = listOf(video, video2, photo, song))

    private fun withMain(vararg clips: Clip) = base().copy(clips = clips.toList())

    @Test fun insertOnEmptyMainTrackStartsAtZero() {
        val (p, c) = TimelineOps.insertAsset(base(), video, 5_000)!!
        assertEquals(Project.MAIN_TRACK, c.trackId)
        assertEquals(0L, c.startMs)
        assertEquals(10_000L, c.durationMs)
        assertEquals(1, p.clips.size)
    }

    @Test fun insertInsideMainClipSplitsAndRipples() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 10_000)
        val (p, c) = TimelineOps.insertAsset(withMain(a), video2, 4_000)!!
        assertEquals(4_000L, c.startMs)
        val main = p.clipsOn("v1")
        assertEquals(3, main.size)
        assertEquals(listOf(0L, 4_000L, 10_000L), main.map { it.startMs })
        // Prawa część rozciętego klipu zaczyna się w źródle tam, gdzie skończyła lewa.
        assertEquals(4_000L, main.last().sourceInMs)
        assertEquals(6_000L, main.last().durationMs)
    }

    @Test fun audioGoesToAudioTrackAndPhotoGetsDefaultDuration() {
        val (_, a) = TimelineOps.insertAsset(base(), song, 1_000)!!
        assertEquals("a1", a.trackId)
        assertEquals(1_000L, a.startMs)
        val (_, ph) = TimelineOps.insertAsset(base(), photo, 0)!!
        assertEquals(TimelineOps.IMAGE_DEFAULT_MS, ph.durationMs)
    }

    @Test fun busyAudioTrackFallsBackToNextAudioTrack() {
        val existing = Clip(assetId = "s", trackId = "a1", startMs = 0, durationMs = 30_000)
        val (_, c) = TimelineOps.insertAsset(base().copy(clips = listOf(existing)), song, 2_000)!!
        assertEquals("a2", c.trackId)
    }

    @Test fun acceptsRules() {
        val p = base()
        val audioTrack = p.track("a1")!!
        val videoTrack = p.track("v1")!!
        assertTrue(TimelineOps.accepts(audioTrack, song))
        assertTrue(TimelineOps.accepts(audioTrack, video))
        assertFalse(TimelineOps.accepts(audioTrack, photo))
        assertFalse(TimelineOps.accepts(videoTrack, song))
        assertTrue(TimelineOps.accepts(videoTrack, photo))
    }

    @Test fun splitKeepsSourceContinuityWithSpeed() {
        val c = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 1_000, durationMs = 4_000, sourceInMs = 2_000, speed = 2f, transitionIn = Transition.FADE_BLACK, transitionOut = Transition.ZOOM)
        val p = TimelineOps.split(withMain(c), "a", 2_000)!!
        val (l, r) = p.clipsOn("v1")
        assertEquals(1_000L, l.durationMs)
        assertEquals(Transition.FADE_BLACK, l.transitionIn)
        assertEquals(Transition.NONE, l.transitionOut)
        assertEquals(2_000L, r.startMs)
        assertEquals(3_000L, r.durationMs)
        assertEquals(4_000L, r.sourceInMs) // 2 s źródła + 1 s osi × 2×
        assertEquals(Transition.NONE, r.transitionIn)
        assertEquals(Transition.ZOOM, r.transitionOut)
    }

    @Test fun splitRejectsEdges() {
        val c = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 4_000)
        assertNull(TimelineOps.split(withMain(c), "a", 50))
        assertNull(TimelineOps.split(withMain(c), "a", 3_990))
    }

    @Test fun trimStartIsLimitedBySourceAndPreviousClip() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 2_000)
        val b = Clip(id = "b", assetId = "v", trackId = "v1", startMs = 3_000, durationMs = 2_000, sourceInMs = 5_000)
        val p = TimelineOps.trimStart(withMain(a, b), "b", 0)!!
        val nb = p.clip("b")!!
        assertEquals(2_000L, nb.startMs) // nie wchodzi na klip „a”
        assertEquals(4_000L, nb.sourceInMs)
        assertEquals(3_000L, nb.durationMs)
    }

    @Test fun trimEndIsLimitedBySourceDuration() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 2_000, sourceInMs = 7_000)
        val p = TimelineOps.trimEnd(withMain(a), "a", 60_000)!!
        assertEquals(3_000L, p.clip("a")!!.durationMs)
    }

    @Test fun photoCanBeStretchedFreely() {
        val a = Clip(id = "a", assetId = "p", trackId = "v1", startMs = 0, durationMs = 4_000)
        val p = TimelineOps.trimEnd(withMain(a), "a", 20_000)!!
        assertEquals(20_000L, p.clip("a")!!.durationMs)
    }

    @Test fun speedChangesDurationAndRipplesMainTrack() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 4_000)
        val b = Clip(id = "b", assetId = "w", trackId = "v1", startMs = 4_000, durationMs = 2_000)
        val p = TimelineOps.setSpeed(withMain(a, b), "a", 2f)!!
        assertEquals(2_000L, p.clip("a")!!.durationMs)
        assertEquals(2f, p.clip("a")!!.speed)
        assertEquals(4_000L, p.clip("a")!!.sourceDurationMs)
        assertEquals(2_000L, p.clip("b")!!.startMs)
    }

    @Test fun moveResolvesCollisionsAndRespectsTrackKinds() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 4_000)
        val b = Clip(id = "b", assetId = "w", trackId = "v1", startMs = 6_000, durationMs = 2_000)
        val p = TimelineOps.move(withMain(a, b), "b", 3_000)!!
        assertEquals(4_000L, p.clip("b")!!.startMs) // dosunięty za klip „a”
        val toAudio = TimelineOps.move(withMain(a, b), "b", 0, "a1")!!
        assertEquals("a1", toAudio.clip("b")!!.trackId) // wideo z dźwiękiem może leżeć na audio
        val photoClip = Clip(id = "c", assetId = "p", trackId = "v1", startMs = 9_000, durationMs = 1_000)
        assertNull(TimelineOps.move(withMain(a, photoClip), "c", 0, "a1"))
    }

    @Test fun rippleDeleteClosesHole() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 4_000)
        val b = Clip(id = "b", assetId = "w", trackId = "v1", startMs = 4_000, durationMs = 2_000)
        val c = Clip(id = "c", assetId = "w", trackId = "v1", startMs = 6_000, durationMs = 2_000)
        val p = TimelineOps.delete(withMain(a, b, c), setOf("b"), ripple = true)
        assertEquals(4_000L, p.clip("c")!!.startMs)
        val q = TimelineOps.delete(withMain(a, b, c), setOf("b"), ripple = false)
        assertEquals(6_000L, q.clip("c")!!.startMs)
    }

    @Test fun closeGapsPacksTrack() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 1_000, durationMs = 1_000)
        val b = Clip(id = "b", assetId = "w", trackId = "v1", startMs = 5_000, durationMs = 2_000)
        val p = TimelineOps.closeGaps(withMain(a, b), "v1")
        assertEquals(listOf(0L, 1_000L), p.clipsOn("v1").map { it.startMs })
    }

    @Test fun duplicateOnMainInsertsRightAfter() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 3_000)
        val b = Clip(id = "b", assetId = "w", trackId = "v1", startMs = 3_000, durationMs = 2_000)
        val (p, ids) = TimelineOps.duplicate(withMain(a, b), setOf("a"))!!
        val copy = p.clip(ids.single())!!
        assertEquals(3_000L, copy.startMs)
        assertEquals(6_000L, p.clip("b")!!.startMs)
    }

    @Test fun detachAudioMutesVideoAndCreatesAudioClip() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 3_000, sourceInMs = 500, speed = 1.5f)
        val (p, audio) = TimelineOps.detachAudio(withMain(a), "a")!!
        assertTrue(p.clip("a")!!.muted)
        assertEquals("a1", audio.trackId)
        assertEquals(500L, audio.sourceInMs)
        assertEquals(1.5f, audio.speed)
        assertNull(TimelineOps.detachAudio(p, "a"))
    }

    @Test fun segmentsFillGapsAndPad() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 1_000, durationMs = 1_000)
        val b = Clip(id = "b", assetId = "w", trackId = "v1", startMs = 3_000, durationMs = 1_000)
        val segs = TimelineOps.segments(withMain(a, b), "v1", padToMs = 6_000)
        assertEquals(listOf(1_000L, 1_000L, 1_000L, 1_000L, 2_000L), segs.map { it.durationMs })
        assertTrue(segs[0] is Segment.Gap)
        assertTrue(segs[1] is Segment.Item)
        assertEquals(6_000L, segs.sumOf { it.durationMs })
    }

    @Test fun segmentsTrimOverlaps() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 2_000)
        val b = Clip(id = "b", assetId = "w", trackId = "v1", startMs = 1_500, durationMs = 2_000, sourceInMs = 0)
        val segs = TimelineOps.segments(withMain(a, b), "v1")
        val second = (segs[1] as Segment.Item).clip
        assertEquals(2_000L, second.startMs)
        assertEquals(1_500L, second.durationMs)
        assertEquals(500L, second.sourceInMs)
    }

    @Test fun snapUsesThreshold() {
        assertEquals(1_000L, TimelineOps.snap(1_040, listOf(0L, 1_000L), 50))
        assertEquals(1_100L, TimelineOps.snap(1_100, listOf(0L, 1_000L), 50))
    }

    @Test fun titleLanesSeparateOverlaps() {
        val t1 = TitleClip(id = "1", text = "a", startMs = 0, durationMs = 3_000)
        val t2 = TitleClip(id = "2", text = "b", startMs = 1_000, durationMs = 3_000)
        val t3 = TitleClip(id = "3", text = "c", startMs = 3_000, durationMs = 1_000)
        val lanes = TimelineOps.titleLanes(listOf(t1, t2, t3))
        assertEquals(0, lanes["1"])
        assertEquals(1, lanes["2"])
        assertEquals(0, lanes["3"])
    }

    @Test fun markerToggles() {
        val p = TimelineOps.toggleMarker(base(), 2_000)
        assertEquals(1, p.markers.size)
        assertEquals(0, TimelineOps.toggleMarker(p, 2_100).markers.size)
    }

    @Test fun projectDurationIncludesTitles() {
        val a = Clip(id = "a", assetId = "v", trackId = "v1", startMs = 0, durationMs = 2_000)
        val p = withMain(a).copy(titles = listOf(TitleClip(text = "x", startMs = 1_000, durationMs = 5_000)))
        assertEquals(6_000L, p.durationMs)
    }

    @Test fun removeTrackKeepsMainAndLastAudio() {
        val p = base()
        assertNull(TimelineOps.removeTrack(p, "v1"))
        val one = TimelineOps.removeTrack(p, "a2")!!
        assertNull(TimelineOps.removeTrack(one, "a1"))
        assertNotNull(TimelineOps.removeTrack(p, "v2"))
    }

    @Test fun sequenceSettingsDimensions() {
        assertEquals(1920, SequenceSettings(AspectRatio.WIDE, 1080).width)
        assertEquals(1080, SequenceSettings(AspectRatio.WIDE, 1080).height)
        assertEquals(1080, SequenceSettings(AspectRatio.PORTRAIT, 1080).width)
        assertEquals(1920, SequenceSettings(AspectRatio.PORTRAIT, 1080).height)
        assertEquals(1350, SequenceSettings(AspectRatio.SOCIAL, 1080).height)
        assertEquals(0, SequenceSettings(AspectRatio.CINEMA, 1080).width % 2)
    }
}
