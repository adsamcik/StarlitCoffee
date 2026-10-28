package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrewSessionHeader(
    onBack: () -> Unit,
    onHelp: (() -> Unit)? = null,
) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp), shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
        },
        actions = {
            onHelp?.let { action -> IconButton(onClick = action, modifier = Modifier.size(48.dp), shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.action_brew_help))
            } }
        },
    )
}
