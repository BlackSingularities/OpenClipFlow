package com.openclipflow.app.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.roundToLong

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class MediaKind { VIDEO, AUDIO, IMAGE }

/** Plik źródłowy w bibliotece projektu (URI z trwałym dostępem albo kopia w pamięci aplikacji). */
@Serializable
data class MediaAsset(
    val id: String = newId(),
    val uri: String,
    val name: String,
    val kind: MediaKind,
    val durationMs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val hasAudio: Boolean = kind != MediaKind.IMAGE,
    val sizeBytes: Long = 0,
    val frameRate: Float = 0f,
    val addedAt: Long = System.currentTimeMillis(),
) {
    /** Wymiary po uwzględnieniu obrotu z metadanych (np. pionowe nagranie z telefonu). */
    val displayWidth: Int get() = if (rotation % 180 != 0) height else width
    val displayHeight: Int get() = if (rotation % 180 != 0) width else height

    /** Zdjęcia można rozciągać dowolnie; wideo i audio mają skończoną długość. */
    val isStill: Boolean get() = kind == MediaKind.IMAGE
}

@Serializable
enum class TrackKind { VIDEO, AUDIO }

@Serializable
data class Track(
    val id: String = newId(),
    val kind: TrackKind,
    val name: String,
    val muted: Boolean = false,
    val locked: Boolean = false,
    val hidden: Boolean = false,
)

/** Gotowe filtry kolorystyczne (macierze RGB + nasycenie). */
@Serializable
enum class FilterPreset(val label: String) {
    NONE("Brak"),
    VIVID("Żywy"),
    WARM("Ciepły"),
    COOL("Chłodny"),
    TEAL_ORANGE("Kino"),
    VINTAGE("Retro"),
    SEPIA("Sepia"),
    FADE("Wyblakły"),
    MONO("Cz-b"),
    NOIR("Noir"),
}

@Serializable
data class ColorAdjust(
    /** -1..1 */
    val brightness: Float = 0f,
    /** -1..1 */
    val contrast: Float = 0f,
    /** -1..1 */
    val saturation: Float = 0f,
    /** Przesunięcie barwy w stopniach, -180..180. */
    val hue: Float = 0f,
    /** -1 (zimno) .. 1 (ciepło) */
    val temperature: Float = 0f,
    /** Rozmycie: 0..1 (sigma do 20 px). */
    val blur: Float = 0f,
) {
    val isIdentity: Boolean get() = this == ColorAdjust()
}

@Serializable
enum class KenBurns(val label: String) { NONE("Brak"), ZOOM_IN("Przybliżenie"), ZOOM_OUT("Oddalenie"), PAN_LEFT("Panorama ←"), PAN_RIGHT("Panorama →") }

@Serializable
data class Transform(
    val scale: Float = 1f,
    /** Stopnie, zgodnie z ruchem wskazówek zegara. */
    val rotation: Float = 0f,
    /** Przesunięcie w ułamkach kadru: -1..1 (0 = środek). */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    /** Wypełnij kadr (przytnij) zamiast zmieścić z pasami. */
    val fill: Boolean = false,
    val kenBurns: KenBurns = KenBurns.NONE,
) {
    val isIdentity: Boolean get() = this == Transform()
}

@Serializable
enum class Transition(val label: String) { NONE("Brak"), FADE_BLACK("Przez czerń"), FADE_WHITE("Przez biel"), ZOOM("Zoom"), SLIDE("Wjazd") }

/**
 * Klip na osi czasu. [startMs]/[durationMs] – położenie na osi (czas wyjściowy),
 * [sourceInMs] – początek fragmentu w pliku źródłowym, [speed] – tempo odtwarzania.
 * Fragment źródła zajmuje durationMs × speed.
 */
@Serializable
data class Clip(
    val id: String = newId(),
    val assetId: String,
    val trackId: String,
    val startMs: Long,
    val durationMs: Long,
    val sourceInMs: Long = 0,
    val speed: Float = 1f,
    /** 0..2 (100% = 1). */
    val volume: Float = 1f,
    val muted: Boolean = false,
    val audioFadeInMs: Long = 0,
    val audioFadeOutMs: Long = 0,
    val transitionIn: Transition = Transition.NONE,
    val transitionOut: Transition = Transition.NONE,
    val transitionMs: Long = 600,
    /**
     * Przenikanie z poprzedniego klipu głównej ścieżki (ms, 0 = twarde cięcie). Oś czasu się nie
     * zmienia – przenikanie korzysta z zapasu materiału przed wejściem tego klipu i/lub za
     * wyjściem poprzedniego (patrz [Dissolves]).
     */
    val dissolveInMs: Long = 0,
    val filter: FilterPreset = FilterPreset.NONE,
    val filterStrength: Float = 1f,
    val color: ColorAdjust = ColorAdjust(),
    val transform: Transform = Transform(),
    /** Krycie warstwy (ścieżki nakładek). */
    val opacity: Float = 1f,
) {
    val endMs: Long get() = startMs + durationMs
    val sourceDurationMs: Long get() = (durationMs * speed).roundToLong()
    val sourceOutMs: Long get() = sourceInMs + sourceDurationMs
}

