package com.adsamcik.starlitcoffee.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeDefinition
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupOption

private val LearningContentMaxWidth = 720.dp

enum class LearningLibraryAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

internal fun resolveLearningLibraryAvailability(
    hasProfiles: Boolean,
): LearningLibraryAvailability = when {
    hasProfiles -> LearningLibraryAvailability.AVAILABLE
    else -> LearningLibraryAvailability.UNAVAILABLE
}

internal data class LearningRecipeLabelParts(
    val title: String,
    val details: String?,
)

internal fun splitLearningRecipeLabel(label: String): LearningRecipeLabelParts {
    val separatorIndex = label.indexOf(RECIPE_LABEL_SEPARATOR)
    if (separatorIndex < 0) return LearningRecipeLabelParts(title = label, details = null)
    return LearningRecipeLabelParts(
        title = label.substring(0, separatorIndex).trim(),
        details = label.substring(separatorIndex + RECIPE_LABEL_SEPARATOR.length)
            .trim()
            .takeIf(String::isNotEmpty),
    )
}

private const val RECIPE_LABEL_SEPARATOR = " · "

internal data class LearningLibraryProfileOption(
    val profileId: BrewerProfileId,
    val displayName: String,
    val methodFamilyName: String,
    val guides: List<LearningLibraryGuideOption>,
)

internal sealed interface LearningLibraryGuideOption {
    val stableId: String

    data class ExactRecipe(
        val recipe: BuiltInP1RecipeDefinition,
    ) : LearningLibraryGuideOption {
        override val stableId: String = recipe.id.value
    }

    data class Standalone(
        override val stableId: String,
        @param:StringRes val labelRes: Int,
    ) : LearningLibraryGuideOption
}

internal fun P1BrewerProfileSetupOption.toLearningLibraryProfile():
    LearningLibraryProfileOption = LearningLibraryProfileOption(
        profileId = profileId,
        displayName = displayName,
        methodFamilyName = methodFamilyName,
        guides = recipes.map { recipe -> LearningLibraryGuideOption.ExactRecipe(recipe) },
    )

/**
 * Timer-free entry point for exact, illustrated brewing knowledge.
 *
 * The library deliberately exposes only brewer and recipe selection. Equipment
 * setup and live-session controls remain in their respective brewing flows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LearningLibraryScreen(
    profiles: List<LearningLibraryProfileOption>,
    availability: LearningLibraryAvailability,
    onOpenGuide: (LearningLibraryProfileOption, LearningLibraryGuideOption) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileGroups = profiles.groupBy { profile -> brewerMethodGroup(profile.profileId) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_learning_title),
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = LearningContentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (availability != LearningLibraryAvailability.AVAILABLE || profiles.isEmpty()) {
                    item(key = "learning_availability") {
                        LearningAvailabilityMessage()
                    }
                } else {
                    profileGroups.forEach { (group, groupedProfiles) ->
                        item(key = "method_${group.name}") {
                            LearningMethodSectionHeader(profile = groupedProfiles.first())
                        }
                        items(
                            items = groupedProfiles,
                            key = { profile -> profile.profileId.value },
                        ) { profile ->
                            LearningProfileCard(
                                profile = profile,
                                onOpenGuide = { guide -> onOpenGuide(profile, guide) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningMethodSectionHeader(profile: LearningLibraryProfileOption) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = localizedMethodFamilyName(
                profileId = profile.profileId,
                fallback = profile.methodFamilyName,
            ),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun LearningAvailabilityMessage() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.msg_brew_guidance_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LearningProfileCard(
    profile: LearningLibraryProfileOption,
    onOpenGuide: (LearningLibraryGuideOption) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrewerProfileIconBadge(profile = profile)
                Text(
                    text = localizedProfileName(
                        profileId = profile.profileId,
                        fallback = profile.displayName,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            profile.guides.forEach { guide ->
                LearningRecipeRow(
                    guide = guide,
                    onClick = { onOpenGuide(guide) },
                )
            }
        }
    }
}

@Composable
private fun BrewerProfileIconBadge(profile: LearningLibraryProfileOption) {
    val kind = brewerProfileIconKind(profile.profileId)
    val containerColor = when (kind) {
        BrewerProfileIconKind.CONE,
        BrewerProfileIconKind.WEDGE,
        BrewerProfileIconKind.SINGLE_CUP_MACHINE,
        -> MaterialTheme.colorScheme.primaryContainer
        BrewerProfileIconKind.FLAT_BOTTOM,
        BrewerProfileIconKind.CARAFE,
        BrewerProfileIconKind.PHIN,
        -> MaterialTheme.colorScheme.secondaryContainer
        BrewerProfileIconKind.IMMERSION,
        BrewerProfileIconKind.CEZVE,
        BrewerProfileIconKind.BATCH_MACHINE,
        BrewerProfileIconKind.PULSAR,
        -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when (kind) {
        BrewerProfileIconKind.CONE,
        BrewerProfileIconKind.WEDGE,
        BrewerProfileIconKind.SINGLE_CUP_MACHINE,
        -> MaterialTheme.colorScheme.onPrimaryContainer
        BrewerProfileIconKind.FLAT_BOTTOM,
        BrewerProfileIconKind.CARAFE,
        BrewerProfileIconKind.PHIN,
        -> MaterialTheme.colorScheme.onSecondaryContainer
        BrewerProfileIconKind.IMMERSION,
        BrewerProfileIconKind.CEZVE,
        BrewerProfileIconKind.BATCH_MACHINE,
        BrewerProfileIconKind.PULSAR,
        -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = Modifier.size(68.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            BrewerProfileIcon(
                profileId = profile.profileId,
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

@Composable
private fun LearningRecipeRow(
    guide: LearningLibraryGuideOption,
    onClick: () -> Unit,
) {
    val guideName = when (guide) {
        is LearningLibraryGuideOption.ExactRecipe -> exactRecipeName(guide.recipe.id)
        is LearningLibraryGuideOption.Standalone -> stringResource(guide.labelRes)
    }
    val label = splitLearningRecipeLabel(guideName)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(5.dp)
                    .height(38.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = label.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                label.details?.let { details ->
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
