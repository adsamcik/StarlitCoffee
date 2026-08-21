package com.adsamcik.starlitcoffee.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.diagnostics.StarlitTracebox
import dev.tracebox.Tracebox
import dev.tracebox.ui.compose.TraceboxAdvancedControls
import dev.tracebox.ui.compose.TraceboxDiagnosticsScreen
import dev.tracebox.ui.compose.TraceboxDiagnosticsUiConfiguration
import dev.tracebox.ui.compose.TraceboxDiagnosticsUiStrings
import dev.tracebox.ui.compose.TraceboxPackageActions
import dev.tracebox.ui.compose.TraceboxPrimaryAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val handle = remember { Tracebox.current() }
    val supportDescription = stringResource(R.string.msg_diagnostics_support_description)
    val configuration = remember(supportDescription) {
        TraceboxDiagnosticsUiConfiguration(
            strings = TraceboxDiagnosticsUiStrings(
                supportDescription = supportDescription,
            ),
            showHeading = false,
            primaryAction = TraceboxPrimaryAction.SHARE,
            packageActions = TraceboxPackageActions(
                upload = false,
                share = true,
                save = true,
                deleteAllData = true,
            ),
            advancedControls = TraceboxAdvancedControls(
                initiallyExpanded = false,
                logcatMirroring = false,
                captureKinds = StarlitTracebox.supportedCaptureKinds,
            ),
            defaultPolicy = StarlitTracebox.defaultPolicy,
        )
    }

    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.screen_diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            if (handle == null) {
                Text(
                    text = stringResource(R.string.msg_diagnostics_unavailable),
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                TraceboxDiagnosticsScreen(
                    handle = handle,
                    configuration = configuration,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
