package org.goldenport.cncf.job

import java.time.Duration
import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkTermination}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobUnitOfWorkSubmissionFixtures.*

/*
 * @since   Oct.  6, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobUnitOfWorkSubmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-unit-of-work-submission-contract, example:$example, rules:$rules, phase:69.7, slice:JM69-10CBD2B")

  "Native UOW Job submission" should {
    "reservation and publication" which {
      "UJS1 genuine reserved identity" must _scenario("UJS1", "R1,R2,R3") {
        "publish the exact issued ID only after outer commit and retain its signed terminal outcome" in {
          Given("docs/spec/job-unit-of-work-submission-contract.md UJS1: a real Ready Subsystem owner, managed SQLite/Entity/Blob, and an open UOW")
          JobUnitOfWorkSubmissionFixtures.withFixture { fixture =>
            val native = fixture.native
            val task = new Task
            val uow = fixture.unitOfWork()
            val beforeids = native.ids.calls.get()
            val beforewrites = native.entity.writes.get()
            val beforeledger = fixture.ledger
            val beforetimers = fixture.timerCount
            When("the original engine reserves a native ID through the UOW execution context")
            val id = fixture.stage(task, uow)
            Then("one native ID exists without any record, Entity Job, lifetime root, evidence, timer, queue, task getter or execution")
            native.ids.calls.get() shouldBe beforeids + 1
            native.engine.getStatus(id) shouldBe None
            native.engine.query(id) shouldBe None
            native.engine.lifetimeSnapshot(id) shouldBe None
            _signed_option(fixture, id) shouldBe None
            success(native.owner._execute_c {
              native.entity.load[JobEntity](JobEntity.entityId(id))(using JobEntity.entityPersistent, native.context)
            }) shouldBe None
            fixture.ledger shouldBe beforeledger
            native.entity.writes.get() shouldBe beforewrites
            native.evidence.requests shouldBe empty
            fixture.events.query(org.goldenport.cncf.event.EventStore.Query()).toOption.get shouldBe empty
            fixture.timerCount shouldBe beforetimers
            native.engine._work_queue.size() shouldBe 0
            native.engine.resourceSnapshot.retained shouldBe 1
            native.engine.resourceSnapshot.pending shouldBe 1
            task.getters.get() shouldBe 0
            task.runs.get() shouldBe 0
            _accepted_submissions(fixture) shouldBe 0L
            When("the outer UOW commits before manual native drain")
            val committed = uow.commit()
            Then("that exact ID is acknowledged Submitted/Published through the same owner, without running the body")
            committed.isSuccess shouldBe true
            uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
            fixture.recorder.entries should contain inOrderOnly (
              "UnitOfWork.prepare", "EventEngine.prepare", "UnitOfWork.commit", "EventEngine.commit", "DataStore.commit")
            native.engine.listJobs().map(_.jobId) shouldBe Vector(id)
            native.engine.getStatus(id) shouldBe Some(JobStatus.Submitted)
            native.signed(id).record.body.identity.jobId shouldBe id.value
            native.signed(id).record.body.identity.revision shouldBe 1L
            fixture.ledger.get.entries.find(_.jobid == id.value).get.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
            native.native(id).seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
            native.evidence.requests.map(_.request.boundary) shouldBe Vector(DurableJobLifecycleWriteBoundary.Admission)
            native.engine._work_queue.size() shouldBe 1
            task.runs.get() shouldBe 0
            _accepted_submissions(fixture) shouldBe 1L
            native.node.activeLeaseCount shouldBe 0
            When("the original manual engine drains its admitted job")
            val drained = native.engine.drainAll()
            Then("the one body and signed terminal revision survive under the same issued identity")
            drained shouldBe 1
            task.runs.get() shouldBe 1
            task.canonical.get() shouldBe 1
            native.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
            native.signed(id).record.body.identity.revision shouldBe 6L
            native.signed(id).record.body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
            native.evidence.requests.map(_.expectedrevision) shouldBe Vector(1L, 2L, 3L, 4L, 5L, 6L)
            native.engine.resourceSnapshot.pending shouldBe 0
          }
        }
      }

      "UJS7 actual native pending capacity" must _scenario("UJS7", "R1,R4") {
        "refuse before ID issuance at the real bound and reuse exactly the aborted slot" in {
          Given("docs/spec/job-unit-of-work-submission-contract.md UJS7: genuine original-budget leases fill all but one pending slot")
          JobUnitOfWorkSubmissionFixtures.withFixture { fixture =>
            val native = fixture.native
            val budget = native.engine._resource_budget
            val leases = Vector.fill(budget.policy.maxpendingwork - 1)(success(budget.reserve(
              native.engine._resource_owner, retained = true, pending = true, root = true)))
            try {
              val firstuow = fixture.unitOfWork()
              val refuseduow = fixture.unitOfWork()
              val lateruow = fixture.unitOfWork()
              val task = new Task
              val beforeids = native.ids.calls.get()
              When("one stage fills the last slot and a second stage requests another")
              val firstid = fixture.stage(task, firstuow)
              val refused = native.engine.stageSubmit(List(task), refuseduow, option)
              Then("the second request refuses before issuing an ID or publishing evidence")
              refused.isSuccess shouldBe false
              native.ids.calls.get() shouldBe beforeids + 1
              native.engine.resourceSnapshot.pending shouldBe budget.policy.maxpendingwork
              native.evidence.requests shouldBe empty
              When("abort frees precisely the staged lease and another open UOW stages work")
              success(firstuow.abort())
              val freed = native.engine.resourceSnapshot
              val laterid = fixture.stage(task, lateruow)
              Then("exactly one slot is reusable while all genuine fixture leases remain held")
              freed.pending shouldBe budget.policy.maxpendingwork - 1
              freed.retained shouldBe leases.size
              laterid should not be firstid
              native.engine.resourceSnapshot.pending shouldBe budget.policy.maxpendingwork
              native.engine.getStatus(firstid) shouldBe None
              native.engine.drainAll() shouldBe 0
              task.runs.get() shouldBe 0
              success(lateruow.abort())
            } finally leases.foreach(budget.release)
            native.engine.resourceSnapshot.pending shouldBe 0
            native.engine.resourceSnapshot.retained shouldBe 0
          }
        }
      }
    }

    "termination ownership" which {
      "UJS2 abort and prepare refusal" must _scenario("UJS2", "R3,R4") {
        "release unpublished native capacity once and never dispatch after repeated endings" in {
          Vector(false, true).foreach { reject =>
            Given(s"docs/spec/job-unit-of-work-submission-contract.md UJS2: an open native UOW with prepare rejection=$reject")
            JobUnitOfWorkSubmissionFixtures.withFixture { fixture =>
              val native = fixture.native
              val task = new Task
              val uow = fixture.unitOfWork(if (reject) EventFault.PrepareRejected else EventFault.None)
              val id = fixture.stage(task, uow)
              When("the reservation terminates by explicit abort or rejected EventEngine prepare")
              val result = if (reject) uow.commit() else uow.abort()
              Then("retained and pending resources return to zero without a published identity")
              result.isSuccess shouldBe !reject
              if (reject) uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Aborted)
              native.engine.resourceSnapshot.retained shouldBe 0
              native.engine.resourceSnapshot.pending shouldBe 0
              native.engine.getStatus(id) shouldBe None
              _signed_option(fixture, id) shouldBe None
              native.evidence.requests shouldBe empty
              When("abort, dispose and commit repeat after termination")
              success(uow.abort()); success(uow.dispose()); uow.commit()
              Then("the released reservation cannot reappear or dispatch twice")
              native.engine.drainAll() shouldBe 0
              native.engine.resourceSnapshot.retained shouldBe 0
              native.engine.resourceSnapshot.pending shouldBe 0
              task.runs.get() shouldBe 0
              task.getters.get() shouldBe 0
              _accepted_submissions(fixture) shouldBe 0L
            }
          }
        }
      }

      "UJS3 ended UOW registration" must _scenario("UJS3", "R3,R4") {
        "refuse late staging and prevent dispose followed by commit from resurrecting the reservation" in {
          Given("docs/spec/job-unit-of-work-submission-contract.md UJS3: a staged native reservation disposed before commit")
          JobUnitOfWorkSubmissionFixtures.withFixture { fixture =>
            val native = fixture.native
            val task = new Task
            val uow = fixture.unitOfWork()
            val id = fixture.stage(task, uow)
            When("dispose releases Pending and commit subsequently invokes the retained callback")
            success(uow.dispose())
            val committed = uow.commit()
            Then("commit fails after its real outer commit and cannot resurrect Released")
            committed.isSuccess shouldBe false
            uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
            native.engine.resourceSnapshot.retained shouldBe 0
            native.engine.resourceSnapshot.pending shouldBe 0
            native.engine.getStatus(id) shouldBe None
            native.engine.drainAll() shouldBe 0
            task.runs.get() shouldBe 0
          }
          Ending.values.foreach { ending =>
            Given(s"docs/spec/job-unit-of-work-submission-contract.md UJS3: an already $ending UOW on a Ready native owner")
            JobUnitOfWorkSubmissionFixtures.withFixture { fixture =>
              val native = fixture.native
              val task = new Task
              val uow = fixture.unitOfWork()
              success(fixture.end(uow, ending))
              val before = native.engine.resourceSnapshot
              When("registerResourceC immediately releases the new reservation while reporting successful registration")
              val result = native.engine.stageSubmit(List(task), uow, option)
              Then("stage refuses instead of returning a false successful ID or appending a live callback")
              result.isSuccess shouldBe false
              native.engine.resourceSnapshot shouldBe before
              native.evidence.requests shouldBe empty
              success(uow.dispose()); uow.commit()
              native.engine.drainAll() shouldBe 0
              native.engine.listJobs() shouldBe empty
              task.runs.get() shouldBe 0
              task.getters.get() shouldBe 0
            }
          }
        }
      }

      "UJS8 shrinking native endings" must _scenario("UJS8", "R3,R4") {
        "preserve exact identities, publication counts and dispatch under Commit Abort and Dispose" in {
          Given("docs/spec/job-unit-of-work-submission-contract.md UJS8: ordinary shrinking over 1..8 jobs and all three UOW endings")
          val property = Prop.forAll(Gen.choose(1, 8), Gen.oneOf(Ending.values.toSeq)) { (count, ending) =>
            Given(s"UJS8 generated case: $count jobs, $ending, a fresh actual configured owner and a unique target-private root")
            JobUnitOfWorkSubmissionFixtures.withFixture { fixture =>
              val native = fixture.native
              val uow = fixture.unitOfWork()
              val tasks = Vector.fill(count)(new Task)
              When("each task stages a genuine ID without native publication")
              val ids = tasks.map(fixture.stage(_, uow))
              Then("all IDs are distinct and the exact pending capacity is held before any body")
              ids.distinct should have size count
              native.engine.resourceSnapshot.pending shouldBe count
              native.engine.listJobs() shouldBe empty
              native.evidence.requests shouldBe empty
              tasks.map(_.runs.get()) shouldBe Vector.fill(count)(0)
              When(s"the generated UOW reaches $ending and the original engine drains")
              val result = fixture.end(uow, ending)
              val published = native.engine.listJobs().map(_.jobId).toSet
              val pending = native.engine.resourceSnapshot.pending
              val drained = native.engine.drainAll()
              Then("only Commit publishes the exact native IDs/count and every other ending has zero dispatch")
              result.isSuccess shouldBe true
              published shouldBe (if (ending == Ending.Commit) ids.toSet else Set.empty[JobId])
              pending shouldBe (if (ending == Ending.Commit) count else 0)
              drained shouldBe (if (ending == Ending.Commit) count else 0)
              native.engine.resourceSnapshot.pending shouldBe 0
              native.engine.resourceSnapshot.retained shouldBe (if (ending == Ending.Commit) count else 0)
              tasks.map(_.runs.get()) shouldBe Vector.fill(count)(if (ending == Ending.Commit) 1 else 0)
              ids.foreach { id =>
                if (ending == Ending.Commit) {
                  native.signed(id).record.body.identity.jobId shouldBe id.value
                  native.signed(id).record.body.identity.revision shouldBe 6L
                  native.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
                } else _signed_option(fixture, id) shouldBe None
              }
              if (ending != Ending.Commit) {
                When("a generated aborted or disposed UOW is committed again")
                uow.commit()
                Then("no released generated ID is resurrected")
                native.engine.listJobs() shouldBe empty
                native.engine.drainAll() shouldBe 0
                native.engine.resourceSnapshot.pending shouldBe 0
              }
              true
            }
          }
          When("ScalaCheck executes seed69710201 with ordinary shrinking and at least24 successful cases")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69710201L)), property)
          Then("all generated native publication and release contracts hold")
          result.passed shouldBe true
          result.succeeded should be >= 24
        }
      }
    }

    "committed refusal and shutdown" which {
      "UJS4 native evidence and publication failures" must _scenario("UJS4", "R4,R5") {
        "retain committed transaction evidence while releasing only unactivated resources" in {
          Vector("keyed-failure", "bridge-refusal", "bridge-exception", "bridge-control", "published-event-exception", "outer-event-exception").foreach { mode =>
            Given(s"docs/spec/job-unit-of-work-submission-contract.md UJS4: actual native lifecycle fault $mode on the original ready owner")
            val control = new LinkageError("planned native admission control")
            configuredFixture { native =>
              mode match {
                case "keyed-failure" => native.selectedEvidence = new DurableJobKeyedEvidenceFixtures.Probe(_ =>
                  Consequence.stateConflict("planned keyed admission Failure"))
                case "bridge-refusal" => native.selectedEvidence = new DurableJobKeyedEvidenceFixtures.Source(
                  _ => DurableJobKeyedEvidenceFixtures.recipe(), _ => DurableJobKeyedEvidenceFixtures.Fault.WrongRevision)
                case "bridge-exception" => native.selectedEvidence = new DurableJobKeyedEvidenceFixtures.Probe(_ =>
                  throw new IllegalStateException("planned bridge admission exception"))
                case "bridge-control" => native.selectedEvidence = new DurableJobKeyedEvidenceFixtures.Probe(_ => throw control)
                case _ => ()
              }
            } { fixture =>
              val native = fixture.native
              val task = new Task
              val uow = fixture.unitOfWork(if (mode == "outer-event-exception") EventFault.CommitException else EventFault.None)
              val id = fixture.stage(task, uow)
              if (mode == "published-event-exception") fixture.failNativeEmit()
              When("outer commit reaches the real lifecycle callback or its modeled event commit fault")
              val result = if (mode == "bridge-control") {
                val thrown = intercept[LinkageError](uow.commit())
                thrown should be theSameInstanceAs control
                uow.lastCommitResult.get
              } else uow.commit()
              Then("the structured failure is attributed, the body never runs, and only unactivated capacity releases")
              result.isSuccess shouldBe false
              val conclusion = JobDurableRuntimeFixtures.failureConclusion(result)
              if (mode == "keyed-failure") conclusion.observation.getEffectiveMessage shouldBe Some("planned keyed admission Failure")
              if (mode == "bridge-refusal") conclusion.observation.getEffectiveMessage shouldBe Some("durable keyed lifecycle evidence refused")
              if (mode == "bridge-exception") conclusion.show should include ("planned bridge admission exception")
              if (mode == "published-event-exception") conclusion.show should include ("planned native event commit exception")
              uow.lastCommitTermination shouldBe Some(
                if (mode == "outer-event-exception") UnitOfWorkTermination.Aborted else UnitOfWorkTermination.Committed)
              uow.lastAbortResult shouldBe None
              native.engine._work_queue.size() shouldBe 0
              native.engine.drainAll() shouldBe 0
              task.runs.get() shouldBe 0
              native.engine.resourceSnapshot.pending shouldBe (if (mode == "published-event-exception") 1 else 0)
              native.engine.resourceSnapshot.retained shouldBe (if (mode == "published-event-exception") 1 else 0)
              if (mode == "published-event-exception") {
                val paid = native.native(id)
                native.engine.getStatus(id) shouldBe Some(JobStatus.Submitted)
                fixture.ledger.get.entries.find(_.jobid == id.value).get.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
                native.signed(id).record.body.identity.revision shouldBe 1L
                success(uow.dispose()); uow.commit()
                native.native(id) shouldBe paid
                native.engine.resourceSnapshot.pending shouldBe 1
              } else {
                native.engine.getStatus(id) shouldBe None
                val before = fixture.ledger
                success(uow.dispose()); uow.commit()
                fixture.ledger shouldBe before
                native.engine.resourceSnapshot.pending shouldBe 0
              }
              _accepted_submissions(fixture) shouldBe 0L
              native.node.activeLeaseCount shouldBe 0
            }
          }
        }
      }

      "UJS5 native shutdown gate" must _scenario("UJS5", "R1,R4") {
        "refuse activation after quiesce or forceCancel and release every provisional resource" in {
          Vector(false, true).foreach { force =>
            Given(s"docs/spec/job-unit-of-work-submission-contract.md UJS5: a genuine staged native reservation before forceCancel=$force")
            JobUnitOfWorkSubmissionFixtures.withFixture { fixture =>
              val native = fixture.native
              val task = new Task
              val uow = fixture.unitOfWork()
              val id = fixture.stage(task, uow)
              When("the original shutdown gate closes between reserve and outer commit")
              if (force) native.engine.forceCancel() else native.engine.quiesce()
              val result = uow.commit()
              Then("activation refuses, exact pending/provisional resources release and no queue or provider body appears")
              result.isSuccess shouldBe false
              uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
              native.engine.resourceSnapshot.pending shouldBe 0
              native.engine.resourceSnapshot.retained shouldBe 0
              native.engine._work_queue.size() shouldBe 0
              native.engine.getStatus(id) shouldBe None
              native.evidence.requests shouldBe empty
              task.runs.get() shouldBe 0
              task.getters.get() shouldBe 0
              native.node.activeLeaseCount shouldBe 0
            }
          }
        }
      }

      "UJS9 callback Failure aggregation" must _scenario("UJS9", "R3,R5") {
        "activate once after an unrelated earlier Failure and preserve engine ownership after repeated endings" in {
          Given("docs/spec/job-unit-of-work-submission-contract.md UJS9: an earlier unrelated failing post-commit callback and a genuine staged job")
          JobUnitOfWorkSubmissionFixtures.withFixture { fixture =>
            val native = fixture.native
            val uow = fixture.unitOfWork()
            val task = new Task
            uow.stagePostCommitC(Consequence.operationInvalid("unrelated.callback", "planned earlier Failure"))
            val id = fixture.stage(task, uow)
            When("the outer UOW commits and runs every structured callback")
            val result = uow.commit()
            Then("overall Failure retains Committed termination and exact native Submitted/Published acknowledgement")
            result.isSuccess shouldBe false
            JobDurableRuntimeFixtures.failureConclusion(result).show should include ("planned earlier Failure")
            uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
            native.engine.getStatus(id) shouldBe Some(JobStatus.Submitted)
            native.signed(id).record.body.identity.revision shouldBe 1L
            native.evidence.requests.map(_.request.boundary) shouldBe Vector(DurableJobLifecycleWriteBoundary.Admission)
            val paid = native.native(id)
            When("dispose and commit repeat after activation, before the original engine drains")
            success(uow.dispose()); success(uow.commit()); success(uow.dispose())
            val pending = native.engine.resourceSnapshot.pending
            val drained = native.engine.drainAll()
            Then("Activated capacity stayed engine-owned, native payment is retained and execution occurs once")
            pending shouldBe 1
            drained shouldBe 1
            task.runs.get() shouldBe 1
            task.canonical.get() shouldBe 1
            native.engine.listJobs().map(_.jobId) shouldBe Vector(id)
            native.native(id).seed.paid shouldBe paid.seed.paid
            native.signed(id).record.body.identity.revision shouldBe 6L
            _accepted_submissions(fixture) shouldBe 1L
            native.engine.drainAll() shouldBe 0
          }
        }
      }
    }

    "input and scheduling compatibility" which {
      "UJS6 staging preflight" must _scenario("UJS6", "R1,R2") {
        "refuse invalid candidates and unsupported ownership before ID, evidence or body" in {
          Given("docs/spec/job-unit-of-work-submission-contract.md UJS6: a Ready native owner and invalid bounded/null/Sync/Ephemeral candidates")
          JobUnitOfWorkSubmissionFixtures.withFixture { fixture =>
            val native = fixture.native
            val task = new Task
            val uow = fixture.unitOfWork()
            val invalidinput = JobInputBoundsFixtures.input(Vector.fill(1025)(JobInputBoundsFixtures.descriptor))
            val candidates = Vector[(List[JobTask], UnitOfWork, JobSubmitOption)](
              (null, uow, option), (List(null), uow, option), (List(task), null, option),
              (List(task), uow, null), (List(task), uow, option.copy(input = null)),
              (List(task), uow, option.copy(input = Some(null))),
              (List(task), uow, option.copy(input = Some(invalidinput))),
              (List(task), uow, option.copy(runMode = JobRunMode.Sync)),
              (List(task), uow, option.copy(persistence = JobPersistencePolicy.Ephemeral)),
              (List(task), uow, option.copy(executionNotes = null)),
              (List(task), uow, option.copy(executionNotes = Vector(null))),
              (List(task), uow, option.copy(executionNotes = Vector.fill(native.engine.runtimeState._lifetime_budget.policy.maxhistorycredits.toInt + 1)("note"))),
              (List.fill(native.engine._resource_budget.policy.maxtaskspersubmission + 1)(task), uow, option),
              (List(task), uow, option.copy(scheduledStartAt = Some(native.engine._now().plus(InMemoryJobEngine.MaxNonRetryDelay).plusSeconds(1)))),
              (List(task), uow, option.copy(parameters = null)),
              (List(task), uow, option.copy(declaredProfile = Some(null))))
            val beforeids = native.ids.calls.get()
            val before = native.engine.resourceSnapshot
            When("each invalid candidate is staged on the native API and a normal engine uses the trait default")
            val results = candidates.map { case (tasks, selected, submitoption) =>
              native.engine.stageSubmit(tasks, selected, submitoption)
            }
            val unsupported = new UnsupportedEngine(native.engine).stageSubmit(List(task), uow, option)
            Then("every refusal is structured and no immediate submission fallback issues an ID or body")
            results.foreach(_.isSuccess shouldBe false)
            unsupported.isSuccess shouldBe false
            JobDurableRuntimeFixtures.failureConclusion(unsupported).show should include ("UOW submission is not supported")
            native.ids.calls.get() shouldBe beforeids
            native.engine.resourceSnapshot shouldBe before
            native.evidence.requests shouldBe empty
            native.engine.listJobs() shouldBe empty
            task.getters.get() shouldBe 0
            task.runs.get() shouldBe 0
            When("the actual bound lifecycle bridge is absent while the original owner remains present")
            native.engine.bindDurableLifecycleWriteBridge(null)
            val unbound = native.engine.stageSubmit(List(task), uow, option)
            Then("the native API refuses without fallback or ID issuance")
            unbound.isSuccess shouldBe false
            native.ids.calls.get() shouldBe beforeids
            native.engine.resourceSnapshot shouldBe before
          }
          Given("UJS6: the original Subsystem engine has no admitted durable owner")
          JobDurableRuntimeFixtures.withFixture { native =>
            val task = new Task
            val beforeids = native.ids.calls.get()
            val uow = new UnitOfWork(native.context)
            try {
              When("stageSubmit requests native publication ownership before startup")
              val result = native.engine.stageSubmit(List(task), uow, option)
              Then("absent ownership refuses before ID/evidence/task body")
              result.isSuccess shouldBe false
              native.ids.calls.get() shouldBe beforeids
              native.engine.resourceSnapshot.pending shouldBe 0
              task.runs.get() shouldBe 0
              native.evidence.requests shouldBe empty
            } finally uow.dispose()
          }
        }
      }

      "UJS10 commit-time scheduling and ordinary submit" must _scenario("UJS10", "R2,R3") {
        "retain option metadata and arm only the original timer while preserving ordinary Sync Async behavior" in {
          Vector(false, true).foreach { elapsed =>
            Given(s"docs/spec/job-unit-of-work-submission-contract.md UJS10: an original native timer and a schedule elapsed=$elapsed with complete submission metadata")
            JobUnitOfWorkSubmissionFixtures.configuredFixture { native =>
              val base = DurableJobKeyedEvidenceFixtures.recipe()
              val supplied = base.copy(projection = base.projection.copy(
                definitionSnapshot = base.projection.definitionSnapshot.copy(
                  definitionId = "definition-uow", key = "uow-key", format = Some("jcl")),
                inputs = Vector(DurableInputReference("upload", DurableValue.Absent))))
              native.selectedEvidence = new DurableJobKeyedEvidenceFixtures.Source(_ => supplied)
            } { fixture =>
              val native = fixture.native
              val task = new Task
              val uow = fixture.unitOfWork()
              val before = native.engine._now()
              val dueat = before.plusSeconds(10)
              val profile = JobDeclaredProfile(expectedStatus = Some(JobStatus.Succeeded))
              val definition = JobDefinitionSnapshot("definition-uow", "uow-key", Some(profile), Some("run"), Some("jcl"))
              val input = JobInputBoundsFixtures.input(Vector(JobInputBoundsFixtures.descriptor))
              val submitoption = option.copy(priority = 7, scheduledStartAt = Some(dueat),
                requestSummary = Some("staged metadata"), parameters = Map("parameter" -> "kept"),
                executionNotes = Vector("original note"), declaredProfile = Some(profile),
                jobDefinitionSnapshot = Some(definition), input = Some(input))
              val timers = fixture.timerCount
              When("work stages and the original clock advances before outer commit")
              val id = fixture.stage(task, uow, submitoption)
              native.advance(Duration.ofSeconds(if (elapsed) 11 else 2))
              Then("no native job timer or body exists before commit, even if its intended schedule elapsed")
              fixture.timerCount shouldBe timers
              task.runs.get() shouldBe 0
              task.getters.get() shouldBe 0
              native.engine.getStatus(id) shouldBe None
              native.evidence.requests shouldBe empty
              val commitat = native.engine._now()
              When("the UOW activates its exact native identity at the real commit-time clock")
              success(uow.commit())
              val record = native.engine.runtimeState.durableJobs.get(id)
              Then("original metadata/input/priority/tasks persist and only a still-future schedule is armed")
              record.id shouldBe id
              record.tasks shouldBe List(task)
              (record.submittedContext eq uow.executionContext) shouldBe true
              record.createdAt shouldBe commitat
              record.priority shouldBe 7
              record.input shouldBe Some(input)
              record.debug.requestSummary shouldBe Some("staged metadata")
              record.debug.parameters shouldBe Map("parameter" -> "kept", "jcl.jobDefinition.id" -> "definition-uow",
                "jcl.jobDefinition.key" -> "uow-key", "jcl.jobDefinition.source" -> "run", "jcl.jobDefinition.format" -> "jcl")
              record.debug.executionNotes shouldBe Vector("original note")
              record.debug.declaredProfile shouldBe Some(profile)
              record.debug.jobDefinitionSnapshot shouldBe Some(definition)
              record.scheduledStartAt shouldBe (if (elapsed) None else Some(dueat))
              fixture.timerCount shouldBe timers + (if (elapsed) 0 else 1)
              native.engine._work_queue.size() shouldBe (if (elapsed) 1 else 0)
              if (!elapsed) {
                When("the original timer advances to just before the future schedule")
                native.advance(Duration.ofSeconds(7))
                Then("the future body stays queued nowhere and has not run")
                native.engine.drainAll() shouldBe 0
                task.runs.get() shouldBe 0
                When("the original timer reaches the intended due instant")
                native.advance(Duration.ofSeconds(1))
              } else When("the already elapsed schedule drains immediately from the original native queue")
              val drained = native.engine.drainAll()
              Then("the scheduled task runs exactly once under the same ID and signed revision")
              drained shouldBe 1
              task.runs.get() shouldBe 1
              native.signed(id).record.body.identity.revision shouldBe 6L
              native.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
            }
          }
          Given("UJS10 compatibility: ordinary Sync, immediate Async and delayed Async use the same original native builder/publication")
          JobUnitOfWorkSubmissionFixtures.withFixture { fixture =>
            val native = fixture.native
            val sync = new Task
            val async = new Task
            val delayed = new Task
            val dueat = native.engine._now().plusSeconds(5)
            When("ordinary submit runs inline Sync and retains immediate/delayed Async admission")
            val syncid = success(native.owner._execute_c(native.engine.submit(List(sync), native.context, option.copy(runMode = JobRunMode.Sync))))
            val asyncid = success(native.owner._execute_c(native.engine.submit(List(async), native.context, option)))
            val delayedid = success(native.owner._execute_c(native.engine.submit(List(delayed), native.context, option.copy(scheduledStartAt = Some(dueat)))))
            Then("Sync returns its original terminal result while Async leaves exact pending work and metadata")
            sync.runs.get() shouldBe 1
            native.engine.getStatus(syncid) shouldBe Some(JobStatus.Succeeded)
            native.signed(syncid).record.body.identity.revision shouldBe 6L
            native.engine.query(asyncid).get.debug.parameters shouldBe Map("origin" -> "uow")
            native.engine.query(asyncid).get.debug.requestSummary shouldBe Some("native UOW task")
            async.runs.get() shouldBe 0
            delayed.runs.get() shouldBe 0
            native.engine.resourceSnapshot.pending shouldBe 2
            When("ordinary immediate work drains and the original timer advances the delayed branch")
            val immediate = native.engine.drainAll()
            native.advance(Duration.ofSeconds(5))
            val scheduled = native.engine.drainAll()
            Then("the existing accumulator branches execute once with unchanged signed terminal authority")
            immediate shouldBe 1
            scheduled shouldBe 1
            Vector(sync, async, delayed).map(_.runs.get()) shouldBe Vector(1, 1, 1)
            Vector(syncid, asyncid, delayedid).foreach(id => native.signed(id).record.body.identity.revision shouldBe 6L)
            native.engine.resourceSnapshot.pending shouldBe 0
          }
        }
      }
    }
  }

  private def _signed_option(fixture: Fixture, jobid: JobId): Option[DurableJobStoreSnapshot] =
    success(fixture.native.owner._execute_c {
      fixture.native.owner._store.load(jobid.value, JobDurableRuntimeFixtures.access)(using fixture.native.context)
    })

  private def _accepted_submissions(fixture: Fixture): Long =
    fixture.native.engine.operationalMetrics.get.filter(point => point.scope == "job.operation" &&
      point.labels.get("operation").contains("submit") && point.labels.get("outcome").contains("accepted"))
      .map(_.count).sum
}
