# Gezgin Faz 7 — Kapanış: Kapsamlı Sample Denetimi + Docs/Versioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development.

**Goal:** Master plan'ın son fazı (`docs/superpowers/plans/2026-07-07-gezgin-v1-implementation.md` satır 37, 913-915, "Faz 7 — Shopr sample + kapanış") + kullanıcının 2026-07-09 talebiyle netleştirilmiş kapsam: multi-module `sample/`'ın TÜM Gezgin özelliklerini kullanan, gerçek-dünya senaryosuna yakın TEK tutarlı bir uygulama olarak denetlenmesi/tamamlanması (Fragment interop dahil — gerçek bir Fragment örneğiyle), artı kütüphanenin genel kapanışı (docs/versioning/publish-iskeleti) + final review + `main`'e merge.

**Architecture:** Faz 0'ın master plan'ı orijinalde `:sample:shopr` tek-modül sample'ını hedefliyordu; proje ilerledikçe kullanıcının açık talebiyle (Sample Showcase, 2026-07-08) **multi-module `sample/`** (`:sample:app` + `:sample:navigation` + `:sample:feature:{auth,home,profile}`) birincil, kapsamlı gösterim aracı haline geldi — `:sample:shopr` daha erken/ikincil bir örnek olarak KALIR (dokunulmaz, kaldırılmaz, ama Faz 7'nin "TÜM özellikler" hedefi multi-module `sample/`'a karşı denetlenir). Faz 7 üç bacaklı: (1) **denetim** — spec + her fazın raporu taranıp mevcut `sample/README.md` kapsama tablosuna karşı kesin bir eksik listesi çıkarılır; (2) **kapatma** — bulunan eksikler (ör. `@FullscreenModal`'ın Faz 4.4'te "gerekli değil" diye bilinçli atlanmış olması — kullanıcının yeni "tüm özellikler" talebiyle YENİDEN karara bağlanır) koda dönüştürülür; (3) **kapanış** — davranış testleri/on-device checklist tutarlılık taraması + KDoc/README/versioning/maven-publish iskeleti + whole-branch final review + merge.

**Tech Stack:** Mevcut zincir; yeni bağımlılık ise (maven-publish plugin gibi) Maven-metadata'dan doğrulanır, varsayılmaz.

## Global Constraints

- Spec kazanır. Master plan'ın Faz 7 seed'i (`v1-implementation.md` 913-915) ile kullanıcının 2026-07-09 talebi ("multi module sample da tum ozelllikleri kullandigin bir ornek... Gercek dunya ornegine yakin... Fragment kullanaarak fragment support ornegi de sagla") BİRLİKTE bağlayıcı — ikincisi birincisinin kapsamını netleştirir/aktive eder, çelişmez.
- **Hiçbir kütüphane (gezgin-core/gezgin-processor/gezgin-mvi) kodu bu fazda DEĞİŞMEZ** — Faz 7 tamamen tüketim (sample) + docs + kapanış; V1 kapsamı Faz 0-6'da tamamlandı. Bir denetim sırasında gerçek bir KÜTÜPHANE bug'ı bulunursa (beklenmez, ama olursa) kullanıcıya escalate edilir, otomatik "V1 scope"un dışına çıkılmaz.
- 7.1'in ürettiği eksik listesi 7.2'nin TEK kaynağı olmalı — 7.2 kendi kafasından yeni "eksik" icat etmez, yalnız 7.1'in bulduklarını kapatır (YAGNI/scope-creep önleme).
- `@FullscreenModal` kararı: Faz 4.4 bunu bilinçli atladı ("DialogContract'ın basit paraleli, core'da zaten uiTest kanıtı var" — `sample/README.md` satır 128-134). Kullanıcının yeni "tüm özellikleri kullandığın örnek" talebi bu kararı AÇIKÇA yeniden gündeme getiriyor — 7.1/7.2 bunu ya (a) somut bir üçüncü modal route ile kapatır ya da (b) Faz 4.4'ün gerekçesini kullanıcının yeni talebi ışığında yeniden değerlendirip AÇIKÇA "hâlâ geçerli" diye rapor eder; sessizce atlanmaz.
- `:sample:shopr`'a dokunulmaz (ayrı, erken bir örnek — bu fazın "multi-module sample" hedefi `sample/` içindir).
- Fragment örneği (Faz 6.4'ün `HelpFragment`'ı) zaten gerçek (View-tabanlı, gezginArgs+gezginNav, cross-module) — 7.1 bunun kullanıcının "gerçek Fragment örneği" barını karşılayıp karşılamadığını AÇIKÇA değerlendirir (muhtemelen karşılıyor — token/stub değil — ama denetim bunu varsaymaz, doğrular).
- TDD; sık commit; her görev implementer + bağımsız reviewer (opus) + fix döngüsü — Faz 0-6'da kurulan disiplin aynen sürer.

