package com.openclipflow.app.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Przenikanie między sąsiednimi klipami głównej ścieżki (montaż A/B).
 *
 * Oś czasu pozostaje bez nakładania: punkt cięcia zostaje tam, gdzie był. Na czas przenikania
 * następny klip zaczyna się wcześniej (zapas materiału przed jego wejściem – „pre-roll”),
 * a poprzedni trwa dłużej (zapas za jego wyjściem – „post-roll”). Klipy na przemian trafiają do
 * dwóch warstw, a krycie górnej warstwy płynnie rośnie albo maleje w oknie przenikania.
 * Zdjęcia mają nieograniczony zapas; dla wideo przenikanie jest skracane do dostępnego materiału.
 */
object Dissolves {
    /** Klipy uznaje się za sąsiednie, gdy dzieli je najwyżej tyle (ok. jedna klatka). */
    const val ADJACENT_MS = 45L
    const val MIN_MS = 100L
    val PRESETS = listOf(0L, 300L, 500L, 800L, 1000L, 1500L, 2000L)

    /** Połączenie dwóch klipów z przenikaniem: [pre] ms przed cięciem, [post] ms po cięciu. */
    data class Join(val prev: Clip, val next: Clip, val pre: Long, val post: Long) {
        val total: Long get() = pre + post
        val windowStart: Long get() = next.startMs - pre
        val windowEnd: Long get() = next.startMs + post
    }

    /** Fragment warstwy do zbudowania sekwencji: klip z poszerzonym zakresem i oknami przenikania. */
    data class Item(val source: Clip, val render: Clip, val layer: Int, val joinIn: Join?, val joinOut: Join?) {
        /** Krycie warstwy w chwili [t] (1 = pełny obraz). */
        fun alpha(t: Long): Float {
            var a = 1f
            joinIn?.let { j -> if (t < j.windowEnd) a = min(a, ((t - j.windowStart).toFloat() / j.total).coerceIn(0f, 1f)) }
            joinOut?.let { j -> if (t > j.windowStart) a = min(a, (1f - (t - j.windowStart).toFloat() / j.total).coerceIn(0f, 1f)) }
            return a
        }
    }

    private fun stillOf(p: Project, c: Clip) = p.asset(c.assetId)?.isStill == true

    /** Zapas materiału przed wejściem klipu (w czasie osi). */
    fun preRollAvailable(p: Project, c: Clip): Long =
        if (stillOf(p, c)) Long.MAX_VALUE / 4 else (c.sourceInMs / c.speed).roundToLong().coerceAtLeast(0)

    /** Zapas materiału za wyjściem klipu (w czasie osi). */
    fun postRollAvailable(p: Project, c: Clip): Long {
        if (stillOf(p, c)) return Long.MAX_VALUE / 4
        val dur = p.asset(c.assetId)?.durationMs ?: return 0
        return ((dur - c.sourceOutMs) / c.speed).roundToLong().coerceAtLeast(0)
    }

    /** Poprzedni klip stykający się z [c] na tej samej ścieżce. */
    fun previousAdjacent(p: Project, c: Clip): Clip? =
        p.clipsOn(c.trackId).lastOrNull { it.id != c.id && it.endMs <= c.startMs + ADJACENT_MS && c.startMs - it.endMs <= ADJACENT_MS }

    fun nextAdjacent(p: Project, c: Clip): Clip? =
        p.clipsOn(c.trackId).firstOrNull { it.id != c.id && it.startMs >= c.endMs - ADJACENT_MS && it.startMs - c.endMs <= ADJACENT_MS }

    /**
     * Faktyczne przenikanie dla klipu [next]: ograniczone połową długości obu klipów oraz
     * zapasem materiału. Najpierw wykorzystuje pre-roll następnego, resztę – post-roll poprzedniego.
     */
    fun join(p: Project, next: Clip): Join? {
        if (next.dissolveInMs < MIN_MS || next.trackId != p.mainTrack.id) return null
        val prev = previousAdjacent(p, next) ?: return null
        val wanted = minOf(next.dissolveInMs, prev.durationMs / 2, next.durationMs / 2)
        val pre = min(wanted, preRollAvailable(p, next))
        val post = min(wanted - pre, postRollAvailable(p, prev))
        if (pre + post < MIN_MS) return null
        return Join(prev, next, pre, post)
    }

