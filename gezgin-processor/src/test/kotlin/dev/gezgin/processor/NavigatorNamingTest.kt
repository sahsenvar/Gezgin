package dev.gezgin.processor

import dev.gezgin.processor.codegen.NavigatorCodegen
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `NavigatorCodegen.navigatorX` (türetilmiş "X" adı) — `-ScreenRoute`/`-DialogRoute`/`-BottomSheetRoute`
 * konvansiyonunun üretilen adları DEĞİŞTİRMEDİĞİNİ (Screen/Flow bileşik olarak atılır, Dialog/BottomSheet
 * korunur) ve eski davranışın (tek sonek) regresyona uğramadığını kilitler.
 */
class NavigatorNamingTest {
    @Test
    fun `compound ScreenRoute strips to base name`() {
        assertEquals("Login", NavigatorCodegen.navigatorX("LoginScreenRoute"))
        assertEquals("Dashboard", NavigatorCodegen.navigatorX("DashboardScreenRoute"))
        assertEquals("Credentials", NavigatorCodegen.navigatorX("CredentialsScreenRoute"))
    }

    @Test
    fun `Dialog and BottomSheet kind tokens are retained after stripping Route`() {
        assertEquals("ForgotPasswordDialog", NavigatorCodegen.navigatorX("ForgotPasswordDialogRoute"))
        assertEquals("EditNameDialog", NavigatorCodegen.navigatorX("EditNameDialogRoute"))
        assertEquals("FilterBottomSheet", NavigatorCodegen.navigatorX("FilterBottomSheetRoute"))
    }

    @Test
    fun `Flow suffix still stripped, bare and legacy single-suffix names unchanged`() {
        assertEquals("SignUp", NavigatorCodegen.navigatorX("SignUpFlow"))
        assertEquals("Checkout", NavigatorCodegen.navigatorX("CheckoutFlow"))
        assertEquals("Detail", NavigatorCodegen.navigatorX("DetailRoute"))
        assertEquals("Feed", NavigatorCodegen.navigatorX("Feed"))
    }

    @Test
    fun `degenerate names equal to a single suffix are not emptied`() {
        assertEquals("Route", NavigatorCodegen.navigatorX("Route"))
        assertEquals("Screen", NavigatorCodegen.navigatorX("Screen"))
    }
}
