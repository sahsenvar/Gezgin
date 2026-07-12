# Gezgin

Type-safe, annotation + KSP-codegen, state-as-data navigation for Compose Multiplatform (Navigation 3 üzerinde).

Apache-2.0 lisanslıdır — bkz. [LICENSE](LICENSE).

- Tasarım spec'i: [docs/gezgin-design.md](docs/gezgin-design.md)
- Örneklerle tanıtım: [docs/gezgin-by-example.md](docs/gezgin-by-example.md)
- Review bulguları: [docs/gezgin-review-findings-r2.md](docs/gezgin-review-findings-r2.md)

## Modüller

Yayınlanan üç artefakt (group: `dev.gezgin`; ayrıntı [docs/gezgin-design.md](docs/gezgin-design.md) §15):

| Modül | Rol | Açıklama |
|---|---|---|
| `gezgin-core` | Zorunlu temel | DI-agnostik: annotation'lar, runtime, `GezginDisplay` (Compose display katmanı), üretilen kodun runtime hedef yüzeyi (navigator facade / `GezginEntryScope`) ve modal scene strategy'leri (Dialog/BottomSheet). Codegen `gezgin-processor`'dadır. |
| `gezgin-processor` | Zorunlu | KSP2 symbol processor — tipli navigator'ları ve entry provider'larını üretir. `ksp(project(":gezgin-processor"))` ile uygulanır. |
| `gezgin-mvi` | Opsiyonel add-on | `gezgin-core`'a bağımlı MVI binder: `@MviViewModel`/`@ScreenEffect` + `GezginMvi<S,I,E>` sözleşmesi + codegen binder (`provideXEntry`) + `ObserveEffects` + DI-detection (Hilt/Koin, androidx fallback). |

## Kurulum / Başlangıç

> ⚠️ **Yayın durumu:** Gezgin henüz **Maven Central'da değil**. Dört modülün `maven-publish`
> yapılandırması şimdilik yalnız **iskelet** (uzak repository / signing YOK) → bugün yalnız
> `publishToMavenLocal` mümkün. Aşağıdaki koordinatlar release günü için doğrudur; şimdilik
> **kaynaktan derleyip** (`./gradlew publishToMavenLocal`) `mavenLocal()`'den tüketin.

Dört artefakt (`group = dev.gezgin`, `version = 0.1.0-alpha01`) — üçü runtime, biri (`gezgin-test`) test:

```kotlin
plugins {
    kotlin("multiplatform")                                    // veya kotlin("jvm")
    kotlin("plugin.serialization")                             // route'lar @Serializable
    id("com.google.devtools.ksp") version "2.2.20-2.0.2"       // KSP2; Kotlin 2.2.20 ile eşleşir
}

dependencies {
    implementation("dev.gezgin:gezgin-core:0.1.0-alpha01")     // zorunlu temel + GezginDisplay
    ksp("dev.gezgin:gezgin-processor:0.1.0-alpha01")           // zorunlu codegen
    // implementation("dev.gezgin:gezgin-mvi:0.1.0-alpha01")   // opsiyonel MVI add-on
    // testImplementation("dev.gezgin:gezgin-test:0.1.0-alpha01") // UI'sız test: GezginTestNavigator + tipli fromX()
}
```

Minimal uçtan-uca örnek — graph → `@Screen` → `rememberNavigator` → `GezginDisplay`:

