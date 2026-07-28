package com.adsamcik.starlitcoffee.data.brewing.snapshot

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeDefinition

/**
 * Copies source-faithful P1 semantics into an immutable recipe snapshot.
 *
 * The live catalog is allowed to improve in a later app version, but a saved
 * recipe, active session, or completed log must continue to describe the
 * source record that was actually selected. This mapper is deliberately pure
 * and additive so legacy snapshots without source metadata remain readable.
 */
object BuiltInP1RecipeSnapshotMapper {

    fun enrich(
        snapshot: BrewRecipeSnapshotV1,
        definition: BuiltInP1RecipeDefinition,
    ): BrewRecipeSnapshotV1 {
        require(snapshot.methodFamilyId == definition.methodFamilyId.value) {
            "Snapshot method family does not match the built-in recipe"
        }
        require(snapshot.brewerProfileId == definition.brewerProfileId.value) {
            "Snapshot brewer profile does not match the built-in recipe"
        }
        require(snapshot.builtInRecipeId == null || snapshot.builtInRecipeId == definition.id.value) {
            "Snapshot already references a different built-in recipe"
        }

        return snapshot.copy(
            builtInRecipeId = definition.id.value,
            ratioSemantics = definition.ratios.map { ratio ->
                RatioSemanticsSnapshotV1(
                    numerator = ratio.definition.numerator.name,
                    denominator = ratio.definition.denominator.name,
                    ratioValue = ratio.ratioValue,
                    includedDenominatorRoles = ratio.includedDenominatorRoles
                        .map { role -> role.name }
                        .sorted(),
                )
            },
            temperatureSemantics = TemperatureSemanticsSnapshotV1(
                basis = definition.temperature.basis.name,
                minimumC = definition.temperature.minimumC,
                maximumC = definition.temperature.maximumC,
            ),
            expectedTimeSemantics = ExpectedTimeSemanticsSnapshotV1(
                basis = definition.expectedTime.basis.name,
                minimumSeconds = definition.expectedTime.minimumSeconds,
                maximumSeconds = definition.expectedTime.maximumSeconds,
            ),
            completionSemantics = definition.completion.name,
            sourceMetadata = RecipeSourceMetadataSnapshotV1(
                sourceSchemaVersion = BuiltInP1RecipeCatalog.SOURCE_SCHEMA_VERSION,
                sourceSha256 = BuiltInP1RecipeCatalog.SOURCE_SHA256,
                sourceReviewedOnIso8601 = definition.evidence.reviewedOn.toString(),
                sourceMethodFamilyId = definition.sourceMethodFamilyId,
                sourceBrewerProfileId = definition.sourceBrewerProfileId.value,
                exactRecipeApproachId = definition.exactRecipeApproachId.value,
                evidenceClass = definition.evidence.evidenceClass.name,
                confidence = definition.evidence.confidence.name,
                sourceIds = definition.evidence.sourceIds.sorted(),
                unresolvedFields = definition.unresolvedFields.sorted(),
                orderedStageCount = definition.orderedStageCount,
            ),
        )
    }
}