@Serializable
enum class TitleStyle(val label: String) { PLAIN("Zwykły"), SHADOW("Cień"), BOX("Tło"), OUTLINE("Kontur"), LOWER_THIRD("Belka"), SUBTITLE("Napisy") }

@Serializable
enum class TitleAnimation(val label: String) { NONE("Brak"), FADE("Zanikanie"), SLIDE_UP("Wjazd"), POP("Wyskok"), TYPEWRITER("Maszyna") }

@Serializable
enum class TitleAlign { START, CENTER, END }

/** Napis na ścieżce tekstu. Pozycja i rozmiar względne do kadru, więc działają w każdej rozdzielczości. */
@Serializable
data class TitleClip(
    val id: String = newId(),
    val text: String,
    val startMs: Long,
    val durationMs: Long = 3000,
    /** Środek napisu w kadrze, 0..1. */
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    /** Wysokość liter jako ułamek wysokości kadru. */
    val size: Float = 0.07f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val accent: Int = 0xCC000000.toInt(),
    val style: TitleStyle = TitleStyle.SHADOW,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val align: TitleAlign = TitleAlign.CENTER,
    val animation: TitleAnimation = TitleAnimation.FADE,
    val animationMs: Long = 400,
) {
    val endMs: Long get() = startMs + durationMs
}

@Serializable
data class Marker(
    val id: String = newId(),
    val timeMs: Long,
    val label: String = "",
    val color: Int = 0xFFFFD54F.toInt(),
)

@Serializable
enum class AspectRatio(val label: String, val w: Int, val h: Int) {
    WIDE("16:9", 16, 9),
    PORTRAIT("9:16", 9, 16),
    SQUARE("1:1", 1, 1),
    SOCIAL("4:5", 4, 5),
    CLASSIC("4:3", 4, 3),
    CINEMA("21:9", 21, 9),
}

/** Parametry sekwencji: proporcje, rozdzielczość (krótszy bok), klatkaż i kolor tła. */
@Serializable
data class SequenceSettings(
    val aspect: AspectRatio = AspectRatio.WIDE,
    val shortSide: Int = 1080,
    val fps: Int = 30,
    val background: Int = 0xFF000000.toInt(),
    val snapping: Boolean = true,
) {
    val width: Int get() = even(if (aspect.w >= aspect.h) shortSide * aspect.w / aspect.h else shortSide)
    val height: Int get() = even(if (aspect.w >= aspect.h) shortSide else shortSide * aspect.h / aspect.w)
    val resolutionLabel: String get() = "$width × $height"

    private fun even(v: Int) = v - v % 2
}

@Serializable
data class Project(
    val id: String = newId(),
    val name: String = "Nowy projekt",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val settings: SequenceSettings = SequenceSettings(),
    val assets: List<MediaAsset> = emptyList(),
    val tracks: List<Track> = defaultTracks(),
    val clips: List<Clip> = emptyList(),
    val titles: List<TitleClip> = emptyList(),
    val markers: List<Marker> = emptyList(),
    val formatVersion: Int = FORMAT_VERSION,
) {
    val durationMs: Long
        get() = maxOf(clips.maxOfOrNull { it.endMs } ?: 0L, titles.maxOfOrNull { it.endMs } ?: 0L)

    fun asset(id: String): MediaAsset? = assets.firstOrNull { it.id == id }
    fun track(id: String): Track? = tracks.firstOrNull { it.id == id }
    fun clip(id: String): Clip? = clips.firstOrNull { it.id == id }
    fun title(id: String): TitleClip? = titles.firstOrNull { it.id == id }

    val videoTracks: List<Track> get() = tracks.filter { it.kind == TrackKind.VIDEO }
    val audioTracks: List<Track> get() = tracks.filter { it.kind == TrackKind.AUDIO }

    /** Główna ścieżka obrazu (pierwsza ścieżka wideo); kolejne to nakładki (obraz w obrazie). */
    val mainTrack: Track get() = videoTracks.first()

    fun clipsOn(trackId: String): List<Clip> = clips.filter { it.trackId == trackId }.sortedBy { it.startMs }

    companion object {
        const val FORMAT_VERSION = 2
        const val MAIN_TRACK = "v1"

        fun defaultTracks() = listOf(
            Track(MAIN_TRACK, TrackKind.VIDEO, "Wideo"),
            Track("v2", TrackKind.VIDEO, "Nakładka"),
            Track("a1", TrackKind.AUDIO, "Audio"),
            Track("a2", TrackKind.AUDIO, "Muzyka"),
        )
    }
}
