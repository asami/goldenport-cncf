package org.goldenport.cncf.action

import cats.~>
import cats.syntax.all.*
import org.goldenport.Consequence
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
  EntityPersistent,
  EntityPersistentUpdate,
  EntityRevisionCarrier,
  EntityStore,
  EntityStoreSpace
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
 * @version Jul. 25, 2026
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

  private final class OperationCapture {
    private var _operations: Vector[String] =
      Vector.empty
    private var _expected_revisions: Vector[Option[EntityRevision]] =
      Vector.empty

    def operations: Vector[String] =
      _operations

    def expectedRevisions: Vector[Option[EntityRevision]] =
      _expected_revisions

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
    capture: OperationCapture
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
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = capture.interpreter,
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
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
    ActionCall.Core(action, context, None, None)
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
}
