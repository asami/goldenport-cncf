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
import org.goldenport.cncf.security.{AuthorizationResourcePolicies, AuthorizationResourcePolicy, OperationAuthorizationRule, SecurityRoleDefinition, SecuritySubject}

/*
 * @since   Apr.  7, 2026
 *  version Apr. 28, 2026
 * @version May.  1, 2026
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

final case class GenericSubsystemMessageDeliveryProviderBinding(
  name: String,
  component: String,
  channel: Option[String] = None,
  enabled: Option[Boolean] = None,
  priority: Option[Int] = None,
  isDefault: Option[Boolean] = None
)

final case class GenericSubsystemMessageDeliveryBinding(
  providers: Vector[GenericSubsystemMessageDeliveryProviderBinding] = Vector.empty
)

final case class GenericSubsystemAuthorizationBinding(
  roles: Map[String, SecurityRoleDefinition] = Map.empty,
  resources: AuthorizationResourcePolicies = AuthorizationResourcePolicies.empty
)

final case class GenericSubsystemSecurityBinding(
  authentication: Option[GenericSubsystemAuthenticationBinding] = None,
  messageDelivery: Option[GenericSubsystemMessageDeliveryBinding] = None,
  authorization: Option[GenericSubsystemAuthorizationBinding] = None
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

  def mergeComponentDefaults(
    defaults: GenericSubsystemDescriptor,
    overrideDescriptor: GenericSubsystemDescriptor
  ): GenericSubsystemDescriptor =
    overrideDescriptor.copy(
      version = overrideDescriptor.version.orElse(defaults.version),
      componentBindings = _merge_component_bindings(defaults.componentBindings, overrideDescriptor.componentBindings),
      extensions = defaults.extensions ++ overrideDescriptor.extensions,
      config = defaults.config ++ overrideDescriptor.config,
      wiring = _merge_record(defaults.wiring, overrideDescriptor.wiring),
      security = _merge_security(defaults.security, overrideDescriptor.security),
      builtin = overrideDescriptor.builtin.orElse(defaults.builtin),
      operationAuthorization = defaults.operationAuthorization ++ overrideDescriptor.operationAuthorization,
      assemblyDescriptor = defaults.assemblyDescriptor
    )

  def applyAssemblyOverride(
    descriptor: GenericSubsystemDescriptor,
    source: GenericSubsystemAssemblyDescriptorSource
  ): GenericSubsystemDescriptor = {
    val rec = source.record
    val bindings = _bindings_from_record(source.path.getOrElse(descriptor.path), rec)
    val overrideDescriptor = descriptor.copy(
      subsystemName = _string(rec, "subsystem", "subsystemName", "name").getOrElse(descriptor.subsystemName),
      version = _string(rec, "version").orElse(descriptor.version),
      componentBindings = if (bindings.nonEmpty) bindings else descriptor.componentBindings,
      extensions = descriptor.extensions ++ _string_map_value(rec, List("extension", "extensions")),
      config = descriptor.config ++ _string_map_value(rec, List("config")),
      wiring = _merge_record(descriptor.wiring, _wiring_value(rec)),
      security = _merge_security(
        descriptor.security,
        _record_value(rec, List("security")).flatMap(r => summon[RecordDecoder[GenericSubsystemSecurityBinding]].fromRecord(r).toOption)
      ),
      builtin = _record_value(rec, List("builtin", "builtins"))
        .flatMap(r => summon[RecordDecoder[GenericSubsystemBuiltinBinding]].fromRecord(r).toOption)
        .orElse(descriptor.builtin),
      operationAuthorization = descriptor.operationAuthorization ++ _operation_authorization_value(rec),
      assemblyDescriptor = Some(_merge_assembly_sources(descriptor.assemblyDescriptor, source))
    )
    overrideDescriptor
  }

  private def _merge_assembly_sources(
    defaults: Option[GenericSubsystemAssemblyDescriptorSource],
    overrideSource: GenericSubsystemAssemblyDescriptorSource
  ): GenericSubsystemAssemblyDescriptorSource =
    defaults match {
      case Some(base) =>
        overrideSource.copy(record = _merge_assembly_records(base.record, overrideSource.record))
      case None =>
        overrideSource
    }

  private def _merge_assembly_records(
    defaults: Record,
    overrides: Record
  ): Record = {
    val base = defaults.asMap
    val over = overrides.asMap
    val wiring = _merge_assembly_wiring(base.get("wiring"), over.get("wiring"))
    val entries = base.toVector ++ over.toVector.filterNot(_._1 == "wiring")
    wiring match {
      case Some(value) => Record.create(entries.filterNot(_._1 == "wiring") :+ ("wiring" -> value))
      case None => Record.create(entries)
    }
  }

  private def _merge_assembly_wiring(
    defaults: Option[Any],
    overrides: Option[Any]
  ): Option[Vector[Record]] = {
    val base = defaults.toVector.flatMap(_wiring_records)
    val over = overrides.toVector.flatMap(_wiring_records)
    if (base.isEmpty && over.isEmpty) {
      None
    } else {
      val overrideKeys = over.flatMap(_wiring_binding_key).toSet
      Some(base.filterNot(r => _wiring_binding_key(r).exists(overrideKeys.contains)) ++ over)
    }
  }

  private def _wiring_records(value: Any): Vector[Record] =
    value match {
      case xs: Seq[?] => xs.toVector.flatMap(_any_to_record)
      case xs: java.util.List[?] => xs.asScala.toVector.flatMap(_any_to_record)
      case _ => Vector.empty
    }

  private def _wiring_binding_key(rec: Record): Option[String] =
    rec.getRecord("from").flatMap { from =>
      for {
        component <- _string(from, "component")
        service <- _string(from, "service")
        operation <- _string(from, "operation")
      } yield Vector(component, service, operation, _string(from, "api").getOrElse("")).map(_comparison_key).mkString("/")
    }

  private def _merge_record(
    defaults: Record,
    overrides: Record
  ): Record =
    if (defaults.asMap.isEmpty) overrides
    else if (overrides.asMap.isEmpty) defaults
    else Record.create(defaults.asMap.toVector ++ overrides.asMap.toVector)

  private def _merge_component_bindings(
    defaults: Vector[GenericSubsystemComponentBinding],
    overrides: Vector[GenericSubsystemComponentBinding]
  ): Vector[GenericSubsystemComponentBinding] =
    if (overrides.isEmpty) {
      defaults
    } else {
      val overrideByName = overrides.map(x => _comparison_key(x.componentName) -> x).toMap
      val defaultKeys = defaults.map(x => _comparison_key(x.componentName)).toSet
      defaults.map(x => overrideByName.getOrElse(_comparison_key(x.componentName), x)) ++
        overrides.filterNot(x => defaultKeys.contains(_comparison_key(x.componentName)))
    }

  private def _merge_security(
    defaults: Option[GenericSubsystemSecurityBinding],
    overrides: Option[GenericSubsystemSecurityBinding]
  ): Option[GenericSubsystemSecurityBinding] =
    (defaults, overrides) match {
      case (None, None) => None
      case (Some(x), None) => Some(x)
      case (None, Some(x)) => Some(x)
      case (Some(a), Some(b)) =>
        Some(GenericSubsystemSecurityBinding(
          authentication = _merge_authentication(a.authentication, b.authentication),
          messageDelivery = _merge_message_delivery(a.messageDelivery, b.messageDelivery),
          authorization = _merge_authorization(a.authorization, b.authorization)
        ))
    }

  private def _merge_authorization(
    defaults: Option[GenericSubsystemAuthorizationBinding],
    overrides: Option[GenericSubsystemAuthorizationBinding]
  ): Option[GenericSubsystemAuthorizationBinding] =
    (defaults, overrides) match {
      case (None, None) => None
      case (Some(x), None) => Some(x)
      case (None, Some(x)) => Some(x)
      case (Some(a), Some(b)) =>
        val overrideKeys = b.roles.keys.map(_comparison_key).toSet
        val inherited = a.roles.filterNot { case (name, _) => overrideKeys.contains(_comparison_key(name)) }
        Some(GenericSubsystemAuthorizationBinding(
          roles = inherited ++ b.roles,
          resources = a.resources.mergeOverride(b.resources)
        ))
    }

  private def _merge_authentication(
    defaults: Option[GenericSubsystemAuthenticationBinding],
    overrides: Option[GenericSubsystemAuthenticationBinding]
  ): Option[GenericSubsystemAuthenticationBinding] =
    (defaults, overrides) match {
      case (None, None) => None
      case (Some(x), None) => Some(x)
      case (None, Some(x)) => Some(x)
      case (Some(a), Some(b)) =>
        Some(GenericSubsystemAuthenticationBinding(
          convention = b.convention.orElse(a.convention),
          fallbackPrivilege = b.fallbackPrivilege.orElse(a.fallbackPrivilege),
          providers = _merge_authentication_providers(a.providers, b.providers)
        ))
    }

  private def _merge_message_delivery(
    defaults: Option[GenericSubsystemMessageDeliveryBinding],
    overrides: Option[GenericSubsystemMessageDeliveryBinding]
  ): Option[GenericSubsystemMessageDeliveryBinding] =
    (defaults, overrides) match {
      case (None, None) => None
      case (Some(x), None) => Some(x)
      case (None, Some(x)) => Some(x)
      case (Some(a), Some(b)) =>
        Some(GenericSubsystemMessageDeliveryBinding(
          providers = _merge_message_delivery_providers(a.providers, b.providers)
        ))
    }

  private def _merge_authentication_providers(
    defaults: Vector[GenericSubsystemAuthenticationProviderBinding],
    overrides: Vector[GenericSubsystemAuthenticationProviderBinding]
  ): Vector[GenericSubsystemAuthenticationProviderBinding] =
    _merge_by_name(defaults, overrides)(_.name)

  private def _merge_message_delivery_providers(
    defaults: Vector[GenericSubsystemMessageDeliveryProviderBinding],
    overrides: Vector[GenericSubsystemMessageDeliveryProviderBinding]
  ): Vector[GenericSubsystemMessageDeliveryProviderBinding] =
    _merge_by_name(defaults, overrides)(_.name)

  private def _merge_by_name[A](
    defaults: Vector[A],
    overrides: Vector[A]
  )(name: A => String): Vector[A] = {
    val overrideKeys = overrides.map(x => _comparison_key(name(x))).toSet
    defaults.filterNot(x => overrideKeys.contains(_comparison_key(name(x)))) ++ overrides
  }

  private def _comparison_key(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase.replace("_", "").replace("-", "")

  def load(path: Path): Consequence[GenericSubsystemDescriptor] =
    if (!Files.exists(path))
      Consequence.resourceNotFound(s"subsystem descriptor path does not exist: ${path}")
    else if (Files.isDirectory(path))
      _resolve_descriptor_file(path) match {
        case Some(file) => _load_file(file)
        case None => Consequence.resourceNotFound(s"subsystem descriptor not found: ${path}")
      }
    else if (_is_archive_file(path))
      _load_archive_file(path)
    else
      _load_file(path)

  def loadArchive(path: Path): Consequence[GenericSubsystemDescriptor] =
    load(path)

  def loadComponentArchive(path: Path): Consequence[GenericSubsystemDescriptor] =
    ComponentDescriptorLoader.loadArchive(path).flatMap(fromComponentDescriptor(path, _))

  def fromComponentDescriptor(
    path: Path,
    descriptor: ComponentDescriptor
  ): Consequence[GenericSubsystemDescriptor] = {
    val componentname =
      descriptor.componentName.orElse(descriptor.name).getOrElse(path.getFileName.toString.stripSuffix(".car"))
    val primary = GenericSubsystemComponentBinding(
      componentName = componentname,
      version = descriptor.version,
      coordinate = None,
      extensionBindings = descriptor.extensionBindings
    )
    _load_assembly_descriptor_consequence(path, "component-car").flatMap { assembly =>
      _decode_optional_assembly_shape(path, assembly).map { shape =>
        val bindings =
          shape.map(_.componentBindings).filter(_.nonEmpty).map { xs =>
            if (xs.exists(x => runtimeComponentName(x.componentName) == runtimeComponentName(componentname))) xs
            else primary +: xs
          }.getOrElse(Vector(primary))
        GenericSubsystemDescriptor(
          path = path,
          subsystemName = shape.map(_.subsystemName).getOrElse(descriptor.subsystemName.getOrElse(componentname)),
          version = shape.flatMap(_.version).orElse(descriptor.version),
          componentBindings = bindings,
          extensions = descriptor.extensions ++ shape.map(_.extensions).getOrElse(Map.empty),
          config = descriptor.config ++ shape.map(_.config).getOrElse(Map.empty),
          wiring = shape.map(_.wiring).getOrElse(Record.empty),
          assemblyDescriptor = assembly,
          security = shape.flatMap(_.security),
          builtin = shape.flatMap(_.builtin),
          operationAuthorization = shape.map(_.operationAuthorization).getOrElse(Map.empty)
        )
      }
    }
  }

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
      _resolve_descriptor_file(path).nonEmpty &&
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

  def runtimeComponentName(componentName: String): String =
    componentName.trim

  private def _resolve_descriptor_file(path: Path): Option[Path] =
    _resolve_descriptor_file_in(path)
      .orElse(_resolve_descriptor_file_in(path.resolve("subsystem").normalize))

  private def _resolve_descriptor_file_in(path: Path): Option[Path] =
    _canonical_descriptor_files
      .map(path.resolve(_).normalize)
      .find(Files.isRegularFile(_))

  private def _resolve_assembly_descriptor_file(path: Path): Option[Path] =
    _canonical_assembly_descriptor_files
      .map(path.resolve(_).normalize)
      .find(Files.isRegularFile(_))

  private def _is_archive_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase
    name.endsWith(".sar") || name.endsWith(".car") || name.endsWith(".zip")
  }

  private def _load_record(
    path: Path,
    source: String
  ): Option[GenericSubsystemAssemblyDescriptorSource] =
    DescriptorRecordLoader.load(path).toOption.flatMap(_.headOption).map { record =>
      GenericSubsystemAssemblyDescriptorSource(record, source, Some(path))
    }

  private def _load_assembly_descriptor_consequence(
    path: Path,
    source: String
  ): Consequence[Option[GenericSubsystemAssemblyDescriptorSource]] =
    if (!Files.exists(path))
      Consequence.success(None)
    else if (Files.isDirectory(path))
      _resolve_assembly_descriptor_file(path) match {
        case Some(file) => _load_record_consequence(file, source).map(Some(_))
        case None => Consequence.success(None)
      }
    else if (_is_archive_file(path))
      _load_assembly_descriptor_from_archive_consequence(path, source)
    else
      _load_record_consequence(path, source).map(Some(_))

  private def _load_record_consequence(
    path: Path,
    source: String
  ): Consequence[GenericSubsystemAssemblyDescriptorSource] =
    DescriptorRecordLoader.load(path).flatMap { records =>
      records.headOption match {
        case Some(record) =>
          Consequence.success(GenericSubsystemAssemblyDescriptorSource(record, source, Some(path)))
        case None =>
          Consequence.resourceInvalid(s"assembly descriptor is empty: ${path}")
      }
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

  private def _load_assembly_descriptor_from_archive_consequence(
    path: Path,
    source: String
  ): Consequence[Option[GenericSubsystemAssemblyDescriptorSource]] = {
    val uri = URI.create(s"jar:${path.toUri}")
    Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
      _resolve_assembly_descriptor_file(fs.getPath("/")) match {
        case Some(file) => _load_record_consequence(file, source).map(Some(_))
        case None => Consequence.success(None)
      }
    }
  }

  private def _decode_optional_assembly_shape(
    path: Path,
    assembly: Option[GenericSubsystemAssemblyDescriptorSource]
  ): Consequence[Option[Shape]] =
    assembly match {
      case Some(source) =>
        summon[RecordDecoder[Shape]].fromRecord(source.record).map(Some(_)).leftMap { c =>
          c.copy(observation = c.observation.copy(cause = c.observation.cause.withMessage(s"${c.displayMessage} in assembly descriptor of ${path}")))
        }
      case None => Consequence.success(None)
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

  private def _role_record(name: String, rec: Record): Record =
    if (rec.getString("name").exists(_.trim.nonEmpty))
      rec
    else
      Record.create(rec.asMap.toVector :+ ("name" -> name))

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

  given RecordDecoder[GenericSubsystemMessageDeliveryProviderBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemMessageDeliveryProviderBinding] = {
      val name = _string(rec, "name")
      val component = _string(rec, "component")
      (name, component) match {
        case (Some(n), Some(c)) =>
          Consequence.success(
            GenericSubsystemMessageDeliveryProviderBinding(
              name = n,
              component = c,
              channel = _string(rec, "channel"),
              enabled = _boolean(rec, "enabled"),
              priority = _int(rec, "priority"),
              isDefault = _boolean(rec, "default", "isDefault")
            )
          )
        case _ =>
          Consequence.argumentMissing("message-delivery provider name/component")
      }
    }

  given RecordDecoder[GenericSubsystemMessageDeliveryBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemMessageDeliveryBinding] = {
      val providers = rec.getAny("providers") match {
        case Some(xs: Seq[?]) =>
          xs.toVector.flatMap(_any_to_record).flatMap(r => summon[RecordDecoder[GenericSubsystemMessageDeliveryProviderBinding]].fromRecord(r).toOption)
        case _ =>
          Vector.empty
      }
      Consequence.success(
        GenericSubsystemMessageDeliveryBinding(
          providers = providers
        )
      )
    }

  given RecordDecoder[SecurityRoleDefinition] with
    def fromRecord(rec: Record): Consequence[SecurityRoleDefinition] = {
      val name = _string(rec, "name")
      name match {
        case Some(n) =>
          Consequence.success(
            SecurityRoleDefinition(
              name = n,
              includes = _string_vector(rec, List("includes", "include", "roles")),
              capabilities = _string_vector(rec, List("capabilities", "capability"))
            )
          )
        case None =>
          Consequence.argumentMissing("authorization role name")
      }
    }

  given RecordDecoder[GenericSubsystemAuthorizationBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemAuthorizationBinding] = {
      val roles =
        _record_value(rec, List("roles", "role")).map { roleRecords =>
          _sequence(roleRecords.asMap.toVector.map {
            case (name, r: Record) =>
              summon[RecordDecoder[SecurityRoleDefinition]]
                .fromRecord(_role_record(name, r))
                .map(x => x.name -> x)
            case (name, m: Map[?, ?]) =>
              summon[RecordDecoder[SecurityRoleDefinition]]
                .fromRecord(_role_record(name, _map_to_record(m)))
                .map(x => x.name -> x)
            case (name, m: java.util.Map[?, ?]) =>
              summon[RecordDecoder[SecurityRoleDefinition]]
                .fromRecord(_role_record(name, _map_to_record(m.asScala.toMap)))
                .map(x => x.name -> x)
            case (name, _) =>
              Consequence.argumentInvalid(s"authorization role must be a mapping: ${name}")
          }).map(_.toMap)
        }.getOrElse(Consequence.success(Map.empty[String, SecurityRoleDefinition]))
      val resources = _resource_policies(rec)
      for {
        r <- roles
        p <- resources
      } yield GenericSubsystemAuthorizationBinding(roles = r, resources = p)
    }

  private def _resource_policies(
    rec: Record
  ): Consequence[AuthorizationResourcePolicies] =
    _record_value(rec, List("resources", "resource", "accessMappings", "access_mappings")) match {
      case Some(r) =>
        for {
          collections <- _resource_family_policies(r, "collections", "collection")
          associations <- _resource_family_policies(r, "associations", "association")
          stores <- _resource_family_policies(r, "stores", "store")
        } yield AuthorizationResourcePolicies(collections, associations, stores)
      case None =>
        Consequence.success(AuthorizationResourcePolicies.empty)
    }

  private def _resource_family_policies(
    rec: Record,
    keys: String*
  ): Consequence[Map[String, Map[String, AuthorizationResourcePolicy]]] =
    _record_value(rec, keys.toList) match {
      case Some(family) =>
        _sequence(family.asMap.toVector.map {
          case (name, r: Record) => _resource_action_policies(name, r)
          case (name, m: Map[?, ?]) => _resource_action_policies(name, _map_to_record(m))
          case (name, m: java.util.Map[?, ?]) => _resource_action_policies(name, _map_to_record(m.asScala.toMap))
          case (name, _) => Consequence.argumentInvalid(s"authorization resource policy must be a mapping: ${name}")
        }).map(_.toMap)
      case None =>
        Consequence.success(Map.empty)
    }

  private def _resource_action_policies(
    name: String,
    rec: Record
  ): Consequence[(String, Map[String, AuthorizationResourcePolicy])] =
    _sequence(rec.asMap.toVector.map {
      case (action, r: Record) => _resource_policy(action, r)
      case (action, m: Map[?, ?]) => _resource_policy(action, _map_to_record(m))
      case (action, m: java.util.Map[?, ?]) => _resource_policy(action, _map_to_record(m.asScala.toMap))
      case (action, s: String) => _resource_policy(action, Record.dataAuto("capability" -> s))
      case (action, _) => Consequence.argumentInvalid(s"authorization resource action policy must be a mapping: ${name}.${action}")
    }).map(xs => SecuritySubject.normalize(name) -> xs.toMap)

  private def _resource_policy(
    action: String,
    rec: Record
  ): Consequence[(String, AuthorizationResourcePolicy)] =
    _resource_permission(rec, action).map { permission =>
      SecuritySubject.normalize(action) ->
        AuthorizationResourcePolicy(
          capabilities = _string_vector(rec, List("capabilities", "capability")),
          permission = permission
        )
    }

  private def _resource_permission(
    rec: Record,
    action: String
  ): Consequence[Option[String]] =
    _string(rec, "permission") match {
      case Some(value) =>
        val normalized = value.trim.toLowerCase(java.util.Locale.ROOT)
        if (Set("read", "write", "execute").contains(normalized))
          Consequence.success(Some(normalized))
        else
          Consequence.argumentInvalid(s"authorization resource permission must be read/write/execute: ${action}.${value}")
      case None =>
        Consequence.success(None)
    }

  private def _sequence[A](
    xs: Vector[Consequence[A]]
  ): Consequence[Vector[A]] =
    xs.foldLeft(Consequence.success(Vector.empty[A])) { (z, x) =>
      z.flatMap(xs => x.map(v => xs :+ v))
    }

  given RecordDecoder[GenericSubsystemSecurityBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemSecurityBinding] = {
      val auth = _record_value(rec, List("authentication"))
        .flatMap(r => summon[RecordDecoder[GenericSubsystemAuthenticationBinding]].fromRecord(r).toOption)
      val messageDelivery = _record_value(rec, List("message_delivery", "messageDelivery", "notification"))
        .flatMap(r => summon[RecordDecoder[GenericSubsystemMessageDeliveryBinding]].fromRecord(r).toOption)
      val authorization =
        _record_value(rec, List("authorization")) match {
          case Some(r) =>
            summon[RecordDecoder[GenericSubsystemAuthorizationBinding]].fromRecord(r).map(Some(_))
          case None =>
            Consequence.success(None)
        }
      authorization.map(a => GenericSubsystemSecurityBinding(auth, messageDelivery, a))
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
            _security_value(rec).map { security =>
              Shape(
                subsystemName = name,
                version = _string(rec, "version"),
                componentBindings = bindings,
                extensions = _string_map_value(rec, List("extension", "extensions")),
                config = _string_map_value(rec, List("config")),
                wiring = _wiring_value(rec),
                security = security,
                builtin = _record_value(rec, List("builtin", "builtins")).flatMap(r => summon[RecordDecoder[GenericSubsystemBuiltinBinding]].fromRecord(r).toOption),
                operationAuthorization = _operation_authorization_value(rec)
              )
            }
        case None =>
          Consequence.argumentMissing("subsystem/subsystemName/name")
      }
    }

  private def _security_value(
    rec: Record
  ): Consequence[Option[GenericSubsystemSecurityBinding]] =
    _record_value(rec, List("security")) match {
      case Some(r) =>
        summon[RecordDecoder[GenericSubsystemSecurityBinding]].fromRecord(r).map(Some(_))
      case None =>
        Consequence.success(None)
    }
}
