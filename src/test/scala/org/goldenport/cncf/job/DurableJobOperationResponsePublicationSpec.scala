package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Field, Record}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DurableJobOperationResponsePublicationFixtures.{Fixture, Source, access, boundaries, review, reviewFields, reviewWire, success, task}

/*
 * Successful terminal publication on the original configured native lifecycle.
 *
 * @since   Oct.  4, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobOperationResponsePublicationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _spec = "docs/spec/durable-job-operation-response-publication-contract.md"
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-operation-response-publication, example:$example, rules:$rules, phase:69.7, slice:JM69-10B3")

  "native successful result responsibility" which {
    "TP1 Publish the actual full Review once" must _scenario("TP1", "R1,R2,R3,R4,R5") {
      "sign the acknowledged owned result and preserve independent evidence" in {
        Given(s"$_spec TP1/R1..R5: an actual configured managed owner, independent closed recipe and six-field Review task")
        DurableJobOperationResponsePublicationFixtures.withFixture() { fixture =>
          val selected = task(review)
          When("the original engine submits and drains the genuine persistent asynchronous task")
          val jobid = fixture.native.submit(selected)
          val drained = fixture.native.engine.drainAll()
          val snapshot = fixture.signed(jobid)
          val reference = fixture.reference(snapshot)
          val restored = success(fixture.read(snapshot))
          val detached = success(fixture.readBytes(snapshot, reference))
          Then("six signed boundaries preserve the independent fields and exact original Review domain and canonical bytes")
          drained shouldBe 1
          _completed(fixture, jobid, selected, review)
          restored shouldBe review
          restored match {
            case OperationResponse.RecordResponse(record) =>
              record.fields.map(field => field.key.toString -> field.value) shouldBe
                reviewFields.map { case (key, value) => key -> Field.Value.Single(value) }
              record.fields.map(_.value) shouldBe reviewFields.map { case (_, value) => Field.Value.Single(value) }
            case _ => fail("TP1 requires the original native RecordResponse")
          }
          detached.bytes.toVector shouldBe reviewWire.getBytes(StandardCharsets.UTF_8).toVector
          fixture.physical(reference) shouldBe detached.bytes.toVector
          reference.reference shouldBe fixture.blob.issued.head.id.value
          reference.byteSize shouldBe reviewWire.getBytes(StandardCharsets.UTF_8).length.toLong
          reference.sha256 shouldBe DurableJobOperationResponsePayloadFixtures.sha256(detached.bytes)
          val metadata = fixture.metadata(reference)
          metadata.storageRef shouldBe Some(fixture.blob.issued.head.storageRef)
          metadata.digest shouldBe Some(reference.sha256)
          metadata.byteSize shouldBe Some(reference.byteSize)
          val seed = fixture.terminalPrefix.get.snapshot.get.record.body
          snapshot.record.body.timeline.dropRight(1) shouldBe seed.timeline
          snapshot.record.body.timeline.last.kind shouldBe "job.durable-terminal-outcome-checkpoint"
          snapshot.record.body.timeline.last.sequence shouldBe seed.timeline.last.sequence + 1L
        }
      }
    }

    "TP2 Refuse a repeated actual terminal checkpoint" must _scenario("TP2", "R1,R2,R4,R5") {
      "retain one actual scalar publication and paid native authority" in {
        Given(s"$_spec TP2/R1,R2,R4,R5: supported scalar success and counted genuine configured Blob")
        DurableJobOperationResponsePublicationFixtures.withFixture() { fixture =>
          val response = OperationResponse.Scalar("original scalar")
          val selected = task(response)
          When("the original persistent execution signs its owned scalar result")
          val jobid = fixture.native.submit(selected)
          val drained = fixture.native.engine.drainAll()
          val snapshot = fixture.signed(jobid)
          val restored = success(fixture.read(snapshot))
          Then("the exact scalar is published once with six original signed revisions")
          drained shouldBe 1
          restored shouldBe response
          _completed(fixture, jobid, selected, response)
          Given("the same actual completed live record, native payment and source count")
          val record = fixture.native.engine._get_record(jobid).get
          val payment = fixture.native.native(jobid)
          val quota = fixture.quota
          val calls = fixture.source.requests.size
          When("the same original bridge receives a repeated terminal checkpoint")
          val repeated = fixture.use(fixture.native.owner._bridge.establishTerminalOutcome(record,
            DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(jobid, record.taskReadModels))(using fixture.native.context))
          Then("the already terminal guard refuses before a second source fetch or native put and refunds nothing")
          repeated.isSuccess shouldBe false
          fixture.source.requests.size shouldBe calls
          fixture.blob.puts.get() shouldBe 1
          fixture.blob.deletes.get() shouldBe 0
          fixture.signed(jobid) shouldBe snapshot
          fixture.native.native(jobid) shouldBe payment
          fixture.quota shouldBe quota
        }
      }
    }

    "TP3 Retain original default Supplied absence" must _scenario("TP3", "R1,R6") {
      "preserve live response identity without allocating a durable body" in {
        Given(s"$_spec TP3/R1,R6: the unmodified original keyed source and default Supplied evidence")
        JobDurableRuntimeFixtures.withFixture { fixture =>
          val blob = new DurableJobOperationResponsePayloadFixtures.MeasuredBlobStore(fixture.selectedBlob)
          fixture.selectedBlob = blob
          success(fixture.start())
          val response = OperationResponse.Scalar("legacy supplied")
          val selected = task(response)
          When("the original configured task succeeds through its unchanged source")
          val jobid = fixture.submit(selected)
          val drained = fixture.engine.drainAll()
          Then("signed absence, raw live result identity and query value remain original with zero puts")
          drained shouldBe 1
          fixture.signed(jobid).record.body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
          fixture.evidence.requests.map(_.request.boundary) shouldBe boundaries
          blob.puts.get() shouldBe 0
          fixture.engine.getResult(jobid) shouldBe Some(JobResult.Success(response))
          fixture.engine.getResult(jobid).get match {
            case JobResult.Success(actual) => actual should be theSameInstanceAs response
            case _ => fail("TP3 requires the original live success")
          }
          fixture.engine.query(jobid).get.result shouldBe Some(response)
          selected.runs.get() shouldBe 1
          selected.canonical.get() shouldBe 1
        }
      }
    }
  }

  "terminal eligibility and pure preflight responsibility" which {
    "TP4 Refuse an absent payload capability" must _scenario("TP4", "R1,R2,R7") {
      "retain the genuine acknowledged native prefix through the old bridge constructor" in {
        Given(s"$_spec TP4/R1,R2,R7: the original direct native database/bridge with explicit terminal intent and no payload capability")
        val root = JobDurableRuntimeFixtures.root()
        val database = new JobDurableLifetimeRecoveryFixtures.Database(root)
        try {
          val owner = database.open()
          val source = new Source
          val bridge = new DurableJobLifecycleWriteBridge(owner.store, source, owner.policy)
          owner.engine.bindDurableLifecycleWriteBridge(bridge)
          var prefix = Option.empty[DurableJobStoreSnapshot]
          source.before = request => if (request.request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
            prefix = Some(owner.signed(request.jobid))
          val selected = task(OperationResponse.Scalar("no capability"))
          When("the real persistent task settles through that original bridge")
          val jobid = success(owner.engine.submit(List(selected), owner.context, JobSubmitOption()))
          val drained = owner.engine.drainAll()
          Then("terminal publication refuses without synthesizing an external reference or discarding paid authority")
          drained shouldBe 1
          owner.signed(jobid) shouldBe prefix.get
          owner.signed(jobid).record.body.result shouldBe DurableResultOutcome.Pending
          owner.signed(jobid).record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
          source.requests.map(_.request.boundary) shouldBe boundaries
          owner.native(jobid).seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
          owner.engine.durableLifecycleWriteFailureFacts shouldBe Vector(DurableJobLifecycleWriteFailureFact(jobid,
            DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused))
          success(owner.events.query(org.goldenport.cncf.event.EventStore.Query())).exists(_.name == "job.succeeded") shouldBe false
        } finally database.close()
      }
    }

    "TP5 Reject invalid publication intent at its addressed boundary" must _scenario("TP5", "R1,R2,R7") {
      "refuse null intent at all six boundaries and owned intent at every nonterminal boundary" in {
        for (boundary <- boundaries; nullintent <- Vector(true, false)
            if nullintent || boundary != DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) {
          Given(s"$_spec TP5/R1,R2,R7: genuine configured $boundary with invalid ${if (nullintent) "null" else "owned"} intent")
          DurableJobOperationResponsePublicationFixtures.withFixture() { fixture =>
            var prefix = Option.empty[DurableJobOperationResponsePublicationFixtures.Prefix]
            fixture.source.before = request => if (request.request.boundary == boundary)
              prefix = Some(fixture.prefix(request.jobid))
            fixture.source.transform = (request, supplied) =>
              if (request.request.boundary == boundary) supplied.copy(resultPublication =
                if (nullintent) null else DurableJobResultPublication.OwnedOperationResponse) else supplied
            val selected = task(OperationResponse.Scalar("invalid intent"))
            When("real submission and dispatch reach the selected invalid boundary once")
            val admitted = fixture.use(fixture.native.engine.submit(List(selected), fixture.native.context,
              JobSubmitOption(persistence = JobPersistencePolicy.Persistent, runMode = JobRunMode.Async)))
            val drained = fixture.native.engine.drainAll()
            val jobid = fixture.source.requests.head.jobid
            val current = fixture.prefix(jobid)
            Then("the observed prior signed and paid prefix survives without boundary mutation or Blob publication")
            val index = boundaries.indexOf(boundary)
            admitted.isSuccess shouldBe (index != 0)
            drained shouldBe (if (index == 0) 0 else 1)
            fixture.source.requests.map(_.request.boundary) shouldBe boundaries.take(index + 1)
            fixture.source.requests.map(_.expectedrevision) shouldBe (1L to index.toLong + 1L).toVector
            current.snapshot shouldBe prefix.get.snapshot
            current.native shouldBe prefix.get.native
            current.quota shouldBe prefix.get.quota
            current.jobacks shouldBe prefix.get.jobacks
            current.puts shouldBe prefix.get.puts
            fixture.blob.puts.get() shouldBe 0
            fixture.native.engine.durableLifecycleWriteFailureFacts shouldBe Vector(DurableJobLifecycleWriteFailureFact(jobid,
              _boundary_failure(boundary)))
            fixture.successEvents shouldBe empty
          }
        }
      }
    }

    "TP6 Reject malformed independently supplied terminal evidence" must _scenario("TP6", "R2,R3,R7") {
      "allocate nothing for result, access, authorization and retry divergence" in {
        val modes = Vector("pending", "external", "tenant", "subject", "scopes", "authorization", "retry")
        modes.foreach { mode =>
          Given(s"$_spec TP6/R2,R3,R7: genuine success and an independently malformed terminal $mode seed")
          DurableJobOperationResponsePublicationFixtures.withFixture() { fixture =>
            fixture.source.transform = (request, supplied) =>
              if (request.request.boundary != DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) supplied
              else mode match {
                case "pending" => supplied.copy(projection = supplied.projection.copy(result = DurableResultOutcome.Pending))
                case "external" => supplied.copy(projection = supplied.projection.copy(result = DurableResultOutcome.Succeeded(
                  DurableValue.External(DurableExternalReference("unrelated", "external", Some("text/plain"), 1L, "0" * 64)))))
                case "tenant" => supplied.copy(access = supplied.access.copy(tenantId = "foreign"))
                case "subject" => supplied.copy(access = supplied.access.copy(subjectId = "foreign"))
                case "scopes" => supplied.copy(access = supplied.access.copy(scopes = Set.empty))
                case "authorization" => supplied.copy(projection = supplied.projection.copy(authorization =
                  supplied.projection.authorization.copy(tenantId = "foreign")))
                case "retry" => supplied.copy(projection = supplied.projection.copy(retry =
                  supplied.projection.retry.copy(maxAttempts = 0)))
              }
            val selected = task(OperationResponse.Scalar("private original"))
            When("the original terminal source returns the selected invalid evidence exactly once")
            val jobid = fixture.native.submit(selected)
            val drained = fixture.native.engine.drainAll()
            Then("pure signed preflight refuses before payload allocation and retains Running/Pending paid authority")
            drained shouldBe 1
            _terminal_refused(fixture, jobid)
            fixture.blob.puts.get() shouldBe 0
            fixture.blob.issued shouldBe empty
            fixture.quotaRow shouldBe fixture.terminalPrefix.get.quota
            fixture.native.ids.calls.get() shouldBe fixture.terminalPrefix.get.ids
          }
        }
      }
    }

    "TP7 Refuse unsupported or null actual success responses" must _scenario("TP7", "R2,R4,R7") {
      "retain original live settlement without publishing or emitting success" in {
        Vector[OperationResponse](OperationResponse.Opaque(new Object), null).foreach { response =>
          Given(s"$_spec TP7/R2,R4,R7: genuine task success with an existing unsupported Opaque or null response")
          DurableJobOperationResponsePublicationFixtures.withFixture() { fixture =>
            val selected = task(response)
            When("the original engine attempts terminal publication of that actual task response")
            val jobid = fixture.native.submit(selected)
            val drained = fixture.native.engine.drainAll()
            Then("the redacted publication refusal retains the live success and acknowledged durable prefix with zero allocation")
            drained shouldBe 1
            _terminal_refused(fixture, jobid)
            fixture.blob.puts.get() shouldBe 0
            fixture.blob.issued shouldBe empty
            fixture.quotaRow shouldBe fixture.terminalPrefix.get.quota
            fixture.native.ids.calls.get() shouldBe fixture.terminalPrefix.get.ids
            fixture.native.engine.getResult(jobid) shouldBe Some(JobResult.Success(response))
          }
        }
      }
    }
  }

  "native acknowledgement and failure ownership" which {
    "TP8 Retain the original producer rollback" must _scenario("TP8", "R4,R5,R7") {
      "refuse corrupt physical acknowledgements and release only the producer allocation" in {
        Vector("size", "digest").foreach { mode =>
          Given(s"$_spec TP8/R4,R5,R7: a configured LocalBlob forwards its put and corrupts the returned $mode")
          DurableJobOperationResponsePublicationFixtures.withFixture() { fixture =>
            fixture.blob.putTransform = result => if (mode == "size") result.copy(byteSize = result.byteSize + 1L)
              else result.copy(digest = "bad")
            val selected = task(review)
            When("the actual engine observes the malformed producer acknowledgement")
            val jobid = fixture.native.submit(selected)
            val drained = fixture.native.engine.drainAll()
            Then("the original producer deletes its own physical allocation once and retains its Released quota marker")
            drained shouldBe 1
            _terminal_refused(fixture, jobid)
            fixture.blob.puts.get() shouldBe 1
            fixture.blob.deletes.get() shouldBe 1
            fixture.blob.issued.size shouldBe 1
            fixture.issuedPhysical(fixture.blob.issued.head) shouldBe false
            fixture.quota.get.entries.map(_.state) shouldBe Vector(DurableJobOwnedBlobQuotaState.Released)
            fixture.quota.get.entries.head.blobid shouldBe fixture.blob.issued.head.id.value
            success(fixture.use(fixture.native.owner._payloads.resourceSnapshot()(using fixture.native.context))).occupiedblobs shouldBe 0L
          }
        }
      }
    }

    "TP9 Retain actual publication after a lost checkpoint acknowledgement" must _scenario("TP9", "R3,R5,R7") {
      "report refusal while preserving the observed committed terminal and Published bytes" in {
        Given(s"$_spec TP9/R3,R5,R7: valid actual Review publication and the original terminal-only losejoback hook")
        DurableJobOperationResponsePublicationFixtures.withFixture() { fixture =>
          val originalbefore = fixture.source.before
          fixture.source.before = request => {
            originalbefore(request)
            if (request.request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
              fixture.native.entity.losejoback = true
          }
          val selected = task(review)
          When("the native terminal checkpoint persists and loses its returned acknowledgement")
          val jobid = fixture.native.submit(selected)
          val drained = fixture.native.engine.drainAll()
          val actual = fixture.signed(jobid)
          val reference = fixture.reference(actual)
          val restored = success(fixture.read(actual))
          Then("actual committed terminal evidence survives the failure without caller rollback, refund or retry")
          drained shouldBe 1
          actual.record.body.identity.revision shouldBe 6L
          actual.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
          restored shouldBe review
          fixture.blob.puts.get() shouldBe 1
          fixture.blob.deletes.get() shouldBe 0
          fixture.physical(reference) shouldBe reviewWire.getBytes(StandardCharsets.UTF_8).toVector
          fixture.quota.get.entries.map(_.state) shouldBe Vector(DurableJobOwnedBlobQuotaState.Published)
          fixture.quota.get.entries.head.blobid shouldBe reference.reference
          fixture.native.native(jobid) shouldBe fixture.terminalPrefix.get.native.get
          fixture.source.requests.map(_.request.boundary) shouldBe boundaries
          fixture.native.engine.durableLifecycleWriteFailureFacts shouldBe Vector(DurableJobLifecycleWriteFailureFact(jobid,
            DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused))
          fixture.successEvents shouldBe empty
        }
      }
    }

    "TP10 Preserve capacity and closed lease refusal" must _scenario("TP10", "R2,R4,R7") {
      "perform no Blob put beyond native capacity or after owner close" in {
        Given(s"$_spec TP10/R2,R4,R7: an actual configured policy smaller than the independent Review wire")
        val root = JobDurableRuntimeFixtures.root()
        val fixture = new Fixture(root)
        try {
          fixture.native.blobPolicy = fixture.native.blobPolicy.copy(maxpayloadbytes = 1)
          fixture.start()
          val selected = task(review)
          When("real terminal publication encounters the configured payload capacity")
          val jobid = fixture.native.submit(selected)
          val drained = fixture.native.engine.drainAll()
          Then("capacity refusal retains native authority and creates no Published allocation or physical put")
          drained shouldBe 1
          _terminal_refused(fixture, jobid)
          fixture.blob.puts.get() shouldBe 0
          fixture.quotaRow shouldBe fixture.terminalPrefix.get.quota
          fixture.blob.issued shouldBe empty
        } finally { fixture.close(); JobDurableRuntimeFixtures.remove(root) }

        Given("an independently configured real owner with an acknowledged running source observation")
        DurableJobOperationResponsePublicationFixtures.withFixture() { closed =>
          val selected = task(review)
          closed.source.transform = (request, supplied) =>
            if (request.request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
              supplied.copy(projection = supplied.projection.copy(result = DurableResultOutcome.Pending)) else supplied
          When("real execution retains its nonterminal signed prefix and logical shutdown closes the original owner")
          val jobid = closed.native.submit(selected)
          closed.native.engine.drainAll()
          val snapshot = closed.signed(jobid)
          val owner = closed.native.owner
          val puts = closed.blob.puts.get()
          success(closed.native.subsystem.shutdownC())
          Then("the existing owner reports Closed while its node retains physical ownership")
          owner.health.state shouldBe JobDurableOperationalState.Closed
          closed.native.provider.get.managedResourceOption.get.isOpen shouldBe true
          When("a same-owner leased publication is requested after logical close")
          val refused = owner._execute_c(owner._result_payloads.publishObserved(snapshot, access, review)(using closed.native.context))
          Then("the original lease refuses before starting any new native or Blob work")
          refused.isSuccess shouldBe false
          (refused, DurableJobRuntimeOwner.refused[Unit]) match {
            case (Consequence.Failure(actualconclusion), Consequence.Failure(expectedconclusion)) =>
              actualconclusion.observation.getEffectiveMessage shouldBe expectedconclusion.observation.getEffectiveMessage
            case _ => fail("TP10 requires actual and expected closed-lease refusal")
          }
          closed.blob.puts.get() shouldBe puts
        }
      }
    }

    "TP13 Reject actual failed and cancelled candidates" must _scenario("TP13", "R2,R7") {
      "retain native failure and cancellation prefix facts without success publication" in {
        Vector(false, true).foreach { cancel =>
          Given(s"$_spec TP13/R2,R7: an actual nonretryable failed task with explicit terminal owned intent; cancel=$cancel")
          DurableJobOperationResponsePublicationFixtures.withFixture() { fixture =>
            val selected = new JobDurableLifetimeRecoveryFixtures.Task
            val failure = Consequence.argumentInvalid[OperationResponse]("original nonretryable task failure").conclusion
            var submitted = Option.empty[JobId]
            var controlled = Option.empty[Consequence[JobControlResponse]]
            val policy = new JobControlPolicy {
              def authorize(jobId: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = Consequence.unit
            }
            selected.body = ctx => {
              if (cancel) controlled = Some(fixture.native.engine.control(submitted.get,
                JobControlRequest(JobControlCommand.Cancel), policy)(using ctx))
              TaskFailed(failure)
            }
            When("the actual submitted task fails or invokes native cancellation under the original owner lease")
            val jobid = fixture.native.submit(selected)
            submitted = Some(jobid)
            val drained = fixture.native.engine.drainAll()
            Then("owned success publication refuses actual Failed/Cancelled candidates and retains acknowledged payment and task/control facts")
            drained shouldBe 1
            fixture.blob.puts.get() shouldBe 0
            fixture.blob.issued shouldBe empty
            fixture.source.requests.count(_.request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) shouldBe 1
            fixture.native.engine.getStatus(jobid) shouldBe Some(if (cancel) JobStatus.Cancelled else JobStatus.Failed)
            fixture.native.engine.getResult(jobid).get shouldBe a[JobResult.Failure]
            fixture.native.engine.durableLifecycleWriteFailureFacts should contain(
              DurableJobLifecycleWriteFailureFact(jobid, DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused))
            fixture.successEvents shouldBe empty
            val snapshot = fixture.signed(jobid)
            snapshot.record.body.result shouldBe DurableResultOutcome.Pending
            snapshot.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
            fixture.native.native(jobid).seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, if (cancel) 0L else 1L)
            fixture.native.native(jobid).seed.remaining shouldBe 0L
            selected.runs.get() shouldBe 1
            if (cancel) {
              controlled.nonEmpty shouldBe true
              fixture.native.native(jobid).cancelclaimed shouldBe true
            } else {
              controlled shouldBe None
              fixture.native.engine.getResult(jobid) shouldBe Some(JobResult.Failure(failure))
              snapshot shouldBe fixture.terminalPrefix.get.snapshot.get
            }
          }
        }
      }
    }
  }

  "independent owner and generated domain responsibility" which {
    "TP11 Recover actual completed bytes through a fresh configured owner" must _scenario("TP11", "R4,R5,R6,R8") {
      "retain signed payment and Published body without a replay or new public result" in {
        Given(s"$_spec TP11/R4,R5,R6,R8: actual task-produced terminal Review and a fully shut down managed writer")
        val root = JobDurableRuntimeFixtures.root()
        val writer = new Fixture(root)
        var reader = Option.empty[Fixture]
        try {
          writer.start()
          val selected = task(review)
          When("the original engine produces its actual terminal record and the physical writer shuts down")
          val jobid = writer.native.submit(selected)
          val drained = writer.native.engine.drainAll()
          val original = writer.signed(jobid)
          val reference = writer.reference(original)
          val payment = writer.native.native(jobid)
          val quota = writer.quota
          writer.close()
          Then("the completed source is actual and its original managed physical pool is closed")
          drained shouldBe 1
          writer.blob.puts.get() shouldBe 1
          writer.native.provider.get.managedResourceOption.get.isOpen shouldBe false
          Given("a separately constructed owner of the same physical namespace and original startup candidate")
          val fresh = new Fixture(root)
          reader = Some(fresh)
          fresh.native.startup.entries = Vector(JobDurableRuntimeFixtures.candidate(jobid))
          Given("an explicit test-only original-management read for the submitted JobId; the default fresh caller has no owner or capability")
          val managementpolicy = new JobQueryPolicy {
            def authorizeRead(model: JobQueryReadModel)(using ctx: ExecutionContext): Consequence[Unit] =
              if (model.jobId == jobid) Consequence.unit
              else JobQueryPolicy.default.authorizeRead(model)
          }
          When("fresh startup authenticates the genuine completed record and the B2 reader uses its actual owner lease")
          fresh.start()
          val recovered = fresh.signed(jobid)
          val restored = success(fresh.read(recovered))
          val managementdefault = success(fresh.native.engine.queryManagementResult(jobid)(using fresh.native.context))
          val management = success(fresh.native.engine.queryManagementResult(jobid, managementpolicy)(using fresh.native.context))
          Then("exact domain, signed body and paid quota survive without body replay, evidence reconstruction or another put")
          recovered.record shouldBe original.record
          restored shouldBe review
          fresh.physical(reference) shouldBe reviewWire.getBytes(StandardCharsets.UTF_8).toVector
          fresh.quota shouldBe quota
          fresh.native.native(jobid) shouldBe payment
          fresh.native.engine.durableTerminalProjectionReport.get.candidates.map(_.fact) shouldBe
            Vector(DurableJobTerminalProjectionFact.Registered)
          fresh.native.port.calls.get() shouldBe 0
          fresh.source.requests shouldBe empty
          fresh.blob.puts.get() shouldBe 0
          fresh.native.engine.drainAll() shouldBe 0
          fresh.native.engine.getResult(jobid) shouldBe None
          fresh.native.engine.query(jobid).get.result shouldBe None
          managementdefault shouldBe None
          management.get shouldBe a[JobManagementResult.UnavailableAfterRestart]
          fresh.native.provider.get should not be theSameInstanceAs(writer.native.provider.get)
        } finally {
          reader.foreach(_.close())
          writer.close()
          JobDurableRuntimeFixtures.remove(root)
        }
      }
    }

    "TP12 Publish bounded generated scalar and record domains with ordinary shrinking" must _scenario("TP12", "R1,R2,R3,R4,R5,R6,R8,R9") {
      "execute at least twenty four real native flows under seed 69710033" in {
        Given(s"$_spec TP12/R1..R6,R8,R9: independently generated nonempty ASCII length1..12 scalar or native record values")
        val text = Gen.choose(1, 12).flatMap(size => Gen.listOfN(size, Gen.alphaNumChar).map(_.mkString))
        val property = Prop.forAll(text, Gen.oneOf(false, true)) { (value, record) =>
          Given(s"$_spec TP12 generated case: fresh actual managed fixture and independent generated domain and type")
          DurableJobOperationResponsePublicationFixtures.withFixture() { fixture =>
            val expected = if (record) OperationResponse.RecordResponse(Record(Vector(
              Field("original", Field.Value.Single(value)), Field("count", Field.Value.Single(value.length)))))
              else OperationResponse.Scalar(value)
            val selected = task(expected)
            When("the real generated task is submitted, drained, published and read through actual native ownership")
            val jobid = fixture.native.submit(selected)
            val drained = fixture.native.engine.drainAll()
            val snapshot = fixture.signed(jobid)
            val restored = success(fixture.read(snapshot))
            Then("the exact generated native domain and type survive one publication, six revisions and original raw compatibility")
            drained shouldBe 1
            restored shouldBe expected
            restored.getClass shouldBe expected.getClass
            restored match {
              case OperationResponse.RecordResponse(actual) =>
                actual.fields.map(_.value) shouldBe Vector(Field.Value.Single(value), Field.Value.Single(value.length))
              case OperationResponse.Scalar(actual) => actual shouldBe value
              case _ => fail("TP12 requires the independently generated native response family")
            }
            _completed(fixture, jobid, selected, expected)
          }
          true
        }
        When("ScalaCheck executes the normal shrinking property with its fixed seed and minimum successes")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69710033L)), property)
        Then("every generated native publication satisfies the exact domain and payment contract")
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }
    }
  }

  private def _completed(fixture: Fixture, jobid: JobId,
    selected: JobDurableLifetimeRecoveryFixtures.Task, response: OperationResponse): Unit = {
    val snapshot = fixture.signed(jobid)
    val body = snapshot.record.body
    val supplied = fixture.source.pairs.last.projection
    fixture.source.requests.map(_.request.boundary) shouldBe boundaries
    fixture.source.requests.map(_.expectedrevision) shouldBe (1L to 6L).toVector
    fixture.source.requests.map(_.jobid).distinct shouldBe Vector(jobid)
    body.identity.revision shouldBe 6L
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    body.authorization shouldBe supplied.authorization
    body.tasks shouldBe supplied.tasks
    body.inputs shouldBe supplied.inputs
    body.lifecycle.retry shouldBe supplied.retry
    body.diagnostics shouldBe supplied.diagnostics
    body.calltreeReference shouldBe supplied.calltreeReference
    body.definitionSnapshot shouldBe supplied.definitionSnapshot
    body.retention shouldBe supplied.retention
    fixture.blob.puts.get() shouldBe 1
    fixture.blob.deletes.get() shouldBe 0
    val reference = fixture.reference(snapshot)
    val entry = fixture.quota.get.entries.find(_.blobid == reference.reference).get
    entry.state shouldBe DurableJobOwnedBlobQuotaState.Published
    entry.bytesize shouldBe reference.byteSize
    entry.payloadsha256 shouldBe reference.sha256
    entry.ownersha256 shouldBe DurableJobOwnedBlobPayloads.ownerFingerprint(snapshot.record)
    fixture.native.native(jobid).seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
    fixture.native.native(jobid).seed.remaining shouldBe 0L
    fixture.native.native(jobid).seed.normalids shouldBe fixture.native.engine._get_record(jobid).get.taskReadModels.map(_.taskId.value).toSet
    selected.runs.get() shouldBe 1
    selected.canonical.get() shouldBe 1
    fixture.native.engine.getResult(jobid) shouldBe Some(JobResult.Success(response))
    fixture.native.engine.query(jobid).get.result shouldBe Some(response)
    fixture.native.engine.durableLifecycleWriteFailureFacts shouldBe empty
  }

  private def _terminal_refused(fixture: Fixture, jobid: JobId): Unit = {
    val prefix = fixture.terminalPrefix.get
    fixture.signed(jobid) shouldBe prefix.snapshot.get
    fixture.signed(jobid).record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
    fixture.signed(jobid).record.body.result shouldBe DurableResultOutcome.Pending
    fixture.native.native(jobid) shouldBe prefix.native.get
    fixture.source.requests.map(_.request.boundary) shouldBe boundaries
    fixture.source.requests.map(_.expectedrevision) shouldBe (1L to 6L).toVector
    fixture.native.engine.durableLifecycleWriteFailureFacts shouldBe Vector(DurableJobLifecycleWriteFailureFact(jobid,
      DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused))
    fixture.successEvents shouldBe empty
  }

  private def _boundary_failure(boundary: DurableJobLifecycleWriteBoundary): DurableJobLifecycleWriteFailure = boundary match {
    case DurableJobLifecycleWriteBoundary.Admission => DurableJobLifecycleWriteFailure.AdmissionRefused
    case DurableJobLifecycleWriteBoundary.StartIntent => DurableJobLifecycleWriteFailure.StartIntentRefused
    case DurableJobLifecycleWriteBoundary.RunningIntent => DurableJobLifecycleWriteFailure.RunningIntentRefused
    case DurableJobLifecycleWriteBoundary.TaskStartIntent => DurableJobLifecycleWriteFailure.TaskStartIntentRefused
    case DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint => DurableJobLifecycleWriteFailure.TaskOutcomeCheckpointRefused
    case DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint => DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused
  }
}
