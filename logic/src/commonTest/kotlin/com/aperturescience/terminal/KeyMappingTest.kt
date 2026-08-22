package com.aperturescience.terminal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyMappingTest {
    @Test
    fun `letters digits space question mark and period are accepted`() {
        for (c in listOf('A', 'Z', 'a', 'z', '0', '9', ' ', '?', '.')) {
            assertTrue(isAcceptedChar(c), "expected '$c' to be accepted")
        }
    }

    @Test
    fun `other punctuation is rejected`() {
        for (c in listOf(',', '!', '-', '_', '/', '\\', ':', ';', '@')) {
            assertFalse(isAcceptedChar(c), "expected '$c' to be rejected")
        }
    }
}
