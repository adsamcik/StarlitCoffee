package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.work.BagDraftField
import com.adsamcik.starlitcoffee.data.work.BagScanDraft
import com.adsamcik.starlitcoffee.util.RecognitionRunState

@Composable
fun BagDraftCard(
    draft: BagScanDraft,
    onOpen: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = draft.field(BagDraftField.NAME).value?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.label_coffee_draft)
    val roaster = draft.field(BagDraftField.ROASTER).value
    val status = when (draft.recognitionRunState) {
        RecognitionRunState.RUNNING,
        RecognitionRunState.PARTIAL,
        -> stringResource(
            if (draft.fields.values.any { !it.value.isNullOrBlank() }) {
                R.string.msg_checking_more_details
            } else {
                R.string.msg_checking_label
            },
        )
        RecognitionRunState.RETRIABLE_FAILURE -> stringResource(R.string.msg_draft_needs_attention)
        else -> stringResource(R.string.action_review_coffee_draft)
    }
    val description = stringResource(R.string.cd_open_coffee_draft, title, status)
    ElevatedCard(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            draft.photoUris.firstOrNull()?.let { uri ->
                BagThumbnail(uri = uri, size = 56.dp, shape = MaterialTheme.shapes.medium)
                Spacer(Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                roaster?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDiscard) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.action_discard_draft),
                )
            }
        }
    }
}
