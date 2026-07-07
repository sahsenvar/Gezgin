package dev.gezgin.core
import kotlin.test.*

class NavResultTest {
    @Test fun valueCarriesPayload() {
        val r: NavResult<String> = NavResult.Value("adres")
        assertEquals("adres", (r as NavResult.Value).value)
    }
    @Test fun canceledIsSingleton() {
        val r: NavResult<Nothing> = NavResult.Canceled
        assertIs<NavResult.Canceled>(r)
    }
}
