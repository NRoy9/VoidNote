package com.greenicephoenix.voidnote.presentation.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.greenicephoenix.voidnote.presentation.theme.Spacing
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.greenicephoenix.voidnote.R

// ─────────────────────────────────────────────────────────────────────────────
// SupportScreen — "Support the Developer"
//
// Two payment options:
//   1. PayPal.me — international payments; you receive in INR after conversion
//   2. Direct UPI — QR code + UPI ID for instant Indian payments (zero fees)
//
// HOW TO COMPLETE SETUP:
//   1. Create a PayPal personal account at paypal.com if you don't have one.
//      Then go to paypal.me and create your personal link (e.g. paypal.me/yourname).
//      Paste that link into PAYPAL_URL below.
//
//   2. UPI_ID should already be set to your real UPI ID below.
//
//   3. For the UPI QR code image:
//      a. Open Google Pay / PhonePe / Paytm → Profile → "My QR Code" → save it
//         OR go to https://upiqr.in, enter your UPI ID, download the PNG.
//      b. Name the file exactly: upi_qr.png
//      c. In Android Studio: right-click app/src/main/res/drawable/ → Show in Explorer
//         → paste upi_qr.png into that folder.
//      d. In UpiCard() below, replace QrPlaceholder() with:
//           Image(
//               painter            = painterResource(R.drawable.upi_qr),
//               contentDescription = "UPI QR Code",
//               modifier           = Modifier
//                                       .size(180.dp)
//                                       .clip(RoundedCornerShape(8.dp))
//           )
//         and add to imports: import androidx.compose.ui.res.painterResource
//                             import androidx.compose.foundation.Image
// ─────────────────────────────────────────────────────────────────────────────

// TODO: Replace with your paypal.me link — e.g. "https://paypal.me/yourname"
private const val PAYPAL_URL = "https://paypal.me/GreenIcePhoenix"

// Your real UPI ID — replace if not already updated
private const val UPI_ID = "greenicephoenix@axisb" // ← replace with your real UPI ID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier        = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding  = PaddingValues(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {

            // ── Header ────────────────────────────────────────────────────────
            item {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text  = "○",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(Spacing.medium))
                    Text(
                        text      = "Void Note is free.\nNo ads. No subscriptions.",
                        style     = MaterialTheme.typography.titleMedium,
                        color     = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(Spacing.small))
                    Text(
                        text      = "If it's been useful, a small contribution\nhelps keep development going.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Section label ─────────────────────────────────────────────────
            item {
                Text(
                    text     = "CHOOSE A METHOD",
                    style    = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = Spacing.small, vertical = Spacing.extraSmall)
                )
            }

            // ── PayPal — for international supporters ─────────────────────────
            item {
                SupportOptionCard(
                    icon     = Icons.Default.AccountBalanceWallet,
                    title    = "Support via PayPal",
                    subtitle = "International · Cards · PayPal balance",
                    onClick  = {
                        // Guard: if PAYPAL_URL is still a placeholder or empty,
                        // show a toast instead of crashing with ActivityNotFoundException.
                        val url = PAYPAL_URL.trim()
                        if (url.isEmpty() || url.contains("YOUR_USERNAME")) {
                            Toast.makeText(
                                context,
                                "PayPal link not set up yet — check back soon!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, url.toUri())
                                )
                            } catch (e: android.content.ActivityNotFoundException) {
                                // No browser installed — extremely rare but handled
                                Toast.makeText(
                                    context,
                                    "No browser found. Visit: $url",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }

            // ── UPI — for Indian supporters ───────────────────────────────────
            item {
                UpiCard(upiId = UPI_ID)
            }

            // ── Footer note ───────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(Spacing.medium))
                Text(
                    text      = "Every contribution is appreciated.\nThank you for using Void Note. ♥",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.extraLarge))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SupportOptionCard — reusable tappable card for external payment links
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SupportOptionCard(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    title   : String,
    subtitle: String,
    onClick : () -> Unit
) {
    Surface(
        onClick          = onClick,
        modifier         = Modifier.fillMaxWidth(),
        shape            = RoundedCornerShape(12.dp),
        color            = MaterialTheme.colorScheme.surface,
        border           = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Icon in a small square container
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurface,
                    modifier           = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Icon(
                imageVector        = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Opens externally",
                tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UpiCard — Direct UPI payment card.
//
// TO ADD YOUR REAL QR CODE (once you have upi_qr.png in res/drawable/):
//   Replace QrPlaceholder() below with:
//     Image(
//         painter            = painterResource(R.drawable.upi_qr),
//         contentDescription = "UPI QR Code",
//         modifier           = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp))
//     )
//   And add import: androidx.compose.ui.res.painterResource
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun UpiCard(upiId: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surface,
        border   = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier            = Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                Box(
                    modifier         = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.QrCode,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface,
                        modifier           = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text  = "UPI — Indian Payments",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text  = "Scan QR or pay via UPI ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.large))

            // ── QR code — replace with real Image once drawable is ready ──────
            Image(
               painter            = painterResource(R.drawable.upi_qr),
               contentDescription = "UPI QR Code",
               modifier           = Modifier
                                       .size(180.dp)
                                       .clip(RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.height(Spacing.medium))

            // UPI ID — user can copy this manually
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text     = upiId,
                    style    = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color    = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        horizontal = Spacing.medium,
                        vertical   = Spacing.small
                    )
                )
            }

            Spacer(Modifier.height(Spacing.small))

            Text(
                text  = "Open any UPI app and scan, or enter the ID above",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QrPlaceholder — shown until the real upi_qr.png drawable is added.
// Replace with Image(painterResource(R.drawable.upi_qr), ...) when ready.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun QrPlaceholder() {
    Box(
        modifier         = Modifier
            .size(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Icon(
                imageVector        = Icons.Default.QrCode,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier           = Modifier.size(48.dp)
            )
            Text(
                text      = "QR coming soon",
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// TODO: Replace with your paypal.me link — e.g. "https://paypal.me/yourname"
//private const val PAYPAL_URL = "paypal.me/GreenIcePhoenix"

// Your real UPI ID — replace if not already updated
//private const val UPI_ID = "greenicephoenix@axisb"