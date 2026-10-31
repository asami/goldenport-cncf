package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimePlan, EntityStorage}
import org.goldenport.cncf.unitofwork.{CommitRecorder, PrepareResult, TransactionContext}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Actual configured root acknowledgement and publication against native SQLite.
 * Fresh owners here establish library persistence, not runtime recovery or
 * real cross-process/CBD Support acceptance. Runtime dispatch associates paid IDs.
 *
 * @since   Oct.  2, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class JobDurableLifetimeAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _access = JobInputBoundsFixtures.access
  private val _now = JobInputBoundsFixtures.instant
  private val _default = DurableJobLifetimeQuotaPolicy.default
  private val _nra1 = afterWord("in spec:durable-job-lifetime-quota-contract, example:NRA1, rules:R1,R3,R4,R6, phase:69.7, slice:JM69-09B3B3C2B2A")
  private val _nra2 = afterWord("in spec:durable-job-lifetime-quota-contract, example:NRA2, rules:R1,R4,R6, phase:69.7, slice:JM69-09B3B3C2B2A")
  private val _nra3 = afterWord("in spec:durable-job-lifetime-quota-contract, example:NRA3, rules:R1,R4,R6, phase:69.7, slice:JM69-09B3B3C2B2A")
  private val _nra4 = afterWord("in spec:durable-job-lifetime-quota-contract, example:NRA4, rules:R3,R4,R6, phase:69.7, slice:JM69-09B3B3C2B2A")
  private val _nra5 = afterWord("in spec:durable-job-lifetime-quota-contract, example:NRA5, rules:R3,R4,R6, phase:69.7, slice:JM69-09B3B3C2B2A")
  private val _nra6 = afterWord("in spec:durable-job-lifetime-quota-contract, example:NRA6, rules:R3,R4,R6, phase:69.7, slice:JM69-09B3B3C2B2A")
  private val _nra7 = afterWord("in spec:durable-job-lifetime-quota-contract, example:NRA7, rules:R2,R3,R4,R6, phase:69.7, slice:JM69-09B3B3C2B2A")
  private val _nra8 = afterWord("in spec:durable-job-lifetime-quota-contract, example:NRA8, rules:R1,R3,R4,R6, phase:69.7, slice:JM69-09B3B3C2B2A")

  "Configured Persistent native root admission" should {
    "acknowledged publication" must _nra1 {
      "NRA1 publish before Sync or queued Async task callbacks and retain the six original checkpoints" in {
        Vector(JobRunMode.Sync, JobRunMode.Async).foreach { mode =>
          Given(s"a real SQLite provider, standard EntityStore and existing manual engine/task fixture in $mode mode")
          _with_database(_default) { fixture =>
            given ExecutionContext = fixture.context
            var pending = Vector.empty[DurableJobLifetimeQuotaEntry]
            var roots = Vector.empty[DurableJobStoreSnapshot]
            var callbacks = Vector.empty[(DurableJobLifetimeQuotaEntry, DurableJobStoreSnapshot)]
            fixture.entity.beforeJobCreate { jobid =>
              pending :+= _ledger(fixture).entries.find(_.jobid == jobid).get
            }
            fixture.entity.afterJobCreate { jobid =>
              roots :+= _success(fixture.store.load(jobid, _access)).get
            }
            val task = new ProbeTask(new JobInputBoundsFixtures.CountingTask, context => {
              val jobid = context.jobContext.jobId.get
              callbacks :+= _ledger(fixture).entries.find(_.jobid == jobid.value).get ->
                _success(fixture.store.load(jobid.value, _access)(using context)).get
            })
            When("the actual configured root submits and its queued Async work is explicitly drained")
            val jobid = _success(fixture.engine.submit(List(task), fixture.context, JobSubmitOption(runMode = mode)))
            val admitted = _success(fixture.store.load(jobid.value, _access)).get
            val binding = fixture.bridge.lifetimeBinding(jobid)
            val beforeruns = task.counter.runs.get()
            val beforequeue = fixture.engine._work_queue.size()
            val drained = if (mode == JobRunMode.Async) fixture.engine.drainAll() else 0
            val finished = _success(fixture.store.load(jobid.value, _access)).get
            val native = _success(fixture.quota.snapshot(jobid.value, _access))
            Then("Pending preceded the actual revision-one signed create, Published preceded the body, and original revisions/results survive")
            pending.size shouldBe 1
            pending.head.rootstate shouldBe DurableJobLifetimeQuotaRootState.Pending
            pending.head.snapshot.seed shouldBe _seed(1, 0L)
            roots.size shouldBe 1
            roots.head.record.body.identity.revision shouldBe 1L
            roots.head.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Submitted
            fixture.bridge.hasAdmittedSnapshot(jobid) shouldBe true
            binding shouldBe defined
            binding.get.access shouldBe _access
            binding.get.quota.snapshot(jobid.value, binding.get.access) shouldBe Consequence.Success(native)
            val owned = fixture.engine.runtimeState._state_monitor.synchronized {
              fixture.engine.runtimeState._native_lifetime_bindings.get(jobid).get
            }
            owned.native shouldBe binding.get
            (owned.context eq fixture.context) shouldBe true
            (owned.bridge.exists(_ eq fixture.bridge)) shouldBe true
            (owned.scope eq fixture.engine._cancellation_scope(jobid)) shouldBe true
            if (mode == JobRunMode.Async) {
              admitted.record.body.identity.revision shouldBe 1L
              beforeruns shouldBe 0
              beforequeue shouldBe 1
              drained shouldBe 1
            } else {
              beforeruns shouldBe 1
              beforequeue shouldBe 0
            }
            callbacks.size shouldBe 1
            callbacks.head._1.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
            callbacks.head._2.record.body.identity.revision shouldBe 4L
            callbacks.head._2.record.body.result shouldBe DurableResultOutcome.Pending
            fixture.source.requests.map(_.boundary) shouldBe Vector(
              DurableJobLifecycleWriteBoundary.Admission, DurableJobLifecycleWriteBoundary.StartIntent,
              DurableJobLifecycleWriteBoundary.RunningIntent, DurableJobLifecycleWriteBoundary.TaskStartIntent,
              DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint, DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
            fixture.source.revisions shouldBe Vector(1L, 2L, 3L, 4L, 5L, 6L)
            finished.record.body.identity.revision shouldBe 6L
            finished.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
            finished.record.body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
            fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
            task.counter.runs.get() shouldBe 1
            fixture.engine.resourceSnapshot.pending shouldBe 0
            fixture.engine.resourceSnapshot.executing shouldBe 0
            native.seed shouldBe _seed(1, 0L).copy(remaining = 0L,
              normalids = fixture.engine._get_record(jobid).get.taskReadModels.map(_.taskId.value).toSet)
            native.cancelclaimed shouldBe false
            native.seed.normalids shouldBe finished.record.body.tasks.map(_.taskId).toSet
            native.seed.remaining shouldBe 0L
          }
        }
      }
    }

    "pure inclusive admission" must _nra2 {
      "NRA2 use actual State lower-only policy and exact task/note counts before evidence or native effects" in {
        Vector((1, 0L), (2, 1L)).foreach { case (count, notes) =>
          Given("a State policy whose task/history/admission capacities exactly fit the complete root batch and notes")
          val policy = _default.copy(lifetime = JobLifetimePolicy(2L * count, 32L + 64L * count + notes, 1L))
          _with_database(policy) { fixture =>
            given ExecutionContext = fixture.context
            val tasks = List.fill(count)(new JobInputBoundsFixtures.CountingTask)
            val suppliednotes = Vector.fill(notes.toInt)("closed note")
            val option = JobSubmitOption(executionNotes = suppliednotes)
            When("the real engine admits the inclusive complete candidate")
            val jobid = _success(fixture.engine.submit(tasks, fixture.context, option))
            val signed = _success(fixture.store.load(jobid.value, _access)).get
            val canonical = _success(DurableJobRecordCodec.canonicalJson(signed.record))
            val native = _success(fixture.quota.snapshot(jobid.value, _access))
            Then("the exact State policy is persisted without enlarging global limits and original task vectors/counts remain intact")
            fixture.context.entitySpace.entityOption(JobEntityCollections.Job).get.descriptor.revisionBinding shouldBe
              Some(EntityRevisionBinding(EntityRevisionRepresentation.Detached))
            _ledger(fixture).policy shouldBe policy
            native.seed shouldBe _seed(count, notes)
            fixture.engine._get_record(jobid).get.tasks shouldBe tasks
            fixture.engine._get_record(jobid).get.debug.executionNotes shouldBe suppliednotes
            fixture.engine._get_record(jobid).get.debug.executionNotes.exists(_.contains("cncf.job.entitySync-failed")) shouldBe false
            DurableJobRecordCodec.decode(canonical, _access) shouldBe Consequence.Success(signed.record)
            fixture.source.requests shouldBe Vector(DurableJobLifecycleWriteRequest.Admission)
            tasks.foreach(_.runs.get() shouldBe 0)
            fixture.entity.jobcreates shouldBe 1
          }
        }
        Vector((JobLifetimePolicy(1L, 96L, 1L), 1, Vector.empty[String]),
          (JobLifetimePolicy(2L, 95L, 1L), 1, Vector.empty[String]),
          (JobLifetimePolicy(2L, 96L, 1L), 2, Vector.empty[String]),
          (JobLifetimePolicy(2L, 96L, 1L), 1, Vector("one over"))).foreach { case (lifetime, count, notes) =>
          Given("an actual lower-only State with one-below capacity or a one-over complete task/note batch")
          _with_database(_default.copy(lifetime = lifetime)) { fixture =>
            val task = new JobInputBoundsFixtures.CountingTask
            val before = fixture.base.observation(task)
            When("the actual engine preflights the complete root before IDs, getters, evidence and leases")
            val result = fixture.engine.submit(List.fill(count)(task), fixture.context, JobSubmitOption(executionNotes = notes))
            Then("pure refusal conserves every observed engine fact and makes no evidence/native call")
            _failure(result, JobResourcePolicy.refused[Unit])
            fixture.base.observation(task) shouldBe before
            fixture.source.requests shouldBe empty
            fixture.entity.jobcreates shouldBe 0
            fixture.entity.quotacreates shouldBe 0
            _raw_quota(fixture) shouldBe None
          }
        }
        Given("the bridge directly receives invalid task/note structure or an invalid complete native policy")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          val task = new JobInputBoundsFixtures.CountingTask
          val record = _submitted_record(fixture, List(task))
          val gettercount = task.getters.get()
          val invalidbridge = new DurableJobLifecycleWriteBridge(fixture.store, fixture.source,
            _default.copy(maxledgerjobs = 0))
          When("pure bridge admission checks null task/note batches and the configured global policy before one Admission request")
          val tasks = fixture.bridge.admit(record.copy(tasks = null))
          val notes = fixture.bridge.admit(record.copy(debug = record.debug.copy(executionNotes = Vector(null))))
          val policy = invalidbridge.admit(record)
          val precedence = invalidbridge.admit(record.copy(status = JobStatus.Running))
          Then("task/note capacity taxonomy and native invalid policy stay closed, while Submitted precedence stays first")
          _failure(tasks, JobResourcePolicy.refused[Unit])
          _failure(notes, JobResourcePolicy.refused[Unit])
          _failure(policy, DurableJobLifetimeQuotaPolicy.invalid[Unit])
          _failure(precedence, Consequence.stateInvalid[Unit]("durable lifecycle write requires a Submitted job without a result"))
          task.getters.get() shouldBe gettercount
          fixture.source.requests shouldBe empty
          fixture.entity.quotacreates shouldBe 0
          fixture.entity.jobcreates shouldBe 0
          fixture.bridge.lifetimeBinding(record.id) shouldBe None
        }
      }
    }

    "native global capacity" must _nra3 {
      "NRA3 conserve the first queued root when a second distinct root reaches the native one-entry ceiling" in {
        Given("an actual bridge with maxledgerjobs one and a valid default local lifetime policy")
        _with_database(_default.copy(maxledgerjobs = 1)) { fixture =>
          given ExecutionContext = fixture.context
          val first = new JobInputBoundsFixtures.CountingTask
          val second = new JobInputBoundsFixtures.CountingTask
          val firstid = _success(fixture.engine.submit(List(first), fixture.context))
          val signed = _raw_job(fixture, firstid.value)
          val ledger = _raw_quota(fixture)
          val before = fixture.base.observation(second)
          When("a second distinct root requests native capacity after ordinary root ID allocation")
          val result = fixture.engine.submit(List(second), fixture.context)
          val refusedid = fixture.engine.durableLifecycleWriteFailureFacts.head.jobid
          Then("the first payment/bytes remain exact and the second has no signed row, bridge binding or executable publication")
          _failure(result, JobResourcePolicy.refused[Unit])
          refusedid should not be firstid
          _raw_job(fixture, firstid.value) shouldBe signed
          _raw_quota(fixture) shouldBe ledger
          _success(fixture.store.load(refusedid.value, _access)) shouldBe None
          fixture.bridge.hasAdmittedSnapshot(refusedid) shouldBe false
          fixture.bridge.lifetimeBinding(refusedid) shouldBe None
          fixture.entity.jobcreates shouldBe 1
          fixture.engine.resourceSnapshot shouldBe before.resources
          fixture.engine._work_queue.size() shouldBe before.queue
          fixture.engine.runtimeState.durableJobs.size() shouldBe before.durable
          fixture.engine.runtimeState.runtimeJobs.size() shouldBe before.runtime
          fixture.engine._cancellation_scopes.size() shouldBe before.scopes
          JobLifetimeFixtures.events(fixture.base).size shouldBe before.events
          fixture.base.timer.calls.get() shouldBe before.timers
          fixture.base.timer.pendingCount shouldBe before.pending
          second.runs.get() shouldBe 0
          second.admissions.get() shouldBe 1
          fixture.base.ids.calls.get() should be > before.ids
          _success(fixture.quota.snapshot(firstid.value, _access)).seed shouldBe _seed(1, 0L)
          first.runs.get() shouldBe 0
        }
      }
    }

    "uncertain native reservation" must _nra4 {
      "NRA4 retain actual Pending after a singleton create commits and loses its one acknowledgement" in {
        Given("a real quota singleton create fault armed after the actual StandardEntityStore write")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          val task = new JobInputBoundsFixtures.CountingTask
          val before = fixture.base.observation(task)
          fixture.entity.arm("quota-create")
          When("the configured Persistent engine submits through real native root reservation")
          val result = fixture.engine.submit(List(task), fixture.context)
          val jobid = fixture.engine.durableLifecycleWriteFailureFacts.head.jobid
          val entry = _ledger(fixture).entries.head
          Then("closed unavailable refuses publication, retains the entire Pending payment and releases the physical provisional lease")
          _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          entry.jobid shouldBe jobid.value
          entry.rootstate shouldBe DurableJobLifetimeQuotaRootState.Pending
          entry.snapshot.seed shouldBe _seed(1, 0L)
          fixture.entity.faultdeliveries shouldBe 1
          fixture.entity.quotacreates shouldBe 1
          fixture.entity.jobcreates shouldBe 0
          _success(fixture.store.load(jobid.value, _access)) shouldBe None
          _no_local_candidate(fixture, task, before, jobid)
          fixture.source.requests shouldBe Vector(DurableJobLifecycleWriteRequest.Admission)
        }
      }
    }

    "uncertain signed creation" must _nra5 {
      "NRA5 leave the engine refused and permit only explicit fresh library confirmation of the exact signed root" in {
        Given("the signed Job create actually commits after native Pending and then loses one response")
        _with_database(_default) { first =>
          val task = new JobInputBoundsFixtures.CountingTask
          val before = first.base.observation(task)
          first.entity.arm("job-create")
          When("the configured submission loses the signed create acknowledgement")
          val result = first.engine.submit(List(task), first.context)
          val jobid = first.engine.durableLifecycleWriteFailureFacts.head.jobid
          val signed = _raw_job(first, jobid.value)
          val pending = _ledger(first).entries.head
          Then("the initial signed row exists with Pending payment but no engine or bridge publication is inferred")
          _failure(result, DurableJobStorageResourcePolicy.unavailable[Unit])
          first.entity.jobcreates shouldBe 1
          first.entity.faultdeliveries shouldBe 1
          pending.rootstate shouldBe DurableJobLifetimeQuotaRootState.Pending
          pending.snapshot.seed shouldBe _seed(1, 0L)
          _no_local_candidate(first, task, before, jobid)
          _success(first.store.load(jobid.value, _access)(using first.context)).get.record.body.identity.revision shouldBe 1L
          Given("the failed engine/provider are closed and completely new provider/EntityStore/context/manual engine owners open the same database")
          first.close()
          val fresh = _open(first.database, first.policy)
          try {
            given ExecutionContext = fresh.context
            When("explicit fresh quota recoverRoot authenticates the persisted initial root and repeats its confirmation")
            val recovered = _success(fresh.quota.recoverRoot(jobid.value, _access))
            val confirmed = _raw_quota(fresh)
            val repeated = fresh.quota.recoverRoot(jobid.value, _access)
            Then("library confirmation promotes once and preserves exact signed bytes and paid seed without task or fresh runtime effects")
            recovered.seed shouldBe _seed(1, 0L)
            repeated shouldBe Consequence.Success(recovered)
            _raw_quota(fresh) shouldBe confirmed
            _ledger(fresh).entries.head.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
            _raw_job(fresh, jobid.value) shouldBe signed
            fresh.entity.quotaupdates shouldBe 1
            fresh.bridge.hasAdmittedSnapshot(jobid) shouldBe false
            fresh.bridge.lifetimeBinding(jobid) shouldBe None
            fresh.engine.listJobs() shouldBe empty
            fresh.engine._work_queue.size() shouldBe 0
            JobLifetimeFixtures.events(fresh.base) shouldBe empty
            fresh.base.timer.calls.get() shouldBe 0
            task.runs.get() shouldBe 0
          } finally fresh.close()
        }
      }
    }

    "uncertain native publication" must _nra6 {
      "NRA6 retain actual Published after its versioned update commits and loses one acknowledgement" in {
        Given("a fault in actual quota updateDetached Pending-to-Published after real native OCC persistence")
        _with_database(_default) { first =>
          val task = new JobInputBoundsFixtures.CountingTask
          val before = first.base.observation(task)
          first.entity.arm("quota-publish")
          When("configured admission requests native publication and loses the committed update response")
          val result = first.engine.submit(List(task), first.context)
          val jobid = first.engine.durableLifecycleWriteFailureFacts.head.jobid
          val signed = _raw_job(first, jobid.value)
          val ledger = _raw_quota(first)
          Then("the existing confirmation-unavailable failure conserves signed/Published facts and grants no local success")
          _failure(result, DurableJobLifetimeQuotaPolicy.confirmation[Unit])
          first.entity.faultdeliveries shouldBe 1
          first.entity.quotaupdates shouldBe 1
          first.entity.jobcreates shouldBe 1
          _ledger(first).entries.head.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
          _no_local_candidate(first, task, before, jobid)
          Given("the failed provider and engine close before independent owners reopen the exact database")
          first.close()
          val fresh = _open(first.database, first.policy)
          try {
            given ExecutionContext = fresh.context
            When("the fresh native quota snapshot authenticates the actual Published signed root")
            val actual = _success(fresh.quota.snapshot(jobid.value, _access))
            Then("the exact payment survives without a refund, inferred engine admission or another publication write")
            actual.seed shouldBe _seed(1, 0L)
            _raw_job(fresh, jobid.value) shouldBe signed
            _raw_quota(fresh) shouldBe ledger
            fresh.entity.quotaupdates shouldBe 0
            fresh.bridge.lifetimeBinding(jobid) shouldBe None
            fresh.engine.listJobs() shouldBe empty
            fresh.engine._work_queue.size() shouldBe 0
            task.runs.get() shouldBe 0
          } finally fresh.close()
        }
      }
    }

    "closed authority refusal" must _nra7 {
      "NRA7 conserve prior native bytes on evidence/access/signature/provider/corrupt/legacy refusals without fallback" in {
        Vector("evidence", "access", "revision", "provider").foreach { mode =>
          Given(s"a separate real configured fixture with closed $mode refusal")
          _with_database(_default) { fixture =>
            val task = new JobInputBoundsFixtures.CountingTask
            val before = fixture.base.observation(task)
            val original = Consequence.securityPermissionDenied[DurableJobLifecycleWriteEvidence]("closed evidence refused")
            mode match {
              case "evidence" => fixture.source.refuseAdmission(original)
              case "access" => fixture.source.useAccess(_access.copy(subjectId = "private-subject"))
              case "revision" => fixture.source.useAdmissionRevision(2L)
              case "provider" => fixture.context.dataStoreSpace.useDataStore(new OpaqueDataStore(fixture.provider))
              case _ => fail("unknown closed fixture")
            }
            When("the actual configured Persistent root requests admission")
            val result = fixture.engine.submit(List(task), fixture.context)
            val jobid = fixture.engine.durableLifecycleWriteFailureFacts.head.jobid
            Then("the original evidence failure or fixed closed quota refusal precedes every quota/signed mutation and callback")
            mode match {
              case "evidence" => _failure(result, original)
              case "access" => _failure(result, DurableJobLifetimeQuotaPolicy.invalid[Unit])
              case "revision" => _failure(result, Consequence.argumentInvalid[Unit]("durable Admission evidence must use semantic revision 1"))
              case _ => _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
            }
            _redacted(result)
            fixture.entity.quotacreates shouldBe 0
            fixture.entity.jobcreates shouldBe 0
            _raw_quota(fixture) shouldBe None
            _no_local_candidate(fixture, task, before, jobid)
            fixture.source.requests shouldBe Vector(DurableJobLifecycleWriteRequest.Admission)
          }
        }
        Given("a canonical initial signed value whose signature is then corrupted before the closed helper boundary")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          val record = _closed_record("private-signature-job")
          val invalid = record.copy(integrity = record.integrity.copy(unsignedBodySha256 = "0" * 64))
          When("the exact root helper receives the invalid signed record and supplied access/counts")
          val result = DurableJobLifetimeAdmission.admit(fixture.store, invalid, _access, 1, 0L, _default)
          Then("signature admission refuses before any singleton/signed write and leaves no authority cache")
          _failure(result, DurableJobLifetimeQuotaPolicy.invalid[Unit])
          _redacted(result)
          fixture.entity.quotacreates shouldBe 0
          fixture.entity.jobcreates shouldBe 0
          _raw_quota(fixture) shouldBe None
          fixture.engine.listJobs() shouldBe empty
        }
        Given("a previously accepted authenticated signed Job and now-corrupt existing native lifetime authority")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          val firsttask = new JobInputBoundsFixtures.CountingTask
          val firstid = _success(fixture.engine.submit(List(firsttask), fixture.context))
          val signed = _raw_job(fixture, firstid.value)
          val authorized = fixture.store.load(firstid.value, _access)
          val denied = fixture.store.load(firstid.value, _access.copy(subjectId = "private-subject"))
          val quota = _raw_quota(fixture).get
          _success(fixture.provider.save(_quota_collection, DataStore.EntryId(_quota_id),
            quota ++ Record.dataAuto("canonicalJson" -> "{private-corrupt-body /private/path authorization")))
          val corrupt = _raw_quota(fixture)
          val task = new JobInputBoundsFixtures.CountingTask
          val before = fixture.base.observation(task)
          When("a second actual configured root reaches corrupt native authority")
          val result = fixture.engine.submit(List(task), fixture.context)
          val jobid = fixture.engine.durableLifecycleWriteFailureFacts.head.jobid
          Then("closed invalid preserves complete corrupt metadata and original signed read/auth facts without repair or reset")
          _failure(result, DurableJobLifetimeQuotaPolicy.invalid[Unit])
          _redacted(result)
          _raw_quota(fixture) shouldBe corrupt
          _raw_job(fixture, firstid.value) shouldBe signed
          fixture.store.load(firstid.value, _access) shouldBe authorized
          _failure(fixture.store.load(firstid.value, _access.copy(subjectId = "private-subject")), denied)
          fixture.entity.jobcreates shouldBe 1
          _no_local_candidate(fixture, task, before, jobid)
          firsttask.runs.get() shouldBe 0
        }
        Given("a real legacy signed V1 Job created directly with no lifetime singleton")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          val body = _closed_record("legacy-root").body
          val completed = _now.plusMillis(1L)
          val legacy = _success(DurableJobRecord.create(body.copy(
            identity = body.identity.copy(updatedAt = completed),
            lifecycle = body.lifecycle.copy(status = DurableJobLifecycleStatus.Succeeded,
              retry = DurableRetryEvidence(Vector(DurableAttemptEvidence(1, _now, Some(completed),
                DurableAttemptOutcome.Succeeded, None)), 3, None, false, false),
              schedule = DurableScheduleState(Some(_now), Some(_now), Some(completed))),
            result = DurableResultOutcome.Succeeded(DurableValue.Absent))))
          val stored = _success(fixture.store.create(legacy, _access))
          val signed = _raw_job(fixture, "legacy-root")
          val denied = fixture.store.load("legacy-root", _access.copy(subjectId = "private-subject"))
          val task = new JobInputBoundsFixtures.CountingTask
          val before = fixture.base.observation(task)
          When("an actual configured root requests bootstrap against the nonempty signed Job collection")
          val result = fixture.engine.submit(List(task), fixture.context)
          val jobid = fixture.engine.durableLifecycleWriteFailureFacts.head.jobid
          Then("fixed unavailable conserves the prior signed V1 record and cannot import/reset authority or use another provider")
          legacy.format shouldBe DurableRecordFormat.V1
          stored.record.format shouldBe DurableRecordFormat.V1
          _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          _redacted(result)
          _raw_quota(fixture) shouldBe None
          _raw_job(fixture, "legacy-root") shouldBe signed
          fixture.store.load("legacy-root", _access) shouldBe Consequence.Success(Some(stored))
          _failure(fixture.store.load("legacy-root", _access.copy(subjectId = "private-subject")), denied)
          fixture.entity.quotacreates shouldBe 0
          fixture.entity.jobcreates shouldBe 1
          _no_local_candidate(fixture, task, before, jobid)
        }
      }
    }

    "fixed-seed finite native sequences" must _nra8 {
      "NRA8 match an independent capacity/payment model through at least 24 actual engine and reopened-owner runs" in {
        Given("ScalaCheck seed6970933621, global ledger caps one through three and eight bounded one-to-four-task roots per real SQLite run")
        var firstfailure = Option.empty[String]
        val property = Prop.forAll(Gen.choose(1, 3), Gen.listOfN(8, Gen.choose(1, 4))) { (cap, batches) => try {
          val policy = _default.copy(maxledgerjobs = cap)
          _with_database(policy) { first =>
            var current = first
            var fresh = Option.empty[Fixture]
            var model = Vector.empty[(JobId, Int, Record)]
            var ownerjobs = Vector.empty[(JobId, List[JobInputBoundsFixtures.CountingTask])]
            var alltasksets = Vector.empty[List[JobInputBoundsFixtures.CountingTask]]
            try {
              batches.zipWithIndex.foreach { case (count, ordinal) =>
                if (ordinal == 2) {
                  Given("two real root attempts have finished and their exact native database survives closing every first owner")
                  val before = _raw_quota(current)
                  current.close()
                  When("fresh provider/EntityStore/context/manual engine owners open the same database without installing runtime recovery")
                  current = _open(first.database, policy)
                  fresh = Some(current)
                  ownerjobs = Vector.empty
                  Then("persisted bytes remain exact and no earlier root has been inserted in the new runtime")
                  _raw_quota(current) shouldBe before
                  current.engine.listJobs() shouldBe empty
                  current.engine._work_queue.size() shouldBe 0
                }
                Given(s"actual root action $ordinal with $count tasks and an independent model of ${model.size} paid entries")
                val tasks = List.fill(count)(new JobInputBoundsFixtures.CountingTask)
                val executiontasks = tasks.zipWithIndex.map { case (counter, index) =>
                  new ProbeTask(counter, _ => (), Some(if (index == 0) "root" else "child"))
                }
                alltasksets :+= tasks
                val prior = _raw_quota(current)
                val before = current.base.observation(tasks.head)
                val priorrefusals = current.engine.durableLifecycleWriteFailureFacts.map(_.jobid).toSet
                val allowed = model.size < cap
                When("the complete Async root submits through the configured bridge into the actual native provider")
                val result = current.engine.submit(executiontasks, current.context)
                Then("inclusive global capacity predicts actual admission, signed rows, exact paid facts and zero callbacks until drain")
                if (allowed) {
                  val jobid = _success(result)
                  model :+= ((jobid, count, _raw_job(current, jobid.value)))
                  ownerjobs :+= jobid -> tasks
                  current.bridge.hasAdmittedSnapshot(jobid) shouldBe true
                  current.bridge.lifetimeBinding(jobid) shouldBe defined
                  current.engine._get_record(jobid).get.tasks shouldBe executiontasks
                } else {
                  _failure(result, JobResourcePolicy.refused[Unit])
                  val newrefusals = current.engine.durableLifecycleWriteFailureFacts.filterNot(fact => priorrefusals(fact.jobid))
                  newrefusals.size shouldBe 1
                  val jobid = newrefusals.head.jobid
                  _raw_quota(current) shouldBe prior
                  _success(current.store.load(jobid.value, _access)(using current.context)) shouldBe None
                  _no_local_candidate(current, tasks.head, before, jobid)
                }
                val ledger = _ledger(current)
                ledger.policy shouldBe policy
                ledger.entries.size shouldBe model.size
                ledger.entries.map(_.jobid).sorted shouldBe model.map(_._1.value).sorted
                model.foreach { case (jobid, taskcount, signed) =>
                  val entry = ledger.entries.find(_.jobid == jobid.value).get
                  entry.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
                  entry.snapshot.seed shouldBe _seed(taskcount, 0L)
                  entry.cancelclaimed shouldBe false
                  _raw_job(current, jobid.value) shouldBe signed
                }
                val usage = _success(current.quota.resourceSnapshot()(using current.context))
                usage.ledgerjobs shouldBe model.size
                usage.chargedtasks shouldBe model.map(value => 2L * value._2).sum
                usage.historycredits shouldBe model.map(value => 32L + 64L * value._2).sum
                usage.admissions shouldBe model.size.toLong
                usage.cancelcredits shouldBe model.size.toLong
                usage.heldbytes shouldBe model.map(value => 4096L + 8192L * value._2).sum
                usage.ledgerjobs should be <= cap
                usage.heldbytes should be <= policy.maxledgerbytes
                val rows = _success(current.provider.search(_job_collection, QueryDirective(Query.Empty,
                  order = QueryOrder.By("id", OrderDirection.Asc), limit = QueryLimit.Limit(4)))).records
                rows.sortBy(_.getString("jobId")) shouldBe model.map(_._3).sortBy(_.getString("jobId"))
                alltasksets.flatten.foreach(_.runs.get() shouldBe 0)
                current.engine._work_queue.size() shouldBe ownerjobs.size
                current.engine.resourceSnapshot.retained shouldBe ownerjobs.size
                current.engine.resourceSnapshot.pending shouldBe ownerjobs.size
                current.engine.resourceSnapshot.executing shouldBe 0
                current.source.requests.count(_.boundary == DurableJobLifecycleWriteBoundary.Admission) shouldBe
                  (if (ordinal < 2) ordinal + 1 else ordinal - 1)
              }
              Given("all generated root actions have completed with bodies still uncalled and only fresh-owner accepted roots queued")
              When("the current actual manual engine explicitly drains its own queued roots")
              val drained = current.engine.drainAll()
              Then("each current-owner task executes once with original checkpoint order and exact paid native ID association")
              drained shouldBe ownerjobs.size
              ownerjobs.foreach { case (jobid, tasks) =>
                val models = current.engine._get_record(jobid).get.taskReadModels
                val stored = _success(current.store.load(jobid.value, _access)(using current.context)).get.record.body.tasks
                Then("the complete original task batch has one root followed by children with matching live and signed parent chains")
                models.size shouldBe tasks.size
                stored.size shouldBe tasks.size
                models.zipWithIndex.foreach { case (taskmodel, index) =>
                  val kind = if (index == 0) DurableTaskRelationKind.Root else DurableTaskRelationKind.Child
                  val parent = if (index == 0) None else Some(models(index - 1).taskId)
                  taskmodel.relation shouldBe Some(kind.wire)
                  taskmodel.parentTaskId shouldBe parent
                  stored(index).taskId shouldBe taskmodel.taskId.value
                  stored(index).parentTaskId shouldBe parent.map(_.value)
                  stored(index).relation shouldBe DurableTaskRelation(kind, parent.map(_.value))
                }
                tasks.foreach(_.runs.get() shouldBe 1)
                current.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
                _success(current.quota.snapshot(jobid.value, _access)(using current.context)).seed shouldBe
                  _seed(tasks.size, 0L).copy(remaining = 0L, normalids = models.map(_.taskId.value).toSet)
              }
              current.engine.resourceSnapshot.pending shouldBe 0
              current.engine.resourceSnapshot.executing shouldBe 0
            } finally fresh.foreach(_.close())
          }
          true
        } catch {
          case scala.util.control.NonFatal(error) =>
            if (firstfailure.isEmpty) {
              val location = error.getStackTrace.find(_.getFileName == "JobDurableLifetimeAdmissionSpec.scala")
                .map(_.toString).getOrElse("no location available")
              firstfailure = Some(s"cap=$cap batches=$batches error=${error.getClass.getName} message=${error.getMessage} location=$location")
            }
            throw error
        }
        }
        When("ScalaCheck runs every finite sequence against a fresh real SQLite database and fresh owners at the reopen action")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
          .withInitialSeed(Seed(6970933621L)), property)
        Then("at least 24 successful native runs contain actual actions and adjacent direct should assertions")
        withClue(s"ScalaCheck seed6970933621: ${firstfailure.getOrElse("no property exception")} ${checked.toString} ") {
          checked.passed shouldBe true
          checked.succeeded should be >= 24
        }
      }
    }
  }

  private final case class Fixture(database: Path, policy: DurableJobLifetimeQuotaPolicy,
    provider: SqlDataStore, entity: ObservedEntityStore, base: JobInputBoundsFixtures.Fixture,
    store: DurableJobStore, quota: DurableJobLifetimeQuota, source: EvidenceSource,
    bridge: DurableJobLifecycleWriteBridge) {
    private var _closed = false
    def engine: InMemoryJobEngine = base.engine
    def context: ExecutionContext = base.context
    def close(): Unit = if (!_closed) {
      _closed = true
      try engine.shutdown() finally { val _ = provider.closeC() }
    }
  }

  private def _with_database(policy: DurableJobLifetimeQuotaPolicy)(body: Fixture => Unit): Unit = {
    val area = Paths.get("target", "durable-job-lifetime-admission", "work")
    val _ = Files.createDirectories(area)
    val root = Files.createTempDirectory(area, "native-")
    var fixture = Option.empty[Fixture]
    try {
      val opened = _open(root.resolve("jobs.db"), policy)
      fixture = Some(opened)
      body(opened)
    } finally {
      fixture.foreach(_.close())
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => { val _ = Files.deleteIfExists(path) })
      finally stream.close()
    }
  }

  private def _open(database: Path, policy: DurableJobLifetimeQuotaPolicy): Fixture = {
    val base = JobLifetimeFixtures.fresh(policy.lifetime)
    val provider = SqlDataStore.sqlite(database.toString)
    val entity = new ObservedEntityStore
    base.context.dataStoreSpace.useDataStore(provider)
    base.context.entityStoreSpace.addEntityStore(entity)
    _install_job_entity(entity, base.context)
    val store = new DurableJobStore(entity)
    val quota = store.lifetimeQuota(policy)
    val source = new EvidenceSource
    val bridge = new DurableJobLifecycleWriteBridge(store, source, policy)
    base.engine.bindDurableLifecycleWriteBridge(bridge)
    Fixture(database, policy, provider, entity, base, store, quota, source, bridge)
  }

  private def _install_job_entity(entity: ObservedEntityStore, context: ExecutionContext): Unit = {
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

  private final class EvidenceSource extends (DurableJobLifecycleWriteRequest => Consequence[DurableJobLifecycleWriteEvidence]) {
    private var _requests = Vector.empty[DurableJobLifecycleWriteRequest]
    private var _revisions = Vector.empty[Long]
    private var _access = JobInputBoundsFixtures.access
    private var _admission_revision = 1L
    private var _admission_failure = Option.empty[Consequence[DurableJobLifecycleWriteEvidence]]
    def requests: Vector[DurableJobLifecycleWriteRequest] = _requests
    def revisions: Vector[Long] = _revisions
    def useAccess(access: DurableJobRecordAccess): Unit = { _access = access }
    def useAdmissionRevision(revision: Long): Unit = { _admission_revision = revision }
    def refuseAdmission(result: Consequence[DurableJobLifecycleWriteEvidence]): Unit = { _admission_failure = Some(result) }
    def apply(request: DurableJobLifecycleWriteRequest): Consequence[DurableJobLifecycleWriteEvidence] = {
      _requests :+= request
      val models = request match {
        case DurableJobLifecycleWriteRequest.TaskStartIntent(_, _, _, tasks) => tasks
        case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(_, _, _, tasks) => tasks
        case DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(_, tasks) => tasks
        case _ => Vector.empty
      }
      val revision = request.boundary match {
        case DurableJobLifecycleWriteBoundary.Admission => _admission_revision
        case DurableJobLifecycleWriteBoundary.StartIntent => 2L
        case DurableJobLifecycleWriteBoundary.RunningIntent => 3L
        case DurableJobLifecycleWriteBoundary.TaskStartIntent => 2L + 2L * models.size
        case DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint => 3L + 2L * models.size
        case DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint => 4L + 2L * models.size
      }
      _revisions :+= revision
      if (request.boundary == DurableJobLifecycleWriteBoundary.Admission && _admission_failure.nonEmpty)
        _admission_failure.get
      else {
        val supplied = JobInputBoundsFixtures.evidence(revision)
        val terminal = request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
        Consequence.success(supplied.copy(access = _access, projection = supplied.projection.copy(
          tasks = if (models.nonEmpty) models.map { model =>
            val kind = DurableTaskRelationKind.parse(model.relation.get).toOption.get
            JobInputBoundsFixtures.taskDescriptor(model).copy(
              relation = DurableTaskRelation(kind, model.parentTaskId.map(_.value)))
          } else supplied.projection.tasks,
          retry = if (terminal) DurableRetryEvidence(Vector(DurableAttemptEvidence(1, JobInputBoundsFixtures.instant,
            Some(JobInputBoundsFixtures.instant.plusMillis(1)), DurableAttemptOutcome.Succeeded, None)),
            3, None, false, false) else supplied.projection.retry,
          result = if (terminal) DurableResultOutcome.Succeeded(DurableValue.Absent) else DurableResultOutcome.Pending)))
      }
    }
  }

  private final class ProbeTask(val counter: JobInputBoundsFixtures.CountingTask,
    body: ExecutionContext => Unit, relationkind: Option[String] = None) extends JobTask {
    def actionId = counter.actionId
    override def requestSummary = counter.requestSummary
    override def requestParameters = counter.requestParameters
    override def defaultPersistence = counter.defaultPersistence
    override def taskKind = counter.taskKind
    override def targetKind = counter.targetKind
    override def relation = relationkind.orElse(counter.relation)
    override def transactionRole = counter.transactionRole
    override def transactionScope = counter.transactionScope
    override def componentName = counter.componentName
    override def serviceName = counter.serviceName
    override def operationName = counter.operationName
    def run(ctx: ExecutionContext): TaskOutcome = { body(ctx); counter.run(ctx) }
    override def observeAdmissionFailure(conclusion: Conclusion, ctx: ExecutionContext): Unit =
      counter.observeAdmissionFailure(conclusion, ctx)
  }

  private final class ObservedEntityStore extends StandardEntityStore {
    private var _before_job_create = Option.empty[String => Unit]
    private var _after_job_create = Option.empty[String => Unit]
    private var _fault_mode = "none"
    private var _fault_deliveries = 0
    private var _job_creates = 0
    private var _quota_creates = 0
    private var _quota_updates = 0
    def jobcreates: Int = _job_creates
    def quotacreates: Int = _quota_creates
    def quotaupdates: Int = _quota_updates
    def faultdeliveries: Int = _fault_deliveries
    def arm(mode: String): Unit = { _fault_mode = mode }
    def beforeJobCreate(action: String => Unit): Unit = { _before_job_create = Some(action) }
    def afterJobCreate(action: String => Unit): Unit = { _after_job_create = Some(action) }
    override def create[T](entity: T, options: EntityCreateOptions)(using tc: EntityPersistentCreate[T],
      ctx: ExecutionContext): Consequence[CreateResult[T]] = {
      val quota = tc.collection(entity) == DurableJobLifetimeQuotaEntity.Collection
      val job = tc.collection(entity) == DurableJobStoreEntity.Collection
      val jobid = if (job) tc.toStoreRecord(entity).getString("jobId").get else ""
      if (quota) _quota_creates += 1
      if (job) { _job_creates += 1; _before_job_create.foreach(_(jobid)) }
      val result = super.create(entity, options)
      if (job && result.isSuccess) _after_job_create.foreach(_(jobid))
      if (result.isSuccess && (quota && _fault_mode == "quota-create" || job && _fault_mode == "job-create"))
        _lose_ack()
      result
    }
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
        (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] = {
      val quota = tc.id(changes).collection == DurableJobLifetimeQuotaEntity.Collection
      if (quota) _quota_updates += 1
      val result = super.updateDetached(changes, expectedRevision, executionPolicy)
      if (quota && result.isSuccess && _fault_mode == "quota-publish") _lose_ack()
      result
    }
    private def _lose_ack(): Nothing = {
      _fault_mode = "none"
      _fault_deliveries += 1
      throw new IllegalStateException("private-lost-ack /private/path authorization private-subject root-token")
    }
  }

  private final class OpaqueDataStore(delegate: SqlDataStore) extends DataStore {
    def isAccept(cid: DataStore.CollectionId): Boolean = true
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.create(collection, id, record)
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] =
      delegate.load(collection, id)
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.save(collection, id, record)
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.update(collection, id, changes)
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.delete(collection, id)
    def prepare(tx: TransactionContext): PrepareResult = record_prepare(CommitRecorder.noop)
    def commit(tx: TransactionContext): Unit = record_commit(CommitRecorder.noop)
    def abort(tx: TransactionContext): Unit = record_abort(CommitRecorder.noop)
  }

  private def _no_local_candidate(fixture: Fixture, task: JobInputBoundsFixtures.CountingTask,
    before: JobInputBoundsFixtures.Observation, jobid: JobId): Unit = {
    fixture.bridge.hasAdmittedSnapshot(jobid) shouldBe false
    fixture.bridge.lifetimeBinding(jobid) shouldBe None
    fixture.engine._get_record(jobid) shouldBe None
    fixture.engine.lifetimeSnapshot(jobid) shouldBe None
    fixture.engine.resourceSnapshot shouldBe before.resources
    fixture.engine.runtimeState.durableJobs.size() shouldBe before.durable
    fixture.engine.runtimeState.runtimeJobs.size() shouldBe before.runtime
    fixture.engine._cancellation_scopes.size() shouldBe before.scopes
    fixture.engine._work_queue.size() shouldBe before.queue
    JobLifetimeFixtures.events(fixture.base).size shouldBe before.events
    fixture.base.timer.calls.get() shouldBe before.timers
    fixture.base.timer.pendingCount shouldBe before.pending
    task.runs.get() shouldBe before.runs
    task.admissions.get() shouldBe before.admissions + 1
    fixture.engine.durableLifecycleWriteFailureFacts.find(_.jobid == jobid).get.failure shouldBe
      DurableJobLifecycleWriteFailure.AdmissionRefused
  }

  private def _submitted_record(fixture: Fixture, tasks: List[JobTask]): JobRecord =
    JobRecord(id = JobInputBoundsFixtures.id("direct-root"), tasks = tasks, submittedContext = fixture.context,
      status = JobStatus.Submitted, result = None, persistence = JobPersistencePolicy.Persistent,
      runMode = JobRunMode.Async, priority = 0, createdAt = _now, updatedAt = _now,
      taskReadModels = Vector.empty, timeline = Vector.empty,
      debug = JobDebugInfo(None, Map.empty, Vector.empty))

  private def _closed_record(jobid: String): DurableJobRecord = {
    val supplied = JobInputBoundsFixtures.evidence(1L).projection
    _success(DurableJobRecord.createV2(DurableJobRecordBody(
      DurableJobIdentity(jobid, 1L, _now, _now), supplied.authorization,
      DurableJobLifecycle(DurableJobLifecycleStatus.Submitted, 0, DurableRunMode.Async,
        supplied.retry, DurableScheduleState(Some(_now), None, None)), supplied.tasks,
      supplied.inputs, supplied.result, Vector.empty, supplied.diagnostics, supplied.calltreeReference,
      supplied.definitionSnapshot, supplied.retention)))
  }

  private def _seed(taskcount: Int, notecount: Long): JobLifetimeBudget.Seed =
    JobLifetimeBudget.Seed(JobLifetimeSnapshot(2L * taskcount, 32L + 64L * taskcount + notecount, 1L, 1L),
      taskcount.toLong, Set.empty, Set.empty)
  private def _job_collection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection)
  private def _quota_collection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(DurableJobLifetimeQuotaEntity.Collection)
  private def _quota_id: EntityId = _success(DurableJobLifetimeQuotaEntity.entityId)
  private def _raw_job(fixture: Fixture, jobid: String): Record =
    _success(fixture.provider.load(_job_collection,
      DataStore.EntryId(_success(DurableJobStoreEntity.entityId(jobid))))(using fixture.context)).get
  private def _raw_quota(fixture: Fixture): Option[Record] =
    _success(fixture.provider.load(_quota_collection, DataStore.EntryId(_quota_id))(using fixture.context))
  private def _ledger(fixture: Fixture): DurableJobLifetimeQuotaRecord =
    _success(DurableJobLifetimeQuotaCodec.decode(_raw_quota(fixture).get.getString("canonicalJson").get))
  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
  private def _failure[A, B](result: Consequence[A], expected: Consequence[B]): Unit = (result, expected) match {
    case (Consequence.Failure(actual), Consequence.Failure(reference)) =>
      actual.status shouldBe reference.status
      actual.observation.taxonomy shouldBe reference.observation.taxonomy
      actual.observation.cause.kind shouldBe reference.observation.cause.kind
      actual.observation.phenomenon shouldBe reference.observation.phenomenon
      actual.displayMessage shouldBe reference.displayMessage
    case _ => fail("expected a stable closed root admission failure")
  }
  private def _redacted[A](result: Consequence[A]): Unit = result match {
    case Consequence.Failure(conclusion) =>
      Vector("private-subject", "private-signature-job", "private-corrupt-body", "/private/path", "authorization", "root-token")
        .foreach(sentinel => conclusion.show should not include sentinel)
    case Consequence.Success(_) => fail("expected closed refusal")
  }
}
