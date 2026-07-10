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
