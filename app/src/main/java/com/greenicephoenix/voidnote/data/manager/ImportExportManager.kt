package com.greenicephoenix.voidnote.data.manager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.local.dao.FolderDao
import com.greenicephoenix.voidnote.data.local.dao.InlineBlockDao
import com.greenicephoenix.voidnote.data.local.dao.NoteDao
import com.greenicephoenix.voidnote.data.local.entity.FolderEntity
import com.greenicephoenix.voidnote.data.local.entity.InlineBlockEntity
import com.greenicephoenix.voidnote.data.local.entity.NoteEntity
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import com.greenicephoenix.voidnote.domain.model.FormatRange
import com.greenicephoenix.voidnote.presentation.settings.BackupHeader
import com.greenicephoenix.voidnote.presentation.settings.FolderBackup
import com.greenicephoenix.voidnote.presentation.settings.ImportResult
import com.greenicephoenix.voidnote.presentation.settings.InlineBlockBackup
import com.greenicephoenix.voidnote.presentation.settings.NoteBackup
import com.greenicephoenix.voidnote.presentation.settings.VoidNoteBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ImportExportManager — produces and consumes Void Note backup files.
 *
 * ─── THE TWO FORMATS ──────────────────────────────────────────────────────────
 *
 * SECURE BACKUP  (.vnbackup)
 * ┌─ voidnote_2026-03-03.vnbackup (standard ZIP, custom extension)
 * ├── backup.json        ← VoidNoteBackup with encrypted note content + salt
 * └── media/
 *     ├── abc-123.enc    ← image file (already encrypted on device, copied as-is)
 *     └── def-456.enc    ← audio file (same)
 *
 * Notes stay ENCRYPTED inside backup.json. The salt allows key re-derivation:
 *   PBKDF2(vault_password + salt_from_backup) → same master key → same AES key
 * A stolen .vnbackup file without the vault password is unreadable.
 *
 * PLAIN TEXT ZIP  (.zip)
 * ┌─ voidnote_notes_2026-03-03.zip
 * ├── README.txt
 * ├── Inbox/                  ← notes with no folder
 * │   └── My First Note.md
 * ├── Work/                   ← folder "Work"
 * │   ├── Meeting Notes.md
 * │   └── Project Ideas.md
 * └── Personal/
 *     └── Journal Entry.md
 *
 * Each .md file has YAML front matter with metadata, then the note content.
 * Export-only — not importable back into the app.
 *
 * ─── WHY NOTES STAY ENCRYPTED IN THE SECURE BACKUP ───────────────────────────
 *
 * Previous design decrypted notes before writing them to the backup.
 * Problems with that approach:
 *   1. A stolen backup = all notes readable with a text editor
 *   2. On import to a new device, notes must be re-encrypted with a NEW key
 *      that was just created — the old key's data must be decoded and
 *      re-encoded, creating a window where plaintext is in memory
 *   3. The backup has no key material, so cross-device restore requires
 *      creating a new vault first, then reimporting — two separate key setups
 *
 * Correct approach (this implementation):
 *   - Notes are stored in the backup as-is (encrypted Base64 ciphertext)
 *   - The salt travels with the backup
 *   - On any device: PBKDF2(same password + same salt) = same 256-bit key
 *   - That same key decrypts all ciphertext from the old device — no re-wrap needed
 *   - The only thing needed is the user's vault password
 *
 * ─── IMPORT STRATEGY: SKIP DUPLICATES ────────────────────────────────────────
 *
 * If a note ID already exists in the DB, it is left untouched.
 * This is correct for "I reinstalled and want to restore" — no duplicates.
 * It is also safe to call import multiple times.
 */
