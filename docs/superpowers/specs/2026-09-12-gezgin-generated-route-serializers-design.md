# Generated route serializers

> Status: approved design, ready for implementation planning
> Date: 2026-09-12
> Baseline: `design/screen-wrapper-de-opinionation` at `2bc6b9e`
> Target release: `0.3.0` (already breaking)

## 1. Purpose

A graph declaration carries a stack of annotations that say something about navigation, plus one
that says nothing about navigation at all:

```kotlin
@GoTo(ItemDetailScreenRoute::class, singleTop = false, name = "goToRelated")
@GoTo(ItemImageViewerRoute::class)
@BackTo(DashboardScreenRoute::class)
@Serializable
data class ItemDetailScreenRoute(val id: String) : HomeGraph
```

`@Serializable` is noise at that position: it is a persistence detail repeated on every route in
every graph. This design removes it by generating each route's `KSerializer` instead of requiring
the user to ask the compiler plugin for one.

Nothing about persistence changes. The wire format, process-death restore, typed results, Fragment
argument decoding and Compose Multiplatform support are all untouched.

## 2. Scope

In scope: the generated serializers module, a generated `KSerializer` per route, a generated
serializer per enum used as a route parameter or result type, the serializer references inside
generated topology edges, the matching validations, and removing the annotation from every sample.

**Explicitly out of scope**, deferred to their own designs: the `topology` parameter on
`rememberNavigator`, the `GezginDisplay` parameter surface, and any change to `NavDisplay` parity.
This document changes what a route declaration looks like and nothing else.

## 3. What the user writes

```kotlin
// before
@NavGraph
@Serializable
sealed interface HomeGraph : Route {

  @GoTo(ItemDetailScreenRoute::class)
  @Serializable
  data object DashboardScreenRoute : HomeGraph

  @BackTo(DashboardScreenRoute::class)
  @Serializable
  data class ItemDetailScreenRoute(val id: String) : HomeGraph
}

// after
@NavGraph
sealed interface HomeGraph : Route {

  @GoTo(ItemDetailScreenRoute::class)
  data object DashboardScreenRoute : HomeGraph

  @BackTo(DashboardScreenRoute::class)
  data class ItemDetailScreenRoute(val id: String) : HomeGraph
}
```

Parameter types keep `@Serializable`, because that is where it belongs:

```kotlin
@Serializable data class ComplexFilter(val query: String, val page: Int)

data class SearchScreenRoute(val filter: ComplexFilter) : HomeGraph
```

Two further simplifications fall out and are part of this design:

- **An enum used as a parameter or result type no longer needs `@Serializable`.** Gezgin generates
  a name-based serializer for it.
- **A graph module that declares no `@Serializable` type of its own no longer needs the
  `kotlin.plugin.serialization` Gradle plugin.** `sample/navigation` is exactly that module: its
  routes' parameter types live in `:sample:domain`.

## 4. Generated output

### 4.1 One serializer per route

```kotlin
internal object ItemDetailScreenRouteSerializer : KSerializer<HomeGraph.ItemDetailScreenRoute> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("dev.gezgin.sample.navigation.HomeGraph.ItemDetailScreenRoute") {
      element<String>("id")
    }

  override fun serialize(encoder: Encoder, value: HomeGraph.ItemDetailScreenRoute) {
    encoder.encodeStructure(descriptor) { encodeStringElement(descriptor, 0, value.id) }
  }

  override fun deserialize(decoder: Decoder): HomeGraph.ItemDetailScreenRoute =
    decoder.decodeStructure(descriptor) {
      var id = ""
      while (true) {
        when (decodeElementIndex(descriptor)) {
          0 -> id = decodeStringElement(descriptor, 0)
          -1 -> break
          else -> error("unexpected index")
        }
      }
      HomeGraph.ItemDetailScreenRoute(id)
    }
}
```

A parameterless route (`data object`) gets the same shape with an empty descriptor and a body that
drains the structure and returns the object instance.

The descriptor's serial name must be the route's **fully-qualified name**, because that is what
kotlinx uses as the default serial name today and the polymorphic discriminator is written from it.
Getting this wrong does not corrupt anything — a snapshot that fails to decode falls back to a
fresh start — but it silently loses every user's navigation state on upgrade, so §11 locks it with
a test that decodes an `@Serializable`-produced snapshot using the generated serializer.

### 4.2 One serializer per enum

Generated once per enum type that appears as a route parameter or a result type, when that enum
does not already carry `@Serializable`:

```kotlin
internal object SortOrderSerializer : KSerializer<SortOrder> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("dev.gezgin.sample.domain.model.SortOrder", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: SortOrder) = encoder.encodeString(value.name)

  override fun deserialize(decoder: Decoder): SortOrder = SortOrder.valueOf(decoder.decodeString())
}
```

Name-based, matching kotlinx's own enum encoding, so an existing snapshot still decodes.

### 4.3 The serializers module

```kotlin
// before
polymorphic(Route::class) { subclass(HomeGraph.ItemDetailScreenRoute::class) }

// after
polymorphic(Route::class) {
  subclass(HomeGraph.ItemDetailScreenRoute::class, ItemDetailScreenRouteSerializer)
}
```

### 4.4 Topology edges

