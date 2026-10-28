package com.openclipflow.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclipflow.app.BuildConfig
import com.openclipflow.app.PlatformActions
import com.openclipflow.app.media.rememberFrame
import com.openclipflow.app.model.Clip
import com.openclipflow.app.model.ExportFormat
import com.openclipflow.app.model.ExportQuality
import com.openclipflow.app.model.ExportSettings
import com.openclipflow.app.model.FilterPreset
import com.openclipflow.app.model.KenBurns
import com.openclipflow.app.model.MediaAsset
import com.openclipflow.app.model.MediaKind
import com.openclipflow.app.model.TimeFormat
import com.openclipflow.app.model.TimelineOps
import com.openclipflow.app.model.TitleAlign
import com.openclipflow.app.model.TitleAnimation
import com.openclipflow.app.model.TitleClip
import com.openclipflow.app.model.TitleStyle
import com.openclipflow.app.model.TrackKind
import com.openclipflow.app.model.Transform
import com.openclipflow.app.model.Transition
import com.openclipflow.app.model.VideoCodec
import com.openclipflow.app.ui.components.ActionRow
import com.openclipflow.app.ui.components.Chips
import com.openclipflow.app.ui.components.ColorDot
import com.openclipflow.app.ui.components.CompactChoice
import com.openclipflow.app.ui.components.DarkField
import com.openclipflow.app.ui.components.Group
import com.openclipflow.app.ui.components.InfoRow
import com.openclipflow.app.ui.components.Panel
import com.openclipflow.app.ui.components.PillButton
import com.openclipflow.app.ui.components.RoundIcon
import com.openclipflow.app.ui.components.SliderRow
import com.openclipflow.app.ui.components.SwitchRow
import com.openclipflow.app.ui.components.TabChip
import com.openclipflow.app.ui.theme.Palette
import com.openclipflow.app.ui.theme.reactive
import com.openclipflow.app.update.UpdateManager
import com.openclipflow.app.update.UpdateState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EditorPanels(vm: EditorViewModel, platform: PlatformActions) {
    val close = { vm.panel = PanelKind.NONE }
    when (vm.panel) {
        PanelKind.NONE -> Unit
        PanelKind.MEDIA -> MediaPanel(vm, platform, close)
        PanelKind.CLIP -> vm.selectedClip?.let { ClipPanel(vm, it, close) } ?: close()
        PanelKind.TITLE -> vm.selectedTitle?.let { TitlePanel(vm, it, close) } ?: close()
        PanelKind.PROJECT -> ProjectPanel(vm, platform, close)
        PanelKind.EXPORT -> ExportPanel(vm, close)
        PanelKind.SETTINGS -> SettingsPanel(vm)
        PanelKind.TRACKS -> TracksPanel(vm, close)
    }
}

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"
private fun signed(v: Float) = (if (v > 0) "+" else "") + (v * 100).roundToInt()

