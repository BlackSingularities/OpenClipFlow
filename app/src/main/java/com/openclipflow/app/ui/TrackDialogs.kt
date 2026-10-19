package com.openclipflow.app.ui

import androidx.compose.material.icons.Icons
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
fun TrackDialogs(vm: EditorViewModel) {
    if (vm.splitChooser) SplitChooser(vm)
    vm.trackMenu?.let { TrackMenu(vm, it) }
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
private fun TrackMenu(vm: EditorViewModel, id: String) {
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
