package org.goldenport.cncf.datastore.sql

import java.sql.Connection
import java.sql.PreparedStatement
import javax.sql.DataSource
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.convert.StringEncodable
import org.goldenport.text.Presentable
import org.goldenport.record.Record
import org.goldenport.record.RecordKeyNaming
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{
  DataStore,
  DataStoreConditionalRoot,
  DataStoreConditionalSuccessor,
  DataStoreConditionalTransitionCheckpoint,
  DataStoreConditionalTransitionFailure,
  DataStoreConditionalTransitionPlan,
  DataStoreConditionalTransitionResult,
  EntityConditionalTransitionDataStore,
  EntityConditionalTransitionSupport,
  EntityCompareAndSetMutationPlan,
  EntityDirectMutationPlan,
  EntityMutationExclusionGuard,
  EntityMutationProviderCapabilities,
  EntityMutationProviderFeature,
  EntityMutationProviderReadback,
  EntityMutationProviderResult,
  EntityMutationReadbackRequirement,
  EntityNativeMutationSupport,
  EntityVersionedMutationCheckpoint,
  EntityVersionedMutationDataStore,
  EntityVersionedMutationFailure,
  EntityVersionedMutationPlan,
  EntityVersionedMutationResult,
  EntityVersionedMutationSupport,
  EntityVersionedSideEffect,
  OrderDirection,
  Query,
  QueryDirective,
  QueryLimit,
  QueryOrder,
  QueryProjection,
  ResultRange,
  SearchResult,
  SearchableDataStore,
  TotalCountCapability
}
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityWritePolicy,
  RevisionPreconditionPolicy,
  SimpleEntityStorageShapePolicy
}
import org.goldenport.cncf.unitofwork.{CommitRecorder, PrepareResult, TransactionContext}
import org.goldenport.cncf.directive.{Query as EntityQuery}
import org.goldenport.observation.Descriptor
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Mar. 12, 2026
 *  version Mar. 19, 2026
 *  version Mar. 31, 2026
 *  version May.  8, 2026
 *  version May. 26, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
