@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.openclipflow.app.engine

import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.Size
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import android.content.Context
import androidx.media3.effect.Presentation
import com.openclipflow.app.model.BackgroundMode
import com.openclipflow.app.model.Clip
import com.openclipflow.app.model.Dissolves
import com.openclipflow.app.model.MediaAsset
import com.openclipflow.app.model.MediaKind
import com.openclipflow.app.model.Project
import com.openclipflow.app.model.SequenceSettings
import com.openclipflow.app.model.TimelineOps
import com.openclipflow.app.model.TimelineOps.Segment
import com.openclipflow.app.model.Track
import com.openclipflow.app.model.TrackKind
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import kotlin.math.min

/**
 * Zamienia projekt w kompozycję Media3. Ta sama kompozycja służy do podglądu
 * (CompositionPlayer) i eksportu (Transformer), więc podgląd odpowiada plikowi wynikowemu.
 *
 * Układ sekwencji: najpierw ścieżki nakładek (od najwyższej – kompozytor rysuje pierwszą
 * sekwencję na wierzchu), potem główna ścieżka obrazu, na końcu ścieżki dźwięku.
 */
object CompositionFactory {

    class Built(
        val composition: Composition,
        val durationMs: Long,
        /** Liczba sekwencji z obrazem (>1 = potrzebny kompozytor wielu wejść). */
        val videoSequences: Int,
    )

    /** Wejście kompozytora (kolejność = od góry): nakładka albo warstwa głównej ścieżki. */
    private sealed interface Input {
        data class Overlay(val track: Track) : Input
        /** Górna warstwa A/B głównej ścieżki – krycie wg okien przenikania. */
        data class MainTop(val items: List<Dissolves.Item>) : Input
        data object MainBase : Input
        /** Tło pod wszystkim (kolor albo rozmyta kopia głównej ścieżki). */
        data object Background : Input
    }

    /**
     * [multiLayer] = false wyłącza wszystko, co wymaga kompozytora wielu wejść (nakładki
     * i przenikania) – awaryjny podgląd na urządzeniach bez jego obsługi.
     */
    fun build(
        context: Context,
        project: Project,
        seq: SequenceSettings,
        multiLayer: Boolean = true,
        includeTitles: Boolean = true,
        audioOnly: Boolean = false,
    ): Built? {
        val total = project.durationMs
        if (total <= 0) return null
        val totalUs = total * 1000
        val w = seq.width
        val h = seq.height
        val main = project.mainTrack

        val sequences = mutableListOf<EditedMediaItemSequence>()
        val inputs = mutableListOf<Input>()
        // Przenikania: główna ścieżka jako dwie warstwy (montaż A/B).
        val abItems = if (multiLayer && !main.hidden && Dissolves.hasAny(project)) Dissolves.layers(project) else null

        if (!audioOnly) {
            val overlays = if (multiLayer) {
                project.videoTracks.drop(1).filter { t -> !t.hidden && project.clips.any { it.trackId == t.id } }.reversed()
            } else {
                emptyList()
            }
            overlays.forEach { t ->
                sequences += videoSequence(project, t, TimelineOps.segments(project, t.id, padToMs = total), w, h, seq.fps, overlay = true)
                inputs += Input.Overlay(t)
            }
            if (abItems != null) {
                sequences += videoSequence(project, main, layerSegments(abItems, 1, total), w, h, seq.fps, overlay = false)
                inputs += Input.MainTop(abItems.filter { it.layer == 1 })
                sequences += videoSequence(project, main, layerSegments(abItems, 0, total), w, h, seq.fps, overlay = false)
                inputs += Input.MainBase
            } else {
                val segs = if (main.hidden) listOf(Segment.Gap(total)) else TimelineOps.segments(project, main.id, padToMs = total)
                sequences += videoSequence(project, main, segs, w, h, seq.fps, overlay = false)
                inputs += Input.MainBase
            }
            if (multiLayer && project.settings.backgroundMode != BackgroundMode.BLACK) {
                backgroundSequence(context, project, total, w, h, seq.fps)?.let {
                    sequences += it
                    inputs += Input.Background
                }
            }
        }

        project.audioTracks.filter { !it.muted }.forEach { t ->
            val segs = TimelineOps.segments(project, t.id) { c -> !c.muted }
            if (segs.any { it is Segment.Item }) sequences += audioSequence(project, t, segs)
        }
        if (audioOnly) {
            // Sam dźwięk: cisza o długości projektu + dźwięk głównej ścieżki obrazu.
            sequences += EditedMediaItemSequence.Builder(ImmutableSet.of(C.TRACK_TYPE_AUDIO)).addGap(totalUs).build()
            if (!main.muted) {
                val hasAudio = { c: Clip -> !c.muted && project.asset(c.assetId)?.hasAudio == true }
                val mainSegs = if (abItems != null) {
                    listOf(layerSegments(abItems, 0, 0), layerSegments(abItems, 1, 0))
                } else {
                    listOf(TimelineOps.segments(project, main.id))
                }
                mainSegs.forEach { segs ->
                    val filtered = segs.map { if (it is Segment.Item && !hasAudio(it.clip)) Segment.Gap(it.durationMs) else it }
                    if (filtered.any { it is Segment.Item }) sequences += audioSequence(project, main, filtered)
                }
            }
        }

        val builder = Composition.Builder(sequences)
        val videoCount = if (audioOnly) 0 else inputs.size
        if (!audioOnly && inputs.size > 1) builder.setVideoCompositorSettings(LayerCompositor(project, inputs, w, h))
        if (!audioOnly && includeTitles && project.titles.isNotEmpty()) {
            builder.setEffects(Effects(ImmutableList.of(), ImmutableList.of<Effect>(OverlayEffect(ImmutableList.of<androidx.media3.effect.TextureOverlay>(TitlesOverlay(project.titles))))))
        }
        return Built(builder.build(), total, videoCount)
    }

