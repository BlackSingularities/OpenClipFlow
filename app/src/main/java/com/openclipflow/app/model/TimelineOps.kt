package com.openclipflow.app.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Operacje montażowe na projekcie – czyste funkcje (bez Androida), więc w pełni testowalne.
 * Każda zwraca nowy [Project] albo null, gdy operacja jest niemożliwa.
 */
object TimelineOps {
    const val MIN_CLIP_MS = 100L
    const val IMAGE_DEFAULT_MS = 4000L
    const val TITLE_DEFAULT_MS = 3000L
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 4f

    // ---------------------------------------------------------------- przyciąganie

    fun snap(value: Long, targets: Collection<Long>, thresholdMs: Long): Long {
        val nearest = targets.minByOrNull { abs(it - value) } ?: return value
        return if (abs(nearest - value) <= thresholdMs) nearest else value
    }

    /** Punkty przyciągania: krawędzie klipów i napisów, znaczniki, głowica i zero. */
    fun snapTargets(p: Project, exclude: Set<String>, playheadMs: Long): List<Long> = buildList {
        add(0L)
        add(playheadMs)
        p.clips.filter { it.id !in exclude }.forEach { add(it.startMs); add(it.endMs) }
        p.titles.filter { it.id !in exclude }.forEach { add(it.startMs); add(it.endMs) }
        p.markers.forEach { add(it.timeMs) }
    }

    // ---------------------------------------------------------------- zgodność ścieżek

    /** Czy materiał może leżeć na danej ścieżce. Wideo na ścieżce audio = sam dźwięk z nagrania. */
    fun accepts(track: Track, asset: MediaAsset): Boolean = when (track.kind) {
        TrackKind.VIDEO -> asset.kind != MediaKind.AUDIO
        TrackKind.AUDIO -> asset.kind == MediaKind.AUDIO || (asset.kind == MediaKind.VIDEO && asset.hasAudio)
    }

    fun defaultDuration(asset: MediaAsset): Long =
        if (asset.isStill) IMAGE_DEFAULT_MS else max(MIN_CLIP_MS, asset.durationMs)

    /** Czy przedział [start, end) jest wolny na ścieżce (z pominięciem [ignore]). */
    fun isFree(p: Project, trackId: String, start: Long, end: Long, ignore: Set<String> = emptySet()): Boolean =
        p.clips.none { it.trackId == trackId && it.id !in ignore && it.startMs < end && start < it.endMs }

    /**
     * Najbliższe wolne miejsce o długości [duration] na ścieżce, możliwie blisko [desired]
     * (przed albo za kolidującymi klipami). Zwraca null, gdy nie da się go znaleźć.
     */
    fun nearestFreeStart(p: Project, trackId: String, desired: Long, duration: Long, ignore: Set<String> = emptySet()): Long? {
        val want = max(0L, desired)
        if (isFree(p, trackId, want, want + duration, ignore)) return want
        val others = p.clips.filter { it.trackId == trackId && it.id !in ignore }.sortedBy { it.startMs }
        val candidates = buildList {
            others.forEach { c ->
                add(c.endMs)
                add(c.startMs - duration)
            }
        }.filter { it >= 0 && isFree(p, trackId, it, it + duration, ignore) }
        return candidates.minByOrNull { abs(it - want) }
    }

    // ---------------------------------------------------------------- dodawanie

