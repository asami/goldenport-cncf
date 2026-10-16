package org.goldenport.cncf.entity

import cats.free.Free
import javax.sql.DataSource
import java.sql.Connection
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore, EntityCreateCheckpoint, EntityCreatePlan, EntityVersionedMutationFailure, EntityVersionedSideEffect}
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqlDialectDriver, SqliteDialectDriver}
import org.goldenport.cncf.unitofwork.*
import org.goldenport.observation.Taxonomy
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Update
import org.sqlite.{SQLiteConfig, SQLiteDataSource}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
private[cncf] object EntityCreateFailureSafetyFixture {
  import EntityObservedDeleteCanonicalFixture.{CanonicalId, Fixture, Snapshot, overflowCollection, success}

  enum BodyMode {
    case Overflow, Inline, Absent, SetNull
  }
  enum FaultMode {
    case RootCreate, SideSave, BeforePublish, ThrowSide, ThrowRoot, ThrowPublish, CommitReject, CommitAckLoss,
      CommitAckThrow, CommitAckNull
  }
  final case class Input(identity: Option[EntityId], record: Record)
  final case class StoredRow(identity: EntityId, record: Record)
  final case class FullSnapshot(root: Option[Map[String, Any]], overflow: Option[Map[String, Any]],
    preexisting: Snapshot)
  final case class FaultTarget(rootcollection: DataStore.CollectionId, rootentry: DataStore.EntryId,
    sidecollection: DataStore.CollectionId, sideentry: DataStore.EntryId, mode: FaultMode)
  final case class Sample(representation: EntityRevisionRepresentation, oldrevision: Long,
    mode: BodyMode, content: String)

  def taxonomy(result: Consequence[?]): Option[Taxonomy] = result match {
    case Consequence.Failure(conclusion) => Some(conclusion.observation.taxonomy)
    case _ => None
  }
  def duplicateTaxonomy(identity: EntityId): Option[Taxonomy] =
    taxonomy(Consequence.DataStoreDuplicate(DataStore.EntryId(identity).print))

  final class MemoryStore extends EntityObservedDeleteCanonicalProviderFixture.MemoryStore() {
    private var _target: Option[FaultTarget] = None
    private var _root_creates = 0
    private var _side_saves = 0
    def rootCreates: Int = _root_creates
    def sideSaves: Int = _side_saves
    def arm(target: FaultTarget): Unit = {
      _target = Some(target)
      _root_creates = 0
      _side_saves = 0
    }
    override protected def create_EntityCheckpoint(plan: EntityCreatePlan, checkpoint: EntityCreateCheckpoint): Consequence[Unit] = {
      val selected = _target.filter(target => target.rootcollection == plan.collection && target.rootentry == plan.entryId)
      checkpoint match {
        case EntityCreateCheckpoint.BeforeSideWrite(save: EntityVersionedSideEffect.Save)
            if selected.exists(target => target.sidecollection == save.collection && target.sideentry == save.entryId) =>
          _side_saves += 1
          _checkpoint_failure(selected.map(_.mode), FaultMode.SideSave, FaultMode.ThrowSide, "create-failure-safety-side-before-write")
        case EntityCreateCheckpoint.BeforeRootWrite if selected.nonEmpty =>
          _root_creates += 1
          _checkpoint_failure(selected.map(_.mode), FaultMode.RootCreate, FaultMode.ThrowRoot, "create-failure-safety-root-before-write")
        case EntityCreateCheckpoint.BeforePublish if selected.nonEmpty =>
          _checkpoint_failure(selected.map(_.mode), FaultMode.BeforePublish, FaultMode.ThrowPublish, "create-failure-safety-before-publish")
        case _ => Consequence.unit
      }
    }
  }

  class SqlStore(dialect: SqlDialectDriver, dataSource: DataSource, config: SqlDataStore.Config = SqlDataStore.Config())
      extends SqlDataStore(dialect, dataSource, config = config) {
    private var _target: Option[FaultTarget] = None
    private var _root_creates = 0
    private var _side_saves = 0
    private var _commit_attempts = 0
    def rootCreates: Int = _root_creates
    def sideSaves: Int = _side_saves
    def commitAttempts: Int = _commit_attempts
    def arm(target: FaultTarget): Unit = {
      _target = Some(target)
      _root_creates = 0
      _side_saves = 0
      _commit_attempts = 0
    }
    override protected def create_EntityCheckpoint(plan: EntityCreatePlan, checkpoint: EntityCreateCheckpoint): Consequence[Unit] = {
      val selected = _target.filter(target => target.rootcollection == plan.collection && target.rootentry == plan.entryId)
      checkpoint match {
        case EntityCreateCheckpoint.BeforeSideWrite(save: EntityVersionedSideEffect.Save)
            if selected.exists(target => target.sidecollection == save.collection && target.sideentry == save.entryId) =>
          _side_saves += 1
          _checkpoint_failure(selected.map(_.mode), FaultMode.SideSave, FaultMode.ThrowSide, "create-failure-safety-side-before-write")
        case EntityCreateCheckpoint.BeforeRootWrite if selected.nonEmpty =>
          _root_creates += 1
          _checkpoint_failure(selected.map(_.mode), FaultMode.RootCreate, FaultMode.ThrowRoot, "create-failure-safety-root-before-write")
        case EntityCreateCheckpoint.BeforePublish if selected.nonEmpty =>
          _checkpoint_failure(selected.map(_.mode), FaultMode.BeforePublish, FaultMode.ThrowPublish, "create-failure-safety-before-publish")
        case _ => Consequence.unit
      }
    }
    override protected def versioned_mutation_commit(connection: Connection): Consequence[Unit] = {
      _commit_attempts += 1
      _target.map(_.mode) match {
        case Some(FaultMode.CommitReject) => EntityVersionedMutationFailure.transactionFailure("create-failure-safety-commit-rejected")
        case Some(FaultMode.CommitAckLoss) =>
          connection.commit()
          EntityVersionedMutationFailure.transactionIndeterminate("create-failure-safety-acknowledgement-loss-after-real-commit")
        case Some(FaultMode.CommitAckThrow) =>
          connection.commit()
          throw new IllegalStateException("create-failure-safety-thrown-ack-after-real-commit")
        case Some(FaultMode.CommitAckNull) =>
          connection.commit()
          null
        case _ => super.versioned_mutation_commit(connection)
      }
    }
  }

  final class SqliteStore(dataSource: DataSource) extends SqlStore(SqliteDialectDriver, dataSource)

  private def _checkpoint_failure(mode: Option[FaultMode], rejected: FaultMode, thrown: FaultMode, message: String): Consequence[Unit] =
    if (mode.contains(thrown)) throw new IllegalStateException(message)
    else if (mode.contains(rejected)) EntityVersionedMutationFailure.providerFailure(message)
    else Consequence.unit

  def withSqlite[A](body: (EntityObservedDeleteCanonicalProviderFixture.SqliteDatabase, SqliteStore) => A): A =
    EntityObservedDeleteCanonicalProviderFixture.withSqlite { database =>
      val config = new SQLiteConfig()
      config.setBusyTimeout(10000)
      val source = new SQLiteDataSource(config)
      source.setUrl(s"jdbc:sqlite:${database.path.toAbsolutePath}")
      val writer = new SqliteStore(source)
      try body(database, writer)
      finally success(writer.closeC())
    }

  def fixture(token: String, representation: EntityRevisionRepresentation = EntityRevisionRepresentation.Detached,
    provider: DataStore = new MemoryStore(), oldRevision: Long = 7L, denyFactory: Boolean = false): Session =
    new Session(EntityObservedDeleteCanonicalFixture.fixture(token = token, representation = representation,
      provider = provider, initialRevision = oldRevision, denyFactory = denyFactory))

  final class Session(val base: Fixture) {
    val freshId: EntityId = new CanonicalId(base.collectionId, "freshcreate")
    val persistent: EntityPersistentCreate[Input] = new EntityPersistentCreate[Input] {
      def id(entity: Input): Option[EntityId] = entity.identity
      def collection(entity: Input): EntityCollectionId = base.collectionId
      def toRecord(entity: Input): Record = entity.record
    }
    val rowPersistent: EntityPersistent[StoredRow] = new EntityPersistent[StoredRow] {
      def id(entity: StoredRow): EntityId = entity.identity
      def toRecord(entity: StoredRow): Record = entity.record
      def fromRecord(record: Record): Consequence[StoredRow] =
        record.getAs[EntityId]("id").map(identity => Consequence.success(StoredRow(identity, record)))
          .getOrElse(Consequence.argumentMissing("id"))
    }
    def seed(): Unit = base.seed()
    def input(mode: BodyMode, identity: Option[EntityId], content: String = ("new overflow content " * 400).trim): Input = {
      val row = Record.dataAuto("title" -> "Candidate")
      val candidate = mode match {
        case BodyMode.Overflow => row ++ Record.dataAuto("content" -> content)
        case BodyMode.Inline => row ++ Record.dataAuto("content" -> "small candidate")
        case BodyMode.Absent => row
        case BodyMode.SetNull => row ++ Record.dataAuto("content" -> Update.SetNull)
      }
      Input(identity, candidate)
    }
    def fault(mode: FaultMode): FaultTarget = {
      val preparation = success(ContentBodyStoragePolicy.planForVersionedSave(
        freshId, input(BodyMode.Overflow, Some(freshId)).record))
      val side = success(preparation.sideEffects.collectFirst {
        case save: EntityVersionedSideEffect.Save => Consequence.success(save)
      }.getOrElse(Consequence.argumentMissing("overflow side save")))
      FaultTarget(base.dataCollection, DataStore.EntryId(freshId), side.collection, side.entryId, mode)
    }
    def createC(input: Input): Consequence[CreateResult[Input]] =
      base.context.entityStoreSpace.create(UnitOfWorkOp.EntityStoreCreate(input, persistent))(using base.context)
    def program(input: Input, authorization: UnitOfWorkAuthorization): ExecUowM[CreateResult[Input]] =
      ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, CreateResult[Input]](
        UnitOfWorkOp.EntityStoreCreate(input, persistent, authorization = Some(authorization))))
    def evaluateC(program: ExecUowM[CreateResult[Input]]): Consequence[CreateResult[Input]] =
      new UnitOfWorkInterpreter(new UnitOfWork(base.context)).evaluateInActiveUnitOfWorkC(program)
    def snapshot(identity: EntityId, store: DataStore = base.provider): FullSnapshot = {
      def _row_(collection: DataStore.CollectionId, entry: DataStore.EntryId): Option[Map[String, Any]] =
        success(store.load(collection, entry)(using base.context)).map(_.asMap)
      FullSnapshot(_row_(base.dataCollection, DataStore.EntryId(identity)),
        _row_(overflowCollection, base.overflowEntry(identity)), base.snapshot(store))
    }
    def hydratedC(identity: EntityId): Consequence[Option[StoredRow]] =
      base.context.entityStoreSpace.load(UnitOfWorkOp.EntityStoreLoad(identity, rowPersistent))(using base.context)
  }
}
