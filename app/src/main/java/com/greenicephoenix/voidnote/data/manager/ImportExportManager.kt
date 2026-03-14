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
                id             = entity.id,
                title          = entity.title,    // ← already encrypted Base64
                content        = entity.content,  // ← already encrypted Base64
                createdAt      = entity.createdAt,
                updatedAt      = entity.updatedAt,
                isPinned       = entity.isPinned,
                isArchived     = entity.isArchived,
                isTrashed      = entity.isTrashed,
                tags           = entity.tags,           // ← already encrypted Base64 list
                folderId       = entity.folderId,
                contentFormats = entity.contentFormats,
                linkedNoteIds  = entity.linkedNoteIds,  // Sprint 11: plain UUIDs, not encrypted
                isDiaryEntry   = entity.isDiaryEntry,   // Sprint 12: journal flag
                inlineBlocks   = (blocksByNote[entity.id] ?: emptyList()).map { block ->
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

            // ── 8b. Import notes + blocks ─────────────────────────────────────
            android.util.Log.d("VoidNoteImport", "Backup contains ${backup.notes.size} notes")
            var totalBlocksInBackup = 0
            backup.notes.forEach { totalBlocksInBackup += it.inlineBlocks.size }
            android.util.Log.d("VoidNoteImport", "Backup contains $totalBlocksInBackup total blocks")

            for (noteBackup in backup.notes) {
                android.util.Log.d("VoidNoteImport",
                    "Note ${noteBackup.id}: ${noteBackup.inlineBlocks.size} blocks in backup, " +
                            "alreadyExists=${noteBackup.id in existingNoteIds}")

                if (noteBackup.id !in existingNoteIds) {
                    noteDao.insertNote(
                        NoteEntity(
                            id             = noteBackup.id,
                            title          = noteBackup.title,
                            content        = noteBackup.content,
                            contentFormats = noteBackup.contentFormats,
                            createdAt      = noteBackup.createdAt,
                            updatedAt      = noteBackup.updatedAt,
                            isPinned       = noteBackup.isPinned,
                            isArchived     = noteBackup.isArchived,
                            isTrashed      = noteBackup.isTrashed,
                            tags           = noteBackup.tags,
                            folderId       = noteBackup.folderId,
                            // Sprint 11: restore link IDs. Because .vnbackup preserves
                            // original UUIDs, all links survive a full restore.
                            // If a linked note was deleted before the backup, its ID
                            // is kept here but silently filtered at display time.
                            linkedNoteIds  = noteBackup.linkedNoteIds,
                            isDiaryEntry   = noteBackup.isDiaryEntry   // Sprint 12: journal flag
                        )
                    )
                    notesImported++
                } else {
                    skipped++
                }

                // ── ALWAYS import blocks, regardless of whether the note was new ──
                // If the note existed but blocks were lost (fresh install, migration),
                // we still want to restore the blocks. Per-block ID check prevents duplicates.
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
                        android.util.Log.d("VoidNoteImport",
                            "  Inserted block ${blockBackup.id} type=${blockBackup.type}")
                    } else {
                        android.util.Log.d("VoidNoteImport",
                            "  Skipped block ${blockBackup.id} (already exists)")
                    }
                }
            }

            android.util.Log.d("VoidNoteImport",
                "Import done: $notesImported notes, $blocksImported blocks inserted, $skipped skipped")

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

    // ─── FLOW B: Import into existing vault (Settings → Import Backup) ───────────

    /**
     * Merge a .vnbackup into an already-unlocked vault.
     * K1 = current session key (stays active). K2 = derived from backup password (in-memory only).
     * Notes: decrypt(K2) → encrypt(K1) → insert. K1 is NEVER replaced.
     */
    suspend fun importIntoExistingVault(
        contentResolver: ContentResolver,
        uri: Uri,
        enteredPassword: String
    ): ImportResult {
        val tempDir = File(context.cacheDir, "import_b_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            var backupJson: String? = null
            val mediaFiles = mutableMapOf<String, File>()

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
                                FileOutputStream(tempFile).use { zip.copyTo(it) }
                                mediaFiles[fileName] = tempFile
                            }
                        }
                        zip.closeEntry(); entry = zip.nextEntry
                    }
                }
            } ?: return ImportResult(0, 0, 0, 0, 0, error = "Could not open backup file")

            if (backupJson == null)
                return ImportResult(0, 0, 0, 0, 0, error = "Invalid backup: backup.json missing")

            val backup = try {
                json.decodeFromString(VoidNoteBackup.serializer(), backupJson!!)
            } catch (e: Exception) {
                return ImportResult(0, 0, 0, 0, 0, error = "Corrupted backup.json: ${e.message}")
            }

            // Derive K2 in-memory — NEVER set as session key
            val backupSalt = encryption.decodeSalt(backup.salt)
            val k2         = encryption.deriveKey(enteredPassword, backupSalt)

            val existingNotes     = noteDao.getAllNotesWithTrash()
            val existingNoteIds   = existingNotes.mapTo(HashSet()) { it.id }
            val existingFolderIds = folderDao.getAllFoldersOnce().mapTo(HashSet()) { it.id }
            val existingBlockIds  = inlineBlockDao.getAllBlocksOnce().mapTo(HashSet()) { it.id }
            val existingContentById = existingNotes.associate { it.id to it.content }

            var notesImported = 0; var foldersImported = 0
            var blocksImported = 0; var mediaRestored = 0; var skipped = 0

            for (folder in backup.folders) {
                if (folder.id !in existingFolderIds) {
                    folderDao.insertFolder(FolderEntity(
                        id = folder.id, name = folder.name,
                        parentFolderId = folder.parentFolderId,
                        createdAt = folder.createdAt, updatedAt = folder.createdAt
                    ))
                    foldersImported++
                }
            }

            for (noteBackup in backup.notes) {
                val targetNoteId: String
                when {
                    noteBackup.id !in existingNoteIds -> {
                        val plainTitle   = encryption.decryptWithKey(noteBackup.title, k2)
                        val plainContent = encryption.decryptWithKey(noteBackup.content, k2)
                        val plainTags    = noteBackup.tags.map { encryption.decryptWithKey(it, k2) }
                        noteDao.insertNote(NoteEntity(
                            id = noteBackup.id,
                            title          = encryption.encrypt(plainTitle),
                            content        = encryption.encrypt(plainContent),
                            // Flow B: formats are plain indices, no decryption needed
                            contentFormats = noteBackup.contentFormats,
                            createdAt = noteBackup.createdAt, updatedAt = noteBackup.updatedAt,
                            isPinned = noteBackup.isPinned, isArchived = noteBackup.isArchived,
                            isTrashed = noteBackup.isTrashed,
                            tags = plainTags.map { encryption.encrypt(it) },
                            folderId = noteBackup.folderId
                        ))
                        notesImported++; targetNoteId = noteBackup.id
                    }
                    existingContentById[noteBackup.id] != noteBackup.content -> {
                        val newId = java.util.UUID.randomUUID().toString()
                        val plainTitle   = encryption.decryptWithKey(noteBackup.title, k2)
                        val plainContent = encryption.decryptWithKey(noteBackup.content, k2)
                        val plainTags    = noteBackup.tags.map { encryption.decryptWithKey(it, k2) }
                        noteDao.insertNote(NoteEntity(
                            id = newId,
                            title          = encryption.encrypt("$plainTitle (Restored)"),
                            content        = encryption.encrypt(plainContent),
                            contentFormats = noteBackup.contentFormats,
                            createdAt = noteBackup.createdAt, updatedAt = System.currentTimeMillis(),
                            isPinned = false, isArchived = false, isTrashed = false,
                            tags = plainTags.map { encryption.encrypt(it) },
                            folderId = noteBackup.folderId
                        ))
                        notesImported++; targetNoteId = newId
                    }
                    else -> { skipped++; targetNoteId = noteBackup.id }
                }

                for (blockBackup in noteBackup.inlineBlocks) {
                    if (blockBackup.id !in existingBlockIds) {
                        inlineBlockDao.insertBlock(InlineBlockEntity(
                            id = blockBackup.id, noteId = targetNoteId,
                            type = blockBackup.type, payload = blockBackup.payload,
                            createdAt = blockBackup.createdAt
                        ))
                        blocksImported++
                    }
                }
            }

            val imageDir = File(context.filesDir, "images").also { it.mkdirs() }
            val audioDir = File(context.filesDir, "audio").also  { it.mkdirs() }
            for ((fileName, tempFile) in mediaFiles) {
                val isAudio = backup.notes.any { n -> n.inlineBlocks.any { b -> b.type == "AUDIO" && b.payload.contains(fileName) } }
                val targetFile = File(if (isAudio) audioDir else imageDir, fileName)
                if (!targetFile.exists()) {
                    try {
                        val plain = encryption.decryptBytesWithKey(tempFile.readBytes(), k2) ?: continue
                        targetFile.writeBytes(encryption.encryptBytes(plain))
                        mediaRestored++
                    } catch (e: Exception) {
                        android.util.Log.e("VoidNoteImport", "Media re-encrypt failed: $fileName", e)
                    }
                }
            }

            return ImportResult(notesImported, foldersImported, blocksImported, mediaRestored, skipped)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ─── MARKDOWN IMPORT ──────────────────────────────────────────────────────

    /**
     * Count how many importable .md notes are in a single .md file or a .zip of .md files.
     * Called for the preview card BEFORE the actual import — fast, no encryption, no DB writes.
     *
     * @return The number of .md files found (each becomes one note).
     */
    fun countMarkdownNotes(contentResolver: ContentResolver, uri: Uri): Int {
        return try {
            val name = uri.lastPathSegment?.lowercase() ?: ""
            contentResolver.openInputStream(uri)?.use { stream ->
                if (name.endsWith(".zip")) {
                    // Count .md entries inside the ZIP
                    var count = 0
                    ZipInputStream(stream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".md", ignoreCase = true)) count++
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                    count
                } else {
                    // Single .md file = always 1 note
                    1
                }
            } ?: 0
        } catch (e: Exception) { 0 }
    }

    /**
     * Import notes from a single .md file or a ZIP of .md files.
     *
     * SUPPORTED INPUT:
     *   Single .md  — one note, YAML front matter optional
     *   .zip        — any ZIP containing .md files (Void Note plain text export,
     *                 Obsidian vault export, Bear export, or hand-crafted)
     *
     * YAML FRONT MATTER (optional, between leading --- delimiters):
     *   title:    Note title (falls back to filename without extension)
     *   tags:     Comma-separated tag list
     *   folder:   Folder name — looked up by name, created if missing
     *   pinned:   true / false
     *   archived: true / false
     *
     * ENCRYPTION:
     *   title, content and tags are individually encrypted with the session key
     *   before being written to the DB — same as any note created in the editor.
     *
     * DEDUPLICATION:
     *   Each imported note gets a fresh UUID. Unlike .vnbackup imports, we do NOT
     *   check for duplicates by ID (the originating app may have different IDs).
     *   Calling import twice WILL create duplicates — this is documented in the UI.
     *
     * @return ImportResult with notesImported, foldersImported, skippedDuplicates (0 — see above)
     */
    suspend fun importMarkdown(contentResolver: ContentResolver, uri: Uri): ImportResult {
        var notesImported  = 0
        var foldersCreated = 0

        // Cache: folderName → folderId so we create each folder at most once per import
        val folderCache = mutableMapOf<String, String>()

        // Pre-load all existing folder names to avoid duplicates
        val existingFolders = folderDao.getAllFoldersOnce()
        existingFolders.forEach { f -> folderCache[f.name.lowercase()] = f.id }

        try {
            val name = uri.lastPathSegment?.lowercase() ?: ""
            contentResolver.openInputStream(uri)?.use { stream ->
                if (name.endsWith(".zip")) {
                    ZipInputStream(stream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".md", ignoreCase = true)) {
                                val text     = zip.readBytes().toString(Charsets.UTF_8)
                                // Derive a fallback title from the file path (last segment, no extension)
                                val fallback = entry.name.substringAfterLast('/').removeSuffix(".md")
                                val parsed   = parseMarkdownFile(text, fallback)
                                val folderId = resolveMarkdownFolder(parsed.folderName, folderCache) { foldersCreated++ }
                                insertMarkdownNote(parsed, folderId)
                                notesImported++
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } else {
                    // Single .md file
                    val text     = stream.readBytes().toString(Charsets.UTF_8)
                    val fallback = uri.lastPathSegment?.removeSuffix(".md")?.removeSuffix(".MD") ?: "Imported Note"
                    val parsed   = parseMarkdownFile(text, fallback)
                    val folderId = resolveMarkdownFolder(parsed.folderName, folderCache) { foldersCreated++ }
                    insertMarkdownNote(parsed, folderId)
                    notesImported++
                }
            }
        } catch (e: Exception) {
            return ImportResult(
                notesImported       = notesImported,
                foldersImported     = foldersCreated,
                blocksImported      = 0,
                mediaFilesRestored  = 0,
                skippedDuplicates   = 0,
                error               = "Import failed: ${e.message}"
            )
        }

        return ImportResult(
            notesImported      = notesImported,
            foldersImported    = foldersCreated,
            blocksImported     = 0,
            mediaFilesRestored = 0,
            skippedDuplicates  = 0
        )
    }

    /**
     * Parse a single .md file into a structured note.
     *
     * YAML FRONT MATTER:
     *   The front matter block (between the first two --- lines) is parsed
     *   line-by-line. Only the keys listed below are read; all others ignored.
     *   Keys are case-insensitive for compatibility with other apps.
     *
     * TITLE RESOLUTION ORDER:
     *   1. `title:` from YAML front matter
     *   2. First # Heading in the body content
     *   3. [fallbackTitle] — typically derived from the filename
     *
     * @param text          Full file text (UTF-8)
     * @param fallbackTitle Title to use when YAML and heading are both absent
     */
    private fun parseMarkdownFile(text: String, fallbackTitle: String): ParsedMarkdownNote {
        val lines = text.lines()
        var i     = 0

        var title:     String?       = null
        var folderName: String?      = null
        var tags:       List<String> = emptyList()
        var isPinned   = false
        var isArchived = false

        // ── Parse YAML front matter ────────────────────────────────────────────
        // Only attempt parsing if the file starts with exactly "---" on its own line.
        if (lines.getOrNull(0)?.trim() == "---") {
            i = 1 // skip opening delimiter
            while (i < lines.size && lines[i].trim() != "---") {
                val line = lines[i].trim()
                // Split on first colon only — values may contain colons (e.g. URLs)
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key   = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    when (key) {
                        "title"    -> title      = value.ifBlank { null }
                        "folder"   -> folderName = value.ifBlank { null }
                        "tags"     -> tags       = value.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(5)
                        "pinned"   -> isPinned   = value.lowercase() == "true"
                        "archived" -> isArchived = value.lowercase() == "true"
                    }
                }
                i++
            }
            i++ // skip closing "---"
        }

        // ── Remaining lines are the note content ──────────────────────────────
        val contentLines = lines.drop(i)
        val content      = contentLines.joinToString("\n").trim()

        // ── Resolve title if YAML didn't supply one ────────────────────────────
        if (title == null) {
            // Look for the first # heading in the body
            val headingLine = contentLines.firstOrNull { it.trimStart().startsWith("# ") }
            title = headingLine?.trim()?.removePrefix("# ")?.trim()
        }
        if (title.isNullOrBlank()) title = fallbackTitle

        return ParsedMarkdownNote(
            title      = title!!,
            content    = content,
            tags       = tags,
            folderName = folderName,
            isPinned   = isPinned,
            isArchived = isArchived
        )
    }

    /**
     * Resolve (or create) a folder by name for a markdown import.
     * Uses [folderCache] to avoid duplicate DB writes per import session.
     * Calls [onFolderCreated] when a new folder row is inserted.
     *
     * @return The folder ID, or null if folderName is null/blank.
     */
    private suspend fun resolveMarkdownFolder(
        folderName: String?,
        folderCache: MutableMap<String, String>,
        onFolderCreated: () -> Unit
    ): String? {
        if (folderName.isNullOrBlank()) return null
        val key = folderName.lowercase()
        return folderCache.getOrPut(key) {
            // No existing folder by this name — create one
            val newId = java.util.UUID.randomUUID().toString()
            folderDao.insertFolder(
                FolderEntity(
                    id          = newId,
                    name        = folderName,
                    createdAt   = System.currentTimeMillis()
                )
            )
            onFolderCreated()
            newId
        }
    }

    /**
     * Encrypt and insert a single parsed markdown note into the DB.
     * Each call gets a fresh UUID — no dedup by ID.
     */
    private suspend fun insertMarkdownNote(parsed: ParsedMarkdownNote, folderId: String?) {
        val id        = java.util.UUID.randomUUID().toString()
        val now       = System.currentTimeMillis()
        val encTitle  = encryption.encrypt(parsed.title)
        val encContent = encryption.encrypt(parsed.content)
        val encTags   = parsed.tags.map { encryption.encrypt(it) }

        noteDao.insertNote(
            NoteEntity(
                id             = id,
                title          = encTitle,
                content        = encContent,
                contentFormats = emptyList(),
                createdAt      = now,
                updatedAt      = now,
                isPinned       = parsed.isPinned,
                isArchived     = parsed.isArchived,
                isTrashed      = false,
                tags           = encTags,
                folderId       = folderId,
                color          = null
            )
        )
    }

    /** Intermediate model used only during markdown parsing — never stored. */
    private data class ParsedMarkdownNote(
        val title:      String,
        val content:    String,
        val tags:       List<String>,
        val folderName: String?,
        val isPinned:   Boolean,
        val isArchived: Boolean
    )

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
    fun readBackupHeader(contentResolver: ContentResolver, uri: Uri): BackupHeader {
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
            ZipInputStream(inputStream).use { zip ->
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