    /**
     * Wstawia materiał w miejscu głowicy. Na głównej ścieżce – wstawienie z przesunięciem
     * (klip pod głowicą zostaje rozcięty, reszta odsuwa się w prawo), na pozostałych – najbliższe
     * wolne miejsce, w razie potrzeby na innej ścieżce tego samego rodzaju albo nowej.
     */
    fun insertAsset(p: Project, asset: MediaAsset, atMs: Long, preferredTrack: String? = null): Pair<Project, Clip>? {
        val duration = defaultDuration(asset)
        val wanted = preferredTrack?.let { p.track(it) }?.takeIf { accepts(it, asset) && !it.locked }
        val track = wanted ?: when (asset.kind) {
            MediaKind.AUDIO -> p.audioTracks.firstOrNull { !it.locked }
            else -> p.mainTrack.takeIf { !it.locked } ?: p.videoTracks.firstOrNull { !it.locked }
        } ?: return null
        if (!accepts(track, asset)) return null
        if (track.id == p.mainTrack.id) {
            var base = p
            val at = max(0L, atMs)
            val under = base.clips.firstOrNull { it.trackId == track.id && it.startMs < at && at < it.endMs }
            val insertAt = if (under != null && at - under.startMs >= MIN_CLIP_MS && under.endMs - at >= MIN_CLIP_MS) {
                base = split(base, under.id, at) ?: base
                at
            } else if (under != null) {
                if (at - under.startMs < under.endMs - at) under.startMs else under.endMs
            } else {
                // Bez dziur na początku: klip trafia tuż za ostatni klip przed głowicą.
                val prevEnd = base.clipsOn(track.id).filter { it.endMs <= at }.maxOfOrNull { it.endMs } ?: 0L
                val nextStart = base.clipsOn(track.id).filter { it.startMs >= at }.minOfOrNull { it.startMs }
                if (nextStart == null) prevEnd else at
            }
            val shifted = base.clips.map {
                if (it.trackId == track.id && it.startMs >= insertAt) it.copy(startMs = it.startMs + duration) else it
            }
            val clip = Clip(assetId = asset.id, trackId = track.id, startMs = insertAt, durationMs = duration)
            return base.copy(clips = shifted + clip) to clip
        }
        val sameKind = listOf(track) + p.tracks.filter { it.kind == track.kind && it.id != track.id && !it.locked && accepts(it, asset) }
        for (t in sameKind) {
            if (isFree(p, t.id, atMs, atMs + duration)) {
                val clip = Clip(assetId = asset.id, trackId = t.id, startMs = max(0L, atMs), durationMs = duration)
                return p.copy(clips = p.clips + clip) to clip
            }
        }
        val newTrack = Track(kind = track.kind, name = nextTrackName(p, track.kind))
        val clip = Clip(assetId = asset.id, trackId = newTrack.id, startMs = max(0L, atMs), durationMs = duration)
        return p.copy(tracks = p.tracks + newTrack, clips = p.clips + clip) to clip
    }

    /** Dokleja materiał na koniec głównej ścieżki (albo pierwszej audio). */
    fun appendAsset(p: Project, asset: MediaAsset): Pair<Project, Clip>? {
        val track = if (asset.kind == MediaKind.AUDIO) p.audioTracks.firstOrNull() else p.mainTrack
        track ?: return null
        val end = p.clipsOn(track.id).maxOfOrNull { it.endMs } ?: 0L
        val clip = Clip(assetId = asset.id, trackId = track.id, startMs = end, durationMs = defaultDuration(asset))
        return p.copy(clips = p.clips + clip) to clip
    }

    fun nextTrackName(p: Project, kind: TrackKind): String {
        val base = if (kind == TrackKind.VIDEO) "Nakładka" else "Audio"
        val n = p.tracks.count { it.kind == kind } + (if (kind == TrackKind.VIDEO) 0 else 1)
        return "$base $n"
    }

    fun addTrack(p: Project, kind: TrackKind): Project =
        p.copy(tracks = p.tracks + Track(kind = kind, name = nextTrackName(p, kind)))

    /** Usuwa ścieżkę razem z klipami; główna ścieżka obrazu zostaje zawsze. */
    fun removeTrack(p: Project, trackId: String): Project? {
        if (trackId == p.mainTrack.id) return null
        val t = p.track(trackId) ?: return null
        if (t.kind == TrackKind.AUDIO && p.audioTracks.size <= 1) return null
        return p.copy(tracks = p.tracks - t, clips = p.clips.filter { it.trackId != trackId })
    }

