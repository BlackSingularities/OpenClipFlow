@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.openclipflow.app.engine

import android.graphics.Matrix
import androidx.media3.common.Effect
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import com.openclipflow.app.model.Clip
import com.openclipflow.app.model.ColorAdjust
import com.openclipflow.app.model.FilterPreset
import com.openclipflow.app.model.KenBurns
import com.openclipflow.app.model.Transition
import kotlin.math.max
import kotlin.math.min

/**
 * Efekty GPU dla klipów: filtry kolorów, korekcja, rozmycie, kadrowanie/transformacja,
 * animacja Ken Burns i przejścia (zanikanie przez czerń/biel, zoom, wjazd).
 *
 * Znaczniki czasu klatek w efektach to czas sekwencji (Media3 dodaje przesunięcie elementu),
 * dlatego efekty znają położenie klipu na osi ([ClipTiming]) i liczą czas lokalny.
 */
data class ClipTiming(val startUs: Long, val durationUs: Long) {
    /** Czas od początku klipu; gdy silnik poda czas lokalny (mniejszy niż start) – używa go wprost. */
    fun local(presentationTimeUs: Long): Long {
        val t = if (presentationTimeUs + 50_000 < startUs) presentationTimeUs else presentationTimeUs - startUs
        return t.coerceIn(0, durationUs)
    }

    /** Postęp 0..1 w obrębie klipu. */
    fun progress(presentationTimeUs: Long): Float =
        if (durationUs <= 0) 0f else local(presentationTimeUs).toFloat() / durationUs
}

object VideoEffects {

    /**
     * Pełna lista efektów obrazu dla klipu. [outW]×[outH] – rozdzielczość sekwencji;
     * [overlay] – klip ścieżki nakładek (przejścia robi tam krycie w kompozytorze).
     */
    fun forClip(clip: Clip, timing: ClipTiming, outW: Int, outH: Int, overlay: Boolean): List<Effect> = buildList {
        filterMatrix(clip.filter, clip.filterStrength)?.let { m -> add(StaticRgb(m)) }
        addAll(colorEffects(clip.color))
        // Klip najpierw trafia do swojego obszaru kadru (cały kadr albo część – układy ekranu),
        // tam działa ruch i kadrowanie, a na końcu obszar jest umieszczany w kadrze sekwencji.
        val region = clip.transform.region
        val rw = evenPx(outW * region.w)
        val rh = evenPx(outH * region.h)
        add(
            Presentation.createForWidthAndHeight(
                rw,
                rh,
                if (clip.transform.fill) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT,
            ),
        )
        val needsMotion = !clip.transform.copy(region = com.openclipflow.app.model.Frame.FULL, fill = false).isIdentity ||
            clip.transitionIn == Transition.ZOOM || clip.transitionOut == Transition.ZOOM ||
            clip.transitionIn == Transition.SLIDE || clip.transitionOut == Transition.SLIDE
        if (needsMotion) add(ClipMotion(clip, timing, rw.toFloat() / rh))
        if (!overlay && (clip.transitionIn.isFade() || clip.transitionOut.isFade())) add(FadeMatrix(clip, timing))
        if (!region.isFull) add(RegionPlacement(region, outW, outH))
    }

    fun evenPx(v: Float): Int = (Math.round(v / 2f) * 2).coerceAtLeast(2)

