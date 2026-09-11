package org.goldenport.cncf.component.builtin.jobcontrol

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.job.{
  ActionId,
  JobEngineTestFixture,
  JobPersistencePolicy,
  JobSubmitOption,
  JobTask,
  TaskFailed,
  TaskOutcome,
  TaskSucceeded
}
import org.goldenport.cncf.job.*
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.openapi.OpenApiProjector
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Record, RecordFormat}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.circe.parser.parse

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobManagementProtocolSpec
    extends AnyWordSpec with Matchers with GivenWhenThen with JobEngineTestFixture {
  private val _e1 = afterWord(
    "in spec:durable-job-management-protocol-contract, example:E1, rules:R1,R2,R3,R4, phase:69.2"
  )

  "E1 Job management protocol" must _e1 {
    "expose only the additive management discovery names and explicit generated methods" in {
      Given("the default subsystem and its job_control protocol")
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
      val jobcontrol = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).get
      val joboperations = jobcontrol.protocol.services.services.find(_.name == "job").get
        .operations.operations.toVector.map(_.name).toSet
      val adminoperations = jobcontrol.protocol.services.services.find(_.name == "job_admin").get
        .operations.operations.toVector.map(_.name).toSet

      When("protocol Help/meta and OpenAPI derive their operation discovery from the component")
      val openapi = parse(OpenApiProjector.forSubsystem(subsystem)).fold(
        error => fail(error.getMessage),
        identity
      ).hcursor.downField("paths")

      Then("all additive reads and exactly the four controls have their generated routes")
      val reads = Set(
        "list_management_jobs",
        "get_management_job_detail",
        "get_management_job_result",
        "list_management_job_tasks",
        "list_management_job_timeline",
        "get_management_job_task_execution_tree",
        "get_management_job_task_detail"
      )
      joboperations should contain allElementsOf reads
      adminoperations should contain allElementsOf Set("cancel_job", "retry_job", "suspend_job", "resume_job")
      reads.foreach { operation =>
        openapi.downField(_path("job", operation)).downField("GET").focus should not be empty
      }
      Set("cancel_job", "retry_job", "suspend_job", "resume_job").foreach { operation =>
        openapi.downField(_path("job_admin", operation)).downField("POST").focus should not be empty
      }

      And("an unmarked legacy operation retains its inferred method")
      openapi.downField(_path("job", "get_job_status")).downField("GET").focus should not be empty
      }
    }

    "return bounded management records through resolver-addressable operations" in {
      Given("a persistent managed job with a sensitive legacy request summary and an ordinary test context")
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        given ExecutionContext = ExecutionContext.test()
        val admin = subsystem.findComponent(BuiltinComponentIdentity.ADMIN).get
        val jobid = admin.logic.submitJob(
          List(ImmediateTask(ActionId.generate())),
          ExecutionContext.create(),
          JobSubmitOption(
            persistence = JobPersistencePolicy.Persistent,
            requestSummary = Some("sensitive-request-summary")
          )
        ).toOption.get

        When("the list and detail operation names are resolved through job_control")
        val list = _record(subsystem, _request("job", "list_management_jobs"))
        val detail = _record(subsystem, _request(
          "job", "get_management_job_detail", "id" -> jobid.value
        ))

        Then("the returned summaries retain only their bounded vocabulary")
        list.asMap.keySet shouldBe Set("entries", "total-count", "next-cursor")
        detail.asMap.keySet shouldBe Set("summary", "retry", "result-summary", "task-count", "timeline-count")
        val summary = detail.getRecord("summary").getOrElse(fail("summary is missing"))
        summary.asMap.keySet shouldBe Set(
          "job-id", "status", "persistence", "origin", "created-at", "updated-at", "scheduled-start-at"
        )
        summary.asMap.keySet should not contain "submitter-principal-id"
        summary.asMap.keySet should not contain "debug-request-summary"
        summary.asMap.keySet should not contain "calltree"
      }
    }

    "return all closed result availability records and preserve retry changed replay semantics" in {
      Given("one completed job, one failed persistent job whose retry fixture holds its retried execution for the replay request, closed pending/restart result fixtures, and an ApplicationContentManager test context for the retry protocol")
        SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val admin = subsystem.findComponent(BuiltinComponentIdentity.ADMIN).get
        val jobcontrol = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).get
        val service = jobcontrol.port.get[JobControlComponent.JobService].get
        val retriedstarted = new CountDownLatch(1)
        val retryrelease = new CountDownLatch(1)
        val completed = admin.logic.submitJob(
          List(ImmediateTask(ActionId.generate())), ExecutionContext.create(),
          JobSubmitOption(persistence = JobPersistencePolicy.Persistent)
        ).toOption.get
        val failed = admin.logic.submitJob(
          List(RetryBlockingTask(ActionId.generate(), retriedstarted, retryrelease)), ExecutionContext.create(),
          JobSubmitOption(persistence = JobPersistencePolicy.Persistent)
        ).toOption.get
        given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
        awaitCondition(
          service.getJobStatus(completed).toOption.exists(_.status.toString == "Succeeded") &&
            service.getJobStatus(failed).toOption.exists(_.status.toString == "Failed"),
          timeoutMillis = 3000L
        ) shouldBe true

        val pendingid = JobId.generate()
        val unavailableid = JobId.generate()
        jobcontrol.withPort(Component.Port.of(new ManagementResultOverride(
          service,
          Map(
            pendingid -> JobManagementResult.Pending(
              JobResultSummary(JobStatus.Running, success = false, message = None)
            ),
            unavailableid -> JobManagementResult.UnavailableAfterRestart(
              JobResultSummary(JobStatus.Succeeded, success = true, message = Some("retained"))
            )
          )
        )).orElse(jobcontrol.port))

        When("available, pending, and fresh-engine retained result states cross job_control")
        val available = _record(subsystem, _request(
          "job", "get_management_job_result", "id" -> completed.value
        ))
        val pending = _record(subsystem, _request(
          "job", "get_management_job_result", "id" -> pendingid.value
        ))
        val unavailable = _record(subsystem, _request(
          "job", "get_management_job_result", "id" -> unavailableid.value
        ))
        val applied = _record(subsystem, _request(
          "job_admin", "retry_job", "id" -> failed.value
        ))
        try {
          And("the applied retry reaches its held retried execution before the duplicate request")
          retriedstarted.await(3, TimeUnit.SECONDS) shouldBe true
          val replay = _record(subsystem, _request(
            "job_admin", "retry_job", "id" -> failed.value
          ))

          Then("the result vocabulary is closed and retry reports apply then replay")
          available.getString("availability") shouldBe Some("available")
          available.getRecord("result-summary") should not be empty
          available.getString("result-value") should not be empty
          pending.getString("availability") shouldBe Some("pending")
          pending.getRecord("result-summary") should not be empty
          pending.asMap.keySet should not contain "result-value"
          unavailable.getString("availability") shouldBe Some("unavailable-after-restart")
          unavailable.getRecord("result-summary") should not be empty
          unavailable.asMap.keySet should not contain "result-value"
          applied.getBoolean("changed") shouldBe Some(true)
          replay.getBoolean("changed") shouldBe Some(false)
        } finally {
          retryrelease.countDown()
        }
      }
    }
  }

  private def _path(service: String, operation: String): String =
    s"/rest/v1${NamingConventions.toNormalizedPath(BuiltinComponentIdentity.JOB_CONTROL.name, service, operation)}"

  private def _request(service: String, operation: String, arguments: (String, Any)*): Request =
    Request.of(
      component = BuiltinComponentIdentity.JOB_CONTROL.name,
      service = service,
      operation = operation,
      arguments = arguments.map { case (name, value) => Argument(name, value) }.toList
    )

  private def _record(subsystem: Subsystem, request: Request)(using executioncontext: ExecutionContext): Record = {
    val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).get
    val result = component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        component.logic.execute(component.logic.createActionCall(action, executioncontext))
      case other =>
        Consequence.argumentInvalid(s"unexpected OperationRequest type: ${other.getClass.getName}")
    }
    result.toOption.collect { case OperationResponse.RecordResponse(record) => record }.getOrElse(
      fail(s"expected Record response but got $result")
    )
  }

  private final case class ImmediateTask(actionId: ActionId) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskSucceeded(OperationResponse.Scalar("ok"))
    }
  }

  private final case class RetryBlockingTask(
    actionId: ActionId,
    retriedstarted: CountDownLatch,
    retryrelease: CountDownLatch
  ) extends JobTask {
    private val _attempts = new AtomicInteger(0)

    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      if (_attempts.incrementAndGet() == 1)
        TaskFailed(Conclusion.simple("expected failure"))
      else {
        retriedstarted.countDown()
        retryrelease.await(30, TimeUnit.SECONDS)
        TaskFailed(Conclusion.simple("expected failure"))
      }
    }
  }

  private final class ManagementResultOverride(
    delegate: JobControlComponent.JobService,
    results: Map[JobId, JobManagementResult]
  ) extends JobControlComponent.JobService {
    def listManagementJobs(request: JobManagementQuery)(using ExecutionContext): Consequence[JobManagementPage] =
      delegate.listManagementJobs(request)
    def getManagementJobDetail(jobId: JobId)(using ExecutionContext): Consequence[JobManagementDetail] =
      delegate.getManagementJobDetail(jobId)
    def getManagementJobResult(jobId: JobId)(using ExecutionContext): Consequence[JobManagementResult] =
      results.get(jobId).map(Consequence.success).getOrElse(delegate.getManagementJobResult(jobId))
    def listManagementJobTasks(jobId: JobId, offset: Int, limit: Int)(using ExecutionContext): Consequence[JobTaskPage] =
      delegate.listManagementJobTasks(jobId, offset, limit)
    def listManagementJobTimeline(jobId: JobId, offset: Int, limit: Int)(using ExecutionContext): Consequence[JobTimelinePage] =
      delegate.listManagementJobTimeline(jobId, offset, limit)
    def getManagementJobTaskExecutionTree(jobId: JobId)(using ExecutionContext): Consequence[JobTraceTree] =
      delegate.getManagementJobTaskExecutionTree(jobId)
    def getManagementJobTaskDetail(jobId: JobId, taskId: TaskId)(using ExecutionContext): Consequence[JobTaskDetail] =
      delegate.getManagementJobTaskDetail(jobId, taskId)
    def getJobStatus(jobId: JobId)(using ExecutionContext): Consequence[JobQueryReadModel] =
      delegate.getJobStatus(jobId)
    def loadJobHistory(jobId: JobId)(using ExecutionContext): Consequence[JobTimelinePage] =
      delegate.loadJobHistory(jobId)
    def getJobCalltree(jobId: JobId)(using ExecutionContext): Consequence[Record] =
      delegate.getJobCalltree(jobId)
    def getTaskExecutionTree(jobId: JobId)(using ExecutionContext): Consequence[JobTraceTree] =
      delegate.getTaskExecutionTree(jobId)
    def getTaskDetail(jobId: JobId, taskId: TaskId)(using ExecutionContext): Consequence[JobTaskDetail] =
      delegate.getTaskDetail(jobId, taskId)
    def getJobResult(jobId: JobId)(using ExecutionContext): Consequence[JobResult] =
      delegate.getJobResult(jobId)
    def awaitJobResult(jobId: JobId)(using ExecutionContext): Consequence[OperationResponse] =
      delegate.awaitJobResult(jobId)
    def describeJobDefinition(body: String, format: RecordFormat): Consequence[JobBatchDefinition] =
      delegate.describeJobDefinition(body, format)
    def submitJobDefinition(body: String, format: RecordFormat)(using ExecutionContext): Consequence[JobBatchSubmissionResult] =
      delegate.submitJobDefinition(body, format)
    def submitJobBatch(body: String, format: RecordFormat)(using ExecutionContext): Consequence[JobBatchSubmissionResult] =
      delegate.submitJobBatch(body, format)
    def compareJobProfile(jobId: JobId)(using ExecutionContext): Consequence[Record] =
      delegate.compareJobProfile(jobId)
    def reconstructJobProfile(jobId: JobId)(using ExecutionContext): Consequence[Record] =
      delegate.reconstructJobProfile(jobId)
    def createJobDefinition(key: String, body: String, format: RecordFormat, status: Option[String])(using ExecutionContext): Consequence[Record] =
      delegate.createJobDefinition(key, body, format, status)
    def updateJobDefinition(key: String, body: String, format: RecordFormat, status: Option[String])(using ExecutionContext): Consequence[Record] =
      delegate.updateJobDefinition(key, body, format, status)
    def activateJobDefinition(key: String)(using ExecutionContext): Consequence[Record] =
      delegate.activateJobDefinition(key)
    def retireJobDefinition(key: String)(using ExecutionContext): Consequence[Record] =
      delegate.retireJobDefinition(key)
    def getJobDefinition(key: String)(using ExecutionContext): Consequence[Record] =
      delegate.getJobDefinition(key)
    def searchJobDefinitions()(using ExecutionContext): Consequence[Record] =
      delegate.searchJobDefinitions()
  }
}