    // ---------------------------------------------------------------- cięcie i przycinanie

    fun split(p: Project, clipId: String, atMs: Long): Project? {
        val c = p.clip(clipId) ?: return null
        val offset = atMs - c.startMs
        if (offset < MIN_CLIP_MS || c.durationMs - offset < MIN_CLIP_MS) return null
        val left = c.copy(durationMs = offset, transitionOut = Transition.NONE, audioFadeOutMs = 0)
        val right = c.copy(
            id = newId(),
            startMs = atMs,
            durationMs = c.durationMs - offset,
            sourceInMs = c.sourceInMs + (offset * c.speed).roundToLong(),
            transitionIn = Transition.NONE,
            audioFadeInMs = 0,
        )
        return p.copy(clips = p.clips.map { if (it.id == clipId) left else it } + right)
    }

    /** Rozcina wszystkie (lub wskazane) klipy przecinane przez głowicę na odblokowanych ścieżkach. */
    fun splitAt(p: Project, atMs: Long, only: Set<String>? = null): Project? {
        val locked = p.tracks.filter { it.locked }.map { it.id }.toSet()
        val targets = p.clips.filter {
            it.trackId !in locked && it.startMs < atMs && atMs < it.endMs && (only == null || it.id in only)
        }
        if (targets.isEmpty()) return null
        var out = p
        targets.forEach { c -> out = split(out, c.id, atMs) ?: out }
        return if (out === p) null else out
    }

    /** Maksymalna długość źródła dostępna od [sourceInMs] (zdjęcia – bez limitu). */
    private fun sourceLimit(p: Project, c: Clip): Long? {
        val a = p.asset(c.assetId) ?: return null
        return if (a.isStill) null else a.durationMs
    }

    /** Przesuwa lewą krawędź klipu (zmienia też punkt wejścia w źródle). */
    fun trimStart(p: Project, clipId: String, newStartMs: Long): Project? {
        val c = p.clip(clipId) ?: return null
        val still = p.asset(c.assetId)?.isStill == true
        val prevEnd = p.clipsOn(c.trackId).filter { it.id != c.id && it.endMs <= c.startMs }.maxOfOrNull { it.endMs } ?: 0L
        // Najdalej w lewo: koniec poprzedniego klipu i początek materiału źródłowego.
        val leftLimit = if (still) prevEnd else max(prevEnd, c.startMs - (c.sourceInMs / c.speed).roundToLong())
        val start = newStartMs.coerceIn(leftLimit, c.endMs - MIN_CLIP_MS)
        val delta = start - c.startMs
        if (delta == 0L) return p
        val sourceIn = if (still) 0L else max(0L, c.sourceInMs + (delta * c.speed).roundToLong())
        val updated = c.copy(startMs = start, durationMs = c.endMs - start, sourceInMs = sourceIn)
        return replace(p, updated)
    }

    /** Przesuwa prawą krawędź klipu. */
    fun trimEnd(p: Project, clipId: String, newEndMs: Long): Project? {
        val c = p.clip(clipId) ?: return null
        val nextStart = p.clipsOn(c.trackId).filter { it.id != c.id && it.startMs >= c.endMs }.minOfOrNull { it.startMs } ?: Long.MAX_VALUE
        val limit = sourceLimit(p, c)
        val sourceMax = if (limit == null) Long.MAX_VALUE else c.startMs + ((limit - c.sourceInMs) / c.speed).roundToLong()
        val end = newEndMs.coerceIn(c.startMs + MIN_CLIP_MS, min(nextStart, sourceMax).coerceAtLeast(c.startMs + MIN_CLIP_MS))
        return replace(p, c.copy(durationMs = end - c.startMs))
    }