class SqlDataStore(
  dialect: SqlDialectDriver,
  datasource: DataSource,
  recorder: CommitRecorder = CommitRecorder.noop,
  config: SqlDataStore.Config = SqlDataStore.Config()
) extends DataStore
    with SearchableDataStore
    with EntityVersionedMutationDataStore
    with EntityConditionalTransitionDataStore {
  import DataStore.*
  private val _record_decoder = new RecordDecoder()
  @volatile private var _managed_resource: Option[ManagedSqlDataStoreResource] = None
  @volatile private var _managed_borrow_resource: Option[ManagedSqlDataStoreResource] = None
  @volatile private var _managed_borrow_admission: Option[() => Consequence[Unit]] = None

  /**
   * Closes a datasource only when its ownership was explicitly transferred by
   * a factory or infrastructure caller. Constructor-injected datasources stay
   * caller-owned for binary and lifecycle compatibility.
   */
  def closeC(): Consequence[Unit] =
    _managed_resource.fold(Consequence.unit)(_.closeC())

  private[sql] def adoptManagedResource(resource: ManagedSqlDataStoreResource): Unit =
    _managed_resource match {
      case Some(_) => throw new IllegalStateException("SQL datastore datasource ownership is already assigned")
      case None => _managed_resource = Some(resource)
    }

  private[sql] def useManagedResource(
    resource: ManagedSqlDataStoreResource,
    admission: () => Consequence[Unit]
  ): Unit = {
    _managed_borrow_resource = Some(resource)
    _managed_borrow_admission = Some(admission)
  }

  private[cncf] def managedResourceOption: Option[ManagedSqlDataStoreResource] =
    _managed_borrow_resource.orElse(_managed_resource)

  def isAccept(cid: CollectionId): Boolean = true

  override def entityMutationProviderCapabilities
      : EntityMutationProviderCapabilities =
    EntityMutationProviderCapabilities.guardedBaseline.copy(
      features =
        EntityMutationProviderCapabilities.guardedBaseline.features ++
          Set(
            EntityMutationProviderFeature.DirectAlwaysWrite,
            EntityMutationProviderFeature.OptimisticCompareAndSet
          )
    )

  def create(
    collection: CollectionId,
    id: EntryId,
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_connection { conn =>
      val cols = _record_columns(record)
      for {
        _ <- _ensure_table(conn, collection, cols)
        exists <- _exists(conn, collection, id)
        _ <- if (exists) Consequence.DataStoreDuplicate(id.print) else _insert(conn, collection, id, cols)
      } yield ()
    }

  def load(
    collection: CollectionId,
    id: EntryId
  )(using ctx: ExecutionContext): Consequence[Option[Record]] =
    _with_connection { conn =>
      _table_exists(conn, collection).flatMap { exists =>
        if (exists)
          _select(conn, collection, id)
        else
          Consequence.success(None)
      }
    }

  def save(
    collection: CollectionId,
    id: EntryId,
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_connection { conn =>
      val cols = _record_columns(record)
      for {
        _ <- _ensure_table(conn, collection, cols)
        _ <- _upsert(conn, collection, id, cols)
      } yield ()
    }

  def update(
    collection: CollectionId,
    id: EntryId,
    changes: Record
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_connection { conn =>
      val cols = _record_columns(changes)
      for {
        _ <- _ensure_table(conn, collection, cols)
        exists <- _exists(conn, collection, id)
        _ <- if (exists) _update(conn, collection, id, cols) else Consequence.DataStoreNotFound(id.print)
      } yield ()
    }

  def delete(
    collection: CollectionId,
    id: EntryId
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_connection { conn =>
      _table_exists(conn, collection).flatMap { exists =>
        if (exists) _delete(conn, collection, id) else Consequence.unit
      }
    }

  def search(
    collection: CollectionId,
    directive: QueryDirective
  ): Consequence[SearchResult] =
    directive.query match {
      case Query.Empty | Query.Expr(_) =>
        _with_connection { conn =>
          _table_exists(conn, collection).flatMap { exists =>
            if (exists)
              _search_empty(conn, collection, directive)
            else
              Consequence.success(SearchResult(Vector.empty, ResultRange.Exact, None))
          }
        }
    }

  override def count(
    collection: CollectionId,
    directive: QueryDirective
  ): Consequence[Int] =
    directive.query match {
      case Query.Empty | Query.Expr(_) =>
        _with_connection { conn =>
          _table_exists(conn, collection).flatMap { exists =>
            if (exists)
              _count(conn, collection, directive)
            else
              Consequence.success(0)
          }
        }
    }

  override def totalCountCapability(collection: CollectionId): TotalCountCapability =
    TotalCountCapability.Supported

  override def mutateEntityDirect(
    plan: EntityDirectMutationPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityMutationProviderResult] =
    EntityNativeMutationSupport
      .validate(plan)
      .flatMap(_ =>
        _validate_native_mutation_columns(
          plan.revisionField,
          plan.changes
        )
      )
      .flatMap { _ =>
        (for {
          _ <- _prepare_native_mutation_schema(
            plan.collection,
            plan.entryId,
            plan.revisionField,
            plan.changes,
            plan.exclusionGuards
          )
          result <-
            _with_versioned_mutation { connection =>
              _mutate_entity_direct(connection, plan)
            }
        } yield result).recoverWith(
          EntityVersionedMutationFailure.normalizeProvider
        )
      }

  override def compareAndSetEntity(
    plan: EntityCompareAndSetMutationPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityMutationProviderResult] =
    EntityNativeMutationSupport
      .validate(plan)
      .flatMap(_ =>
        _validate_native_mutation_columns(
          plan.revisionField,
          plan.changes
        )
      )
      .flatMap { _ =>
        (for {
          _ <- _prepare_native_mutation_schema(
            plan.collection,
            plan.entryId,
            plan.revisionField,
            plan.changes,
            plan.exclusionGuards
          )
          result <-
            _with_versioned_mutation { connection =>
              _compare_and_set_entity(connection, plan)
            }
        } yield result).recoverWith(
          EntityVersionedMutationFailure.normalizeProvider
        )
      }

  def mutateVersionedEntity(
    plan: EntityVersionedMutationPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityVersionedMutationResult] =
    EntityVersionedMutationSupport
      .validate(plan)
      .flatMap { _ =>
        (for {
          _ <- _prepare_versioned_mutation_schema(plan)
          result <-
            _with_versioned_mutation { connection =>
              _versioned_mutation(connection, plan)
            }
        } yield result).recoverWith(
          EntityVersionedMutationFailure.normalizeProvider
        )
      }

  def conditionalTransition(
    plan: DataStoreConditionalTransitionPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[DataStoreConditionalTransitionResult] =
    EntityConditionalTransitionSupport
      .validate(plan)
      .flatMap { admitted =>
        (for {
          _ <- _prepare_conditional_transition_schema(admitted)
          result <-
            _with_conditional_transition { conn =>
              _conditional_transition(conn, admitted)
            }
        } yield result).recoverWith(
          DataStoreConditionalTransitionFailure.normalizeProvider
        )
      }

  def debugSearchSql(
    collection: CollectionId,
    directive: QueryDirective
  ): SqlDataStore.SqlStatement =
    _search_sql(collection, directive)

  def debugCountSql(
    collection: CollectionId,
    directive: QueryDirective
  ): SqlDataStore.SqlStatement =
    _count_sql(collection, directive)

  def prepare(tx: TransactionContext): PrepareResult =
    record_prepare(recorder)

  def commit(tx: TransactionContext): Unit =
    record_commit(recorder)

  def abort(tx: TransactionContext): Unit =
    record_abort(recorder)

  protected def conditional_transition_checkpoint(
    checkpoint: DataStoreConditionalTransitionCheckpoint
  ): Consequence[Unit] =
    Consequence.unit

  protected def versioned_mutation_checkpoint(
    checkpoint: EntityVersionedMutationCheckpoint
  ): Consequence[Unit] =
    Consequence.unit

  protected def sql_statement(
    sql: String
  ): Unit = ()

  protected def versioned_mutation_commit(
    connection: Connection
  ): Consequence[Unit] =
    try {
      connection.commit()
      Consequence.unit
    } catch {
      case e: Throwable =>
        EntityVersionedMutationFailure.transactionIndeterminate(
          s"Versioned mutation commit outcome is indeterminate: ${e.getClass.getName}"
        )
    }

  protected def conditional_transition_commit(
    connection: Connection
  ): Consequence[Unit] =
    try {
      connection.commit()
      Consequence.unit
    } catch {
      case e: Throwable =>
        DataStoreConditionalTransitionFailure.transactionIndeterminate(
          s"Conditional transition commit outcome is indeterminate: ${e.getClass.getName}"
        )
    }

  private def _with_conditional_transition[A](
    body: Connection => Consequence[A]
  ): Consequence[A] =
    _with_managed_datasource(source => _with_conditional_transition(source, body))

  private def _with_conditional_transition[A](
    source: DataSource,
    body: Connection => Consequence[A]
  ): Consequence[A] =
    try {
      val connection = source.getConnection()
      try {
        val autocommit = connection.getAutoCommit
        try {
          connection.setAutoCommit(false)
          val result =
            try body(connection)
            catch {
              case e: Throwable =>
                DataStoreConditionalTransitionFailure.providerFailure(
                  s"Conditional transition provider failed: ${e.getClass.getName}"
                )
            }
          result match {
            case success: Consequence.Success[A] =>
              conditional_transition_commit(connection) match {
                case _: Consequence.Success[?] =>
                  success
                case Consequence.Failure(conclusion) =>
                  _rollback_conditional_transition(connection, conclusion)
              }
            case Consequence.Failure(conclusion) =>
              val normalized =
                DataStoreConditionalTransitionFailure
                  .normalizeProvider[A](conclusion)
              _rollback_conditional_transition(
                connection,
                normalized.conclusion
              )
          }
        } finally {
          _restore_conditional_connection(connection, autocommit)
        }
      } finally {
        _close_conditional_connection(connection)
      }
    } catch {
      case e: Throwable =>
        DataStoreConditionalTransitionFailure.providerFailure(
          s"Conditional transition transaction could not start: ${e.getClass.getName}"
        )
    }

  private def _with_versioned_mutation[A](
    body: Connection => Consequence[A]
  ): Consequence[A] =
    _with_managed_datasource(source => _with_versioned_mutation(source, body))

  private def _with_versioned_mutation[A](
    source: DataSource,
    body: Connection => Consequence[A]
  ): Consequence[A] =
    try {
      val connection = source.getConnection()
      try {
        val autocommit = connection.getAutoCommit
        try {
          connection.setAutoCommit(false)
          val result =
            try body(connection)
            catch {
              case e: Throwable =>
                EntityVersionedMutationFailure.providerFailure(
                  s"Versioned mutation provider failed: ${e.getClass.getName}"
                )
            }
          result match {
            case success: Consequence.Success[A] =>
              versioned_mutation_commit(connection) match {
                case _: Consequence.Success[?] =>
                  success
                case Consequence.Failure(conclusion) =>
                  _rollback_versioned_mutation(connection, conclusion)
              }
            case Consequence.Failure(conclusion) =>
              val normalized =
                EntityVersionedMutationFailure
                  .normalizeProvider[A](conclusion)
              _rollback_versioned_mutation(
                connection,
                normalized.conclusion
              )
          }
        } finally {
          _restore_conditional_connection(connection, autocommit)
        }
      } finally {
        _close_conditional_connection(connection)
      }
    } catch {
      case e: Throwable =>
        EntityVersionedMutationFailure.providerFailure(
          s"Versioned mutation transaction could not start: ${e.getClass.getName}"
        )
    }

  private def _rollback_versioned_mutation[A](
    connection: Connection,
    conclusion: Conclusion
  ): Consequence[A] =
    try {
      connection.rollback()
      Consequence.Failure(conclusion)
    } catch {
      case e: Throwable =>
        EntityVersionedMutationFailure.transactionIndeterminate(
          s"Versioned mutation rollback outcome is indeterminate: ${e.getClass.getName}"
        )
    }

  private def _rollback_conditional_transition[A](
    connection: Connection,
    conclusion: Conclusion
  ): Consequence[A] =
    try {
      connection.rollback()
      Consequence.Failure(conclusion)
    } catch {
      case e: Throwable =>
        DataStoreConditionalTransitionFailure.transactionIndeterminate(
          s"Conditional transition rollback outcome is indeterminate: ${e.getClass.getName}"
        )
    }

  private def _restore_conditional_connection(
    connection: Connection,
    autocommit: Boolean
  ): Unit =
    try connection.setAutoCommit(autocommit)
    catch {
      case _: Throwable => ()
    }

  private def _close_conditional_connection(
    connection: Connection
  ): Unit =
    try connection.close()
    catch {
      case _: Throwable => ()
    }

  private def _prepare_conditional_transition_schema(
    plan: DataStoreConditionalTransitionPlan
  ): Consequence[Unit] =
    if (_requires_conditional_schema_preparation)
      _with_connection { connection =>
        for {
          _ <- _ensure_existing_table_columns(
            connection,
            plan.root.collection,
            _conditional_root_columns(plan.root)
          )
          _ <- plan.successor match {
            case create: DataStoreConditionalSuccessor.Create =>
              _ensure_table(
                connection,
                create.collection,
                _record_columns(create.record)
              )
            case _: DataStoreConditionalSuccessor.Bind =>
              Consequence.unit
          }
          _ <- plan.sideEffects.foldLeft(Consequence.unit) {
            case (
                  result,
                  EntityVersionedSideEffect.Save(
                    collection,
                    _,
                    record
                  )
                ) =>
              result.flatMap(_ =>
                _ensure_table(
                  connection,
                  collection,
                  _record_columns(record)
                )
              )
            case (result, _: EntityVersionedSideEffect.Delete) =>
              result
          }
        } yield ()
      }
    else
      Consequence.unit

  private def _prepare_versioned_mutation_schema(
    plan: EntityVersionedMutationPlan
  ): Consequence[Unit] =
    if (_requires_conditional_schema_preparation)
      _with_connection { connection =>
        for {
          existing <-
            _required_record(
              connection,
              plan.collection,
              plan.entryId
            )
          revision <- EntityVersionedMutationSupport
            .revision(existing, plan.revisionField)
          rootrecord =
            EntityVersionedMutationSupport.applyRootMutation(
              existing,
              plan,
              revision
            )
          _ <- _ensure_existing_table_columns(
            connection,
            plan.collection,
            _record_columns(rootrecord)
          )
          _ <- plan.sideEffects.foldLeft(Consequence.unit) {
            case (
                  result,
                  EntityVersionedSideEffect.Save(
                    collection,
                    _,
                    record
                  )
                ) =>
              result.flatMap(_ =>
                _ensure_table(
                  connection,
                  collection,
                  _record_columns(record)
                )
              )
            case (result, _: EntityVersionedSideEffect.Delete) =>
              result
          }
        } yield ()
      }
    else
      Consequence.unit

  private def _prepare_native_mutation_schema(
    collection: CollectionId,
    entryid: EntryId,
    revisionfield: String,
    changes: Record,
    exclusionguards: Vector[EntityMutationExclusionGuard]
  ): Consequence[Unit] =
    _with_connection { connection =>
      _table_exists(connection, collection).flatMap { exists =>
        if (exists)
          _existing_columns(connection, collection).flatMap { columns =>
            val revisioncolumn = _column_name(revisionfield)
            if (columns.contains(revisioncolumn))
              _ensure_columns(
                connection,
                collection,
                _record_columns(changes) ++
                  _native_mutation_guard_columns(exclusionguards)
              )
            else
              Consequence.operationInvalid(
                "entity-native-mutation-schema",
                Vector(
                  Descriptor.Facet.Reason(
                    "missing-managed-revision-column"
                  ),
                  Descriptor.Facet.FieldPath(revisionfield),
                  Descriptor.Facet.Policy(
                    EntityVersionedMutationFailure.POLICY
                  )
                )
              )
          }
        else
          Consequence.DataStoreNotFound(entryid.print)
      }
    }

  private def _validate_native_mutation_columns(
    revisionfield: String,
    changes: Record
  ): Consequence[Unit] = {
    val revisioncolumn = _column_name(revisionfield)
    val collision =
      _record_columns(changes).exists(_._1 == revisioncolumn)
    if (collision)
      Consequence.argumentPolicyViolation(
        "changes",
        "framework-managed-revision",
        s"record without normalized revision column $revisioncolumn",
        revisioncolumn
      )
    else
      Consequence.unit
  }

  private def _native_mutation_guard_columns(
    guards: Vector[EntityMutationExclusionGuard]
  ): Vector[(String, Any)] =
    guards.map {
      case EntityMutationExclusionGuard.EqualTo(fieldname, value) =>
        _column_name(fieldname) -> _column_value(value)
      case EntityMutationExclusionGuard.Present(fieldname) =>
        _column_name(fieldname) -> ""
    }

  private def _requires_conditional_schema_preparation: Boolean =
    dialect.name == MySqlDialectDriver.name

  private def _ensure_existing_table_columns(
    connection: Connection,
    collection: CollectionId,
    columns: Vector[(String, Any)]
  ): Consequence[Unit] =
    _table_exists(connection, collection).flatMap { exists =>
      if (exists)
        _ensure_columns(connection, collection, columns)
      else
        Consequence.unit
    }

  private def _conditional_root_columns(
    root: DataStoreConditionalRoot
  ): Vector[(String, Any)] =
    _record_columns(
      root.changes ++
        Record.dataAuto(root.revisionField -> root.nextRevision.value)
    )

  private def _conditional_transition(
    connection: Connection,
    plan: DataStoreConditionalTransitionPlan
  ): Consequence[DataStoreConditionalTransitionResult] =
    for {
      existingoption <-
        _select_conditional_root(
          connection,
          plan.root.collection,
          plan.root.entryId
        )
      existing <-
        existingoption
          .map(Consequence.success)
          .getOrElse(
            Consequence.DataStoreNotFound(plan.root.entryId.print)
          )
      matches <-
        EntityConditionalTransitionSupport.rootMatches(
          existing,
          plan.root
        )
      result <-
        if (matches)
          _apply_conditional_transition(connection, plan)
        else
          Consequence.success(
            DataStoreConditionalTransitionResult.NotMatched(existing)
          )
    } yield result

  private def _versioned_mutation(
    connection: Connection,
    plan: EntityVersionedMutationPlan
  ): Consequence[EntityVersionedMutationResult] =
    for {
      existingoption <-
        _select_conditional_root(
          connection,
          plan.collection,
          plan.entryId
        )
      existing <-
        existingoption
          .map(Consequence.success)
          .getOrElse(
            Consequence.DataStoreNotFound(plan.entryId.print)
          )
      actual <-
        EntityVersionedMutationSupport.revision(
          existing,
          plan.revisionField
        )
      desired =
        EntityVersionedMutationSupport.desiredRecord(existing, plan)
      result <-
        if (
          plan.preconditionPolicy ==
            RevisionPreconditionPolicy.ObservedRequired &&
          plan.expectedRevision.exists(_ != actual)
        )
          _stale_versioned_mutation(plan.expectedRevision, actual)
        else if (
          plan.writePolicy == EntityWritePolicy.WriteIfChanged &&
          EntityVersionedMutationSupport.businessStateEquals(
            existing,
            desired,
            plan
          )
        )
          Consequence.success(
            EntityVersionedMutationResult.NoOp(existing)
          )
        else if (
          plan.concurrencyPolicy == EntityConcurrencyPolicy.Optimistic &&
          plan.expectedRevision.exists(_ != actual)
        )
          _stale_versioned_mutation(plan.expectedRevision, actual)
        else
          actual.nextC.flatMap { nextrevision =>
            _apply_versioned_mutation(
              connection,
              plan,
              existing,
              nextrevision
            )
          }
    } yield result

  private def _mutate_entity_direct(
    connection: Connection,
    plan: EntityDirectMutationPlan
  ): Consequence[EntityMutationProviderResult] =
    for {
      updated <-
        _native_mutation_update_count(
          connection,
          plan.collection,
          plan.entryId,
          plan.revisionField,
          plan.changes,
          None,
          plan.exclusionGuards
        )
      result <-
        if (updated == 1)
          _native_mutation_applied(
            connection,
            plan.collection,
            plan.entryId,
            plan.readbackRequirement
          )
        else
          _diagnose_direct_mutation_failure(connection, plan)
    } yield result

  private def _compare_and_set_entity(
    connection: Connection,
    plan: EntityCompareAndSetMutationPlan
  ): Consequence[EntityMutationProviderResult] =
    for {
      updated <-
        _native_mutation_update_count(
          connection,
          plan.collection,
          plan.entryId,
          plan.revisionField,
          plan.changes,
          Some(plan.expectedRevision),
          plan.exclusionGuards
        )
      result <-
        if (updated == 1)
          _native_mutation_applied(
            connection,
            plan.collection,
            plan.entryId,
            plan.readbackRequirement
          )
        else
          _diagnose_compare_and_set_failure(connection, plan)
    } yield result

  private def _native_mutation_applied(
    connection: Connection,
    collection: CollectionId,
    entryid: EntryId,
    requirement: EntityMutationReadbackRequirement
  ): Consequence[EntityMutationProviderResult] =
    requirement match {
      case EntityMutationReadbackRequirement.None =>
        Consequence.success(
          EntityMutationProviderResult.Applied(
            EntityMutationProviderReadback.Omitted
          )
        )
      case EntityMutationReadbackRequirement.AuthoritativeRecord =>
        _required_record(connection, collection, entryid).map(record =>
          EntityMutationProviderResult.Applied(
            EntityMutationProviderReadback.Authoritative(record)
          )
        )
    }

  private def _diagnose_direct_mutation_failure(
    connection: Connection,
    plan: EntityDirectMutationPlan
  ): Consequence[EntityMutationProviderResult] =
    _required_record(connection, plan.collection, plan.entryId).flatMap {
      record =>
        EntityNativeMutationSupport
          .admitExisting(plan.entryId, record, plan.exclusionGuards)
          .flatMap(_ =>
            EntityVersionedMutationSupport
              .revision(record, plan.revisionField)
              .flatMap { revision =>
                revision.nextC.flatMap(_ =>
                  EntityVersionedMutationFailure.providerFailure(
                    "Direct Entity mutation affected no row"
                  )
                )
              }
          )
    }

  private def _diagnose_compare_and_set_failure(
    connection: Connection,
    plan: EntityCompareAndSetMutationPlan
  ): Consequence[EntityMutationProviderResult] =
    _required_record(connection, plan.collection, plan.entryId).flatMap {
      record =>
        EntityNativeMutationSupport
          .admitExisting(plan.entryId, record, plan.exclusionGuards)
          .flatMap(_ =>
            EntityVersionedMutationSupport
              .revision(record, plan.revisionField)
              .flatMap { actual =>
                if (actual != plan.expectedRevision)
                  Consequence.success(
                    EntityMutationProviderResult.Stale(
                      plan.expectedRevision,
                      actual
                    )
                  )
                else
                  actual.nextC.flatMap(_ =>
                    EntityVersionedMutationFailure.providerFailure(
                      "Compare-and-set Entity mutation affected no row"
                    )
                  )
              }
          )
    }

  private def _apply_versioned_mutation(
    connection: Connection,
    plan: EntityVersionedMutationPlan,
    existing: Record,
    nextrevision: EntityRevision
  ): Consequence[EntityVersionedMutationResult] = {
    val rootrecord =
      EntityVersionedMutationSupport.applyRootMutation(
        existing,
        plan,
        nextrevision
      )
    for {
      _ <- versioned_mutation_checkpoint(
        EntityVersionedMutationCheckpoint.RootPrepared
      )
      _ <- _update_versioned_root_record(
        connection,
        plan.collection,
        plan.entryId,
        rootrecord
      )
      _ <- _apply_conditional_side_effects(connection, plan.sideEffects)
      _ <- versioned_mutation_checkpoint(
        EntityVersionedMutationCheckpoint.SideEffectsPrepared
      )
      _ <- versioned_mutation_checkpoint(
        EntityVersionedMutationCheckpoint.BeforePublish
      )
      authoritative <-
        _required_record(
          connection,
          plan.collection,
          plan.entryId
        )
    } yield EntityVersionedMutationResult.Applied(authoritative)
  }

  private def _stale_versioned_mutation(
    expectedrevision: Option[EntityRevision],
    actualrevision: EntityRevision
  ): Consequence[EntityVersionedMutationResult] =
    expectedrevision
      .map(expected =>
        Consequence.success(
          EntityVersionedMutationResult.Stale(
            expected,
            actualrevision
          )
        )
      )
      .getOrElse(
        Consequence.configurationInvalid(
          "stale Entity mutation requires an expected revision"
        )
      )

  private def _update_versioned_root_record(
    connection: Connection,
    collection: CollectionId,
    entryid: EntryId,
    record: Record
  ): Consequence[Unit] = {
    val columns = _record_columns(record)
    for {
      _ <- _ensure_table(connection, collection, columns)
      updated <-
        _update_count(
          connection,
          collection,
          entryid,
          columns
        )
      _ <-
        if (updated == 1)
          Consequence.unit
        else
          Consequence.DataStoreNotFound(entryid.print)
    } yield ()
  }

  private def _apply_conditional_transition(
    connection: Connection,
    plan: DataStoreConditionalTransitionPlan
  ): Consequence[DataStoreConditionalTransitionResult] =
    for {
      _ <- conditional_transition_checkpoint(
        DataStoreConditionalTransitionCheckpoint.GuardAdmitted
      )
      _ <- _prepare_conditional_successor(connection, plan.successor)
      _ <- conditional_transition_checkpoint(
        DataStoreConditionalTransitionCheckpoint.SuccessorPrepared
      )
      _ <- _update_root_record(
        connection,
        plan.root
      )
      _ <- conditional_transition_checkpoint(
        DataStoreConditionalTransitionCheckpoint.RootPrepared
      )
      _ <- _apply_conditional_side_effects(connection, plan.sideEffects)
      _ <- conditional_transition_checkpoint(
        DataStoreConditionalTransitionCheckpoint.BeforePublish
      )
      authoritativeroot <-
        _required_record(
          connection,
          plan.root.collection,
          plan.root.entryId
        )
      authoritativesuccessor <-
        _required_record(
          connection,
          plan.successor.collection,
          plan.successor.entryId
        )
    } yield DataStoreConditionalTransitionResult.Transitioned(
      authoritativeroot,
      authoritativesuccessor
    )

  private def _prepare_conditional_successor(
    connection: Connection,
    successor: DataStoreConditionalSuccessor
  ): Consequence[Unit] =
    successor match {
      case create: DataStoreConditionalSuccessor.Create =>
        val columns = _record_columns(create.record)
        for {
          _ <- _ensure_table(connection, create.collection, columns)
          exists <- _exists(connection, create.collection, create.entryId)
          _ <-
            if (exists)
              Consequence.operationConflict(
                "entity-conditional-transition",
                Vector(
                  Descriptor.Facet.Reason("successor-collision"),
                  Descriptor.Facet.Policy(
                    "entity.conditional-transition.successor-create"
                  )
                )
              )
            else
              _insert(
                connection,
                create.collection,
                create.entryId,
                columns
              )
        } yield ()
      case bind: DataStoreConditionalSuccessor.Bind =>
        _select_if_table_exists(
          connection,
          bind.collection,
          bind.entryId
        ).flatMap {
          case None =>
            Consequence.DataStoreNotFound(bind.entryId.print)
          case Some(record) =>
            EntityVersionedMutationSupport
              .revision(record, bind.revisionField)
              .flatMap { actual =>
                if (actual == bind.expectedRevision)
                  Consequence.unit
                else
                  Consequence.operationConflict(
                    "entity-conditional-transition",
                    Vector(
                      Descriptor.Facet.Reason(
                        "bound-successor-revision-conflict"
                      ),
                      Descriptor.Facet.Policy(
                        "entity.conditional-transition.successor-bind"
                      )
                    )
                  )
              }
        }
    }

  private def _update_root_record(
    connection: Connection,
    root: DataStoreConditionalRoot
  ): Consequence[Unit] = {
    val columns = _conditional_root_columns(root)
    for {
      _ <- _ensure_table(connection, root.collection, columns)
      updated <-
        _update_count(
          connection,
          root.collection,
          root.entryId,
          columns
        )
      _ <-
        if (updated == 1)
          Consequence.unit
        else
          Consequence.DataStoreNotFound(root.entryId.print)
    } yield ()
  }

  private def _apply_conditional_side_effects(
    connection: Connection,
    effects: Vector[EntityVersionedSideEffect]
  ): Consequence[Unit] =
    effects.foldLeft(Consequence.unit) {
      case (result, EntityVersionedSideEffect.Save(collection, entryid, record)) =>
        result.flatMap { _ =>
          val columns = _record_columns(record)
          for {
            _ <- _ensure_table(connection, collection, columns)
            _ <- _upsert(connection, collection, entryid, columns)
          } yield ()
        }
      case (result, EntityVersionedSideEffect.Delete(collection, entryid)) =>
        result.flatMap(_ =>
          _table_exists(connection, collection).flatMap { exists =>
            if (exists)
              _delete(connection, collection, entryid)
            else
              Consequence.unit
          }
        )
    }

  private def _required_record(
    connection: Connection,
    collection: CollectionId,
    entryid: EntryId
  ): Consequence[Record] =
    _select_if_table_exists(connection, collection, entryid).flatMap {
      case Some(record) => Consequence.success(record)
      case None => Consequence.DataStoreNotFound(entryid.print)
    }

  private def _select_if_table_exists(
    connection: Connection,
    collection: CollectionId,
    entryid: EntryId
  ): Consequence[Option[Record]] =
    _table_exists(connection, collection).flatMap { exists =>
      if (exists)
        _select(connection, collection, entryid)
      else
        Consequence.success(None)
    }

  private def _select_conditional_root(
    connection: Connection,
    collection: CollectionId,
    entryid: EntryId
  ): Consequence[Option[Record]] =
    _table_exists(connection, collection).flatMap { exists =>
      if (exists) {
        val basesql =
          dialect.selectByIdSql(_table_name(collection))
        val sql =
          if (dialect.name == MySqlDialectDriver.name)
            s"$basesql FOR UPDATE"
          else
            basesql
        _select_with_sql(connection, collection, entryid, sql)
      } else {
        Consequence.success(None)
      }
    }

  private def _with_connection[A](
    f: Connection => Consequence[A]
  ): Consequence[A] =
    _with_managed_datasource(source => _with_connection(source, f))

  private def _with_managed_datasource[A](
    f: DataSource => Consequence[A]
  ): Consequence[A] =
    _managed_borrow_resource.orElse(_managed_resource) match {
      case Some(resource) =>
        _managed_borrow_admission.map(_()).getOrElse(Consequence.unit).flatMap(_ => resource.borrowC(f))
      case None => f(datasource)
    }

  private def _with_connection[A](
    source: DataSource,
    f: Connection => Consequence[A]
  ): Consequence[A] =
    Consequence {
      val conn = source.getConnection()
      try {
        f(conn)
      } finally {
        conn.close()
      }
    }.flatMap(identity)

  private def _prepare_statement(
    connection: Connection,
    sql: String
  ): PreparedStatement = {
    sql_statement(sql)
    connection.prepareStatement(sql)
  }

  private def _table_name(collection: CollectionId): String =
    collection.collectionName

  private def _record_columns(
    record: Record
  ): Vector[(String, Any)] =
    record.fields.collect {
      case field if field.key != "id" =>
        val k = field.key
        val v = field.value.single
        val key = if (config.normalizeColumnNames) _to_column_name(k) else k
        key -> _column_value(v)
    }
      .foldLeft(Vector.empty[(String, Any)]) { case (z, (k, v)) =>
        z.indexWhere(_._1 == k) match {
          case -1 => z :+ (k -> v)
          case i => z.updated(i, k -> v)
        }
      }

  private def _column_value(
    value: Any
  ): Any =
    value match {
      case org.simplemodeling.model.directive.Update.SetNull =>
        null
      case m: StringEncodable =>
        given org.goldenport.context.ExecutionContext = org.goldenport.convert.StringEncoder.storageExecutionContext
        m.encode
      case m: Record =>
        m.toJsonString
      case m: RecordPresentable =>
        m.toRecord().toJsonString
      case xs: Iterable[?] =>
        Json.fromValues(xs.iterator.map(_json_from_value).toVector).noSpaces
      case m: Byte => m.toInt
      case m: Short => m.toInt
      case m: Int => m
      case m: Long => m
      case m: Boolean => if (m) 1 else 0
      case m: Float => m.toDouble
      case m: Double => m
      case m: BigInt => m.toDouble
      case m: BigDecimal => m.toDouble
      case other =>
        Presentable.print(other)
    }

  private def _json_from_value(
    value: Any
  ): Json =
    value match {
      case null => Json.Null
      case m: StringEncodable =>
        given org.goldenport.context.ExecutionContext = org.goldenport.convert.StringEncoder.storageExecutionContext
        Json.fromString(m.encode)
      case m: Record =>
        parse(m.toJsonString).getOrElse(Json.fromString(m.toJsonString))
      case m: RecordPresentable =>
        val jsontext = m.toRecord().toJsonString
        parse(jsontext).getOrElse(Json.fromString(jsontext))
      case xs: Iterable[?] =>
        Json.fromValues(xs.iterator.map(_json_from_value).toVector)
      case m: Byte => Json.fromInt(m.toInt)
      case m: Short => Json.fromInt(m.toInt)
      case m: Int => Json.fromInt(m)
      case m: Long => Json.fromLong(m)
      case m: Boolean => Json.fromBoolean(m)
      case m: Float => Json.fromFloatOrNull(m)
      case m: Double => Json.fromDoubleOrNull(m)
      case m: BigInt => Json.fromBigInt(m)
      case m: BigDecimal => Json.fromBigDecimal(m)
      case other => Json.fromString(Presentable.print(other))
    }

  private def _decode_column_value(
    value: Any
  ): Any = {
    value match {
      case s: String =>
        val trimmed = s.trim
        if (trimmed.startsWith("{") && trimmed.endsWith("}"))
          _record_decoder.json(trimmed).toOption.getOrElse(s)
        else if (trimmed.startsWith("[") && trimmed.endsWith("]"))
          parse(trimmed).toOption.map(_json_to_value).getOrElse(s)
        else
          s
      case other =>
        other
    }
  }

  private def _json_to_value(
    json: Json
  ): Any =
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = n => n.toLong.getOrElse(n.toDouble),
      jsonString = identity,
      jsonArray = _.map(_json_to_value).toVector,
      jsonObject = obj => Record.dataAuto(obj.toVector.map { case (k, v) => k -> _json_to_value(v) }*)
    )

  private def _table_exists(
    conn: Connection,
    collection: CollectionId
  ): Consequence[Boolean] =
    Consequence {
      val sql = dialect.tableExistsSql(_table_name(collection))
      val stmt = conn.createStatement()
      try {
        val rs = stmt.executeQuery(sql)
        try rs.next()
        finally rs.close()
      } finally {
        stmt.close()
      }
    }

  private def _existing_columns(
    conn: Connection,
    collection: CollectionId
  ): Consequence[Set[String]] =
    Consequence {
      val sql = dialect.tableColumnsSql(_table_name(collection))
      val stmt = conn.createStatement()
      try {
        val rs = stmt.executeQuery(sql)
        val buf = scala.collection.mutable.Set.empty[String]
        try {
          while (rs.next()) {
            buf += rs.getString(dialect.tableColumnsNameColumn)
          }
        } finally {
          rs.close()
        }
        buf.toSet
      } finally {
        stmt.close()
      }
    }

  private def _ensure_table(
    conn: Connection,
    collection: CollectionId,
    columns: Vector[(String, Any)]
  ): Consequence[Unit] =
    _table_exists(conn, collection).flatMap { exists =>
      if (exists)
        _ensure_columns(conn, collection, columns)
      else
        _create_table(conn, collection, columns)
    }

  private def _create_table(
    conn: Connection,
    collection: CollectionId,
    columns: Vector[(String, Any)]
  ): Consequence[Unit] =
    Consequence {
      val sql = dialect.createTableSql(_table_name(collection), columns)
      val stmt = conn.createStatement()
      try {
        stmt.execute(sql)
      } finally {
        stmt.close()
      }
    }

  private def _ensure_columns(
    conn: Connection,
    collection: CollectionId,
    columns: Vector[(String, Any)]
  ): Consequence[Unit] =
    _existing_columns(conn, collection).flatMap { existing =>
      val missing = columns.map(_._1).filterNot(existing.contains)
      missing.foldLeft(Consequence.unit) { (z, col) =>
        z.flatMap(_ => _add_column(conn, collection, columns.find(_._1 == col).get))
      }
    }

  private def _add_column(
    conn: Connection,
    collection: CollectionId,
    column: (String, Any)
  ): Consequence[Unit] =
    Consequence {
      val sql = dialect.addColumnSql(_table_name(collection), column)
      val stmt = conn.createStatement()
      try {
        stmt.execute(sql)
        ()
      } finally {
        stmt.close()
      }
    }.recoverWith { conclusion =>
      val installed =
        _existing_columns(conn, collection).toOption.exists(
          _.contains(column._1)
        )
      if (installed)
        Consequence.unit
      else
        Consequence.Failure(conclusion)
    }

  private def _exists(
    conn: Connection,
    collection: CollectionId,
    id: EntryId
  ): Consequence[Boolean] =
    Consequence {
      val sql = dialect.selectByIdSql(_table_name(collection))
      val stmt = _prepare_statement(conn, sql)
      try {
        stmt.setString(1, id.print)
        val rs = stmt.executeQuery()
        try rs.next()
        finally rs.close()
      } finally {
        stmt.close()
      }
    }

  private def _insert(
    conn: Connection,
    collection: CollectionId,
    id: EntryId,
    columns: Vector[(String, Any)]
  ): Consequence[Unit] =
    Consequence {
      val sql = dialect.insertSql(_table_name(collection), columns.map(_._1))
      val stmt = _prepare_statement(conn, sql)
      try {
        stmt.setString(1, id.print)
        columns.zipWithIndex.foreach { case ((_, v), i) =>
          stmt.setObject(i + 2, v)
        }
        stmt.executeUpdate()
      } finally {
        stmt.close()
      }
    }

  private def _upsert(
    conn: Connection,
    collection: CollectionId,
    id: EntryId,
    columns: Vector[(String, Any)]
  ): Consequence[Unit] =
    Consequence {
      val sql = dialect.upsertSql(_table_name(collection), columns.map(_._1))
      val stmt = _prepare_statement(conn, sql)
      try {
        stmt.setString(1, id.print)
        columns.zipWithIndex.foreach { case ((_, value), index) =>
          stmt.setObject(index + 2, value)
        }
        stmt.executeUpdate()
        ()
      } finally {
        stmt.close()
      }
    }

  private def _update(
    conn: Connection,
    collection: CollectionId,
    id: EntryId,
    columns: Vector[(String, Any)]
  ): Consequence[Unit] =
    if (columns.isEmpty)
      Consequence.unit
    else
      Consequence {
        val sql = dialect.updateSql(_table_name(collection), columns.map(_._1))
        val stmt = _prepare_statement(conn, sql)
        try {
          columns.zipWithIndex.foreach { case ((_, v), i) =>
            stmt.setObject(i + 1, v)
          }
          stmt.setString(columns.length + 1, id.print)
          stmt.executeUpdate()
        } finally {
          stmt.close()
        }
      }

  private def _update_count(
    conn: Connection,
    collection: CollectionId,
    id: EntryId,
    columns: Vector[(String, Any)]
  ): Consequence[Int] =
    if (columns.isEmpty)
      Consequence.success(0)
    else
      Consequence {
        val sql =
          dialect.updateSql(
            _table_name(collection),
            columns.map(_._1)
          )
        val stmt = _prepare_statement(conn, sql)
        try {
          columns.zipWithIndex.foreach { case ((_, value), index) =>
            stmt.setObject(index + 1, value)
          }
          stmt.setString(columns.length + 1, id.print)
          stmt.executeUpdate()
        } finally {
          stmt.close()
        }
      }

  private def _native_mutation_update_count(
    connection: Connection,
    collection: CollectionId,
    entryid: EntryId,
    revisionfield: String,
    changes: Record,
    expectedrevision: Option[EntityRevision],
    exclusionguards: Vector[EntityMutationExclusionGuard]
  ): Consequence[Int] =
    Consequence {
      val columns = _record_columns(changes)
      val revisioncolumn = _column_name(revisionfield)
      val assignments =
        columns
          .map { case (name, _) =>
            s"${dialect.quoteIdentifier(name)} = ?"
          }
          .appended(
            s"${dialect.quoteIdentifier(revisioncolumn)} = ${dialect.quoteIdentifier(revisioncolumn)} + 1"
          )
          .mkString(", ")
      val expectedclause =
        expectedrevision
          .map(_ =>
            s" AND ${dialect.quoteIdentifier(revisioncolumn)} = ?"
          )
          .getOrElse("")
      val exclusionclause =
        exclusionguards
          .map { guard =>
            guard match {
              case EntityMutationExclusionGuard.EqualTo(fieldname, _) =>
                val column =
                  dialect.quoteIdentifier(_column_name(fieldname))
                s" AND ($column IS NULL OR $column <> ?)"
              case EntityMutationExclusionGuard.Present(fieldname) =>
                s" AND ${dialect.absentValueSql(_column_name(fieldname))}"
            }
          }
          .mkString
      val equalityguards =
        exclusionguards.collect {
          case guard: EntityMutationExclusionGuard.EqualTo => guard
        }
      val sql =
        s"UPDATE ${dialect.quoteIdentifier(_table_name(collection))} SET $assignments WHERE ${dialect.quoteIdentifier("id")} = ?$expectedclause$exclusionclause AND ${dialect.entityRevisionGuardSql(revisioncolumn)}"
      val statement = _prepare_statement(connection, sql)
      try {
        columns.zipWithIndex.foreach { case ((_, value), index) =>
          statement.setObject(index + 1, value)
        }
        val idindex = columns.length + 1
        statement.setString(idindex, entryid.print)
        expectedrevision match {
          case Some(expected) =>
            statement.setLong(idindex + 1, expected.value)
            equalityguards.zipWithIndex.foreach { case (guard, index) =>
              statement.setObject(
                idindex + 2 + index,
                _column_value(guard.value)
              )
            }
            val guardoffset = idindex + 2 + equalityguards.size
            statement.setLong(guardoffset, EntityRevision.INITIAL.value)
            statement.setLong(guardoffset + 1, Long.MaxValue)
          case None =>
            equalityguards.zipWithIndex.foreach { case (guard, index) =>
              statement.setObject(
                idindex + 1 + index,
                _column_value(guard.value)
              )
            }
            val guardoffset = idindex + 1 + equalityguards.size
            statement.setLong(guardoffset, EntityRevision.INITIAL.value)
            statement.setLong(guardoffset + 1, Long.MaxValue)
        }
        statement.executeUpdate()
      } finally {
        statement.close()
      }
    }

  private def _delete(
    conn: Connection,
    collection: CollectionId,
    id: EntryId
  ): Consequence[Unit] =
    Consequence {
      val sql = dialect.deleteSql(_table_name(collection))
      val stmt = _prepare_statement(conn, sql)
      try {
        stmt.setString(1, id.print)
        stmt.executeUpdate()
      } finally {
        stmt.close()
      }
    }

  private def _select(
    conn: Connection,
    collection: CollectionId,
    id: EntryId
  ): Consequence[Option[Record]] = {
    val sql = dialect.selectByIdSql(_table_name(collection))
    _select_with_sql(conn, collection, id, sql)
  }

  private def _select_with_sql(
    conn: Connection,
    collection: CollectionId,
    id: EntryId,
    sql: String
  ): Consequence[Option[Record]] =
    Consequence {
      val stmt = _prepare_statement(conn, sql)
      try {
        stmt.setString(1, id.print)
        val rs = stmt.executeQuery()
        try {
          if (rs.next())
            Some(_record_from_result_set(rs))
          else
            None
        } finally {
          rs.close()
        }
      } finally {
        stmt.close()
      }
    }

  private def _search_empty(
    conn: Connection,
    collection: CollectionId,
    directive: QueryDirective
  ): Consequence[SearchResult] =
    for {
      _ <- _ensure_query_columns(conn, collection, directive.query)
      r <- Consequence {
      val sql = _search_sql(collection, directive)
      val stmt = _prepare_statement(conn, sql.sql)
      try {
        _bind(stmt, sql.params)
        val rs = stmt.executeQuery()
        try {
          val buf = Vector.newBuilder[Record]
          while (rs.next()) {
            buf += _record_from_result_set(rs)
          }
          val records = buf.result()
          val range = directive.limit match {
            case QueryLimit.Unbounded => ResultRange.Exact
            case QueryLimit.Limit(n) => ResultRange.Limited(n)
          }
          SearchResult(records, range, None)
        } finally {
          rs.close()
        }
      } finally {
        stmt.close()
      }
      }
    } yield r

  private def _search_sql(
    collection: CollectionId,
    directive: QueryDirective
  ): SqlDataStore.SqlStatement = {
    val select = directive.projection match {
      case QueryProjection.All =>
        "*"
      case QueryProjection.Fields(names) if names.isEmpty =>
        "*"
      case QueryProjection.Fields(names) =>
        val normalized = names.map(_column_name).distinct
        ("id" +: normalized.filterNot(_ == "id")).map(dialect.quoteIdentifier).mkString(", ")
    }
    val order = directive.order match {
      case QueryOrder.None =>
        ""
      case QueryOrder.By(field, OrderDirection.Asc) =>
        s" ORDER BY ${dialect.quoteIdentifier(_column_name(field))} ASC"
      case QueryOrder.By(field, OrderDirection.Desc) =>
        s" ORDER BY ${dialect.quoteIdentifier(_column_name(field))} DESC"
    }
    val limit = directive.limit match {
      case QueryLimit.Unbounded =>
        ""
      case QueryLimit.Limit(n) =>
        s" LIMIT ${math.max(0, n)}"
    }
    val offset =
      if (directive.offset <= 0)
        ""
      else if (limit.nonEmpty)
        s" OFFSET ${directive.offset}"
      else
        s" LIMIT -1 OFFSET ${directive.offset}"
    val where = _where_sql(directive.query)
    SqlDataStore.SqlStatement(
      s"SELECT $select FROM ${dialect.quoteIdentifier(_table_name(collection))}${where.sql}$order$limit$offset",
      where.params
    )
  }

  private def _count(
    conn: Connection,
    collection: CollectionId,
    directive: QueryDirective
  ): Consequence[Int] =
    for {
      _ <- _ensure_query_columns(conn, collection, directive.query)
      r <- Consequence {
      val sql = _count_sql(collection, directive)
      val stmt = _prepare_statement(conn, sql.sql)
      try {
        _bind(stmt, sql.params)
        val rs = stmt.executeQuery()
        try {
          if (rs.next()) rs.getInt(1) else 0
        } finally {
          rs.close()
        }
      } finally {
        stmt.close()
      }
      }
    } yield r

  private def _ensure_query_columns(
    conn: Connection,
    collection: CollectionId,
    query: Query
  ): Consequence[Unit] = {
    val columns = _query_columns(query)
      .map(_column_name)
      .distinct
      .filterNot(_ == "id")
      .map(_ -> "")
    if (columns.isEmpty)
      Consequence.unit
    else
      _ensure_table(conn, collection, columns)
  }

  private def _query_columns(
    query: Query
  ): Vector[String] =
    query match {
      case Query.Empty => Vector.empty
      case Query.Expr(expr) => _expr_columns(expr)
    }

  private def _expr_columns(
    expr: EntityQuery.Expr
  ): Vector[String] =
    expr match {
      case EntityQuery.True | EntityQuery.False => Vector.empty
      case EntityQuery.And(items) => items.flatMap(_expr_columns)
      case EntityQuery.Or(items) => items.flatMap(_expr_columns)
      case EntityQuery.Not(item) => _expr_columns(item)
      case EntityQuery.Eq(path, _) => Vector(path)
      case EntityQuery.Ne(path, _) => Vector(path)
      case EntityQuery.Gt(path, _) => Vector(path)
      case EntityQuery.Gte(path, _) => Vector(path)
      case EntityQuery.Lt(path, _) => Vector(path)
      case EntityQuery.Lte(path, _) => Vector(path)
      case EntityQuery.Contains(path, _, _) => Vector(path)
      case EntityQuery.StartsWith(path, _, _) => Vector(path)
      case EntityQuery.EndsWith(path, _, _) => Vector(path)
      case EntityQuery.Like(path, _, _) => Vector(path)
      case EntityQuery.IsNull(path) => Vector(path)
      case EntityQuery.IsNotNull(path) => Vector(path)
      case _ => Vector.empty
    }

  private def _count_sql(
    collection: CollectionId,
    directive: QueryDirective
  ): SqlDataStore.SqlStatement = {
    val where = _where_sql(directive.query)
    SqlDataStore.SqlStatement(
      s"SELECT COUNT(*) FROM ${dialect.quoteIdentifier(_table_name(collection))}${where.sql}",
      where.params
    )
  }

  private def _where_sql(query: Query): SqlDataStore.SqlStatement =
    query match {
      case Query.Empty =>
        SqlDataStore.SqlStatement.empty
      case Query.Expr(expr) =>
        _expr_sql(expr) match {
          case SqlDataStore.SqlStatement.Empty => SqlDataStore.SqlStatement.empty
          case x => x.copy(sql = s" WHERE ${x.sql}")
        }
    }

  private def _expr_sql(expr: EntityQuery.Expr): SqlDataStore.SqlStatement =
    expr match {
      case EntityQuery.True =>
        SqlDataStore.SqlStatement.Empty
      case EntityQuery.False =>
        SqlDataStore.SqlStatement("1 = 0")
      case EntityQuery.And(items) =>
        _join_expr(items, "AND")
      case EntityQuery.Or(items) =>
        _join_expr(items, "OR")
      case EntityQuery.Not(item) =>
        val x = _expr_sql(item)
        if (x.isEmpty) SqlDataStore.SqlStatement.Empty else x.copy(sql = s"NOT (${x.sql})")
      case EntityQuery.Eq(path, value) =>
        _binary(path, "=", value)
      case EntityQuery.Ne(path, value) =>
        _binary(path, "<>", value)
      case EntityQuery.Gt(path, value) =>
        _binary(path, ">", value)
      case EntityQuery.Gte(path, value) =>
        _binary(path, ">=", value)
      case EntityQuery.Lt(path, value) =>
        _binary(path, "<", value)
      case EntityQuery.Lte(path, value) =>
        _binary(path, "<=", value)
      case EntityQuery.Contains(path, value, caseinsensitive) =>
        _like(path, s"%$value%", caseinsensitive)
      case EntityQuery.StartsWith(path, value, caseinsensitive) =>
        _like(path, s"$value%", caseinsensitive)
      case EntityQuery.EndsWith(path, value, caseinsensitive) =>
        _like(path, s"%$value", caseinsensitive)
      case EntityQuery.Like(path, pattern, caseinsensitive) =>
        _like(path, pattern, caseinsensitive)
      case EntityQuery.IsNull(path) =>
        SqlDataStore.SqlStatement(s"${_column_ref(path)} IS NULL")
      case EntityQuery.IsNotNull(path) =>
        SqlDataStore.SqlStatement(s"${_column_ref(path)} IS NOT NULL")
      case _ =>
        SqlDataStore.SqlStatement.Empty
    }

  private def _join_expr(
    items: Vector[EntityQuery.Expr],
    op: String
  ): SqlDataStore.SqlStatement = {
    val xs = items.map(_expr_sql).filterNot(_.isEmpty)
    if (xs.isEmpty)
      SqlDataStore.SqlStatement.Empty
    else
      SqlDataStore.SqlStatement(
        xs.map(x => s"(${x.sql})").mkString(s" $op "),
        xs.flatMap(_.params)
      )
  }

  private def _binary(path: String, op: String, value: Any): SqlDataStore.SqlStatement =
    SqlDataStore.SqlStatement(s"${_column_ref(path)} $op ?", Vector(_column_value(value)))

  private def _like(path: String, pattern: String, caseinsensitive: Boolean): SqlDataStore.SqlStatement =
    if (caseinsensitive)
      SqlDataStore.SqlStatement(s"LOWER(${_column_ref(path)}) LIKE LOWER(?)", Vector(pattern))
    else
      SqlDataStore.SqlStatement(s"${_column_ref(path)} LIKE ?", Vector(pattern))

  private def _column_ref(path: String): String =
    dialect.quoteIdentifier(_column_name(path))

  private def _bind(stmt: PreparedStatement, params: Vector[Any]): Unit =
    params.zipWithIndex.foreach { case (value, index) =>
      stmt.setObject(index + 1, value)
    }

  private def _record_from_result_set(
    rs: java.sql.ResultSet
  ): Record = {
    val md = rs.getMetaData
    val count = md.getColumnCount
    val values = (1 to count).toVector.map { i =>
      val rawname = md.getColumnLabel(i)
      val name =
        if (
          SimpleEntityStorageShapePolicy
            .isConcurrencyRevisionStorageField(rawname)
        )
          rawname
        else if (config.normalizeColumnNames)
          _to_property_name(rawname)
        else
          rawname
      val value = _decode_column_value(rs.getObject(i))
      name -> value
    }
    Record.create(values)
  }

  private def _column_name(name: String): String =
    if (config.normalizeColumnNames) _to_column_name(name) else name

  private def _to_column_name(name: String): String =
    RecordKeyNaming.toSnakeColumnName(name)

  private def _to_property_name(name: String): String =
    RecordKeyNaming.toCanonicalCamelName(name)
}

object SqlDataStore {
  sealed trait DialectSelection {
    def resolve(jdbcUrl: String): SqlDialectDriver
  }
  case object Auto extends DialectSelection {
    def resolve(jdbcUrl: String): SqlDialectDriver =
      dialectFromJdbcUrl(jdbcUrl) match {
        case Auto => throw new IllegalArgumentException(s"Unsupported JDBC dialect: $jdbcUrl")
        case other => other.resolve(jdbcUrl)
      }
  }
  case object Sqlite extends DialectSelection {
    def resolve(jdbcUrl: String): SqlDialectDriver = SqliteDialectDriver
  }
  case object Mysql extends DialectSelection {
    def resolve(jdbcUrl: String): SqlDialectDriver = MySqlDialectDriver
  }

  final case class SqlStatement(
    sql: String,
    params: Vector[Any] = Vector.empty
  ) {
    def isEmpty: Boolean = sql.isEmpty
  }

  object SqlStatement {
    val Empty: SqlStatement = SqlStatement("")
    val empty: SqlStatement = Empty
  }

  final case class Config(
    normalizeColumnNames: Boolean = false
  )

  def dialectFromJdbcUrl(jdbcUrl: String): DialectSelection = {
    val lower = Option(jdbcUrl).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)
    if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:"))
      Mysql
    else if (lower.startsWith("jdbc:sqlite:"))
      Sqlite
    else
      Auto
  }

  def jdbc(
    jdbcUrl: String,
    dialect: DialectSelection = Auto,
    username: Option[String] = None,
    password: Option[String] = None,
    driverClassName: Option[String] = None,
    recorder: CommitRecorder = CommitRecorder.noop,
    config: Config = Config()
  ): SqlDataStore =
    _or_throw(jdbcC(jdbcUrl, dialect, username, password, driverClassName, recorder, config))

  /** Structured alternative to the legacy throwing JDBC factory. */
  def jdbcC(
    jdbcUrl: String,
    dialect: DialectSelection = Auto,
    username: Option[String] = None,
    password: Option[String] = None,
    driverClassName: Option[String] = None,
    recorder: CommitRecorder = CommitRecorder.noop,
    config: Config = Config()
  ): Consequence[SqlDataStore] =
    Consequence {
    val resolveddialect = dialect.resolve(jdbcUrl)
    val hikariconfig = new HikariConfig()
    hikariconfig.setJdbcUrl(jdbcUrl)
    username.foreach(hikariconfig.setUsername)
    password.foreach(hikariconfig.setPassword)
    driverClassName.foreach(hikariconfig.setDriverClassName)
    _configure_sqlite_transactions(hikariconfig, resolveddialect)
    hikariconfig.setMaximumPoolSize(4)
    val datasource = new HikariDataSource(hikariconfig)
    _owned(resolveddialect, datasource, recorder, config)
  }

  def sqlite(
    path: String,
    recorder: CommitRecorder = CommitRecorder.noop,
    config: Config = Config()
  ): SqlDataStore =
    _or_throw(sqliteC(path, recorder, config))

  /** Structured alternative to the legacy throwing SQLite factory. */
  def sqliteC(
    path: String,
    recorder: CommitRecorder = CommitRecorder.noop,
    config: Config = Config()
  ): Consequence[SqlDataStore] =
    Consequence {
    val hikariconfig = new HikariConfig()
    val jdbcurl =
      if (path == ":memory:")
        "jdbc:sqlite::memory:"
      else
        s"jdbc:sqlite:$path"
    hikariconfig.setJdbcUrl(jdbcurl)
    hikariconfig.setDriverClassName("org.sqlite.JDBC")
    _configure_sqlite_transactions(
      hikariconfig,
      SqliteDialectDriver
    )
    hikariconfig.setMaximumPoolSize(4)
    val datasource = new HikariDataSource(hikariconfig)
    _owned(SqliteDialectDriver, datasource, recorder, config)
  }

  /**
   * Explicit infrastructure ownership transfer for an already-created
   * datasource. The primary constructor intentionally remains non-owning.
   */
  def owning(
    dialect: SqlDialectDriver,
    datasource: DataSource,
    close: () => Unit,
    recorder: CommitRecorder = CommitRecorder.noop,
    config: Config = Config()
  ): SqlDataStore =
    _owned(dialect, datasource, recorder, config, close)

  /**
   * Creates an unpublished resource for SystemNode registration.  The caller
   * transfers it to the registry; a datastore view created from the resource
   * is deliberately non-owning.
   */
  private[cncf] def managedJdbcResourceC(
    jdbcUrl: String,
    dialect: DialectSelection = Auto,
    username: Option[String] = None,
    password: Option[String] = None,
    driverClassName: Option[String] = None
  ): Consequence[ManagedSqlDataStoreResource] =
    Consequence {
      val resolveddialect = dialect.resolve(jdbcUrl)
      val hikariconfig = new HikariConfig()
      hikariconfig.setJdbcUrl(jdbcUrl)
      username.foreach(hikariconfig.setUsername)
      password.foreach(hikariconfig.setPassword)
      driverClassName.foreach(hikariconfig.setDriverClassName)
      _configure_sqlite_transactions(hikariconfig, resolveddialect)
      hikariconfig.setMaximumPoolSize(4)
      val datasource = new HikariDataSource(hikariconfig)
      ManagedSqlDataStoreResource.hikari(datasource, () => datasource.close())
    }

  private[cncf] def managedSqliteResourceC(
    path: String
  ): Consequence[ManagedSqlDataStoreResource] =
    managedJdbcResourceC(
      jdbcUrl = if (path == ":memory:") "jdbc:sqlite::memory:" else s"jdbc:sqlite:$path",
      dialect = Sqlite,
      driverClassName = Some("org.sqlite.JDBC")
    )

  private[cncf] def managedView(
    resource: ManagedSqlDataStoreResource,
    jdbcUrl: String,
    dialect: DialectSelection,
    admission: () => Consequence[Unit],
    recorder: CommitRecorder = CommitRecorder.noop,
    config: Config = Config()
  ): SqlDataStore =
    val store = new SqlDataStore(dialect.resolve(jdbcUrl), resource.datasource, recorder, config)
    store.useManagedResource(resource, admission)
    store

  private def _owned(
    dialect: SqlDialectDriver,
    datasource: DataSource,
    recorder: CommitRecorder,
    config: Config
  ): SqlDataStore =
    _owned(dialect, datasource, recorder, config, () => datasource match {
      case hikari: HikariDataSource => hikari.close()
      case _ => throw new IllegalStateException("owned SQL datasource requires an explicit close action")
    })

  private def _owned(
    dialect: SqlDialectDriver,
    datasource: DataSource,
    recorder: CommitRecorder,
    config: Config,
    close: () => Unit
  ): SqlDataStore = {
    val resource = ManagedSqlDataStoreResource.hikari(datasource, close)
    ManagedSqlDataStoreResource.closeUnpublished(resource) {
      val store = new SqlDataStore(dialect, datasource, recorder, config)
      store.adoptManagedResource(resource)
      store
    }
  }

  private def _or_throw(result: Consequence[SqlDataStore]): SqlDataStore =
    result match {
      case Consequence.Success(store) => store
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
    }

  private def _configure_sqlite_transactions(
    hikariconfig: HikariConfig,
    resolveddialect: SqlDialectDriver
  ): Unit =
    if (resolveddialect.name == SqliteDialectDriver.name) {
      hikariconfig.addDataSourceProperty("busy_timeout", "10000")
      hikariconfig.addDataSourceProperty("transaction_mode", "IMMEDIATE")
    }
}
