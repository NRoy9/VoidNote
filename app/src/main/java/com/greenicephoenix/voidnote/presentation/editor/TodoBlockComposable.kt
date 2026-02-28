package com.greenicephoenix.voidnote.presentation.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.model.InlineBlockPayload
import com.greenicephoenix.voidnote.domain.model.TodoItem
import com.greenicephoenix.voidnote.presentation.theme.Spacing

/**
 * TodoBlockComposable — Polished interactive checklist block.
 *
 * DESIGN (Nothing aesthetic):
 * ┌──────────────────────────────────────────────┐
 * │ ☑ CHECKLIST                   2 / 5    [🗑] │  ← header row
 * ├──────────────────────────────────────────────┤
 * │ | ☑ Buy milk (strikethrough)          [×]   │  ← checked item
 * │ | ☐ Buy eggs                          [×]   │  ← unchecked item
 * │ |  + Add item                               │  ← ghost button
 * └──────────────────────────────────────────────┘
 *   ↑ Left accent bar (animates to primary color when all done)
 *
 * ANIMATIONS:
 * - Items slide + fade in when added (expandVertically + fadeIn)
 * - Items collapse + fade out when deleted (shrinkVertically + fadeOut)
 * - Left accent bar color transitions outline → primary when all checked
 * - Text color fades on check/uncheck (300ms)
 * - Delete button fades in on focus (200ms) — no layout jump
 *
 * @param block            The TODO block data to display.
 * @param onToggleItem     Called when the user taps a checkbox. Param: itemId.
 * @param onAddItem        Called when user taps "Add item" or presses Enter.
 * @param onUpdateItemText Called when user edits item text. Params: itemId, newText.
 * @param onDeleteItem     Called when user taps × on an item. Param: itemId.
 * @param onDeleteBlock    Called when user taps the block-level trash icon.
 * @param modifier         Compose modifier.
 */
