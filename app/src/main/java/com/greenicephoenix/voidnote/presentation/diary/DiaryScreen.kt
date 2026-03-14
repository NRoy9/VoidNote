package com.greenicephoenix.voidnote.presentation.diary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// DIARY SCREEN  (Sprint 12 — Task 03)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * DiaryScreen — the Journal calendar view.
 *
 * LAYOUT:
 *   ┌──────────────────────────────────────────┐
 *   │  ← JOURNAL                               │  TopBar
 *   ├──────────────────────────────────────────┤
 *   │                                          │
 *   │       < March 2026 >                     │  Month header + nav arrows
 *   │                                          │
 *   │   Mo  Tu  We  Th  Fr  Sa  Su             │  Weekday labels
 *   │                              1           │
 *   │    2   3   4   5   6   7   8             │  Day grid
 *   │    9  10  11  ●  13  14  15             │  ● = has entry (dot indicator)
 *   │   16  17  18  19  20  ●  22             │
 *   │   23  24  25  26  27  28  29             │
 *   │   30  31                                 │
 *   │                                          │
 *   │             [✎ Today]                    │  FAB
 *   └──────────────────────────────────────────┘
 *
 * INTERACTION:
 *   • Tap a day with an entry → opens the entry in the editor
 *   • Tap an empty day → creates a new entry for that date → opens editor
 *   • Tap Today FAB → opens/creates today's entry
 *   • ← / → arrows → navigate months
 *
 * CALENDAR IMPLEMENTATION:
 *   We build the calendar manually using java.util.Calendar — no library needed.
 *   For a given month/year we compute:
 *     - The day-of-week of the 1st (to know how many leading blank cells to add)
 *     - The number of days in the month
 *   This produces a flat list of nullable integers (null = blank cell).
 *
 * @param onNavigateToEditor  Navigate to the note editor with the given noteId
 * @param onNavigateBack      Pop back to the previous screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    onNavigateToEditor: (noteId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: DiaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Collect one-shot navigation events → navigate to editor
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collectLatest { noteId ->
            onNavigateToEditor(noteId)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            DiaryTopBar(onNavigateBack = onNavigateBack)
        },
        floatingActionButton = {
            // Today FAB — always visible, always opens/creates today's entry
            ExtendedFloatingActionButton(
                onClick          = { viewModel.openOrCreateToday() },
                icon             = { Icon(Icons.Default.Edit, contentDescription = null) },
                text             = { Text("Today") },
                containerColor   = MaterialTheme.colorScheme.primary,
                contentColor     = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = Spacing.medium)
        ) {

            Spacer(Modifier.height(Spacing.medium))

            // ── Month navigation header ──────────────────────────────────────
            MonthHeader(
                year       = uiState.displayYear,
                month      = uiState.displayMonth,
                onPrevious = { viewModel.previousMonth() },
                onNext     = { viewModel.nextMonth() },
                onJumpTo   = { y, m -> viewModel.jumpToMonth(y, m) }
            )

            Spacer(Modifier.height(Spacing.large))

            // ── Calendar grid ────────────────────────────────────────────────
            // AnimatedContent slides the grid left/right when the month changes,
            // giving a natural "flipping pages" feel.
            AnimatedContent(
                targetState   = "${uiState.displayYear}-${uiState.displayMonth}",
                transitionSpec = {
                    // Determine slide direction based on which month we moved to.
                    // We use string comparison since "2026-2" > "2026-1" lexicographically
                    // for months 0-9 but not always for higher numbers — so we compare
                    // the initialState/targetState pair directly.
                    val entering = targetState > initialState
                    if (entering) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "calendar_month"
            ) { yearMonth ->
                // Re-parse year/month from the key so the animation target is stable
                val parts = yearMonth.split("-")
                val year  = parts[0].toIntOrNull() ?: uiState.displayYear
                val month = parts[1].toIntOrNull() ?: uiState.displayMonth

                CalendarGrid(
                    year           = year,
                    month          = month,
                    entryDateKeys  = uiState.entryDateKeys,
                    onDayClick     = { dateKey -> viewModel.openOrCreateEntry(dateKey) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text     = "JOURNAL",
                style    = MaterialTheme.typography.titleMedium.copy(
                    letterSpacing = 3.sp,
                    fontWeight    = FontWeight.Bold
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// MONTH HEADER
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Month header row with tappable label that opens the month/year picker.
 *
 *   [←]   March 2026 ▾   [→]
 *
 * The ▾ chevron signals the label is tappable. Tapping opens a dialog
 * to jump to any month from 5 years ago through the current month.
 */
@Composable
private fun MonthHeader(
    year: Int,
    month: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpTo: (year: Int, month: Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    val monthName = remember(year, month) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(
            Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
            }.time
        )
    }

    // Prevent navigating past the current month into the future
    val todayCal    = remember { Calendar.getInstance() }
    val isCurrentMonth = year == todayCal.get(Calendar.YEAR) &&
            month == todayCal.get(Calendar.MONTH)

    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous month",
                tint               = MaterialTheme.colorScheme.onSurface
            )
        }

        // Tappable month + year label — subtle pill with dropdown chevron
        Surface(
            onClick = { showPicker = true },
            shape   = RoundedCornerShape(8.dp),
            color   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border  = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text      = monthName,
                    style     = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color     = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Icon(
                    imageVector        = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Pick month and year",
                    tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier           = Modifier.size(20.dp)
                )
            }
        }

        IconButton(
            onClick  = onNext,
            enabled  = !isCurrentMonth
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next month",
                tint               = if (!isCurrentMonth) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
        }
    }

    if (showPicker) {
        MonthYearPickerDialog(
            currentYear  = year,
            currentMonth = month,
            onConfirm    = { y, m ->
                onJumpTo(y, m)
                showPicker = false
            },
            onDismiss    = { showPicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MONTH / YEAR PICKER DIALOG
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MonthYearPickerDialog — lets the user jump to any month/year.
 *
 * DESIGN:
 *   • Year row at top — scroll left/right through years (5 years back → today)
 *   • 3×4 month grid below — 12 month chips
 *   • Current selection highlighted in primary color
 *   • Future months are disabled (greyed out)
 *   • "Go" button confirms
 *
 * RANGE: 5 years back from today's year through today's month.
 * We don't allow future months — diary entries can only be written for
 * today or the past.
 */
@Composable
private fun MonthYearPickerDialog(
    currentYear: Int,
    currentMonth: Int,
    onConfirm: (year: Int, month: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val todayCal   = remember { Calendar.getInstance() }
    val todayYear  = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH)
    val minYear    = todayYear - 5

    var selectedYear  by remember { mutableIntStateOf(currentYear) }
    var selectedMonth by remember { mutableIntStateOf(currentMonth) }

    val monthNames = remember {
        val fmt = SimpleDateFormat("MMM", Locale.getDefault())
        (0..11).map { m ->
            fmt.format(Calendar.getInstance().apply {
                set(Calendar.MONTH, m)
                set(Calendar.DAY_OF_MONTH, 1)
            }.time)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text  = "Go to",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {

                // ── Year selector ─────────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick  = { if (selectedYear > minYear) selectedYear-- },
                        enabled  = selectedYear > minYear
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous year",
                            tint = if (selectedYear > minYear) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    }

                    Text(
                        text  = selectedYear.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick  = { if (selectedYear < todayYear) selectedYear++ },
                        enabled  = selectedYear < todayYear
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next year",
                            tint = if (selectedYear < todayYear) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    }
                }

                // ── Month grid — 3 columns × 4 rows ──────────────────────────
                // A month is disabled if it's in the future
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    (0..11).chunked(3).forEach { rowMonths ->
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            rowMonths.forEach { m ->
                                val isFuture  = selectedYear == todayYear && m > todayMonth
                                val isSelected = m == selectedMonth
                                val chipColor  = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isFuture   -> MaterialTheme.colorScheme.surfaceVariant
                                    else       -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                val textColor  = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isFuture   -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    else       -> MaterialTheme.colorScheme.onSurface
                                }

                                Surface(
                                    onClick  = { if (!isFuture) selectedMonth = m },
                                    enabled  = !isFuture,
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(8.dp),
                                    color    = chipColor
                                ) {
                                    Text(
                                        text      = monthNames[m],
                                        style     = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color     = textColor,
                                        textAlign = TextAlign.Center,
                                        modifier  = Modifier.padding(vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedYear, selectedMonth) }) {
                Text("Go", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CALENDAR GRID
// ─────────────────────────────────────────────────────────────────────────────

/**
 * CalendarGrid — renders the 7-column day grid for a given month/year.
 *
 * HOW THE GRID IS BUILT:
 * 1. Find the day-of-week of the 1st (Monday = 0, Sunday = 6 in our layout).
 * 2. Prepend that many null cells (blank leading spaces).
 * 3. Append days 1..daysInMonth.
 * 4. Pad with trailing nulls to fill the last row to 7 cells.
 * 5. Chunk into rows of 7 and render.
 *
 * WHY MONDAY FIRST?
 * Most of the world uses Monday as the first day of the week.
 * ISO 8601 standard. Matches Google Calendar, Apple Calendar.
 *
 * @param entryDateKeys  "yyyy-MM-dd" strings that have a diary entry — drawn as dots.
 * @param onDayClick     Called with the "yyyy-MM-dd" key when the user taps a day.
 */
@Composable
private fun CalendarGrid(
    year          : Int,
    month         : Int,
    entryDateKeys : Set<String>,
    onDayClick    : (String) -> Unit
) {
    val keyFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayKey     = remember { keyFormatter.format(java.util.Date()) }

    // Build the calendar cells
    val cells: List<Int?> = remember(year, month) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        // Day of week of the 1st: Calendar.MONDAY=2 … Calendar.SUNDAY=1
        // We want Monday=0, Tuesday=1, … Sunday=6
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val leadingBlanks  = (firstDayOfWeek - Calendar.MONDAY + 7) % 7

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // null = blank cell, Int = day number
        val cells = mutableListOf<Int?>()
        repeat(leadingBlanks) { cells.add(null) }
        for (day in 1..daysInMonth) cells.add(day)
        // Pad to a multiple of 7
        while (cells.size % 7 != 0) cells.add(null)
        cells
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Weekday header row ────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { label ->
                Text(
                    text      = label,
                    style     = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(Spacing.small))

        // ── Day rows ─────────────────────────────────────────────────────────
        cells.chunked(7).forEach { week ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                week.forEach { day ->
                    if (day == null) {
                        // Blank cell — spacer with same weight so grid stays aligned
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        // Build the date key for this cell
                        val dateKey = remember(year, month, day) {
                            keyFormatter.format(
                                Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                }.time
                            )
                        }
                        val hasEntry = dateKey in entryDateKeys
                        val isToday  = dateKey == todayKey

                        DayCell(
                            day      = day,
                            hasEntry = hasEntry,
                            isToday  = isToday,
                            onClick  = { onDayClick(dateKey) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DAY CELL
// ─────────────────────────────────────────────────────────────────────────────

/**
 * DayCell — a single day in the calendar grid.
 *
 * STATES:
 *   • Normal          — day number in onSurface color
 *   • Today           — primary color circle behind the number
 *   • Has entry       — small dot below the number (primary color)
 *   • Today + entry   — circle + dot
 *
 * The cell is always tappable — tapping an empty day creates a new entry.
 *
 * @param day       Day of month (1–31)
 * @param hasEntry  Whether a diary entry exists for this day
 * @param isToday   Whether this day is today
 * @param onClick   Called when the user taps the cell
 */
@Composable
private fun DayCell(
    day      : Int,
    hasEntry : Boolean,
    isToday  : Boolean,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    val primary    = MaterialTheme.colorScheme.primary
    val onPrimary  = MaterialTheme.colorScheme.onPrimary
    val onSurface  = MaterialTheme.colorScheme.onSurface

    Column(
        modifier            = modifier
            .aspectRatio(1f)   // keep cells square regardless of screen width
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Day number — inside a circle if today
        Box(
            modifier        = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isToday) primary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = day.toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isToday || hasEntry) FontWeight.Bold else FontWeight.Normal
                ),
                color = when {
                    isToday  -> onPrimary
                    hasEntry -> primary
                    else     -> onSurface.copy(alpha = 0.85f)
                }
            )
        }

        // Entry dot — shown below the number when an entry exists
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    if (hasEntry) primary else Color.Transparent
                )
        )
    }
}