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
import androidx.compose.ui.text.style.TextOverflow
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
            Text(title, color = EntryWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                    Text("Example", color = EntryMuted, fontSize = 14.sp)
                    Spacer(Modifier.width(10.dp))
                    EntryButton(exampleUrl, small = true) { url = exampleUrl; message = null }
                }
            }
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFFF8B7B), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    keyboardActions: KeyboardActions
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
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
                if (value.isEmpty()) Text(hint, color = EntryMuted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            text,
            color = if (focused) EntryInk else EntryWhite,
            fontSize = if (small) 14.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