    /** Ustawia długość wprost (zdjęcia, napisy wpisane liczbą); przesuwa kolejne klipy na głównej ścieżce. */
    fun setDuration(p: Project, clipId: String, durationMs: Long): Project? {
        val c = p.clip(clipId) ?: return null
        val limit = sourceLimit(p, c)
        val maxDur = if (limit == null) Long.MAX_VALUE else ((limit - c.sourceInMs) / c.speed).roundToLong()
        val d = durationMs.coerceIn(MIN_CLIP_MS, max(MIN_CLIP_MS, maxDur))
        return resizeWithRipple(p, c, d)
    }

    private fun resizeWithRipple(p: Project, c: Clip, newDuration: Long): Project {
        val delta = newDuration - c.durationMs
        val ripple = c.trackId == p.mainTrack.id
        val clips = p.clips.map {
            when {
                it.id == c.id -> it.copy(durationMs = newDuration)
                ripple && it.trackId == c.trackId && it.startMs >= c.endMs -> it.copy(startMs = max(0L, it.startMs + delta))
                else -> it
            }
        }
        var out = p.copy(clips = clips)
        if (!ripple) {
            // Poza główną ścieżką nie nachodzimy na następny klip.
            val next = p.clipsOn(c.trackId).filter { it.id != c.id && it.startMs >= c.endMs }.minOfOrNull { it.startMs }
            if (next != null && c.startMs + newDuration > next) {
                out = replace(p, c.copy(durationMs = next - c.startMs))
            }
        }
        return out
    }

    fun setSpeed(p: Project, clipId: String, speed: Float): Project? {
        val c = p.clip(clipId) ?: return null
        val s = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        val still = p.asset(c.assetId)?.isStill == true
        if (still) return replace(p, c.copy(speed = 1f))
        val sourceDur = c.sourceDurationMs
        val newDuration = max(MIN_CLIP_MS, (sourceDur / s).roundToLong())
        return resizeWithRipple(p, c.copy(speed = s), newDuration).let { out ->
            out.copy(clips = out.clips.map { if (it.id == c.id) it.copy(speed = s) else it })
        }
    }

    // ---------------------------------------------------------------- przesuwanie

    /**
     * Przesuwa klip na nowy czas i (opcjonalnie) ścieżkę. Kolizje rozwiązuje, dosuwając klip
     * do najbliższego wolnego miejsca. Zwraca null, gdy ścieżka nie przyjmuje materiału.
     */
    fun move(p: Project, clipId: String, newStartMs: Long, trackId: String = p.clip(clipId)?.trackId ?: ""): Project? {
        val c = p.clip(clipId) ?: return null
        val track = p.track(trackId) ?: return null
        val asset = p.asset(c.assetId) ?: return null
        if (!accepts(track, asset) || track.locked) return null
        val start = nearestFreeStart(p, track.id, newStartMs, c.durationMs, setOf(c.id)) ?: return null
        return replace(p, c.copy(startMs = start, trackId = track.id))
    }

    /** Przesuwa kilka klipów o tę samą różnicę czasu (bez zmiany ścieżek). */
    fun moveGroup(p: Project, ids: Set<String>, deltaMs: Long): Project? {
        val chosen = p.clips.filter { it.id in ids }
        if (chosen.isEmpty()) return null
        val minStart = chosen.minOf { it.startMs }
        val d = max(deltaMs, -minStart)
        val moved = chosen.map { it.copy(startMs = it.startMs + d) }
        val others = p.clips.filter { it.id !in ids }
        val collides = moved.any { m -> others.any { o -> o.trackId == m.trackId && o.startMs < m.endMs && m.startMs < o.endMs } }
        if (collides) return null
        return p.copy(clips = others + moved)
    }

    // ---------------------------------------------------------------- usuwanie

    /** Usuwa klipy; [ripple] dosuwa dalsze klipy tej samej ścieżki w miejsce usuniętych. */
    fun delete(p: Project, ids: Set<String>, ripple: Boolean): Project {
        val removed = p.clips.filter { it.id in ids }
        var clips = p.clips.filter { it.id !in ids }
        if (ripple) {
            removed.sortedByDescending { it.startMs }.forEach { r ->
                clips = clips.map {
                    if (it.trackId == r.trackId && it.startMs >= r.endMs) it.copy(startMs = max(0L, it.startMs - r.durationMs)) else it
                }
            }
        }
        val titles = p.titles.filter { it.id !in ids }
        return p.copy(clips = clips, titles = titles)
    }

