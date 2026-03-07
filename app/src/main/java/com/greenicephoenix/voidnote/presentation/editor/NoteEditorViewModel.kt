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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private var autoSaveJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var currentNoteId: String = noteId
    private var currentFolderId: String? = null
    private var isDeleting = false

    // ── SPRINT 5: Expose folder list ──────────────────────────────────────────
    // Converted to StateFlow with WhileSubscribed so it stops collecting when
    // no UI is observing (e.g. when the screen is in the background).
    // SharingStarted.WhileSubscribed(5_000) means: keep the upstream Flow alive
    // for 5 seconds after the last subscriber disappears — handles config changes.
    val folders: StateFlow<List<Folder>> = folderRepository.getAllFolders()
        .stateIn(
            scope            = viewModelScope,
            started          = SharingStarted.WhileSubscribed(5_000),
            initialValue     = emptyList()
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
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTE LOADING
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadNote() {
        if (noteId == "new") {
            currentNoteId = UUID.randomUUID().toString()
            _uiState.value = _uiState.value.copy(
                title = "", content = "", isNewNote = true, isLoading = false
            )
            observeBlocks()
        } else {
            viewModelScope.launch {
                val note = noteRepository.getNoteById(noteId)
                if (note != null) {
                    currentNoteId   = note.id
                    currentFolderId = note.folderId

                    val logicalContent = DocumentParser.extractLogicalContent(note.content)

                    // ── SPRINT 5: Resolve folder name for display ──────────────
                    // Look up the folder name once. Cheap: one DB hit, not a stream.
                    // The name is only needed for display in the overflow menu.
                    val folderName = note.folderId?.let { id ->
                        folderRepository.getFolderById(id)?.name
                    }

                    _uiState.value = _uiState.value.copy(
                        title             = note.title,
                        content           = logicalContent,
                        contentFormats    = note.contentFormats,
                        isPinned          = note.isPinned,
                        isArchived        = note.isArchived,
                        tags              = note.tags,
                        isNewNote         = false,
                        isLoading         = false,
                        currentFolderName = folderName,      // ← SPRINT 5
                        noteColor         = note.color      // ← SPRINT 7 FIX: load saved color into UI state
                    )
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
            val itemId  = UUID.randomUUID().toString()
            val newBlock = InlineBlock(
                id        = blockId,
                noteId    = currentNoteId,
                type      = InlineBlockType.TODO,
                payload   = InlineBlockPayload.Todo(
                    items = listOf(TodoItem(id = itemId, text = "", isChecked = false, sortOrder = 0))
                ),
                createdAt = System.currentTimeMillis()
            )
            inlineBlockRepository.insertBlock(newBlock)
            delay(50)
            saveNote()
        }
    }

    fun toggleTodoItem(blockId: String, itemId: String) {
        viewModelScope.launch {
            val block   = _uiState.value.blocks[blockId] ?: return@launch
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
            val block   = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch
            val maxOrder = payload.items.maxOfOrNull { it.sortOrder } ?: -1
            val newItem  = TodoItem(id = UUID.randomUUID().toString(), text = "", isChecked = false, sortOrder = maxOrder + 1)
            inlineBlockRepository.updateBlock(block.copy(payload = payload.copy(items = payload.items + newItem)))
        }
    }

    fun updateTodoItemText(blockId: String, itemId: String, newText: String) {
        viewModelScope.launch {
            val block   = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch
            inlineBlockRepository.updateBlock(
                block.copy(payload = payload.copy(
                    items = payload.items.map { if (it.id == itemId) it.copy(text = newText) else it }
                ))
            )
        }
    }

    fun deleteTodoItem(blockId: String, itemId: String) {
        viewModelScope.launch {
            val block   = _uiState.value.blocks[blockId] ?: return@launch
            val payload = block.payload as? InlineBlockPayload.Todo ?: return@launch
            val remaining = payload.items.filter { it.id != itemId }
            if (remaining.isEmpty()) { deleteBlock(blockId); return@launch }
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
                id        = blockId,
                noteId    = currentNoteId,
                type      = InlineBlockType.IMAGE,
                payload   = InlineBlockPayload.Image(filePath = encFilePath, caption = "", width = width, height = height),
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
                id        = blockId,
                noteId    = currentNoteId,
                type      = InlineBlockType.IMAGE,
                payload   = InlineBlockPayload.Image(filePath = encFilePath, caption = "", width = width, height = height),
                createdAt = System.currentTimeMillis()
            )
            inlineBlockRepository.insertBlock(newBlock)
            delay(50)
            saveNote()
        }
    }

    fun updateImageCaption(blockId: String, newCaption: String) {
        viewModelScope.launch {
            val block   = _uiState.value.blocks[blockId] ?: return@launch
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
            val started  = voiceRecorder.startRecording(tempPath)

            if (!started) {
                android.util.Log.e("NoteEditor", "startRecording: MediaRecorder failed to start")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isRecording        = true,
                recordingElapsedMs = 0L,
                recordingTempPath  = tempPath
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
        val tempPath   = _uiState.value.recordingTempPath ?: return
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
                id        = blockId,
                noteId    = currentNoteId,
                type      = InlineBlockType.AUDIO,
                payload   = InlineBlockPayload.Audio(
                    filePath   = encFilePath,
                    durationMs = durationMs.coerceAtLeast(0L)
                ),
                createdAt = System.currentTimeMillis()
            )

            inlineBlockRepository.insertBlock(newBlock)
            delay(50)
            saveNote()

            // Clear recording state — UI returns to normal
            _uiState.value = _uiState.value.copy(
                isRecording        = false,
                recordingTempPath  = null,
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
                else -> { /* TODO — no file */ }
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
        if (state.title.isBlank() && state.content.isBlank()) return

        val currentBlocks = inlineBlockRepository.getBlocksForNote(currentNoteId).first()
        val rawContent    = DocumentParser.buildRawContent(
            logicalContent = state.content,
            blocks         = currentBlocks
        )

        val note = Note(
            id             = currentNoteId,
            title          = state.title,
            content        = rawContent,
            contentFormats = state.contentFormats,
            createdAt      = System.currentTimeMillis(),
            updatedAt      = System.currentTimeMillis(),
            isPinned       = state.isPinned,
            isArchived     = state.isArchived,
            isTrashed      = false,
            tags           = state.tags,
            folderId       = currentFolderId,
            color    = _uiState.value.noteColor
        )

        if (state.isNewNote) {
            noteRepository.insertNote(note, folderId = currentFolderId)
            _uiState.value = _uiState.value.copy(isNewNote = false, lastSaved = System.currentTimeMillis())
        } else {
            noteRepository.updateNote(note)
            _uiState.value = _uiState.value.copy(lastSaved = System.currentTimeMillis())
        }
    }

    private suspend fun ensureNotePersisted() {
        if (!_uiState.value.isNewNote) return
        val state = _uiState.value
        val note  = Note(
            id             = currentNoteId,
            title          = state.title,
            content        = state.content,
            contentFormats = state.contentFormats,
            createdAt      = System.currentTimeMillis(),
            updatedAt      = System.currentTimeMillis(),
            isPinned       = state.isPinned,
            isArchived     = state.isArchived,
            isTrashed      = false,
            tags           = state.tags,
            folderId       = currentFolderId
        )
        noteRepository.insertNote(note, folderId = currentFolderId)
        _uiState.value = _uiState.value.copy(isNewNote = false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONTENT CHANGES
    // ─────────────────────────────────────────────────────────────────────────

    fun onTitleChange(newTitle: String) {
        _uiState.value = _uiState.value.copy(title = newTitle)
        scheduleAutoSave()
    }

    fun onContentChange(newContent: String) {
        val oldContent = _uiState.value.content
        val oldFormats = _uiState.value.contentFormats

        if (newContent.length > oldContent.length) {
            val insertPos    = findInsertPosition(oldContent, newContent)
            val insertLength = newContent.length - oldContent.length
            var newFormats   = adjustFormatsForTextChange(oldFormats, oldContent, newContent)

            if (insertPos >= 0 && insertLength > 0) {
                val insertEnd = insertPos + insertLength
                if (_uiState.value.activeBold)          newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.BOLD)
                if (_uiState.value.activeItalic)         newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.ITALIC)
                if (_uiState.value.activeUnderline)      newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.UNDERLINE)
                if (_uiState.value.activeStrikethrough)  newFormats = addFormat(newFormats, insertPos, insertEnd, FormatType.STRIKETHROUGH)
                _uiState.value.activeHeading?.let { newFormats = addFormat(newFormats, insertPos, insertEnd, it) }
            }
            _uiState.value = _uiState.value.copy(content = newContent, contentFormats = newFormats)
        } else {
            _uiState.value = _uiState.value.copy(
                content        = newContent,
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
        val hasFmt  = hasFormat(current, start, end, type)
        _uiState.value = _uiState.value.copy(
            contentFormats = if (hasFmt) removeFormat(current, start, end, type)
            else        addFormat(current, start, end, type)
        )
        scheduleAutoSave()
    }

    fun toggleActiveBold()          { _uiState.value = _uiState.value.copy(activeBold = !_uiState.value.activeBold) }
    fun toggleActiveItalic()        { _uiState.value = _uiState.value.copy(activeItalic = !_uiState.value.activeItalic) }
    fun toggleActiveUnderline()     { _uiState.value = _uiState.value.copy(activeUnderline = !_uiState.value.activeUnderline) }
    fun toggleActiveStrikethrough() { _uiState.value = _uiState.value.copy(activeStrikethrough = !_uiState.value.activeStrikethrough) }
    fun setActiveHeading(type: FormatType?) { _uiState.value = _uiState.value.copy(activeHeading = type) }

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
                (_uiState.value.title.isNotBlank() || _uiState.value.content.isNotBlank())) {
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
}

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
    val cameraCaptureTempPath: String? = null,  // path of temp JPEG during camera capture
    val pendingCameraUri: Uri? = null,           // URI to launch after permission granted

    // Voice recording state
    val isRecording: Boolean = false,            // true while MediaRecorder is active
    val recordingElapsedMs: Long = 0L,           // elapsed recording time, updated every 100ms
    val recordingTempPath: String? = null,       // path of plain .aac during active recording

    // Preview toggle — shows rendered formatted text instead of the editor
    val showPreview: Boolean = false,

    // ── SPRINT 5 ADDITION ─────────────────────────────────────────────────────
    // The name of the folder this note currently lives in.
    // Null means the note is at root level (no folder).
    // Used by TopBar to show "In: My Notes" in the overflow menu.
    val currentFolderName: String? = null,
    val noteColor: NoteColor? = null    // Sprint 6: current note color
)