package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import scala.util.Using
import io.circe.{ACursor, Decoder, HCursor, Json}
import org.goldenport.Consequence

/*
 * The versioned, provider-owned component-style catalog.  A style snapshot is
 * copied into descriptor schema v2 so admission never depends on ambient mode
 * defaults or a subsequently changed catalog.
 *
 * @since   Jul. 31, 2026
 * @version Aug. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentStyleId private (family: String, name: String, major: Int) {
  def canonical: String = if (family.isEmpty) s"$name@$major" else s"$family.$name@$major"
}

object ComponentStyleId {
  def createC(family: String, name: String, major: Int): Consequence[ComponentStyleId] =
    _create(family, name, major, "component style").fold(Consequence.argumentInvalid, Consequence.success)

  def parseC(value: String): Consequence[ComponentStyleId] =
    _parse(value, "component style").flatMap { case (family, name, major) => _create(family, name, major, "component style") }
      .fold(Consequence.argumentInvalid, Consequence.success)

  private[component] def _parse(value: String, label: String): Either[String, (String, String, Int)] = {
    val text = Option(value).getOrElse("")
    val at = text.lastIndexOf('@')
    val dot = if (at > 0) text.lastIndexOf('.', at - 1) else -1
    if (at <= 0 || at == text.length - 1)
      Left(s"Invalid $label identity: $text")
    else
      scala.util.Try(text.substring(at + 1).toInt).toOption match {
        case Some(major) if text.substring(at + 1) == major.toString && dot > 0 => Right((text.substring(0, dot), text.substring(dot + 1, at), major))
        case Some(major) if text.substring(at + 1) == major.toString && label == "component style" => Right(("", text.substring(0, at), major))
        case _ => Left(s"Invalid $label identity: $text")
      }
  }

  private[component] def _create(family: String, name: String, major: Int, label: String): Either[String, ComponentStyleId] =
    if ((_valid_part(family) || (label == "component style" && family.isEmpty)) && _valid_part(name) && major > 0)
      Right(ComponentStyleId(family, name, major))
    else
      Left(s"Invalid $label identity: ${Option(family).getOrElse("")}.${Option(name).getOrElse("")}@$major")

  private[component] def _valid_part(value: String): Boolean =
    Option(value).exists(_.matches("[a-z0-9-]+"))
}

final case class ComponentStyleProviderId private (value: String) {
  def canonical: String = value
}

object ComponentStyleProviderId {
  def createC(value: String): Consequence[ComponentStyleProviderId] =
    _create(value).fold(Consequence.argumentInvalid, Consequence.success)
  def parseC(value: String): Consequence[ComponentStyleProviderId] = createC(value)
  private[component] def _create(value: String): Either[String, ComponentStyleProviderId] =
    if (ComponentStyleId._valid_part(value))
      Right(ComponentStyleProviderId(value))
    else Left(s"Invalid component style provider identity: ${Option(value).getOrElse("")}")
}

final case class ComponentCapabilityId private (family: String, name: String, major: Int) {
  def canonical: String = s"$family.$name@$major"
}
object ComponentCapabilityId {
  def createC(family: String, name: String, major: Int): Consequence[ComponentCapabilityId] =
    _create(family, name, major).fold(Consequence.argumentInvalid, Consequence.success)
  def parseC(value: String): Consequence[ComponentCapabilityId] =
    ComponentStyleId._parse(value, "component capability").flatMap { case (f, n, m) => _create(f, n, m) }.fold(Consequence.argumentInvalid, Consequence.success)
  private[component] def _create(family: String, name: String, major: Int): Either[String, ComponentCapabilityId] =
    ComponentStyleId._create(family, name, major, "component capability").map(x => ComponentCapabilityId(x.family, x.name, x.major))
}

final case class ComponentCapabilityBundleId private (family: String, name: String, major: Int) {
  def canonical: String = s"$family.$name@$major"
}
object ComponentCapabilityBundleId {
  def createC(family: String, name: String, major: Int): Consequence[ComponentCapabilityBundleId] =
    _create(family, name, major).fold(Consequence.argumentInvalid, Consequence.success)
  def parseC(value: String): Consequence[ComponentCapabilityBundleId] =
    ComponentStyleId._parse(value, "component capability bundle").flatMap { case (f, n, m) => _create(f, n, m) }.fold(Consequence.argumentInvalid, Consequence.success)
  private[component] def _create(family: String, name: String, major: Int): Either[String, ComponentCapabilityBundleId] =
    ComponentStyleId._create(family, name, major, "component capability bundle").map(x => ComponentCapabilityBundleId(x.family, x.name, x.major))
}

final case class SubsystemCapabilityId private (family: String, name: String, major: Int) {
  def canonical: String = s"$family.$name@$major"
}
object SubsystemCapabilityId {
  def createC(family: String, name: String, major: Int): Consequence[SubsystemCapabilityId] =
    _create(family, name, major).fold(Consequence.argumentInvalid, Consequence.success)
  def parseC(value: String): Consequence[SubsystemCapabilityId] =
    ComponentStyleId._parse(value, "subsystem capability").flatMap { case (f, n, m) => _create(f, n, m) }.fold(Consequence.argumentInvalid, Consequence.success)
  private[component] def _create(family: String, name: String, major: Int): Either[String, SubsystemCapabilityId] =
    ComponentStyleId._create(family, name, major, "subsystem capability").map(x => SubsystemCapabilityId(x.family, x.name, x.major))
}

final case class ComponentStyleParameterSchema(
  `type`: String = "object",
  properties: Map[String, Json] = Map.empty,
  required: Vector[String] = Vector.empty,
  additionalProperties: Boolean = false
) {
  def validate: Either[String, Unit] =
    Either.cond(
      `type` == "object" && properties.isEmpty && required.isEmpty && !additionalProperties,
      (),
      "Component style parameter schema must be the closed empty object schema"
    )
}

final case class ComponentStyleDefinition(
  id: ComponentStyleId,
  parameterSchema: ComponentStyleParameterSchema,
  bundles: Vector[ComponentCapabilityBundleId],
  capabilities: Vector[ComponentCapabilityId],
  subsystemCapabilities: Vector[SubsystemCapabilityId]
)

final case class ComponentCapabilityBundleDefinition(
  id: ComponentCapabilityBundleId,
  bundles: Vector[ComponentCapabilityBundleId],
  capabilities: Vector[ComponentCapabilityId]
)

final case class ComponentStyleSnapshot(
  apiVersion: String,
  provider: ComponentStyleProviderId,
  id: ComponentStyleId,
  version: Int,
  parameterSchema: ComponentStyleParameterSchema,
  parameters: Map[String, Json],
  bundles: Vector[ComponentCapabilityBundleId],
  capabilities: Vector[ComponentCapabilityId],
  effectiveCapabilities: Vector[ComponentCapabilityId],
  subsystemCapabilities: Vector[SubsystemCapabilityId]
) {
  def canonicalJson: Json = ComponentStyleSnapshot.toJson(this)
  def render: String = canonicalJson.noSpaces
}

object ComponentStyleSnapshot {
  def toJson(snapshot: ComponentStyleSnapshot): Json =
    Json.obj(
      "apiVersion" -> Json.fromString(snapshot.apiVersion),
      "provider" -> Json.fromString(snapshot.provider.canonical),
      "id" -> Json.fromString(snapshot.id.canonical),
      "version" -> Json.fromInt(snapshot.version),
      "parameterSchema" -> _schema_json(snapshot.parameterSchema),
      "parameters" -> Json.obj(snapshot.parameters.toVector.sortBy(_._1)*),
      "provides" -> Json.obj(
        "bundles" -> Json.arr(snapshot.bundles.map(x => Json.fromString(x.canonical))*),
        "capabilities" -> Json.arr(snapshot.capabilities.map(x => Json.fromString(x.canonical))*),
        "effective" -> Json.arr(snapshot.effectiveCapabilities.map(x => Json.fromString(x.canonical))*)
      ),
      "requires" -> Json.obj(
        "subsystemCapabilities" -> Json.arr(snapshot.subsystemCapabilities.map(x => Json.fromString(x.canonical))*)
      )
    )

  private[component] def _schema_json(schema: ComponentStyleParameterSchema): Json =
    Json.obj(
      "type" -> Json.fromString(schema.`type`),
      "properties" -> Json.obj(schema.properties.toVector.sortBy(_._1)*),
      "required" -> Json.arr(schema.required.map(Json.fromString)*),
      "additionalProperties" -> Json.fromBoolean(schema.additionalProperties)
    )
}

final case class ComponentStyleCatalog(
  apiVersion: String,
  provider: ComponentStyleProviderId,
  bundles: Map[ComponentCapabilityBundleId, ComponentCapabilityBundleDefinition],
  styles: Map[ComponentStyleId, ComponentStyleDefinition]
) {
  def resolveC(id: ComponentStyleId): Consequence[ComponentStyleDefinition] =
    styles.get(id).map(Consequence.success).getOrElse(Consequence.argumentInvalid(s"Unknown component style: ${id.canonical}"))

  def expandC(definition: ComponentStyleDefinition): Consequence[ComponentStyleSnapshot] =
    ComponentStyleCatalog._expand(this, definition).fold(Consequence.argumentInvalid, Consequence.success)
}

object ComponentStyleCatalog {
  val API_VERSION = "cncf.textus/v1"
  val KIND = "ComponentStyleCatalog"
  val PROVIDER = "cncf"
  val RESOURCE_PATH = "META-INF/cncf/component-style-catalog.json"

  lazy val default: ComponentStyleCatalog =
    loadC().toOption.getOrElse(throw new IllegalStateException(s"Cannot load $RESOURCE_PATH"))

  def loadC(): Consequence[ComponentStyleCatalog] = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(RESOURCE_PATH))
    stream.map { in =>
      val text = Using.resource(in)(x => new String(x.readAllBytes(), StandardCharsets.UTF_8))
      parse(text).fold(Consequence.argumentInvalid, Consequence.success)
    }.getOrElse(Consequence.argumentInvalid(s"Missing component style catalog resource: $RESOURCE_PATH"))
  }

  def parse(text: String): Either[String, ComponentStyleCatalog] =
    for {
      json <- io.circe.parser.parse(text).left.map(x => s"Invalid component style catalog JSON: ${x.message}")
      cursor = json.hcursor
      _ <- _only(cursor, Set("apiVersion", "kind", "provider", "capabilityBundles", "componentStyles"), "catalog")
      api <- _required[String](cursor, "apiVersion", "catalog")
      _ <- Either.cond(api == API_VERSION, (), s"Unsupported component style catalog apiVersion: $api")
      kind <- _required[String](cursor, "kind", "catalog")
      _ <- Either.cond(kind == KIND, (), s"Invalid component style catalog kind: $kind")
      provider <- _required[String](cursor, "provider", "catalog").flatMap(ComponentStyleProviderId._create)
      _ <- Either.cond(provider.canonical == PROVIDER, (), s"Unsupported component style catalog provider: ${provider.canonical}")
      bundlejsons <- _required[Vector[Json]](cursor, "capabilityBundles", "catalog")
      bundlepairs <- _sequence(bundlejsons.zipWithIndex.map { case (x, i) => _bundle(x.hcursor, i) })
      _ <- _unique(bundlepairs.map(_.id.canonical), "component capability bundle")
      stylejsons <- _required[Vector[Json]](cursor, "componentStyles", "catalog")
      definitions <- _sequence(stylejsons.zipWithIndex.map { case (x, i) => _style(x.hcursor, i) })
      _ <- _unique(definitions.map(_.id.canonical), "component style")
      catalog = ComponentStyleCatalog(api, provider, bundlepairs.map(x => x.id -> x).toMap, definitions.map(x => x.id -> x).toMap)
      _ <- _validate_catalog(catalog)
    } yield catalog

  private[component] def _expand(catalog: ComponentStyleCatalog, definition: ComponentStyleDefinition): Either[String, ComponentStyleSnapshot] =
    for {
      _ <- definition.parameterSchema.validate
      _ <- _canonical_unique(definition.bundles.map(_.canonical), "component capability bundles")
      _ <- _canonical_unique(definition.capabilities.map(_.canonical), "component capabilities")
      _ <- _canonical_unique(definition.subsystemCapabilities.map(_.canonical), "subsystem capabilities")
      _ <- _major_compatible(definition.bundles.map(_.canonical), "component capability bundles")
      _ <- _major_compatible(definition.capabilities.map(_.canonical), "component capabilities")
      _ <- _major_compatible(definition.subsystemCapabilities.map(_.canonical), "subsystem capabilities")
      expanded <- _expand_bundles(catalog, definition.bundles, Set.empty)
      effective = (expanded ++ definition.capabilities).sortBy(_.canonical)
      _ <- _canonical_unique(effective.map(_.canonical), "effective component capabilities")
      _ <- _major_compatible(effective.map(_.canonical), "effective component capabilities")
    } yield ComponentStyleSnapshot(
      apiVersion = catalog.apiVersion,
      provider = catalog.provider,
      id = definition.id,
      version = definition.id.major,
      parameterSchema = definition.parameterSchema,
      parameters = Map.empty,
      bundles = definition.bundles,
      capabilities = definition.capabilities,
      effectiveCapabilities = effective,
      subsystemCapabilities = definition.subsystemCapabilities
    )

  private def _bundle(cursor: HCursor, index: Int): Either[String, ComponentCapabilityBundleDefinition] =
    for {
      _ <- _only(cursor, Set("id", "bundles", "capabilities"), s"capabilityBundles[$index]")
      id <- _required[String](cursor, "id", s"capabilityBundles[$index]").flatMap(x => ComponentCapabilityBundleId.parseC(x).toOption.toRight(s"Invalid component capability bundle identity: $x"))
      bundles <- _identities(cursor, "bundles", s"capabilityBundles[$index]")(ComponentCapabilityBundleId.parseC)
      capabilities <- _identities(cursor, "capabilities", s"capabilityBundles[$index]")(ComponentCapabilityId.parseC)
      _ <- _canonical_unique(bundles.map(_.canonical), s"capabilityBundles[$index].bundles")
      _ <- _canonical_unique(capabilities.map(_.canonical), s"capabilityBundles[$index].capabilities")
      _ <- _major_compatible(bundles.map(_.canonical), s"capabilityBundles[$index].bundles")
      _ <- _major_compatible(capabilities.map(_.canonical), s"capabilityBundles[$index].capabilities")
    } yield ComponentCapabilityBundleDefinition(id, bundles, capabilities)

  private def _style(cursor: HCursor, index: Int): Either[String, ComponentStyleDefinition] =
    for {
      _ <- _only(cursor, Set("id", "parameterSchema", "provides", "requires"), s"componentStyles[$index]")
      id <- _required[String](cursor, "id", s"componentStyles[$index]").flatMap(x => ComponentStyleId.parseC(x).toOption.toRight(s"Invalid component style identity: $x"))
      schema <- _schema(cursor.downField("parameterSchema"), s"componentStyles[$index].parameterSchema")
      provides = cursor.downField("provides")
      _ <- _only(provides, Set("bundles", "capabilities"), s"componentStyles[$index].provides")
      bundles <- _identities(provides, "bundles", s"componentStyles[$index].provides")(ComponentCapabilityBundleId.parseC)
      capabilities <- _identities(provides, "capabilities", s"componentStyles[$index].provides")(ComponentCapabilityId.parseC)
      requires = cursor.downField("requires")
      _ <- _only(requires, Set("subsystemCapabilities"), s"componentStyles[$index].requires")
      subsystem <- _identities(requires, "subsystemCapabilities", s"componentStyles[$index].requires")(SubsystemCapabilityId.parseC)
      _ <- _canonical_unique(bundles.map(_.canonical), s"componentStyles[$index].provides.bundles")
      _ <- _canonical_unique(capabilities.map(_.canonical), s"componentStyles[$index].provides.capabilities")
      _ <- _canonical_unique(subsystem.map(_.canonical), s"componentStyles[$index].requires.subsystemCapabilities")
    } yield ComponentStyleDefinition(id, schema, bundles, capabilities, subsystem)

  private[component] def snapshotC(json: Json, catalog: ComponentStyleCatalog = default): Consequence[ComponentStyleSnapshot] =
    _snapshot(json.hcursor, catalog).fold(Consequence.argumentInvalid, Consequence.success)

  private def _snapshot(cursor: HCursor, catalog: ComponentStyleCatalog): Either[String, ComponentStyleSnapshot] =
    for {
      _ <- _only(cursor, Set("apiVersion", "provider", "id", "version", "parameterSchema", "parameters", "provides", "requires"), "componentStyle")
      api <- _required[String](cursor, "apiVersion", "componentStyle")
      _ <- Either.cond(api == catalog.apiVersion, (), s"Unsupported component style snapshot apiVersion: $api")
      provider <- _required[String](cursor, "provider", "componentStyle").flatMap(ComponentStyleProviderId._create)
      _ <- Either.cond(provider == catalog.provider, (), s"Component style snapshot provider does not match catalog: ${provider.canonical}")
      id <- _required[String](cursor, "id", "componentStyle").flatMap(x => ComponentStyleId.parseC(x).toOption.toRight(s"Invalid component style identity: $x"))
      version <- _required[Int](cursor, "version", "componentStyle")
      _ <- Either.cond(version == id.major, (), s"Component style snapshot version does not match id: ${id.canonical}")
      definition <- catalog.styles.get(id).toRight(s"Unknown component style: ${id.canonical}")
      schema <- _schema(cursor.downField("parameterSchema"), "componentStyle.parameterSchema")
      _ <- Either.cond(schema == definition.parameterSchema, (), s"Component style snapshot parameter schema does not match catalog: ${id.canonical}")
      parameters <- _required[Map[String, Json]](cursor, "parameters", "componentStyle")
      _ <- Either.cond(parameters.isEmpty, (), "Component style snapshot parameters must be empty")
      provides = cursor.downField("provides")
      _ <- _only(provides, Set("bundles", "capabilities", "effective"), "componentStyle.provides")
      bundles <- _identities(provides, "bundles", "componentStyle.provides")(ComponentCapabilityBundleId.parseC)
      capabilities <- _identities(provides, "capabilities", "componentStyle.provides")(ComponentCapabilityId.parseC)
      effective <- _identities(provides, "effective", "componentStyle.provides")(ComponentCapabilityId.parseC)
      requires = cursor.downField("requires")
      _ <- _only(requires, Set("subsystemCapabilities"), "componentStyle.requires")
      subsystem <- _identities(requires, "subsystemCapabilities", "componentStyle.requires")(SubsystemCapabilityId.parseC)
      expected <- _expand(catalog, definition)
      actual = ComponentStyleSnapshot(api, provider, id, version, schema, parameters, bundles, capabilities, effective, subsystem)
      _ <- Either.cond(actual == expected, (), s"Component style snapshot does not match catalog: ${id.canonical}")
    } yield actual

  private def _schema(cursor: ACursor, context: String): Either[String, ComponentStyleParameterSchema] =
    for {
      _ <- _only(cursor, Set("type", "properties", "required", "additionalProperties"), context)
      kind <- _required[String](cursor, "type", context)
      properties <- _required[Map[String, Json]](cursor, "properties", context)
      required <- _required[Vector[String]](cursor, "required", context)
      additional <- _required[Boolean](cursor, "additionalProperties", context)
      schema = ComponentStyleParameterSchema(kind, properties, required, additional)
      _ <- schema.validate
    } yield schema

  private def _identities[A](cursor: ACursor, field: String, context: String)(parse: String => Consequence[A]): Either[String, Vector[A]] =
    _required[Vector[String]](cursor, field, context).flatMap { values =>
      _sequence[A](values.map { value =>
        parse(value).toOption.toRight(s"Invalid $context.$field identity: $value")
      })
    }

  private def _validate_catalog(catalog: ComponentStyleCatalog): Either[String, Unit] =
    for {
      bundleclosures <- _sequence(catalog.bundles.keys.toVector.sortBy(_.canonical).map { id =>
        _expand_bundles(catalog, Vector(id), Set.empty).map(id -> _)
      })
      _ <- _sequence(bundleclosures.map { case (id, capabilities) =>
        _validate_capability_closure(capabilities, s"component capability bundle ${id.canonical} closure")
      })
      _ <- _sequence(catalog.styles.values.toVector.sortBy(_.id.canonical).map(_expand(catalog, _)))
    } yield ()

  private def _expand_bundles(
    catalog: ComponentStyleCatalog,
    ids: Vector[ComponentCapabilityBundleId],
    ancestors: Set[ComponentCapabilityBundleId]
  ): Either[String, Vector[ComponentCapabilityId]] =
    for {
      bundleclosure <- _bundle_closure(catalog, ids, ancestors)
      _ <- _major_compatible(bundleclosure.map(_.canonical), "component capability bundle closure")
      capabilities <- _sequence(ids.map { id =>
      if (ancestors.contains(id))
        Left(s"Cyclic component capability bundle reference: ${id.canonical}")
      else
        catalog.bundles.get(id).toRight(s"Unknown component capability bundle: ${id.canonical}").flatMap { definition =>
          for {
            _ <- _canonical_unique(definition.bundles.map(_.canonical), s"component capability bundle ${id.canonical} bundles")
            _ <- _canonical_unique(definition.capabilities.map(_.canonical), s"component capability bundle ${id.canonical} capabilities")
            _ <- _major_compatible(definition.bundles.map(_.canonical), s"component capability bundle ${id.canonical} bundles")
            _ <- _major_compatible(definition.capabilities.map(_.canonical), s"component capability bundle ${id.canonical} capabilities")
            nested <- _expand_bundles(catalog, definition.bundles, ancestors + id)
          } yield nested ++ definition.capabilities
        }
      })
    } yield capabilities.flatten

  private def _bundle_closure(
    catalog: ComponentStyleCatalog,
    ids: Vector[ComponentCapabilityBundleId],
    ancestors: Set[ComponentCapabilityBundleId]
  ): Either[String, Vector[ComponentCapabilityBundleId]] =
    _sequence(ids.map { id =>
      if (ancestors.contains(id))
        Left(s"Cyclic component capability bundle reference: ${id.canonical}")
      else
        catalog.bundles.get(id).toRight(s"Unknown component capability bundle: ${id.canonical}").flatMap { definition =>
          _bundle_closure(catalog, definition.bundles, ancestors + id).map(id +: _)
        }
    }).map(_.flatten)

  private def _required[A: Decoder](cursor: ACursor, field: String, context: String): Either[String, A] =
    cursor.get[A](field).left.map(x => s"Component style $context requires $field: ${x.message}")

  private def _only(cursor: ACursor, expected: Set[String], context: String): Either[String, Unit] =
    cursor.success.toRight(s"Component style $context must be an object").flatMap { hcursor =>
      val unknown = hcursor.keys.toVector.flatten.filterNot(expected).sorted
      Either.cond(unknown.isEmpty, (), s"Unknown component style $context fields: ${unknown.mkString(", ")}")
    }

  private def _canonical_unique(values: Vector[String], context: String): Either[String, Unit] =
    Either.cond(values == values.distinct && values == values.sorted, (), s"$context must be unique and canonical-order")

  private def _unique(values: Vector[String], context: String): Either[String, Unit] =
    Either.cond(values.distinct.size == values.size, (), s"Duplicate $context identities: ${values.groupBy(value => value).collect { case (k, xs) if xs.size > 1 => k }.toVector.sorted.mkString(", ")}")

  private def _major_compatible(values: Vector[String], context: String): Either[String, Unit] = {
    val incompatible = values.groupBy(_.takeWhile(_ != '@')).collect {
      case (identity, versions) if versions.distinct.size > 1 => identity
    }.toVector.sorted
    Either.cond(incompatible.isEmpty, (), s"$context contains version-incompatible identities: ${incompatible.mkString(", ")}")
  }

  private def _validate_capability_closure(
    capabilities: Vector[ComponentCapabilityId],
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _unique(capabilities.map(_.canonical), context)
      _ <- _major_compatible(capabilities.map(_.canonical), context)
    } yield ()

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]])((z, x) => z.flatMap(xs => x.map(xs :+ _)))
}
