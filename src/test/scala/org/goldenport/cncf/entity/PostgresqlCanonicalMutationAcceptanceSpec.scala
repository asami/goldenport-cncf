package org.goldenport.cncf.entity

import cats.free.Free
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.*
import org.goldenport.cncf.unitofwork.*
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
final class PostgresqlCanonicalMutationAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen with PostgresqlAcceptance {
  import EntityObservedDeleteCanonicalFixture.{body, fixture, reason, success}

  "Native PostgreSQL canonical Entity mutation" should {
    "preserve admitted updates, hydration and authoritative policy results" which {
      Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
        s"PG-E5 update the actual $representation root and overflow then preserve no-op and stale outcomes" in {
          Given("a registered canonical collection with its real revision binding and complete native root/body pair")
          with_postgresql { database =>
            val writer = database.open()
            val reader = database.open()
            val setup = fixture(database.token(s"pg_canonical_update_$representation"), representation = representation, provider = writer)
            seedCanonical(new EntityCreateFailureSafetyFixture.Session(setup), preserveDeletionPolicy = true)
            val before = setup.snapshot(reader)
            val beforeroot = success(reader.load(setup.dataCollection, DataStore.EntryId(setup.id))(using setup.context)).get
            val content = "changed canonical overflow 日 " * 400
            When("canonical observed update prepares and commits its changed root/body through native persistence")
            val result = _update(setup, representation, Record.dataAuto("title" -> "After", "content" -> content), EntityRevision.INITIAL)
            val after = setup.snapshot(reader)
            val afterroot = success(reader.load(setup.dataCollection, DataStore.EntryId(setup.id))(using setup.context)).get
            val hydrated = success(new EntityCreateFailureSafetyFixture.Session(setup).hydratedC(setup.id))
            Then("the actual canonical result, revision, hydration and complete unrelated records correspond")
            result shouldBe a[Consequence.Success[?]]
            success(EntityRevision.createC(after.root.get(setup.revisionField))).value shouldBe 2L
            after.root.get("title") shouldBe "After"
            beforeroot.getString("name").isDefined shouldBe true
            afterroot.fields.filter(_.key == "name") shouldBe beforeroot.fields.filter(_.key == "name")
            afterroot.fields.count(_.key == "name") shouldBe 1
            hydrated.flatMap(_.record.getString("content")) shouldBe Some(content)
            after.unrelatedRoot shouldBe before.unrelatedRoot
            after.unrelatedOverflow shouldBe before.unrelatedOverflow
            And("canonical no-op and stale observed attempts preserve the complete committed state")
            val committed = database.snapshotAll()
            _update(setup, representation, Record.dataAuto("title" -> "After", "content" -> content), revision(2)) shouldBe a[Consequence.Success[?]]
            database.snapshotAll() shouldBe committed
            val stale = _update(setup, representation, Record.dataAuto("title" -> "After", "content" -> content), revision(1))
            reason(stale) shouldBe Some("stale-entity-revision")
            database.snapshotAll() shouldBe committed
          }
        }
      }
      "PG-E5 preserve actual native persistence and resident/View state after update authorization denial" in {
        Given("the actual canonical factory denying a UserPermission update on primed resident and View state")
        with_postgresql { database =>
          val setup = fixture(database.token("pg_update_denied"), provider = database.open(), denyFactory = true)
          seedCanonical(new EntityCreateFailureSafetyFixture.Session(setup), preserveDeletionPolicy = true)
          setup.primeResident()
          val resident = setup.collection.resolveScoped(setup.id)(using setup.context)
          val view = setup.view.resolve(setup.id)
          val builds = setup.viewBuilds
          val before = database.snapshotAll()
          val persistent = _persistent(setup)
          val grant = setup.authorization().copy(accessKind = "update")
          val operation = UnitOfWorkOp.EntityStoreUpdateByIdObserved(setup.id,
            Record.dataAuto("title" -> "Denied"), EntityRevision.INITIAL, persistent, authorization = Some(grant))
          val program = ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, EntityRecordSnapshot](operation))
          When("the active UnitOfWork interpreter evaluates the ordinary typed canonical update")
          val result = new UnitOfWorkInterpreter(new UnitOfWork(setup.context)).evaluateInActiveUnitOfWorkC(program)
          Then("the original authorization result preserves all selected/unrelated data and caches")
          EntityCreateFailureSafetyFixture.taxonomy(result) shouldBe EntityCreateFailureSafetyFixture.taxonomy(Consequence.securityPermissionDenied("canonical-delete-factory-denied"))
          setup.factoryCalls shouldBe Vector(grant)
          database.snapshotAll() shouldBe before
          setup.collection.resolveScoped(setup.id)(using setup.context) shouldBe resident
          setup.view.resolve(setup.id) shouldBe view
          setup.viewBuilds shouldBe builds
        }
      }
    }

    "execute canonical observed deletion with actual native root/body lifetime" which {
      for {
        representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
        logical <- Vector(false, true)
      } {
        s"PG-E6 delete $representation with logical policy $logical and the matched prior revision" in {
          Given("an actual canonical collection, full native rows, primed resident state and a built View")
          with_postgresql { database =>
            val writer = database.open()
            val reader = database.open()
            val setup = fixture(database.token(s"pg_delete_${representation}_$logical"), logical = logical,
              representation = representation, provider = writer, initialRevision = 7L)
            seedCanonical(new EntityCreateFailureSafetyFixture.Session(setup), preserveDeletionPolicy = true)
            setup.primeResident()
            success(setup.view.resolve(setup.id))
            val before = setup.snapshot(reader)
            When("the actual active canonical UnitOfWork authorizes and executes strict observed deletion")
            val deleted = success(setup.evaluateC(setup.program(revision(7), Some(setup.authorization()))))
            val after = setup.snapshot(reader)
            val visible = success(setup.visibleC)
            Then("the receipt identifies the actual prior match, normal reads hide the target and unrelated state is retained")
            deleted.id shouldBe setup.id
            deleted.matchedRevision shouldBe revision(7)
            if (logical) {
              success(EntityRevision.createC(after.root.get(setup.revisionField))).value shouldBe 8L
              after.overflow shouldBe before.overflow
              after.root.get("title") shouldBe before.root.get("title")
            } else {
              after.root shouldBe None
              after.overflow shouldBe None
            }
            after.unrelatedRoot shouldBe before.unrelatedRoot
            after.unrelatedOverflow shouldBe before.unrelatedOverflow
            success(setup.loadC()) shouldBe None
            visible.map(_.id) should not contain setup.id
            setup.collection.resolveScoped(setup.id)(using setup.context) shouldBe a[Consequence.Failure[?]]
            val committed = database.snapshotAll()
            setup.deleteC(revision(7)) shouldBe a[Consequence.Failure[?]]
            database.snapshotAll() shouldBe committed
          }
        }
      }
      for {
        representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
        rejected <- Vector("stale", "exhausted", "malformed", "missing", "excluded", "permission", "precommit")
      } {
        s"PG-E6 preserve complete $representation state after $rejected observed deletion" in {
          Given("a actual native canonical pair with the selected prepublication admission or checkpoint rejection")
          with_postgresql { database =>
            val writer = database.openAcknowledgement()
            val setup = fixture(database.token(s"pg_delete_rejected_${representation}_$rejected"), representation = representation,
              provider = writer, initialRevision = (if (rejected == "exhausted") Long.MaxValue else 1L), denyFactory = rejected == "permission")
            seedCanonical(new EntityCreateFailureSafetyFixture.Session(setup), preserveDeletionPolicy = true)
            if (rejected == "stale") success(setup.updateC(expected = revision(1)))
            if (rejected == "malformed") {
              database.statement(s"ALTER TABLE ${quote(setup.dataCollection.collectionName)} ALTER COLUMN ${quote(setup.revisionField)} TYPE TEXT USING ${quote(setup.revisionField)}::text")
              database.statement(s"UPDATE ${quote(setup.dataCollection.collectionName)} SET ${quote(setup.revisionField)}='malformed' WHERE ${quote("id")}=${_literal(DataStore.EntryId(setup.id).print)}")
            }
            if (rejected == "missing") database.statement(s"ALTER TABLE ${quote(setup.dataCollection.collectionName)} DROP COLUMN ${quote(setup.revisionField)}")
            if (rejected == "excluded") database.statement(s"ALTER TABLE ${quote(setup.dataCollection.collectionName)} ADD COLUMN ${quote("aliveness")} TEXT; UPDATE ${quote(setup.dataCollection.collectionName)} SET ${quote("aliveness")}='dead' WHERE ${quote("id")}=${_literal(DataStore.EntryId(setup.id).print)}")
            if (rejected == "precommit") writer.arm(AcknowledgementMode.RejectVersioned)
            val before = database.snapshotAll()
            When("the actual canonical observed deletion is interpreted through its native provider")
            val result = if (rejected == "permission") setup.evaluateC(setup.program(revision(1), Some(setup.authorization())))
              else setup.deleteC(if (rejected == "exhausted") revision(Long.MaxValue) else revision(1))
            Then("the typed failure preserves all actual root/body, schema and unrelated fields")
            result shouldBe a[Consequence.Failure[?]]
            if (rejected == "stale") reason(result) shouldBe Some("stale-entity-revision")
            if (rejected == "precommit") reason(result) shouldBe Some("provider-failure")
            database.snapshotAll() shouldBe before
          }
        }
      }
      "PG-E6 preserve resident and View state after actual delete permission denial" in {
        Given("a native canonical pair with real resident/View caches and denying factory")
        with_postgresql { database =>
          val setup = fixture(database.token("pg_delete_cache_denied"), provider = database.open(), denyFactory = true)
          seedCanonical(new EntityCreateFailureSafetyFixture.Session(setup), preserveDeletionPolicy = true)
          setup.primeResident()
          val resident = setup.collection.resolveScoped(setup.id)(using setup.context)
          val view = setup.view.resolve(setup.id)
          val builds = setup.viewBuilds
          val before = database.snapshotAll()
          When("the actual UserPermission deletion program is denied by its canonical owner")
          val result = setup.evaluateC(setup.program(EntityRevision.INITIAL, Some(setup.authorization())))
          Then("failure does not perform deletion or successful-delete cache invalidation")
          result shouldBe a[Consequence.Failure[?]]
          database.snapshotAll() shouldBe before
          setup.collection.resolveScoped(setup.id)(using setup.context) shouldBe resident
          setup.view.resolve(setup.id) shouldBe view
          setup.viewBuilds shouldBe builds
        }
      }
    }
  }

  private def _persistent(setup: EntityObservedDeleteCanonicalFixture.Fixture): EntityPersistentUpdate[Record] =
    new EntityPersistentUpdate[Record] {
      def collection(entity: Record) = setup.collectionId
      def toRecord(entity: Record): Record = entity
      def fromRecord(record: Record): Consequence[Record] = Consequence.success(record)
    }

  private def _update(setup: EntityObservedDeleteCanonicalFixture.Fixture, representation: EntityRevisionRepresentation,
    record: Record, expected: EntityRevision): Consequence[?] = {
    given org.goldenport.cncf.context.ExecutionContext = setup.context
    val persistent = _persistent(setup)
    val policy = EntityMutationExecutionPolicy(EntityConcurrencyPolicy.Optimistic, EntityWritePolicy.WriteIfChanged,
      RevisionPreconditionPolicy.ObservedRequired, Some(expected))
    if (representation == EntityRevisionRepresentation.Embedded)
      setup.context.entityStoreSpace.updateByIdObserved(UnitOfWorkOp.EntityStoreUpdateByIdObserved(setup.id, record, expected, persistent,
        executionPolicy = policy))
    else setup.context.entityStoreSpace.updateByIdDetached(UnitOfWorkOp.EntityStoreUpdateByIdDetached(setup.id, record,
      Some(expected), persistent, executionPolicy = policy))
  }

  private def _literal(value: String): String = "E'" + value.replace("\\", "\\\\").replace("'", "''") + "'"
}
