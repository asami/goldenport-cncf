package org.goldenport.cncf.job

import java.time.{Clock, Duration, ZoneOffset}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventStore
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Authenticated paid recovery, closed history and acknowledged continuation.
 * Real SQLite owners remain in-process evidence, pending independent acceptance.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobDurableLifetimeRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobDurableLifetimeRecoveryFixtures.*

  "Native durable lifetime recovery (phase:69.7, slice:JM69-09B3B3C2B2B2)" should {
    "authenticated fresh ownership" which {
      "NR1 reopen a Published declaration without manufacturing payment or Admission evidence" in {
        Given("one genuine Published Submitted root with an unassociated declaration and a closed original SQLite owner")
        withDatabase { database =>
          val first = database.open()
          val jobid = _id("nr1")
          val original = publish(first.store, record(jobid), first.policy)(using first.context)
          val native = first.native(jobid)
          val bytes = _raw_quota(first)
          database.closeBeforeReopen(first)
          val owner = database.open()
          val task = new Task
          val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
          val discovery = candidates(jobid)
          val port = _port(owner, jobid, List(task))
          val writes = owner.entity.writes.get()
          When("the fresh provider, context and State register the explicitly discovered runtime")
          val report = success(owner.engine.recoverDurableStartup(discovery, owner.store, port, 1)(using owner.context))
          Then("the exact paid authority is retained without a write, task association or Admission request")
          report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Registered)
          owner.context should not be theSameInstanceAs(first.context)
          owner.provider should not be theSameInstanceAs(first.provider)
          owner.entity should not be theSameInstanceAs(first.entity)
          owner.engine.runtimeState should not be theSameInstanceAs(first.engine.runtimeState)
          owner.native(jobid) shouldBe native
          native.seed shouldBe JobLifetimeBudget.Seed(JobLifetimeSnapshot(2, 96, 1, 1), 1, Set.empty, Set.empty)
          _raw_quota(owner) shouldBe bytes
          owner.signed(jobid) shouldBe original
          owner.entity.writes.get() shouldBe writes
          source.requests shouldBe empty
          discovery.calls.get() shouldBe 1
          port.calls.get() shouldBe 1
          owner.engine.lifetimeSnapshot(jobid) shouldBe Some(JobLifetimeSnapshot(2, 96, 1, 1))
          _binding(owner, jobid).context should be theSameInstanceAs(owner.context)
          _binding(owner, jobid).bridge shouldBe owner.engine._lifecycle_bridge(jobid)
          owner.engine._get_record(jobid).get.createdAt shouldBe original.record.body.identity.createdAt
          owner.engine._get_record(jobid).get.updatedAt shouldBe original.record.body.identity.updatedAt
          owner.ids.calls.get() shouldBe 0
          owner.timer.pendingCount shouldBe 1
          owner.engine.drainAll() shouldBe 0
          task.runs.get() shouldBe 0
        }
      }

      "NR2 continue a scheduled normal JobRun from the actual admitted semantic revision" in {
        Given("a Published root with a genuine second Submitted checkpoint before the original provider closes")
        withDatabase { database =>
          val first = database.open()
          val jobid = _id("nr2")
          val root = publish(first.store, record(jobid), first.policy)(using first.context)
          val original = _checkpoint(first, root, root.record.body.copy(
            timeline = Vector(DurableTimelineEvent(1, instant, "job.submitted", None, Some("original")))))
          database.closeBeforeReopen(first)
          val owner = database.open()
          val task = new Task
          val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
          success(_recover(owner, jobid, List(task))).candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Registered)
          val before = owner.native(jobid)
          When("the original scheduled timer becomes due and the normal queue drains")
          owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
          owner.engine.drainAll() shouldBe 1
          Then("five genuine acknowledgements advance N+1 through N+5, followed by one canonical success")
          source.requests.map(_.boundary) shouldBe _normal_boundaries
          source.revisions shouldBe Vector(3L, 4L, 5L, 6L, 7L)
          owner.entity.jobacks.get() shouldBe 5
          task.runs.get() shouldBe 1
          task.canonical.get() shouldBe 1
          owner.engine.getResult(jobid) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
          val signed = owner.signed(jobid).record.body
          signed.identity.revision shouldBe 7L
          signed.timeline.take(original.record.body.timeline.size) shouldBe original.record.body.timeline
          signed.timeline.drop(1).map(_.kind) shouldBe Vector("job.durable-start-intent", "job.durable-running-intent",
            "task.durable-start-intent", "task.durable-outcome-checkpoint", "job.durable-terminal-outcome-checkpoint")
          signed.timeline.exists(_.kind == "job.runtime-rehydrated") shouldBe false
          signed.identity.createdAt shouldBe original.record.body.identity.createdAt
          signed.identity.updatedAt.isAfter(original.record.body.identity.updatedAt) shouldBe true
          _protected(signed) shouldBe _protected(original.record.body)
          val seed = owner.native(jobid).seed
          seed.paid shouldBe before.seed.paid
          seed.remaining shouldBe 0L
          seed.normalids shouldBe owner.engine._get_record(jobid).get.taskReadModels.map(_.taskId.value).toSet
          seed.claims shouldBe empty
          owner.engine.durableLifecycleWriteFailureFacts shouldBe empty
          owner.engine.resourceSnapshot.pending shouldBe 0
          owner.engine.resourceSnapshot.executing shouldBe 0
        }
      }

      "NR3 retain terminal ownership without executable lifecycle capability" in {
        Given("an actually associated and checkpointed terminal root reopened with no configured bridge")
        withDatabase { database =>
          val first = database.open()
          val jobid = _id("nr3")
          val terminal = _terminal(record(jobid))
          val original = publishTerminal(first.store, terminal, first.policy)(using first.context)
          val native = first.native(jobid)
          database.closeBeforeReopen(first)
          val owner = database.open()
          val bytes = _raw_quota(owner)
          val writes = owner.entity.writes.get()
          val clocks = owner.clock.calls.get()
          When("the terminal-only coordinator registers its closed projection")
          val report = success(owner.engine.recoverDurableTerminalFacts(candidates(jobid), owner.store, 1)(using owner.context))
          Then("only the closed fact, exact seed and original native handle are retained")
          report.candidates.map(_.fact) shouldBe Vector(DurableJobTerminalProjectionFact.Registered)
          _binding(owner, jobid).bridge shouldBe None
          owner.engine._get_record(jobid) shouldBe None
          owner.engine.getResult(jobid) shouldBe None
          owner.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
          owner.engine.queryTasks(jobid).get.tasks.map(_.taskId.value) shouldBe terminal.body.tasks.map(_.taskId)
          owner.engine.durableTerminalProjection(jobid).get.snapshot shouldBe original
          owner.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
          owner.engine.runtimeState.durableJobs.isEmpty shouldBe true
          owner.engine._work_queue.size() shouldBe 0
          owner.timer.calls.get() shouldBe 0
          owner.clock.calls.get() shouldBe clocks
          owner.entity.writes.get() shouldBe writes
          owner.native(jobid) shouldBe native
          val loads = owner.entity.quotaloads.get()
          owner.engine.lifetimeSnapshot(jobid) shouldBe Some(native.seed.paid)
          owner.entity.quotaloads.get() shouldBe loads + 1
          _raw_quota(owner) shouldBe bytes
          And("a subsequently configured bridge cannot give that terminal binding executable authority")
          bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
          owner.engine._lifecycle_bridge(jobid) shouldBe None
          owner.signed(jobid) shouldBe original
        }
      }

      "NR4 refuse each missing, Pending, corrupt or V1 authority independently" in {
        Vector("missing-ledger", "missing-entry", "pending", "canonical", "counters", "v1").foreach { mode =>
          Given(s"a real signed candidate with independently prepared $mode native authority")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr4$mode")
            val proposal = record(jobid)
            val original = mode match {
              case "missing-ledger" => success(owner.store.create(proposal, access)(using owner.context))
              case "missing-entry" =>
                publish(owner.store, record(_id("other-entry")), owner.policy)(using owner.context)
                success(owner.store.create(proposal, access)(using owner.context))
              case "pending" =>
                success(owner.quota.reserveRoot(proposal, access, 1)(using owner.context))
                success(owner.store.create(proposal, access)(using owner.context))
              case "v1" => success(owner.store.create(success(DurableJobRecord.create(_terminal(proposal).body)), access)(using owner.context))
              case _ => publish(owner.store, proposal, owner.policy)(using owner.context)
            }
            if (mode == "canonical") _replace_quota(owner, "private-invalid-native-json")
            if (mode == "counters") _replace_quota(owner,
              _quota_text(owner).replace("\"chargedTasks\":2", "\"chargedTasks\":-1"))
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            val task = new Task
            val before = _effects(owner, task)
            val bytes = _raw_quota(owner)
            val writes = owner.entity.writes.get()
            When("public startup registration reaches its original eligible runtime or V1 refusal boundary")
            val refused = if (mode == "v1") {
              val report = success(owner.engine.recoverDurableTerminalFacts(candidates(jobid), owner.store, 1)(using owner.context))
              report.candidates.map(_.fact) == Vector(DurableJobTerminalProjectionFact.Refused)
            } else success(_recover(owner, jobid, List(task))).candidates.map(_.fact) == Vector(DurableJobRuntimeRehydrationFact.Refused)
            Then("no payment, seed, bridge entry, physical capacity or signed mutation supplies missing authority")
            refused shouldBe true
            _effects(owner, task) shouldBe before
            owner.engine.durableTerminalProjection(jobid) shouldBe None
            _raw_quota(owner) shouldBe bytes
            owner.signed(jobid) shouldBe original
            owner.entity.writes.get() shouldBe writes
            owner.engine._get_record(jobid) shouldBe None
            owner.engine.lifetimeSnapshot(jobid) shouldBe None
            source.requests shouldBe empty
          }
        }
      }

      "NR5 require exact access, native owner, complete policy and caller recovery context" in {
        Vector("subject", "tenant", "scopes", "owner", "policy", "context").foreach { mode =>
          Given(s"a genuine Published root and one $mode mismatch")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr5$mode")
            val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
            if (mode == "owner") {
              val ledger = success(DurableJobLifetimeQuotaCodec.decode(_quota_text(owner)))
              _replace_quota(owner, success(DurableJobLifetimeQuotaCodec.encode(ledger.copy(
                entries = ledger.entries.map(_.copy(ownersha256 = "b" * 64))))))
            }
            val selected = if (mode == "policy") owner.policy.copy(maxledgerjobs = 4095) else owner.policy
            owner.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(owner.store,
              new Source(owner.store, original.record, owner.context), selected))
            val candidate = candidates(jobid).entries.head.copy(access = mode match {
              case "subject" => access.copy(subjectId = "foreign")
              case "tenant" => access.copy(tenantId = "foreign")
              case "scopes" => access.copy(scopes = Set.empty)
              case _ => access
            })
            val discovery = new SourceCandidates(Vector(candidate))
            val task = new Task
            val port = _port(owner, jobid, List(task))
            val before = _effects(owner, task)
            val bytes = _raw_quota(owner)
            val context = if (mode == "context") ExecutionContext.create(Clock.fixed(instant, ZoneOffset.UTC)) else owner.context
            When("the caller offers the explicit candidate and recovery context without authority fallback")
            val report = success(owner.engine.recoverDurableStartup(discovery, owner.store, port, 1)(using context))
            Then("the mismatch refuses before local or physical mutation and the exact caller context reaches IO")
            report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Refused)
            _effects(owner, task) shouldBe before
            _raw_quota(owner) shouldBe bytes
            owner.signed(jobid) shouldBe original
            owner.engine.runtimeState._native_lifetime_bindings.contains(jobid) shouldBe false
            if (Set("subject", "tenant", "scopes", "context").contains(mode)) port.calls.get() shouldBe 0
            else port.calls.get() shouldBe 1
            owner.entity.contexts.exists(_ eq context) shouldBe true
          }
        }
      }

      "NR6 refuse signed body and provider revision drift after the original port admission" in {
        Vector("body", "provider").foreach { mode =>
          Given(s"an admitted Published snapshot whose $mode changes while the eligible port reconstructs")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr6$mode")
            val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            val task = new Task
            val port = _port(owner, jobid, List(task))
            var changed = original
            port.before = () => {
              if (mode == "body") changed = _checkpoint(owner, original, original.record.body.copy(
                lifecycle = original.record.body.lifecycle.copy(priority = 1)))
              else {
                _save_job(owner, jobid, _raw_job(owner, jobid).upsertSingle("cncf_revision",
                  Math.addExact(original.providerrevision.value, 1L)))
                changed = owner.signed(jobid)
              }
            }
            val before = _effects(owner, task)
            val bytes = _raw_quota(owner)
            When("native recovery authenticates the original candidate after the independent native change")
            val report = success(owner.engine.recoverDurableStartup(candidates(jobid), owner.store, port, 1)(using owner.context))
            Then("neither a new read-derived candidate nor its revision can replace the admitted original")
            report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Refused)
            changed.providerrevision should not be original.providerrevision
            if (mode == "provider") changed.record shouldBe original.record
            else changed.record should not be original.record
            owner.signed(jobid) shouldBe changed
            _effects(owner, task) shouldBe before
            _raw_quota(owner) shouldBe bytes
            source.requests shouldBe empty
            owner.engine._get_record(jobid) shouldBe None
          }
        }
      }
    }

    "bounded footprint and publication" which {
      "NR7 reject intrinsic signed and live excess before native quota reads and construction effects" in {
        Vector("tasks", "timeline", "diagnostics", "retry", "live", "input").foreach { mode =>
          Given(s"a canonically signed Submitted source exceeding only its intended $mode bound")
          withDatabase { database =>
            val owner = database.open(JobLifetimePolicy(2, 96, 1), JobResourcePolicy.default.copy(maxretryattempts = 1))
            val jobid = _id(s"nr7$mode")
            val base = record(jobid, if (mode == "tasks") 3 else 1,
              if (mode == "timeline") 97 else if (mode == "diagnostics") 40 else 0)
            val body = base.body.copy(
              diagnostics = if (mode == "diagnostics") Vector.fill(57)(DurableDiagnosticSummary("category", "code", "warning", "summary")) else base.body.diagnostics,
              lifecycle = if (mode == "retry") base.body.lifecycle.copy(retry = base.body.lifecycle.retry.copy(maxAttempts = 2))
                else base.body.lifecycle.copy(retry = base.body.lifecycle.retry.copy(maxAttempts = 1)))
            val original = success(owner.store.create(success(DurableJobRecord.createV2(body)), access)(using owner.context))
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            val task = new Task
            val value = if (mode == "input") Some(JobInputBoundsFixtures.input(Vector.fill(1025)(JobInputBoundsFixtures.descriptor))) else None
            val port = new Port(_ => DurableJobRuntimeRehydration(jobid,
              List.fill(if (mode == "live") 1025 else 1)(task), owner.context, value, None))
            val before = _effects(owner, task)
            val loads = owner.entity.quotaloads.get()
            When("the real source, signed reader and eligible port reach complete pure footprint admission")
            val report = success(owner.engine.recoverDurableStartup(candidates(jobid), owner.store, port, 1)(using owner.context))
            Then("specific excess refuses before quota IO, clock, ID, getter, seed, timer or insertion")
            report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Refused)
            port.calls.get() shouldBe 1
            owner.entity.quotaloads.get() shouldBe loads
            _effects(owner, task) shouldBe before
            owner.signed(jobid) shouldBe original
            source.requests shouldBe empty
          }
        }
      }

      "NR8 distinguish actual unused native tickets from unassociated declaration names" in {
        Vector(false, true).foreach { accepted =>
          Given("a genuine root with either consumed native normal capacity or two unused declaration tickets")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr8$accepted")
            val root = publish(owner.store, record(jobid, if (accepted) 2 else 1), owner.policy,
              if (accepted) 2 else 1)(using owner.context)
            val original = if (accepted) root else {
              success(owner.quota.associate(jobid.value, access, "historic-opaque")(using owner.context)) shouldBe true
              _checkpoint(owner, root, root.record.body.copy(tasks = Vector(root.record.body.tasks.head.copy(
                taskId = "historic-opaque", transaction = DurableTransactionDescriptor(DurableTransactionRole.Own,
                  DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed)))))
            }
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            val task = new Task
            val seed = owner.native(jobid).seed
            val bytes = _raw_quota(owner)
            val before = _effects(owner, task)
            When("the actual native authority admits or refuses the uncancelled complete live batch")
            val report = success(_recover(owner, jobid, List.fill(if (accepted) 2 else 1)(task)))
            Then("only sufficient unused tickets register and no declaration id becomes an associated normal")
            report.candidates.map(_.fact) shouldBe Vector(if (accepted) DurableJobRuntimeRehydrationFact.Registered else DurableJobRuntimeRehydrationFact.Refused)
            owner.native(jobid).seed shouldBe seed
            _raw_quota(owner) shouldBe bytes
            owner.signed(jobid) shouldBe original
            source.requests shouldBe empty
            if (accepted) {
              seed shouldBe JobLifetimeBudget.Seed(JobLifetimeSnapshot(4, 160, 1, 1), 2, Set.empty, Set.empty)
              owner.engine.lifetimeSnapshot(jobid) shouldBe Some(seed.paid)
              owner.timer.pendingCount shouldBe 1
            } else {
              seed.remaining shouldBe 0L
              _effects(owner, task) shouldBe before
              owner.engine._get_record(jobid) shouldBe None
            }
          }
        }
      }

      "NR9 cover executed normals, task events and original compensation claims with exact opaque strings" in {
        Vector("executed", "event", "claim", "covered").foreach { mode =>
          Given(s"a genuine Published root with $mode closed obligations and independently associated native facts")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr9$mode")
            val root = publish(owner.store, record(jobid, 2), owner.policy, 2)(using owner.context)
            val normal = root.record.body.tasks.head.copy(taskId = "opaque:old/task", transaction =
              DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed))
            val compensation = DurableCompensationDescriptor(normal.target.operation,
              normal.taskId, DurableCompensationStatus.Succeeded, None)
            val companion = normal.copy(taskId = "opaque:companion/task", relation =
              DurableTaskRelation(DurableTaskRelationKind.Compensation, Some(normal.taskId)),
              transaction = normal.transaction.copy(outcome = DurableTransactionOutcome.Compensated),
              compensation = Some(compensation))
            val tasks = if (Set("claim", "covered").contains(mode)) Vector(normal, companion) else Vector(normal)
            if (Set("claim", "covered").contains(mode)) success(owner.quota.associate(jobid.value, access, normal.taskId)(using owner.context)) shouldBe true
            if (mode == "covered") success(owner.quota.claimCompensation(jobid.value, access, normal.taskId)(using owner.context)) shouldBe true
            val original = _checkpoint(owner, root, root.record.body.copy(tasks = tasks,
              timeline = Vector(DurableTimelineEvent(1, instant, "task.succeeded", Some(normal.taskId), Some("closed")))))
            val selected = if (mode == "event") original.copy(record = original.record.copy(body =
              original.record.body.copy(timeline = Vector(DurableTimelineEvent(1, instant, "task.succeeded", Some("unknown-task"), None))))) else original
            val bytes = _raw_quota(owner)
            val writes = owner.entity.writes.get()
            When("bounded pure obligations and the exact read-only Published snapshot are prepared")
            val result = DurableJobLifetimeRecovery.footprint(selected, JobResourcePolicy.default, owner.policy.lifetime)
              .flatMap(value => DurableJobLifetimeRecovery.prepare(owner.store, selected, access, owner.context, owner.policy, value, Some(1)))
            Then("missing execution, event membership or original claims refuse without inventing coverage")
            result.isSuccess shouldBe (mode == "covered")
            if (mode == "covered") {
              val prepared = success(result)
              prepared.snapshot shouldBe original
              prepared.quota.seed.normalids shouldBe Set(normal.taskId)
              prepared.quota.seed.claims shouldBe Set(normal.taskId)
              prepared.quota.seed.remaining shouldBe 1L
              prepared.quota.seed.paid shouldBe JobLifetimeSnapshot(4, 160, 1, 1)
              prepared.snapshot.record.body.tasks shouldBe tasks
            }
            _raw_quota(owner) shouldBe bytes
            owner.signed(jobid) shouldBe original
            owner.entity.writes.get() shouldBe writes
          }
        }
      }

      "NR10 preserve paid authority across duplicates, collisions, quiesce and physical capacity refusal" in {
        Vector("duplicate", "terminal", "seed", "quiesce", "retained", "pending").foreach { mode =>
          Given(s"an original Published candidate and an existing local $mode refusal precondition")
          withDatabase { database =>
            val resources = JobResourcePolicy.default.copy(maxretainedjobs = if (mode == "retained") 1 else 2, maxpendingwork = 1)
            val owner = database.open(resources = resources)
            val jobid = _id(s"nr10$mode")
            val original = if (mode == "terminal") publishTerminal(owner.store, _terminal(record(jobid)), owner.policy)(using owner.context)
              else publish(owner.store, record(jobid), owner.policy)(using owner.context)
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            val task = new Task
            mode match {
              case "duplicate" => success(_recover(owner, jobid, List(task))).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
              case "terminal" => success(owner.engine.recoverDurableTerminalFacts(candidates(jobid), owner.store, 1)(using owner.context)).candidates.head.fact shouldBe DurableJobTerminalProjectionFact.Registered
              case "seed" => owner.engine.runtimeState._lifetime_budget.commitSeeds(Map(jobid -> owner.native(jobid).seed))
              case "quiesce" => owner.engine.quiesce()
              case "retained" | "pending" =>
                success(owner.engine.submit(List(new Task), owner.context, JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)))
              case _ => ()
            }
            val native = owner.native(jobid)
            val bytes = _raw_quota(owner)
            val before = _effects(owner, task)
            val entry = owner.engine._lifecycle_bridge(jobid).exists(_.hasAdmittedSnapshot(jobid))
            val loads = owner.entity.quotaloads.get()
            When("the same identity attempts registration at that existing refusal boundary")
            val report = success(_recover(owner, jobid, List(task)))
            Then("the original binding, physical state, signed bytes and paid counters remain unchanged")
            report.candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Refused
            if (Set("retained", "pending").contains(mode)) {
              val after = _effects(owner, task)
              after._1 shouldBe before._1
              after._2 shouldBe before._2
              after._4 shouldBe before._4
              after._5 shouldBe before._5
              after._6 shouldBe before._6
              after._8 shouldBe before._8
              after._9 shouldBe before._9
              after._10 shouldBe before._10
              after._11 shouldBe before._11
              owner.engine._get_record(jobid) shouldBe None
              owner.engine.runtimeState._native_lifetime_bindings.contains(jobid) shouldBe false
            } else _effects(owner, task) shouldBe before
            owner.native(jobid) shouldBe native
            _raw_quota(owner) shouldBe bytes
            owner.signed(jobid) shouldBe original
            owner.engine._lifecycle_bridge(jobid).exists(_.hasAdmittedSnapshot(jobid)) shouldBe entry
            source.requests shouldBe empty
            if (Set("duplicate", "terminal", "seed", "quiesce").contains(mode)) owner.entity.quotaloads.get() shouldBe loads + 1
            if (mode == "duplicate") {
              When("a separately legitimate terminal checkpoint collides with the retained executable identity")
              val terminal = _terminal(original.record)
              terminal.body.tasks.foreach(task => success(owner.quota.associate(jobid.value, access, task.taskId)(using owner.context)) shouldBe true)
              val closed = _checkpoint(owner, original, terminal.body)
              val collision = success(owner.engine.recoverDurableTerminalFacts(candidates(jobid), owner.store, 1)(using owner.context))
              Then("terminal registration refuses and cannot overwrite the existing runtime or its handle")
              collision.candidates.head.fact shouldBe DurableJobTerminalProjectionFact.Refused
              owner.engine._get_record(jobid).nonEmpty shouldBe true
              owner.engine.durableTerminalProjection(jobid) shouldBe None
              owner.signed(jobid) shouldBe closed
            }
          }
        }
      }

      "NR11 require the exact configured bridge and share its installed handle across State owners" in {
        Vector("absent", "foreign", "owned", "shared").foreach { mode =>
          Given(s"a Published runtime offered to an $mode lifecycle bridge")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr11$mode")
            val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
            val task = new Task
            val source = new Source(owner.store, original.record, owner.context)
            val selected = if (mode == "foreign") new DurableJobStore(owner.entity) else owner.store
            val bridge = new DurableJobLifecycleWriteBridge(selected, source, owner.policy)
            if (mode != "absent") owner.engine.bindDurableLifecycleWriteBridge(bridge)
            if (mode == "owned") success(bridge.adoptRecovery(jobid, _prepare(owner, original)))
            val bytes = _raw_quota(owner)
            val before = _effects(owner, task)
            When("native recovery attempts the actual store-bound ownership installation")
            val report = success(_recover(owner, jobid, List(task)))
            Then("only the explicit exact unoccupied store bridge can become executable ownership")
            report.candidates.head.fact shouldBe (if (mode == "shared") DurableJobRuntimeRehydrationFact.Registered else DurableJobRuntimeRehydrationFact.Refused)
            _raw_quota(owner) shouldBe bytes
            owner.signed(jobid) shouldBe original
            source.requests shouldBe empty
            if (mode == "shared") {
              val binding = _binding(owner, jobid)
              val other = JobInputBoundsFixtures.fresh(owner.engine.runtimeState)
              try {
                And("another State owner uses the exact installed bridge, native context and seed")
                other.engine.runtimeState._native_lifetime_bindings(jobid) should be theSameInstanceAs(binding)
                other.engine._lifecycle_bridge(jobid).exists(_ eq bridge) shouldBe true
                other.engine.lifetimeSnapshot(jobid) shouldBe Some(JobLifetimeSnapshot(2, 96, 1, 1))
                binding.context should be theSameInstanceAs(owner.context)
                other.timer.pendingCount shouldBe 0
              } finally other.engine.shutdown()
            } else {
              _effects(owner, task) shouldBe before
              owner.engine.runtimeState._native_lifetime_bindings.contains(jobid) shouldBe false
              owner.engine._get_record(jobid) shouldBe None
            }
          }
        }
      }
    }
  }

  "Recovered cancellation and continuation" should {
    "persisted safety intent" which {
      "NR12 settle a confirmed unstarted Cancel before any task getter or due work" in {
        Given("a Published Submitted declaration with one prepaid native Cancel already persisted")
        withDatabase { database =>
          val first = database.open()
          val jobid = _id("nr12")
          val original = publish(first.store, record(jobid), first.policy)(using first.context)
          success(first.quota.cancel(jobid.value, access)(using first.context)) shouldBe true
          val native = first.native(jobid)
          database.closeBeforeReopen(first)
          val owner = database.open()
          val task = new Task
          val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
          source.terminalstatus = JobStatus.Cancelled
          val bytes = _raw_quota(owner)
          val quotawrites = owner.entity.quotawrites.get()
          When("fresh recovery installs only retained ownership and applies original once-only safety settlement")
          val report = success(_recover(owner, jobid, List(task)))
          Then("one terminal acknowledgement retains signed declarations and no normal work or fabricated refusal")
          report.candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
          source.requests.map(_.boundary) shouldBe Vector(DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
          source.revisions shouldBe Vector(2L)
          owner.entity.jobacks.get() shouldBe 1
          owner.entity.quotawrites.get() shouldBe quotawrites
          task.getters.get() shouldBe 0
          task.runs.get() shouldBe 0
          task.canonical.get() shouldBe 0
          task.compensations.get() shouldBe 0
          owner.ids.calls.get() shouldBe 1
          success(owner.events.query(EventStore.Query())).map(_.name) shouldBe Vector("job.cancelled")
          owner.timer.calls.get() shouldBe 0
          owner.timer.pendingCount shouldBe 0
          owner.engine._work_queue.size() shouldBe 0
          owner.engine.resourceSnapshot.retained shouldBe 1
          owner.engine.resourceSnapshot.pending shouldBe 0
          owner.engine.resourceSnapshot.executing shouldBe 0
          val retained = owner.engine._get_record(jobid).get
          retained.status shouldBe JobStatus.Cancelled
          retained.tasks shouldBe Nil
          retained.taskReadModels shouldBe empty
          retained.debug.requestSummary shouldBe None
          _binding(owner, jobid).scope.isCancelled shouldBe true
          _binding(owner, jobid).cancellationSettled shouldBe true
          owner.engine.durableLifecycleWriteFailureFacts shouldBe empty
          val signed = owner.signed(jobid).record.body
          signed.lifecycle.status shouldBe DurableJobLifecycleStatus.Cancelled
          signed.result shouldBe DurableResultOutcome.Cancelled(None)
          signed.tasks shouldBe original.record.body.tasks
          signed.timeline.map(_.kind) shouldBe Vector("job.durable-terminal-outcome-checkpoint")
          _protected(signed) shouldBe _protected(original.record.body)
          owner.native(jobid) shouldBe native
          native.seed.paid shouldBe JobLifetimeSnapshot(2, 96, 1, 0)
          native.seed.remaining shouldBe 1L
          native.seed.normalids shouldBe empty
          _raw_quota(owner) shouldBe bytes
          owner.engine.drainAll() shouldBe 0
        }
      }

      "NR13 preserve classifier refusal and settle eligible opaque history only once across State owners" in {
        Vector(false, true).foreach { eligible =>
          Given("genuine associated opaque historical execution and native Cancel, with Submitted or Running signed lifecycle")
          withDatabase { database =>
            val first = database.open()
            val jobid = _id(s"nr13$eligible")
            val root = publish(first.store, record(jobid), first.policy)(using first.context)
            val historical = _historic(first, root, "opaque:not-a-universal-id")
            val original = if (eligible) historical else _checkpoint(first, historical, historical.record.body.copy(
              lifecycle = historical.record.body.lifecycle.copy(status = DurableJobLifecycleStatus.Running)))
            success(first.quota.cancel(jobid.value, access)(using first.context)) shouldBe true
            val native = first.native(jobid)
            database.closeBeforeReopen(first)
            val owner = database.open()
            val task = new Task
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            source.terminalstatus = JobStatus.Cancelled
            val port = _port(owner, jobid, List(task))
            val before = _effects(owner, task)
            When("the original coordinator classifies before any engine adoption")
            val report = success(owner.engine.recoverDurableStartup(candidates(jobid), owner.store, port, 1)(using owner.context))
            Then("Running remains RecoveryRequired, while eligible Submitted history has one confirmed closed settlement")
            report.candidates.head.fact shouldBe (if (eligible) DurableJobRuntimeRehydrationFact.Registered else DurableJobRuntimeRehydrationFact.Refused)
            port.calls.get() shouldBe (if (eligible) 1 else 0)
            task.getters.get() shouldBe 0
            task.runs.get() shouldBe 0
            owner.ids.calls.get() shouldBe (if (eligible) 1 else 0)
            success(owner.events.query(EventStore.Query())).map(_.name) shouldBe
              (if (eligible) Vector("job.cancelled") else Vector.empty)
            owner.timer.calls.get() shouldBe 0
            owner.native(jobid) shouldBe native
            if (eligible) {
              val closed = owner.signed(jobid)
              closed.record.body.tasks shouldBe original.record.body.tasks
              closed.record.body.timeline.take(original.record.body.timeline.size) shouldBe original.record.body.timeline
              closed.record.body.timeline.last.kind shouldBe "job.durable-terminal-outcome-checkpoint"
              closed.record.body.identity.revision shouldBe original.record.body.identity.revision + 1L
              source.requests.map(_.boundary) shouldBe Vector(DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
              val shared = JobInputBoundsFixtures.fresh(owner.engine.runtimeState)
              try {
                val binding = _binding(owner, jobid)
                val bytes = _raw_quota(owner)
                When("both original owners observe, drain and reapply known safety intent")
                owner.engine._apply_safety_cancel(jobid)
                shared.engine._apply_safety_cancel(jobid)
                owner.engine.drainAll() shouldBe 0
                shared.engine.drainAll() shouldBe 0
                Then("the same settled handle, signed prefix, terminal event and paid credits remain once-only")
                shared.engine.runtimeState._native_lifetime_bindings(jobid) should be theSameInstanceAs(binding)
                binding.cancellationSettled shouldBe true
                shared.engine.lifetimeSnapshot(jobid) shouldBe Some(JobLifetimeSnapshot(2, 96, 1, 0))
                owner.signed(jobid) shouldBe closed
                _raw_quota(owner) shouldBe bytes
                owner.entity.jobacks.get() shouldBe 1
                source.requests.size shouldBe 1
                task.canonical.get() shouldBe 0
                owner.ids.calls.get() shouldBe 1
                success(owner.events.query(EventStore.Query())).map(_.name) shouldBe Vector("job.cancelled")
              } finally shared.engine.shutdown()
            } else {
              success(DurableJobRecovery.classify(original.record, replay)).outcome shouldBe DurableJobRecoveryOutcome.RecoveryRequired
              _effects(owner, task) shouldBe before
              owner.signed(jobid) shouldBe original
              source.requests shouldBe empty
              owner.engine.runtimeState._native_lifetime_bindings.contains(jobid) shouldBe false
            }
          }
        }
      }

      "NR14 retain paid refusal authority on an actual stale or lost Cancel terminal acknowledgement" in {
        Vector("stale", "lost").foreach { mode =>
          Given(s"a genuinely confirmed native cancellation whose terminal acknowledgement is $mode")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr14$mode")
            val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
            success(owner.quota.cancel(jobid.value, access)(using owner.context)) shouldBe true
            val native = owner.native(jobid)
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            source.terminalstatus = JobStatus.Cancelled
            var advanced = original
            if (mode == "stale") source.before = request => {
              if (request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) {
                source.before = _ => ()
                advanced = _checkpoint(owner, original, original.record.body)
              }
            }
            else owner.entity.losejoback = true
            val task = new Task
            val bytes = _raw_quota(owner)
            When("the original retained-only safety path attempts its actual signed terminal write")
            success(_recover(owner, jobid, List(task))).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
            Then("the acknowledged or ambiguous native result retains charges and blocks canonical completion and later work")
            owner.engine.durableLifecycleWriteFailureFacts.map(_.failure) shouldBe Vector(DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused)
            _binding(owner, jobid).checkpointfailure shouldBe Some(DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused)
            task.getters.get() shouldBe 0
            task.runs.get() shouldBe 0
            task.canonical.get() shouldBe 0
            owner.ids.calls.get() shouldBe 0
            owner.timer.calls.get() shouldBe 0
            owner.engine._work_queue.size() shouldBe 0
            owner.engine.drainAll() shouldBe 0
            owner.native(jobid) shouldBe native
            _raw_quota(owner) shouldBe bytes
            val retained = owner.signed(jobid)
            if (mode == "stale") retained shouldBe advanced
            else {
              retained.record.body.identity.revision shouldBe 2L
              retained.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Cancelled
            }
            val candidate = new Task
            owner.engine.enqueueTaskInJob(jobid, candidate, owner.context) shouldBe a[Consequence.Failure[_]]
            candidate.getters.get() shouldBe 0
            candidate.runs.get() shouldBe 0
            owner.signed(jobid) shouldBe retained
            source.requests.size shouldBe 1
            success(owner.events.query(EventStore.Query())).count(_.name == "job.completed") shouldBe 0
          }
        }
      }

      "NR15 renew only explicit paid Retry and preserve Recovery RetryRun signed-write exclusion" in {
        Given("a genuinely cancelled retained runtime with no reconstructed executable tasks")
        withDatabase { database =>
          val owner = database.open()
          val jobid = _id("nr15control")
          val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
          success(owner.quota.cancel(jobid.value, access)(using owner.context)) shouldBe true
          val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
          source.terminalstatus = JobStatus.Cancelled
          val task = new Task
          success(_recover(owner, jobid, List(task))).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
          val cancelled = owner.signed(jobid)
          val scope = _binding(owner, jobid).scope
          When("the original authorized explicit Retry admits its actual empty retained batch")
          success(_control(owner, jobid, JobControlCommand.Retry)).changed shouldBe true
          Then("only that paid Retry buys 32 history, one admission and one Cancel credit and resets the shared scope")
          owner.native(jobid).seed.paid shouldBe JobLifetimeSnapshot(2, 128, 2, 1)
          owner.native(jobid).cancelclaimed shouldBe false
          _binding(owner, jobid).scope should not be theSameInstanceAs(scope)
          _binding(owner, jobid).scope.isCancelled shouldBe false
          _binding(owner, jobid).cancellationSettled shouldBe false
          owner.engine.drainAll() shouldBe 1
          owner.signed(jobid) shouldBe cancelled
          source.requests.size shouldBe 1
          task.runs.get() shouldBe 0
        }
        Given("a separately eligible unstarted Submitted source with an actual delayed Retry due time")
        withDatabase { database =>
          val owner = database.open()
          val jobid = _id("nr15recovery")
          val base = record(jobid)
          val proposal = success(DurableJobRecord.createV2(base.body.copy(lifecycle = base.body.lifecycle.copy(
            retry = base.body.lifecycle.retry.copy(nextRetryAt = Some(instant.plusSeconds(10)))))))
          val original = publish(owner.store, proposal, owner.policy)(using owner.context)
          val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
          val task = new Task
          success(_recover(owner, jobid, List(task))).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
          When("the original delayed Recovery RetryRun timer becomes due")
          owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
          owner.engine.drainAll() shouldBe 1
          Then("one actual native association and body occur with taskbridgewrites=false and unchanged signed history")
          task.runs.get() shouldBe 1
          task.canonical.get() shouldBe 1
          owner.signed(jobid) shouldBe original
          source.requests shouldBe empty
          owner.native(jobid).seed.paid shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
          owner.native(jobid).seed.remaining shouldBe 0L
          owner.native(jobid).seed.normalids.size shouldBe 1
          owner.entity.jobacks.get() shouldBe 0
        }
      }
    }

    "acknowledgement-relative normal checkpoints" which {
      "NR16 stop Start and Running refusal, stale revision and numeric overflow before normal task execution" in {
        Vector("start", "running", "stale").foreach { mode =>
          Given(s"a genuinely adopted root whose $mode checkpoint is independently refused")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr16$mode")
            val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            if (mode == "start") source.refuse = Some(DurableJobLifecycleWriteBoundary.StartIntent)
            if (mode == "running") source.refuse = Some(DurableJobLifecycleWriteBoundary.RunningIntent)
            var retained = original
            if (mode == "stale") source.before = request => {
              if (request.boundary == DurableJobLifecycleWriteBoundary.StartIntent) {
                source.before = _ => ()
                retained = _checkpoint(owner, original, original.record.body)
              }
            }
            val task = new Task
            success(_recover(owner, jobid, List(task))).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
            val getters = task.getters.get()
            val bytes = _raw_quota(owner)
            When("the original delayed-start queue attempts its first acknowledged lifecycle checkpoints")
            owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
            owner.engine.drainAll() shouldBe 1
            Then("the refused boundary stops every task getter, association, body and canonical success")
            task.getters.get() shouldBe getters
            task.runs.get() shouldBe 0
            task.canonical.get() shouldBe 0
            owner.ids.calls.get() shouldBe 1
            success(owner.events.query(EventStore.Query())).map(_.name) shouldBe Vector("job.delayed.enqueued")
            source.requests.map(_.boundary) shouldBe (if (mode == "running") _normal_boundaries.take(2) else _normal_boundaries.take(1))
            owner.engine.durableLifecycleWriteFailureFacts.map(_.failure) shouldBe Vector(if (mode == "running")
              DurableJobLifecycleWriteFailure.RunningIntentRefused else DurableJobLifecycleWriteFailure.StartIntentRefused)
            owner.native(jobid).seed shouldBe JobLifetimeBudget.Seed(JobLifetimeSnapshot(2, 96, 1, 1), 1, Set.empty, Set.empty)
            _raw_quota(owner) shouldBe bytes
            if (mode == "running") {
              owner.signed(jobid).record.body.identity.revision shouldBe 2L
              owner.signed(jobid).record.body.timeline.map(_.kind) shouldBe Vector("job.durable-start-intent")
            } else owner.signed(jobid) shouldBe retained
          }
        }
        Given("a genuine prepared SQLite authority and a hostile pure current revision at Long.MaxValue")
        withDatabase { database =>
          val owner = database.open()
          val jobid = _id("nr16overflow")
          val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
          val prepared = _prepare(owner, original)
          val supplied = success(new Source(owner.store, original.record, owner.context)(DurableJobLifecycleWriteRequest.StartIntent))
          val current = original.copy(record = original.record.copy(body = original.record.body.copy(
            identity = original.record.body.identity.copy(revision = Long.MaxValue))))
          val writes = owner.entity.writes.get()
          When("the pure recovered projection must calculate the next semantic revision")
          val result = DurableJobRecoveryCheckpointProjection.project(_start_record(owner, jobid, original), supplied, prepared, current, false)
          Then("checked overflow refuses before evidence can acquire provider or lifecycle authority")
          result shouldBe a[Consequence.Failure[_]]
          owner.entity.writes.get() shouldBe writes
          owner.signed(jobid) shouldBe original
          owner.native(jobid).seed.paid shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
        }
      }

      "NR17 stop actual TaskStart, TaskOutcome and Terminal refusal without native refunds or canonical success" in {
        _normal_boundaries.drop(2).foreach { boundary =>
          Given(s"a genuine adopted native JobRun whose original evidence source refuses $boundary")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr17$boundary")
            val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            source.refuse = Some(boundary)
            val task = new Task
            success(_recover(owner, jobid, List(task))).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
            When("the scheduled normal invocation reaches that actual acknowledged task boundary")
            owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
            owner.engine.drainAll() shouldBe 1
            Then("only prior acknowledgements survive and no canonical success or refund is inferred")
            val index = _normal_boundaries.indexOf(boundary)
            source.requests.map(_.boundary) shouldBe _normal_boundaries.take(index + 1)
            owner.entity.jobacks.get() shouldBe index
            owner.signed(jobid).record.body.identity.revision shouldBe index.toLong + 1L
            task.runs.get() shouldBe (if (boundary == DurableJobLifecycleWriteBoundary.TaskStartIntent) 0 else 1)
            task.canonical.get() shouldBe 0
            owner.engine.durableLifecycleWriteFailureFacts.map(_.failure) shouldBe Vector(boundary match {
              case DurableJobLifecycleWriteBoundary.TaskStartIntent => DurableJobLifecycleWriteFailure.TaskStartIntentRefused
              case DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint => DurableJobLifecycleWriteFailure.TaskOutcomeCheckpointRefused
              case _ => DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused
            })
            val native = owner.native(jobid)
            native.seed.paid shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
            native.seed.remaining shouldBe 0L
            native.seed.normalids.size shouldBe 1
            native.seed.claims shouldBe empty
            val bytes = _raw_quota(owner)
            val signed = owner.signed(jobid)
            val later = new Task
            owner.engine.enqueueTaskInJob(jobid, later, owner.context) shouldBe a[Consequence.Failure[_]]
            later.getters.get() shouldBe 0
            later.runs.get() shouldBe 0
            owner.signed(jobid) shouldBe signed
            _raw_quota(owner) shouldBe bytes
          }
        }
      }

      "NR18 continue opaque historical task strings without losing any completed descriptor or prefix value" in {
        Given("a closed non-UniversalId normal descriptor, associated native ID and actual task-event history before reopen")
        withDatabase { database =>
          val first = database.open()
          val jobid = _id("nr18")
          val root = publish(first.store, record(jobid), first.policy)(using first.context)
          val original = _historic(first, root, "opaque:legacy-task/closed")
          success(first.quota.reserveWork(jobid.value, access, 1)(using first.context)).seed.paid shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
          database.closeBeforeReopen(first)
          val owner = database.open()
          val task = new Task
          val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
          success(_recover(owner, jobid, List(task))).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
          When("the normal due invocation signs its actual five checkpoints over the retained closed prefix")
          owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
          owner.engine.drainAll() shouldBe 1
          Then("opaque strings, summaries, timestamps and completed descriptor bytes remain exact without parsing into live TaskId")
          withClue(s"NR18 boundaries=${source.requests.map(_.boundary)}, revisions=${source.revisions}, failures=${owner.engine.durableLifecycleWriteFailureFacts}, signedRevision=${owner.signed(jobid).record.body.identity.revision}: ") {
            task.runs.get() shouldBe 1
          }
          task.canonical.get() shouldBe 1
          val completed = owner.signed(jobid).record.body
          completed.tasks.head shouldBe original.record.body.tasks.head
          completed.timeline.take(original.record.body.timeline.size) shouldBe original.record.body.timeline
          completed.timeline.head.taskId shouldBe Some("opaque:legacy-task/closed")
          completed.tasks.size shouldBe 2
          completed.timeline.size shouldBe original.record.body.timeline.size + 5
          completed.identity.revision shouldBe original.record.body.identity.revision + 5L
          source.revisions shouldBe (3L to 7L).toVector
          completed.timeline.exists(_.kind == "job.runtime-rehydrated") shouldBe false
          owner.engine.queryTasks(jobid).get.tasks.size shouldBe 1
          owner.native(jobid).seed.paid shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
          owner.native(jobid).seed.remaining shouldBe 0L
          owner.native(jobid).seed.normalids shouldBe Set("opaque:legacy-task/closed") ++
            owner.engine._get_record(jobid).get.taskReadModels.map(_.taskId.value)
        }
      }

      "NR19 replace only unassociated Pending declarations with the actual live descriptors" in {
        Given("three Published declaration tickets and two actual complete operation tasks")
        withDatabase { database =>
          val owner = database.open()
          val jobid = _id("nr19")
          val original = publish(owner.store, record(jobid, 3), owner.policy, 3)(using owner.context)
          val first = new Task
          val second = new Task("child")
          val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
          success(_recover(owner, jobid, List(first, second))).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
          val declarations = original.record.body.tasks.map(_.taskId).toSet
          When("the normal scheduled invocation supplies its actually registered read models")
          owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
          owner.engine.drainAll() shouldBe 1
          Then("no definition placeholder becomes a phantom execution, association or duplicate history row")
          withClue(s"NR19 boundaries=${source.requests.map(_.boundary)}, revisions=${source.revisions}, failures=${owner.engine.durableLifecycleWriteFailureFacts}, signedRevision=${owner.signed(jobid).record.body.identity.revision}: ") {
            first.runs.get() shouldBe 1
          }
          second.runs.get() shouldBe 1
          first.canonical.get() shouldBe 1
          second.canonical.get() shouldBe 1
          val models = owner.engine._get_record(jobid).get.taskReadModels
          val signed = owner.signed(jobid).record.body
          signed.tasks.map(_.taskId).toSet shouldBe models.map(_.taskId.value).toSet
          signed.tasks.map(_.taskId).toSet.intersect(declarations) shouldBe empty
          signed.tasks(1).parentTaskId shouldBe Some(signed.tasks.head.taskId)
          signed.tasks(1).relation shouldBe DurableTaskRelation(DurableTaskRelationKind.Child, Some(signed.tasks.head.taskId))
          signed.timeline.flatMap(_.taskId).toSet shouldBe signed.tasks.map(_.taskId).toSet
          signed.timeline.count(_.kind == "task.durable-start-intent") shouldBe 2
          signed.timeline.count(_.kind == "task.durable-outcome-checkpoint") shouldBe 2
          owner.native(jobid).seed shouldBe JobLifetimeBudget.Seed(JobLifetimeSnapshot(6, 224, 1, 1), 1,
            models.map(_.taskId.value).toSet, Set.empty)
        }
      }
    }
  }

  "Closed recovery projection and finite ownership sequences" should {
    "immutable source and coordinator precedence" which {
      "NR20 refuse protected source, access and task changes and checked suffix overflow before signed mutation" in {
        Vector("authorization", "inputs", "definition", "retention", "access", "target", "parent", "reference").foreach { mode =>
          Given(s"an authenticated adopted root whose actual closed source changes $mode")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr20$mode")
            val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            source.transform = supplied => mode match {
              case "authorization" => supplied.copy(projection = supplied.projection.copy(
                authorization = supplied.projection.authorization.copy(requiredScopes = Set("job.read", "new-scope"))))
              case "inputs" => supplied.copy(projection = supplied.projection.copy(
                inputs = Vector(DurableInputReference("extra", DurableValue.Absent))))
              case "definition" => supplied.copy(projection = supplied.projection.copy(
                definitionSnapshot = supplied.projection.definitionSnapshot.copy(hash = "b" * 64)))
              case "retention" => supplied.copy(projection = supplied.projection.copy(
                retention = supplied.projection.retention.copy(retainUntil = Some(instant.plusSeconds(100)))))
              case "access" => supplied.copy(access = access.copy(scopes = access.scopes + "extra"))
              case _ if source.requests.last.boundary == DurableJobLifecycleWriteBoundary.TaskStartIntent =>
                supplied.copy(projection = supplied.projection.copy(tasks = supplied.projection.tasks.map(task => mode match {
                  case "target" => task.copy(target = task.target.copy(operation = task.target.operation.copy(operationId = "other")))
                  case "parent" => task.copy(parentTaskId = Some("orphaned-parent"))
                  case _ => task.copy(relation = DurableTaskRelation(DurableTaskRelationKind.Child, Some("wrong-reference")))
                })))
              case _ => supplied
            }
            val task = new Task
            success(_recover(owner, jobid, List(task))).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
            When("normal dispatch reaches the first source or task equality check affected by that change")
            owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
            owner.engine.drainAll() shouldBe 1
            Then("the precise checkpoint refuses while protected source fields and all prior acknowledgements stay retained")
            val taskchange = Set("target", "parent", "reference").contains(mode)
            owner.signed(jobid).record.body.identity.revision shouldBe (if (taskchange) 3L else 1L)
            owner.entity.jobacks.get() shouldBe (if (taskchange) 2 else 0)
            _protected(owner.signed(jobid).record.body) shouldBe _protected(original.record.body)
            owner.engine.durableLifecycleWriteFailureFacts.map(_.failure) shouldBe Vector(if (taskchange)
              DurableJobLifecycleWriteFailure.TaskStartIntentRefused else DurableJobLifecycleWriteFailure.StartIntentRefused)
            task.runs.get() shouldBe 0
            task.canonical.get() shouldBe 0
            owner.native(jobid).seed.paid shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
          }
        }
        Vector("sequence", "timestamp", "identity").foreach { mode =>
          Given(s"a genuine prepared native authority and a hostile pure $mode suffix candidate")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr20$mode")
            val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
            val prepared = _prepare(owner, original)
            val source = new Source(owner.store, original.record, owner.context)
            val supplied = success(source(DurableJobLifecycleWriteRequest.StartIntent))
            val base = _start_record(owner, jobid, original)
            val candidate = mode match {
              case "timestamp" => base.copy(updatedAt = original.record.body.identity.updatedAt,
                timeline = base.timeline.map(_.copy(occurredAt = original.record.body.identity.updatedAt)))
              case "identity" => base.copy(createdAt = base.createdAt.plusMillis(1))
              case _ => base
            }
            val current = if (mode == "sequence") original.copy(record = original.record.copy(body =
              original.record.body.copy(timeline = Vector(DurableTimelineEvent(Long.MaxValue, instant, "job.submitted", None, None))))) else original
            val writes = owner.entity.writes.get()
            val bytes = _raw_quota(owner)
            When("the pure closed-prefix projection admits its checked next sequence and monotone timestamp")
            val result = DurableJobRecoveryCheckpointProjection.project(candidate, supplied, prepared, current, false)
            Then("overflow, nonadvancing time and immutable identity changes refuse without any provider write")
            result shouldBe a[Consequence.Failure[_]]
            owner.entity.writes.get() shouldBe writes
            owner.signed(jobid) shouldBe original
            _raw_quota(owner) shouldBe bytes
          }
        }
        Given("an actually associated historic descriptor whose closed immutable target conflicts with the same live ID")
        withDatabase { database =>
          val owner = database.open()
          val jobid = _id("nr20executed")
          val root = publish(owner.store, record(jobid), owner.policy)(using owner.context)
          val taskid = TaskId("cncf", "recovery", Some(instant), Some("closed-original"))
          val original = _historic(owner, root, taskid.value)
          val prepared = _prepare(owner, original)
          val model = JobTaskReadModel(taskid, None, JobTaskStatus.Running, instant, None,
            JobTaskResultSummary(true, None), Some("component-a"), Some("service-a"), Some("conflicting"),
            "operation", Some("operation"), Some("root"), Some("own"), Some("per-task"), Some("running"))
          val basis = _start_record(owner, jobid, original).copy(taskReadModels = Vector(model))
          val supplied = success(new Source(owner.store, original.record, owner.context)(DurableJobLifecycleWriteRequest.StartIntent))
            .copy(projection = JobInputBoundsFixtures.evidence(3L).projection.copy(
              authorization = original.record.body.authorization,
              tasks = Vector(descriptor(model)),
              definitionSnapshot = original.record.body.definitionSnapshot))
          val writes = owner.entity.writes.get()
          When("the unchanged live projection validates the attempted rewrite of an actually executed closed identity")
          val result = DurableJobRecoveryCheckpointProjection.project(basis, supplied, prepared, original, false)
          Then("the immutable executed target conflict is refused before any historical descriptor can be replaced")
          result shouldBe a[Consequence.Failure[_]]
          owner.entity.writes.get() shouldBe writes
          owner.signed(jobid) shouldBe original
        }
      }

      "NR21 retain source, authorized load, classifier and eligible port refusal order" in {
        Vector("source", "missing", "corrupt", "access", "running", "started", "proof", "port").foreach { mode =>
          Given(s"one explicit native recovery boundary independently refusing at $mode")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr21$mode")
            val base = record(jobid)
            val original = if (mode == "missing") None else {
              val root = publish(owner.store, base, owner.policy)(using owner.context)
              Some(mode match {
                case "running" => _checkpoint(owner, root, root.record.body.copy(
                  lifecycle = root.record.body.lifecycle.copy(status = DurableJobLifecycleStatus.Running)))
                case "started" => _checkpoint(owner, root, root.record.body.copy(
                  lifecycle = root.record.body.lifecycle.copy(schedule = root.record.body.lifecycle.schedule.copy(
                    startedAt = Some(instant.plusSeconds(10))))))
                case _ => root
              })
            }
            if (mode == "corrupt") _save_job(owner, jobid, _raw_job(owner, jobid).upsertSingle("canonicalJson", "invalid-private-job"))
            val source = candidates(jobid)
            if (mode == "source") source.entries = Vector(source.entries.head.copy(ordinal = -1L))
            if (mode == "access") source.entries = Vector(source.entries.head.copy(access = access.copy(subjectId = "foreign")))
            if (mode == "proof") source.entries = Vector(source.entries.head.copy(replayEvidence = replay.copy(provider = None)))
            val task = new Task
            val port = if (mode == "port") new Port(_ => null) else _port(owner, jobid, List(task))
            original.foreach(value => bind(owner.engine, owner.store, value.record, owner.context, owner.policy))
            val before = _effects(owner, task)
            val loads = owner.entity.loads.get()
            val bytes = _raw_quota(owner)
            val row = if (original.nonEmpty) Some(_raw_job(owner, jobid)) else None
            When("the unchanged public coordinator crosses the selected source, reader, classifier or port boundary")
            val report = owner.engine.recoverDurableStartup(source, owner.store, port, 1)(using owner.context)
            Then("only the eligible port boundary invokes reconstruction, with no subsequent native or runtime mutation")
            if (mode == "source") report shouldBe a[Consequence.Failure[_]]
            else success(report).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Refused
            source.calls.get() shouldBe 1
            port.calls.get() shouldBe (if (mode == "port") 1 else 0)
            if (mode == "source") owner.entity.loads.get() shouldBe loads
            _effects(owner, task) shouldBe before
            _raw_quota(owner) shouldBe bytes
            row.foreach(value => _raw_job(owner, jobid) shouldBe value)
            owner.engine.runtimeState._native_lifetime_bindings.contains(jobid) shouldBe false
          }
        }
      }

      "NR22 retain signed input declaration matching and original malformed input refusal before quota reads" in {
        Vector(false, true).foreach { accepted =>
          Given("the existing legacy SHA/raw payload input, either valid or malformed, at its original admission boundary")
          withDatabase { database =>
            val owner = database.open()
            val jobid = _id(s"nr22$accepted")
            val value = JobInputBoundsFixtures.input(Vector(JobInputBoundsFixtures.descriptor.copy(
              filename = Some("legacy.txt"), byteSize = Some(3), sha256 = Some("abc"),
              inlineBase64 = Some(if (accepted) "YWJj" else "@@"), blobId = Some("blob-1"))))
            val base = record(jobid)
            val original = if (accepted) publish(owner.store, success(DurableJobRecord.createV2(base.body.copy(
              inputs = Vector(DurableInputReference("input-0", DurableValue.Absent))))), owner.policy)(using owner.context)
              else success(owner.store.create(base, access)(using owner.context))
            val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
            val task = new Task
            val port = new Port(_ => DurableJobRuntimeRehydration(jobid, List(task), owner.context, Some(value), None))
            val before = _effects(owner, task)
            val loads = owner.entity.quotaloads.get()
            When("the original runtime input validation runs before read-only native ownership")
            val report = success(owner.engine.recoverDurableStartup(candidates(jobid), owner.store, port, 1)(using owner.context))
            Then("a malformed raw input preserves pure refusal while valid input keeps signed Absent declarations and sanitizer evidence")
            report.candidates.head.fact shouldBe (if (accepted) DurableJobRuntimeRehydrationFact.Registered else DurableJobRuntimeRehydrationFact.Refused)
            port.calls.get() shouldBe 1
            owner.signed(jobid) shouldBe original
            if (accepted) {
              original.record.body.inputs shouldBe Vector(DurableInputReference("input-0", DurableValue.Absent))
              owner.engine._get_record(jobid).get.input shouldBe Some(value.copy(payloads = value.payloads.map(_.sanitized)))
              owner.engine._get_record(jobid).get.input.get.payloads.head.sha256 shouldBe Some("abc")
              When("the actually published input fixture drains its normal due work")
              owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
              owner.engine.drainAll() shouldBe 1
              Then("all five native acknowledgements preserve original signed input matching and exact payment")
              owner.signed(jobid).record.body.inputs shouldBe original.record.body.inputs
              source.requests.map(_.boundary) shouldBe _normal_boundaries
              owner.entity.jobacks.get() shouldBe 5
              task.runs.get() shouldBe 1
              owner.native(jobid).seed.paid shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
            } else {
              owner.entity.quotaloads.get() shouldBe loads
              _effects(owner, task) shouldBe before
              source.requests shouldBe empty
              owner.engine._get_record(jobid) shouldBe None
            }
          }
        }
      }
    }

    "independent numeric sequences and atomic ownership" which {
      "NR23 conserve exact counters, normal IDs and claims in at least 24 fixed-seed fresh-owner sequences" in {
        Given("Seed6970933641, finite registration/due/refusal/reopen actions and literal 2*n, 32+64*n arithmetic")
        val generator = for {
          count <- Gen.choose(1, 3)
          actions <- Gen.listOfN(4, Gen.choose(0, 3))
        } yield (count, actions)
        val property = Prop.forAll(generator) { case (count, actions) =>
          Vector(false, true).foreach { cancelled =>
            withDatabase { database =>
              val lifetime = JobLifetimePolicy(2L * count, 32L + 64L * count, 1)
              val first = database.open(lifetime)
              val jobid = _id(s"nr23$count$cancelled")
              val root = publish(first.store, record(jobid, count), first.policy, count)(using first.context)
              val historicalids = Vector.tabulate(count)(index =>
                TaskId("cncf", "recovery", Some(instant), Some(s"historic$index")).value)
              val original = if (cancelled) {
                historicalids.foreach(taskid => success(first.quota.associate(jobid.value, access, taskid)(using first.context)) shouldBe true)
                _checkpoint(first, root, root.record.body.copy(tasks = root.record.body.tasks.zip(historicalids).map { case (task, taskid) =>
                  task.copy(taskId = taskid, transaction = DurableTransactionDescriptor(DurableTransactionRole.Own,
                    DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed))
                }, timeline = historicalids.zipWithIndex.map { case (taskid, index) =>
                  DurableTimelineEvent(index.toLong + 1, instant, "task.succeeded", Some(taskid), Some("historic"))
                }))
              } else root
              if (cancelled) {
                success(first.quota.cancel(jobid.value, access)(using first.context)) shouldBe true
                val bytes = _raw_quota(first)
                success(first.quota.cancel(jobid.value, access)(using first.context)) shouldBe false
                _raw_quota(first) shouldBe bytes
              }
              database.closeBeforeReopen(first)
              val owner = database.open(lifetime)
              val tasks = List.tabulate(count)(index => new Task(if (index == 0) "root" else "child"))
              val source = bind(owner.engine, owner.store, original.record, owner.context, owner.policy)
              if (cancelled) source.terminalstatus = JobStatus.Cancelled
              var executed = cancelled
              var normalids = if (cancelled) historicalids.toSet else Set.empty[String]
              val paid = JobLifetimeSnapshot(2L * count, 32L + 64L * count, 1, if (cancelled) 0 else 1)
              When("the fresh owner registers the actual native seed with zero additional admission")
              success(_recover(owner, jobid, tasks)).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Registered
              Then("all four charged counters and the closed normal/claim sets match the independent initial model")
              owner.native(jobid).seed shouldBe JobLifetimeBudget.Seed(paid, if (cancelled) 0 else count.toLong, normalids, Set.empty)
              actions.foreach { action =>
                Given(s"the actual finite action $action with executed=$executed and persistedCancel=$cancelled")
                val bytes = _raw_quota(owner)
                val signed = owner.signed(jobid)
                When("the generated action observes, dispatches or attempts a specific duplicate/capacity refusal")
                action match {
                  case 0 => owner.engine.lifetimeSnapshot(jobid) shouldBe Some(paid)
                  case 1 if !executed =>
                    owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
                    owner.engine.drainAll() shouldBe 1
                    executed = true
                    normalids = owner.engine._get_record(jobid).get.taskReadModels.map(_.taskId.value).toSet
                  case 1 => owner.engine.drainAll() shouldBe 0
                  case 2 => success(_recover(owner, jobid, tasks)).candidates.head.fact shouldBe DurableJobRuntimeRehydrationFact.Refused
                  case _ =>
                    val refused = new Task("child")
                    val before = _effects(owner, refused)
                    owner.engine.enqueueTaskInJob(jobid, refused, owner.context) shouldBe a[Consequence.Failure[_]]
                    _effects(owner, refused) shouldBe before
                }
                Then("only actual due associations reduce remaining capacity and no action changes paid counters or claims")
                owner.native(jobid).seed shouldBe JobLifetimeBudget.Seed(paid, if (executed) 0 else count.toLong, normalids, Set.empty)
                if (action != 1 || signed.record.body.lifecycle.status != DurableJobLifecycleStatus.Submitted) {
                  _raw_quota(owner) shouldBe bytes
                  owner.signed(jobid) shouldBe signed
                }
              }
              if (!executed) {
                When("the retained original due state completes the sequence's actual normal dispatch")
                owner.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
                owner.engine.drainAll() shouldBe 1
                normalids = owner.engine._get_record(jobid).get.taskReadModels.map(_.taskId.value).toSet
              }
              Then("each normal or safety branch has exact final payment, IDs, claims and acknowledged terminal behavior")
              val terminal = owner.signed(jobid)
              terminal.record.body.lifecycle.status shouldBe (if (cancelled) DurableJobLifecycleStatus.Cancelled else DurableJobLifecycleStatus.Succeeded)
              tasks.foreach(task => task.runs.get() shouldBe (if (cancelled) 0 else 1))
              if (cancelled) {
                tasks.foreach(task => task.getters.get() shouldBe 0)
                terminal.record.body.tasks shouldBe original.record.body.tasks
                terminal.record.body.timeline.take(count) shouldBe original.record.body.timeline
                source.requests.map(_.boundary) shouldBe Vector(DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
              } else {
                source.requests.size shouldBe 3 + 2 * count
                source.revisions shouldBe (2L to (4L + 2L * count)).toVector
              }
              owner.native(jobid).seed shouldBe JobLifetimeBudget.Seed(paid, 0, normalids, Set.empty)
              val bytes = _raw_quota(owner)
              database.closeBeforeReopen(owner)
              val reopened = database.open(lifetime)
              When("another closed SQLite provider and State register the actual terminal facts and reject their duplicate")
              success(reopened.engine.recoverDurableTerminalFacts(candidates(jobid), reopened.store, 1)(using reopened.context)).candidates.head.fact shouldBe DurableJobTerminalProjectionFact.Registered
              success(reopened.engine.recoverDurableTerminalFacts(candidates(jobid), reopened.store, 1)(using reopened.context)).candidates.head.fact shouldBe DurableJobTerminalProjectionFact.Refused
              Then("reopening retains exact terminal bytes, counters and native ID/claim sets without a write or runtime construction")
              reopened.native(jobid).seed shouldBe JobLifetimeBudget.Seed(paid, 0, normalids, Set.empty)
              reopened.signed(jobid) shouldBe terminal
              _raw_quota(reopened) shouldBe bytes
              reopened.engine._get_record(jobid) shouldBe None
              reopened.timer.calls.get() shouldBe 0
              reopened.entity.writes.get() shouldBe 0
            }
          }
          true
        }
        When("ScalaCheck runs the finite real-owner sequences with default shrinking and the frozen seed")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(6970933641L)), property)
        Then("at least 24 generated sequences preserve the independent complete numeric and ownership model")
        withClue(s"NR23 status=${checked.status}, succeeded=${checked.succeeded}: ") {
          checked.passed shouldBe true
        }
        checked.succeeded should be >= 24
      }

      "NR24 admit exactly one concurrent bridge owner and release only the losing physical provisional lease" in {
        Given("two empty States and one exact-store bridge, with both actual ports blocked before native preparation")
        withDatabase { database =>
          val owner = database.open()
          val jobid = _id("nr24")
          val original = publish(owner.store, record(jobid), owner.policy)(using owner.context)
          val source = new Source(owner.store, original.record, owner.context)
          val bridge = new DurableJobLifecycleWriteBridge(owner.store, source, owner.policy)
          owner.engine.bindDurableLifecycleWriteBridge(bridge)
          val other = JobInputBoundsFixtures.fresh()
          other.engine.bindDurableLifecycleWriteBridge(bridge)
          val ready = new CountDownLatch(2)
          val release = new CountDownLatch(1)
          val failures = new AtomicReference[Throwable]
          val outcomes = Vector.fill(2)(new AtomicReference[DurableJobRuntimeRehydrationFact])
          val tasks = Vector(new Task, new Task)
          val engines = Vector(owner.engine, other.engine)
          val bytes = _raw_quota(owner)
          val writes = owner.entity.writes.get()
          val actors = engines.zipWithIndex.map { case (engine, index) =>
            new Thread(() => try {
              val port = _port(owner, jobid, List(tasks(index)))
              port.before = () => { ready.countDown(); _await(release) }
              val report = success(engine.recoverDurableStartup(candidates(jobid), owner.store, port, 1)(using owner.context))
              outcomes(index).set(report.candidates.head.fact)
            } catch { case error: Throwable => failures.compareAndSet(null, error); () })
          }
          try {
            actors.foreach(_.start())
            _await(ready)
            When("both independent State monitors attempt the one atomic acknowledged bridge entry")
            release.countDown()
            _join(actors)
            Option(failures.get()).foreach(throw _)
            Then("exactly one State publishes paid ownership and the loser publishes no seed, record, timer or entry")
            outcomes.map(_.get()).count(_ == DurableJobRuntimeRehydrationFact.Registered) shouldBe 1
            outcomes.map(_.get()).count(_ == DurableJobRuntimeRehydrationFact.Refused) shouldBe 1
            engines.zipWithIndex.foreach { case (engine, index) =>
              val admitted = outcomes(index).get() == DurableJobRuntimeRehydrationFact.Registered
              engine.runtimeState._native_lifetime_bindings.contains(jobid) shouldBe admitted
              engine._get_record(jobid).nonEmpty shouldBe admitted
              engine.lifetimeSnapshot(jobid) shouldBe (if (admitted) Some(JobLifetimeSnapshot(2, 96, 1, 1)) else None)
              engine.resourceSnapshot.retained shouldBe (if (admitted) 1 else 0)
              engine.resourceSnapshot.pending shouldBe (if (admitted) 1 else 0)
              engine._work_queue.size() shouldBe 0
              tasks(index).runs.get() shouldBe 0
            }
            owner.timer.pendingCount + other.timer.pendingCount shouldBe 1
            bridge.hasAdmittedSnapshot(jobid) shouldBe true
            owner.native(jobid).seed shouldBe JobLifetimeBudget.Seed(JobLifetimeSnapshot(2, 96, 1, 1), 1, Set.empty, Set.empty)
            _raw_quota(owner) shouldBe bytes
            owner.signed(jobid) shouldBe original
            owner.entity.writes.get() shouldBe writes
            source.requests shouldBe empty
          } finally {
            release.countDown()
            _join(actors)
            other.engine.shutdown()
          }
        }
      }
    }
  }

  private val _normal_boundaries = Vector(DurableJobLifecycleWriteBoundary.StartIntent,
    DurableJobLifecycleWriteBoundary.RunningIntent, DurableJobLifecycleWriteBoundary.TaskStartIntent,
    DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint, DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
  private def _id(name: String): JobId = JobInputBoundsFixtures.id(name)
  private def _port(owner: Owner, jobid: JobId, tasks: List[JobTask]): Port =
    new Port(_ => DurableJobRuntimeRehydration(jobid, tasks, owner.context, None, None))
  private def _recover(owner: Owner, jobid: JobId, tasks: List[JobTask]): Consequence[DurableJobRuntimeRehydrationReport] =
    owner.engine.recoverDurableStartup(candidates(jobid), owner.store, _port(owner, jobid, tasks), 1)(using owner.context)
  private def _binding(owner: Owner, jobid: JobId): JobDurableLifetimeRuntimeBinding =
    owner.engine.runtimeState._native_lifetime_bindings(jobid)
  private def _effects(owner: Owner, task: Task) =
    (owner.engine.resourceSnapshot, owner.ids.calls.get(), owner.clock.calls.get(), owner.timer.calls.get(),
      owner.timer.pendingCount, owner.engine._work_queue.size(), task.getters.get(), task.runs.get(),
      task.canonical.get(), task.compensations.get(), success(owner.events.query(EventStore.Query())).size)
  private def _protected(body: DurableJobRecordBody) =
    (body.authorization, body.inputs, body.definitionSnapshot, body.retention, body.calltreeReference)
  private def _prepare(owner: Owner, snapshot: DurableJobStoreSnapshot): DurableJobLifetimeRecovery.Prepared =
    success(DurableJobLifetimeRecovery.footprint(snapshot, JobResourcePolicy.default, owner.policy.lifetime)
      .flatMap(value => DurableJobLifetimeRecovery.prepare(owner.store, snapshot, access, owner.context,
        owner.policy, value, None)))
  private def _historic(owner: Owner, root: DurableJobStoreSnapshot, taskid: String): DurableJobStoreSnapshot = {
    success(owner.quota.associate(root.record.body.identity.jobId, access, taskid)(using owner.context)) shouldBe true
    val task = root.record.body.tasks.head.copy(taskId = taskid, transaction = DurableTransactionDescriptor(
      DurableTransactionRole.Own, DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed))
    _checkpoint(owner, root, root.record.body.copy(tasks = Vector(task), timeline = Vector(
      DurableTimelineEvent(1, instant, "task.succeeded", Some(taskid), Some("exact original summary")))))
  }
  private def _start_record(owner: Owner, jobid: JobId, snapshot: DurableJobStoreSnapshot): JobRecord = {
    val updatedat = snapshot.record.body.identity.updatedAt.plusMillis(1)
    JobRecord(jobid, List(new Task), owner.context, JobStatus.Submitted, None,
      JobPersistencePolicy.Persistent, JobRunMode.Async, 0, None, snapshot.record.body.identity.createdAt,
      updatedat, Vector.empty, timeline = Vector(JobTimelineEvent(1, updatedat, "job.durable-start-intent", None, None, None)),
      debug = JobDebugInfo(None, Map.empty, Vector.empty), input = None)
  }
  private def _control(owner: Owner, jobid: JobId, command: JobControlCommand): Consequence[JobControlResponse] =
    owner.engine.control(jobid, JobControlRequest(command), new JobControlPolicy {
      def authorize(id: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = Consequence.unit
    })(using owner.context)
  private def _await(latch: CountDownLatch): Unit =
    if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("bounded native recovery barrier timed out")
  private def _join(actors: Vector[Thread]): Unit = actors.foreach { actor =>
    actor.join(5000)
    if (actor.isAlive) { actor.interrupt(); actor.join(5000) }
    actor.isAlive shouldBe false
  }
  private def _checkpoint(owner: Owner, previous: DurableJobStoreSnapshot, body: DurableJobRecordBody): DurableJobStoreSnapshot =
    success(owner.store.checkpoint(previous, success(DurableJobRecord.createV2(body.copy(identity = body.identity.copy(
      revision = previous.record.body.identity.revision + 1L)))), access)(using owner.context))
  private def _terminal(original: DurableJobRecord): DurableJobRecord = success(DurableJobRecord.createV2(original.body.copy(
    lifecycle = original.body.lifecycle.copy(status = DurableJobLifecycleStatus.Succeeded,
      schedule = original.body.lifecycle.schedule.copy(scheduledAt = None, completedAt = Some(instant.plusSeconds(20)))),
    tasks = original.body.tasks.zipWithIndex.map { case (task, index) => task.copy(
      taskId = TaskId("cncf", "recovery", Some(instant), Some(s"closed$index")).value,
      transaction = DurableTransactionDescriptor(DurableTransactionRole.Own,
        DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed)) },
    result = DurableResultOutcome.Succeeded(DurableValue.Absent))))
  private def _quota_collection = DataStore.CollectionId.EntityStore(DurableJobLifetimeQuotaEntity.Collection)
  private def _raw_quota(owner: Owner): Option[Record] = success(owner.provider.load(_quota_collection,
    DataStore.EntryId(success(DurableJobLifetimeQuotaEntity.entityId)))(using owner.context))
  private def _quota_text(owner: Owner): String = _raw_quota(owner).get.getString("canonicalJson").get
  private def _replace_quota(owner: Owner, text: String): Unit = {
    success(owner.provider.save(_quota_collection, DataStore.EntryId(success(DurableJobLifetimeQuotaEntity.entityId)),
      _raw_quota(owner).get.upsertSingle("canonicalJson", text))(using owner.context)); ()
  }
  private def _raw_job(owner: Owner, jobid: JobId): Record = success(owner.provider.load(
    DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection),
    DataStore.EntryId(success(DurableJobStoreEntity.entityId(jobid.value))))(using owner.context)).get
  private def _save_job(owner: Owner, jobid: JobId, row: Record): Unit = {
    success(owner.provider.save(DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection),
      DataStore.EntryId(success(DurableJobStoreEntity.entityId(jobid.value))), row)(using owner.context)); ()
  }
}
