@file:OptIn(GezginInternalApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.gezgin.core

import dev.gezgin.core.compose.decodeNavigatorState
import dev.gezgin.core.compose.encodeNavigatorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

sealed interface SpikeGraph : Route {
  data object ListRoute : SpikeGraph

  data class DetailRoute(
    val id: String,
    val name: String?,
    val order: SortOrder,
    val filter: ComplexFilter,
  ) : SpikeGraph
}

enum class SortOrder {
  RELEVANCE,
  PRICE_ASC,
}

@Serializable data class ComplexFilter(val query: String, val page: Int)

private object ListRouteGezginSerializer : KSerializer<SpikeGraph.ListRoute> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("dev.gezgin.core.SpikeGraph.ListRoute")

  override fun serialize(encoder: Encoder, value: SpikeGraph.ListRoute) {
    encoder.encodeStructure(descriptor) {}
  }

  override fun deserialize(decoder: Decoder): SpikeGraph.ListRoute =
    decoder.decodeStructure(descriptor) {
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          CompositeDecoder.DECODE_DONE -> break
          else -> throw SerializationException("unexpected index $index")
        }
      }
      SpikeGraph.ListRoute
    }
}

private object SortOrderGezginSerializer : KSerializer<SortOrder> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("dev.gezgin.core.SortOrder", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: SortOrder) = encoder.encodeString(value.name)

  override fun deserialize(decoder: Decoder): SortOrder = SortOrder.valueOf(decoder.decodeString())
}

private object DetailRouteGezginSerializer : KSerializer<SpikeGraph.DetailRoute> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("dev.gezgin.core.SpikeGraph.DetailRoute") {
      element("id", String.serializer().descriptor)
      element("name", String.serializer().nullable.descriptor)
      element("order", SortOrderGezginSerializer.descriptor)
      element("filter", ComplexFilter.serializer().descriptor)
    }

  override fun serialize(encoder: Encoder, value: SpikeGraph.DetailRoute) {
    encoder.encodeStructure(descriptor) {
      encodeSerializableElement(descriptor, 0, String.serializer(), value.id)
      encodeSerializableElement(descriptor, 1, String.serializer().nullable, value.name)
      encodeSerializableElement(descriptor, 2, SortOrderGezginSerializer, value.order)
      encodeSerializableElement(descriptor, 3, ComplexFilter.serializer(), value.filter)
    }
  }

  override fun deserialize(decoder: Decoder): SpikeGraph.DetailRoute =
    decoder.decodeStructure(descriptor) {
      var id: String? = null
      var seen0 = false
      var name: String? = null
      var seen1 = false
      var order: SortOrder? = null
      var seen2 = false
      var filter: ComplexFilter? = null
      var seen3 = false
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> {
            id = decodeSerializableElement(descriptor, 0, String.serializer())
            seen0 = true
          }
          1 -> {
            name = decodeSerializableElement(descriptor, 1, String.serializer().nullable)
            seen1 = true
          }
          2 -> {
            order = decodeSerializableElement(descriptor, 2, SortOrderGezginSerializer)
            seen2 = true
          }
          3 -> {
            filter = decodeSerializableElement(descriptor, 3, ComplexFilter.serializer())
            seen3 = true
          }
          CompositeDecoder.DECODE_DONE -> break
          else -> throw SerializationException("unexpected index $index")
        }
      }
      if (!seen0)
        throw SerializationException("dev.gezgin.core.SpikeGraph.DetailRoute: missing 'id'")
      if (!seen1)
        throw SerializationException("dev.gezgin.core.SpikeGraph.DetailRoute: missing 'name'")
      if (!seen2)
        throw SerializationException("dev.gezgin.core.SpikeGraph.DetailRoute: missing 'order'")
      if (!seen3)
        throw SerializationException("dev.gezgin.core.SpikeGraph.DetailRoute: missing 'filter'")
      SpikeGraph.DetailRoute(
        id = id as String,
        name = name,
        order = order as SortOrder,
        filter = filter as ComplexFilter,
      )
    }
}

private val spikeJson = Json {
  serializersModule = SerializersModule {
    polymorphic(Route::class) {
      subclass(SpikeGraph.ListRoute::class, ListRouteGezginSerializer)
      subclass(SpikeGraph.DetailRoute::class, DetailRouteGezginSerializer)
    }
  }
}

class GeneratedRouteSerializerRoundTripTest {
  @Test
  fun `a route with no Serializable round-trips through the real save and restore path`() {
    val topology = GezginTopology(emptyMap(), emptyMap(), emptyMap())
    val navigator = RawNavigator(start = SpikeGraph.ListRoute, topology = topology, onRootBack = {})
    val detail =
      SpikeGraph.DetailRoute("item-42", null, SortOrder.PRICE_ASC, ComplexFilter("shoes", 3))
    navigator.navigate(detail, singleTop = true)

    val encoded = encodeNavigatorState(navigator, spikeJson)
    val restored =
      decodeNavigatorState(encoded, SpikeGraph.ListRoute, topology, spikeJson, onRootBack = {})

    assertEquals(listOf<Route>(SpikeGraph.ListRoute, detail), restored.backStack.value)
  }

  @Test
  fun `a snapshot written by a Serializable route decodes with the generated serializer`() {
    val topology = GezginTopology(emptyMap(), emptyMap(), emptyMap())
    val legacy =
      """{"keys":[{"route":{"type":"dev.gezgin.core.SpikeGraph.DetailRoute","id":"a","name":null,""" +
        """"order":"RELEVANCE","filter":{"query":"q","page":1}},"id":0}],"nextId":1,""" +
        """"pendingSlots":[]}"""

    val restored =
      decodeNavigatorState(legacy, SpikeGraph.ListRoute, topology, spikeJson, onRootBack = {})

    assertEquals(
      listOf<Route>(SpikeGraph.DetailRoute("a", null, SortOrder.RELEVANCE, ComplexFilter("q", 1))),
      restored.backStack.value,
    )
  }
}
