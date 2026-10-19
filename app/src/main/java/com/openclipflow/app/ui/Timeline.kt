package com.openclipflow.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclipflow.app.media.Waveforms
import com.openclipflow.app.media.rememberFrame
import com.openclipflow.app.media.rememberWaveform
import com.openclipflow.app.model.Clip
import com.openclipflow.app.model.FilterPreset
import com.openclipflow.app.model.MediaAsset
import com.openclipflow.app.model.MediaKind
import com.openclipflow.app.model.Project
import com.openclipflow.app.model.TimeFormat
import com.openclipflow.app.model.TimelineOps
import com.openclipflow.app.model.TitleClip
import com.openclipflow.app.model.Track
import com.openclipflow.app.model.TrackKind
import com.openclipflow.app.model.Transition
import com.openclipflow.app.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val RULER = 24.dp
private val TITLE_LANE = 28.dp
private val MAIN_ROW = 60.dp
private val OVERLAY_ROW = 46.dp
private val AUDIO_ROW = 42.dp
private val ROW_GAP = 4.dp
private val HEADER_W = 52.dp

/** Wiersz osi czasu: rząd napisów albo ścieżka. */
private data class Lane(val key: String, val track: Track?, val titleLane: Int, val top: Float, val height: Float)

@Composable
fun Timeline(vm: EditorViewModel, platform: com.openclipflow.app.PlatformActions, modifier: Modifier = Modifier) {
    val p = vm.project
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pxPerMs = with(density) { vm.zoom.dp.toPx() } / 1000f
    val lanesInfo = TimelineOps.titleLanes(p.titles)
    val titleLaneCount = max(1, (lanesInfo.values.maxOrNull() ?: -1) + 1)

    // Układ wierszy: napisy, nakładki (od najwyższej), główna ścieżka, audio.
    val lanes = remember(p.tracks, titleLaneCount, density) {
        with(density) {
            var y = 0f
            val out = mutableListOf<Lane>()
            repeat(titleLaneCount) { i ->
                out += Lane("t$i", null, i, y, TITLE_LANE.toPx())
                y += TITLE_LANE.toPx() + ROW_GAP.toPx() / 2
            }
            y += ROW_GAP.toPx() / 2
            val videos = p.videoTracks
            (videos.drop(1).reversed() + videos.first()).forEach { t ->
                val h = if (t.id == p.mainTrack.id) MAIN_ROW else OVERLAY_ROW
                out += Lane(t.id, t, -1, y, h.toPx())
                y += h.toPx() + ROW_GAP.toPx()
            }
            p.audioTracks.forEach { t ->
                out += Lane(t.id, t, -1, y, AUDIO_ROW.toPx())
                y += AUDIO_ROW.toPx() + ROW_GAP.toPx()
            }
            out
        }
    }
    val bodyHeightPx = lanes.lastOrNull()?.let { it.top + it.height + with(density) { ROW_GAP.toPx() } } ?: 0f
    val currentVm by rememberUpdatedState(vm)

    BoxWithConstraints(modifier.background(Color(0xFF0A0A0A))) {
        val totalW = constraints.maxWidth.toFloat()
        val headerPx = with(density) { HEADER_W.toPx() }
        val areaW = totalW - headerPx
        val centerX = areaW / 2f
        val viewStart = vm.playheadMs - (centerX / pxPerMs).toLong()
        val viewEnd = vm.playheadMs + ((areaW - centerX) / pxPerMs).toLong()
        fun xOf(t: Long) = centerX + (t - vm.playheadMs) * pxPerMs
        val fling = remember { Animatable(0f) }

        Column(Modifier.fillMaxSize()) {
            // ---------------------------------------------------------------- linijka
            Row(Modifier.fillMaxWidth().height(RULER)) {
                Box(Modifier.width(HEADER_W).fillMaxHeight().background(Color.Black), contentAlignment = Alignment.Center) {
                    Text(TimeFormat.short(vm.playheadMs).substringBeforeLast('.'), color = Palette.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Ruler(vm, p, pxPerMs, viewStart, viewEnd, ::xOf, Modifier.weight(1f).fillMaxHeight())
            }
            // ---------------------------------------------------------------- ścieżki
            val vScroll = rememberScrollState()
            Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(vScroll)) {
                Row(Modifier.fillMaxWidth().height(with(density) { bodyHeightPx.toDp() })) {
                    TrackHeaders(vm, lanes, Modifier.width(HEADER_W).fillMaxHeight())
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(0.dp))
                            // Przewijanie (szukanie), rzut, zoom szczypnięciem, dotknięcie = odznaczenie.
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    scope.launch { fling.stop() }
                                    val tracker = VelocityTracker()
                                    tracker.addPosition(down.uptimeMillis, down.position)
                                    var dragging = false
                                    var zooming = false
                                    var startDist = 0f
                                    var startZoom = currentVm.zoom
                                    var acc = 0f
                                    var consumedByChild = false
                                    while (true) {
                                        val ev = awaitPointerEvent(PointerEventPass.Main)
                                        val pressed = ev.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        if (pressed.size >= 2) {
                                            val d = (pressed[0].position - pressed[1].position).getDistance()
                                            if (!zooming) {
                                                zooming = true
                                                startDist = d
                                                startZoom = currentVm.zoom
                                            } else if (startDist > 0) {
                                                currentVm.zoom = (startZoom * d / startDist).coerceIn(4f, 1200f)
                                            }
                                            ev.changes.forEach { it.consume() }
                                            continue
                                        }
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                                        if (ch.isConsumed && !dragging) {
                                            consumedByChild = true
                                            break
                                        }
                                        tracker.addPosition(ch.uptimeMillis, ch.position)
                                        val dx = ch.positionChange().x
                                        acc += dx
                                        if (!dragging && abs(acc) > viewConfiguration.touchSlop && !zooming) {
                                            dragging = true
                                            currentVm.pause()
                                            currentVm.preview.setScrubbing(true)
                                        }
                                        if (dragging) {
                                            val ppm = with(density) { currentVm.zoom.dp.toPx() } / 1000f
                                            currentVm.seek(currentVm.playheadMs - (dx / ppm).roundToLong())
                                            ch.consume()
                                        }
                                    }
                                    if (dragging) {
                                        currentVm.preview.setScrubbing(false)
                                        val v = tracker.calculateVelocity().x
                                        if (abs(v) > 300f) {
                                            scope.launch {
                                                fling.snapTo(0f)
                                                var last = 0f
                                                fling.animateDecay(-v, exponentialDecay(frictionMultiplier = 1.4f)) {
                                                    val ppm = with(density) { currentVm.zoom.dp.toPx() } / 1000f
                                                    currentVm.seek(currentVm.playheadMs + ((value - last) / ppm).roundToLong())
                                                    last = value
                                                }
                                            }
                                        }
                                    } else if (!zooming && !consumedByChild) {
                                        currentVm.select(null)
                                    }
                                }
                            },
                    ) {
                        LaneBackgrounds(lanes, p)
                        // Napisy.
                        p.titles.forEach { t ->
                            val lane = lanes.firstOrNull { it.track == null && it.titleLane == (lanesInfo[t.id] ?: 0) } ?: return@forEach
                            if (t.endMs < viewStart - 2000 || t.startMs > viewEnd + 2000) return@forEach
                            androidx.compose.runtime.key(t.id) {
                                TitleBlock(vm, t, lane, pxPerMs, viewStart, viewEnd, ::xOf)
                            }
                        }
                        // Klipy – jedna warstwa, więc klip przeciągany między ścieżkami nie traci gestu.
                        p.clips.forEach { c ->
                            if (c.endMs < viewStart - 2000 || c.startMs > viewEnd + 2000) return@forEach
                            val lane = lanes.firstOrNull { it.track?.id == c.trackId } ?: return@forEach
                            androidx.compose.runtime.key(c.id) {
                                ClipBlock(vm, p, c, lane, lanes, pxPerMs, viewStart, viewEnd, ::xOf)
                            }
                        }
                        // Cięcia głównej ścieżki: okno przenikania i przycisk ustawień przejścia.
                        val mainLane = lanes.firstOrNull { it.track?.id == p.mainTrack.id }
                        if (mainLane != null) {
                            val mainClips = p.clipsOn(p.mainTrack.id)
                            mainClips.zipWithNext().forEach { (a, b) ->
                                if (b.startMs - a.endMs > com.openclipflow.app.model.Dissolves.ADJACENT_MS || a.endMs - b.startMs > com.openclipflow.app.model.Dissolves.ADJACENT_MS) return@forEach
                                val cutX = xOf(b.startMs)
                                if (cutX < -40f || cutX > areaW + 40f) return@forEach
                                val join = com.openclipflow.app.model.Dissolves.join(p, b)
                                androidx.compose.runtime.key("cut" + b.id) {
                                    if (join != null) {
                                        val x0 = xOf(join.windowStart)
                                        Box(
                                            Modifier
                                                .offset { IntOffset(x0.roundToInt(), mainLane.top.roundToInt()) }
                                                .width(with(density) { (join.total * pxPerMs).coerceAtLeast(2f).toDp() })
                                                .height(with(density) { mainLane.height.toDp() })
                                                .background(Color.White.copy(alpha = 0.22f)),
                                        )
                                    }
                                    Box(
                                        Modifier
                                            .offset { IntOffset(cutX.roundToInt() - 12.dp.roundToPx(), (mainLane.top + mainLane.height / 2).roundToInt() - 12.dp.roundToPx()) }
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (join != null) Color.White else Color.Black.copy(alpha = 0.75f))
                                            .clickable { vm.openCut(b.id) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Rounded.Gradient,
                                            if (join != null) "Przenikanie ${TimeFormat.duration(join.total)}" else "Dodaj przejście",
                                            tint = if (join != null) Palette.Ink else Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                        // Przycisk „+” na końcu głównej ścieżki.
                        if (mainLane != null) {
                            val endX = xOf(p.clipsOn(p.mainTrack.id).maxOfOrNull { it.endMs } ?: 0L) + with(density) { 6.dp.toPx() }
                            if (endX < areaW) {
                                Box(
                                    Modifier
                                        .offset { IntOffset(endX.roundToInt(), (mainLane.top + mainLane.height / 2).roundToInt() - 18.dp.roundToPx()) }
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .clickable { platform.pickMedia(ImportTarget.TIMELINE) },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Add, "Dodaj multimedia", tint = Palette.Ink) }
                            }
                        }
                        // Znaczniki.
                        Canvas(Modifier.fillMaxSize()) {
                            p.markers.forEach { m ->
                                val x = xOf(m.timeMs)
                                if (x in 0f..size.width) drawLine(Color(m.color).copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                            }
                        }
                    }
                }
            }
        }
        // Głowica – stała na środku obszaru ścieżek.
        Canvas(Modifier.fillMaxSize()) {
            val x = headerPx + centerX
            drawLine(Palette.Playhead, Offset(x, 4.dp.toPx()), Offset(x, size.height), 2.dp.toPx())
            val path = Path().apply {
                moveTo(x - 6.dp.toPx(), 0f)
                lineTo(x + 6.dp.toPx(), 0f)
                lineTo(x + 6.dp.toPx(), 6.dp.toPx())
                lineTo(x, 12.dp.toPx())
                lineTo(x - 6.dp.toPx(), 6.dp.toPx())
                close()
            }
            drawPath(path, Palette.Playhead)
        }
    }
}

@Composable
private fun Ruler(vm: EditorViewModel, p: Project, pxPerMs: Float, viewStart: Long, viewEnd: Long, xOf: (Long) -> Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = TextStyle(color = Palette.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    Canvas(
        modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val ppm = with(density) { vm.zoom.dp.toPx() } / 1000f
                    vm.seek(vm.playheadMs + ((o.x - size.width / 2f) / ppm).roundToLong())
                }
            },
    ) {
        val minGapPx = 64.dp.toPx()
        val steps = longArrayOf(100, 250, 500, 1000, 2000, 5000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000, 600_000)
        val step = steps.firstOrNull { it * pxPerMs >= minGapPx } ?: steps.last()
        val minor = step / 5
        var t = floor(viewStart.toDouble() / minor).toLong() * minor
        while (t <= viewEnd + minor) {
            if (t >= 0) {
                val x = xOf(t)
                val major = t % step == 0L
                drawLine(Palette.TextFaint, Offset(x, size.height), Offset(x, size.height - (if (major) 9.dp else 4.dp).toPx()), 1.dp.toPx())
                if (major) {
                    val label = if (step < 1000) TimeFormat.short(t).let { it.substring(0, it.length - 1) } else TimeFormat.short(t).substringBeforeLast('.')
                    drawText(measurer, label, Offset(x + 3.dp.toPx(), 2.dp.toPx()), style)
                }
            }
            t += minor
        }
        // Koniec projektu.
        val endX = xOf(p.durationMs)
        if (endX in 0f..size.width) drawLine(Palette.Record, Offset(endX, 0f), Offset(endX, size.height), 1.5.dp.toPx())
        p.markers.forEach { m ->
            val x = xOf(m.timeMs)
            if (x in -10f..size.width + 10f) {
                val path = Path().apply {
                    moveTo(x - 5.dp.toPx(), size.height - 10.dp.toPx())
                    lineTo(x + 5.dp.toPx(), size.height - 10.dp.toPx())
                    lineTo(x, size.height)
                    close()
                }
                drawPath(path, Color(m.color))
            }
        }
    }
}

