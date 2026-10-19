package com.openclipflow.app.ui

import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openclipflow.app.PlatformActions
import com.openclipflow.app.engine.ClipMotion
import com.openclipflow.app.engine.TitleRenderer
import com.openclipflow.app.model.MediaKind
import com.openclipflow.app.model.TimeFormat
import com.openclipflow.app.model.TimelineOps
import com.openclipflow.app.model.TrackKind
import com.openclipflow.app.ui.components.PillButton
import com.openclipflow.app.ui.theme.DockBar
import com.openclipflow.app.ui.theme.GlassIconButton
import com.openclipflow.app.ui.theme.MonoDigits
import com.openclipflow.app.ui.theme.Palette
import com.openclipflow.app.ui.theme.ToolButton
import com.openclipflow.app.ui.theme.reactive
import kotlin.math.abs

@Composable
fun EditorScreen(vm: EditorViewModel, platform: PlatformActions) {
    var fullscreen by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Ink)) {
        val compactHeight = maxHeight < 520.dp
        val timelineH = when {
            compactHeight -> (maxHeight * 0.42f).coerceIn(120.dp, 190.dp)
            else -> (maxHeight * 0.34f).coerceIn(190.dp, 360.dp)
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (!fullscreen) TopBar(vm)
            if (compactHeight && !fullscreen) {
                // Poziomy telefon: narzędzia w pionowej kolumnie obok podglądu.
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    PreviewArea(vm, platform, Modifier.weight(1f).fillMaxHeight())
                    ToolRail(vm, platform, Modifier.width(78.dp).fillMaxHeight())
                }
            } else {
                PreviewArea(vm, platform, Modifier.fillMaxWidth().weight(1f))
            }
            TransportBar(vm, fullscreen) { fullscreen = !fullscreen }
            if (!fullscreen) {
                Timeline(vm, platform, Modifier.fillMaxWidth().height(timelineH))
                if (!compactHeight) BottomTools(vm, platform)
            }
        }
        EditorPanels(vm, platform)
        TrackDialogs(vm)
    }
}

