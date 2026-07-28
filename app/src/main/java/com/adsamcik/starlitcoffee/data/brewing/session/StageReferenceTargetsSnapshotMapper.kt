package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassTarget
import com.adsamcik.starlitcoffee.domain.brewing.session.StageReferenceTargets
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTemperatureTarget
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeTarget

/** Owns the persisted representation of source reference cues. */
internal object StageReferenceTargetsSnapshotMapper {

    fun toSnapshot(targets: StageReferenceTargets): StageReferenceTargetsSnapshotV1 =
        StageReferenceTargetsSnapshotV1(
            timeTargets = targets.timeTargets.map { target ->
                StageTimeTargetSnapshotV1(
                    id = target.id.value,
                    reference = target.reference.name,
                    qualifier = target.qualifier.name,
                    minimumMillis = target.minimumMillis,
                    maximumMillis = target.maximumMillis,
                )
            },
            massTargets = targets.massTargets.map { target ->
                StageMassTargetSnapshotV1(
                    id = target.id.value,
                    role = target.role.name,
                    reference = target.reference.name,
                    qualifier = target.qualifier.name,
                    minimumGrams = target.minimumGrams,
                    maximumGrams = target.maximumGrams,
                )
            },
            temperatureTarget = targets.temperatureTarget?.let { target ->
                StageTemperatureTargetSnapshotV1(
                    qualifier = target.qualifier.name,
                    minimumC = target.minimumC,
                    maximumC = target.maximumC,
                )
            },
        )

    fun toDomain(snapshot: StageReferenceTargetsSnapshotV1): StageReferenceTargets =
        StageReferenceTargets(
            timeTargets = snapshot.timeTargets.mapIndexed { index, target ->
                StageTimeTarget(
                    id = StageTargetId(
                        target.id ?: listOf(target.reference.lowercase(), index + 1)
                            .joinToString(separator = "_"),
                    ),
                    reference = enumValue(target.reference, "time reference"),
                    qualifier = enumValue(target.qualifier, "target qualifier"),
                    minimumMillis = target.minimumMillis,
                    maximumMillis = target.maximumMillis,
                )
            },
            massTargets = snapshot.massTargets.mapIndexed { index, target ->
                target.toDomain(index)
            },
            temperatureTarget = snapshot.temperatureTarget?.let { target ->
                StageTemperatureTarget(
                    qualifier = enumValue(target.qualifier, "target qualifier"),
                    minimumC = target.minimumC,
                    maximumC = target.maximumC,
                )
            },
        )

    private fun StageMassTargetSnapshotV1.toDomain(index: Int): StageMassTarget {
        val semantics = when {
            role != null && reference != null -> Pair(
                enumValue<QuantityRole>(role, "mass-target role"),
                enumValue<StageMassReference>(reference, "mass-target reference"),
            )

            role == null && reference == null -> when (kind) {
                "ADDED_WATER" -> Pair(
                    QuantityRole.BREW_WATER_INPUT,
                    StageMassReference.STAGE_ADDED,
                )

                "CUMULATIVE_WATER" -> Pair(
                    QuantityRole.BREW_WATER_INPUT,
                    StageMassReference.BREW_CUMULATIVE,
                )

                "BEVERAGE_YIELD" -> Pair(
                    QuantityRole.BEVERAGE_YIELD,
                    StageMassReference.RECIPE_TOTAL,
                )

                else -> throw IllegalArgumentException("Unknown legacy mass-target kind: $kind")
            }

            else -> throw IllegalArgumentException(
                "Mass-target role and reference must either both be present or both be absent",
            )
        }
        val fallbackId = listOf(semantics.first.name, semantics.second.name, index + 1)
            .joinToString(separator = "_")
            .lowercase()
        return StageMassTarget(
            id = StageTargetId(id ?: fallbackId),
            role = semantics.first,
            reference = semantics.second,
            qualifier = enumValue(qualifier, "target qualifier"),
            minimumGrams = minimumGrams,
            maximumGrams = maximumGrams,
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String, label: String): T = try {
        enumValueOf(raw)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Unknown $label: $raw", error)
    }
}
