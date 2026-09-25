package dev.gezgin.core

import dev.gezgin.core.annotation.OnDismiss
import dev.gezgin.core.annotation.Open
import kotlin.test.Test
import kotlin.test.assertEquals

class CallbackModalAnnotationsSmokeTest {

  data class ConfirmDialog(
    val id: String,
    val onConfirm: () -> Unit,
    @OnDismiss val onDismiss: () -> Unit,
  ) : Route

  data class PickSheet(val onSelect: (String) -> Unit) : Route

  @Open(ConfirmDialog::class, PickSheet::class)
  @Open(ConfirmDialog::class, name = "openConfirmAgain")
  data object Caller : Route

  @Test
  fun openAndOnDismissCompileOnTheirTargets() {
    assertEquals("ConfirmDialog", ConfirmDialog::class.simpleName)
  }
}
