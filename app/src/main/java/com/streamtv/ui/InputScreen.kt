package com.streamtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EntryInk = Color(0xFF000000)
private val EntryWhite = Color(0xFFFFFFFF)
private val EntryMuted = Color(0xFF99999E)
private val EntryGlass = Color(0xFF29292C)

/**
 * Full-screen text entry for TV. Floating TV keyboards sit over the lower half of the screen
 * and do not resize the window, so every control lives in the top half: the fields in the top
 * third and the buttons right below them.
 */
@Composable
fun TextEntryScreen(
    title: String,
    urlHint: String,
    initialUrl: String,
    exampleUrl: String?,
    showName: Boolean,
    saveLabel: String,
    onSave: (name: String, url: String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(initialUrl) }
    var message by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val firstField = remember { FocusRequester() }
    val urlField = remember { FocusRequester() }
    val saveButton = remember { FocusRequester() }

    fun submit() {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            message = "The address must start with http:// or https://"
            return
        }
        keyboard?.hide()
        onSave(name.trim(), cleanUrl)
    }

    BackHandler { keyboard?.hide(); onCancel() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { firstField.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    Box(Modifier.fillMaxSize().background(EntryInk)) {
        Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 72.dp, top = 28.dp)) {
            Text(t(title), color = EntryWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (showName) {
                    EntryField(
                        value = name, hint = "Name (optional)", onValueChange = { name = it },
                        modifier = Modifier.weight(.32f).focusRequester(firstField),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { runCatching { urlField.requestFocus() } })
                    )
                }
                EntryField(
                    value = url, hint = urlHint, onValueChange = { url = it; message = null },
                    modifier = Modifier.weight(.68f).focusRequester(if (showName) urlField else firstField),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        runCatching { saveButton.requestFocus() }
                    })
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryButton(saveLabel, primary = true, modifier = Modifier.focusRequester(saveButton)) { submit() }
                EntryButton("Cancel") { keyboard?.hide(); onCancel() }
                EntryButton("Paste") {
                    val pasted = clipboard.getText()?.text?.trim().orEmpty()
                    if (pasted.isEmpty()) message = "Nothing to paste: the clipboard is empty"
                    else { url = pasted; message = null }
                }
                EntryButton("Clear") { url = ""; message = null }
            }
            if (exampleUrl != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Example"), color = EntryMuted, fontSize = 14.sp)
                    Spacer(Modifier.width(10.dp))
                    EntryButton(exampleUrl, small = true) { url = exampleUrl; message = null }
                }
            }
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(t(it), color = Color(0xFFFF8B7B), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Builds the M3U address an Xtream Codes server exposes for an account. */
fun xtreamPlaylistUrl(server: String, username: String, password: String): String {
    val trimmed = server.trim().trimEnd('/')
    val base = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
    fun enc(value: String) = java.net.URLEncoder.encode(value.trim(), "UTF-8")
    return "$base/get.php?username=${enc(username)}&password=${enc(password)}&type=m3u_plus&output=ts"
}

/** Playlist addresses can carry a password; show only what identifies the playlist. */
fun playlistLabel(url: String): String = when {
    url.isBlank() -> "None yet"
    url.startsWith("local://") -> "Imported file"
    url.contains("/get.php?") -> {
        val host = url.substringAfter("://").substringBefore('/')
        val user = url.substringAfter("username=", "").substringBefore('&')
        "Xtream  ·  $host  ·  $user"
    }
    else -> url
}

/** Xtream Codes login. Same top-half layout as [TextEntryScreen]. */
@Composable
fun XtreamEntryScreen(onSave: (name: String, url: String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val nameField = remember { FocusRequester() }
    val serverField = remember { FocusRequester() }
    val userField = remember { FocusRequester() }
    val passField = remember { FocusRequester() }
    val saveButton = remember { FocusRequester() }

    fun submit() {
        if (server.isBlank() || username.isBlank() || password.isBlank()) {
            message = "Fill in the server, username and password"
            return
        }
        keyboard?.hide()
        onSave(name.trim(), xtreamPlaylistUrl(server, username, password))
    }

    BackHandler { keyboard?.hide(); onCancel() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { nameField.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    Box(Modifier.fillMaxSize().background(EntryInk)) {
        Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 72.dp, top = 28.dp)) {
            Text(t("Xtream Codes login"), color = EntryWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EntryField(
                    value = name, hint = "Name (optional)", onValueChange = { name = it },
                    modifier = Modifier.weight(.32f).focusRequester(nameField),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { runCatching { serverField.requestFocus() } })
                )
                EntryField(
                    value = server, hint = "Server  http://example.com:8080", onValueChange = { server = it; message = null },
                    modifier = Modifier.weight(.68f).focusRequester(serverField),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { runCatching { userField.requestFocus() } })
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EntryField(
                    value = username, hint = "Username", onValueChange = { username = it; message = null },
                    modifier = Modifier.weight(.5f).focusRequester(userField),
                    keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { runCatching { passField.requestFocus() } })
                )
                EntryField(
                    value = password, hint = "Password", onValueChange = { password = it; message = null },
                    modifier = Modifier.weight(.5f).focusRequester(passField),
                    keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        runCatching { saveButton.requestFocus() }
                    })
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryButton("Save & load", primary = true, modifier = Modifier.focusRequester(saveButton)) { submit() }
                EntryButton("Cancel") { keyboard?.hide(); onCancel() }
                message?.let { Text(t(it), color = Color(0xFFFF8B7B), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/**
 * Floating PIN card in the middle of the screen with its own number pad, so no system keyboard
 * is needed. The PIN is sent as soon as the fourth digit is entered.
 */
@Composable
fun PinPadScreen(title: String, error: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var digits by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    fun type(digit: Char) {
        if (digits.length >= 4) return
        val next = digits + digit
        if (next.length == 4) { digits = ""; onSubmit(next) } else digits = next
    }
    BackHandler { onCancel() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF050506))
                // Number keys on the remote work too.
                .onPreviewKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    val digit = when (code) {
                        in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> '0' + (code - android.view.KeyEvent.KEYCODE_0)
                        in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> '0' + (code - android.view.KeyEvent.KEYCODE_NUMPAD_0)
                        else -> null
                    }
                    if (digit == null) false else { if (event.type == KeyEventType.KeyDown) type(digit); true }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xF01C1C1E))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
                    .padding(horizontal = 30.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(t(title), color = EntryWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { index ->
                        Box(
                            Modifier.size(13.dp).clip(CircleShape)
                                .background(if (index < digits.length) EntryWhite else Color.Transparent)
                                .border(1.5.dp, EntryWhite.copy(alpha = .55f), CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (error != null && digits.isEmpty()) t(error) else " ", color = Color(0xFFFF8B7B), fontSize = 14.sp, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                val rows = listOf("123", "456", "789", "<0x")
                for (row in rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (key in row) {
                            PinKey(
                                label = when (key) { '<' -> "⌫"; 'x' -> "✕"; else -> key.toString() },
                                modifier = if (key == '1') Modifier.focusRequester(first) else Modifier
                            ) {
                                when (key) {
                                    '<' -> digits = digits.dropLast(1)
                                    'x' -> onCancel()
                                    else -> type(key)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, spring(stiffness = 430f), label = "pinKeyScale")
    Box(
        modifier.size(52.dp).scale(scale).clip(CircleShape)
            .background(if (focused) EntryWhite else Color(0x1FFFFFFF))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (focused) EntryInk else EntryWhite, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** One field in the top half of the screen: a channel name, or a 4-digit PIN when [pin] is true. */
@Composable
fun SingleFieldScreen(
    title: String, hint: String, initial: String, pin: Boolean, error: String?, saveLabel: String,
    onSave: (String) -> Unit, onCancel: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    val keyboard = LocalSoftwareKeyboardController.current
    val field = remember { FocusRequester() }
    val saveButton = remember { FocusRequester() }
    BackHandler { keyboard?.hide(); onCancel() }
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { field.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    Box(Modifier.fillMaxSize().background(EntryInk)) {
        Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 72.dp, top = 28.dp)) {
            Text(t(title), color = EntryWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            EntryField(
                value = value, hint = hint,
                onValueChange = { typed -> value = if (pin) typed.filter { it.isDigit() }.take(4) else typed },
                modifier = Modifier.fillMaxWidth(if (pin) .3f else .6f).focusRequester(field),
                keyboardOptions = KeyboardOptions(keyboardType = if (pin) KeyboardType.NumberPassword else KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    runCatching { saveButton.requestFocus() }
                }),
                masked = pin
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryButton(saveLabel, primary = true, modifier = Modifier.focusRequester(saveButton)) { keyboard?.hide(); onSave(value) }
                EntryButton("Cancel") { keyboard?.hide(); onCancel() }
                error?.let { Text(t(it), color = Color(0xFFFF8B7B), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun EntryField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    masked: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = TextStyle(color = EntryWhite, fontSize = 18.sp),
        cursorBrush = SolidColor(EntryWhite),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(EntryGlass)
                    .border(if (focused) 2.dp else 1.dp, EntryWhite.copy(alpha = if (focused) .85f else .10f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(t(hint), color = EntryMuted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        }
    )
}

@Composable
private fun EntryButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(stiffness = 430f), label = "entryButtonScale")
    Box(
        modifier.scale(scale).clip(CircleShape)
            .background(if (focused) EntryWhite else EntryGlass)
            .border(1.dp, EntryWhite.copy(alpha = if (primary) .7f else .12f), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = if (small) 16.dp else 24.dp, vertical = if (small) 8.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            t(text),
            color = if (focused) EntryInk else EntryWhite,
            fontSize = if (small) 14.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
