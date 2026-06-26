package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Android-Settings-style building blocks: a small tinted [SettingsSectionHeader],
 * a rounded [SettingsGroup] container, and compact [SettingsSwitchRow] /
 * [SettingsNavigationRow] / [SettingsSelectorBlock] rows that live inside it.
 *
 * The goal is progressive disclosure and low visual load: short titles, optional
 * one/two-line summaries, controls aligned to the trailing edge, and the whole
 * row tappable — instead of every option shouting a full paragraph of help text.
 */

private val RowHorizontalPadding = 16.dp
private val RowVerticalPadding = 14.dp
private val RowMinHeight = 56.dp
private val RowTrailingGap = 16.dp
private const val DisabledAlpha = 0.38f

/**
 * Small section label that introduces a [SettingsGroup], mirroring the tinted
 * category headers in the system Settings app.
 */
@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 8.dp, top = 12.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/**
 * Rounded container that groups related preference rows into a single card.
 * Place [SettingsSwitchRow] / [SettingsNavigationRow] / [SettingsSelectorBlock]
 * inside; use [SettingsRowDivider] between rows for an inset separator.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

/** Inset divider used between rows within a [SettingsGroup]. */
@Composable
fun SettingsRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = RowHorizontalPadding),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Compact switch row: title, optional [summary], and a trailing [Switch]. The
 * whole row is toggleable for an easy tap target; when [enabled] is false the
 * text dims and the row stops responding (used for sub-options gated behind a
 * master toggle).
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else DisabledAlpha
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(
            title = title,
            summary = summary,
            contentAlpha = contentAlpha,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(RowTrailingGap))
        // The row owns the click/semantics via toggleable, so the Switch itself
        // is non-interactive (onCheckedChange = null) and only reflects state.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Compact navigation row: title, optional [summary], and a trailing chevron.
 * Tapping invokes [onClick] — used to drill into a focused sub-screen.
 */
@Composable
fun SettingsNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leadingIcon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(RowTrailingGap))
        }
        SettingsRowText(
            title = title,
            summary = summary,
            contentAlpha = 1f,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(RowTrailingGap))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Block for a richer selector (e.g. a chip row): a title, optional [summary],
 * and a [content] slot beneath. Lives inside a [SettingsGroup] like the other
 * rows, but stacks its control vertically because chips don't fit on one line.
 */
@Composable
fun SettingsSelectorBlock(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Composable
private fun SettingsRowText(
    title: String,
    summary: String?,
    contentAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
        )
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
    }
}
