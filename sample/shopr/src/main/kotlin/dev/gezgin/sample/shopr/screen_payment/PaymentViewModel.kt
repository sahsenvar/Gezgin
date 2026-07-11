package dev.gezgin.sample.shopr.screen_payment

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.nav.OrderId
import dev.gezgin.sample.shopr.nav.PaymentNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(CheckoutFlow.Payment::class)
class PaymentViewModel(
    private val nav: PaymentNavigator,
) : ViewModel(), GezginMvi<PaymentUiState, PaymentIntent, PaymentEffect> {

    private val _uiState = MutableStateFlow(PaymentUiState())
    override val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<PaymentEffect>()
    override val effects: Flow<PaymentEffect> = _effects.flow

    init {
        _effects.send(PaymentEffect.ShowMessage("Ödeme tutarı: ${_uiState.value.amount}"))
    }

    override fun onIntent(intent: PaymentIntent) {
        when (intent) {
            // CheckoutFlow ResultFlow<OrderId> — sonuç ile flow'dan çıkılır.
            PaymentIntent.Pay -> nav.quitWith(OrderId(value = "ORD-1001"))
        }
    }
}
