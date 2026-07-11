package dev.gezgin.core.compose

// Bilinçli no-op: bu default-arg pozisyonu (Composable-DIŞI) LocalContext/Activity'ye erişemez —
// Activity'si olan çağıran kendi `onRootBack`'ini açıkça geçmeli.
internal actual fun platformDefaultRootBack(): () -> Unit = {}