// ============================================================================ biblioteka

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaPanel(vm: EditorViewModel, platform: PlatformActions, onClose: () -> Unit) {
    var filter by remember { mutableStateOf<MediaKind?>(null) }
    var info by remember { mutableStateOf<MediaAsset?>(null) }
    Panel("Biblioteka", onClose, actions = { RoundIcon(Icons.Rounded.Add, "Importuj", size = 38.dp) { platform.pickMedia(ImportTarget.LIBRARY) } }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TabChip(Icons.Rounded.GridOn, "Wszystko", filter == null) { filter = null }
            TabChip(Icons.Rounded.Videocam, "Wideo", filter == MediaKind.VIDEO) { filter = MediaKind.VIDEO }
            TabChip(Icons.Rounded.Photo, "Zdjęcia", filter == MediaKind.IMAGE) { filter = MediaKind.IMAGE }
            TabChip(Icons.Rounded.MusicNote, "Audio", filter == MediaKind.AUDIO) { filter = MediaKind.AUDIO }
        }
        val assets = vm.project.assets.filter { filter == null || it.kind == filter }.sortedByDescending { it.addedAt }
        if (assets.isEmpty()) {
            Text("Brak plików. Zaimportuj filmy, zdjęcia albo muzykę – dotknięcie pliku dodaje go na oś czasu w miejscu głowicy.", color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            PillButton("Importuj pliki", icon = Icons.Rounded.Add) { platform.pickMedia(ImportTarget.LIBRARY) }
        } else {
            Text("Dotknij, aby dodać w miejscu głowicy · przytrzymaj – szczegóły", color = Palette.TextDim, fontSize = 11.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                assets.forEach { a -> AssetTile(vm, a, Modifier.weight(1f), onInfo = { info = a }) }
                repeat((3 - assets.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    info?.let { a ->
        com.openclipflow.app.ui.components.DarkDialog(a.name, onDismiss = { info = null }, buttons = {
            PillButton("Usuń z projektu", danger = true) { vm.removeAsset(a); info = null }
            PillButton("Zamknij", primary = false) { info = null }
        }) {
            InfoRow(Icons.Rounded.Description, "Rodzaj", when (a.kind) { MediaKind.VIDEO -> "Wideo"; MediaKind.AUDIO -> "Dźwięk"; MediaKind.IMAGE -> "Zdjęcie" })
            if (a.kind != MediaKind.IMAGE) InfoRow(Icons.Rounded.Timer, "Długość", TimeFormat.duration(a.durationMs))
            if (a.width > 0) InfoRow(Icons.Rounded.AspectRatio, "Rozdzielczość", "${a.displayWidth} × ${a.displayHeight}")
            if (a.sizeBytes > 0) InfoRow(Icons.Rounded.Info, "Rozmiar", TimeFormat.bytes(a.sizeBytes))
            InfoRow(Icons.Rounded.Movie, "Użycia na osi czasu", vm.project.clips.count { it.assetId == a.id }.toString())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetTile(vm: EditorViewModel, a: MediaAsset, modifier: Modifier, onInfo: () -> Unit) {
    val frame by rememberFrame(a, minOf(1000L, a.durationMs / 3), 160)
    val used = vm.project.clips.any { it.assetId == a.id }
    Column(modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { vm.addAssetToTimeline(a) }, onLongClick = onInfo)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Palette.PanelHigh), contentAlignment = Alignment.Center) {
            val f = frame
            if (f != null) Image(f, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(if (a.kind == MediaKind.AUDIO) Icons.Rounded.MusicNote else Icons.Rounded.Movie, null, tint = Palette.TextFaint, modifier = Modifier.size(30.dp))
            if (a.kind != MediaKind.IMAGE) {
                Text(
                    TimeFormat.short(a.durationMs).substringBeforeLast('.'),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (used) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(RoundedCornerShape(50)).background(Color.White))
        }
        Text(a.name, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp, start = 2.dp))
    }
}

// ============================================================================ inspektor klipu

@Composable
private fun ClipPanel(vm: EditorViewModel, c: Clip, onClose: () -> Unit) {
    val asset = vm.project.asset(c.assetId)
    val track = vm.project.track(c.trackId)
    val video = track?.kind == TrackKind.VIDEO
    val tabs = ClipTab.entries.filter { t ->
        when (t) {
            ClipTab.AUDIO -> asset?.hasAudio == true
            ClipTab.FILTER, ClipTab.ADJUST, ClipTab.TRANSFORM, ClipTab.TRANSITION -> video
            ClipTab.SPEED -> true
        }
    }
    val tab = if (vm.clipTab in tabs) vm.clipTab else tabs.first()
    Panel(asset?.name ?: "Klip", onClose) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEach { t ->
                val icon = when (t) {
                    ClipTab.SPEED -> Icons.Rounded.Speed
                    ClipTab.AUDIO -> Icons.Rounded.VolumeUp
                    ClipTab.FILTER -> Icons.Rounded.AutoFixHigh
                    ClipTab.ADJUST -> Icons.Rounded.Tune
                    ClipTab.TRANSFORM -> Icons.Rounded.CropFree
                    ClipTab.TRANSITION -> Icons.Rounded.Animation
                }
                TabChip(icon, if (t == ClipTab.SPEED && asset?.kind == MediaKind.IMAGE) "Czas" else t.label, t == tab) { vm.clipTab = t }
            }
        }
        when (tab) {
            ClipTab.SPEED -> SpeedTab(vm, c, asset)
            ClipTab.AUDIO -> AudioTab(vm, c)
            ClipTab.FILTER -> FilterTab(vm, c, asset)
            ClipTab.ADJUST -> AdjustTab(vm, c)
            ClipTab.TRANSFORM -> TransformTab(vm, c, track?.id != vm.project.mainTrack.id)
            ClipTab.TRANSITION -> TransitionTab(vm, c)
        }
        Group("Klip") {
            InfoRow(Icons.Rounded.Timer, "Na osi czasu", "${TimeFormat.short(c.startMs)} – ${TimeFormat.short(c.endMs)} (${TimeFormat.duration(c.durationMs)})")
            if (asset != null && asset.kind != MediaKind.IMAGE) InfoRow(Icons.Rounded.Movie, "Fragment źródła", "${TimeFormat.short(c.sourceInMs)} – ${TimeFormat.short(c.sourceOutMs)} z ${TimeFormat.short(asset.durationMs)}")
        }
    }
}

@Composable
private fun SpeedTab(vm: EditorViewModel, c: Clip, asset: MediaAsset?) {
    if (asset?.kind == MediaKind.IMAGE) {
        Group("Czas wyświetlania") {
            SliderRow(Icons.Rounded.Timer, "Długość", c.durationMs / 1000f, 0.5f..20f, { String.format(Locale.ROOT, "%.1f s", it) }, default = 4f, onChange = { vm.updateClip(c.copy(durationMs = (it * 1000).toLong()), live = true) }, onCommit = { vm.commitLive() })
            Chips(null, null, listOf(1f, 2f, 3f, 5f, 8f, 10f), -1f, { "${it.roundToInt()} s" }) { vm.setDuration(c.id, (it * 1000).toLong()) }
        }
        return
    }
    Group("Prędkość odtwarzania") {
        // Skala logarytmiczna: 0,25× … 4× z 1× pośrodku.
        val log = kotlin.math.log2(c.speed)
        SliderRow(
            Icons.Rounded.Speed,
            "Tempo",
            log,
            -2f..2f,
            { "${trimFloat(Math.round(Math.pow(2.0, it.toDouble()) * 100) / 100f)}×" },
            default = 0f,
            onChange = { vm.setSpeed(c.id, Math.pow(2.0, it.toDouble()).toFloat().let { s -> (s * 20).roundToInt() / 20f }, live = true) },
            onCommit = { vm.commitLive() },
        )
        Chips(null, null, listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f), c.speed, { "${trimFloat(it)}×" }) { vm.setSpeed(c.id, it, live = false) }
        Text(
            "Zmiana tempa zmienia długość klipu – kolejne klipy głównej ścieżki przesuwają się automatycznie. Dźwięk zachowuje wysokość.",
            color = Palette.TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AudioTab(vm: EditorViewModel, c: Clip) {
    Group("Głośność") {
        SwitchRow(Icons.Rounded.VolumeOff, "Wycisz klip", null, c.muted) { vm.updateClip(c.copy(muted = it)) }
        SliderRow(Icons.Rounded.VolumeUp, "Poziom", c.volume, 0f..2f, ::pct, default = 1f, enabled = !c.muted, onChange = { vm.updateClip(c.copy(volume = (it * 100).roundToInt() / 100f), live = true) }, onCommit = { vm.commitLive() })
    }
    Group("Wyciszanie") {
        val maxFade = (c.durationMs / 2 / 1000f).coerceAtLeast(0.1f).coerceAtMost(10f)
        SliderRow(Icons.Rounded.GraphicEq, "Narastanie", c.audioFadeInMs / 1000f, 0f..maxFade, { String.format(Locale.ROOT, "%.1f s", it) }, default = 0f, onChange = { vm.updateClip(c.copy(audioFadeInMs = (it * 1000).toLong()), live = true) }, onCommit = { vm.commitLive() })
        SliderRow(Icons.Rounded.GraphicEq, "Wygaszanie", c.audioFadeOutMs / 1000f, 0f..maxFade, { String.format(Locale.ROOT, "%.1f s", it) }, default = 0f, onChange = { vm.updateClip(c.copy(audioFadeOutMs = (it * 1000).toLong()), live = true) }, onCommit = { vm.commitLive() })
    }
    val asset = vm.project.asset(c.assetId)
    if (vm.project.track(c.trackId)?.kind == TrackKind.VIDEO && asset?.kind == MediaKind.VIDEO && !c.muted) {
        Group(null) { ActionRow(Icons.Rounded.MusicNote, "Odłącz dźwięk", "Przenosi dźwięk na osobną ścieżkę audio") { vm.detachAudio() } }
    }
}

@Composable
private fun FilterTab(vm: EditorViewModel, c: Clip, asset: MediaAsset?) {
    val t = ((vm.playheadMs.coerceIn(c.startMs, c.endMs - 1) - c.startMs) * c.speed).toLong() + c.sourceInMs
    val frame by rememberFrame(asset, (t / 1000) * 1000, 120)
    Group("Filtry") {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPreset.entries.forEach { f ->
                val sel = c.filter == f
                Column(
                    Modifier.width(74.dp).reactive(f.label) { vm.updateClip(c.copy(filter = f)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Color.White else Palette.PanelHigh)
                            .padding(if (sel) 3.dp else 0.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    ) {
                        frame?.let {
                            Image(
                                it,
                                null,
                                contentScale = ContentScale.Crop,
                                colorFilter = filterPreviewColor(f),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(f.label, color = if (sel) Color.White else Palette.TextDim, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (c.filter != FilterPreset.NONE) {
            SliderRow(Icons.Rounded.Opacity, "Intensywność", c.filterStrength, 0f..1f, ::pct, default = 1f, onChange = { vm.updateClip(c.copy(filterStrength = it), live = true) }, onCommit = { vm.commitLive() })
        }
    }
    Group(null) { ActionRow(Icons.Rounded.Palette, "Zastosuj wygląd do wszystkich klipów", "Filtr i korekcja kolorów na całej osi czasu") { vm.applyToAll(c) } }
}

/** Przybliżenie filtra na miniaturze (macierz kolorów Compose). */
private fun filterPreviewColor(f: FilterPreset): androidx.compose.ui.graphics.ColorFilter? {
    val m = com.openclipflow.app.engine.VideoEffects.filterMatrix(f, 1f) ?: return null
    // Macierz GL (kolumnami, z przesunięciem w kolumnie 4) → macierz ColorMatrix 4×5 (wierszami).
    val cm = floatArrayOf(
        m[0], m[4], m[8], 0f, m[12] * 255f,
        m[1], m[5], m[9], 0f, m[13] * 255f,
        m[2], m[6], m[10], 0f, m[14] * 255f,
        0f, 0f, 0f, 1f, 0f,
    )
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(cm))
}

@Composable
private fun AdjustTab(vm: EditorViewModel, c: Clip) {
    val a = c.color
    fun set(n: com.openclipflow.app.model.ColorAdjust) = vm.updateClip(c.copy(color = n), live = true)
    Group("Korekcja kolorów", ) {
        SliderRow(Icons.Rounded.BrightnessMedium, "Jasność", a.brightness, -1f..1f, ::signed, default = 0f, onChange = { set(a.copy(brightness = it)) }, onCommit = { vm.commitLive() })
        SliderRow(Icons.Rounded.Contrast, "Kontrast", a.contrast, -1f..1f, ::signed, default = 0f, onChange = { set(a.copy(contrast = it)) }, onCommit = { vm.commitLive() })
        SliderRow(Icons.Rounded.WaterDrop, "Nasycenie", a.saturation, -1f..1f, ::signed, default = 0f, onChange = { set(a.copy(saturation = it)) }, onCommit = { vm.commitLive() })
        SliderRow(Icons.Rounded.Thermostat, "Temperatura", a.temperature, -1f..1f, ::signed, default = 0f, onChange = { set(a.copy(temperature = it)) }, onCommit = { vm.commitLive() })
        SliderRow(Icons.Rounded.Colorize, "Odcień", a.hue, -180f..180f, { "${it.roundToInt()}°" }, default = 0f, onChange = { set(a.copy(hue = it)) }, onCommit = { vm.commitLive() })
        SliderRow(Icons.Rounded.BlurOn, "Rozmycie", a.blur, 0f..1f, ::pct, default = 0f, onChange = { set(a.copy(blur = it)) }, onCommit = { vm.commitLive() })
    }
    if (!a.isIdentity) Group(null) { ActionRow(Icons.Rounded.Refresh, "Przywróć oryginał") { vm.updateClip(c.copy(color = com.openclipflow.app.model.ColorAdjust())) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransformTab(vm: EditorViewModel, c: Clip, overlay: Boolean) {
    val t = c.transform
    fun set(n: Transform, live: Boolean = true) = vm.updateClip(c.copy(transform = n), live = live)
    Text("Przeciągaj, szczyp i obracaj obraz bezpośrednio w podglądzie.", color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp))
    Group("Układ w kadrze") {
        val s = vm.project.settings
        val aspect = s.width.toFloat() / s.height
        val current = com.openclipflow.app.model.LayoutPreset.of(t.region)
        Text(
            "Gdzie leży to ujęcie. Dwie ścieżki wideo obok siebie: jedno ujęcie „Lewa połowa”, drugie „Prawa połowa” (albo narzędzie Układ).",
            color = Palette.TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.openclipflow.app.model.LayoutPreset.entries.forEach { lp ->
                val sel = lp == current
                Column(
                    Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Color.White else Color.White.copy(alpha = 0.06f))
                        .reactive(lp.label) { vm.setClipLayout(c.id, lp) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LayoutThumb(listOf(lp.frame), aspect, selected = sel, modifier = Modifier.height(34.dp))
                    Text(lp.label, color = if (sel) Palette.Ink else Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
    Group("Kadr") {
        CompactChoice(Icons.Rounded.AspectRatio, "Dopasowanie", listOf(false, true), t.fill, { if (it) "Wypełnij kadr (przytnij)" else "Zmieść całość (pasy)" }, { if (it) "Wypełnij" else "Zmieść" }) { set(t.copy(fill = it), live = false) }
        SliderRow(Icons.Rounded.ZoomIn, "Skala", t.scale, 0.1f..4f, ::pct, default = 1f, onChange = { set(t.copy(scale = it)) }, onCommit = { vm.commitLive() })
        SliderRow(Icons.Rounded.RotateRight, "Obrót", t.rotation, -180f..180f, { "${it.roundToInt()}°" }, default = 0f, onChange = { set(t.copy(rotation = it)) }, onCommit = { vm.commitLive() })
        SliderRow(Icons.Rounded.OpenWith, "Poziomo", t.offsetX, -1f..1f, ::signed, default = 0f, onChange = { set(t.copy(offsetX = it)) }, onCommit = { vm.commitLive() })
        SliderRow(Icons.Rounded.SwapVert, "Pionowo", t.offsetY, -1f..1f, ::signed, default = 0f, onChange = { set(t.copy(offsetY = it)) }, onCommit = { vm.commitLive() })
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Obróć 90°", primary = false, icon = Icons.Rounded.RotateRight) { set(t.copy(rotation = ((t.rotation + 90f + 180f) % 360f) - 180f), live = false) }
            PillButton("Odbij", primary = t.flipH, icon = Icons.Rounded.Flip) { set(t.copy(flipH = !t.flipH), live = false) }
            PillButton("Odbij ↕", primary = t.flipV) { set(t.copy(flipV = !t.flipV), live = false) }
        }
    }
    if (overlay) {
        Group("Warstwa") {
            SliderRow(Icons.Rounded.Opacity, "Krycie", c.opacity, 0f..1f, ::pct, default = 1f, onChange = { vm.updateClip(c.copy(opacity = it), live = true) }, onCommit = { vm.commitLive() })
        }
    }
    Group("Ruch (Ken Burns)") {
        Chips(Icons.Rounded.CenterFocusStrong, "Animacja kadru", KenBurns.entries, t.kenBurns, { it.label }) { set(t.copy(kenBurns = it), live = false) }
    }
    if (!t.isIdentity) Group(null) { ActionRow(Icons.Rounded.Refresh, "Przywróć kadr") { set(Transform(), live = false) } }
}

@Composable
private fun DissolveChips(vm: EditorViewModel, target: Clip) {
    val p = vm.project
    val possible = com.openclipflow.app.model.Dissolves.maxPossible(p, target)
    val actual = com.openclipflow.app.model.Dissolves.join(p, target)?.total ?: 0L
    Chips(null, null, com.openclipflow.app.model.Dissolves.PRESETS, target.dissolveInMs, { if (it == 0L) "Cięcie" else TimeFormat.duration(it) }) { vm.setDissolve(target.id, it) }
    Text(
        when {
            possible < com.openclipflow.app.model.Dissolves.MIN_MS -> "Brak zapasu materiału po obu stronach cięcia – skróć klip, by zrobić miejsce na przenikanie."
            target.dissolveInMs > 0 && actual < target.dissolveInMs -> "Faktycznie ${TimeFormat.duration(actual)} – tyle pozwala zapas materiału."
            else -> "Możliwe do ${TimeFormat.duration(possible)}. Punkt cięcia i długość filmu się nie zmieniają."
        },
        color = Palette.TextDim,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun TransitionTab(vm: EditorViewModel, c: Clip) {
    val p = vm.project
    if (c.trackId == p.mainTrack.id) {
        val prev = com.openclipflow.app.model.Dissolves.previousAdjacent(p, c)
        val next = com.openclipflow.app.model.Dissolves.nextAdjacent(p, c)
        if (prev != null) Group("Przenikanie z poprzedniego ujęcia") { DissolveChips(vm, c) }
        if (next != null) Group("Przenikanie do następnego ujęcia") { DissolveChips(vm, next) }
        if (prev != null || next != null) {
            Group(null) {
                ActionRow(Icons.Rounded.Animation, "Przenikanie na wszystkich cięciach", "Ten sam czas (${TimeFormat.duration((if (prev != null) c.dissolveInMs else next?.dissolveInMs ?: 0).coerceAtLeast(500))}) między wszystkimi ujęciami") {
                    vm.setDissolveAll((if (prev != null) c.dissolveInMs else next?.dissolveInMs ?: 0).coerceAtLeast(500))
                }
                ActionRow(Icons.Rounded.Refresh, "Usuń wszystkie przenikania") { vm.setDissolveAll(0) }
            }
        }
    }
    Group(if (c.trackId == p.mainTrack.id) "Wejście klipu (bez przenikania)" else "Wejście") {
        Chips(null, null, Transition.entries, c.transitionIn, { it.label }) { vm.updateClip(c.copy(transitionIn = it)) }
    }
    Group(if (c.trackId == vm.project.mainTrack.id) "Wyjście klipu (bez przenikania)" else "Wyjście") {
        Chips(null, null, Transition.entries, c.transitionOut, { it.label }) { vm.updateClip(c.copy(transitionOut = it)) }
    }
    Group("Czas trwania") {
        val maxT = (c.durationMs / 2 / 1000f).coerceIn(0.2f, 3f)
        SliderRow(Icons.Rounded.Timer, "Długość przejścia", c.transitionMs / 1000f, 0.1f..maxT, { String.format(Locale.ROOT, "%.1f s", it) }, default = 0.6f.coerceAtMost(maxT), onChange = { vm.updateClip(c.copy(transitionMs = (it * 1000).toLong()), live = true) }, onCommit = { vm.commitLive() })
    }
    Group(null) {
        ActionRow(Icons.Rounded.Animation, "Zastosuj do wszystkich klipów głównej ścieżki", "Te same przejścia między wszystkimi ujęciami") {
            vm.edit { p -> p.copy(clips = p.clips.map { if (it.trackId == p.mainTrack.id) it.copy(transitionIn = c.transitionIn, transitionOut = c.transitionOut, transitionMs = c.transitionMs) else it }) }
            vm.toast("Przejścia ustawione na całej ścieżce")
        }
    }
}

// ============================================================================ napisy

private val TITLE_PRESETS = listOf(
    TitleClip(text = "Tytuł", startMs = 0, size = 0.11f, style = TitleStyle.SHADOW, animation = TitleAnimation.POP),
    TitleClip(text = "Podpis", startMs = 0, y = 0.86f, size = 0.05f, style = TitleStyle.SUBTITLE, bold = false, animation = TitleAnimation.FADE),
    TitleClip(text = "Imię Nazwisko\nRola", startMs = 0, x = 0.3f, y = 0.8f, size = 0.05f, style = TitleStyle.LOWER_THIRD, align = TitleAlign.START, animation = TitleAnimation.SLIDE_UP),
    TitleClip(text = "KONIEC", startMs = 0, size = 0.12f, style = TitleStyle.OUTLINE, animation = TitleAnimation.FADE),
    TitleClip(text = "Pisz…", startMs = 0, size = 0.07f, style = TitleStyle.BOX, animation = TitleAnimation.TYPEWRITER),
)

@Composable
private fun TitlePanel(vm: EditorViewModel, t: TitleClip, onClose: () -> Unit) {
    var text by remember(t.id) { mutableStateOf(t.text) }
    LaunchedEffect(t.text) { if (t.text != text) text = t.text }
    fun set(n: TitleClip, live: Boolean = false) = vm.updateTitle(n, live)
    Panel("Napis", onClose) {
        DarkField(text, { text = it; set(t.copy(text = it), live = true) }, "Treść", singleLine = false, minLines = 2)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TITLE_PRESETS.forEach { pr ->
                PillButton(pr.style.label + " · " + pr.animation.label, primary = false) {
                    vm.commitLive()
                    set(pr.copy(id = t.id, text = t.text, startMs = t.startMs, durationMs = t.durationMs, color = t.color))
                }
            }
        }
        Group("Wygląd") {
            Chips(Icons.Rounded.FormatSize, "Styl", TitleStyle.entries, t.style, { it.label }) { set(t.copy(style = it)) }
            SliderRow(Icons.Rounded.FormatSize, "Rozmiar", t.size, 0.02f..0.3f, { "${(it * 1000).roundToInt() / 10f}%" }, default = 0.07f, onChange = { set(t.copy(size = it), live = true) }, onCommit = { vm.commitLive() })
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Rounded.FormatBold, "Pogrubienie", active = t.bold, size = 38.dp) { set(t.copy(bold = !t.bold)) }
                RoundIcon(Icons.Rounded.FormatItalic, "Kursywa", active = t.italic, size = 38.dp) { set(t.copy(italic = !t.italic)) }
                Spacer(Modifier.width(8.dp))
                RoundIcon(Icons.Rounded.FormatAlignLeft, "Do lewej", active = t.align == TitleAlign.START, size = 38.dp) { set(t.copy(align = TitleAlign.START)) }
                RoundIcon(Icons.Rounded.FormatAlignCenter, "Do środka", active = t.align == TitleAlign.CENTER, size = 38.dp) { set(t.copy(align = TitleAlign.CENTER)) }
                RoundIcon(Icons.Rounded.FormatAlignRight, "Do prawej", active = t.align == TitleAlign.END, size = 38.dp) { set(t.copy(align = TitleAlign.END)) }
            }
            Text("Kolor tekstu", color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp))
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Palette.TitleColors.forEach { col -> ColorDot(col, t.color == col) { set(t.copy(color = col)) } }
            }
            Text("Kolor tła / konturu", color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp))
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Palette.AccentColors.forEach { col -> ColorDot(col, t.accent == col) { set(t.copy(accent = col)) } }
            }
        }
        Group("Animacja") {
            Chips(Icons.Rounded.Animation, "Pojawianie się", TitleAnimation.entries, t.animation, { it.label }) { set(t.copy(animation = it)) }
            SliderRow(Icons.Rounded.Timer, "Czas animacji", t.animationMs / 1000f, 0.1f..2f, { String.format(Locale.ROOT, "%.1f s", it) }, default = 0.4f, onChange = { set(t.copy(animationMs = (it * 1000).toLong()), live = true) }, onCommit = { vm.commitLive() })
        }
        Group("Czas") {
            SliderRow(Icons.Rounded.Timer, "Długość", t.durationMs / 1000f, 0.5f..30f, { String.format(Locale.ROOT, "%.1f s", it) }, default = 3f, onChange = { set(t.copy(durationMs = (it * 1000).toLong()), live = true) }, onCommit = { vm.commitLive() })
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Od głowicy", primary = false) { set(t.copy(startMs = vm.playheadMs)) }
                PillButton("Do końca filmu", primary = false) { set(t.copy(durationMs = (vm.project.durationMs - t.startMs).coerceAtLeast(500))) }
                PillButton("Wyśrodkuj", primary = false) { set(t.copy(x = 0.5f, y = 0.5f)) }
            }
        }
        Group(null) { ActionRow(Icons.Rounded.Delete, "Usuń napis", danger = true) { vm.deleteSelection(ripple = false) } }
    }
}

// ============================================================================ projekt

@Composable
private fun ProjectPanel(vm: EditorViewModel, platform: PlatformActions, onClose: () -> Unit) {
    val p = vm.project
    val s = p.settings
    var name by remember(p.id) { mutableStateOf(p.name) }
    Panel("Projekt", onClose) {
        DarkField(name, { name = it; if (it.isNotBlank()) vm.editLive { pr -> pr.copy(name = it.trim()) } }, "Nazwa projektu")
        Group("Tło kadru") {
            Chips(Icons.Rounded.Palette, "Tam, gdzie obraz nie wypełnia kadru", com.openclipflow.app.model.BackgroundMode.entries, s.backgroundMode, { it.label }) { vm.setBackground(it) }
            if (s.backgroundMode == com.openclipflow.app.model.BackgroundMode.COLOR) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (Palette.TitleColors + listOf(0xFF263238.toInt(), 0xFF3E2723.toInt(), 0xFF1B5E20.toInt(), 0xFF0D47A1.toInt())).forEach { col ->
                        ColorDot(col, s.background == col) { vm.setBackground(com.openclipflow.app.model.BackgroundMode.COLOR, col) }
                    }
                }
            }
            if (s.backgroundMode == com.openclipflow.app.model.BackgroundMode.BLUR) {
                Text("Rozmyta, przyciemniona kopia ujęcia wypełnia pasy – np. pionowe nagranie w filmie 16:9.", color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        Group("Format kadru") {
            Box(Modifier.padding(8.dp)) { AspectPicker(s.aspect) { vm.updateSequence(s.copy(aspect = it)) } }
            Chips(Icons.Rounded.HighQuality, "Rozdzielczość", listOf(720, 1080, 1440, 2160), s.shortSide, { if (it == 2160) "4K" else "${it}p" }) { vm.updateSequence(s.copy(shortSide = it)) }
            Chips(Icons.Rounded.Speed, "Klatki na sekundę", listOf(24, 25, 30, 50, 60), s.fps, { "$it" }) { vm.updateSequence(s.copy(fps = it)) }
            InfoRow(Icons.Rounded.AspectRatio, "Wynik", "${s.resolutionLabel} · ${s.fps} kl/s")
        }
        Group("Edycja") {
            SwitchRow(Icons.Rounded.Link, "Przyciąganie", "Klipy przyklejają się do krawędzi, głowicy i znaczników", s.snapping) { vm.updateSequence(s.copy(snapping = it)) }
            SwitchRow(Icons.Rounded.Delete, "Usuwanie z przesunięciem", "Usunięcie klipu dosuwa kolejne (bez dziur)", vm.rippleDelete) { vm.setRipplePref(it) }
            SwitchRow(Icons.Rounded.GraphicEq, "Fale dźwięku na wideo", null, vm.showWaveforms) { vm.setWaveformsPref(it) }
            SwitchRow(Icons.Rounded.GridOn, "Linie pomocnicze", "Strefy bezpieczne i trójpodział w podglądzie", vm.showSafeArea) { vm.setSafeAreaPref(it) }
        }
        Group("Plik projektu") {
            ActionRow(Icons.Rounded.SaveAlt, "Zapisz plik projektu…", "Kopia zapasowa .ocf.json (bez plików multimedialnych)") { vm.exportProjectFile() }
            ActionRow(Icons.Rounded.FileDownload, "Otwórz plik projektu…") { platform.openProjectFile() }
        }
        Group("Podsumowanie") {
            InfoRow(Icons.Rounded.Timer, "Długość", TimeFormat.duration(p.durationMs))
            InfoRow(Icons.Rounded.Movie, "Elementy", "${p.clips.size} klipów · ${p.titles.size} napisów · ${p.assets.size} plików")
        }
    }
}

// ============================================================================ eksport

@Composable
private fun ExportPanel(vm: EditorViewModel, onClose: () -> Unit) {
    val s = vm.exportSettings
    val seq = s.resolve(vm.project.settings)
    val state = vm.exportState
    Panel("Eksport", { vm.resetExport(); onClose() }) {
        when (state) {
            is ExportState.Running -> {
                Group(null) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Renderowanie… ${(state.progress * 100).roundToInt()}%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { state.progress }, color = Color.White, trackColor = Color.White.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Text("Możesz zamknąć ten panel – eksport trwa dalej. Nie zamykaj aplikacji.", color = Palette.TextDim, fontSize = 12.sp)
                    }
                }
                PillButton("Przerwij", danger = true) { vm.cancelExport() }
            }
            is ExportState.Done -> {
                Group(null) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Gotowe!", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${state.output.file.name} · ${TimeFormat.bytes(state.output.sizeBytes)} · ${TimeFormat.duration(state.output.durationMs)}" +
                                if (state.output.galleryUri != null) "\nZapisano w galerii (Filmy/OpenClipFlow)" else "",
                            color = Palette.TextDim,
                            fontSize = 12.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Udostępnij", icon = Icons.Rounded.Share) { vm.shareExport() }
                    PillButton("Zapisz jako…", primary = false, icon = Icons.Rounded.SaveAlt) { vm.saveExportAs() }
                }
                PillButton("Eksportuj ponownie", primary = false) { vm.resetExport() }
            }
            else -> {
                if (state is ExportState.Failed) {
                    Text(state.message, color = Palette.Record, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Record.copy(alpha = 0.12f)).padding(12.dp))
                }
                Group("Format") {
                    CompactChoice(Icons.Rounded.Movie, "Plik", ExportFormat.entries, s.format, { it.label }, { if (it == ExportFormat.VIDEO) "MP4" else "M4A" }) { vm.updateExportSettings(s.copy(format = it)) }
                }
                if (s.format == ExportFormat.VIDEO) {
                    Group("Obraz") {
                        Chips(Icons.Rounded.HighQuality, "Rozdzielczość", ExportSettings.RESOLUTIONS, s.shortSide, { if (it == 0) "Projekt (${vm.project.settings.shortSide}p)" else if (it == 2160) "4K" else "${it}p" }) { vm.updateExportSettings(s.copy(shortSide = it)) }
                        Chips(Icons.Rounded.Speed, "Klatki/s", ExportSettings.FRAME_RATES, s.fps, { if (it == 0) "Projekt (${vm.project.settings.fps})" else "$it" }) { vm.updateExportSettings(s.copy(fps = it)) }
                        Chips(Icons.Rounded.Tune, "Jakość", ExportQuality.entries, s.quality, { it.label }) { vm.updateExportSettings(s.copy(quality = it)) }
                        CompactChoice(Icons.Rounded.Videocam, "Kodek", VideoCodec.entries, s.codec, { if (it == VideoCodec.HEVC) "H.265 – mniejszy plik, nowsze urządzenia" else "H.264 – działa wszędzie" }, { it.label }) { vm.updateExportSettings(s.copy(codec = it)) }
                    }
                }
                Group("Podsumowanie") {
                    if (s.format == ExportFormat.VIDEO) InfoRow(Icons.Rounded.AspectRatio, "Wynik", "${seq.resolutionLabel} · ${seq.fps} kl/s · ${(s.videoBitrate(vm.project.settings) / 100_000) / 10f} Mb/s")
                    InfoRow(Icons.Rounded.Timer, "Długość", TimeFormat.duration(vm.project.durationMs))
                    InfoRow(Icons.Rounded.Info, "Szacowany rozmiar", "≈ " + TimeFormat.bytes(s.estimatedBytes(vm.project.settings, vm.project.durationMs)))
                }
                PillButton("Eksportuj", icon = Icons.Rounded.FileDownload, enabled = vm.project.durationMs > 0, modifier = Modifier.fillMaxWidth()) { vm.startExport() }
            }
        }
    }
}

// ============================================================================ ścieżki

@Composable
private fun TracksPanel(vm: EditorViewModel, onClose: () -> Unit) {
    val p = vm.project
    Panel("Ścieżki", onClose) {
        Text("Ścieżki wideo leżą jedna nad drugą – wyższa przykrywa niższą. Żeby widzieć dwa ujęcia naraz, użyj narzędzia Układ (obok siebie, jedno nad drugim, okienko). Ścieżki audio przyjmują tylko dźwięk.", color = Palette.TextDim, fontSize = 11.sp)
        Group("Obraz (od góry)") {
            (p.videoTracks.drop(1).reversed() + p.mainTrack).forEach { t -> TrackRow(vm, t, main = t.id == p.mainTrack.id) }
        }
        Group("Dźwięk") {
            p.audioTracks.forEach { t -> TrackRow(vm, t, main = false) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Ścieżka wideo", primary = false, icon = Icons.Rounded.AddCircle) { vm.addTrack(TrackKind.VIDEO) }
            PillButton("Ścieżka audio", primary = false, icon = Icons.Rounded.AddCircle) { vm.addTrack(TrackKind.AUDIO) }
        }
    }
}

@Composable
private fun TrackRow(vm: EditorViewModel, t: com.openclipflow.app.model.Track, main: Boolean) {
    val count = vm.project.clips.count { it.trackId == t.id }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        com.openclipflow.app.ui.components.RowIcon(trackIcon(t, main))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(t.name + if (main) " (główna)" else "", fontSize = 13.sp, maxLines = 1)
            Text("$count klipów", fontSize = 10.sp, color = Palette.TextDim)
        }
        RoundIcon(Icons.Rounded.CallSplit, "Podziel tę ścieżkę", size = 34.dp, enabled = t.id in vm.tracksUnderPlayhead()) { vm.splitTrack(t.id) }
        if (t.kind == TrackKind.VIDEO) RoundIcon(if (t.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (t.hidden) "Pokaż" else "Ukryj", active = t.hidden, size = 34.dp) { vm.updateTrack(t.id) { it.copy(hidden = !it.hidden) } }
        RoundIcon(if (t.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, if (t.muted) "Włącz dźwięk" else "Wycisz", active = t.muted, size = 34.dp) { vm.updateTrack(t.id) { it.copy(muted = !it.muted) } }
        RoundIcon(if (t.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (t.locked) "Odblokuj" else "Zablokuj", active = t.locked, size = 34.dp) { vm.updateTrack(t.id) { it.copy(locked = !it.locked) } }
        if (!main) RoundIcon(Icons.Rounded.Delete, "Usuń ścieżkę", size = 34.dp, tint = Palette.Record) { vm.removeTrack(t.id) }
    }
}

// ============================================================================ ustawienia aplikacji

@Composable
fun SettingsPanel(vm: EditorViewModel) {
    Panel("Ustawienia", { vm.panel = PanelKind.NONE }) {
        UpdateBanner(vm)
        Group("Interfejs") {
            SwitchRow(Icons.Rounded.Vibration, "Wibracje", "Delikatna haptyka przy dotyku przycisków", vm.haptics) { vm.setHapticsPref(it) }
            SwitchRow(Icons.Rounded.TouchApp, "Linie pomocnicze w podglądzie", null, vm.showSafeArea) { vm.setSafeAreaPref(it) }
            SwitchRow(Icons.Rounded.GraphicEq, "Fale dźwięku na klipach wideo", null, vm.showWaveforms) { vm.setWaveformsPref(it) }
        }
        Group("Aktualizacje") {
            var auto by remember { mutableStateOf(vm.settings.autoUpdateCheck) }
            SwitchRow(Icons.Rounded.SystemUpdate, "Sprawdzaj automatycznie", "Raz na kilka godzin, przy starcie aplikacji", auto) { auto = it; vm.settings.autoUpdateCheck = it }
            ActionRow(
                Icons.Rounded.Refresh,
                "Sprawdź teraz",
                when (val u = vm.update) {
                    UpdateState.Checking -> "Sprawdzanie…"
                    UpdateState.UpToDate -> "Masz najnowszą wersję"
                    is UpdateState.Failed -> u.message
                    is UpdateState.Available -> "Dostępna wersja ${u.info.version}"
                    else -> "Zainstalowana wersja ${UpdateManager.currentVersion}"
                },
            ) { vm.checkUpdates() }
        }
        Group("O aplikacji") {
            InfoRow(Icons.Rounded.Info, "Wersja", UpdateManager.currentVersion + if (BuildConfig.RELEASE_SIGNED) "" else " (podpis testowy)")
            InfoRow(Icons.Rounded.Movie, "Silnik", "Media3 Transformer · efekty GPU · H.264 / H.265 / AAC")
            ActionRow(Icons.Rounded.Person, "Pomysł i wykonanie", "Adam Rędzikowski · redzikowski.com") { vm.openUrl("https://redzikowski.com") }
            ActionRow(Icons.Rounded.LocalCafe, "Postaw kawę", "Wesprzyj rozwój na buycoffee.to") { vm.openUrl("https://buycoffee.to/adamredzikowski") }
        }
    }
}
