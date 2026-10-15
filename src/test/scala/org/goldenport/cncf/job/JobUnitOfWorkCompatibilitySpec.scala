package org.goldenport.cncf.job

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkTermination}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobDurableRuntimeFixtures.{failureConclusion, success}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobUnitOfWorkCompatibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Native Job submission" should {
    "stage an unpublished reservation" which {
      "JC01 publishes the issued identity only after commit and executes it once on drain" in {
        Given("a Ready Subsystem-owned SQLite/Entity/Blob runtime and an open UnitOfWork")
        JobDurableRuntimeFixtures.withFixture { native =>
          success(native.start())
          val unitofwork = new UnitOfWork(native.context)
          val task = new ObservationTask
          val option = JobSubmitOption(priority = 7, requestSummary = Some("staged review"),
            parameters = Map("review" -> "selected"), executionNotes = Vector("native staging"))
          val issued = native.ids.calls.get()
          try {
            When("Persistent Async work is staged in that UnitOfWork")
            val jobid = success(native.engine.stageSubmit(List(task), unitofwork, option))

            Then("one genuine ID reserves capacity without publishing a record, evidence or queued body")
            native.ids.calls.get() shouldBe issued + 1
            native.engine.getStatus(jobid) shouldBe None
            native.engine.query(jobid) shouldBe None
            success(native.owner._execute_c {
              native.owner._store.load(jobid.value, JobDurableRuntimeFixtures.access)(using native.context)
            }) shouldBe None
            native.engine.resourceSnapshot.retained shouldBe 1
            native.engine.resourceSnapshot.pending shouldBe 1
            native.evidence.requests shouldBe empty
            native.engine.drainAll() shouldBe 0
            task.runs shouldBe 0

            When("the outer UnitOfWork commits")
            val committed = unitofwork.commit()

            Then("the same native ID and metadata are published once and one body waits for manual drain")
            committed.isSuccess shouldBe true
            unitofwork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
            native.engine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
            val model = native.engine.query(jobid).get
            model.jobId shouldBe jobid
            model.debug.requestSummary shouldBe option.requestSummary
            model.debug.parameters shouldBe option.parameters
            model.debug.executionNotes shouldBe option.executionNotes
            native.engine._get_record(jobid).get.priority shouldBe option.priority
            native.signed(jobid).record.body.identity.jobId shouldBe jobid.value
            native.evidence.requests.map(_.jobid) shouldBe Vector(jobid)
            native.evidence.requests.map(_.request.boundary) shouldBe Vector(DurableJobLifecycleWriteBoundary.Admission)
            task.runs shouldBe 0

            When("the original engine manually drains its queued work")
            val drained = native.engine.drainAll()

            Then("the native body succeeds exactly once and leaves no pending work")
            drained shouldBe 1
            task.runs shouldBe 1
            native.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
            native.signed(jobid).record.body.identity.jobId shouldBe jobid.value
            native.engine.resourceSnapshot.pending shouldBe 0
            native.engine.drainAll() shouldBe 0
          } finally success(unitofwork.dispose())
        }
      }

      "JC02 releases an unpublished reservation on normal Abort or Dispose" in {
        Vector("Abort", "Dispose").foreach { ending =>
          Given(s"a fresh Ready native fixture and an open UnitOfWork for $ending")
          JobDurableRuntimeFixtures.withFixture { native =>
            success(native.start())
            val unitofwork = new UnitOfWork(native.context)
            val task = new ObservationTask
            try {
              When("one native Job is staged without committing")
              val jobid = success(native.engine.stageSubmit(List(task), unitofwork, JobSubmitOption()))

              Then("its retained and pending capacity are reserved while the Job is absent")
              native.engine.resourceSnapshot.retained shouldBe 1
              native.engine.resourceSnapshot.pending shouldBe 1
              native.engine.query(jobid) shouldBe None

              When(s"the UnitOfWork ends normally through $ending")
              if (ending == "Abort") success(unitofwork.abort()) else success(unitofwork.dispose())

              Then("the unpublished capacity is released and no Job or body becomes visible")
              native.engine.resourceSnapshot.retained shouldBe 0
              native.engine.resourceSnapshot.pending shouldBe 0
              native.engine.getStatus(jobid) shouldBe None
              native.engine.query(jobid) shouldBe None
              native.evidence.requests shouldBe empty
              native.engine.drainAll() shouldBe 0
              task.runs shouldBe 0
            } finally if (ending == "Abort") success(unitofwork.dispose())
          }
        }
      }

      "JC03 refuses Sync, nonPersistent options and an owner that has not reached Ready" in {
        Given("a Ready native owner and options outside Persistent Async staging")
        JobDurableRuntimeFixtures.withFixture { native =>
          success(native.start())
          val unitofwork = new UnitOfWork(native.context)
          val task = new ObservationTask
          try {
            Vector(JobSubmitOption(runMode = JobRunMode.Sync),
              JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)).foreach { option =>
              val before = (native.ids.calls.get(), native.engine.resourceSnapshot, native.evidence.requests)
              When(s"staging is requested with ${option.persistence} ${option.runMode}")
              val refused = native.engine.stageSubmit(List(task), unitofwork, option)

              Then("a structured option refusal precedes ID issuance, native evidence and dispatch")
              failureConclusion(refused).observation.getEffectiveMessage shouldBe
                Some("stageSubmit requires Persistent Async options")
              (native.ids.calls.get(), native.engine.resourceSnapshot, native.evidence.requests) shouldBe before
              native.engine.listJobs(persistentOnly = false) shouldBe empty
              native.engine.drainAll() shouldBe 0
              task.runs shouldBe 0
            }
          } finally success(unitofwork.dispose())
        }

        Given("a separate actual configured fixture whose owner has not been started")
        JobDurableRuntimeFixtures.withFixture { native =>
          val unitofwork = new UnitOfWork(native.context)
          val task = new ObservationTask
          val before = (native.ids.calls.get(), native.engine.resourceSnapshot, native.evidence.requests)
          try {
            When("Persistent Async staging is requested before native Ready admission")
            val refused = native.engine.stageSubmit(List(task), unitofwork, JobSubmitOption())

            Then("a structured runtime refusal leaves the ID, resources, evidence and body untouched")
            failureConclusion(refused).observation.getEffectiveMessage shouldBe Some("durable runtime startup refused")
            (native.ids.calls.get(), native.engine.resourceSnapshot, native.evidence.requests) shouldBe before
            native.engine.listJobs(persistentOnly = false) shouldBe empty
            native.engine.drainAll() shouldBe 0
            task.runs shouldBe 0
          } finally success(unitofwork.dispose())
        }
      }
    }

    "retain ordinary publication behavior" which {
      "JC04 preserves Sync, immediate Async and future Async bodies and metadata" in {
        Given("the existing Ready owner, original engine and three ordinary submission modes")
        JobDurableRuntimeFixtures.withFixture { native =>
          success(native.start())
          val sync = new ObservationTask
          val immediate = new ObservationTask
          val delayed = new ObservationTask
          val due = native.engine._now().plusSeconds(10)
          val option = JobSubmitOption(priority = 9, requestSummary = Some("ordinary native work"),
            parameters = Map("source" -> "ordinary"))

          When("ordinary Sync work is submitted under the native owner's lease")
          val syncid = success(native.owner._execute_c {
            native.engine.submit(List(sync), native.context, option.copy(runMode = JobRunMode.Sync))
          })

          Then("the Sync body completes inline with its exact published native ID")
          sync.runs shouldBe 1
          native.engine.getStatus(syncid) shouldBe Some(JobStatus.Succeeded)
          native.signed(syncid).record.body.identity.jobId shouldBe syncid.value

          When("ordinary immediate and future Async work are submitted")
          val immediateid = success(native.owner._execute_c {
            native.engine.submit(List(immediate), native.context, option)
          })
          val delayedid = success(native.owner._execute_c {
            native.engine.submit(List(delayed), native.context, option.copy(scheduledStartAt = Some(due)))
          })

          Then("both bodies wait, retain their metadata, and the future Job retains its due time")
          immediate.runs shouldBe 0
          delayed.runs shouldBe 0
          Vector(syncid, immediateid, delayedid).distinct.size shouldBe 3
          Vector(syncid, immediateid, delayedid).foreach { jobid =>
            val model = native.engine.query(jobid).get
            model.jobId shouldBe jobid
            model.debug.requestSummary shouldBe option.requestSummary
            model.debug.parameters shouldBe option.parameters
            native.engine._get_record(jobid).get.priority shouldBe option.priority
            native.signed(jobid).record.body.identity.jobId shouldBe jobid.value
          }
          native.engine.query(delayedid).get.scheduledStartAt shouldBe Some(due)

          When("the original manual queue is drained before the future due time")
          val drained = native.engine.drainAll()

          Then("only the immediate Async body runs")
          drained shouldBe 1
          immediate.runs shouldBe 1
          delayed.runs shouldBe 0
          native.engine.getStatus(immediateid) shouldBe Some(JobStatus.Succeeded)
          native.engine.getStatus(delayedid) shouldBe Some(JobStatus.Submitted)

          When("the existing fixture advances its manual scheduler to the future due time")
          native.advance(Duration.ofSeconds(10))
          native.engine.drainAll()

          Then("each ordinary body has run once with its exact native ID and no pending work remains")
          Vector(sync.runs, immediate.runs, delayed.runs) shouldBe Vector(1, 1, 1)
          Vector(syncid, immediateid, delayedid).foreach { jobid =>
            native.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
            native.signed(jobid).record.body.identity.jobId shouldBe jobid.value
          }
          native.engine.resourceSnapshot.pending shouldBe 0
          native.engine.drainAll() shouldBe 0
        }
      }
    }
  }

  private final class ObservationTask extends JobTask {
    private val _runs = new AtomicInteger
    override val actionId: ActionId =
      ActionId("cncf", "compatibility", Some(JobDurableRuntimeFixtures.instant), Some("observe"))
    def runs: Int = _runs.get()
    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def relation: Option[String] = Some("root")
    override def transactionRole: Option[String] = Some("own")
    override def transactionScope: Option[String] = Some("per-task")
    override def componentName: Option[String] = Some("component-a")
    override def serviceName: Option[String] = Some("service-a")
    override def operationName: Option[String] = Some("run")
    override def run(ctx: ExecutionContext): TaskOutcome = {
      _runs.incrementAndGet()
      TaskSucceeded(OperationResponse.Scalar("ok"))
    }
  }
}
