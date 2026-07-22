package dev.gezgin.core.compose

/**
 * `rememberNavigator`'ın `onRootBack` varsayılanı (§8, §12) — platform başına expect/actual. Task
 * 3.2 kapsamında HER iki actual da no-op'tur: gerçek davranış (ör. Android'de host
 * `Activity.finish()`) yalnız kullanıcı kodunun bağlamı olduğu bir yerde (LocalContext/Activity)
 * anlamlıdır — bu, Faz 3.6 Shopr sample'ında (androidApp) belgelenecek (TODO). Şimdilik kök
 * `back()` hiçbir şey yapmadan yutulur (N5, §8.1 empty-stack invariant'ı ihlal edilmez — yalnız
 * dışa sinyal verilmez).
 */
internal expect fun platformDefaultRootBack(): () -> Unit
