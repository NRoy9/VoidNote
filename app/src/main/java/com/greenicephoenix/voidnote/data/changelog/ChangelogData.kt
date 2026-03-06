package com.greenicephoenix.voidnote.data.changelog

/**
 * ChangelogData — The single source of truth for all version release notes.
 *
 * HOW TO ADD A NEW RELEASE:
 * 1. Bump versionName in app/build.gradle.kts (e.g. "0.0.2-alpha")
 * 2. Add a new VersionEntry at the TOP of the `entries` list
 * 3. That's it. The What's New dialog and full changelog screen both
 *    read from this list automatically.
 *
 * WHY HARDCODED INSTEAD OF A SERVER?
 * - Works offline — no network needed to show release notes
 * - No backend to maintain
 * - Version notes are part of the build — always match what's in the app
 * - Easy to review in code review what you're shipping
 *
 * DESIGN:
 * Each VersionEntry has a list of ChangeItem objects.
 * Each ChangeItem has a type (NEW / IMPROVED / FIXED / SECURITY) and a description.
 * The UI renders a coloured dot per type — users can scan quickly.
 */

data class VersionEntry(
    val version: String,            // e.g. "0.1.0-alpha" — must match versionName in Gradle
    val releaseDate: String,        // e.g. "28 Feb 2026" — human readable, shown in changelog
    val tagline: String,            // short flavour text shown under version number
    val changes: List<ChangeItem>
)

data class ChangeItem(
    val type: ChangeType,
    val description: String
)

enum class ChangeType(val label: String) {
    NEW("New"),
    IMPROVED("Improved"),
    FIXED("Fixed"),
    SECURITY("Security")
}

object ChangelogData {

