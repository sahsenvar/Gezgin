package dev.gezgin.core

/**
 * Sonuç-döndüren bir hedeften (`ResultRoute<T>`/`ResultFlow<T>`) dönen sonuç (§6). Suspend
 * `goToXForResult()` ya da `xResults` stream'i bunu verir; tüketici [Value] / [Canceled] üzerinde eşleşir.
 */
sealed interface NavResult<out T> {
    /** Hedefin `backWithResult(value)`/`quitWith(value)` ile teslim ettiği sonuç değeri. */
    data class Value<T>(val value: T) : NavResult<T>

    /** Hedef sonuç vermeden kapandı (geri/quit ya da PD sonrası bekleyen slotun düşmesi). */
    data object Canceled : NavResult<Nothing>
}
