package com.greenicephoenix.voidnote.presentation.settings

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
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

// ─────────────────────────────────────────────────────────────────────────────
// SupportScreen — "Support the Developer"
//
// Three options:
//   1. Buy Me a Coffee — external link to buymeacoffee.com
//   2. Ko-fi           — external link to ko-fi.com
//   3. Razorpay / UPI  — QR code image + UPI ID for direct payment
//
// HOW TO SET UP YOUR REAL LINKS:
//   1. Replace BUYMEACOFFEE_URL with your actual Buy Me a Coffee page URL
//   2. Replace KOFI_URL with your actual Ko-fi page URL
//   3. Replace UPI_ID with your actual UPI ID (e.g. yourname@upi)
//   4. For the QR code: generate your UPI QR from any UPI app or
//      https://upiqr.in, save it as res/drawable/upi_qr.png, then
//      replace the QrPlaceholder composable below with:
//        Image(painterResource(R.drawable.upi_qr), "UPI QR Code",
//              modifier = Modifier.size(200.dp))
// ─────────────────────────────────────────────────────────────────────────────

private const val BUYMEACOFFEE_URL = "https://www.buymeacoffee.com/YOUR_USERNAME"
private const val KOFI_URL         = "https://ko-fi.com/YOUR_USERNAME"
private const val UPI_ID           = "yourname@upi"   // Replace with your UPI ID

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

            // ── Buy Me a Coffee ───────────────────────────────────────────────
            item {
                SupportOptionCard(
                    icon     = Icons.Default.Coffee,
                    title    = "Buy Me a Coffee",
                    subtitle = "buymeacoffee.com",
                    onClick  = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, BUYMEACOFFEE_URL.toUri())
                        )
                    }
                )
            }

            // ── Ko-fi ─────────────────────────────────────────────────────────
            item {
                SupportOptionCard(
                    icon     = Icons.Default.Favorite,
                    title    = "Ko-fi",
                    subtitle = "ko-fi.com",
                    onClick  = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, KOFI_URL.toUri())
                        )
                    }
                )
            }

            // ── Razorpay / UPI QR ─────────────────────────────────────────────
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
// SupportOptionCard — reusable card for external link options (BMaC, Ko-fi)
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
// UpiCard — Razorpay / UPI card with QR placeholder and UPI ID
//
// HOW TO ADD YOUR REAL QR CODE:
//   1. Generate your UPI QR at https://upiqr.in or from your UPI app
//   2. Save it as app/src/main/res/drawable/upi_qr.png
//   3. Replace QrPlaceholder() with:
//        Image(
//            painter  = painterResource(R.drawable.upi_qr),
//            contentDescription = "UPI QR Code",
//            modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp))
//        )
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
                        text  = "UPI / Razorpay",
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
            QrPlaceholder()

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