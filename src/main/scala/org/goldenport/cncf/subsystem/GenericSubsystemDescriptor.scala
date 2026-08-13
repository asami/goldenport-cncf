package org.goldenport.cncf.subsystem

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.record.RecordDecoder
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentId, ComponentInstanceId, ComponentInstanceMetadata, SubsystemCapabilityId}
import org.goldenport.cncf.component.ComponentDescriptorLoader
import org.goldenport.cncf.component.DescriptorRecordLoader
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.rule.{RuleSet, RuleSetDescriptor}
import org.goldenport.cncf.security.{AuthorizationResourcePolicies, AuthorizationResourcePolicy, OperationAuthorizationRule, SecurityRoleDefinition, SecuritySubject}
import org.goldenport.cncf.spi.{SpiCardinality, SpiProviderSelector, SpiRuntimeBinding, SpiSelection, SpiSocketSelector}

/*
 * @since   Apr.  7, 2026
 *  version Apr. 28, 2026
 *  version May.  7, 2026
 * @version Aug. 13, 2026
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

final case class GenericSubsystemLocalSubjectBinding(
  id: String,
  roles: Vector[String] = Vector.empty,
  capabilities: Vector[String] = Vector.empty,
  attributes: Map[String, String] = Map.empty,
  securityLevel: Option[String] = None
)

final case class GenericSubsystemAuthenticationBinding(
  convention: Option[String] = None,
  fallbackPrivilege: Option[String] = None,
  localSubject: Option[GenericSubsystemLocalSubjectBinding] = None,
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

/**
 * Declares a component instance as an authority for a subsystem capability.
 *
 * This is deliberately distinct from `GenericSubsystemComponentBinding.capabilities`.
 * The latter is instance-selection metadata (for example, `html` or
 * `same-origin`), whereas this declaration is the typed authority consumed by
 * component-style assembly admission.
 */
final case class GenericSubsystemCapabilityProviderBinding(
  name: String,
  component: String,
  capabilities: Vector[SubsystemCapabilityId] = Vector.empty
)

final case class GenericSubsystemUserNotificationProviderBinding(
  name: String,
  component: String,
  channel: Option[String] = None,
  enabled: Option[Boolean] = None,
  priority: Option[Int] = None,
  isDefault: Option[Boolean] = None
)

final case class GenericSubsystemUserNotificationEventForwardingBinding(
  event: String,
  provider: Option[String] = None,
  channel: Option[String] = None,
  enabled: Option[Boolean] = None,
  appVisibleOnly: Option[Boolean] = None,
  asyncOnly: Option[Boolean] = None,
  notificationType: Option[String] = None,
  priority: Option[String] = None,
  dedupeKey: Option[String] = None
)

final case class GenericSubsystemUserNotificationBinding(
  providers: Vector[GenericSubsystemUserNotificationProviderBinding] = Vector.empty,
  eventForwarding: Vector[GenericSubsystemUserNotificationEventForwardingBinding] = Vector.empty
)

