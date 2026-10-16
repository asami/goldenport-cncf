package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.EntityCreateProviderFixture.race
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalFixture.{reason, success}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
final class PostgresqlConditionalTransitionAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen with PostgresqlAcceptance {
  "Native PostgreSQL conditional lifecycle" should {
    "preserve admitted Create, Bind and source predicate outcomes" which {
      Vector(false, true).foreach { bind =>
        s"PG-E7 commit actual guarded successor Bind $bind with authoritative root and body" in {
          Given("a real native root, complete side/unrelated records and an admitted same-provider successor")
          with_postgresql { database =>
            val writer = database.open()
            val reader = database.open()
            val setup = new MutationSession(database.token(s"pg_conditional_$bind"), writer)
            setup.seed()
            if (bind) success(writer.create(setup.successorCollection, setup.successorEntry,
              Record.dataAuto("title" -> "Bound complete successor", "cncf_revision" -> 1L))(using setup.executionContext))
            val before = database.snapshotAll()
            val plan = setup.conditionalPlan(bind)
            When("the actual DataStoreSpace delegates the one admitted native conditional transaction")
            val result = setup.executionContext.dataStoreSpace.conditionalTransition(plan)(using setup.executionContext)
            val transitioned = success(result).asInstanceOf[DataStoreConditionalTransitionResult.Transitioned]
            val root = setup.load(reader)
            val successor = success(reader.load(setup.successorCollection, setup.successorEntry)(using setup.executionContext)).get
            Then("the authoritative result and independent complete root/successor/body records correspond")
            transitioned.rootRecord.asMap shouldBe root.asMap
            transitioned.successorRecord.asMap shouldBe successor.asMap
            root.getString("status") shouldBe Some("closed")
            root.getLong("cncf_revision") shouldBe Some(2L)
            root.getString("successor_id") shouldBe Some(setup.successorEntry.print)
            successor.getLong("cncf_revision") shouldBe Some(1L)
            success(reader.load(setup.sideCollection, setup.sideEntry)(using setup.executionContext)).get.getString("content") shouldBe Some("Changed complete body")
            val after = database.snapshotAll()
            after("public" -> setup.rootCollection.collectionName).rows.find(_("id") == "unrelated") shouldBe before("public" -> setup.rootCollection.collectionName).rows.find(_("id") == "unrelated")
            after("public" -> setup.sideCollection.collectionName).rows.find(_("id") == "unrelated") shouldBe before("public" -> setup.sideCollection.collectionName).rows.find(_("id") == "unrelated")
            if (bind) after("public" -> setup.successorCollection.collectionName) shouldBe before("public" -> setup.successorCollection.collectionName)
          }
        }
      }
      Vector("revision", "field").foreach { mismatch =>
        s"PG-E7 return authoritative NotMatched for $mismatch without materializing prospective targets" in {
          Given("an actual native root and absent prospective successor and side relations")
          with_postgresql { database =>
            val writer = database.open()
            val setup = new MutationSession(database.token(s"pg_notmatched_$mismatch"), writer)
            setup.seed(status = if (mismatch == "field") "closed" else "open")
            val prospective = DataStore.CollectionId.EntityStore(
              EntityCollectionId("test", "native", database.token("pg_absent_successor")))
            val side = DataStore.CollectionId(database.token("pg_absent_side"))
            val initial = setup.conditionalPlan(expectedRevision = if (mismatch == "revision") 2L else 1L)
            val plan = success(DataStoreConditionalTransitionPlan.create(initial.root,
              initial.successor.asInstanceOf[DataStoreConditionalSuccessor.Create].copy(collection = prospective),
              Vector(EntityVersionedSideEffect.Save(side, setup.sideEntry, Record.dataAuto("content" -> "Unpublished"))), initial.correlation))
            val before = database.snapshotAll()
            When("native root lock and exact source predicates return an authoritative mismatch")
            val result = writer.conditionalTransition(plan)(using setup.executionContext)
            Then("the context outcome has the actual root record and complete schema/row state is retained")
            success(result) shouldBe DataStoreConditionalTransitionResult.NotMatched(setup.load(database.open()))
            database.snapshotAll() shouldBe before
            database.snapshotAll().keys.exists(_._2 == prospective.collectionName) shouldBe false
            database.snapshotAll().keys.exists(_._2 == side.collectionName) shouldBe false
          }
        }
      }
      Vector("create-collision", "bind-missing", "bind-stale", "root-missing", "field-type").foreach { rejected =>
        s"PG-E7 preserve all native state for $rejected successor/source admission" in {
          Given("the actual native conditional source and selected invalid successor or source state")
          with_postgresql { database =>
            val writer = database.open()
            val setup = new MutationSession(database.token(s"pg_conditional_rejected_$rejected"), writer)
            setup.seed()
            if (rejected == "create-collision" || rejected == "bind-stale")
              success(writer.create(setup.successorCollection, setup.successorEntry,
                Record.dataAuto("title" -> "Protected existing successor", "cncf_revision" -> 3L))(using setup.executionContext))
            if (rejected == "root-missing") success(writer.delete(setup.rootCollection, setup.rootEntry)(using setup.executionContext))
            val fields = if (rejected == "field-type") Vector(DataStoreConditionalExpectedField("status", success(DataStoreConditionalValue.from(1L))))
              else Vector(DataStoreConditionalExpectedField("status", success(DataStoreConditionalValue.from("open"))))
            val plan = setup.conditionalPlan(bind = rejected.startsWith("bind"), expectedFields = fields)
            val before = database.snapshotAll()
            When("the actual native guarded transition evaluates the source and successor")
            val result = writer.conditionalTransition(plan)(using setup.executionContext)
            Then("structured failure never overwrites the successor or publishes partial root/body rows")
            result shouldBe a[Consequence.Failure[?]]
            if (rejected == "create-collision") reason(result) shouldBe Some("successor-collision")
            if (rejected == "bind-stale") reason(result) shouldBe Some("bound-successor-revision-conflict")
            database.snapshotAll() shouldBe before
          }
        }
      }
    }

    "preserve actual rollback and acknowledgement semantics" which {
      Vector(AcknowledgementMode.RejectConditional, AcknowledgementMode.Reject, AcknowledgementMode.Indeterminate,
        AcknowledgementMode.Throw, AcknowledgementMode.Null).foreach { mode =>
        s"PG-E9/PG-E10 retain the real conditional $mode outcome without replay" in {
          Given("a native conditional writer and a complete independently observed root/successor/body baseline")
          with_postgresql { database =>
            val writer = database.openAcknowledgement()
            val setup = new MutationSession(database.token(s"pg_conditional_ack_$mode"), writer)
            setup.seed()
            val before = database.snapshotAll()
            writer.arm(mode)
            When("one actual conditional transaction reaches prepublish, rejected commit or real commit with acknowledgement loss")
            val result = writer.conditionalTransition(setup.conditionalPlan())(using setup.executionContext)
            Then("known failure rolls back, while unknown acknowledgement retains the correct durable whole pair")
            result shouldBe a[Consequence.Failure[?]]
            writer.commitAttempts shouldBe (if (mode == AcknowledgementMode.RejectConditional) 0 else 1)
            if (mode == AcknowledgementMode.RejectConditional || mode == AcknowledgementMode.Reject) {
              reason(result) shouldBe Some(if (mode == AcknowledgementMode.Reject) "transaction-failure" else "provider-failure")
              database.snapshotAll() shouldBe before
            } else {
              reason(result) shouldBe Some("transaction-indeterminate")
              val reader = database.open()
              setup.load(reader).getString("status") shouldBe Some("closed")
              setup.load(reader).getLong("cncf_revision") shouldBe Some(2L)
              success(reader.load(setup.successorCollection, setup.successorEntry)(using setup.executionContext)).get.getLong("cncf_revision") shouldBe Some(1L)
              success(reader.load(setup.sideCollection, setup.sideEntry)(using setup.executionContext)).get.getString("content") shouldBe Some("Changed complete body")
              val after = database.snapshotAll()
              after("public" -> setup.rootCollection.collectionName).rows.find(_("id") == "unrelated") shouldBe before("public" -> setup.rootCollection.collectionName).rows.find(_("id") == "unrelated")
              after("public" -> setup.sideCollection.collectionName).rows.find(_("id") == "unrelated") shouldBe before("public" -> setup.sideCollection.collectionName).rows.find(_("id") == "unrelated")
            }
          }
        }
      }
    }

    "arbitrate independent native root and successor races" which {
      "PG-E7 retain one same-root winner and an authoritative NotMatched loser" in {
        Given("two independent native writer pools, distinct successors and different complete body candidates")
        with_postgresql { database =>
          val first = database.open()
          val second = database.open()
          val setup = new MutationSession(database.token("pg_same_root_race"), first)
          setup.seed()
          val firstbase = setup.conditionalPlan(successorEntryId = DataStore.StringEntryId("first-successor"))
          val firstplan = success(DataStoreConditionalTransitionPlan.create(firstbase.root, firstbase.successor,
            Vector(setup.sideEffect("First winning body")), firstbase.correlation))
          val secondbase = setup.conditionalPlan(successorEntryId = DataStore.StringEntryId("second-successor"))
          val secondplan = success(DataStoreConditionalTransitionPlan.create(secondbase.root, secondbase.successor,
            Vector(setup.sideEffect("Second winning body")), secondbase.correlation))
          When("both native transactions contend on the actual root FOR UPDATE lock")
          val results = race(() => first.conditionalTransition(firstplan)(using setup.executionContext),
            () => second.conditionalTransition(secondplan)(using context(second)))
          val winners = results.zipWithIndex.collect { case (Consequence.Success(value: DataStoreConditionalTransitionResult.Transitioned), index) => value -> index }
          val reader = database.open()
          Then("one whole acknowledged outcome wins and the loser creates no successor or mismatched body")
          winners.size shouldBe 1
          results.flatMap(_.toOption).count(_.isInstanceOf[DataStoreConditionalTransitionResult.NotMatched]) shouldBe 1
          val (winner, index) = winners.head
          val plans = Vector(firstplan, secondplan)
          winner.rootRecord.asMap shouldBe setup.load(reader).asMap
          success(reader.load(setup.successorCollection, plans(1 - index).successor.entryId)(using setup.executionContext)) shouldBe None
          success(reader.load(setup.sideCollection, setup.sideEntry)(using setup.executionContext)).get.getString("content") shouldBe
            Some(if (index == 0) "First winning body" else "Second winning body")
        }
      }
      "PG-E7 retain one different-roots/same-successor winner and roll back the native id-arbiter loser" in {
        Given("independent native roots, separate bodies and one absent shared successor identity")
        with_postgresql { database =>
          val first = database.open()
          val second = database.open()
          val setup = new MutationSession(database.token("pg_successor_race"), first)
          setup.seed()
          val secondroot = DataStore.StringEntryId("second-root")
          val secondside = DataStore.StringEntryId("second-body")
          success(first.create(setup.rootCollection, secondroot, Record.dataAuto("status" -> "open", "title" -> "Second before", "cncf_revision" -> 1L))(using setup.executionContext))
          success(first.create(setup.sideCollection, secondside, Record.dataAuto("content" -> "Second before body"))(using setup.executionContext))
          val before = database.snapshotAll()
          val firstbase = setup.conditionalPlan()
          val secondbase = setup.conditionalPlan(rootEntryId = secondroot)
          val plans = Vector(success(DataStoreConditionalTransitionPlan.create(firstbase.root, firstbase.successor,
            Vector(setup.sideEffect("First after body")), firstbase.correlation)),
            success(DataStoreConditionalTransitionPlan.create(secondbase.root, secondbase.successor,
              Vector(EntityVersionedSideEffect.Save(setup.sideCollection, secondside,
                Record.dataAuto("content" -> "Second after body"))), secondbase.correlation)))
          When("different native root locks admit simultaneous id-targeted successor creates")
          val results = race(() => first.conditionalTransition(plans(0))(using setup.executionContext),
            () => second.conditionalTransition(plans(1))(using context(second)))
          val winners = results.zipWithIndex.collect { case (Consequence.Success(value: DataStoreConditionalTransitionResult.Transitioned), index) => value -> index }
          Then("one acknowledged successor owner wins and count-zero conflict rolls back the complete losing root/body")
          winners.size shouldBe 1
          val (_, index) = winners.head
          reason(results(1 - index)) shouldBe Some("successor-collision")
          val after = database.snapshotAll()
          val losingroot = Vector(setup.rootEntry, secondroot)(1 - index).print
          val losingside = Vector(setup.sideEntry, secondside)(1 - index).print
          after("public" -> setup.rootCollection.collectionName).rows.find(_("id") == losingroot) shouldBe before("public" -> setup.rootCollection.collectionName).rows.find(_("id") == losingroot)
          after("public" -> setup.sideCollection.collectionName).rows.find(_("id") == losingside) shouldBe before("public" -> setup.sideCollection.collectionName).rows.find(_("id") == losingside)
          success(database.open().load(setup.successorCollection, setup.successorEntry)(using setup.executionContext)).get.getLong("cncf_revision") shouldBe Some(1L)
        }
      }
    }
  }
}
