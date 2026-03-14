package com.greenicephoenix.voidnote.presentation.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.storage.AudioStorageManager
import com.greenicephoenix.voidnote.data.storage.ImageStorageManager
import com.greenicephoenix.voidnote.data.storage.VoiceRecorderManager
import com.greenicephoenix.voidnote.domain.model.Folder
import com.greenicephoenix.voidnote.domain.model.FormatRange
import com.greenicephoenix.voidnote.domain.model.FormatType
import com.greenicephoenix.voidnote.domain.model.InlineBlock
import com.greenicephoenix.voidnote.domain.model.InlineBlockPayload
import com.greenicephoenix.voidnote.domain.model.InlineBlockType
import com.greenicephoenix.voidnote.domain.model.Note
import com.greenicephoenix.voidnote.domain.model.TodoItem
import com.greenicephoenix.voidnote.domain.repository.FolderRepository
import com.greenicephoenix.voidnote.domain.repository.InlineBlockRepository
import com.greenicephoenix.voidnote.domain.repository.NoteRepository
import com.greenicephoenix.voidnote.domain.model.NoteColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for Note Editor Screen.
 *
 * BLOCK TYPES SUPPORTED:
 *   TODO   — checklists, managed in-memory via Flow
 *   IMAGE  — gallery pick or camera capture, AES-256-GCM encrypted .enc files
 *   AUDIO  — voice recording, AES-256-GCM encrypted .enc files
 *
 * RECORDING STATE MACHINE:
 *   IDLE → startRecording() → RECORDING → stopRecording() → IDLE
 *   While RECORDING: recordingElapsedMs increments every 100ms (coroutine timer)
 *   stopRecording(): encrypt plain .aac → .enc → insert AUDIO block → DB
 *
 * FILE CLEANUP:
 *   deleteBlock() checks block type:
 *     IMAGE → ImageStorageManager.deleteEncFile(filePath)
 *     AUDIO → AudioStorageManager.deleteEncFile(filePath)
 *     TODO  → no file cleanup needed
 *
 * ─── SPRINT 5 ADDITIONS ───────────────────────────────────────────────────────
 *
 * 1. MOVE TO FOLDER (P1-5)
 *    FolderRepository is now injected. `folders` exposes all folders as a
 *    StateFlow so the MoveToFolderDialog in the Screen can show the list.
 *    `moveToFolder(folderId)` calls noteRepository.moveNoteToFolder() and
 *    updates `currentFolderId` so subsequent saves use the right folder.
 *
 * 2. CURRENT FOLDER NAME (for UI display)
 *    `currentFolderName` is added to NoteEditorUiState so the TopBar overflow
 *    menu can show "In: FolderName" or "Not in a folder".
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val inlineBlockRepository: InlineBlockRepository,
    private val imageStorage: ImageStorageManager,
    private val audioStorage: AudioStorageManager,
    private val voiceRecorder: VoiceRecorderManager,
    // ── SPRINT 5 ADDITION ─────────────────────────────────────────────────────
    // FolderRepository is needed to:
    //   a) Expose the full folder list for the MoveToFolderDialog
    //   b) Look up the current folder's name to display in the TopBar
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val noteId: String = savedStateHandle.get<String>("noteId") ?: "new"

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    /**
     * Sprint 12 — one-shot event to navigate to a diary entry (prev/next day).
     * SharedFlow with replay=0 so rotation doesn't re-trigger.
     */
    private val _diaryNavEvent = MutableSharedFlow<String>(replay = 0)
    val diaryNavEvent: SharedFlow<String> = _diaryNavEvent.asSharedFlow()

    private var autoSaveJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var currentNoteId: String = noteId
    private var currentFolderId: String? = null
    private var isDeleting = false

    // ── SPRINT 9: B4/B5 tracking vars ────────────────────────────────────────
    //
    // cleanupScope: a separate coroutine scope that outlives viewModelScope.
    // viewModelScope is cancelled BEFORE onCleared() returns, so any coroutine
    // launched into it for cleanup would be cancelled immediately. cleanupScope
    // is manually cancelled after cleanup completes.
    private val cleanupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // wasNewOnOpen: true when the editor was opened as a brand-new note (noteId == "new").
    // Loaded existing notes must never be deleted by ghost-note cleanup.
    private var wasNewOnOpen: Boolean = false

    // userTypedTitle: true the moment onTitleChange() is called (user touched the title).
    // Prevents auto-generated "Untitled Note N" from overwriting a user's own title.
    private var userTypedTitle: Boolean = false

    // autoTitleGenerated: true after "Untitled Note N" has been assigned once this session.
    // Prevents re-numbering on every subsequent auto-save.
    private var autoTitleGenerated: Boolean = false

    // ── SPRINT 5: Expose folder list ──────────────────────────────────────────
    // Converted to StateFlow with WhileSubscribed so it stops collecting when
    // no UI is observing (e.g. when the screen is in the background).
    // SharingStarted.WhileSubscribed(5_000) means: keep the upstream Flow alive
    // for 5 seconds after the last subscriber disappears — handles config changes.
    val folders: StateFlow<List<Folder>> = folderRepository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        loadNote()
    }

    override fun onCleared() {
        super.onCleared()
        // Safety net: ensure microphone is released if ViewModel is cleared
        // while recording is active (e.g. app killed, nav back during recording)
        if (_uiState.value.isRecording) {
            voiceRecorder.stopRecording()
        }
        voiceRecorder.releaseRecorder()

        // B4 FIX — Ghost note cleanup.
        //
        // PROBLEM: ensureNotePersisted() must write a note row BEFORE inserting any
        // inline block (FK constraint: inline_blocks.noteId → notes.id). So when a
        // user taps "Add Checklist" on a blank note, a shell note is created in DB.
        // If the user then deletes that block and navigates away without typing
        // anything, this empty shell remains visible in the notes list.
        //
        // FIX: detect the ghost condition in onCleared() and permanently delete the
        // shell. viewModelScope is already cancelled here, so we use cleanupScope —
        // a separate IO scope that lives until we explicitly cancel it.
        //
        // GHOST CONDITIONS (all must be true):
        //   wasNewOnOpen    → opened as a brand-new note (not loaded from DB)
        //   !isNewNote      → was persisted by ensureNotePersisted() (ID exists in DB)
        //   !userTypedTitle → user never interacted with the title field
        //   content.isBlank → user never typed body text
        //   blocks.isEmpty  → no blocks remain (all deleted, or none were kept)
        val state = _uiState.value
        val isGhostNote = wasNewOnOpen &&
                !state.isNewNote &&
                !userTypedTitle &&
                state.content.isBlank() &&
                state.blocks.isEmpty()

        // Sprint 12: diary entries created by tapping an empty calendar day
        // should also be cleaned up if the user leaves without writing anything.
        // A diary entry is "empty" when content is blank and no blocks exist,
        // regardless of wasNewOnOpen (diary notes are always pre-created with a title).
        val isEmptyDiaryEntry = state.isDiaryEntry &&
                !state.isNewNote &&
                state.content.isBlank() &&
                state.blocks.isEmpty()

        if (isGhostNote || isEmptyDiaryEntry) {
            cleanupScope.launch {
                noteRepository.deleteNotePermanently(currentNoteId)
                cleanupScope.cancel()
            }
        } else {
            cleanupScope.cancel()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTE LOADING
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadNote() {
        if (noteId == "new") {
            // Track that this session started with a new note — required for
            // ghost-note detection in onCleared() and auto-title in saveNote().
            wasNewOnOpen = true
            currentNoteId = UUID.randomUUID().toString()
            _uiState.value = _uiState.value.copy(
                title = "", content = "", isNewNote = true, isLoading = false
            )
            observeBlocks()
        } else {
            viewModelScope.launch {
                val note = noteRepository.getNoteById(noteId)
                if (note != null) {
                    currentNoteId = note.id
                    currentFolderId = note.folderId

                    val logicalContent = DocumentParser.extractLogicalContent(note.content)

                    // ── SPRINT 5: Resolve folder name for display ──────────────
                    // Look up the folder name once. Cheap: one DB hit, not a stream.
                    // The name is only needed for display in the overflow menu.
                    val folderName = note.folderId?.let { id ->
                        folderRepository.getFolderById(id)?.name
                    }

                    _uiState.value = _uiState.value.copy(
                        title = note.title,
                        content = logicalContent,
                        contentFormats = note.contentFormats,
                        isPinned = note.isPinned,
                        isArchived = note.isArchived,
                        tags = note.tags,
                        isNewNote = false,
                        isLoading = false,
                        currentFolderName = folderName,
                        noteColor = note.color,
                        linkedNoteIds = note.linkedNoteIds,
                        isDiaryEntry = note.isDiaryEntry   // Sprint 12
                    )
                    // Sprint 11: resolve IDs → titles for the linked-notes strip
                    loadLinkedNotePreviews(note.linkedNoteIds)
                    // Sprint 12: if this is a diary entry, load prev/next navigation
                    if (note.isDiaryEntry) loadDiaryNeighbours(note.title)
                } else {
                    currentNoteId = UUID.randomUUID().toString()
                    _uiState.value = _uiState.value.copy(isNewNote = true, isLoading = false)
                }
                observeBlocks()
            }
        }
    }

    private fun observeBlocks() {
        viewModelScope.launch {
            inlineBlockRepository.getBlocksForNote(currentNoteId)
                .collect { blockList ->
                    _uiState.value = _uiState.value.copy(
                        blocks = blockList.associateBy { it.id }
                    )
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TODO BLOCK OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────

    fun insertTodoBlock() {
        viewModelScope.launch {
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
            inlineBlockRepository.insertBlock(newBlock)
            delay(50)
            saveNote()
        }
    }

    /**
     * Insert a blank CODE block at the end of the current note. (Sprint 12)
     *
     * A code block is an InlineBlock with type=CODE and an empty Code payload.
     * The user fills in the code text and optional language label directly
     * inside the CodeBlockComposable.
     *
     * WHY ensureNotePersisted() first?
     * InlineBlocks have a FK on noteId → notes.id. The note must exist
     * in the DB before we can insert a block referencing it.
     */
    fun insertCodeBlock() {
        viewModelScope.launch {
            ensureNotePersisted()
            val blockId = UUID.randomUUID().toString()
            val newBlock = InlineBlock(
                id = blockId,
                noteId = currentNoteId,
                type = InlineBlockType.CODE,
                payload = InlineBlockPayload.Code(code = "", language = ""),
                createdAt = System.currentTimeMillis()
            )
            inlineBlockRepository.insertBlock(newBlock)
            delay(50)
            saveNote()
        }
    }

    /**
     * Update the code text inside a CODE block. (Sprint 12)
     * Called every time the user types in the CodeBlockComposable's text field.
     */
    fun updateCodeBlock(blockId: String, code: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Code ?: return@launch
            inlineBlockRepository.updateBlock(
                block.copy(payload = payload.copy(code = code))
            )
        }
    }

    /**
     * Update the language label of a CODE block. (Sprint 12)
     * Called when the user edits the language chip in the top-right corner.
     */
    fun updateCodeBlockLanguage(blockId: String, language: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Code ?: return@launch
            inlineBlockRepository.updateBlock(
                block.copy(payload = payload.copy(language = language))
            )
        }
    }

    fun toggleTodoItem(blockId: String, itemId: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch
            val updated = block.copy(
                payload = payload.copy(
                    items = payload.items.map { if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it }
                )
            )
            inlineBlockRepository.updateBlock(updated)
        }
    }

    fun addTodoItem(blockId: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch
            val maxOrder = payload.items.maxOfOrNull { it.sortOrder } ?: -1
            val newItem = TodoItem(
                id = UUID.randomUUID().toString(),
                text = "",
                isChecked = false,
                sortOrder = maxOrder + 1
            )
            inlineBlockRepository.updateBlock(block.copy(payload = payload.copy(items = payload.items + newItem)))
        }
    }

    /**
     * Paste multi-line text into a checklist — each line becomes its own item.
     *
     * Called when the user pastes text containing newlines into a TodoItemRow.
     * Example: paste "Buy milk\nBuy eggs\nBuy bread" while editing item X:
     *   • Item X text → "Buy milk"   (first line replaces current item)
     *   • New item    → "Buy eggs"   (inserted immediately after X)
     *   • New item    → "Buy bread"  (inserted after that)
     *   • Cursor      → lands on "Buy bread" (last pasted item)
     *
     * HOW IT WORKS:
     * 1. Sort all existing items by sortOrder for stable insertion.
     * 2. Find the index of `afterItemId` (the item that was being edited).
     * 3. Update that item's text to `firstLineText`.
     * 4. Build new TodoItem objects for each remaining line.
     * 5. Insert them into the list immediately after position `insertIndex`.
     * 6. Renumber ALL sortOrders from 0 based on final list index.
     *    This avoids sortOrder collisions and keeps ordering clean forever.
     * 7. One atomic updateBlock call — no intermediate state visible in UI.
     *
     * @param blockId       The TODO block being edited.
     * @param afterItemId   The item that received the paste (its text → firstLineText).
     * @param firstLineText The first line of the pasted text (replaces current item).
     * @param remainingLines Lines 2..N of the pasted text (become new items below).
     */
    fun pasteTodoLines(
        blockId: String,
        afterItemId: String,
        firstLineText: String,
        remainingLines: List<String>
    ) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch

            // Sort by sortOrder for stable, predictable insertion position
            val sortedItems = payload.items.sortedBy { it.sortOrder }.toMutableList()

            // Find the item that was being edited when paste happened
            val insertIndex = sortedItems.indexOfFirst { it.id == afterItemId }
            if (insertIndex == -1) return@launch

            // Replace the current item's text with the first pasted line
            sortedItems[insertIndex] = sortedItems[insertIndex].copy(text = firstLineText)

            // Build new items for lines 2..N, inserting after the current item
            val newItems = remainingLines.mapIndexed { i, line ->
                TodoItem(
                    id = UUID.randomUUID().toString(),
                    text = line,
                    isChecked = false,
                    sortOrder = insertIndex + 1 + i  // placeholder — renumbered below
                )
            }
            sortedItems.addAll(insertIndex + 1, newItems)

            // Renumber all sortOrders cleanly (0, 1, 2, ...) from final list order.
            // This prevents sortOrder gaps or collisions that could mess up future
            // insertions or sort ordering.
            val renumbered = sortedItems.mapIndexed { index, item ->
                item.copy(sortOrder = index)
            }

            // Single atomic write — no intermediate flicker in the UI
            inlineBlockRepository.updateBlock(
                block.copy(payload = payload.copy(items = renumbered))
            )
        }
    }

    fun updateTodoItemText(blockId: String, itemId: String, newText: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch
            inlineBlockRepository.updateBlock(
                block.copy(
                    payload = payload.copy(
                    items = payload.items.map { if (it.id == itemId) it.copy(text = newText) else it }
                ))
            )
        }
    }

    fun deleteTodoItem(blockId: String, itemId: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch
            val remaining = payload.items.filter { it.id != itemId }
            if (remaining.isEmpty()) {
                deleteBlock(blockId); return@launch
            }
            inlineBlockRepository.updateBlock(block.copy(payload = payload.copy(items = remaining)))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IMAGE BLOCK OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Insert an IMAGE block from the gallery photo picker.
     * Encrypts bytes from the content URI → .enc file → insert block.
     * Source gallery file is never modified.
     */
    fun insertImageBlock(imageUri: Uri) {
        viewModelScope.launch {
            ensureNotePersisted()
            val blockId = UUID.randomUUID().toString()

            val encFilePath = imageStorage.saveFromUri(imageUri, blockId)
            if (encFilePath == null) {
                android.util.Log.e("NoteEditor", "insertImageBlock: save failed")
                return@launch
            }

            val (width, height) = imageStorage.readDimensions(encFilePath)
            val newBlock = InlineBlock(
                id = blockId,
                noteId = currentNoteId,
                type = InlineBlockType.IMAGE,
                payload = InlineBlockPayload.Image(
                    filePath = encFilePath,
                    caption = "",
                    width = width,
                    height = height
                ),
                createdAt = System.currentTimeMillis()
            )
            inlineBlockRepository.insertBlock(newBlock)
            delay(50)
            saveNote()
        }
    }

    /**
     * Insert an IMAGE block from camera capture.
     * Encrypts the temp plain JPEG written by the camera → .enc → delete plain.
     * Photo never appears in gallery.
     */
    fun insertCameraImage(tempFilePath: String) {
        viewModelScope.launch {
            ensureNotePersisted()
            val blockId = UUID.randomUUID().toString()

            val encFilePath = imageStorage.encryptCameraTempFile(tempFilePath, blockId)
            if (encFilePath == null) {
                android.util.Log.e("NoteEditor", "insertCameraImage: encryption failed")
                return@launch
            }

            val (width, height) = imageStorage.readDimensions(encFilePath)
            val newBlock = InlineBlock(
                id = blockId,
                noteId = currentNoteId,
                type = InlineBlockType.IMAGE,
                payload = InlineBlockPayload.Image(
                    filePath = encFilePath,
                    caption = "",
                    width = width,
                    height = height
                ),
                createdAt = System.currentTimeMillis()
            )
            inlineBlockRepository.insertBlock(newBlock)
            delay(50)
            saveNote()
        }
    }

    fun updateImageCaption(blockId: String, newCaption: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Image ?: return@launch
            inlineBlockRepository.updateBlock(block.copy(payload = payload.copy(caption = newCaption)))
        }
    }

    /** Prepare a camera capture URI. Stored in state so the Screen's LaunchedEffect can launch it. */
    fun prepareCameraCapture(): Uri? {
        val (uri, tempPath) = imageStorage.createCameraTempFile()
        _uiState.value = _uiState.value.copy(cameraCaptureTempPath = tempPath)
        return uri
    }

    fun clearCameraCapturePath() {
        _uiState.value = _uiState.value.copy(cameraCaptureTempPath = null)
    }

    fun storePendingCameraUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(pendingCameraUri = uri)
    }

    fun clearPendingCameraUri() {
        _uiState.value = _uiState.value.copy(pendingCameraUri = null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUDIO BLOCK OPERATIONS — VOICE RECORDING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start a voice recording session.
     *
     * Flow:
     *   1. ensureNotePersisted() — FK guard same as image/todo
     *   2. AudioStorageManager.createRecordingTempFile() → plain .aac path
     *   3. VoiceRecorderManager.startRecording(path) → MediaRecorder starts
     *   4. UiState: isRecording=true, recordingTempPath=path
     *   5. Timer coroutine starts — increments recordingElapsedMs every 100ms
     *
     * Called when RECORD_AUDIO permission is granted and user taps the
     * voice button in InsertBlockSheet.
     */
    fun startRecording() {
        viewModelScope.launch {
            ensureNotePersisted()

            val tempPath = audioStorage.createRecordingTempFile()
            val started = voiceRecorder.startRecording(tempPath)

            if (!started) {
                android.util.Log.e("NoteEditor", "startRecording: MediaRecorder failed to start")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isRecording = true,
                recordingElapsedMs = 0L,
                recordingTempPath = tempPath
            )

            // Timer: increment elapsed time every 100ms while recording
            recordingTimerJob = viewModelScope.launch {
                while (_uiState.value.isRecording) {
                    delay(100)
                    _uiState.value = _uiState.value.copy(
                        recordingElapsedMs = _uiState.value.recordingElapsedMs + 100L
                    )
                }
            }
        }
    }

    /**
     * Stop the active recording and insert the encrypted AUDIO block.
     *
     * Flow:
     *   1. VoiceRecorderManager.stopRecording() → plain .aac file is complete
     *   2. Capture durationMs from elapsed state (more reliable than reading file)
     *   3. AudioStorageManager.encryptRecordingTempFile() → .enc + delete plain .aac
     *   4. Insert InlineBlock(type=AUDIO) into DB
     *   5. UiState: isRecording=false, clear recording state
     *
     * Called when user taps the Stop button in RecordingSheet.
     */
    fun stopRecording() {
        val tempPath = _uiState.value.recordingTempPath ?: return
        val durationMs = _uiState.value.recordingElapsedMs

        // Stop the timer first
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        viewModelScope.launch {
            // Stop MediaRecorder — writes final bytes to the plain temp file
            val stopped = voiceRecorder.stopRecording()
            if (!stopped) {
                android.util.Log.e("NoteEditor", "stopRecording: MediaRecorder stop failed")
                _uiState.value = _uiState.value.copy(
                    isRecording = false, recordingTempPath = null, recordingElapsedMs = 0L
                )
                return@launch
            }

            val blockId = UUID.randomUUID().toString()

            // Encrypt the plain .aac → permanent .enc + delete plain
            val encFilePath = audioStorage.encryptRecordingTempFile(tempPath, blockId)
            if (encFilePath == null) {
                android.util.Log.e("NoteEditor", "stopRecording: encryption failed")
                _uiState.value = _uiState.value.copy(
                    isRecording = false, recordingTempPath = null, recordingElapsedMs = 0L
                )
                return@launch
            }

            val newBlock = InlineBlock(
                id = blockId,
                noteId = currentNoteId,
                type = InlineBlockType.AUDIO,
                payload = InlineBlockPayload.Audio(
                    filePath = encFilePath,
                    durationMs = durationMs.coerceAtLeast(0L)
                ),
                createdAt = System.currentTimeMillis()
            )

            inlineBlockRepository.insertBlock(newBlock)
            delay(50)
            saveNote()

            // Clear recording state — UI returns to normal
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                recordingTempPath = null,
                recordingElapsedMs = 0L
            )
        }
    }

    /**
     * Cancel an active recording without saving.
     * Stops the recorder, deletes the temp file.
     * Called if user navigates away while recording.
     */
    fun cancelRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        viewModelScope.launch {
            voiceRecorder.stopRecording()
            _uiState.value.recordingTempPath?.let {
                audioStorage.deleteEncFile(it)  // deletes plain .aac temp
            }
            _uiState.value = _uiState.value.copy(
                isRecording = false, recordingTempPath = null, recordingElapsedMs = 0L
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLOCK DELETE (handles all types)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Delete a block of any type.
     *
     * For IMAGE and AUDIO blocks: deletes the physical .enc file from filesDir
     * BEFORE removing the DB row. This ensures no orphaned files if the DB
     * delete succeeds but file delete fails (rare, but safe ordering).
     *
     * For TODO blocks: no file to delete, goes straight to DB.
     */
    fun deleteBlock(blockId: String) {
        viewModelScope.launch {
            val block = _uiState.value.blocks[blockId]

            when (block?.type) {
                InlineBlockType.IMAGE -> {
                    val path = (block.payload as? InlineBlockPayload.Image)?.filePath
                    path?.let { imageStorage.deleteEncFile(it) }
                }

                InlineBlockType.AUDIO -> {
                    val path = (block.payload as? InlineBlockPayload.Audio)?.filePath
                    path?.let { audioStorage.deleteEncFile(it) }
                }

                else -> { /* TODO — no file */
                }
            }

            inlineBlockRepository.deleteBlock(blockId)
            delay(50)
            saveNote()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE LOGIC
    // ─────────────────────────────────────────────────────────────────────────

    private fun scheduleAutoSave() {
        if (isDeleting) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(500)
            saveNote()
        }
    }

    fun forceSave() {
        if (isDeleting) return
        autoSaveJob?.cancel()
        viewModelScope.launch { saveNote() }
    }

    private suspend fun saveNote() {
        val state = _uiState.value

        // B4/B5 FIX — Blocks-aware early-return check.
        //
        // OLD CHECK: `if (state.title.isBlank() && state.content.isBlank()) return`
        // PROBLEM: This missed notes that contain ONLY inline blocks (checklists,
        // images, audio) with no typed title or text. Such notes would never be
        // saved properly — the block markers never made it into notes.content.
        //
        // NEW CHECK: also consider whether blocks exist. If NOTHING exists at all
        // (no title, no text, no blocks), there is genuinely nothing to save.
        val currentBlocks = inlineBlockRepository.getBlocksForNote(currentNoteId).first()
        val hasAnyContent = state.title.isNotBlank() ||
                state.content.isNotBlank() ||
                currentBlocks.isNotEmpty()
        if (!hasAnyContent) return

        // SMART AUTO-TITLE — derives the title from note content instead of
        // always falling back to "Untitled Note N".
        //
        // PRIORITY ORDER (evaluated top-to-bottom, first match wins):
        //   1. userTypedTitle  → user owns the title, never touch it
        //   2. !wasNewOnOpen   → existing note from DB, never auto-rename it
        //   3. content text    → first non-blank line, markdown stripped, ≤50 chars
        //   4. blocks only     → block-aware name ("Checklist", "Voice note", etc.)
        //   5. truly empty     → "Untitled Note N" generated once via counter
        //
        // Cases 3 & 4 re-derive on EVERY save so the title tracks edits in real time
        // (e.g. user types body text → title appears → user keeps typing → title updates).
        // Case 5 uses autoTitleGenerated to ensure N is only chosen once.
        //
        // The resolved title is written back to uiState so the title field shows it
        // if the user scrolls up — no special-casing needed in the Screen.
        val resolvedTitle: String = when {
            // User explicitly typed in the title field — always honour it as-is
            userTypedTitle -> state.title

            // Existing note loaded from DB — never auto-rename retroactively
            !wasNewOnOpen -> state.title

            // Body text exists → derive from first meaningful line
            state.content.isNotBlank() -> deriveSmartTitle(state.content)

            // No text but blocks exist → name after the dominant block type
            currentBlocks.isNotEmpty() -> deriveBlockTitle(currentBlocks)

            // Truly blank note — fall back to numbered title, generated only once
            !autoTitleGenerated -> {
                autoTitleGenerated = true
                val number = noteRepository.getNewUntitledNoteNumber()
                "Untitled Note $number"
            }

            // autoTitleGenerated already true and still blank — keep existing title
            else -> state.title
        }

        // Reflect any auto-derived title back into UI state so the title field
        // shows it without a round-trip read from the DB
        if (resolvedTitle != state.title) {
            _uiState.value = _uiState.value.copy(title = resolvedTitle)
        }

        val rawContent = DocumentParser.buildRawContent(
            logicalContent = state.content,
            blocks = currentBlocks
        )

        val note = Note(
            id = currentNoteId,
            title = resolvedTitle,
            content = rawContent,
            contentFormats = state.contentFormats,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isPinned = state.isPinned,
            isArchived = state.isArchived,
            isTrashed = false,
            tags = state.tags,
            folderId = currentFolderId,
            color = _uiState.value.noteColor,
            linkedNoteIds = state.linkedNoteIds,
            isDiaryEntry = state.isDiaryEntry   // Sprint 12: preserve diary flag
        )

        if (state.isNewNote) {
            noteRepository.insertNote(note, folderId = currentFolderId)
            _uiState.value =
                _uiState.value.copy(isNewNote = false, lastSaved = System.currentTimeMillis())
        } else {
            noteRepository.updateNote(note)
            _uiState.value = _uiState.value.copy(lastSaved = System.currentTimeMillis())
        }
    }

    private suspend fun ensureNotePersisted() {
        if (!_uiState.value.isNewNote) return
        val state = _uiState.value
        val note = Note(
            id = currentNoteId,
            title = state.title,
            content = state.content,
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
        _uiState.value = _uiState.value.copy(isNewNote = false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONTENT CHANGES
    // ─────────────────────────────────────────────────────────────────────────

    fun onTitleChange(newTitle: String) {
        // Signal that the user has explicitly touched the title field.
        // This prevents saveNote() from overwriting it with an auto-generated title.
        userTypedTitle = true
        _uiState.value = _uiState.value.copy(title = newTitle)
        scheduleAutoSave()
    }

    fun onContentChange(newContent: String) {
        val oldContent = _uiState.value.content
        val oldFormats = _uiState.value.contentFormats

        if (newContent.length > oldContent.length) {
            val insertPos = findInsertPosition(oldContent, newContent)
            val insertLength = newContent.length - oldContent.length
            var newFormats = adjustFormatsForTextChange(oldFormats, oldContent, newContent)

            if (insertPos >= 0 && insertLength > 0) {
                val insertEnd = insertPos + insertLength
                if (_uiState.value.activeBold) newFormats =
                    addFormat(newFormats, insertPos, insertEnd, FormatType.BOLD)
                if (_uiState.value.activeItalic) newFormats =
                    addFormat(newFormats, insertPos, insertEnd, FormatType.ITALIC)
                if (_uiState.value.activeUnderline) newFormats =
                    addFormat(newFormats, insertPos, insertEnd, FormatType.UNDERLINE)
                if (_uiState.value.activeStrikethrough) newFormats =
                    addFormat(newFormats, insertPos, insertEnd, FormatType.STRIKETHROUGH)
                _uiState.value.activeHeading?.let {
                    newFormats = addFormat(newFormats, insertPos, insertEnd, it)
                }
            }
            _uiState.value = _uiState.value.copy(content = newContent, contentFormats = newFormats)
        } else {
            _uiState.value = _uiState.value.copy(
                content = newContent,
                contentFormats = adjustFormatsForTextChange(oldFormats, oldContent, newContent)
            )
        }
        scheduleAutoSave()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FORMATTING
    // ─────────────────────────────────────────────────────────────────────────

    fun applyFormatting(start: Int, end: Int, type: FormatType) {
        val current = _uiState.value.contentFormats
        val hasFmt = hasFormat(current, start, end, type)
        _uiState.value = _uiState.value.copy(
            contentFormats = if (hasFmt) removeFormat(current, start, end, type)
            else addFormat(current, start, end, type)
        )
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
        _uiState.value =
            _uiState.value.copy(activeStrikethrough = !_uiState.value.activeStrikethrough)
    }

    fun setActiveHeading(type: FormatType?) {
        _uiState.value = _uiState.value.copy(activeHeading = type)
    }

    fun clearAllFormatting() {
        _uiState.value = _uiState.value.copy(
            contentFormats = emptyList(), activeBold = false, activeItalic = false,
            activeUnderline = false, activeStrikethrough = false, activeHeading = null
        )
        scheduleAutoSave()
    }

    /**
     * Toggle between edit mode and format preview mode.
     *
     * PREVIEW MODE:
     * Shows a read-only styled view of the note content with all FormatRanges
     * rendered visually (bold is bold, headings are large, etc.).
     * Uses the existing applyFormatting() — no new library needed.
     *
     * The toolbar simplifies in preview mode — only the toggle button is shown
     * so the user can return to editing.
     */
    fun togglePreview() {
        _uiState.value = _uiState.value.copy(showPreview = !_uiState.value.showPreview)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTE ACTIONS
    // ─────────────────────────────────────────────────────────────────────────

    fun deleteNote() {
        viewModelScope.launch {
            isDeleting = true
            autoSaveJob?.cancel()
            if (_uiState.value.isNewNote &&
                (_uiState.value.title.isNotBlank() || _uiState.value.content.isNotBlank())
            ) {
                saveNote()
            }
            delay(100)
            noteRepository.moveToTrash(currentNoteId)
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPinned = !_uiState.value.isPinned)
            noteRepository.togglePin(currentNoteId)
        }
    }

    fun archiveNote() {
        viewModelScope.launch { noteRepository.toggleArchive(currentNoteId) }
    }

    // ── SPRINT 5: Move note to folder ─────────────────────────────────────────
    /**
     * Move this note to a different folder, or remove it from any folder (null = root).
     *
     * HOW IT WORKS:
     * 1. Call noteRepository.moveNoteToFolder() — this updates the folderId in the DB.
     *    This is a flag-only update (not re-encrypt) so it's fast.
     * 2. Update `currentFolderId` in memory so future auto-saves preserve the new folder.
     * 3. Update `currentFolderName` in uiState so the TopBar overflow shows the new folder name.
     *    We look up the folder name here rather than making the Screen do an extra DB call.
     *
     * @param folderId Null = "No folder" (root level). String = the target folder's ID.
     */
    fun moveToFolder(folderId: String?) {
        viewModelScope.launch {
            // Persist the note first if it hasn't been saved yet
            // (can't move a note that doesn't exist in the DB)
            ensureNotePersisted()

            // Update the DB
            noteRepository.moveNoteToFolder(currentNoteId, folderId)

            // Update in-memory folder tracking
            currentFolderId = folderId

            // Resolve the folder name for the UI
            val folderName = folderId?.let { id ->
                folderRepository.getFolderById(id)?.name
            }

            _uiState.value = _uiState.value.copy(currentFolderName = folderName)
        }
    }

    /**
     * Sprint 6 — update the color accent on this note.
     *
     * Uses currentNoteId (the ViewModel's in-memory note ID) — NOT a field
     * from uiState. Updates the DB immediately (flag-only, no re-encryption).
     * Also updates uiState.noteColor instantly for immediate visual feedback.
     *
     * @param color  The chosen NoteColor, or null to remove the accent.
     */
    fun updateNoteColor(color: NoteColor?) {
        // Update UI immediately — no need to wait for DB round-trip
        _uiState.value = _uiState.value.copy(noteColor = color)

        // Persist to DB in background (skip if note hasn't been saved yet —
        // saveNote() will pick up noteColor from uiState on first save)
        if (!_uiState.value.isNewNote) {
            viewModelScope.launch {
                noteRepository.updateNoteColor(currentNoteId, color)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TAG MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // SMART AUTO-TITLE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Derive a display title from the first meaningful line of body content.
     *
     * WHAT IT STRIPS (order matters — applied left to right):
     *   Markdown headings  : "### My Title" → "My Title"
     *   Bold markers       : "**hello**"    → "hello"
     *   Italic markers     : "*hello*"      → "hello"
     *   List prefixes      : "- item"       → "item"  (dash or bullet)
     *   Leading whitespace : "  text"       → "text"
     *
     * TRUNCATION:
     *   If the stripped line is longer than MAX_TITLE_LENGTH characters it is
     *   trimmed at a word boundary (no mid-word cut) and "…" is appended.
     *   MAX_TITLE_LENGTH = 50 matches the NoteCard preview width on most phones.
     *
     * EMPTY INPUT:
     *   Returns an empty string — callers are responsible for falling back.
     */
    private fun deriveSmartTitle(content: String): String {
        val MAX_TITLE_LENGTH = 50

        // Find the first line that has visible characters
        val firstLine = content.lines().firstOrNull { it.isNotBlank() } ?: return ""

        val stripped = firstLine
            .trim()
            // Strip markdown heading prefixes (### ## #)
            .removePrefix("### ").removePrefix("## ").removePrefix("# ")
            // Strip bold markers (**text** or __text__)
            .removePrefix("**").removeSuffix("**")
            .removePrefix("__").removeSuffix("__")
            // Strip italic markers (*text* or _text_)
            .removePrefix("*").removeSuffix("*")
            .removePrefix("_").removeSuffix("_")
            // Strip unordered list prefixes (- or •)
            .removePrefix("- ").removePrefix("• ")
            // Strip blockquote marker
            .removePrefix("> ")
            .trim()

        if (stripped.isEmpty()) return ""

        // Truncate cleanly at a space boundary to avoid mid-word cuts
        return if (stripped.length <= MAX_TITLE_LENGTH) {
            stripped
        } else {
            // Walk back from MAX_TITLE_LENGTH to the nearest space
            val cutAt = stripped.lastIndexOf(' ', MAX_TITLE_LENGTH)
                .takeIf { it > MAX_TITLE_LENGTH / 2 }  // only if the space isn't too far back
                ?: MAX_TITLE_LENGTH
            stripped.take(cutAt).trimEnd() + "…"
        }
    }

    /**
     * Derive a title from the inline blocks present when body text is blank.
     *
     * Covers three common block-only note patterns:
     *   • Voice note  — only AUDIO blocks                  → "Voice note"
     *   • Photo note  — IMAGE blocks (no todo)             → "Note with image"
     *   • Checklist   — any TODO block                     → "Checklist"
     *   • Mixed       — multiple types                     → "Note" (generic)
     *
     * This is called only after deriveSmartTitle() returns empty, so a note
     * that has both text and blocks will always use the text-derived title.
     */
    private fun deriveBlockTitle(blocks: List<InlineBlock>): String {
        val hasImage = blocks.any { it.type == InlineBlockType.IMAGE }
        val hasAudio = blocks.any { it.type == InlineBlockType.AUDIO }
        val hasTodo = blocks.any { it.type == InlineBlockType.TODO }

        return when {
            hasAudio && !hasImage && !hasTodo -> "Voice note"
            hasImage && !hasTodo -> "Note with image"
            hasTodo && !hasImage && !hasAudio -> "Checklist"
            else -> "Note"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE FORMAT HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun hasFormat(formats: List<FormatRange>, start: Int, end: Int, type: FormatType) =
        formats.any { it.type == type && it.start <= start && it.end >= end }

    private fun addFormat(formats: List<FormatRange>, start: Int, end: Int, type: FormatType) =
        formats + FormatRange(start, end, type)

    private fun removeFormat(formats: List<FormatRange>, start: Int, end: Int, type: FormatType) =
        formats.filter { !(it.type == type && it.start == start && it.end == end) }

    private fun findInsertPosition(oldText: String, newText: String): Int {
        var i = 0
        while (i < oldText.length && i < newText.length && oldText[i] == newText[i]) i++
        return i
    }

    private fun adjustFormatsForTextChange(
        formats: List<FormatRange>, oldText: String, newText: String
    ): List<FormatRange> {
        val lengthDiff = newText.length - oldText.length
        if (lengthDiff == 0) return formats
        val changePos = findInsertPosition(oldText, newText)
        return formats.mapNotNull { range ->
            when {
                range.end <= changePos -> range
                range.start >= changePos -> FormatRange(
                    (range.start + lengthDiff).coerceAtLeast(0),
                    (range.end + lengthDiff).coerceAtLeast(range.start + 1),
                    range.type
                )

                else -> FormatRange(
                    range.start,
                    (range.end + lengthDiff).coerceAtLeast(range.start + 1),
                    range.type
                )
            }
        }.filter { it.start < newText.length && it.end <= newText.length && it.start < it.end }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WIKI LINK RESOLUTION  (Sprint 11)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolve a [[wiki link]] target and call back with the destination note ID.
     *
     * STEPS:
     * 1. Save the current note immediately — we're about to navigate away and
     *    we don't want the auto-save debounce to lose unsaved content.
     * 2. Search all non-trashed, decrypted notes for one whose title matches
     *    [linkTitle] (case-insensitive, leading/trailing whitespace ignored).
     * 3. If found     → onResolved(existingNote.id)
     * 4. If not found → insert a new blank note with [linkTitle] as its title,
     *                   then onResolved(newNoteId)
     *
     * WHY IN-MEMORY SEARCH?
     * Note titles are AES-256-GCM ciphertext in SQLite.
     * SQL LIKE compares against ciphertext — it can never match a plaintext
     * pattern. NoteRepository.getAllNotes() returns fully decrypted domain
     * models, so we search those instead.
     *
     * WHY flow.first()?
     * getAllNotes() is a Flow. We only need a single snapshot here (we're not
     * observing changes — we just need the current list to resolve one link).
     * .first() collects exactly one emission and then cancels the upstream.
     *
     * @param linkTitle  The inner text of [[...]]. Already trimmed by the caller.
     * @param onResolved Callback with the target note's ID. Always called on the
     *                   main dispatcher (viewModelScope default).
     */
    // ─────────────────────────────────────────────────────────────────────────
    // NOTE LINKING  (Sprint 11)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Load all non-trashed notes for the link picker bottom sheet.
     *
     * Called once when the user taps "Link" in the overflow menu — we fetch the
     * current list at that moment so the picker always shows fresh data.
     * The current note is excluded (can't link a note to itself).
     *
     * WHY in-memory search?
     * Titles are AES-256-GCM encrypted in the DB — SQL LIKE can't match
     * plaintext. We decrypt via getAllNotes() and filter in Kotlin.
     */
    fun loadAllNotesForLinkPicker() {
        viewModelScope.launch {
            val all = noteRepository.getAllNotes().first()
            val previews = all
                .filter { it.id != currentNoteId }   // exclude self
                .map { LinkedNotePreview(id = it.id, title = it.title.ifBlank { "Untitled" }) }
            _uiState.value = _uiState.value.copy(allNotesForPicker = previews)
        }
    }

    /**
     * Add [targetNoteId] to this note's linked set and persist immediately.
     * No-op if already linked.
     */
    fun linkNote(targetNoteId: String) {
        val current = _uiState.value.linkedNoteIds
        if (targetNoteId in current) return
        val updated = current + targetNoteId
        _uiState.value = _uiState.value.copy(linkedNoteIds = updated)
        // Reload resolved previews so the strip refreshes
        loadLinkedNotePreviews(updated)
        viewModelScope.launch { saveNote() }
    }

    /**
     * Remove [targetNoteId] from this note's linked set and persist immediately.
     */
    fun unlinkNote(targetNoteId: String) {
        val updated = _uiState.value.linkedNoteIds - targetNoteId
        _uiState.value = _uiState.value.copy(linkedNoteIds = updated)
        loadLinkedNotePreviews(updated)
        viewModelScope.launch { saveNote() }
    }

    /**
     * Resolve [ids] to their decrypted titles for display in the linked-notes
     * strip above the tags section. Dead IDs (deleted notes) are silently
     * dropped — the ID stays in linkedNoteIds but never shows in the UI.
     */
    private fun loadLinkedNotePreviews(ids: List<String>) {
        if (ids.isEmpty()) {
            _uiState.value = _uiState.value.copy(linkedNotePreviews = emptyList())
            return
        }
        viewModelScope.launch {
            val all = noteRepository.getAllNotes().first()
            val byId = all.associateBy { it.id }
            val previews = ids.mapNotNull { id ->
                byId[id]?.let { note ->
                    LinkedNotePreview(id = note.id, title = note.title.ifBlank { "Untitled" })
                }
            }
            _uiState.value = _uiState.value.copy(linkedNotePreviews = previews)
        }
    }

    /**
     * Load the previous and next diary entries relative to the current one.
     * (Sprint 12)
     *
     * WHY USE THE TITLE TO FIND NEIGHBOURS?
     * Diary entry titles follow the format "📅 March 14, 2026".
     * We parse them into Calendar objects to find the entries for
     * yesterday and tomorrow. SQL can't help here — titles are ciphertext.
     *
     * The results populate prevDiaryNoteId and nextDiaryNoteId in uiState,
     * which the DiaryTopBar uses to show/hide the ← → navigation arrows.
     *
     * @param currentTitle  The title of the current diary entry (used to find date)
     */
    /**
     * Load the adjacent calendar days for the diary prev/next arrows.
     * (Sprint 12)
     *
     * ALWAYS sets prevDiaryDateKey (yesterday) and nextDiaryDateKey (tomorrow),
     * UNLESS today is the current entry — then nextDiaryDateKey is null because
     * we don't let users create future diary entries.
     *
     * The dateKey (not noteId) is stored so the arrows can create new entries
     * for days that don't have one yet, matching how top diary apps work.
     */
    private fun loadDiaryNeighbours(currentTitle: String) {
        viewModelScope.launch {
            val titleFormatter =
                java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
            val keyFormatter =
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

            val stripped = currentTitle.removePrefix("📅 ").trim()
            val currentDate = try {
                titleFormatter.parse(stripped)
            } catch (_: Exception) {
                null
            }
                ?: return@launch

            val todayKey = keyFormatter.format(java.util.Date())
            val currentKey = keyFormatter.format(currentDate)

            val currentCal = java.util.Calendar.getInstance().apply { time = currentDate }
            val prevCal = (currentCal.clone() as java.util.Calendar).apply {
                add(
                    java.util.Calendar.DAY_OF_YEAR,
                    -1
                )
            }
            val nextCal = (currentCal.clone() as java.util.Calendar).apply {
                add(
                    java.util.Calendar.DAY_OF_YEAR,
                    +1
                )
            }

            _uiState.value = _uiState.value.copy(
                // Previous day: always available (users can write back-entries)
                prevDiaryDateKey = keyFormatter.format(prevCal.time),
                // Next day: only available up to today — no future entries
                nextDiaryDateKey = if (currentKey < todayKey) keyFormatter.format(nextCal.time) else null
            )
        }
    }

    /**
     * Navigate to a diary entry for [dateKey], creating it if it doesn't exist.
     * Called by the prev/next arrows in DiaryTopBar.
     * Emits the target noteId via diaryNavEvent — the screen handles navigation.
     */
    fun openOrCreateDiaryNeighbour(dateKey: String) {
        viewModelScope.launch {
            // First save the current note so no content is lost
            saveNote()

            val titleFormatter =
                java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
            val keyFormatter =
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

            // Check if an entry already exists for this date
            val allDiary = noteRepository.getDiaryEntries().first()
            val existing = allDiary.firstOrNull { note ->
                val stripped = note.title.removePrefix("📅 ").trim()
                try {
                    keyFormatter.format(titleFormatter.parse(stripped)!!) == dateKey
                } catch (_: Exception) {
                    false
                }
            }

            if (existing != null) {
                _diaryNavEvent.emit(existing.id)
                return@launch
            }

            // Create a new entry for this date
            val date = try {
                keyFormatter.parse(dateKey)
            } catch (_: Exception) {
                null
            }
                ?: return@launch
            val title = "📅 ${titleFormatter.format(date)}"
            val noteId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            noteRepository.insertNote(
                com.greenicephoenix.voidnote.domain.model.Note(
                    id = noteId,
                    title = title,
                    content = "",
                    createdAt = now,
                    updatedAt = now,
                    isDiaryEntry = true
                )
            )
            _diaryNavEvent.emit(noteId)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUPPORTING DATA CLASSES
// ─────────────────────────────────────────────────────────────────────────────

    /**
     * Lightweight note reference used in two places:
     *   1. The link picker list (allNotesForPicker) — all notes the user can link to
     *   2. The linked-notes strip (linkedNotePreviews) — notes already linked to this one
     *
     * We only need id + title; no content, no blocks, no formatting.
     * This keeps memory usage tiny even with hundreds of notes.
     */
    data class LinkedNotePreview(
        val id: String,
        val title: String
    )

// ─────────────────────────────────────────────────────────────────────────────
// UI STATE
// ─────────────────────────────────────────────────────────────────────────────

    data class NoteEditorUiState(
        val title: String = "",
        val content: String = "",
        val contentFormats: List<FormatRange> = emptyList(),
        val blocks: Map<String, InlineBlock> = emptyMap(),
        val isPinned: Boolean = false,
        val isArchived: Boolean = false,
        val tags: List<String> = emptyList(),
        val isNewNote: Boolean = true,
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val lastSaved: Long = 0L,

        // Active formatting for new typed characters
        val activeBold: Boolean = false,
        val activeItalic: Boolean = false,
        val activeUnderline: Boolean = false,
        val activeStrikethrough: Boolean = false,
        val activeHeading: FormatType? = null,

        // Camera capture state
        val cameraCaptureTempPath: String? = null,
        val pendingCameraUri: Uri? = null,

        // Voice recording state
        val isRecording: Boolean = false,
        val recordingElapsedMs: Long = 0L,
        val recordingTempPath: String? = null,

        // Preview toggle
        val showPreview: Boolean = false,

        // Sprint 5: folder name for TopBar display
        val currentFolderName: String? = null,

        // Sprint 6: note color accent
        val noteColor: NoteColor? = null,

        // ── Sprint 11: Note Linking ───────────────────────────────────────────────

        /**
         * The raw list of linked note IDs persisted to the DB.
         * Loaded from note.linkedNoteIds on open; updated by linkNote/unlinkNote.
         * Written back to DB on every saveNote() call.
         */
        val linkedNoteIds: List<String> = emptyList(),

        /**
         * Resolved previews (id + title) for linked notes — shown in the strip
         * above the tags section. Derived from linkedNoteIds by loadLinkedNotePreviews().
         * Dead links (deleted notes) are silently absent here even if still in linkedNoteIds.
         */
        val linkedNotePreviews: List<LinkedNotePreview> = emptyList(),

        /**
         * All notes available for the link picker bottom sheet.
         * Populated on demand when the user taps "Link" in the overflow menu.
         * Empty list = picker not yet loaded (shows a loading state).
         */
        val allNotesForPicker: List<LinkedNotePreview> = emptyList(),

        // ── Sprint 12: Diary mode ─────────────────────────────────────────────────

        /**
         * True when this note is a diary entry (isDiaryEntry = true in the DB).
         * When true the editor TopBar shows a locked date label and prev/next arrows
         * instead of the normal title field and overflow menu.
         */
        val isDiaryEntry: Boolean = false,

        /**
         * The date key "yyyy-MM-dd" of the previous calendar day.
         * Always set when isDiaryEntry=true — tapping navigates to that day's
         * entry (creating it if it doesn't exist yet).
         */
        val prevDiaryDateKey: String? = null,

        /**
         * The date key "yyyy-MM-dd" of the next calendar day.
         * null when today is the current entry (can't navigate to the future).
         */
        val nextDiaryDateKey: String? = null
    )