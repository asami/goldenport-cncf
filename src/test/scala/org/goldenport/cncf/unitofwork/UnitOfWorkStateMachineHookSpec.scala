package org.goldenport.cncf.unitofwork

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, RuntimeContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistent, EntityStore, EntityStoreSpace}
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkStateMachineHookSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _cid = EntityCollectionId("test", "sm", "person")

  "UnitOfWork transition validation hook" should {
    "invoke pre-check before update" in {
      Given("runtime context with a counting transition hook")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val hook = new _CountingHook
      val context = _execution_context(datastorespace, entitystorespace, hook)
      given ExecutionContext = context
      given EntityPersistent[PersonEntity] = _person_persistent
      val uow = new UnitOfWork(context, EventEngine.noop(DataStore.noop()))
      val id = EntityId("test", "sm_1", _cid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              PersonEntity(id, "taro", 20).toRecord()
            )
          )
        )
      )
      val entity = PersonEntity(id, "taro", 21)

      When("updating entity through UnitOfWork interpreter")
      val result = new UnitOfWorkInterpreter(uow).execute(
        UnitOfWorkOp.EntityStoreUpdate(entity, summon[EntityPersistent[PersonEntity]])
      )

      Then("pre-check is invoked once and update succeeds")
      result shouldBe ()
      hook.beforeUpdateCount shouldBe 1
    }

    "block update when pre-check fails" in {
      Given("runtime context with a rejecting transition hook")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val hook = new _RejectingUpdateHook
      val context = _execution_context(datastorespace, entitystorespace, hook)
      given ExecutionContext = context
      given EntityPersistent[PersonEntity] = _person_persistent
      val id = EntityId("test", "sm_2", _cid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              PersonEntity(id, "hanako", 30).toRecord()
            )
          )
        )
      )
      val uow = new UnitOfWork(context, EventEngine.noop(DataStore.noop()))

      When("updating entity through UnitOfWork interpreter")
      val result = new UnitOfWorkInterpreter(uow).run(
        org.goldenport.ConsequenceT.liftF(
          cats.free.Free.liftF[UnitOfWorkOp, Unit](
            UnitOfWorkOp.EntityStoreUpdate(
              PersonEntity(id, "hanako", 31),
              summon[EntityPersistent[PersonEntity]]
            )
          )
        )
      )

      Then("update fails before mutation and existing record stays unchanged")
      result shouldBe a[Consequence.Failure[_]]

      val loadedAge = for {
        cid <- context.entityStoreSpace.dataStoreCollection(id)
        dsid <- context.entityStoreSpace.dataStoreEntryId(id)
        ds <- context.dataStoreSpace.dataStore(cid)
        rec <- ds.load(cid, dsid)
      } yield rec.flatMap(_.asMap.get("age").collect {
        case n: Int => n
      })

      loadedAge shouldBe Consequence.success(Some(30))
    }
  }

  private def _execution_context(
    datastorespace: DataStoreSpace,
    entitystorespace: EntityStoreSpace,
    hook: TransitionValidationHook
  ): ExecutionContext = {
    val observability = ObservabilityContext(
      traceId = TraceId("test", "runtime"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "runtime"))
    )
    val driver = FakeHttpDriver.okText("nop")
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "uow-statemachine-hook-spec-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver),
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in test context")
      },
      commitAction = uow => {
        val _ = uow.commit()
        ()
      },
      abortAction = uow => {
        val _ = uow.rollback()
        ()
      },
      disposeAction = _ => (),
      token = "uow-statemachine-hook-spec-runtime",
      transitionValidationHook = hook
    )
    ExecutionContext.withRuntimeContext(ExecutionContext.create(), runtime)
  }

  private final case class PersonEntity(
    id: EntityId,
    name: String,
    age: Int
  ) {
    def toRecord(): Record =
      Record.dataAuto(
        "id" -> id,
        "name" -> name,
        "age" -> age
      )
  }

  private val _person_persistent: EntityPersistent[PersonEntity] = new EntityPersistent[PersonEntity] {
    def id(e: PersonEntity): EntityId = e.id
    def toRecord(e: PersonEntity): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[PersonEntity] = {
      val m = r.asMap
      (m.get("id"), m.get("name"), m.get("age")) match {
        case (Some(id: EntityId), Some(name: String), Some(age: Int)) =>
          Consequence.success(PersonEntity(id, name, age))
        case _ =>
          Consequence.failure("invalid person record")
      }
    }
  }

  private final class _CountingHook extends TransitionValidationHook {
    private var _before_update_count = 0
    def beforeUpdateCount: Int = _before_update_count

    def beforeSave[T](
      entity: T,
      tc: org.goldenport.cncf.entity.EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      Consequence.unit
    }

    def beforeUpdate[T](
      entity: T,
      tc: org.goldenport.cncf.entity.EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      _before_update_count = _before_update_count + 1
      Consequence.unit
    }

    def beforeUpdateById[P](
      id: EntityId,
      patch: P,
      tc: org.goldenport.cncf.entity.EntityPersistentUpdate[P]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (id, patch, tc)
      Consequence.unit
    }
  }

  private final class _RejectingUpdateHook extends TransitionValidationHook {
    def beforeSave[T](
      entity: T,
      tc: org.goldenport.cncf.entity.EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      Consequence.unit
    }

    def beforeUpdate[T](
      entity: T,
      tc: org.goldenport.cncf.entity.EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      Consequence.failure("transition pre-check failed")
    }

    def beforeUpdateById[P](
      id: EntityId,
      patch: P,
      tc: org.goldenport.cncf.entity.EntityPersistentUpdate[P]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (id, patch, tc)
      Consequence.unit
    }
  }
}
