package com.openclipflow.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.openclipflow.app.model.TimeFormat
import com.openclipflow.app.model.TrackKind
import com.openclipflow.app.ui.components.ActionRow
import com.openclipflow.app.ui.components.DarkDialog
import com.openclipflow.app.ui.components.PillButton
import com.openclipflow.app.ui.theme.Palette

/** Okna związane ze ścieżkami: wybór ścieżki do cięcia i menu nagłówka ścieżki. */
@Composable
fun TrackDialogs(vm: EditorViewModel, platform: com.openclipflow.app.PlatformActions) {
    if (vm.splitChooser) SplitChooser(vm)
    if (vm.layoutChooser) LayoutChooser(vm)
    vm.trackMenu?.let { TrackMenu(vm, platform, it) }
}

/** Miniatura układu: prostokąty obszarów w kadrze o proporcjach projektu; [accent] – podświetlony. */
@Composable
fun LayoutThumb(frames: List<com.openclipflow.app.model.Frame>, aspect: Float, selected: Boolean, accent: Int = -1, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val fg = if (selected) Palette.Ink else androidx.compose.ui.graphics.Color.White
    androidx.compose.foundation.Canvas(modifier.then(androidx.compose.ui.Modifier.aspectRatioSafe(aspect))) {
        drawRoundRect(fg.copy(alpha = 0.25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        frames.forEachIndexed { i, f ->
            val inset = 1.5.dp.toPx()
            drawRoundRect(
                fg.copy(alpha = if (accent < 0 || accent == i) 0.95f else 0.55f),
                topLeft = androidx.compose.ui.geometry.Offset(f.x * size.width + inset, f.y * size.height + inset),
                size = androidx.compose.ui.geometry.Size((f.w * size.width - 2 * inset).coerceAtLeast(1f), (f.h * size.height - 2 * inset).coerceAtLeast(1f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}

private fun androidx.compose.ui.Modifier.aspectRatioSafe(a: Float) = this.aspectRatio(a.coerceIn(0.3f, 3f))

@Composable
private fun LayoutChooser(vm: EditorViewModel) {
    val close = { vm.layoutChooser = false }
    val s = vm.project.settings
    val aspect = s.width.toFloat() / s.height
    DarkDialog("Układ dwóch ujęć", onDismiss = close, buttons = { PillButton("Zamknij", primary = false, onClick = close) }) {
        Text(
            "Ustawia ujęcie ze ścieżki Wideo 1 i ujęcie ze ścieżki nad nią, które są pod głowicą. Każde z nich możesz potem dopracować w zakładce Kadr.",
            color = Palette.TextDim,
            fontSize = 12.sp,
        )
        com.openclipflow.app.model.SplitLayout.entries.forEach { l ->
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f))
                    .clickable { close(); vm.applySplitLayout(l) }
                    .padding(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                val frames = if (l == com.openclipflow.app.model.SplitLayout.RESET) listOf(com.openclipflow.app.model.Frame.FULL) else listOf(l.main.frame, l.upper.frame)
                LayoutThumb(frames, aspect, selected = false, modifier = androidx.compose.ui.Modifier.height(40.dp))
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(14.dp))
                Text(l.label, color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp)
            }
        }
    }
}

private fun trackLabel(vm: EditorViewModel, id: String): String =
    if (id == TITLES_LANE) "Napisy" else vm.project.track(id)?.let { t -> t.name + if (t.id == vm.project.mainTrack.id) " (główna)" else "" } ?: id

@Composable
private fun SplitChooser(vm: EditorViewModel) {
    val close = { vm.splitChooser = false }
    val under = vm.tracksUnderPlayhead()
    DarkDialog("Podziel w ${TimeFormat.short(vm.playheadMs)}", onDismiss = close, buttons = { PillButton("Anuluj", primary = false, onClick = close) }) {
        ActionRow(Icons.Rounded.CallSplit, "Wszystkie ścieżki", "Cięcie przez cały montaż (${under.size} ścieżki)") {
            close()
            vm.splitAtPlayhead()
        }
        under.forEach { id ->
            val t = vm.project.track(id)
            val icon = if (t == null) Icons.Rounded.TextFields else trackIcon(t, t.id == vm.project.mainTrack.id)
            ActionRow(icon, "Tylko: ${trackLabel(vm, id)}", "Pozostałe ścieżki zostają nietknięte") {
                close()
                vm.splitTrack(id)
            }
        }
    }
}

@Composable
private fun TrackMenu(vm: EditorViewModel, platform: com.openclipflow.app.PlatformActions, id: String) {
    val close = { vm.trackMenu = null }
    val t = vm.project.track(id)
    val main = t?.id == vm.project.mainTrack.id
    val canSplit = id in vm.tracksUnderPlayhead()
    DarkDialog(trackLabel(vm, id), onDismiss = close, buttons = { PillButton("Zamknij", primary = false, onClick = close) }) {
        ActionRow(
            Icons.Rounded.CallSplit,
            "Podziel tę ścieżkę",
            if (canSplit) "W miejscu głowicy (${TimeFormat.short(vm.playheadMs)}), tylko ta ścieżka" else "Głowica nie przecina tu żadnego klipu",
            enabled = canSplit,
        ) {
            close()
            vm.splitTrack(id)
        }
        ActionRow(Icons.Rounded.SelectAll, "Zaznacz wszystko na ścieżce") {
            close()
            vm.selectTrackClips(id)
        }
        if (t == null) {
            ActionRow(Icons.Rounded.TextFields, "Dodaj napis w miejscu głowicy") {
                close()
                vm.addTitle()
            }
            return@DarkDialog
        }
        ActionRow(Icons.Rounded.Add, "Dodaj pliki na tę ścieżkę", if (t.kind == TrackKind.AUDIO) "Muzyka i nagrania – w miejscu głowicy" else "Filmy i zdjęcia – w miejscu głowicy") {
            close()
            vm.importTrackId = t.id
            platform.pickMedia(ImportTarget.TRACK, if (t.kind == TrackKind.AUDIO) arrayOf("audio/*") else arrayOf("video/*", "image/*"))
        }
        ActionRow(Icons.Rounded.SpaceBar, "Usuń luki na ścieżce") {
            close()
            vm.closeGaps(t.id)
        }
        ActionRow(if (t.muted) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, if (t.muted) "Włącz dźwięk" else "Wycisz ścieżkę") {
            vm.updateTrack(t.id) { it.copy(muted = !it.muted) }
        }
        if (t.kind == TrackKind.VIDEO) {
            ActionRow(if (t.hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (t.hidden) "Pokaż obraz" else "Ukryj obraz") {
                vm.updateTrack(t.id) { it.copy(hidden = !it.hidden) }
            }
        }
        ActionRow(if (t.locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, if (t.locked) "Odblokuj" else "Zablokuj", "Zablokowanej ścieżki nie da się przypadkiem przesunąć ani pociąć") {
            vm.updateTrack(t.id) { it.copy(locked = !it.locked) }
        }
        ActionRow(Icons.Rounded.Layers, "Wszystkie ścieżki…") {
            close()
            vm.panel = PanelKind.TRACKS
        }
        if (!main) {
            ActionRow(Icons.Rounded.Delete, "Usuń ścieżkę", "Razem z klipami", danger = true) {
                close()
                vm.removeTrack(t.id)
            }
        }
        if (main) Text("Główna ścieżka obrazu – tło całego filmu.", color = Palette.TextDim, fontSize = 11.sp)
    }
}