```kotlin
// 1) Navigasyon grafiği = sealed route ağacı
@NavGraph
@Serializable
sealed interface AppGraph {
    @Serializable
    data object HomeRoute : AppGraph          // app-start: argümansız kurulabilmeli (data object)
}

// 2) Ekran — bir @GoTo kenarı eklersen fonksiyona tipli `nav: HomeNavigator` üretilir
@Screen(HomeRoute::class)
@Composable
fun HomeScreen() {
    Text("Merhaba Gezgin")
}

// 3) Host + display. Codegen graph paketine `gezginTopology` + stable `gezginJson` üretir; kurulum
//    core `rememberNavigator`'ı çağırır. Graph modülü düz-JVM olduğundan (Compose plugin YOK) oraya
//    @Composable ÜRETİLMEZ — üretilse Compose-lowering almadan derlenir, tüketici runtime'da çöker.
//    @FragmentScreen kullanıyorsan Activity FragmentActivity/AppCompatActivity OLMALI; yalnız @Screen
//    ekranlar için ComponentActivity yeter.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navigator = rememberNavigator(
                start = HomeRoute,
                topology = gezginTopology,   // codegen üretir (graph paketi)
                json = gezginJson,           // codegen üretir: process-wide stable Json
                onRootBack = { finish() },
            )
            GezginDisplay(navigator = navigator) {
                appGraphEntries()   // entry bundle'ını SEN yazarsın: @Screen başına üretilen provideXEntry()'leri topla
            }
        }
    }
}
```

### KSP seçenekleri

Adopter'ın bilebileceği KSP seçenekleri (`ksp { arg("<ad>", "<değer>") }`):

| Seçenek | Default | Ne zaman |
|---|---|---|
| `gezgin.emitSerializers` | `true` | Polimorfik `Route` `SerializersModule`'ünü kendin sağlıyorsan `false` ver (opt-out). |
| `gezgin.emitTestAccessors` | `false` | Tipli `GezginTestNavigator.fromX()` test erişimcilerini üretmek için `true` ver (opt-in). Flag'i modülün **`main` KSP round'unda** (`ksp {}` bloğu — graph'ların bulunduğu round) aç; erişimciler `main`'e üretilir ve modülün `test` kaynak kümesi `nav.fromX()`'i doğrudan çağırır — **çok-modül düzeninde de çalışır**. `:gezgin-test`'i `main` compile classpath'ine `compileOnly` olarak ekle (üretilen erişimciler derlensin; app runtime'ına sızmaz), `test` için `testImplementation` ile yeniden ekle. Bkz. [docs/gezgin-by-example.md](docs/gezgin-by-example.md) §8. |

## Uyumluluk

Gezgin'in derlenip test edildiği sürümler (kaynak: `gradle/libs.versions.toml` + modül build dosyaları):

| Bileşen | Sürüm |
|---|---|
| Kotlin | `2.2.20` |
| KSP | `2.2.20-2.0.2` |
| Compose Multiplatform | `1.11.0` |
| Navigation 3 — Google `androidx.navigation3:navigation3-runtime` | `1.1.4` |
| Navigation 3 — JetBrains `org.jetbrains.androidx.navigation3` (UI/KMP) | `1.0.0-alpha05` ⚠️ |
| JetBrains lifecycle-nav3 (`org.jetbrains.androidx.lifecycle`) | `2.10.0-alpha05` ⚠️ |
| AGP | `8.11.0` |
| Gradle | `8.14` |
| JDK toolchain | `17` |
| androidx.fragment (`fragment-compose`, interop) | `1.8.9` |
| min SDK | `24` |
| compile / target SDK | `36` |

> ⚠️ **Kararlılık uyarısı — Navigation 3 hâlâ alpha.** Gezgin'in çok-platform display katmanı,
> JetBrains'in KMP navigation3 portuna (`org.jetbrains.androidx.navigation3` `1.0.0-alpha05`) ve
> aynı release-train'deki lifecycle-nav3'e (`2.10.0-alpha05`) dayanır; bunlar **alpha** olduğundan
> API'leri sürümler arası kırılabilir. Sürümler bu tabloya **açıkça pinlenmiştir**; yeni bir alpha'ya
> yükseltmek Gezgin tarafında kırıcı değişiklik gerektirebilir.

## Lisans

Apache License 2.0 — tam metin: [LICENSE](LICENSE). `Copyright 2026 Gezgin contributors`.
