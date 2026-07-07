package dev.gezgin.core.compose

import androidx.compose.runtime.staticCompositionLocalOf
import dev.gezgin.core.RawNavigator

/**
 * Top-entry-drive sözleşmesi (Faz 2 final-review devri (b), §10.1/§12): bir entry'nin content'i
 * YALNIZ KENDİ `entryId`'siyle kurulmuş bir navigator görür — [GezginDisplay] her entry content'ini
 * [toNavEntry] içinde bu iki local ile sarar. Faz 3.4'ün üreteceği `provideXEntry` bunlardan tipli
 * (codegen'in `xNavigator()` uzantısı) navigator kuracak.
 *
 * `staticCompositionLocalOf` — değer entry başına stabil (entry recompose olduğunda değişmez,
 * yalnız entry değiştiğinde farklı bir Content() ağacı kurulur); dynamic'in read-tracking maliyetine
 * gerek yok.
 */
val LocalGezginEntryId = staticCompositionLocalOf<Long> {
    error("LocalGezginEntryId yalnız GezginDisplay'in kurduğu entry content'leri içinde okunabilir.")
}

val LocalGezginRawNavigator = staticCompositionLocalOf<RawNavigator> {
    error("LocalGezginRawNavigator yalnız GezginDisplay'in kurduğu entry content'leri içinde okunabilir.")
}
