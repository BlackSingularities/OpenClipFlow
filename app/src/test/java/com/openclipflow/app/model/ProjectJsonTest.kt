package com.openclipflow.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectJsonTest {
    @Test fun roundTripKeepsEverything() {
        val asset = MediaAsset(id = "v", uri = "content://v", name = "a.mp4", kind = MediaKind.VIDEO, durationMs = 9_000)
        val p = Project(
            name = "Wakacje",
            settings = SequenceSettings(AspectRatio.PORTRAIT, 1440, 60),
            assets = listOf(asset),
            clips = listOf(
                Clip(
                    assetId = "v", trackId = "v1", startMs = 0, durationMs = 3_000, speed = 1.5f, volume = 0.4f,
                    filter = FilterPreset.SEPIA, color = ColorAdjust(brightness = 0.2f), transform = Transform(scale = 2f, kenBurns = KenBurns.ZOOM_IN),
                    transitionIn = Transition.FADE_WHITE,
                ),
            ),
            titles = listOf(TitleClip(text = "Cześć\nświecie", startMs = 500, style = TitleStyle.LOWER_THIRD, animation = TitleAnimation.TYPEWRITER)),
            markers = listOf(Marker(timeMs = 1_200, label = "beat")),
        )
        val back = ProjectJson.decode(ProjectJson.encode(p))
        assertEquals(p, back)
    }

    @Test fun unknownFieldsAndMissingTracksAreTolerated() {
        val json = """{"id":"x","name":"Stary","futureField":42,"tracks":[],"assets":[],"clips":[]}"""
        val p = ProjectJson.decode(json)
        assertEquals("Stary", p.name)
        assertTrue(p.videoTracks.isNotEmpty())
        assertTrue(p.audioTracks.isNotEmpty())
    }

    @Test fun clipsWithMissingAssetsAreDropped() {
        val p = Project(clips = listOf(Clip(assetId = "missing", trackId = "v1", startMs = 0, durationMs = 1_000)))
        assertTrue(ProjectJson.decode(ProjectJson.encode(p)).clips.isEmpty())
    }

    @Test fun historyUndoRedo() {
        val h = History()
        val a = Project(name = "a")
        val b = a.copy(name = "b")
        h.record(a)
        assertEquals(a, h.undo(b))
        assertEquals(b, h.redo(a))
        assertTrue(h.canUndo)
    }

    @Test fun timeFormats() {
        assertEquals("1:05.50", TimeFormat.short(65_500))
        assertEquals("1:00:00", TimeFormat.short(3_600_000))
        assertEquals("00:00:01:15", TimeFormat.timecode(1_500, 30))
        assertEquals(33L, TimeFormat.frameMs(30))
    }

    @Test fun exportEstimates() {
        val s = ExportSettings(quality = ExportQuality.HIGH)
        val seq = SequenceSettings(AspectRatio.WIDE, 1080, 30)
        val bps = s.videoBitrate(seq)
        assertTrue(bps in 5_000_000..15_000_000)
        assertTrue(ExportSettings(codec = VideoCodec.HEVC).videoBitrate(seq) < bps)
        assertEquals(720, ExportSettings(shortSide = 720).resolve(seq).height)
        assertTrue(s.estimatedBytes(seq, 60_000) > 30_000_000)
    }
}
