package dev.gezgin.core

import kotlin.reflect.KClass

internal class GezginState(
  initial: List<GezginKey>,
  internal var nextId: Long,
  private val topology: GezginTopology,
) {
  private val _stack = initial.toMutableList()
  val stack: List<GezginKey>
    get() = _stack

  fun push(route: Route, enterFlow: Boolean = false, singleTop: Boolean = true): GezginKey? {
    val top = _stack.lastOrNull()
    if (singleTop && top?.route == route) return null
    val target = topology.flowChain(route::class)
    val source = top?.let { topology.flowChain(it.route::class) } ?: emptyList()
    var common = target.zip(source).takeWhile { (a, b) -> a.id == b.id }.count()
    if (enterFlow && target.isNotEmpty()) common = minOf(common, target.size - 1)
    val flowPath =
      (top?.flowPath ?: emptyList()).take(common) +
        List(target.size - common) { nextId++ } // her yeni flow segmenti taze id
    return GezginKey(route, nextId++, flowPath).also { _stack += it }
  }

  fun pop(): GezginKey? {
    if (_stack.size <= 1) return null
    return _stack.removeAt(_stack.lastIndex)
  }

  fun replaceUpTo(
    route: Route,
    clearUpTo: KClass<out Route>?,
    inclusive: Boolean,
    enterFlow: Boolean = false,
  ): GezginKey {
    val cutFrom = cutIndex(clearUpTo, inclusive)
    while (_stack.size > cutFrom) _stack.removeAt(_stack.lastIndex)
    return push(
      route,
      enterFlow = enterFlow,
      singleTop = false,
    )!! // !! güvenli: singleTop=false → push null dönemez
  }

  /**
   * M4 — `replaceUpTo`'nun MUTASYON YAPMADAN sonuçtaki kök (dip) route'unu hesaplar: temizleme tüm
   * stack'i kaldıracaksa (`cutFrom == 0`) yeni kök `route` olur, aksi halde mevcut dip korunur.
   * [RawNavigator.replaceTo] bunu modal-kind-at-root reddi için state'i değiştirmeden önce
   * kullanır.
   */
  fun resultingRootAfterReplace(
    route: Route,
    clearUpTo: KClass<out Route>?,
    inclusive: Boolean,
  ): Route = if (cutIndex(clearUpTo, inclusive) == 0) route else _stack.first().route

  /**
   * `clearUpTo` hedefi stack'te var mı — [RawNavigator.replaceTo]'nun MUTASYON/`require` YAPMADAN,
   * güvenli no-op kararı için sorduğu ön-koşul (null hedef = "yalnız top'u değiştir", daima
   * geçerli). [cutIndex]'in `require(i >= 0)`'ına düşmeden önce bu döner: hedef yoksa çağıran
   * ReplaceToTargetMissing yayıp erken çıkar.
   */
  fun hasOnStack(clearUpTo: KClass<out Route>?): Boolean =
    clearUpTo == null || _stack.any { clearUpTo.isInstance(it.route) }

  /**
   * `replaceUpTo`/`resultingRootAfterReplace` ortak kesme-indeksi: dip=0'a kadar korunacak entry
   * sayısı.
   */
  private fun cutIndex(clearUpTo: KClass<out Route>?, inclusive: Boolean): Int =
    if (clearUpTo == null) _stack.lastIndex
    else {
      val i = _stack.indexOfLast { clearUpTo.isInstance(it.route) } // nearest-ancestor (§4.2/M3)
      require(i >= 0) { "clearUpTo target is not on the stack: ${clearUpTo.simpleName}" }
      if (inclusive) i else i + 1
    }

  fun backTo(target: KClass<out Route>, inclusive: Boolean): List<GezginKey>? {
    val i = _stack.dropLast(1).indexOfLast { target.isInstance(it.route) } // top hariç ara
    if (i < 0) return null
    val keepUntil =
      maxOf(
        if (inclusive) i else i + 1,
        1,
      ) // §8.1 empty-stack invariant: dip entry asla poplanmaz (inclusive dipte exclusive'e düşer)
    val removed = _stack.subList(keepUntil, _stack.size).toList()
    while (_stack.size > keepUntil) _stack.removeAt(_stack.lastIndex)
    return removed
  }

  fun currentFlowId(): Long? = _stack.lastOrNull()?.flowPath?.lastOrNull()

  fun quitFlow(flowInstanceId: Long): List<GezginKey>? {
    val top = _stack.lastOrNull() ?: return null
    if (flowInstanceId !in top.flowPath)
      return null // quit yalnız içinde bulunulan flow için — orta-stack pop yok
    val first = _stack.indexOfFirst { flowInstanceId in it.flowPath }
    if (first <= 0) return null // dipte (root flow) → üst katman onRootBack'e çevirir
    // Invariant (push'un miras kuralından): id'yi taşıyan entry'ler daima bitişik bir blok
    // oluşturur
    // ve top-guard sayesinde bu blok stack'in tepesinde biter → filter+removeAll = tepeden atomik
    // pop.
    val removed = _stack.filter { flowInstanceId in it.flowPath }
    _stack.removeAll { flowInstanceId in it.flowPath }
    return removed
  }
}
