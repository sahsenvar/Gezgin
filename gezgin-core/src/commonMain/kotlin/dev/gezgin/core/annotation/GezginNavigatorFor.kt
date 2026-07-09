package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * İÇ CODEGEN SÖZLEŞMESİ (elle KULLANMA). `NavigatorCodegen`, ürettiği her `XNavigator` sınıfını KÖKEN
 * route'unun [KClass]'ıyla damgalar — sınıf ADI değil, KİMLİĞİ taşınsın diye.
 *
 * Neden: cross-module nav-wiring PROBE'u (`NavigatorProbe`) bir route'un navigator'ı olup olmadığını
 * classpath'te `getClassDeclarationByName("${routePkg}.${x}Navigator")` ile arar. `x` türetimi (`stripSuffix`)
 * çakışabilir — `HelpRoute` ve `HelpScreenRoute` ikisi de `x=Help` → `HelpNavigator` ADIYLA eşleşir. Ada
 * bakan bir probe, display-only bir route'a YABANCI bir route'un navigator'ını sessizce bağlardı (derlenir,
 * cast tutar, yanlış edge'lerle gezinilir). Probe sınıfı bulduğunda [route]'u entry'nin routeFq'siyle
 * karşılaştırır → yalnız KİMLİK eşleşince nav bağlanır.
 *
 * Retention açıkça belirtilmez (kardeş anotasyonlar gibi varsayılan) → cross-module KSP okuması için
 * damganın derlenmiş classpath declaration'ında görünür kalması şart.
 */
@Target(AnnotationTarget.CLASS)
annotation class GezginNavigatorFor(val route: KClass<out Route>)
