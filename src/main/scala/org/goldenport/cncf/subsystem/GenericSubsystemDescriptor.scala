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

/*
 * @since   Apr.  7, 2026
 * @version Apr.  9, 2026
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
  wiring: Record = Record.empty,
  security: Option[GenericSubsystemSecurityBinding] = None
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
    wiring: Record,
    security: Option[GenericSubsystemSecurityBinding]
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

  def load(path: Path): Consequence[GenericSubsystemDescriptor] =
    if (!Files.exists(path))
      Consequence.failure(s"subsystem descriptor path does not exist: ${path}")
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

  private def _is_archive_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase
    name.endsWith(".sar") || name.endsWith(".zip")
  }

  private def _load_archive_file(path: Path): Consequence[GenericSubsystemDescriptor] = {
    val uri = URI.create(s"jar:${path.toUri}")
    Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
      val root = fs.getPath("/")
      _resolve_descriptor_file(root) match {
        case Some(file) =>
          DescriptorRecordLoader.load(file).flatMap { records =>
            records.headOption.map(_from_record(path, _)).getOrElse(Consequence.failure(s"subsystem descriptor is empty in archive: ${path}"))
          }
        case None =>
          Consequence.failure(s"subsystem descriptor not found in archive: ${path}")
      }
    }
  }

  private def _load_file(path: Path): Consequence[GenericSubsystemDescriptor] =
    DescriptorRecordLoader.load(path).flatMap { records =>
      records.headOption.map(_from_record(path, _)).getOrElse(Consequence.failure(s"subsystem descriptor is empty: ${path}"))
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
        security = None
      )
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
        wiring = s.wiring,
        security = s.security
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
          Consequence.failure("missing authentication provider name/component")
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
                wiring = _record_value(rec, List("wiring")).getOrElse(Record.empty),
                security = _record_value(rec, List("security")).flatMap(r => summon[RecordDecoder[GenericSubsystemSecurityBinding]].fromRecord(r).toOption)
              )
            )
        case None =>
          Consequence.failure("missing subsystem/subsystemName/name")
      }
    }
}
