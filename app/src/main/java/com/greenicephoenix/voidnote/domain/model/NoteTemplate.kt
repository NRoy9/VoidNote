package com.greenicephoenix.voidnote.domain.model

/**
 * NoteTemplate — a pre-structured note the user can pick when creating a new note.
 *
 * WHY SEPARATE CONTENT AND TODO SECTIONS?
 * Checklists in Void Note are NOT stored as text ("- [ ] item").
 * They are InlineBlock records in a separate DB table, with a marker token
 * (⟦block:TODO:uuid⟧) embedded in the note content string.
 * So templates that want real checkboxes must declare them as [TemplateTodoSection]
 * objects — the ViewModel creates the actual InlineBlock records on note creation.
 *
 * Templates that are pure text (Meeting Notes, Journal, Review) only use [content].
 * The Todo template uses [todoSections] instead of text for its checklist items.
 *
 * @param id            Stable identifier
 * @param name          Display name in the picker
 * @param description   One-line description in the picker
 * @param emoji         Visual icon in the picker (no custom font needed)
 * @param titlePrefix   Pre-filled title text. Empty = blank title field.
 * @param content       Pre-filled plain-text body (for non-todo templates)
 * @param todoSections  Checklist sections — each becomes a real TODO InlineBlock
 */
data class NoteTemplate(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val titlePrefix: String,
    val content: String = "",
    val todoSections: List<TemplateTodoSection> = emptyList()
)

/**
 * TemplateTodoSection — one checklist block inside a template.
 *
 * A single template can have multiple sections (e.g. High Priority / Medium / Low).
 * Each section becomes one InlineBlock(TODO) in the DB.
 * A section header is written as a plain-text line above the block marker in the
 * note content so the user can see the section label in the editor.
 *
 * @param header  Plain-text label shown above the checklist ("High Priority")
 * @param items   Pre-filled checklist item texts (each becomes a TodoItem)
 */
data class TemplateTodoSection(
    val header: String,
    val items: List<String>   // plain text — each becomes a TodoItem(isChecked=false)
)

/**
 * BuiltInTemplates — all templates shipped with the app.
 *
 * To add a new template in a future sprint:
 *   1. Add a new val here
 *   2. Add it to [all]
 *   No other code changes needed.
 */
object BuiltInTemplates {

    val blank = NoteTemplate(
        id          = "builtin_blank",
        name        = "Blank",
        description = "A clean slate — start from nothing",
        emoji       = "📄",
        titlePrefix = "",
        content     = ""
    )

    val meetingNotes = NoteTemplate(
        id          = "builtin_meeting",
        name        = "Meeting Notes",
        description = "Attendees, agenda, decisions, action items",
        emoji       = "🤝",
        titlePrefix = "Meeting: ",
        content     = """
Date: 
Attendees: 

Agenda
1. 
2. 
3. 

Discussion


Decisions Made


Action Items
• 
• 

Next Meeting
        """.trimIndent()
    )

    val dailyJournal = NoteTemplate(
        id          = "builtin_journal",
        name        = "Self-Reflection",
        description = "One line a day keeps the void at bay",
        emoji       = "📔",
        titlePrefix = "Reflection: ",
        content     = """
— — —

How am I feeling right now?


What happened today worth remembering?


One thing I learned


What am I grateful for?


If I could redo one thing today, what would it be?


— — —
        """.trimIndent()
    )

    val todoList = NoteTemplate(
        id           = "builtin_todo",
        name         = "Todo / Task List",
        description  = "Prioritised checklist with sections",
        emoji        = "✅",
        titlePrefix  = "Tasks: ",
        content      = "",   // built dynamically from todoSections by the ViewModel
        todoSections = listOf(
            TemplateTodoSection(header = "High Priority",   items = listOf("", "")),
            TemplateTodoSection(header = "Medium Priority", items = listOf("", "")),
            TemplateTodoSection(header = "Low Priority",    items = listOf("", ""))
        )
    )

    val bookMovieReview = NoteTemplate(
        id          = "builtin_review",
        name        = "Book / Movie Review",
        description = "Rating, summary, key takeaways",
        emoji       = "⭐",
        titlePrefix = "Review: ",
        content     = """
Type:  Book / Movie / Show / Album
Creator: 
Rating:   / 10

Summary


What I loved


What didn't land


Key takeaway


Would I recommend it?  Yes / No
To whom?
        """.trimIndent()
    )

    /** Ordered list shown in the template picker. Blank is always first. */
    val all: List<NoteTemplate> = listOf(
        blank,
        meetingNotes,
        dailyJournal,
        todoList,
        bookMovieReview
    )
}