    /**
     * Obrys klipu w kadrze sekwencji (współrzędne NDC, 4 narożniki) – do ramki zaznaczenia
     * w podglądzie. Uwzględnia obszar układu, ruch i obrót.
     */
    fun outline(clip: Clip, localUs: Long, durationUs: Long, outAspect: Float): FloatArray {
        val r = clip.transform.region
        val regionAspect = outAspect * r.w / r.h
        val m = ClipMotion.compute(clip, localUs, durationUs, regionAspect)
        val pts = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)
        m.mapPoints(pts)
        RegionPlacement.matrix(r).mapPoints(pts)
        return pts
    }

    private fun Transition.isFade() = this == Transition.FADE_BLACK || this == Transition.FADE_WHITE

    fun colorEffects(c: ColorAdjust): List<Effect> = buildList {
        if (c.temperature != 0f) {
            val t = c.temperature.coerceIn(-1f, 1f) * 0.18f
            add(StaticRgb(scaleMatrix(1f + t, 1f + t * 0.15f, 1f - t)))
        }
        if (c.brightness != 0f) add(StaticRgb(offsetMatrix(c.brightness.coerceIn(-1f, 1f) * 0.4f)))
        if (c.contrast != 0f) add(Contrast(c.contrast.coerceIn(-1f, 1f) * 0.8f))
        if (c.saturation != 0f || c.hue != 0f) {
            add(
                HslAdjustment.Builder()
                    .adjustHue(c.hue)
                    .adjustSaturation((c.saturation * 100f).coerceIn(-100f, 100f))
                    .build(),
            )
        }
        if (c.blur > 0.01f) add(GaussianBlur(c.blur.coerceIn(0f, 1f) * 20f))
    }

    // ------------------------------------------------------------ macierze kolorów

    /** Macierz 3×3 (wierszami) + przesunięcie → macierz 4×4 OpenGL (kolumnami). */
    fun rgb(m: FloatArray, offset: FloatArray = floatArrayOf(0f, 0f, 0f)): FloatArray {
        val out = FloatArray(16)
        for (row in 0 until 3) for (col in 0 until 3) out[col * 4 + row] = m[row * 3 + col]
        out[12] = offset[0]
        out[13] = offset[1]
        out[14] = offset[2]
        out[15] = 1f
        return out
    }

    fun identity3() = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    fun scaleMatrix(r: Float, g: Float, b: Float) = rgb(floatArrayOf(r, 0f, 0f, 0f, g, 0f, 0f, 0f, b))

    fun offsetMatrix(v: Float) = rgb(identity3(), floatArrayOf(v, v, v))

    private fun saturation3(s: Float): FloatArray {
        // Wagi luminancji BT.709.
        val lr = 0.2126f * (1 - s)
        val lg = 0.7152f * (1 - s)
        val lb = 0.0722f * (1 - s)
        return floatArrayOf(lr + s, lg, lb, lr, lg + s, lb, lr, lg, lb + s)
    }

    private fun mul3(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(9)
        for (r in 0 until 3) for (c in 0 until 3) {
            o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
        }
        return o
    }

    private fun lerp3(a: FloatArray, b: FloatArray, t: Float) = FloatArray(9) { a[it] + (b[it] - a[it]) * t }

    /** Macierz gotowego filtra wymieszana z tożsamością wg [strength]; null = brak filtra. */
    fun filterMatrix(preset: FilterPreset, strength: Float): FloatArray? {
        if (preset == FilterPreset.NONE || strength <= 0.001f) return null
        val (m, off) = when (preset) {
            FilterPreset.NONE -> return null
            FilterPreset.VIVID -> saturation3(1.45f) to floatArrayOf(0f, 0f, 0f)
            FilterPreset.WARM -> mul3(floatArrayOf(1.12f, 0f, 0f, 0f, 1.02f, 0f, 0f, 0f, 0.86f), saturation3(1.1f)) to floatArrayOf(0.01f, 0f, 0f)
            FilterPreset.COOL -> mul3(floatArrayOf(0.9f, 0f, 0f, 0f, 1.0f, 0f, 0f, 0f, 1.14f), saturation3(1.05f)) to floatArrayOf(0f, 0f, 0.01f)
            FilterPreset.TEAL_ORANGE -> mul3(
                floatArrayOf(1.10f, 0.05f, -0.10f, -0.02f, 1.02f, 0.02f, -0.12f, 0.12f, 1.05f),
                saturation3(1.15f),
            ) to floatArrayOf(0f, 0f, 0.01f)
            FilterPreset.VINTAGE -> mul3(floatArrayOf(0.95f, 0.08f, 0f, 0.05f, 0.88f, 0.05f, 0.02f, 0.06f, 0.72f), saturation3(0.75f)) to floatArrayOf(0.04f, 0.03f, 0.02f)
            FilterPreset.SEPIA -> floatArrayOf(0.393f, 0.769f, 0.189f, 0.349f, 0.686f, 0.168f, 0.272f, 0.534f, 0.131f) to floatArrayOf(0f, 0f, 0f)
            FilterPreset.FADE -> lerp3(identity3(), saturation3(0.6f), 1f).map { it * 0.85f }.toFloatArray() to floatArrayOf(0.07f, 0.07f, 0.08f)
            FilterPreset.MONO -> saturation3(0f) to floatArrayOf(0f, 0f, 0f)
            FilterPreset.NOIR -> saturation3(0f).map { it * 1.25f }.toFloatArray() to floatArrayOf(-0.08f, -0.08f, -0.08f)
        }
        val s = strength.coerceIn(0f, 1f)
        return rgb(lerp3(identity3(), m, s), FloatArray(3) { off[it] * s })
    }

    /** Krzywa wygładzona (smoothstep) – łagodny początek i koniec animacji. */
    fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }

    /**
     * Współczynnik przejścia 0..1 (1 = pełny obraz) na początku i końcu klipu;
     * [inT]/[outT] – czy dane przejście jest aktywne.
     */
    fun transitionLevel(localUs: Long, durationUs: Long, transitionUs: Long, inT: Boolean, outT: Boolean): Float {
        val len = min(transitionUs, durationUs / 2).coerceAtLeast(1)
        var level = 1f
        if (inT && localUs < len) level = min(level, localUs.toFloat() / len)
        if (outT && localUs > durationUs - len) level = min(level, (durationUs - localUs).toFloat() / len)
        return ease(level)
    }
}

