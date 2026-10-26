package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.adsamcik.starlitcoffee.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrewSessionHeader(
    title: String,
    onBack: () -> Unit,
    onHelp: (() -> Unit)? = null,
    onControls: (() -> Unit)? = null,
) {
    TopAppBar(
        title = { Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
        },
        actions = {
            onHelp?.let { action -> FilledTonalIconButton(onClick = action, shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.action_brew_help))
            } }
            onControls?.let { action -> IconButton(onClick = action) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.action_brew_controls))
            } }
        },
    )
}
