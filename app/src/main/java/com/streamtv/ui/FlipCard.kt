package com.streamtv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.streamtv.data.Channel

private val CardWhite = Color.White
private val CardMuted = Color(0xFFB5B5BA)
private val CardGlass = Color(0xF01C1C1E)
private val CardBorder = Color(0x33FFFFFF)
private val CardWidth = 288.dp

/** What the front of the card offers for a channel. [onPick] gets "favorite", "history", "hide", "lock" or "rename". */
class CardOptions(val channel: Channel, val starred: Boolean, val locked: Boolean, val inHistory: Boolean, val onPick: (String) -> Unit)

/** The same card with other rows: a heading, then (label, icon, id) rows; [onPick] gets the id. */
class CardMenu(
    val heading: String, val rows: List<Triple<String, String, String>>,
    /** Where the entry is on screen: the card opens over it, as wide as the list. */
    val anchor: androidx.compose.ui.geometry.Rect? = null,
    val onPick: (String) -> Unit
)

/** What the back of the card shows: who it is about, what it asks, and what went wrong last time. */
data class PinFace(val heading: String, val message: String, val error: String?)

/** A yes/no question on the back of the card, used when no PIN is set (hiding a channel, removing a playlist). */
class CardQuestion(val heading: String, val question: String, val confirmLabel: String, val action: () -> Unit)

/** The words on the back of the card for a PIN question. */
fun pinFace(prompt: PinPrompt, wrong: Boolean): PinFace {
    val message = when (prompt.purpose) {
        "lock" -> "Please enter the PIN to lock this channel"
        "hide" -> "Please enter the PIN to hide this channel"
        "unlock" -> "Please enter the PIN to unlock this channel"
        "open" -> "Please enter the PIN to open this channel"
        "verify" -> "Enter your current PIN"
        "set" -> when {
            prompt.channel == null -> "Choose a new 4-digit PIN"
            prompt.then == "hide" -> "Choose a 4-digit PIN to hide this channel"
            else -> "Choose a 4-digit PIN to lock this channel"
        }
        "repeat" -> "Enter the same PIN again to confirm"
        else -> prompt.reason ?: "Please enter the PIN to continue"
    }
    val error = when {
        prompt.mismatch -> "The PINs did not match"
        wrong -> "Wrong PIN"
        else -> null
    }
    return PinFace(heading = prompt.channel?.name ?: prompt.label ?: "Parental control", message = message, error = error)
}

/** requestFocus() throws while the target is not on screen yet; wait a few frames for it. */
private suspend fun FocusRequester.focusWhenReady() {
    repeat(20) {
        if (runCatching { requestFocus() }.isSuccess) return
        withFrameNanos { }
    }
}

/**
 * One floating card for everything about a channel. The front holds the options; the back holds
 * the PIN pad. Asking for the PIN turns the card over in place: both faces share one size and one
 * position, so only the face changes. A PIN asked for on its own (a locked channel, removing a
 * playlist, changing the PIN) opens the card already showing its back.
 */
