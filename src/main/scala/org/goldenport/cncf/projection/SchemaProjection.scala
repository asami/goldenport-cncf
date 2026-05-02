package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component

/*
 * @since   Mar.  5, 2026
 * @version May.  2, 2026
 * @author  ASAMI, Tomoharu
 */
object SchemaProjection {
  import MetaProjectionSupport._

  def project(base: Component, selector: Option[String] = None): Record =
    resolve(base, selector) match {
      case Target.Subsystem(components, name) =>
        Record.data(
          "type" -> "schema",
          "name" -> name,
          "authorizationPolicies" -> AuthorizationPolicyProjection.project(components, name),
          "components" -> components.map { component =>
            project(base, Some(component.name))
          }
        )
      case Target.ComponentTarget(component) =>
        val services = component.protocol.services.services.sortBy(_.name)
        val artifact = artifact_record(component)
        val aggregates = aggregateMetas(component).map { x =>
          Record.data(
            "name" -> x.name,
            "entityName" -> x.entityName
          )
        }
        val views = viewMetas(component).map { x =>
          Record.data(
            "name" -> x.name,
            "entityName" -> x.entityName,
            "viewNames" -> x.viewNames,
            "queries" -> x.queries.map(q => Record.data("name" -> q.name, "expression" -> q.expression.getOrElse(""))),
            "sourceEvents" -> x.sourceEvents,
            "rebuildable" -> x.rebuildable.getOrElse(false)
          )
        }
        val operationdefs = operationMetas(component).map { x =>
          Record.data(
            "name" -> x.name,
            "kind" -> x.kind,
            "inputType" -> x.inputType,
            "outputType" -> x.outputType,
            "inputValueKind" -> x.inputValueKind,
            "visibility" -> x.visibility,
            "parameters" -> x.parameters
          )
        }
        val entitycollections = entityCollectionRecords(component)
        Record.data(
          "type" -> "schema",
          "targetType" -> "component",
          "name" -> component.name,
          "origin" -> user_origin_label(component.origin.label),
          "artifact" -> artifact,
          "services" -> services.map { service =>
            project(base, Some(s"${component.name}.${service.name}"))
          },
          "entityCollections" -> entitycollections,
          "aggregateCollections" -> aggregates,
          "viewCollections" -> views,
          "authorizationPolicies" -> AuthorizationPolicyProjection.project(Vector(component), component.subsystem.map(_.name).getOrElse(component.name)),
          "operationDefinitions" -> operationdefs
        )
      case Target.ServiceTarget(component, service) =>
        val operations = service.operations.operations.toVector.sortBy(_.name)
        Record.data(
          "type" -> "schema",
          "targetType" -> "service",
          "name" -> s"${component.name}.${service.name}",
          "operations" -> operations.map { op =>
            project(base, Some(s"${component.name}.${service.name}.${op.name}"))
          }
        )
      case Target.OperationTarget(component, service, operation) =>
        Record.data(
          "type" -> "schema",
          "targetType" -> "operation",
          "name" -> s"${component.name}.${service.name}.${operation.name}",
          "request" -> Record.data(
            "parameters" -> operation.specification.request.parameters.toVector.map(parameter_record)
          ),
          "response" -> Record.data(
            "result" -> render_operation_returns(operation)
          )
        )
      case Target.NotFound(target) =>
        Record.data(
          "type" -> "error",
          "name" -> target.getOrElse("unknown"),
          "summary" -> "target not found"
        )
    }
}
