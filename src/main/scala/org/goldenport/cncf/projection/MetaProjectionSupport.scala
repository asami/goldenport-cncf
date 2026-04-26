package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.schema.DataType
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, ServiceDefinition}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentOriginLabel
import org.goldenport.cncf.entity.SimpleEntityStorageShapePolicy
import org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Mar.  5, 2026
 * @version Apr. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[projection] object MetaProjectionSupport {
  final case class AggregateMeta(
    name: String,
    entityName: String
  )
  final case class ViewQueryMeta(
    name: String,
    expression: Option[String]
  )
  final case class ViewMeta(
    name: String,
    entityName: String,
    viewNames: Vector[String],
    queries: Vector[ViewQueryMeta],
    sourceEvents: Vector[String],
    rebuildable: Option[Boolean]
  )
  final case class OperationMeta(
    name: String,
    kind: String,
    inputType: String,
    outputType: String,
    inputValueKind: String,
    parameters: Vector[Record]
  )

  sealed trait Target
  object Target {
    final case class Subsystem(components: Vector[Component], name: String) extends Target
    final case class ComponentTarget(component: Component) extends Target
    final case class ServiceTarget(component: Component, service: ServiceDefinition) extends Target
    final case class OperationTarget(
      component: Component,
      service: ServiceDefinition,
      operation: OperationDefinition
    ) extends Target
    final case class NotFound(selector: Option[String]) extends Target
  }

  def components(base: Component): Vector[Component] =
    base.subsystem.map(_.components.sortBy(_.name)).filter(_.nonEmpty).getOrElse(Vector(base))

  def resolve(base: Component, selector: Option[String]): Target = {
    val comps = components(base)
    selector.map(_.trim).filter(_.nonEmpty) match {
      case None =>
        Target.Subsystem(comps, base.subsystem.map(_.name).getOrElse("subsystem"))
      case Some(s) =>
        val segments = s.split("\\.").toVector.filter(_.nonEmpty)
        segments match {
          case Vector(componentName) =>
            _find_component(comps, componentName).map(Target.ComponentTarget.apply).getOrElse(Target.NotFound(Some(s)))
          case Vector(componentName, serviceName) =>
            (for {
              comp <- _find_component(comps, componentName)
              service <- _find_service(comp, serviceName)
            } yield Target.ServiceTarget(comp, service)).getOrElse(Target.NotFound(Some(s)))
          case Vector(componentName, serviceName, operationName) =>
            (for {
              comp <- _find_component(comps, componentName)
              service <- _find_service(comp, serviceName)
              op <- _find_operation(service, operationName)
            } yield Target.OperationTarget(comp, service, op)).getOrElse(Target.NotFound(Some(s)))
          case _ =>
            Target.NotFound(Some(s))
        }
    }
  }

  def component_record(comp: Component): Record =
    Record.data( // Includes runtime-origin and archive metadata for CAR/SAR introspection.
      "type" -> "component",
      "name" -> comp.name,
      "runtimeName" -> component_runtime_name(comp),
      "origin" -> user_origin_label(comp.origin.label),
      "artifact" -> artifact_record(comp)
    )

  def user_origin_label(origin: String): String =
    ComponentOriginLabel.userLabel(origin)

  def artifact_record(comp: Component): Record =
    comp.artifactMetadata.map { m =>
      Record.data(
        "sourceType" -> m.sourceType,
        "name" -> m.name,
        "version" -> m.version,
        "component" -> m.component.getOrElse(""),
        "subsystem" -> m.subsystem.getOrElse(""),
        "effectiveExtensions" -> m.effectiveExtensions.toVector.sortBy(_._1).map { case (k, v) => Record.data("key" -> k, "value" -> v) },
        "effectiveConfig" -> m.effectiveConfig.toVector.sortBy(_._1).map { case (k, v) => Record.data("key" -> k, "value" -> v) }
      )
    }.getOrElse(Record.empty)

  def service_record(service: ServiceDefinition): Record =
    Record.data(
      "type" -> "service",
      "name" -> service.name,
      "runtimeName" -> service_runtime_name(service),
      "useCases" -> _service_use_case_records(service)
    )

  private def _service_use_case_records(service: ServiceDefinition): Vector[Record] =
    try {
      val method = service.getClass.getMethod("useCaseRecords")
      method.invoke(service) match {
        case xs: Vector[?] => xs.collect { case r: Record => r }
        case xs: Seq[?] => xs.collect { case r: Record => r }.toVector
        case _ => Vector.empty
      }
    } catch {
      case _: NoSuchMethodException => Vector.empty
      case _: Throwable => Vector.empty
    }

  def operation_record(service: ServiceDefinition, operation: OperationDefinition): Record =
    Record.data(
      "type" -> "operation",
      "name" -> s"${service.name}.${operation.name}",
      "runtimeName" -> s"${service_runtime_name(service)}.${operation_runtime_name(operation)}"
    )

  def parameter_record(param: ParameterDefinition): Record = {
    val datatype = Option(param.domain.datatype).map(_.toString).getOrElse("unknown")
    val multiplicity = Option(param.domain.multiplicity).map(_.toString).getOrElse("unknown")
    Record.data(
      "name" -> param.name,
      "kind" -> param.kind.toString,
      "type" -> datatype,
      "multiplicity" -> multiplicity
    )
  }

  def operation_details(operation: OperationDefinition): Record = {
    val args = operation.specification.request.parameters.toVector.map(parameter_record)
    val returns = render_operation_returns(operation)
    Record.data(
      "arguments" -> args,
      "returns" -> returns
    )
  }

  def render_operation_returns(operation: OperationDefinition): String =
    Option(operation.specification.response.result)
      .map(_render_data_types)
      .getOrElse("unknown")

  def aggregateMetas(component: Component): Vector[AggregateMeta] =
    component.aggregateDefinitions
      .sortBy(_.name)
      .map(x => AggregateMeta(x.name, x.entityName))

  def viewMetas(component: Component): Vector[ViewMeta] =
    component.viewDefinitions
      .sortBy(_.name)
      .map { x =>
        ViewMeta(
          x.name,
          x.entityName,
          x.viewNames.distinct.sorted,
          x.queries.sortBy(_.name).map(q => ViewQueryMeta(q.name, q.expression)),
          x.sourceEvents.distinct.sorted,
          x.rebuildable
        )
      }

  def operationMetas(component: Component): Vector[OperationMeta] =
    component.operationDefinitions
      .groupBy(_.name)
      .toVector
      .sortBy(_._1)
      .map(_._2.head)
      .map { x =>
        OperationMeta(
          name = x.name,
          kind = x.kind,
          inputType = x.inputType,
          outputType = x.outputType,
          inputValueKind = x.inputValueKind,
          parameters = x.parameters.map { p =>
            Record.data(
              "name" -> p.name,
              "datatype" -> p.datatype,
              "multiplicity" -> p.multiplicity
            )
          }
        )
      }

  def entityCollectionRecords(component: Component): Vector[Record] =
    component.componentDescriptors
      .flatMap(_.entityRuntimeDescriptors)
      .groupBy(_.entityName)
      .toVector
      .map(_._2.head)
      .sortBy(_.entityName)
      .map(entity_collection_record(component, _))

  def entity_collection_record(
    component: Component,
    descriptor: EntityRuntimeDescriptor
  ): Record =
    Record.data(
      "entityName" -> descriptor.entityName,
      "collectionId" -> _collection_id_string(descriptor),
      "memoryPolicy" -> descriptor.memoryPolicy.toString,
      "workingSetPolicy" -> descriptor.workingSetPolicy.map(_.label).getOrElse(""),
      "workingSetPolicySource" -> descriptor.workingSetPolicySource.map(_.toString.toLowerCase).getOrElse(""),
      "usageKind" -> descriptor.usageKind.toString,
      "operationKind" -> descriptor.operationKind.toString,
      "applicationDomain" -> descriptor.applicationDomain.toString,
      "storageShape" -> storage_shape_record(component, descriptor)
    )

  def storage_shape_record(
    component: Component,
    descriptor: EntityRuntimeDescriptor
  ): Record =
    Record.data(
      "policy" -> SimpleEntityStorageShapePolicy.PolicyName,
      "fields" -> storage_shape_field_records(component, descriptor)
    )

  def storage_shape_field_records(
    component: Component,
    descriptor: EntityRuntimeDescriptor
  ): Vector[Record] = {
    val platform = _management_storage_fields ++ _security_storage_fields ++ Vector(_permission_storage_field)
    val platformKeys = platform.map(_.getString("logicalName").getOrElse("")).map(_normalize).toSet
    val domain =
      descriptor.schema.toVector.flatMap(_.columns).flatMap { column =>
        val logical = column.name.value
        if (platformKeys.contains(_normalize(logical)) || _non_storage_schema_keys.contains(_normalize(logical)))
          None
        else
          Some(_field_record(
            logicalName = logical,
            storageName = SimpleEntityStorageShapePolicy.targetName(logical),
            classification = "scalar_attribute",
            storageKind = "column",
            dataType = Some(column.domain.datatype.name)
          ))
      }
    val delegated = _delegated_collection_fields(component, descriptor)
    (platform ++ domain ++ delegated).sortBy { r =>
      (
        _storage_sort_order(r.getString("classification").getOrElse("")),
        r.getString("logicalName").getOrElse("")
      )
    }
  }

  def component_runtime_name(component: Component): String =
    NamingConventions.toNormalizedSegment(component.name)

  def service_runtime_name(service: ServiceDefinition): String =
    NamingConventions.toNormalizedSegment(service.name)

  def operation_runtime_name(operation: OperationDefinition): String =
    NamingConventions.toNormalizedSegment(operation.name)

  def selector_runtime_name(
    component: Component,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): String =
    NamingConventions.toNormalizedSelector(component.name, service.name, operation.name)

  private def _find_component(comps: Vector[Component], name: String): Option[Component] =
    comps.find { x =>
      NamingConventions.equivalentByNormalized(x.name, name) ||
        x.artifactMetadata.toVector.exists { metadata =>
          metadata.component.exists(NamingConventions.equivalentByNormalized(_, name)) ||
            NamingConventions.equivalentByNormalized(metadata.name, name)
        }
    }

  private def _find_service(component: Component, serviceName: String): Option[ServiceDefinition] =
    component.protocol.services.services.find(x => NamingConventions.equivalentByNormalized(x.name, serviceName))

  private def _find_operation(service: ServiceDefinition, operationName: String): Option[OperationDefinition] =
    service.operations.operations.find(x => NamingConventions.equivalentByNormalized(x.name, operationName))

  private def _render_data_types(xs: Seq[DataType]): String =
    xs.toVector match {
      case Vector() => "unknown"
      case Vector(x) => x.name
      case vs => vs.map(_.name).mkString("[", ", ", "]")
    }

  private def _management_storage_fields: Vector[Record] =
    SimpleEntityStorageShapePolicy.ManagementLogicalFields.map { logical =>
      _field_record(
        logicalName = logical,
        storageName = SimpleEntityStorageShapePolicy.targetName(logical),
        classification = "management",
        storageKind = "expanded_column"
      )
    }

  private def _security_storage_fields: Vector[Record] =
    SimpleEntityStorageShapePolicy.SecurityIdentityLogicalFields.map { logical =>
      _field_record(
        logicalName = logical,
        storageName = SimpleEntityStorageShapePolicy.targetName(logical),
        classification = "security_identity",
        storageKind = "expanded_column"
      )
    }

  private def _permission_storage_field: Record =
    _field_record(
      logicalName = "permission",
      storageName = SimpleEntityStorageShapePolicy.PermissionField,
      classification = "permission",
      storageKind = "compact_json_text"
    )

  private def _delegated_collection_fields(
    component: Component,
    descriptor: EntityRuntimeDescriptor
  ): Vector[Record] = {
    val aggregatemembers = component.aggregateDefinitions
      .filter(x => NamingConventions.equivalentByNormalized(x.entityName, descriptor.entityName))
      .flatMap(_.members)
      .map(_.name)
    aggregatemembers.distinct.sorted.map(name =>
      _field_record(
        logicalName = name,
        storageName = name,
        classification = "delegated_collection",
        storageKind = "delegated_collection",
        source = Some("aggregate")
      )
    ).toVector
  }

  private def _field_record(
    logicalName: String,
    storageName: String,
    classification: String,
    storageKind: String,
    dataType: Option[String] = None,
    source: Option[String] = None
  ): Record =
    Record.data(
      "logicalName" -> logicalName,
      "storageName" -> storageName,
      "classification" -> classification,
      "storageKind" -> storageKind,
      "dataType" -> dataType.getOrElse(""),
      "source" -> source.getOrElse("")
    )

  private def _storage_sort_order(classification: String): Int =
    classification match {
      case "management" => 10
      case "security_identity" => 20
      case "permission" => 30
      case "scalar_attribute" => 40
      case "delegated_collection" => 50
      case _ => 90
    }

  private def _collection_id_string(descriptor: EntityRuntimeDescriptor): String =
    Vector(
      descriptor.collectionId.major,
      descriptor.collectionId.minor,
      descriptor.collectionId.name
    ).mkString("-")

  private def _normalize(p: String): String =
    p.replace("-", "").replace("_", "").trim.toLowerCase(java.util.Locale.ROOT)

  private val _non_storage_schema_keys: Set[String] =
    Set(
      "lifecycleAttributes",
      "publicationAttributes",
      "securityAttributes",
      "resourceAttributes",
      "auditAttributes",
      "nameAttributes",
      "descriptiveAttributes",
      "contextualAttribute",
      "lifecycle_attributes",
      "publication_attributes",
      "security_attributes",
      "resource_attributes",
      "audit_attributes",
      "name_attributes",
      "descriptive_attributes",
      "contextual_attribute"
    ).map(_normalize)
}
