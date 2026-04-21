package org.goldenport.cncf.component.builtin.workflow

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.workflow.{WorkflowDefinition, WorkflowDefinitionId, WorkflowEngine, WorkflowHistoryEntry, WorkflowInstance, WorkflowInstanceId, WorkflowRegistration}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.DataType
import org.goldenport.value.BaseContent

/*
 * @since   Apr. 22, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkflowComponent() extends Component {
}

object WorkflowComponent {
  trait WorkflowService {
    def listWorkflowDefinitions(): Consequence[Vector[WorkflowDefinition]]
    def describeWorkflowDefinition(nameOrId: String): Consequence[WorkflowDefinition]
    def listWorkflowInstances(): Consequence[Vector[WorkflowInstance]]
    def getWorkflowInstance(id: WorkflowInstanceId): Consequence[WorkflowInstance]
    def loadWorkflowHistory(id: WorkflowInstanceId): Consequence[Vector[WorkflowHistoryEntry]]
  }

  val name: String = "workflow"
  val componentId: ComponentId = ComponentId(name)

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component =
      WorkflowComponent()

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = spec.RequestDefinition()
      val identityRequest = _identity_request
      val listDefinitions = new ListWorkflowDefinitionsOperationDefinition(request, spec.ResponseDefinition(result = List(DataType.Named("RecordList"))))
      val describeDefinition = new DescribeWorkflowDefinitionOperationDefinition(identityRequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val listInstances = new ListWorkflowInstancesOperationDefinition(request, spec.ResponseDefinition(result = List(DataType.Named("RecordList"))))
      val getInstance = new GetWorkflowInstanceOperationDefinition(identityRequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val loadHistory = new LoadWorkflowHistoryOperationDefinition(identityRequest, spec.ResponseDefinition(result = List(DataType.Named("RecordList"))))
      val workflowService = spec.ServiceDefinition(
        name = "workflow",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(listDefinitions, describeDefinition, listInstances, getInstance, loadHistory)
        )
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(services = Vector(workflowService)),
        handler = ProtocolHandler.default
      )
      comp.withPort(Component.Port.of(new DefaultWorkflowService(comp)))
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(name, componentId, instanceid, protocol)
    }

    private def _identity_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition(content = BaseContent.simple("id"), kind = spec.ParameterDefinition.Kind.Argument))
      )
  }

  private final class DefaultWorkflowService(component: Component) extends WorkflowService {
    private def _engine: WorkflowEngine =
      component.subsystem.map(_.workflowEngine).getOrElse(throw new IllegalStateException("workflow engine is not available"))

    def listWorkflowDefinitions(): Consequence[Vector[WorkflowDefinition]] =
      Consequence.success(_engine.definitions)

    def describeWorkflowDefinition(nameOrId: String): Consequence[WorkflowDefinition] =
      _engine.findDefinition(nameOrId) match {
        case Some(definition) => Consequence.success(definition)
        case None => Consequence.operationNotFound(s"workflow definition:$nameOrId")
      }

    def listWorkflowInstances(): Consequence[Vector[WorkflowInstance]] =
      Consequence.success(_engine.instances)

    def getWorkflowInstance(id: WorkflowInstanceId): Consequence[WorkflowInstance] =
      _engine.findInstance(id) match {
        case Some(instance) => Consequence.success(instance)
        case None => Consequence.operationNotFound(s"workflow instance:${id.value}")
      }

    def loadWorkflowHistory(id: WorkflowInstanceId): Consequence[Vector[WorkflowHistoryEntry]] =
      _engine.findInstance(id) match {
        case Some(_) => Consequence.success(_engine.history(id))
        case None => Consequence.operationNotFound(s"workflow history:${id.value}")
      }
  }

  private final class ListWorkflowDefinitionsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list_workflow_definitions",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(ListWorkflowDefinitionsAction(req))
  }

  private final class DescribeWorkflowDefinitionOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "describe_workflow_definition",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _identity(req).map(DescribeWorkflowDefinitionAction(req, _))
  }

  private final class ListWorkflowInstancesOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list_workflow_instances",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(ListWorkflowInstancesAction(req))
  }

  private final class GetWorkflowInstanceOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "get_workflow_instance",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _workflow_instance_id(req).map(GetWorkflowInstanceAction(req, _))
  }

  private final class LoadWorkflowHistoryOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "load_workflow_history",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _workflow_instance_id(req).map(LoadWorkflowHistoryAction(req, _))
  }

  private final case class ListWorkflowDefinitionsAction(request: Request) extends SyncWorkflowAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ListWorkflowDefinitionsCall(core)
  }

  private final case class DescribeWorkflowDefinitionAction(request: Request, identity: String) extends SyncWorkflowAction {
    def createCall(core: ActionCall.Core): ActionCall =
      DescribeWorkflowDefinitionCall(core, identity)
  }

  private final case class ListWorkflowInstancesAction(request: Request) extends SyncWorkflowAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ListWorkflowInstancesCall(core)
  }

  private final case class GetWorkflowInstanceAction(request: Request, id: WorkflowInstanceId) extends SyncWorkflowAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetWorkflowInstanceCall(core, id)
  }

  private final case class LoadWorkflowHistoryAction(request: Request, id: WorkflowInstanceId) extends SyncWorkflowAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadWorkflowHistoryCall(core, id)
  }

  private abstract class SyncWorkflowAction extends CommandAction {
    override def commandExecutionMode: org.goldenport.cncf.action.CommandExecutionMode =
      org.goldenport.cncf.action.CommandExecutionMode.SyncDirectNoJob
  }

  private final case class ListWorkflowDefinitionsCall(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.listWorkflowDefinitions()).map { definitions =>
        OperationResponse.RecordResponse(
          Record.data(
            "definitions" -> definitions.map(_definition_record)
          )
        )
      }
  }

  private final case class DescribeWorkflowDefinitionCall(core: ActionCall.Core, identity: String) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.describeWorkflowDefinition(identity)).map(definition =>
        OperationResponse.RecordResponse(_definition_record(definition))
      )
  }

  private final case class ListWorkflowInstancesCall(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.listWorkflowInstances()).map { instances =>
        OperationResponse.RecordResponse(
          Record.data(
            "instances" -> instances.map(_instance_record)
          )
        )
      }
  }

  private final case class GetWorkflowInstanceCall(core: ActionCall.Core, id: WorkflowInstanceId) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.getWorkflowInstance(id)).map(instance =>
        OperationResponse.RecordResponse(_instance_record(instance))
      )
  }

  private final case class LoadWorkflowHistoryCall(core: ActionCall.Core, id: WorkflowInstanceId) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.loadWorkflowHistory(id)).map { history =>
        OperationResponse.RecordResponse(
          Record.data(
            "instance-id" -> id.value,
            "history" -> history.map(_history_record)
          )
        )
      }
  }

  private def _service(core: ActionCall.Core): Consequence[WorkflowService] =
    core.component match {
      case Some(component) =>
        component.port.get[WorkflowService] match {
          case Some(service) => Consequence.success(service)
          case None => Consequence.serviceUnavailable("workflow service is not available")
        }
      case None =>
        Consequence.serviceUnavailable("component is not initialized")
    }

  private def _definition_record(definition: WorkflowDefinition): Record =
    Record.data(
      "definition-id" -> definition.id.value,
      "name" -> definition.name,
      "registrations" -> definition.registrations.map(_registration_record)
    )

  private def _registration_record(registration: WorkflowRegistration): Record =
    Record.data(
      "name" -> registration.name,
      "event-name" -> registration.eventName,
      "entity-collection" -> registration.entityCollection,
      "entity-id-key" -> registration.entityIdKey,
      "status-field" -> registration.statusField,
      "priority" -> registration.priority.value,
      "status-rules" -> registration.statusRules.map { rule =>
        Record.data(
          "current-status" -> rule.currentStatus,
          "next-action" -> rule.nextAction,
          "note" -> rule.note.getOrElse("")
        )
      }
    )

  private def _instance_record(instance: WorkflowInstance): Record =
    Record.data(
      "instance-id" -> instance.id.value,
      "registration-name" -> instance.registrationName,
      "entity-collection" -> instance.entityCollection,
      "entity-id" -> instance.entityId,
      "status" -> instance.status.toString,
      "triggering-event-name" -> instance.triggeringEventName,
      "current-entity-status" -> instance.currentEntityStatus.getOrElse(""),
      "last-action" -> instance.lastAction.getOrElse(""),
      "related-job-ids" -> instance.relatedJobIds.map(_.value),
      "started-at" -> instance.startedAt.toString,
      "updated-at" -> instance.updatedAt.toString,
      "job-surface" -> Record.data(
        "selectors" -> Vector(
          "job_control.job.get_job_status",
          "job_control.job.load_job_history",
          "job_control.job.get_job_result"
        ),
        "summary" -> "Execution detail belongs to job_control surfaces."
      )
    )

  private def _history_record(entry: WorkflowHistoryEntry): Record =
    Record.data(
      "occurred-at" -> entry.occurredAt.toString,
      "status" -> entry.status.toString,
      "message" -> entry.message,
      "entity-status" -> entry.entityStatus.getOrElse(""),
      "selected-action" -> entry.selectedAction.getOrElse(""),
      "related-job-id" -> entry.relatedJobId.map(_.value).getOrElse("")
    )

  private def _identity(req: Request): Consequence[String] =
    req.arguments.find(_.name == "id").map(_.value.toString).filter(_.nonEmpty) match {
      case Some(value) => Consequence.success(value)
      case None =>
        req.properties.find(_.name == "id").map(_.value.toString).filter(_.nonEmpty) match {
          case Some(value) => Consequence.success(value)
          case None => Consequence.argumentMissing("id")
        }
    }

  private def _workflow_instance_id(req: Request): Consequence[WorkflowInstanceId] =
    _identity(req).flatMap(WorkflowInstanceId.parse)
}
