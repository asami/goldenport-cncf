package org.goldenport.cncf.job

import java.nio.file.{Files, Paths}
import java.time.{Clock, Duration, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.conclusion.Disposition
import org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimePlan, EntityStorage}
import org.goldenport.cncf.event.EventStore
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Actual native lifetime runtime specifications; same-State attachment does
 * not discharge the separate fresh-State recovery and cross-process gates.
 *
 * @since   Oct. 2, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class JobDurableLifetimeRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _access = JobInputBoundsFixtures.access
  private val _now = JobInputBoundsFixtures.instant
  private val _short_retry = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(10)), 3)
  private def _scenario(id: String) = afterWord(
    s"in spec:durable-job-lifetime-quota-contract, example:$id, rules:R1,R3,R4,R6, phase:69.7, slice:JM69-09B3B3C2B2B1")

  "Native lifetime runtime" should {
    "actual root publication and dispatch" must _scenario("NRT-A") {
      "associate before metadata/body and keep the original six acknowledged checkpoints in Sync and Async" in {
        Vector(JobRunMode.Sync, JobRunMode.Async).foreach { mode =>
          Given(s"NRT-A: actual temporary SQLite, declared Detached JobEntity and counting $mode root")
          _with_database() { fixture =>
            val task = new Task()
            var published = false
            var callbackproof = Vector.empty[DurableJobLifetimeQuotaSnapshot]
            fixture.entity.onjobcreate = Some(() => task.armMetadata(() => {
              val id = fixture.engine.runtimeState.durableJobs.keySet().iterator().next()
              callbackproof :+= _native(fixture, id)
            }))
            task.body = ctx => {
              val id = ctx.jobContext.jobId.get
              published = _ledger(fixture).entries.find(_.jobid == id.value).get.rootstate ==
                DurableJobLifetimeQuotaRootState.Published
              _signed(fixture, id).body.identity.revision shouldBe 4L
              TaskSucceeded(OperationResponse.Scalar("ok"))
            }
            When("actual configured Persistent submission dispatches and Async is explicitly drained")
            val id = _success(fixture.engine.submit(List(task), fixture.context, JobSubmitOption(runMode = mode)))
            fixture.engine.drainAll() shouldBe (if (mode == JobRunMode.Async) 1 else 0)
            val paid = _native(fixture, id)
            val before = task.effects
            val clocks = fixture.owner.clock.calls.get()
            val diagnostic = fixture.engine.lifetimeSnapshot(id)
            Then("Published and exact associated proof precede every armed action getter/body, and diagnostics add no callback or clock")
            published shouldBe true
            callbackproof should not be empty
            callbackproof.foreach { proof =>
              proof.seed.remaining shouldBe 0L
              proof.seed.normalids.size shouldBe 1
              proof.seed.paid shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
            }
            diagnostic shouldBe Some(paid.seed.paid)
            task.effects shouldBe before
            fixture.owner.clock.calls.get() shouldBe clocks
            paid.seed.normalids shouldBe fixture.engine._get_record(id).get.taskReadModels.map(_.taskId.value).toSet
            fixture.source.requests.map(_.boundary) shouldBe Vector(
              DurableJobLifecycleWriteBoundary.Admission, DurableJobLifecycleWriteBoundary.StartIntent,
              DurableJobLifecycleWriteBoundary.RunningIntent, DurableJobLifecycleWriteBoundary.TaskStartIntent,
              DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint, DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
            fixture.source.revisions shouldBe Vector(1L, 2L, 3L, 4L, 5L, 6L)
            _signed(fixture, id).body.identity.revision shouldBe 6L
            task.runs.get() shouldBe 1
            task.canonical.get() shouldBe 1
            fixture.engine.resourceSnapshot.pending shouldBe 0
            fixture.engine.resourceSnapshot.executing shouldBe 0
          }
        }
      }
    }

    "shared native expansion" must _scenario("NRT-B") {
      "pay nested, external and queued children once using the root bridge/context and reject busy or exhausted candidates without effects" in {
        Given("NRT-B: a real Running root, two State owners, four admissions and two physical execution leases")
        _with_database(JobLifetimePolicy(8, 384, 4), JobResourcePolicy(maxexecutingwork = 2)) { fixture =>
          val child = new Task(relationkind = "child")
          val root = new Task()
          root.body = ctx => {
            _success(fixture.engine.runTaskInJobSync(ctx.jobContext.jobId.get, child, ctx))
            fixture.engine.resourceSnapshot.executing shouldBe 1
            _ok
          }
          _with_running(fixture, root) { (id, ctx) =>
            val other = fixture.addOwner()
            val external = new Task()
            val queued = new Task()
            val before = _native(fixture, id).seed.paid
            When("the second owner without a bridge performs real external Sync and queued work through its provider-free context")
            _success(other.engine.runTaskInJobSync(id, external, other.context))
            val queuedid = _success(other.engine.enqueueTaskInJob(id, queued, other.context))
            val admitted = _native(fixture, id)
            val beforedispatch = admitted.seed
            other.engine.drainAll() shouldBe 1
            val refused = new Task()
            val conserved = _facts(other, refused)
            val prior = _raw_quota(fixture)
            val denied = other.engine.enqueueTaskInJob(id, refused, other.context)
            Then("each child consumes only its actual Work allowance, queued association repeats without payment, and overflow is atomic")
            before shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
            admitted.seed.paid shouldBe JobLifetimeSnapshot(8, 384, 4, 4)
            admitted.seed.normalids should contain(queuedid.value)
            _native(fixture, id).seed shouldBe beforedispatch
            _failure(denied, JobResourcePolicy.refused[Unit])
            _raw_quota(fixture) shouldBe prior
            _facts(other, refused) shouldBe conserved
            child.runs.get() shouldBe 1
            external.runs.get() shouldBe 1
            queued.runs.get() shouldBe 1
            fixture.source.requests.count(_.boundary == DurableJobLifecycleWriteBoundary.TaskStartIntent) shouldBe 4
            _binding(fixture, id).context shouldBe fixture.context
            (_binding(fixture, id).bridge.exists(_ eq fixture.bridge)) shouldBe true
            _success(fixture.quota.associate(id.value, _access, queuedid.value)(using fixture.context)) shouldBe true
            _native(fixture, id).seed shouldBe beforedispatch
            ctx.jobContext.cancellationScope shouldBe Some(_binding(fixture, id).scope)
          }
        }
        Given("NRT-B: the root owns the only physical lease, independently of unused paid capacity")
        _with_database(JobLifetimePolicy(6, 288, 3), JobResourcePolicy(maxexecutingwork = 1)) { fixture =>
          _with_running(fixture, new Task()) { (id, _) =>
            val other = fixture.addOwner()
            val task = new Task()
            val before = _facts(other, task)
            val native = _raw_quota(fixture)
            When("another actual thread attempts external Sync work while the root lease is occupied")
            val result = other.engine.runTaskInJobSync(id, task, other.context)
            Then("physical busy refuses before native payment, IDs, clocks, getters or body")
            _failure(result, JobResourcePolicy.refused[Unit])
            _facts(other, task) shouldBe before
            _raw_quota(fixture) shouldBe native
          }
        }
      }
    }

    "paid annotations and control" must _scenario("NRT-C") {
      "preserve exact annotation/control fit, replay, profile allowance and authorization precedence across owners" in {
        Given("NRT-C: one queued native root with exactly two controls and one two-note annotation allowance")
        _with_database(JobLifetimePolicy(2, 194, 4)) { fixture =>
          val task = new Task()
          val id = _success(fixture.engine.submit(List(task), fixture.context))
          val other = fixture.addOwner()
          When("the second owner annotates and executes Suspend, replay Suspend and Resume at the inclusive cap")
          other.engine.annotateJob(id, Map("closed" -> "yes"), Vector("one", "two"))
          _native(fixture, id).seed.paid shouldBe JobLifetimeSnapshot(2, 130, 2, 1)
          _success(_control(other, id, JobControlCommand.Suspend)).changed shouldBe true
          val replayproof = _raw_quota(fixture)
          _success(_control(other, id, JobControlCommand.Suspend)).changed shouldBe false
          _raw_quota(fixture) shouldBe replayproof
          _success(_control(other, id, JobControlCommand.Resume)).changed shouldBe true
          val record = fixture.engine._get_record(id).get
          val proof = _raw_quota(fixture)
          val clocks = other.clock.calls.get()
          Then("native counters equal 0/32+m/1/0 and 0/32/1/0 before mutation, and later annotation refusal is exact")
          _native(fixture, id).seed.paid shouldBe JobLifetimeSnapshot(2, 194, 4, 1)
          Vector(null, Vector(null), Vector.fill(195)("over"), Vector.empty[String]).foreach { notes =>
            Given("NRT-C: a complete invalid or unpaid diagnostic note vector with the original debug/time retained")
            When("the Unit annotation API receives that vector")
            val error = intercept[IllegalArgumentException](other.engine.annotateJob(id, Map("new" -> "no"), notes))
            Then("the fixed annotation exception conserves both native bytes and the original local record")
            error.getMessage shouldBe "JobEngine lifetime annotation refused"
            fixture.engine._get_record(id).get shouldBe record
            _raw_quota(fixture) shouldBe proof
            other.clock.calls.get() shouldBe clocks
          }
          other.engine.annotateJob(JobInputBoundsFixtures.id("absent"), Map.empty, null)
          other.engine.annotateJobProfile(id, JobDeclaredProfile())
          val profile = fixture.engine._get_record(id).get
          other.engine.annotateJobProfile(id, JobDeclaredProfile())
          fixture.engine._get_record(id).get shouldBe profile
          _raw_quota(fixture) shouldBe proof
          task.runs.get() shouldBe 0
          val denied = new JobControlPolicy {
            def authorize(id: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] =
              Consequence.stateInvalid("closed authorization refusal")
          }
          Given("NRT-C: original authorization refuses before an otherwise chargeable control")
          When("the actual public control API invokes the denying policy")
          val result = other.engine.control(id, JobControlRequest(JobControlCommand.Suspend), denied)(using other.context)
          Then("authorization remains first and produces no quota write or source request")
          _failure(result, Consequence.stateInvalid[Unit]("closed authorization refusal"))
          _raw_quota(fixture) shouldBe proof
        }
      }
    }

    "native Retry origin and due transfer" must _scenario("NRT-D") {
      "pay automatic immediate/delayed Retry once, preserve signed task pairs and close denied attempts with the original failure" in {
        Vector(Disposition.UserAction.RetryNow, Disposition.UserAction.RetryLater).foreach { action =>
          Given(s"NRT-D: an actual failing root with $action and one bounded short retry allowance")
          _with_database(JobLifetimePolicy(4, 192, 2)) { fixture =>
            fixture.source.attemptcount = 2
            val task = new Task()
            task.body = _ => if (task.runs.get() == 1) TaskFailed(_retry_failure(action)) else _ok
            val id = _success(fixture.engine.submit(List(task), fixture.context))
            When("the first body fails and the original automatic retry becomes due or immediately queues")
            fixture.engine.drainOne() shouldBe true
            val afterpayment = _native(fixture, id).seed.paid
            val oldscope = task.scopes.head
            if (action == Disposition.UserAction.RetryLater) {
              fixture.owner.timer.pendingCount shouldBe 1
              fixture.owner.timer.advanceBy(Duration.ofMillis(10)) shouldBe 1
              fixture.owner.timer.advanceBy(Duration.ZERO) shouldBe 0
            }
            fixture.engine.drainAll() shouldBe 1
            Then("Retry adds the exact native allowance once, creates one new shared scope and retains the acknowledged normal task pairs")
            afterpayment shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
            _native(fixture, id).seed.paid shouldBe afterpayment
            _native(fixture, id).seed.remaining shouldBe 0L
            _native(fixture, id).seed.normalids.size shouldBe 2
            task.runs.get() shouldBe 2
            (oldscope eq task.scopes.last) shouldBe false
            _signed(fixture, id).body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
            _signed(fixture, id).body.tasks.size shouldBe 2
            fixture.source.requests.count(_.boundary == DurableJobLifecycleWriteBoundary.TaskStartIntent) shouldBe 2
            fixture.source.requests.count(_.boundary == DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint) shouldBe 2
            fixture.engine.resourceSnapshot.pending shouldBe 0
          }
        }
        Vector("paid", "physical", "missing-delay", "overflow", "timer").foreach { mode =>
          Given(s"NRT-D: an actual RetryLater failure whose next expansion is denied by $mode")
          val lifetime = if (mode == "paid") JobLifetimePolicy(2, 96, 1) else JobLifetimePolicy(4, 192, 2)
          val resources = if (mode == "physical") JobResourcePolicy(maxpendingwork = 1) else JobResourcePolicy.default
          val schedule = if (mode == "missing-delay") InMemoryJobEngine.RetrySchedule(Vector.empty, 3) else _short_retry
          _with_database(lifetime, resources, schedule) { fixture =>
            val original = _retry_failure(Disposition.UserAction.RetryLater)
            fixture.source.exhausted = true
            var blocker = Option.empty[JobResourceBudget.Lease]
            val task = new Task()
            task.body = _ => {
              if (mode == "overflow") fixture.entity.onquotaack = Some(() => fixture.owner.clock.overflowonce = true)
              if (mode == "timer") fixture.owner.timer.failnext = true
              if (mode == "physical") blocker = Some(_success(fixture.engine._reserve_pending(
                fixture.engine.runtimeState.durableJobs.keySet().iterator().next(), root = false)))
              TaskFailed(original)
            }
            try {
              val id = _success(fixture.engine.submit(List(task), fixture.context))
              When("the real first body fails and automatic retry eligibility/payment/publication reaches its original closure")
              fixture.engine.drainOne() shouldBe true
              Then("one initial body executes with zero accepted retries, exhaustion retains the original Conclusion, and paid ghosts are never refunded")
              task.runs.get() shouldBe 1
              val record = fixture.engine._get_record(id).get
              record.retry.attemptCount shouldBe 0
              record.retry.exhausted shouldBe true
              record.retry.recoveryRequired shouldBe true
              record.retry.deadLetter shouldBe true
              record.retry.poison shouldBe false
              record.result shouldBe Some(JobResult.Failure(original))
              _native(fixture, id).seed.paid.chargedtasks shouldBe (if (mode == "overflow" || mode == "timer") 4L else 2L)
              fixture.owner.timer.pendingCount shouldBe 0
            } finally blocker.foreach(fixture.engine._resource_budget.release)
          }
        }
        Given("NRT-D: a real poison Failed terminal root and a second owner using the original Control Retry origin")
        _with_database(JobLifetimePolicy(4, 192, 2)) { fixture =>
          val task = new Task(_ => TaskFailed(_failure_conclusion))
          val id = _success(fixture.engine.submit(List(task), fixture.context))
          fixture.engine.drainAll() shouldBe 1
          val other = fixture.addOwner()
          val signed = _raw_job(fixture, id)
          val sourcecount = fixture.source.requests.size
          task.body = _ => _ok
          When("Control Retry is acknowledged and its compatibility queue dispatches")
          _success(_control(other, id, JobControlCommand.Retry))
          other.engine.drainAll() shouldBe 1
          Then("native Retry/association still pay while the original Control origin adds no signed task boundary")
          _native(fixture, id).seed.paid shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
          _native(fixture, id).seed.normalids.size shouldBe 2
          task.runs.get() shouldBe 2
          _raw_job(fixture, id) shouldBe signed
          fixture.source.requests.size shouldBe sourcecount
        }
      }
    }

    "prepaid Cancel and shared scope" must _scenario("NRT-E") {
      "retain a real lost Cancel acknowledgement and finish safety only at the next confirmed intent gate" in {
        Given("NRT-E: an exhausted queued root, two owners, one prepaid Cancel and a commit-then-fault EntityStore hook")
        _with_database(JobLifetimePolicy(2, 96, 1)) { fixture =>
          val task = new Task()
          val id = _success(fixture.engine.submit(List(task), fixture.context))
          val other = fixture.addOwner()
          val scope = _binding(fixture, id).scope
          val before = fixture.engine._get_record(id).get
          fixture.entity.arm()
          When("the second owner loses acknowledgement after the actual native Cancel CAS commits")
          val result = _control(other, id, JobControlCommand.Cancel)
          Then("the failed call publishes no local cancellation, while a separate fresh native owner proves the retained intent and zero credits")
          _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          fixture.entity.deliveries shouldBe 1
          fixture.engine._get_record(id).get shouldBe before
          scope.isCancelled shouldBe false
          val proof = _native(fixture, id)
          proof.cancelclaimed shouldBe true
          proof.seed.paid.cancelcredits shouldBe 0L
          val writes = fixture.entity.quotawrites
          Given("NRT-E: persisted Cancel is now known at a separate dispatch gate")
          When("the root owner drains and both owners repeat the original Cancel command")
          fixture.engine.drainAll() shouldBe 1
          _success(_control(other, id, JobControlCommand.Cancel)).changed shouldBe false
          _success(_control(fixture.owner, id, JobControlCommand.Cancel)).changed shouldBe false
          Then("one safety cancellation consumes no further payment, cancels the shared scope and prevents all normal callbacks")
          scope.isCancelled shouldBe true
          fixture.engine.getStatus(id) shouldBe Some(JobStatus.Cancelled)
          task.runs.get() shouldBe 0
          fixture.engine._get_record(id).get.timeline.count(_.kind == "job.cancelled") shouldBe 1
          fixture.entity.quotawrites shouldBe writes
          fixture.engine.resourceSnapshot.pending shouldBe 0
        }
        Given("NRT-E: a live callback and a legal paid Retry after confirmed cancellation")
        _with_database(JobLifetimePolicy(4, 192, 2)) { fixture =>
          val root = new Task()
          _with_running(fixture, root) { (id, ctx) =>
            val other = fixture.addOwner()
            fixture.source.terminalstatus = Some(JobStatus.Cancelled)
            val oldscope = ctx.jobContext.cancellationScope.get
            When("another owner cancels the actual running root and requests legal Control Retry")
            _success(_control(other, id, JobControlCommand.Cancel))
            oldscope.isCancelled shouldBe true
            _success(_control(other, id, JobControlCommand.Retry))
            Then("only acknowledged Retry resets persisted intent and installs a new shared scope while the old callback stays cancelled")
            _native(fixture, id).cancelclaimed shouldBe false
            _native(fixture, id).seed.paid shouldBe JobLifetimeSnapshot(4, 192, 2, 1)
            (_binding(fixture, id).scope eq oldscope) shouldBe false
            _binding(fixture, id).scope.isCancelled shouldBe false
            oldscope.isCancelled shouldBe true
          }
        }
        Given("NRT-E: real delayed work already owns its pending generation")
        _with_database() { fixture =>
          val task = new Task()
          val id = _success(fixture.engine.submit(List(task), fixture.context,
            JobSubmitOption(scheduledStartAt = Some(_now.plusMillis(10)))))
          _success(fixture.quota.cancel(id.value, _access)(using fixture.context))
          When("a fresh due gate and a same-State attachment observe the prepaid persisted Cancel")
          fixture.owner.timer.advanceBy(Duration.ofMillis(10)) shouldBe 1
          val other = fixture.addOwner()
          Then("confirmed intent removes the delayed generation and cannot create a queue or callback")
          fixture.engine.drainAll() shouldBe 0
          other.engine.drainAll() shouldBe 0
          task.runs.get() shouldBe 0
          fixture.engine.resourceSnapshot.pending shouldBe 0
          other.timer.pendingCount shouldBe 0
          _native(fixture, id).seed.paid.cancelcredits shouldBe 0L
        }
      }
    }

    "prepaid compensation and terminal interpretation" must _scenario("NRT-F") {
      "claim the original once and sign success/failure/missing aggregates with exact actual companion equality" in {
        Vector("succeeded", "failed", "missing", "declared-missing").foreach { mode =>
          Given(s"NRT-F: a committed original and actual failing child, exhausted expansion, compensation mode $mode")
          _with_database(JobLifetimePolicy(4, 160, 1)) { fixture =>
            val companion = new Task(_ => if (mode == "failed") TaskFailed(_failure_conclusion) else _ok)
            companion.declared = Some("undo")
            val original = new Task()
            original.companion = Option.when(mode == "succeeded" || mode == "failed")(companion)
            original.declared = Option.when(mode != "missing")("undo")
            val failed = new Task(_ => TaskFailed(_failure_conclusion), relationkind = "child")
            val id = _success(fixture.engine.submit(List(original, failed), fixture.context))
            original.oncompensation = () => {
              _native(fixture, id).seed.claims.size shouldBe 1
              fixture.engine._get_record(id).get.timeline.count(_.kind == "job.compensation.started") shouldBe 1
            }
            When("the actual normal failure selects the prepaid original and terminal Bridge projects its unchanged aggregate")
            fixture.engine.drainAll() shouldBe 1
            val record = fixture.engine._get_record(id).get
            val model = record.taskReadModels.head
            val signed = _signed(fixture, id)
            val other = fixture.addOwner()
            val before = (record, original.effects, companion.effects, _raw_quota(fixture),
              fixture.owner.ids.calls.get(), other.ids.calls.get())
            val repeated = _race(
              fixture.engine._run_compensations(id, None, Vector(model.taskId -> original), fixture.context),
              other.engine._run_compensations(id, None, Vector(model.taskId -> original), other.context))
            Then("exactly one claim permits getters/body, duplicate false has no effects, and missing has no invented action/companion descriptor")
            original.compensations.get() shouldBe 1
            companion.runs.get() shouldBe (if (mode.contains("missing")) 0 else 1)
            model.compensationStatus shouldBe Some(if (mode.contains("missing")) "missing" else mode)
            model.compensationActionRef shouldBe original.declared
            _native(fixture, id).seed.paid shouldBe JobLifetimeSnapshot(4, 160, 1, 1)
            _native(fixture, id).seed.claims shouldBe Set(model.taskId.value)
            signed.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Failed
            signed.body.tasks.head.compensation shouldBe None
            signed.body.lifecycle.retry.recoveryRequired shouldBe true
            signed.body.tasks.count(_.relation.kind == DurableTaskRelationKind.Compensation) shouldBe
              (if (mode.contains("missing")) 0 else 1)
            signed.body.tasks.filter(_.relation.kind == DurableTaskRelationKind.Compensation).foreach { actual =>
              actual.compensation.get.compensatesTaskId shouldBe model.taskId.value
              actual.compensation.get.operation.operationId shouldBe "undo"
              actual.target.operation.operationId shouldBe "run"
            }
            fixture.source.requests.count(_.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) shouldBe 1
            fixture.source.requests.count(_.boundary == DurableJobLifecycleWriteBoundary.TaskStartIntent) shouldBe 2
            repeated._1 shouldBe empty
            repeated._2 shouldBe empty
            (fixture.engine._get_record(id).get, original.effects, companion.effects, _raw_quota(fixture),
              fixture.owner.ids.calls.get(), other.ids.calls.get()) shouldBe before
          }
        }
        Given("NRT-F: native Cancel and exhausted capacity coexist with an already committed original")
        _with_database(JobLifetimePolicy(2, 96, 1)) { fixture =>
          val original = new Task()
          original.companion = Some(new Task())
          original.declared = Some("undo")
          val id = _success(fixture.engine.submit(List(original), fixture.context))
          fixture.engine.drainAll() shouldBe 1
          val taskid = fixture.engine._get_record(id).get.taskReadModels.head.taskId
          _success(fixture.quota.cancel(id.value, _access)(using fixture.context))
          When("another State owner asks for the actually owed compensation selector after persisted Cancel")
          val other = fixture.addOwner()
          other.engine._run_compensations(id, None, Vector(taskid -> original), other.context).size shouldBe 1
          Then("prepaid compensation remains available and uses the retained shared scope without ordinary Work")
          original.compensations.get() shouldBe 1
          _native(fixture, id).seed.claims shouldBe Set(taskid.value)
          _native(fixture, id).seed.paid shouldBe JobLifetimeSnapshot(2, 96, 1, 0)
          (original.companion.get.asInstanceOf[Task].scopes.head eq _binding(fixture, id).scope) shouldBe true
          _native(fixture, id).cancelclaimed shouldBe true
        }
      }
    }

    "closed acknowledgement uncertainty" must _scenario("NRT-G") {
      "retain actual Work, Retry, association and compensation ghosts without inference, reissue, body or refund" in {
        Given("NRT-G: an actual Running root with an isolated commit-then-fault native Work payment")
        _with_database(JobLifetimePolicy(4, 192, 2)) { fixture =>
          _with_running(fixture, new Task()) { (id, _) =>
            val task = new Task()
            val before = _facts(fixture.owner, task)
            fixture.entity.arm()
            When("queued child admission loses the native payment acknowledgement after actual SQLite mutation")
            val result = fixture.engine.enqueueTaskInJob(id, task, fixture.context)
            Then("the exact native paid ghost remains while all candidate effects and provisional physical work are conserved")
            _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
            fixture.entity.deliveries shouldBe 1
            _facts(fixture.owner, task) shouldBe before
            _native(fixture, id).seed.paid shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
            task.runs.get() shouldBe 0
          }
        }
        Given("NRT-G: a real RetryNow task arms acknowledgement loss only after its original body fails")
        _with_database(JobLifetimePolicy(4, 192, 2)) { fixture =>
          fixture.source.exhausted = true
          val task = new Task(_ => { fixture.entity.arm(); TaskFailed(_retry_failure(Disposition.UserAction.RetryNow)) })
          val id = _success(fixture.engine.submit(List(task), fixture.context))
          When("automatic Retry loses the actual payment acknowledgement")
          fixture.engine.drainAll() shouldBe 1
          Then("the Retry charge persists while one initial body and zero accepted retries close without executing unpaid new work")
          fixture.entity.deliveries shouldBe 1
          _native(fixture, id).seed.paid shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
          task.runs.get() shouldBe 1
          task.successcanonical.get() shouldBe 0
          fixture.engine.resourceSnapshot.pending shouldBe 0
          fixture.engine._get_record(id).get.retry.attemptCount shouldBe 0
        }
        Given("NRT-G: a queued actual root with association acknowledgement loss armed after publication")
        _with_database() { fixture =>
          val task = new Task()
          val id = _success(fixture.engine.submit(List(task), fixture.context))
          val getters = task.getters.get()
          fixture.entity.arm()
          When("root dispatch commits its native association and loses that acknowledgement")
          fixture.engine.drainAll() shouldBe 1
          val other = fixture.addOwner()
          val proof = _native(fixture, id)
          val candidate = new Task()
          val bytes = _raw_quota(fixture)
          val result = other.engine.enqueueTaskInJob(id, candidate, other.context)
          Then("fresh proof exposes one associated ghost but cannot authorize metadata, body, canonical result or another normal admission")
          fixture.entity.deliveries shouldBe 1
          proof.seed.normalids.size shouldBe 1
          proof.seed.remaining shouldBe 0L
          task.getters.get() shouldBe getters
          task.runs.get() shouldBe 0
          task.canonical.get() shouldBe 0
          fixture.engine._get_record(id).get.taskReadModels shouldBe empty
          result shouldBe a[Consequence.Failure[_]]
          candidate.effects shouldBe (0, 0, 0, 0)
          _raw_quota(fixture) shouldBe bytes
          fixture.source.requests.exists(_.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) shouldBe false
          fixture.engine.resourceSnapshot.executing shouldBe 0
        }
        Vector(false, true).foreach { precommit =>
          Given(s"NRT-G: two committed originals and a real ${if (precommit) "precommit" else "committed lost-ack"} compensation claim failure")
          _with_database(JobLifetimePolicy(4, 160, 1)) { fixture =>
            val first = new Task()
            val second = new Task(relationkind = "child")
            Vector(first, second).foreach { task => task.companion = Some(new Task()); task.declared = Some("undo") }
            val id = _success(fixture.engine.submit(List(first, second), fixture.context))
            fixture.engine.drainAll() shouldBe 1
            val models = fixture.engine._get_record(id).get.taskReadModels
            val other = fixture.addOwner()
            fixture.entity.arm(precommit = precommit, after = 2)
            When("one batch claims the first selector then loses/refuses the second before any compensation callback")
            val result = fixture.engine._run_compensations(id, None,
              Vector(models.head.taskId -> first, models.last.taskId -> second), fixture.context)
            val calls = fixture.entity.quotawrites
            val retained = _native(fixture, id).seed.claims
            val repeat = other.engine._run_compensations(id, None, Vector(models.last.taskId -> second), other.context)
            Then("acknowledged earlier claims remain ghosts and the uncertain original cannot be submitted again by a second owner")
            result shouldBe empty
            repeat shouldBe empty
            fixture.entity.deliveries shouldBe 1
            fixture.entity.quotawrites shouldBe calls
            retained shouldBe (if (precommit) Set(models.head.taskId.value) else models.map(_.taskId.value).toSet)
            first.compensations.get() shouldBe 0
            second.compensations.get() shouldBe 0
            _binding(fixture, id).refused shouldBe true
          }
          Given(s"NRT-G: a ${if (precommit) "precommit" else "committed"} uncertain selector and a different still-owed original")
          _with_database(JobLifetimePolicy(4, 160, 1)) { fixture =>
            val first = new Task()
            val second = new Task(relationkind = "child")
            Vector(first, second).foreach { task => task.companion = Some(new Task()); task.declared = Some("undo") }
            val id = _success(fixture.engine.submit(List(first, second), fixture.context))
            fixture.engine.drainAll() shouldBe 1
            val models = fixture.engine._get_record(id).get.taskReadModels
            fixture.entity.arm(precommit)
            fixture.engine._run_compensations(id, None, Vector(models.head.taskId -> first), fixture.context) shouldBe empty
            val other = fixture.addOwner()
            val calls = fixture.entity.quotawrites
            When("the second owner revisits the ambiguous original then selects the different actually owed original")
            other.engine._run_compensations(id, None, Vector(models.head.taskId -> first), other.context) shouldBe empty
            fixture.entity.quotawrites shouldBe calls
            other.engine._run_compensations(id, None, Vector(models.last.taskId -> second), other.context).size shouldBe 1
            Then("uncertainty suppresses only reissue of that selector while another native claim remains eligible")
            first.compensations.get() shouldBe 0
            second.compensations.get() shouldBe 1
            fixture.entity.quotawrites shouldBe calls + 1
            _native(fixture, id).seed.claims should contain(models.last.taskId.value)
            _binding(fixture, id).refused shouldBe true
          }
        }
      }
    }

    "fresh same-State native bootstrap" must _scenario("NRT-H") {
      "restore exact native supersets and refuse malformed whole input or unavailable native proof before seeding effects" in {
        Given("NRT-H: an actual three-task batch whose original succeeds, second child fails, and unused third child leaves paid normal capacity")
        _with_database(JobLifetimePolicy(6, 400, 5)) { fixture =>
          val original = new Task()
          val failed = new Task(_ => TaskFailed(_failure_conclusion), relationkind = "child")
          val unused = new Task(relationkind = "child")
          val id = _success(fixture.engine.submit(List(original, failed, unused), fixture.context))
          fixture.engine.drainAll() shouldBe 1
          unused.runs.get() shouldBe 0
          val taskid = fixture.engine._get_record(id).get.taskReadModels.head.taskId
          fixture.engine.annotateJob(id, Map.empty, Vector("native history"))
          _success(fixture.quota.claimCompensation(id.value, _access, taskid.value)(using fixture.context)) shouldBe false
          _success(fixture.quota.cancel(id.value, _access)(using fixture.context))
          val native = _native(fixture, id).seed
          val callbacks = original.effects
          When("a second actual engine attaches to the same live State and reads authenticated paid diagnostics")
          val other = fixture.addOwner()
          Then("the mirror is the exact native Seed including unused normals/claims/zero Cancel, and no legacy floor or callbacks appear")
          fixture.engine.runtimeState._lifetime_budget.snapshot(id) shouldBe Some(native.paid)
          other.engine.lifetimeSnapshot(id) shouldBe Some(native.paid)
          native.remaining shouldBe 1L
          native.claims shouldBe Set(taskid.value)
          native.paid.cancelcredits shouldBe 0L
          original.effects shouldBe callbacks
          val before = fixture.engine.resourceSnapshot
          val loads = fixture.entity.loads
          val previous = fixture.engine._get_record(id).get
          val invalidid = JobInputBoundsFixtures.id("malformed-map")
          fixture.engine.runtimeState.runtimeJobs.put(invalidid, previous.copy(id = invalidid, tasks = null))
          try {
            Given("NRT-H: another bounded whole-map record is structurally malformed before any native read")
            When("the actual constructor attempts same-State attachment")
            val error = intercept[IllegalArgumentException](fixture.addOwner())
            Then("fixed bootstrap refusal precedes provider IO/resource mutation and leaves existing paid proof intact")
            error.getMessage shouldBe "JobEngine resource bootstrap refused"
            fixture.entity.loads shouldBe loads
            fixture.engine.resourceSnapshot shouldBe before
            fixture.engine.runtimeState._lifetime_budget.snapshot(id) shouldBe Some(native.paid)
          } finally fixture.engine.runtimeState.runtimeJobs.remove(invalidid)
        }
        Given("NRT-H: two individually bounded maps whose structurally valid retained union exceeds one identity")
        _with_database(resources = JobResourcePolicy.default.copy(maxretainedjobs = 1)) { fixture =>
          val task = new Task()
          val id = _success(fixture.engine.submit(List(task), fixture.context))
          fixture.engine.drainAll() shouldBe 1
          val original = fixture.engine._get_record(id).get
          val extraid = JobInputBoundsFixtures.id("retained-union")
          val extra = original.copy(id = extraid, persistence = JobPersistencePolicy.Ephemeral)
          val native = _native(fixture, id).seed
          val resources = fixture.engine.resourceSnapshot
          val mirror = fixture.engine.runtimeState._lifetime_budget.snapshot(id)
          val effects = task.effects
          fixture.engine.runtimeState.runtimeJobs.put(extraid, extra)
          val loads = fixture.entity.loads
          try {
            When("another owner constructs over the whole retained union")
            val error = intercept[IllegalArgumentException](fixture.addOwner())
            Then("the pure union bound refuses before provider reads and conserves resources, native mirror, both records and task effects")
            error.getMessage shouldBe "JobEngine resource bootstrap refused"
            fixture.entity.loads shouldBe loads
            fixture.engine.resourceSnapshot shouldBe resources
            fixture.engine.runtimeState._lifetime_budget.snapshot(id) shouldBe mirror
            mirror shouldBe Some(native.paid)
            fixture.engine.runtimeState.durableJobs.get(id) shouldBe original
            fixture.engine.runtimeState.runtimeJobs.get(extraid) shouldBe extra
            task.effects shouldBe effects
          } finally fixture.engine.runtimeState.runtimeJobs.remove(extraid)
        }
        Vector("missing", "corrupt", "policy", "provider").foreach { mode =>
          Given(s"NRT-H: a real native-bound root whose sidecar is $mode for the next same-State attachment")
          _with_database() { fixture =>
            val task = new Task()
            val id = _success(fixture.engine.submit(List(task), fixture.context))
            val record = fixture.engine._get_record(id).get
            val before = fixture.engine.resourceSnapshot
            val seed = fixture.engine.runtimeState._lifetime_budget.snapshot(id)
            mode match {
              case "missing" => _success(fixture.provider.delete(_quota_collection, DataStore.EntryId(_quota_id))(using fixture.context))
              case "corrupt" => _replace_quota(fixture, "private-corrupt-body")
              case "policy" =>
                val ledger = _ledger(fixture)
                _replace_quota(fixture, _success(DurableJobLifetimeQuotaCodec.encode(ledger.copy(
                  policy = ledger.policy.copy(lifetime = JobLifetimePolicy(8191, 524288, 16384))))))
              case _ => fixture.entity.failload = true
            }
            When("the constructor authenticates fresh native proof after the whole pure batch")
            val error = intercept[IllegalArgumentException](fixture.addOwner())
            Then("fixed bootstrap failure conserves original resources/records/mirror without timer or body publication")
            error.getMessage shouldBe "JobEngine resource bootstrap refused"
            fixture.engine.resourceSnapshot shouldBe before
            fixture.engine._get_record(id).get shouldBe record
            fixture.engine.runtimeState._lifetime_budget.snapshot(id) shouldBe seed
            task.runs.get() shouldBe 0
            fixture.owner.timer.pendingCount shouldBe 0
          }
        }
      }
    }

    "shared closed checkpoint gates" must _scenario("NRT-I") {
      "retain the original checkpoint refusal across owners and deny bridge-absent bypass" in {
        Vector(DurableJobLifecycleWriteBoundary.TaskStartIntent, DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
          DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint).foreach { boundary =>
          Given(s"NRT-I: a shared native job whose original source refuses $boundary")
          _with_database() { fixture =>
            val task = new Task()
            fixture.source.refuse = Some(boundary)
            val id = _success(fixture.engine.submit(List(task), fixture.context))
            When("the first owner hits that actual signed checkpoint and a bridge-absent second owner attempts new work/control")
            fixture.engine.drainAll() shouldBe 1
            val other = fixture.addOwner()
            val candidate = new Task()
            val proof = _raw_quota(fixture)
            val work = other.engine.enqueueTaskInJob(id, candidate, other.context)
            val control = _control(other, id, JobControlCommand.Suspend)
            Then("the same bounded closed kind is visible and neither candidate body nor canonical success bypasses the owning bridge")
            work shouldBe a[Consequence.Failure[_]]
            control shouldBe a[Consequence.Failure[_]]
            other.engine.durableLifecycleWriteFailureFacts shouldBe fixture.engine.durableLifecycleWriteFailureFacts
            other.engine.durableLifecycleWriteFailureFacts.size shouldBe 1
            candidate.effects shouldBe (0, 0, 0, 0)
            task.runs.get() shouldBe (if (boundary == DurableJobLifecycleWriteBoundary.TaskStartIntent) 0 else 1)
            task.canonical.get() shouldBe 0
            _raw_quota(fixture) shouldBe proof
            _redacted(work)
            _redacted(control)
          }
        }
      }
    }

    "finite actual native sequences" must _scenario("NRT-J") {
      "match independent paid arithmetic and bounded two-owner claims in at least 24 fixed-seed runs" in {
        Given("NRT-J: Seed6970933631, finite Sync/Async roots, annotation/control/child choices and independent numeric counters")
        var firstfailure = Option.empty[String]
        val property = Prop.forAll(Gen.oneOf(JobRunMode.Sync, JobRunMode.Async),
          Gen.listOfN(5, Gen.choose(0, 3))) { (mode, actions) => try {
          _with_database(JobLifetimePolicy(8, 480, 6)) { fixture =>
            val original = new Task()
            original.companion = Some(new Task())
            original.declared = Some("undo")
            var jobid = Option.empty[JobId]
            var second = Option.empty[Owner]
            _with_running(fixture, original, mode) { (id, _) =>
            jobid = Some(id)
            val other = fixture.addOwner()
            second = Some(other)
            var charged = 2L
            var history = 96L
            var admissions = 1L
            var ids = fixture.engine._get_record(id).get.taskReadModels.map(_.taskId.value).toSet
            var bodies = 1
            actions.zipWithIndex.foreach { case (action, ordinal) =>
              Given(s"NRT-J: actual generated action $ordinal kind=$action with paid $charged/$history/$admissions")
              val prior = _raw_quota(fixture)
              val record = fixture.engine._get_record(id).get
              val task = new Task()
              val effects = _facts(other, task)
              val iswork = action < 2
              val deltacharged = if (iswork) 2L else 0L
              val deltahistory = if (iswork) 96L else if (action == 2) 33L else 0L
              val deltaadmissions = if (action == 3) 0L else 1L
              val allowed = charged + deltacharged <= 8 && history + deltahistory <= 480 && admissions + deltaadmissions <= 6
              When("the generated operation uses actual child or annotation admission and fresh native proof")
              val result: Consequence[Unit] = if (iswork) {
                other.engine.enqueueTaskInJob(id, task, other.context).map { taskid =>
                  ids += taskid.value
                  other.engine.drainAll() shouldBe 1
                  bodies += 1
                }
              } else if (action == 3) {
                _control(other, id, JobControlCommand.Resume).map { replay => replay.changed shouldBe false }
              } else {
                try {
                  other.engine.annotateJob(id, Map.empty, if (action == 2) Vector("one") else Vector.empty)
                  Consequence.unit
                } catch { case _: IllegalArgumentException => JobResourcePolicy.refused }
              }
              Then("independent inclusive arithmetic predicts admission and every exact fresh numeric/selector/body fact")
              result.isSuccess shouldBe allowed
              if (allowed) { charged += deltacharged; history += deltahistory; admissions += deltaadmissions }
              else {
                _raw_quota(fixture) shouldBe prior
                fixture.engine._get_record(id).get shouldBe record
                _facts(other, task) shouldBe effects
              }
              val native = _native(fixture, id)
              native.seed.paid shouldBe JobLifetimeSnapshot(charged, history, admissions, (charged / 2))
              native.seed.remaining shouldBe 0L
              native.seed.normalids shouldBe ids
              native.seed.claims shouldBe empty
              native.cancelclaimed shouldBe false
              fixture.engine._get_record(id).get.taskReadModels.count(_.status == JobTaskStatus.Succeeded) shouldBe bodies - 1
              _success(fixture.quota.associate(id.value, _access, ids.head)(using fixture.context)) shouldBe true
              _native(fixture, id).seed shouldBe native.seed
            }
            }
            val id = jobid.get
            val other = second.get
            val originalid = fixture.engine._get_record(id).get.taskReadModels.head.taskId
            Given("NRT-J: two bounded actual owners select the same original and persisted Cancel has prepaid credits")
            When("joined actors race native compensation selection, then duplicate Cancel and association are explicitly revisited")
            val claims = _race(fixture.engine._run_compensations(id, None, Vector(originalid -> original), fixture.context),
              other.engine._run_compensations(id, None, Vector(originalid -> original), other.context))
            val beforecancel = _native(fixture, id).seed.paid
            _success(fixture.quota.cancel(id.value, _access)(using fixture.context)) shouldBe true
            val cancelled = _native(fixture, id)
            _success(fixture.quota.cancel(id.value, _access)(using fixture.context)) shouldBe false
            _success(fixture.quota.associate(id.value, _access, originalid.value)(using fixture.context)) shouldBe true
            Then("one actual claim/body and one Cancel credit change the numeric model while duplicates change nothing")
            claims._1.size + claims._2.size shouldBe 1
            original.compensations.get() shouldBe 1
            cancelled.seed.claims shouldBe Set(originalid.value)
            cancelled.seed.paid shouldBe beforecancel.copy(cancelcredits = beforecancel.cancelcredits - 1)
            cancelled.cancelclaimed shouldBe true
            _native(fixture, id) shouldBe cancelled
          }
          true
        } catch { case NonFatal(error) =>
          if (firstfailure.isEmpty) firstfailure = Some(s"mode=$mode actions=$actions ${error.getClass.getName}: ${error.getMessage}")
          throw error
        } }
        When("ScalaCheck runs the finite real database sequences with shrinking and the frozen seed")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(6970933631L)), property)
        Then("at least 24 actual sequences complete every adjacent conservation assertion")
        withClue(s"Seed6970933631 ${firstfailure.getOrElse("no property exception")} $checked ") {
          checked.passed shouldBe true
          checked.succeeded should be >= 24
        }
      }
    }

    "transient dispatch refusal stops the root invocation" must _scenario("NRT-L") {
      "withhold Sync and Async completion after one acknowledged task and allow a later explicit child" in {
        Vector(JobRunMode.Sync, JobRunMode.Async).foreach { mode =>
          Given(s"NRT-L: docs/spec/durable-job-lifetime-quota-contract.md R1,R3,R4,R6; three $mode tasks and a quota-only refusal armed after the first acknowledged outcome")
          _with_database(JobLifetimePolicy(8, 320, 2)) { fixture =>
            val first = new Task()
            val second = new Task(relationkind = "child")
            val third = new Task(relationkind = "child")
            var acknowledged = Option.empty[DurableJobLifetimeQuotaSnapshot]
            var quotabytes = Option.empty[Record]
            fixture.entity.onjoback = Some(() => {
              fixture.source.requests.last match {
                case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(id, _, _, _) =>
                  fixture.entity.onjoback = None
                  _signed(fixture, id).body.identity.revision shouldBe 5L
                  acknowledged = Some(_native(fixture, id))
                  quotabytes = Some(_raw_quota(fixture))
                  fixture.entity.refusequotaloadonce = true
                case _ => ()
              }
            })
            When("the actual root dispatch reads native quota immediately before its second normal task")
            val id = _success(fixture.engine.submit(List(first, second, third), fixture.context, JobSubmitOption(runMode = mode)))
            fixture.engine.drainAll() shouldBe (if (mode == JobRunMode.Async) 1 else 0)
            Then("one acknowledged body remains Running/Pending and neither skipped task nor root completion acquires effects")
            fixture.entity.quotaloadrefusals shouldBe 1
            first.runs.get() shouldBe 1
            first.canonical.get() shouldBe 0
            first.successcanonical.get() shouldBe 0
            second.effects shouldBe (0, 0, 0, 0)
            third.effects shouldBe (0, 0, 0, 0)
            val record = fixture.engine._get_record(id).get
            record.status shouldBe JobStatus.Running
            record.primaryResult shouldBe None
            record.deferredResult shouldBe None
            record.baseTasksCompleted shouldBe false
            record.taskReadModels.size shouldBe 1
            record.timeline.count(_.kind == "job.succeeded") shouldBe 0
            _success(fixture.owner.events.query(EventStore.Query(name = Some("job.succeeded")))) shouldBe empty
            fixture.source.requests.count(_.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) shouldBe 0
            fixture.source.revisions shouldBe Vector(1L, 2L, 3L, 4L, 5L)
            val signed = _signed(fixture, id)
            signed.body.identity.revision shouldBe 5L
            signed.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
            signed.body.result shouldBe DurableResultOutcome.Pending
            _binding(fixture, id).refused shouldBe false
            _binding(fixture, id).checkpointfailure shouldBe None
            fixture.engine._lifecycle_failure(id) shouldBe None
            _native(fixture, id) shouldBe acknowledged.get
            _native(fixture, id).seed.normalids shouldBe Set(record.taskReadModels.head.taskId.value)
            _native(fixture, id).seed.remaining shouldBe 2L
            _raw_quota(fixture) shouldBe quotabytes.get
            fixture.engine.resourceSnapshot.pending shouldBe 0
            fixture.engine.resourceSnapshot.executing shouldBe 0
            Given("NRT-L: the same Running State retains paid proof and the single read refusal has cleared")
            val child = new Task(relationkind = "child")
            When("a separate public Sync child request explicitly revisits eligibility and pays for one new child")
            _success(fixture.engine.runTaskInJobSync(id, child, fixture.context)) shouldBe _ok
            Then("that child succeeds once without starting skipped root tasks or manufacturing the previous root completion")
            child.runs.get() shouldBe 1
            child.canonical.get() shouldBe 1
            _native(fixture, id).seed.paid shouldBe JobLifetimeSnapshot(8, 320, 2, 2)
            _native(fixture, id).seed.remaining shouldBe 2L
            _native(fixture, id).seed.normalids.size shouldBe 2
            _binding(fixture, id).refused shouldBe false
            fixture.engine._lifecycle_failure(id) shouldBe None
            second.effects shouldBe (0, 0, 0, 0)
            third.effects shouldBe (0, 0, 0, 0)
            first.canonical.get() shouldBe 0
            fixture.engine.getStatus(id) shouldBe Some(JobStatus.Running)
            fixture.source.requests.count(_.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) shouldBe 0
            _success(fixture.owner.events.query(EventStore.Query(name = Some("job.succeeded")))) shouldBe empty
            fixture.engine.resourceSnapshot.pending shouldBe 0
            fixture.engine.resourceSnapshot.executing shouldBe 0
          }
        }
      }
    }

    "confirmed dispatch Cancel settles once" must _scenario("NRT-M") {
      "acknowledge one cancelled terminal for Sync and Async roots and preserve another owner's legal replay" in {
        Vector(JobRunMode.Sync, JobRunMode.Async).foreach { mode =>
          Given(s"NRT-M: docs/spec/durable-job-lifetime-quota-contract.md R1,R3,R4,R6; a real $mode three-task root with native Cancel after its first acknowledged outcome")
          _with_database(JobLifetimePolicy(6, 224, 1)) { fixture =>
            val first = new Task()
            val second = new Task(relationkind = "child")
            val third = new Task(relationkind = "child")
            var cancellations = 0
            fixture.entity.onjoback = Some(() => {
              fixture.source.requests.last match {
                case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(id, _, _, _) =>
                  fixture.entity.onjoback = None
                  _signed(fixture, id).body.identity.revision shouldBe 5L
                  fixture.source.terminalstatus = Some(JobStatus.Cancelled)
                  _success(fixture.quota.cancel(id.value, _access)(using fixture.context)) shouldBe true
                  cancellations += 1
                  fixture.engine.getStatus(id) shouldBe Some(JobStatus.Running)
                  _binding(fixture, id).scope.isCancelled shouldBe false
                case _ => ()
              }
            })
            When("the next native dispatch observes Cancel and the original root then reaches cancelled completion")
            val id = _success(fixture.engine.submit(List(first, second, third), fixture.context, JobSubmitOption(runMode = mode)))
            fixture.engine.drainAll() shouldBe (if (mode == JobRunMode.Async) 1 else 0)
            Then("the first acknowledged cancellation settles once without a second terminal refusal or later task effects")
            cancellations shouldBe 1
            first.runs.get() shouldBe 1
            first.canonical.get() shouldBe 1
            second.effects shouldBe (0, 0, 0, 0)
            third.effects shouldBe (0, 0, 0, 0)
            _binding(fixture, id).scope.isCancelled shouldBe true
            fixture.engine.runtimeState.synchronized { _binding(fixture, id).cancellationSettled shouldBe true }
            _binding(fixture, id).refused shouldBe false
            _binding(fixture, id).checkpointfailure shouldBe None
            fixture.engine._lifecycle_failure(id) shouldBe None
            fixture.engine.getStatus(id) shouldBe Some(JobStatus.Cancelled)
            fixture.engine._get_record(id).get.timeline.count(_.kind == "job.cancelled") shouldBe 1
            _success(fixture.owner.events.query(EventStore.Query(name = Some("job.cancelled")))).size shouldBe 1
            fixture.source.requests.count(_.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) shouldBe 1
            fixture.source.revisions shouldBe Vector(1L, 2L, 3L, 4L, 5L, 6L)
            val signed = _signed(fixture, id)
            signed.body.identity.revision shouldBe 6L
            signed.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Cancelled
            val proof = _native(fixture, id)
            proof.cancelclaimed shouldBe true
            proof.seed.paid shouldBe JobLifetimeSnapshot(6, 224, 1, 0)
            proof.seed.remaining shouldBe 2L
            proof.seed.normalids.size shouldBe 1
            fixture.engine.resourceSnapshot.pending shouldBe 0
            fixture.engine.resourceSnapshot.executing shouldBe 0
            Given("NRT-M: another owner attaches to the same known settled State and cancelled signed row")
            val other = fixture.addOwner()
            val before = fixture.engine._get_record(id).get
            val quota = _raw_quota(fixture)
            val writes = fixture.entity.quotawrites
            When("the second owner repeats the legal Cancel command")
            _success(_control(other, id, JobControlCommand.Cancel)).changed shouldBe false
            Then("replay changes no payment, checkpoint, event, local record or shared failure fact")
            fixture.engine._get_record(id).get shouldBe before
            _raw_quota(fixture) shouldBe quota
            fixture.entity.quotawrites shouldBe writes
            fixture.source.requests.count(_.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) shouldBe 1
            _success(fixture.owner.events.query(EventStore.Query(name = Some("job.cancelled")))).size shouldBe 1
            _success(other.events.query(EventStore.Query(name = Some("job.cancelled")))) shouldBe empty
            other.engine._lifecycle_failure(id) shouldBe None
          }
        }
      }
    }

    "terminal preprocessing preserves aggregate evidence" must _scenario("NRT-K") {
      "refuse malformed root/child aggregates through the actual Bridge and acknowledge a valid absent-action missing terminal once" in {
        for (relation <- Vector("root", "child"); mode <- Vector("unknown", "stray", "blank", "model-recovery", "record-recovery", "valid")) {
          Given(s"NRT-K: actual SQLite Bridge checkpoints locally close the selected $relation as Succeeded and the opposite task as Failed, retaining Running/Pending before a $mode aggregate")
          _with_database() { fixture =>
            given ExecutionContext = fixture.context
            val index = if (relation == "root") 0 else 1
            val root = new Task(_ => if (index == 0) _ok else TaskFailed(_failure_conclusion))
            val child = new Task(_ => if (index == 1) _ok else TaskFailed(_failure_conclusion), relationkind = "child")
            val id = _success(fixture.engine.submit(List(root, child), fixture.context))
            val base = fixture.engine._get_record(id).get
            _success(fixture.bridge.establishStartIntent(base))
            _success(fixture.bridge.establishRunningIntent(base))
            val rootid = TaskId("cncf", "runtime", Some(_now), Some("closed-root"))
            val childid = TaskId("cncf", "runtime", Some(_now), Some("closed-child"))
            var record = base.copy(status = JobStatus.Running, updatedAt = _now.plusMillis(4))
            Vector(rootid -> None, childid -> Some(rootid)).zipWithIndex.foreach { case ((taskid, parent), ordinal) =>
              val succeeded = ordinal == index
              val startedat = _now.plusMillis(3L + ordinal * 4L)
              val finishedat = startedat.plusMillis(1L)
              val sequence = record.timeline.map(_.sequence).maxOption.getOrElse(0L) + 1L
              val running = _closed_model(taskid, parent, finished = false).copy(startedAt = startedat)
              record = record.copy(taskReadModels = record.taskReadModels :+ running,
                timeline = record.timeline :+ JobTimelineEvent(sequence = sequence, occurredAt = startedat,
                  kind = "task.running", taskId = Some(taskid), parentTaskId = parent, note = None),
                updatedAt = running.startedAt)
              _success(fixture.bridge.establishTaskStart(record,
                DurableJobLifecycleWriteRequest.TaskStartIntent(id, taskid, parent, record.taskReadModels)))
              record = record.copy(taskReadModels = record.taskReadModels.init :+ running.copy(
                status = if (succeeded) JobTaskStatus.Succeeded else JobTaskStatus.Failed, finishedAt = Some(finishedat),
                result = JobTaskResultSummary(succeeded, Some(if (succeeded) "ok" else "original failure")),
                transactionOutcome = Some(if (succeeded) "committed" else "failed")),
                timeline = record.timeline ++ Vector(
                  JobTimelineEvent(sequence = sequence + 1L, occurredAt = finishedat,
                    kind = if (succeeded) "task.succeeded" else "task.failed", taskId = Some(taskid), parentTaskId = parent,
                    note = Option.when(!succeeded)("original failure")),
                  JobTimelineEvent(sequence = sequence + 2L, occurredAt = finishedat,
                    kind = if (succeeded) "task.transaction.committed" else "task.transaction.failed",
                    taskId = Some(taskid), parentTaskId = parent, note = None)),
                updatedAt = finishedat)
              _success(fixture.bridge.establishTaskOutcome(record,
                DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(id, taskid, parent, record.taskReadModels)))
            }
            val prior = _raw_job(fixture, id)
            _signed(fixture, id).body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
            _signed(fixture, id).body.result shouldBe DurableResultOutcome.Pending
            val selected = record.taskReadModels(index)
            val aggregate = selected.copy(compensationActionRef = None, compensatesTaskId = None,
              compensationStatus = if (mode == "stray") None else Some(if (mode == "unknown") "unknown" else "missing"),
              compensationFailureSummary = Some(if (mode == "blank") " " else "closed missing action"),
              recoveryRequired = mode != "model-recovery")
            val terminal = record.copy(status = JobStatus.Failed, result = Some(JobResult.Failure(_failure_conclusion)),
              updatedAt = _now.plusMillis(10), taskReadModels = record.taskReadModels.updated(index, aggregate),
              retry = record.retry.copy(recoveryRequired = mode != "record-recovery"))
            fixture.source.terminalstatus = Some(JobStatus.Failed)
            fixture.source.terminalrecovery = Some(terminal.retry.recoveryRequired)
            val native = _raw_quota(fixture)
            val callbacks = (root.effects, child.effects)
            val sourcecount = fixture.source.requests.size
            val request = DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(id, terminal.taskReadModels)
            When("the original terminal Bridge API receives the complete unchanged aggregate and matching ordinary source descriptors")
            val result = fixture.bridge.establishTerminalOutcome(terminal, request)
            Then("malformed evidence cannot be normalized away; only the closed no-action missing recovery aggregate changes the signed row once")
            result.isSuccess shouldBe (mode == "valid")
            fixture.source.requests.last shouldBe request
            fixture.source.requests.size shouldBe sourcecount + 1
            fixture.source.revisions.last shouldBe 8L
            _raw_quota(fixture) shouldBe native
            (root.effects, child.effects) shouldBe callbacks
            terminal.taskReadModels(index) shouldBe aggregate
            if (mode == "valid") {
              val signed = _signed(fixture, id)
              signed.body.identity.revision shouldBe 8L
              signed.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Failed
              signed.body.lifecycle.retry.recoveryRequired shouldBe true
              signed.body.tasks(index).compensation shouldBe None
            } else {
              result shouldBe a[Consequence.Failure[_]]
              _raw_job(fixture, id) shouldBe prior
              _signed(fixture, id).body.identity.revision shouldBe 7L
            }
          }
        }
      }
    }
  }

  private def _ok: TaskOutcome = TaskSucceeded(OperationResponse.Scalar("ok"))
  private def _failure_conclusion: Conclusion = Consequence.stateInvalid[Unit]("original failure").conclusion
  private def _retry_failure(action: Disposition.UserAction): Conclusion =
    Conclusion.simple("original failure").copy(disposition = Disposition(action))

  private final class Task(initial: ExecutionContext => TaskOutcome = _ => TaskSucceeded(OperationResponse.Scalar("ok")),
    relationkind: String = "root") extends JobTask {
    var body = initial
    var companion = Option.empty[JobTask]
    var declared = Option.empty[String]
    var oncompensation: () => Unit = () => ()
    private var _metadata = Option.empty[() => Unit]
    val getters = new AtomicInteger(0)
    val runs = new AtomicInteger(0)
    val compensations = new AtomicInteger(0)
    val canonical = new AtomicInteger(0)
    val successcanonical = new AtomicInteger(0)
    var scopes = Vector.empty[JobCancellationScope]
    def effects: (Int, Int, Int, Int) = (getters.get(), runs.get(), compensations.get(), canonical.get())
    def armMetadata(action: () => Unit): Unit = { _metadata = Some(action) }
    def actionId: ActionId = {
      _metadata.foreach(_())
      getters.incrementAndGet()
      ActionId("cncf", "runtime", Some(_now), Some("action"))
    }
    override def taskKind = "operation"
    override def targetKind = Some("operation")
    override def relation = Some(relationkind)
    override def transactionRole = Some("own")
    override def transactionScope = Some("per-task")
    override def componentName = Some("component-a")
    override def serviceName = Some("service-a")
    override def operationName = Some("run")
    override def compensationActionRef = declared
    override def compensationTask = { compensations.incrementAndGet(); oncompensation(); companion }
    def run(ctx: ExecutionContext): TaskOutcome = {
      runs.incrementAndGet()
      scopes :+= ctx.jobContext.cancellationScope.get
      body(ctx)
    }
    override def observeCanonicalOutcome(outcome: TaskOutcome, ctx: ExecutionContext, cancelled: Boolean): Unit = {
      canonical.incrementAndGet()
      if (outcome.isInstanceOf[TaskSucceeded]) successcanonical.incrementAndGet()
      ()
    }
  }

  private final class RuntimeClock extends Clock with JobTimeSource {
    val manual = new ManualJobTimeSource(_now)
    val calls = new AtomicInteger(0)
    var overflowonce = false
    def peek: Instant = manual.now()
    def now(): Instant = {
      calls.incrementAndGet()
      if (overflowonce) { overflowonce = false; Instant.MAX } else peek
    }
    def instant(): Instant = now()
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = Clock.fixed(peek, zone)
  }
  private final class RuntimeTimer(clock: RuntimeClock) extends JobTimer {
    private val _delegate = new InMemoryJobEngine.ManualJobTimer(clock.manual)
    val calls = new AtomicInteger(0)
    var failnext = false
    def pendingCount: Int = _delegate.pendingCount
    def schedule(dueat: Instant)(body: => Unit): JobTimerRegistration = {
      calls.incrementAndGet()
      if (failnext) { failnext = false; throw new IllegalStateException("private schedule refusal") }
      _delegate.schedule(dueat)(body)
    }
    def advanceBy(duration: Duration): Int = _delegate.advanceBy(duration)
    override def shutdown(): Unit = _delegate.shutdown()
  }
  private final case class Owner(engine: InMemoryJobEngine, context: ExecutionContext,
    clock: RuntimeClock, timer: RuntimeTimer,
    ids: JobInputBoundsFixtures.CountingIds, events: EventStore)
  private def _owner(state: InMemoryJobEngine.State, schedule: InMemoryJobEngine.RetrySchedule): Owner = {
    val clock = new RuntimeClock
    val timer = new RuntimeTimer(clock)
    val ids = new JobInputBoundsFixtures.CountingIds
    // Provider timestamps use a fixed context clock; candidate engine effects
    // use RuntimeClock and RuntimeTimer as separate deterministic observation domains.
    val context = ExecutionContext.withIdGenerationContext(ExecutionContext.create(Clock.fixed(_now, ZoneOffset.UTC)), ids)
    val events = EventStore.inMemory
    val engine = new InMemoryJobEngine(state, schedule, InMemoryJobEngine.SchedulerConfig(1, false), clock,
      Some(timer))(scala.concurrent.ExecutionContext.global)
    engine.withEventStore(events)
    Owner(engine, context, clock, timer, ids, events)
  }
  private final case class Fixture(owner: Owner, provider: SqlDataStore, entity: ObservedEntityStore,
    store: DurableJobStore, quota: DurableJobLifetimeQuota, source: Source,
    bridge: DurableJobLifecycleWriteBridge, schedule: InMemoryJobEngine.RetrySchedule) {
    private var _owners = Vector(owner)
    def engine: InMemoryJobEngine = owner.engine
    def context: ExecutionContext = owner.context
    def addOwner(): Owner = {
      val added = _owner(engine.runtimeState, schedule)
      _owners :+= added
      added
    }
    def close(): Unit = {
      try _owners.reverse.foreach(_.engine.shutdown()) finally { val _ = provider.closeC() }
    }
  }
  private def _with_database(lifetime: JobLifetimePolicy = JobLifetimePolicy.default,
    resources: JobResourcePolicy = JobResourcePolicy.default,
    schedule: InMemoryJobEngine.RetrySchedule = _short_retry)(body: Fixture => Unit): Unit = {
    val area = Paths.get("target", "durable-job-lifetime-runtime", "work")
    val _ = Files.createDirectories(area)
    val root = Files.createTempDirectory(area, "native-")
    var opened = Option.empty[Fixture]
    var provider = Option.empty[SqlDataStore]
    var owner = Option.empty[Owner]
    try {
      val state = InMemoryJobEngine.State()
      _success(state.bindLifetimePolicy(lifetime))
      _success(state.bindResourcePolicy(resources))
      val base = _owner(state, schedule)
      owner = Some(base)
      val sqlite = SqlDataStore.sqlite(root.resolve("jobs.db").toString)
      provider = Some(sqlite)
      val entity = new ObservedEntityStore
      base.context.dataStoreSpace.useDataStore(sqlite)
      base.context.entityStoreSpace.addEntityStore(entity)
      _install_job_entity(entity, base.context)
      val store = new DurableJobStore(entity)
      val policy = DurableJobLifetimeQuotaPolicy.default.copy(lifetime = lifetime)
      val quota = store.lifetimeQuota(policy)
      val source = new Source(schedule.maxRetries)
      entity.acknowledge = () => source.acknowledge()
      val bridge = new DurableJobLifecycleWriteBridge(store, source, policy)
      base.engine.bindDurableLifecycleWriteBridge(bridge)
      val fixture = Fixture(base, sqlite, entity, store, quota, source, bridge, schedule)
      opened = Some(fixture)
      body(fixture)
    } finally {
      if (opened.nonEmpty) opened.get.close()
      else {
        owner.foreach(_.engine.shutdown())
        provider.foreach(value => { val _ = value.closeC() })
      }
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => { val _ = Files.deleteIfExists(path) })
      finally stream.close()
    }
  }
  private def _install_job_entity(entity: StandardEntityStore, context: ExecutionContext): Unit = {
    val declared = JobControlComponent.componentDescriptors.flatMap(_.entityRuntimeDescriptors)
      .find(_.collectionId == JobEntityCollections.Job).get
    val binding = _success(EntityRevisionBinding.resolve(
      EntityRevisionModelMetadata(declared.revisionModelKind.get, None), declared.revisionRepresentation))
    val persistent = JobEntity.entityPersistent
    val realm = new EntityRealm[JobEntity](entityName = declared.entityName,
      loader = EntityLoader.fromEntityStore(entity)(using persistent, context),
      state = new DurableJobStoreEntityRealmRef(EntityRealmState(Map.empty)))
    val descriptor = EntityDescriptor[JobEntity](collectionId = declared.collectionId,
      plan = EntityRuntimePlan[JobEntity](entityName = declared.entityName,
        memoryPolicy = EntityMemoryPolicy.StoreOnly, workingSet = None,
        partitionStrategy = declared.partitionStrategy, maxPartitions = declared.maxPartitions,
        maxEntitiesPerPartition = declared.maxEntitiesPerPartition,
        concurrencyPolicy = declared.concurrencyPolicy.getOrElse(EntityConcurrencyPolicy.default)),
      persistent = persistent, revisionBinding = binding)
    context.entitySpace.registerEntity(declared.entityName, new EntityCollection(descriptor, EntityStorage(realm)))
  }

  private final class Source(maxattempts: Int) extends (DurableJobLifecycleWriteRequest => Consequence[DurableJobLifecycleWriteEvidence]) {
    private var _acknowledged = 0L
    private var _requests = Vector.empty[DurableJobLifecycleWriteRequest]
    private var _revisions = Vector.empty[Long]
    var refuse = Option.empty[DurableJobLifecycleWriteBoundary]
    var exhausted = false
    var terminalstatus = Option.empty[JobStatus]
    var terminalrecovery = Option.empty[Boolean]
    var attemptcount = 1
    def acknowledge(): Unit = { _acknowledged += 1L }
    def requests = _requests
    def revisions = _revisions
    def apply(request: DurableJobLifecycleWriteRequest): Consequence[DurableJobLifecycleWriteEvidence] = {
      _requests :+= request
      val revision = _acknowledged + 1L
      _revisions :+= revision
      if (refuse.contains(request.boundary)) Consequence.stateInvalid("private-source authorization /private/path root-token")
      else {
        val models = request match {
          case DurableJobLifecycleWriteRequest.TaskStartIntent(_, _, _, tasks) => tasks
          case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(_, _, _, tasks) => tasks
          case DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(_, tasks) => tasks
          case _ => Vector.empty
        }
        val terminal = request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
        val normal = models.filterNot(_.relation.contains("compensation"))
        val status = terminalstatus.getOrElse(if (normal.lastOption.exists(_.status == JobTaskStatus.Failed)) JobStatus.Failed else JobStatus.Succeeded)
        val failure = DurableFailureSummary("execution", "failed", "original failure", false)
        val result = if (!terminal) DurableResultOutcome.Pending else status match {
          case JobStatus.Succeeded => DurableResultOutcome.Succeeded(DurableValue.Absent)
          case JobStatus.Cancelled => DurableResultOutcome.Cancelled(Some(failure))
          case _ => DurableResultOutcome.Failed(failure)
        }
        val attempts = if (!terminal) Vector.empty else normal.filter(_.parentTaskId.isEmpty).take(attemptcount).zipWithIndex.map {
          case (model, index) => DurableAttemptEvidence(index + 1, model.startedAt,
            Some(normal.flatMap(_.finishedAt).max),
            if (index < attemptcount - 1) DurableAttemptOutcome.Failed
            else status match {
              case JobStatus.Succeeded => DurableAttemptOutcome.Succeeded
              case JobStatus.Cancelled => DurableAttemptOutcome.Cancelled
              case _ => DurableAttemptOutcome.Failed
            }, if (status == JobStatus.Succeeded && index == attemptcount - 1) None else Some(failure))
        }
        val supplied = JobInputBoundsFixtures.evidence(revision)
        Consequence.success(supplied.copy(projection = supplied.projection.copy(
          tasks = if (models.nonEmpty) models.map(_descriptor) else supplied.projection.tasks,
          result = result, retry = DurableRetryEvidence(attempts, maxattempts, None,
            terminal && exhausted,
            if (terminal) terminalrecovery.getOrElse(status == JobStatus.Failed || exhausted || models.exists(_.recoveryRequired)) else false))))
      }
    }
  }
  private def _descriptor(model: JobTaskReadModel): DurableTaskDescriptor = {
    val relation = _success_or_fail(DurableTaskRelationKind.parse(model.relation.get))
    val outcome = model.transactionOutcome.get match {
      case "running" => DurableTransactionOutcome.Pending
      case "committed" => DurableTransactionOutcome.Committed
      case "failed" => DurableTransactionOutcome.Failed
      case _ => DurableTransactionOutcome.Compensated
    }
    val compensation = if (model.compensatesTaskId.nonEmpty) Some(DurableCompensationDescriptor(
      DurableOperationReference(model.component.get, model.service, model.compensationActionRef.get, None),
      model.compensatesTaskId.get.value, _success_or_fail(DurableCompensationStatus.parse(model.compensationStatus.get)),
      model.compensationFailureSummary.map(value => DurableFailureSummary("compensation", "failed", value, false)))) else None
    DurableTaskDescriptor(model.taskId.value, model.parentTaskId.map(_.value), DurableTaskKind.Operation,
      DurableTaskTarget(model.targetKind.get, DurableOperationReference(model.component.get, model.service, model.operation.get, None)),
      DurableTaskRelation(relation, model.parentTaskId.map(_.value)),
      DurableTransactionDescriptor(_success_or_fail(DurableTransactionRole.parse(model.transactionRole.get)),
        _success_or_fail(DurableTransactionScope.parse(model.transactionScope.get)), outcome), compensation)
  }
  private def _success_or_fail[A](result: Either[String, A]): A = result.fold(fail(_), identity)

  private final class ObservedEntityStore extends StandardEntityStore {
    var acknowledge: () => Unit = () => ()
    var onjobcreate = Option.empty[() => Unit]
    var onjoback = Option.empty[() => Unit]
    var onquotaack = Option.empty[() => Unit]
    var failload = false
    var refusequotaloadonce = false
    var quotaloadrefusals = 0
    private var _armed = false
    private var _precommit = false
    private var _remaining = 1
    var deliveries = 0
    var quotawrites = 0
    var loads = 0
    def arm(precommit: Boolean = false, after: Int = 1): Unit = {
      _armed = true; _precommit = precommit; _remaining = after
    }
    override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[Option[EntityRevisionCarrier[T]]] = {
      loads += 1
      if (refusequotaloadonce && id.collection == DurableJobLifetimeQuotaEntity.Collection) {
        refusequotaloadonce = false
        quotaloadrefusals += 1
        throw new IllegalStateException("private quota read refusal /private/path authorization root-token")
      }
      if (failload) throw new IllegalStateException("private provider refusal /private/path authorization root-token")
      super.loadDetached(id)
    }
    override def create[T](entity: T, options: EntityCreateOptions)(using tc: EntityPersistentCreate[T], ctx: ExecutionContext)
        : Consequence[CreateResult[T]] = {
      val result = super.create(entity, options)
      if (result.isSuccess && tc.collection(entity) == DurableJobStoreEntity.Collection) {
        acknowledge()
        onjobcreate.foreach(_())
      }
      result
    }
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
        (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] = {
      val quota = tc.id(changes).collection == DurableJobLifetimeQuotaEntity.Collection
      val job = tc.id(changes).collection == DurableJobStoreEntity.Collection
      if (quota) quotawrites += 1
      val fault = quota && _armed && { _remaining -= 1; _remaining == 0 }
      if (fault && _precommit) _lose_ack()
      val result = super.updateDetached(changes, expectedRevision, executionPolicy)
      if (job && result.isSuccess) {
        acknowledge()
        onjoback.foreach(_())
      }
      if (fault && result.isSuccess) _lose_ack()
      if (quota && result.isSuccess) onquotaack.foreach(_())
      result
    }
    private def _lose_ack(): Nothing = {
      _armed = false
      deliveries += 1
      throw new IllegalStateException("private-lost-ack /private/path authorization root-token")
    }
  }

  private def _with_running(fixture: Fixture, root: Task, mode: JobRunMode = JobRunMode.Async)
    (body: (JobId, ExecutionContext) => Unit): Unit = {
    val entered = new CountDownLatch(1)
    val released = new CountDownLatch(1)
    val context = new AtomicReference[ExecutionContext]()
    val error = new AtomicReference[Throwable]()
    val original = root.body
    root.body = ctx => {
      val outcome = original(ctx)
      context.set(ctx)
      entered.countDown()
      _await(released)
      outcome
    }
    if (mode == JobRunMode.Async) _success(fixture.engine.submit(List(root), fixture.context))
    val actor = new Thread(() => try {
      if (mode == JobRunMode.Sync) _success(fixture.engine.submit(List(root), fixture.context, JobSubmitOption(runMode = mode)))
      else fixture.engine.drainOne()
      ()
    } catch { case failure: Throwable => error.set(failure) }, "native-runtime-root")
    actor.start()
    try {
      _await(entered)
      body(context.get().jobContext.jobId.get, context.get())
    } finally {
      released.countDown()
      _join(Vector(actor))
      if (error.get() != null) throw error.get()
    }
  }
  private def _race[A, B](left: => A, right: => B): (A, B) = {
    val ready = new CountDownLatch(2)
    val start = new CountDownLatch(1)
    val leftresult = new AtomicReference[Either[Throwable, A]]()
    val rightresult = new AtomicReference[Either[Throwable, B]]()
    val leftactor = new Thread(() => { ready.countDown(); try { _await(start); leftresult.set(Right(left)) }
      catch { case failure: Throwable => leftresult.set(Left(failure)) } }, "native-runtime-left")
    val rightactor = new Thread(() => { ready.countDown(); try { _await(start); rightresult.set(Right(right)) }
      catch { case failure: Throwable => rightresult.set(Left(failure)) } }, "native-runtime-right")
    val actors = Vector(leftactor, rightactor)
    actors.foreach(_.start())
    try {
      _await(ready)
      start.countDown()
      _join(actors)
      def _value_[T](result: Either[Throwable, T]): T = result match {
        case Right(value) => value
        case Left(error) => throw error
        case null => fail("bounded native actor returned no result")
      }
      _value_(leftresult.get()) -> _value_(rightresult.get())
    } finally { start.countDown(); _join(actors) }
  }
  private def _await(latch: CountDownLatch): Unit =
    if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("bounded native runtime barrier timed out")
  private def _join(actors: Vector[Thread]): Unit = actors.foreach { actor =>
    actor.join(5000)
    if (actor.isAlive) { actor.interrupt(); actor.join(5000) }
    actor.isAlive shouldBe false
  }
  private def _closed_model(id: TaskId, parent: Option[TaskId], finished: Boolean): JobTaskReadModel =
    JobTaskReadModel(id, parent, if (finished) JobTaskStatus.Succeeded else JobTaskStatus.Running,
      _now.plusMillis(3), Option.when(finished)(_now.plusMillis(4)), JobTaskResultSummary(true, None),
      Some("component-a"), Some("service-a"), Some("run"), "operation", Some("operation"),
      Some(if (parent.isEmpty) "root" else "child"), Some("own"), Some("per-task"),
      Some(if (finished) "committed" else "running"))
  private def _control(owner: Owner, id: JobId, command: JobControlCommand): Consequence[JobControlResponse] =
    owner.engine.control(id, JobControlRequest(command), new JobControlPolicy {
      def authorize(id: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = Consequence.unit
    })(using owner.context)
  private def _facts(owner: Owner, task: Task) =
    (owner.engine.resourceSnapshot, owner.ids.calls.get(), owner.clock.calls.get(),
      _success(owner.events.query(EventStore.Query())).size, owner.timer.calls.get(), owner.timer.pendingCount,
      owner.engine._work_queue.size(), task.effects)
  private def _binding(fixture: Fixture, id: JobId): JobDurableLifetimeRuntimeBinding =
    fixture.engine.runtimeState.synchronized(fixture.engine.runtimeState._native_lifetime_bindings(id))
  private def _native(fixture: Fixture, id: JobId): DurableJobLifetimeQuotaSnapshot =
    _success(fixture.store.lifetimeQuota(_ledger(fixture).policy).snapshot(id.value, _access)(using fixture.context))
  private def _signed(fixture: Fixture, id: JobId): DurableJobRecord =
    _success(fixture.store.load(id.value, _access)(using fixture.context)).get.record
  private def _job_collection = DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection)
  private def _quota_collection = DataStore.CollectionId.EntityStore(DurableJobLifetimeQuotaEntity.Collection)
  private def _quota_id: EntityId = _success(DurableJobLifetimeQuotaEntity.entityId)
  private def _raw_job(fixture: Fixture, id: JobId): Record =
    _success(fixture.provider.load(_job_collection, DataStore.EntryId(_success(DurableJobStoreEntity.entityId(id.value))))(using fixture.context)).get
  private def _raw_quota(fixture: Fixture): Record =
    _success(fixture.provider.load(_quota_collection, DataStore.EntryId(_quota_id))(using fixture.context)).get
  private def _ledger(fixture: Fixture): DurableJobLifetimeQuotaRecord =
    _success(DurableJobLifetimeQuotaCodec.decode(_raw_quota(fixture).getString("canonicalJson").get))
  private def _replace_quota(fixture: Fixture, text: String): Unit =
    _success(fixture.provider.save(_quota_collection, DataStore.EntryId(_quota_id),
      _raw_quota(fixture).upsertSingle("canonicalJson", text))(using fixture.context))
  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
  private def _failure[A, B](actual: Consequence[A], expected: Consequence[B]): Unit = (actual, expected) match {
    case (Consequence.Failure(value), Consequence.Failure(reference)) =>
      value.status shouldBe reference.status
      value.observation.taxonomy shouldBe reference.observation.taxonomy
      value.observation.cause.kind shouldBe reference.observation.cause.kind
      value.observation.phenomenon shouldBe reference.observation.phenomenon
      value.displayMessage shouldBe reference.displayMessage
    case _ => fail("expected fixed closed native lifetime refusal")
  }
  private def _redacted[A](result: Consequence[A]): Unit = result match {
    case Consequence.Failure(value) =>
      Vector("private-source", "private-lost-ack", "/private/path", "root-token", "authorization")
        .foreach(sentinel => value.show should not include sentinel)
    case _ => fail("expected a closed refusal")
  }
}
