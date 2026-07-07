package dev.gezgin.core.compose

// Desktop/JVM: kök geri no-op (pencere kapatma politikası host uygulamaya bırakılır — sample'da
// gerekirse ExitApplication ile override edilebilir).
actual fun platformDefaultRootBack(): () -> Unit = {}
