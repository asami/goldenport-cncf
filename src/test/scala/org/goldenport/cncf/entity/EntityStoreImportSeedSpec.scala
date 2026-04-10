package org.goldenport.cncf.entity

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.context.{DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, ScopeContext, ScopeKind, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 27, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityStoreImportSeedSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val collectionId = EntityCollectionId("test", "a", "import_seed_person")

  "EntityStoreSpace.importSeed" should {
    "import entities and make them loadable through the entity-store route" in {
      Given("an in-memory entity-store space and a matching persistent codec")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[PersonEntity] = _person_persistent
      val e1 = PersonEntity(EntityId("test", "p1", collectionId), "taro")

      val seed = EntityStoreSeed(
        Vector(
          EntityStoreSeedEntry(e1)
        )
      )

      When("importing the seed")
      val imported = entitystorespace.importSeed(seed)

      Then("the entities can be loaded through the entity-store route")
      imported shouldBe Consequence.unit
      entitystorespace.load(
        UnitOfWorkOp.EntityStoreLoad(e1.id, summon[EntityPersistent[PersonEntity]])
      ) shouldBe Consequence.success(Some(e1))
    }
  }

  private def _execution_context(
    datastorespace: DataStoreSpace,
    entitystorespace: EntityStoreSpace
  ): ExecutionContext = {
    val observability = ObservabilityContext(
      traceId = TraceId("test", "seed"),
      spanId = None,
      correlationId = None
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "entity-store-import-seed-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = None,
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] = {
          val _ = fa
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in this spec")
        }
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "entity-store-import-seed-runtime-context"
    )
    val _ = context
    context
  }

  private final case class PersonEntity(
    id: EntityId,
    name: String
  ) extends EntityPersistable {
    def toRecord(): Record =
      Record.dataAuto(
        "id" -> id,
        "name" -> name
      )
  }

  private def _person_persistent: EntityPersistent[PersonEntity] =
    new EntityPersistent[PersonEntity] {
      def id(e: PersonEntity): EntityId = e.id
      def toRecord(e: PersonEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[PersonEntity] = {
        val m = r.asMap
        (m.get("id"), m.get("name")) match {
          case (Some(id: EntityId), Some(name: String)) =>
            Consequence.success(PersonEntity(id, name))
          case _ =>
            Consequence.failure("invalid person record")
        }
      }
    }
}
