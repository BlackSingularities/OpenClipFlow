package com.openclipflow.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclipflow.app.PlatformActions
import com.openclipflow.app.R
import com.openclipflow.app.data.ProjectSummary
import com.openclipflow.app.model.AspectRatio
import com.openclipflow.app.model.TimeFormat
import com.openclipflow.app.ui.components.ActionRow
import com.openclipflow.app.ui.components.DarkDialog
import com.openclipflow.app.ui.components.DarkField
import com.openclipflow.app.ui.components.PillButton
import com.openclipflow.app.ui.components.RoundIcon
import com.openclipflow.app.ui.theme.GlassLevel
import com.openclipflow.app.ui.theme.Palette
import com.openclipflow.app.ui.theme.glass
import com.openclipflow.app.ui.theme.reactive
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(vm: EditorViewModel, platform: PlatformActions) {
    var newDialog by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ProjectSummary?>(null) }
    var renameFor by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteFor by remember { mutableStateOf<ProjectSummary?>(null) }
    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.openclipflow_brand),
                        contentDescription = null,
                        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("OpenClipFlow", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("Montuj, tnij, dodawaj napisy i eksportuj filmy", fontSize = 13.sp, color = Palette.TextDim)
                    }
                    RoundIcon(Icons.Rounded.Settings, "Ustawienia", badge = if (vm.updatePending) "1" else null) { vm.panel = PanelKind.SETTINGS }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { UpdateBanner(vm) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White)
                            .reactive("Nowy projekt z filmów") { platform.pickMedia(ImportTarget.NEW_PROJECT) }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Palette.Ink, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Nowy projekt", color = Palette.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Wybierz filmy, zdjęcia i muzykę", color = Palette.Ink.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                    Row(horizontalArrangement = RowGap) {
                        HomeAction(Icons.Rounded.Movie, "Pusty 16:9", Modifier.weight(1f)) { vm.newProject(AspectRatio.WIDE) }
                        HomeAction(Icons.Rounded.Smartphone, "Pionowy 9:16", Modifier.weight(1f)) { vm.newProject(AspectRatio.PORTRAIT) }
                        HomeAction(Icons.Rounded.PhotoLibrary, "Inny format", Modifier.weight(1f)) { newDialog = true }
                        HomeAction(Icons.Rounded.FileOpen, "Otwórz plik", Modifier.weight(1f)) { platform.openProjectFile() }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (vm.projects.isEmpty()) "Twoje projekty pojawią się tutaj. Zapisują się automatycznie." else "PROJEKTY",
                    color = if (vm.projects.isEmpty()) Palette.TextDim else Palette.Amber,
                    fontSize = if (vm.projects.isEmpty()) 13.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (vm.projects.isEmpty()) 0.sp else 1.4.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            items(vm.projects, key = { it.id }) { p ->
                ProjectCard(vm, p, onOpen = { vm.openProject(p.id) }, onLong = { menuFor = p })
            }
        }
    }
    if (newDialog) NewProjectDialog(onDismiss = { newDialog = false }) { aspect, name ->
        newDialog = false
        vm.newProject(aspect, name.ifBlank { null })
    }
    menuFor?.let { p ->
        DarkDialog(p.name, onDismiss = { menuFor = null }, buttons = { PillButton("Zamknij", primary = false) { menuFor = null } }) {
            ActionRow(Icons.Rounded.DriveFileRenameOutline, "Zmień nazwę") { renameFor = p; menuFor = null }
            ActionRow(Icons.Rounded.ContentCopy, "Duplikuj", "Kopia z tą samą osią czasu") { vm.duplicateProject(p.id); menuFor = null }
            ActionRow(Icons.Rounded.Delete, "Usuń projekt", "Pliki filmów zostają na urządzeniu", danger = true) { deleteFor = p; menuFor = null }
        }
    }
    renameFor?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        DarkDialog(
            "Zmień nazwę",
            onDismiss = { renameFor = null },
            buttons = {
                PillButton("Anuluj", primary = false) { renameFor = null }
                PillButton("Zapisz", enabled = name.isNotBlank()) { vm.renameProject(p.id, name); renameFor = null }
            },
        ) { DarkField(name, { name = it }, "Nazwa projektu") }
    }
    deleteFor?.let { p ->
        DarkDialog(
            "Usunąć „${p.name}”?",
            onDismiss = { deleteFor = null },
            buttons = {
                PillButton("Anuluj", primary = false) { deleteFor = null }
                PillButton("Usuń", danger = true) { vm.deleteProject(p.id); deleteFor = null }
            },
        ) { Text("Projekt zniknie z aplikacji. Filmy, zdjęcia i wyeksportowane pliki pozostaną w galerii.", color = Palette.TextDim, fontSize = 13.sp) }
    }
}

@Composable
private fun HomeAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.reactive(label, onClick = onClick).glass(RoundedCornerShape(20.dp), GlassLevel.Thin).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(vm: EditorViewModel, p: ProjectSummary, onOpen: () -> Unit, onLong: () -> Unit) {
    val thumb = remember(p.id, vm.thumbsVersion) { vm.store.thumb(p.id)?.asImageBitmap() }
    Column(Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onOpen, onLongClick = onLong).padding(4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp)).background(Palette.PanelHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.Movie, null, tint = Palette.TextFaint, modifier = Modifier.size(40.dp))
            }
            Text(
                TimeFormat.short(p.durationMs).substringBeforeLast('.'),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 7.dp, vertical = 3.dp),
            )
            Text(
                p.aspect,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Text(p.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
        Text(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.updatedAt)) + " · " + "${p.clipCount} el.",
            color = Palette.TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (AspectRatio, String) -> Unit) {
    var aspect by remember { mutableStateOf(AspectRatio.WIDE) }
    var name by remember { mutableStateOf("") }
    DarkDialog(
        "Nowy projekt",
        onDismiss = onDismiss,
        buttons = {
            PillButton("Anuluj", primary = false, onClick = onDismiss)
            PillButton("Utwórz") { onCreate(aspect, name) }
        },
    ) {
        DarkField(name, { name = it }, "Nazwa (opcjonalnie)")
        AspectPicker(aspect) { aspect = it }
    }
}

/** Kafelki proporcji kadru z miniaturką kształtu. */
@Composable
fun AspectPicker(selected: AspectRatio, onSelect: (AspectRatio) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AspectRatio.entries.forEach { a ->
            val sel = a == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Color.White else Color.White.copy(alpha = 0.07f))
                    .reactive(a.label) { onSelect(a) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                    val w = if (a.w >= a.h) 30f else 30f * a.w / a.h
                    val h = if (a.w >= a.h) 30f * a.h / a.w else 30f
                    Box(Modifier.size(w.dp, h.dp).clip(RoundedCornerShape(3.dp)).background(if (sel) Palette.Ink else Color.White.copy(alpha = 0.7f)))
                }
                Spacer(Modifier.height(4.dp))
                Text(a.label, color = if (sel) Palette.Ink else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
