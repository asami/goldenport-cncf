package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{PostgresqlAcceptance, PostgresqlPersistenceFixture}
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.AcknowledgementMode
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityVersionedMutationOutcomeFixture.{ProjectionMode, success, revision, conclusion}
import org.goldenport.cncf.observability.{DslChokepointOutcome, DslChokepointPhase}
import org.goldenport.cncf.operation.CmlOperationAccess
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 19, 2026
 */
@org.scalatest.tags.Slow
final class PostgresqlAggregateObservedMutationCaptureAcceptanceSpec extends AnyWordSpec
    with AggregateObservedMutationCaptureFixture.Vocabulary with PostgresqlAcceptance {
  import AggregateObservedMutationCaptureFixture.*
  import AggregateObservedMutationCaptureFixture.given

  "Required native PostgreSQL Aggregate mutation capture" should {
    "retain actual canonical outcomes independently of optional projection and coherence" which {
      "AGC-01 retain all registered root/alias and current/changed/stale branches for both managed representations" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          val sessions = representations.map(value => value -> new Session(database.token("aggregate_capture"), value, writer)).toMap
          for { representation <- representations; alias <- Vector(false, true); mode <- Mode.values.toVector } {
            Given("required PostgreSQL 17.11 durability, actual Aggregate registration and independent complete native state")
            val sample = Sample("Native 日本 Ω", "native retained security/audit", mode, representation, alias, ProjectionMode.Good, Fault.None)
            When("the actual registered Aggregate scenario interprets one native command")
            val satisfied = checkLaw(sample, sessions(representation), reader, () => database.snapshotAll(), "postgresql")
            Then("the exact raw outcome, full logical Fields and physical tables correspond without enclosing lifecycle")
            satisfied shouldBe true
          }
        }
      }
      "AGC-01 retain current native maximum-revision NoOp" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          representations.foreach { representation =>
            Given("a complete native Aggregate at Long.MAX_VALUE and independently declared exact NoOp state")
            val current = new Session(database.token("aggregate_capture_max"), representation, writer)
            current.seed(initialRevision = Long.MaxValue)
            val before = current.rows(reader); val beforephysical = database.snapshotAll(); val resident = current.resident
            val wanted = expected(current, before, beforephysical, "Before", false, Long.MaxValue, "postgresql")
            current.arm()
            When("one actual alias command retains its complete business value at the maximum observation")
            val attempt = current.attempt(root => Consequence.success(root), revision(Long.MaxValue), current.aliasName)
            Then("the real provider NoOp requires no successor and retains the same raw Record and active owner")
            val capture = success(attempt.result)
            checkPrimitive(current, 1, revision(Long.MaxValue)); checkCapture(capture, current, wanted, false, Long.MaxValue)
            checkProjection(capture.mutation.projection, EntityVersionedMutationOutcomeFixture.typedVariant(representation), wanted.rows, current.data, Long.MaxValue)
            checkCoherence(current, capture, resident, Fault.None, true)
            checkRows(current.rows(reader), wanted.rows); checkPhysical(database.snapshotAll(), wanted.physical)
          }
        }
      }
      "AGC-02 retain actual native default-policy and factory denial before domain" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          for { representation <- representations; factory <- Vector(false, true); alias <- Vector(false, true) } {
            Given("an actual declared manager policy or factory denial and independent complete native baseline")
            val current = new Session(database.token("aggregate_capture_auth"), representation, writer,
              access = CmlOperationAccess(if (factory) "authenticated_only" else "manager_only"), denyFactory = factory)
            current.seed(); val before = current.rows(reader); val beforephysical = database.snapshotAll(); val resident = current.resident
            val name = if (alias) current.aliasName else current.rootName
            current.arm()
            When("the registered command reaches normal Aggregate authorization on the selected active native UoW")
            val attempt = current.attempt(root => Consequence.success(root.withTitle("Forbidden")), aggregateName = name)
            Then("the real policy rejection precedes builder, domain and provider and retains every native row")
            checkPrimitive(current, 0); attempt.callbacks shouldBe 0; current.builderCalls shouldBe Vector.empty
            attempt.result shouldBe a[Consequence.Failure[?]]
            if (factory) {
              (conclusion(attempt.result) eq current.factoryFailure.conclusion) shouldBe true
              checkAuthorization(current, name)
            } else current.factoryCalls shouldBe Vector.empty
            current.resident shouldBe resident
            checkRows(current.rows(reader), before); checkPhysical(database.snapshotAll(), beforephysical)
          }
        }
      }
      "AGC-05 retain every native typed projection and actual Realm fault after authoritative ACK" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          val sessions = representations.map(value => value -> new Session(database.token("aggregate_capture_fault"), value, writer)).toMap
          for { representation <- representations; changed <- Vector(false, true)
            projection <- ProjectionMode.values.toVector; fault <- Fault.values.toVector } {
            Given("actual native provider outcomes and independently selected post-ACK projection/Realm faults")
            val sample = Sample("Native fault 日本 Ω", "native retained fault baseline", if (changed) Mode.Changed else Mode.Current,
              representation, true, projection, fault)
            When("the registered Aggregate scenario reaches the canonical typed producer then optional local coherence")
            val satisfied = checkLaw(sample, sessions(representation), reader, () => database.snapshotAll(), "postgresql")
            Then("complete exact capture and native full state survive the optional failures without replay")
            satisfied shouldBe true
          }
        }
      }
      "AGC-05 retain native known capture after actual framework persistence/outer leave faults" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          for { representation <- representations; changed <- Vector(false, true); phasefault <- Vector(false, true) } {
            Given("real framework hooks armed only after the native provider ACK and an independent full-state expectation")
            val log = new HookLog; val first = new RecordingHook("first", log); val second = new RecordingHook("second", log)
            val current = new Session(database.token("aggregate_capture_telemetry"), representation, writer, dslHooks = Some(Vector(first, second)))
            current.seed(); log.clear()
            val before = current.rows(reader); val beforephysical = database.snapshotAll(); val resident = current.resident
            val title = if (changed) "After telemetry" else "Before"; val version = if (changed) 2L else 1L
            val wanted = expected(current, before, beforephysical, title, changed, version, "postgresql")
            current.arm(afterAck = () => second.arm(event =>
              if (event.outcome.nonEmpty && (if (phasefault) event.phase.contains(DslChokepointPhase.Persistence) else event.phase.isEmpty))
                Some(new IllegalStateException("private native telemetry credential")) else None))
            When("the actual alias command receives a known ACK before telemetry throws NonFatal")
            val capture = success(current.attempt(root => Consequence.success(root.withTitle(title)), aggregateName = current.aliasName).result)
            Then("the exact capture and full native state survive and all remaining hooks release")
            checkPrimitive(current, 1); checkCapture(capture, current, wanted, changed, version)
            checkProjection(capture.mutation.projection, EntityVersionedMutationOutcomeFixture.typedVariant(representation), wanted.rows, current.data, version)
            checkCoherence(current, capture, resident, Fault.None, true)
            checkRows(current.rows(reader), wanted.rows); checkPhysical(database.snapshotAll(), wanted.physical)
            log.events.takeRight(2).map(event => event.name -> event.outcome) shouldBe Vector(
              "second" -> Some(DslChokepointOutcome.Success), "first" -> Some(DslChokepointOutcome.Success))
            log.events.foreach(event => (event.execution eq current.context) shouldBe true)
          }
        }
      }
      "AGC-06 retain both native captures after the real owned SQL writer closes following ACK" in {
        for { representation <- representations; changed <- Vector(false, true) } {
          with_postgresql { database =>
            Given("a native Aggregate session with its owned SQL writer and an independent durable reader")
            val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
            val current = new Session(database.token("aggregate_capture_closed"), representation, writer)
            When("the shared native closure scenario closes the writer only after authoritative ACK")
            checkClosed(current, reader,
              () => database.snapshotAll(), "postgresql", changed, () => { success(writer.closeC()); () })
            Then("one canonical native attempt leaves enclosing commit and abort with the caller")
            checkPrimitive(current, 1)
          }
        }
      }
      "AGC-06 preserve the complete actual independent native winner without replay" in {
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val competitor = database.open(normalizeColumns = true)
          val reader = database.open(normalizeColumns = true)
          representations.foreach { representation =>
            Given("an actual native Aggregate session and independently owned competing provider and durable reader")
            val current = new Session(database.token("aggregate_capture_race"), representation, writer)
            When("the shared native race scenario publishes its independent winner before the Aggregate save")
            checkRace(current, competitor, reader,
              () => database.snapshotAll(), "postgresql")
            Then("the losing Aggregate attempts its canonical mutation once without enclosing lifecycle activity")
            checkPrimitive(current, 1)
          }
        }
      }
      "AGC-07 distinguish known rollback from real committed Indeterminate, thrown and null acknowledgements" in {
        for { representation <- representations; mode <- Vector(AcknowledgementMode.Normal, AcknowledgementMode.Reject,
          AcknowledgementMode.Indeterminate, AcknowledgementMode.Throw, AcknowledgementMode.Null) } {
          with_postgresql { database =>
            Given("an actual native Aggregate session with the selected real acknowledgement writer and independent reader")
            val writer = database.openAcknowledgement(); val reader = database.open()
            val current = new Session(database.token("aggregate_capture_ack"), representation, writer)
            When("the shared acknowledgement scenario interprets the selected native commit mode")
            checkAcknowledgement(current, writer, reader,
              () => database.snapshotAll(), mode)
            Then("one canonical attempt preserves enclosing lifecycle ownership across every ACK mode")
            checkPrimitive(current, 1)
          }
        }
      }
    }

    "satisfy the total native law on bounded reused roots and pools" which {
      "AGC-P1 require one hundred PostgreSQL successes with zero discarded samples" in {
        Given("total Unicode, representation, alias, revision-mode and lawful post-ACK fault generation with meaningful shrinking")
        with_postgresql { database =>
          val writer = database.open(normalizeColumns = true); val reader = database.open(normalizeColumns = true)
          val sessions = representations.map(value => value -> new Session(database.token("aggregate_capture_law"), value, writer)).toMap
          val property = Prop.forAll(samples)(sample => checkLaw(sample, sessions(sample.representation), reader,
            () => database.snapshotAll(), "postgresql"))
          When("ScalaCheck interprets the actual native Aggregate law sequentially on reused providers and roots")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
          Then("complete native state, capture identity and lifecycle must pass one hundred successes without discard")
          checked.passed shouldBe true; checked.succeeded should be >= 100; checked.discarded shouldBe 0
        }
      }
    }
  }
}