## Görev haritası

| # | Görev | Gate |
|---|---|---|
| 7.1 | **Denetim (araştırma, kod YOK):** `docs/gezgin-design.md` + `docs/gezgin-by-example.md` + her fazın `.superpowers/sdd/task-*-report.md`'si taranır; mevcut `sample/README.md` kapsama tablosuna karşı KESİN bir eksik listesi çıkarılır (`@FullscreenModal` dahil, ama ona sınırlı değil — MVI DI-detection yollarının [Koin/Hilt] belgelenme yeterliliği, Fragment örneğinin derinliği, vb. her şey taranır). Ayrıca "tek tutarlı gerçek-dünya uygulaması" barını değerlendirir: mevcut ekranlar (auth/home/profile) birbirine bağlı, mantıklı bir ürün hikayesi anlatıyor mu, yoksa özellik-başına-kopuk-demo mu? | Yazılı denetim raporu (görev-tablosu formatında eksik listesi + her biri için önerilen kapatma) — kod değişikliği yok |
| 7.2 | 7.1'in bulduğu HER eksiği kapatır (implementer 7.1'in raporunu okuyup uygular; kapsamı 7.1'in listesiyle sınırlı) | sample derlenir + `AppNavBehaviorTest` yeşil + `assembleDebug` yeşil |
| 7.3 | Davranış testleri taraması (7.2'nin eklediği yeni senaryolar için gerekiyorsa `AppNavBehaviorTest.kt`'ye ekleme) + `docs/gezgin-on-device-checklist.md`'nin 4 faz boyunca biriken ~17 maddesinin TUTARLILIK taraması (bayat/çelişkili/yinelenen madde var mı — İCRA edilmez, insan hâlâ cihazda doğrular, ama liste kendi içinde tutarlı olmalı) | full-repo build yeşil; checklist tutarlılık notu raporda |
| 7.4 | Kapanış: kök `README.md`'ye modül şeması + KDoc kapanış taraması (kritik public API'lerde eksik KDoc var mı) + versioning (`0.1.0-alpha01` — `gradle/libs.versions.toml` ya da modül `build.gradle.kts`'lerinde nerede yaşadığına bakılır) + `maven-publish` iskeleti (gerçek yayına ATILMAZ, yalnız plugin+POM iskeleti) + whole-branch final review (fable) + `main`'e merge | fable review "Ready to merge: Yes" |

## Riskler

1. **7.1'in "eksik" tanımı çok geniş yorumlanırsa scope patlar** — Global Constraints'teki "7.2 yalnız 7.1'in bulduklarını kapatır" kuralı bunu sınırlar; 7.1'in kendisi de "gerçek bir özellik eksikliği" ile "nice-to-have polish" ayrımını AÇIKÇA yapmalı (yalnız birincisi zorunlu kapatma listesine girer).
2. **`@FullscreenModal` eklemek yeni bir route/ekran + kapsama tablosu satırı gerektirir** — küçük ama gerçek bir kod değişikliği; Faz 4'ün `FullscreenModalContract`/scene mekanizması zaten olgun (core'da uiTest kanıtlı), risk düşük.
3. **`:sample:shopr` ile multi-module `sample/` arasındaki ilişki belirsizliği** — 7.1 bunu AÇIKÇA netleştirmeli (shopr dokunulmaz/legacy, sample/ birincil) ki kullanıcı ya da gelecekteki bir okuyucu iki sample'ın neden var olduğunu sorgulamasın.
4. **maven-publish iskeleti gerçek bir yayın DEĞİL** — kazara `./gradlew publish` çalıştırılmaması için 7.4 açıkça "iskelet, publish edilmez" diye işaretlenir; kullanıcı onayı olmadan hiçbir publish komutu ÇALIŞTIRILMAZ.
