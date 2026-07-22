package dev.gezgin.compat.zad

import dev.gezgin.test.GezginTestNavigator

/** Compile-only proof that the published test artifact resolves in the test source set. */
internal fun acceptsPublishedGezginTestNavigator(navigator: GezginTestNavigator): Int =
  navigator.backStack.size
