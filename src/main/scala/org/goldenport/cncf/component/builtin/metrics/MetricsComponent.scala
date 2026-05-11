package org.goldenport.cncf.component.builtin.metrics

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.DataType

/*
 * @since   Mar. 29, 2026
 *  version Apr. 10, 2026
 * @version May. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class MetricsComponent() extends Component {
}

object MetricsComponent {
  trait MetricsService {
    def loadEntityAccessMetrics(): Consequence[Record]
    def loadRuntimeMetrics(): Consequence[Record]
    def loadMetricsCatalog(): Consequence[Record]
  }

  val name: String = "metrics"
  val componentId: ComponentId = ComponentId(name)

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component =
      MetricsComponent()

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = spec.RequestDefinition()
      val response = spec.ResponseDefinition(result = List(DataType.Named("Record")))
      val loadEntityAccessMetrics = new LoadEntityAccessMetricsOperationDefinition(request, response)
      val loadRuntimeMetrics = new LoadRuntimeMetricsOperationDefinition(request, response)
      val loadMetricsCatalog = new LoadMetricsCatalogOperationDefinition(request, response)
      val service = spec.ServiceDefinition(
        name = "metrics",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            loadEntityAccessMetrics,
            loadRuntimeMetrics,
            loadMetricsCatalog
          )
        )
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(services = Vector(service)),
        handler = ProtocolHandler.default
      )
      comp.withPort(Component.Port.of(new DefaultMetricsService(params.subsystem.entityAccessMetrics)))
      val instanceId = ComponentInstanceId.default(componentId)
      Component.Core.create(name, componentId, instanceId, protocol)
    }
  }

  private final class DefaultMetricsService(
    registry: EntityAccessMetricsRegistry
  ) extends MetricsService {
    def loadEntityAccessMetrics(): Consequence[Record] =
      Consequence.success(registry.toRecord)

    def loadRuntimeMetrics(): Consequence[Record] =
      Consequence.success(RuntimeDashboardMetrics.runtimeMetricsSnapshot(registry).toRecord)

    def loadMetricsCatalog(): Consequence[Record] =
      Consequence.success(RuntimeDashboardMetrics.metricsCatalogRecord)
  }

  private final class LoadEntityAccessMetricsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "load_entity_access_metrics",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(LoadEntityAccessMetricsAction(req))
  }

  private final class LoadRuntimeMetricsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "load_runtime_metrics",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(LoadRuntimeMetricsAction(req))
  }

  private final class LoadMetricsCatalogOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "load_metrics_catalog",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(LoadMetricsCatalogAction(req))
  }

  private final case class LoadEntityAccessMetricsAction(
    request: Request
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadEntityAccessMetricsActionCall(core)
  }

  private final case class LoadRuntimeMetricsAction(
    request: Request
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadRuntimeMetricsActionCall(core)
  }

  private final case class LoadMetricsCatalogAction(
    request: Request
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadMetricsCatalogActionCall(core)
  }

  private final case class LoadEntityAccessMetricsActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component.flatMap(_.port.get[MetricsService]) match {
        case Some(service) =>
          service.loadEntityAccessMetrics().map(OperationResponse.RecordResponse.apply)
        case None =>
          Consequence.serviceUnavailable("metrics service is not available")
      }
  }

  private final case class LoadRuntimeMetricsActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component.flatMap(_.port.get[MetricsService]) match {
        case Some(service) =>
          service.loadRuntimeMetrics().map(OperationResponse.RecordResponse.apply)
        case None =>
          Consequence.serviceUnavailable("metrics service is not available")
      }
  }

  private final case class LoadMetricsCatalogActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component.flatMap(_.port.get[MetricsService]) match {
        case Some(service) =>
          service.loadMetricsCatalog().map(OperationResponse.RecordResponse.apply)
        case None =>
          Consequence.serviceUnavailable("metrics service is not available")
      }
  }
}
