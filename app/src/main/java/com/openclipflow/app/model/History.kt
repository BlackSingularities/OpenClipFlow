package com.openclipflow.app.model

import kotlinx.serialization.json.Json
import java.util.Locale

/** Historia zmian projektu (cofnij / ponów) – migawki niezmiennych obiektów, więc tania. */
class History(private val limit: Int = 120) {
    private val undo = ArrayDeque<Project>()
    private val redo = ArrayDeque<Project>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun record(before: Project) {
        undo.addLast(before)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: Project): Project? {
        val prev = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        return prev
    }

    fun redo(current: Project): Project? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return next
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }
}

object ProjectJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = false
    }

    fun encode(p: Project): String = json.encodeToString(Project.serializer(), p)

    fun decode(text: String): Project = json.decodeFromString(Project.serializer(), text).normalized()

    /** Po wczytaniu: zawsze jest główna ścieżka wideo i co najmniej jedna audio. */
    fun Project.normalized(): Project {
        var tracks = tracks
        if (tracks.none { it.kind == TrackKind.VIDEO }) tracks = listOf(Track(Project.MAIN_TRACK, TrackKind.VIDEO, "Wideo")) + tracks
        if (tracks.none { it.kind == TrackKind.AUDIO }) tracks = tracks + Track("a1", TrackKind.AUDIO, "Audio")
        val trackIds = tracks.map { it.id }.toSet()
        val assetIds = assets.map { it.id }.toSet()
        val audioTrackIds = tracks.filter { it.kind == TrackKind.AUDIO }.map { it.id }.toSet()
        val byId = assets.associateBy { it.id }
        // Projekty sprzed 1.2: wideo na ścieżce audio = wyodrębniony dźwięk.
        val fixed = clips.filter { it.trackId in trackIds && it.assetId in assetIds }.map { c ->
            if (c.trackId in audioTrackIds && byId[c.assetId]?.kind == MediaKind.VIDEO && !c.audioOnly) c.copy(audioOnly = true) else c
        }
        return copy(tracks = tracks, clips = fixed)
    }
}

object TimeFormat {
    /** 1:23.45 – minuty, sekundy, setne; od godziny: 1:02:03. */
    fun short(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val h = total / 3_600_000
        val m = (total / 60_000) % 60
        val s = (total / 1000) % 60
        val cs = (total % 1000) / 10
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d.%02d", m, s, cs)
    }

    /** Kod czasowy z numerem klatki: 00:01:23:12. */
    fun timecode(ms: Long, fps: Int): String {
        val total = ms.coerceAtLeast(0)
        val f = ((total % 1000) * fps / 1000).toInt()
        val s = (total / 1000) % 60
        val m = (total / 60_000) % 60
        val h = total / 3_600_000
        return String.format(Locale.ROOT, "%02d:%02d:%02d:%02d", h, m, s, f)
    }

    /** Długość do opisu: „12,5 s”, „3 min 05 s”. */
    fun duration(ms: Long): String {
        val s = ms / 1000.0
        return if (s < 60) String.format(Locale.forLanguageTag("pl"), "%.1f s", s)
        else String.format(Locale.ROOT, "%d min %02d s", (ms / 60_000), (ms / 1000) % 60)
    }

    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.forLanguageTag("pl"), "%.2f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(Locale.forLanguageTag("pl"), "%.1f MB", b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> String.format(Locale.forLanguageTag("pl"), "%d kB", b / (1L shl 10))
        else -> "$b B"
    }

    /** Czas jednej klatki w ms (do kroków klatka po klatce). */
    fun frameMs(fps: Int): Long = (1000.0 / fps.coerceAtLeast(1)).toLong().coerceAtLeast(1)
}
