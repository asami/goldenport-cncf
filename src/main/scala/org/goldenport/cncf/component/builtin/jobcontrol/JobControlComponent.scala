package org.goldenport.cncf.component.builtin.jobcontrol

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.job.{JobControlCommand, JobControlRequest, JobId}
import org.goldenport.cncf.job.{JobQueryReadModel, JobTimelinePage}
import org.goldenport.cncf.event.EventStore
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record

/*
 * @since   Mar. 28, 2026
 * @version Mar. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobControlComponent() extends Component {
}

object JobControlComponent {
  trait JobService {
    def loadJob(jobId: JobId): Consequence[JobQueryReadModel]
    def loadJobHistory(jobId: JobId): Consequence[JobTimelinePage]
  }

  trait JobAdminService {
    def cancelJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def suspendJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def resumeJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def loadJobEvents(jobId: JobId): Consequence[Vector[Record]]
  }

  val name: String = "job_control"
  val componentId: ComponentId = ComponentId(name)

  object Factory extends Component.Factory {
    protected def create_Components(params: ComponentCreate): Vector[Component] =
      Vector(JobControlComponent())

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = spec.RequestDefinition()
      val response = spec.ResponseDefinition()
      val idrequest = _job_id_request
      val loadJob = new LoadJobOperationDefinition(request = idrequest, response = response)
      val loadJobHistory = new LoadJobHistoryOperationDefinition(request = idrequest, response = response)
      val cancelJob = new ControlJobOperationDefinition(
        name = "cancel_job",
        command = JobControlCommand.Cancel,
        request = idrequest,
        response = response
      )
      val suspendJob = new ControlJobOperationDefinition(
        name = "suspend_job",
        command = JobControlCommand.Suspend,
        request = idrequest,
        response = response
      )
      val resumeJob = new ControlJobOperationDefinition(
        name = "resume_job",
        command = JobControlCommand.Resume,
        request = idrequest,
        response = response
      )
      val loadJobEvents = new LoadJobEventsOperationDefinition(request = idrequest, response = response)
      val jobService = spec.ServiceDefinition(
        name = "job",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(loadJob, loadJobHistory)
        )
      )
      val jobAdminService = spec.ServiceDefinition(
        name = "job_admin",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(cancelJob, suspendJob, resumeJob, loadJobEvents)
        )
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          services = Vector(jobService, jobAdminService)
        ),
        handler = ProtocolHandler.default
      )
      comp.withPort(
        Component.Port.of(new DefaultJobService(comp), new DefaultJobAdminService(comp))
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(
        name,
        componentId,
        instanceid,
        protocol
      )
    }

    private def _job_id_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition("id", spec.ParameterDefinition.Kind.Argument))
      )
  }

  private final class DefaultJobService(component: Component) extends JobService {
    def loadJob(jobId: JobId): Consequence[JobQueryReadModel] =
      component.jobEngine.query(jobId) match {
        case Some(model) => Consequence.success(model)
        case None => Consequence.failure(s"job not found: ${jobId.value}")
      }

    def loadJobHistory(jobId: JobId): Consequence[JobTimelinePage] =
      component.jobEngine.queryTimeline(jobId) match {
        case Some(page) => Consequence.success(page)
        case None => Consequence.failure(s"job history not found: ${jobId.value}")
      }
  }

  private final class DefaultJobAdminService(component: Component) extends JobAdminService {
    def cancelJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse] =
      component.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Cancel))

    def suspendJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse] =
      component.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Suspend))

    def resumeJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse] =
      component.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Resume))

    def loadJobEvents(jobId: JobId): Consequence[Vector[Record]] =
      component.eventStore match {
        case Some(store) =>
          store.query(EventStore.Query()).map { records =>
            records.filter { record =>
              record.payload.get("job-id").contains(jobId.value) ||
              record.attributes.get("job-id").contains(jobId.value)
            }.map(_event_record)
          }
        case None =>
          Consequence.failure("event store is not available")
      }
  }

  private final class LoadJobOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = "load_job",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        LoadJobAction(req, jobid)
      }
  }

  private final class LoadJobHistoryOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = "load_job_history",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        LoadJobHistoryAction(req, jobid)
      }
  }

  private final class ControlJobOperationDefinition(
    name: String,
    command: JobControlCommand,
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = name,
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        ControlJobAction(req, jobid, command)
      }
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
      _job_id(req).map { jobid =>
        LoadJobEventsAction(req, jobid)
      }
  }

  private final case class LoadJobAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadJobCall(core, jobId)
  }

  private final case class LoadJobHistoryAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadJobHistoryCall(core, jobId)
  }

  private final case class ControlJobAction(
    request: Request,
    jobId: JobId,
    command: JobControlCommand
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ControlJobCall(core, jobId, command)
  }

  private final case class LoadJobEventsAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadJobEventsCall(core, jobId)
  }

  private abstract class SyncJobAction extends CommandAction {
    override def commandExecutionMode: org.goldenport.cncf.action.CommandExecutionMode =
      org.goldenport.cncf.action.CommandExecutionMode.SyncDirectNoJob
  }

  private final case class LoadJobCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.loadJob(jobId)) match {
            case Some(result) => result.map(model => OperationResponse.RecordResponse(_job_record(model)))
            case None => Consequence.failure("job service is not available")
          }
        case None =>
          Consequence.failure("component is not initialized")
      }
  }

  private final case class LoadJobHistoryCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.loadJobHistory(jobId)) match {
            case Some(result) => result.map(page => OperationResponse.RecordResponse(_timeline_record(jobId, page)))
            case None => Consequence.failure("job service is not available")
          }
        case None =>
          Consequence.failure("component is not initialized")
      }
  }

  private final case class ControlJobCall(
    core: ActionCall.Core,
    jobId: JobId,
    command: JobControlCommand
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          given org.goldenport.cncf.context.ExecutionContext = core.executionContext
          val jobAdmin = component.port.get[JobAdminService].getOrElse {
            return Consequence.failure("job admin service is not available")
          }
          val response = command match {
            case JobControlCommand.Cancel => jobAdmin.cancelJob(jobId)
            case JobControlCommand.Suspend => jobAdmin.suspendJob(jobId)
            case JobControlCommand.Resume => jobAdmin.resumeJob(jobId)
            case JobControlCommand.Retry => component.logic.controlJob(jobId, JobControlRequest(command))
          }
          response.map { response =>
            OperationResponse.RecordResponse(
              Record.data(
                "job-id" -> response.jobId.value,
                "status" -> response.status.toString,
                "async" -> response.async,
                "response" -> response.response.map(_.print).getOrElse("")
              )
            )
          }
        case None =>
          Consequence.failure("component is not initialized")
      }
  }

  private final case class LoadJobEventsCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobAdminService].map(_.loadJobEvents(jobId)) match {
            case Some(result) =>
              result.map { records =>
                OperationResponse.RecordResponse(
                  Record.data(
                    "job-id" -> jobId.value,
                    "events" -> records
                  )
                )
              }
            case None =>
              Consequence.failure("job admin service is not available")
          }
        case None =>
          Consequence.failure("component is not initialized")
      }
  }

  private def _job_id(req: Request): Consequence[JobId] =
    req.arguments.find(_.name == "id") match {
      case Some(arg) => JobId.parse(arg.value.toString)
      case None =>
        req.properties.find(_.name == "id") match {
          case Some(prop) => JobId.parse(prop.value.toString)
          case None => Consequence.failure("id argument is required")
        }
    }

  private def _job_matches(
    record: org.goldenport.cncf.event.EventRecord,
    jobId: JobId
  ): Boolean =
    record.payload.get("job-id").exists(_.toString == jobId.value) ||
      record.attributes.get("job-id").exists(_ == jobId.value)

  private def _job_record(model: JobQueryReadModel): Record =
    Record.data(
      "job-id" -> model.jobId.value,
      "status" -> model.status.toString,
      "persistence" -> model.persistence.toString,
      "origin" -> model.origin.toString,
      "created-at" -> model.createdAt.toString,
      "updated-at" -> model.updatedAt.toString,
      "result-success" -> model.resultSummary.success,
      "result-message" -> model.resultSummary.message.getOrElse(""),
      "result" -> model.result.map(_.print).getOrElse(""),
      "tasks" -> model.tasks.tasks.map { task =>
        Record.data(
          "task-id" -> task.taskId.value,
          "status" -> task.status.toString,
          "success" -> task.result.success,
          "message" -> task.result.message.getOrElse("")
        )
      },
      "timeline" -> model.timeline.events.map(_timeline_event_record),
      "debug-request-summary" -> model.debug.requestSummary.getOrElse("")
    )

  private def _timeline_record(
    jobId: JobId,
    page: JobTimelinePage
  ): Record =
    Record.data(
      "job-id" -> jobId.value,
      "offset" -> page.offset,
      "limit" -> page.limit,
      "total-count" -> page.totalCount,
      "fetched-count" -> page.fetchedCount,
      "events" -> page.events.map(_timeline_event_record)
    )

  private def _timeline_event_record(
    event: org.goldenport.cncf.job.JobTimelineEvent
  ): Record =
    Record.data(
      "sequence" -> event.sequence,
      "occurred-at" -> event.occurredAt.toString,
      "kind" -> event.kind,
      "task-id" -> event.taskId.map(_.value).getOrElse(""),
      "parent-task-id" -> event.parentTaskId.map(_.value).getOrElse(""),
      "note" -> event.note.getOrElse("")
    )

  private def _event_record(
    event: org.goldenport.cncf.event.EventRecord
  ): Record =
    Record.data(
      "event-id" -> event.id.value,
      "name" -> event.name,
      "kind" -> event.kind,
      "sequence" -> event.sequence,
      "created-at" -> event.createdAt.toString,
      "lane" -> event.lane.value,
      "persistent" -> event.persistent,
      "payload" -> event.payload.toVector.sortBy(_._1).map { case (k, v) =>
        Record.data(k -> v)
      }
    )
}
