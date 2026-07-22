package dev.gezgin.buildlogic.kdoc

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

data class KotlinSourceInput(val path: String, val content: String, val generated: Boolean = false)

enum class KDocExclusionReason {
  GENERATED,
  GEZGIN_INTERNAL_API,
  OVERRIDE,
}

data class PublicApiDeclaration(
  val path: String,
  val line: Int,
  val column: Int,
  val name: String,
  val kind: String,
  val topLevelType: Boolean,
  val excluded: Boolean,
  val exclusionReason: KDocExclusionReason?,
  val hasKDoc: Boolean,
  val hasAuthor: Boolean,
  val kDocText: String?,
)

enum class KDocFindingKind {
  MISSING_KDOC,
  MISSING_AUTHOR,
  NON_ENGLISH_KDOC,
  PROCESS_ARTIFACT_KDOC,
}

data class PublicApiKDocFinding(val declaration: PublicApiDeclaration, val kind: KDocFindingKind)

data class KotlinPublicApiScanResult(
  val declarations: List<PublicApiDeclaration>,
  val findings: List<PublicApiKDocFinding>,
) {
  val includedCount: Int
    get() = declarations.count { !it.excluded }

  val excludedCount: Int
    get() = declarations.count { it.excluded }
}

/**
 * Parses Kotlin into PSI and inventories effective-public declarations without relying on line
 * layout or modifier ordering. Only file declarations, public class members, and
 * primary-constructor properties are visited; local declarations and declarations hidden by a
 * non-public container are not consumer-visible.
 */
