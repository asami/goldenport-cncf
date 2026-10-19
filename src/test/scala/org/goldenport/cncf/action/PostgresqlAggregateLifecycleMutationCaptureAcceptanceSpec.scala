package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.{AcknowledgementMode, AcknowledgementStore}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityCreateFailureSafetyFixture.BodyMode
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.cncf.unitofwork.UnitOfWorkMutationCaptureFixture
import org.goldenport.cncf.unitofwork.UnitOfWorkMutationCaptureFixture.{DecodeMode, conclusion, reason, revision, success}
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * @since   Oct. 16, 2026
 * @version Oct. 19, 2026
 */
@org.scalatest.tags.Slow
final class PostgresqlAggregateLifecycleMutationCaptureAcceptanceSpec extends AnyWordSpec
    with AggregateLifecycleMutationCaptureFixture.Vocabulary with PostgresqlAcceptance {
  import AggregateLifecycleMutationCaptureFixture.*
  import AggregateLifecycleMutationCaptureFixture.given

  "Required PostgreSQL 17.11 Aggregate lifecycle capture with fsync and synchronous_commit" should {
    "retain positive native root and alias receipts" which {
      for (normalized <- Vector(false, true)) {
        s"ALC-PG1 retain native create for both representations all bodies IDs and aliases with normalization $normalized" in {
          Given("mandatory PostgreSQL and independent exact and normalized readers on one bounded owned environment")
          with_postgresql { database =>
            val environment = UnitOfWorkMutationCaptureFixture.postgresql(database, normalized)
            val tokens = representations.map(representation => representation -> database.token("alc_create_" + representation)).toMap
            for { representation <- representations; body <- BodyMode.values.toVector; supplied <- Vector(false, true); alias <- Vector(false, true) } {
              val session = new Session(tokens(representation), sample.copy(value = sample.value.copy(
                representation = representation, body = body, supplied = supplied), alias = alias), environment)
              try {
                When("the complete actual registered Aggregate creation scenario receives its native ACK")
                val satisfied = checkCreateLaw(session)
                Then("exact raw references and complete independently declared catalog rows NULL cells and resident/View state agree")
                satisfied shouldBe true
              } finally session.cleanup()
            }
          }
        }
        s"ALC-PG1 retain native physical and logical root/alias deletion with normalization $normalized" in {
          Given("mandatory PostgreSQL, both root representations and a complete independent deletion oracle")
          with_postgresql { database =>
            val environment = UnitOfWorkMutationCaptureFixture.postgresql(database, normalized)
            val tokens = representations.map(representation => representation -> database.token("alc_delete_" + representation)).toMap
            for { representation <- representations; logical <- Vector(false, true); alias <- Vector(false, true) } {
              val session = new Session(tokens(representation), sample.copy(value = sample.value.copy(
                representation = representation, logical = logical), alias = alias), environment)
              try {
                When("one actual Aggregate helper performs canonical observed deletion on native PostgreSQL")
                val satisfied = checkDeleteLaw(session)
                Then("the exact prior revision receipt and full independent tombstone/body-preservation or root/body-removal state agree")
                satisfied shouldBe true
              } finally session.cleanup()
            }
          }
        }
      }
    }
    "preserve native authority admission and optional coherence" which {
      "ALC-PG2 preserve native default factory domain stale duplicate and foreign-target rejections" in {
        for { representation <- representations
          variant <- Vector("manager-create", "manager-delete", "factory-create", "factory-delete", "veto", "stale", "duplicate", "foreign") } {
          Given("an actual named Aggregate root/alias, selected native failure condition and complete independent baseline")
          with_postgresql { database =>
            val environment = UnitOfWorkMutationCaptureFixture.postgresql(database, false)
            val session = new Session(database.token("alc_rejection"), sample.copy(value = sample.value.copy(representation = representation), alias = true),
              environment, access = CmlOperationAccess(if (variant.startsWith("manager")) "manager_only" else "default"),
              denyFactory = variant.startsWith("factory"))
            session.seed(); val before = session.baseline
            val candidate = session.data.input.copy(identity = Some(session.data.id), record = session.data.input.record.upsertSingle("id", session.data.id))
            val foreign = new UnitOfWorkMutationCaptureFixture.CanonicalId(EntityCollectionId("foreign", "capture", session.rootName), "foreign")
            When("the actual helper reaches default/factory/domain/admission or duplicate rejection")
            val result: Consequence[?] = if (variant.endsWith("create")) session.create().result
              else if (variant == "duplicate") session.create(() => Consequence.success(candidate)).result
              else session.delete(_ => if (variant == "veto") session.veto else Consequence.unit,
                observed = revision(sample.value.version - (if (variant == "stale") 1L else 0L)),
                target = if (variant == "foreign") foreign else session.data.id).result
            Then("every native selected/unrelated cell and logical field remains unchanged and no receipt or replay is manufactured")
            result shouldBe a[Consequence.Failure[?]]
            session.data.recording.creates shouldBe (if (variant == "duplicate") 1 else 0)
            session.data.recording.deletes shouldBe 0
            session.order.count(_ == "command") shouldBe (if (variant == "duplicate") 1 else 0)
            session.order.count(_ == "validate") shouldBe (if (variant == "veto") 1 else 0)
            session.builderCalls should have size (if (variant == "veto") 1 else 0)
            if (variant.startsWith("factory")) (conclusion(result) eq session.factoryFailure.conclusion) shouldBe true
            if (variant == "veto") (conclusion(result) eq session.veto.conclusion) shouldBe true
            if (variant == "stale") reason(result) shouldBe Some("stale-entity-revision")
            checkPreserved(session, before)
          }
        }
      }
      "ALC-PG2 retain exact native creation through registered post-ACK decoder failures" in {
        Given("mandatory native persistence and actual registered optional decoder faults armed only after real ACK")
        with_postgresql { database =>
          val environment = UnitOfWorkMutationCaptureFixture.postgresql(database, true)
          val tokens = representations.map(representation => representation -> database.token("alc_decode_" + representation)).toMap
          for { representation <- representations; mode <- DecodeMode.values.toVector } {
            val session = new Session(tokens(representation), sample.copy(value = sample.value.copy(representation = representation, mode = mode), alias = true), environment)
            try {
              When("the complete actual Aggregate create scenario encounters optional registered decode behavior")
              val satisfied = checkCreateLaw(session)
              Then("the exact acknowledged result and full durable native state survive without callback or mutation replay")
              satisfied shouldBe true
            } finally session.cleanup()
          }
        }
      }
      "ALC-PG2 retain native physical/logical deletion through real post-ACK Realm eviction failure" in {
        for { representation <- representations; logical <- Vector(false, true) } {
          Given("an actual registered Realm, native root/body data and a full independent pre-action oracle")
          with_postgresql { database =>
            val environment = UnitOfWorkMutationCaptureFixture.postgresql(database, true)
            val session = new Session(database.token("alc_evict"), sample.copy(value = sample.value.copy(representation = representation, logical = logical), alias = true), environment)
            session.seed(); val before = session.baseline
            val wanted = UnitOfWorkMutationCaptureFixture.expectedDelete(session.data, before.rows, before.physical, true)
            val exact = UnitOfWorkMutationCaptureFixture.expectedDelete(session.data, before.exact, before.physical, false)
            session.reset(throwEvict = true)
            When("one actual native observed-delete ACK precedes throwing optional Realm removal")
            val attempt = session.delete()
            Then("the exact matched receipt and full native tombstone/absence survive while the resident failure is explicit")
            checkDelete(session, before, wanted, exact, attempt, evictionFailure = true)
          }
        }
      }
      "ALC-PG2 retain the actual independent native winner after atomic stale delete recheck" in {
        for { representation <- representations; logical <- Vector(false, true) } {
          Given("a real PostgreSQL Aggregate and a separately pooled native provider for an intervening winner")
          with_postgresql { database =>
            val environment = UnitOfWorkMutationCaptureFixture.postgresql(database, false)
            val session = new Session(database.token("alc_winner"), sample.copy(value = sample.value.copy(representation = representation, logical = logical), alias = true), environment)
            When("the complete independent-winner scenario commits before the native conditional delete")
            checkRace(session)
            Then("the loser makes one actual atomic delete attempt and retains the caller-owned lifecycle")
            session.data.recording.deletes shouldBe 1; checkLifecycle(session)
          }
        }
      }
    }
    "retain native acknowledgement distinctions" which {
      "ALC-PG2 distinguish rejected commit from real commit with positive indeterminate thrown or null ACK" in {
        for { representation <- representations; action <- Vector("create", "physical-delete", "logical-delete")
          mode <- Vector(AcknowledgementMode.Normal, AcknowledgementMode.Reject, AcknowledgementMode.Indeterminate, AcknowledgementMode.Throw, AcknowledgementMode.Null) } {
          Given("a real native JDBC acknowledgement seam and actual registered root/alias on an independent owned environment")
          with_postgresql { database =>
            val environment = UnitOfWorkMutationCaptureFixture.postgresql(database, false, acknowledgement = true)
            val session = new Session(database.token("alc_ack"), sample.copy(value = sample.value.copy(
              representation = representation, logical = action == "logical-delete"), alias = true), environment)
            When("the complete actual native acknowledgement scenario interprets one lifecycle helper")
            checkAcknowledgement(session, environment.writer.asInstanceOf[AcknowledgementStore], action == "create", mode)
            Then("provider ACK alone never supplies an enclosing UnitOfWork commit witness")
            checkLifecycle(session)
          }
        }
      }
    }
    "satisfy two separate bounded total native laws" which {
      "ALC-PG-P1 require 100 sequential native create successes with meaningful shrinking and zero discards" in {
        Given("mandatory native PostgreSQL, a total Unicode/body/representation/identity/root-alias generator and two suite-owned roots")
        with_postgresql { database =>
          val environment = UnitOfWorkMutationCaptureFixture.postgresql(database, true)
          val tokens = representations.map(representation => representation -> database.token("alc_create_law_" + representation)).toMap
          val law = Prop.forAll(samples) { value =>
            val session = new Session(tokens(value.value.representation), value, environment)
            try checkCreateLaw(session) finally session.cleanup()
          }
          When("actual Aggregate creation executes sequentially and exact owned IDs are cleaned before legal reuse")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), law)
          Then("at least 100 successes and zero discards prove positive native creation rather than duplicate-only behavior")
          result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0
        }
      }
      "ALC-PG-P2 require 100 sequential native current-stale-veto deletion successes with meaningful shrinking and zero discards" in {
        Given("mandatory native PostgreSQL and a total legal-revision/logical-physical/current-stale-veto generator with bounded roots and pools")
        with_postgresql { database =>
          val environment = UnitOfWorkMutationCaptureFixture.postgresql(database, true)
          val tokens = representations.map(representation => representation -> database.token("alc_delete_law_" + representation)).toMap
          val law = Prop.forAll(samples) { value =>
            val session = new Session(tokens(value.value.representation), value, environment)
            try checkDeleteLaw(session) finally session.cleanup()
          }
          When("the real Aggregate observed-delete law executes on one worker without filters implications or absent-native skips")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), law)
          Then("at least 100 successful native cases and zero discards retain exact prior receipts or complete preserved state")
          result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0
        }
      }
    }
  }
}
