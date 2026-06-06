package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance

/**
 * Colors for the app's prominent filled call-to-action buttons.
 *
 * The default Material 3 [androidx.compose.material3.Button] fills with
 * `primary` / `onPrimary`. Under Material You some dark-mode palettes produce a
 * near-white `primary` whose `onPrimary` then fails contrast — the button reads
 * as a glaring white slab with invisible text. In dark mode this instead uses
 * the `primaryContainer` / `onPrimaryContainer` pair, which Material You always
 * generates as a contrast-safe couple and which is a dimmer, comfortable tone on
 * a dark surface (matching the tonal keypad buttons). Light mode keeps the
 * prominent filled accent, which is already a dark button on a light surface.
 *
 * Detection is by surface luminance so it follows whichever scheme is actually
 * applied (system dark mode, dim mode, etc.), not just the system setting.
 */
@Composable
fun primaryActionButtonColors(): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    return if (isDark) {
        ButtonDefaults.buttonColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        )
    } else {
        ButtonDefaults.buttonColors()
    }
}
