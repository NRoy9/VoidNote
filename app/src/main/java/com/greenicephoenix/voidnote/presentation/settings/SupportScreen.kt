package com.greenicephoenix.voidnote.presentation.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.greenicephoenix.voidnote.presentation.theme.Spacing

// ─────────────────────────────────────────────────────────────────────────────
// CONSTANTS
// ─────────────────────────────────────────────────────────────────────────────

private const val UPI_ID     = "greenicephoenix@axisb"
private const val PAYPAL_ME  = "https://paypal.me/GreenIcePhoenix"
private const val APP_NAME   = "Void Note"

// PayPal supported currencies — shown in the currency picker bottom sheet
private data class PayPalCurrency(
    val code: String,
    val symbol: String,
    val label: String
)

private val PAYPAL_CURRENCIES = listOf(
    PayPalCurrency("USD", "$",  "USD — US Dollar"),
    PayPalCurrency("EUR", "€",  "EUR — Euro"),
    PayPalCurrency("GBP", "£",  "GBP — British Pound"),
    PayPalCurrency("CAD", "C$", "CAD — Canadian Dollar"),
    PayPalCurrency("AUD", "A$", "AUD — Australian Dollar"),
    PayPalCurrency("SGD", "S$", "SGD — Singapore Dollar"),
    PayPalCurrency("JPY", "¥",  "JPY — Japanese Yen"),
)

// Only one accordion section open at a time
private enum class OpenSection { NONE, UPI, PAYPAL }

