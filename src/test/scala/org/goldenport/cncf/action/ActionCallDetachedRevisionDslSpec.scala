package org.goldenport.cncf.action

import cats.~>
import cats.data.State
import cats.effect.Ref
import cats.syntax.all.*
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{
  DataStoreContext,
  EntityStoreContext,
  ExecutionContext,
  ObservabilityContext,
  RuntimeContext,
  ScopeContext,
  ScopeKind,
  TraceId
}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityPersistent,
  EntityPersistentUpdate,
  EntityRevisionCarrier,
  EntityStore,
  EntityStoreSpace
}
import org.goldenport.cncf.entity.runtime.{
  EntityCollection,
  EntityDescriptor,
  EntityLoader,
  EntityMemoryPolicy,
  EntityRealm,
  EntityRealmState,
  EntityRuntimePlan,
  EntityStorage,
  PartitionStrategy
}
import org.goldenport.cncf.unitofwork.{
  ExecUowM,
  UnitOfWork,
  UnitOfWorkOp
}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 25, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class ActionCallDetachedRevisionDslSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  "ActionCall detached revision DSL" should {
    "construct only explicit detached UnitOfWork operations" in {
      Given(
        "Phase 50 SE-05; one caller-side program using detached load, save, typed update, and patch update"
      )
      val capture = new OperationCapture
      val call = new DetachedRevisionCall(_core(capture))

      When("the FunctionalActionCall program is interpreted")
      val result = call.execute()

      Then(
        "all four DSL boundaries preserve the external carrier and expected revision"
      )
      result shouldBe a[Consequence.Success[?]]
      capture.operations shouldBe Vector(
        "load-detached",
        "save-detached",
        "update-detached",
        "update-by-id-detached"
      )
      capture.expectedRevisions.map(_.map(_.value)) shouldBe
        Vector(None, Some(1L), Some(2L), Some(3L))
    }

    "preserve the caller codec for managed save" in {
      Given(
        "a caller codec and a different runtime collection codec for the same Entity collection"
      )
      val capture = new OperationCapture
      val component = _runtime_component()
      val call =
        new ManagedSaveCall(_core(capture, Some(component)))

      When("the caller builds and executes a managed save program")
      val result = call.execute()

      Then(
        "the UnitOfWork operation retains the caller codec instead of substituting the runtime codec"
      )
      result shouldBe a[Consequence.Success[?]]
      capture.managedSavePersistent shouldBe Some(_persistent)
    }
  }

  private val _collection_id =
    EntityCollectionId("test", "detached", "action_dsl")
  private val _id =
    EntityId("test", "detached_action", _collection_id)

  private final case class DetachedEntity(
    id: EntityId,
    name: String
  )

  private final case class DetachedPatch(
    name: Update[String]
  )

  private val _persistent: EntityPersistent[DetachedEntity] =
    new EntityPersistent[DetachedEntity] {
      def id(entity: DetachedEntity): EntityId =
        entity.id

      def toRecord(entity: DetachedEntity): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "name" -> entity.name
        )

      def fromRecord(record: Record): Consequence[DetachedEntity] =
        for {
          id <- record
            .getAs[EntityId]("id")
            .map(Consequence.success)
            .getOrElse(Consequence.argumentMissing("id"))
          name <- record
            .getString("name")
            .map(Consequence.success)
            .getOrElse(Consequence.argumentMissing("name"))
        } yield DetachedEntity(id, name)
    }

  private val _patch_persistent: EntityPersistentUpdate[DetachedPatch] =
    new EntityPersistentUpdate[DetachedPatch] {
      def collection(entity: DetachedPatch): EntityCollectionId =
        _collection_id

      def toRecord(entity: DetachedPatch): Record =
        Record.dataAuto("name" -> entity.name)

      def fromRecord(record: Record): Consequence[DetachedPatch] =
        Consequence.argumentInvalid(
          "DetachedPatch decoding is not used"
        )
    }

  private final class DetachedRevisionCall(
    val core: ActionCall.Core
  ) extends FunctionalActionCall
      with ActionCall.Core.Holder {
    protected def build_Program: ExecUowM[OperationResponse] =
      for {
        loaded <- entity_load_detached[DetachedEntity](_id)(using
          _persistent
        )
        saved <- entity_save_detached(
          loaded.entity.copy(name = "saved"),
          loaded.revision
        )(using _persistent)
        updated <- entity_update_detached(
          saved.entity.copy(name = "updated"),
          saved.revision
        )(using _persistent)
        _ <- entity_update_detached(
          _id,
          DetachedPatch(Update.set("patched")),
          updated.revision
        )(using _patch_persistent)
      } yield OperationResponse.Void()
  }

  private final class ManagedSaveCall(
    val core: ActionCall.Core
  ) extends FunctionalActionCall
      with ActionCall.Core.Holder {
    protected def build_Program: ExecUowM[OperationResponse] =
      entity_save_managed(
        DetachedEntity(_id, "managed")
      )(using _persistent).map(_ => OperationResponse.Void())
  }

  private final class OperationCapture {
    private var _operations: Vector[String] =
      Vector.empty
    private var _expected_revisions: Vector[Option[EntityRevision]] =
      Vector.empty
    private var _managed_save_persistent: Option[EntityPersistent[?]] =
      None

    def operations: Vector[String] =
      _operations

    def expectedRevisions: Vector[Option[EntityRevision]] =
      _expected_revisions

    def managedSavePersistent: Option[EntityPersistent[?]] =
      _managed_save_persistent

    def interpreter: UnitOfWorkOp ~> Consequence =
      new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = {
          val result: Consequence[?] = operation match {
            case _: UnitOfWorkOp.EntityStoreLoadDetached[?] =>
              _record("load-detached", None)
              Consequence.success(
                Some(
                  EntityRevisionCarrier(
                    DetachedEntity(_id, "loaded"),
                    EntityRevision.INITIAL
                  )
                )
              )
            case value: UnitOfWorkOp.EntityStoreSaveDetached[?] =>
              _record("save-detached", value.expectedRevision)
              Consequence.success(
                EntityRevisionCarrier(
                  DetachedEntity(_id, "saved"),
                  _revision(2L)
                )
              )
            case value: UnitOfWorkOp.EntityStoreUpdateDetached[?] =>
              _record("update-detached", value.expectedRevision)
              Consequence.success(
                EntityRevisionCarrier(
                  DetachedEntity(_id, "updated"),
                  _revision(3L)
                )
              )
            case value: UnitOfWorkOp.EntityStoreUpdateByIdDetached[?] =>
              _record("update-by-id-detached", value.expectedRevision)
              Consequence.success(
                EntityRevisionCarrier(
                  Record.dataAuto("id" -> _id, "name" -> "patched"),
                  _revision(4L)
                )
              )
            case value: UnitOfWorkOp.EntityStoreSaveManaged[?] =>
              _managed_save_persistent = Some(value.tc)
              Consequence.success(value.entity)
            case other =>
              Consequence.operationInvalid(
                s"unexpected UnitOfWork operation: ${other.getClass.getName}"
              )
          }
          result.asInstanceOf[Consequence[A]]
        }
      }

    private def _record(
      operation: String,
      expectedrevision: Option[EntityRevision]
    ): Unit = {
      _operations = _operations :+ operation
      _expected_revisions = _expected_revisions :+ expectedrevision
    }
  }

  private def _core(
    capture: OperationCapture,
    component: Option[Component] = None
  ): ActionCall.Core = {
    val datastorespace = DataStoreSpace.default()
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val observability = ObservabilityContext(
      traceId = TraceId("test", "action_call_detached_revision"),
      spanId = None,
      correlationId = None
    )
    lazy val context: ExecutionContext =
      ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "action-call-detached-revision",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = None,
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitofworksupplier = () => new UnitOfWork(context),
      unitofworkinterpreterfn = capture.interpreter,
      commitaction = _ => (),
      abortaction = _ => (),
      disposeaction = _ => (),
      token = "action-call-detached-revision"
    )
    val action = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall =
        throw new UnsupportedOperationException("not used")

      override def request: Request =
        Request(
          component = Some("detached-revision"),
          service = None,
          operation = "detached_revision",
          arguments = Nil,
          switches = Nil,
          properties = Nil
        )
    }
    ActionCall.Core(action, context, component, None)
  }

  private def _runtime_component(): Component = {
    val component = new Component() {}
    val runtimepersistent = new EntityPersistent[DetachedEntity] {
      def id(entity: DetachedEntity): EntityId =
        throw new IllegalStateException(
          s"runtime codec must not inspect caller Entity: ${entity.name}"
        )

      def toRecord(entity: DetachedEntity): Record =
        _persistent.toRecord(entity)

      def fromRecord(record: Record): Consequence[DetachedEntity] =
        _persistent.fromRecord(record)
    }
    given EntityPersistent[DetachedEntity] = runtimepersistent
    val realm = new EntityRealm[DetachedEntity](
      entityName = _collection_id.name,
      loader = EntityLoader[DetachedEntity](_ => None),
      state = new IdRef(EntityRealmState(Map.empty))
    )
    val collection = new EntityCollection[DetachedEntity](
      EntityDescriptor(
        _collection_id,
        EntityRuntimePlan(
          entityName = _collection_id.name,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 1,
          maxEntitiesPerPartition = 1,
          concurrencyPolicy = EntityConcurrencyPolicy.None
        ),
        runtimepersistent
      ),
      EntityStorage(realm)
    )
    component.entitySpace.registerEntity(_collection_id.name, collection)
    component
  }

  private def _revision(
    value: Long
  ): EntityRevision =
    EntityRevision
      .createC(value)
      .toOption
      .getOrElse(
        throw new IllegalArgumentException(
          s"invalid test Entity revision: $value"
        )
      )

  private final class IdRef[A](
    initial: A
  ) extends Ref[cats.Id, A] {
    private var _value: A = initial

    def get: A = synchronized(_value)

    def set(value: A): Unit = synchronized {
      _value = value
    }

    override def getAndSet(value: A): A = synchronized {
      val previous = _value
      _value = value
      previous
    }

    def access: (A, A => Boolean) = synchronized {
      val snapshot = _value
      val setter: A => Boolean = (next: A) =>
        synchronized {
          if (_value == snapshot) {
            _value = next
            true
          } else {
            false
          }
        }
      snapshot -> setter
    }

    def tryUpdate(f: A => A): Boolean = synchronized {
      _value = f(_value)
      true
    }

    def tryModify[B](f: A => (A, B)): Option[B] = synchronized {
      val (next, result) = f(_value)
      _value = next
      Some(result)
    }

    def update(f: A => A): Unit = synchronized {
      _value = f(_value)
    }

    def modify[B](f: A => (A, B)): B = synchronized {
      val (next, result) = f(_value)
      _value = next
      result
    }

    def tryModifyState[B](state: State[A, B]): Option[B] =
      tryModify(value => state.run(value).value)

    def modifyState[B](state: State[A, B]): B =
      modify(value => state.run(value).value)
  }
}
