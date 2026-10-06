package org.goldenport.cncf.job

import java.io.IOException
import java.util.concurrent.CountDownLatch
import org.goldenport.Consequence
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.servicecontainer.ServiceContainerCleanupOutcome
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DurableJobOperationResponseQueryFixtures.{Child, Effects, Fixture, access, assertDomain,
  caller, message as failureMessage, recipe, review, success}

/*
 * Current-caller result authority over genuine native and sequential JVM facts.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobOperationResponseQuerySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _spec = "docs/spec/durable-job-operation-response-query-contract.md"
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-operation-response-query, example:$example, rules:$rules, phase:69.7, slice:JM69-10B4")

  "current caller and detached result responsibility" which {
    "E1_Q1 Preserve live and missing queries" must _scenario("E1", "R1,R5,R8") {
      "retain actual live response identity and authorize no missing Job" in {
        Given(s"$_spec E1/R1,R5,R8: an actual Task-produced owned success and independent unknown opaque Job ID")
        DurableJobOperationResponseQueryFixtures.withFixture { fixture =>
          val produced = fixture.produce()
          val context = fixture.current
          val before = fixture.effects
          var authorizations = 0
          val policy = new JobQueryPolicy {
            def authorizeRead(model: JobQueryReadModel)(using ctx: ExecutionContext): Consequence[Unit] = {
              authorizations += 1; JobQueryPolicy.default.authorizeRead(model)
            }
          }
          When("the original visible query reads the completed live owner model and then a missing Job")
          val live = success(fixture.native.engine.queryVisible(produced.id, policy)(using context)).get
          val absent = success(fixture.native.engine.queryVisible(JobInputBoundsFixtures.id("query-unknown"), policy)(using context))
          Then("the exact live response and raw live result remain and the missing query has no callbacks or effects")
          live.result shouldBe Some(review)
          live.result.get should be theSameInstanceAs review
          fixture.native.engine.getResult(produced.id) shouldBe Some(JobResult.Success(review))
          fixture.native.engine.getResult(produced.id).get match {
            case JobResult.Success(response) => response should be theSameInstanceAs review
            case _ => fail("E1 requires the actual original live success")
          }
          absent shouldBe None
          authorizations shouldBe 1
          fixture.effects shouldBe before
        }
      }
    }

    "E2_Q2 Resolve exact native Review for the current owner" must _scenario("E2", "R1,R2,R3,R4,R5,R8") {
      "return detached six-field native values through default overview and independently current durable authority" in {
        Given(s"$_spec E2/R1..R5,R8: a genuine six-field Review writer closed before an independently configured native reader")
        DurableJobOperationResponseQueryFixtures.withRecovered { (fixture, produced) =>
          val context = fixture.current
          context.security.subjectKind.toString shouldBe "User"
          val raw = fixture.native.engine.query(produced.id).get
          val before = fixture.rows(produced.id, Some(produced.reference))
          val baseline = fixture.effects
          When("the default owner overview policy permits the actual raw current caller and the owned result is read")
          val visible = success(fixture.query(produced.id, context)).get
          Then("the exact native Review escapes only in a detached model after one resolver and one closed stream")
          assertDomain(visible.result.get, "review", "")
          visible shouldBe raw.copy(result = Some(review))
          visible should not be theSameInstanceAs(raw)
          fixture.resolver.contexts should have size 1
          fixture.resolver.contexts.head should be theSameInstanceAs context
          fixture.effects.metadata - baseline.metadata shouldBe 1
          fixture.effects.gets - baseline.gets shouldBe 1
          fixture.effects.opens - baseline.opens shouldBe 1
          fixture.effects.closes - baseline.closes shouldBe 1
          _no_work(fixture.effects, baseline)
          fixture.rows(produced.id, Some(produced.reference)) shouldBe before
          fixture.native.signed(produced.id) shouldBe produced.snapshot
          fixture.native.native(produced.id) shouldBe produced.payment
          _raw_unavailable(fixture, produced.id, context)
          Given("the same registered raw projection after a successful detached read")
          val laterbaseline = fixture.effects
          When("a later valid caller reads independently")
          val later = success(fixture.query(produced.id, context)).get
          Then("the result is read again without a model or response cache")
          assertDomain(later.result.get, "review", "")
          fixture.effects.resolvers - laterbaseline.resolvers shouldBe 1
          fixture.effects.gets - laterbaseline.gets shouldBe 1
          fixture.effects.opens - laterbaseline.opens shouldBe 1
          fixture.effects.closes - laterbaseline.closes shouldBe 1
          later.result.get should not be theSameInstanceAs(visible.result.get)
          fixture.native.engine.query(produced.id).get should be theSameInstanceAs raw
          _no_work(fixture.effects, laterbaseline)
        }
      }
    }

    "E3_Q3 Preserve ordinary overview refusal" must _scenario("E3", "R1,R2,R7") {
      "deny an ordinary foreign caller before any resolver and preserve the exact returned policy failure" in {
        Given(s"$_spec E3/R1,R2,R7: a genuine reopened result and a foreign User without overview capability")
        DurableJobOperationResponseQueryFixtures.withRecovered { (fixture, produced) =>
          val foreign = caller(fixture.native.context, subject = "foreign-subject")
          val expected = JobQueryPolicy.default.authorizeRead(fixture.native.engine.query(produced.id).get)(using foreign)
          val before = fixture.effects
          When("the foreign caller asks the original default visible query")
          val refused = fixture.query(produced.id, foreign)
          Then("ordinary owner policy refusal remains original with no resolver or physical access")
          refused.isSuccess shouldBe false
          failureMessage(refused) shouldBe failureMessage(expected)
          failureMessage(refused) should not be Some("durable job result query refused")
          fixture.effects shouldBe before
          Given("an independently supplied original policy refusal value")
          val original = Consequence.operationIllegal[Unit]("job.query", "selected overview refusal")
          val policy = new JobQueryPolicy {
            def authorizeRead(model: JobQueryReadModel)(using ExecutionContext): Consequence[Unit] = original
          }
          When("the policy returns its own failure")
          val preserved = fixture.native.engine.queryVisible(produced.id, policy)(using foreign)
          Then("the exact returned refusal is preserved")
          preserved should be theSameInstanceAs original
          fixture.effects shouldBe before
        }
      }
    }

    "E4_Q4 Refuse overview-granted hostile raw durable grants" must _scenario("E4", "R2,R3,R4,R7") {
      "deny tenant subject and exact raw scope divergence before metadata without retaining a grant" in {
        Given(s"$_spec E4/R2,R3,R4,R7: a genuine reopened result and existing job_view overview capability")
        DurableJobOperationResponseQueryFixtures.withRecovered { (fixture, produced) =>
          val hostile = Vector(
            caller(fixture.native.context, tenant = "foreign-tenant", overview = true),
            caller(fixture.native.context, subject = "foreign-subject", overview = true),
            caller(fixture.native.context, scopes = None, overview = true),
            caller(fixture.native.context, scopes = Some("other.read"), overview = true),
            caller(fixture.native.context, scopes = Some("JOB.READ"), overview = true),
            caller(fixture.native.context, tenant = " TENANT-A ", overview = true))
          hostile.foreach { context =>
            val before = fixture.effects
            When("the independently foreign or raw-scope hostile caller reads after ordinary overview permission")
            val refused = fixture.query(produced.id, context)
            Then("strict signed tenant subject and scope equality denies with fixed refusal before Blob dispatch")
            _refused(refused)
            fixture.effects.resolvers - before.resolvers shouldBe 1
            fixture.resolver.contexts.last should be theSameInstanceAs context
            fixture.effects.metadata shouldBe before.metadata
            fixture.effects.gets shouldBe before.gets
            fixture.effects.opens shouldBe before.opens
            _no_work(fixture.effects, before)
          }
          Given("a later independently valid current caller after all hostile attempts")
          val before = fixture.effects
          When("that caller presents exact raw job.read")
          val visible = success(fixture.query(produced.id, fixture.current)).get
          Then("a fresh successful native read proves that no hostile attempt poisoned or retained a caller grant")
          assertDomain(visible.result.get, "review", "")
          fixture.effects.resolvers - before.resolvers shouldBe 1
          fixture.effects.gets - before.gets shouldBe 1
          fixture.effects.opens - before.opens shouldBe 1
          fixture.effects.closes - before.closes shouldBe 1
          _no_work(fixture.effects, before)
        }
      }
    }
  }

  "malformed authority and observed native refusal" which {
    "E5_Q5 Admit resolver input shape and redact callback faults" must _scenario("E5", "R2,R3,R7") {
      "reject null resolver composition before Ready and malformed current resolution before Blob" in {
        Vector[Option[DurableJobCallerAccessResolver]](null, Some(null)).foreach { selected =>
          Given(s"$_spec E5/R2,R3,R7: an independent actual fixture with null resolver container or element")
          val root = JobDurableRuntimeFixtures.root()
          val fixture = new Fixture(root)
          fixture.native.selectedCallerAccessResolver = selected
          try {
            When("the original provider starts that invalid trusted input")
            val started = fixture.start()
            Then("startup refuses before Ready and before any owned read")
            started.isSuccess shouldBe false
            fixture.native.engine.durableOperationalHealth.get.state should not be JobDurableOperationalState.Ready
            fixture.native.engine._durable_runtime_owner_option shouldBe None
            fixture.blob.gets.get() shouldBe 0
          } finally { fixture.close(); JobDurableRuntimeFixtures.remove(root) }
        }
        Given(s"$_spec E5/R2,R3,R7: genuine publication and explicit compatible resolver None on the fresh native reader")
        val root = JobDurableRuntimeFixtures.root()
        val writer = new Fixture(root)
        val reader = new Fixture(root)
        try {
          success(writer.start()); val produced = writer.produce(); writer.close()
          reader.native.selectedCallerAccessResolver = None
          reader.open(produced.id)
          val before = reader.effects
          When("an eligible owned result is queried without an explicitly composed resolver")
          val refused = reader.query(produced.id, reader.current)
          Then("compatible startup remains Ready but query refuses without startup or signed grant fallback")
          _refused(refused)
          reader.effects shouldBe before
        } finally { reader.close(); writer.close(); JobDurableRuntimeFixtures.remove(root) }

        val modes = Vector("null-result", "null-access", "null-tenant", "blank-tenant", "null-subject", "blank-subject",
          "null-scopes", "null-token", "blank-token", "failure", "io", "interrupt", "fatal", "null-caller", "entry-interrupt", "policy-io")
        modes.foreach { mode =>
          Given(s"$_spec E5/R2,R3,R7: independent genuine reopened result with selected malformed resolution $mode")
          DurableJobOperationResponseQueryFixtures.withRecovered { (fixture, produced) =>
            val fatal = new LinkageError("PRIVATE path payload credentials")
            fixture.resolver.selected = _ => mode match {
              case "null-result" => null
              case "null-access" => Consequence.success(null)
              case "null-tenant" => Consequence.success(access.copy(tenantId = null))
              case "blank-tenant" => Consequence.success(access.copy(tenantId = " "))
              case "null-subject" => Consequence.success(access.copy(subjectId = null))
              case "blank-subject" => Consequence.success(access.copy(subjectId = "\t"))
              case "null-scopes" => Consequence.success(access.copy(scopes = null))
              case "null-token" => Consequence.success(access.copy(scopes = Set(null)))
              case "blank-token" => Consequence.success(access.copy(scopes = Set(" ")))
              case "failure" => Consequence.stateInvalid("PRIVATE path payload credentials")
              case "io" => throw new IOException("PRIVATE path payload credentials")
              case "interrupt" => throw new InterruptedException("PRIVATE path payload credentials")
              case "fatal" => throw fatal
              case _ => Consequence.success(access)
            }
            val context = if (mode == "null-caller") null else fixture.current
            val policy = new JobQueryPolicy {
              def authorizeRead(model: JobQueryReadModel)(using ExecutionContext): Consequence[Unit] =
                if (mode == "policy-io") throw new IOException("PRIVATE overview payload credentials") else Consequence.unit
            }
            val before = fixture.effects
            try {
              When("the selected current resolution or callback is consumed once by the eligible query")
              if (mode == "entry-interrupt") Thread.currentThread().interrupt()
              val result = if (mode == "fatal") {
                val thrown = intercept[LinkageError](fixture.native.engine.queryVisible(produced.id, policy)(using context))
                thrown should be theSameInstanceAs fatal
                None
              } else Some(fixture.native.engine.queryVisible(produced.id, policy)(using context))
              Then("ordinary malformed failures are redacted, interruption is restored and fatal errors propagate")
              result.foreach(_refused)
              Thread.currentThread().isInterrupted shouldBe (mode == "interrupt" || mode == "entry-interrupt")
              val callbacks = if (Set("null-caller", "entry-interrupt", "policy-io").contains(mode)) 0 else 1
              fixture.effects.resolvers - before.resolvers shouldBe callbacks
              if (callbacks == 1) fixture.resolver.contexts.last should be theSameInstanceAs context
              fixture.effects.metadata shouldBe before.metadata
              fixture.effects.gets shouldBe before.gets
              fixture.effects.opens shouldBe before.opens
              _no_work(fixture.effects, before)
            } finally { Thread.interrupted(); () }
          }
        }
      }
    }

    "E6_Q6 Refuse current native row and quota faults" must _scenario("E6", "R3,R4,R7") {
      "consume the registered projection against actual corrupt stale absent inactive and Released native authority" in {
        Vector("corrupt", "revision", "missing", "inactive", "released").foreach { mode =>
          Given(s"$_spec E6/R3,R4,R7: genuine registered terminal result and isolated explicit native $mode fixture fault")
          DurableJobOperationResponseQueryFixtures.withRecovered { (fixture, produced) =>
            mode match {
              case "corrupt" => fixture.replaceText(fixture.jobEntityId(produced.id), "PRIVATE corrupt native canonical text")
              case "revision" => fixture.checkpoint(produced.snapshot)
              case "missing" => fixture.removeRow(fixture.jobEntityId(produced.id))
              case "inactive" => fixture.checkpoint(produced.snapshot, inactive = true)
              case "released" => fixture.releaseQuota()
            }
            val rows = fixture.rows(produced.id, Some(produced.reference))
            val before = fixture.effects
            When("the original registered projection is queried after the native fault setup")
            val result = fixture.query(produced.id, fixture.current)
            Then("current signed Active and Published authority refuses before physical get without query mutation")
            _refused(result)
            fixture.effects.gets shouldBe before.gets
            fixture.effects.opens shouldBe before.opens
            _no_work(fixture.effects, before)
            fixture.rows(produced.id, Some(produced.reference)) shouldBe rows
            fixture.native.engine.query(produced.id).get.result shouldBe None
            fixture.native.engine.durableTerminalProjection(produced.id).get.snapshot shouldBe produced.snapshot
          }
        }
      }
    }

    "E7_Q7 Bound native Blob faults and close opened streams" must _scenario("E7", "R3,R4,R6,R7") {
      "refuse actual metadata reply and byte faults through the original B2 reader" in {
        val modes = Vector("metadata-size", "metadata-content", "wrong-size", "wrong-content", "null-result",
          "tamper", "short", "trailing", "stream-throw")
        modes.foreach { mode =>
          Given(s"$_spec E7/R3,R4,R6,R7: genuine current owned result with measured native forwarding mode $mode")
          DurableJobOperationResponseQueryFixtures.withRecovered { (fixture, produced) =>
            if (mode == "metadata-size") fixture.replaceBlob(produced.reference, value => value.copy(byteSize = Some(value.byteSize.get + 1L)))
            else if (mode == "metadata-content") fixture.replaceBlob(produced.reference, _.copy(contentType = Some(ContentType.TEXT_PLAIN)))
            else fixture.blob.mode = mode
            val before = fixture.effects
            val rows = fixture.rows(produced.id, Some(produced.reference))
            When("the actual configured Blob reader observes the selected malformed metadata reply or bytes")
            val result = fixture.query(produced.id, fixture.current)
            Then("fixed refusal escapes without fallback or mutation and every actually opened stream closes once")
            _refused(result)
            val stream = Set("tamper", "short", "trailing", "stream-throw").contains(mode)
            fixture.effects.gets - before.gets shouldBe (if (mode.startsWith("metadata-")) 0 else 1)
            fixture.effects.opens - before.opens shouldBe (if (stream) 1 else 0)
            fixture.effects.closes - before.closes shouldBe (if (stream) 1 else 0)
            fixture.effects.consumed - before.consumed should be <= produced.reference.byteSize + 1L
            fixture.effects.maxrequest should be <= math.min(8192L, produced.reference.byteSize + 1L).toInt
            fixture.blob.fallbacks.get() shouldBe 0
            _no_work(fixture.effects, before)
            fixture.rows(produced.id, Some(produced.reference)) shouldBe rows
            fixture.native.engine.query(produced.id).get.result shouldBe None
          }
        }
      }
    }

    "E8_Q8 Refuse native authority changes after opening" must _scenario("E8", "R3,R4,R6,R7") {
      "close one stream and let original final optimistic checks reject actual revision deletion and quota-release races" in {
        Vector("revision", "missing", "released").foreach { mode =>
          Given(s"$_spec E8/R3,R4,R6,R7: genuine current terminal result and one explicit beforeRead native $mode fault")
          DurableJobOperationResponseQueryFixtures.withRecovered { (fixture, produced) =>
            var injectedwrites = 0
            var injectedentitywrites = 0
            var postfault = Vector.empty[Option[org.goldenport.record.Record]]
            fixture.blob.beforeRead = () => {
              val before = fixture.effects
              mode match {
                case "revision" => fixture.checkpoint(produced.snapshot)
                case "missing" => fixture.removeRow(fixture.jobEntityId(produced.id))
                case "released" => fixture.releaseQuota()
              }
              injectedwrites += fixture.effects.writes - before.writes
              injectedentitywrites += fixture.effects.entitywrites - before.entitywrites
              postfault = fixture.rows(produced.id, Some(produced.reference))
            }
            val before = fixture.effects
            When("the physical opening precedes a real native authority mutation during the bounded read")
            val result = fixture.query(produced.id, fixture.current)
            Then("final native admission refuses before a response escapes and the query adds no mutation beyond explicit setup")
            _refused(result)
            injectedwrites should be > 0
            fixture.effects.writes - before.writes shouldBe injectedwrites
            fixture.effects.entitywrites - before.entitywrites shouldBe injectedentitywrites
            fixture.effects.gets - before.gets shouldBe 1
            fixture.effects.opens - before.opens shouldBe 1
            fixture.effects.closes - before.closes shouldBe 1
            fixture.effects.puts shouldBe before.puts
            fixture.effects.deletes shouldBe before.deletes
            fixture.effects.sources shouldBe before.sources
            fixture.effects.ports shouldBe before.ports
            fixture.effects.tasks shouldBe before.tasks
            fixture.rows(produced.id, Some(produced.reference)) shouldBe postfault
            fixture.native.engine.query(produced.id).get.result shouldBe None
          }
        }
      }
    }
  }

  "lifecycle and compatibility responsibility" which {
    "E9_Q9 Drain an admitted real read through logical close" must _scenario("E9", "R3,R6,R7,R8") {
      "release owner monitors while a resolver holds the original managed lease and refuse new reads" in {
        Given(s"$_spec E9/R3,R6,R7,R8: a genuine recovered result and bounded resolver barriers inside a real native lease")
        DurableJobOperationResponseQueryFixtures.withRecovered { (fixture, produced) =>
          val entered = new CountDownLatch(1)
          val release = new CountDownLatch(1)
          val context = fixture.current
          val owner = fixture.native.owner
          fixture.resolver.before = _ => { entered.countDown(); JobDurableRuntimeFixtures.await(release) }
          val reading = JobDurableRuntimeFixtures.actor(fixture.query(produced.id, context))
          var closing = Option.empty[(Thread, java.util.concurrent.atomic.AtomicReference[Either[Throwable, Consequence[Vector[ServiceContainerCleanupOutcome]]]])]
          try {
            JobDurableRuntimeFixtures.await(entered)
            When("actual subsystem shutdown begins while one resolver is admitted and a second query arrives after owner close")
            val selected = JobDurableRuntimeFixtures.actor(fixture.native.subsystem.shutdownC())
            closing = Some(selected)
            JobDurableRuntimeFixtures.eventually(owner.health.state == JobDurableOperationalState.Closed)
            val refused = fixture.query(produced.id, context)
            Then("shutdown waits for one actual lease while new reads refuse without a second resolver or Blob call")
            selected._1.isAlive shouldBe true
            fixture.native.node.activeLeaseCount shouldBe 1
            fixture.closed shouldBe false
            _refused(refused)
            fixture.resolver.contexts should have size 1
            fixture.blob.gets.get() shouldBe 0
            fixture.blob.opens.get() shouldBe 0
            Given("the original read is admitted and the physical native datasource remains open")
            When("the resolver is released and both original actors finish")
            release.countDown()
            JobDurableRuntimeFixtures.join(reading._1)
            JobDurableRuntimeFixtures.join(selected._1)
            Then("the admitted result passes final native checks and drain finishes before SystemNode physical finalization")
            reading._2.get() match {
              case Right(result) => assertDomain(success(result).get.result.get, "review", "")
              case Left(error) => throw error
            }
            selected._2.get() match {
              case Right(result) => success(result)
              case Left(error) => throw error
            }
            fixture.blob.gets.get() shouldBe 1
            fixture.blob.opens.get() shouldBe 1
            fixture.blob.closes.get() shouldBe 1
            fixture.native.node.activeLeaseCount shouldBe 0
            fixture.native.node.state shouldBe SystemNode.State.Running
            fixture.closed shouldBe false
            When("the original node owns explicit physical finalization")
            val stopped = fixture.native.node.shutdownC()
            Then("the actual SQLite resource and original node close")
            success(stopped)
            fixture.native.node.state shouldBe SystemNode.State.Stopped
            fixture.closed shouldBe true
          } finally {
            release.countDown()
            JobDurableRuntimeFixtures.join(reading._1)
            closing.foreach(value => JobDurableRuntimeFixtures.join(value._1))
          }
        }
        Given(s"$_spec E9/R3,R6,R7,R8: a distinct genuine recovered result whose owner closes before any read")
        DurableJobOperationResponseQueryFixtures.withRecovered { (fixture, produced) =>
          val context = fixture.current
          success(fixture.native.subsystem.shutdownC())
          val before = fixture.effects
          When("a new visible query reaches the already closed owner")
          val result = fixture.query(produced.id, context)
          Then("closed-before-read refuses without any new resolver or Blob dispatch")
          _refused(result)
          fixture.effects shouldBe before
          fixture.native.node.activeLeaseCount shouldBe 0
        }
      }
    }

    "E10_Q10 Preserve original absence and legacy selections" must _scenario("E10", "R1,R5,R8") {
      "retain actual default Supplied, live failed cancelled nonterminal and negative legacy terminal behavior" in {
        Given(s"$_spec E10/R1,R5,R8: original configured default Supplied source and actual live task execution")
        JobDurableRuntimeFixtures.withFixture { native =>
          val blob = new DurableJobOperationResponsePayloadFixtures.MeasuredBlobStore(native.selectedBlob)
          val resolver = new DurableJobOperationResponseQueryFixtures.Resolver
          native.selectedBlob = blob
          native.selectedCallerAccessResolver = Some(resolver)
          success(native.start())
          val context = caller(native.context, overview = true)
          val task = DurableJobOperationResponsePublicationFixtures.task(OperationResponse.Scalar("original Supplied"))
          When("the actual default task completes with its original signed absence")
          val id = native.submit(task)
          native.engine.drainAll() shouldBe 1
          val visible = success(native.engine.queryVisible(id)(using context)).get
          Then("actual live response and Supplied absence retain their original compatibility with no owned read")
          native.signed(id).record.body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
          visible.result shouldBe Some(OperationResponse.Scalar("original Supplied"))
          resolver.contexts shouldBe empty
          blob.gets.get() shouldBe 0
          blob.puts.get() shouldBe 0
          Vector("failed", "cancelled", "submitted").foreach { mode =>
            Given(s"$_spec E10/R1,R5,R8: independently actual live $mode task under the original configured engine")
            val selected = new JobDurableLifetimeRecoveryFixtures.Task
            selected.body = _ => TaskFailed(Consequence.stateInvalid[Nothing]("ordinary failure").conclusion)
            val jobid = success(native.owner._execute_c(native.engine.submit(List(selected), context,
              JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral))))
            When("original execution or control selects the requested live state")
            if (mode == "failed") native.engine.drainAll() shouldBe 1
            else if (mode == "cancelled") {
              val policy = new JobControlPolicy {
                def authorize(jobId: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = Consequence.unit
              }
              success(native.engine.control(jobid, JobControlRequest(JobControlCommand.Cancel), policy)(using context))
            }
            val raw = native.engine.query(jobid).get
            val result = success(native.engine.queryVisible(jobid)(using context)).get
            Then("original status and result survive with no caller resolver or payload work")
            result shouldBe raw
            raw.status shouldBe (mode match {
              case "failed" => JobStatus.Failed
              case "cancelled" => JobStatus.Cancelled
              case _ => JobStatus.Submitted
            })
            resolver.contexts shouldBe empty
            blob.gets.get() shouldBe 0
          }
        }
        Vector("absent", "inline", "shared", "owned-wrong-header", "failed", "cancelled").foreach { mode =>
          Given(s"$_spec E10/R1,R5,R8: an explicitly negative canonical legacy $mode terminal consumer seed")
          _negative_fixture(mode) { (fixture, id) =>
            val raw = fixture.native.engine.query(id).get
            val before = fixture.effects
            When("the ordinary default overview authorizes a legacy terminal selection")
            val visible = success(fixture.query(id, fixture.current)).get
            Then("the legacy model remains body-free with original management unavailability and no new owned dispatch")
            visible should be theSameInstanceAs raw
            visible.result shouldBe None
            fixture.effects shouldBe before
            _raw_unavailable(fixture, id, fixture.current)
          }
        }
      }
    }
  }

  "true sequential JVM result responsibility" which {
    "E11_Q11 Accept exact Review in a fresh JVM" must _scenario("E11", "R1,R2,R3,R4,R5,R6,R8,R9") {
      "finish real writer closure before a distinct default-policy native result reader starts" in {
        Given(s"$_spec E11/R1..R6,R8,R9: independent six literal Review fields and a unique managed native namespace")
        DurableJobOperationResponseQueryFixtures.withCase { selected =>
          When("an absolute Java writer exits after actual closure and a separate native reader consumes only its opaque Job ID")
          val writer = selected.writer()
          val reader = selected.reader(writer.receipt.id)
          Then("both children independently check exact domain and native authority and parent receives only bounded observations")
          _process_pair(writer, reader, false)
        }
      }
    }

    "E12_Q12 Refuse foreign current caller after valid fresh startup" must _scenario("E12", "R2,R3,R7,R9") {
      "distinguish valid original startup authority from overview-granted foreign durable query authority" in {
        Given(s"$_spec E12/R2,R3,R7,R9: a real writer and independent fresh startup candidate with original internal access")
        DurableJobOperationResponseQueryFixtures.withCase { selected =>
          When("a foreign-reader launches after actual writer exit with a current foreign subject and existing job_view grant")
          val writer = selected.writer()
          val reader = selected.reader(writer.receipt.id, foreign = true)
          Then("fresh Ready startup registers the original projection before strict current subject refusal with zero Blob work")
          _process_pair(writer, reader, true)
        }
      }
    }

    "E13_Q13 Preserve generated scalar across ordinary shrinking JVM pairs" must _scenario("E13", "R1,R2,R3,R4,R5,R6,R8,R9") {
      "run at least twenty-four real independent scalar writer and reader pairs with seed69710034" in {
        Given(s"$_spec E13/R1..R6,R8,R9: independently generated ASCII alpha1..8, ordinary shrinking, seed69710034/min24/workers1")
        val inputs = Gen.choose(1, 8).flatMap(length => Gen.listOfN(length, Gen.alphaChar).map(_.mkString))
        val property = Prop.forAll(inputs) { input =>
          Given(s"$_spec E13/R1..R6,R8,R9: a new genuine managed namespace for one independently selected scalar input")
          DurableJobOperationResponseQueryFixtures.withCase { selected =>
            When("the scalar writer closes and a separate reader constructs its expected domain only from the same independent input")
            val writer = selected.writer("scalar", input)
            val reader = selected.reader(writer.receipt.id, "scalar", input)
            Then("both children assert exact native scalar value and payment while the reader executes and mutates nothing")
            _process_pair(writer, reader, false)
          }
          true
        }
        When("the ordinary shrinking property checks twenty-four sequential native JVM cases")
        val checked = Test.check(Test.Parameters.default.withInitialSeed(Seed(69710034L))
          .withMinSuccessfulTests(24).withWorkers(1), property)
        Then("the complete seeded process property passes with at least twenty-four successes")
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }
    }
  }

  private def _refused(result: Consequence[?]): Unit = {
    result.isSuccess shouldBe false
    failureMessage(result) shouldBe Some("durable job result query refused")
  }
  private def _no_work(after: Effects, before: Effects): Unit = {
    after.writes shouldBe before.writes
    after.entitywrites shouldBe before.entitywrites
    after.puts shouldBe before.puts
    after.deletes shouldBe before.deletes
    after.sources shouldBe before.sources
    after.ports shouldBe before.ports
    after.tasks shouldBe before.tasks
    after.getters shouldBe before.getters
    after.canonical shouldBe before.canonical
  }
  private def _raw_unavailable(fixture: Fixture, id: JobId, context: ExecutionContext): Unit = {
    fixture.native.engine.query(id).get.result shouldBe None
    fixture.native.engine.getResult(id) shouldBe None
    success(fixture.native.engine.queryManagementResult(id)(using context)) shouldBe
      Some(JobManagementResult.UnavailableAfterRestart(fixture.native.engine.query(id).get.resultSummary))
  }
  private def _negative_fixture[A](mode: String)(body: (Fixture, JobId) => A): A = {
    val root = JobDurableRuntimeFixtures.root()
    val writer = new Fixture(root, owned = false)
    val reader = new Fixture(root)
    try {
      success(writer.start())
      val id = JobInputBoundsFixtures.id(s"negative_${mode.replace('-', '_')}")
      val supplied = JobDurableLifetimeRecoveryFixtures.record(id).body
      val status = mode match {
        case "failed" => DurableJobLifecycleStatus.Failed
        case "cancelled" => DurableJobLifecycleStatus.Cancelled
        case _ => DurableJobLifecycleStatus.Succeeded
      }
      val result = mode match {
        case "inline" => DurableResultOutcome.Succeeded(DurableValue.Inline(DurableInlineMetadata("string", Some("text/plain"), 0L, "0" * 64, "metadata")))
        case "shared" => DurableResultOutcome.Succeeded(DurableValue.External(DurableExternalReference("opaque-shared", "external", Some("text/plain"), 0L, "0" * 64)))
        case "owned-wrong-header" => DurableResultOutcome.Succeeded(DurableValue.External(DurableExternalReference("opaque-owned", DurableJobOwnedBlobPayloads.storageKind, Some("text/plain"), 0L, "0" * 64)))
        case "failed" => DurableResultOutcome.Failed(DurableFailureSummary("execution", "failed", "ordinary failure", false))
        case "cancelled" => DurableResultOutcome.Cancelled(None)
        case _ => DurableResultOutcome.Succeeded(DurableValue.Absent)
      }
      val record = success(DurableJobRecord.createV2(supplied.copy(authorization = recipe().projection.authorization,
        tasks = supplied.tasks.map(task => task.copy(taskId = TaskId("cncf", "query", Some(JobDurableRuntimeFixtures.instant), Some("negative_task")).value,
          transaction = task.transaction.copy(role = DurableTransactionRole.Own, scope = DurableTransactionScope.PerTask,
            outcome = if (mode == "failed") DurableTransactionOutcome.Failed else DurableTransactionOutcome.Committed))),
        lifecycle = supplied.lifecycle.copy(status = status, schedule = supplied.lifecycle.schedule.copy(
          startedAt = Some(JobDurableRuntimeFixtures.instant.plusSeconds(10)), completedAt = Some(JobDurableRuntimeFixtures.instant.plusSeconds(20)))), result = result)))
      success(writer.use {
        JobDurableLifetimeRecoveryFixtures.publishTerminal(writer.native.owner._store, record)(using writer.native.context)
        Consequence.unit
      })
      writer.close()
      reader.open(id)
      body(reader, id)
    } finally { reader.close(); writer.close(); JobDurableRuntimeFixtures.remove(root) }
  }
  private def _process_pair(writer: Child, reader: Child, foreign: Boolean): Unit = {
    val parent = DurableJobOperationResponseQueryFixtures.identity()
    writer.exited shouldBe true
    reader.exited shouldBe true
    writer.exitcode shouldBe 0
    reader.exitcode shouldBe 0
    Set(parent.pid, writer.identity.pid, reader.identity.pid).size shouldBe 3
    Set(parent.start, writer.identity.start, reader.identity.start).size shouldBe 3
    Set(parent.uuid, writer.identity.uuid, reader.identity.uuid).size shouldBe 3
    val stable = Vector("id", "revision", "providerrevision", "reference", "status", "quotastate", "chargedtasks",
      "historycredits", "admissions", "cancelcredits", "remaining", "normalids", "claims", "cancelclaimed")
    stable.foreach(key => reader.receipt(key) shouldBe writer.receipt(key))
    Vector(writer, reader).foreach { child =>
      val facts = child.receipt
      facts.number("revision") shouldBe 6L
      facts("status") shouldBe "succeeded"
      facts("quotastate") shouldBe "Published"
      facts.number("chargedtasks") shouldBe 2L
      facts.number("historycredits") shouldBe 96L
      facts.number("admissions") shouldBe 1L
      facts.number("cancelcredits") shouldBe 1L
      facts.number("remaining") shouldBe 0L
      facts.number("normalids") shouldBe 1L
      facts.number("claims") shouldBe 0L
      facts.flag("cancelclaimed") shouldBe false
      Vector("ready", "domainChecked", "subsystemclosed", "ownerclosed", "nodeclosed", "sqlclosed").foreach(key => facts.flag(key) shouldBe true)
      facts.number("leases") shouldBe 0L
      facts.number("ports") shouldBe 0L
      facts.number("deletes") shouldBe 0L
    }
    writer.receipt.number("tasks") shouldBe 1L
    writer.receipt.number("canonical") shouldBe 1L
    writer.receipt.number("sources") shouldBe 6L
    writer.receipt.number("puts") shouldBe 1L
    writer.receipt.number("writes") should be > 0L
    val facts = reader.receipt
    Vector("tasks", "getters", "canonical", "sources", "ports", "writes", "entitywrites", "puts", "deletes", "drained").foreach(key => facts.number(key) shouldBe 0L)
    Vector("rawabsent", "managementunavailable", "rowsunchanged").foreach(key => facts.flag(key) shouldBe true)
    facts.number("resolvers") shouldBe 1L
    facts.number("gets") shouldBe (if (foreign) 0L else 1L)
    facts.number("opens") shouldBe (if (foreign) 0L else 1L)
    facts.number("closes") shouldBe (if (foreign) 0L else 1L)
    facts("refusal") shouldBe (if (foreign) "durable job result query refused" else "")
  }
}