// ─────────────────────────────────────────────────────────────────────────────
// SUPPORT SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    // ── Accordion state ───────────────────────────────────────────────────────
    var openSection by remember { mutableStateOf(OpenSection.NONE) }

    // ── UPI / INR state ───────────────────────────────────────────────────────
    // null selectedInr = Custom chip is active; Int = a preset is active
    var selectedInr by remember { mutableStateOf<Int?>(50) }
    var customInr   by remember { mutableStateOf("") }

    // ── PayPal state ──────────────────────────────────────────────────────────
    var selectedUsd       by remember { mutableStateOf<Int?>(2) }
    var customUsd         by remember { mutableStateOf("") }
    var selectedCurrency  by remember { mutableStateOf(PAYPAL_CURRENCIES[0]) }
    var showCurrencySheet by remember { mutableStateOf(false) }

    // ── Error dialogs ─────────────────────────────────────────────────────────
    var showNoUpiDialog     by remember { mutableStateOf(false) }
    var showNoBrowserDialog by remember { mutableStateOf(false) }

    // Effective amounts resolved from preset OR custom field
    val effectiveInr = selectedInr?.toString() ?: customInr.trim()
    val effectiveUsd = selectedUsd?.toString() ?: customUsd.trim()

    // Void Note accent = MaterialTheme.colorScheme.primary (the red configured in Theme.kt)
    // We reference it inline rather than a hardcoded constant so it respects the theme.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint               = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text  = "SUPPORT $APP_NAME",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(Spacing.large))

        // ── Heart icon ────────────────────────────────────────────────────────
        Icon(
            imageVector        = Icons.Default.Favorite,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier
                .size(52.dp)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(Spacing.large))

        // ── Developer message card ────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape  = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier            = Modifier.padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text  = "Built by one person, for everyone.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = "$APP_NAME is a labour of love — every screen, every feature, and " +
                            "every bug fix is crafted by a solo developer. The core features — " +
                            "encrypted notes, rich text, folders, tags, biometric lock — will " +
                            "always be free. No exceptions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text  = "Your support funds the time it takes to build new features, fix issues " +
                            "quickly, and keep the app updated for every new version of Android. " +
                            "If $APP_NAME has been useful to you, even a small tip goes a long way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )

                Text(
                    text      = "Payments are voluntary • Core features always free • No payment data stored by this app",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(Spacing.large))

        // ─────────────────────────────────────────────────────────────────────
        // INDIA SECTION — UPI
        // ─────────────────────────────────────────────────────────────────────
        SectionLabel("INDIA")

        AccordionCard(
            title    = "Pay with UPI",
            subtitle = "GPay · PhonePe · Paytm · BHIM · any UPI app",
            isOpen   = openSection == OpenSection.UPI,
            onToggle = {
                openSection =
                    if (openSection == OpenSection.UPI) OpenSection.NONE else OpenSection.UPI
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // INR preset chips + Custom
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 200, 500).forEach { amount ->
                        AmountChip(
                            label      = "₹$amount",
                            isSelected = selectedInr == amount,
                            modifier   = Modifier.weight(1f),
                            onClick    = { selectedInr = amount; customInr = "" }
                        )
                    }
                    AmountChip(
                        label      = "Custom",
                        isSelected = selectedInr == null,
                        modifier   = Modifier.weight(1f),
                        onClick    = {
                            selectedInr = null
                            if (customInr.isBlank()) customInr = "250"
                        }
                    )
                }

                // Custom INR field — only shown when Custom chip is selected
                AnimatedVisibility(visible = selectedInr == null) {
                    OutlinedTextField(
                        value         = customInr,
                        onValueChange = { v -> customInr = v.filter { it.isDigit() } },
                        label           = { Text("Enter amount") },
                        prefix          = { Text("₹") },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier        = Modifier.fillMaxWidth(),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            focusedLabelColor    = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // UPI pay button
                // URL format: upi://pay?pa=UPI_ID&pn=NAME&am=AMOUNT&cu=INR&tn=NOTE
                Button(
                    onClick  = {
                        if (effectiveInr.isBlank() || effectiveInr == "0") return@Button
                        val url = "upi://pay" +
                                "?pa=$UPI_ID" +
                                "&pn=$APP_NAME" +
                                "&am=$effectiveInr" +
                                "&cu=INR" +
                                "&tn=${APP_NAME.replace(" ", "+")}+Tip"
                        if (!launchUrl(context, url)) showNoUpiDialog = true
                    },
                    enabled  = effectiveInr.isNotBlank() && effectiveInr != "0",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    ),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text  = if (effectiveInr.isNotBlank() && effectiveInr != "0")
                            "Pay ₹$effectiveInr with UPI"
                        else "Select or enter an amount",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.small))

        // ─────────────────────────────────────────────────────────────────────
        // INTERNATIONAL SECTION — PayPal
        // ─────────────────────────────────────────────────────────────────────
        SectionLabel("INTERNATIONAL")

        AccordionCard(
            title    = "Pay with PayPal",
            subtitle = "Opens in your browser · Pay in your currency",
            isOpen   = openSection == OpenSection.PAYPAL,
            onToggle = {
                openSection =
                    if (openSection == OpenSection.PAYPAL) OpenSection.NONE else OpenSection.PAYPAL
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Currency selector row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { showCurrencySheet = true }
                        .padding(horizontal = Spacing.medium, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text  = "Currency",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text  = "${selectedCurrency.symbol}  ${selectedCurrency.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "Change",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // PayPal preset chips + Custom
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10, 20).forEach { amount ->
                        AmountChip(
                            label      = "${selectedCurrency.symbol}$amount",
                            isSelected = selectedUsd == amount,
                            modifier   = Modifier.weight(1f),
                            onClick    = { selectedUsd = amount; customUsd = "" }
                        )
                    }
                    AmountChip(
                        label      = "Custom",
                        isSelected = selectedUsd == null,
                        modifier   = Modifier.weight(1f),
                        onClick    = {
                            selectedUsd = null
                            if (customUsd.isBlank()) customUsd = "7"
                        }
                    )
                }

                // Custom PayPal field — only shown when Custom chip is selected
                AnimatedVisibility(visible = selectedUsd == null) {
                    OutlinedTextField(
                        value         = customUsd,
                        onValueChange = { v ->
                            // Allow digits and one decimal point
                            val filtered = v.filter { it.isDigit() || it == '.' }
                            val dotIdx   = filtered.indexOf('.')
                            customUsd = if (dotIdx == -1) filtered
                            else filtered.substring(0, dotIdx + 1) +
                                    filtered.substring(dotIdx + 1).filter { it.isDigit() }
                            selectedUsd = null
                        },
                        label           = { Text("Enter amount") },
                        prefix          = { Text(selectedCurrency.symbol) },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier        = Modifier.fillMaxWidth(),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.primary,
                            focusedLabelColor    = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // PayPal button
                // URL format: https://paypal.me/USERNAME/AMOUNTCURRENCYCODE
                // e.g.        https://paypal.me/GreenIcePhoenix/5EUR
                Button(
                    onClick  = {
                        if (effectiveUsd.isBlank() || effectiveUsd == "0") return@Button
                        val url = "$PAYPAL_ME/${effectiveUsd}${selectedCurrency.code}"
                        if (!launchUrl(context, url)) showNoBrowserDialog = true
                    },
                    enabled  = effectiveUsd.isNotBlank() && effectiveUsd != "0",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    ),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text  = if (effectiveUsd.isNotBlank() && effectiveUsd != "0")
                            "Pay ${selectedCurrency.symbol}$effectiveUsd (${selectedCurrency.code}) with PayPal"
                        else "Select or enter an amount",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text      = "Charged in ${selectedCurrency.code} · Opens in browser",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(Spacing.extraExtraLarge))
    }

    // ── Currency bottom sheet ─────────────────────────────────────────────────
    if (showCurrencySheet) {
        ModalBottomSheet(onDismissRequest = { showCurrencySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium)
                    .padding(bottom = Spacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text     = "Select Currency",
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = Spacing.small)
                )

                PAYPAL_CURRENCIES.forEach { currency ->
                    val isSelected = currency.code == selectedCurrency.code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else Color.Transparent
                            )
                            .clickable {
                                selectedCurrency  = currency
                                selectedUsd       = listOf(2, 5, 10, 20)[1]  // reset to 2 preset
                                customUsd         = ""
                                showCurrencySheet = false
                            }
                            .padding(horizontal = Spacing.medium, vertical = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            // Symbol in a small square container
                            Box(
                                modifier         = Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.background,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text  = currency.symbol,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text  = currency.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (isSelected) {
                            Text(
                                "✓",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    // ── No UPI app installed dialog ───────────────────────────────────────────
    if (showNoUpiDialog) {
        AlertDialog(
            onDismissRequest = { showNoUpiDialog = false },
            shape          = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title          = {
                Text("No UPI App Found", style = MaterialTheme.typography.titleMedium)
            },
            text           = {
                Text(
                    "Please install Google Pay, PhonePe, Paytm, or BHIM and try again.\n\n" +
                            "Alternatively, use the PayPal option above."
                )
            },
            confirmButton  = {
                TextButton(onClick = { showNoUpiDialog = false }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    // ── No browser installed dialog ───────────────────────────────────────────
    if (showNoBrowserDialog) {
        AlertDialog(
            onDismissRequest = { showNoBrowserDialog = false },
            shape          = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title          = {
                Text("Cannot Open PayPal", style = MaterialTheme.typography.titleMedium)
            },
            text           = {
                Text("Could not open a browser. Please install Chrome or Firefox and try again.")
            },
            confirmButton  = {
                TextButton(onClick = { showNoBrowserDialog = false }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AccordionCard — tappable header with animated expandable body
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccordionCard(
    title: String,
    subtitle: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape  = RoundedCornerShape(20.dp)
    ) {
        Column {
            // Tappable header row — always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = Spacing.large, vertical = Spacing.medium),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text  = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Icon(
                    imageVector        = if (isOpen) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = if (isOpen) "Collapse" else "Expand",
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(20.dp)
                )
            }

            // Animated expandable body
            AnimatedVisibility(
                visible = isOpen,
                enter   = expandVertically(),
                exit    = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    Column(
                        modifier = Modifier.padding(Spacing.medium),
                        content  = content
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SectionLabel — small grey caps label above each accordion group
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = Spacing.large, bottom = Spacing.small)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// AmountChip — preset amount selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AmountChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.background
            )
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// URL launching helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Attempt to launch a URL via ACTION_VIEW.
 *
 * @return true if the Intent was handled, false if ActivityNotFoundException
 *         was thrown (no app can handle the URL).
 */
private fun launchUrl(context: Context, url: String): Boolean {
    if (url.isBlank()) return false
    val intent   = Intent(Intent.ACTION_VIEW, url.toUri())
    val activity = context.findActivity()
    return try {
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** Walk the Context wrapper chain to find the nearest Activity. */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}