@Composable
private fun TopBar(vm: EditorViewModel) {
    DockBar(Modifier.fillMaxWidth().height(52.dp)) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Projekty", size = 42.dp) { vm.closeEditor() }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { vm.panel = PanelKind.PROJECT }.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(vm.project.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${vm.project.settings.aspect.label} · ${vm.project.settings.resolutionLabel} · ${vm.project.settings.fps} kl/s",
                color = Palette.TextDim,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        GlassIconButton(Icons.AutoMirrored.Rounded.Undo, "Cofnij", size = 42.dp, enabled = vm.canUndo) { vm.undo() }
        GlassIconButton(Icons.AutoMirrored.Rounded.Redo, "Ponów", size = 42.dp, enabled = vm.canRedo) { vm.redo() }
        GlassIconButton(Icons.Rounded.Settings, "Ustawienia", size = 42.dp, badge = if (vm.updatePending) "1" else null) { vm.panel = PanelKind.SETTINGS }
        val exporting = vm.exportState is ExportState.Running
        Row(
            Modifier
                .padding(start = 4.dp, end = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .reactive("Eksportuj film") { vm.panel = PanelKind.EXPORT }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (exporting) {
                CircularProgressIndicator(
                    progress = { (vm.exportState as ExportState.Running).progress },
                    color = Palette.Ink,
                    trackColor = Palette.Ink.copy(alpha = 0.15f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(Icons.Rounded.Upload, null, tint = Palette.Ink, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("Eksport", color = Palette.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** Podgląd: obraz z silnika, napisy rysowane tym samym kodem co w eksporcie, ręczna edycja kadru. */
@Composable
private fun PreviewArea(vm: EditorViewModel, platform: PlatformActions, modifier: Modifier) {
    val p = vm.project
    val seq = p.settings
    Box(modifier.background(Color(0xFF050505)).padding(8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(seq.width.toFloat() / seq.height, matchHeightConstraintsFirst = seq.width < seq.height * 1.2f)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx -> SurfaceView(ctx).also { vm.preview.attach(it) } },
                onRelease = { vm.preview.detach(it) },
                modifier = Modifier.fillMaxSize(),
            )
            PreviewOverlay(vm)
            if (p.clips.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Oś czasu jest pusta", color = Palette.TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Dodaj filmy i zdjęcia", icon = Icons.Rounded.Add) { platform.pickMedia(ImportTarget.TIMELINE) }
                }
            }
        }
        vm.preview.message?.let { msg ->
            Text(
                msg,
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { vm.preview.message = null }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PreviewOverlay(vm: EditorViewModel) {
    val current by rememberUpdatedState(vm)
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Dotknięcie napisu – zaznacza go; przeciąganie/szczypanie/obrót – edytuje
                // zaznaczony napis albo kadr zaznaczonego klipu.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width
                    val h = size.height
                    val v = current
                    val t = v.playheadMs
                    val hitTitle = v.project.titles.lastOrNull { it.startMs <= t && t < it.endMs && TitleRenderer.bounds(it, w, h).contains(down.position.x, down.position.y) }
                    if (hitTitle != null && hitTitle.id !in v.selection) v.select(hitTitle.id)
                    val title = v.selectedTitle?.takeIf { it.startMs <= t && t < it.endMs }
                    val clip = if (title == null) v.selectedClip?.takeIf { it.startMs <= t && t < it.endMs && v.project.track(it.trackId)?.kind == TrackKind.VIDEO } else null
                    var changed = false
                    var moved = false
                    do {
                        val ev = awaitPointerEvent()
                        val zoom = ev.calculateZoom()
                        val pan = ev.calculatePan()
                        val rot = ev.calculateRotation()
                        if (pan.getDistance() > 0.5f || zoom != 1f || rot != 0f) moved = true
                        if (title != null && moved) {
                            v.editLive { pr ->
                                val cur = pr.title(title.id) ?: return@editLive null
                                TimelineOps.updateTitle(
                                    pr,
                                    cur.copy(
                                        x = (cur.x + pan.x / w).coerceIn(0f, 1f),
                                        y = (cur.y + pan.y / h).coerceIn(0f, 1f),
                                        size = (cur.size * zoom).coerceIn(0.02f, 0.35f),
                                    ),
                                )
                            }
                            changed = true
                        } else if (clip != null && moved) {
                            v.editLive { pr ->
                                val cur = pr.clip(clip.id) ?: return@editLive null
                                val tr = cur.transform
                                TimelineOps.replace(
                                    pr,
                                    cur.copy(
                                        transform = tr.copy(
                                            offsetX = (tr.offsetX + pan.x / w).coerceIn(-1.5f, 1.5f),
                                            offsetY = (tr.offsetY + pan.y / h).coerceIn(-1.5f, 1.5f),
                                            scale = (tr.scale * zoom).coerceIn(0.1f, 6f),
                                            rotation = ((tr.rotation + rot) % 360f),
                                        ),
                                    ),
                                )
                            }
                            changed = true
                        }
                        if (changed) ev.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (ev.changes.any { it.pressed })
                    if (changed) v.commitLive()
                    else if (!moved && hitTitle == null && v.selectedTitle != null) v.select(null)
                }
            },
    ) {
        val v = current
        val t = v.playheadMs
        drawIntoCanvas { c -> TitleRenderer.drawAll(c.nativeCanvas, v.project.titles, t, size.width.toInt(), size.height.toInt()) }
        // Ramka zaznaczonego napisu albo klipu.
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        v.selectedTitle?.takeIf { it.startMs <= t && t < it.endMs }?.let { title ->
            val r = TitleRenderer.bounds(title, size.width.toInt(), size.height.toInt())
            drawRect(Color.White, Offset(r.left, r.top), Size(r.width(), r.height()), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
        }
        v.selectedClip?.takeIf { it.startMs <= t && t < it.endMs && v.project.track(it.trackId)?.kind == TrackKind.VIDEO }?.let { c ->
            val m = ClipMotion.compute(c, (t - c.startMs) * 1000, c.durationMs * 1000, size.width / size.height)
            val pts = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)
            m.mapPoints(pts)
            val path = androidx.compose.ui.graphics.Path().apply {
                for (i in 0 until 4) {
                    val x = (pts[i * 2] + 1f) / 2f * size.width
                    val y = (1f - pts[i * 2 + 1]) / 2f * size.height
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
        }
        if (v.showSafeArea) {
            val g = Color.White.copy(alpha = 0.35f)
            drawRect(g, Offset(size.width * 0.05f, size.height * 0.05f), Size(size.width * 0.9f, size.height * 0.9f), style = Stroke(1.dp.toPx()))
            drawRect(g, Offset(size.width * 0.1f, size.height * 0.1f), Size(size.width * 0.8f, size.height * 0.8f), style = Stroke(1.dp.toPx()))
            for (i in 1..2) {
                drawLine(g.copy(alpha = 0.2f), Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1f)
                drawLine(g.copy(alpha = 0.2f), Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1f)
            }
        }
    }
}

@Composable
private fun TransportBar(vm: EditorViewModel, fullscreen: Boolean, onFullscreen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).background(Color.Black).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(TimeFormat.short(vm.playheadMs), style = MonoDigits, color = Color.White, fontSize = 13.sp)
            Text("/ " + TimeFormat.short(vm.project.durationMs), style = MonoDigits, color = Palette.TextDim, fontSize = 10.sp)
        }
        GlassIconButton(Icons.Rounded.SkipPrevious, "Poprzednie cięcie", size = 38.dp) { vm.jumpToEdge(false) }
        GlassIconButton(Icons.Rounded.KeyboardArrowLeft, "Klatka wstecz", size = 38.dp, onLongPress = { vm.stepFrames(-10) }) { vm.stepFrames(-1) }
        Spacer(Modifier.width(4.dp))
        GlassIconButton(
            if (vm.preview.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (vm.preview.isPlaying) "Pauza" else "Odtwórz",
            size = 44.dp,
            active = true,
        ) { vm.togglePlay() }
        Spacer(Modifier.width(4.dp))
        GlassIconButton(Icons.Rounded.KeyboardArrowRight, "Klatka naprzód", size = 38.dp, onLongPress = { vm.stepFrames(10) }) { vm.stepFrames(1) }
        GlassIconButton(Icons.Rounded.SkipNext, "Następne cięcie", size = 38.dp) { vm.jumpToEdge(true) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            GlassIconButton(Icons.Rounded.Bookmark, "Znacznik", size = 36.dp) { vm.toggleMarker() }
            GlassIconButton(Icons.Rounded.ZoomOutMap, "Dopasuj oś czasu", size = 36.dp) {
                val d = vm.project.durationMs.coerceAtLeast(1000)
                vm.zoom = (300f * 1000f / d).coerceIn(4f, 400f)
            }
            GlassIconButton(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Pełny ekran", size = 36.dp, onClick = onFullscreen)
        }
    }
}

private data class Tool(val icon: ImageVector, val label: String, val danger: Boolean = false, val active: Boolean = false, val action: () -> Unit)

@Composable
private fun tools(vm: EditorViewModel, platform: PlatformActions): List<Tool> {
    val clip = vm.selectedClip
    val title = vm.selectedTitle
    return when {
        vm.selection.size > 1 -> listOf(
            Tool(Icons.Rounded.CallSplit, "Podziel") { vm.splitAtPlayhead() },
            Tool(Icons.Rounded.ContentCopy, "Duplikuj") { vm.duplicateSelection() },
            Tool(Icons.Rounded.Deselect, "Odznacz") { vm.select(null) },
            Tool(Icons.Rounded.Delete, "Usuń", danger = true) { vm.deleteSelection() },
        )
        clip != null -> {
            val asset = vm.project.asset(clip.assetId)
            val track = vm.project.track(clip.trackId)
            val isVideoTrack = track?.kind == TrackKind.VIDEO
            buildList {
                add(Tool(Icons.Rounded.CallSplit, "Podziel") { vm.splitAtPlayhead() })
                if (asset?.kind != MediaKind.IMAGE) add(Tool(Icons.Rounded.Speed, "Prędkość", active = vm.panel == PanelKind.CLIP && vm.clipTab == ClipTab.SPEED) { vm.openClipPanel(ClipTab.SPEED) })
                else add(Tool(Icons.Rounded.Speed, "Czas") { vm.openClipPanel(ClipTab.SPEED) })
                if (asset?.hasAudio == true) add(Tool(Icons.Rounded.VolumeUp, "Dźwięk", active = vm.panel == PanelKind.CLIP && vm.clipTab == ClipTab.AUDIO) { vm.openClipPanel(ClipTab.AUDIO) })
                if (isVideoTrack) {
                    add(Tool(Icons.Rounded.FilterVintage, "Filtry", active = vm.panel == PanelKind.CLIP && vm.clipTab == ClipTab.FILTER) { vm.openClipPanel(ClipTab.FILTER) })
                    add(Tool(Icons.Rounded.Tune, "Korekcja", active = vm.panel == PanelKind.CLIP && vm.clipTab == ClipTab.ADJUST) { vm.openClipPanel(ClipTab.ADJUST) })
                    add(Tool(Icons.Rounded.Crop, "Kadr", active = vm.panel == PanelKind.CLIP && vm.clipTab == ClipTab.TRANSFORM) { vm.openClipPanel(ClipTab.TRANSFORM) })
                    add(Tool(Icons.Rounded.Animation, "Przejścia", active = vm.panel == PanelKind.CLIP && vm.clipTab == ClipTab.TRANSITION) { vm.openClipPanel(ClipTab.TRANSITION) })
                }
                if (isVideoTrack && asset?.kind == MediaKind.VIDEO && asset.hasAudio && !clip.muted) add(Tool(Icons.Rounded.MusicOff, "Odłącz audio") { vm.detachAudio() })
                add(Tool(Icons.Rounded.ContentCopy, "Duplikuj") { vm.duplicateSelection() })
                add(Tool(Icons.Rounded.FindReplace, "Podmień") {
                    vm.replaceClipId = clip.id
                    platform.pickMedia(ImportTarget.REPLACE, if (track?.kind == TrackKind.AUDIO) arrayOf("audio/*", "video/*") else arrayOf("video/*", "image/*"))
                })
                add(Tool(Icons.Rounded.Delete, "Usuń", danger = true) { vm.deleteSelection() })
            }
        }
        title != null -> listOf(
            Tool(Icons.Rounded.Edit, "Edytuj", active = vm.panel == PanelKind.TITLE) { vm.panel = PanelKind.TITLE },
            Tool(Icons.Rounded.CallSplit, "Podziel") { vm.splitAtPlayhead() },
            Tool(Icons.Rounded.ContentCopy, "Duplikuj") { vm.duplicateSelection() },
            Tool(Icons.Rounded.Delete, "Usuń", danger = true) { vm.deleteSelection() },
        )
        else -> listOf(
            Tool(Icons.Rounded.Add, "Dodaj") { platform.pickMedia(ImportTarget.TIMELINE) },
            Tool(Icons.Rounded.PhotoLibrary, "Biblioteka", active = vm.panel == PanelKind.MEDIA) { vm.panel = PanelKind.MEDIA },
            Tool(Icons.Rounded.TextFields, "Tekst") { vm.addTitle() },
            Tool(Icons.Rounded.AudioFile, "Muzyka") { platform.pickMedia(ImportTarget.TIMELINE, arrayOf("audio/*")) },
            Tool(Icons.Rounded.PictureInPicture, "Nakładka") { platform.pickMedia(ImportTarget.OVERLAY, arrayOf("video/*", "image/*")) },
            Tool(Icons.Rounded.CallSplit, "Podziel") { vm.splitTool() },
            Tool(Icons.Rounded.Layers, "Ścieżki", active = vm.panel == PanelKind.TRACKS) { vm.panel = PanelKind.TRACKS },
            Tool(Icons.Rounded.AspectRatio, "Format", active = vm.panel == PanelKind.PROJECT) { vm.panel = PanelKind.PROJECT },
            Tool(Icons.Rounded.SpaceBar, "Usuń luki") { vm.closeGaps() },
            Tool(Icons.Rounded.CameraAlt, "Zapisz klatkę") { vm.saveFrame() },
            Tool(Icons.Rounded.LinearScale, "Zaznacz wszystko") { vm.selectAll() },
        )
    }
}

@Composable
private fun BottomTools(vm: EditorViewModel, platform: PlatformActions) {
    val list = tools(vm, platform)
    DockBar(Modifier.fillMaxWidth().height(60.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            list.forEach { t -> ToolButton(t.icon, t.label, Modifier.width(72.dp), active = t.active, danger = t.danger, onClick = t.action) }
        }
    }
}

@Composable
private fun ToolRail(vm: EditorViewModel, platform: PlatformActions, modifier: Modifier) {
    val list = tools(vm, platform)
    Column(modifier.background(Color.Black).verticalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        list.forEach { t -> ToolButton(t.icon, t.label, Modifier.fillMaxWidth(), active = t.active, danger = t.danger, onClick = t.action) }
    }
}

/** Ikona narzędzia „styl” używana w panelach. */
val StyleIcon = Icons.Rounded.Style
val FxIcon = Icons.Rounded.AutoAwesome
