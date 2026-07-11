package dev.gezgin.core

import dev.gezgin.core.Self
import dev.gezgin.core.annotation.BottomSheet
import dev.gezgin.core.annotation.Dialog
import dev.gezgin.core.annotation.FullscreenModal
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.ReplaceTo
import dev.gezgin.core.annotation.NavGraph
import dev.gezgin.core.annotation.Screen
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class AnnotationsSmokeTest {

	sealed interface TestGraph : Route {
		@NavGraph
		@GoTo(ScreenA::class, singleTop = true)
		@GoTo(ScreenB::class, singleTop = false) // Repeatable proof
		data object Screen1 : TestGraph

		@ReplaceTo(target = ScreenC::class) // Using Self::class default implicitly
		data object Screen2 : TestGraph

		@ReplaceTo(target = ScreenD::class, clearUpTo = Screen1::class, inclusive = true)
		data object Screen3 : TestGraph
	}

	sealed interface ScreenA : Route
	data object ScreenAImpl : ScreenA

	sealed interface ScreenB : Route
	data object ScreenBImpl : ScreenB

	sealed interface ScreenC : Route
	data object ScreenCImpl : ScreenC

	sealed interface ScreenD : Route
	data object ScreenDImpl : ScreenD

	// Kind annotation'lari (§3.2) — FUNCTION target smoke: route zorunlu-açık (sentinel kaldırıldı);
	// positional ve named arg formları + açık route param'i, dördü de derlenir.
	@Screen(ScreenA::class)
	fun screenFun(route: ScreenA) = Unit

	@Screen(route = ScreenA::class)
	fun screenFunExplicit(route: ScreenA) = Unit

	@Dialog(ScreenB::class)
	fun dialogFun(route: ScreenB) = Unit

	@BottomSheet(ScreenC::class)
	fun bottomSheetFun(route: ScreenC) = Unit

	@FullscreenModal(ScreenD::class)
	fun fullscreenModalFun(route: ScreenD) = Unit

	@Test
	fun kindAnnotationsSmokeTest() {
		screenFun(ScreenAImpl)
		screenFunExplicit(ScreenAImpl)
		dialogFun(ScreenBImpl)
		bottomSheetFun(ScreenCImpl)
		fullscreenModalFun(ScreenDImpl)
	}

	@Test
	fun annotationsSmokeTest() {
		// Runtime assertion: Self is a Route
		assertTrue(Self is Route)

		// Runtime assertion: annotated classes exist
		assertNotNull(TestGraph.Screen1::class.simpleName)
		assertNotNull(TestGraph.Screen2::class.simpleName)
		assertNotNull(TestGraph.Screen3::class.simpleName)

		// Trivial list size assertion
		val annotatedElements = listOf(
			TestGraph.Screen1::class.simpleName,
			TestGraph.Screen2::class.simpleName,
			TestGraph.Screen3::class.simpleName
		)
		assertTrue(annotatedElements.size == 3)
	}
}
