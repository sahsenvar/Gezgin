@file:OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)

package dev.gezgin.core.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
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
    fun `LocalGezginSheetState outside bottom sheet content throws`() {
        val error = assertFailsWith<IllegalStateException> {
            runComposeUiTest {
                setContent { LocalGezginSheetState.current }
                waitForIdle()
            }
        }

        assertTrue(error.message?.contains("LocalGezginSheetState") == true, error.message)
    }
}
