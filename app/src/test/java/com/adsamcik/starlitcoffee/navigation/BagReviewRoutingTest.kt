package com.adsamcik.starlitcoffee.navigation

import com.adsamcik.starlitcoffee.data.work.BagReviewContext
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.viewmodel.BagPhotoSessionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BagReviewRoutingTest {

    @Test
    fun `rescan review routes to existing bag update flow`() {
        assertEquals(
            BagReviewDestination.Rescan(targetBagId = 42L),
            bagReviewDestination(BagReviewContext.rescan(42L)),
        )
    }

    @Test
    fun `missing or add new context keeps inventory add flow`() {
        assertEquals(BagReviewDestination.AddNew, bagReviewDestination(null))
        assertEquals(
            BagReviewDestination.AddNew,
            bagReviewDestination(BagReviewContext.addNew()),
        )
    }

    @Test
    fun `cold start rescan builds inventory beneath review`() {
        val plan = bagReviewNavigationPlan(
            destination = BagReviewDestination.Rescan(targetBagId = 42L),
            requiresInventoryBackStack = true,
        )

        assertEquals(
            listOf(BagInventory, RescanBag(bagId = 42L)),
            plan.routes,
        )
    }

    @Test
    fun `new bag from rescan transfers draft to inventory receiver`() {
        val plan = bagReviewNavigationPlan(
            destination = BagReviewDestination.Rescan(targetBagId = 42L),
            requiresInventoryBackStack = true,
        )

        assertEquals(BagInventory, plan.newBagTransferDestination)
        assertEquals(BagInventory, plan.routes[plan.routes.lastIndex - 1])
    }

    @Test
    fun `ordinary in app rescan keeps existing back stack`() {
        val plan = bagReviewNavigationPlan(
            destination = BagReviewDestination.Rescan(targetBagId = 42L),
            requiresInventoryBackStack = false,
        )

        assertEquals(listOf(RescanBag(bagId = 42L)), plan.routes)
        assertEquals(BagInventory, plan.newBagTransferDestination)
    }

    @Test
    fun `queued rescan for the currently open bag suppresses duplicate navigation`() {
        assertTrue(
            shouldSuppressBagReviewNavigation(
                hasExplicitRequest = false,
                currentRescanBagId = 42L,
                destination = BagReviewDestination.Rescan(targetBagId = 42L),
            ),
        )
    }

    @Test
    fun `queued rescan for a different bag navigates to its target`() {
        assertFalse(
            shouldSuppressBagReviewNavigation(
                hasExplicitRequest = false,
                currentRescanBagId = 41L,
                destination = BagReviewDestination.Rescan(targetBagId = 42L),
            ),
        )
    }

    @Test
    fun `explicit rescan request is not suppressed even for the open bag`() {
        assertFalse(
            shouldSuppressBagReviewNavigation(
                hasExplicitRequest = true,
                currentRescanBagId = 42L,
                destination = BagReviewDestination.Rescan(targetBagId = 42L),
            ),
        )
    }

    @Test
    fun `cold start queue restoration builds inventory beneath rescan`() {
        val restoredReview = BagPhotoSessionResult(
            sessionId = "restored-session",
            result = BagPhotoProcessingResult(),
            reviewContext = BagReviewContext.rescan(42L),
            requiresInventoryBackStack = true,
        )

        val plan = bagReviewNavigationPlan(
            destination = bagReviewDestination(restoredReview.reviewContext),
            requiresInventoryBackStack = restoredReview.requiresInventoryBackStack,
        )

        assertEquals(listOf(BagInventory, RescanBag(42L)), plan.routes)
    }

    @Test
    fun `cold start waits for inventory before declaring rescan target missing`() {
        assertEquals(
            RescanTargetStatus.LOADING,
            rescanTargetStatus(
                inventoryLoaded = false,
                availableBagIds = emptyList(),
                targetBagId = 42L,
            ),
        )
        assertEquals(
            RescanTargetStatus.MISSING,
            rescanTargetStatus(
                inventoryLoaded = true,
                availableBagIds = emptyList(),
                targetBagId = 42L,
            ),
        )
        assertEquals(
            RescanTargetStatus.AVAILABLE,
            rescanTargetStatus(
                inventoryLoaded = true,
                availableBagIds = listOf(42L),
                targetBagId = 42L,
            ),
        )
    }
}