    /**
     * Tło: jednolity kolor (obraz 16×16 rozciągnięty na kadr) albo rozmyta, przyciemniona kopia
     * głównej ścieżki wypełniająca cały kadr. Rozmycie liczone w małej rozdzielczości – tanio.
     */
    private fun backgroundSequence(context: Context, p: Project, totalMs: Long, w: Int, h: Int, fps: Int): EditedMediaItemSequence? {
        val b = EditedMediaItemSequence.Builder(ImmutableSet.of(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
        when (p.settings.backgroundMode) {
            BackgroundMode.BLACK -> return null
            BackgroundMode.COLOR -> {
                val file = colorImage(context, p.settings.background) ?: return null
                val item = MediaItem.Builder().setUri(android.net.Uri.fromFile(file)).setImageDurationMs(totalMs).build()
                b.addItem(
                    EditedMediaItem.Builder(item)
                        .setDurationUs(totalMs * 1000)
                        .setFrameRate(fps)
                        .setRemoveAudio(true)
                        .setEffects(Effects(ImmutableList.of(), ImmutableList.of<Effect>(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_STRETCH_TO_FIT))))
                        .build(),
                )
            }
            BackgroundMode.BLUR -> {
                val main = p.mainTrack
                if (main.hidden) return null
                TimelineOps.segments(p, main.id, padToMs = totalMs).forEach { s ->
                    val asset = (s as? Segment.Item)?.let { p.asset(it.clip.assetId) }
                    if (s is Segment.Gap || asset == null) {
                        b.addGap(s.durationMs * 1000)
                    } else {
                        val clip = (s as Segment.Item).clip
                        val effects = ImmutableList.of<Effect>(
                            Presentation.createForWidthAndHeight(VideoEffects.evenPx(w / 8f), VideoEffects.evenPx(h / 8f), Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
                            androidx.media3.effect.GaussianBlur(3f),
                            StaticRgb(VideoEffects.scaleMatrix(0.6f, 0.6f, 0.6f)),
                            Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_STRETCH_TO_FIT),
                        )
                        val eb = EditedMediaItem.Builder(mediaItem(asset, clip)).setFrameRate(fps).setRemoveAudio(true).setEffects(Effects(ImmutableList.of(), effects))
                        if (asset.kind == MediaKind.IMAGE) eb.setDurationUs(clip.durationMs * 1000) else eb.setDurationUs(asset.durationMs * 1000).applySpeed(clip)
                        b.addItem(eb.build())
                    }
                }
            }
        }
        return b.build()
    }

    private fun colorImage(context: Context, color: Int): java.io.File? = runCatching {
        val dir = java.io.File(context.cacheDir, "backgrounds").apply { mkdirs() }
        val f = java.io.File(dir, "bg_" + Integer.toHexString(color) + ".png")
        if (!f.exists()) {
            val bmp = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.eraseColor(color or (0xFF shl 24))
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        f
    }.getOrNull()

    /** Segmenty jednej warstwy A/B w formacie [Segment] (klip z poszerzonym zakresem). */
    private fun layerSegments(items: List<Dissolves.Item>, layer: Int, padToMs: Long): List<Segment> =
        Dissolves.segments(items, layer, padToMs).map { (dur, item) -> if (item == null) Segment.Gap(dur) else Segment.Item(item.render) }

    private fun videoSequence(p: Project, track: Track, segs: List<Segment>, w: Int, h: Int, fps: Int, overlay: Boolean): EditedMediaItemSequence {
        val b = EditedMediaItemSequence.Builder(ImmutableSet.of(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
        segs.forEach { s ->
            when (s) {
                is Segment.Gap -> b.addGap(s.durationMs * 1000)
                is Segment.Item -> {
                    val asset = p.asset(s.clip.assetId)
                    if (asset == null) b.addGap(s.durationMs * 1000) else b.addItem(videoItem(asset, s.clip, track, w, h, fps, overlay))
                }
            }
        }
        return b.build()
    }

    private fun audioSequence(p: Project, track: Track, segs: List<Segment>): EditedMediaItemSequence {
        val b = EditedMediaItemSequence.Builder(ImmutableSet.of(C.TRACK_TYPE_AUDIO))
        segs.forEach { s ->
            when (s) {
                is Segment.Gap -> b.addGap(s.durationMs * 1000)
                is Segment.Item -> {
                    val asset = p.asset(s.clip.assetId)
                    if (asset == null || asset.kind == MediaKind.IMAGE) {
                        b.addGap(s.durationMs * 1000)
                    } else {
                        b.addItem(
                            EditedMediaItem.Builder(mediaItem(asset, s.clip))
                                .setDurationUs(asset.durationMs * 1000)
                                .setRemoveVideo(true)
                                .applySpeed(s.clip)
                                .setEffects(Effects(audioProcessors(s.clip, track), ImmutableList.of()))
                                .build(),
                        )
                    }
                }
            }
        }
        return b.build()
    }

    private fun videoItem(asset: MediaAsset, clip: Clip, track: Track, w: Int, h: Int, fps: Int, overlay: Boolean): EditedMediaItem {
        val timing = ClipTiming(clip.startMs * 1000, clip.durationMs * 1000)
        val effects = VideoEffects.forClip(clip, timing, w, h, overlay)
        val silent = clip.muted || track.muted || !asset.hasAudio || asset.kind == MediaKind.IMAGE
        val b = EditedMediaItem.Builder(mediaItem(asset, clip))
            .setFrameRate(fps)
            .setRemoveAudio(silent)
            .setEffects(Effects(if (silent) ImmutableList.of() else audioProcessors(clip, track), effects))
        if (asset.kind == MediaKind.IMAGE) {
            b.setDurationUs(clip.durationMs * 1000)
        } else {
            b.setDurationUs(asset.durationMs * 1000)
            b.applySpeed(clip)
        }
        return b.build()
    }

    private fun EditedMediaItem.Builder.applySpeed(clip: Clip): EditedMediaItem.Builder {
        if (clip.speed != 1f) setSpeed(ConstantSpeed(clip.speed))
        return this
    }

    private fun mediaItem(asset: MediaAsset, clip: Clip): MediaItem {
        val b = MediaItem.Builder().setUri(asset.uri).setMediaId(clip.id)
        if (asset.kind == MediaKind.IMAGE) {
            b.setImageDurationMs(clip.durationMs)
        } else {
            val end = min(asset.durationMs, clip.sourceOutMs)
            b.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.sourceInMs.coerceAtLeast(0))
                    .setEndPositionMs(end.coerceAtLeast(clip.sourceInMs + 1))
                    .build(),
            )
        }
        return b.build()
    }

    /** Głośność (z podbiciem powyżej 100%) i wyciszenia na początku i końcu klipu. */
    private fun audioProcessors(clip: Clip, track: Track): ImmutableList<AudioProcessor> {
        val list = ImmutableList.builder<AudioProcessor>()
        val volume = if (track.muted) 0f else clip.volume.coerceIn(0f, 2f)
        val durUs = clip.durationMs * 1000
        val fadeIn = min(clip.audioFadeInMs * 1000, durUs / 2)
        val fadeOut = min(clip.audioFadeOutMs * 1000, durUs / 2)
        if (volume < 0.999f || fadeIn > 1 || fadeOut > 1) {
            val gain = DefaultGainProvider.Builder(min(volume, 1f))
            if (fadeIn > 1) gain.addFadeAt(0, fadeIn) { i, n -> min(volume, 1f) * DefaultGainProvider.FADE_IN_EQUAL_POWER.getGainFactorAt(i, n) }
            if (fadeOut > 1) gain.addFadeAt(durUs - fadeOut, fadeOut) { i, n -> min(volume, 1f) * DefaultGainProvider.FADE_OUT_EQUAL_POWER.getGainFactorAt(i, n) }
            list.add(GainProcessor(gain.build()))
        }
        if (volume > 1.001f) {
            val boost = ChannelMixingAudioProcessor()
            for (ch in 1..2) boost.putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(ch, ch).scaleBy(volume))
            list.add(boost)
        }
        return list.build()
    }

    /** Stała prędkość klipu. */
    class ConstantSpeed(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    /**
     * Kompozytor warstw: wynik w rozdzielczości sekwencji. Nakładka widoczna tylko w czasie
     * trwania klipu (krycie + płynne pojawianie się), górna warstwa A/B głównej ścieżki –
     * krycie wg okien przenikania, dolna zawsze pełna.
     */
    private class LayerCompositor(
        private val project: Project,
        private val inputs: List<Input>,
        private val w: Int,
        private val h: Int,
    ) : VideoCompositorSettings {
        private val hidden = StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        private val full = StaticOverlaySettings.Builder().build()

        override fun getOutputSize(inputSizes: List<Size>): Size = Size(w, h)

        private fun alpha(a: Float): OverlaySettings = when {
            a >= 0.999f -> full
            a <= 0.001f -> hidden
            else -> StaticOverlaySettings.Builder().setAlphaScale(a).build()
        }

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            val tMs = presentationTimeUs / 1000
            return when (val input = inputs.getOrNull(inputId)) {
                null, Input.MainBase, Input.Background -> full
                is Input.MainTop -> {
                    val item = input.items.firstOrNull { tMs >= it.render.startMs && tMs < it.render.endMs } ?: return hidden
                    alpha(item.alpha(tMs))
                }
                is Input.Overlay -> {
                    val clip = TimelineOps.clipAt(project, input.track.id, tMs) ?: return hidden
                    val local = (presentationTimeUs - clip.startMs * 1000).coerceAtLeast(0)
                    val level = VideoEffects.transitionLevel(
                        local,
                        clip.durationMs * 1000,
                        clip.transitionMs * 1000,
                        clip.transitionIn != com.openclipflow.app.model.Transition.NONE,
                        clip.transitionOut != com.openclipflow.app.model.Transition.NONE,
                    )
                    alpha(clip.opacity * level)
                }
            }
        }
    }
}
