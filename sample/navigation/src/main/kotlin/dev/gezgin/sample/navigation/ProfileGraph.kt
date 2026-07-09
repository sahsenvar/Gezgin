package dev.gezgin.sample.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import dev.gezgin.core.BottomSheetContract
import dev.gezgin.core.DialogContract
import dev.gezgin.core.ResultFlow
import dev.gezgin.core.ResultRoute
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.FlowGraph
import dev.gezgin.core.annotation.GoForResult
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.NavGraph
import dev.gezgin.core.annotation.ReplaceTo
import dev.gezgin.core.annotation.StartDestination
import dev.gezgin.core.compose.GezginTransition
import dev.gezgin.core.compose.transition
import kotlinx.serialization.Serializable

@Serializable
data class AvatarChoice(val uri: String)

@Serializable
enum class NotificationLevel { ALL, MENTIONS, NONE }

@NavGraph
@Serializable
sealed interface ProfileGraph : Route {

    override val transition: GezginTransition?
        get() = transition { forward { fadeIn() togetherWith fadeOut() } }

    @GoForResult(EditNameDialogRoute::class)                            // screen-mode (String)
    @GoTo(SettingsScreenRoute::class)
    @GoForResult(AvatarFlow::class, name = "pickAvatar")                // FLOW-mode result (AvatarChoice)
    @GoForResult(NotificationsSheetRoute::class, name = "pickNotifications")  // MVI @BottomSheet result
    @Serializable
    data object ProfileScreenRoute : ProfileGraph

    /** Route-level transition override (getter) — cascade level 3 vs. the graph default above. */
    @ReplaceTo(
        AuthGraph.LoginScreenRoute::class,
        clearUpTo = HomeGraph.DashboardScreenRoute::class,
        inclusive = true,
        name = "logout",
    )
    @Serializable
    data object SettingsScreenRoute : ProfileGraph {
        override val transition: GezginTransition?
            get() = transition { forward { slideInHorizontally() togetherWith slideOutHorizontally() } }
    }

    /**
     * Screen-mode result producer — real `@Dialog` overlay (Faz 4). `DialogContract`'ın KOŞULLU
     * desenini gösterir (§7/Contracts.kt, `ForgotPasswordDialogRoute`'daki SABİT desenin karşıtı):
     * `dismissOnClickOutside` route ctor param'ından (`current`) hesaplanır — mevcut isim BOŞSA (ilk
     * kayıt akışı varsayımı) dışarı-tık kapatmaz, kullanıcı bir isim girmeden çıkamaz; mevcut ismi
     * DÜZENLERKEN dışarı tık ile rahatça vazgeçilebilir. `dismissOnBackPress` varsayılan (`true`) her
     * durumda çalışır.
     */
    @Serializable
    data class EditNameDialogRoute(val current: String) : ProfileGraph, ResultRoute<String>, DialogContract {
        override val dismissOnClickOutside: Boolean get() = current.isNotBlank()
    }

    // MVI-mode @BottomSheet result producer (Integ M3) — @ViewModel/@BottomSheet-content/@ScreenEffect
    // triple lives in :feature:profile (per-module KSP matching, §10.1).
    @Serializable
    data class NotificationsSheetRoute(val current: NotificationLevel) :
        ProfileGraph, ResultRoute<NotificationLevel>, BottomSheetContract {
        override val skipPartiallyExpanded: Boolean get() = true
    }

    /** Flow that RETURNS a result (ResultFlow<AvatarChoice>) — members get quitWith(AvatarChoice). */
    @FlowGraph
    @Serializable
    sealed interface AvatarFlow : ProfileGraph, ResultFlow<AvatarChoice> {

        @StartDestination
        @GoTo(CropScreenRoute::class)
        @Serializable
        data object PickSourceScreenRoute : AvatarFlow

        @GoTo(ZoomFlow::class)                                          // @GoTo into a result-less nested flow
        @Serializable
        data class CropScreenRoute(val source: String) : AvatarFlow

        /**
         * NESTED @FlowGraph — chain [AvatarFlow, ZoomFlow]. Çıkış: flow-entry'de plain `back()` =
         * `quit()` semantiği (§8.1) — yalnız ZoomFlow'un kendi segmentini kapatır, AvatarFlow (ve
         * CropScreenRoute) açık kalır. `quitWith` ise SÖZLEŞME SAHİPLİĞİ üzerinden çözülür (spec §6):
         * ZoomFlow, `ResultFlow<T>`'u yalnız TRANSİTİF taşır (AvatarFlow'dan miras), kendi
         * sözleşmesini deklare etmez — bu yüzden nested içinden `quitWith(AvatarChoice(...))`
         * çağırmak en yakın DOĞRUDAN-deklare-eden atayı (AvatarFlow) bitirir: HEM ZoomFlow HEM
         * AvatarFlow segmenti yıkılır, Value doğrudan Profile'ın `pickAvatarResults`'ına teslim
         * edilir. ZoomScreenRoute'un kendi `quit()`u YOK (üyeler arasında `@Quit` annotasyonu yok).
         */
        @FlowGraph
        @Serializable
        sealed interface ZoomFlow : AvatarFlow {

            @StartDestination
            @Serializable
            data object ZoomScreenRoute : ZoomFlow
        }
    }
}
