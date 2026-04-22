package org.goldenport.cncf.subsystem

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.RecordDecoder
import org.goldenport.cncf.component.ComponentDescriptor
import org.goldenport.cncf.component.ComponentDescriptorLoader
import org.goldenport.cncf.component.DescriptorRecordLoader
import org.goldenport.cncf.security.OperationAuthorizationRule

/*
 * @since   Apr.  7, 2026
 *  version Apr. 11, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class GenericSubsystemAuthenticationProviderBinding(
  name: String,
  component: String,
  kind: Option[String] = None,
  enabled: Option[Boolean] = None,
  priority: Option[Int] = None,
  schemes: Vector[String] = Vector.empty,
  isDefault: Option[Boolean] = None
)

final case class GenericSubsystemAuthenticationBinding(
  convention: Option[String] = None,
  fallbackPrivilege: Option[String] = None,
  providers: Vector[GenericSubsystemAuthenticationProviderBinding] = Vector.empty
)

final case class GenericSubsystemSecurityBinding(
  authentication: Option[GenericSubsystemAuthenticationBinding] = None
)

final case class GenericSubsystemBuiltinBinding(
  exclude: Vector[String] = Vector.empty
)

final case class GenericSubsystemPortBinding(
  name: String,
  service: Option[String] = None,
  operation: Option[String] = None
) {
  def toRecord: Record =
    Record.data(
      "name" -> name,
      "service" -> service.getOrElse(""),
      "operation" -> operation.getOrElse("")
    )
}

final case class GenericSubsystemComponentBinding(
  componentName: String,
  version: Option[String] = None,
  coordinate: Option[String] = None,
  extensionBindings: Record = Record.empty,
  api: Vector[GenericSubsystemPortBinding] = Vector.empty,
  spi: Vector[GenericSubsystemPortBinding] = Vector.empty
) {
  def componentVersion: Option[String] =
    version.orElse(coordinate.flatMap(GenericSubsystemDescriptor.coordinateVersion))

  def runtimeComponentName: String =
    GenericSubsystemDescriptor.runtimeComponentName(componentName)

  def toComponentDescriptor: ComponentDescriptor =
    ComponentDescriptor(
      name = Some(componentName),
      version = componentVersion,
      componentName = Some(componentName),
      extensionBindings = extensionBindings
    )

  def portRecord: Record =
    Record.data(
      "component" -> componentName,
      "api" -> api.map(_.toRecord),
      "spi" -> spi.map(_.toRecord)
    )
}

final case class GenericSubsystemResolvedWiringBinding(
  fromComponent: String,
  fromService: String,
  fromOperation: String,
  fromApi: Option[String] = None,
  toComponent: String,
  toSpi: Option[String] = None,
  toService: String,
  toOperation: String,
  glue: Record = Record.empty,
  mode: String = "api-spi-routing"
) {
  def toRecord: Record =
    Record.data(
      "from" -> Record.data(
        "component" -> fromComponent,
        "service" -> fromService,
        "operation" -> fromOperation,
        "api" -> fromApi.getOrElse("")
      ),
      "to" -> Record.data(
        "component" -> toComponent,
        "spi" -> toSpi.getOrElse(""),
        "service" -> toService,
        "operation" -> toOperation
      ),
      "glue" -> glue,
      "mode" -> mode
    )
}

final case class GenericSubsystemAssemblyDescriptorSource(
  record: Record,
  source: String,
  path: Option[Path] = None
)

final case class GenericSubsystemDescriptor(
  path: Path,
  subsystemName: String,
  version: Option[String] = None,
  componentBindings: Vector[GenericSubsystemComponentBinding] = Vector.empty,
  extensions: Map[String, String] = Map.empty,
  config: Map[String, String] = Map.empty,
  wiring: Record = Record.empty,
  assemblyDescriptor: Option[GenericSubsystemAssemblyDescriptorSource] = None,
  security: Option[GenericSubsystemSecurityBinding] = None,
  builtin: Option[GenericSubsystemBuiltinBinding] = None,
  operationAuthorization: Map[String, OperationAuthorizationRule] = Map.empty
) {
  def componentVersion: Option[String] =
    version.orElse(componentBindings.headOption.flatMap(_.componentVersion))

  def componentName: String =
    componentBindings.headOption.map(_.componentName).getOrElse("")

  def coordinate: String =
    componentBindings.headOption.flatMap(_.coordinate).getOrElse("")

  def componentExtensionBindings: Record =
    componentBindings.headOption.map(_.extensionBindings).getOrElse(Record.empty)

  def runtimeComponentName: String =
    componentBindings.headOption.map(_.runtimeComponentName).getOrElse("")

  def runtimeComponentNames: Vector[String] =
    componentBindings.map(_.runtimeComponentName)

  def toComponentDescriptors: Vector[ComponentDescriptor] =
    componentBindings.map(_.toComponentDescriptor)

  def declaredPorts: Vector[Record] =
    componentBindings
      .filterNot(x => x.api.isEmpty && x.spi.isEmpty)
      .map(_.portRecord)

  def resolvedWiringBindings: Vector[Record] =
    resolvedWiring.map(_.toRecord)

  def resolvedWiring: Vector[GenericSubsystemResolvedWiringBinding] =
    GenericSubsystemDescriptor.resolveAssemblyWiringBindings(this)
      .filter(_.nonEmpty)
      .getOrElse(GenericSubsystemDescriptor.resolveWiringBindings(this))

  def operationAuthorizationRule(
    selector: String
  ): Option[OperationAuthorizationRule] =
    operationAuthorization.get(selector)
}

object GenericSubsystemDescriptor {
  final case class Shape(
    subsystemName: String,
    version: Option[String],
    componentBindings: Vector[GenericSubsystemComponentBinding],
    extensions: Map[String, String],
    config: Map[String, String],
    wiring: Record,
    security: Option[GenericSubsystemSecurityBinding],
    builtin: Option[GenericSubsystemBuiltinBinding],
    operationAuthorization: Map[String, OperationAuthorizationRule]
  )

  private val _canonical_descriptor_files = Vector(
    "descriptor.json",
    "descriptor.yaml",
    "descriptor.yml",
    "descriptor.conf",
    "descriptor.hocon",
    "descriptor.xml",
    "subsystem-descriptor.json",
    "subsystem-descriptor.yaml",
    "subsystem-descriptor.yml",
    "subsystem-descriptor.conf",
    "subsystem-descriptor.hocon",
    "subsystem-descriptor.xml"
  )

  private val _canonical_assembly_descriptor_files = Vector(
    "assembly-descriptor.yaml",
    "assembly-descriptor.yml",
    "assembly-descriptor.json",
    "assembly-descriptor.conf",
    "assembly-descriptor.hocon",
    "assembly-descriptor.xml"
  )

  def load(path: Path): Consequence[GenericSubsystemDescriptor] =
    if (!Files.exists(path))
      Consequence.resourceNotFound(s"subsystem descriptor path does not exist: ${path}")
    else if (Files.isDirectory(path))
      _resolve_descriptor_file(path) match {
        case Some(file) => _load_file(file)
        // Descriptor-first path is the current design.
        // manifest.json fallback remains only as a compatibility memo.
        case None => _load_manifest_compat(path)
      }
    else if (_is_archive_file(path))
      _load_archive_file(path)
    else
      _load_file(path)

  def loadArchive(path: Path): Consequence[GenericSubsystemDescriptor] =
    load(path)

  def loadAssemblyDescriptor(path: Path): Option[GenericSubsystemAssemblyDescriptorSource] =
    if (!Files.exists(path))
      None
    else if (Files.isDirectory(path))
      _resolve_assembly_descriptor_file(path).flatMap(_load_record(_, "config"))
    else if (_is_archive_file(path))
      _load_assembly_descriptor_from_archive(path, "config")
    else
      _load_record(path, "config")

  def loadAdjacentAssemblyDescriptor(path: Path): Option[GenericSubsystemAssemblyDescriptorSource] =
    if (!Files.exists(path))
      None
    else if (Files.isDirectory(path))
      _resolve_assembly_descriptor_file(path).flatMap(_load_record(_, "sar"))
    else if (_is_archive_file(path))
      _load_assembly_descriptor_from_archive(path, "sar")
    else
      Option(path.getParent).flatMap(parent => _resolve_assembly_descriptor_file(parent).flatMap(_load_record(_, "sar")))

  def looksLikeArchiveDirectory(path: Path): Boolean = {
    val componentdir = path.resolve("component")
    Files.isDirectory(path) && Files.isDirectory(componentdir) &&
      // Descriptor files are the intended signal.
      // manifest.json is still accepted only for compatibility.
      (_resolve_descriptor_file(path).nonEmpty || Files.exists(path.resolve("meta").resolve("manifest.json"))) &&
      _contains_component_archive(componentdir)
  }

  private def _contains_component_archive(componentdir: Path): Boolean = {
    val stream = Files.list(componentdir)
    try {
      stream.iterator().asScala.exists { p =>
        (Files.isRegularFile(p) && p.getFileName.toString.toLowerCase.endsWith(".car")) ||
        (Files.isDirectory(p) && ComponentDescriptorLoader.looksLikeArchiveDirectory(p))
      }
    } finally {
      stream.close()
    }
  }

  def coordinateParts(coordinate: String): Vector[String] =
    coordinate.split(":").toVector.map(_.trim).filter(_.nonEmpty)

  def coordinateArtifact(coordinate: String): Option[String] =
    coordinateParts(coordinate) match {
      case Vector(_, artifact, _) => Some(artifact)
      case Vector(artifact, _) => Some(artifact)
      case _ => None
    }

  def coordinateVersion(coordinate: String): Option[String] =
    coordinateParts(coordinate) match {
      case Vector(_, _, version) => Some(version)
      case Vector(_, version) => Some(version)
      case _ => None
    }

  def runtimeComponentName(componentName: String): String = {
    val normalized = componentName.trim
    val stripped =
      if (normalized.startsWith("textus-")) normalized.stripPrefix("textus-")
      else if (normalized.startsWith("textus_")) normalized.stripPrefix("textus_")
      else normalized
    if (stripped.exists(ch => ch == '-' || ch == '_'))
      stripped
        .split("[-_]")
        .toVector
        .filter(_.nonEmpty)
        .map(_.toLowerCase.capitalize)
        .mkString
    else
      stripped
  }

  private def _resolve_descriptor_file(path: Path): Option[Path] =
    _canonical_descriptor_files
      .map(path.resolve(_).normalize)
      .find(Files.isRegularFile(_))

  private def _resolve_assembly_descriptor_file(path: Path): Option[Path] =
    _canonical_assembly_descriptor_files
      .map(path.resolve(_).normalize)
      .find(Files.isRegularFile(_))

  private def _is_archive_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase
    name.endsWith(".sar") || name.endsWith(".zip")
  }

  private def _load_record(
    path: Path,
    source: String
  ): Option[GenericSubsystemAssemblyDescriptorSource] =
    DescriptorRecordLoader.load(path).toOption.flatMap(_.headOption).map { record =>
      GenericSubsystemAssemblyDescriptorSource(record, source, Some(path))
    }

  private def _load_assembly_descriptor_from_archive(
    path: Path,
    source: String
  ): Option[GenericSubsystemAssemblyDescriptorSource] = {
    val uri = URI.create(s"jar:${path.toUri}")
    Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
      _resolve_assembly_descriptor_file(fs.getPath("/")).flatMap { file =>
        DescriptorRecordLoader.load(file).toOption.flatMap(_.headOption).map { record =>
          GenericSubsystemAssemblyDescriptorSource(record, source, Some(path))
        }
      }
    }
  }

  private def _load_archive_file(path: Path): Consequence[GenericSubsystemDescriptor] = {
    val uri = URI.create(s"jar:${path.toUri}")
    Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
      val root = fs.getPath("/")
      _resolve_descriptor_file(root) match {
        case Some(file) =>
          DescriptorRecordLoader.load(file).flatMap { records =>
            records.headOption.map { rec =>
              val assemblyDescriptor = _resolve_assembly_descriptor_file(root).flatMap { file =>
                DescriptorRecordLoader.load(file).toOption.flatMap(_.headOption).map { record =>
                  GenericSubsystemAssemblyDescriptorSource(record, "sar", Some(path))
                }
              }
              _from_record(path, rec, assemblyDescriptor)
            }.getOrElse(Consequence.resourceInvalid(s"subsystem descriptor is empty in archive: ${path}"))
          }
        case None =>
          Consequence.resourceNotFound(s"subsystem descriptor not found in archive: ${path}")
      }
    }
  }

  private def _load_file(path: Path): Consequence[GenericSubsystemDescriptor] =
    DescriptorRecordLoader.load(path).flatMap { records =>
      records.headOption.map(_from_record(path, _)).getOrElse(Consequence.resourceInvalid(s"subsystem descriptor is empty: ${path}"))
    }

  private def _load_manifest_compat(path: Path): Consequence[GenericSubsystemDescriptor] =
    // Compatibility-only path. New subsystem archives are expected to carry
    // an explicit descriptor instead of relying on meta/manifest.json.
    org.goldenport.cncf.component.ArchiveManifest.load(path, "sar").map { m =>
      GenericSubsystemDescriptor(
        path = path,
        subsystemName = m.subsystem.getOrElse(m.name),
        version = Some(m.version),
        componentBindings = Vector.empty,
        extensions = m.extensions,
        config = m.config,
        wiring = Record.empty,
        assemblyDescriptor = loadAdjacentAssemblyDescriptor(path),
        security = None,
        builtin = None,
        operationAuthorization = Map.empty
      )
    }

  private def _from_record(
    path: Path,
    rec: Record,
    assemblyDescriptor: Option[GenericSubsystemAssemblyDescriptorSource] = None
  ): Consequence[GenericSubsystemDescriptor] = {
    val assemblyDescriptor0 =
      if (_is_archive_file(path)) assemblyDescriptor
      else assemblyDescriptor.orElse(loadAdjacentAssemblyDescriptor(path))
    summon[RecordDecoder[Shape]].fromRecord(rec).map { s =>
      GenericSubsystemDescriptor(
        path = path,
        subsystemName = s.subsystemName,
        version = s.version,
        componentBindings = s.componentBindings,
        extensions = s.extensions,
        config = s.config,
        wiring = s.wiring,
        assemblyDescriptor = assemblyDescriptor0,
        security = s.security,
        builtin = s.builtin,
        operationAuthorization = s.operationAuthorization
      )
    }.leftMap { c =>
      c.copy(observation = c.observation.copy(cause = c.observation.cause.withMessage(s"${c.displayMessage} in ${path}")))
    }
  }

  private def _bindings_from_record(path: Path, rec: Record): Vector[GenericSubsystemComponentBinding] =
    rec.getAny("components") match {
      case Some(xs: Seq[?]) =>
        xs.toVector.flatMap(x => _any_to_record(x).flatMap(_binding_from_record(path, _, None)))
      case _ =>
        rec.getRecord("component") match {
          case Some(r) if _string(rec, "component").isEmpty =>
            r.asMap.toVector.flatMap { case (k, v) =>
              _any_to_record(v).flatMap(_binding_from_record(path, _, Some(k)))
            }
          case _ =>
            _binding_from_record(path, rec, None).toVector
        }
    }

  private def _binding_from_record(path: Path, rec: Record, defaultName: Option[String]): Option[GenericSubsystemComponentBinding] = {
    val componentName = _string(rec, "component", "componentName", "name").orElse(defaultName)
    componentName.map { name =>
      val version = _string(rec, "version")
      val coordinate = _string(rec, "coordinate")
      coordinate.foreach { c =>
        val artifact = coordinateArtifact(c).getOrElse(throw new IllegalArgumentException(s"invalid component coordinate: $c"))
        val cversion = coordinateVersion(c).getOrElse(throw new IllegalArgumentException(s"invalid component coordinate: $c"))
        require(artifact == name, s"component coordinate artifact must match component name: component=$name coordinate=$c")
        version.foreach(v => require(v == cversion, s"component version must match coordinate version: component=$name version=$v coordinate=$c"))
      }
      GenericSubsystemComponentBinding(
        componentName = name,
        version = version,
        coordinate = coordinate,
        extensionBindings = _record_value(rec, List("extension_bindings", "extensionBindings", "extension_binding")).getOrElse(Record.empty),
        api = _ports_from_record(rec, "api"),
        spi = _ports_from_record(rec, "spi")
      )
    }
  }

  private def _ports_from_record(rec: Record, key: String): Vector[GenericSubsystemPortBinding] =
    _record_value(rec, List(key)).map { ports =>
      ports.asMap.toVector.flatMap { case (name, value) =>
        _any_to_record(value).map { r =>
          GenericSubsystemPortBinding(
            name = name,
            service = _string(r, "service"),
            operation = _string(r, "operation")
          )
        }.orElse {
          value match {
            case s: String if s.trim.nonEmpty =>
              Some(GenericSubsystemPortBinding(name = name, operation = Some(s.trim)))
            case _ =>
              None
          }
        }
      }
    }.getOrElse(Vector.empty)

  private def _component_extension_bindings(lines: Vector[String], componentName: String): Record = {
    val runtimeName = runtimeComponentName(componentName)
    val prefix = s"component.${runtimeName}.extension_binding.knowledge_source_adapters "
    val keys = lines.collect {
      case line if line.startsWith(prefix) =>
        line.substring(prefix.length).trim
    }.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).distinct
    if (keys.isEmpty)
      Record.empty
    else
      Record.data(
        "knowledge_source_adapters" -> keys.map(key => Record.data("key" -> key)).toVector
      )
  }

  private def _boolean(rec: Record, keys: String*): Option[Boolean] =
    keys.iterator.map(rec.getString).collectFirst {
      case Some(s) if s.trim.equalsIgnoreCase("true") || s.trim.equalsIgnoreCase("yes") || s.trim.equalsIgnoreCase("on") || s.trim == "1" => true
      case Some(s) if s.trim.equalsIgnoreCase("false") || s.trim.equalsIgnoreCase("no") || s.trim.equalsIgnoreCase("off") || s.trim == "0" => false
    }

  private def _int(rec: Record, keys: String*): Option[Int] =
    keys.iterator.flatMap(rec.getString).flatMap(s => scala.util.Try(s.trim.toInt).toOption).toSeq.headOption

  private def _string_vector(rec: Record, keys: List[String]): Vector[String] =
    keys.iterator.map(rec.getAny).collectFirst {
      case Some(xs: Seq[?]) => xs.toVector.collect { case s: String if s.trim.nonEmpty => s.trim }
      case Some(s: String) if s.trim.nonEmpty => s.split("[,|\\s]+").toVector.map(_.trim).filter(_.nonEmpty)
    }.getOrElse(Vector.empty)

  private def _string(rec: Record, keys: String*): Option[String] =
    keys.iterator.map(rec.getString).collectFirst { case Some(s) if s.trim.nonEmpty => s.trim }

  private def _record_value(rec: Record, keys: List[String]): Option[Record] =
    keys.iterator.map(rec.getAny).collectFirst {
      case Some(r: Record) => r
      case Some(m: Map[?, ?]) => _map_to_record(m)
      case Some(m: java.util.Map[?, ?]) => _map_to_record(m.asScala.toMap)
    }

  private def _string_map_value(rec: Record, keys: List[String]): Map[String, String] =
    _record_value(rec, keys).map(_.asMap.collect { case (k, v: String) => k -> v }).getOrElse(Map.empty)

  private def _operation_authorization_value(
    rec: Record
  ): Map[String, OperationAuthorizationRule] = {
    val direct = _record_value(rec, List("operationAuthorization", "operation_authorization"))
    val nested = _record_value(rec, List("authorization")).flatMap { auth =>
      _record_value(auth, List("operations", "operation", "operationAuthorization", "operation_authorization"))
    }
    direct.orElse(nested).map { rules =>
      rules.asMap.toVector.flatMap {
        case (selector, r: Record) =>
          Some(selector -> OperationAuthorizationRule.fromRecord(r))
        case (selector, m: Map[?, ?]) =>
          Some(selector -> OperationAuthorizationRule.fromRecord(_map_to_record(m)))
        case (selector, m: java.util.Map[?, ?]) =>
          Some(selector -> OperationAuthorizationRule.fromRecord(_map_to_record(m.asScala.toMap)))
        case _ =>
          None
      }.toMap
    }.getOrElse(Map.empty)
  }

  private def _wiring_value(rec: Record): Record =
    _record_value(rec, List("wiring")).getOrElse {
      val entries = rec.asMap.iterator.collect {
        case (k, v) if k.startsWith("wiring/") =>
          k.stripPrefix("wiring/") -> _record_value_any(v)
        case (k, v) if k.startsWith("wiring.") =>
          k.stripPrefix("wiring.") -> _record_value_any(v)
      }.toVector
      if (entries.isEmpty) Record.empty else Record.create(entries)
    }

  def resolveWiringBindings(descriptor: GenericSubsystemDescriptor): Vector[GenericSubsystemResolvedWiringBinding] = {
    val componentIndex = descriptor.componentBindings.map(x => x.componentName -> x).toMap
    val groups = scala.collection.mutable.LinkedHashMap.empty[String, scala.collection.mutable.Map[String, String]]
    _flatten_record(descriptor.wiring).iterator.foreach {
      case (k, v) =>
        val key = k.toString
        val value = Option(v).map(_.toString).getOrElse("")
        val path = key.split("/").toVector.filter(_.nonEmpty)
        if (path.size >= 4) {
          val group = path.take(3).mkString("/")
          val leaf = path.drop(3).mkString("/")
          val slot = groups.getOrElseUpdate(group, scala.collection.mutable.LinkedHashMap.empty[String, String])
          slot.update(leaf, value)
        }
    }
    groups.toVector.flatMap { case (group, values) =>
      group.split("/").toVector.filter(_.nonEmpty) match {
        case Vector(fromComponent, fromService, fromOperation) =>
          val targetComponent = values.get("target_component")
          val fromApi = values.get("api")
          val targetSpi = values.get("target_spi").orElse(values.get("spi"))
          (targetComponent, targetSpi) match {
            case (Some(toComponent), Some(spiName)) =>
              componentIndex.get(toComponent)
                .flatMap(_.spi.find(_.name == spiName))
                .flatMap { spi =>
                  for {
                    toService <- spi.service
                    toOperation <- spi.operation
                  } yield GenericSubsystemResolvedWiringBinding(
                    fromComponent = fromComponent,
                    fromService = fromService,
                    fromOperation = fromOperation,
                    fromApi = fromApi,
                    toComponent = toComponent,
                    toSpi = Some(spiName),
                    toService = toService,
                    toOperation = toOperation
                    ,
                    glue = _glue_value(values)
                  )
                }
            case _ =>
              for {
                toComponent <- values.get("target_component")
                toService <- values.get("target_service")
                toOperation <- values.get("target_operation")
              } yield GenericSubsystemResolvedWiringBinding(
                fromComponent = fromComponent,
                fromService = fromService,
                fromOperation = fromOperation,
                fromApi = fromApi,
                toComponent = toComponent,
                toSpi = targetSpi,
                toService = toService,
                toOperation = toOperation,
                glue = _glue_value(values),
                mode = "direct-operation-routing"
              )
          }
        case _ =>
          None
      }
    }
  }

  def resolveAssemblyWiringBindings(
    descriptor: GenericSubsystemDescriptor
  ): Option[Vector[GenericSubsystemResolvedWiringBinding]] =
    descriptor.assemblyDescriptor.map { source =>
      source.record.getAny("wiring") match {
        case Some(xs: Seq[?]) =>
          xs.toVector.flatMap(_any_to_record).flatMap(_resolved_wiring_binding_from_record)
        case Some(xs: java.util.List[?]) =>
          xs.asScala.toVector.flatMap(_any_to_record).flatMap(_resolved_wiring_binding_from_record)
        case _ =>
          Vector.empty
      }
    }

  private def _resolved_wiring_binding_from_record(
    rec: Record
  ): Option[GenericSubsystemResolvedWiringBinding] =
    for {
      from <- rec.getRecord("from")
      to <- rec.getRecord("to")
      fromComponent <- _string(from, "component")
      fromService <- _string(from, "service")
      fromOperation <- _string(from, "operation")
      toComponent <- _string(to, "component")
      toService <- _string(to, "service")
      toOperation <- _string(to, "operation")
    } yield GenericSubsystemResolvedWiringBinding(
      fromComponent = fromComponent,
      fromService = fromService,
      fromOperation = fromOperation,
      fromApi = _string(from, "api"),
      toComponent = toComponent,
      toSpi = _string(to, "spi"),
      toService = toService,
      toOperation = toOperation,
      glue = _record_value(rec, List("glue")).getOrElse(Record.empty),
      mode = _string(rec, "mode").getOrElse("api-spi-routing")
    )

  def resolveWiringBindings(wiring: Record): Vector[GenericSubsystemResolvedWiringBinding] = {
    val groups = scala.collection.mutable.LinkedHashMap.empty[String, scala.collection.mutable.Map[String, String]]
    _flatten_record(wiring).iterator.foreach {
      case (k, v) =>
        val key = k.toString
        val value = Option(v).map(_.toString).getOrElse("")
        val path = key.split("/").toVector.filter(_.nonEmpty)
        if (path.size >= 4) {
          val group = path.take(3).mkString("/")
          val leaf = path.drop(3).mkString("/")
          val slot = groups.getOrElseUpdate(group, scala.collection.mutable.LinkedHashMap.empty[String, String])
          slot.update(leaf, value)
        }
    }
    groups.toVector.flatMap { case (group, values) =>
      group.split("/").toVector.filter(_.nonEmpty) match {
        case Vector(fromComponent, fromService, fromOperation) =>
          for {
            toComponent <- values.get("target_component")
            toService <- values.get("target_service")
            toOperation <- values.get("target_operation")
          } yield GenericSubsystemResolvedWiringBinding(
            fromComponent = fromComponent,
            fromService = fromService,
            fromOperation = fromOperation,
            fromApi = values.get("api"),
            toComponent = toComponent,
            toSpi = values.get("target_spi").orElse(values.get("spi")),
            toService = toService,
            toOperation = toOperation,
            glue = _glue_value(values),
            mode = "direct-operation-routing"
          )
        case _ =>
          None
      }
    }
  }

  private def _glue_value(values: collection.Map[String, String]): Record = {
    val entries = values.iterator.collect {
      case (k, v) if k.startsWith("glue/") =>
        k.stripPrefix("glue/") -> v
      case (k, v) if k.startsWith("glue.") =>
        k.stripPrefix("glue.") -> v
    }.toVector
    if (entries.isEmpty) Record.empty else Record.create(entries)
  }

  private def _flatten_record(
    rec: Record,
    prefix: Vector[String] = Vector.empty
  ): Vector[(String, Any)] =
    rec.asMap.toVector.flatMap { case (k, v) =>
      val path = prefix :+ k.toString
      v match {
        case r: Record =>
          _flatten_record(r, path)
        case m: Map[?, ?] =>
          _flatten_record(_map_to_record(m), path)
        case m: java.util.Map[?, ?] =>
          _flatten_record(_map_to_record(m.asScala.toMap), path)
        case other =>
          Vector(path.mkString("/") -> other)
      }
    }

  private def _any_to_record(value: Any): Option[Record] =
    value match {
      case r: Record => Some(r)
      case m: Map[?, ?] => Some(_map_to_record(m))
      case m: java.util.Map[?, ?] => Some(_map_to_record(m.asScala.toMap))
      case _ => None
    }

  private def _map_to_record(m: collection.Map[?, ?]): Record =
    Record.create(m.iterator.map { case (k, v) => k.toString -> _record_value_any(v) }.toSeq)

  private def _record_value_any(value: Any): Any =
    value match {
      case r: Record => r
      case m: Map[?, ?] => _map_to_record(m)
      case m: java.util.Map[?, ?] => _map_to_record(m.asScala.toMap)
      case xs: Seq[?] => xs.toVector.map(_record_value_any)
      case xs: java.util.List[?] => xs.asScala.toVector.map(_record_value_any)
      case other => other
    }

  private def _single_value(lines: Vector[String], key: String, path: Path): String =
    _optional_single_value(lines, key).getOrElse(throw new IllegalArgumentException(s"missing $key in ${path}"))

  private def _optional_single_value(lines: Vector[String], key: String): Option[String] = {
    val prefix = s"$key "
    lines.collectFirst {
      case line if line.startsWith(prefix) =>
        line.substring(prefix.length).trim
    }.filter(_.nonEmpty)
  }

  given RecordDecoder[GenericSubsystemComponentBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemComponentBinding] = {
      val componentName = _string(rec, "component", "componentName", "name")
      componentName match {
        case Some(name) =>
          val version = _string(rec, "version")
          val coordinate = _string(rec, "coordinate")
          coordinate.foreach { c =>
            val artifact = coordinateArtifact(c).getOrElse(throw new IllegalArgumentException(s"invalid component coordinate: $c"))
            val cversion = coordinateVersion(c).getOrElse(throw new IllegalArgumentException(s"invalid component coordinate: $c"))
            require(artifact == name, s"component coordinate artifact must match component name: component=$name coordinate=$c")
            version.foreach(v => require(v == cversion, s"component version must match coordinate version: component=$name version=$v coordinate=$c"))
          }
          Consequence.success(
            GenericSubsystemComponentBinding(
              componentName = name,
              version = version,
              coordinate = coordinate,
              extensionBindings = _record_value(rec, List("extension_bindings", "extensionBindings", "extension_binding")).getOrElse(Record.empty)
            )
          )
        case None =>
          Consequence.argumentMissing("component/componentName/name")
      }
    }

  given RecordDecoder[GenericSubsystemAuthenticationProviderBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemAuthenticationProviderBinding] = {
      val name = _string(rec, "name")
      val component = _string(rec, "component")
      (name, component) match {
        case (Some(n), Some(c)) =>
          Consequence.success(
            GenericSubsystemAuthenticationProviderBinding(
              name = n,
              component = c,
              kind = _string(rec, "kind"),
              enabled = _boolean(rec, "enabled"),
              priority = _int(rec, "priority"),
              schemes = _string_vector(rec, List("schemes", "scheme")),
              isDefault = _boolean(rec, "default", "isDefault")
            )
          )
        case _ =>
          Consequence.argumentMissing("authentication provider name/component")
      }
    }

  given RecordDecoder[GenericSubsystemAuthenticationBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemAuthenticationBinding] = {
      val providers = rec.getAny("providers") match {
        case Some(xs: Seq[?]) =>
          xs.toVector.flatMap(_any_to_record).flatMap(r => summon[RecordDecoder[GenericSubsystemAuthenticationProviderBinding]].fromRecord(r).toOption)
        case _ =>
          Vector.empty
      }
      Consequence.success(
        GenericSubsystemAuthenticationBinding(
          convention = _string(rec, "convention"),
          fallbackPrivilege = _string(rec, "fallback_privilege", "fallbackPrivilege"),
          providers = providers
        )
      )
    }

  given RecordDecoder[GenericSubsystemSecurityBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemSecurityBinding] =
      _record_value(rec, List("authentication")) match {
        case Some(auth) => summon[RecordDecoder[GenericSubsystemAuthenticationBinding]].fromRecord(auth).map(x => GenericSubsystemSecurityBinding(Some(x)))
        case None => Consequence.success(GenericSubsystemSecurityBinding(None))
      }

  given RecordDecoder[GenericSubsystemBuiltinBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemBuiltinBinding] =
      Consequence.success(
        GenericSubsystemBuiltinBinding(
          exclude = _string_vector(rec, List("exclude", "excluded", "disable", "disabled"))
        )
      )

  given RecordDecoder[Shape] with
    def fromRecord(rec: Record): Consequence[Shape] = {
      val subsystemName = _string(rec, "subsystem", "subsystemName", "name")
      subsystemName match {
        case Some(name) =>
          val bindings = _bindings_from_record(Path.of("<record>"), rec)
          if (bindings.isEmpty)
            Consequence.argumentMissing("component bindings")
          else
            Consequence.success(
              Shape(
                subsystemName = name,
                version = _string(rec, "version"),
                componentBindings = bindings,
                extensions = _string_map_value(rec, List("extension", "extensions")),
                config = _string_map_value(rec, List("config")),
                wiring = _wiring_value(rec),
                security = _record_value(rec, List("security")).flatMap(r => summon[RecordDecoder[GenericSubsystemSecurityBinding]].fromRecord(r).toOption),
                builtin = _record_value(rec, List("builtin", "builtins")).flatMap(r => summon[RecordDecoder[GenericSubsystemBuiltinBinding]].fromRecord(r).toOption),
                operationAuthorization = _operation_authorization_value(rec)
              )
            )
        case None =>
          Consequence.argumentMissing("subsystem/subsystemName/name")
      }
    }
}
