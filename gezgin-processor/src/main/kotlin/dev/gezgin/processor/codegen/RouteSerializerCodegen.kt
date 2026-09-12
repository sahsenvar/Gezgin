package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import dev.gezgin.processor.model.GraphModel
import dev.gezgin.processor.model.ParamModel
import dev.gezgin.processor.model.RouteModel
import dev.gezgin.processor.serial.SerialKind

private const val GENERATED_FILE = "GezginRouteSerializers"
private const val SERIALIZATION_PKG = "kotlinx.serialization"
private const val DESCRIPTORS_PKG = "$SERIALIZATION_PKG.descriptors"
private const val ENCODING_PKG = "$SERIALIZATION_PKG.encoding"
private const val BUILTINS_PKG = "$SERIALIZATION_PKG.builtins"

private val K_SERIALIZER = ClassName(SERIALIZATION_PKG, "KSerializer")
private val SERIAL_DESCRIPTOR = ClassName(DESCRIPTORS_PKG, "SerialDescriptor")
private val PRIMITIVE_KIND = ClassName(DESCRIPTORS_PKG, "PrimitiveKind")
private val ENCODER = ClassName(ENCODING_PKG, "Encoder")
private val DECODER = ClassName(ENCODING_PKG, "Decoder")
private val SERIALIZATION_EXCEPTION = ClassName(SERIALIZATION_PKG, "SerializationException")
private val BUILD_CLASS_SERIAL_DESCRIPTOR =
  MemberName(DESCRIPTORS_PKG, "buildClassSerialDescriptor")
private val PRIMITIVE_SERIAL_DESCRIPTOR = MemberName(DESCRIPTORS_PKG, "PrimitiveSerialDescriptor")
private val LIST_SERIALIZER = MemberName(BUILTINS_PKG, "ListSerializer")
private val BUILTIN_SERIALIZER = MemberName(BUILTINS_PKG, "serializer")
private val NULLABLE = MemberName(BUILTINS_PKG, "nullable")
private val ENCODE_STRUCTURE = MemberName(ENCODING_PKG, "encodeStructure")
private val DECODE_STRUCTURE = MemberName(ENCODING_PKG, "decodeStructure")

/** Emits explicit serializers for routes and bare enums discovered by the graph model. */
internal object RouteSerializerCodegen {

  fun generate(model: GraphModel, packageName: String): FileSpec? {
    val routeTypes =
      model.routes.filterNot(RouteModel::isSerializable).sortedBy(RouteModel::fqName).map {
        routeSerializer(it, packageName)
      }
    val enumTypes =
      bareEnumTypes(model)
        .filter { it.packageName == packageName }
        .sortedBy(ClassName::canonicalName)
        .map(::enumSerializer)

    if (routeTypes.isEmpty() && enumTypes.isEmpty()) return null

    return FileSpec.builder(packageName, GENERATED_FILE)
      .apply {
        routeTypes.forEach(::addType)
        enumTypes.forEach(::addType)
      }
      .build()
  }

  /** Generates one serializer file for each enum package outside the graph's target package. */
  internal fun generateEnumSerializers(model: GraphModel, packageName: String): List<FileSpec> =
    bareEnumTypes(model)
      .filter { it.packageName != packageName }
      .groupBy(ClassName::packageName)
      .toSortedMap()
      .map { (enumPackage, enumTypes) ->
        FileSpec.builder(enumPackage, GENERATED_FILE)
          .apply {
            enumTypes.sortedBy(ClassName::canonicalName).forEach { addType(enumSerializer(it)) }
          }
          .build()
      }

  /** Returns the generated route serializer class name shared with topology codegen. */
  internal fun serializerName(route: RouteModel, packageName: String): ClassName =
    ClassName(packageName, "${route.simpleName}GezginSerializer")

