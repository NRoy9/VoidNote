package com.greenicephoenix.voidnote.presentation.settings

import com.greenicephoenix.voidnote.domain.model.FormatRange
import kotlinx.serialization.Serializable

/**
 * Export / backup data models for Void Note.
 *
 * ─── TWO BACKUP FORMATS ───────────────────────────────────────────────────────
 *
 * 1. SECURE BACKUP (.vnbackup)
 *    A ZIP file containing backup.json + media files.
 *
 *    backup.json uses VoidNoteBackup as the root object.
 *    CRITICAL DESIGN DECISION: note content stays ENCRYPTED inside the backup.
 *    The salt field travels with the backup so any device can re-derive the key:
 *
 *      PBKDF2(vault_password + salt_from_backup) → same master key
 *      → same AES key → note ciphertext decrypts correctly
 *
 *    A stolen .vnbackup file is useless without the vault password.
 *    This is end-to-end encrypted backup — we never see plaintext.
 *
 * 2. PLAIN TEXT ZIP (.zip)
 *    A ZIP mirroring the app's folder structure.
 *    Notes exported as .md files with YAML front matter.
 *    EXPORT-ONLY — not importable back.
 *    Intended for reading/archiving outside the app.
 *
 * ─── SALT FIELD EXPLAINED ─────────────────────────────────────────────────────
 *
 * The salt is NOT secret — it is specifically designed to be stored alongside
 * the encrypted data. Its purpose is to ensure that two users with the same
 * password produce different keys (prevents rainbow-table attacks). The salt
 * alone reveals nothing; the vault password is still required.
 */

// ─── Secure backup models ──────────────────────────────────────────────────────

@Serializable
data class VoidNoteBackup(
    val version: String = "2.0",

    /**
     * Base64-encoded 16-byte salt used to derive the master key via PBKDF2.
     * Stored in the backup so the key can be re-derived on any device.
     * NOT SECRET — the vault password is required alongside this salt.
     */
    val salt: String,
    val verificationBlob: String = "",
    val exportDate: Long,
    val appVersion: String = "1.0.0",
    val noteCount: Int,
    val folderCount: Int,
    val mediaCount: Int = 0,
    val notes: List<NoteBackup>,
    val folders: List<FolderBackup>
)

/**
 * Individual note in the secure backup.
 *
 * ENCRYPTION BOUNDARY:
 * title, content, and tags contain ENCRYPTED ciphertext (Base64-encoded IV +
 * AES-256-GCM ciphertext), exactly as they are stored in the Room database.
 * No decryption happens during export. No re-encryption happens during import.
 * The same key derived from (password + salt) works in both directions.
 *
 * id, folderId, timestamps, and boolean flags are NOT encrypted because:
 * - They are metadata needed to restore note structure
 * - They contain no sensitive content
 * - Encrypting IDs would prevent referential integrity on import
 */
@Serializable
data class NoteBackup(
    val id: String,
    val title: String,       // encrypted Base64 ciphertext
    val content: String,     // encrypted Base64 ciphertext
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val tags: List<String> = emptyList(),  // each tag is encrypted Base64
    val folderId: String? = null,
    val inlineBlocks: List<InlineBlockBackup> = emptyList(),
    /**
     * Bold/italic/heading format ranges for this note's content.
     *
     * WHY DEFAULT emptyList():
     * Old backups (pre-Sprint 4) don't have this field. The Json parser is
     * configured with coerceInputValues = true, so missing fields fall back
     * to their declared default. Old backups restore with no formatting —
     * which is exactly what was happening before, just now intentionally.
     * New backups will carry formatting through correctly.
     *
     * NOT ENCRYPTED: format ranges contain only character indices and a
     * FormatType enum value. No user content, nothing sensitive.
     */
    val contentFormats: List<FormatRange> = emptyList()
)

/**
 * Inline block (TODO list, image, audio) in the secure backup.
 *
 * The payload is stored as-is (raw JSON string) because:
 * - Text content inside TODO items is part of the payload JSON
 * - But image/audio filePaths are paths on the original device,
 *   not sensitive in isolation
 * - The corresponding media .enc files in the backup are ALREADY ENCRYPTED
 *   at the byte level — no additional encryption needed here
 *
 * On import: the payload JSON is inserted directly into inline_blocks.
 * File references are updated to point to the new device's storage paths.
 */
@Serializable
data class InlineBlockBackup(
    val id: String,
    val noteId: String,
    val type: String,     // "TODO", "IMAGE", "AUDIO"
    val payload: String,  // raw JSON — same format as inline_blocks.payload in DB
    val createdAt: Long
)

@Serializable
data class FolderBackup(
    val id: String,
    val name: String,
    val createdAt: Long,
    val parentFolderId: String? = null
)

// ─── Import result ────────────────────────────────────────────────────────────

/**
 * Returned by ImportExportManager.importSecureBackup().
 * Drives the success/error UI in SettingsScreen.
 */
data class ImportResult(
    val notesImported: Int,
    val foldersImported: Int,
    val blocksImported: Int,
    val mediaFilesRestored: Int,
    val skippedDuplicates: Int,
    val error: String? = null  // non-null = import failed
) {
    val isSuccess: Boolean get() = error == null
    val summary: String get() = if (isSuccess) {
        buildString {
            append("Restored $notesImported note${if (notesImported != 1) "s" else ""}")
            if (foldersImported > 0) append(", $foldersImported folder${if (foldersImported != 1) "s" else ""}")
            if (skippedDuplicates > 0) append(" ($skippedDuplicates duplicate${if (skippedDuplicates != 1) "s" else ""} skipped)")
        }
    } else {
        "Import failed: $error"
    }
}

/**
 * Lightweight header read from a .vnbackup before the full import.
 * Lets RestoreBackupViewModel verify the password and show counts
 * to the user without loading the entire backup into memory.
 */
data class BackupHeader(
    val salt: String,
    val verificationBlob: String,
    val noteCount: Int,
    val folderCount: Int,
    val appVersion: String
)