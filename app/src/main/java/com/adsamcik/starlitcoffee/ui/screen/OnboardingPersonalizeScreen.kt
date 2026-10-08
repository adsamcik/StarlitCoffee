package com.adsamcik.starlitcoffee.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.BrewingSetCodec
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.ui.component.BrewingSetDraft
import com.adsamcik.starlitcoffee.ui.component.BrewingSetEditor
import com.adsamcik.starlitcoffee.ui.component.BrewingSetForm
import com.adsamcik.starlitcoffee.ui.component.BrewingSetsList
import com.adsamcik.starlitcoffee.ui.component.ScreenTopBar
import com.adsamcik.starlitcoffee.ui.component.initialBrewingSetup
import com.adsamcik.starlitcoffee.ui.component.primaryActionButtonColors
import java.util.UUID

@Composable
fun OnboardingPersonalizeScreen(
    drafts: List<BrewingSetDraft>, activeId: String,
    isSubmitting: Boolean = false, submitFailed: Boolean = false,
    onBack: () -> Unit,
    onDraftsChanged: (List<BrewingSetDraft>, String) -> Unit,
    onFinish: (List<BrewingSet>, String) -> Unit,
) {
    val data = GrinderDataSource.getInstance(LocalContext.current)
    val active = drafts.find { it.set.id == activeId } ?: drafts.first()
    val resolved = drafts.map { it.build(data) }
    var newSet by rememberSaveable { mutableStateOf<String?>(null) }
    val requestBack = { if (!isSubmitting) onBack() }
    BackHandler(onBack = requestBack)
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        ScreenTopBar(title = stringResource(R.string.label_brewing_sets), onBack = requestBack, backEnabled = !isSubmitting)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.msg_brewing_sets_onboarding), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (drafts.size > 1) {
                BrewingSetsList(drafts.map { draft -> draft.build(data) ?: draft.set }, active.set.id, data, !isSubmitting,
                    onSelect = { onDraftsChanged(drafts, it) }, onEdit = { onDraftsChanged(drafts, it.id) },
                    onDelete = { removed ->
                        val remaining = drafts.filterNot { it.set.id == removed.id }
                        if (remaining.isNotEmpty()) onDraftsChanged(remaining,
                            activeId.takeIf { it != removed.id } ?: remaining.first().set.id)
                    })
            }
            BrewingSetForm(active, data, !isSubmitting) { changed ->
                onDraftsChanged(drafts.map { if (it.set.id == changed.set.id) changed else it }, active.set.id)
            }
            FilledTonalButton(onClick = {
                newSet = BrewingSetCodec.encode(listOf(BrewingSet(UUID.randomUUID().toString(),
                    method = BrewMethod.PULSAR, setup = initialBrewingSetup(BrewMethod.PULSAR))))
            }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth().testTag("add_brewing_set")) {
                androidx.compose.material3.Icon(Icons.Default.Add, null)
                Text(stringResource(R.string.action_add_brewing_set), Modifier.padding(start = 8.dp))
            }
            if (submitFailed) Text(stringResource(R.string.msg_settings_save_failed), color = MaterialTheme.colorScheme.error)
            drafts.firstOrNull { it.build(data) == null }?.let { invalid ->
                Text(stringResource(R.string.msg_brewing_set_recipe_invalid), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { onDraftsChanged(drafts, invalid.set.id) }, modifier = Modifier.testTag("fix_invalid_brewing_set")) {
                    Text(com.adsamcik.starlitcoffee.ui.component.brewingSetName(invalid.set))
                }
            }
        }
        Button(onClick = { onFinish(resolved.filterNotNull(), active.set.id) },
            enabled = resolved.all { it != null } && !isSubmitting, colors = primaryActionButtonColors(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("onboarding_finish_button")) {
            Text(stringResource(R.string.action_start_brewing))
        }
    }
    newSet?.let { encoded ->
        BrewingSetEditor(BrewingSetCodec.decode(encoded).first(), true, data, false,
            onSave = { saved -> onDraftsChanged(drafts + BrewingSetDraft(saved), saved.id); newSet = null },
            onDismiss = { newSet = null })
    }
}