`TopologyCodegen` currently emits `EdgeSpec(id, serializer<SortOrder>())`. `serializer<T>()` is the
compiler plugin's reified entry point; without the plugin it falls back to reflection, which is
JVM-only and would break both the multiplatform promise and §3's "drop the plugin" claim. Edge
serializers must therefore use the same explicit resolution as route parameters:

```kotlin
// before
EdgeSpec("…#pickSort", serializer<SortOrder>())

// after
EdgeSpec("…#pickSort", SortOrderSerializer)
```

## 5. Type resolution

For every type Gezgin must persist — a route constructor parameter or a result payload — the
processor emits a serializer reference by these rules, in order:

| Type | Emitted reference |
|---|---|
| `String`, `Int`, `Long`, `Boolean`, `Float`, `Double`, `Short`, `Byte`, `Char` | `String.serializer()` etc. from `kotlinx.serialization.builtins` |
| any `T?` where `T` resolves | `<T reference>.nullable` |
| `List<T>` where `T` resolves | `ListSerializer(<T reference>)` |
| an enum class | the generated serializer from §4.2, or `.serializer()` when the enum carries `@Serializable` |
| a non-generic class carrying `@Serializable` | `<Type>.serializer()` |
| anything else | error `SZ1` |

`@Serializable` is detected by reading the annotation off the type's declaration, which resolves for
classpath declarations as well as source ones.

Generic user types (`Box<T>`) are deliberately **not** supported. `.serializer()` on a generic class
requires a serializer per type argument, and no route or result in any sample needs one. A route
that declares one gets `SZ1` naming the type; the user flattens it or wraps it in a non-generic
`@Serializable` class.

## 6. `@Serializable` on a route stays legal

A route that still carries `@Serializable` keeps using its own compiler-generated serializer, and
the module registers it the old way (`subclass(X::class)`). No consumer is forced to change
anything, and a mixed graph is valid.

This is not a deprecation window kept out of caution — it is the honest reading of the annotation.
`@Serializable` on a route is a legitimate way to say "encode this type"; the design only removes
the *obligation*, not the option.

## 7. What does not change

- The encoded form of `SavedState`, `GezginKey`, and each route.
- Process-death restore, including pending result slots and their payloads.
- `Gezgin.initFragmentInterop(gezginJson)` and `gezginArgs` decoding.
- Compose Multiplatform support: no reflection is introduced anywhere.
- `gezgin-core` keeps `kotlinx-serialization` and the compiler plugin; `SavedState` and `GezginKey`
  remain `@Serializable`.

## 8. Validation

| Code | Condition |
|---|---|
| `SZ1` | a route parameter or result type resolves to no serializer (unsupported shape, generic user type, or a class without `@Serializable`); the message names the declaration, the parameter and the type |

`SZ1` exists because the failure would otherwise surface as an unresolved `Type.serializer()` call
inside generated code — a Kotlin error pointing at a file the user did not write.

There is deliberately only one code. A second condition was drafted for "the type is `@Serializable`
but unreachable from this module"; it was dropped because it cannot occur — a route that names a
type in its constructor already requires that type to be on the compile classpath and visible.

## 9. Verified before writing

Measured on 2026-09-12 with two throwaway probes, both since deleted.

- A graph and its routes with **no** `@Serializable`, using hand-written stand-ins for the
  generated serializers, round-tripped through the real `encodeNavigatorState` /
  `decodeNavigatorState` path: back stack and `current` preserved exactly. Parameters covered
  `String`, `String?`, an enum with no annotation, and an `@Serializable` data class.
- The encoded form was identical in shape to today's:
  `{"keys":[{"route":{"type":"…ListRoute"},"id":0},{"route":{"type":"…DetailRoute","id":"x","name":"n","order":"RELEVANCE","filter":{"query":"q","page":1}},"id":1}],"nextId":2,"pendingSlots":[]}`
- A separate Gradle module with **no** `kotlin.plugin.serialization` and no `@Serializable`
  anywhere, containing routes plus hand-written serializers, compiled successfully. This is the
  evidence for §3's claim that a graph module can drop the plugin.

## 10. Migration

Per graph module:

1. Delete `@Serializable` from the `@NavGraph` interface and from every route inside it.
2. Delete `@Serializable` from any enum used only as a route parameter or result type.
3. Remove `alias(libs.plugins.kotlin.serialization)` if the module declares no `@Serializable` type
   of its own.

Nothing else moves. A consumer who changes nothing keeps working (§6).

## 11. Testing

- Golden codegen tests for each parameter kind: parameterless object, `String`, nullable, enum,
  `List<String>`, `@Serializable` class, and a mixture.
- A round-trip test through `encodeNavigatorState` / `decodeNavigatorState` asserting the back stack
  survives, mirroring the probe in §9.
- A byte-compatibility test: a snapshot produced by an `@Serializable` route decodes with the
  generated serializer and vice versa, proving §4.1's serial-name claim.
- A result-edge test proving a PD-safe result payload still encodes and decodes after §4.4.
- Negative tests for `SZ1` and `SZ2`.
- A cross-module test where the parameter type lives in a different module than the graph.
- `sample/navigation` builds with the serialization plugin removed; every sample builds with no
  `@Serializable` on any route.

## 12. Open items

- Whether to also generate serializers for `Set<T>` and `Map<K, V>`. Deferred: no sample needs
  them, and `SZ1` names the type clearly enough for a user to flatten.