    /** Usuwa przerwy między klipami na ścieżce. */
    fun closeGaps(p: Project, trackId: String): Project {
        var cursor = 0L
        val packed = p.clipsOn(trackId).map { c -> c.copy(startMs = cursor).also { cursor += c.durationMs } }
        val byId = packed.associateBy { it.id }
        return p.copy(clips = p.clips.map { byId[it.id] ?: it })
    }

    // ---------------------------------------------------------------- powielanie i audio

    fun duplicate(p: Project, ids: Set<String>): Pair<Project, Set<String>>? {
        val chosen = p.clips.filter { it.id in ids }.sortedBy { it.startMs }
        val titles = p.titles.filter { it.id in ids }
        if (chosen.isEmpty() && titles.isEmpty()) return null
        var out = p
        val created = mutableSetOf<String>()
        chosen.forEach { c ->
            if (c.trackId == p.mainTrack.id) {
                // Na głównej ścieżce kopia wchodzi tuż za oryginał, dalsze klipy się odsuwają.
                val copy = c.copy(id = newId(), startMs = c.endMs)
                out = out.copy(clips = out.clips.map { if (it.trackId == c.trackId && it.startMs >= c.endMs) it.copy(startMs = it.startMs + c.durationMs) else it } + copy)
                created += copy.id
            } else {
                val start = nearestFreeStart(out, c.trackId, c.endMs, c.durationMs) ?: return@forEach
                val copy = c.copy(id = newId(), startMs = start)
                out = out.copy(clips = out.clips + copy)
                created += copy.id
            }
        }
        titles.forEach { t ->
            val copy = t.copy(id = newId(), startMs = t.endMs)
            out = out.copy(titles = out.titles + copy)
            created += copy.id
        }
        return out to created
    }

    /** Kopiuje dźwięk klipu wideo na ścieżkę audio i wycisza oryginał (montaż J/L, osobna obróbka). */
    fun detachAudio(p: Project, clipId: String): Pair<Project, Clip>? {
        val c = p.clip(clipId) ?: return null
        val asset = p.asset(c.assetId) ?: return null
        if (asset.kind != MediaKind.VIDEO || !asset.hasAudio || c.muted) return null
        var base = p
        val track = base.audioTracks.firstOrNull { !it.locked && isFree(base, it.id, c.startMs, c.endMs) }
            ?: Track(kind = TrackKind.AUDIO, name = nextTrackName(base, TrackKind.AUDIO)).also { base = base.copy(tracks = base.tracks + it) }
        val audio = c.copy(
            id = newId(),
            trackId = track.id,
            transitionIn = Transition.NONE,
            transitionOut = Transition.NONE,
            filter = FilterPreset.NONE,
            color = ColorAdjust(),
            transform = Transform(),
        )
        val clips = base.clips.map { if (it.id == c.id) it.copy(muted = true) else it } + audio
        return base.copy(clips = clips) to audio
    }

    // ---------------------------------------------------------------- napisy

    fun addTitle(p: Project, text: String, atMs: Long, template: TitleClip? = null): Pair<Project, TitleClip> {
        val t = (template ?: TitleClip(text = text, startMs = 0)).copy(id = newId(), text = text, startMs = max(0L, atMs))
        return p.copy(titles = p.titles + t) to t
    }

    fun updateTitle(p: Project, t: TitleClip): Project = p.copy(titles = p.titles.map { if (it.id == t.id) t else it })

    fun moveTitle(p: Project, id: String, startMs: Long): Project? {
        val t = p.title(id) ?: return null
        return updateTitle(p, t.copy(startMs = max(0L, startMs)))
    }

