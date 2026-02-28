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