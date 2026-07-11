@file:OptIn(ExperimentalTestApi::class, GezginInternalApi::class)

package dev.gezgin.core.compose

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.gezgin.core.GezginInternalApi
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GezginLocalsTest {

    @Test
    fun `LocalGezginEntryId outside entry content throws`() {
        val error = assertFailsWith<IllegalStateException> {
            runComposeUiTest {
                setContent { LocalGezginEntryId.current }
                waitForIdle()
            }
        }

        assertTrue(error.message?.contains("LocalGezginEntryId") == true, error.message)
    }

    @Test
    fun `LocalGezginRawNavigator outside entry content throws`() {
        val error = assertFailsWith<IllegalStateException> {
            runComposeUiTest {
                setContent { LocalGezginRawNavigator.current }
                waitForIdle()
            }
        }

        assertTrue(error.message?.contains("LocalGezginRawNavigator") == true, error.message)
    }

    @Test
    fun `LocalGezginSheetController outside bottom sheet content throws`() {
        val error = assertFailsWith<IllegalStateException> {
            runComposeUiTest {
                setContent { LocalGezginSheetController.current }
                waitForIdle()
            }
        }

        assertTrue(error.message?.contains("LocalGezginSheetController") == true, error.message)
    }
}
