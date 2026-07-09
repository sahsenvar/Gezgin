package dev.gezgin.processor.codegen

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.RouteModel

/**
 * "Bu route bir `NavigatorCodegen` `xNavigator` factory'si KAZANIYOR mu?" kararının TEK kaynağı —
 * fragment (`FS5`), core-mode (`SC2`) ve MVI-mode (`MV7`) nav-wiring guard'larının üçü de buraya çağırır
 * (drift yok). İki dal:
 *
 * - **SAME-module route** ([routeModel] != null): navigator BU KSP turunda üretilecek, henüz classpath'te
 *   YOK → kararı bellekteki [GraphModel] üzerinden [NavigatorCodegen.hasNavigator] verir (probe kullanılamaz).
 * - **CROSS-module route** ([routeModel] == null, başka modülde derlenmiş): navigator, route kazandıysa,
 *   classpath'te ZATEN derlenmiş bir `XNavigator` sınıfıdır → [GezginNavigatorFor] damgası ile KİMLİK
 *   doğrulanarak probe edilir. Eski `?: true` kör iyimserliği (nav-wanting VM/effect/fragment'ı navigator'sız
 *   cross-module route'ta üretilen kodda `raw.xNavigator()` unresolved reference'ına götürürdü — `FS5`'in
 *   öldürmek için var olduğu hata) bununla değişti.
 *
 * **Kimlik, ad DEĞİL (FS5/M1).** `x` türetimi çakışabilir (`HelpRoute`/`HelpScreenRoute` → `x=Help` →
 * ikisi de `HelpNavigator` adıyla eşleşir). Ada bakan bir probe display-only bir route'a YABANCI bir
 * route'un navigator'ını sessizce bağlardı. [probeCompiledNavigator] sınıfı ADIYLA bulur ama
 * [GezginNavigatorFor.route]'u entry'nin [routeFq]'siyle karşılaştırır → yalnız KİMLİK eşleşince kabul.
 *
 * **Paket sözleşmesi (M2).** Probe navigator'ı [routePackageName] içinde arar; navigator'lar
 * `TopologyCodegen.targetPackage` (tüm graph/route'ların ortak öneki) altında üretilir. Bu iki paketin
 * DAİMA eşit olması `GezginProcessor`'ın `[PKG]` denetimiyle (her route/graph paketi == targetPackage)
 * garanti altındadır — aksi halde çok-alt-paketli bir nav modülü navigator'ı route'un paketi DIŞINDA üretir
 * ve probe onu ıskalardı (false negative). `[PKG]` bu düzeni nav modülünün KENDİ derlemesinde reddeder.
 *
 * **İzleme — incremental derleme kör noktası (Integ m2).** Probe sonucu üretilen entry dosyasına SABİTLENİR.
 * Nav modülündeki bir route sonradan SON edge'ini kaybederse (navigator sınıfı yok olur) veya kimlik damgası
 * değişirse, feature modülünün doğruluğu KSP'nin classpath-ABI değişiminde o modülü yeniden işlemesine
 * bağlıdır. Aralıklı KSP izolasyonunda bu tetiklenmeyebilir → nav modülünde edge topolojisi değişince feature
 * modüllerinde TEMİZ yeniden-derleme (clean build) gerekir. Bkz. `docs/gezgin-on-device-checklist.md`.
 */
object NavigatorProbe {

    const val MARKER_FQ = "dev.gezgin.core.annotation.GezginNavigatorFor"

    fun routeEarnsNavigator(
        resolver: Resolver,
        routeModel: RouteModel?,
        graphsByFq: Map<String, GraphModelNode>,
        routePackageName: String,
        x: String,
        routeFq: String,
    ): Boolean =
        if (routeModel != null) {
            NavigatorCodegen.hasNavigator(routeModel, graphsByFq)
        } else {
            probeCompiledNavigator(resolver, routePackageName, x, routeFq)
        }

    /**
     * `${routePackageName}.${x}Navigator` classpath'te var VE [GezginNavigatorFor] damgası [routeFq]'yi
     * gösteriyorsa `true`. Sınıf yoksa (edge'siz route → hiç navigator yok) ya da damga başka bir route'u
     * gösteriyorsa (ada-çakışan decoy) `false`.
     */
    private fun probeCompiledNavigator(
        resolver: Resolver,
        routePackageName: String,
        x: String,
        routeFq: String,
    ): Boolean {
        val navClass = resolver.getClassDeclarationByName("$routePackageName.${x}Navigator") ?: return false
        val markedRouteFq = navClass.annotations
            .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == MARKER_FQ }
            ?.arguments?.firstOrNull { it.name?.asString() == "route" }
            ?.let { it.value as? KSType }
            ?.declaration?.qualifiedName?.asString()
        return markedRouteFq == routeFq
    }
}
