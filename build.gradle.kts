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

// Faz 9.1 — ABI doğrulaması YALNIZ yayınlanan 3 modül için (core/mvi/processor). Yayınlanmayan her şey
// (gezgin-test + tüm sample/* modülleri) hariç tutulur → onlar için .api dosyası tutulmaz/kontrol edilmez.
apiValidation {
    // Faz 9.3 (K4) — @GezginInternalApi ile işaretli forced-public semboller (codegen/gezgin-test kancaları)
    // kilitli ABI yüzeyinden düşürülür → alpha01 sonrası deprecation döngüsü olmadan evrilebilirler.
    nonPublicMarkers += "dev.gezgin.core.GezginInternalApi"
    ignoredProjects += listOf(
        "gezgin-test",
        "shopr", "navigation", "app",
        "auth", "home", "profile",
    )
}