@Composable
fun FlipCardDialog(
    /** Over the playing video: on the side of the screen, with nothing dimmed. */
    atEnd: Boolean,
    /** The PIN pad on the back; or [question], a yes/no question there instead. */
    pin: PinFace?,
    question: CardQuestion?,
    onPinSubmit: (String) -> Unit,
    onPinCancel: () -> Unit,
    onQuestionCancel: () -> Unit,
    onDismiss: () -> Unit,
    /** Nothing pressed for a while (8 s on the options, 20 s on the PIN pad): put the card away. */
    onIdle: () -> Unit,
    /** The front; null when the card only asks for the PIN. */
    options: CardOptions?,
    /** Another front with rows of its own (a side-list entry's card), used instead of [options]. */
    menu: CardMenu? = null
) {
    val hasFront = options != null || menu != null
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { if (atEnd) window?.setDimAmount(0f) }

        // The back keeps its last words while the card turns back to the front. Before the first
        // question it is laid out empty: both faces are measured from the very first frame, so the
        // card never changes height when it turns over.
        val keptFace = remember { arrayOf(PinFace(heading = "", message = "", error = null)) }
        if (pin != null) keptFace[0] = pin
        val face = pin ?: keptFace[0]
        val keptQuestion = remember { arrayOf(CardQuestion("", "", "Cancel") {}) }
        if (question != null) keptQuestion[0] = question
        val ask = question ?: keptQuestion[0]
        // Which of the two backs is in use; kept while the card turns back to the front.
        val keptMode = remember { booleanArrayOf(false) }
        if (pin != null) keptMode[0] = false else if (question != null) keptMode[0] = true
        val questionMode = keptMode[0]
        val flipped = pin != null || question != null

        val rotation = remember { Animatable(if (flipped) 180f else 0f) }
        LaunchedEffect(flipped) { rotation.animateTo(if (flipped) 180f else 0f, tween(560, easing = FastOutSlowInEasing)) }
        // The card fades in at its final size: growing in while the first row also grew into its
        // focus look read as a little stretch, as if something were wrong.
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, tween(180)) }
        // Rows animate their focus only once the card has settled; the first highlight is instant.
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(250); settled = true }
        val backVisible = rotation.value > 90f

        var digits by remember { mutableStateOf("") }
        fun type(digit: Char) {
            if (!flipped || questionMode || digits.length >= 4) return
            val next = digits + digit
            if (next.length == 4) { digits = ""; onPinSubmit(next) } else digits = next
        }
        LaunchedEffect(flipped) { digits = "" }

        // One focus target per option; the card remembers the last one focused, so turning back
        // from the PIN pad puts the white highlight where it was (on "Lock channel", say).
        val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
        val lastRow = remember { arrayOf(menu?.rows?.firstOrNull()?.third ?: "favorite") }
        val backFirst = remember { FocusRequester() }
        val questionFirst = remember { FocusRequester() }
        // Focus follows the face: wait one frame so the face just turned to can take focus.
        LaunchedEffect(flipped, questionMode) {
            withFrameNanos { }
            if (flipped) (if (questionMode) questionFirst else backFirst).focusWhenReady()
            else if (hasFront) rowFocus.getOrPut(lastRow[0]) { FocusRequester() }.focusWhenReady()
        }
        // Every key press restarts the idle clock.
        var keyTick by remember { mutableStateOf(0) }
        LaunchedEffect(keyTick, flipped) {
            kotlinx.coroutines.delay(if (flipped) 20_000L else 8_000L)
            onIdle()
        }
        // The OK press that opened this card may still be held down; ignore it until it is released.
        val armed = remember { booleanArrayOf(false) }

        // A side-list entry's card opens right over the list, at the entry, as wide as the list;
        // its first option sits where the entry was.
        val anchor = menu?.anchor
        val density = androidx.compose.ui.platform.LocalDensity.current
        Box(
            if (anchor != null) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = if (anchor != null) Alignment.TopStart else if (atEnd) Alignment.CenterEnd else Alignment.Center
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        rotationY = rotation.value
                        cameraDistance = 16f * density
                        alpha = appear.value.coerceIn(0f, 1f)
                    }
                    .then(
                        if (anchor == null) Modifier.width(CardWidth)
                        else with(density) {
                            Modifier.absoluteOffset(anchor.left.toDp(), (anchor.top.toDp() - 54.dp).coerceAtLeast(16.dp)).width(anchor.width.toDp())
                        }
                    )
                    .clip(RoundedCornerShape(28.dp)).background(CardGlass)
                    .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) keyTick++
                        val code = event.nativeKeyEvent.keyCode
                        val digit = when (code) {
                            in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> '0' + (code - android.view.KeyEvent.KEYCODE_0)
                            in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> '0' + (code - android.view.KeyEvent.KEYCODE_NUMPAD_0)
                            else -> null
                        }
                        if (digit != null) {
                            // Number keys on the remote type the PIN.
                            if (event.type == KeyEventType.KeyDown) type(digit)
                            return@onPreviewKeyEvent true
                        }
                        val ok = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter || event.key == Key.ButtonSelect
                        when {
                            !ok || armed[0] -> false
                            event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> { armed[0] = true; false }
                            else -> true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Both faces are always laid out, so the card is as big as the bigger one and never
                // changes size; the face turned away is invisible and cannot take focus.
                if (hasFront) {
                    Column(
                        Modifier.fillMaxWidth().graphicsLayer { alpha = if (backVisible) 0f else 1f }
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                    ) {
                        if (options != null) ChannelOptionsFront(options, enabled = !flipped, settled = settled, focus = rowFocus) { lastRow[0] = it }
                        else if (menu != null) {
                            Text(menu.heading, color = CardWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
                            Spacer(Modifier.height(10.dp))
                            for ((label, icon, id) in menu.rows) {
                                val requester = remember(id) { rowFocus.getOrPut(id) { FocusRequester() } }
                                OptionRow(label, icon, !flipped, settled, Modifier.focusRequester(requester), { lastRow[0] = id }) { menu.onPick(id) }
                            }
                        }
                    }
                }
                // A menu card never turns over, so it has no back and is only as tall as its rows.
                if (menu == null) Box(
                    Modifier.fillMaxWidth().graphicsLayer { rotationY = 180f; alpha = if (backVisible) 1f else 0f },
                    contentAlignment = Alignment.Center
                ) {
                    // The PIN pad sets the size of the back; the question sits in the same space.
                    Column(
                        Modifier.fillMaxWidth().graphicsLayer { alpha = if (questionMode) 0f else 1f }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PinBack(face, digits, enabled = flipped && !questionMode, first = backFirst,
                            onDigit = { type(it) }, onDelete = { digits = digits.dropLast(1) }, onCancel = onPinCancel)
                    }
                    Column(
                        Modifier.matchParentSize().graphicsLayer { alpha = if (questionMode) 1f else 0f }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        QuestionBack(ask, enabled = flipped && questionMode, first = questionFirst,
                            onCancel = onQuestionCancel, onConfirm = { ask.action() })
                    }
                }
            }
        }
    }
}