    /** Ile przenikania da się uzyskać między tymi klipami (do podpowiedzi w interfejsie). */
    fun maxPossible(p: Project, next: Clip): Long {
        val prev = previousAdjacent(p, next) ?: return 0
        val half = min(prev.durationMs / 2, next.durationMs / 2)
        val avail = min(Long.MAX_VALUE / 4, preRollAvailable(p, next) + postRollAvailable(p, prev))
        return min(half, avail)
    }

    fun joins(p: Project): List<Join> = p.clipsOn(p.mainTrack.id).mapNotNull { join(p, it) }

    fun hasAny(p: Project): Boolean = p.clipsOn(p.mainTrack.id).any { it.dissolveInMs >= MIN_MS } && joins(p).isNotEmpty()

    /**
     * Klipy głównej ścieżki rozłożone na dwie warstwy (0 = dolna, 1 = górna) z poszerzonymi
     * zakresami. Klip łączący się przenikaniem trafia zawsze na inną warstwę niż poprzedni.
     */
    fun layers(p: Project): List<Item> {
        val main = p.clipsOn(p.mainTrack.id)
        val joinsByNext = joins(p).associateBy { it.next.id }
        val joinsByPrev = joinsByNext.values.associateBy { it.prev.id }
        var layer = 0
        var prevLayer = 1
        return main.map { c ->
            val jIn = joinsByNext[c.id]
            val jOut = joinsByPrev[c.id]
            // Klipy bez przenikania leżą na dolnej warstwie; łańcuch przenikań naprzemiennie.
            layer = if (jIn != null) 1 - prevLayer else 0
            prevLayer = layer
            val start = c.startMs - (jIn?.pre ?: 0)
            val end = c.endMs + (jOut?.post ?: 0)
            val still = stillOf(p, c)
            val render = c.copy(
                startMs = start,
                durationMs = end - start,
                sourceInMs = if (still) c.sourceInMs else max(0L, c.sourceInMs - ((jIn?.pre ?: 0) * c.speed).roundToLong()),
                transitionIn = if (jIn != null) Transition.NONE else c.transitionIn,
                transitionOut = if (jOut != null) Transition.NONE else c.transitionOut,
                audioFadeInMs = max(c.audioFadeInMs, jIn?.total ?: 0),
                audioFadeOutMs = max(c.audioFadeOutMs, jOut?.total ?: 0),
            )
            Item(c, render, layer, jIn, jOut)
        }
    }

    /** Elementy jednej warstwy jako ciągła sekwencja (przerwy + klipy) dopełniona do [padToMs]. */
    fun segments(items: List<Item>, layer: Int, padToMs: Long): List<Pair<Long, Item?>> {
        val out = mutableListOf<Pair<Long, Item?>>()
        var cursor = 0L
        items.filter { it.layer == layer }.sortedBy { it.render.startMs }.forEach { it ->
            val r = it.render
            if (r.startMs < cursor) return@forEach // nie powinno się zdarzyć – zabezpieczenie
            if (r.startMs > cursor) out += (r.startMs - cursor) to null
            out += r.durationMs to it
            cursor = r.endMs
        }
        if (padToMs > cursor) out += (padToMs - cursor) to null
        return out
    }

    /** Ustawia przenikanie na wszystkich cięciach głównej ścieżki. */
    fun applyToAll(p: Project, ms: Long): Project {
        val main = p.mainTrack.id
        return p.copy(clips = p.clips.map { c -> if (c.trackId == main && previousAdjacent(p, c) != null) c.copy(dissolveInMs = ms) else c })
    }
}
