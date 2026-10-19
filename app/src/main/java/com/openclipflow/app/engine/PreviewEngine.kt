@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.openclipflow.app.engine

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.CompositionPlayer
import com.openclipflow.app.model.Project
import com.openclipflow.app.model.SequenceSettings

/**
 * Podgląd montażu: odtwarza kompozycję zbudowaną z projektu (z efektami GPU) na SurfaceView.
 * Nakładki wymagają kompozytora wielu wejść; gdy urządzenie go nie obsłuży, podgląd
 * automatycznie pokazuje samą główną ścieżkę (eksport i tak zawiera nakładki).
 */
class PreviewEngine(context: Context) {
    private val app = context.applicationContext
    private var player: CompositionPlayer? = null
    private var multiInput = false
    private var surface: SurfaceView? = null
    private var lastProject: Project? = null
    private var lastSeq: SequenceSettings? = null
    private var builtDurationMs = 0L

    var isPlaying by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var overlaysUnsupported by mutableStateOf(false)
        private set
    /** Wywoływane, gdy odtwarzanie dojdzie do końca. */
    var onEnded: (() -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            ready = state == Player.STATE_READY || state == Player.STATE_ENDED
            if (state == Player.STATE_ENDED) {
                player?.pause()
                onEnded?.invoke()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Błąd podglądu", error)
            if (multiInput && !overlaysUnsupported) {
                // Najczęściej: brak obsługi kompozytora wielu wejść – podgląd bez nakładek.
                overlaysUnsupported = true
                message = "Podgląd nakładek i przenikań niedostępny na tym urządzeniu – eksport je uwzględni"
                val p = lastProject
                val s = lastSeq
                if (p != null && s != null) {
                    lastProject = null
                    load(p, s, currentPositionMs(), force = true)
                }
            } else {
                message = "Nie można odtworzyć podglądu: ${error.errorCodeName}"
            }
        }
    }

    private fun ensurePlayer(needMulti: Boolean): CompositionPlayer {
        val current = player
        if (current != null && multiInput == needMulti) return current
        current?.removeListener(listener)
        current?.release()
        val b = CompositionPlayer.Builder(app)
        if (needMulti) b.setVideoGraphFactory(MultipleInputVideoGraph.Factory())
        val p = b.build()
        p.addListener(listener)
        surface?.let { p.setVideoSurfaceView(it) }
        player = p
        multiInput = needMulti
        return p
    }

    fun attach(view: SurfaceView) {
        surface = view
        player?.setVideoSurfaceView(view)
    }

    fun detach(view: SurfaceView) {
        if (surface === view) {
            player?.clearVideoSurfaceView(view)
            surface = null
        }
    }

    /**
     * Ładuje projekt do odtwarzacza (tylko gdy coś się zmieniło). Podgląd liczy efekty
     * w niższej rozdzielczości (maks. 720 p) – płynniej, a wygląd jest ten sam.
     */
    fun load(project: Project, seq: SequenceSettings, positionMs: Long, force: Boolean = false) {
        if (!force && project == lastProject && seq == lastSeq) return
        lastProject = project
        lastSeq = seq
        val previewSeq = seq.copy(shortSide = minOf(seq.shortSide, 720))
        val built = try {
            CompositionFactory.build(project, previewSeq, multiLayer = !overlaysUnsupported, includeTitles = false)
        } catch (e: Exception) {
            Log.w(TAG, "Nie można zbudować kompozycji", e)
            message = "Błąd kompozycji: ${e.message}"
            null
        }
        if (built == null) {
            player?.let { it.pause(); it.removeListener(listener); it.release() }
            player = null
            ready = false
            isPlaying = false
            builtDurationMs = 0
            return
        }
        val wasPlaying = player?.isPlaying == true
        val p = ensurePlayer(built.videoSequences > 1)
        builtDurationMs = built.durationMs
        try {
            p.setComposition(built.composition, positionMs.coerceIn(0, maxOf(0, built.durationMs - 1)))
            p.prepare()
            p.playWhenReady = wasPlaying
        } catch (e: Exception) {
            Log.w(TAG, "setComposition", e)
            message = "Błąd podglądu: ${e.message}"
        }
    }

    fun play() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED || currentPositionMs() >= builtDurationMs - 30) p.seekTo(0)
        p.play()
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(ms: Long) {
        val p = player ?: return
        p.seekTo(ms.coerceIn(0, maxOf(0, builtDurationMs - 1)))
    }

    fun setScrubbing(on: Boolean) {
        runCatching { player?.setScrubbingModeEnabled(on) }
    }

    fun currentPositionMs(): Long = player?.currentPosition ?: 0L

    fun release() {
        player?.removeListener(listener)
        player?.release()
        player = null
        lastProject = null
    }

    private companion object {
        const val TAG = "PreviewEngine"
    }
}