class KotlinPublicApiScanner : AutoCloseable {
  private val gezginInternalApiFqName = "dev.gezgin.core.GezginInternalApi"
  private val gezginInternalApiPackage = gezginInternalApiFqName.substringBeforeLast('.')
  private val gezginInternalApiShortName = gezginInternalApiFqName.substringAfterLast('.')
  private val disposable = Disposer.newDisposable("gezgin-public-api-kdoc-scanner")
  private val environment =
    KotlinCoreEnvironment.createForProduction(
      disposable,
      CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
      },
      EnvironmentConfigFiles.JVM_CONFIG_FILES,
    )
  private val psiFactory = KtPsiFactory(environment.project, markGenerated = false)

  override fun close() {
    Disposer.dispose(disposable)
  }

  fun scan(input: KotlinSourceInput): KotlinPublicApiScanResult {
    val file = psiFactory.createFile(input.path.substringAfterLast('/'), input.content)
    val declarations = buildList {
      file.declarations.forEach { declaration ->
        visit(
          declaration = declaration,
          file = file,
          input = input,
          inheritedExclusion = null,
          destination = this,
        )
      }
    }
    val findings =
      declarations.flatMap { declaration ->
        if (declaration.excluded) {
          emptyList()
        } else {
          buildList {
            if (!declaration.hasKDoc)
              add(PublicApiKDocFinding(declaration, KDocFindingKind.MISSING_KDOC))
            if (declaration.topLevelType && !declaration.hasAuthor) {
              add(PublicApiKDocFinding(declaration, KDocFindingKind.MISSING_AUTHOR))
            }
            if (declaration.kDocText?.containsTurkishKDoc() == true) {
              add(PublicApiKDocFinding(declaration, KDocFindingKind.NON_ENGLISH_KDOC))
            }
            if (declaration.kDocText?.containsProcessArtifact() == true) {
              add(PublicApiKDocFinding(declaration, KDocFindingKind.PROCESS_ARTIFACT_KDOC))
            }
          }
        }
      }
    return KotlinPublicApiScanResult(declarations = declarations, findings = findings)
  }

  private fun visit(
    declaration: KtDeclaration,
    file: KtFile,
    input: KotlinSourceInput,
    inheritedExclusion: KDocExclusionReason?,
    destination: MutableList<PublicApiDeclaration>,
  ) {
    if (!declaration.isEffectivelyPublic()) return

    val directExclusion =
      when {
        input.generated -> KDocExclusionReason.GENERATED
        inheritedExclusion != null -> inheritedExclusion
        declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) -> KDocExclusionReason.OVERRIDE
        declaration.hasDirectGezginInternalApi(file) -> KDocExclusionReason.GEZGIN_INTERNAL_API
        else -> null
      }
    if (declaration.isInventoryDeclaration()) {
      destination += declaration.toInventoryEntry(file, input, directExclusion)
    }

    if (declaration is KtClassOrObject) {
      declaration.primaryConstructorParameters.filter(KtParameter::hasValOrVar).forEach { parameter
        ->
        visit(parameter, file, input, directExclusion, destination)
      }
      declaration.declarations.forEach { member ->
        visit(member, file, input, directExclusion, destination)
      }
    }
  }

  private fun KtDeclaration.isEffectivelyPublic(): Boolean =
    !hasModifier(KtTokens.PRIVATE_KEYWORD) &&
      !hasModifier(KtTokens.PROTECTED_KEYWORD) &&
      !hasModifier(KtTokens.INTERNAL_KEYWORD)

  private fun KtDeclaration.isInventoryDeclaration(): Boolean =
    when (this) {
      is KtEnumEntry,
      is KtSecondaryConstructor -> true
      is KtClassOrObject,
      is KtNamedFunction,
      is KtProperty,
      is KtTypeAlias -> true
      is KtParameter -> hasValOrVar()
      else -> false
    }

  private fun KtDeclaration.hasDirectGezginInternalApi(file: KtFile): Boolean =
    annotationEntries.any { entry ->
      val writtenName = entry.typeReference?.text ?: return@any false
      when {
        writtenName == gezginInternalApiFqName -> true
        '.' in writtenName -> false
        else -> {
          val matchingExplicitImports =
            file.importDirectives.filter { importDirective ->
              if (importDirective.isAllUnder) return@filter false
              val importedFqName = importDirective.importedFqName ?: return@filter false
              val visibleName = importDirective.aliasName ?: importedFqName.shortName().asString()
              visibleName == writtenName
            }
          when {
            matchingExplicitImports.isNotEmpty() ->
              matchingExplicitImports.any { importDirective ->
                importDirective.importedFqName?.asString() == gezginInternalApiFqName
              }
            writtenName == gezginInternalApiShortName &&
              file.importDirectives.any { importDirective ->
                importDirective.isAllUnder &&
                  importDirective.importedFqName?.asString() == gezginInternalApiPackage
              } -> true
            writtenName == gezginInternalApiShortName &&
              file.packageFqName.asString() == gezginInternalApiPackage -> true
            else -> false
          }
        }
      }
    }

  private fun KtDeclaration.toInventoryEntry(
    file: KtFile,
    input: KotlinSourceInput,
    exclusion: KDocExclusionReason?,
  ): PublicApiDeclaration {
    val offset = textOffset.coerceAtLeast(0)
    val prefix = input.content.substring(0, offset.coerceAtMost(input.content.length))
    val lineStart = prefix.lastIndexOf('\n') + 1
    val name = (this as? KtNamedDeclaration)?.name ?: "<anonymous>"
    val kdoc = effectiveKDoc()
    return PublicApiDeclaration(
      path = input.path,
      line = prefix.count { it == '\n' } + 1,
      column = offset - lineStart + 1,
      name = name,
      kind = declarationKind(),
      topLevelType = parent === file && this is KtClassOrObject,
      excluded = exclusion != null,
      exclusionReason = exclusion,
      hasKDoc = kdoc != null,
      hasAuthor = kdoc.hasExactAuthor(),
      kDocText = kdoc?.text,
    )
  }

  private fun String.containsTurkishKDoc(): Boolean =
    TURKISH_CHARACTER.containsMatchIn(this) || TURKISH_WORD.containsMatchIn(this)

  private fun String.containsProcessArtifact(): Boolean = PROCESS_ARTIFACT.containsMatchIn(this)

  private fun KtDeclaration.effectiveKDoc(): KDoc? {
    docComment?.let {
      return it
    }
    if (this !is KtParameter || !hasValOrVar()) return null
    val owner = getStrictParentOfType<KtClassOrObject>() ?: return null
    val ownerDoc = owner.docComment ?: return null
    val propertyName = name ?: return null
    return ownerDoc.takeIf { doc ->
      Regex("(?m)^\\s*\\*?\\s*@(property|param)\\s+${Regex.escape(propertyName)}\\b")
        .containsMatchIn(doc.text)
    }
  }

  private fun KDoc?.hasExactAuthor(): Boolean =
    this?.text?.lineSequence()?.any { line ->
      line.trim().removePrefix("*").trim() == "@author @sahsenvar"
    } == true

  private fun KtDeclaration.declarationKind(): String =
    when (this) {
      is KtEnumEntry -> "enum entry"
      is KtSecondaryConstructor -> "constructor"
      is KtObjectDeclaration -> "object"
      is KtClassOrObject ->
        when {
          hasModifier(KtTokens.ANNOTATION_KEYWORD) -> "annotation class"
          this is KtClass && isInterface() -> "interface"
          else -> "class"
        }
      is KtNamedFunction -> "function"
      is KtProperty,
      is KtParameter -> "property"
      is KtTypeAlias -> "typealias"
      else -> "declaration"
    }

  private companion object {
    val TURKISH_CHARACTER =
      Regex("[\u00e7\u011f\u0131\u0130\u00f6\u015f\u00fc\u00c7\u011e\u00d6\u015e\u00dc]")
    val TURKISH_WORD =
      Regex(
        "\\b(?:bir|bu|ve|veya|ile|icin|degil|varsa|yoksa|olur|olmaz|eder|etmez|alir|" +
          "verir|okur|yazar|kendi|sadece|sonra|gore|yeni|eski|kullanici|kullanilan|" +
          "kullanir|doner|dondurur|sonuc|sirasinda|yalniz|oldugu|olarak|davranis|" +
          "davranisi|ekler|cikarir|siler|saglar|gerekir|gecerli|gecersiz|durum|deger|" +
          "degeri|ornegin|ayni|tutulur|edilir|edilmez|olmadan|uzerinden|tarafindan|" +
          "tanimli|zorunlu|varsayilan|hata|cagri|katman|ekran|akis|anahtar|geri|ileri|" +
          "hedef|kaynak|islem|islemi|uygulama|modul|sinif|fonksiyon|ozellik|parametre|" +
          "kural|kapsam|sekilde)\\b|" +
          "\\b(?:gezgin|route|entry|navigator|flow|stack|screen|graph)'(?:in|un|nin|nun)\\b",
        RegexOption.IGNORE_CASE,
      )
    val PROCESS_ARTIFACT =
      Regex(
        "\\b(?:todo|fixme)\\b|" +
          "\\b(?:task|phase|faz|görev|gorev|aşama|asama)\\s*(?:[-#:]\\s*)?\\d+(?:[.-]\\d+)*\\b|" +
          "\\btask\\s*[-#:]\\s*[a-z0-9]+\\b|" +
          "\\b(?:final[- ]review|review report|review findings?|review checkpoint|" +
          "spike report|implementation brief|implementation report|implementation checkpoint|" +
          "release report|release checkpoint|migration report|migration checkpoint|" +
          "phase migration|phase implementation)\\b",
        RegexOption.IGNORE_CASE,
      )
  }
}
