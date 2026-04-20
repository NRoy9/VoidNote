package com.greenicephoenix.voidnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database Entity for Folders.
 *
 * SPRINT 15 ADDITION:
 * passwordHash and passwordSalt — nullable columns for per-folder passwords.
 *
 * WHY NULLABLE (not NOT NULL DEFAULT '')?
 * null means "no password set" — a clean, unambiguous sentinel value.
 * An empty string would be ambiguous (is it "no password" or "a blank password"?).
 * Checking `passwordHash != null` is clear and intent-revealing.
 *
 * MIGRATION: MIGRATION_9_10 adds both columns as nullable TEXT.
 */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    // For nested folders (folder inside folder) — null = root level
    val parentFolderId: String? = null,

    // Optional color code
    val color: String? = null,

    val createdAt: Long,
    val updatedAt: Long = createdAt,

    // ── Sprint 15: Per-folder password ───────────────────────────────────
    // null = no password set on this folder
    // non-null = folder is password-protected; user must enter password to open
    val passwordHash: String? = null,   // PBKDF2 hash (Base64)
    val passwordSalt: String? = null    // Random salt used to produce the hash (Base64)
)