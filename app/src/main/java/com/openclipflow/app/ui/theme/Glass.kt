package com.openclipflow.app.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Czy wykonywać haptykę przy dotyku elementów interfejsu. */
val LocalHaptics = staticCompositionLocalOf { true }

/** Wewnątrz paska narzędzi przyciski są płaskie (bez własnego tła). */
val LocalToolbar = staticCompositionLocalOf { false }

/** Dymki z nazwą elementu pokazywane po przytrzymaniu. */
val LocalHints = staticCompositionLocalOf<HintState?> { null }

/** Płaskie, nieprzezroczyste tła (bez rozmycia – lekkie dla GPU, które i tak liczy podgląd). */
enum class GlassLevel(val tint: Color) {
    Thin(Color(0xFF1C1C1C)),
    Regular(Color(0xFF000000)),
    Thick(Color(0xFF000000)),
}

private val ActiveTint = Color.White

/** Kształt przycisków i plakietek: koło / pigułka. */
val BlockShape = RoundedCornerShape(50)

@Composable
fun Modifier.glass(shape: Shape, level: GlassLevel = GlassLevel.Thin, active: Boolean = false): Modifier {
    val bg by animateColorAsState(if (active) ActiveTint else level.tint, tween(160), label = "flatBg")
    return this.clip(shape).background(bg)
}

@Stable
class HintState {
    var text by mutableStateOf<String?>(null)
        private set
    var anchor by mutableStateOf(Rect.Zero)
        private set
    var token by mutableIntStateOf(0)
        private set

    fun show(text: String, anchor: Rect) {
        this.text = text
        this.anchor = anchor
        token++
    }

    fun hide() {
        text = null
    }
}

/** Zapewnia haptykę i dymki całemu drzewu interfejsu. */
@Composable
fun GlassScope(haptics: Boolean, content: @Composable BoxScope.() -> Unit) {
    val hints = remember { HintState() }
    CompositionLocalProvider(
        LocalHaptics provides haptics,
        LocalHints provides hints,
        androidx.compose.material3.LocalContentColor provides Color.White,
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            HintLayer(hints)
        }
    }
}

@Composable
private fun HintLayer(hints: HintState) {
    val density = LocalDensity.current
    var bubble by remember { mutableStateOf(IntSize.Zero) }
    var root by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(hints.token) {
        if (hints.text != null) {
            delay(1600)
            hints.hide()
        }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.boundsInRoot() }) {
        val text = hints.text
        AnimatedVisibility(
            visible = text != null,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.offset {
                val a = hints.anchor
                val gap = with(density) { 10.dp.roundToPx() }
                val x = (a.center.x - root.left - bubble.width / 2f).roundToInt()
                val above = (a.top - root.top - bubble.height - gap).roundToInt()
                val y = if (above > gap) above else (a.bottom - root.top + gap).roundToInt()
                val maxX = (root.width - bubble.width - gap).roundToInt().coerceAtLeast(gap)
                IntOffset(x.coerceIn(gap, maxX), y)
            },
        ) {
            Text(
                text ?: "",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .onSizeChanged { bubble = it }
                    .clip(RoundedCornerShape(50))
                    .background(Palette.Raised)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * Reaktywna powierzchnia dotyku: sprężyste wciśnięcie, haptyka, po przytrzymaniu dymek
 * z nazwą ([label]); [onLongPress] go zastępuje.
 */
@Composable
fun Modifier.reactive(
    label: String?,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val haptics = LocalHaptics.current
    val hints = LocalHints.current
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongPress)
    val currentLabel by rememberUpdatedState(label)
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "press")
    return this
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                pressed = true
                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val slop = viewConfiguration.touchSlop
                var moved = false
                var longFired = false
                try {
                    while (true) {
                        val event = if (!moved && !longFired) {
                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            longFired = true
                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val l = long
                            if (l != null) l() else currentLabel?.let { hints?.show(it, bounds) }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!moved && !longFired) click()
                            change.consume()
                            break
                        }
                        if ((change.position - down.position).getDistance() > slop) moved = true
                    }
                } finally {
                    pressed = false
                }
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (enabled) 1f else 0.35f
        }
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

/** Okrągły przycisk z ikoną; zmiana ikony – płynne przenikanie. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    badge: String? = null,
    level: GlassLevel = GlassLevel.Thin,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val iconTint by animateColorAsState(tint ?: if (active) Palette.Ink else Color.White, tween(200), label = "iconTint")
    Box(
        modifier
            .reactive(label, enabled, onLongPress, onClick)
            .size(size)
            .buttonSurface(BlockShape, level, active),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = { (fadeIn(tween(160)) + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.6f)) },
            label = "icon",
        ) { ic ->
            Icon(ic, null, tint = iconTint, modifier = Modifier.size(size * 0.5f))
        }
        if (badge != null) {
            Text(
                badge,
                color = Palette.Ink,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Palette.Amber, RoundedCornerShape(50))
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Modifier.buttonSurface(shape: Shape, level: GlassLevel, active: Boolean): Modifier {
    if (!LocalToolbar.current) return glass(shape, level, active = active)
    val bg by animateColorAsState(if (active) ActiveTint else Color.Transparent, tween(200), label = "flatBg")
    return this.clip(shape).background(bg)
}

/**
 * Przycisk narzędzia z ikoną i podpisem pod spodem (dolny pasek edytora, jak w OpenPDF).
 */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val fg by animateColorAsState(
        when {
            active -> Palette.Ink
            danger -> Palette.Record
            else -> Color.White
        },
        tween(180),
        label = "toolFg",
    )
    val bg by animateColorAsState(if (active) Color.White else Color.Transparent, tween(180), label = "toolBg")
    Column(
        modifier
            .reactive(label, enabled, onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Text(label, color = fg.copy(alpha = if (active) 1f else 0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Pasek przytwierdzony do krawędzi: czarny pas na całą szerokość. */
@Composable
fun DockBar(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    CompositionLocalProvider(LocalToolbar provides true) {
        Row(
            modifier
                .background(Color.Black)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/** Kapsuła z płaskimi przyciskami w środku. */
@Composable
fun GlassToolbar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalToolbar provides true) {
        Row(
            modifier
                .glass(RoundedCornerShape(50), GlassLevel.Thin)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/** Kropka koloru aktywności. */
@Composable
fun BoxScope.ActiveDot() {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 3.dp)
            .size(4.dp)
            .clip(CircleShape)
            .background(Color.White),
    )
}
