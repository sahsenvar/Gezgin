package dev.gezgin.processor.model

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

internal const val NAV_GRAPH_FQ = "dev.gezgin.core.annotation.NavGraph"
internal const val FLOW_GRAPH_FQ = "dev.gezgin.core.annotation.FlowGraph"

/**
 * Which annotated graph a declaration belongs to: the DIRECT annotated supertype is primary, the
 * lexically enclosing annotated graph the fallback. Shared by [ModelReader] and the wrapper
 * route-metadata reader so both attribute membership identically.
 */
internal class GraphMembership {
  /** Per-instance memoization; the chain walks would otherwise re-resolve supertypes. */
  private val parentCache = HashMap<String, KSClassDeclaration?>()

  fun membershipParent(decl: KSClassDeclaration): KSClassDeclaration? {
    val key = decl.qualifiedName?.asString() ?: return computeMembershipParent(decl)
    if (parentCache.containsKey(key)) return parentCache[key]
    return computeMembershipParent(decl).also { parentCache[key] = it }
  }

  private fun computeMembershipParent(decl: KSClassDeclaration): KSClassDeclaration? {
    val directAnnotated = directAnnotatedGraphSupertypes(decl)
    val lexParent =
      (decl.parentDeclaration as? KSClassDeclaration)?.takeIf { it.isAnnotatedGraph() }
    val lexFq = lexParent?.qualifiedName?.asString()
    return when {
      lexParent != null && directAnnotated.any { it.qualifiedName?.asString() == lexFq } ->
        lexParent
      directAnnotated.isNotEmpty() -> directAnnotated.first()
      lexParent != null -> lexParent
      else -> null
    }
  }

  /** Enclosing annotated graphs from outermost to innermost, excluding `decl` itself. */
  fun enclosingGraphChain(decl: KSClassDeclaration): List<KSClassDeclaration> {
    val chain = mutableListOf<KSClassDeclaration>()
    val seen = mutableSetOf<String>()
    var cur = membershipParent(decl)
    while (cur != null) {
      val fq = cur.qualifiedName?.asString()
      if (fq != null && !seen.add(fq)) break
      chain.add(0, cur)
      cur = membershipParent(cur)
    }
    return chain
  }

  fun directAnnotatedGraphSupertypes(decl: KSClassDeclaration): List<KSClassDeclaration> =
    decl.superTypes
      .map { it.resolve().declaration }
      .filterIsInstance<KSClassDeclaration>()
      .filter { it.isAnnotatedGraph() }
      .distinctBy { it.qualifiedName?.asString() }
      .toList()
}

internal fun KSClassDeclaration.isAnnotatedGraph(): Boolean =
  hasGezginAnnotation(NAV_GRAPH_FQ) || hasGezginAnnotation(FLOW_GRAPH_FQ)

internal fun KSAnnotated.hasGezginAnnotation(fq: String): Boolean =
  annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == fq }
