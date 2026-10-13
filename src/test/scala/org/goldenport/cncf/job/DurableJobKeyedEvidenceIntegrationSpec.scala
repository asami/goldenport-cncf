package org.goldenport.cncf.job

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import org.goldenport.Consequence
import org.goldenport.cncf.event.EventStore
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Actual signed native addressing across overlap, reopen and boundary refusal.
 * Fresh SQLite ownership is in-process evidence; process/CBD acceptance remains open.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobKeyedEvidenceIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DurableJobKeyedEvidenceFixtures.*
  import JobDurableLifetimeRecoveryFixtures.{Owner, Task, publish, record, success}

  private def _scenario(id: String, rules: String) = afterWord(
    s"in spec:durable-job-keyed-evidence, example:$id, rules:$rules, phase:69.7, slice:JM69-09D1A")

  "Native keyed lifecycle evidence" should {
    "independent acknowledged Job histories" which {
      "KI1 address overlapping Jobs independently of invocation order" must _scenario("KI1", "R6,R7,R8,R9") {
        "finish the second Job while the first actual body is still open" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KI1; R6,R7,R8,R9; one real manual SQLite engine, two roots and bounded body barriers")
          withDatabase { database =>
            val owner = database.open()
            val supplied = recipe()
            val source = new Source(_ => supplied)
            bind(owner, source)
            val firsttask = new Task
            val secondtask = new Task
            val entered = new CountDownLatch(1)
            val release = new CountDownLatch(1)
            val drained = new AtomicBoolean(false)
            val failure = new AtomicReference[Throwable]()
            val firstid = success(owner.engine.submit(List(firsttask), owner.context, JobSubmitOption()))
            val secondid = success(owner.engine.submit(List(secondtask), owner.context, JobSubmitOption()))
            firsttask.body = ctx => {
              ctx.jobContext.jobId shouldBe Some(firstid)
              owner.signed(firstid).record.body.identity.revision shouldBe 4L
              owner.native(firstid).seed.remaining shouldBe 0L
              entered.countDown()
              await(release)
              TaskSucceeded(OperationResponse.Scalar("ok"))
            }
            val actor = new Thread(() => {
              try drained.set(owner.engine.drainOne())
              catch { case error: Throwable => failure.set(error) }
            }, "keyed-evidence-first-body")
            try {
              When("the first real task blocks after its signed start and the second Job drains")
              actor.start()
              await(entered)
              owner.engine.drainOne() shouldBe true
              Then("the second signs revision six while the first remains at acknowledged revision four")
              firstid.value should not be secondid.value
              actor.isAlive shouldBe true
              firsttask.runs.get() shouldBe 1
              firsttask.canonical.get() shouldBe 0
              owner.signed(firstid).record.body.identity.revision shouldBe 4L
              owner.signed(firstid).record.body.result shouldBe DurableResultOutcome.Pending
              owner.engine.getResult(firstid) shouldBe None
              _assert_completed(owner, source, secondid, secondtask, supplied)
              source.requests.map(_.jobid.value) shouldBe Vector(firstid.value, secondid.value) ++
                Vector.fill(3)(firstid.value) ++ Vector.fill(5)(secondid.value)
            } finally {
              release.countDown()
              if (actor.getState != Thread.State.NEW) join(actor)
            }
            When("the first body is released and its owned thread joins")
            Then("both real bodies and canonical observations occur once with separate complete histories")
            failure.get() shouldBe null
            drained.get() shouldBe true
            _assert_completed(owner, source, firstid, firsttask, supplied)
            _assert_completed(owner, source, secondid, secondtask, supplied)
            source.requests.map(_.jobid.value).takeRight(2) shouldBe Vector.fill(2)(firstid.value)
            owner.engine.drainAll() shouldBe 0
            owner.engine.durableLifecycleWriteFailureFacts shouldBe empty
          }
        }
      }

      "KI2 continue from the actual reopened signed revision" must _scenario("KI2", "R6,R7,R8,R9") {
        "request three through seven without Admission or reconstructed payment" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KI2; R6,R7,R8,R9; genuine reserve/create/publish and an ordinary second Submitted/Pending checkpoint")
          withDatabase { database =>
            val first = database.open()
            val jobid = JobInputBoundsFixtures.id("keyed_recovered_root")
            val root = publish(first.store, record(jobid, rows = 1), first.policy)(using first.context)
            val body = root.record.body
            val updated = success(DurableJobRecord.createV2(body.copy(identity = body.identity.copy(
              revision = 2L, updatedAt = body.identity.updatedAt.plusMillis(1)))))
            val original = success(first.store.checkpoint(root, updated, JobInputBoundsFixtures.access)(using first.context))
            val originalnative = first.native(jobid)
            val originalentry = ledger(first).get.entries.find(_.jobid == jobid.value).get
            database.closeBeforeReopen(first)
            val owner = database.open()
            val supplied = originalRecipe(original.record)
            val source = new Source(id => {
              id.value shouldBe jobid.value
              supplied
            })
            bind(owner, source)
            val task = new Task
            When("a fresh SQLite provider, context and State use the unchanged candidate/replay/Port recovery path")
            val report = success(recover(owner, jobid, List(task)))
            Then("registration retains exact paid authority and emits no evidence invocation")
            report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Registered)
            owner.provider should not be theSameInstanceAs(first.provider)
            owner.context should not be theSameInstanceAs(first.context)
            owner.engine.runtimeState should not be theSameInstanceAs(first.engine.runtimeState)
            owner.native(jobid) shouldBe originalnative
            ledger(owner).get.entries.find(_.jobid == jobid.value).get shouldBe originalentry
            source.requests shouldBe empty
            owner.ids.calls.get() shouldBe 0
            owner.engine.drainAll() shouldBe 0
            When("the actual due timer advances ten seconds and the normal JobRun drains")
            owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
            owner.engine.drainAll() shouldBe 1
            Then("five acknowledged keyed invocations preserve the original signed history and protected body")
            source.requests.map(_.jobid.value) shouldBe Vector.fill(5)(jobid.value)
            source.requests.map(_.expectedrevision) shouldBe (3L to 7L).toVector
            source.requests.map(_.request.boundary) shouldBe boundaries.tail
            owner.entity.jobacks.get() shouldBe 5
            task.runs.get() shouldBe 1
            task.canonical.get() shouldBe 1
            owner.engine.getResult(jobid) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
            val signed = owner.signed(jobid).record.body
            signed.identity.revision shouldBe 7L
            signed.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
            signed.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
            signed.lifecycle.retry.maxAttempts shouldBe body.lifecycle.retry.maxAttempts
            signed.lifecycle.retry.attempts.size shouldBe 1
            signed.identity.createdAt shouldBe body.identity.createdAt
            signed.identity.updatedAt.isAfter(original.record.body.identity.updatedAt) shouldBe true
            protectedFacts(signed) shouldBe protectedFacts(original.record.body)
            signed.timeline.take(original.record.body.timeline.size) shouldBe original.record.body.timeline
            signed.timeline.drop(original.record.body.timeline.size).map(_.kind) shouldBe Vector(
              "job.durable-start-intent", "job.durable-running-intent", "task.durable-start-intent",
              "task.durable-outcome-checkpoint", "job.durable-terminal-outcome-checkpoint")
            signed.timeline.exists(_.kind == "job.runtime-rehydrated") shouldBe false
            val native = owner.native(jobid)
            native.seed.paid shouldBe originalnative.seed.paid
            native.seed.remaining shouldBe 0L
            native.seed.normalids shouldBe owner.engine._get_record(jobid).get.taskReadModels.map(_.taskId.value).toSet
            native.seed.claims shouldBe empty
            val entry = ledger(owner).get.entries.find(_.jobid == jobid.value).get
            (entry.rootstate, entry.roottoken, entry.rootsha256, entry.ownersha256) shouldBe
              (originalentry.rootstate, originalentry.roottoken, originalentry.rootsha256, originalentry.ownersha256)
            owner.engine.durableLifecycleWriteFailureFacts shouldBe empty
          }
        }
      }
    }

    "native refusal and finite keyed permutations" which {
      "KI3 preserve only acknowledgements before every addressed fault" must _scenario("KI3", "R5,R6,R7,R8") {
        "refuse foreign headers, wrong projection revisions and missing pairs at all six boundaries" in {
          for (boundary <- boundaries; fault <- Vector(Fault.WrongJob, Fault.WrongRevision, Fault.WrongProjection, Fault.MissingPair)) {
            Given(s"docs/spec/durable-job-keyed-evidence-contract.md; KI3; R5,R6,R7,R8; native $boundary with independently selected $fault")
            withDatabase { database =>
              val owner = database.open()
              val supplied = recipe()
              val task = new Task
              var previous = Option.empty[DurableJobStoreSnapshot]
              var paid = Option.empty[DurableJobLifetimeQuotaSnapshot]
              var entry = Option.empty[DurableJobLifetimeQuotaEntry]
              val source = new Source(_ => supplied,
                key => if (key.request.boundary == boundary) fault else Fault.Matching,
                key => if (key.request.boundary == boundary) {
                  previous = success(owner.store.load(key.jobid.value, supplied.access)(using owner.context))
                  if (boundary != DurableJobLifecycleWriteBoundary.Admission) {
                    paid = Some(owner.native(key.jobid))
                    entry = ledger(owner).flatMap(_.entries.find(_.jobid == key.jobid.value))
                  }
                })
              bind(owner, source)
              When("actual native admission and normal dispatch reach the selected fault once")
              val admitted = owner.engine.submit(List(task), owner.context, JobSubmitOption())
              owner.engine.drainAll() shouldBe (if (boundary == DurableJobLifecycleWriteBoundary.Admission) 0 else 1)
              Then("only the exact prior signed acknowledgement and native paid authority remain")
              val index = boundaries.indexOf(boundary)
              source.requests.map(_.request.boundary) shouldBe boundaries.take(index + 1)
              source.requests.map(_.expectedrevision) shouldBe (1L to index.toLong + 1L).toVector
              val jobid = source.requests.head.jobid
              source.requests.map(_.jobid.value).distinct shouldBe Vector(jobid.value)
              success(owner.store.load(jobid.value, supplied.access)(using owner.context)) shouldBe previous
              owner.entity.jobacks.get() shouldBe math.max(0, index - 1)
              task.runs.get() shouldBe (if (index >= 4) 1 else 0)
              task.canonical.get() shouldBe 0
              owner.engine.durableLifecycleWriteFailureFacts shouldBe Vector(DurableJobLifecycleWriteFailureFact(jobid,
                boundary match {
                  case DurableJobLifecycleWriteBoundary.Admission => DurableJobLifecycleWriteFailure.AdmissionRefused
                  case DurableJobLifecycleWriteBoundary.StartIntent => DurableJobLifecycleWriteFailure.StartIntentRefused
                  case DurableJobLifecycleWriteBoundary.RunningIntent => DurableJobLifecycleWriteFailure.RunningIntentRefused
                  case DurableJobLifecycleWriteBoundary.TaskStartIntent => DurableJobLifecycleWriteFailure.TaskStartIntentRefused
                  case DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint => DurableJobLifecycleWriteFailure.TaskOutcomeCheckpointRefused
                  case DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint => DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused
                }))
              if (index == 0) {
                admitted.isSuccess shouldBe false
                ledger(owner) shouldBe None
                owner.engine._get_record(jobid) shouldBe None
                val message = _failure_conclusion(admitted).observation.getEffectiveMessage
                message shouldBe Consequence.argumentInvalid[JobId](fault match {
                  case Fault.WrongProjection => "durable Admission evidence must use semantic revision 1"
                  case Fault.MissingPair => "durable lifecycle write evidence is missing"
                  case _ => "durable keyed lifecycle evidence refused"
                }).conclusion.observation.getEffectiveMessage
                _failure_conclusion(admitted).show should not include (jobid.value)
                _failure_conclusion(admitted).show should not include (JobInputBoundsFixtures.id("foreign-response").value)
              } else {
                admitted.isSuccess shouldBe true
                previous.get.record.body.identity.revision shouldBe index.toLong
                previous.get.record.body.result shouldBe DurableResultOutcome.Pending
                previous.get.record.body.lifecycle.status shouldBe
                  (if (index <= 2) DurableJobLifecycleStatus.Submitted else DurableJobLifecycleStatus.Running)
                owner.native(jobid) shouldBe paid.get
                paid.get.seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
                paid.get.seed.remaining shouldBe (if (index < 3) 1L else 0L)
                paid.get.seed.normalids.size shouldBe (if (index < 3) 0 else 1)
                paid.get.seed.claims shouldBe empty
                entry.get.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
                ledger(owner).get.entries.find(_.jobid == jobid.value) shouldBe entry
                val local = owner.engine._get_record(jobid).get
                local.status shouldBe (if (index <= 2) JobStatus.Submitted else if (index == 5) JobStatus.Succeeded else JobStatus.Running)
                local.taskReadModels.map(_.status) shouldBe (if (index < 3) Vector.empty
                  else Vector(if (index == 3) JobTaskStatus.Running else JobTaskStatus.Succeeded))
                local.result shouldBe (if (index == 5) Some(JobResult.Success(OperationResponse.Scalar("ok"))) else None)
                val events = success(owner.events.query(EventStore.Query()))
                events.exists(_.name == "job.succeeded") shouldBe false
                if (index >= 3) {
                  val later = new Task
                  owner.engine.enqueueTaskInJob(jobid, later, owner.context).isSuccess shouldBe false
                  later.getters.get() shouldBe 0
                  later.runs.get() shouldBe 0
                  owner.signed(jobid) shouldBe previous.get
                  owner.native(jobid) shouldBe paid.get
                }
              }
              owner.engine.drainAll() shouldBe 0
              source.requests.size shouldBe index + 1
            }
          }
        }
      }

      "KI4 isolate finite admission and drain permutations under shrinking" must _scenario("KI4", "R6,R7,R8,R9") {
        "retain each caller recipe in its own signed Job through at least twenty four real SQLite cases" in {
          Given("docs/spec/durable-job-keyed-evidence-contract.md; KI4; R6,R7,R8,R9; seed69709701 with independent order choices and opaque caller recipe markers")
          val marker = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.take(24).mkString)
          val property = Prop.forAll(marker, marker, Gen.oneOf(false, true), Gen.oneOf(false, true)) {
            (firstmarker, secondmarker, reverseadmission, reversedrain) =>
              withDatabase { database =>
                val owner = database.open()
                val markers = Vector(s"first_$firstmarker", s"second_$secondmarker")
                val jobids = markers.map(JobInputBoundsFixtures.id)
                val recipes = jobids.zip(markers.map(recipe)).map { case (jobid, supplied) => jobid.value -> supplied }.toMap
                val source = new Source(jobid => recipes(jobid.value))
                bind(owner, source)
                val tasks = Vector(new Task, new Task)
                val admissionorder = if (reverseadmission) Vector(1, 0) else Vector(0, 1)
                val drainorder = if (reversedrain) Vector(1, 0) else Vector(0, 1)
                When("independent caller ID contexts admit both roots and the selected priority order drains them")
                admissionorder.foreach { index =>
                  success(owner.engine.submit(List(tasks(index)), contextFor(owner, markers(index)),
                    JobSubmitOption(priority = drainorder.indexOf(index)))) shouldBe jobids(index)
                }
                source.requests.map(_.jobid.value) shouldBe admissionorder.map(index => jobids(index).value)
                drainorder.foreach { index =>
                  owner.engine.drainOne() shouldBe true
                  Then("the actual signed outcome, protected authorization and caller definition stay under the exact Job key")
                  _assert_completed(owner, source, jobids(index), tasks(index), recipes(jobids(index).value))
                }
                source.requests.filter(_.request.boundary == DurableJobLifecycleWriteBoundary.StartIntent)
                  .map(_.jobid.value) shouldBe drainorder.map(index => jobids(index).value)
                owner.signed(jobids.head).record.body.definitionSnapshot should not be
                  owner.signed(jobids.last).record.body.definitionSnapshot
                owner.engine.durableLifecycleWriteFailureFacts shouldBe empty
                owner.engine.drainAll() shouldBe 0
              }
              true
          }
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
            .withInitialSeed(Seed(69709701L)), property)
          withClue(s"KI4 shrinking native seed69709701: $checked") { checked.passed shouldBe true }
          checked.succeeded should be >= 24
        }
      }
    }
  }

  private def _assert_completed(owner: Owner, source: Source, jobid: JobId, task: Task, supplied: Recipe): Unit = {
    val selected = source.requests.filter(_.jobid.value == jobid.value)
    selected.map(_.expectedrevision) shouldBe (1L to 6L).toVector
    selected.map(_.request.boundary) shouldBe boundaries
    val signed = owner.signed(jobid).record.body
    signed.identity.jobId shouldBe jobid.value
    signed.identity.revision shouldBe 6L
    signed.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    signed.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
    signed.authorization shouldBe supplied.projection.authorization
    signed.definitionSnapshot shouldBe supplied.projection.definitionSnapshot
    signed.inputs shouldBe supplied.projection.inputs
    signed.retention shouldBe supplied.projection.retention
    signed.calltreeReference shouldBe supplied.projection.calltreeReference
    signed.lifecycle.retry.maxAttempts shouldBe 3
    signed.lifecycle.retry.attempts.size shouldBe 1
    val models = owner.engine._get_record(jobid).get.taskReadModels
    signed.lifecycle.retry.attempts.head.startedAt shouldBe models.head.startedAt
    signed.lifecycle.retry.attempts.head.finishedAt shouldBe models.head.finishedAt
    task.runs.get() shouldBe 1
    task.canonical.get() shouldBe 1
    owner.engine.getResult(jobid) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
    val native = owner.native(jobid)
    native.seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
    native.seed.remaining shouldBe 0L
    native.seed.normalids shouldBe models.map(_.taskId.value).toSet
    native.seed.claims shouldBe empty
    ledger(owner).get.entries.find(_.jobid == jobid.value).get.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
  }

  private def _failure_conclusion[A](result: Consequence[A]): org.goldenport.Conclusion = result match {
    case Consequence.Failure(value) => value
    case Consequence.Success(_) => fail("expected a consequence failure")
  }
}
