package com.adsamcik.starlitcoffee.ui.screen
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.MainActivity
import com.adsamcik.starlitcoffee.notification.BrewSessionVisibilityRegistry
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionEntityMapper
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionRestoreResult
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionOperationResult
import com.adsamcik.starlitcoffee.data.db.entity.ActiveBrewSessionEntity
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageActualValue
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import com.adsamcik.starlitcoffee.ui.component.ExitBrewConfirmationDialog
import com.adsamcik.starlitcoffee.ui.component.primaryActionButtonColors
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentationMapper
import com.adsamcik.starlitcoffee.ui.session.BrewSessionActionAvailability
import com.adsamcik.starlitcoffee.ui.session.BrewSessionLiveRegion
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import com.adsamcik.starlitcoffee.ui.session.DurableBrewSessionFeedback
import com.adsamcik.starlitcoffee.ui.session.StageActualInputKind
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInInstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidancePreferences
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceRequest
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolution
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolver
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePresentationLevel
import com.adsamcik.starlitcoffee.ui.guidance.P1BuiltInGuidanceCatalog
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactRecipeReleaseGate
import com.adsamcik.starlitcoffee.ui.guidance.LegacyBuiltInGuidanceCatalog
import com.adsamcik.starlitcoffee.ui.util.DimModeScaffold
import com.adsamcik.starlitcoffee.ui.util.KeepScreenOn
import com.adsamcik.starlitcoffee.ui.util.keepScreenOnTimeoutMillis
import com.adsamcik.starlitcoffee.ui.util.rememberDimModeController
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Restores and presents one persisted brew session without becoming a second
 * timer engine. The periodic display refresh is intentionally visual only;
 * durable timing and automatic completion remain the coordinator/worker's
 * responsibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewSessionScreen(
    sessionId: String,
    sessionFlow: Flow<ActiveBrewSessionEntity?>,
    onDispatch: suspend (SessionEvent) -> BrewSessionOperationResult,
    onBack: () -> Unit,
    guidancePreferences: DurableBrewSessionGuidancePreferences,
    exactRecipeReleaseGate: P1ExactRecipeReleaseGate,
    onRememberGuidanceForProfile: suspend (String, GuidancePresentationLevel) -> Unit,
    dimModeEnabled: Boolean = true,
    dimModeTrueBlack: Boolean = false,
    dimModeReduceBrightness: Boolean = false,
    dimModeFullscreen: Boolean = false,
    dimModeForceDarkInLight: Boolean = false,
    vibrationTheme: BrewVibrationTheme = BrewVibrationTheme.CLASSIC,
    onScreenForegrounded: () -> Unit = {},
    onScreenBackgrounded: () -> Unit = {},
) {
    val entity by sessionFlow.collectAsStateWithLifecycle(initialValue = null)
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowWallClockMillis by remember(sessionId) { mutableStateOf(System.currentTimeMillis()) }
    var dispatching by remember(sessionId) { mutableStateOf(false) }
    var showCancelDialog by remember(sessionId) { mutableStateOf(false) }
    var sessionGuidanceOverride by remember(sessionId) { mutableStateOf<GuidancePresentationLevel?>(null) }
    var savingGuidance by remember(sessionId) { mutableStateOf(false) }
    var actionFailureMessageResId by remember(sessionId) { mutableStateOf<Int?>(null) }
    var autoReconcileRequestedStageId by remember(sessionId) { mutableStateOf<StageInstanceId?>(null) }
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val scope = rememberCoroutineScope()
    val currentOnScreenForegrounded by rememberUpdatedState(onScreenForegrounded)
    val currentOnScreenBackgrounded by rememberUpdatedState(onScreenBackgrounded)
    val dimController = rememberDimModeController(featureEnabled = dimModeEnabled)

    val restored = remember(entity) { entity?.let(ActiveBrewSessionEntityMapper::restore) }
    val presentation = remember(restored, nowWallClockMillis) {
        restored?.let { ActiveBrewSessionPresentationMapper.map(it, nowWallClockMillis) }
    }
    val restoredSession = (restored as? ActiveBrewSessionRestoreResult.Restored)?.value
    val persistedRecipe = restoredSession?.recipe
    val exactRecipeId = remember(persistedRecipe?.builtInRecipeId) {
        persistedRecipe?.builtInRecipeId?.let { rawRecipeId ->
            runCatching { BuiltInRecipeId(rawRecipeId) }
                .getOrNull()
                ?.takeIf { recipeId -> BuiltInP1RecipeCatalog.find(recipeId) != null }
        }
    }
    val isReleaseGatedExactSession = persistedRecipe?.let { recipe ->
        exactRecipeReleaseGate.shouldGatePersistedSession(
            rawRecipeId = recipe.builtInRecipeId,
            rawBrewerProfileId = recipe.brewerProfileId,
        )
    } ?: false
    if (isReleaseGatedExactSession) {
        BackHandler(onBack = onBack)
        BrewSessionUnavailable(
            hasSession = true,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val guidanceResolver = remember(exactRecipeId, exactRecipeReleaseGate) {
        val guidanceCatalogs = exactRecipeId
            ?.let(exactRecipeReleaseGate::catalogFor)
            ?.let(::listOf)
            ?: listOf(LegacyBuiltInGuidanceCatalog.catalog, P1BuiltInGuidanceCatalog.catalog)
        DurableBrewSessionGuidanceResolver(
            guidanceCatalogs = guidanceCatalogs,
            instructionAssets = BuiltInInstructionAssetCatalog.catalog,
        )
    }

    val guidanceResolution = remember(
        restoredSession?.recipe,
        presentation,
        guidancePreferences,
        sessionGuidanceOverride,
    ) {
        val availablePresentation = presentation as? ActiveBrewSessionPresentation.Available
        if (restoredSession != null && availablePresentation != null) {
            guidanceResolver.resolve(
                DurableBrewSessionGuidanceRequest(
                    methodFamilyId = restoredSession.recipe.methodFamilyId,
                    brewerProfileId = restoredSession.recipe.brewerProfileId,
                    currentStage = availablePresentation.currentStage,
                    preferences = guidancePreferences.copy(sessionOverride = sessionGuidanceOverride),
                ),
            )
        } else {
            null
        }
    }

    val status = (presentation as? ActiveBrewSessionPresentation.Available)?.status
    val activity = LocalContext.current.findActivity() as? MainActivity
    val isPictureInPicture = activity?.isShowingBrewPictureInPicture == true
    if (presentation is ActiveBrewSessionPresentation.Available) {
        DisposableEffect(sessionId, lifecycleOwner) {
            val foregroundHandle = BrewSessionVisibilityRegistry.foregroundHandle(sessionId)
            fun markForeground() {
                foregroundHandle.onResumed()
                isLifecycleResumed = true
                currentOnScreenForegrounded()
            }
            fun markBackground() {
                foregroundHandle.onPaused()
                isLifecycleResumed = false
                currentOnScreenBackgrounded()
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> markForeground()
                    Lifecycle.Event.ON_PAUSE -> markBackground()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            val initiallyResumed = lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.RESUMED)
            isLifecycleResumed = initiallyResumed
            if (initiallyResumed) {
                markForeground()
            } else {
                currentOnScreenBackgrounded()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                val wasResumed = isLifecycleResumed
                foregroundHandle.dispose()
                if (wasResumed) currentOnScreenBackgrounded()
            }
        }
    }

    val shouldOfferPictureInPicture = (presentation as? ActiveBrewSessionPresentation.Available)
        ?.let(::shouldOfferPictureInPicture)
        ?: false
    val availablePresentation = presentation as? ActiveBrewSessionPresentation.Available
    val stickyPrimaryAction = availablePresentation?.actions?.let(::primaryBrewSessionAction)
    if (shouldOfferPictureInPicture && !isPictureInPicture) {
        KeepScreenOn(
            timeoutMillis = keepScreenOnTimeoutMillis((MAX_PICTURE_IN_PICTURE_STAGE_MILLIS / MILLIS_PER_SECOND).toInt()),
        )
    }
    availablePresentation?.let { activePresentation ->
        DurableBrewSessionFeedback(
            presentation = activePresentation,
            vibrationTheme = vibrationTheme,
            dimController = dimController,
            enabled = isLifecycleResumed && !isPictureInPicture,
        )
    }
    LaunchedEffect(activity, status) {
        if (status == BrewSessionStatus.RUNNING) {
            activity?.requestBrewNotificationPermission()
        }
    }
    DisposableEffect(activity, shouldOfferPictureInPicture) {
        activity?.setBrewPictureInPictureAvailable(shouldOfferPictureInPicture)
        onDispose { activity?.setBrewPictureInPictureAvailable(false) }
    }

    val shouldRefreshDisplay = status == BrewSessionStatus.RUNNING &&
        (isLifecycleResumed || (isPictureInPicture && shouldOfferPictureInPicture))
    LaunchedEffect(sessionId, shouldRefreshDisplay) {
        if (!shouldRefreshDisplay) return@LaunchedEffect
        while (true) {
            nowWallClockMillis = System.currentTimeMillis()
            delay(DISPLAY_REFRESH_MILLIS)
        }
    }
    LaunchedEffect(status) {
        if (status == BrewSessionStatus.CANCELLED) onBack()
    }

    val dispatch: (SessionEvent) -> Unit = { event ->
        if (!dispatching) {
            dispatching = true
            scope.launch {
                try {
                    when (onDispatch(event)) {
                        is BrewSessionOperationResult.Active,
                        is BrewSessionOperationResult.PendingEffect -> actionFailureMessageResId = null

                        is BrewSessionOperationResult.NotFound -> {
                            actionFailureMessageResId = R.string.msg_brew_session_not_found
                            onBack()
                        }

                        is BrewSessionOperationResult.Unavailable,
                        is BrewSessionOperationResult.ConcurrentUpdate -> {
                            actionFailureMessageResId = R.string.msg_brew_session_unavailable
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    actionFailureMessageResId = R.string.msg_brew_session_unavailable
                } finally {
                    dispatching = false
                }
            }
        }
    }

    val expiredAutomaticStageId = (presentation as? ActiveBrewSessionPresentation.Available)
        ?.takeIf { it.status == BrewSessionStatus.RUNNING }
        ?.currentStage
        ?.takeIf { stage -> stage.completion.isAutomaticDeadlineReached() }
        ?.stageInstanceId
    LaunchedEffect(expiredAutomaticStageId, dispatching) {
        if (expiredAutomaticStageId == null) {
            autoReconcileRequestedStageId = null
        } else if (!dispatching && autoReconcileRequestedStageId != expiredAutomaticStageId) {
            autoReconcileRequestedStageId = expiredAutomaticStageId
            dispatch(SessionEvent.Reconcile())
        }
    }

    if (isPictureInPicture) {
        val availablePresentation = presentation as? ActiveBrewSessionPresentation.Available
        if (availablePresentation == null) {
            BrewSessionLoading()
        } else {
            BrewSessionPictureInPictureContent(
                presentation = availablePresentation,
                isBoundedStage = shouldOfferPictureInPicture,
            )
        }
        return
    }

    BackHandler(onBack = onBack)
    DimModeScaffold(
        controller = dimController,
        modifier = Modifier.fillMaxSize(),
        trueBlackBackground = dimModeTrueBlack,
        reduceBrightness = dimModeReduceBrightness,
        hideSystemBars = dimModeFullscreen,
        forceDarkInLight = dimModeForceDarkInLight,
    ) {
    Scaffold(
        bottomBar = {
            if (availablePresentation != null && stickyPrimaryAction != null) {
                BrewSessionPrimaryActionBar(
                    action = stickyPrimaryAction,
                    isDispatching = dispatching,
                    onDispatch = dispatch,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_brew_session_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (presentation) {
            null -> BrewSessionLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            is ActiveBrewSessionPresentation.Unavailable -> BrewSessionUnavailable(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                hasSession = entity != null,
                onBack = onBack,
            )

            is ActiveBrewSessionPresentation.Available -> BrewSessionContent(
                presentation = presentation,
                completedLogId = entity?.completedLogId,
                isDispatching = dispatching,
                actionFailureMessageResId = actionFailureMessageResId,
                onDispatch = dispatch,
                onCancelRequest = { showCancelDialog = true },
                onBack = onBack,
                guidanceResolution = guidanceResolution,
                sessionGuidanceOverride = sessionGuidanceOverride,
                onSessionGuidanceOverride = { level -> sessionGuidanceOverride = level },
                onRememberGuidanceForProfile = { level ->
                    val profileId = restoredSession?.recipe?.brewerProfileId
                    if (!savingGuidance && profileId != null) {
                        savingGuidance = true
                        scope.launch {
                            try {
                                onRememberGuidanceForProfile(profileId, level)
                                sessionGuidanceOverride = null
                            } finally {
                                savingGuidance = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    }
    if (showCancelDialog) {
        ExitBrewConfirmationDialog(
            onConfirm = {
                showCancelDialog = false
                dispatch(SessionEvent.Cancel(newEventId()))
            },
            onDismiss = { showCancelDialog = false },
        )
    }
}

@Composable
private fun BrewSessionPictureInPictureContent(
    presentation: ActiveBrewSessionPresentation.Available,
    isBoundedStage: Boolean,
) {
    val stage = presentation.currentStage
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stage?.action?.label()
                    ?: stringResource(R.string.screen_brew_session_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isBoundedStage) {
                // Android cannot force-close an existing PiP window. Once the
                // stage needs manual, observed, actual, or long-form attention,
                // stop presenting it as a timer and ask the user to expand it.
                Text(
                    text = stringResource(
                        when (presentation.status) {
                            BrewSessionStatus.COMPLETED -> R.string.msg_brew_session_finished
                            BrewSessionStatus.PAUSED -> R.string.msg_brew_session_paused
                            else -> R.string.msg_brew_notification_step_started
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val remainingMillis = stage?.completion?.remainingMillis()
            val time = remainingMillis?.let(::formatDuration)
                ?: formatDuration(presentation.totalActiveElapsedMillis)
            Text(
                text = time,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = stringResource(
                    if (remainingMillis == null) R.string.label_elapsed else R.string.label_remaining,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrewSessionLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun BrewSessionUnavailable(
    hasSession: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(
                if (hasSession) R.string.msg_brew_session_unavailable else R.string.msg_brew_session_not_found,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun BrewSessionContent(
    presentation: ActiveBrewSessionPresentation.Available,
    completedLogId: Long?,
    isDispatching: Boolean,
    actionFailureMessageResId: Int?,
    onDispatch: (SessionEvent) -> Unit,
    onCancelRequest: () -> Unit,
    onBack: () -> Unit,
    guidanceResolution: DurableBrewSessionGuidanceResolution?,
    sessionGuidanceOverride: GuidancePresentationLevel?,
    onSessionGuidanceOverride: (GuidancePresentationLevel?) -> Unit,
    onRememberGuidanceForProfile: (GuidancePresentationLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stage = presentation.currentStage
    val stageProgress = presentation.stageProgress
    val actionLabel = stage?.action?.label().orEmpty()
    val progressLabel = stageProgress.currentStageNumber?.let { number ->
        stringResource(R.string.format_brew_stage_progress, number, stageProgress.totalStageCount)
    }.orEmpty()
    val statusDescription = stringResource(
        R.string.cd_brew_session_status,
        actionLabel,
        progressLabel,
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics {
                contentDescription = statusDescription
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (presentation.status == BrewSessionStatus.COMPLETED) {
            CompletedBrewSession(
                completedLogId = completedLogId,
                onBack = onBack,
            )
            return@Column
        }

        actionFailureMessageResId?.let { messageResId ->
            Text(
                text = stringResource(messageResId),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }

        Text(
            text = progressLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                heading()
                contentDescription = statusDescription
                liveRegion = if (presentation.accessibility.liveRegion == BrewSessionLiveRegion.ASSERTIVE) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            },
        )
        BrewSessionTiming(presentation, stage)
        stage?.let { current ->
            BrewSessionStageCard(
                stage = current,
                isDispatching = isDispatching,
                canManualAdvance = presentation.actions.canManualAdvance,
                canFinish = presentation.actions.canFinish,
                onDispatch = onDispatch,
            )
        }
        BrewSessionSafety(presentation.safetyMessages)
        stage?.let { current ->
            BrewSessionReferenceCues(
                sessionKey = presentation.sessionId,
                cues = current.referenceCues,
            )
        }
        guidanceResolution?.let { guidance ->
            BrewSessionGuidancePanel(
                resolution = guidance,
                sessionOverride = sessionGuidanceOverride,
                onSessionOverride = onSessionGuidanceOverride,
                onRememberForBrewer = onRememberGuidanceForProfile,
            )
        }
        BrewSessionSecondaryControls(
            presentation = presentation,
            isDispatching = isDispatching,
            onDispatch = onDispatch,
            onCancelRequest = onCancelRequest,
        )
    }
}

@Composable
private fun BrewSessionTiming(
    presentation: ActiveBrewSessionPresentation.Available,
    stage: CurrentBrewStagePresentation?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TimingValue(
                label = stringResource(R.string.label_elapsed),
                value = formatDuration(presentation.totalActiveElapsedMillis),
            )
            val remaining = stage?.completion?.remainingMillis()
            if (remaining != null) {
                TimingValue(
                    label = stringResource(R.string.label_remaining),
                    value = formatDuration(remaining),
                    alignEnd = true,
                )
            }
        }
    }
}

@Composable
private fun TimingValue(
    label: String,
    value: String,
    alignEnd: Boolean = false,
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BrewSessionStageCard(
    stage: CurrentBrewStagePresentation,
    isDispatching: Boolean,
    canManualAdvance: Boolean,
    canFinish: Boolean,
    onDispatch: (SessionEvent) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val completion = stage.completion) {
                BrewStageCompletionPresentation.Manual -> {
                    Text(stringResource(R.string.msg_brew_stage_manual))
                    CompletionButton(
                        enabled = canManualAdvance && !isDispatching,
                        isFinal = canFinish,
                        onDispatch = onDispatch,
                    )
                }

                BrewStageCompletionPresentation.Immediate -> {
                    Text(stringResource(R.string.msg_brew_stage_manual))
                }

                is BrewStageCompletionPresentation.Countdown -> {
                    Text(
                        text = stringResource(
                            R.string.format_brew_stage_remaining_time,
                            formatDuration(completion.remainingMillis),
                        ),
                    )
                }

                is BrewStageCompletionPresentation.ElapsedRange -> {
                    Text(
                        text = stringResource(R.string.label_remaining) + ": " +
                            formatDuration(completion.maximumRemainingMillis),
                    )
                    CompletionButton(
                        enabled = canManualAdvance && !isDispatching,
                        isFinal = canFinish,
                        onDispatch = onDispatch,
                    )
                }

                is BrewStageCompletionPresentation.ActualValue -> ActualValueControl(
                    completion = completion,
                    isDispatching = isDispatching,
                    onDispatch = onDispatch,
                )

                is BrewStageCompletionPresentation.ObservedEvent -> {
                    Text(stringResource(R.string.msg_brew_stage_observation))
                    Button(
                        enabled = !completion.alreadyRecorded && !isDispatching,
                        onClick = {
                            onDispatch(
                                SessionEvent.RecordObservation(
                                    completion.observationId,
                                    newEventId(),
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.action_mark_observed))
                    }
                }

                is BrewStageCompletionPresentation.ExternalMarker -> {
                    Text(stringResource(R.string.msg_brew_stage_observation))
                    Button(
                        enabled = !completion.alreadyRecorded && !isDispatching,
                        onClick = {
                            onDispatch(
                                SessionEvent.RecordMarker(
                                    completion.markerId,
                                    newEventId(),
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.action_mark_signal_received))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionButton(
    enabled: Boolean,
    isFinal: Boolean,
    onDispatch: (SessionEvent) -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = {
            onDispatch(
                if (isFinal) SessionEvent.Finish(newEventId()) else SessionEvent.ManualAdvance(newEventId()),
            )
        },
    ) {
        Text(
            stringResource(
                if (isFinal) R.string.action_finish else R.string.action_complete_step,
            ),
        )
    }
}

@Composable
private fun ActualValueControl(
    completion: BrewStageCompletionPresentation.ActualValue,
    isDispatching: Boolean,
    onDispatch: (SessionEvent) -> Unit,
) {
    var text by remember(completion.inputKind) {
        mutableStateOf(completion.recordedGrams?.toString().orEmpty())
    }
    val value = text.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    Text(stringResource(R.string.format_brew_stage_target_grams, completion.targetGrams))
    completion.recordedGrams?.let { recorded ->
        Text(stringResource(R.string.format_brew_stage_recorded_grams, recorded))
    }
    Text(stringResource(R.string.format_brew_stage_remaining_grams, completion.remainingGrams))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.label_measured_amount)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        enabled = value != null && !isDispatching,
        onClick = {
            value?.let { grams ->
                onDispatch(SessionEvent.RecordActual(completion.inputKind.event(grams), newEventId()))
            }
        },
    ) {
        Text(stringResource(R.string.action_save_measurement))
    }
}

@Composable
private fun BrewSessionSafety(messages: List<StageSafetyMessage>) {
    messages.forEach { message ->
        val container = when (message.severity) {
            StageSafetySeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
            StageSafetySeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
            StageSafetySeverity.ADVICE -> MaterialTheme.colorScheme.secondaryContainer
        }
        Card(colors = CardDefaults.cardColors(containerColor = container)) {
            Text(
                text = stringResource(message.messageRes()),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

internal enum class BrewSessionPrimaryAction {
    START,
    PAUSE,
    RESUME,
}

internal fun primaryBrewSessionAction(
    actions: BrewSessionActionAvailability,
): BrewSessionPrimaryAction? = when {
    actions.canStart -> BrewSessionPrimaryAction.START
    actions.canPause -> BrewSessionPrimaryAction.PAUSE
    actions.canResume -> BrewSessionPrimaryAction.RESUME
    else -> null
}

@Composable
private fun BrewSessionPrimaryActionBar(
    action: BrewSessionPrimaryAction,
    isDispatching: Boolean,
    onDispatch: (SessionEvent) -> Unit,
) {
    val labelRes = when (action) {
        BrewSessionPrimaryAction.START -> R.string.action_start_brewing
        BrewSessionPrimaryAction.PAUSE -> R.string.action_pause
        BrewSessionPrimaryAction.RESUME -> R.string.action_resume
    }
    val onClick = {
        onDispatch(
            when (action) {
                BrewSessionPrimaryAction.START -> SessionEvent.Start(newEventId())
                BrewSessionPrimaryAction.PAUSE -> SessionEvent.Pause(newEventId())
                BrewSessionPrimaryAction.RESUME -> SessionEvent.Resume(newEventId())
            },
        )
    }
    val buttonModifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp)
        .height(56.dp)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        if (action == BrewSessionPrimaryAction.PAUSE) {
            OutlinedButton(
                enabled = !isDispatching,
                onClick = onClick,
                modifier = buttonModifier,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Button(
                enabled = !isDispatching,
                onClick = onClick,
                modifier = buttonModifier,
                shape = MaterialTheme.shapes.extraLarge,
                colors = primaryActionButtonColors(),
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun BrewSessionSecondaryControls(
    presentation: ActiveBrewSessionPresentation.Available,
    isDispatching: Boolean,
    onDispatch: (SessionEvent) -> Unit,
    onCancelRequest: () -> Unit,
) {
    if (!presentation.actions.canSkip && !presentation.actions.canCancel) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (presentation.actions.canSkip) {
            TextButton(
                enabled = !isDispatching,
                onClick = { onDispatch(SessionEvent.Skip(newEventId())) },
            ) {
                Text(stringResource(R.string.action_skip))
            }
        }
        if (presentation.actions.canCancel) {
            OutlinedButton(
                enabled = !isDispatching,
                onClick = onCancelRequest,
            ) {
                Text(stringResource(R.string.action_cancel_brew))
            }
        }
    }
}

@Composable
private fun CompletedBrewSession(
    completedLogId: Long?,
    onBack: () -> Unit,
) {
    Text(
        text = stringResource(
            if (completedLogId == null) R.string.msg_brew_session_finishing else R.string.msg_brew_session_finished,
        ),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    if (completedLogId != null) {
        Button(onClick = onBack) {
            Text(stringResource(R.string.action_finish))
        }
    }
}

@Composable
private fun BrewStageAction.label(): String = stringResource(
    when (this) {
        BrewStageAction.PREPARE -> R.string.action_brew_prepare
        BrewStageAction.RINSE -> R.string.action_brew_rinse
        BrewStageAction.ADD_COFFEE -> R.string.action_brew_add_coffee
        BrewStageAction.ADD_WATER -> R.string.action_brew_add_water
        BrewStageAction.BLOOM -> R.string.action_brew_bloom
        BrewStageAction.POUR -> R.string.action_brew_pour
        BrewStageAction.AGITATE -> R.string.action_brew_agitate
        BrewStageAction.STEEP -> R.string.action_brew_steep
        BrewStageAction.RELEASE -> R.string.action_brew_release
        BrewStageAction.PRESS -> R.string.action_brew_press
        BrewStageAction.HEAT -> R.string.action_brew_heat
        BrewStageAction.OBSERVE -> R.string.action_brew_observe
        BrewStageAction.FILTER -> R.string.action_brew_filter
        BrewStageAction.SERVE -> R.string.action_brew_serve
        BrewStageAction.CLEAN_UP -> R.string.action_brew_clean_up
        BrewStageAction.CUSTOM -> R.string.action_brew_custom
    },
)

private fun BrewStageCompletionPresentation.isAutomaticDeadlineReached(): Boolean = when (this) {
    is BrewStageCompletionPresentation.Countdown -> remainingMillis <= 0L
    is BrewStageCompletionPresentation.ElapsedRange -> maximumRemainingMillis <= 0L
    else -> false
}

private fun BrewStageCompletionPresentation.remainingMillis(): Long? = when (this) {
    is BrewStageCompletionPresentation.Countdown -> remainingMillis
    is BrewStageCompletionPresentation.ElapsedRange -> maximumRemainingMillis
    else -> null
}

private fun StageActualInputKind.event(grams: Double): StageActualValue = when (this) {
    StageActualInputKind.ADDED_AMOUNT_GRAMS -> StageActualValue.AddedAmount(grams)
    StageActualInputKind.CUMULATIVE_AMOUNT_GRAMS -> StageActualValue.CumulativeAmount(grams)
    StageActualInputKind.BEVERAGE_YIELD_GRAMS -> StageActualValue.BeverageYield(grams)
}

private fun StageSafetyMessage.messageRes(): Int = when {
    code.contains("open_flame") || code.contains("unattended") -> R.string.warning_brew_safety_open_flame
    code.contains("hot_metal") || code.contains("hot_glass") -> R.string.warning_brew_safety_hot_metal
    code.contains("overflow") || code.contains("boil_over") -> R.string.warning_brew_safety_overflow
    code.contains("stable") || code.contains("stability") -> R.string.warning_brew_safety_stability
    code.contains("food") || code.contains("refriger") -> R.string.warning_brew_safety_food_storage
    code.contains("hot_liquid") || code.contains("burn") -> R.string.warning_brew_safety_hot_liquid
    else -> R.string.warning_brew_safety_generic
}

internal fun shouldOfferPictureInPicture(
    presentation: ActiveBrewSessionPresentation.Available,
): Boolean {
    if (presentation.status != BrewSessionStatus.RUNNING) return false
    return when (val completion = presentation.currentStage?.completion) {
        is BrewStageCompletionPresentation.Countdown -> {
            completion.targetElapsedMillis <= MAX_PICTURE_IN_PICTURE_STAGE_MILLIS
        }

        is BrewStageCompletionPresentation.ElapsedRange -> {
            completion.maximumElapsedMillis <= MAX_PICTURE_IN_PICTURE_STAGE_MILLIS
        }

        else -> false
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0L) / MILLIS_PER_SECOND).coerceAtMost(Int.MAX_VALUE.toLong())
    return String.format(Locale.getDefault(), "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private fun newEventId(): SessionEventId = SessionEventId("ui:${UUID.randomUUID()}")

private const val DISPLAY_REFRESH_MILLIS = 1_000L
private const val MAX_PICTURE_IN_PICTURE_STAGE_MILLIS = 20L * 60L * 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
