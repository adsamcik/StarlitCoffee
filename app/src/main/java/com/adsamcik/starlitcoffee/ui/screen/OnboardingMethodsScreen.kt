package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.ui.component.MethodChoiceGrid
import com.adsamcik.starlitcoffee.ui.component.primaryActionButtonColors

@Composable
fun OnboardingMethodsScreen(
    selected: BrewMethod?,
    onSelect: (BrewMethod) -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.screen_first_brewing_set_method), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.msg_first_brewing_set), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            MethodChoiceGrid(selected, tagPrefix = "onboarding_method", onSelect = onSelect)
        }
        Button(onClick = onNext, enabled = selected != null,
            colors = primaryActionButtonColors(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("onboarding_next_button")) {
            Text(stringResource(R.string.action_next))
        }
    }
}
