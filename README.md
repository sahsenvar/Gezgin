# Gezgin

Type-safe, annotation + KSP-codegen, state-as-data navigation for Compose Multiplatform (Navigation 3 üzerinde).

- Tasarım spec'i: [docs/gezgin-design.md](docs/gezgin-design.md)
- Örneklerle tanıtım: [docs/gezgin-by-example.md](docs/gezgin-by-example.md)
- Review bulguları: [docs/gezgin-review-findings-r2.md](docs/gezgin-review-findings-r2.md)

## Modüller

Yayınlanan üç artefakt (group: `dev.gezgin`; ayrıntı [docs/gezgin-design.md](docs/gezgin-design.md) §15):

| Modül | Rol | Açıklama |
|---|---|---|
| `gezgin-core` | Zorunlu temel | DI-agnostik: annotation'lar, runtime, `GezginDisplay` (Compose display katmanı), üretilen kodun runtime hedef yüzeyi (navigator facade / `GezginEntryScope`) ve modal scene strategy'leri (Dialog/BottomSheet). Codegen `gezgin-processor`'dadır. |
| `gezgin-processor` | Zorunlu | KSP2 symbol processor — tipli navigator'ları ve entry provider'larını üretir. `ksp(project(":gezgin-processor"))` ile uygulanır. |
| `gezgin-mvi` | Opsiyonel add-on | `gezgin-core`'a bağımlı MVI binder: `@ViewModel`/`@ScreenEffect` + `GezginMvi<S,I,E>` sözleşmesi + codegen binder (`provideXEntry`) + `ObserveAsEvents` + DI-detection (Hilt/Koin, androidx fallback). |
