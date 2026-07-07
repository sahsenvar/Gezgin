package dev.gezgin.core

import dev.gezgin.core.Self
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.ReplaceTo
import dev.gezgin.core.annotation.NavGraph
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class AnnotationsSmokeTest {

	sealed interface TestGraph : Route {
		@NavGraph
		@GoTo(target = ScreenA::class, singleTop = true)
		@GoTo(target = ScreenB::class, singleTop = false) // Repeatable proof
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
