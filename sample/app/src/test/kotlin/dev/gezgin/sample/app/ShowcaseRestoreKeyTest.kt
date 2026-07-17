package dev.gezgin.sample.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ShowcaseRestoreKeyTest {
    @Test
    fun `showcase host uses one explicit stable restore namespace`() {
        assertEquals("sample-showcase", SHOWCASE_RESTORE_KEY)
    }
}