@Composable
private fun TrackHeaders(vm: EditorViewModel, lanes: List<Lane>, modifier: Modifier) {
    val density = LocalDensity.current
    Box(modifier.background(Color.Black)) {
        lanes.forEach { lane ->
            val t = lane.track
            if (t == null && lane.titleLane > 0) return@forEach
            Column(
                Modifier
                    .offset { IntOffset(0, lane.top.roundToInt()) }
                    .width(HEADER_W)
                    .height(with(density) { lane.height.toDp() })
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.PanelHigh)
                    .clickable {
                        vm.trackMenu = t?.id ?: TITLES_LANE
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                val icon = when {
                    t == null -> Icons.Rounded.TextFields
                    t.kind == TrackKind.AUDIO -> Icons.Rounded.MusicNote
                    t.id == vm.project.mainTrack.id -> Icons.Rounded.Movie
                    else -> Icons.Rounded.PictureInPicture
                }
                Icon(icon, t?.name ?: "Napisy", tint = Color.White, modifier = Modifier.size(16.dp))
                val code = when {
                    t == null -> "T"
                    t.kind == TrackKind.VIDEO -> "V" + (vm.project.videoTracks.indexOf(t) + 1)
                    else -> "A" + (vm.project.audioTracks.indexOf(t) + 1)
                }
                Text(code, color = Palette.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                if (t != null && lane.height > with(density) { 36.dp.toPx() }) {
                    Row {
                        if (t.muted) Icon(Icons.Rounded.VolumeOff, "Wyciszona", tint = Palette.Record, modifier = Modifier.size(11.dp))
                        if (t.hidden) Icon(Icons.Rounded.VisibilityOff, "Ukryta", tint = Palette.Record, modifier = Modifier.size(11.dp))
                        if (t.locked) Icon(Icons.Rounded.Lock, "Zablokowana", tint = Palette.Marker, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LaneBackgrounds(lanes: List<Lane>, p: Project) {
    Canvas(Modifier.fillMaxSize()) {
        lanes.forEach { l ->
            val alpha = if (l.track?.id == p.mainTrack.id) 0.07f else 0.04f
            drawRoundRect(Color.White.copy(alpha = alpha), Offset(0f, l.top), Size(size.width, l.height), CornerRadius(6.dp.toPx()))
        }
    }
}

/** Widoczny fragment elementu (klipy bywają dłuższe niż ekran – rysujemy tylko to, co widać). */
private data class Visible(val startMs: Long, val endMs: Long, val x: Float, val width: Float, val leftEdge: Boolean, val rightEdge: Boolean)

private fun visible(start: Long, end: Long, viewStart: Long, viewEnd: Long, pxPerMs: Float, xOf: (Long) -> Float): Visible {
    val margin = (200 / pxPerMs).toLong()
    val s = max(start, viewStart - margin)
    val e = min(end, viewEnd + margin)
    return Visible(s, e, xOf(s), max(1f, (e - s) * pxPerMs), s == start, e == end)
}

@Composable
private fun ClipBlock(
    vm: EditorViewModel,
    p: Project,
    c: Clip,
    lane: Lane,
    lanes: List<Lane>,
    pxPerMs: Float,
    viewStart: Long,
    viewEnd: Long,
    xOf: (Long) -> Float,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val asset = p.asset(c.assetId)
    val track = lane.track!!
    val selected = c.id in vm.selection
    val v = visible(c.startMs, c.endMs, viewStart, viewEnd, pxPerMs, xOf)
    if (v.endMs <= v.startMs) return
    val color = when {
        track.kind == TrackKind.AUDIO -> Palette.AudioClip
        asset?.kind == MediaKind.IMAGE -> Palette.ImageClip
        else -> Palette.VideoClip
    }
    val shape = RoundedCornerShape(
        topStart = if (v.leftEdge) 7.dp else 0.dp,
        bottomStart = if (v.leftEdge) 7.dp else 0.dp,
        topEnd = if (v.rightEdge) 7.dp else 0.dp,
        bottomEnd = if (v.rightEdge) 7.dp else 0.dp,
    )
    val currentClip by rememberUpdatedState(c)
    val currentLanes by rememberUpdatedState(lanes)
    var moving by remember { mutableStateOf(false) }
    val heightDp = with(density) { lane.height.toDp() }

    Box(
        Modifier
            .offset { IntOffset(v.x.roundToInt(), lane.top.roundToInt()) }
            .width(with(density) { v.width.toDp() })
            .height(heightDp)
            .graphicsLayer { alpha = if (moving) 0.8f else 1f }
            .clip(shape)
            .background(color)
            .then(if (selected) Modifier.border(2.dp, Palette.Selection, shape) else Modifier)
            // Dotknięcie = zaznaczenie (ponowne – inspektor), przytrzymanie i przeciągnięcie = przesuwanie.
            .pointerInput(c.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val slop = viewConfiguration.touchSlop
                    var longPressed = false
                    var cancelled = false
                    val origStart = currentClip.startMs
                    val origLaneTop = lane.top
                    var totalDx = 0f
                    var totalDy = 0f
                    while (true) {
                        val ev = if (!longPressed) withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() } else awaitPointerEvent()
                        if (ev == null) {
                            if (track.locked) {
                                vm.toast("Ścieżka jest zablokowana")
                                cancelled = true
                                break
                            }
                            longPressed = true
                            moving = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.pause()
                            if (currentClip.id !in vm.selection) vm.select(currentClip.id)
                            continue
                        }
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            if (!longPressed && !cancelled) {
                                val id = currentClip.id
                                if (id in vm.selection && vm.selection.size == 1) vm.panel = PanelKind.CLIP else vm.select(id)
                            }
                            ch.consume()
                            break
                        }
                        if (!longPressed) {
                            if (ch.isConsumed || (ch.position - down.position).getDistance() > slop) {
                                cancelled = true
                                break
                            }
                            continue
                        }
                        val d = ch.positionChange()
                        totalDx += d.x
                        totalDy += d.y
                        ch.consume()
                        val ppm = with(density) { vm.zoom.dp.toPx() } / 1000f
                        var start = origStart + (totalDx / ppm).roundToLong()
                        if (vm.project.settings.snapping) {
                            val targets = TimelineOps.snapTargets(vm.project, setOf(currentClip.id), vm.playheadMs)
                            val th = (10.dp.toPx() / ppm).roundToLong()
                            val snappedStart = TimelineOps.snap(start, targets, th)
                            val snappedEnd = TimelineOps.snap(start + currentClip.durationMs, targets, th)
                            start = if (snappedStart != start) snappedStart else if (snappedEnd != start + currentClip.durationMs) snappedEnd - currentClip.durationMs else start
                        }
                        val y = origLaneTop + down.position.y + totalDy
                        val target = currentLanes.firstOrNull { it.track != null && y >= it.top && y < it.top + it.height }?.track
                        val trackId = target?.takeIf { t -> asset != null && TimelineOps.accepts(t, asset, currentClip) && !t.locked }?.id ?: currentClip.trackId
                        vm.editLive { pr -> TimelineOps.move(pr, currentClip.id, start, trackId) }
                    }
                    if (longPressed) {
                        moving = false
                        vm.commitLive()
                    }
                }
            },
    ) {
        if (asset != null && track.kind == TrackKind.VIDEO) Filmstrip(asset, c, v, pxPerMs, lane.height)
        if (asset != null && asset.hasAudio && (track.kind == TrackKind.AUDIO || vm.showWaveforms) && !(track.kind == TrackKind.VIDEO && c.muted)) {
            WaveformStrip(asset, c, v, pxPerMs, if (track.kind == TrackKind.AUDIO) 1f else 0.32f)
        }
        // Podpis i plakietki.
        Row(
            Modifier.padding(start = 6.dp, top = 3.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (c.speed != 1f) Text("${trimFloat(c.speed)}× ", color = Palette.Marker, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            if (c.muted || track.muted) Icon(Icons.Rounded.VolumeOff, null, tint = Palette.Record, modifier = Modifier.size(10.dp))
            if (c.filter != FilterPreset.NONE || !c.color.isIdentity) Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(10.dp))
            Text(
                (if (v.leftEdge) asset?.name ?: "Brak pliku" else "") + "  " + TimeFormat.short(c.durationMs),
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Znaczniki przejść.
        if (c.transitionIn != Transition.NONE && v.leftEdge) TransitionTick(Alignment.BottomStart, c.transitionMs, pxPerMs)
        if (c.transitionOut != Transition.NONE && v.rightEdge) TransitionTick(Alignment.BottomEnd, c.transitionMs, pxPerMs)
        if (selected && vm.selection.size == 1 && !track.locked) {
            if (v.leftEdge) TrimHandle(vm, c.id, left = true, Modifier.align(Alignment.CenterStart))
            if (v.rightEdge) TrimHandle(vm, c.id, left = false, Modifier.align(Alignment.CenterEnd))
        }
    }
}

fun trimFloat(f: Float): String = if (f == f.roundToInt().toFloat()) f.roundToInt().toString() else String.format(java.util.Locale.ROOT, "%.2f", f).trimEnd('0').trimEnd('.')

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TransitionTick(align: Alignment, ms: Long, pxPerMs: Float) {
    val w = with(LocalDensity.current) { (ms * pxPerMs).coerceIn(6f, 400f).toDp() }
    Box(Modifier.align(align).width(w).height(4.dp).background(Color.White.copy(alpha = 0.7f)))
}

@Composable
private fun Filmstrip(asset: MediaAsset, c: Clip, v: Visible, pxPerMs: Float, heightPx: Float) {
    val density = LocalDensity.current
    val thumbH = heightPx.roundToInt().coerceAtLeast(8)
    val aspect = if (asset.displayHeight > 0) asset.displayWidth.toFloat() / asset.displayHeight else 16f / 9f
    val thumbW = thumbH * aspect.coerceIn(0.5f, 2.4f)
    val firstIndex = floor(((v.startMs - c.startMs) * pxPerMs) / thumbW).toInt()
    val lastIndex = ceil(((v.endMs - c.startMs) * pxPerMs) / thumbW).toInt()
    // Czas miniatury zaokrąglony – te same klatki przy różnym powiększeniu (pamięć podręczna).
    val quant = when {
        thumbW / pxPerMs < 800 -> 500L
        thumbW / pxPerMs < 4000 -> 1000L
        else -> 5000L
    }
    Box(Modifier.fillMaxSize()) {
        for (i in firstIndex until lastIndex) {
            val tLocal = (i * thumbW / pxPerMs).toLong()
            val src = c.sourceInMs + (tLocal * c.speed).toLong()
            val tq = (src / quant) * quant
            val x = (c.startMs - v.startMs) * pxPerMs + i * thumbW
            val frame by rememberFrame(asset, tq, (thumbH / 2).coerceAtLeast(24))
            frame?.let {
                Image(
                    it,
                    null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.85f,
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), 0) }
                        .width(with(density) { thumbW.toDp() })
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun WaveformStrip(asset: MediaAsset, c: Clip, v: Visible, pxPerMs: Float, heightFraction: Float) {
    val wave by rememberWaveform(asset)
    val data = wave ?: return
    val gain = (if (c.muted) 0f else c.volume).coerceIn(0f, 2f)
    Canvas(Modifier.fillMaxSize()) {
        val bar = 2.dp.toPx()
        val h = size.height * heightFraction
        val base = size.height
        var x = 0f
        while (x < size.width) {
            val tTimeline = v.startMs + (x / pxPerMs).toLong()
            val src = c.sourceInMs + ((tTimeline - c.startMs) * c.speed).toLong()
            val idx = (src / Waveforms.STEP_MS).toInt()
            val amp = if (idx in data.indices) data[idx] else 0f
            val bh = (amp * h * min(1f, max(gain, 0.08f))).coerceAtLeast(1f)
            drawRect(Palette.Waveform.copy(alpha = if (heightFraction < 1f) 0.75f else 0.9f), Offset(x, base - bh), Size(bar * 0.7f, bh))
            x += bar
        }
    }
}

@Composable
private fun TrimHandle(vm: EditorViewModel, id: String, left: Boolean, modifier: Modifier) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(0L) }
    var acc by remember { mutableStateOf(0f) }
    Box(
        modifier
            .width(16.dp)
            .fillMaxHeight()
            .background(Color.White)
            .pointerInput(id, left) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        vm.pause()
                        val c = vm.project.clip(id)
                        origin = if (left) c?.startMs ?: 0 else c?.endMs ?: 0
                        acc = 0f
                    },
                    onDragEnd = { vm.commitLive() },
                    onDragCancel = { vm.commitLive() },
                ) { change, dx ->
                    change.consume()
                    acc += dx
                    val ppm = with(density) { vm.zoom.dp.toPx() } / 1000f
                    var t = origin + (acc / ppm).roundToLong()
                    if (vm.project.settings.snapping) {
                        t = TimelineOps.snap(t, TimelineOps.snapTargets(vm.project, setOf(id), vm.playheadMs), (10.dp.toPx() / ppm).roundToLong())
                    }
                    vm.editLive { p -> if (left) TimelineOps.trimStart(p, id, t) else TimelineOps.trimEnd(p, id, t) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Ink))
    }
}

@Composable
private fun TitleBlock(vm: EditorViewModel, t: TitleClip, lane: Lane, pxPerMs: Float, viewStart: Long, viewEnd: Long, xOf: (Long) -> Float) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val selected = t.id in vm.selection
    val v = visible(t.startMs, t.endMs, viewStart, viewEnd, pxPerMs, xOf)
    if (v.endMs <= v.startMs) return
    val shape = RoundedCornerShape(6.dp)
    val current by rememberUpdatedState(t)
    Box(
        Modifier
            .offset { IntOffset(v.x.roundToInt(), lane.top.roundToInt()) }
            .width(with(density) { v.width.toDp() })
            .height(with(density) { lane.height.toDp() })
            .clip(shape)
            .background(Palette.TitleClip)
            .then(if (selected) Modifier.border(2.dp, Palette.Selection, shape) else Modifier)
            .pointerInput(t.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    var longPressed = false
                    var cancelled = false
                    val orig = current.startMs
                    var dx = 0f
                    while (true) {
                        val ev = if (!longPressed) withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() } else awaitPointerEvent()
                        if (ev == null) {
                            longPressed = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.pause()
                            vm.select(current.id)
                            continue
                        }
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            if (!longPressed && !cancelled) {
                                if (current.id in vm.selection && vm.selection.size == 1) vm.panel = PanelKind.TITLE else vm.select(current.id)
                            }
                            ch.consume()
                            break
                        }
                        if (!longPressed) {
                            if (ch.isConsumed || (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                cancelled = true
                                break
                            }
                            continue
                        }
                        dx += ch.positionChange().x
                        ch.consume()
                        val ppm = with(density) { vm.zoom.dp.toPx() } / 1000f
                        var s = orig + (dx / ppm).roundToLong()
                        if (vm.project.settings.snapping) s = TimelineOps.snap(s, TimelineOps.snapTargets(vm.project, setOf(current.id), vm.playheadMs), (10.dp.toPx() / ppm).roundToLong())
                        vm.editLive { p -> TimelineOps.moveTitle(p, current.id, s) }
                    }
                    if (longPressed) vm.commitLive()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TextFields, null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(t.text.replace('\n', ' '), color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected && vm.selection.size == 1) {
            if (v.leftEdge) TitleTrimHandle(vm, t.id, true, Modifier.align(Alignment.CenterStart))
            if (v.rightEdge) TitleTrimHandle(vm, t.id, false, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun TitleTrimHandle(vm: EditorViewModel, id: String, left: Boolean, modifier: Modifier) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(0L) }
    var acc by remember { mutableStateOf(0f) }
    Box(
        modifier
            .width(14.dp)
            .fillMaxHeight()
            .background(Color.White)
            .pointerInput(id, left) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        val t = vm.project.title(id)
                        origin = if (left) t?.startMs ?: 0 else t?.endMs ?: 0
                        acc = 0f
                    },
                    onDragEnd = { vm.commitLive() },
                    onDragCancel = { vm.commitLive() },
                ) { change, dx ->
                    change.consume()
                    acc += dx
                    val ppm = with(density) { vm.zoom.dp.toPx() } / 1000f
                    var t = origin + (acc / ppm).roundToLong()
                    if (vm.project.settings.snapping) t = TimelineOps.snap(t, TimelineOps.snapTargets(vm.project, setOf(id), vm.playheadMs), (10.dp.toPx() / ppm).roundToLong())
                    vm.editLive { p -> if (left) TimelineOps.trimTitleStart(p, id, t) else TimelineOps.trimTitleEnd(p, id, t) }
                }
            },
    )
}

/** Ikona dla rodzaju ścieżki – używana też w panelu ścieżek. */
fun trackIcon(t: Track, main: Boolean) = when {
    t.kind == TrackKind.AUDIO -> Icons.Rounded.GraphicEq
    main -> Icons.Rounded.Movie
    else -> Icons.Rounded.PictureInPicture
}
