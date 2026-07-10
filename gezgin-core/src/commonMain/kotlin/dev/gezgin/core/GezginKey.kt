package dev.gezgin.core
import kotlinx.serialization.Serializable

@Serializable
public data class GezginKey(
    val route: Route,                       // polimorfik (app SerializersModule'ü ile)
    val id: Long,                           // instance kimliği → Nav3 contentKey (§2.1)
    val flowPath: List<Long> = emptyList(), // kapsayan flow-instance zinciri, dış → iç (§8.1)
)
