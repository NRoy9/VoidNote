package com.greenicephoenix.voidnote.domain.model

/**
 * Folder — domain model for organizing notes.
 *
 * SPRINT 15 ADDITION:
 * passwordHash / passwordSalt for per-folder password protection.
 * isPasswordProtected() helper — readable intent at call sites.
 */
data class Folder(
    val id: String,
    val name: String,
    val parentFolderId: String? = null,
    val color: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,

    // Sprint 15 — null means no password set
    val passwordHash: String? = null,
    val passwordSalt: String? = null
) {
    fun isRootFolder(): Boolean = parentFolderId == null

    fun hasValidName(): Boolean = name.isNotBlank()

    /** True if this folder has a password set. */
    fun isPasswordProtected(): Boolean = passwordHash != null && passwordSalt != null
}