package dev.gezgin.core.compose

// TODO(Faz 3.6, Shopr sample): Android'de anlamlı bir varsayılan tipik olarak host Activity'nin
// finish()'idir — bu, LocalContext üzerinden Activity'ye erişim gerektirir ve androidApp
// sample'ında belgelenecektir. Bu görevde (3.2) yalnız no-op sağlanıyor.
actual fun platformDefaultRootBack(): () -> Unit = {}
