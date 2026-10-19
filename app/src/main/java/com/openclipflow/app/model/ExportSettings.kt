package com.openclipflow.app.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExportQuality(val label: String, val bitsPerPixel: Float) {
    SMALL("Mały plik", 0.05f),
    BALANCED("Zrównoważona", 0.09f),
    HIGH("Wysoka", 0.14f),
    MAX("Maksymalna", 0.22f),
}

@Serializable
enum class VideoCodec(val label: String, val mime: String) {
    H264("H.264", "video/avc"),
    HEVC("H.265", "video/hevc"),
}

@Serializable
enum class ExportFormat(val label: String) { VIDEO("Wideo MP4"), AUDIO("Tylko dźwięk M4A") }

/** Ustawienia eksportu; rozdzielczość 0 = jak w projekcie. */
@Serializable
data class ExportSettings(
    val shortSide: Int = 0,
    val fps: Int = 0,
    val quality: ExportQuality = ExportQuality.HIGH,
    val codec: VideoCodec = VideoCodec.H264,
    val format: ExportFormat = ExportFormat.VIDEO,
    val audioBitrate: Int = 192_000,
) {
    fun resolve(seq: SequenceSettings): SequenceSettings =
        seq.copy(shortSide = if (shortSide > 0) shortSide else seq.shortSide, fps = if (fps > 0) fps else seq.fps)

    /** Szacunkowa przepływność wideo w b/s (min. 1 Mb/s, maks. 80 Mb/s). */
    fun videoBitrate(seq: SequenceSettings): Int {
        val r = resolve(seq)
        val codecFactor = if (codec == VideoCodec.HEVC) 0.65f else 1f
        val bps = r.width.toLong() * r.height * r.fps * quality.bitsPerPixel * codecFactor
        return bps.toLong().coerceIn(1_000_000L, 80_000_000L).toInt()
    }

    fun estimatedBytes(seq: SequenceSettings, durationMs: Long): Long {
        val bits = (if (format == ExportFormat.VIDEO) videoBitrate(seq).toLong() else 0L) + audioBitrate
        return bits * durationMs / 8000L
    }

    companion object {
        val RESOLUTIONS = listOf(0, 480, 720, 1080, 1440, 2160)
        val FRAME_RATES = listOf(0, 24, 25, 30, 50, 60)
    }
}
