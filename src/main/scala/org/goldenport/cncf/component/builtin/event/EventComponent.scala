package org.goldenport.cncf.component.builtin.event

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.event.{EventId, EventRecord, EventStore}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.DataType
import org.goldenport.value.BaseContent

/*
 * @since   Mar. 28, 2026
 *  version Apr. 11, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventComponent() extends Component {
}

object EventComponent {
  trait EventService {
    def loadEvent(eventId: EventId): Consequence[EventRecord]
    def searchEvent(): Consequence[Vector[EventRecord]]
  }

  trait EventAdminService {
    def loadEventStoreStatus(): Consequence[Record]
    def searchEventLog(): Consequence[Vector[EventRecord]]
    def loadJobEvents(jobId: org.goldenport.cncf.job.JobId): Consequence[Vector[EventRecord]]
  }

  val name: String = "event"
  val componentId: ComponentId = ComponentId(name)

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component =
      EventComponent()

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = spec.RequestDefinition()
      val idRequest = _event_id_request
      val jobIdRequest = _job_id_request
      val loadEvent = new LoadEventOperationDefinition(request = idRequest, response = spec.ResponseDefinition(result = List(DataType.Named("EventRecord"))))
      val searchEvent = new SearchEventOperationDefinition(request = request, response = spec.ResponseDefinition(result = List(DataType.Named("EventRecordList"))))
      val loadEventStoreStatus = new LoadEventStoreStatusOperationDefinition(request = request, response = spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val searchEventLog = new SearchEventLogOperationDefinition(request = request, response = spec.ResponseDefinition(result = List(DataType.Named("EventRecordList"))))
      val loadJobEvents = new LoadJobEventsOperationDefinition(request = jobIdRequest, response = spec.ResponseDefinition(result = List(DataType.Named("EventRecordList"))))
      val eventService = spec.ServiceDefinition(
        name = "event",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(loadEvent, searchEvent)
        )
      )
      val eventAdminService = spec.ServiceDefinition(
        name = "event_admin",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(loadEventStoreStatus, searchEventLog, loadJobEvents)
        )
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          services = Vector(eventService, eventAdminService)
        ),
        handler = ProtocolHandler.default
      )
      comp.withPort(
        Component.Port.of(new DefaultEventService(comp), new DefaultEventAdminService(comp))
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(
        name,
        componentId,
        instanceid,
        protocol
      )
    }

    private def _event_id_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition(content = BaseContent.simple("id"), kind = spec.ParameterDefinition.Kind.Argument))
      )

    private def _job_id_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition(content = BaseContent.simple("id"), kind = spec.ParameterDefinition.Kind.Argument))
      )
  }

  private final class DefaultEventService(component: Component) extends EventService {
    def loadEvent(eventId: EventId): Consequence[EventRecord] =
      component.eventStore match {
        case Some(store) =>
          store.load(eventId).flatMap {
            case Some(event) => Consequence.success(event)
            case None => Consequence.operationNotFound(s"event:${eventId.value}")
          }
        case None =>
          Consequence.serviceUnavailable("event store is not available")
      }

    def searchEvent(): Consequence[Vector[EventRecord]] =
      component.eventStore match {
        case Some(store) => store.query(EventStore.Query())
        case None => Consequence.serviceUnavailable("event store is not available")
      }
  }

  private final class DefaultEventAdminService(component: Component) extends EventAdminService {
    def loadEventStoreStatus(): Consequence[Record] =
      component.eventStore match {
        case Some(store) =>
          store.query(EventStore.Query()).map { records =>
            Record.data(
              "count" -> records.size,
              "last-sequence" -> records.lastOption.map(_.sequence).getOrElse(0L),
              "names" -> records.map(_.name)
            )
          }
        case None =>
          Consequence.serviceUnavailable("event store is not available")
      }

    def searchEventLog(): Consequence[Vector[EventRecord]] =
      component.eventStore match {
        case Some(store) => store.query(EventStore.Query())
        case None => Consequence.serviceUnavailable("event store is not available")
      }

    def loadJobEvents(jobId: org.goldenport.cncf.job.JobId): Consequence[Vector[EventRecord]] =
      component.eventStore match {
        case Some(store) =>
          store.query(EventStore.Query()).map { records =>
            records.filter { record =>
              record.payload.get("job-id").contains(jobId.value) ||
              record.attributes.get("job-id").contains(jobId.value)
            }
          }
        case None =>
          Consequence.serviceUnavailable("event store is not available")
      }
  }

  private final class LoadEventOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "load_event",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _event_id(req).map(LoadEventAction(req, _))
  }

  private final class SearchEventOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "search_event",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(SearchEventAction(req))
  }

  private final class LoadEventStoreStatusOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "load_event_store_status",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(LoadEventStoreStatusAction(req))
  }

  private final class SearchEventLogOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "search_event_log",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(SearchEventLogAction(req))
  }

  private final class LoadJobEventsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "load_job_events",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map(LoadJobEventsAction(req, _))
  }

  private final case class LoadEventAction(
    request: Request,
    eventId: EventId
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadEventCall(core, eventId)
  }

  private final case class SearchEventAction(
    request: Request
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      SearchEventCall(core)
  }

  private final case class LoadEventStoreStatusAction(
    request: Request
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadEventStoreStatusCall(core)
  }

  private final case class SearchEventLogAction(
    request: Request
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      SearchEventLogCall(core)
  }

  private final case class LoadJobEventsAction(
    request: Request,
    jobId: org.goldenport.cncf.job.JobId
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadJobEventsCall(core, jobId)
  }

  private final case class LoadEventCall(
    core: ActionCall.Core,
    eventId: EventId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _event_store(core).flatMap { store =>
        core.component.flatMap(_.port.get[EventService]) match {
          case Some(service) =>
            service.loadEvent(eventId).map(event => OperationResponse.RecordResponse(_event_record(event)))
          case None =>
            Consequence.serviceUnavailable("event service is not available")
        }
      }
  }

  private final case class SearchEventCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _event_store(core).flatMap { store =>
        core.component.flatMap(_.port.get[EventService]) match {
          case Some(service) =>
            service.searchEvent().map { records =>
              OperationResponse.RecordResponse(
                Record.data("events" -> records.map(_event_record))
              )
            }
          case None =>
            Consequence.serviceUnavailable("event service is not available")
        }
      }
  }

  private final case class LoadEventStoreStatusCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _event_store(core).flatMap { store =>
        core.component.flatMap(_.port.get[EventAdminService]) match {
          case Some(service) =>
            service.loadEventStoreStatus().map(record => OperationResponse.RecordResponse(record))
          case None =>
            Consequence.serviceUnavailable("event admin service is not available")
        }
      }
  }

  private final case class SearchEventLogCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _event_store(core).flatMap { store =>
        core.component.flatMap(_.port.get[EventAdminService]) match {
          case Some(service) =>
            service.searchEventLog().map { records =>
              OperationResponse.RecordResponse(
                Record.data("event-log" -> records.map(_event_record))
              )
            }
          case None =>
            Consequence.serviceUnavailable("event admin service is not available")
        }
      }
  }

  private final case class LoadJobEventsCall(
    core: ActionCall.Core,
    jobId: org.goldenport.cncf.job.JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _event_store(core).flatMap { store =>
        core.component.flatMap(_.port.get[EventAdminService]) match {
          case Some(service) =>
            service.loadJobEvents(jobId).map { records =>
              OperationResponse.RecordResponse(
                Record.data(
                  "job-id" -> jobId.value,
                  "events" -> records.map(_event_record)
                )
              )
            }
          case None =>
            Consequence.serviceUnavailable("event admin service is not available")
        }
      }
  }

  private def _event_store(core: ActionCall.Core): Consequence[EventStore] =
    core.component.flatMap(_.eventStore) match {
      case Some(store) => Consequence.success(store)
      case None => Consequence.serviceUnavailable("event store is not available")
    }

  private def _event_id(req: Request): Consequence[EventId] =
    req.arguments.find(_.name == "id") match {
      case Some(arg) => _parse_event_id(arg.value.toString)
      case None =>
        req.properties.find(_.name == "id") match {
          case Some(prop) => _parse_event_id(prop.value.toString)
          case None => Consequence.argumentMissing("id")
        }
    }

  private def _job_id(req: Request): Consequence[org.goldenport.cncf.job.JobId] =
    req.arguments.find(_.name == "id") match {
      case Some(arg) => org.goldenport.cncf.job.JobId.parse(arg.value.toString)
      case None =>
        req.properties.find(_.name == "id") match {
          case Some(prop) => org.goldenport.cncf.job.JobId.parse(prop.value.toString)
          case None => Consequence.argumentMissing("id")
        }
    }

  private def _parse_event_id(s: String): Consequence[EventId] =
    EventId.parse(s)

  private def _event_record(record: EventRecord): Record =
    Record.data(
      "event-id" -> record.id.value,
      "name" -> record.name,
      "kind" -> record.kind,
      "created-at" -> record.createdAt.toString,
      "lane" -> record.lane.value,
      "persistent" -> record.persistent,
      "sequence" -> record.sequence,
      "source-subsystem" -> record.attributes.getOrElse("cncf.source.subsystem", ""),
      "source-component" -> record.attributes.getOrElse("cncf.source.component", ""),
      "target-subsystem" -> record.attributes.getOrElse("cncf.target.subsystem", ""),
      "target-component" -> record.attributes.getOrElse("cncf.target.component", ""),
      "origin-boundary" -> record.attributes.getOrElse("cncf.event.originBoundary", ""),
      "reception-rule" -> record.attributes.getOrElse("cncf.event.receptionRule", ""),
      "reception-policy" -> record.attributes.getOrElse("cncf.event.receptionPolicy", ""),
      "policy-source" -> record.attributes.getOrElse("cncf.event.policySource", ""),
      "saga-id" -> record.attributes.getOrElse("cncf.event.sagaId", ""),
      "task-relation" -> record.attributes.getOrElse("cncf.event.taskRelation", ""),
      "transaction-relation" -> record.attributes.getOrElse("cncf.event.transactionRelation", ""),
      "failure-policy" -> record.attributes.getOrElse("cncf.event.failurePolicy", ""),
      "failure-disposition-base" -> record.attributes.getOrElse("cncf.event.failureDispositionBase", ""),
      "dispatch-kind" -> record.attributes.getOrElse("cncf.event.dispatchKind", ""),
      "dispatch-status" -> record.attributes.getOrElse("cncf.event.dispatchStatus", ""),
      "retry-kind" -> record.attributes.getOrElse("cncf.job.retryKind", ""),
      "retry-attempt-count" -> record.attributes.getOrElse("cncf.job.retryAttemptCount", ""),
      "retry-max-attempts" -> record.attributes.getOrElse("cncf.job.retryMaxAttempts", ""),
      "retry-next-due-at" -> record.attributes.getOrElse("cncf.job.retryNextDueAt", ""),
      "retry-exhausted" -> record.attributes.getOrElse("cncf.job.retryExhausted", ""),
      "recovery-required" -> record.attributes.getOrElse("cncf.job.recoveryRequired", ""),
      "dead-letter" -> record.attributes.getOrElse("cncf.job.deadLetter", ""),
      "poison" -> record.attributes.getOrElse("cncf.job.poison", ""),
      "saga-relation" -> record.attributes.getOrElse("cncf.event.sagaRelation", ""),
      "event-history" -> record.attributes.getOrElse("cncf.event.history", ""),
      "payload" -> record.payload.toVector.sortBy(_._1).map { case (k, v) =>
        s"$k=$v"
      },
      "attributes" -> record.attributes.toVector.sortBy(_._1).map { case (k, v) =>
        s"$k=$v"
      }
    )
}
