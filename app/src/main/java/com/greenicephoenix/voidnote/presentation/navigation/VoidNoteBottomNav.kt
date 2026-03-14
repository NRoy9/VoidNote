package com.greenicephoenix.voidnote.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// VOID NOTE BOTTOM NAVIGATION  (Sprint 12 — Task 04)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The four primary destinations shown in the bottom nav bar.
 *
 * WHY FOUR AND NOT FIVE?
 * Four items is the sweet spot — each item has enough width to show a label
 * without crowding. Five items on a 360dp screen starts to feel cramped.
 *
 * ITEMS IN ORDER:
 *   Notes    — the main note list (home)
 *   Search   — search across all notes
 *   Journal  — the diary calendar
 *   Settings — app settings
 *
 * Folders and Tags are accessible from inside the Notes screen (filter chips
 * and dedicated rows), so they don't need a bottom nav slot.
 *
 * @param currentRoute  The current NavController route — used to highlight the
 *                      active item.
 * @param onNavigate    Called with the target route when an item is tapped.
 */
@Composable
fun VoidNoteBottomNav(
    currentRoute: String?,
    onNavigate: (route: String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp  // flat — Nothing design avoids shadows on bars
    ) {
        BottomNavItem.all.forEach { item ->
            val selected = currentRoute == item.route

            NavigationBarItem(
                selected = selected,
                onClick  = {
                    // Only navigate if we're not already on this screen.
                    // This prevents re-creating the screen and losing scroll position.
                    if (currentRoute != item.route) {
                        onNavigate(item.route)
                    }
                },
                icon = {
                    Icon(
                        imageVector        = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text  = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.5.sp,
                            fontWeight    = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = MaterialTheme.colorScheme.primary,
                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    indicatorColor      = MaterialTheme.colorScheme.primaryContainer
                ),
                alwaysShowLabel = true
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NAV ITEM DEFINITIONS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sealed class defining each bottom nav item.
 * Selected/unselected icons give a filled vs outlined visual state.
 */
private sealed class BottomNavItem(
    val route         : String,
    val label         : String,
    val selectedIcon  : ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Notes : BottomNavItem(
        route          = "notes_list",
        label          = "Notes",
        selectedIcon   = Icons.AutoMirrored.Filled.Notes,
        unselectedIcon = Icons.AutoMirrored.Outlined.Notes
    )

    data object Search : BottomNavItem(
        route          = "search",
        label          = "Search",
        selectedIcon   = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search
    )

    data object Journal : BottomNavItem(
        route          = "diary",
        label          = "Journal",
        selectedIcon   = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book
    )

    data object Settings : BottomNavItem(
        route          = "settings",
        label          = "Settings",
        selectedIcon   = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val all = listOf(Notes, Search, Journal, Settings)
    }
}