package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.ui.component.iconForMethod
import com.adsamcik.starlitcoffee.ui.component.primaryActionButtonColors
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme

private val EnabledMethodsSetSaver: Saver<MutableState<Set<BrewMethod>>, ArrayList<String>> = Saver(
    save = { state -> ArrayList(state.value.map { it.name }) },
    restore = { list ->
        mutableStateOf(
            list.mapNotNull { runCatching { BrewMethod.valueOf(it) }.getOrNull() }.toSet(),
        )
    },
)

private val NullableBrewMethodSaver: Saver<MutableState<BrewMethod?>, String> = Saver(
    save = { it.value?.name ?: "" },
    restore = { name ->
        mutableStateOf(
            if (name.isEmpty()) null else runCatching { BrewMethod.valueOf(name) }.getOrNull(),
        )
    },
)

@Composable
fun OnboardingMethodsScreen(
    initialMethods: Set<BrewMethod> = emptySet(),
    initialDefault: BrewMethod? = null,
    onNext: (selectedMethods: Set<BrewMethod>, defaultMethod: BrewMethod) -> Unit,
) {
    val enabledMethods = rememberSaveable(saver = EnabledMethodsSetSaver) {
        mutableStateOf(initialMethods)
    }
    val defaultMethod = rememberSaveable(saver = NullableBrewMethodSaver) {
        mutableStateOf(initialDefault)
    }

    val enabledSet = enabledMethods.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.screen_methods_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.msg_methods_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            val methods = BrewMethod.entries.toList()
            val isOddCount = methods.size % 2 != 0
            itemsIndexed(
                items = methods,
                span = { index, _ ->
                    if (isOddCount && index == methods.lastIndex) {
                        GridItemSpan(2)
                    } else {
                        GridItemSpan(1)
                    }
                },
            ) { _, method ->
                val isSelected = enabledSet.contains(method)
                val isDefault = defaultMethod.value == method

                val containerColor = animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    label = "card_color",
                )

                val contentColor = animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    label = "content_color",
                )

                OutlinedCard(
                    onClick = {
                        val newSelected = !isSelected
                        val current = enabledMethods.value
                        enabledMethods.value = if (newSelected) current + method else current - method
                        if (!newSelected && defaultMethod.value == method) {
                            // Unselected the default — pick a new one
                            defaultMethod.value = enabledMethods.value.firstOrNull()
                        } else if (newSelected && defaultMethod.value == null) {
                            defaultMethod.value = method
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = containerColor.value,
                        contentColor = contentColor.value,
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("onboarding_method_${method.name}"),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = iconForMethod(method),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = method.displayName,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }

                        if (isSelected) {
                            IconButton(
                                onClick = { defaultMethod.value = method },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(
                                    imageVector = if (isDefault) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarOutline
                                    },
                                    contentDescription = if (isDefault) {
                                        stringResource(R.string.label_default_method)
                                    } else {
                                        stringResource(R.string.label_set_as_default)
                                    },
                                    tint = if (isDefault) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (enabledSet.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.format_selected,
                        enabledSet.size,
                        enabledSet.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    val methods = enabledSet.toSet()
                    val default = defaultMethod.value ?: methods.first()
                    onNext(methods, default)
                },
                enabled = enabledSet.isNotEmpty(),
                colors = primaryActionButtonColors(),
                modifier = Modifier.testTag("onboarding_next_button"),
            ) {
                Text(stringResource(R.string.action_next))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingMethodsScreenPreview() {
    StarlitCoffeeTheme {
        OnboardingMethodsScreen(
            onNext = { _, _ -> },
        )
    }
}
