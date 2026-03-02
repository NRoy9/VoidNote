package com.greenicephoenix.voidnote.presentation.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.storage.ImageStorageManager
import com.greenicephoenix.voidnote.domain.model.FormatRange
import com.greenicephoenix.voidnote.domain.model.FormatType
import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.model.InlineBlockPayload
import com.greenicephoenix.voidnote.domain.model.InlineBlockType
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.model.TodoItem
import com.greenicephoenix.voidnote.domain.repository.InlineBlockRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for Note Editor Screen
 *
 * RESPONSIBILITIES:
 * 1. Load/create notes from the database
 * 2. Auto-save with 500ms debounce
 * 3. Rich text formatting (bold, italic, underline, headings)
 * 4. Tag management (add/remove, max 5)
 * 5. Pin/archive/delete operations
 * 6. Inline block management (TODO checklists) ← NEW
 *
 * BLOCK ARCHITECTURE:
 * The ViewModel maintains two pieces of state:
 *   - uiState.content = LOGICAL content (plain text, no markers)
 *   - uiState.blocks  = Map<blockId, InlineBlock> for all blocks in this note
 *
 * When saving to the database, buildRawContent() combines them:
 *   rawContent = logicalContent + marker tokens
 *
 * When loading from the database, we separate them:
 *   logicalContent = DocumentParser.extractLogicalContent(rawContent)
 *   blocks = loaded from inline_blocks table via InlineBlockRepository
 *
 * This separation keeps FormatRange positions always valid — they index
 * the logical content and never need to account for marker lengths.
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val inlineBlockRepository: InlineBlockRepository,
    private val imageStorage: ImageStorageManager   // ← NEW: handles encrypted file I/O
) : ViewModel() {

    // Navigation argument — "new" means create a new note
    private val noteId: String = savedStateHandle.get<String>("noteId") ?: "new"

    // ─── State ────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    // Internal tracking
    private var autoSaveJob: Job? = null
    private var currentNoteId: String = noteId
    private var currentFolderId: String? = null
    private var isDeleting = false

    init {
        loadNote()
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOTE LOADING
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Load an existing note from the database, or initialise a new blank note.
     *
     * For existing notes, we:
     * 1. Load the note's raw content from Room
     * 2. Strip marker tokens to get the logical content for the text editor
     * 3. Load this note's blocks from the inline_blocks table via a reactive Flow
     *
     * The blocks Flow keeps uiState.blocks automatically up-to-date whenever
     * the database changes (e.g. after insertBlock, updateBlock).
     */
    private fun loadNote() {
        if (noteId == "new") {
            // New note — generate an ID immediately so we can attach blocks to it
            currentNoteId = UUID.randomUUID().toString()
            _uiState.value = _uiState.value.copy(
                title = "",
                content = "",
                isNewNote = true,
                isLoading = false
            )
            // Start observing blocks (returns empty list for a new noteId, which is correct)
            observeBlocks()

        } else {
            viewModelScope.launch {
                val note = noteRepository.getNoteById(noteId)
                if (note != null) {
                    currentNoteId = note.id
                    currentFolderId = note.folderId

                    // IMPORTANT: Strip marker tokens from stored content.
                    // The raw content (with markers) is reconstructed at save time.
                    // The UI always works with logical content only.
                    val logicalContent = DocumentParser.extractLogicalContent(note.content)

                    _uiState.value = _uiState.value.copy(
                        title = note.title,
                        content = logicalContent,
                        contentFormats = note.contentFormats,
                        isPinned = note.isPinned,
                        isArchived = note.isArchived,
                        tags = note.tags,
                        isNewNote = false,
                        isLoading = false
                    )
                } else {
                    // Note not found — treat as new
                    currentNoteId = UUID.randomUUID().toString()
                    _uiState.value = _uiState.value.copy(
                        isNewNote = true,
                        isLoading = false
                    )
                }
                // Start observing blocks now that we have the correct noteId
                observeBlocks()
            }
        }
    }

    /**
     * Start collecting the blocks Flow for this note.
     *
     * WHY A SEPARATE FUNCTION?
     * We need to call this AFTER setting currentNoteId (which may be set
     * asynchronously when loading an existing note). Calling it too early
     * with the wrong ID would observe blocks for the wrong note.
     *
     * HOW IT WORKS:
     * Flow.collect() is a suspending function that never returns on its own —
     * it emits every time the database changes. We launch it in a coroutine
     * that lives as long as the ViewModel. When the user leaves the editor
     * and the ViewModel is cleared, the coroutine is cancelled automatically.
     */
    private fun observeBlocks() {
        viewModelScope.launch {
            inlineBlockRepository.getBlocksForNote(currentNoteId)
                .collect { blockList ->
                    // Convert the list to a Map for O(1) lookups by blockId in the UI
                    val blocksMap = blockList.associateBy { it.id }
                    _uiState.value = _uiState.value.copy(blocks = blocksMap)
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INLINE BLOCK OPERATIONS (TODO CHECKLISTS)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Insert a new TODO block into this note.
     *
     * Creates the block in the database and schedules a save.
     * The blocks Flow will automatically update uiState.blocks when the
     * database insert completes, causing the UI to show the new block.
     *
     * The block is initialised with a single empty item so the user can
     * start typing immediately.
     */
    fun insertTodoBlock() {
        viewModelScope.launch {
            // ── CRITICAL FK GUARD ─────────────────────────────────────────
            // inline_blocks.noteId has a FOREIGN KEY constraint → notes.id.
            // If this is a new note and the user taps "Add Checklist" before
            // typing anything, saveNote() returns early because title+content
            // are both blank — so the notes row doesn't exist yet.
            // Inserting the block then crashes with:
            //   SQLiteConstraintException: FOREIGN KEY constraint failed (code 787)
            //
            // ensureNotePersisted() writes a minimal placeholder note row
            // even when blank, satisfying the FK before insertBlock() runs.
            ensureNotePersisted()

            val blockId = UUID.randomUUID().toString()
            val itemId = UUID.randomUUID().toString()

            val newBlock = InlineBlock(
                id = blockId,
                noteId = currentNoteId,
                type = InlineBlockType.TODO,
                payload = InlineBlockPayload.Todo(
                    items = listOf(
                        TodoItem(
                            id = itemId,
                            text = "",
                            isChecked = false,
                            sortOrder = 0
                        )
                    )
                ),
                createdAt = System.currentTimeMillis()
            )

            // Insert the block into the database.
            // The Flow collector in observeBlocks() will pick this up and update
            // uiState.blocks automatically — no manual state update needed here.
            inlineBlockRepository.insertBlock(newBlock)

            // Rebuild the marker tokens in the note's raw content.
            // delay(50) lets the Flow emit the new block first so saveNote()
            // sees the updated blocks map when it queries getBlocksForNote().
            delay(50)
            saveNote()
        }
    }

    /**
     * Insert a new IMAGE block from a gallery URI.
     *
     * Gallery flow:
     * - Read bytes from the content URI
     * - Encrypt them with AES-256-GCM → write to filesDir/images/image_<id>.enc
     * - Source gallery file is UNTOUCHED (user chose to include it, may need it elsewhere)
     * - Insert block into DB with the .enc file path
     *
     * @param imageUri  Content URI from PickVisualMedia
     */
    fun insertImageBlock(imageUri: Uri) {
        viewModelScope.launch {
            ensureNotePersisted()

            val blockId = UUID.randomUUID().toString()

            // Encrypt and save to app-private storage
            val encFilePath = imageStorage.saveFromUri(imageUri, blockId)
            if (encFilePath == null) {
                android.util.Log.e("NoteEditor", "insertImageBlock: save failed, aborting")
                return@launch
            }

            // Read image dimensions from the encrypted file (decrypts in memory, no disk write)
            val (width, height) = imageStorage.readDimensions(encFilePath)

            val newBlock = InlineBlock(
                id        = blockId,
                noteId    = currentNoteId,
                type      = InlineBlockType.IMAGE,
                payload   = InlineBlockPayload.Image(
                    filePath = encFilePath,
                    caption  = "",
                    width    = width,
                    height   = height
                ),
                createdAt = System.currentTimeMillis()
            )

            inlineBlockRepository.insertBlock(newBlock)
            delay(50)
            saveNote()
        }
    }

    /**
     * Insert an IMAGE block from a camera capture.
     *
     * Camera flow:
     * - createCameraTempFile() creates a plain JPEG in filesDir/camera_tmp/
     *   and returns a FileProvider content:// URI for the camera app to write to
     * - Camera writes the JPEG directly to our app-private storage (NEVER to DCIM)
     * - After capture succeeds, encryptCameraTempFile() encrypts it in-place:
     *     temp plain JPEG → encrypted .enc → delete plain JPEG
     * - Insert block into DB with the .enc file path
     *
     * @param tempFilePath  The absolute path of the temp file (returned when
     *                      creating the capture URI in the Screen)
     */
    fun insertCameraImage(tempFilePath: String) {
        viewModelScope.launch {
            ensureNotePersisted()

            val blockId = UUID.randomUUID().toString()

            // Encrypt the temp JPEG and delete the plain file
            val encFilePath = imageStorage.encryptCameraTempFile(tempFilePath, blockId)
            if (encFilePath == null) {
                android.util.Log.e("NoteEditor", "insertCameraImage: encryption failed, aborting")
                return@launch
            }

            val (width, height) = imageStorage.readDimensions(encFilePath)

            val newBlock = InlineBlock(
                id        = blockId,
                noteId    = currentNoteId,
                type      = InlineBlockType.IMAGE,
                payload   = InlineBlockPayload.Image(
                    filePath = encFilePath,
                    caption  = "",
                    width    = width,
                    height   = height
                ),
                createdAt = System.currentTimeMillis()
            )

            inlineBlockRepository.insertBlock(newBlock)
            delay(50)
            saveNote()
        }
    }

    /**
     * Update the caption text on an IMAGE block.
     *
     * Called on every keystroke in the caption field.
     * The ViewModel's auto-save (500ms debounce) persists the note content.
     * The block itself is updated immediately in the database.
     *
     * @param blockId    The UUID of the IMAGE block.
     * @param newCaption The updated caption text.
     */
    fun updateImageCaption(blockId: String, newCaption: String) {
        viewModelScope.launch {
            val block   = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Image ?: return@launch

            val updatedBlock = block.copy(
                payload = payload.copy(caption = newCaption)
            )

            inlineBlockRepository.updateBlock(updatedBlock)
            // Caption changes don't modify the note's raw content (markers are unchanged),
            // so we don't need to call saveNote() here — the block table update is enough.
        }
    }

    /**
     * Ensure the note row exists in the database before inserting any
     * dependent records (inline_blocks have a FOREIGN KEY on noteId).
     *
     * For EXISTING notes this is a no-op — the row is already there.
     *
     * For NEW notes that are still blank (user hasn't typed yet), the normal
     * saveNote() guard `if (title.isBlank() && content.isBlank()) return`
     * would skip the insert, leaving no parent row for the block FK.
     * This function bypasses that guard and writes the placeholder row.
     *
     * The note may be blank at this point — that's fine. saveNote() will
     * update it with real content when the auto-save fires.
     */
    private suspend fun ensureNotePersisted() {
        if (!_uiState.value.isNewNote) return  // Already in DB — nothing to do

        val state = _uiState.value
        val note = Note(
            id = currentNoteId,
            title = state.title,                 // May be blank — intentional
            content = state.content,             // May be blank — intentional
            contentFormats = state.contentFormats,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isPinned = state.isPinned,
            isArchived = state.isArchived,
            isTrashed = false,
            tags = state.tags,
            folderId = currentFolderId
        )

        noteRepository.insertNote(note, folderId = currentFolderId)
        // Mark as no longer new so subsequent saves use updateNote() not insertNote()
        _uiState.value = _uiState.value.copy(isNewNote = false)
    }

    /**
     * Toggle a todo item's checked state.
     *
     * Finds the block and item, flips the isChecked flag, and saves back.
     * This triggers a database update which the Flow re-emits, updating the UI.
     *
     * @param blockId  The UUID of the TODO block containing this item.
     * @param itemId   The UUID of the specific item to toggle.
     */
    fun toggleTodoItem(blockId: String, itemId: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch

            // Create an updated payload with the item's isChecked flipped
            val updatedItems = payload.items.map { item ->
                if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item
            }

            val updatedBlock = block.copy(
                payload = payload.copy(items = updatedItems)
            )

            inlineBlockRepository.updateBlock(updatedBlock)
            // The Flow in observeBlocks() will update the UI automatically
        }
    }

    /**
     * Add a new empty item to a TODO block.
     *
     * The new item is appended at the end (highest sortOrder).
     *
     * @param blockId  The UUID of the TODO block to add an item to.
     */
    fun addTodoItem(blockId: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch

            val maxSortOrder = payload.items.maxOfOrNull { it.sortOrder } ?: -1

            val newItem = TodoItem(
                id = UUID.randomUUID().toString(),
                text = "",
                isChecked = false,
                sortOrder = maxSortOrder + 1
            )

            val updatedBlock = block.copy(
                payload = payload.copy(items = payload.items + newItem)
            )

            inlineBlockRepository.updateBlock(updatedBlock)
        }
    }

    /**
     * Update the text of a specific todo item.
     *
     * Called on every keystroke in an item's text field.
     * We do NOT debounce here — blocks are small and the user expects
     * real-time persistence. Room handles concurrent writes safely.
     *
     * @param blockId  The UUID of the TODO block.
     * @param itemId   The UUID of the item being edited.
     * @param newText  The new text content for this item.
     */
    fun updateTodoItemText(blockId: String, itemId: String, newText: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch

            val updatedItems = payload.items.map { item ->
                if (item.id == itemId) item.copy(text = newText) else item
            }

            val updatedBlock = block.copy(
                payload = payload.copy(items = updatedItems)
            )

            inlineBlockRepository.updateBlock(updatedBlock)
        }
    }

    /**
     * Delete a specific todo item from a block.
     *
     * If this is the last item, the caller (screen) should call deleteBlock()
     * instead, which removes the block entirely.
     *
     * @param blockId  The UUID of the TODO block.
     * @param itemId   The UUID of the item to remove.
     */
    fun deleteTodoItem(blockId: String, itemId: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch

            val updatedItems = payload.items.filter { it.id != itemId }

            if (updatedItems.isEmpty()) {
                // No items left — delete the whole block
                deleteBlock(blockId)
                return@launch
            }

            val updatedBlock = block.copy(
                payload = payload.copy(items = updatedItems)
            )

            inlineBlockRepository.updateBlock(updatedBlock)
        }
    }

    /**
     * Delete an entire block from this note.
     *
     * For IMAGE blocks: also deletes the physical image file from app storage.
     * For TODO blocks: no file to delete.
     *
     * The blocks Flow emits the updated list (without this block), causing
     * the UI to remove the block composable automatically.
     */
    fun deleteBlock(blockId: String) {
        viewModelScope.launch {
            // For IMAGE blocks: delete the physical .enc file before removing DB row
            val block = _uiState.value.blocks[blockId]
            if (block?.type == InlineBlockType.IMAGE) {
                val payload = block.payload as? InlineBlockPayload.Image
                payload?.filePath?.let { imageStorage.deleteEncFile(it) }
            }

            inlineBlockRepository.deleteBlock(blockId)
            delay(50)
            saveNote()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOTE SAVE LOGIC
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Schedule an auto-save with 500ms debounce.
     *
     * WHY DEBOUNCE?
     * The user may type many characters per second. If we saved to the
     * database on every keystroke, we'd hammer Room with hundreds of writes.
     * Debouncing means we wait 500ms after the LAST change before saving.
     * This is standard practice for note apps.
     */
    private fun scheduleAutoSave() {
        if (isDeleting) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(500)
            saveNote()
        }
    }

    /**
     * Save the note to the database immediately (no debounce).
     * Called by onDispose (when leaving the screen) to ensure no data loss.
     */
    fun forceSave() {
        if (isDeleting) {
            android.util.Log.d("NoteEditor", "Skipping forceSave - deleting")
            return
        }
        autoSaveJob?.cancel()
        viewModelScope.launch {
            saveNote()
        }
    }

    /**
     * Core save function — builds raw content and writes to Room.
     *
     * CONTENT RECONSTRUCTION:
     * We get the current logical content from state, then ask the repository
     * for the current list of blocks. We use Flow.first() which gets the
     * current snapshot from the database without subscribing.
     * DocumentParser.buildRawContent() combines them into the raw string
     * that goes into the database.
     *
     * WHY QUERY BLOCKS FROM REPOSITORY INSTEAD OF STATE?
     * Using state.blocks could race with a recently-inserted block.
     * The repository is always the source of truth.
     */
    private suspend fun saveNote() {
        val state = _uiState.value

        // Don't save completely empty notes — prevents ghost empty notes
        if (state.title.isBlank() && state.content.isBlank()) return

        // Get the definitive current block list from the database
        val currentBlocks = inlineBlockRepository
            .getBlocksForNote(currentNoteId)
            .first()  // Get current snapshot — does NOT subscribe

        // Build the raw content: logical text + marker tokens for all blocks
        val rawContent = DocumentParser.buildRawContent(
            logicalContent = state.content,
            blocks = currentBlocks
        )

        val note = Note(
            id = currentNoteId,
            title = state.title,
            content = rawContent,         // ← raw content WITH markers goes to DB
            contentFormats = state.contentFormats,
            createdAt = if (state.isNewNote) System.currentTimeMillis() else 0L,
            updatedAt = System.currentTimeMillis(),
            isPinned = state.isPinned,
            isArchived = state.isArchived,
            isTrashed = false,
            tags = state.tags,
            folderId = currentFolderId
        )

        if (state.isNewNote) {
            noteRepository.insertNote(note, folderId = currentFolderId)
            _uiState.value = _uiState.value.copy(isNewNote = false)
        } else {
            noteRepository.updateNote(note, folderId = currentFolderId)
        }

        _uiState.value = _uiState.value.copy(
            lastSaved = System.currentTimeMillis(),
            isSaving = false
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEXT EDITING
    // ─────────────────────────────────────────────────────────────────────

    fun setFolder(folderId: String?) {
        currentFolderId = folderId
    }

    fun onTitleChange(newTitle: String) {
        _uiState.value = _uiState.value.copy(title = newTitle)
        scheduleAutoSave()
    }

    /**
     * Handle content changes from the text editor.
     *
     * NOTE: newContent is LOGICAL content (no marker tokens).
     * The marker tokens are handled separately in saveNote().
     * FormatRanges index the logical content and are adjusted here.
     */
    fun onContentChange(newContent: String) {
        val oldContent = _uiState.value.content
        val oldFormats = _uiState.value.contentFormats

        if (newContent.length > oldContent.length) {
            // Text was added
            val insertPos = findInsertPosition(oldContent, newContent)
            val insertLength = newContent.length - oldContent.length

            var newFormats = adjustFormatsForTextChange(oldFormats, oldContent, newContent)

            if (insertPos >= 0 && insertLength > 0) {
                val insertEnd = insertPos + insertLength
                if (_uiState.value.activeBold) {
                    newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.BOLD)
                }
                if (_uiState.value.activeItalic) {
                    newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.ITALIC)
                }
                if (_uiState.value.activeUnderline) {
                    newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.UNDERLINE)
                }
                if (_uiState.value.activeStrikethrough) {
                    newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.STRIKETHROUGH)
                }
                _uiState.value.activeHeading?.let { heading ->
                    newFormats = addFormat(newFormats, insertPos, insertEnd, heading)
                }
            }

            _uiState.value = _uiState.value.copy(content = newContent, contentFormats = newFormats)
        } else {
            // Text was deleted or replaced
            val newFormats = adjustFormatsForTextChange(oldFormats, oldContent, newContent)
            _uiState.value = _uiState.value.copy(content = newContent, contentFormats = newFormats)
        }

        scheduleAutoSave()
    }

    private fun findInsertPosition(oldText: String, newText: String): Int {
        var i = 0
        while (i < oldText.length && i < newText.length && oldText[i] == newText[i]) {
            i++
        }
        return i
    }

    private fun adjustFormatsForTextChange(
        formats: List<FormatRange>,
        oldText: String,
        newText: String
    ): List<FormatRange> {
        val lengthDiff = newText.length - oldText.length
        if (lengthDiff == 0) return formats
        val changePos = findInsertPosition(oldText, newText)
        return formats.mapNotNull { range ->
            when {
                range.end <= changePos -> range
                range.start >= changePos -> FormatRange(
                    start = (range.start + lengthDiff).coerceAtLeast(0),
                    end = (range.end + lengthDiff).coerceAtLeast(range.start + 1),
                    type = range.type
                )
                range.start < changePos && range.end > changePos -> FormatRange(
                    start = range.start,
                    end = (range.end + lengthDiff).coerceAtLeast(range.start + 1),
                    type = range.type
                )
                else -> null
            }
        }.filter { it.start < newText.length && it.end <= newText.length && it.start < it.end }
    }

    // ─────────────────────────────────────────────────────────────────────
    // NOTE ACTIONS
    // ─────────────────────────────────────────────────────────────────────

    fun deleteNote() {
        viewModelScope.launch {
            isDeleting = true
            autoSaveJob?.cancel()
            if (_uiState.value.isNewNote &&
                (_uiState.value.title.isNotBlank() || _uiState.value.content.isNotBlank())) {
                saveNote()
            }
            delay(100)
            noteRepository.moveToTrash(currentNoteId)
            android.util.Log.d("NoteEditor", "Note $currentNoteId moved to trash")
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPinned = !_uiState.value.isPinned)
            noteRepository.togglePin(currentNoteId)
        }
    }

    fun archiveNote() {
        viewModelScope.launch {
            noteRepository.toggleArchive(currentNoteId)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // TAG MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        val current = _uiState.value.tags
        if (trimmed !in current && current.size < 5) {
            _uiState.value = _uiState.value.copy(tags = current + trimmed)
            scheduleAutoSave()
        }
    }

    fun removeTag(tag: String) {
        _uiState.value = _uiState.value.copy(tags = _uiState.value.tags - tag)
        scheduleAutoSave()
    }

    // ─────────────────────────────────────────────────────────────────────
    // FORMATTING
    // ─────────────────────────────────────────────────────────────────────

    fun applyFormatting(start: Int, end: Int, type: FormatType) {
        val current = _uiState.value.contentFormats
        val hasFmt = hasFormat(current, start, end, type)
        val newFormats = if (hasFmt) removeFormat(current, start, end, type)
        else addFormat(current, start, end, type)
        _uiState.value = _uiState.value.copy(contentFormats = newFormats)
        scheduleAutoSave()
    }

    fun toggleActiveBold() {
        _uiState.value = _uiState.value.copy(activeBold = !_uiState.value.activeBold)
    }

    fun toggleActiveItalic() {
        _uiState.value = _uiState.value.copy(activeItalic = !_uiState.value.activeItalic)
    }

    fun toggleActiveUnderline() {
        _uiState.value = _uiState.value.copy(activeUnderline = !_uiState.value.activeUnderline)
    }

    fun toggleActiveStrikethrough() {
        _uiState.value = _uiState.value.copy(activeStrikethrough = !_uiState.value.activeStrikethrough)
    }

    fun setActiveHeading(type: FormatType?) {
        _uiState.value = _uiState.value.copy(activeHeading = type)
    }

    fun clearAllFormatting() {
        _uiState.value = _uiState.value.copy(
            contentFormats = emptyList(),
            activeBold = false,
            activeItalic = false,
            activeUnderline = false,
            activeStrikethrough = false,
            activeHeading = null
        )
        scheduleAutoSave()
    }

    /**
     * Store the camera temp file path in UiState so the Screen can retrieve it
     * when the camera returns. The Screen needs the path to call insertCameraImage().
     *
     * WHY STATE?
     * The path is created in the ViewModel (where ImageStorageManager lives) and
     * consumed in the Screen (where the TakePicture launcher callback runs).
     * UiState is the correct channel to pass data from ViewModel → Screen.
     */
    fun prepareCameraCapture(): android.net.Uri? {
        val (uri, tempPath) = imageStorage.createCameraTempFile()
        _uiState.value = _uiState.value.copy(cameraCaptureTempPath = tempPath)
        return uri
    }

    /**
     * Store a camera capture URI in UiState so NoteEditorScreen's LaunchedEffect
     * can launch the camera after the permission callback grants access.
     *
     * WHY THIS PATTERN?
     * The Accompanist permission callback fires AFTER the system dialog closes.
     * At that point, the Screen composable (which holds cameraLauncher) isn't
     * directly accessible from the ViewModel. The clean solution is:
     *   1. ViewModel stores the URI in state
     *   2. LaunchedEffect(uiState.pendingCameraUri) in the Screen sees the change
     *   3. Screen launches the camera
     *   4. ViewModel clears the URI so the effect doesn't fire twice
     */
    fun storePendingCameraUri(uri: android.net.Uri) {
        _uiState.value = _uiState.value.copy(pendingCameraUri = uri)
    }

    fun clearPendingCameraUri() {
        _uiState.value = _uiState.value.copy(pendingCameraUri = null)
    }

    fun clearCameraCapturePath() {
        _uiState.value = _uiState.value.copy(cameraCaptureTempPath = null)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI STATE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immutable UI state for the Note Editor screen.
 *
 * Every field here is observed by the Compose UI via collectAsState().
 * When any field changes, only the composables that READ that field recompose.
 *
 * NEW FIELD: blocks
 * A Map from block UUID to InlineBlock. The UI uses this to render TODO
 * blocks below the text editor. The Map is keyed by ID for O(1) lookup.
 */
data class NoteEditorUiState(
    val title: String = "",
    val content: String = "",                       // LOGICAL content — no markers
    val contentFormats: List<FormatRange> = emptyList(),
    val blocks: Map<String, InlineBlock> = emptyMap(),  // ← NEW: block id → block
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val tags: List<String> = emptyList(),
    val isNewNote: Boolean = true,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val lastSaved: Long = 0L,

    // Active formatting state for new typed text
    val activeBold: Boolean = false,
    val activeItalic: Boolean = false,
    val activeUnderline: Boolean = false,
    val activeStrikethrough: Boolean = false,   // ← NEW
    val activeHeading: FormatType? = null,
    val cameraCaptureTempPath: String? = null, // path of temp JPEG during camera capture
    val pendingCameraUri: android.net.Uri? = null
)