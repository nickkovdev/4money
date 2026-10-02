package ua.com.radiokot.money.inbox.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PayeeNormalizerTest {

    @Test
    fun sebSampleTrailingDotIsRemoved() {
        assertEquals("deepseerwea", PayeeNormalizer.normalize("DEEPSEERWEA ."))
        assertEquals("DEEPSEERWEA", PayeeNormalizer.displayName("DEEPSEERWEA ."))
    }

    @Test
    fun whitespaceIsCollapsedAndNbspIsSpace() {
        assertEquals("cafe brivibas", PayeeNormalizer.normalize("  CAFE   BRIVIBAS  "))
    }

    @Test
    fun trailingTerminalIdsAndCityAreRemoved() {
        assertEquals("acme hyper", PayeeNormalizer.normalize("ACME HYPER 0123 RIGA"))
        assertEquals("kiosks", PayeeNormalizer.normalize("KIOSKS 45 LV"))
    }

    @Test
    fun singleTokenIsNeverRemoved() {
        assertEquals("shop24", PayeeNormalizer.normalize("SHOP24"))
        assertEquals("riga", PayeeNormalizer.normalize("RIGA"))
    }

    @Test
    fun decomposedDiacriticsAreComposed() {
        val decomposed = "KAFEJNĪCA" // I + combining macron
        assertEquals("kafejnīca", PayeeNormalizer.normalize(decomposed))
    }

    @Test
    fun patternKeepsDigits() {
        assertEquals("shop 24", PayeeNormalizer.normalizePattern(" Shop  24 "))
    }
}