    /**
     * All release notes — newest first.
     * The What's New dialog shows only the FIRST entry (latest version).
     * The Changelog screen shows ALL entries.
     *
     * ADD NEW ENTRIES AT THE TOP.
     */
    val entries: List<VersionEntry> = listOf(

        // ── Sprint 4 + Sprint 5 ───────────────────────────────────────────────
        VersionEntry(
            version     = "0.1.0-alpha",
            releaseDate = "06 Mar 2026",
            tagline     = "Stability, polish, and a few long-overdue quality-of-life improvements.",
            changes     = listOf(
                // Sprint 5 new features
                ChangeItem(ChangeType.NEW,      "Move to folder — reassign any note to a different folder from the editor"),
                ChangeItem(ChangeType.NEW,      "Fullscreen image viewer — tap any image block to open it fullscreen with pinch-to-zoom"),
                ChangeItem(ChangeType.NEW,      "Reading time — estimated read time now shown alongside word and character count"),
                ChangeItem(ChangeType.NEW,      "Tags browser — dedicated screen to browse and filter notes by tag"),

                // Sprint 4 features (carried forward)
                ChangeItem(ChangeType.NEW,      "Numbered lists — insert numbered list items with auto-continuing numbering"),
                ChangeItem(ChangeType.NEW,      "Format preview — toggle between edit mode and a styled read-only preview"),
                ChangeItem(ChangeType.NEW,      "Export screen — dedicated export screen with format picker (Secure Backup / Plain Text)"),

                // Improvements
                ChangeItem(ChangeType.IMPROVED, "Tag limit feedback — toolbar shows 'Max 5 tags' clearly instead of silently hiding the Add button"),
                ChangeItem(ChangeType.IMPROVED, "Vault unlock now correctly rejects wrong passwords before loading notes"),
                ChangeItem(ChangeType.IMPROVED, "Export and import flows are now separate dedicated screens for clarity"),

                // Fixes
                ChangeItem(ChangeType.FIXED,    "Note formatting (bold, italic etc.) now correctly survives export and re-import"),
                ChangeItem(ChangeType.FIXED,    "Folder notes page title now updates live when folder is renamed"),
                ChangeItem(ChangeType.FIXED,    "Back navigation from main screen now backgrounds the app instead of re-triggering the lock screen"),

                // Security
                ChangeItem(ChangeType.SECURITY, "Vault unlock now verifies password against a verification blob before activating the key"),
                ChangeItem(ChangeType.SECURITY, "ProGuard rules hardened for Room, Hilt, Keystore and serialization classes in release builds"),
            )
        ),

        VersionEntry(
            version     = "0.0.2-alpha",
            releaseDate = "05 Mar 2026",
            tagline     = "Your notes now travel with you — securely.",
            changes     = listOf(
                ChangeItem(ChangeType.NEW,      "Secure backup — export all notes as an encrypted .vnbackup file"),
                ChangeItem(ChangeType.NEW,      "Restore from backup — recover your vault on a fresh install"),
                ChangeItem(ChangeType.NEW,      "Import backup — merge notes from a backup into your existing vault"),
                ChangeItem(ChangeType.NEW,      "Change vault password — re-encrypts all notes with a new password"),
                ChangeItem(ChangeType.NEW,      "Image blocks — attach photos to notes from gallery or camera"),
                ChangeItem(ChangeType.NEW,      "Audio blocks — record voice notes directly inside a note"),
                ChangeItem(ChangeType.SECURITY, "Backup files are end-to-end encrypted — unreadable without your vault password"),
                ChangeItem(ChangeType.SECURITY, "Password change uses atomic database transaction — no data loss if interrupted"),
                ChangeItem(ChangeType.FIXED,    "Checklists, images and audio now correctly preserved in backup and restore"),
                ChangeItem(ChangeType.FIXED,    "Restore button now activates correctly after selecting a backup file"),
            )
        ),
        VersionEntry(
            version = "0.0.1-alpha",
            releaseDate = "28 Feb 2026",
            tagline = "First alpha — the void opens.",
            changes = listOf(
                ChangeItem(ChangeType.NEW, "Rich text editor with bold, italic, underline and strikethrough"),
                ChangeItem(ChangeType.NEW, "Heading styles — H1, H2, H3"),
                ChangeItem(ChangeType.NEW, "Interactive checklists — add, check, delete items inline"),
                ChangeItem(ChangeType.NEW, "Tag-based organisation — up to 5 tags per note"),
                ChangeItem(ChangeType.NEW, "Folder system for grouping notes"),
                ChangeItem(ChangeType.NEW, "Full-text search including checklist item content"),
                ChangeItem(ChangeType.NEW, "Archive — file notes away without deleting"),
                ChangeItem(ChangeType.NEW, "Trash with 30-day auto-delete"),
                ChangeItem(ChangeType.NEW, "Export notes as JSON or plain text"),
                ChangeItem(ChangeType.NEW, "Dark, Light and Extra Dark (OLED) themes"),
                ChangeItem(ChangeType.NEW, "Pin important notes to the top"),
                ChangeItem(ChangeType.SECURITY, "Biometric lock — fingerprint or PIN required on launch"),
                ChangeItem(ChangeType.SECURITY, "Auto-locks when app goes to background"),
                ChangeItem(ChangeType.IMPROVED, "Insert block panel stays above keyboard — no extra taps"),
                ChangeItem(ChangeType.FIXED, "Cursor no longer jumps while typing in checklists"),
            )
        )

        // ── TEMPLATE FOR NEXT RELEASE ─────────────────────────────────────────
        // Copy this block to the top of the list when shipping a new version:
        //
        // VersionEntry(
        //     version = "0.0.2-alpha",
        //     releaseDate = "DD MMM YYYY",
        //     tagline = "Short punchy description of this release",
        //     changes = listOf(
        //         ChangeItem(ChangeType.NEW,      "Something brand new"),
        //         ChangeItem(ChangeType.IMPROVED, "Something made better"),
        //         ChangeItem(ChangeType.FIXED,    "Something that was broken"),
        //         ChangeItem(ChangeType.SECURITY, "Something security related"),
        //     )
        // ),
    )

    /** Convenience: latest version string, used to compare against stored preference. */
    val latestVersion: String get() = entries.firstOrNull()?.version ?: ""

    /** Look up a specific version's notes — used by the full changelog screen. */
    fun forVersion(version: String): VersionEntry? = entries.find { it.version == version }
}