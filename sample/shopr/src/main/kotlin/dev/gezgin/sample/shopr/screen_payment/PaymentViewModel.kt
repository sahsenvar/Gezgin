package dev.gezgin.sample.shopr.screen_payment

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.nav.OrderId
import dev.gezgin.sample.shopr.ui.BaseViewModel
import dev.gezgin.sample.shopr.ui.EffectSink
import dev.gezgin.sample.shopr.ui.ViewModelOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PaymentViewModel : BaseViewModel<PaymentUiState, PaymentIntent, PaymentEffect>() {

  private val _uiState = MutableStateFlow(PaymentUiState())
  override val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<PaymentEffect>()
  override val effects: Flow<PaymentEffect> = _effects.flow

  init {
    _effects.send(PaymentEffect.ShowMessage("Ödeme tutarı: ${_uiState.value.amount}"))
  }

  override fun onIntent(intent: PaymentIntent) {
    when (intent) {
      // CheckoutFlow ResultFlow<OrderId> — sonuç ile flow'dan çıkılır.
      PaymentIntent.Pay -> _effects.send(PaymentEffect.CompletePayment(OrderId(value = "ORD-1001")))
    }
  }
}

@ViewModelOf(CheckoutFlow.Payment::class)
@Composable
fun paymentViewModel(): PaymentViewModel = viewModel { PaymentViewModel() }
