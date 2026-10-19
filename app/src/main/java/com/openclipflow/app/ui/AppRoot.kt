package com.openclipflow.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclipflow.app.PlatformActions
import com.openclipflow.app.ui.theme.GlassLevel
import com.openclipflow.app.ui.theme.GlassScope
import com.openclipflow.app.ui.theme.Palette
import com.openclipflow.app.ui.theme.glass
import com.openclipflow.app.update.UpdateState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun AppRoot(vm: EditorViewModel, platform: PlatformActions) {
    GlassScope(haptics = vm.haptics) {
        Box(Modifier.fillMaxSize().background(Palette.Ink)) {
            when (vm.screen) {
                Screen.HOME -> HomeScreen(vm, platform)
                Screen.EDITOR -> EditorScreen(vm, platform)
            }
            if (vm.screen == Screen.HOME && vm.panel == PanelKind.SETTINGS) SettingsPanel(vm)
            BusyOverlay(vm.busy)
            Box(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 96.dp),
                contentAlignment = Alignment.BottomCenter,
            ) { MessagePill(vm) }
        }
    }
}

@Composable
private fun MessagePill(vm: EditorViewModel) {
    LaunchedEffect(vm.messageToken) {
        if (vm.message != null) {
            delay(2600)
            vm.message = null
        }
    }
    AnimatedVisibility(
        visible = vm.message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Text(
            vm.message ?: "",
            color = Palette.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable { vm.message = null }
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun BusyOverlay(text: String?) {
    AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Palette.PanelHigh).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(text ?: "", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

/**
 * Baner nowej wersji: jednym dotknięciem aplikacja pobiera APK z GitHub Releases
 * i instaluje go (wydania są podpisane tym samym kluczem).
 */
@Composable
fun UpdateBanner(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val u = vm.update
    AnimatedVisibility(visible = vm.updatePending && !vm.updateBannerHidden, modifier = modifier) {
        val (title, action) = when (u) {
            is UpdateState.Available -> "Nowa wersja ${u.info.version}" to "Aktualizuj"
            is UpdateState.Downloading -> "Pobieranie ${u.info.version} · ${(u.progress * 100).roundToInt()}%" to null
            is UpdateState.Ready -> "Wersja ${u.info.version} pobrana" to "Zainstaluj"
            is UpdateState.Installing -> "Instalowanie ${u.info.version}…" to null
            else -> "" to null
        }
        val notes = (u as? UpdateState.Available)?.info?.notes ?: (u as? UpdateState.Ready)?.info?.notes
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), GlassLevel.Thin).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (u is UpdateState.Available) "Pobierze się i zainstaluje automatycznie" else "Nie zamykaj aplikacji",
                        color = Palette.TextDim,
                        fontSize = 11.sp,
                    )
                }
                if (action != null) {
                    Text(
                        action,
                        color = Palette.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { vm.updateNow() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                if (u is UpdateState.Available) {
                    Icon(
                        Icons.Rounded.Close,
                        "Później",
                        tint = Palette.TextDim,
                        modifier = Modifier.padding(start = 6.dp).size(32.dp).clip(RoundedCornerShape(50)).clickable { vm.updateBannerHidden = true }.padding(6.dp),
                    )
                }
            }
            if (u is UpdateState.Downloading) {
                LinearProgressIndicator(
                    progress = { u.progress },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            if (!notes.isNullOrBlank()) {
                Text(notes, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 34.dp))
            }
        }
    }
}

/** Wspólny odstęp między przyciskami w rzędzie. */
val RowGap = Arrangement.spacedBy(10.dp)
