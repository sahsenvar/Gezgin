import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Faz 9.1 — BCV yalnız KÖK'e uygulanır (apply false DEĞİL); alt-projelerin apiCheck/apiDump görevlerini
    // kendisi kurar ve `apiCheck`'i `check` yaşam-döngüsüne bağlar (varsayılan davranış).
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka) apply false
}

private data class PublicApiKDocFinding(
    val file: File,
    val line: Int,
    val signature: String,
    val missingKDoc: Boolean,
    val missingAuthor: Boolean,
)

private fun precedingKDoc(lines: List<String>, declarationIndex: Int): String? {
    var cursor = declarationIndex - 1
    var annotationParenthesisDepth = 0
    while (cursor >= 0) {
        val candidate = lines[cursor].trim()
        if (candidate.endsWith("*/") && annotationParenthesisDepth == 0) break
        if (candidate.isBlank()) {
            cursor--
            continue
        }

        val opens = candidate.count { it == '(' }
        val closes = candidate.count { it == ')' }
        if (annotationParenthesisDepth > 0 || candidate.startsWith("@") || closes > opens) {
            annotationParenthesisDepth = (annotationParenthesisDepth + closes - opens).coerceAtLeast(0)
            cursor--
            continue
        }
        return null
    }
    if (cursor < 0 || !lines[cursor].trimEnd().endsWith("*/")) return null

    val end = cursor
    while (cursor >= 0 && !lines[cursor].contains("/**")) cursor--
    if (cursor < 0) return null
    return lines.subList(cursor, end + 1).joinToString("\n")
}

private fun hasGezginInternalApi(lines: List<String>, declarationIndex: Int): Boolean {
    if (lines[declarationIndex].contains("@GezginInternalApi")) return true

    var cursor = declarationIndex - 1
    while (cursor >= 0) {
        val candidate = lines[cursor].trim()
        when {
            candidate.isBlank() -> cursor--
            candidate.startsWith("@") -> {
                if (candidate.startsWith("@GezginInternalApi")) return true
                cursor--
            }
            else -> return false
        }
    }
    return false
}

private val publicTypeDeclaration = Regex(
    "^public\\s+(?:(?:expect|actual|sealed|data|enum|annotation|value|fun)\\s+)*(?:class|interface|object)\\b",
)

private fun publicApiKDocFindings(file: File): List<PublicApiKDocFinding> {
    val lines = file.readLines()
    return lines.mapIndexedNotNull { index, line ->
        val signature = line.trim()
        if (!signature.startsWith("public ") ||
            signature.startsWith("public override ") ||
            hasGezginInternalApi(lines, index)
        ) {
            return@mapIndexedNotNull null
        }

        val kdoc = precedingKDoc(lines, index)
        val isTopLevelType = line == line.trimStart() && publicTypeDeclaration.containsMatchIn(signature)
        PublicApiKDocFinding(
            file = file,
            line = index + 1,
            signature = signature,
            missingKDoc = kdoc == null,
            missingAuthor = isTopLevelType && kdoc?.lineSequence()?.none { it.trim() == "* @author @sahsenvar" } != false,
        ).takeIf { it.missingKDoc || it.missingAuthor }
    }
}

private data class PublicApiKDocInventory(val included: Int, val excluded: Int)

private fun publicApiKDocInventory(file: File): PublicApiKDocInventory {
    val lines = file.readLines()
    var included = 0
    var excluded = 0
    lines.forEachIndexed { index, line ->
        val signature = line.trim()
        if (!signature.startsWith("public ")) return@forEachIndexed
        if (signature.startsWith("public override ") || hasGezginInternalApi(lines, index)) {
            excluded++
        } else {
            included++
        }
    }
    return PublicApiKDocInventory(included = included, excluded = excluded)
}

val publicApiSourceRoots = mapOf(
    "gezgin-core" to listOf("commonMain", "androidMain", "jvmMain"),
    "gezgin-mvi" to listOf("commonMain", "androidMain", "jvmMain"),
    "gezgin-processor" to listOf("main"),
    "gezgin-test" to listOf("commonMain", "androidMain", "jvmMain"),
)

private val expectedPublicApiKDocInventory = mapOf(
    "gezgin-core" to PublicApiKDocInventory(included = 101, excluded = 16),
    "gezgin-mvi" to PublicApiKDocInventory(included = 14, excluded = 0),
    "gezgin-processor" to PublicApiKDocInventory(included = 1, excluded = 0),
    "gezgin-test" to PublicApiKDocInventory(included = 12, excluded = 1),
)

tasks.register("checkPublicApiKDoc") {
    group = "verification"
    description = "Checks handwritten consumer-visible public Kotlin declarations for KDoc and authorship."

    val moduleSources = publicApiSourceRoots.mapValues { (module, sourceSets) ->
        sourceSets.map { sourceSet -> fileTree("$module/src/$sourceSet/kotlin") { include("**/*.kt") } }
    }
    val sources = moduleSources.values.flatten()
    inputs.files(sources)

    doLast {
        val sourceFiles = sources.flatMap { it.files }.sortedBy { it.relativeTo(rootDir).invariantSeparatorsPath }
        val inventory = moduleSources.mapValues { (_, trees) ->
            trees.flatMap { it.files }.map(::publicApiKDocInventory).fold(PublicApiKDocInventory(0, 0)) { total, item ->
                PublicApiKDocInventory(total.included + item.included, total.excluded + item.excluded)
            }
        }
        logger.lifecycle(
            "Public API KDoc inventory: " + inventory.entries.joinToString(", ") { (module, count) ->
                "$module=${count.included} included/${count.excluded} excluded"
            },
        )
        if (inventory != expectedPublicApiKDocInventory) {
            throw GradleException(
                "Public API KDoc inventory changed. Review the declarations and update " +
                    "expectedPublicApiKDocInventory intentionally. Expected $expectedPublicApiKDocInventory, actual $inventory",
            )
        }
        val findings = sourceFiles.flatMap(::publicApiKDocFindings)
        if (findings.isNotEmpty()) {
            val details = findings.joinToString("\n") { finding ->
                val reasons = buildList {
                    if (finding.missingKDoc) add("missing KDoc")
                    if (finding.missingAuthor) add("missing exact @author @sahsenvar")
                }.joinToString(", ")
                "${finding.file.relativeTo(rootDir).invariantSeparatorsPath}:${finding.line}: $reasons: ${finding.signature}"
            }
            throw GradleException("Public API KDoc check failed (${findings.size} declaration(s)):\n$details")
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("checkPublicApiKDoc"))
    }
}

// F-MAJOR-2 — ABI doğrulaması artık yayınlanan/yayına-komşu 4 modül için (core/mvi/processor/test).
// gezgin-test bir POM iskeleti kazandı ve dış-benimseyene sunulabilir hale geldi → BCV kapsamına alındı
// (.api dump tutulur). Yalnız sample/* modülleri (gerçekten yayınlanmayan) hariç tutulur.
apiValidation {
    // Faz 9.3 (K4) — @GezginInternalApi ile işaretli forced-public semboller (codegen/gezgin-test kancaları)
    // kilitli ABI yüzeyinden düşürülür → alpha01 sonrası deprecation döngüsü olmadan evrilebilirler.
    nonPublicMarkers += "dev.gezgin.core.GezginInternalApi"
    ignoredProjects += listOf(
        "shopr", "navigation", "app", "domain",
        "auth", "home", "profile",
    )
}