  private fun routeSerializer(route: RouteModel, packageName: String): TypeSpec {
    val routeType = ClassName.bestGuess(route.fqName)
    return TypeSpec.objectBuilder(serializerName(route, packageName).simpleName)
      .addModifiers(KModifier.INTERNAL)
      .addSuperinterface(K_SERIALIZER.parameterizedBy(routeType))
      .addProperty(descriptorProperty(route))
      .addFunction(serializeFunction(route, routeType))
      .addFunction(deserializeFunction(route, routeType))
      .build()
  }

  private fun descriptorProperty(route: RouteModel): PropertySpec {
    val body = CodeBlock.builder().add("%M(%S)", BUILD_CLASS_SERIAL_DESCRIPTOR, route.fqName)
    if (route.ctorParams.isNotEmpty()) {
      body.add(" {\n").indent()
      route.ctorParams.forEach { param ->
        body.add("element(%S, %L.descriptor)\n", param.name, serializerRef(param))
      }
      body.unindent().add("}")
    }

    return PropertySpec.builder("descriptor", SERIAL_DESCRIPTOR)
      .addModifiers(KModifier.OVERRIDE)
      .initializer(body.build())
      .build()
  }

  private fun serializeFunction(route: RouteModel, routeType: ClassName): FunSpec {
    val body =
      CodeBlock.builder().add("encoder.%M(descriptor) {", ENCODE_STRUCTURE).apply {
        if (route.ctorParams.isNotEmpty()) {
          add("\n").indent()
          route.ctorParams.forEachIndexed { index, param ->
            add(
              "encodeSerializableElement(descriptor, %L, %L, value.%N)\n",
              index,
              serializerRef(param),
              param.name,
            )
          }
          unindent()
        }
        add("}")
      }

    return FunSpec.builder("serialize")
      .addModifiers(KModifier.OVERRIDE)
      .addParameter("encoder", ENCODER)
      .addParameter("value", routeType)
      .addCode(body.build())
      .build()
  }

  private fun deserializeFunction(route: RouteModel, routeType: ClassName): FunSpec {
    val body =
      CodeBlock.builder().add("return decoder.%M(descriptor) {\n", DECODE_STRUCTURE).indent()
    if (route.ctorParams.isEmpty()) {
      body.add("while (decodeElementIndex(descriptor) != -1) {}\n")
      body.add("%T\n", routeType)
    } else {
      route.ctorParams.forEach { param ->
        body.add("var %N: %T = null\n", param.name, param.typeName.copy(nullable = true))
      }
      route.ctorParams.forEachIndexed { index, _ -> body.add("var seen%L = false\n", index) }
      body.add("while (true) {\n").indent()
      body.add("when (val index = decodeElementIndex(descriptor)) {\n").indent()
      route.ctorParams.forEachIndexed { index, param ->
        body.add(
          "%L -> { %N = decodeSerializableElement(descriptor, %L, %L); seen%L = true }\n",
          index,
          param.name,
          index,
          serializerRef(param),
          index,
        )
      }
      body.add("-1 -> break\n")
      body.add("else -> throw %T(\"unexpected index \$index\")\n", SERIALIZATION_EXCEPTION)
      body.unindent().add("}\n")
      body.unindent().add("}\n")
      route.ctorParams.forEachIndexed { index, param ->
        body.add(
          "if (!seen%L) throw %T(%S)\n",
          index,
          SERIALIZATION_EXCEPTION,
          "${route.fqName}: missing '${param.name}'",
        )
      }
      body.add(routeConstructor(route, routeType)).add("\n")
    }
    body.unindent().add("}")

    return FunSpec.builder("deserialize")
      .addModifiers(KModifier.OVERRIDE)
      .addParameter("decoder", DECODER)
      .returns(routeType)
      .addCode(body.build())
      .build()
  }