@Composable
fun TodoBlockComposable(
    block: InlineBlock,
    onToggleItem: (itemId: String) -> Unit,
    onAddItem: () -> Unit,
    onUpdateItemText: (itemId: String, newText: String) -> Unit,
    onDeleteItem: (itemId: String) -> Unit,
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todoPayload = block.payload as? InlineBlockPayload.Todo ?: return
    val items = todoPayload.items.sortedBy { it.sortOrder }

    val checkedCount = items.count { it.isChecked }
    val totalCount = items.size
    val allChecked = totalCount > 0 && checkedCount == totalCount

    // ── Left accent bar color ──────────────────────────────────────────────
    // Animates from muted outline → primary color when every item is checked.
    // A quiet, satisfying signal of completion.
    val accentColor by animateColorAsState(
        targetValue = if (allChecked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        animationSpec = tween(durationMillis = 400),
        label = "todoAccentBar"
    )

    // Track whether any item in this block has keyboard focus.
    // When true, per-item × delete buttons become visible.
    var isBlockFocused by remember { mutableStateOf(false) }

    // ── Focus management for Enter key → next item ──────────────────────────
    // When the user presses Enter on a checklist item, we call onAddItem() to
    // create a new item at the end. The new item appears after the Flow emits
    // the updated list (async). We detect the growth via LaunchedEffect and
    // fire a FocusRequester on the last item so the cursor moves there.
    //
    // shouldFocusLastItem: set to true when Enter is pressed, reset after focus fires.
    // lastItemFocusRequester: attached to the last TodoItemRow in the list.
    var shouldFocusLastItem by remember { mutableStateOf(false) }
    val lastItemFocusRequester = remember { FocusRequester() }

    // Watch the items count. When it grows (new item added) AND we're expecting
    // a focus jump, fire the focus request on the new last item.
    LaunchedEffect(items.size) {
        if (shouldFocusLastItem && items.isNotEmpty()) {
            shouldFocusLastItem = false
            try {
                lastItemFocusRequester.requestFocus()
            } catch (_: Exception) {
                // FocusRequester not yet attached — safe to ignore
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.small)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        BlockHeader(
            checkedCount = checkedCount,
            totalCount = totalCount,
            onDeleteBlock = onDeleteBlock
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── Content: accent bar + items ──────────────────────────────────────
        // height(IntrinsicSize.Min) is critical — it gives the Row a bounded
        // height so the nested Box with fillMaxHeight() works correctly.
        // Without it, the left accent bar would collapse to 0dp height.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left accent bar — 2dp pill with animated color
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(
                        color = accentColor,
                        shape = RoundedCornerShape(1.dp)
                    )
            )

            Spacer(modifier = Modifier.width(Spacing.medium))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Items ──────────────────────────────────────────────────
                // key() ensures Compose tracks each item by its stable UUID,
                // not by list position. This is essential for animations:
                // without it, adding item C at the end might animate item A.
                items.forEachIndexed { index, item ->
                    // Only the LAST item gets the focus requester.
                    // When a new item is appended, it becomes the new last item
                    // and receives the focus requester — so Enter always moves
                    // the cursor to the freshly created item.
                    val isLastItem = index == items.lastIndex
                    key(item.id) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(180)) + expandVertically(
                                animationSpec = tween(220),
                                expandFrom = Alignment.Top
                            ),
                            exit = fadeOut(tween(130)) + shrinkVertically(
                                animationSpec = tween(180),
                                shrinkTowards = Alignment.Top
                            )
                        ) {
                            TodoItemRow(
                                item = item,
                                focusRequester = if (isLastItem) lastItemFocusRequester else null,
                                onToggle = { onToggleItem(item.id) },
                                onTextChange = { newText ->
                                    onUpdateItemText(item.id, newText)
                                },
                                onDelete = {
                                    // Last item? Remove the whole block, not just item
                                    if (items.size <= 1) onDeleteBlock()
                                    else onDeleteItem(item.id)
                                },
                                onFocusChanged = { focused ->
                                    if (focused) isBlockFocused = true
                                },
                                onImeAction = {
                                    shouldFocusLastItem = true  // arm the focus jump
                                    onAddItem()                 // create the new item
                                },
                                showDeleteButton = isBlockFocused || items.size > 1
                            )
                        }
                    }
                }

                // ── Ghost "Add item" button ──────────────────────────────
                // Low-contrast, small — present but not competing with content
                TextButton(
                    onClick = onAddItem,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.alpha(0.55f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add item",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Header row for a TODO block.
 *
 * Layout:  [☑ icon]  [CHECKLIST label]  ─────  [n / total]  [🗑 delete]
 *
 * The "CHECKLIST" label uses wide letter-spacing to evoke dot-matrix printing —
 * a subtle nod to the Nothing aesthetic without requiring a custom font.
 *
 * Progress turns primary color when all items are checked, matching
 * the accent bar animation for a cohesive "completed" signal.
 */
@Composable
private fun BlockHeader(
    checkedCount: Int,
    totalCount: Int,
    onDeleteBlock: () -> Unit
) {
    val allDone = totalCount > 0 && checkedCount == totalCount

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: icon + "CHECKLIST" label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckBox,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            Text(
                text = "CHECKLIST",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp    // Wide tracking = dot-matrix character
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }

        // Right: progress counter + delete button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (totalCount > 0) {
                Text(
                    text = "$checkedCount / $totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (allDone) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            // Trash icon to delete the whole block at once.
            // Intentionally small and low-contrast — destructive but not alarming.
            IconButton(
                onClick = onDeleteBlock,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete checklist",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

/**
 * A single row inside a TODO block.
 *
 * Layout:  [ ☐ ] [ editable text ─────────────────── ] [ × ]
 *
 * KEY DESIGN DECISIONS:
 * - Delete button always occupies space (never removed from layout).
 *   It fades in/out with alpha. This prevents the text from reflowing
 *   horizontally when focus changes — a jarring UX moment avoided.
 * - Strikethrough and text color both animate on check state change,
 *   giving a polished, native-feeling interaction.
 */
@Composable
private fun TodoItemRow(
    item: TodoItem,
    focusRequester: FocusRequester? = null,   // non-null only on the last item
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onImeAction: () -> Unit,
    showDeleteButton: Boolean
) {
    // Smooth color fade on check/uncheck — 300ms feels natural
    val textColor by animateColorAsState(
        targetValue = if (item.isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "itemTextColor"
    )

    // Delete button fades in without causing layout shift
    val deleteAlpha by animateFloatAsState(
        targetValue = if (showDeleteButton) 1f else 0f,
        animationSpec = tween(200),
        label = "deleteAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox — 36dp touch target, 20dp icon (Material standard)
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (item.isChecked) Icons.Default.CheckBox
                else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (item.isChecked) "Mark incomplete" else "Mark complete",
                tint = if (item.isChecked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp)
            )
        }

        // ── Local text state — THE KEY FIX for cursor corruption ────────────
        //
        // ROOT CAUSE OF BUG:
        // Previously: value = item.text  (String from DB via Flow)
        // Every keystroke fired: onTextChange → ViewModel → DB write → Flow emits
        // → recomposition → BasicTextField received a new String from DB.
        // Compose treated this as an EXTERNAL update and reset cursor position.
        // Fast typing meant multiple keystrokes were in-flight before the DB
        // round-trip completed → characters dropped or doubled.
        //
        // FIX: Local TextFieldValue is the source of truth for TYPING.
        // The DB is only updated via onTextChange (ViewModel debounces the save).
        // Compose sees a stable local value → cursor never jumps.
        //
        // remember(item.id): resets local state when a NEW item is created
        // (item.id changes), but NOT on every DB round-trip while typing.
        // This is the correct key — id is stable during typing, changes on new item.
        var localValue by remember(item.id) {
            mutableStateOf(TextFieldValue(text = item.text))
        }

        // One-way sync: if item.text changes from OUTSIDE while we don't have
        // focus (e.g. note loads, another device syncs), update local state.
        // We guard with !isFocused to never clobber an in-progress keystroke.
        var isFocused by remember { mutableStateOf(false) }
        LaunchedEffect(item.text) {
            if (!isFocused && localValue.text != item.text) {
                localValue = TextFieldValue(text = item.text)
            }
        }

        BasicTextField(
            value = localValue,
            onValueChange = { newValue ->
                // Intercept Enter/Return — both software keyboard "Next" button
                // and physical keyboards send \n through onValueChange.
                if (newValue.text.contains('\n')) {
                    onImeAction()
                } else {
                    localValue = newValue          // update local state immediately
                    onTextChange(newValue.text)    // propagate to ViewModel (debounced save)
                }
            },
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    onFocusChanged(focusState.isFocused)
                },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = textColor,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough
                else TextDecoration.None,
                lineHeight = 22.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { onImeAction() }),
            decorationBox = { innerTextField ->
                Box {
                    if (localValue.text.isEmpty()) {
                        Text(
                            text = "List item",
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Delete button — always in layout, fades in with alpha
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(32.dp)
                .alpha(deleteAlpha),
            enabled = showDeleteButton
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove item",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}