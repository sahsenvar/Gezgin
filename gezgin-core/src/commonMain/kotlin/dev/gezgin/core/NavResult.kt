package dev.gezgin.core

sealed interface NavResult<out T> {
    data class Value<T>(val value: T) : NavResult<T>
    data object Canceled : NavResult<Nothing>
}
