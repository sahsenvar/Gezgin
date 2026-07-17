package dev.gezgin.processor.fixtures

/**
 * Task 3.4 fixture — `@Screen`/`@Dialog`/`@BottomSheet`/`@FullscreenModal`-annotated composable
 * stubs over [SHOP_SOURCE]'s routes, feeding [dev.gezgin.processor.EntryCodegenTest]'s positive
 * (golden-text) cases. Kept in a SEPARATE source file from `SHOP_SOURCE` (both compiled together by
 * the test) since composables realistically live in a different module/package than their routes —
 * exactly the cross-package scenario [dev.gezgin.processor.codegen.EntryCodegen] must qualify
 * navigator-factory calls for.
 *
 * - `FeedScreen(route: Feed, nav: FeedNavigator)` — full core-mode shape: explicit `@Screen(Feed::class)`
 *   route + nav; the `route:` param carries route data and its type matches the annotation route.
 * - `CatalogScreen(nav: CatalogNavigator)` — no `route:` param at all (Catalog is a bare `data
 *   object`, nothing to pass) — route type comes from the explicit `@Screen(Catalog::class)`.
 * - `AboutScreen(route: About)` — no `nav:` param — `About` has NO navigator ([SHOP_SOURCE]'s
 *   bare `@NoBack` route); pins that a `route`-only composable never needs one.
 * - `PromoDialog(route: Promo, nav: PromoNavigator)` — `@Dialog` kind mapping.
 * - `ProductScreen(route: Product)` — `Product` is [SHOP_SOURCE]'s `@NoBack` route; pins that the
 *   generated `register` call carries `noBack = true` (M5′ flag, read from the route model).
 */
val ENTRY_SOURCE = """
    package dev.gezgin.shopui

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.core.annotation.Dialog
    import dev.gezgin.shop.HomeGraph.Feed
    import dev.gezgin.shop.HomeGraph.Catalog
    import dev.gezgin.shop.HomeGraph.About
    import dev.gezgin.shop.HomeGraph.Promo
    import dev.gezgin.shop.HomeGraph.Product
    import dev.gezgin.shop.FeedNavigator
    import dev.gezgin.shop.CatalogNavigator
    import dev.gezgin.shop.PromoNavigator

    @Screen(Feed::class)
    @Composable
    fun FeedScreen(route: Feed, nav: FeedNavigator) {
    }

    @Screen(Catalog::class)
    @Composable
    fun CatalogScreen(nav: CatalogNavigator) {
    }

    @Screen(About::class)
    @Composable
    fun AboutScreen(route: About) {
    }

    @Dialog(Promo::class)
    @Composable
    fun PromoDialog(route: Promo, nav: PromoNavigator) {
    }

    @Screen(Product::class)
    @Composable
    fun ProductScreen(route: Product) {
    }
""".trimIndent()
