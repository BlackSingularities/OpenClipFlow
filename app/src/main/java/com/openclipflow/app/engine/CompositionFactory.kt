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
import com.openclipflow.app.model.Clip
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

    fun build(
        project: Project,
        seq: SequenceSettings,
        includeOverlays: Boolean = true,
        includeTitles: Boolean = true,
        audioOnly: Boolean = false,
    ): Built? {
        val total = project.durationMs
        if (total <= 0) return null
        val totalUs = total * 1000
        val w = seq.width
        val h = seq.height

        val sequences = mutableListOf<EditedMediaItemSequence>()
        // Kolejne sekwencje obrazu: indeks wejścia kompozytora → ścieżka nakładki.
        val overlayInputs = mutableListOf<Track>()

        if (!audioOnly) {
            val overlays = if (includeOverlays) {
                project.videoTracks.drop(1).filter { t -> !t.hidden && project.clips.any { it.trackId == t.id } }.reversed()
            } else {
                emptyList()
            }
            overlays.forEach { t ->
                sequences += videoSequence(project, t, total, w, h, seq.fps, overlay = true)
                overlayInputs += t
            }
            sequences += videoSequence(project, project.mainTrack, total, w, h, seq.fps, overlay = false)
        }

        project.audioTracks.filter { !it.muted }.forEach { t ->
            val segs = TimelineOps.segments(project, t.id) { c -> !c.muted }
            if (segs.any { it is Segment.Item }) sequences += audioSequence(project, t, segs)
        }
        if (audioOnly && sequences.isEmpty()) {
            // Sam obraz bez dźwięku – plik audio z ciszą o długości projektu.
            sequences += EditedMediaItemSequence.Builder(ImmutableSet.of(C.TRACK_TYPE_AUDIO)).addGap(totalUs).build()
        }
        if (audioOnly) {
            // Dźwięk z głównej ścieżki obrazu też trafia do pliku audio.
            val main = project.mainTrack
            if (!main.muted) {
                val segs = TimelineOps.segments(project, main.id) { c -> !c.muted && project.asset(c.assetId)?.hasAudio == true }
                if (segs.any { it is Segment.Item }) sequences += audioSequence(project, main, segs)
            }
        }

        val builder = Composition.Builder(sequences)
        val videoCount = if (audioOnly) 0 else overlayInputs.size + 1
        if (overlayInputs.isNotEmpty()) builder.setVideoCompositorSettings(OverlayCompositor(project, overlayInputs, w, h))
        if (!audioOnly && includeTitles && project.titles.isNotEmpty()) {
            builder.setEffects(Effects(ImmutableList.of(), ImmutableList.of<Effect>(OverlayEffect(ImmutableList.of<androidx.media3.effect.TextureOverlay>(TitlesOverlay(project.titles))))))
        }
        return Built(builder.build(), total, videoCount)
    }

    private fun videoSequence(p: Project, track: Track, totalMs: Long, w: Int, h: Int, fps: Int, overlay: Boolean): EditedMediaItemSequence {
        val b = EditedMediaItemSequence.Builder(ImmutableSet.of(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
        val segs = if (track.hidden) listOf(Segment.Gap(totalMs)) else TimelineOps.segments(p, track.id, padToMs = totalMs)
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
     * Kompozytor nakładek: wynik w rozdzielczości sekwencji; warstwa nakładki widoczna tylko
     * w czasie trwania klipu, z kryciem i płynnym pojawianiem się/znikaniem (przejścia).
     */
    private class OverlayCompositor(
        private val project: Project,
        private val overlays: List<Track>,
        private val w: Int,
        private val h: Int,
    ) : VideoCompositorSettings {
        private val hidden = StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        private val full = StaticOverlaySettings.Builder().build()

        override fun getOutputSize(inputSizes: List<Size>): Size = Size(w, h)

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            val track = overlays.getOrNull(inputId) ?: return full
            val tMs = presentationTimeUs / 1000
            val clip = TimelineOps.clipAt(project, track.id, tMs) ?: return hidden
            val local = (presentationTimeUs - clip.startMs * 1000).coerceAtLeast(0)
            val level = VideoEffects.transitionLevel(
                local,
                clip.durationMs * 1000,
                clip.transitionMs * 1000,
                clip.transitionIn != com.openclipflow.app.model.Transition.NONE,
                clip.transitionOut != com.openclipflow.app.model.Transition.NONE,
            )
            val alpha = (clip.opacity * level).coerceIn(0f, 1f)
            return if (alpha >= 0.999f) full else StaticOverlaySettings.Builder().setAlphaScale(alpha).build()
        }
    }
}
