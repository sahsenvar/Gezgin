package dev.gezgin.core

/**
 * The result returned from a result-producing destination (`ResultRoute<T>`/`ResultFlow<T>`, §6).
 * The suspend `goToXForResult()` or the `xResults` stream yields this; the consumer matches on
 * [Value]/[Canceled].
 *
 * @author @sahsenvar
 */
public sealed interface NavResult<out T> {
  /**
   * The result value delivered by the target's `backWithResult(value)`/`quitWith(value)`.
   *
   * @property value the value returned to the caller
   */
  public data class Value<T>(val value: T) : NavResult<T>

  /**
   * The target closed without a result (back/quit, or a pending slot dropped after process death).
   */
  public data object Canceled : NavResult<Nothing>
}
