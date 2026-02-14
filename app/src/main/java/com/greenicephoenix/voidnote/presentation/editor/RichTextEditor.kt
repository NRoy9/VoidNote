package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.AnnotatedString
import com.greenicephoenix.voidnote.domain.model.FormatRange

/**
 * Rich Text Editor - Simplified with native text selection
 */
@Composable
fun RichTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    formats: List<FormatRange> = emptyList(),
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val customTextSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    )

    // Apply formatting to create annotated string
    val annotatedString = remember(value.text, formats) {
        if (formats.isEmpty()) {
            AnnotatedString(value.text)
        } else {
            applyFormatting(value.text, formats)
        }
    }

    val displayValue = remember(annotatedString, value.selection, value.composition) {
        TextFieldValue(
            annotatedString = annotatedString,
            selection = value.selection,
            composition = value.composition
        )
    }

    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
        BasicTextField(
            value = displayValue,
            onValueChange = { newValue ->
                onValueChange(
                    TextFieldValue(
                        text = newValue.text,
                        selection = newValue.selection,
                        composition = newValue.composition
                    )
                )
            },
            textStyle = textStyle.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            enabled = enabled,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                    innerTextField()
                }
            },
            modifier = modifier
        )
    }
}


/*package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange

/**
 * Rich Text Editor - WITH FORMATTING DISPLAY & DOUBLE-TAP
 */
@Composable
fun RichTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    formats: List<FormatRange> = emptyList(),
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val customTextSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    )

    // Apply formatting to create annotated string
    val annotatedString = remember(value.text, formats) {
        if (formats.isEmpty()) {
            AnnotatedString(value.text)
        } else {
            applyFormatting(value.text, formats)
        }
    }

    val displayValue = remember(annotatedString, value.selection, value.composition) {
        TextFieldValue(
            annotatedString = annotatedString,
            selection = value.selection,
            composition = value.composition
        )
    }

    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
        Box(
            modifier = modifier.pointerInput(value.text) {
                detectTapGestures(
                    onDoubleTap = {
                        // Find word at current cursor position
                        val text = value.text
                        val cursorPos = value.selection.start

                        if (text.isNotEmpty() && cursorPos in text.indices) {
                            // Find word boundaries
                            var start = cursorPos
                            while (start > 0 && text[start - 1].isLetterOrDigit()) {
                                start--
                            }

                            var end = cursorPos
                            while (end < text.length && text[end].isLetterOrDigit()) {
                                end++
                            }

                            // Select word
                            if (start < end) {
                                onValueChange(
                                    value.copy(selection = TextRange(start, end))
                                )
                            }
                        }
                    }
                )
            }
        ) {
            BasicTextField(
                value = displayValue,
                onValueChange = { newValue ->
                    onValueChange(
                        TextFieldValue(
                            text = newValue.text,
                            selection = newValue.selection,
                            composition = newValue.composition
                        )
                    )
                },
                textStyle = textStyle.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                enabled = enabled,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
*/