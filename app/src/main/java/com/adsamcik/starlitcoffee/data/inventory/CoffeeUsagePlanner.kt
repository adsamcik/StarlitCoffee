package com.adsamcik.starlitcoffee.data.inventory

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.CoffeeBagStatus
import kotlin.math.abs

data class CoffeeUsagePlan(
    val previousBag: CoffeeBagEntity,
    val updatedBag: CoffeeBagEntity,
    val amountG: Float,
    val usedAt: Long,
)

enum class CoffeeUsageRejection {
    INVALID_AMOUNT,
    BAG_NOT_FOUND,
    BAG_FINISHED,
    EXCEEDS_REMAINING,
}

sealed interface CoffeeUsagePlanResult {
    data class Planned(val plan: CoffeeUsagePlan) : CoffeeUsagePlanResult

    data class Rejected(
        val reason: CoffeeUsageRejection,
        val remainingG: Float? = null,
    ) : CoffeeUsagePlanResult
}

/** Pure policy for a manual coffee-use entry and its matching inventory change. */
object CoffeeUsagePlanner {
    fun plan(
        bag: CoffeeBagEntity,
        amountG: Float,
        usedAt: Long,
    ): CoffeeUsagePlanResult {
        if (!amountG.isFinite() || amountG <= 0f) {
            return CoffeeUsagePlanResult.Rejected(CoffeeUsageRejection.INVALID_AMOUNT)
        }
        if (bag.status == CoffeeBagStatus.FINISHED.name) {
            return CoffeeUsagePlanResult.Rejected(CoffeeUsageRejection.BAG_FINISHED)
        }

        val remaining = bag.weightG
        if (remaining != null && amountG - remaining > WEIGHT_EPSILON_G) {
            return CoffeeUsagePlanResult.Rejected(
                reason = CoffeeUsageRejection.EXCEEDS_REMAINING,
                remainingG = remaining,
            )
        }

        var updated = bag
        if (bag.status == CoffeeBagStatus.SEALED.name) {
            updated = updated.copy(
                status = CoffeeBagStatus.OPEN.name,
                openedDate = usedAt,
            )
        }
        if (remaining != null) {
            val nextWeight = (remaining - amountG)
                .takeUnless { abs(it) <= WEIGHT_EPSILON_G }
                ?.coerceAtLeast(0f)
                ?: 0f
            updated = updated.copy(weightG = nextWeight)
            if (nextWeight == 0f && updated.status != CoffeeBagStatus.FINISHED.name) {
                updated = updated.copy(status = CoffeeBagStatus.FINISHED.name)
            }
        }

        return CoffeeUsagePlanResult.Planned(
            CoffeeUsagePlan(
                previousBag = bag,
                updatedBag = updated,
                amountG = amountG,
                usedAt = usedAt,
            ),
        )
    }

    private const val WEIGHT_EPSILON_G = 0.001f
}
