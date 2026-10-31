package org.goldenport.cncf.datastore

import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.EntityCreateProviderFixture.race
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.*
import org.goldenport.cncf.datastore.sql.{PostgreSqlDialectDriver, SqlDataStore}
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalFixture.{reason, success}
import org.goldenport.record.{Field, Record}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 31, 2026
 */
@org.scalatest.DoNotDiscover
final class PostgresqlConditionalTransitionAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen with PostgresqlAcceptance {
  "Native PostgreSQL conditional lifecycle" should {
    "retain the existing two-guard Update capability" which {
      for (normalize <- Vector(false, true); mode <- Vector("success", "revision", "field")) {
        s"map and prepare the complete second guard for $mode with normalization=$normalize" in {
          Given("docs/spec/entity-conflict-and-conditional-transition.md: the existing conditional-update contract and two native roots with independently fixed revision and field guards")
          with_postgresql { database =>
            val writer = database.open(normalizeColumns = normalize)
            val reader = database.open(normalizeColumns = normalize)
            val setup = new MutationSession(database.token("merged_update"), writer)
            setup.seed()
            success(writer.create(setup.successorCollection, setup.successorEntry,
              Record.dataAuto("title" -> "Before second", "acceptState" -> (if (mode == "field") "blocked" else "ready"),
                "nextTitle" -> "Before next", "cncf_revision" -> (if (mode == "revision") 2L else 1L)))(using setup.executionContext))
            val second = success(DataStoreConditionalRoot.create(setup.componentOwner, setup.successorCollection,
              setup.successorEntry, "cncf_revision", Some(EntityRevision.INITIAL),
              Vector(DataStoreConditionalExpectedField("acceptState", success(DataStoreConditionalValue.from("ready")))),
              Record.dataAuto("nextTitle" -> "After second"), revision(2L)))
            val base = setup.conditionalPlan()
            val plan = success(DataStoreConditionalTransitionPlan.create(base.root,
              DataStoreConditionalSuccessor.Update(second), base.sideEffects, base.correlation))
            val before = database.snapshotAll()

            When("one native transition maps both guarded roots and uses the same transaction")
            val result = writer.conditionalTransition(plan)(using setup.executionContext)

            Then("both acknowledged revisions and complete independent state obey the existing Update or conflict contract")
            if (mode == "success") {
              val changed = success(result).asInstanceOf[DataStoreConditionalTransitionResult.Transitioned]
              changed.rootRecord.asMap shouldBe setup.load(reader).asMap
              changed.successorRecord.asMap shouldBe success(reader.load(setup.successorCollection, setup.successorEntry)(using setup.executionContext)).get.asMap
              changed.rootRecord.getLong("cncf_revision") shouldBe Some(2L)
              changed.successorRecord.getLong("cncf_revision") shouldBe Some(2L)
              val rootexpected = _updated(before, setup.rootCollection, setup.rootEntry,
                Map("status" -> "closed", "successor_id" -> setup.successorEntry.print, "cncf_revision" -> 2L))
              val secondexpected = _updated(rootexpected, setup.successorCollection, setup.successorEntry,
                Map((if (normalize) "next_title" else "nextTitle") -> "After second", "cncf_revision" -> 2L))
              database.snapshotAll() shouldBe _updated(secondexpected, setup.sideCollection, setup.sideEntry,
                Map("content" -> "Changed complete body"))
            } else {
              reason(result) shouldBe Some("updated-successor-state-conflict")
              database.snapshotAll() shouldBe before
            }
          }
        }
      }
    }

    "retain ordinary native side Create without expanding strict create" which {
      Vector("versioned", "conditional").foreach { route =>
        s"commit exact Create Save Delete state through the $route route" in {
          Given("docs/spec/entity-conflict-and-conditional-transition.md R12: ordinary side effects and a native root with complete mutable, removable and unrelated rows")
          with_postgresql { database =>
            val writer = database.open()
            val setup = new MutationSession(database.token("merged_side_create"), writer)
            setup.seed()
            val created = DataStore.StringEntryId("created")
            val removed = DataStore.StringEntryId("remove-me")
            success(writer.create(setup.sideCollection, removed, Record.dataAuto("content" -> "Remove this"))(using setup.executionContext))
            val effects = Vector(EntityVersionedSideEffect.Create(setup.sideCollection, created, Record.dataAuto("content" -> "Immutable new body")),
              setup.sideEffect(), EntityVersionedSideEffect.Delete(setup.sideCollection, removed))
            val base = setup.conditionalPlan()
            val conditional = success(DataStoreConditionalTransitionPlan.create(base.root, base.successor, effects))
            val versioned = versionedPlan(setup.rootCollection, setup.rootEntry, Record.dataAuto("title" -> "After"), sideEffects = effects)
            val before = database.snapshotAll()

            When("the ordinary guarded native operation executes the three existing effect kinds once")
            val result = if (route == "conditional") writer.conditionalTransition(conditional)(using setup.executionContext)
              else writer.mutateVersionedEntity(versioned)(using setup.executionContext)

            Then("the authoritative result and entire independent database match the separately prescribed complete next state")
            val reader = database.open()
            result match {
              case Consequence.Success(value: DataStoreConditionalTransitionResult.Transitioned) =>
                value.rootRecord.asMap shouldBe setup.load(reader).asMap
                value.successorRecord.asMap shouldBe success(reader.load(setup.successorCollection, setup.successorEntry)(using setup.executionContext)).get.asMap
              case Consequence.Success(EntityVersionedMutationResult.Applied(record)) => record.asMap shouldBe setup.load(reader).asMap
              case other => fail(s"Expected native guarded publication: $other")
            }
            val rootchanges: Map[String, Any] = if (route == "conditional")
              Map("status" -> "closed", "successor_id" -> setup.successorEntry.print, "cncf_revision" -> 2L)
              else Map("title" -> "After", "cncf_revision" -> 2L)
            val rootexpected = _updated(before, setup.rootCollection, setup.rootEntry, rootchanges)
            val bodyexpected = _updated(rootexpected, setup.sideCollection, setup.sideEntry, Map("content" -> "Changed complete body"))
            val sidekey = "public" -> setup.sideCollection.collectionName
            val sidetable = bodyexpected(sidekey)
            val sideexpected = bodyexpected.updated(sidekey, sidetable.copy(rows =
              (sidetable.rows.filterNot(_("id") == removed.print) :+
                Map[String, Any]("id" -> created.print, "content" -> "Immutable new body")).sortBy(_("id").toString)))
            val expected = if (route == "conditional") {
              val key = "public" -> setup.successorCollection.collectionName
              val table = sideexpected(key)
              sideexpected.updated(key, table.copy(rows = (table.rows :+
                Map[String, Any]("id" -> setup.successorEntry.print, "title" -> "Successor", "cncf_revision" -> 1L)).sortBy(_("id").toString)))
            } else sideexpected
            database.snapshotAll() shouldBe expected
          }
        }

        s"roll back every earlier native effect on a duplicate side Create through $route" in {
          Given("docs/spec/entity-conflict-and-conditional-transition.md R12: a protected side identity and complete independent root, successor, body and removable state")
          with_postgresql { database =>
            val writer = database.open()
            val setup = new MutationSession(database.token("merged_duplicate"), writer)
            setup.seed()
            val duplicate = DataStore.StringEntryId("protected")
            val early = DataStore.StringEntryId("early")
            val removed = DataStore.StringEntryId("remove-me")
            success(writer.create(setup.sideCollection, duplicate, Record.dataAuto("content" -> "Protected"))(using setup.executionContext))
            success(writer.create(setup.sideCollection, removed, Record.dataAuto("content" -> "Retained"))(using setup.executionContext))
            val effects = Vector(EntityVersionedSideEffect.Create(setup.sideCollection, early, Record.dataAuto("content" -> "Unpublished")),
              setup.sideEffect(), EntityVersionedSideEffect.Delete(setup.sideCollection, removed),
              EntityVersionedSideEffect.Create(setup.sideCollection, duplicate, Record.dataAuto("content" -> "Must not replace")))
            val base = setup.conditionalPlan()
            val plan = success(DataStoreConditionalTransitionPlan.create(base.root, base.successor, effects))
            val before = database.snapshotAll()

            When("the actual transaction encounters the duplicate after earlier root and side work")
            val result = if (route == "conditional") writer.conditionalTransition(plan)(using setup.executionContext)
              else writer.mutateVersionedEntity(versionedPlan(setup.rootCollection, setup.rootEntry,
                Record.dataAuto("title" -> "Unpublished"), sideEffects = effects))(using setup.executionContext)

            Then("native datastore duplicate is returned and every catalog column and row equals the original snapshot")
            _check_duplicate(result, duplicate)
            database.snapshotAll() shouldBe before
          }
        }

        s"reject ambiguous Create schema before native $route checkpoints" in {
          Given("docs/spec/postgresql-phase-96-persistence.md PG8: pre-effect schema admission and distinct Record keys that alias one native normalized column")
          with_postgresql { database =>
            val checkpoints = new AtomicInteger()
            val writer = new SqlDataStore(PostgreSqlDialectDriver, database.source(),
              config = SqlDataStore.Config(normalizeColumnNames = true)) {
              override protected def versioned_mutation_checkpoint(checkpoint: EntityVersionedMutationCheckpoint): Consequence[Unit] = {
                checkpoints.incrementAndGet()
                Consequence.unit
              }
              override protected def conditional_transition_checkpoint(checkpoint: DataStoreConditionalTransitionCheckpoint): Consequence[Unit] = {
                checkpoints.incrementAndGet()
                Consequence.unit
              }
            }
            try {
              val setup = new MutationSession(database.token("merged_alias"), writer)
              setup.seed()
              val record = Record(Vector(Field("foo_bar", Field.Value.Single("one")), Field("fooBar", Field.Value.Single("two"))))
              val effects = Vector(EntityVersionedSideEffect.Create(setup.sideCollection, DataStore.StringEntryId("created"), record))
              val base = setup.conditionalPlan()
              val plan = success(DataStoreConditionalTransitionPlan.create(base.root, base.successor, effects))
              val before = database.snapshotAll()

              When("the ordinary native route admits its complete root and side records")
              val result = if (route == "conditional") writer.conditionalTransition(plan)(using setup.executionContext)
                else writer.mutateVersionedEntity(versionedPlan(setup.rootCollection, setup.rootEntry,
                  Record.dataAuto("title" -> "Unpublished"), sideEffects = effects))(using setup.executionContext)

              Then("structured alias rejection precedes every transaction checkpoint and leaves the entire native database unchanged")
              result match {
                case Consequence.Failure(conclusion) =>
                  conclusion.observation.taxonomy shouldBe Consequence.argumentInvalid(
                    "record", "unique native SQL and decoded Record fields", "native column aliases").conclusion.observation.taxonomy
                case _ => fail("Native alias admission must return a structured argument failure")
              }
              checkpoints.get() shouldBe 0
              database.snapshotAll() shouldBe before
            } finally success(writer.closeC())
          }
        }

        s"arbitrate different native roots sharing one side Create identity through $route" in {
          Given("docs/spec/entity-conflict-and-conditional-transition.md R12: an absence-only identity, independent native connections and two distinct guarded roots")
          with_postgresql { database =>
            val writers = Vector(database.open(), database.open())
            val setup = new MutationSession(database.token("merged_side_race"), writers.head)
            setup.seed()
            val roots = Vector(setup.rootEntry, DataStore.StringEntryId("other-root"))
            val successors = Vector(DataStore.StringEntryId("next-first"), DataStore.StringEntryId("next-second"))
            val shared = DataStore.StringEntryId("shared-created")
            success(writers.head.create(setup.rootCollection, roots(1), Record.dataAuto("title" -> "Second before", "status" -> "open",
              "cncf_revision" -> 1L, "body_id" -> setup.sideEntry.print, "aliveness" -> "alive", "successor_id" -> ""))(using setup.executionContext))
            val before = database.snapshotAll()
            def _attempt_(index: Int): Consequence[Any] = {
              val writer = writers(index)
              val effects = Vector(EntityVersionedSideEffect.Create(setup.sideCollection, shared, Record.dataAuto("content" -> s"Winner $index")))
              if (route == "conditional") {
                val base = setup.conditionalPlan(rootEntryId = roots(index), successorEntryId = successors(index))
                val plan = success(DataStoreConditionalTransitionPlan.create(base.root, base.successor, effects))
                writer.conditionalTransition(plan)(using context(writer)).map(value => (value: Any))
              } else writer.mutateVersionedEntity(versionedPlan(setup.rootCollection, roots(index),
                Record.dataAuto("title" -> s"After $index"), sideEffects = effects))(using context(writer)).map(value => (value: Any))
            }

            When("the two real transactions contend without a shared caller lock or automatic retry")
            val results = race(() => _attempt_(0), () => _attempt_(1))

            Then("one whole intent wins, one native duplicate loses and complete independent state contains no losing effects")
            results.count(_.isSuccess) shouldBe 1
            val winner = results.indexWhere(_.isSuccess)
            _check_duplicate(results(1 - winner), shared)
            val rootchanges: Map[String, Any] = if (route == "conditional")
              Map("status" -> "closed", "successor_id" -> successors(winner).print, "cncf_revision" -> 2L)
              else Map("title" -> s"After $winner", "cncf_revision" -> 2L)
            val rootexpected = _updated(before, setup.rootCollection, roots(winner), rootchanges)
            val sidekey = "public" -> setup.sideCollection.collectionName
            val sidetable = rootexpected(sidekey)
            val sideexpected = rootexpected.updated(sidekey, sidetable.copy(rows = (sidetable.rows :+
              Map[String, Any]("id" -> shared.print, "content" -> s"Winner $winner")).sortBy(_("id").toString)))
            val expected = if (route == "conditional") {
              val key = "public" -> setup.successorCollection.collectionName
              val table = sideexpected(key)
              sideexpected.updated(key, table.copy(rows = (table.rows :+
                Map[String, Any]("id" -> successors(winner).print, "title" -> "Successor", "cncf_revision" -> 1L)).sortBy(_("id").toString)))
            } else sideexpected
            database.snapshotAll() shouldBe expected
          }
        }
      }
    }

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

  private def _updated(snapshot: Map[(String, String), StoredTable], collection: DataStore.CollectionId,
    entry: DataStore.EntryId, changes: Map[String, Any]): Map[(String, String), StoredTable] = {
    val key = "public" -> collection.collectionName
    val table = snapshot(key)
    table.rows.count(_("id") == entry.print) shouldBe 1
    snapshot.updated(key, table.copy(rows = table.rows.map(row => if (row("id") == entry.print) row ++ changes else row)))
  }

  private def _check_duplicate(result: Consequence[?], entry: DataStore.EntryId): Unit = result match {
    case Consequence.Failure(conclusion) =>
      conclusion.observation.taxonomy shouldBe Consequence.DataStoreDuplicate(entry.print).conclusion.observation.taxonomy
    case _ => fail("The losing native Create must return structured datastore duplicate")
  }
}
