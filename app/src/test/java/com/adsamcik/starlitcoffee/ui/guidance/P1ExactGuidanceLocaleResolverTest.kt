package com.adsamcik.starlitcoffee.ui.guidance

import org.junit.Assert.assertEquals
import org.junit.Test

class P1ExactGuidanceLocaleResolverTest {

    @Test
    fun `unsupported device locale resolves to reviewed English fallback`() {
        assertEquals(
            "en",
            P1ExactGuidanceLocaleResolver.resolve(listOf("ja-JP")),
        )
    }

    @Test
    fun `first supported preference wins after unsupported languages`() {
        assertEquals(
            "de",
            P1ExactGuidanceLocaleResolver.resolve(listOf("ja-JP", "de-AT", "en-US")),
        )
    }

    @Test
    fun `regional preference resolves to its packaged language resource`() {
        assertEquals(
            "fr",
            P1ExactGuidanceLocaleResolver.resolve(listOf("fr-CA")),
        )
    }

    @Test
    fun `empty preference list resolves to reviewed English fallback`() {
        assertEquals(
            "en",
            P1ExactGuidanceLocaleResolver.resolve(emptyList()),
        )
    }
}
