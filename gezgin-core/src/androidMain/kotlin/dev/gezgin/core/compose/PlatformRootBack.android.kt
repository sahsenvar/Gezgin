package dev.gezgin.core.compose

// Faz 3.6 (Shopr sample) tamamlandı: sample/shopr'un `MainActivity`'si artık `rememberNavigator`'a
// açıkça `onRootBack = { finish() }` GEÇİYOR — bu yüzden bu platform DEFAULT'u (parametre atlanırsa
// devreye girer) sample'da hiç kullanılmıyor. `platformDefaultRootBack()` yine de bilinçli olarak
// no-op kalıyor: LocalContext/Activity'ye burada (Composable-DIŞI, `rememberNavigator`'ın default-arg
// pozisyonu) erişim yok — Activity'ye erişimi olan HER çağıran (bu sample gibi) kendi `onRootBack`'ini
// açıkça geçmeli; bu no-op yalnız "hiç geçilmezse sessizce hiçbir şey yapma" güvenli tabanı.
actual fun platformDefaultRootBack(): () -> Unit = {}