  private fun routeConstructor(route: RouteModel, routeType: ClassName): CodeBlock {
    if (route.ctorParams.isEmpty()) return CodeBlock.of("%T", routeType)

    val body = CodeBlock.builder().add("%T(", routeType)
    route.ctorParams.forEachIndexed { index, param ->
      if (index > 0) body.add(", ")
      body.add("%N = %N", param.name, param.name)
      if (!param.isNullable) body.add(" as %T", param.typeName.copy(nullable = false))
    }
    return body.add(")").build()
  }

  private fun serializerRef(param: ParamModel): CodeBlock =
    serializerRef(param.kind, param.typeName, param.isNullable)

  private fun serializerRef(kind: SerialKind, typeName: TypeName, isNullable: Boolean): CodeBlock {
    val bare =
      when (kind) {
        is SerialKind.Builtin -> CodeBlock.of("%L.%M()", kind.fq, BUILTIN_SERIALIZER)
        is SerialKind.ListOf -> {
          val elementType =
            (typeName.copy(nullable = false) as ParameterizedTypeName).typeArguments.single()
          CodeBlock.of(
            "%M(%L)",
            LIST_SERIALIZER,
            serializerRef(kind.element, elementType, elementType.isNullable),
          )
        }
        else -> SerializerRef.of(kind, typeName, isNullable = false)
      }
    return if (isNullable) CodeBlock.of("%L.%M", bare, NULLABLE) else bare
  }

  private fun enumSerializer(enumType: ClassName): TypeSpec {
    val serializerType = SerializerRef.enumSerializerName(enumType)
    return TypeSpec.objectBuilder(serializerType.simpleName)
      .addModifiers(KModifier.INTERNAL)
      .addSuperinterface(K_SERIALIZER.parameterizedBy(enumType))
      .addProperty(
        PropertySpec.builder("descriptor", SERIAL_DESCRIPTOR)
          .addModifiers(KModifier.OVERRIDE)
          .initializer(
            "%M(%S, %T.STRING)",
            PRIMITIVE_SERIAL_DESCRIPTOR,
            enumType.canonicalName,
            PRIMITIVE_KIND,
          )
          .build()
      )
      .addFunction(
        FunSpec.builder("serialize")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("encoder", ENCODER)
          .addParameter("value", enumType)
          .addStatement("encoder.encodeString(value.name)")
          .build()
      )
      .addFunction(
        FunSpec.builder("deserialize")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("decoder", DECODER)
          .returns(enumType)
          .addStatement("return %T.valueOf(decoder.decodeString())", enumType)
          .build()
      )
      .build()
  }

  private fun bareEnumTypes(model: GraphModel): List<ClassName> {
    val enumTypes = linkedMapOf<String, ClassName>()
    model.routes.forEach { route ->
      route.ctorParams.forEach { param -> collectBareEnums(param.kind, param.typeName, enumTypes) }
      collectResultEnum(route.resultTypeKind, route.resultTypeFq, enumTypes)
    }
    model.graphs.forEach { graph ->
      collectResultEnum(graph.resultTypeKind, graph.resultTypeFq, enumTypes)
    }
    return enumTypes.values.toList()
  }

  private fun collectBareEnums(
    kind: SerialKind,
    typeName: TypeName,
    enumTypes: MutableMap<String, ClassName>,
  ) {
    when (kind) {
      SerialKind.BareEnum -> {
        val enumType = typeName.copy(nullable = false) as? ClassName ?: return
        enumTypes[enumType.canonicalName] = enumType
      }
      is SerialKind.ListOf -> {
        val listType = typeName.copy(nullable = false) as? ParameterizedTypeName ?: return
        collectBareEnums(kind.element, listType.typeArguments.single(), enumTypes)
      }
      else -> Unit
    }
  }

  private fun collectResultEnum(
    kind: SerialKind?,
    typeFq: String?,
    enumTypes: MutableMap<String, ClassName>,
  ) {
    if (kind != SerialKind.BareEnum || typeFq == null) return
    val enumType = ClassName.bestGuess(typeFq)
    enumTypes[enumType.canonicalName] = enumType
  }
}
