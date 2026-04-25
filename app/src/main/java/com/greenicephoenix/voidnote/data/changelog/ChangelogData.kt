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

        // ── v1.2.0 —  ────────────────────────────────────────────────
        VersionEntry(
            version = "1.2.0",
            releaseDate = "25 Apr 2026",
            tagline     = "One tap to capture, one pass to protect.",
            changes = listOf(
                ChangeItem(ChangeType.NEW, "App theme updated to GIP design system"),
                ChangeItem(ChangeType.NEW, "New app icon — crystalline Ice/Cryo design aligned with the GIP ecosystem"),
                ChangeItem(ChangeType.IMPROVED, "Dark mode now uses Void Deep (#050508) backgrounds per the GIP design system"),
                ChangeItem(ChangeType.NEW, "Quick Capture Widget — add notes or voice memos directly from your home screen"),
                ChangeItem(ChangeType.NEW, "Small widget (2×1): one-tap new note and voice recording buttons"),
                ChangeItem(ChangeType.NEW, "Medium widget (4×2): recent notes list plus quick capture and voice buttons"),
                ChangeItem(ChangeType.NEW, "Widget settings: choose whether saving a quick note opens the full editor"),
                ChangeItem(ChangeType.NEW, "Password-protected folders — lock any folder with its own independent password"),
                ChangeItem(ChangeType.NEW, "Folders re-lock automatically when the app is backgrounded or vault locks"),
                ChangeItem(ChangeType.NEW, "Set, change, or remove folder passwords from inside the folder"),
                ChangeItem(ChangeType.NEW, "Lock a folder instantly from the folder menu"),
                ChangeItem(ChangeType.IMPROVED, "Protected folders show a lock icon and 'Protected' label in the folder list"),

            )
        ),

        // ── v1.1.1 — Bug fixes ────────────────────────────────────────────────
        VersionEntry(
            version     = "1.1.1",
            releaseDate = "29 Mar 2026",
            tagline     = "Quieter, sharper, and counts what matters.",
            changes     = listOf(
                ChangeItem(ChangeType.FIXED,    "Export file picker no longer opens twice when saving a backup"),
                ChangeItem(ChangeType.FIXED,    "Journal entries are now counted separately from notes in Settings"),
                ChangeItem(ChangeType.FIXED,    "Clear All Data now correctly removes journal entries alongside notes"),
                ChangeItem(ChangeType.FIXED,    "Import preview now shows separate counts for notes, journal entries, and folders"),
                ChangeItem(ChangeType.FIXED,    "Google Keep Takeout ZIP now imports correctly — nested Takeout/Keep/ folder structure is handled"),
                ChangeItem(ChangeType.FIXED,    "Code blocks no longer scramble text when typing quickly"),
                ChangeItem(ChangeType.FIXED,    "What's New dialog no longer appears on a fresh install"),
            )
        ),

        // ── v1.1.0 — The biggest update yet ──────────────────────────────────
        VersionEntry(
            version     = "1.1.0",
            releaseDate = "20 Mar 2026",
            tagline     = "Journal, templates, linked notes, and a brand new home.",
            changes     = listOf(
                ChangeItem(ChangeType.NEW,      "Journal — a full calendar view for daily diary entries, one entry per day"),
                ChangeItem(ChangeType.NEW,      "Note Templates — start from a blank, meeting notes, self-reflection, task list, or book review"),
                ChangeItem(ChangeType.NEW,      "Daily Note — one tap from the + menu opens or creates today's dated note"),
                ChangeItem(ChangeType.NEW,      "Note Linking — link notes to each other; linked chips appear above the tag bar"),
                ChangeItem(ChangeType.NEW,      "Code Blocks — insert a dark monospace code block with optional language label"),
                ChangeItem(ChangeType.NEW,      "Focus Mode — hides all chrome for distraction-free writing; ghost exit button to return"),
                ChangeItem(ChangeType.NEW,      "Bottom navigation — Notes · Search · Journal · Settings always accessible"),
                ChangeItem(ChangeType.IMPROVED, "Smart auto-title — notes without a title get one derived from the first line you type"),
                ChangeItem(ChangeType.IMPROVED, "Import Backup now supports plain Markdown files and Markdown ZIP exports"),
            )
        ),

        // ── v1.0.2 — Share fix, community & support ───────────────────────────
        VersionEntry(
            version     = "1.0.2",
            releaseDate = "11 Mar 2026",
            tagline     = "Cleaner shares, better sheets, and a place for community.",
            changes     = listOf(
                ChangeItem(ChangeType.FIXED,    "Share note — checklist items were completely missing from shared text"),
                //ChangeItem(ChangeType.NEW,      "Support the Developer screen — Ko-fi and Buy Me a Coffee, accessible from Settings"),
                ChangeItem(ChangeType.NEW,      "Discord community — join from Settings → About or the website footer"),
                ChangeItem(ChangeType.IMPROVED, "All picker dialogs replaced with bottom sheets — heading style, color, folder, tag, theme, and rename"),
            )
        ),

        // ── v1.0.1 — Polish & UX improvements ────────────────────────────────
        VersionEntry(
            version     = "1.0.1",
            releaseDate = "10 Mar 2026",
            tagline     = "Smoother, faster, more satisfying to use.",
            changes     = listOf(
                ChangeItem(ChangeType.NEW,      "Long-press any note card for quick actions — pin, archive, or delete without opening the note"),
                ChangeItem(ChangeType.NEW,      "Undo archive — a snackbar appears after archiving so you can reverse it instantly"),
                ChangeItem(ChangeType.NEW,      "Swipe gestures on folder note lists — swipe right to pin, swipe left to archive"),
                ChangeItem(ChangeType.NEW,      "Sort on Archive and Trash screens — same four sort options as the main list"),
                ChangeItem(ChangeType.IMPROVED, "Note cards now show word count and estimated reading time for longer notes"),
                ChangeItem(ChangeType.IMPROVED, "Note cards show image and audio badges when a note contains media blocks"),
                ChangeItem(ChangeType.IMPROVED, "Haptic feedback when crossing a swipe threshold — physical confirmation before you lift your finger"),
                ChangeItem(ChangeType.IMPROVED, "Haptic feedback on checklist item toggle — matches native Android checkbox feel"),
            )
        ),

        // ── v1.0.0 — First production release ────────────────────────────────
        // Free-tier feature set is complete. No longer alpha.
        VersionEntry(
            version     = "1.0.0",
            releaseDate = "07 Mar 2026",
            tagline     = "Out of alpha, into the void, and ready for the world.",
            changes     = listOf(
                ChangeItem(ChangeType.NEW,      "Official launch — Void Note is no longer alpha. All free-tier features are complete and stable"),
                ChangeItem(ChangeType.NEW,      "Website — visit voidnote.pages.dev for the latest APK, privacy policy and release notes"),
                ChangeItem(ChangeType.NEW,      "App icon — document shape with red fold accent, consistent across splash screen and website"),
                ChangeItem(ChangeType.IMPROVED, "Color category picker moved to the ⋮ overflow menu — editor is now clutter-free"),
                ChangeItem(ChangeType.IMPROVED, "Overflow menu color item shows the current color as a small dot — visible at a glance"),
                ChangeItem(ChangeType.IMPROVED, "Splash screen logo is now visible in both light and dark mode"),
                ChangeItem(ChangeType.IMPROVED, "Note cards show a clean left accent strip for color — no more tinted backgrounds"),
                ChangeItem(ChangeType.FIXED,    "Selected note color was reset to None every time a note was re-opened — now persists correctly"),
                ChangeItem(ChangeType.FIXED,    "Color accent strip on note cards now renders at full card height"),
            )
        ),

        //Sprint 6
        VersionEntry(
            version     = "0.2.0-alpha",
            releaseDate = "06 Mar 2026",
            tagline     = "Smarter sorting, brighter notes, and a vault that stays current.",
            changes     = listOf(
                ChangeItem(ChangeType.NEW,      "Color accents — tag any note with one of 6 colors, visible in the list"),
                ChangeItem(ChangeType.NEW,      "Sort notes — by last modified, date created, or title A→Z / Z→A"),
                ChangeItem(ChangeType.NEW,      "Update checker — get notified when a new release is available on GitHub"),
                ChangeItem(ChangeType.NEW,      "Move to folder — reassign a note to any folder from inside the editor"),
                ChangeItem(ChangeType.NEW,      "Fullscreen image viewer — tap any image block to view it full screen"),
                ChangeItem(ChangeType.IMPROVED, "Reading time estimate shown alongside word and character count"),
                ChangeItem(ChangeType.IMPROVED, "Tag limit raised and enforced — max 5 tags per note with clear indicator"),
                ChangeItem(ChangeType.FIXED,    "Vault unlock screen no longer loops — navigates correctly after unlock"),
            )
        ),

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
            tagline = "The first block, the first lock, and the opening of the void.",
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