/**
 * Umieszcza klatkę obszaru (np. połowę ekranu) w pełnym kadrze sekwencji: wyjście ma rozmiar
 * kadru, a obraz trafia w prostokąt [region]; reszta pozostaje przezroczysta.
 */
class RegionPlacement(private val region: com.openclipflow.app.model.Frame, private val outW: Int, private val outH: Int) : MatrixTransformation {
    private val m = matrix(region)

    override fun configure(inputWidth: Int, inputHeight: Int): androidx.media3.common.util.Size = androidx.media3.common.util.Size(outW, outH)

    override fun getMatrix(presentationTimeUs: Long): Matrix = m

    companion object {
        fun matrix(r: com.openclipflow.app.model.Frame): Matrix = Matrix().apply {
            postScale(r.w, r.h)
            postTranslate((r.x + r.w / 2f) * 2f - 1f, 1f - (r.y + r.h / 2f) * 2f)
        }
    }
}

/** Stała macierz koloru. */
class StaticRgb(private val matrix: FloatArray) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
}

/** Zanikanie przez czerń lub biel na początku/końcu klipu. */
class FadeMatrix(private val clip: Clip, private val timing: ClipTiming) : RgbMatrix {
    private val out = FloatArray(16)

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val local = timing.local(presentationTimeUs)
        val inFade = clip.transitionIn == Transition.FADE_BLACK || clip.transitionIn == Transition.FADE_WHITE
        val outFade = clip.transitionOut == Transition.FADE_BLACK || clip.transitionOut == Transition.FADE_WHITE
        val len = min(clip.transitionMs * 1000, timing.durationUs / 2).coerceAtLeast(1)
        val (k, white) = when {
            inFade && local < len -> VideoEffects.ease(local.toFloat() / len) to (clip.transitionIn == Transition.FADE_WHITE)
            outFade && local > timing.durationUs - len -> VideoEffects.ease((timing.durationUs - local).toFloat() / len) to (clip.transitionOut == Transition.FADE_WHITE)
            else -> 1f to false
        }
        out.fill(0f)
        out[0] = k
        out[5] = k
        out[10] = k
        out[15] = 1f
        val add = if (white) 1f - k else 0f
        out[12] = add
        out[13] = add
        out[14] = add
        return out
    }
}

/**
 * Transformacja klipu w kadrze sekwencji (współrzędne NDC -1..1): skala, obrót z poprawką
 * proporcji, przesunięcie, odbicia, Ken Burns i przejścia ruchu (zoom, wjazd).
 */
class ClipMotion(private val clip: Clip, private val timing: ClipTiming, private val aspect: Float) : MatrixTransformation {
    private val m = Matrix()

    override fun getMatrix(presentationTimeUs: Long): Matrix = compute(clip, timing.local(presentationTimeUs), timing.durationUs, aspect, m)

    companion object {
        fun compute(clip: Clip, localUs: Long, durationUs: Long, aspect: Float, m: Matrix = Matrix()): Matrix {
            val t = clip.transform
            val p = if (durationUs <= 0) 0f else (localUs.toFloat() / durationUs).coerceIn(0f, 1f)
            var scale = t.scale
            var dx = t.offsetX * 2f
            val dy = -t.offsetY * 2f
            when (t.kenBurns) {
                KenBurns.NONE -> Unit
                KenBurns.ZOOM_IN -> scale *= 1f + 0.18f * p
                KenBurns.ZOOM_OUT -> scale *= 1.18f - 0.18f * p
                KenBurns.PAN_LEFT -> { scale *= 1.15f; dx += 0.13f - 0.26f * p }
                KenBurns.PAN_RIGHT -> { scale *= 1.15f; dx += -0.13f + 0.26f * p }
            }
            val transUs = clip.transitionMs * 1000
            val len = max(1L, min(transUs, durationUs / 2))
            if (clip.transitionIn == Transition.ZOOM && localUs < len) scale *= 1f + 0.35f * (1f - VideoEffects.ease(localUs.toFloat() / len))
            if (clip.transitionOut == Transition.ZOOM && localUs > durationUs - len) scale *= 1f + 0.35f * (1f - VideoEffects.ease((durationUs - localUs).toFloat() / len))
            if (clip.transitionIn == Transition.SLIDE && localUs < len) dx += 2f * (1f - VideoEffects.ease(localUs.toFloat() / len))
            if (clip.transitionOut == Transition.SLIDE && localUs > durationUs - len) dx -= 2f * (1f - VideoEffects.ease((durationUs - localUs).toFloat() / len))

            m.reset()
            // Kolejność (od ostatniej): odbicie → skala → obrót w proporcjach kadru → przesunięcie.
            m.postScale(if (t.flipH) -scale else scale, if (t.flipV) -scale else scale)
            if (t.rotation != 0f) {
                m.postScale(aspect, 1f)
                m.postRotate(-t.rotation)
                m.postScale(1f / aspect, 1f)
            }
            m.postTranslate(dx, dy)
            return m
        }
    }
}