@Composable
private fun PinBack(
    face: PinFace, digits: String, enabled: Boolean, first: FocusRequester,
    onDigit: (Char) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit
) {
    Text(t(face.heading), color = CardWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
    Text(t(face.message), color = CardMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
        maxLines = 2, minLines = 2, lineHeight = 18.sp)
    Spacer(Modifier.height(10.dp))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    Box(
                        Modifier.size(13.dp).clip(CircleShape)
                            .background(if (index < digits.length) CardWhite else Color.Transparent)
                            .border(1.5.dp, CardWhite.copy(alpha = .55f), CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(if (face.error != null && digits.isEmpty()) t(face.error) else " ", color = Color(0xFFFF8B7B), fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            for (row in listOf("123", "456", "789", "<0x")) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (key in row) {
                        PinKey(
                            label = when (key) { '<' -> "⌫"; 'x' -> "✕"; else -> key.toString() },
                            enabled = enabled,
                            modifier = if (key == '1') Modifier.focusRequester(first) else Modifier
                        ) {
                            when (key) {
                                '<' -> onDelete()
                                'x' -> onCancel()
                                else -> onDigit(key)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun QuestionBack(ask: CardQuestion, enabled: Boolean, first: FocusRequester, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Text(t(ask.heading), color = CardMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(6.dp))
    Text(t(ask.question), color = CardWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center, maxLines = 2)
    Spacer(Modifier.height(24.dp))
    // Focus starts on Cancel, so a stray OK press removes nothing.
    CardChoice(t("Cancel"), danger = false, enabled = enabled, modifier = Modifier.focusRequester(first), onClick = onCancel)
    Spacer(Modifier.height(10.dp))
    CardChoice(t(ask.confirmLabel), danger = true, enabled = enabled, onClick = onConfirm)
}

/** A wide button on the back of the card; [danger] is a soft, muted red rather than a loud one. */
@Composable
private fun CardChoice(label: String, danger: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, spring(stiffness = 430f), label = "choiceScale")
    val background = when {
        danger && focused -> Color(0xFFB5544D)
        danger -> Color(0x33C0544D)
        focused -> CardWhite
        else -> Color(0x1FFFFFFF)
    }
    val text = when {
        danger && focused -> CardWhite
        danger -> Color(0xFFEFA39B)
        focused -> Color(0xFF1C1C1E)
        else -> CardWhite
    }
    Box(
        modifier.fillMaxWidth().height(48.dp).scale(scale).clip(CircleShape).background(background)
            .focusProperties { canFocus = enabled }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, spring(stiffness = 430f), label = "pinKeyScale")
    Box(
        modifier.size(40.dp).scale(scale).clip(CircleShape)
            .background(if (focused) CardWhite else Color(0x1FFFFFFF))
            .focusProperties { canFocus = enabled }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (focused) Color(0xFF1C1C1E) else CardWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The front of the card: the channel's options. */
@Composable
private fun ChannelOptionsFront(
    options: CardOptions, enabled: Boolean, settled: Boolean,
    focus: MutableMap<String, FocusRequester>, onFocused: (String) -> Unit
) {
    val row: @Composable (String, String, String) -> Unit = { label, icon, option ->
        OptionItem(label, icon, option, options, enabled, settled, focus, onFocused)
    }
    val channel = options.channel
    val starred = options.starred
    val locked = options.locked
    val inHistory = options.inHistory
    Text(channelDisplayName(channel.name), color = CardWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
    Spacer(Modifier.height(10.dp))
    // A favourite's star is filled in white; taking it out leaves the outline again.
    row(if (starred) "Remove from favorites" else "Add to favorites", if (starred) "starFilled" else "star", "favorite")
    // Only for channels that are in "Last watched".
    if (inHistory) row("Remove from history", "history", "history")
    row("Hide channel", "hide", "hide")
    row(if (locked) "Unlock channel" else "Lock channel", "lock", "lock")
    row("Rename", "edit", "rename")
}

@Composable
private fun OptionItem(
    label: String, icon: String, option: String, options: CardOptions, enabled: Boolean, settled: Boolean,
    focus: MutableMap<String, FocusRequester>, onFocused: (String) -> Unit
) {
    val requester = remember(option) { focus.getOrPut(option) { FocusRequester() } }
    OptionRow(label, icon, enabled, settled, Modifier.focusRequester(requester), { onFocused(option) }) { options.onPick(option) }
}

/** One action on the front of the card. */
@Composable
private fun OptionRow(label: String, icon: String, enabled: Boolean, settled: Boolean, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.03f else 1f,
        if (settled) spring<Float>(dampingRatio = .78f, stiffness = 420f) else snap<Float>(),
        label = "optionScale"
    )
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp).scale(scale).clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color(0xF2F2F2F7) else Color.Transparent)
            .focusProperties { canFocus = enabled }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptionIcon(icon, if (focused) Color(0xB31C1C1E) else if (icon == "starFilled") CardWhite else Color(0xB3FFFFFF), Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(t(label), color = if (focused) Color(0xFF1C1C1E) else CardWhite, fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Simple one-colour line icons for the options: "star", "hide", "history", "lock" or "edit". */
@Composable
private fun OptionIcon(kind: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(width = w * .09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            "star", "starFilled" -> {
                val path = Path()
                val outer = w * .46f
                val inner = outer * .46f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outer else inner
                    val angle = Math.toRadians(-90.0 + i * 36.0)
                    val x = w / 2f + (r * kotlin.math.cos(angle)).toFloat()
                    val y = h / 2f + (r * kotlin.math.sin(angle)).toFloat() + outer * .06f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                if (kind == "starFilled") drawPath(path, tint)
                drawPath(path, tint, style = line)
            }
            "hide" -> {
                // An eye with a line through it.
                val eye = Path().apply {
                    moveTo(w * .06f, h * .5f)
                    quadraticBezierTo(w * .5f, h * .08f, w * .94f, h * .5f)
                    quadraticBezierTo(w * .5f, h * .92f, w * .06f, h * .5f)
                    close()
                }
                drawPath(eye, tint, style = line)
                drawCircle(tint, radius = w * .12f, center = Offset(w * .5f, h * .5f), style = line)
                drawLine(tint, Offset(w * .16f, h * .86f), Offset(w * .84f, h * .14f), strokeWidth = w * .09f, cap = StrokeCap.Round)
            }
            "history" -> {
                // A clock face.
                drawCircle(tint, radius = w * .42f, center = Offset(w * .5f, h * .5f), style = line)
                drawLine(tint, Offset(w * .5f, h * .5f), Offset(w * .5f, h * .26f), strokeWidth = w * .09f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .5f, h * .5f), Offset(w * .68f, h * .6f), strokeWidth = w * .09f, cap = StrokeCap.Round)
            }
            "move" -> {
                // Up and down arrows.
                val s = w * .09f
                drawLine(tint, Offset(w * .5f, h * .1f), Offset(w * .5f, h * .9f), strokeWidth = s, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .5f, h * .1f), Offset(w * .28f, h * .32f), strokeWidth = s, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .5f, h * .1f), Offset(w * .72f, h * .32f), strokeWidth = s, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .5f, h * .9f), Offset(w * .28f, h * .68f), strokeWidth = s, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .5f, h * .9f), Offset(w * .72f, h * .68f), strokeWidth = s, cap = StrokeCap.Round)
            }
            "lock" -> {
                drawRoundRect(tint, topLeft = Offset(w * .18f, h * .44f), size = Size(w * .64f, h * .48f),
                    cornerRadius = CornerRadius(w * .1f), style = line)
                drawArc(tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * .3f, h * .1f), size = Size(w * .4f, h * .68f), style = line)
            }
            else -> {
                // A pencil over a baseline.
                drawLine(tint, Offset(w * .26f, h * .68f), Offset(w * .76f, h * .18f), strokeWidth = w * .17f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * .14f, h * .9f), Offset(w * .86f, h * .9f), strokeWidth = w * .09f, cap = StrokeCap.Round)
            }
        }
    }
}
