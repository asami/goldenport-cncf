package org.goldenport.cncf.component.builtin.jobcontrol

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{JobControlCommand, JobControlResponse, JobExperienceQuery, JobExperienceQueryCodec, JobExperienceResult, JobExperienceScope, JobExperienceService, JobExperienceView, JobId}
import org.goldenport.cncf.openapi.{OpenApiHttpMethod, OpenApiOperationProjection}
import org.goldenport.cncf.usernotification.UserNotificationInboxRuntime
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain}
import org.goldenport.value.BaseContent

/*
 * Descriptor-backed public job experience service. Web and REST use the same
 * parser and action calls, so ingress cannot reinterpret scope or ownership.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[jobcontrol] object JobExperienceProtocol {
  def serviceDefinition: spec.ServiceDefinition = {
    val list = _definition("list_my_jobs", _list_request, OpenApiHttpMethod.GET, request => _query(request, Some("mine")).map(ListJobsAction(request, _)))
    val application = _definition("list_application_jobs", _list_request, OpenApiHttpMethod.GET, request => _query(request, Some("application")).map(ListJobsAction(request, _)))
    val operator = _definition("list_operator_jobs", _list_request, OpenApiHttpMethod.GET, request => _query(request, Some("operator")).map(ListJobsAction(request, _)))
    val get = _definition("get_job_experience", _id_scope_request, OpenApiHttpMethod.GET, request => _id_scope(request).map { case (jobid, scope) => GetJobAction(request, jobid, scope) })
    val diagnostics = _definition("get_job_diagnostics", _diagnostic_request, OpenApiHttpMethod.GET, request => _diagnostics(request).map { case (jobid, scope, offset, limit) => DiagnosticsAction(request, jobid, scope, offset, limit) })
    val control = _definition("control_job_experience", _control_request, OpenApiHttpMethod.POST, request => _control(request).map { case (jobid, scope, command) => ControlAction(request, jobid, scope, command) })
    val notifications = _definition("list_my_notifications", _notification_list_request, OpenApiHttpMethod.GET, request => _notification_list(request).map(NotificationListAction(request, _)))
    val notification = _definition("get_my_notification", _notification_id_request, OpenApiHttpMethod.GET, request => _notification_id(request).map { case (id, application) => NotificationGetAction(request, id, application) })
    val markread = _definition("mark_notification_read", _notification_id_request, OpenApiHttpMethod.POST, request => _notification_id(request).map { case (id, application) => NotificationReadAction(request, id, application) })
    val catalog = _definition("get_operator_catalog", spec.RequestDefinition(), OpenApiHttpMethod.GET, request => Consequence.success(CatalogAction(request)))
    spec.ServiceDefinition(
      name = "job_experience",
      operations = spec.OperationDefinitionGroup(operations = cats.data.NonEmptyVector.of(list, application, operator, get, diagnostics, control, notifications, notification, markread, catalog))
    )
  }

  def pageRecord(page: org.goldenport.cncf.job.JobExperiencePage): Record =
    Record.data("entries" -> page.entries.map { entry =>
      Record.data(
        "job-id" -> entry.jobId.value,
        "canonical-status" -> entry.canonicalStatus.toString,
        "status" -> entry.displayStatus.toString,
        "next-step" -> entry.vocabulary.nextStep,
        "persistence" -> entry.persistence.toString,
        "origin" -> entry.origin.toString,
        "created-at" -> entry.createdAt.toString,
        "updated-at" -> entry.updatedAt.toString
      )
    }, "total-count" -> page.totalCount, "next-cursor" -> page.nextCursor.map(_.value).getOrElse(""))

  def viewRecord(view: JobExperienceView): Record =
    Record.data(
      "job-id" -> view.detail.summary.jobId.value,
      "canonical-status" -> view.detail.summary.status.toString,
      "status" -> view.displayStatus.toString,
      "next-step" -> view.vocabulary.nextStep,
      "progress" -> view.progress.state,
      "task-count" -> view.progress.taskCount,
      "result" -> _result_record(view.result),
      "commands" -> view.controls.map(_.toString),
      "refreshable" -> view.refreshable
    )

  private def _definition(
    name: String,
    request: spec.RequestDefinition,
    method: OpenApiHttpMethod,
    create: Request => Consequence[OperationRequest]
  ): spec.OperationDefinition =
    new ExperienceOperationDefinition(name, request, method, create)

  private final class ExperienceOperationDefinition(
    name: String,
    request: spec.RequestDefinition,
    method: OpenApiHttpMethod,
    create: Request => Consequence[OperationRequest]
  ) extends spec.OperationDefinition with OpenApiOperationProjection {
    final val openApiHttpMethod: OpenApiHttpMethod = method
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = name, request = request, response = spec.ResponseDefinition(result = List(DataType.Named("Record"))))
    def createOperationRequest(request: Request): Consequence[OperationRequest] = create(request)
  }

  private final case class ListJobsAction(request: Request, query: JobExperienceQuery) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = ListJobsCall(core, query)
  }
  private final case class GetJobAction(request: Request, jobId: JobId, scope: JobExperienceScope) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = GetJobCall(core, jobId, scope)
  }
  private final case class DiagnosticsAction(request: Request, jobId: JobId, scope: JobExperienceScope, offset: Int, limit: Int) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = DiagnosticsCall(core, jobId, scope, offset, limit)
  }
  private final case class ControlAction(request: Request, jobId: JobId, scope: JobExperienceScope, command: JobControlCommand) extends SyncCommandAction {
    def createCall(core: ActionCall.Core): ActionCall = ControlCall(core, jobId, scope, command)
  }
  private final case class NotificationListAction(request: Request, values: (Option[String], Boolean, Int, Option[String])) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = NotificationListCall(core, values)
  }
  private final case class NotificationGetAction(request: Request, id: String, application: Option[String]) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = NotificationGetCall(core, id, application)
  }
  private final case class NotificationReadAction(request: Request, id: String, application: Option[String]) extends SyncCommandAction {
    def createCall(core: ActionCall.Core): ActionCall = NotificationReadCall(core, id, application)
  }
  private final case class CatalogAction(request: Request) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = CatalogCall(core)
  }

  private abstract class SyncCommandAction extends CommandAction {
    override def commandExecutionMode: org.goldenport.cncf.action.CommandExecutionMode = org.goldenport.cncf.action.CommandExecutionMode.Sync
  }

  private final case class ListJobsCall(core: ActionCall.Core, query: JobExperienceQuery) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = _service_response(core)(_.list(query)(using core.executionContext).map(pageRecord))
  }
  private final case class GetJobCall(core: ActionCall.Core, jobid: JobId, scope: JobExperienceScope) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = _service_response(core)(_.get(jobid, scope)(using core.executionContext).map(viewRecord))
  }
  private final case class DiagnosticsCall(core: ActionCall.Core, jobid: JobId, scope: JobExperienceScope, offset: Int, limit: Int) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = _service_response(core)(_.diagnostics(jobid, scope, offset, limit)(using core.executionContext).map { diagnostics =>
      Record.data("job-id" -> diagnostics.jobId.value, "tasks" -> diagnostics.tasks.tasks.map(task => Record.data("id" -> task.taskId.value, "status" -> task.status.toString, "started-at" -> task.startedAt.toString, "finished-at" -> task.finishedAt.map(_.toString).getOrElse(""))), "timeline" -> diagnostics.timeline.events.map(event => Record.data("id" -> event.taskId.map(_.value).getOrElse(""), "kind" -> event.kind, "status" -> "", "occurred-at" -> event.occurredAt.toString)))
    })
  }
  private final case class ControlCall(core: ActionCall.Core, jobid: JobId, scope: JobExperienceScope, command: JobControlCommand) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = _service_response(core)(_.control(jobid, scope, command)(using core.executionContext).map(_control_record))
  }
  private final case class NotificationListCall(core: ActionCall.Core, values: (Option[String], Boolean, Int, Option[String])) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given ExecutionContext = core.executionContext
      UserNotificationInboxRuntime.list(core.executionContext, values._1, values._2, values._3, values._4).map { page =>
        OperationResponse.RecordResponse(Record.data("entries" -> page.entries.map(_notification_record), "total-count" -> page.totalCount, "next-cursor" -> page.nextCursor.getOrElse("")))
      }
    }
  }
  private final case class NotificationGetCall(core: ActionCall.Core, id: String, application: Option[String]) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = UserNotificationInboxRuntime.get(core.executionContext, id, application).map(value => OperationResponse.RecordResponse(_notification_record(value)))
  }
  private final case class NotificationReadCall(core: ActionCall.Core, id: String, application: Option[String]) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = UserNotificationInboxRuntime.markRead(core.executionContext, id, application).map { value =>
      OperationResponse.RecordResponse(Record.data("id" -> value.id, "read-at" -> value.readAt.map(_.toString).getOrElse(""), "changed" -> value.changed))
    }
  }
  private final case class CatalogCall(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = core.component match {
      case Some(component) => _service_response(core)(_.operatorCatalog(component)(using core.executionContext))
      case None => Consequence.serviceUnavailable("job experience service is not available")
    }
  }

  private def _service_response(core: ActionCall.Core)(f: JobExperienceService => Consequence[Record]): Consequence[OperationResponse] =
    core.component.flatMap(_.port.get[JobExperienceService]) match {
      case Some(service) =>
        given ExecutionContext = core.executionContext
        f(service).map(OperationResponse.RecordResponse.apply)
      case None => Consequence.serviceUnavailable("job experience service is not available")
    }

  private def _query(request: Request, forced: Option[String]): Consequence[JobExperienceQuery] =
    JobExperienceQueryCodec.parse(
      _string(request, "scope"),
      _string(request, "application"),
      _string(request, "persistentOnly"),
      _string(request, "status"),
      _string(request, "origin"),
      _string(request, "limit"),
      _string(request, "cursor"),
      forced
    )

  private def _id_scope(request: Request): Consequence[(JobId, JobExperienceScope)] =
    for { jobid <- _job_id(request); scope <- JobExperienceQueryCodec.parseScope(_string(request, "scope"), _string(request, "application")) } yield (jobid, scope)
  private def _diagnostics(request: Request): Consequence[(JobId, JobExperienceScope, Int, Int)] =
    for {
      base <- _id_scope(request)
      offset <- _int(request, "offset", 0)
      limit <- _int(request, "limit", 25)
      _ <- if (offset >= 0 && limit >= 1 && limit <= 100) Consequence.unit else Consequence.argumentInvalid("offset must be nonnegative and limit must be in 1..100")
    } yield (base._1, base._2, offset, limit)
  private def _control(request: Request): Consequence[(JobId, JobExperienceScope, JobControlCommand)] =
    for { base <- _id_scope(request); command <- _command(request) } yield (base._1, base._2, command)
  private def _notification_list(request: Request): Consequence[(Option[String], Boolean, Int, Option[String])] =
    JobExperienceQueryCodec.parseNotifications(_string(request, "application"), _string(request, "unreadOnly"), _string(request, "limit"), _string(request, "cursor")).map(value => (value.application, value.unreadOnly, value.limit, value.cursor))
  private def _notification_id(request: Request): Consequence[(String, Option[String])] =
    _string(request, "id").map { id =>
      _string(request, "application")
        .map(JobExperienceScope.normalizedApplication)
        .getOrElse(Consequence.success(""))
        .map(application => id -> Option.when(application.nonEmpty)(application))
    }.getOrElse(Consequence.argumentMissing("id"))
  private def _job_id(request: Request): Consequence[JobId] = _string(request, "id").map(JobId.parse).getOrElse(Consequence.argumentMissing("id"))
  private def _string(request: Request, name: String): Option[String] = request.arguments.find(_.name == name).orElse(request.properties.find(_.name == name)).map(_.value.toString.trim).filter(_.nonEmpty)
  private def _int(request: Request, name: String, default: Int): Consequence[Int] = _string(request, name).map(_.toIntOption).flatten.map(Consequence.success).getOrElse(if (_string(request, name).isEmpty) Consequence.success(default) else Consequence.argumentInvalid(s"$name must be an integer"))
  private def _command(request: Request): Consequence[JobControlCommand] = _string(request, "command").flatMap(value => JobControlCommand.values.find(_.toString.equalsIgnoreCase(value))).map(Consequence.success).getOrElse(Consequence.argumentInvalid("invalid command"))
  private def _result_record(value: JobExperienceResult): Record = value match { case JobExperienceResult.Available(text) => Record.data("availability" -> "available", "text" -> text); case JobExperienceResult.Failed => Record.data("availability" -> "failed", "text" -> "The job failed. Contact an operator for assistance."); case JobExperienceResult.Pending => Record.data("availability" -> "pending"); case JobExperienceResult.Unavailable => Record.data("availability" -> "unavailable"); case JobExperienceResult.UnavailableAfterRestart => Record.data("availability" -> "unavailable-after-restart") }
  private def _control_record(value: JobControlResponse): Record = Record.data("job-id" -> value.jobId.value, "status" -> value.status.toString, "async" -> value.async, "changed" -> value.changed)
  private def _notification_record(value: org.goldenport.cncf.usernotification.UserNotificationInboxView): Record = Record.data("id" -> value.id, "application" -> value.application.getOrElse(""), "title" -> value.title, "body" -> value.body, "created-at" -> value.createdAt.toString, "updated-at" -> value.updatedAt.toString, "read-at" -> value.readAt.map(_.toString).getOrElse(""), "expires-at" -> value.expiresAt.map(_.toString).getOrElse(""), "action-url" -> value.actionUrl.getOrElse(""), "status" -> value.status)

  private def _request(names: String*): spec.RequestDefinition =
    spec.RequestDefinition(
      parameters = names.toList.map(name => spec.ParameterDefinition(
        content = BaseContent.simple(name),
        kind = spec.ParameterDefinition.Kind.Argument,
        domain = ValueDomain(multiplicity = if (name == "id" || name == "command") Multiplicity.One else Multiplicity.ZeroOne)
      ))
    )
  private val _list_request = _request("persistentOnly", "status", "origin", "limit", "cursor", "application")
  private val _id_scope_request = _request("id", "scope", "application")
  private val _diagnostic_request = _request("id", "scope", "application", "offset", "limit")
  private val _control_request = _request("id", "scope", "application", "command")
  private val _notification_list_request = _request("application", "unreadOnly", "limit", "cursor")
  private val _notification_id_request = _request("id", "application")
}