@Singleton
class ImportExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val inlineBlockDao: InlineBlockDao,
    private val encryption: NoteEncryptionManager,
    private val preferencesManager: PreferencesManager
) {

    private val json = Json {
        prettyPrint       = true
        ignoreUnknownKeys = true  // forward-compatible with future backup versions
        coerceInputValues = true  // missing optional fields get their declared defaults
    }

    // Extracts filePath values from IMAGE/AUDIO block payload JSON
    private val filePathRegex = Regex(""""filePath"\s*:\s*"([^"]+)"""")

    // ─── SECURE BACKUP EXPORT ─────────────────────────────────────────────────

    /**
     * Export all notes as a .vnbackup file (encrypted ZIP).
     *
     * The salt is read from DataStore and written into backup.json so the
     * receiving device can re-derive the master key from the user's password.
     *
     * @param contentResolver  LocalContext.current.contentResolver from the screen
     * @param uri              Destination URI from CreateDocument("application/octet-stream")
     * @return                 Number of notes exported
     */
    suspend fun exportSecureBackup(contentResolver: ContentResolver, uri: Uri): Int {
        // ── 1. Read salt from DataStore ───────────────────────────────────────
        // The salt was generated at vault setup and stored at that time.
        // It must be included in the backup so cross-device restore works.
        val saltBase64 = preferencesManager.vaultSaltFlow.first()
        check(saltBase64.isNotEmpty()) { "Vault salt not found — vault may not be set up." }

        // ── 2. Load raw DB rows (encrypted ciphertext, NOT domain models) ─────
        // We query NoteDAO directly so we get the encrypted title/content/tags.
        // NoteRepository.getAllNotes() would give us decrypted domain models —
        // we DON'T want that here because notes must stay encrypted in the backup.
        val noteEntities   = noteDao.getAllNotesWithTrash()
        val folderEntities = folderDao.getAllFoldersOnce()
        val allBlocks      = inlineBlockDao.getAllBlocksOnce()

        val blocksByNote = allBlocks.groupBy { it.noteId }

        // ── 3. Build NoteBackup objects (encrypted fields as-is) ──────────────
        val noteBackups = noteEntities.map { entity ->
            NoteBackup(
                id           = entity.id,
                title        = entity.title,    // ← already encrypted Base64
                content      = entity.content,  // ← already encrypted Base64
                createdAt    = entity.createdAt,
                updatedAt    = entity.updatedAt,
                isPinned     = entity.isPinned,
                isArchived   = entity.isArchived,
                isTrashed    = entity.isTrashed,
                // trashedAt not referenced — NoteEntity may or may not have this
                // field depending on which DB version is installed. It defaults
                // to null on import, which is the correct behaviour for restored notes.
                tags         = entity.tags,     // ← already encrypted Base64 list
                folderId     = entity.folderId,
                inlineBlocks = (blocksByNote[entity.id] ?: emptyList()).map { block ->
                    InlineBlockBackup(
                        id        = block.id,
                        noteId    = block.noteId,
                        type      = block.type,
                        payload   = block.payload,
                        createdAt = block.createdAt
                    )
                }
            )
        }

        // ── 4. Collect media file paths from IMAGE/AUDIO blocks ───────────────
        val mediaFilePaths = mutableSetOf<String>()
        for (block in allBlocks) {
            if (block.type == "IMAGE" || block.type == "AUDIO") {
                filePathRegex.find(block.payload)
                    ?.groupValues?.getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { mediaFilePaths.add(it) }
            }
        }

        // ── 5. Build VoidNoteBackup with the salt included ────────────────────
        val backup = VoidNoteBackup(
            version     = "2.0",
            salt        = saltBase64,           // ← KEY ADDITION: enables cross-device restore
            verificationBlob = encryption.createVerificationBlob(),
            exportDate  = System.currentTimeMillis(),
            appVersion  = getAppVersion(),
            noteCount   = noteBackups.size,
            folderCount = folderEntities.size,
            mediaCount  = mediaFilePaths.size,
            notes       = noteBackups,
            folders     = folderEntities.map { folder ->
                FolderBackup(
                    id             = folder.id,
                    name           = folder.name,
                    createdAt      = folder.createdAt,
                    parentFolderId = folder.parentFolderId
                )
            }
        )

        // ── 6. Write the ZIP ──────────────────────────────────────────────────
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zip ->

                // Entry 1: backup.json (encrypted note content + salt + metadata)
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(json.encodeToString(VoidNoteBackup.serializer(), backup).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // Entries 2+: media/*.enc — copy verbatim (already encrypted)
                for (filePath in mediaFilePaths) {
                    val file = File(filePath)
                    if (file.exists() && file.isFile) {
                        zip.putNextEntry(ZipEntry("media/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")

        return noteBackups.size
    }

    // ─── PLAIN TEXT ZIP EXPORT ────────────────────────────────────────────────

    /**
     * Export all notes as a human-readable ZIP with folder structure.
     *
     * Structure:
     *   voidnote_notes_YYYY-MM-DD.zip
     *   ├── README.txt
     *   ├── Inbox/        ← notes with no folder assigned
     *   │   └── Note Title.md
     *   └── [Folder Name]/
     *       └── Note Title.md
     *
     * Each .md file contains YAML front matter with metadata, then content.
     * Notes are DECRYPTED because the whole purpose of plain text export is
     * human readability outside the app.
     *
     * @param contentResolver  LocalContext.current.contentResolver
     * @param uri              Destination URI from CreateDocument("application/zip")
     * @return                 Number of notes exported
     */
    suspend fun exportPlainTextZip(contentResolver: ContentResolver, uri: Uri): Int {
        val noteEntities   = noteDao.getAllNotesOnce()
        val folderEntities = folderDao.getAllFoldersOnce()

        // Build a map of folderId → folder name for quick lookup
        val folderNameById = folderEntities.associate { it.id to it.name }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // Track used filenames per directory to handle duplicate note titles
        val usedNames = mutableMapOf<String, MutableSet<String>>()

        contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zip ->

                // ── README.txt ────────────────────────────────────────────────
                zip.putNextEntry(ZipEntry("README.txt"))
                zip.write(buildReadme(noteEntities.size, folderEntities.size).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // ── One .md file per note ─────────────────────────────────────
                for (entity in noteEntities) {
                    // Skip trashed notes — they are in the bin, not real content
                    if (entity.isTrashed) continue

                    // Determine the directory: folder name or "Inbox" for unfiled
                    val dirName = if (entity.folderId != null) {
                        sanitizeFileName(folderNameById[entity.folderId] ?: "Unknown Folder")
                    } else {
                        "Inbox"
                    }

                    // Decrypt the fields for human-readable output
                    val title   = encryption.decrypt(entity.title).ifBlank { "Untitled" }
                    val content = encryption.decrypt(entity.content)
                    val tags    = entity.tags.map { encryption.decrypt(it) }

                    // Build a unique filename (title + numeric suffix if collision)
                    val namesInDir = usedNames.getOrPut(dirName) { mutableSetOf() }
                    val fileName   = uniqueFileName(sanitizeFileName(title), namesInDir)
                    namesInDir.add(fileName)

                    val entryPath = "$dirName/$fileName.md"

                    zip.putNextEntry(ZipEntry(entryPath))
                    zip.write(buildMarkdownFile(
                        title     = title,
                        content   = content,
                        tags      = tags,
                        folder    = if (entity.folderId != null) folderNameById[entity.folderId] else null,
                        isPinned  = entity.isPinned,
                        isArchived = entity.isArchived,
                        createdAt = dateFormat.format(Date(entity.createdAt)),
                        updatedAt = dateFormat.format(Date(entity.updatedAt))
                    ).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")

        return noteEntities.count { !it.isTrashed }
    }

    // ─── SECURE BACKUP IMPORT ─────────────────────────────────────────────────

    /**
     * Import a .vnbackup file onto this device.
     *
     * The password verification must have already happened BEFORE this is called
     * (handled by SettingsViewModel.verifyExportPassword()). By the time this
     * function runs, we know the entered password is correct.
     *
     * What happens:
     *   1. Unzip: backup.json into memory, media files into a temp directory
     *   2. Parse backup.json → VoidNoteBackup
     *   3. Derive the master key: PBKDF2(entered password + salt from backup)
     *   4. Activate the derived key as the session key
     *   5. Store the salt + wrapped key so future launches work without password
     *   6. Store a fresh verification blob for this device
     *   7. Insert folders and notes (skip duplicates)
     *   8. Copy media files to their permanent directories
     *
     * @param contentResolver  LocalContext.current.contentResolver
     * @param uri              Source URI from OpenDocument launcher
     * @param enteredPassword  The vault password the user typed (already verified)
     * @return                 ImportResult with counts and any error message
     */
    suspend fun importSecureBackup(
        contentResolver: ContentResolver,
        uri: Uri,
        enteredPassword: String
    ): ImportResult {
        val tempDir = File(context.cacheDir, "import_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            var backupJson: String?                    = null
            val mediaFiles = mutableMapOf<String, File>() // filename.enc → temp File

            // ── 1. Unzip ──────────────────────────────────────────────────────
            contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "backup.json" ->
                                backupJson = zip.readBytes().toString(Charsets.UTF_8)

                            entry.name.startsWith("media/") && entry.name.endsWith(".enc") -> {
                                val fileName = entry.name.removePrefix("media/")
                                val tempFile = File(tempDir, fileName)
                                FileOutputStream(tempFile).use { out -> zip.copyTo(out) }
                                mediaFiles[fileName] = tempFile
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return ImportResult(0, 0, 0, 0, 0, error = "Could not open backup file")

            if (backupJson == null) {
                return ImportResult(0, 0, 0, 0, 0,
                    error = "Invalid backup: backup.json missing from ZIP")
            }

            // ── 2. Parse JSON ─────────────────────────────────────────────────
            val backup = try {
                json.decodeFromString(VoidNoteBackup.serializer(), backupJson!!)
            } catch (e: Exception) {
                return ImportResult(0, 0, 0, 0, 0,
                    error = "Corrupted backup.json: ${e.message}")
            }

            if (backup.salt.isBlank()) {
                return ImportResult(0, 0, 0, 0, 0,
                    error = "Backup is missing salt — cannot restore encryption.")
            }

            // ── 3. Derive the master key from the backup's salt ───────────────
            // PBKDF2(same password + same salt) = same 256-bit key that originally
            // encrypted all the notes in this backup.
            val salt      = encryption.decodeSalt(backup.salt)
            val masterKey = encryption.deriveKey(enteredPassword, salt)

            // ── 4. Activate the derived key ───────────────────────────────────
            encryption.activateKey(masterKey)

            // ── 5. Store salt + wrapped key so future launches are seamless ───
            // Without this, every app launch after import would require a password.
            val wrappedKey = encryption.wrapAndEncode(masterKey)
            preferencesManager.setVaultSalt(backup.salt)
            preferencesManager.setVaultWrappedKey(wrappedKey)
            preferencesManager.setVaultSetupComplete()

            // ── 6. Store fresh verification blob for this device ──────────────
            val blob = encryption.createVerificationBlob()
            preferencesManager.setVaultVerificationBlob(blob)

            // ── 7. Check for existing IDs (skip duplicates) ───────────────────
            val existingNoteIds   = noteDao.getAllNotesOnce().mapTo(HashSet()) { it.id }
            val existingFolderIds = folderDao.getAllFoldersOnce().mapTo(HashSet()) { it.id }
            val existingBlockIds  = inlineBlockDao.getAllBlocksOnce().mapTo(HashSet()) { it.id }

            var notesImported   = 0
            var foldersImported = 0
            var blocksImported  = 0
            var mediaRestored   = 0
            var skipped         = 0

            // ── 8a. Import folders ────────────────────────────────────────────
            for (folder in backup.folders) {
                if (folder.id !in existingFolderIds) {
                    folderDao.insertFolder(
                        FolderEntity(
                            id             = folder.id,
                            name           = folder.name,
                            parentFolderId = folder.parentFolderId,
                            createdAt      = folder.createdAt,
                            updatedAt      = folder.createdAt
                        )
                    )
                    foldersImported++
                } else {
                    skipped++
                }
            }

            // ── 8b. Import notes ──────────────────────────────────────────────
            for (noteBackup in backup.notes) {
                if (noteBackup.id !in existingNoteIds) {
                    // Notes are inserted with their encrypted ciphertext as-is.
                    // The session key (just activated above) will decrypt them
                    // correctly because it was derived from the same password + salt.
                    //
                    // contentFormats = emptyList<FormatRange>()
                    //   The type argument MUST be explicit — see ImportExportManager
                    //   comments in previous sessions for the full explanation.
                    //
                    // trashedAt is intentionally NOT set. It defaults to null in
                    //   NoteEntity (Long? = null). Restored notes should start a
                    //   fresh 30-day trash window rather than inheriting an old
                    //   timestamp. Not referencing trashedAt also ensures this
                    //   file compiles against both pre-v5 and v5 NoteEntity.
                    noteDao.insertNote(
                        NoteEntity(
                            id             = noteBackup.id,
                            title          = noteBackup.title,    // encrypted as-is
                            content        = noteBackup.content,  // encrypted as-is
                            contentFormats = emptyList<FormatRange>(),
                            createdAt      = noteBackup.createdAt,
                            updatedAt      = noteBackup.updatedAt,
                            isPinned       = noteBackup.isPinned,
                            isArchived     = noteBackup.isArchived,
                            isTrashed      = noteBackup.isTrashed,
                            tags           = noteBackup.tags,    // encrypted as-is
                            folderId       = noteBackup.folderId
                        )
                    )
                    notesImported++

                    for (blockBackup in noteBackup.inlineBlocks) {
                        if (blockBackup.id !in existingBlockIds) {
                            inlineBlockDao.insertBlock(
                                InlineBlockEntity(
                                    id        = blockBackup.id,
                                    noteId    = blockBackup.noteId,
                                    type      = blockBackup.type,
                                    payload   = blockBackup.payload,
                                    createdAt = blockBackup.createdAt
                                )
                            )
                            blocksImported++
                        }
                    }
                } else {
                    skipped++
                }
            }

            // ── 8c. Restore media files ───────────────────────────────────────
            val imageDir = File(context.filesDir, "images").also { it.mkdirs() }
            val audioDir = File(context.filesDir, "audio").also  { it.mkdirs() }

            val importedMediaNames = mutableSetOf<String>()
            for (note in backup.notes) {
                if (note.id !in existingNoteIds) {
                    for (block in note.inlineBlocks) {
                        if (block.type == "IMAGE" || block.type == "AUDIO") {
                            filePathRegex.find(block.payload)
                                ?.groupValues?.getOrNull(1)
                                ?.let { importedMediaNames.add(File(it).name) }
                        }
                    }
                }
            }

            for ((fileName, tempFile) in mediaFiles) {
                if (fileName in importedMediaNames) {
                    val isAudio = backup.notes.any { note ->
                        note.inlineBlocks.any { block ->
                            block.type == "AUDIO" && block.payload.contains(fileName)
                        }
                    }
                    val targetFile = File(if (isAudio) audioDir else imageDir, fileName)
                    if (!targetFile.exists()) {
                        tempFile.copyTo(targetFile, overwrite = false)
                        mediaRestored++
                    }
                }
            }

            return ImportResult(
                notesImported      = notesImported,
                foldersImported    = foldersImported,
                blocksImported     = blocksImported,
                mediaFilesRestored = mediaRestored,
                skippedDuplicates  = skipped
            )

        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Sanitize a string for use as a file or directory name.
     * Strips characters illegal on Android/Windows filesystems.
     * Truncates to 50 characters to keep paths short.
     */
    private fun sanitizeFileName(name: String): String {
        val illegal = Regex("""[\\/:*?"<>|]""")
        return name.replace(illegal, "_")
            .trim()
            .take(50)
            .ifBlank { "Untitled" }
    }

    /**
     * Return a unique filename by appending _2, _3, ... if [base] already exists
     * in [used]. Does NOT add the extension — caller appends ".md".
     */
    private fun uniqueFileName(base: String, used: Set<String>): String {
        if (base !in used) return base
        var i = 2
        while ("${base}_$i" in used) i++
        return "${base}_$i"
    }

    /**
     * Build the YAML front matter + content for a plain text .md file.
     *
     * Output format:
     * ---
     * title: Meeting Notes
     * created: 2026-03-03 14:30
     * updated: 2026-03-03 15:45
     * tags: work, meetings
     * folder: Work
     * pinned: true
     * ---
     *
     * [note content here]
     */
    private fun buildMarkdownFile(
        title: String,
        content: String,
        tags: List<String>,
        folder: String?,
        isPinned: Boolean,
        isArchived: Boolean,
        createdAt: String,
        updatedAt: String
    ): String = buildString {
        appendLine("---")
        appendLine("title: $title")
        appendLine("created: $createdAt")
        appendLine("updated: $updatedAt")
        if (tags.isNotEmpty()) appendLine("tags: ${tags.joinToString(", ")}")
        if (folder != null) appendLine("folder: $folder")
        if (isPinned)  appendLine("pinned: true")
        if (isArchived) appendLine("archived: true")
        appendLine("---")
        appendLine()
        append(content)
    }

    /**
     * README.txt content placed at the root of plain text ZIP exports.
     */
    private fun buildReadme(noteCount: Int, folderCount: Int): String = buildString {
        appendLine("VOID NOTE — Plain Text Export")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
        appendLine("Notes: $noteCount   Folders: $folderCount")
        appendLine()
        appendLine("This ZIP contains your notes as Markdown (.md) files.")
        appendLine("Files are organized by folder. Unfiled notes are in 'Inbox/'.")
        appendLine()
        appendLine("Each file starts with YAML front matter (between '---' lines)")
        appendLine("containing the note's metadata, followed by the note content.")
        appendLine()
        appendLine("This export is for reading/archiving only.")
        appendLine("To back up your notes for re-importing, use the")
        appendLine("'Secure Backup (.vnbackup)' option in Void Note.")
    }

    fun generateSecureBackupFilename(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "voidnote_$ts.vnbackup"
    }

    fun generatePlainTextFilename(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "voidnote_notes_$ts.zip"
    }

    private fun getAppVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) { "1.0.0" }

    /**
     * Read only the backup header from a .vnbackup ZIP — fast, low memory.
     * Does NOT extract media files or parse note content.
     * Called by RestoreBackupViewModel so it can show note/folder counts and
     * verify the password before committing to the full import.
     */
    suspend fun readBackupHeader(contentResolver: ContentResolver, uri: Uri): BackupHeader {
        val backupJson = readBackupJson(contentResolver, uri)
            ?: throw IllegalArgumentException("backup.json not found in ZIP")
        val backup = json.decodeFromString(VoidNoteBackup.serializer(), backupJson)
        return BackupHeader(
            salt               = backup.salt,
            verificationBlob   = backup.verificationBlob,
            noteCount          = backup.noteCount,
            folderCount        = backup.folderCount,
            appVersion         = backup.appVersion
        )
    }

    /**
     * Read only backup.json from a .vnbackup ZIP without extracting media.
     * Returns null if the ZIP doesn't contain backup.json.
     */
    private fun readBackupJson(contentResolver: ContentResolver, uri: Uri): String? {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            java.util.zip.ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json") {
                        return zip.readBytes().toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }
}