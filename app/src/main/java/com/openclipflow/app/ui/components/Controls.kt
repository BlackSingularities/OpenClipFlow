package com.openclipflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openclipflow.app.ui.theme.BlockShape
import com.openclipflow.app.ui.theme.GlassIconButton
import com.openclipflow.app.ui.theme.GlassLevel
import com.openclipflow.app.ui.theme.MonoDigits
import com.openclipflow.app.ui.theme.Palette
import com.openclipflow.app.ui.theme.glass
import com.openclipflow.app.ui.theme.reactive

/** Okrągły przycisk z ikoną. */
@Composable
fun RoundIcon(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    badge: String? = null,
    size: Dp = 44.dp,
    tint: Color? = null,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) = GlassIconButton(icon, label, modifier, size, active, enabled, tint, badge, onLongPress = onLongPress, onClick = onClick)

/**
 * Panel z nagłówkiem i przyciskiem zamknięcia: na wąskim ekranie – na całą wysokość,
 * na szerokim – kolumna przy prawej krawędzi (podgląd i oś czasu zostają widoczne).
 */
@Composable
fun Panel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    dimBehind: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val short = maxHeight < 520.dp
        Box(
            Modifier
                .fillMaxSize()
                .then(if (dimBehind) Modifier.background(Color.Black.copy(alpha = 0.4f)) else Modifier)
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
        Column(
            modifier
                .then(
                    when {
                        wide || short -> Modifier.align(Alignment.CenterEnd).width(if (wide) 400.dp else 360.dp).fillMaxHeight()
                        else -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    },
                )
                .background(GlassLevel.Thick.tint)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                actions()
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Icons.Rounded.Close, "Zamknij", size = 38.dp, onClick = onClose)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
fun Group(
    title: String?,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Column {
        if (title != null) Row(
            Modifier
                .fillMaxWidth()
                .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(start = 4.dp, end = 4.dp, bottom = 3.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title.uppercase(), color = Palette.Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            if (collapsible) {
                Icon(
                    Icons.Rounded.ExpandMore,
                    if (expanded) "Zwiń sekcję $title" else "Rozwiń sekcję $title",
                    tint = Palette.Amber,
                    modifier = Modifier.size(18.dp).rotate(if (expanded) 0f else -90f),
                )
            }
        }
        AnimatedVisibility(visible = !collapsible || expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(vertical = 2.dp),
                content = content,
            )
        }
    }
}

@Composable
fun RowIcon(icon: ImageVector, tint: Color = Palette.Amber) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!value) }
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 13.sp, color = if (enabled) Color.White else Palette.TextFaint)
            if (subtitle != null) Text(subtitle, fontSize = 10.sp, color = Palette.TextDim, lineHeight = 12.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.scale(0.82f),
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Amber, checkedThumbColor = Palette.Ink),
        )
    }
}

/**
 * Suwak z ikoną, tytułem i wartością; dotknięcie wartości przywraca [default].
 * [onCommit] – po puszczeniu suwaka (jeden krok historii zmian zamiast setek).
 */
@Composable
fun SliderRow(
    icon: ImageVector,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    default: Float? = null,
    steps: Int = 0,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), color = if (enabled) Color.White else Palette.TextFaint)
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .then(if (default != null && value != default) Modifier.clickable { onChange(default); onCommit() } else Modifier)
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(format(value), style = MonoDigits, fontSize = 12.sp, color = Color.White)
                if (default != null && value != default) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Refresh, "Przywróć", tint = Palette.TextDim, modifier = Modifier.size(13.dp))
                }
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onCommit,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.padding(start = 40.dp).height(30.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/** Wybór w jednej linii z przełącznikiem segmentowym po prawej. */
@Composable
fun <T> CompactChoice(
    rowIcon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    short: (T) -> String = label,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(rowIcon)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label(selected), fontSize = 10.sp, lineHeight = 12.sp, color = Palette.TextDim, maxLines = 1)
        }
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f)).padding(2.dp)) {
            options.forEach { o ->
                val sel = o == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Color.White else Color.Transparent)
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { onSelect(o) })
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(short(o), fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) Palette.Ink else Palette.TextDim)
                }
            }
        }
    }
}

@Composable
fun <T> Chips(
    icon: ImageVector?,
    title: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean = true,
    hint: String? = null,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        if (icon != null && title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(icon)
                Text(title, fontSize = 14.sp, color = if (enabled) Color.White else Palette.TextFaint, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        Row(
            Modifier
                .padding(start = if (icon != null) 40.dp else 0.dp, top = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            options.forEach { o ->
                val sel = o == selected
                Text(
                    label(o),
                    color = if (sel) Palette.Ink else if (enabled) Color.White else Palette.TextFaint,
                    fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Palette.Amber else Color.White.copy(alpha = 0.07f))
                        .clickable(enabled = enabled) { onSelect(o) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
        if (hint != null) Text(hint, fontSize = 11.sp, color = Palette.TextDim, modifier = Modifier.padding(start = if (icon != null) 40.dp else 0.dp, top = 3.dp))
    }
}

/** Wiersz-akcja: ikona, tytuł, opis; dotknięcie wykonuje akcję. */
@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (danger) Palette.Record else Palette.Amber)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                color = when {
                    !enabled -> Palette.TextFaint
                    danger -> Palette.Record
                    else -> Color.White
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 11.sp, color = Palette.TextDim)
            Text(value.ifBlank { "—" }, fontSize = 13.sp, color = Color.White)
        }
    }
}

/** Przycisk-pigułka: biały (główny) albo półprzezroczysty. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> Color.White.copy(alpha = 0.08f)
        danger -> Palette.Record
        primary -> Palette.Amber
        else -> Color.White.copy(alpha = 0.1f)
    }
    val fg = when {
        !enabled -> Palette.TextFaint
        danger -> Color.White
        primary -> Palette.Ink
        else -> Color.White
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Ciemne okno dialogowe w stylu aplikacji. */
@Composable
fun DarkDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.PanelHigh)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                buttons()
            }
        }
    }
}

@Composable
fun DarkField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    number: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Palette.TextDim,
            cursorColor = Color.White,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
        ),
    )
}

/** Zakładka-chip (przełączanie sekcji panelu). */
@Composable
fun TabChip(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .reactive(label, onClick = onClick)
            .glass(BlockShape, active = selected)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Palette.Ink else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = if (selected) Palette.Ink else Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

/** Kropka koloru do wyboru. */
@Composable
fun ColorDot(color: Int, selected: Boolean, size: Dp = 30.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(if (selected) 3.dp else 2.dp)
            .clip(CircleShape)
            .background(Color(0xFF444444))
            .background(Color(color)),
    )
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