    fun trimTitleStart(p: Project, id: String, newStart: Long): Project? {
        val t = p.title(id) ?: return null
        val s = newStart.coerceIn(0L, t.endMs - MIN_CLIP_MS)
        return updateTitle(p, t.copy(startMs = s, durationMs = t.endMs - s))
    }

    fun trimTitleEnd(p: Project, id: String, newEnd: Long): Project? {
        val t = p.title(id) ?: return null
        return updateTitle(p, t.copy(durationMs = max(MIN_CLIP_MS, newEnd - t.startMs)))
    }

    /** Podział nachodzących napisów na rzędy (do rysowania ścieżki tekstu). */
    fun titleLanes(titles: List<TitleClip>): Map<String, Int> {
        val laneEnds = mutableListOf<Long>()
        val out = mutableMapOf<String, Int>()
        titles.sortedBy { it.startMs }.forEach { t ->
            val lane = laneEnds.indexOfFirst { it <= t.startMs }.takeIf { it >= 0 } ?: laneEnds.size.also { laneEnds += 0L }
            laneEnds[lane] = t.endMs
            out[t.id] = lane
        }
        return out
    }

    fun splitTitle(p: Project, id: String, atMs: Long): Project? {
        val t = p.title(id) ?: return null
        val off = atMs - t.startMs
        if (off < MIN_CLIP_MS || t.durationMs - off < MIN_CLIP_MS) return null
        val left = t.copy(durationMs = off)
        val right = t.copy(id = newId(), startMs = atMs, durationMs = t.durationMs - off)
        return p.copy(titles = p.titles.map { if (it.id == id) left else it } + right)
    }

    // ---------------------------------------------------------------- znaczniki

    fun toggleMarker(p: Project, atMs: Long, toleranceMs: Long = 150): Project {
        val near = p.markers.firstOrNull { abs(it.timeMs - atMs) <= toleranceMs }
        return if (near != null) p.copy(markers = p.markers - near) else p.copy(markers = (p.markers + Marker(timeMs = max(0L, atMs))).sortedBy { it.timeMs })
    }

    // ---------------------------------------------------------------- odczyt

    fun clipAt(p: Project, trackId: String, timeMs: Long): Clip? =
        p.clips.firstOrNull { it.trackId == trackId && it.startMs <= timeMs && timeMs < it.endMs }

    fun replace(p: Project, c: Clip): Project = p.copy(clips = p.clips.map { if (it.id == c.id) c else it })

    /** Element sekwencji do zbudowania kompozycji: klip albo przerwa (czarna klatka / cisza). */
    sealed interface Segment {
        val durationMs: Long

        data class Gap(override val durationMs: Long) : Segment
        data class Item(val clip: Clip) : Segment {
            override val durationMs: Long get() = clip.durationMs
        }
    }

    /**
     * Ścieżka jako ciąg segmentów od zera, z przerwami w lukach i (opcjonalnie) dopełniona do
     * [padToMs]. Nachodzące klipy są przycinane, żeby sekwencja była zawsze ciągła.
     */
    fun segments(p: Project, trackId: String, padToMs: Long = 0L, include: (Clip) -> Boolean = { true }): List<Segment> {
        val out = mutableListOf<Segment>()
        var cursor = 0L
        p.clipsOn(trackId).filter(include).forEach { c ->
            var clip = c
            if (clip.startMs < cursor) {
                val cut = cursor - clip.startMs
                if (clip.durationMs - cut < MIN_CLIP_MS) return@forEach
                clip = clip.copy(startMs = cursor, durationMs = clip.durationMs - cut, sourceInMs = clip.sourceInMs + (cut * clip.speed).roundToLong())
            }
            if (clip.startMs > cursor) out += Segment.Gap(clip.startMs - cursor)
            out += Segment.Item(clip)
            cursor = clip.endMs
        }
        if (padToMs > cursor) out += Segment.Gap(padToMs - cursor)
        return out
    }
}
