package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.ui.component.iconForMethod
import com.adsamcik.starlitcoffee.ui.component.primaryActionButtonColors
import com.adsamcik.starlitcoffee.ui.util.localizedDisplayName

@Composable
fun OnboardingMethodsScreen(
    initialMethods: Set<BrewMethod> = emptySet(),
    initialDefault: BrewMethod? = null,
    onNext: (selectedMethods: Set<BrewMethod>, defaultMethod: BrewMethod) -> Unit,
) {
    var methodName by rememberSaveable { mutableStateOf((initialDefault ?: initialMethods.firstOrNull())?.name) }
    val chosen = BrewMethod.entries.find { it.name == methodName }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text(stringResource(R.string.screen_first_brewing_set_method), style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.msg_first_brewing_set), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)) {
            itemsIndexed(BrewMethod.entries.toList(), span = { index, _ ->
                GridItemSpan(if (BrewMethod.entries.size % 2 == 1 && index == BrewMethod.entries.lastIndex) 2 else 1)
            }) { _, method ->
                val isSelected = method == chosen
                OutlinedCard(onClick = { methodName = method.name }, shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().height(120.dp).testTag("onboarding_method_${method.name}")
                        .semantics { selected = isSelected }) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(iconForMethod(method), contentDescription = null, modifier = Modifier.size(32.dp))
                            Text(method.localizedDisplayName(), style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null,
                                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
                        }
                    }
                }
            }
        }
        Button(onClick = { requireNotNull(chosen).let { onNext(setOf(it), it) } }, enabled = chosen != null,
            colors = primaryActionButtonColors(), modifier = Modifier.align(Alignment.End).testTag("onboarding_next_button")) {
            Text(stringResource(R.string.action_next))
        }
    }
}
