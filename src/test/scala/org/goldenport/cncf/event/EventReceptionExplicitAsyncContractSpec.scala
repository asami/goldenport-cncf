package org.goldenport.cncf.event

import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind, SecurityContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.job.{ActionId, JobContext, JobControlPolicy, JobControlRequest, JobControlResponse, JobEngine, JobId, JobQueryReadModel, JobResult, JobStatus, JobSubmitOption, JobTask, JobTaskPage, JobTimelinePage, TaskId}
import org.goldenport.cncf.unitofwork.{UnitOfWorkResource, UnitOfWorkTermination}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 12, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventReceptionExplicitAsyncContractSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _e6 = afterWord("in spec:action-execution-semantics, example:E6, rules:R6,R7,R9,R10,R11,R12, phase:57.2, slice:AES-04A")

  "EventReception explicit asynchronous contract" should {
    "E6 event continuation timing" must _e6 {
      "return Failure when same-Job async enqueue admission is rejected without an action scope" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R6,R7,R9,R12; Example: E5,E6; a same-Job async reception whose enqueue admission is rejected")
        val jobengine = new RecordingAdmissionJobEngine(
          enqueueresult = Consequence.operationInvalid("job.same-job.async-task", "admission rejected"),
          submitresult = Consequence.success(JobId.generate())
        )
        val reception = _reception("same-job-rejected", EventReceptionExecutionPolicy.AsyncSameJobSameSagaNewTransaction, jobengine)
        val base = _base_context(ScopeKind.Component)
        val jobid = JobId.generate()
        given ExecutionContext = ExecutionContext.withJobContext(
          base,
          JobContext(Some(jobid), Some(TaskId.generate()), Some(ActionId.generate()))
        )

        When("the reception immediately enqueues the selected same-Job continuation")
        val result = reception.receiveAuthorized(_input("same-job-rejected"))

        Then("the enqueue admission Failure is returned rather than a successful Unit dispatch")
        result.isFaillure shouldBe true
      }

      "preserve security, saga, and Job relation for successful immediate same-Job enqueue" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R6,R7,R9,R11,R12; Example: E5,E6; an admitted same-Job async continuation")
        val acceptedjobid = JobId.generate()
        val jobengine = new RecordingAdmissionJobEngine(
          enqueueresult = Consequence.success(TaskId.generate()),
          submitresult = Consequence.success(acceptedjobid)
        )
        val reception = _reception("same-job-accepted", EventReceptionExecutionPolicy.AsyncSameJobSameSagaNewTransaction, jobengine)
        val base = _base_context(ScopeKind.Component)
        val jobid = JobId.generate()
        given ExecutionContext = ExecutionContext.withJobContext(
          base,
          JobContext(Some(jobid), Some(TaskId.generate()), Some(ActionId.generate()))
        )

        When("the reception immediately enqueues the admitted continuation")
        val result = reception.receiveAuthorized(_input("same-job-accepted"))

        Then("the successful dispatch retains the caller security, saga, and same Job context")
        result.isSuccess shouldBe true
        jobengine.enqueueContexts should have size 1
        jobengine.enqueueContexts.head.security shouldBe base.security
        jobengine.enqueueContexts.head.jobContext.jobId shouldBe Some(jobid)
        jobengine.enqueueContexts.head.observability.sagaId shouldBe Some("saga-accepted")
      }

      "surface staged new-Job submission Failure during UnitOfWork commit without transaction abort" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R7,R10,R11,R12; Example: E6,E8; an action-scoped new-Job async reception whose submission admission is rejected")
        val jobengine = new RecordingAdmissionJobEngine(
          enqueueresult = Consequence.success(TaskId.generate()),
          submitresult = Consequence.operationInvalid("job.submit", "admission rejected")
        )
        val reception = _reception("new-job-rejected", EventReceptionExecutionPolicy.AsyncNewJobSameSaga, jobengine)
        given ExecutionContext = _base_context(ScopeKind.Action)
        val unitofwork = summon[ExecutionContext].runtime.unitOfWork

        When("the action-scoped reception stages new-Job submission and the UnitOfWork commits")
        val receptionresult = reception.receiveAuthorized(_input("new-job-rejected"))
        val commitresult = unitofwork.commit()

        Then("the transaction remains committed while the staged submission Failure is observable as post-commit dispatch failure")
        receptionresult.isSuccess shouldBe true
        commitresult.isFaillure shouldBe true
        unitofwork.lastAbortResult shouldBe None
        unitofwork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      }

      "preserve security and saga context for successful staged new-Job submission" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R6,R7,R9,R11,R12; Example: E5,E6; an action-scoped admitted new-Job async reception")
        val acceptedjobid = JobId.generate()
        val jobengine = new RecordingAdmissionJobEngine(
          enqueueresult = Consequence.success(TaskId.generate()),
          submitresult = Consequence.success(acceptedjobid)
        )
        val reception = _reception("new-job-accepted", EventReceptionExecutionPolicy.AsyncNewJobSameSaga, jobengine)
        given ExecutionContext = _base_context(ScopeKind.Action)
        val unitofwork = summon[ExecutionContext].runtime.unitOfWork

        When("the action-scoped reception stages and commits accepted new-Job submission")
        val receptionresult = reception.receiveAuthorized(_input("new-job-accepted"))
        val commitresult = unitofwork.commit()

        Then("the accepted submission preserves security and same-saga metadata without a transaction abort")
        receptionresult.isSuccess shouldBe true
        commitresult.isSuccess shouldBe true
        jobengine.submitContexts should have size 1
        jobengine.submitContexts.head.security shouldBe summon[ExecutionContext].security
        jobengine.submitContexts.head.observability.sagaId shouldBe Some("saga-accepted")
        jobengine.submitOptions.head.parameters.get("saga.relation") shouldBe Some("same-saga")
        jobengine.submitOptions.head.parameters.get("reception.jobRelation") shouldBe Some("newjob")
        unitofwork.lastAbortResult shouldBe None
      }

      "surface staged same-Job enqueue Failure during UnitOfWork commit without transaction abort" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R6,R7,R10,R11,R12; Example: E8; an action-scoped same-Job async reception whose enqueue admission is rejected")
        val jobengine = new RecordingAdmissionJobEngine(
          enqueueresult = Consequence.operationInvalid("job.same-job.async-task", "admission rejected"),
          submitresult = Consequence.success(JobId.generate())
        )
        val reception = _reception("same-job-staged-rejected", EventReceptionExecutionPolicy.AsyncSameJobSameSagaNewTransaction, jobengine)
        val base = _base_context(ScopeKind.Action)
        val jobid = JobId.generate()
        given ExecutionContext = ExecutionContext.withJobContext(
          base,
          JobContext(Some(jobid), Some(TaskId.generate()), Some(ActionId.generate()))
        )
        val unitofwork = summon[ExecutionContext].runtime.unitOfWork
        val terminations = ArrayBuffer.empty[UnitOfWorkTermination]
        unitofwork.registerResourceC(new RecordingResource(terminations)).isSuccess shouldBe true

        When("the action-scoped reception stages same-Job enqueue and the UnitOfWork commits")
        val receptionresult = reception.receiveAuthorized(_input("same-job-staged-rejected"))
        val commitresult = unitofwork.commit()

        Then("the enqueue runs once after commit, preserves caller context, and reports its admission Failure under committed termination")
        receptionresult.isSuccess shouldBe true
        commitresult.isFaillure shouldBe true
        jobengine.enqueueContexts should have size 1
        jobengine.enqueueContexts.head.security shouldBe base.security
        jobengine.enqueueContexts.head.observability.sagaId shouldBe Some("saga-accepted")
        jobengine.enqueueContexts.head.jobContext.jobId shouldBe Some(jobid)
        unitofwork.lastAbortResult shouldBe None
        unitofwork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
        terminations.toVector shouldBe Vector(UnitOfWorkTermination.Committed)
        terminations.toVector should not contain UnitOfWorkTermination.Aborted
      }
    }
  }

  private def _reception(
    name: String,
    executionpolicy: EventReceptionExecutionPolicy,
    jobengine: JobEngine
  ): EventReception = {
    val eventengine = EventEngine.noop(DataStore.noop())
    val reception = EventReception.default(
      eventBus = EventBus.default(eventengine),
      dispatcher = new NoopDispatcher,
      currentSubsystemName = Some("inventory"),
      currentComponentName = Some("notice-admin"),
      jobEngine = Some(jobengine)
    )
    reception.register(CmlEventDefinition(name, CmlEventCategory.NonActionEvent, Some("published")))
    reception.registerSubscription(
      CmlSubscriptionDefinition(
        name = s"$name-subscription",
        eventName = name,
        route = DispatchRoute.Unicast,
        target = Some("targetId"),
        actionName = "notice.dispatch"
      )
    )
    reception.registerRule(
      EventReceptionRule(
        name = s"$name-rule",
        condition = EventReceptionCondition(
          originBoundary = Some(EventOriginBoundary.SameSubsystem),
          eventName = Some(name),
          eventKind = Some("published")
        ),
        policy = executionpolicy
      )
    )
    reception
  }

  private def _input(name: String): ReceptionInput =
    ReceptionInput(
      name = name,
      kind = "published",
      attributes = Map(
        "targetId" -> "notice-1",
        EventReception.StandardAttribute.SourceSubsystem -> "inventory",
        EventReception.StandardAttribute.SourceComponent -> "notice-api",
        EventReception.StandardAttribute.SagaId -> "saga-accepted",
        EventReception.StandardAttribute.operationEventTransactionRequirement -> "ignore"
      )
    )

  private def _base_context(scopekind: ScopeKind): ExecutionContext = {
    val base = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
    val scope = ScopeContext(
      kind = scopekind,
      name = "explicit-async-contract",
      parent = None,
      observabilitycontext = base.observability
    )
    base.withScope(scope)
  }

  private final class NoopDispatcher extends ActionCallDispatcher {
    def dispatchAction(actionname: String, event: DomainEvent): Consequence[Unit] = {
      val _ = actionname
      val _ = event
      Consequence.unit
    }
  }

  private final class RecordingAdmissionJobEngine(
    enqueueresult: Consequence[TaskId],
    submitresult: Consequence[JobId]
  ) extends JobEngine {
    private val _enqueue_contexts = ArrayBuffer.empty[ExecutionContext]
    private val _submit_contexts = ArrayBuffer.empty[ExecutionContext]
    private val _submit_options = ArrayBuffer.empty[JobSubmitOption]

    def enqueueContexts: Vector[ExecutionContext] = _enqueue_contexts.toVector
    def submitContexts: Vector[ExecutionContext] = _submit_contexts.toVector
    def submitOptions: Vector[JobSubmitOption] = _submit_options.toVector

    def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] =
      submit(tasks, ctx, JobSubmitOption())

    def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): Consequence[JobId] = {
      val _ = tasks
      _submit_contexts += ctx
      _submit_options += option
      submitresult
    }

    def getStatus(jobId: JobId): Option[JobStatus] = None
    def getResult(jobId: JobId): Option[JobResult] = None
    def awaitResult(jobId: JobId, timeoutMillis: Long): Consequence[JobResult] =
      Consequence.operationInvalid("job.await", "not exercised by explicit async reception contract")
    def control(
      jobId: JobId,
      request: JobControlRequest,
      policy: JobControlPolicy
    )(using ExecutionContext): Consequence[JobControlResponse] =
      Consequence.operationInvalid("job.control", "not exercised by explicit async reception contract")
    def query(jobId: JobId): Option[JobQueryReadModel] = None
    def queryTasks(jobId: JobId, offset: Int, limit: Int): Option[JobTaskPage] = None
    def queryTimeline(jobId: JobId, offset: Int, limit: Int): Option[JobTimelinePage] = None

    override def enqueueTaskInJob(jobId: JobId, task: JobTask, ctx: ExecutionContext): Consequence[TaskId] = {
      val _ = jobId
      val _ = task
      _enqueue_contexts += ctx
      enqueueresult
    }
  }

  private final class RecordingResource(
    terminations: ArrayBuffer[UnitOfWorkTermination]
  ) extends UnitOfWorkResource {
    def releaseC(termination: UnitOfWorkTermination): Consequence[Unit] = {
      terminations += termination
      Consequence.unit
    }
  }
}
