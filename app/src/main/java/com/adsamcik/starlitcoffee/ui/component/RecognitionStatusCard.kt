package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.util.RecognitionOffer
import com.adsamcik.starlitcoffee.util.RecognitionAnnouncement
import com.adsamcik.starlitcoffee.util.RecognitionPresentation
import com.adsamcik.starlitcoffee.util.RecognitionRecoveryAction
import com.adsamcik.starlitcoffee.util.RecognitionStatusText

@Composable
fun RecognitionStatusCard(
    presentation: RecognitionPresentation,
    actions: RecognitionActions = RecognitionActions(),
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val setupMessage = when (actions.setupState) {
        RecognitionSetupState.OPENING -> stringResource(R.string.msg_opening_label_recognition_setup)
        RecognitionSetupState.UNAVAILABLE, RecognitionSetupState.FAILED ->
            stringResource(R.string.msg_could_not_open_label_recognition_setup)
        RecognitionSetupState.IDLE -> null
    }.takeIf { presentation.offer == RecognitionOffer.FINISH_SETUP }
    RecognitionLiveAnnouncement(presentation.announcement, setupMessage)
    val message = when (presentation.status) {
        RecognitionStatusText.CHECKING_LABEL -> stringResource(R.string.msg_checking_label)
        RecognitionStatusText.CHECKING_MORE_DETAILS -> stringResource(R.string.msg_checking_more_details)
        RecognitionStatusText.DETAILS_NEED_REVIEW -> pluralStringResource(
            R.plurals.format_label_details_need_review,
            presentation.unresolvedCount,
            presentation.unresolvedCount,
        )
        RecognitionStatusText.COULD_NOT_READ_MORE -> stringResource(R.string.msg_could_not_read_more_details)
        null -> null
    }
    if (message == null && presentation.offer == null && presentation.recoveryAction == null) return
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (presentation.offer != null) {
                Text(
                    text = stringResource(R.string.msg_label_recognition_offer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            setupMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (actions.setupState == RecognitionSetupState.OPENING) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            when {
                presentation.offer == RecognitionOffer.FINISH_SETUP && actions.onSetup != null -> {
                    TextButton(onClick = actions.onSetup, enabled = enabled && actions.setupState != RecognitionSetupState.OPENING) {
                        Text(stringResource(R.string.action_finish_label_recognition_setup))
                    }
                }
                presentation.offer == RecognitionOffer.INSTALL && actions.onInstall != null -> {
                    TextButton(onClick = actions.onInstall, enabled = enabled) {
                        Text(stringResource(R.string.action_set_up_label_recognition))
                    }
                }
                presentation.offer == RecognitionOffer.ENABLE && actions.onEnable != null -> {
                    TextButton(onClick = actions.onEnable, enabled = enabled) {
                        Text(stringResource(R.string.action_use_label_recognition))
                    }
                }
                presentation.recoveryAction == RecognitionRecoveryAction.RETRY && actions.onRetry != null -> {
                    TextButton(onClick = actions.onRetry, enabled = enabled) {
                        Text(stringResource(R.string.action_try_label_again))
                    }
                }
                presentation.recoveryAction == RecognitionRecoveryAction.RETAKE && actions.onRetake != null -> {
                    TextButton(onClick = actions.onRetake, enabled = enabled) {
                        Text(stringResource(R.string.action_retake_label_photo))
                    }
                }
            }
            if (presentation.offer != null && actions.onDisable != null) {
                TextButton(onClick = actions.onDisable, enabled = enabled) {
                    Text(stringResource(R.string.action_always_enter_manually))
                }
            }
        }
    }
}

/** Stable status only: changing field counts and controls never enter this live region. */
@Composable
private fun RecognitionLiveAnnouncement(announcement: RecognitionAnnouncement?, setupMessage: String?) {
    val message = setupMessage ?: when (announcement) {
        RecognitionAnnouncement.CHECKING -> stringResource(R.string.msg_checking_label)
        RecognitionAnnouncement.READY_TO_REVIEW -> stringResource(R.string.announce_label_details_ready)
        RecognitionAnnouncement.NO_RESULT -> stringResource(R.string.announce_label_recognition_no_result)
        RecognitionAnnouncement.COULD_NOT_READ_MORE -> stringResource(R.string.msg_could_not_read_more_details)
        RecognitionAnnouncement.RETRY -> stringResource(R.string.msg_could_not_read_more_details) +
            " " + stringResource(R.string.action_try_label_again)
        RecognitionAnnouncement.RETAKE -> stringResource(R.string.msg_could_not_read_more_details) +
            " " + stringResource(R.string.action_retake_label_photo)
        RecognitionAnnouncement.ENABLE -> stringResource(R.string.action_use_label_recognition)
        RecognitionAnnouncement.INSTALL -> stringResource(R.string.action_set_up_label_recognition)
        RecognitionAnnouncement.FINISH_SETUP -> stringResource(R.string.action_finish_label_recognition_setup)
        null -> null
    } ?: return
    Spacer(
        modifier = Modifier.size(1.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = message
        },
    )
}

enum class RecognitionSetupState { IDLE, OPENING, UNAVAILABLE, FAILED }

data class RecognitionActions(
    val onRetry: (() -> Unit)? = null,
    val onEnable: (() -> Unit)? = null,
    val onInstall: (() -> Unit)? = null,
    val onSetup: (() -> Unit)? = null,
    val onDisable: (() -> Unit)? = null,
    val onRetake: (() -> Unit)? = null,
    val setupState: RecognitionSetupState = RecognitionSetupState.IDLE,
)
