package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.brewing.BrewStepContent

@Composable
internal fun GuideStepCanvas(
    content: BrewStepContent,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        (content.title ?: content.action?.label())?.let {
            Text(it, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
        }
        if (content.referenceCues.isNotEmpty() || content.facts.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content.primaryReferenceCue?.let { ReferenceCueRow(it, prominent = true) }
                    content.referenceCues.filterNot { it == content.primaryReferenceCue }.forEach { ReferenceCueRow(it) }
                    content.facts.forEach { DetailsRow(stringResource(it.labelRes), it.value) }
                }
            }
        }
        content.illustration?.let { illustration ->
            Surface(shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                ApprovedInstructionAssetImage(illustration.asset, illustration.description, Modifier.padding(8.dp))
            }
        }
        content.instruction?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        content.target?.let {
            Surface(shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            }
        }
        content.operations.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        (content.controlRequirements + content.utilities).distinct().forEach {
            Text(it.localizedLabel(), style = MaterialTheme.typography.bodyMedium)
        }
        content.warning?.let { GuideCue(it, critical = content.safetyCritical, completion = false) }
        content.completionCue?.let { GuideCue(it, critical = false, completion = true) }
        if (content.details.isNotEmpty()) {
            ConnectedGuideDisclosure(
                title = stringResource(R.string.heading_guide_why), expanded = expanded,
                onExpandedChange = onExpandedChange,
            ) {
                content.details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** One continuous surface, including the header's focus/expanded state. */
@Composable
internal fun ConnectedGuideDisclosure(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val expansionDescription = stringResource(
        if (expanded) R.string.action_hide_details else R.string.action_show_details,
    )
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.animateContentSize()) {
            Surface(onClick = { onExpandedChange(!expanded) },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .semantics { stateDescription = expansionDescription }) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}

@Composable
private fun GuideCue(text: String, critical: Boolean, completion: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = if (critical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (critical) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {
            Icon(if (completion) Icons.Outlined.Visibility else Icons.Outlined.WarningAmber, null, Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (completion) Text(stringResource(R.string.label_guide_done_when), style = MaterialTheme.typography.labelLarge)
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
