package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.RecordDecoder
import org.goldenport.cncf.component.ComponentDescriptor
import org.goldenport.cncf.component.ComponentDescriptorLoader
import org.goldenport.cncf.component.DescriptorRecordLoader

/*
 * @since   Apr.  7, 2026
 * @version Apr.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final case class GenericSubsystemComponentBinding(
  componentName: String,
  coordinate: Option[String] = None,
  extensionBindings: Record = Record.empty
) {
  def componentVersion: Option[String] =
    coordinate.flatMap(GenericSubsystemDescriptor.coordinateParts(_).lift(2))

  def runtimeComponentName: String =
    GenericSubsystemDescriptor.runtimeComponentName(componentName)

  def toComponentDescriptor: ComponentDescriptor =
    ComponentDescriptor(
      componentName = Some(runtimeComponentName),
      extensionBindings = extensionBindings
    )
}

final case class GenericSubsystemDescriptor(
  path: Path,
  subsystemName: String,
  version: Option[String] = None,
  componentBindings: Vector[GenericSubsystemComponentBinding] = Vector.empty,
  extensions: Map[String, String] = Map.empty,
  config: Map[String, String] = Map.empty,
  wiring: Record = Record.empty
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
}

object GenericSubsystemDescriptor {
  final case class Shape(
    subsystemName: String,
    version: Option[String],
    componentBindings: Vector[GenericSubsystemComponentBinding],
    extensions: Map[String, String],
    config: Map[String, String],
    wiring: Record
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
    "subsystem-descriptor.xml",
    "subsystem.cml"
  )

  def load(path: Path): Consequence[GenericSubsystemDescriptor] =
    if (!Files.exists(path))
      Consequence.failure(s"subsystem descriptor path does not exist: ${path}")
    else if (Files.isDirectory(path))
      _resolve_descriptor_file(path) match {
        case Some(file) => _load_file(file)
        case None => _load_manifest_compat(path)
      }
    else
      _load_file(path)

  def loadArchive(path: Path): Consequence[GenericSubsystemDescriptor] =
    load(path)

  def looksLikeArchiveDirectory(path: Path): Boolean = {
    val componentdir = path.resolve("component")
    Files.isDirectory(path) && Files.isDirectory(componentdir) &&
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

  private def _load_file(path: Path): Consequence[GenericSubsystemDescriptor] = {
    val lower = path.getFileName.toString.toLowerCase
    if (lower.endsWith(".cml"))
      _load_cml(path)
    else
      DescriptorRecordLoader.load(path).flatMap { records =>
        records.headOption.map(_from_record(path, _)).getOrElse(Consequence.failure(s"subsystem descriptor is empty: ${path}"))
      }
  }

  private def _load_manifest_compat(path: Path): Consequence[GenericSubsystemDescriptor] =
    org.goldenport.cncf.component.ArchiveManifest.load(path, "sar").map { m =>
      GenericSubsystemDescriptor(
        path = path,
        subsystemName = m.subsystem.getOrElse(m.name),
        version = Some(m.version),
        componentBindings = Vector.empty,
        extensions = m.extensions,
        config = m.config,
        wiring = Record.empty
      )
    }

  private def _load_cml(path: Path): Consequence[GenericSubsystemDescriptor] =
    Consequence {
      val lines = Files.readAllLines(path).asScala.toVector.map(_.trim).filterNot(_.isEmpty)
      val subsystemName = _single_value(lines, "subsystem", path)
      val version = _optional_single_value(lines, "version")
      val bindings = _bindings_from_cml(lines)
      require(bindings.nonEmpty, s"missing component in ${path}")
      GenericSubsystemDescriptor(
        path = path,
        subsystemName = subsystemName,
        version = version,
        componentBindings = bindings,
        wiring = Record.empty
      )
    }

  private def _bindings_from_cml(lines: Vector[String]): Vector[GenericSubsystemComponentBinding] = {
    val buffer = Vector.newBuilder[GenericSubsystemComponentBinding]
    var currentName: Option[String] = None
    var currentCoordinate: Option[String] = None
    def flush(): Unit =
      currentName.foreach { name =>
        currentCoordinate.foreach { coordinate =>
          val parts = coordinateParts(coordinate)
          require(parts.size == 3, s"invalid component coordinate: $coordinate")
          require(parts(1) == name, s"component coordinate artifact must match component name: component=$name coordinate=$coordinate")
        }
        buffer += GenericSubsystemComponentBinding(
          componentName = name,
          coordinate = currentCoordinate,
          extensionBindings = _component_extension_bindings(lines, name)
        )
      }
    lines.foreach { line =>
      if (line.startsWith("component ")) {
        flush()
        currentName = Some(line.stripPrefix("component ").trim)
        currentCoordinate = None
      } else if (line.startsWith("coordinate ")) {
        currentCoordinate = Some(line.stripPrefix("coordinate ").trim)
      }
    }
    flush()
    buffer.result()
  }

  private def _from_record(path: Path, rec: Record): Consequence[GenericSubsystemDescriptor] = {
    summon[RecordDecoder[Shape]].fromRecord(rec).map { s =>
      GenericSubsystemDescriptor(
        path = path,
        subsystemName = s.subsystemName,
        version = s.version,
        componentBindings = s.componentBindings,
        extensions = s.extensions,
        config = s.config,
        wiring = s.wiring
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
      val coordinate = _string(rec, "coordinate")
      coordinate.foreach { c =>
        val parts = coordinateParts(c)
        require(parts.size == 3, s"invalid component coordinate: $c")
        require(parts(1) == name, s"component coordinate artifact must match component name: component=$name coordinate=$c")
      }
      GenericSubsystemComponentBinding(
        componentName = name,
        coordinate = coordinate,
        extensionBindings = _record_value(rec, List("extension_bindings", "extensionBindings", "extension_binding")).getOrElse(Record.empty)
      )
    }
  }

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

  private def _string(rec: Record, keys: String*): Option[String] =
    keys.iterator.map(rec.getString).collectFirst { case Some(s) if s.trim.nonEmpty => s.trim }

  private def _record_value(rec: Record, keys: List[String]): Option[Record] =
    keys.iterator.map(rec.getAny).collectFirst {
      case Some(r: Record) => r
      case Some(m: Map[?, ?]) => Record.create(m.iterator.map { case (k, v) => k.toString -> v }.toMap)
    }

  private def _string_map_value(rec: Record, keys: List[String]): Map[String, String] =
    _record_value(rec, keys).map(_.asMap.collect { case (k, v: String) => k -> v }).getOrElse(Map.empty)

  private def _any_to_record(value: Any): Option[Record] =
    value match {
      case r: Record => Some(r)
      case m: Map[?, ?] => Some(Record.create(m.iterator.map { case (k, v) => k.toString -> v }.toMap))
      case _ => None
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
          val coordinate = _string(rec, "coordinate")
          coordinate.foreach { c =>
            val parts = coordinateParts(c)
            require(parts.size == 3, s"invalid component coordinate: $c")
            require(parts(1) == name, s"component coordinate artifact must match component name: component=$name coordinate=$c")
          }
          Consequence.success(
            GenericSubsystemComponentBinding(
              componentName = name,
              coordinate = coordinate,
              extensionBindings = _record_value(rec, List("extension_bindings", "extensionBindings", "extension_binding")).getOrElse(Record.empty)
            )
          )
        case None =>
          Consequence.failure("missing component/componentName/name")
      }
    }

  given RecordDecoder[Shape] with
    def fromRecord(rec: Record): Consequence[Shape] = {
      val subsystemName = _string(rec, "subsystem", "subsystemName", "name")
      subsystemName match {
        case Some(name) =>
          val bindings = _bindings_from_record(Path.of("<record>"), rec)
          if (bindings.isEmpty)
            Consequence.failure("missing component bindings")
          else
            Consequence.success(
              Shape(
                subsystemName = name,
                version = _string(rec, "version"),
                componentBindings = bindings,
                extensions = _string_map_value(rec, List("extension", "extensions")),
                config = _string_map_value(rec, List("config")),
                wiring = _record_value(rec, List("wiring")).getOrElse(Record.empty)
              )
            )
        case None =>
          Consequence.failure("missing subsystem/subsystemName/name")
      }
    }
}
