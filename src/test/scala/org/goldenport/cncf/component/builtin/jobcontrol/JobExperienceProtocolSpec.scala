package org.goldenport.cncf.component.builtin.jobcontrol

import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.job.{JobControlCommand, JobExperienceQueryCodec, JobExperienceService, JobId, JobPersistencePolicy, JobRunMode, JobStatus, JobSubmitOption}
import org.goldenport.cncf.http.StaticFormAppRenderer
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.openapi.OpenApiHttpMethod
import org.goldenport.cncf.openapi.OpenApiProjector
import org.goldenport.cncf.projection.HelpProjection
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemRuntimeBinding, GenericSubsystemUserNotificationBinding, GenericSubsystemUserNotificationProviderBinding, Subsystem}
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.goldenport.cncf.usernotification.{UserNotificationInboxEntry, UserNotificationInboxPage, UserNotificationInboxProvider, UserNotificationInboxQuery, UserNotificationInboxReadResult, UserNotificationInboxUpdate, UserNotificationRequest, UserNotificationResult}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.schema.Multiplicity
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobExperienceProtocolSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Job experience protocol" should {
    "P1 expose one descriptor-backed service with the frozen operations and HTTP methods" in {
      Given("the installed job_control component")
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))

        When("Help and OpenAPI derive the service operations from its protocol")
        val service = component.protocol.services.services.find(_.name == "job_experience").getOrElse(fail("job_experience is missing"))
        val operations = service.operations.operations.toVector.map(_.name).toSet
        val paths = parse(OpenApiProjector.forSubsystem(subsystem)).fold(error => fail(error.getMessage), identity).hcursor.downField("paths")

        Then("all reads are GET and the two user commands are POST")
        operations shouldBe Set("list_my_jobs", "list_application_jobs", "list_operator_jobs", "get_job_experience", "get_job_diagnostics", "control_job_experience", "list_my_notifications", "get_my_notification", "mark_notification_read", "get_operator_catalog")
        val getpath = s"/rest/v1${NamingConventions.toNormalizedPath(component.name, "job_experience", "get_job_experience")}"
        val controlpath = s"/rest/v1${NamingConventions.toNormalizedPath(component.name, "job_experience", "control_job_experience")}"
        val readpath = s"/rest/v1${NamingConventions.toNormalizedPath(component.name, "job_experience", "mark_notification_read")}"
        paths.downField(getpath).downField(OpenApiHttpMethod.GET.toString).focus should not be empty
        paths.downField(controlpath).downField(OpenApiHttpMethod.POST.toString).focus should not be empty
        paths.downField(readpath).downField(OpenApiHttpMethod.POST.toString).focus should not be empty
      }
    }

    "P1 discover the generated Help/meta and OpenAPI operation before invoking its installed component resolver" in {
      Given("the installed job_control component and its generated job_experience Help/meta surface")
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
        val selector = s"${component.componentId.name}.job_experience.list_my_jobs"
        val help = HelpProjection.projectModel(component, Some(selector))
        val operation = component.protocol.services.services.find(_.name == "job_experience")
          .flatMap(_.operations.operations.toVector.find(_.name == "list_my_jobs"))
          .getOrElse(fail("list_my_jobs Help operation is missing"))
        val paths = parse(OpenApiProjector.forSubsystem(subsystem)).fold(error => fail(error.getMessage), identity).hcursor.downField("paths")
        given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.User)

        When("the Help/meta-discovered GET operation is resolved and executed through the component port")
        val response = _execute(component, _request("list_my_jobs", "limit" -> 25))

        Then("the actual Help/meta surface, OpenAPI, resolver, and bounded response agree on the installed operation")
        help.`type` shouldBe "operation"
        help.name shouldBe "list_my_jobs"
        help.selector.map(_.canonical) shouldBe Some(selector)
        operation.name shouldBe "list_my_jobs"
        val listpath = s"/rest/v1${NamingConventions.toNormalizedPath(component.name, "job_experience", "list_my_jobs")}"
        paths.downField(listpath).downField(OpenApiHttpMethod.GET.toString).focus should not be empty
        response.toOption.collect { case OperationResponse.RecordResponse(record) => record.getAny("entries") } should not be empty
      }
    }

    "P1 expose optional/defaulted parameters to the static form validator while keeping id and command required" in {
      Given("the installed job_control job_experience descriptor and its static form validator")
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
        val service = component.protocol.services.services.find(_.name == "job_experience").getOrElse(fail("job_experience is missing"))
        val markreadparameters = service.operations.operations.toVector.find(_.name == "mark_notification_read")
          .getOrElse(fail("mark_notification_read is missing")).specification.request.parameters.map(parameter => parameter.name -> parameter).toMap
        val controlparameters = service.operations.operations.toVector.find(_.name == "control_job_experience")
          .getOrElse(fail("control_job_experience is missing")).specification.request.parameters.map(parameter => parameter.name -> parameter).toMap
        val listparameters = service.operations.operations.toVector.find(_.name == "list_my_jobs")
          .getOrElse(fail("list_my_jobs is missing")).specification.request.parameters
        val renderer = new StaticFormAppRenderer
        val id = JobId.generate().value

        When("the actual descriptor-backed form validator receives required-only notification and control requests")
        val markread = renderer.validateOperationForm(subsystem, BuiltinComponentIdentity.JOB_CONTROL.name, "job_experience", "mark_notification_read", Map("id" -> "fixture-notification")).getOrElse(fail("mark_notification_read form validation is missing"))
        val missingid = renderer.validateOperationForm(subsystem, BuiltinComponentIdentity.JOB_CONTROL.name, "job_experience", "mark_notification_read", Map.empty).getOrElse(fail("missing-id form validation is missing"))
        val control = renderer.validateOperationForm(subsystem, BuiltinComponentIdentity.JOB_CONTROL.name, "job_experience", "control_job_experience", Map("id" -> id, "command" -> "Cancel")).getOrElse(fail("control form validation is missing"))
        val missingcommand = renderer.validateOperationForm(subsystem, BuiltinComponentIdentity.JOB_CONTROL.name, "job_experience", "control_job_experience", Map("id" -> id)).getOrElse(fail("missing-command form validation is missing"))

        Then("the descriptor preserves optional defaults while the renderer enforces id and command")
        markreadparameters("id").multiplicity shouldBe Multiplicity.One
        markreadparameters("application").multiplicity shouldBe Multiplicity.ZeroOne
        controlparameters("id").multiplicity shouldBe Multiplicity.One
        controlparameters("command").multiplicity shouldBe Multiplicity.One
        controlparameters("scope").multiplicity shouldBe Multiplicity.ZeroOne
        controlparameters("application").multiplicity shouldBe Multiplicity.ZeroOne
        listparameters.foreach(_.multiplicity shouldBe Multiplicity.ZeroOne)
        markread.valid shouldBe true
        missingid.valid shouldBe false
        control.valid shouldBe true
        missingcommand.valid shouldBe false
      }
    }

    "P2 reject the shared protocol scope and bounded parameter grammar" in {
      Given("protocol ingress values that cannot name a valid Job experience query")
      val badscope = JobExperienceQueryCodec.parse(Some("recipient"), None, None, None, None, None, None)
      val badlimit = JobExperienceQueryCodec.parse(None, None, None, None, None, Some("101"), None, Some("mine"))

      When("the common decoder is used before an action call is created")
      val scopresult = badscope.isSuccess
      val limitresult = badlimit.isSuccess

      Then("both values are rejected before component-port dispatch")
      scopresult shouldBe false
      limitresult shouldBe false
    }

    "P2 apply actual resolver defaults permissions and bounds before a job experience action executes" in {
      Given("ordinary and content-manager execution contexts with installed resolver actions")
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
        val ordinary = ExecutionContext.create(SecurityContext.Privilege.User)
        val administrator = ExecutionContext.create(SecurityContext.Privilege.ApplicationContentManager)

        When("default Mine, Application, Operator, and over-bound diagnostic requests are dispatched")
        val mine = _execute(component, _request("list_my_jobs"))(using ordinary)
        val application = _execute(component, _request("list_application_jobs", "application" -> "blog"))(using ordinary)
        val operator = _execute(component, _request("list_operator_jobs"))(using ordinary)
        val invaliddiagnostics = _execute(component, _request("get_job_diagnostics", "id" -> JobId.generate().value, "scope" -> "mine", "offset" -> -1, "limit" -> 101))(using administrator)

        Then("only the default personal read reaches its resolver result; forbidden scopes and bounds fail before projection")
        mine.isSuccess shouldBe true
        application.isSuccess shouldBe false
        operator.isSuccess shouldBe false
        invaliddiagnostics.isSuccess shouldBe false
      }
    }

    "P3 return the actual canonical control changed=false replay record through the command resolver" in {
      Given("a submitted runtime Job and an installed content-manager command port")
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
        given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.ApplicationContentManager)
        val id = subsystem.jobEngine.submit(
          Nil,
          summon[ExecutionContext],
          JobSubmitOption(
            persistence = JobPersistencePolicy.Ephemeral,
            runMode = JobRunMode.Async,
            scheduledStartAt = Some(summon[ExecutionContext].clock.instant().plusSeconds(300L))
          )
        ).toOption.getOrElse(fail("submission failed"))
        subsystem.jobEngine.getStatus(id) shouldBe Some(JobStatus.Submitted)

        val inbox = _install_inbox_provider(subsystem)

        When("the discovered control and notification-read commands are executed through their ActionCall resolvers")
        val first = _record(_execute(component, _request("control_job_experience", "id" -> id.value, "scope" -> "mine", "command" -> "Cancel")))
        val replay = _record(_execute(component, _request("control_job_experience", "id" -> id.value, "scope" -> "mine", "command" -> "Cancel")))
        val read = _record(_execute(component, _request("mark_notification_read", "id" -> inbox.entryId)))
        val readreplay = _record(_execute(component, _request("mark_notification_read", "id" -> inbox.entryId)))

        Then("the first control and read records change canonical state once, while both replays expose bounded changed=false outcomes")
        first.getBoolean("changed") shouldBe Some(true)
        replay.getBoolean("changed") shouldBe Some(false)
        replay.asMap.keySet shouldBe Set("job-id", "status", "async", "changed")
        read.getBoolean("changed") shouldBe Some(true)
        readreplay.getBoolean("changed") shouldBe Some(false)
        read.asMap.keySet shouldBe Set("id", "read-at", "changed")
      }
    }

    "P4 project installed descriptor-backed operator catalog availability" in {
      Given("the installed job_control component and an operator-capable context")
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
        val service = component.port.get[JobExperienceService].getOrElse(fail("job experience service is missing"))
        given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.ApplicationContentManager)

        When("the descriptor-derived catalog is requested")
        val catalog = service.operatorCatalog(component).toOption.getOrElse(fail("operator catalog is unavailable"))
        val categories = catalog.getAny("categories").collect {
          case values: Seq[?] => values.collect { case value: Record => value }
        }.getOrElse(Vector.empty)

        Then("installed operations are available while missing runtime categories and deferred retention remain factual")
        categories.flatMap(_.getString("name")) should contain allOf ("search", "diagnostics", "recovery", "queue", "scheduler", "health", "retention")
        categories.find(_.getString("name").contains("retention")).flatMap(_.getBoolean("available")) shouldBe Some(false)
        categories.find(_.getString("name").contains("health")).flatMap(_.getBoolean("runtime-only")) shouldBe Some(true)
        categories.filter(entry => Set("queue", "scheduler").contains(entry.getString("name").getOrElse(""))).foreach { entry =>
          entry.getBoolean("available") should not be empty
        }
      }
    }
  }

  private def _request(operation: String, arguments: (String, Any)*): Request =
    Request.of(
      component = BuiltinComponentIdentity.JOB_CONTROL.name,
      service = "job_experience",
      operation = operation,
      arguments = arguments.map { case (name, value) => Argument(name, value) }.toList
    )

  private def _execute(
    component: org.goldenport.cncf.component.Component,
    request: Request
  )(using context: ExecutionContext): Consequence[OperationResponse] =
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action => component.logic.execute(component.logic.createActionCall(action, context))
      case other => Consequence.argumentInvalid(s"unexpected operation request: ${other.getClass.getName}")
    }

  private def _record(result: Consequence[OperationResponse]): Record =
    result.toOption.collect { case OperationResponse.RecordResponse(record) => record }
      .getOrElse(fail("expected bounded record response"))

  private final class ProtocolInboxProvider extends UserNotificationInboxProvider {
    val name: String = "protocol-inbox"
    val entryId: String = "protocol-notice"
    private var _read = false
    private var _read_at: Option[java.time.Instant] = None
    private def _entry(query: UserNotificationInboxQuery) = UserNotificationInboxEntry(entryId, query.recipientUserId, None, "Done", "Job completed", java.time.Instant.EPOCH, _read_at.getOrElse(java.time.Instant.EPOCH), _read_at, None, "dedupe", Some("/web/system/jobs"))
    def notify(request: UserNotificationRequest)(using ExecutionContext): Consequence[UserNotificationResult] = Consequence.success(UserNotificationResult(notificationId = Some("protocol-notice")))
    def queryInbox(query: UserNotificationInboxQuery)(using ExecutionContext): Consequence[UserNotificationInboxPage] = Consequence.success(UserNotificationInboxPage(query.recipientUserId, query.application, query.unreadOnly, Vector(_entry(query)).filter(entry => !query.unreadOnly || entry.readAt.isEmpty), if (query.unreadOnly && _read) 0 else 1, None))
    def getInboxEntry(id: String, query: UserNotificationInboxQuery)(using ExecutionContext): Consequence[Option[UserNotificationInboxEntry]] = Consequence.success(Option.when(id == entryId)(_entry(query)))
    def markInboxRead(id: String, query: UserNotificationInboxQuery)(using context: ExecutionContext): Consequence[UserNotificationInboxReadResult] = synchronized { val changed = !_read; _read = true; if (changed) _read_at = Some(context.clock.instant()); Consequence.success(UserNotificationInboxReadResult(id, query.recipientUserId, None, _read_at, changed)) }
    def updateInboxEntry(id: String, query: UserNotificationInboxQuery, update: UserNotificationInboxUpdate)(using ExecutionContext): Consequence[UserNotificationInboxEntry] = Consequence.success(_entry(query))
  }

  private def _install_inbox_provider(subsystem: Subsystem): ProtocolInboxProvider = {
    val provider = new ProtocolInboxProvider
    val component = new Component() { override def userNotificationProviders = Vector(provider) }
    val id = ComponentId("org.goldenport.cncf.test.ProtocolInbox")
    val initialized = component.initialize(ComponentInit(subsystem, Component.Core.create(id.name, id, ComponentInstanceId.default(id), org.goldenport.protocol.Protocol.empty, jobEngine = subsystem.jobEngine), ComponentOrigin.Builtin))
      .withArtifactMetadata(Component.ArtifactMetadata("spec", "protocol-inbox", "0.0.0", Some("protocol-inbox")))
    subsystem.add(Vector(initialized))
    subsystem.withDescriptor(GenericSubsystemDescriptor(
      path = java.nio.file.Path.of("<memory>"), subsystemName = subsystem.name,
      componentBindings = Vector(GenericSubsystemComponentBinding("protocol-inbox")),
      runtime = Some(GenericSubsystemRuntimeBinding(userNotification = Some(GenericSubsystemUserNotificationBinding(providers = Vector(GenericSubsystemUserNotificationProviderBinding(
        name = "protocol-inbox", component = "protocol-inbox", enabled = Some(true), priority = Some(100), isDefault = Some(true)
      ))))))
    ))
    provider
  }
}
