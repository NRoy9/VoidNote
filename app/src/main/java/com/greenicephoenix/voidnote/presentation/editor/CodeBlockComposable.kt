package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.domain.model.InlineBlockPayload
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.TextRange

// ─────────────────────────────────────────────────────────────────────────────
// CODE BLOCK COMPOSABLE  (Sprint 12)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * CodeBlockComposable — an inline code editor block embedded in a note.
 *
 * DESIGN:
 *   ┌─────────────────────────────────── [Kotlin ×] ┐
 *   │ val greeting = "Hello, Void"                  │
 *   │ println(greeting)                             │
 *   └───────────────────────────────────────────────┘
 *
 *   • Dark surface regardless of theme — code blocks always look like a terminal
 *   • Monospace font (FontFamily.Monospace) — essential for code readability
 *   • Horizontal scroll for long lines — no line wrapping inside the block
 *   • Language chip in top-right — tappable to edit, shows "×" to clear
 *   • Language field appears inline when editing — no dialog needed
 *   • Delete button (×) in the top-left when focused
 *
 * EDITING BEHAVIOUR:
 *   • The code text field fills the block width, horizontally scrollable
 *   • Tab key inserts 4 spaces (keyboard shortcut via onValueChange filter)
 *   • Auto-capitalisation and autocorrect are disabled — code is case-sensitive
 *
 * @param payload         The current Code payload (code text + language label)
 * @param onCodeChange    Called on every keystroke with the new code string
 * @param onLanguageChange Called when the language label is changed
 * @param onDelete        Called when the user taps the delete (×) button
 * @param modifier        Standard Compose modifier
 */
@Composable
fun CodeBlockComposable(
    payload: InlineBlockPayload.Code,
    onCodeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Track whether the code field is focused — shows delete button when true
    var isFocused by remember { mutableStateOf(false) }

    // Controls whether the language text field is visible for editing
    var editingLanguage by remember { mutableStateOf(false) }

    // Local mutable state for the language while the user edits it.
    // We commit to the ViewModel only when the field loses focus.
    var languageDraft by remember(payload.language) { mutableStateOf(payload.language) }

    // ── Code block colors — always dark regardless of system theme ────────────
    // This matches the universal convention for code blocks (VSCode, GitHub, etc.)
    val blockBackground = Color(0xFF1A1A1A)  // near-black surface
    val codeTextColor   = Color(0xFFD4D4D4)  // light gray — easy on eyes
    val labelColor      = Color(0xFF808080)  // muted gray for language label
    val borderColor     = Color(0xFF2D2D2D)  // subtle border

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(8.dp),
        color    = blockBackground,
        border   = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header row: icon + language label + optional delete ───────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: code icon + "CODE" label
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Code,
                        contentDescription = null,
                        tint               = labelColor,
                        modifier           = Modifier.size(14.dp)
                    )
                    Text(
                        text  = "CODE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontSize      = 10.sp
                        ),
                        color = labelColor
                    )
                }

                // Right: language chip + delete when focused
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    // Language label / edit field
                    if (editingLanguage) {
                        // Inline language input — small, monospace, right-aligned
                        BasicTextField(
                            value         = languageDraft,
                            onValueChange = { languageDraft = it },
                            textStyle     = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 11.sp,
                                color      = codeTextColor
                            ),
                            cursorBrush   = SolidColor(codeTextColor),
                            singleLine    = true,
                            modifier      = Modifier
                                .widthIn(min = 40.dp, max = 120.dp)
                                .onFocusChanged { focus ->
                                    if (!focus.isFocused) {
                                        // Commit on focus loss
                                        onLanguageChange(languageDraft.trim())
                                        editingLanguage = false
                                    }
                                },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Unspecified
                            )
                        )
                        // × to clear language
                        IconButton(
                            onClick  = {
                                languageDraft = ""
                                onLanguageChange("")
                                editingLanguage = false
                            },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Close,
                                contentDescription = "Clear language",
                                tint               = labelColor,
                                modifier           = Modifier.size(12.dp)
                            )
                        }
                    } else {
                        // Show language as a tappable chip; "Add language" if blank
                        val chipText = payload.language.ifBlank { "+ lang" }
                        Surface(
                            onClick = { editingLanguage = true },
                            shape   = RoundedCornerShape(4.dp),
                            color   = Color(0xFF2A2A2A)
                        ) {
                            Text(
                                text     = chipText,
                                style    = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 11.sp
                                ),
                                color    = if (payload.language.isBlank()) labelColor.copy(alpha = 0.6f)
                                else codeTextColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Delete block button — only visible when focused
                    if (isFocused) {
                        IconButton(
                            onClick  = onDelete,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Close,
                                contentDescription = "Delete code block",
                                tint               = Color(0xFFFF5555),  // soft red
                                modifier           = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // ── Divider between header and code ───────────────────────────────
            HorizontalDivider(color = borderColor)

            // ── Code text area ─────────────────────────────────────────────────────────
            //
            // WHY remember(block id via stable key) and NOT remember(payload.code)?
            //
            // Every keystroke calls onCodeChange → ViewModel → DB → observeBlocks() emits
            // → new payload arrives → if we key remember() on payload.code, the TextFieldValue
            // resets on every character, placing the cursor at position 0 (front of text).
            //
            // Solution: key remember() on Unit (never resets during the block's lifetime),
            // then manually sync ONLY when the payload changes from outside
            // (initial load, external edit) using a SideEffect. During active typing,
            // localValue.text == payload.code so the sync is a no-op — cursor is preserved.
            var localValue by remember { mutableStateOf(TextFieldValue(payload.code)) }

            // Sync if the payload was changed from outside this composable (e.g. initial
            // load, undo). During normal typing localValue.text already equals payload.code
            // so this block is skipped and the cursor position is never disturbed.
            val latestCode = payload.code
            LaunchedEffect(latestCode) {
                if (localValue.text != latestCode) {
                    // External change — reset with cursor at end
                    localValue = TextFieldValue(
                        text      = latestCode,
                        selection = TextRange(latestCode.length)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 80.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(Spacing.medium)
            ) {
                if (localValue.text.isEmpty() && !isFocused) {
                    Text(
                        text  = "// Start typing your code…",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 13.sp
                        ),
                        color = labelColor.copy(alpha = 0.5f)
                    )
                }

                BasicTextField(
                    value         = localValue,
                    onValueChange = { newValue ->
                        localValue = newValue        // own the cursor immediately
                        onCodeChange(newValue.text)  // push plain text to ViewModel
                    },
                    textStyle     = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 13.sp,
                        color      = codeTextColor,
                        lineHeight = 20.sp
                    ),
                    cursorBrush   = SolidColor(codeTextColor),
                    keyboardOptions = KeyboardOptions(
                        capitalization     = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType       = KeyboardType.Ascii
                    ),
                    modifier      = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }
        }
    }
}