final case class GenericSubsystemRuntimeBinding(
  userNotification: Option[GenericSubsystemUserNotificationBinding] = None
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
  spi: Vector[GenericSubsystemPortBinding] = Vector.empty,
  instance: Option[String] = None,
  config: Map[String, String] = Map.empty,
  rules: Record = Record.empty,
  purposes: Vector[String] = Vector.empty,
  tags: Vector[String] = Vector.empty,
  priority: Option[Int] = None,
  isDefault: Option[Boolean] = None,
  capabilities: Vector[String] = Vector.empty,
  componentId: Option[ComponentId] = None
) {
  def componentVersion: Option[String] =
    version.orElse(coordinate.flatMap(GenericSubsystemDescriptor.coordinateVersion))

  def runtimeComponentName: String =
    componentId.map(_.name).getOrElse(GenericSubsystemDescriptor.runtimeComponentName(componentName))

  def canonicalInstanceId: Option[ComponentInstanceId] =
    componentId.map(ComponentInstanceId(_, instanceName))

  def instanceName: String = instance.getOrElse("default")

  def instanceMetadata: ComponentInstanceMetadata =
    ComponentInstanceMetadata(
      componentName = componentName,
      instance = instanceName,
      config = config,
      rules = rules,
      purposes = purposes,
      tags = tags,
      priority = priority.getOrElse(0),
      isDefault = isDefault.getOrElse(false),
      capabilities = capabilities,
      componentId = componentId
    )

  def hasInstanceDeclaration: Boolean =
    instance.nonEmpty || config.nonEmpty || rules.fields.nonEmpty ||
      purposes.nonEmpty || tags.nonEmpty || priority.nonEmpty || isDefault.nonEmpty || capabilities.nonEmpty

  def toComponentDescriptor: ComponentDescriptor =
    ComponentDescriptor(
      name = Some(componentName),
      version = componentVersion,
      componentName = Some(componentName),
      extensionBindings = extensionBindings,
      schemaVersion = componentId.map(_ => 3),
      componentId = componentId
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
  runtime: Option[GenericSubsystemRuntimeBinding] = None,
  security: Option[GenericSubsystemSecurityBinding] = None,
  builtin: Option[GenericSubsystemBuiltinBinding] = None,
  operationAuthorization: Map[String, OperationAuthorizationRule] = Map.empty,
  ruleSets: Vector[RuleSet] = Vector.empty,
  subsystemCapabilityProviders: Vector[GenericSubsystemCapabilityProviderBinding] = Vector.empty,
  componentDescriptorOverrides: Vector[ComponentDescriptor] = Vector.empty,
  implicitRootComponentName: Option[String] = None
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
    componentBindings.map { binding =>
      componentDescriptorOverrides.find { descriptor =>
        binding.componentId match {
          case Some(id) => descriptor.componentId.contains(id)
          case None => GenericSubsystemDescriptor.runtimeComponentName(descriptor.componentName.orElse(descriptor.name).getOrElse("")) == binding.runtimeComponentName
        }
      }.getOrElse(binding.toComponentDescriptor)
    }

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
    runtime: Option[GenericSubsystemRuntimeBinding],
    security: Option[GenericSubsystemSecurityBinding],
    builtin: Option[GenericSubsystemBuiltinBinding],
    operationAuthorization: Map[String, OperationAuthorizationRule],
    ruleSets: Vector[RuleSet],
    subsystemCapabilityProviders: Vector[GenericSubsystemCapabilityProviderBinding]
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
      runtime = _merge_runtime(defaults.runtime, overrideDescriptor.runtime),
      security = _merge_security(defaults.security, overrideDescriptor.security),
      builtin = overrideDescriptor.builtin.orElse(defaults.builtin),
      operationAuthorization = defaults.operationAuthorization ++ overrideDescriptor.operationAuthorization,
      ruleSets = if (overrideDescriptor.ruleSets.nonEmpty) overrideDescriptor.ruleSets else defaults.ruleSets,
      subsystemCapabilityProviders = if (overrideDescriptor.subsystemCapabilityProviders.nonEmpty) overrideDescriptor.subsystemCapabilityProviders else defaults.subsystemCapabilityProviders,
      assemblyDescriptor = defaults.assemblyDescriptor
    )

  def applyAssemblyOverride(
    descriptor: GenericSubsystemDescriptor,
    source: GenericSubsystemAssemblyDescriptorSource
  ): GenericSubsystemDescriptor =
    applyAssemblyOverrideC(descriptor, source).TAKE

  def applyAssemblyOverrideC(
    descriptor: GenericSubsystemDescriptor,
    source: GenericSubsystemAssemblyDescriptorSource
  ): Consequence[GenericSubsystemDescriptor] = {
    val rec = source.record
    _override_bindings_from_record_c(rec).flatMap { bindings =>
      for {
        runtime <- _optional_runtime_c(rec)
        security <- _security_value(rec)
        builtin <- _optional_builtin_c(rec)
        rulesets <- _rule_sets_c(rec)
        capabilityproviders <- _subsystem_capability_providers_c(rec)
      } yield {
        descriptor.copy(
          subsystemName = _string(rec, "subsystem", "subsystemName", "name").getOrElse(descriptor.subsystemName),
          version = _string(rec, "version").orElse(descriptor.version),
          componentBindings = if (bindings.nonEmpty) bindings else descriptor.componentBindings,
          extensions = descriptor.extensions ++ _string_map_value(rec, List("extension", "extensions")),
          config = descriptor.config ++ _string_map_value(rec, List("config")),
          wiring = _merge_record(descriptor.wiring, _wiring_value(rec)),
          runtime = _merge_runtime(descriptor.runtime, runtime),
          security = _merge_security(descriptor.security, security),
          builtin = builtin.orElse(descriptor.builtin),
          operationAuthorization = descriptor.operationAuthorization ++ _operation_authorization_value(rec),
          ruleSets = if (_has_rule_sets(rec)) rulesets else descriptor.ruleSets,
          subsystemCapabilityProviders = if (_has_subsystem_capability_providers(rec)) capabilityproviders else descriptor.subsystemCapabilityProviders,
          assemblyDescriptor = Some(_merge_assembly_sources(descriptor.assemblyDescriptor, source))
        )
      }
    }
  }

  private def _override_bindings_from_record_c(
    rec: Record
  ): Consequence[Vector[GenericSubsystemComponentBinding]] =
    if (rec.getAny("components").nonEmpty || rec.getAny("component").nonEmpty)
      _bindings_from_record_c(rec)
    else
      Consequence.success(Vector.empty)

  private def _merge_assembly_sources(
    defaults: Option[GenericSubsystemAssemblyDescriptorSource],
    overridesource: GenericSubsystemAssemblyDescriptorSource
  ): GenericSubsystemAssemblyDescriptorSource =
    defaults match {
      case Some(base) =>
        overridesource.copy(record = _merge_assembly_records(base.record, overridesource.record))
      case None =>
        overridesource
    }

  private def _merge_assembly_records(
    defaults: Record,
    overrides: Record
  ): Record = {
    val base = defaults.asMap
    val over = overrides.asMap
    val wiring = _merge_assembly_wiring(base.get("wiring"), over.get("wiring"))
    val spi = _merge_assembly_spi(base.get("spi"), over.get("spi"))
    val entries = base.toVector ++ over.toVector.filterNot {
      case (key, _) => key == "wiring" || key == "spi"
    }
    val withwiring = wiring match {
      case Some(value) => entries.filterNot(_._1 == "wiring") :+ ("wiring" -> value)
      case None => entries
    }
    val withspi = spi match {
      case Some(value) => withwiring.filterNot(_._1 == "spi") :+ ("spi" -> value)
      case None => withwiring
    }
    Record.create(withspi)
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
      val overridekeys = over.flatMap(_wiring_binding_key).toSet
      Some(base.filterNot(r => _wiring_binding_key(r).exists(overridekeys.contains)) ++ over)
    }
  }

  private def _merge_assembly_spi(
    defaults: Option[Any],
    overrides: Option[Any]
  ): Option[Record] =
    (defaults.flatMap(_any_to_record), overrides.flatMap(_any_to_record)) match {
      case (None, None) => None
      case (Some(record), None) => Some(record)
      case (None, Some(record)) => Some(record)
      case (Some(base), Some(over)) =>
        val bindings = _merge_assembly_spi_bindings(base.getAny("bindings"), over.getAny("bindings"))
        val entries = base.asMap.toVector ++ over.asMap.toVector.filterNot(_._1 == "bindings")
        bindings match {
          case Some(value) => Some(Record.create(entries.filterNot(_._1 == "bindings") :+ ("bindings" -> value)))
          case None => Some(Record.create(entries))
        }
    }

  private def _merge_assembly_spi_bindings(
    defaults: Option[Any],
    overrides: Option[Any]
  ): Option[Vector[Record]] = {
    val base = _records_value(defaults)
    val over = _records_value(overrides)
    if (base.isEmpty && over.isEmpty) {
      None
    } else {
      val overridekeys = over.flatMap(_spi_binding_key).toSet
      Some(base.filterNot(r => _spi_binding_key(r).exists(overridekeys.contains)) ++ over)
    }
  }

  private def _spi_binding_key(
    rec: Record
  ): Option[String] =
    rec.getAny("socket").flatMap(_any_to_record).flatMap { socket =>
      _string(socket, "contract").map { contract =>
        val cardinality = _spi_cardinality_key(socket)
        val socketkey = Vector(
          _string(socket, "component").getOrElse(""),
          _string(socket, "instance").getOrElse(""),
          _string(socket, "name").getOrElse(""),
          contract,
          cardinality
        ).map(_comparison_key)
        val providerkey =
          if (_is_many_spi_cardinality(cardinality)) {
            rec.getAny("provider").flatMap(_any_to_record).toVector.flatMap { provider =>
              Vector(
                _string(provider, "component").getOrElse(""),
                _string(provider, "instance").getOrElse("")
              ).map(_comparison_key)
            }
          } else {
            Vector.empty
          }
        (socketkey ++ providerkey).mkString("/")
      }
    }

  private def _is_many_spi_cardinality(value: String): Boolean =
    Set("many", "set", "zero-or-more", "one-or-more", "non-empty").contains(
      value.trim.toLowerCase.replace('_', '-').replace(' ', '-')
    )

  private def _spi_cardinality_key(socket: Record): String = {
    val value = _string(socket, "cardinality").getOrElse("one")
    val normalized = value.trim.toLowerCase.replace('_', '-').replace(' ', '-')
    val required = _boolean(socket, "required").getOrElse(
      !Set("optional", "zero-or-one", "many", "set", "zero-or-more").contains(normalized)
    )
    if (_is_many_spi_cardinality(normalized)) {
      if (required) "one-or-more" else "many"
    } else {
      if (required) "one" else "optional"
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
      val mergedoverrides = overrides.map { overridebinding =>
        defaults
          .filter(_component_binding_matches(_, overridebinding))
          .foldLeft(overridebinding) { (merged, defaultbinding) =>
            _merge_component_binding(defaultbinding, merged)
          }
      }
      val (mergeddefaults, emittedoverrides) = defaults.foldLeft(
        Vector.empty[GenericSubsystemComponentBinding] -> Set.empty[Int]
      ) { case ((result, emitted), defaultbinding) =>
        val matching = overrides.indices.filter { index =>
          _component_binding_matches(defaultbinding, overrides(index))
        }
        val pending = matching.filterNot(emitted.contains)
        val nextresult =
          if (pending.nonEmpty)
            result ++ pending.map(mergedoverrides)
          else if (matching.isEmpty)
            result :+ defaultbinding
          else
            result
        nextresult -> (emitted ++ matching)
      }
      mergeddefaults ++ mergedoverrides.zipWithIndex.collect {
        case (binding, index) if !emittedoverrides.contains(index) => binding
      }
    }

  private def _component_binding_matches(
    defaults: GenericSubsystemComponentBinding,
    overrides: GenericSubsystemComponentBinding
  ): Boolean =
    _component_binding_component_key(defaults) == _component_binding_component_key(overrides) &&
      (defaults.instance.isEmpty ||
        _component_binding_instance_key_for_merge(defaults) == _component_binding_instance_key_for_merge(overrides))

  private def _component_binding_component_key(
    binding: GenericSubsystemComponentBinding
  ): String =
    binding.componentId
      .orElse(ComponentId.parseC(binding.componentName).toOption)
      .map(id => s"canonical:${id.name}")
      .getOrElse(s"legacy:${_comparison_key(runtimeComponentName(binding.componentName))}")

  private def _component_binding_instance_key_for_merge(
    binding: GenericSubsystemComponentBinding
  ): String =
    binding.componentId
      .orElse(ComponentId.parseC(binding.componentName).toOption)
      .map(id => ComponentInstanceId(id, binding.instanceName).canonicalKey)
      .getOrElse(_component_binding_instance_key(binding))

  private def _merge_component_binding(
    defaults: GenericSubsystemComponentBinding,
    overrides: GenericSubsystemComponentBinding
  ): GenericSubsystemComponentBinding =
    overrides.copy(
      version = overrides.version.orElse(defaults.version),
      coordinate = overrides.coordinate.orElse(defaults.coordinate),
      extensionBindings = _merge_record(defaults.extensionBindings, overrides.extensionBindings),
      api = if (overrides.api.nonEmpty) overrides.api else defaults.api,
      spi = if (overrides.spi.nonEmpty) overrides.spi else defaults.spi,
      instance = overrides.instance.orElse(defaults.instance),
      config = defaults.config ++ overrides.config,
      rules = _merge_record(defaults.rules, overrides.rules),
      purposes = if (overrides.purposes.nonEmpty) overrides.purposes else defaults.purposes,
      tags = if (overrides.tags.nonEmpty) overrides.tags else defaults.tags,
      priority = overrides.priority.orElse(defaults.priority),
      isDefault = overrides.isDefault.orElse(defaults.isDefault),
      capabilities = if (overrides.capabilities.nonEmpty) overrides.capabilities else defaults.capabilities,
      componentId = overrides.componentId
    )

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

  private def _merge_runtime(
    defaults: Option[GenericSubsystemRuntimeBinding],
    overrides: Option[GenericSubsystemRuntimeBinding]
  ): Option[GenericSubsystemRuntimeBinding] =
    (defaults, overrides) match {
      case (None, None) => None
      case (Some(x), None) => Some(x)
      case (None, Some(x)) => Some(x)
      case (Some(a), Some(b)) =>
        Some(GenericSubsystemRuntimeBinding(
          userNotification = _merge_user_notification(a.userNotification, b.userNotification)
        ))
    }

  private def _merge_user_notification(
    defaults: Option[GenericSubsystemUserNotificationBinding],
    overrides: Option[GenericSubsystemUserNotificationBinding]
  ): Option[GenericSubsystemUserNotificationBinding] =
    (defaults, overrides) match {
      case (None, None) => None
      case (Some(x), None) => Some(x)
      case (None, Some(x)) => Some(x)
      case (Some(a), Some(b)) =>
        Some(GenericSubsystemUserNotificationBinding(
          providers = _merge_user_notification_providers(a.providers, b.providers),
          eventForwarding =
            if (b.eventForwarding.nonEmpty) b.eventForwarding else a.eventForwarding
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
        val overridekeys = b.roles.keys.map(_comparison_key).toSet
        val inherited = a.roles.filterNot { case (name, _) => overridekeys.contains(_comparison_key(name)) }
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
          localSubject = b.localSubject.orElse(a.localSubject),
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

  private def _merge_user_notification_providers(
    defaults: Vector[GenericSubsystemUserNotificationProviderBinding],
    overrides: Vector[GenericSubsystemUserNotificationProviderBinding]
  ): Vector[GenericSubsystemUserNotificationProviderBinding] =
    _merge_by_name(defaults, overrides)(_.name)

  private def _merge_by_name[A](
    defaults: Vector[A],
    overrides: Vector[A]
  )(name: A => String): Vector[A] = {
    val overridekeys = overrides.map(x => _comparison_key(name(x))).toSet
    defaults.filterNot(x => overridekeys.contains(_comparison_key(name(x)))) ++ overrides
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
    descriptor: ComponentDescriptor,
    includeAssemblyDescriptor: Boolean = true
  ): Consequence[GenericSubsystemDescriptor] =
    descriptor.requireCanonicalIdentityC.flatMap { _ =>
      _from_canonical_component_descriptor(path, descriptor, includeAssemblyDescriptor)
    }

  private def _from_canonical_component_descriptor(
    path: Path,
    descriptor: ComponentDescriptor,
    includeassemblydescriptor: Boolean
  ): Consequence[GenericSubsystemDescriptor] = {
    descriptor.requireCanonicalIdentityC.flatMap { case (componentid, release) =>
      val componentname = componentid.name
      val primary = GenericSubsystemComponentBinding(
        componentName = componentname,
        version = Some(release),
        coordinate = None,
        extensionBindings = descriptor.extensionBindings,
        componentId = Some(componentid)
      )
      val assemblyc =
        if (includeassemblydescriptor)
          _load_assembly_descriptor_consequence(path, "component-car")
        else
          Consequence.success(None)
      assemblyc.flatMap { assembly =>
        _decode_optional_assembly_shape(path, assembly).flatMap { shape =>
          _implicit_subsystem_name_c(path, descriptor, shape).flatMap { subsystemname =>
            _canonical_component_bindings_c(componentid, primary, shape.map(_.componentBindings)).map { bindings =>
              GenericSubsystemDescriptor(
                path = path,
                subsystemName = subsystemname,
                version = shape.flatMap(_.version).orElse(descriptor.version),
                componentBindings = bindings,
                extensions = descriptor.extensions ++ shape.map(_.extensions).getOrElse(Map.empty),
                config = shape.map(_.config).getOrElse(Map.empty),
                wiring = shape.map(_.wiring).getOrElse(Record.empty),
                assemblyDescriptor = assembly,
                runtime = shape.flatMap(_.runtime),
                security = shape.flatMap(_.security),
                builtin = shape.flatMap(_.builtin),
                operationAuthorization = shape.map(_.operationAuthorization).getOrElse(Map.empty),
                ruleSets = shape.map(_.ruleSets).getOrElse(Vector.empty),
                subsystemCapabilityProviders = shape.map(_.subsystemCapabilityProviders).getOrElse(Vector.empty),
                componentDescriptorOverrides = Vector(descriptor),
                implicitRootComponentName = Some(componentid.name)
              )
            }
          }
        }
      }
    }
  }

  private def _canonical_component_bindings_c(
    componentid: ComponentId,
    primary: GenericSubsystemComponentBinding,
    source: Option[Vector[GenericSubsystemComponentBinding]]
  ): Consequence[Vector[GenericSubsystemComponentBinding]] = {
    val bindings = source.filter(_.nonEmpty).getOrElse(Vector.empty)
    bindings.collectFirst {
      case binding if binding.componentId.isEmpty || binding.version.isEmpty => binding
    } match {
      case Some(binding) =>
        Consequence.resourceInvalid(
          s"canonical component assembly binding requires namespace/id/version: " +
            s"legacy=${binding.componentName}; required=${componentid.name}"
        )
      case None =>
        val augmented =
          if (bindings.exists(_.componentId.contains(componentid))) bindings
          else primary +: bindings
        _validate_component_bindings_c(augmented)
    }
  }

  private def _implicit_subsystem_name_c(
    path: Path,
    descriptor: ComponentDescriptor,
    assembly: Option[Shape]
  ): Consequence[String] =
    assembly.map(_.subsystemName).orElse(
      descriptor.subsystemName.orElse(descriptor.componentId.map(_.localId.value())).orElse(descriptor.componentName).orElse(descriptor.name)
    ).map(_.trim).filter(_.nonEmpty) match {
      case Some(value) => Consequence.success(value)
      case None =>
        Consequence.resourceInvalid(
          s"implicit Subsystem requires descriptor-owned subsystemName, componentName, or name: $path"
        )
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

  /**
   * Loads an explicitly configured assembly descriptor. Unlike the
   * compatibility Option loader, a missing, empty, unreadable, or malformed
   * configured source is an invalid configuration rather than an absence.
   */
  def loadAssemblyDescriptorC(
    path: Path
  ): Consequence[GenericSubsystemAssemblyDescriptorSource] =
    if (path == null)
      Consequence.configurationInvalid("assembly descriptor path is invalid")
    else if (!Files.exists(path))
      Consequence.resourceNotFound(s"assembly descriptor path does not exist: ${path}")
    else
      try {
        _load_assembly_descriptor_consequence(path, "config").flatMap {
          case Some(source) => Consequence.success(source)
          case None => Consequence.resourceNotFound(s"assembly descriptor not found: ${path}")
        }
      } catch {
        case NonFatal(_) =>
          Consequence.resourceInvalid(s"assembly descriptor cannot be loaded: ${path}")
      }

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

  private def _optional_runtime_c(
    rec: Record
  ): Consequence[Option[GenericSubsystemRuntimeBinding]] =
    _optional_record_decode_c(
      rec,
      List("runtime"),
      "runtime"
    )(summon[RecordDecoder[GenericSubsystemRuntimeBinding]])

  private def _optional_builtin_c(
    rec: Record
  ): Consequence[Option[GenericSubsystemBuiltinBinding]] =
    _optional_record_decode_c(
      rec,
      List("builtin", "builtins"),
      "builtin"
    )(summon[RecordDecoder[GenericSubsystemBuiltinBinding]])

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
              _load_assembly_descriptor_consequence(root, "sar").flatMap { assemblydescriptor =>
                _from_record(path, rec, assemblydescriptor.map(_.copy(path = Some(path))))
              }
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
    assemblydescriptor: Option[GenericSubsystemAssemblyDescriptorSource] = None
  ): Consequence[GenericSubsystemDescriptor] = {
    val assemblydescriptor0 =
      if (_is_archive_file(path)) assemblydescriptor
      else assemblydescriptor.orElse(loadAdjacentAssemblyDescriptor(path))
    summon[RecordDecoder[Shape]].fromRecord(rec).map { s =>
      GenericSubsystemDescriptor(
        path = path,
        subsystemName = s.subsystemName,
        version = s.version,
        componentBindings = s.componentBindings,
        extensions = s.extensions,
        config = s.config,
        wiring = s.wiring,
        assemblyDescriptor = assemblydescriptor0,
        runtime = s.runtime,
        security = s.security,
        builtin = s.builtin,
        operationAuthorization = s.operationAuthorization,
        ruleSets = s.ruleSets,
        subsystemCapabilityProviders = s.subsystemCapabilityProviders
      )
    }.leftMap { c =>
      c.copy(observation = c.observation.copy(cause = c.observation.cause.withMessage(s"${c.displayMessage} in ${path}")))
    }
  }

  private def _bindings_from_record_c(
    rec: Record
  ): Consequence[Vector[GenericSubsystemComponentBinding]] =
    rec.getAny("components") match {
      case Some(xs: Seq[?]) =>
        _component_bindings_from_values(xs.toVector)
      case Some(xs: java.util.List[?]) =>
        _component_bindings_from_values(xs.asScala.toVector)
      case Some(_) =>
        Consequence.resourceInvalid("components must be a list of component declarations")
      case _ =>
        rec.getRecord("component") match {
          case Some(r) if _string(rec, "component").isEmpty =>
            _sequence(r.asMap.toVector.map { case (k, v) =>
              _any_to_record(v) match {
                case Some(bindingrecord) => _binding_from_record_c(bindingrecord, Some(k))
                case None => Consequence.resourceInvalid(s"component.${k} must be a component declaration")
              }
            }).flatMap(_validate_component_bindings_c)
          case _ =>
            _binding_from_record_c(rec, None).map(Vector(_)).flatMap(_validate_component_bindings_c)
        }
    }

  private def _component_bindings_from_values(
    values: Vector[Any]
  ): Consequence[Vector[GenericSubsystemComponentBinding]] =
    _sequence(values.zipWithIndex.map { case (value, index) =>
      _any_to_record(value) match {
        case Some(bindingrecord) => _binding_from_record_c(bindingrecord, None)
        case None => Consequence.resourceInvalid(s"components[${index}] must be a component declaration")
      }
    }).flatMap(_validate_component_bindings_c)

  private[cncf] def _validate_component_bindings_c(
    bindings: Vector[GenericSubsystemComponentBinding]
  ): Consequence[Vector[GenericSubsystemComponentBinding]] = {
    bindings.collectFirst {
      case binding if binding.componentId.isEmpty || binding.version.isEmpty => binding
    } match {
      case Some(binding) =>
        Consequence.resourceInvalid(
          s"component assembly binding requires canonical namespace/id/version: " +
            s"alias=${binding.componentName}; required=canonical namespace/id/version"
        )
      case None =>
        val duplicate = bindings
          .groupBy(_component_binding_instance_key)
          .collectFirst { case (id, xs) if xs.size > 1 => id }
        val duplicatedefault = bindings
          .filter(_.isDefault.contains(true))
          .groupBy(_component_binding_default_key)
          .collectFirst { case (componentid, xs) if xs.size > 1 => componentid }
        duplicate match {
          case Some(id) => Consequence.resourceInvalid(s"duplicate component instance id: ${id}")
          case None => duplicatedefault match {
            case Some(component) => Consequence.resourceInvalid(s"multiple default component instances: ${component}")
            case None => Consequence.success(bindings)
          }
        }
      }
    }

  private def _component_binding_instance_key(
    binding: GenericSubsystemComponentBinding
  ): String =
    binding.componentId.map(id => ComponentInstanceId(id, binding.instanceName).canonicalKey).getOrElse(
      s"canonical-required:${binding.componentName}@${binding.instanceName}"
    )

  private def _component_binding_default_key(
    binding: GenericSubsystemComponentBinding
  ): String =
    binding.componentId.map(id => ComponentInstanceId.default(id).canonicalKey).getOrElse(
      s"canonical-required:${binding.componentName}@default"
    )

  private def _binding_from_record_c(
    rec: Record,
    defaultname: Option[String]
  ): Consequence[GenericSubsystemComponentBinding] =
    _component_binding_identity_c(rec, defaultname).flatMap { case (name, componentid, release) =>
      val version = Some(release)
      val coordinate = _string(rec, "coordinate")
      val instance = _string(rec, "instance")
      val instancespecified = rec.getAny("instance").nonEmpty
      val invalidinstance = instancespecified && instance.forall(x => !_valid_instance_name(x))
      if (invalidinstance) {
        Consequence.resourceInvalid(s"invalid component instance name: ${rec.getAny("instance").getOrElse("")}")
      } else {
        val coordinatefailure = coordinate.flatMap { c =>
          val artifact = coordinateArtifact(c)
          val cversion = coordinateVersion(c)
          if (artifact.isEmpty || cversion.isEmpty)
            Some(s"invalid component coordinate: $c")
          else if (!artifact.contains(name))
            Some(s"component coordinate artifact must match component name: component=$name coordinate=$c")
          else if (version.exists(_ != cversion.get))
            Some(s"component version must match coordinate version: component=$name version=${version.get} coordinate=$c")
          else
            None
        }
        coordinatefailure match {
          case Some(message) => Consequence.resourceInvalid(message)
          case None => Consequence.success(GenericSubsystemComponentBinding(
            componentName = name,
            version = version,
            coordinate = coordinate,
            extensionBindings = _record_value(rec, List("extension_bindings", "extensionBindings", "extension_binding")).getOrElse(Record.empty),
            api = _ports_from_record(rec, "api"),
            spi = _ports_from_record(rec, "spi"),
            instance = instance,
            config = _string_map_value(rec, List("config")),
            rules = _record_value(rec, List("rules")).getOrElse(Record.empty),
            purposes = _string_vector(rec, List("purposes", "purpose")),
            tags = _string_vector(rec, List("tags", "tag")),
            priority = _int(rec, "priority"),
            isDefault = _boolean(rec, "default", "isDefault"),
            capabilities = _string_vector(rec, List("capabilities", "capability")),
            componentId = Some(componentid)
          ))
        }
      }
    }

  private def _component_binding_identity_c(
    rec: Record,
    defaultname: Option[String]
  ): Consequence[(String, ComponentId, String)] = {
    val legacyfields = Vector("component", "componentName", "name").filter(rec.getAny(_).nonEmpty)
    if (legacyfields.nonEmpty)
      Consequence.resourceInvalid(
        s"component assembly binding rejects legacy identity fields: ${legacyfields.mkString(",")}"
      )
    else
      for {
        namespace <- _required_component_binding_identity_field_c(rec, "namespace")
        id <- _required_component_binding_identity_field_c(rec, "id")
        release <- _required_component_binding_identity_field_c(rec, "version")
        componentid <- ComponentId.parseC(s"${namespace}.${id}")
        _ <- _validate_component_binding_canonical_release_c(componentid, release)
      } yield (componentid.name, componentid, release)
  }

  private def _validate_component_binding_canonical_release_c(
    componentid: ComponentId,
    release: String
  ): Consequence[Unit] =
    ComponentDescriptor(
      name = Some(componentid.name),
      version = Some(release),
      componentName = Some(componentid.name),
      schemaVersion = Some(3),
      componentId = Some(componentid)
    ).requireCanonicalIdentityC match {
      case Consequence.Success(_) =>
        Consequence.success(())
      case Consequence.Failure(conclusion) =>
        Consequence.resourceInvalid(s"component binding version is invalid: ${conclusion.displayMessage}")
    }

  private def _component_binding_identity_field_c(
    rec: Record,
    key: String
  ): Consequence[Option[String]] =
    rec.getAny(key) match {
      case None => Consequence.success(None)
      case Some(value: String) if value.nonEmpty && value == value.trim => Consequence.success(Some(value))
      case Some(_: String) => Consequence.resourceInvalid(s"component binding ${key} must be a nonempty string without surrounding whitespace")
      case Some(_) => Consequence.resourceInvalid(s"component binding ${key} must be a string")
    }

  private def _required_component_binding_identity_field_c(
    rec: Record,
    key: String
  ): Consequence[String] =
    _component_binding_identity_field_c(rec, key).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.argumentMissing(s"component binding ${key}")
    }

  private def _valid_instance_name(value: String): Boolean =
    value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")

  private def _subsystem_capability_providers_c(
    rec: Record
  ): Consequence[Vector[GenericSubsystemCapabilityProviderBinding]] =
    _record_value(rec, List("subsystemCapabilities", "subsystem_capabilities", "subsystem-capabilities")) match {
      case Some(capabilities) =>
        capabilities.getAny("providers") match {
          case Some(xs: Seq[?]) =>
            _sequence(xs.toVector.zipWithIndex.map { case (value, index) =>
              _any_to_record(value) match {
                case Some(provider) => _subsystem_capability_provider_c(provider, s"subsystemCapabilities.providers[$index]")
                case None => Consequence.resourceInvalid(s"subsystemCapabilities.providers[$index] must be a provider declaration")
              }
            }).flatMap(_validate_subsystem_capability_providers)
          case Some(xs: java.util.List[?]) =>
            _sequence(xs.asScala.toVector.zipWithIndex.map { case (value, index) =>
              _any_to_record(value) match {
                case Some(provider) => _subsystem_capability_provider_c(provider, s"subsystemCapabilities.providers[$index]")
                case None => Consequence.resourceInvalid(s"subsystemCapabilities.providers[$index] must be a provider declaration")
              }
            }).flatMap(_validate_subsystem_capability_providers)
          case Some(_) => Consequence.resourceInvalid("subsystemCapabilities.providers must be a list of provider declarations")
          case None => Consequence.argumentMissing("subsystemCapabilities.providers")
        }
      case None => Consequence.success(Vector.empty)
    }

  private def _has_subsystem_capability_providers(rec: Record): Boolean =
    _record_value(rec, List("subsystemCapabilities", "subsystem_capabilities", "subsystem-capabilities")).nonEmpty

  private def _subsystem_capability_provider_c(
    rec: Record,
    location: String
  ): Consequence[GenericSubsystemCapabilityProviderBinding] =
    (_string(rec, "name"), _string(rec, "component", "componentName")) match {
      case (Some(name), Some(component)) =>
        val capabilitytexts = _string_vector(rec, List("provides", "capabilities", "capability"))
        if (capabilitytexts.isEmpty)
          Consequence.argumentMissing(s"$location.provides")
        else
          _sequence(capabilitytexts.map(SubsystemCapabilityId.parseC)).map { capabilities =>
            GenericSubsystemCapabilityProviderBinding(name, component, capabilities)
          }
      case (None, _) => Consequence.argumentMissing(s"$location.name")
      case (_, None) => Consequence.argumentMissing(s"$location.component")
    }

  private def _validate_subsystem_capability_providers(
    providers: Vector[GenericSubsystemCapabilityProviderBinding]
  ): Consequence[Vector[GenericSubsystemCapabilityProviderBinding]] = {
    val duplicateprovider = providers.groupBy(_.name).collectFirst { case (name, xs) if xs.size > 1 => name }
    val duplicatecapability = providers.flatMap { provider =>
      provider.capabilities.map(capability => (provider.name, capability.canonical))
    }.groupBy(identity).collectFirst { case ((provider, capability), xs) if xs.size > 1 => s"$provider:$capability" }
    duplicateprovider match {
      case Some(name) => Consequence.resourceInvalid(s"duplicate subsystem capability provider name: $name")
      case None => duplicatecapability match {
        case Some(identity) => Consequence.resourceInvalid(s"duplicate subsystem capability provider declaration: $identity")
        case None => Consequence.success(providers)
      }
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

  private def _component_extension_bindings(lines: Vector[String], componentname: String): Record = {
    val runtimename = runtimeComponentName(componentname)
    val prefix = s"component.${runtimename}.extension_binding.knowledge_source_adapters "
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
    _record_value(rec, keys).map(_.asMap.flatMap { case (key, value) =>
      _config_scalar_string(value).map(key -> _)
    }).getOrElse(Map.empty)

  private def _config_scalar_string(p: Any): Option[String] =
    p match {
      case null => None
      case x: String => Some(x)
      case x: Boolean => Some(x.toString)
      case x: Byte => Some(x.toString)
      case x: Short => Some(x.toString)
      case x: Int => Some(x.toString)
      case x: Long => Some(x.toString)
      case x: Float => Some(x.toString)
      case x: Double => Some(x.toString)
      case x: BigDecimal => Some(x.toString)
      case x: java.math.BigDecimal => Some(x.toString)
      case _ => None
    }

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
    val componentindex = descriptor.componentBindings.map(x => x.componentName -> x).toMap
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
        case Vector(fromcomponent, fromservice, fromoperation) =>
          val targetcomponent = values.get("target_component")
          val fromapi = values.get("api")
          val targetspi = values.get("target_spi").orElse(values.get("spi"))
          (targetcomponent, targetspi) match {
            case (Some(tocomponent), Some(spiname)) =>
              componentindex.get(tocomponent)
                .flatMap(_.spi.find(_.name == spiname))
                .flatMap { spi =>
                  for {
                    toservice <- spi.service
                    tooperation <- spi.operation
                  } yield GenericSubsystemResolvedWiringBinding(
                    fromComponent = fromcomponent,
                    fromService = fromservice,
                    fromOperation = fromoperation,
                    fromApi = fromapi,
                    toComponent = tocomponent,
                    toSpi = Some(spiname),
                    toService = toservice,
                    toOperation = tooperation,
                    glue = _glue_value(values)
                  )
                }
            case _ =>
              for {
                tocomponent <- values.get("target_component")
                toservice <- values.get("target_service")
                tooperation <- values.get("target_operation")
              } yield GenericSubsystemResolvedWiringBinding(
                fromComponent = fromcomponent,
                fromService = fromservice,
                fromOperation = fromoperation,
                fromApi = fromapi,
                toComponent = tocomponent,
                toSpi = targetspi,
                toService = toservice,
                toOperation = tooperation,
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

  def resolveAssemblySpiBindings(
    descriptor: GenericSubsystemDescriptor
  ): Consequence[Vector[SpiRuntimeBinding]] =
    descriptor.assemblyDescriptor.toVector.foldLeft(Consequence.success(Vector.empty[SpiRuntimeBinding])) {
      case (result, source) =>
        result.flatMap { xs =>
          _assembly_spi_binding_records(source).flatMap { records =>
            records.foldLeft(Consequence.success(Vector.empty[SpiRuntimeBinding])) { (bindings, record) =>
              bindings.flatMap { current =>
                _spi_binding_from_record(record).map(current :+ _)
              }
            }.map(xs ++ _)
          }
        }
    }.flatMap(_validate_spi_runtime_bindings)

  private def _assembly_spi_binding_records(
    source: GenericSubsystemAssemblyDescriptorSource
  ): Consequence[Vector[Record]] =
    source.record.getAny("spi") match {
      case None => Consequence.success(Vector.empty)
      case Some(value) =>
        _any_to_record(value) match {
          case Some(spi) =>
            _records_value_c(spi.getAny("bindings"), "assembly.spi.bindings")
          case None =>
            Consequence.resourceInvalid(s"assembly.spi must be a record: ${source.path.map(_.toString).getOrElse(source.source)}")
        }
    }

  private def _spi_binding_from_record(
    rec: Record
  ): Consequence[SpiRuntimeBinding] =
    _record_field(rec, "socket").flatMap { socket =>
      _required_string(socket, "contract").flatMap { contract =>
        _optional_record_field(rec, "provider").flatMap { provider =>
          val service = provider.flatMap(_string(_, "service"))
          val socketinstance = _string(socket, "instance")
          val socketname = _string(socket, "name")
          val providerinstance = provider.flatMap(_string(_, "instance"))
          val invalidsocketinstance = socketinstance.exists(x => !_valid_instance_name(x))
          val invalidsocketname = socketname.exists(x => !_valid_instance_name(x))
          val invalidproviderinstance = providerinstance.exists(x => !_valid_instance_name(x))
          val socketcomponent = _string(socket, "component")
          val providercomponent = provider.flatMap(_string(_, "component"))
          val cardinalityname = _string(socket, "cardinality").getOrElse("one")
          val normalizedcardinality = cardinalityname.trim.toLowerCase.replace('_', '-').replace(' ', '-')
          val required = _boolean(socket, "required").getOrElse(
            !Set("optional", "zero-or-one", "many", "set", "zero-or-more").contains(normalizedcardinality)
          )
          if (invalidsocketinstance) {
            Consequence.resourceInvalid(s"invalid assembly SPI socket instance: ${socketinstance.getOrElse("")}")
          } else if (invalidsocketname) {
            Consequence.resourceInvalid(s"invalid assembly SPI socket name: ${socketname.getOrElse("")}")
          } else if (invalidproviderinstance) {
            Consequence.resourceInvalid(s"invalid assembly SPI provider instance: ${providerinstance.getOrElse("")}")
          } else if (socketinstance.nonEmpty && socketcomponent.isEmpty) {
            Consequence.resourceInvalid("assembly SPI socket instance requires socket.component")
          } else if (providerinstance.nonEmpty && providercomponent.isEmpty) {
            Consequence.resourceInvalid("assembly SPI provider instance requires provider.component")
          } else if (service.nonEmpty) {
            Consequence.resourceInvalid("assembly.spi.bindings provider.service is not supported yet")
          } else {
            SpiCardinality.from(cardinalityname, required).flatMap { cardinality =>
              _optional_record_field(rec, "selection").map { selection =>
                SpiRuntimeBinding(
                  socket = SpiSocketSelector(
                    component = socketcomponent,
                    contract = contract,
                    instance = socketinstance,
                    name = socketname,
                    cardinality = cardinality
                  ),
                  provider = SpiProviderSelector(
                    component = providercomponent,
                    service = service,
                    instance = providerinstance
                  ),
                  selection = _spi_selection(selection)
                )
              }
            }
          }
        }
      }
    }

  private def _validate_spi_runtime_bindings(
    bindings: Vector[SpiRuntimeBinding]
  ): Consequence[Vector[SpiRuntimeBinding]] = {
    val duplicate = bindings
      .groupBy { binding =>
        val component = binding.socket.component.map(_spi_selector_key).getOrElse("*")
        val instance = binding.socket.instance.map(_spi_selector_key).getOrElse("*")
        val name = binding.socket.name.map(_spi_selector_key).getOrElse("*")
        val socket = s"${component}/${instance}/${name}/${binding.socket.contract}/${binding.socket.cardinality}"
        if (binding.socket.cardinality.isMany) {
          val providercomponent = binding.provider.component.map(_spi_selector_key).getOrElse("*")
          val providerinstance = binding.provider.instance.map(_spi_selector_key).getOrElse("*")
          s"${socket}/${providercomponent}/${providerinstance}"
        } else {
          socket
        }
      }
      .collectFirst { case (key, xs) if xs.size > 1 => key }
    duplicate match {
      case Some(key) => Consequence.resourceInvalid(s"duplicate assembly SPI socket binding: ${key}")
      case None => Consequence.success(bindings)
    }
  }

  private def _spi_selector_key(value: String): String =
    s"selector:${_comparison_key(value)}"

  private def _spi_selection(
    rec: Option[Record]
  ): SpiSelection =
    rec.map { record =>
      SpiSelection(
        provider = _string(record, "provider"),
        mode = _string(record, "mode").orElse(_string(record, "profile")),
        engine = _string(record, "engine")
      )
    }.getOrElse(SpiSelection())

  private def _records_value(
    value: Option[Any]
  ): Vector[Record] =
    value match {
      case Some(xs: Seq[?]) => xs.toVector.flatMap(_any_to_record)
      case Some(xs: java.util.List[?]) => xs.asScala.toVector.flatMap(_any_to_record)
      case Some(record: Record) => Vector(record)
      case _ => Vector.empty
    }

  private def _records_value_c(
    value: Option[Any],
    label: String
  ): Consequence[Vector[Record]] =
    value match {
      case None => Consequence.success(Vector.empty)
      case Some(record: Record) => Consequence.success(Vector(record))
      case Some(xs: Seq[?]) => _records_from_values_c(xs.toVector, label)
      case Some(xs: java.util.List[?]) => _records_from_values_c(xs.asScala.toVector, label)
      case Some(_) => Consequence.resourceInvalid(s"${label} must be a record or list of records")
    }

  private def _decode_records_c[A](
    value: Option[Any],
    label: String
  )(
    decoder: RecordDecoder[A]
  ): Consequence[Vector[A]] =
    _records_value_c(value, label).flatMap(records => _sequence(records.map(decoder.fromRecord)))

  private def _optional_record_decode_c[A](
    rec: Record,
    keys: List[String],
    label: String
  )(
    decoder: RecordDecoder[A]
  ): Consequence[Option[A]] =
    keys.iterator.map(rec.getAny).collectFirst { case Some(value) => value } match {
      case Some(value) =>
        _any_to_record(value) match {
          case Some(record) => decoder.fromRecord(record).map(Some(_))
          case None => Consequence.resourceInvalid(s"${label} must be a record")
        }
      case None =>
        Consequence.success(None)
    }

  private def _has_rule_sets(rec: Record): Boolean =
    List("ruleSets", "rule_sets", "rule-sets").exists(rec.getAny(_).nonEmpty)

  private def _rule_sets_c(rec: Record): Consequence[Vector[RuleSet]] =
    _records_value_c(
      List("ruleSets", "rule_sets", "rule-sets").iterator.map(rec.getAny).collectFirst {
        case value @ Some(_) => value
      }.flatten,
      "ruleSets"
    ).flatMap { records =>
      _sequence(records.map(RuleSetDescriptor.decodeC)).flatMap(_validate_rule_sets_c)
    }

  private def _validate_rule_sets_c(values: Vector[RuleSet]): Consequence[Vector[RuleSet]] = {
    val identities = values.map(_.identity.print)
    if (identities.distinct.size != identities.size)
      Consequence.argumentInvalid("ruleSets must have unique RuleSet id/version identities")
    else
      Consequence.success(values.sortBy(_.identity.print))
  }

  private def _records_from_values_c(
    values: Vector[Any],
    label: String
  ): Consequence[Vector[Record]] =
    values.zipWithIndex.foldLeft(Consequence.success(Vector.empty[Record])) {
      case (result, (value, index)) =>
        result.flatMap { xs =>
          _any_to_record(value) match {
            case Some(record) => Consequence.success(xs :+ record)
            case None => Consequence.resourceInvalid(s"${label}[${index}] must be a record")
          }
        }
    }

  private def _record_field(
    rec: Record,
    key: String
  ): Consequence[Record] =
    _optional_record_field(rec, key).flatMap {
      case Some(record) => Consequence.success(record)
      case None => Consequence.resourceInvalid(s"assembly.spi.bindings.${key} is required")
    }

  private def _optional_record_field(
    rec: Record,
    key: String
  ): Consequence[Option[Record]] =
    rec.getAny(key) match {
      case None => Consequence.success(None)
      case Some(value) =>
        _any_to_record(value) match {
          case Some(record) => Consequence.success(Some(record))
          case None => Consequence.resourceInvalid(s"assembly.spi.bindings.${key} must be a record")
        }
    }

  private def _required_string(
    rec: Record,
    key: String
  ): Consequence[String] =
    _string(rec, key) match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.resourceInvalid(s"assembly.spi.bindings.socket.${key} is required")
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
    def fromRecord(rec: Record): Consequence[GenericSubsystemComponentBinding] =
      _binding_from_record_c(rec, None)

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
      for {
        providers <- _decode_records_c(
          rec.getAny("providers"),
          "authentication.providers"
        )(summon[RecordDecoder[GenericSubsystemAuthenticationProviderBinding]])
        localsubject <- _optional_record_decode_c(
          rec,
          List("local_subject", "localSubject"),
          "authentication.local_subject"
        )(summon[RecordDecoder[GenericSubsystemLocalSubjectBinding]])
      } yield
        GenericSubsystemAuthenticationBinding(
          convention = _string(rec, "convention"),
          fallbackPrivilege = _string(rec, "fallback_privilege", "fallbackPrivilege"),
          localSubject = localsubject,
          providers = providers
        )
    }

  given RecordDecoder[GenericSubsystemLocalSubjectBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemLocalSubjectBinding] =
      _string(rec, "id", "subject_id", "subjectId") match {
        case Some(id) =>
          Consequence.success(
            GenericSubsystemLocalSubjectBinding(
              id = id,
              roles = _string_vector(rec, List("roles", "role")),
              capabilities = _string_vector(rec, List("capabilities", "capability")),
              attributes = _string_map_value(rec, List("attributes")),
              securityLevel = _string(rec, "security_level", "securityLevel")
            )
          )
        case None =>
          Consequence.argumentMissing("local subject id")
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
      _decode_records_c(
        rec.getAny("providers"),
        "message-delivery.providers"
      )(summon[RecordDecoder[GenericSubsystemMessageDeliveryProviderBinding]])
        .map(GenericSubsystemMessageDeliveryBinding.apply)
    }

  given RecordDecoder[GenericSubsystemUserNotificationProviderBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemUserNotificationProviderBinding] = {
      val name = _string(rec, "name")
      val component = _string(rec, "component")
      (name, component) match {
        case (Some(n), Some(c)) =>
          Consequence.success(
            GenericSubsystemUserNotificationProviderBinding(
              name = n,
              component = c,
              channel = _string(rec, "channel"),
              enabled = _boolean(rec, "enabled"),
              priority = _int(rec, "priority"),
              isDefault = _boolean(rec, "default", "isDefault")
            )
          )
        case _ =>
          Consequence.argumentMissing("user-notification provider name/component")
      }
    }

  given RecordDecoder[GenericSubsystemUserNotificationEventForwardingBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemUserNotificationEventForwardingBinding] = {
      val event = _string(rec, "event").orElse(_string(rec, "name"))
      event match {
        case Some(e) =>
          Consequence.success(
            GenericSubsystemUserNotificationEventForwardingBinding(
              event = e,
              provider = _string(rec, "provider"),
              channel = _string(rec, "channel"),
              enabled = _boolean(rec, "enabled"),
              appVisibleOnly = _boolean(rec, "appVisibleOnly", "app_visible_only"),
              asyncOnly = _boolean(rec, "asyncOnly", "async_only"),
              notificationType = _string(rec, "notificationType", "notification_type"),
              priority = _string(rec, "priority"),
              dedupeKey = _string(rec, "dedupeKey", "dedupe_key")
            )
          )
        case None =>
          Consequence.argumentMissing("user-notification event-forwarding event")
      }
    }

  given RecordDecoder[GenericSubsystemUserNotificationBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemUserNotificationBinding] = {
      val providers = _decode_records_c(
        rec.getAny("providers"),
        "user-notification.providers"
      )(summon[RecordDecoder[GenericSubsystemUserNotificationProviderBinding]])
      val eventforwarding = rec.getAny("eventForwarding").orElse(rec.getAny("event_forwarding")) match {
        case Some(xs: Seq[?]) =>
          _sequence(xs.toVector.map { x =>
            _any_to_record(x) match {
              case Some(r) =>
                summon[RecordDecoder[GenericSubsystemUserNotificationEventForwardingBinding]].fromRecord(r)
              case None =>
                Consequence.argumentInvalid(s"user-notification event-forwarding entry must be a mapping: ${x}")
            }
          })
        case Some(x) =>
          Consequence.argumentInvalid(s"user-notification eventForwarding must be a list: ${x}")
        case None =>
          Consequence.success(Vector.empty)
      }
      for {
        providers0 <- providers
        eventforwarding0 <- eventforwarding
      } yield {
        GenericSubsystemUserNotificationBinding(
          providers = providers0,
          eventForwarding = eventforwarding0
        )
      }
    }

  given RecordDecoder[GenericSubsystemRuntimeBinding] with
    def fromRecord(rec: Record): Consequence[GenericSubsystemRuntimeBinding] =
      _optional_record_decode_c(
        rec,
        List("user_notification", "userNotification"),
        "runtime.user_notification"
      )(summon[RecordDecoder[GenericSubsystemUserNotificationBinding]]).map { userNotification =>
        GenericSubsystemRuntimeBinding(
          userNotification = userNotification
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
      for {
        auth <- _optional_record_decode_c(
          rec,
          List("authentication"),
          "security.authentication"
        )(summon[RecordDecoder[GenericSubsystemAuthenticationBinding]])
        messagedelivery <- _optional_record_decode_c(
          rec,
          List("message_delivery", "messageDelivery", "notification"),
          "security.message_delivery"
        )(summon[RecordDecoder[GenericSubsystemMessageDeliveryBinding]])
        authorization <- _optional_record_decode_c(
          rec,
          List("authorization"),
          "security.authorization"
        )(summon[RecordDecoder[GenericSubsystemAuthorizationBinding]])
      } yield GenericSubsystemSecurityBinding(auth, messagedelivery, authorization)
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
      val subsystemname = _string(rec, "subsystem", "subsystemName", "name")
      subsystemname match {
        case Some(name) =>
          _bindings_from_record_c(rec).flatMap { bindings =>
            if (bindings.isEmpty)
              Consequence.argumentMissing("component bindings")
            else for {
              runtime <- _optional_runtime_c(rec)
              security <- _security_value(rec)
              builtin <- _optional_builtin_c(rec)
              rulesets <- _rule_sets_c(rec)
              capabilityproviders <- _subsystem_capability_providers_c(rec)
            } yield {
              Shape(
                subsystemName = name,
                version = _string(rec, "version"),
                componentBindings = bindings,
                extensions = _string_map_value(rec, List("extension", "extensions")),
                config = _string_map_value(rec, List("config")),
                wiring = _wiring_value(rec),
                runtime = runtime,
                security = security,
                builtin = builtin,
                operationAuthorization = _operation_authorization_value(rec),
                ruleSets = rulesets,
                subsystemCapabilityProviders = capabilityproviders
              )
            }
          }
        case None =>
          Consequence.argumentMissing("subsystem/subsystemName/name")
      }
    }

  private def _security_value(
    rec: Record
  ): Consequence[Option[GenericSubsystemSecurityBinding]] =
    _optional_record_decode_c(
      rec,
      List("security"),
      "security"
    )(summon[RecordDecoder[GenericSubsystemSecurityBinding]])
}
