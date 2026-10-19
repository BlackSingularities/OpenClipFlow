@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.openclipflow.app.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.effect.CanvasOverlay
import com.openclipflow.app.model.TitleAlign
import com.openclipflow.app.model.TitleAnimation
import com.openclipflow.app.model.TitleClip
import com.openclipflow.app.model.TitleStyle
import kotlin.math.max
import kotlin.math.min

/**
 * Rysuje napisy na płótnie kadru. Ten sam kod działa w podglądzie (nad obrazem odtwarzacza)
 * i w eksporcie (nakładka GPU), więc wynik wygląda dokładnie jak na ekranie.
 */
object TitleRenderer {
    private val fill = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val stroke = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val box = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    /** Rysuje wszystkie napisy widoczne w chwili [timeMs]. */
    @Synchronized
    fun drawAll(canvas: Canvas, titles: List<TitleClip>, timeMs: Long, width: Int, height: Int, selectedId: String? = null) {
        titles.forEach { t ->
            if (timeMs >= t.startMs && timeMs < t.endMs) draw(canvas, t, timeMs - t.startMs, width, height)
        }
    }

    /** Prostokąt napisu w pikselach kadru (do zaznaczania i przeciągania w podglądzie). */
    @Synchronized
    fun bounds(t: TitleClip, width: Int, height: Int): RectF {
        val layout = layout(t, t.text, width, height)
        val pad = padding(t, height)
        val cx = t.x * width
        val cy = t.y * height
        val w = layout.width.toFloat()
        val h = layout.height.toFloat()
        return RectF(cx - w / 2 - pad, cy - h / 2 - pad, cx + w / 2 + pad, cy + h / 2 + pad)
    }

    private fun padding(t: TitleClip, height: Int): Float {
        val ts = t.size * height
        return when (t.style) {
            TitleStyle.BOX, TitleStyle.SUBTITLE -> ts * 0.35f
            TitleStyle.LOWER_THIRD -> ts * 0.5f
            else -> ts * 0.1f
        }
    }

    private fun configure(t: TitleClip, height: Int) {
        val ts = max(4f, t.size * height)
        val style = when {
            t.bold && t.italic -> Typeface.BOLD_ITALIC
            t.bold -> Typeface.BOLD
            t.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val face = Typeface.create(Typeface.SANS_SERIF, style)
        fill.textSize = ts
        fill.typeface = face
        fill.color = t.color
        fill.clearShadowLayer()
        if (t.style == TitleStyle.SHADOW) fill.setShadowLayer(ts * 0.12f, 0f, ts * 0.04f, Color.argb(200, 0, 0, 0))
        stroke.textSize = ts
        stroke.typeface = face
        stroke.style = Paint.Style.STROKE
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.strokeWidth = ts * 0.14f
        stroke.color = t.accent
    }

    private fun layout(t: TitleClip, text: String, width: Int, height: Int): StaticLayout {
        configure(t, height)
        val maxW = (width * 0.9f).toInt().coerceAtLeast(10)
        val natural = text.lines().maxOfOrNull { fill.measureText(it) }?.toInt()?.plus(2) ?: 1
        val w = min(maxW, max(1, natural))
        val align = when (t.align) {
            TitleAlign.START -> Layout.Alignment.ALIGN_NORMAL
            TitleAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            TitleAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, fill, w)
            .setAlignment(align)
            .setLineSpacing(0f, 1.05f)
            .setIncludePad(false)
            .build()
    }

    private fun draw(canvas: Canvas, t: TitleClip, localMs: Long, width: Int, height: Int) {
        if (t.text.isBlank()) return
        val anim = max(1L, min(t.animationMs, t.durationMs / 2))
        val inP = VideoEffects.ease(localMs.toFloat() / anim)
        val outP = VideoEffects.ease((t.durationMs - localMs).toFloat() / anim)
        val p = min(inP, outP)
        var alpha = 1f
        var dy = 0f
        var scale = 1f
        var text = t.text
        when (t.animation) {
            TitleAnimation.NONE -> Unit
            TitleAnimation.FADE -> alpha = p
            TitleAnimation.SLIDE_UP -> { alpha = p; dy = (1f - p) * t.size * height * 1.2f }
            TitleAnimation.POP -> { alpha = p; scale = 0.6f + 0.4f * p + if (p in 0.6f..0.99f) (1f - p) * 0.25f else 0f }
            TitleAnimation.TYPEWRITER -> {
                val reveal = min(t.durationMs * 0.6f, t.text.length * 55f).coerceAtLeast(1f)
                val n = (t.text.length * (localMs / reveal)).toInt().coerceIn(0, t.text.length)
                text = t.text.take(n)
                alpha = outP
                if (text.isEmpty()) return
            }
        }
        if (alpha <= 0.01f) return
        val layout = layout(t, text, width, height)
        val full = if (text == t.text) layout else layout(t, t.text, width, height).also { configure(t, height) }
        val pad = padding(t, height)
        val cx = t.x * width
        val cy = t.y * height + dy
        val w = full.width.toFloat()
        val h = full.height.toFloat()
        val save = canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        canvas.translate(-w / 2, -h / 2)
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        when (t.style) {
            TitleStyle.BOX, TitleStyle.SUBTITLE, TitleStyle.LOWER_THIRD -> {
                box.color = t.accent
                box.alpha = (Color.alpha(t.accent) * alpha).toInt()
                val r = if (t.style == TitleStyle.LOWER_THIRD) pad * 0.25f else pad * 0.8f
                rect.set(-pad * (if (t.style == TitleStyle.LOWER_THIRD) 1.6f else 1f), -pad, w + pad * (if (t.style == TitleStyle.LOWER_THIRD) 1.6f else 1f), h + pad)
                canvas.drawRoundRect(rect, r, r, box)
                if (t.style == TitleStyle.LOWER_THIRD) {
                    // Cienka linia akcentu nad belką.
                    box.color = t.color
                    box.alpha = a
                    rect.set(rect.left, rect.top - pad * 0.35f, rect.left + rect.width() * 0.35f, rect.top - pad * 0.12f)
                    canvas.drawRect(rect, box)
                }
            }
            TitleStyle.OUTLINE -> {
                stroke.alpha = (Color.alpha(t.accent) * alpha).toInt()
                StaticLayout.Builder.obtain(text, 0, text.length, stroke, layout.width)
                    .setAlignment(layout.alignment)
                    .setLineSpacing(0f, 1.05f)
                    .setIncludePad(false)
                    .build()
                    .draw(canvas)
            }
            else -> Unit
        }
        fill.alpha = (Color.alpha(t.color) * alpha).toInt()
        layout.draw(canvas)
        canvas.restoreToCount(save)
    }
}

/** Nakładka eksportu: wszystkie napisy projektu rysowane na pełnym kadrze. */
class TitlesOverlay(private val titles: List<TitleClip>) : CanvasOverlay(true) {
    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        TitleRenderer.drawAll(canvas, titles, presentationTimeUs / 1000, canvas.width, canvas.height)
    }
}
