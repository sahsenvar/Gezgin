plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Faz 9.1 — BCV yalnız KÖK'e uygulanır (apply false DEĞİL); alt-projelerin apiCheck/apiDump görevlerini
    // kendisi kurar ve `apiCheck`'i `check` yaşam-döngüsüne bağlar (varsayılan davranış).
    alias(libs.plugins.binary.compatibility.validator)
}

// F-MAJOR-2 — ABI doğrulaması artık yayınlanan/yayına-komşu 4 modül için (core/mvi/processor/test).
// gezgin-test bir POM iskeleti kazandı ve dış-benimseyene sunulabilir hale geldi → BCV kapsamına alındı
// (.api dump tutulur). Yalnız sample/* modülleri (gerçekten yayınlanmayan) hariç tutulur.
apiValidation {
    // Faz 9.3 (K4) — @GezginInternalApi ile işaretli forced-public semboller (codegen/gezgin-test kancaları)
    // kilitli ABI yüzeyinden düşürülür → alpha01 sonrası deprecation döngüsü olmadan evrilebilirler.
    nonPublicMarkers += "dev.gezgin.core.GezginInternalApi"
    ignoredProjects += listOf(
        "shopr", "navigation", "app", "domain",
        "auth", "home", "profile",
    )
}
