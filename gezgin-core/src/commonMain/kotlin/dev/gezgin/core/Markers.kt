package dev.gezgin.core

/**
 * Sentinel Route marker for self-referential navigation operations (serialization-agnostic).
 *
 * @author @sahsenvar
 */
public object Self : Route

/**
 * Marker interface for routes that return a result of type [T].
 *
 * @author @sahsenvar
 */
public interface ResultRoute<T>

/**
 * Marker interface for flows that return a result of type [T].
 *
 * @author @sahsenvar
 */
public interface ResultFlow<T>
