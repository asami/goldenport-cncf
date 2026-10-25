package org.goldenport.cncf.datastore.sql

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import scala.util.control.NonFatal
import javax.sql.DataSource
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.record.{Field, Record}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{EventHistoryCorrelation, ManagedEventHistory}
import org.goldenport.cncf.datastore.{
  DataStore,
  DataStoreSpace,
  DataStoreObservationTarget,
  EntityObservationDataStore,
  DataStoreConditionalMultiGuardPlan,
  DataStoreMultiGuardAcknowledgement,
  DataStoreMultiGuardReadback,
  DataStoreMultiGuardRecord,
  DataStoreConditionalRoot,
  DataStoreConditionalSuccessor,
  DataStoreConditionalTransitionCheckpoint,
  DataStoreConditionalTransitionFailure,
  DataStoreConditionalTransitionPlan,
  DataStoreConditionalTransitionResult,
  EntityConditionalUpdateDataStore,
  EntityVerifiedMultiGuardDataStore,
  EntityRetainedEventDataStore,
  EntityTransientEventDataStore,
  EntityConditionalTransitionSupport,
  EntityCreateCheckpoint,
  EntityCreateDataStore,
  EntityCreatePlan,
  EntityCreateResult,
  EntityCreateSupport,
  EntityCompareAndSetMutationPlan,
  EntityCompareAndDeletePlan,
  EntityCompareAndDeleteResult,
  EntityDeleteRootMutation,
  EntityDirectMutationPlan,
  EntityMutationExclusionGuard,
  EntityMutationProviderCapabilities,
  EntityMutationProviderFeature,
  EntityMutationProviderReadback,
  EntityMutationProviderResult,
  EntityMutationReadbackRequirement,
  EntityNativeMutationSupport,
  EntityObservedDeleteSupport,
  EntityVersionedMutationCheckpoint,
  EntityVersionedMutationDataStore,
  EntityVersionedMutationFailure,
  EntityVersionedMutationPlan,
  EntityVersionedMutationResult,
  EntityVersionedRootMutation,
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
import org.simplemodeling.model.directive.Update

/*
 * @since   Mar. 12, 2026
 *  version Mar. 19, 2026
 *  version Mar. 31, 2026
 *  version May.  8, 2026
 *  version May. 26, 2026
 *  version Jul. 26, 2026
 *  version Sep. 29, 2026
 * @version Oct. 25, 2026
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
    with EntityCreateDataStore
    with EntityConditionalUpdateDataStore
    with ManagedEventHistory.Provider {
  import DataStore.*
  private val _record_codec = new SqlDataStoreRecordCodec(config, dialect eq PostgreSqlDialectDriver)
  private val _event_history_columns = Vector("id", "event_json", EventHistoryCorrelation.namespaceField,
    EventHistoryCorrelation.keyField, EventHistoryCorrelation.nameField, EventHistoryCorrelation.sequenceField)
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

  /** A non-owning verified view is available only for borrowed managed SQLite.
    * It grants no invocation authority and never changes the ordinary view.
    */
  private[cncf] def managedMultiGuardVerifiedViewC(): Consequence[SqlDataStore] =
    (_managed_borrow_resource, _managed_borrow_admission) match {
      case (Some(resource), Some(admission)) if dialect.name == SqliteDialectDriver.name =>
        admission().map { _ =>
          val view = new SqlDataStore(dialect, resource.datasource, recorder, config)
              with EntityVerifiedMultiGuardDataStore with EntityObservationDataStore with EntityRetainedEventDataStore with EntityTransientEventDataStore {
            override protected def commit_transient_events(inv: DataStoreSpace.TransientEventInvocation)
                (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] =
              commitTransientEventsManagedC(inv)
            override protected def readback_transient_events(inv: DataStoreSpace.TransientEventInvocation)
                (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] =
              readbackTransientEventsManagedC(inv)
            override protected def commit_retained_events(inv: DataStoreSpace.RetainedEventInvocation)
                (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] =
              commitRetainedEventsManagedC(inv)
            override protected def readback_retained_events(inv: DataStoreSpace.RetainedEventInvocation)
                (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] =
              readbackRetainedEventsManagedC(inv)
            override private[datastore] def supportsPreparedEvents: Boolean = true
            override protected def observe_native(invocation: DataStoreSpace.ObservationInvocation)
                (using ExecutionContext): Consequence[Vector[(DataStoreObservationTarget, Option[Record])]] =
              observeDeclaredSnapshotC(invocation)
            override protected def commit_multi_guard_for_readback(plan: DataStoreConditionalMultiGuardPlan)
                (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] =
              Consequence.configurationInvalid("Managed multi-guard requires a host invocation")
            override protected def commit_multi_guard_admitted(invocation: DataStoreSpace.MultiGuardInvocation)
                (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] =
              conditionalMultiGuardAcknowledgedManaged(invocation)
            override protected def readback_multi_guard(invocation: DataStoreSpace.MultiGuardInvocation)
                (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] =
              readbackMultiGuardSnapshotC(invocation)
          }
          view.useManagedResource(resource, admission)
          view
        }
      case _ => Consequence.configurationInvalid("Verified multi-guard requires borrowed managed SQLite")
    }

  def isAccept(cid: CollectionId): Boolean = true

  override def entityMutationProviderCapabilities
      : EntityMutationProviderCapabilities =
    EntityMutationProviderCapabilities.guardedBaseline.copy(
      features =
        EntityMutationProviderCapabilities.guardedBaseline.features ++
          Set(
            EntityMutationProviderFeature.DirectAlwaysWrite,
            EntityMutationProviderFeature.OptimisticCompareAndSet,
            EntityMutationProviderFeature.OptimisticCompareAndDelete,
            EntityMutationProviderFeature.AuthoritativeDeleteResult
          ) ++ (if (_entity_create_supported) Set(EntityMutationProviderFeature.AtomicCreate) else Set.empty)
    )

  private def _entity_create_supported: Boolean =
    (dialect eq SqliteDialectDriver) || (dialect eq MySqlDialectDriver) || _postgresql

  private def _postgresql: Boolean = dialect eq PostgreSqlDialectDriver

  override def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext): Consequence[EntityCreateResult] =
    for {
      _ <- EntityCreateSupport.validate(plan)
      _ <- EntityCreateSupport.requireCapabilities(entityMutationProviderCapabilities, plan.sideEffects.nonEmpty)
      _ <- if (_entity_create_supported) Consequence.unit else
        EntityNativeMutationSupport.unsupported("entity-create", EntityMutationProviderFeature.AtomicCreate)
      _ <- _validate_create_columns(plan.record)
      _ <- plan.sideEffects.foldLeft(Consequence.unit) { (result, effect) =>
        result.flatMap(_ => effect match {
          case _: EntityVersionedSideEffect.Create => EntityCreateSupport.unsupportedSideEffect
          case EntityVersionedSideEffect.Save(_, _, record) => _validate_create_columns(record)
          case _: EntityVersionedSideEffect.Delete => Consequence.unit
        })
      }
      result <- _with_managed_datasource { source =>
        // Schema preparation owns no row writes, and precedes MySQL's data transaction.
        if (_postgresql)
          _with_entity_create(source, connection =>
            _prepare_entity_create(connection, plan).flatMap(addresses => _execute_entity_create(connection, plan, addresses)))
        else
          _with_connection(source, connection => _prepare_entity_create(connection, plan)).flatMap { addresses =>
            _with_entity_create(source, connection => _execute_entity_create(connection, plan, addresses))
          }
      }
    } yield result

  protected def create_EntityCheckpoint(plan: EntityCreatePlan, checkpoint: EntityCreateCheckpoint): Consequence[Unit] =
    Consequence.unit

  private def _validate_create_columns(record: Record): Consequence[Unit] = {
    val columns = record.fields.filterNot(_.key == "id").map(field =>
      if (_postgresql) _column_name(field.key) else _create_identifier_key(_column_name(field.key)))
    if (columns.contains("id"))
      Consequence.argumentPolicyViolation("record", "entity-create.sql-identity", "fields excluding physical id aliases", "id column alias")
    else if (columns.distinct.size != columns.size)
      Consequence.argumentInvalid("record", "unique SQL columns", "normalized column aliases")
    else Consequence.unit
  }

  private def _create_identifier_key(name: String): String =
    name.map(character => if (character >= 'A' && character <= 'Z') (character + 32).toChar else character)

  private def _create_query[A](connection: Connection, sql: String, values: Vector[String])(
    read: java.sql.ResultSet => A
  ): Consequence[A] = Consequence {
    val statement = _prepare_statement(connection, sql)
    try {
      values.zipWithIndex.foreach { case (value, index) => statement.setString(index + 1, value) }
      val rows = statement.executeQuery()
      try read(rows) finally rows.close()
    } finally statement.close()
  }

  private def _resolve_create_table(connection: Connection, collection: CollectionId): Consequence[SqlDataStore.EntityCreateAddress] = {
    if (_postgresql) return _pg_address(connection, collection)
    val requested = _table_name(collection)
    val mode = if (dialect eq MySqlDialectDriver)
      _create_query(connection, "SELECT @@lower_case_table_names", Vector.empty)(rows => { rows.next(); rows.getInt(1) })
    else Consequence.success(1)
    mode.flatMap { lowercase =>
      val sql = if (dialect eq SqliteDialectDriver)
        "SELECT name FROM sqlite_master WHERE type='table' AND name = ? COLLATE NOCASE"
      else if (lowercase == 0)
        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND BINARY TABLE_NAME=BINARY ?"
      else
        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND BINARY LOWER(TABLE_NAME)=BINARY LOWER(?)"
      _create_query(connection, sql, Vector(requested)) { rows =>
        val names = Vector.newBuilder[String]
        while (rows.next()) names += rows.getString(1)
        names.result()
      }.flatMap {
        case Vector(name) => Consequence.success(SqlDataStore.EntityCreateAddress(name, true))
        case Vector() => Consequence.success(SqlDataStore.EntityCreateAddress(
          if ((dialect eq SqliteDialectDriver) || lowercase == 0) requested else _create_identifier_key(requested), false))
        case _ => _create_schema_failure("Ambiguous native table identity")
      }
    }
  }

  private def _prepare_entity_create(
    connection: Connection,
    plan: EntityCreatePlan
  ): Consequence[Vector[SqlDataStore.EntityCreateAddress]] = {
    if (_postgresql) return _pg_prepare_create(connection, plan)
    val collections = plan.collection +: plan.sideEffects.map(_.collection)
    for {
      _ <- (plan.record +: plan.sideEffects.collect { case save: EntityVersionedSideEffect.Save => save.record })
        .foldLeft(Consequence.unit) { (result, record) => result.flatMap { _ =>
          val names = "id" +: record.fields.filterNot(_.key == "id").map(field => _column_name(field.key))
          _create_column_names_unique(connection, names).flatMap { unique =>
            if (unique) Consequence.unit else Consequence.argumentInvalid("record", "unique native SQL columns", "native column aliases")
          }
        } }
      addresses <- collections.foldLeft(Consequence.success(Vector.empty[SqlDataStore.EntityCreateAddress])) { (result, collection) =>
        result.flatMap(values => _resolve_create_table(connection, collection).map(values :+ _))
      }
      _ <- _prepare_create_record(connection, addresses.head, plan.record)
      _ <- plan.sideEffects.zip(addresses.tail).foldLeft(Consequence.unit) { case (result, (effect, address)) =>
        result.flatMap(_ => effect match {
          case _: EntityVersionedSideEffect.Create => EntityCreateSupport.unsupportedSideEffect
          case EntityVersionedSideEffect.Save(_, _, record) => _prepare_create_record(connection, address, record)
          case _: EntityVersionedSideEffect.Delete => Consequence.unit
        })
      }
      resolved <- collections.foldLeft(Consequence.success(Vector.empty[SqlDataStore.EntityCreateAddress])) { (result, collection) =>
        result.flatMap(values => _resolve_create_table(connection, collection).map(values :+ _))
      }
      _ <- _validate_create_addresses(connection, plan, resolved)
    } yield resolved
  }

  private def _prepare_create_record(connection: Connection, address: SqlDataStore.EntityCreateAddress, record: Record): Consequence[Unit] =
    _resolve_create_table(connection, CollectionId.Instance(address.table)).flatMap { resolved =>
      _create_record_columns(connection, resolved, record).flatMap(columns =>
        _ensure_table(connection, CollectionId.Instance(resolved.table), columns))
    }

  private def _create_column_names_unique(connection: Connection, names: Vector[String]): Consequence[Boolean] =
    if (_postgresql)
      names.foldLeft(Consequence.success(Vector.empty[String])) { (result, name) =>
        result.flatMap(values => _pg_name(connection, name).map(values :+ _))
      }.map(values => values.distinct.size == values.size)
    else if (dialect eq SqliteDialectDriver) Consequence.success(names.map(_create_identifier_key).distinct.size == names.size)
    else {
      // A derived table applies MySQL's own identifier equality without DDL or row effects.
      val projections = names.map(name => s"NULL AS ${dialect.quoteIdentifier(name)}").mkString(", ")
      _create_query(connection, s"SELECT * FROM (SELECT $projections) AS cncf_entity_create_columns WHERE 1=0", Vector.empty)(_ => true)
        .recoverWith { conclusion =>
          if (conclusion.getException.exists {
            case error: SQLException => error.getErrorCode == 1060
            case _ => false
          }) Consequence.success(false)
          else Consequence.Failure(conclusion)
        }
    }

  private def _create_record_columns(
    connection: Connection,
    address: SqlDataStore.EntityCreateAddress,
    record: Record
  ): Consequence[Vector[(String, Any)]] = {
    if (_postgresql) return _pg_record_columns(connection, CollectionId.Instance(address.table), record)
    val columns = _record_columns(record)
    val collection = CollectionId.Instance(address.table)
    _table_exists(connection, collection).flatMap { exists =>
      if (!exists) Consequence.success(columns)
      else _existing_columns(connection, collection).flatMap { existing =>
        if (existing.groupBy(_create_identifier_key).exists(_._2.size != 1)) _create_schema_failure("Ambiguous native column identity")
        else columns.foldLeft(Consequence.success(Vector.empty[(String, Any)])) { case (result, (name, value)) =>
          result.flatMap { values =>
            existing.toVector.foldLeft(Consequence.success(Option.empty[String])) { (matched, candidate) =>
              matched.flatMap {
                case selected @ Some(_) => Consequence.success(selected)
                case None =>
                  if (candidate == name) Consequence.success(Some(candidate))
                  else _create_column_names_unique(connection, Vector(candidate, name)).map(unique => if (unique) None else Some(candidate))
              }
            }.map(selected => values :+ (selected.getOrElse(name) -> value))
          }
        }
      }
    }
  }

  private def _validate_create_addresses(
    connection: Connection,
    plan: EntityCreatePlan,
    addresses: Vector[SqlDataStore.EntityCreateAddress]
  ): Consequence[Unit] = {
    val entries = plan.entryId +: plan.sideEffects.map(_.entryId)
    val pairs = for { left <- addresses.indices.toVector; right <- (left + 1 until addresses.size).toVector
      if addresses(left)._same_relation(addresses(right)) } yield (left, right)
    pairs.foldLeft(Consequence.unit) { case (result, (left, right)) =>
      result.flatMap { _ =>
        _create_id_collation(connection, addresses(left)).flatMap { collation =>
          _create_ids_equal(connection, entries(left).print, entries(right).print, collation).flatMap { equal =>
            if (!equal) Consequence.unit
            else if (left == 0) EntityCreateSupport.aliasedTarget else EntityCreateSupport.repeatedTarget
          }
        }
      }
    }
  }

  private def _create_id_collation(
    connection: Connection,
    address: SqlDataStore.EntityCreateAddress
  ): Consequence[SqlDataStore.EntityCreateCollation] = {
    if (_postgresql) _pg_id_collation(connection, address)
    else if (dialect eq SqliteDialectDriver) {
      if (!address.exists) Consequence.success(SqlDataStore.EntityCreateCollation(None, "BINARY"))
      else _create_query(connection, s"PRAGMA table_info(${dialect.quoteIdentifier(address.table)})", Vector.empty) { rows =>
        val columns = Vector.newBuilder[(String, String, Int)]
        while (rows.next()) columns += ((rows.getString("name"), rows.getString("type"), rows.getInt("pk")))
        columns.result()
      }.flatMap { columns =>
        val primary = columns.filter(_._3 > 0)
        if (primary.size != 1 || _create_identifier_key(primary.head._1) != "id" || !primary.head._2.equalsIgnoreCase("TEXT"))
          _create_schema_failure("Entity create requires the native text id primary key")
        else _create_query(connection, s"PRAGMA index_list(${dialect.quoteIdentifier(address.table)})", Vector.empty) { rows =>
          val indexes = Vector.newBuilder[String]
          while (rows.next()) if (rows.getString("origin") == "pk") indexes += rows.getString("name")
          indexes.result()
        }.flatMap {
          case Vector(index) => _create_query(connection, s"PRAGMA index_xinfo(${dialect.quoteIdentifier(index)})", Vector.empty) { rows =>
            val collations = Vector.newBuilder[String]
            while (rows.next()) if (rows.getInt("key") == 1 && _create_identifier_key(rows.getString("name")) == "id")
              collations += rows.getString("coll")
            collations.result()
          }.flatMap {
            case Vector(collation) if collation != null => Consequence.success(SqlDataStore.EntityCreateCollation(None, collation))
            case _ => _create_schema_failure("Native id collation is unavailable")
          }
          case _ => _create_schema_failure("Native id primary-key index is unavailable")
        }
      }
    } else {
      val sql = if (address.exists)
        "SELECT CHARACTER_SET_NAME,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND BINARY TABLE_NAME=BINARY ? AND COLUMN_NAME='id' AND COLUMN_KEY='PRI'"
      else "SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=DATABASE()"
      _create_query(connection, sql, if (address.exists) Vector(address.table) else Vector.empty) { rows =>
        val values = Vector.newBuilder[SqlDataStore.EntityCreateCollation]
        while (rows.next()) values += SqlDataStore.EntityCreateCollation(Option(rows.getString(1)), rows.getString(2))
        values.result()
      }.flatMap {
        case Vector(value) if value.charset.exists(_matches_create_catalog_identifier) && _matches_create_catalog_identifier(value.collation) =>
          if (!address.exists) Consequence.success(value)
          else _create_query(connection,
            "SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND BINARY TABLE_NAME=BINARY ? AND INDEX_NAME='PRIMARY' ORDER BY SEQ_IN_INDEX",
            Vector(address.table)) { rows =>
            val keys = Vector.newBuilder[String]
            while (rows.next()) keys += rows.getString(1)
            keys.result()
          }.flatMap {
            case Vector(name) if _create_identifier_key(name) == "id" => Consequence.success(value)
            case _ => _create_schema_failure("Native id primary key is inconsistent")
          }
        case _ => _create_schema_failure("Native id charset/collation is unavailable")
      }
    }
  }

  private def _matches_create_catalog_identifier(value: String): Boolean =
    value != null && value.nonEmpty && value.forall(character => character.isLetterOrDigit && character <= 127 || character == '_')

  private def _create_ids_equal(connection: Connection, left: String, right: String, collation: SqlDataStore.EntityCreateCollation): Consequence[Boolean] = {
    if (_postgresql) {
      val identifier = collation.namespace.map(PostgreSqlDialectDriver.quoteIdentifier).map(_ + ".").getOrElse("") +
        PostgreSqlDialectDriver.quoteIdentifier(collation.collation)
      return _create_query(connection, s"SELECT (CAST(? AS TEXT) COLLATE $identifier) = (CAST(? AS TEXT) COLLATE $identifier)",
        Vector(left, right))(rows => { rows.next(); rows.getBoolean(1) })
    }
    val expression = collation.charset match {
      case Some(charset) => s"CONVERT(? USING ${dialect.quoteIdentifier(charset)}) COLLATE ${dialect.quoteIdentifier(collation.collation)}"
      case None => s"? COLLATE ${dialect.quoteIdentifier(collation.collation)}"
    }
    _create_query(connection, s"SELECT $expression = $expression", Vector(left, right))(rows => { rows.next(); rows.getBoolean(1) })
  }

  private def _create_schema_failure[A](message: String): Consequence[A] =
    Consequence.configurationInvalid(message)

  private def _pg_name(connection: Connection, name: String): Consequence[String] =
    _create_query(connection, "SELECT CAST(? AS pg_catalog.name)::text", Vector(name))(rows => { rows.next(); rows.getString(1) })

  private def _pg_address(connection: Connection, collection: CollectionId): Consequence[SqlDataStore.EntityCreateAddress] =
    _create_query(connection,
      "SELECT n.nspname,c.relname,c.oid::bigint,c.relkind::text FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE c.oid=pg_catalog.to_regclass(?)",
      Vector(PostgreSqlDialectDriver.quoteIdentifier(_table_name(collection)))) { rows =>
      val addresses = Vector.newBuilder[(String, String, Long, String)]
      while (rows.next()) addresses += ((rows.getString(1), rows.getString(2), rows.getLong(3), rows.getString(4)))
      addresses.result()
    }.flatMap {
      case Vector((schema, table, oid, "r")) =>
        Consequence.success(SqlDataStore.EntityCreateAddress(table, true, Some(schema), Some(oid)))
      case Vector() => for {
        schema <- _create_query(connection, "SELECT pg_catalog.current_schema()", Vector.empty)(rows => { rows.next(); Option(rows.getString(1)) })
        admitted <- schema.map(Consequence.success).getOrElse(_create_schema_failure[String]("PostgreSQL current schema is unavailable"))
        name <- _pg_name(connection, _table_name(collection))
      } yield SqlDataStore.EntityCreateAddress(name, false, Some(admitted))
      case _ => _create_schema_failure("Unsupported or ambiguous PostgreSQL relation")
    }

  private def _pg_table_sql(connection: Connection, collection: CollectionId): String =
    _pg_address(connection, collection).take._qualified_sql

  private def _pg_lock_address(connection: Connection, collection: CollectionId,
    address: SqlDataStore.EntityCreateAddress): Consequence[SqlDataStore.EntityCreateAddress] =
    if (!address.exists) Consequence.success(address)
    else Consequence {
      val statement = connection.createStatement()
      try statement.execute(s"LOCK TABLE ${address._qualified_sql} IN ROW EXCLUSIVE MODE")
      finally statement.close()
    }.flatMap(_ => _pg_address(connection, collection)).flatMap { resolved =>
      if (resolved.oid == address.oid && resolved.schema == address.schema && resolved.table == address.table)
        Consequence.success(resolved)
      else _create_schema_failure("PostgreSQL relation changed during address admission")
    }

  private def _pg_columns(connection: Connection, collection: CollectionId): Consequence[Vector[String]] =
    _pg_address(connection, collection).flatMap { address =>
      if (!address.exists) Consequence.success(Vector.empty)
      else _create_query(connection,
        "SELECT attname FROM pg_catalog.pg_attribute WHERE attrelid=CAST(? AS oid) AND attnum>0 AND NOT attisdropped ORDER BY attnum",
        Vector(address.oid.get.toString)) { rows =>
        val names = Vector.newBuilder[String]
        while (rows.next()) names += rows.getString(1)
        names.result()
      }.flatMap { names =>
        if (names.map(_pg_read_key).distinct.size == names.size) Consequence.success(names)
        else _create_schema_failure("PostgreSQL columns have ambiguous decoded Record keys")
      }
    }

  override def recordKey(storageName: String): String =
    if (_postgresql)
      _with_connection(connection => _pg_name(connection, _column_name(storageName))).map(_pg_read_key).take
    else _pg_read_key(_column_name(storageName))

  private def _pg_read_key(name: String): String =
    if (SimpleEntityStorageShapePolicy.isConcurrencyRevisionStorageField(name)) name
    else if (config.normalizeColumnNames) org.goldenport.record.RecordKeyNaming.toCanonicalCamelName(name)
    else name

  private def _pg_field(connection: Connection, collection: CollectionId, name: String): Consequence[String] =
    for {
      native <- _pg_name(connection, _column_name(name))
      existing <- _pg_columns(connection, collection)
      selected <- {
        if (existing.contains(native)) Consequence.success(native)
        else existing.filter(column => _pg_read_key(column) == name) match {
          case Vector(column) => Consequence.success(column)
          case Vector() => Consequence.success(native)
          case _ => _create_schema_failure[String]("Ambiguous PostgreSQL field correspondence")
        }
      }
    } yield selected

  private def _pg_read_field(connection: Connection, collection: CollectionId, name: String): Consequence[String] =
    _pg_field(connection, collection, name).map(_pg_read_key)

  private def _pg_guard_parameter(connection: Connection, collection: CollectionId,
    name: String): Consequence[String] =
    for {
      column <- _pg_field(connection, collection, name)
      address <- _pg_address(connection, collection)
      types <- _create_query(connection,
        "SELECT n.nspname,t.typname,t.typtype::text FROM pg_catalog.pg_attribute a " +
          "JOIN pg_catalog.pg_type t ON t.oid=a.atttypid " +
          "JOIN pg_catalog.pg_namespace n ON n.oid=t.typnamespace " +
          "WHERE a.attrelid=CAST(? AS oid) AND a.attname=? AND a.attnum>0 AND NOT a.attisdropped",
        Vector(address.oid.map(_.toString).orNull, column)) { rows =>
        val values = Vector.newBuilder[(String, String, String)]
        while (rows.next()) values += ((rows.getString(1), rows.getString(2), rows.getString(3)))
        values.result()
      }
      parameter <- types match {
        case Vector(("pg_catalog", typename, "b"))
            if Set("text", "varchar", "bpchar", "int2", "int4", "int8", "float4", "float8", "numeric", "bool").contains(typename) =>
          Consequence.success(s"CAST(? AS ${dialect.quoteIdentifier("pg_catalog")}.${dialect.quoteIdentifier(typename)})")
        case _ => _create_schema_failure[String]("PostgreSQL exclusion guard requires an unambiguous native scalar column")
      }
    } yield parameter

  private def _pg_record_columns(connection: Connection, collection: CollectionId,
    record: Record): Consequence[Vector[(String, Any)]] =
    _pg_resolve_columns(connection, collection, record.fields.filterNot(_.key == "id").map(field => field.key -> _column_value(field.value.single)))

  private def _pg_resolve_columns(connection: Connection, collection: CollectionId,
    columns: Vector[(String, Any)]): Consequence[Vector[(String, Any)]] =
    columns.foldLeft(Consequence.success(Vector.empty[(String, Any)])) { case (result, (name, value)) =>
      result.flatMap(values => _pg_field(connection, collection, name).map(column => values :+ (column -> value)))
    }.flatMap { resolved =>
      val names = resolved.map(_._1)
      if (names.contains("id")) Consequence.argumentPolicyViolation("record", "entity-create.sql-identity", "fields excluding physical id aliases", "id column alias")
      else if (names.distinct.size != names.size || names.map(_pg_read_key).distinct.size != names.size)
        Consequence.argumentInvalid("record", "unique native SQL and decoded Record fields", "native column aliases")
      else _pg_columns(connection, collection).flatMap { existing =>
        val allnames = (existing ++ names).distinct
        if (allnames.map(_pg_read_key).distinct.size == allnames.size) Consequence.success(resolved)
        else _create_schema_failure("PostgreSQL columns have ambiguous decoded Record keys")
      }
    }

  private def _pg_map_record(connection: Connection, collection: CollectionId, record: Record): Consequence[Record] =
    _pg_record_columns(connection, collection, record).flatMap { _ =>
      record.fields.foldLeft(Consequence.success(Vector.empty[Field])) { (result, field) =>
        result.flatMap(fields => _pg_read_field(connection, collection, field.key).map(key => fields :+ field.copy(key = key)))
      }.map(Record.apply)
    }

  private def _pg_id_collation(connection: Connection,
    address: SqlDataStore.EntityCreateAddress): Consequence[SqlDataStore.EntityCreateCollation] =
    if (!address.exists) Consequence.success(SqlDataStore.EntityCreateCollation(None, "default", Some("pg_catalog")))
    else _create_query(connection,
      "SELECT p.conname,a.attname,t.typname,tn.nspname,t.typtype::text,p.condeferrable,p.condeferred,p.convalidated," +
        "cardinality(p.conkey),i.indnkeyatts,i.indnatts,i.indisvalid,i.indisready,i.indimmediate," +
        "a.attcollation::bigint,i.indcollation[0]::bigint,cn.nspname,co.collname " +
        "FROM pg_catalog.pg_constraint p JOIN pg_catalog.pg_index i ON i.indexrelid=p.conindid " +
        "JOIN pg_catalog.pg_attribute a ON a.attrelid=p.conrelid AND a.attnum=p.conkey[1] " +
        "JOIN pg_catalog.pg_type t ON t.oid=a.atttypid JOIN pg_catalog.pg_namespace tn ON tn.oid=t.typnamespace " +
        "JOIN pg_catalog.pg_collation co ON co.oid=a.attcollation JOIN pg_catalog.pg_namespace cn ON cn.oid=co.collnamespace " +
        "WHERE p.conrelid=CAST(? AS oid) AND p.contype='p'", Vector(address.oid.get.toString)) { rows =>
      val values = Vector.newBuilder[(Boolean, SqlDataStore.EntityCreateCollation)]
      while (rows.next()) {
        val admissible = rows.getString(2) == "id" && Set("text", "varchar").contains(rows.getString(3)) &&
          rows.getString(4) == "pg_catalog" && rows.getString(5) == "b" && !rows.getBoolean(6) && !rows.getBoolean(7) &&
          rows.getBoolean(8) && rows.getInt(9) == 1 && rows.getInt(10) == 1 && rows.getInt(11) == 1 &&
          rows.getBoolean(12) && rows.getBoolean(13) && rows.getBoolean(14) && rows.getLong(15) == rows.getLong(16)
        values += admissible -> SqlDataStore.EntityCreateCollation(None, rows.getString(18), Some(rows.getString(17)), Some(rows.getString(1)))
      }
      values.result()
    }.flatMap {
      case Vector((true, collation)) => Consequence.success(collation)
      case _ => _create_schema_failure("PostgreSQL Entity mutation requires an immediate text id primary key with matching native collation")
    }

  private def _pg_admit_targets(connection: Connection,
    targets: Vector[(CollectionId, EntryId)]): Consequence[Vector[SqlDataStore.EntityCreateAddress]] =
    targets.foldLeft(Consequence.success(Vector.empty[SqlDataStore.EntityCreateAddress])) { case (result, (collection, _)) =>
      for {
        addresses <- result
        address <- _pg_address(connection, collection)
        locked <- _pg_lock_address(connection, collection, address)
        _ <- _pg_columns(connection, collection)
        _ <- _pg_id_collation(connection, locked)
      } yield addresses :+ locked
    }.flatMap { addresses =>
      val pairs = for { left <- addresses.indices.toVector; right <- (left + 1 until addresses.size).toVector
        if addresses(left)._same_relation(addresses(right)) } yield left -> right
      pairs.foldLeft(Consequence.unit) { case (result, (left, right)) =>
        for {
          _ <- result
          collation <- _pg_id_collation(connection, addresses(left))
          equal <- _create_ids_equal(connection, targets(left)._2.print, targets(right)._2.print, collation)
          _ <- if (!equal) Consequence.unit else if (left == 0) EntityCreateSupport.aliasedTarget else EntityCreateSupport.repeatedTarget
        } yield ()
      }.map(_ => addresses)
    }

  private def _pg_prepare_records(connection: Connection,
    records: Vector[(CollectionId, Record)]): Consequence[Unit] =
    records.foldLeft(Consequence.unit) { case (result, (collection, record)) =>
      for {
        _ <- result
        columns <- _pg_record_columns(connection, collection, record)
        _ <- _ensure_table(connection, collection, columns)
      } yield ()
    }

  private def _pg_prepare_create(connection: Connection,
    plan: EntityCreatePlan): Consequence[Vector[SqlDataStore.EntityCreateAddress]] = {
    val targets = (plan.collection -> plan.entryId) +: plan.sideEffects.map(effect => effect.collection -> effect.entryId)
    val records = (plan.collection -> plan.record) +: plan.sideEffects.collect {
      case save: EntityVersionedSideEffect.Save => save.collection -> save.record
    }
    for {
      _ <- _pg_admit_targets(connection, targets)
      _ <- _pg_prepare_records(connection, records)
      addresses <- _pg_admit_targets(connection, targets)
    } yield addresses
  }

  private def _pg_strict_insert(connection: Connection, collection: CollectionId,
    entryid: EntryId, columns: Vector[(String, Any)]): Consequence[Int] =
    for {
      address <- _pg_address(connection, collection)
      collation <- _pg_id_collation(connection, address)
      resolved <- _pg_resolve_columns(connection, collection, columns)
      count <- Consequence {
        val sql = PostgreSqlDialectDriver._strict_insert_at_sql(address._qualified_sql, resolved.map(_._1), collation.primarykey.get)
        val statement = _prepare_statement(connection, sql)
        try {
          statement.setString(1, entryid.print)
          resolved.zipWithIndex.foreach { case ((_, value), index) => statement.setObject(index + 2, value) }
          statement.executeUpdate()
        } finally statement.close()
      }
    } yield count

  private def _pg_map_guards(connection: Connection, collection: CollectionId,
    guards: Vector[EntityMutationExclusionGuard]): Consequence[Vector[EntityMutationExclusionGuard]] =
    guards.foldLeft(Consequence.success(Vector.empty[EntityMutationExclusionGuard])) { (result, guard) =>
      result.flatMap { values => guard match {
        case EntityMutationExclusionGuard.EqualTo(name, value) =>
          _pg_read_field(connection, collection, name).map(key => values :+ EntityMutationExclusionGuard.EqualTo(key, value))
        case EntityMutationExclusionGuard.Present(name) =>
          _pg_read_field(connection, collection, name).map(key => values :+ EntityMutationExclusionGuard.Present(key))
      } }
    }

  private def _pg_native_fields(connection: Connection, collection: CollectionId, entryid: EntryId,
    revisionfield: String, changes: Record, guards: Vector[EntityMutationExclusionGuard]
  ): Consequence[(String, Record, Vector[EntityMutationExclusionGuard])] =
    for {
      _ <- _pg_admit_targets(connection, Vector(collection -> entryid))
      revisioncolumn <- _pg_field(connection, collection, revisionfield)
      columns <- _pg_columns(connection, collection)
      _ <- if (columns.contains(revisioncolumn)) Consequence.unit
        else Consequence.operationInvalid("entity-native-mutation-schema", Vector(
          Descriptor.Facet.Reason("missing-managed-revision-column"), Descriptor.Facet.FieldPath(revisionfield),
          Descriptor.Facet.Policy(EntityVersionedMutationFailure.POLICY)))
      mapped <- _pg_map_record(connection, collection, changes)
      mappedguards <- _pg_map_guards(connection, collection, guards)
      revision = _pg_read_key(revisioncolumn)
      _ <- if (mapped.fields.exists(_.key == revision))
        Consequence.argumentPolicyViolation("changes", "framework-managed-revision", "record without native revision aliases", revision)
        else Consequence.unit
    } yield (revision, mapped, mappedguards)

  private def _pg_effective_versioned(connection: Connection,
    plan: EntityVersionedMutationPlan): Consequence[EntityVersionedMutationPlan] =
    if (!_postgresql) Consequence.success(plan) else for {
      _ <- _pg_admit_targets(connection, (plan.collection -> plan.entryId) +:
        plan.sideEffects.map(effect => effect.collection -> effect.entryId))
      revision <- _pg_read_field(connection, plan.collection, plan.revisionField)
      mutation <- plan.rootMutation match {
        case EntityVersionedRootMutation.Replace(record) => _pg_map_record(connection, plan.collection, record).map(EntityVersionedRootMutation.Replace.apply)
        case EntityVersionedRootMutation.Patch(changes) => _pg_map_record(connection, plan.collection, changes).map(EntityVersionedRootMutation.Patch.apply)
      }
      exclusions <- plan.comparisonExcludedFields.toVector.foldLeft(Consequence.success(Set.empty[String])) { (result, name) =>
        result.flatMap(values => _pg_read_field(connection, plan.collection, name).map(values + _))
      }
      effective = plan.copy(revisionField = revision, rootMutation = mutation, comparisonExcludedFields = exclusions)
      _ <- EntityVersionedMutationSupport.validate(effective)
    } yield effective

  private def _pg_effective_conditional(connection: Connection,
    plan: DataStoreConditionalTransitionPlan): Consequence[DataStoreConditionalTransitionPlan] =
    if (!_postgresql) Consequence.success(plan) else for {
      _ <- _pg_admit_targets(connection, Vector(plan.root.collection -> plan.root.entryId,
        plan.successor.collection -> plan.successor.entryId) ++ plan.sideEffects.map(effect => effect.collection -> effect.entryId))
      mappedroot <- _pg_map_conditional_root(connection, plan.root)
      successor <- plan.successor match {
        case create: DataStoreConditionalSuccessor.Create =>
          for {
            name <- _pg_read_field(connection, create.collection, create.revisionField)
            record <- _pg_map_record(connection, create.collection, create.record)
          } yield create.copy(revisionField = name, record = record)
        case bind: DataStoreConditionalSuccessor.Bind =>
          _pg_read_field(connection, bind.collection, bind.revisionField).map(name => bind.copy(revisionField = name))
        case update: DataStoreConditionalSuccessor.Update =>
          _pg_map_conditional_root(connection, update.root).map(DataStoreConditionalSuccessor.Update.apply)
      }
      effective <- DataStoreConditionalTransitionPlan.create(mappedroot, successor, plan.sideEffects, plan.correlation)
    } yield effective

  private def _pg_map_conditional_root(connection: Connection,
    root: DataStoreConditionalRoot): Consequence[DataStoreConditionalRoot] =
    for {
      revision <- _pg_read_field(connection, root.collection, root.revisionField)
      changes <- _pg_map_record(connection, root.collection, root.changes)
      expected <- root.expectedFields.foldLeft(Consequence.success(Vector.empty[org.goldenport.cncf.datastore.DataStoreConditionalExpectedField])) { (result, field) =>
        result.flatMap(values => _pg_read_field(connection, root.collection, field.storageField).map(name => values :+ field.copy(storageField = name)))
      }
      mapped <- DataStoreConditionalRoot.create(root.componentOwner, root.collection, root.entryId,
        revision, Some(root.expectedRevision), expected, changes, root.nextRevision)
    } yield mapped

  private def _pg_prepare_mutation(connection: Connection,
    rootcollection: CollectionId, rootentry: EntryId, rootrecord: Record,
    effects: Vector[EntityVersionedSideEffect], successor: Option[DataStoreConditionalSuccessor] = None): Consequence[Unit] =
    if (!_postgresql) Consequence.unit else {
      val successorrecords = successor.toVector.flatMap {
        case create: DataStoreConditionalSuccessor.Create => Vector(create.collection -> create.record)
        case update: DataStoreConditionalSuccessor.Update => Vector(update.collection ->
          (update.root.changes ++ Record.dataAuto(update.revisionField -> update.root.nextRevision.value)))
        case _: DataStoreConditionalSuccessor.Bind => Vector.empty
      }
      val records = Vector(rootcollection -> rootrecord) ++ successorrecords ++ effects.collect {
        case create: EntityVersionedSideEffect.Create => create.collection -> create.record
        case save: EntityVersionedSideEffect.Save => save.collection -> save.record
      }
      val targets = Vector(rootcollection -> rootentry) ++ successor.toVector.map(value => value.collection -> value.entryId) ++
        effects.map(effect => effect.collection -> effect.entryId)
      for {
        _ <- _pg_admit_targets(connection, targets)
        _ <- _pg_prepare_records(connection, records)
        _ <- _pg_admit_targets(connection, targets)
      } yield ()
    }

  private def _execute_entity_create(connection: Connection, plan: EntityCreatePlan, addresses: Vector[SqlDataStore.EntityCreateAddress]): Consequence[EntityCreateResult] = {
    val root = if (_postgresql) plan.collection else CollectionId.Instance(addresses.head.table)
    def _guard_(): Consequence[Unit] = _exists(connection, root, plan.entryId).flatMap { exists =>
      if (exists) Consequence.DataStoreDuplicate(plan.entryId.print) else Consequence.unit
    }
    for {
      _ <- _guard_()
      _ <- plan.sideEffects.zip(addresses.tail).foldLeft(Consequence.unit) { case (result, (effect, address)) =>
        result.flatMap { _ => for {
          _ <- _guard_()
          _ <- create_EntityCheckpoint(plan, EntityCreateCheckpoint.BeforeSideWrite(effect))
          _ <- effect match {
            case _: EntityVersionedSideEffect.Create => EntityCreateSupport.unsupportedSideEffect
            case EntityVersionedSideEffect.Save(_, entry, record) =>
              if (_postgresql) _pg_record_columns(connection, effect.collection, record).flatMap(columns => _upsert(connection, effect.collection, entry, columns))
              else _create_record_columns(connection, address, record).flatMap(columns => _upsert(connection, CollectionId.Instance(address.table), entry, columns))
            case EntityVersionedSideEffect.Delete(_, entry) =>
              if (address.exists) _delete(connection, if (_postgresql) effect.collection else CollectionId.Instance(address.table), entry) else Consequence.unit
          }
        } yield () }
      }
      _ <- create_EntityCheckpoint(plan, EntityCreateCheckpoint.BeforeRootWrite)
      columns <- if (_postgresql) _pg_record_columns(connection, root, plan.record) else _create_record_columns(connection, addresses.head, plan.record)
      _ <- if (_postgresql) _pg_strict_insert(connection, root, plan.entryId, columns).flatMap { count =>
        if (count == 1) Consequence.unit else if (count == 0) Consequence.DataStoreDuplicate(plan.entryId.print)
        else EntityVersionedMutationFailure.providerFailure("Entity create affected an unexpected row count")
      } else _insert(connection, root, plan.entryId, columns).recoverWith { conclusion =>
        if (_is_create_unique_exception(conclusion))
          _select_conditional_root(connection, root, plan.entryId).flatMap {
            case Some(_) => Consequence.DataStoreDuplicate(plan.entryId.print)
            case None => Consequence.Failure(conclusion)
          }
        else Consequence.Failure(conclusion)
      }
      record <- Consequence {
        if (_postgresql) PostgreSqlDialectDriver._select_by_id_at_sql(_pg_table_sql(connection, root))
        else dialect.selectByIdSql(addresses.head.table)
      }.flatMap(sql => _select_with_sql(connection, plan.collection, plan.entryId, sql)).flatMap {
        case Some(value) => Consequence.success(value)
        case None => Consequence.DataStoreNotFound(plan.entryId.print)
      }
      admitted <- EntityCreateSupport.validateResult(plan, EntityCreateResult(record),
        name => if (_postgresql) _pg_read_field(connection, root, name).take
          else if (config.normalizeColumnNames) org.goldenport.record.RecordKeyNaming.toCanonicalCamelName(_column_name(name)) else name,
        value => EntityCreateSupport.scalar(_column_value(value)))
      _ <- create_EntityCheckpoint(plan, EntityCreateCheckpoint.BeforePublish)
    } yield admitted
  }

  private def _is_create_unique_exception(conclusion: Conclusion): Boolean = conclusion.getException.exists {
    case error: org.sqlite.SQLiteException =>
      (dialect eq SqliteDialectDriver) && Set("SQLITE_CONSTRAINT_PRIMARYKEY", "SQLITE_CONSTRAINT_UNIQUE").contains(error.getResultCode.name())
    case error: SQLException => (dialect eq MySqlDialectDriver) && error.getErrorCode == 1062 && error.getSQLState == "23000"
    case _ => false
  }

  private def _with_entity_create[A](source: DataSource, body: Connection => Consequence[A]): Consequence[A] = {
    var publicationstarted = false
    var transactionstarted = false
    var transactionfinished = false
    try {
      val connection = source.getConnection()
      try {
        val autocommit = connection.getAutoCommit
        try {
          connection.setAutoCommit(false)
          transactionstarted = true
          val outcome = try body(connection) catch {
            case NonFatal(error) => EntityVersionedMutationFailure.providerFailure[A](s"Entity create failed before publication: ${error.getClass.getName}")
          }
          outcome match {
            case success: Consequence.Success[A] =>
              publicationstarted = true
              val acknowledgement = try versioned_mutation_commit(connection) catch {
                case NonFatal(_) => EntityVersionedMutationFailure.transactionIndeterminate[Unit]("Entity create commit acknowledgement escaped")
              }
              acknowledgement match {
                case _: Consequence.Success[?] => transactionfinished = true; success
                case Consequence.Failure(conclusion) => _rollback_versioned_mutation(connection, conclusion, () => transactionfinished = true)
                case null => _rollback_versioned_mutation(connection,
                  EntityVersionedMutationFailure.transactionIndeterminate[A]("Entity create commit acknowledgement is missing").conclusion,
                  () => transactionfinished = true)
              }
            case Consequence.Failure(conclusion) =>
              _rollback_versioned_mutation(connection, EntityVersionedMutationFailure.normalizeProvider[A](conclusion).conclusion,
                () => transactionfinished = true)
            case null => _rollback_versioned_mutation(connection,
              EntityVersionedMutationFailure.providerFailure[A]("Entity create returned no precommit outcome").conclusion,
              () => transactionfinished = true)
          }
        } finally {
          if (transactionstarted && !transactionfinished) _discard_conditional_connection(connection)
          else _restore_conditional_connection(connection, autocommit)
        }
      } finally _close_conditional_connection(connection)
    } catch {
      case NonFatal(error) =>
        if (publicationstarted) EntityVersionedMutationFailure.transactionIndeterminate(s"Entity create publication acknowledgement is unknown: ${error.getClass.getName}")
        else EntityVersionedMutationFailure.providerFailure(s"Entity create transaction could not start: ${error.getClass.getName}")
    }
  }

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

  /** Existing dispatch-owned SQLite only. Schema inspection is read-only; this
    * path never invokes ordinary search or its schema-creation helpers.
    */
  override private[cncf] def readCommittedEventHistoryC(
    collection: CollectionId,
    query: ManagedEventHistory.Query
  ): Consequence[Vector[Record]] =
    if (!(dialect eq SqliteDialectDriver) || _managed_borrow_resource.isEmpty ||
        _managed_borrow_admission.isEmpty)
      Consequence.configurationInvalid("Event history requires a borrowed managed SQLite resource")
    else if (collection == null || !collection.isInstanceOf[CollectionId.EntityStore])
      Consequence.configurationInvalid("Event history requires its declared entity collection")
    else ManagedEventHistory.directiveC(query).flatMap { directive =>
      _with_connection { connection =>
        _event_history_schema_c(connection, collection).flatMap { _ =>
          _read_event_history_c(connection, collection, directive, query.limit + 1)
        }
      }
    }

  private def _event_history_schema_c(connection: Connection, collection: CollectionId): Consequence[Unit] =
    _create_query(connection, dialect.tableColumnsSql(_table_name(collection)), Vector.empty) { rows =>
      val columns = Vector.newBuilder[(String, String)]
      while (rows.next()) columns += ((rows.getString("name"), rows.getString("type")))
      columns.result()
    }.flatMap { columns =>
      val schema = columns.toMap
      val numeric = schema.get(_column_name(EventHistoryCorrelation.sequenceField))
        .exists(value => value != null && value.toUpperCase(java.util.Locale.ROOT).contains("INT"))
      if (_event_history_columns.forall(name => schema.contains(_column_name(name))) && numeric)
        Consequence.unit
      else Consequence.dataStoreUnavailable("Event history projection schema is unavailable or nonnumeric")
    }

  private def _read_event_history_c(connection: Connection, collection: CollectionId,
    directive: QueryDirective, maximum: Int): Consequence[Vector[Record]] =
    Consequence {
      val sql = _search_sql(collection, directive)
      val statement = _prepare_statement(connection, sql.sql)
      try {
        _bind(statement, sql.params)
        val rows = statement.executeQuery()
        try {
          val records = Vector.newBuilder[Record]
          var count = 0
          var status: Consequence[Unit] = Consequence.unit
          while (status.isSuccess && rows.next()) {
            if (count >= maximum)
              status = Consequence.stateConflict("Event history provider exceeded its source bound")
            else status = Consequence {
              Record(_event_history_columns.flatMap { name =>
                Option(rows.getObject(_column_name(name))).map(value => Field(name, Field.Value.Single(value)))
              })
            }.map { record =>
              records += record
              count += 1
              ()
            }
          }
          status.map(_ => records.result())
        } finally rows.close()
      } finally statement.close()
    }.flatMap(identity)

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

  override def compareAndDeleteEntity(
    plan: EntityCompareAndDeletePlan
  )(using ctx: ExecutionContext): Consequence[EntityCompareAndDeleteResult] =
    EntityObservedDeleteSupport.validate(plan).flatMap { _ =>
      val changes = plan.rootMutation match {
        case EntityDeleteRootMutation.Physical => Record.empty
        case EntityDeleteRootMutation.Logical(patch) => patch
      }
      (for {
        _ <- EntityObservedDeleteSupport.requireCapabilities(
          entityMutationProviderCapabilities, plan.sideEffects.nonEmpty
        )
        _ <- _validate_native_mutation_columns(plan.revisionField, changes)
        _ <-
          if (_record_columns(changes).exists(_._1 == "id"))
            Consequence.argumentPolicyViolation(
              "rootMutation.changes", "framework-managed-identity",
              "patch without normalized id column", "id"
            )
          else Consequence.unit
        _ <- _prepare_native_mutation_schema(
          plan.collection, plan.entryId, plan.revisionField, changes, plan.exclusionGuards
        )
        result <- _with_versioned_mutation(connection =>
          _compare_and_delete_entity(connection, plan, changes)
        )
      } yield result).recoverWith(EntityVersionedMutationFailure.normalizeProvider)
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

  /** Internal SQLite staging candidate; no production host capability opt-in.
    * Cross-owner remains denied until managed authority/atomicity is admitted.
    */
  private[datastore] def conditionalMultiGuardAcknowledgedSameOwner(
    plan: DataStoreConditionalMultiGuardPlan
  )(using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] =
    _conditional_multi_guard_acknowledged(plan, None)

  private[datastore] def conditionalMultiGuardAcknowledgedManaged(
    invocation: DataStoreSpace.MultiGuardInvocation
  )(using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] =
    if (invocation == null || _managed_borrow_resource.isEmpty || _managed_borrow_admission.isEmpty)
      Consequence.configurationInvalid("Managed multi-guard host invocation is required")
    else invocation.validateProviderC(this).flatMap { _ =>
      _conditional_multi_guard_acknowledged(invocation.plan, Some(invocation))
    }

  private def _conditional_multi_guard_authority(
    invocation: Option[DataStoreSpace.MultiGuardInvocation]
  ): Consequence[Unit] = invocation.fold(Consequence.unit)(_.validateProviderC(this))

  private[datastore] def commitTransientEventsManagedC(inv: DataStoreSpace.TransientEventInvocation)
      (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] = for {
    _ <- inv.validateProviderC(this)
    _ <- if (dialect.name == SqliteDialectDriver.name) Consequence.unit
      else Consequence.operationInvalid("transient-event-native-transaction")
    transition = inv.nativeTransition
    changes = Vector(transition.root.changes) ++ inv.localGuards.map(_.changes) ++
      transition.sideEffects.collect { case c: EntityVersionedSideEffect.Create => c.record } ++ (transition.successor match {
      case update: DataStoreConditionalSuccessor.Update => Vector(update.root.changes)
      case create: DataStoreConditionalSuccessor.Create => Vector(create.record)
      case _ => Vector.empty
    })
    _ <- if (changes.forall(record => !record.fields.exists(_.key == "id"))) Consequence.unit
      else Consequence.configurationInvalid("Transient completion reserves physical id")
    result <- _with_conditional_transition { connection => for {
      _ <- inv.validateProviderC(this)
      existing <- _required_record(connection, transition.root.collection, transition.root.entryId)
      matched <- EntityConditionalTransitionSupport.rootMatches(existing, transition.root)
      result <- if (!matched) Consequence.success(DataStoreMultiGuardAcknowledgement.NotMatched(existing))
        else for {
          _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.GuardAdmitted)
          _ <- inv.validateProviderC(this)
          _ <- _prepare_conditional_successor(connection, transition.successor)
          _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.SuccessorPrepared)
          _ <- inv.validateProviderC(this)
          _ <- _update_root_record(connection, transition.root)
          _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.RootPrepared)
          _ <- inv.validateProviderC(this)
          _ <- _apply_completion_local_guards_c(connection, inv.localGuards)
          _ <- _apply_conditional_side_effects(connection, transition.sideEffects)
          records <- _transient_event_records_c(connection, inv)
          _ <- inv.validateReadbackC(records)
          _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.BeforePublish)
          _ <- inv.validateProviderC(this)
        } yield DataStoreMultiGuardAcknowledgement.Committed(records)
      _ <- inv.validateProviderC(this)
    } yield result }
  } yield result

  private def _transient_event_records_c(connection: Connection, inv: DataStoreSpace.TransientEventInvocation):
      Consequence[Vector[DataStoreMultiGuardRecord]] =
    inv.targets.foldLeft(Consequence.success(Vector.empty[DataStoreMultiGuardRecord])) { case (acc, (collection, id)) => for {
      rows <- acc
      _ <- inv.validateProviderC(this)
      record <- _multi_guard_logical_record(connection, collection, id)
    } yield rows :+ DataStoreMultiGuardRecord(collection, id, record) }

  private[datastore] def readbackTransientEventsManagedC(inv: DataStoreSpace.TransientEventInvocation)
      (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] =
    inv.validateProviderC(this).flatMap { _ => _with_connection { connection =>
      val autoCommit = connection.getAutoCommit
      connection.setAutoCommit(false)
      try _transient_event_records_c(connection, inv)
      finally {
        try connection.rollback()
        finally connection.setAutoCommit(autoCommit)
      }
    }}

  private[datastore] def commitRetainedEventsManagedC(inv: DataStoreSpace.RetainedEventInvocation)
      (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] = for {
    _ <- inv.validateProviderC(this)
    _ <- if (dialect.name == SqliteDialectDriver.name) Consequence.unit
      else Consequence.operationInvalid("retained-event-native-transaction")
    transition = inv.nativeTransition
    changes = Vector(transition.root.changes) ++ inv.localGuards.map(_.changes) ++
      transition.sideEffects.collect { case c: EntityVersionedSideEffect.Create => c.record } ++ (transition.successor match {
      case update: DataStoreConditionalSuccessor.Update => Vector(update.root.changes)
      case create: DataStoreConditionalSuccessor.Create => Vector(create.record)
      case _ => Vector.empty
    })
    _ <- if (changes.forall(record => !record.fields.exists(_.key == "id"))) Consequence.unit
      else Consequence.configurationInvalid("Retained completion reserves physical id")
    result <- _with_conditional_transition { connection => for {
      _ <- inv.validateProviderC(this)
      _ <- _validate_prepared_observations_c(connection, inv.preparation)
      existing <- _required_record(connection, transition.root.collection, transition.root.entryId)
      matched <- EntityConditionalTransitionSupport.rootMatches(existing, transition.root)
      result <- if (!matched) Consequence.success(DataStoreMultiGuardAcknowledgement.NotMatched(existing))
        else for {
          _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.GuardAdmitted)
          _ <- inv.validateProviderC(this)
          _ <- _prepare_conditional_successor(connection, transition.successor)
          _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.SuccessorPrepared)
          _ <- inv.validateProviderC(this)
          _ <- _update_root_record(connection, transition.root)
          _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.RootPrepared)
          _ <- inv.validateProviderC(this)
          _ <- _apply_completion_local_guards_c(connection, inv.localGuards)
          _ <- _apply_conditional_side_effects(connection, transition.sideEffects)
          records <- _retained_event_records_c(connection, inv)
          _ <- inv.validateReadbackC(records)
          _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.BeforePublish)
          _ <- inv.validateProviderC(this)
        } yield DataStoreMultiGuardAcknowledgement.Committed(records)
      _ <- inv.validateProviderC(this)
    } yield result }
  } yield result

  private def _retained_event_records_c(connection: Connection, inv: DataStoreSpace.RetainedEventInvocation):
      Consequence[Vector[DataStoreMultiGuardRecord]] = inv.targetsC.flatMap { targets =>
    targets.foldLeft(Consequence.success(Vector.empty[DataStoreMultiGuardRecord])) { case (acc, (collection, id)) => for {
      records <- acc
      _ <- inv.validateProviderC(this)
      record <- _multi_guard_logical_record(connection, collection, id)
    } yield records :+ DataStoreMultiGuardRecord(collection, id, record) }
  }

  /** Local intents share the already-open native write connection. No second
    * commit, schema preflight, executable callback or EventStore cursor write.
    */
  private def _apply_completion_local_guards_c(connection: Connection,
      guards: Vector[DataStoreConditionalRoot]): Consequence[Unit] =
    guards.foldLeft(Consequence.unit) { (acc, guard) => acc.flatMap { _ => for {
      current <- _required_record(connection, guard.collection, guard.entryId)
      matched <- EntityConditionalTransitionSupport.rootMatches(current, guard)
      _ <- if (matched) _update_root_record(connection, guard)
        else Consequence.operationConflict("local-completion", Vector(Descriptor.Facet.Reason("local-guard-conflict")))
    } yield () }}

  private[datastore] def readbackRetainedEventsManagedC(inv: DataStoreSpace.RetainedEventInvocation)
      (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] =
    inv.validateProviderC(this).flatMap { _ => _with_connection { connection =>
      val autoCommit = connection.getAutoCommit
      connection.setAutoCommit(false)
      try _retained_event_records_c(connection, inv)
      finally {
        try connection.rollback()
        finally connection.setAutoCommit(autoCommit)
      }
    }}

  private def _conditional_multi_guard_acknowledged(
    plan: DataStoreConditionalMultiGuardPlan,
    invocation: Option[DataStoreSpace.MultiGuardInvocation]
  )(using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] =
    for {
      admitted <- DataStoreConditionalMultiGuardPlan.validate(plan)
      _ <- if (dialect.name == SqliteDialectDriver.name &&
        (invocation.isDefined || admitted.additionalGuards.forall(_.componentOwner == admitted.scope.workflowOwner)))
        Consequence.unit
      else Consequence.operationInvalid("entity-conditional-multi-guard")
      _ <- _conditional_multi_guard_authority(invocation)
      _ <- if ((Vector(admitted.transition.root.changes) ++
        admitted.additionalGuards.map(_.changes) ++
        (admitted.transition.successor match {
          case update: DataStoreConditionalSuccessor.Update => Vector(update.root.changes)
          case create: DataStoreConditionalSuccessor.Create => Vector(create.record)
          case _ => Vector.empty
        }) ++ admitted.transition.sideEffects.collect {
          case effect: EntityVersionedSideEffect.Create => effect.record
        }).forall(record => !record.fields.exists(_.key == "id"))) Consequence.unit
      else Consequence.configurationInvalid("SQLite multi-guard reserves the physical id column")
      // SQLite schema expansion belongs to the same write transaction as the
      // guarded records; no preflight DDL may survive a rejected proposal.
      result <- _with_conditional_transition { connection =>
        for {
          _ <- _conditional_multi_guard_authority(invocation)
          _ <- invocation.flatMap(_.preparation) match {
            case None => Consequence.unit
            case Some(prepared) => _validate_prepared_observations_c(connection, prepared)
          }
          existing <- _required_record(connection, admitted.transition.root.collection,
            admitted.transition.root.entryId)
          matches <- EntityConditionalTransitionSupport.rootMatches(existing, admitted.transition.root)
          result <- if (!matches)
            Consequence.success(DataStoreMultiGuardAcknowledgement.NotMatched(existing))
          else for {
            _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.GuardAdmitted)
            _ <- _conditional_multi_guard_authority(invocation)
            _ <- _prepare_conditional_successor(connection, admitted.transition.successor)
            _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.SuccessorPrepared)
            _ <- _update_root_record(connection, admitted.transition.root)
            _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.RootPrepared)
            _ <- admitted.additionalGuards.foldLeft(Consequence.unit) { (result, guard) =>
              result.flatMap { _ => for {
                current <- _required_record(connection, guard.collection, guard.entryId)
                matched <- EntityConditionalTransitionSupport.rootMatches(current, guard)
                _ <- if (matched) _update_root_record(connection, guard)
                  else Consequence.operationConflict("entity-conditional-multi-guard", Vector(
                    Descriptor.Facet.Reason("additional-guard-conflict"),
                    Descriptor.Facet.Policy("entity.conditional-multi-guard")))
              } yield () }
            }
            _ <- _apply_conditional_side_effects(connection, admitted.transition.sideEffects)
            // Capture exact transaction post-images before native commit, never by later loads.
            records <- _multi_guard_records(connection, admitted)
            retained <- invocation.fold(Consequence.success(Vector.empty[DataStoreMultiGuardRecord]))(_.retainedC)
            actualRetained <- retained.foldLeft(Consequence.success(Vector.empty[DataStoreMultiGuardRecord])) {
              case (acc, expected) => for {
                rows <- acc
                actual <- _multi_guard_logical_record(connection, expected.collection, expected.entryId)
              } yield rows :+ DataStoreMultiGuardRecord(expected.collection, expected.entryId, actual)
            }
            _ <- conditional_transition_checkpoint(DataStoreConditionalTransitionCheckpoint.BeforePublish)
            _ <- _conditional_multi_guard_authority(invocation)
          } yield DataStoreMultiGuardAcknowledgement.Committed(records ++ actualRetained)
          _ <- _conditional_multi_guard_authority(invocation)
        } yield result
      }
    } yield result

  /** One connection and native read transaction for all admitted targets.
    * Ending this read-only snapshot does not invoke the write-commit hook.
    */
  private[datastore] def readbackMultiGuardSnapshotC(
    invocation: DataStoreSpace.MultiGuardInvocation
  )(using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] =
    if (invocation == null) Consequence.configurationInvalid("Multi-guard invocation is missing")
    else invocation.validateProviderC(this).flatMap { _ =>
      _with_connection { connection =>
        val autoCommit = connection.getAutoCommit
        connection.setAutoCommit(false)
        try invocation.readbackTargetsC.flatMap(_.foldLeft(Consequence.success(Vector.empty[DataStoreMultiGuardRecord])) {
            case (result, (collection, entryId)) => for {
              records <- result
              _ <- invocation.validateProviderC(this)
              record <- _multi_guard_logical_record(connection, collection, entryId)
            } yield records :+ DataStoreMultiGuardRecord(collection, entryId, record)
          })
        finally {
          try connection.rollback()
          finally connection.setAutoCommit(autoCommit)
        }
      }
    }

  /** Runs on the SAME write connection, before root/successor staging or DDL.
    * Every explicit absence and retained closure fact is rechecked; no retry.
    */
  private def _validate_prepared_observations_c(connection: Connection,
      prepared: org.goldenport.cncf.datastore.ManagedEventPreparation): Consequence[Unit] = for {
    _ <- prepared.validateProviderC(this)
    expected <- prepared.recordsC
    actual <- expected.foldLeft(Consequence.success(Vector.empty[(DataStoreObservationTarget, Option[Record])])) {
      case (acc, (target, _)) => for {
        rows <- acc
        _ <- prepared.validateProviderC(this)
        exists <- _table_exists(connection, target.collection)
        _ <- if (exists) Consequence.unit else Consequence.stateConflict("Prepared observation collection is missing")
        physical <- _select(connection, target.collection, target.entryId)
        logical <- physical match {
          case Some(record) if record.fields.count(_.key == "id") == 1 &&
              record.getString("id").contains(target.entryId.print) && record.fields.map(_.key).distinct.size == record.fields.size =>
            Consequence.success(Some(Record(record.fields.filterNot(_.key == "id").sortBy(_.key))))
          case Some(_) => Consequence.stateConflict("Prepared observation physical identity or fields differ")
          case None => Consequence.success(None)
        }
      } yield rows :+ (target -> logical)
    }
    _ <- prepared.validateObservedC(actual)
    _ <- prepared.validateProviderC(this)
  } yield ()

  /** Fixed targets only. Missing tables are not evidence of row absence. */
  private[datastore] def observeDeclaredSnapshotC(invocation: DataStoreSpace.ObservationInvocation)
      (using ExecutionContext): Consequence[Vector[(DataStoreObservationTarget, Option[Record])]] =
    invocation.validateProviderC(this).flatMap { _ => _with_connection { connection =>
      val autoCommit = connection.getAutoCommit
      connection.setAutoCommit(false)
      def readTargets(targets: Vector[DataStoreObservationTarget],
          previous: Vector[(DataStoreObservationTarget, Option[Record])]):
          Consequence[Vector[(DataStoreObservationTarget, Option[Record])]] =
        invocation.validateTargetsC((previous.map(_._1) ++ targets).distinct).flatMap { _ =>
          targets.filterNot(t => previous.exists(_._1 == t)).foldLeft(
        Consequence.success(previous)) { (acc, target) =>
        for {
          rows <- acc
          _ <- invocation.validateProviderC(this)
          exists <- _table_exists(connection, target.collection)
          _ <- if (exists) Consequence.unit
            else Consequence.configurationInvalid("Observation collection is not provisioned")
          physical <- _select(connection, target.collection, target.entryId)
          logical <- physical match {
            case Some(record) if record.fields.count(_.key == "id") == 1 &&
                record.getString("id").contains(target.entryId.print) &&
                record.fields.map(_.key).distinct.size == record.fields.size =>
              Consequence.success(Some(Record(record.fields.filterNot(_.key == "id").sortBy(_.key))))
            case Some(_) => Consequence.configurationInvalid("Observation physical identity or fields differ")
            case None if target == invocation.targets.head =>
              Consequence.stateConflict("Observation cursor is missing")
            case None => Consequence.success(None)
          }
        } yield rows :+ (target -> logical)
      }}
      try for {
        initial <- readTargets(invocation.targets, Vector.empty)
        complete <- invocation.eventRequest match {
          case None => Consequence.success(initial)
          case Some(request) => for {
            refs <- request.referencedTargetsC(initial)
            referenced <- readTargets(refs, initial)
            intrinsic <- request.intrinsicTargetsC(referenced)
            closed <- readTargets(intrinsic, referenced)
          } yield closed
        }
      } yield complete
      finally {
        try connection.rollback()
        finally connection.setAutoCommit(autoCommit)
      }
    }}

  private def _multi_guard_records(
    connection: Connection,
    plan: DataStoreConditionalMultiGuardPlan
  ): Consequence[Vector[DataStoreMultiGuardRecord]] =
    DataStoreMultiGuardReadback.targets(plan)
      .foldLeft(Consequence.success(Vector.empty[DataStoreMultiGuardRecord])) {
        case (result, (collection, entryId)) => for {
          records <- result
          record <- _multi_guard_logical_record(connection, collection, entryId)
        } yield records :+ DataStoreMultiGuardRecord(collection, entryId, record)
      }

  /** Exact physical identity is checked, then removed from the logical record.
    * This reserved-column adapter does not drop arbitrary fields or values.
    */
  private def _multi_guard_logical_record(
    connection: Connection,
    collection: CollectionId,
    entryId: EntryId
  ): Consequence[Record] =
    _required_record(connection, collection, entryId).flatMap { record =>
      val ids = record.fields.filter(_.key == "id")
      if (ids.size == 1 && record.getString("id").contains(entryId.print))
        Consequence.success(Record(record.fields.filterNot(_.key == "id").sortBy(_.key)))
      else Consequence.configurationInvalid("SQLite multi-guard physical record identity differs")
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
  ): Consequence[A] = {
    var transactionstarted = false
    var publicationstarted = false
    var transactionfinished = false
    try {
      val connection = source.getConnection()
      try {
        val autocommit = connection.getAutoCommit
        try {
          connection.setAutoCommit(false)
          transactionstarted = true
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
              publicationstarted = true
              val acknowledgement = try conditional_transition_commit(connection) catch {
                case error: Throwable => DataStoreConditionalTransitionFailure.transactionIndeterminate[Unit](
                  s"Conditional transition commit acknowledgement escaped: ${error.getClass.getName}")
              }
              acknowledgement match {
                case _: Consequence.Success[?] =>
                  transactionfinished = true
                  success
                case Consequence.Failure(conclusion) =>
                  _rollback_conditional_transition(connection, conclusion, () => transactionfinished = true)
                case null =>
                  _rollback_conditional_transition(connection,
                    DataStoreConditionalTransitionFailure.transactionIndeterminate[A]("Conditional transition commit acknowledgement is missing").conclusion,
                    () => transactionfinished = true)
              }
            case Consequence.Failure(conclusion) =>
              val normalized =
                DataStoreConditionalTransitionFailure
                  .normalizeProvider[A](conclusion)
              _rollback_conditional_transition(
                connection,
                normalized.conclusion,
                () => transactionfinished = true
              )
            case null =>
              _rollback_conditional_transition(connection,
                DataStoreConditionalTransitionFailure.providerFailure[A]("Conditional transition returned no precommit outcome").conclusion,
                () => transactionfinished = true)
          }
        } finally {
          if (transactionstarted && !transactionfinished) _discard_conditional_connection(connection)
          else _restore_conditional_connection(connection, autocommit)
        }
      } finally {
        _close_conditional_connection(connection)
      }
    } catch {
      case e: Throwable =>
        if (publicationstarted) DataStoreConditionalTransitionFailure.transactionIndeterminate(
          s"Conditional transition publication acknowledgement is unknown: ${e.getClass.getName}")
        else DataStoreConditionalTransitionFailure.providerFailure(
          s"Conditional transition transaction could not start: ${e.getClass.getName}")
    }
  }

  private def _with_versioned_mutation[A](
    body: Connection => Consequence[A]
  ): Consequence[A] =
    _with_managed_datasource(source => _with_versioned_mutation(source, body))

  private def _with_versioned_mutation[A](
    source: DataSource,
    body: Connection => Consequence[A]
  ): Consequence[A] = {
    var transactionstarted = false
    var publicationstarted = false
    var transactionfinished = false
    try {
      val connection = source.getConnection()
      try {
        val autocommit = connection.getAutoCommit
        try {
          connection.setAutoCommit(false)
          transactionstarted = true
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
              publicationstarted = true
              val acknowledgement = try versioned_mutation_commit(connection) catch {
                case error: Throwable => EntityVersionedMutationFailure.transactionIndeterminate[Unit](
                  s"Versioned mutation commit acknowledgement escaped: ${error.getClass.getName}")
              }
              acknowledgement match {
                case _: Consequence.Success[?] =>
                  transactionfinished = true
                  success
                case Consequence.Failure(conclusion) =>
                  _rollback_versioned_mutation(connection, conclusion, () => transactionfinished = true)
                case null =>
                  _rollback_versioned_mutation(connection,
                    EntityVersionedMutationFailure.transactionIndeterminate[A]("Versioned mutation commit acknowledgement is missing").conclusion,
                    () => transactionfinished = true)
              }
            case Consequence.Failure(conclusion) =>
              val normalized =
                EntityVersionedMutationFailure
                  .normalizeProvider[A](conclusion)
              _rollback_versioned_mutation(
                connection,
                normalized.conclusion,
                () => transactionfinished = true
              )
            case null =>
              _rollback_versioned_mutation(connection,
                EntityVersionedMutationFailure.providerFailure[A]("Versioned mutation returned no precommit outcome").conclusion,
                () => transactionfinished = true)
          }
        } finally {
          if (transactionstarted && !transactionfinished) _discard_conditional_connection(connection)
          else _restore_conditional_connection(connection, autocommit)
        }
      } finally {
        _close_conditional_connection(connection)
      }
    } catch {
      case e: Throwable =>
        if (publicationstarted) EntityVersionedMutationFailure.transactionIndeterminate(
          s"Versioned mutation publication acknowledgement is unknown: ${e.getClass.getName}")
        else EntityVersionedMutationFailure.providerFailure(
          s"Versioned mutation transaction could not start: ${e.getClass.getName}")
    }
  }

  private def _rollback_versioned_mutation[A](
    connection: Connection,
    conclusion: Conclusion,
    oncompleted: () => Unit
  ): Consequence[A] =
    try {
      connection.rollback()
      oncompleted()
      Consequence.Failure(conclusion)
    } catch {
      case e: Throwable =>
        EntityVersionedMutationFailure.transactionIndeterminate(
          s"Versioned mutation rollback outcome is indeterminate: ${e.getClass.getName}"
        )
    }

  private def _rollback_conditional_transition[A](
    connection: Connection,
    conclusion: Conclusion,
    oncompleted: () => Unit
  ): Consequence[A] =
    try {
      connection.rollback()
      oncompleted()
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

  private def _discard_conditional_connection(connection: Connection): Unit =
    try connection.abort(new java.util.concurrent.Executor {
      def execute(command: Runnable): Unit = command.run()
    }) catch {
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
            case update: DataStoreConditionalSuccessor.Update =>
              _ensure_existing_table_columns(connection, update.collection,
                _conditional_root_columns(update.root))
          }
          _ <- plan.sideEffects.foldLeft(Consequence.unit) {
            case (result, EntityVersionedSideEffect.Create(collection, _, record)) =>
              result.flatMap(_ =>
                _ensure_table(connection, collection, _record_columns(record))
              )
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
            case (result, EntityVersionedSideEffect.Create(collection, _, record)) =>
              result.flatMap(_ =>
                _ensure_table(connection, collection, _record_columns(record))
              )
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
    if (_postgresql) Consequence.unit else _with_connection { connection =>
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
    originalplan: DataStoreConditionalTransitionPlan
  ): Consequence[DataStoreConditionalTransitionResult] =
    for {
      plan <- _pg_effective_conditional(connection, originalplan)
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
    originalplan: EntityVersionedMutationPlan
  ): Consequence[EntityVersionedMutationResult] =
    for {
      plan <- _pg_effective_versioned(connection, originalplan)
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
          _sql_business_state_equals(
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

  private def _sql_business_state_equals(
    existing: Record,
    desired: Record,
    plan: EntityVersionedMutationPlan
  ): Boolean =
    EntityVersionedMutationSupport.businessStateEquals(
      _sql_comparison_record(existing),
      _sql_comparison_record(desired),
      plan
    )

  private def _sql_comparison_record(record: Record): Record =
    Record(record.fields.flatMap { field =>
      field.value match {
        case Field.Value.Single(Update.SetNull) => None
        case Field.Value.Single(value: String) =>
          Some(field.copy(value = Field.Value.Single(_decode_column_value(value))))
        case _ => Some(field)
      }
    })

  private def _compare_and_delete_entity(
    connection: Connection,
    originalplan: EntityCompareAndDeletePlan,
    originalchanges: Record
  ): Consequence[EntityCompareAndDeleteResult] =
    for {
      fields <- if (_postgresql) _pg_native_fields(connection, originalplan.collection, originalplan.entryId,
        originalplan.revisionField, originalchanges, originalplan.exclusionGuards)
        else Consequence.success((originalplan.revisionField, originalchanges, originalplan.exclusionGuards))
      (revisionfield, changes, guards) = fields
      plan = originalplan.copy(revisionField = revisionfield, exclusionGuards = guards)
      _ <- if (_postgresql) _pg_admit_targets(connection, (plan.collection -> plan.entryId) +:
        plan.sideEffects.map(effect => effect.collection -> effect.entryId)).map(_ => ()) else Consequence.unit
      existingoption <- _select_conditional_root(connection, plan.collection, plan.entryId)
      existing <- existingoption.map(Consequence.success)
        .getOrElse(Consequence.DataStoreNotFound(plan.entryId.print))
      _ <- _admit_observed_delete_existing(plan, existing)
      actual <- EntityVersionedMutationSupport.revision(existing, plan.revisionField)
      result <-
        if (actual != plan.expectedRevision)
          Consequence.success(EntityCompareAndDeleteResult.Stale(plan.expectedRevision, actual))
        else
          actual.nextC.flatMap { _ =>
            for {
              _ <- _pg_prepare_mutation(connection, plan.collection, plan.entryId, changes, plan.sideEffects)
              _ <- if (_postgresql) _ensure_columns(connection, plan.collection,
                _native_mutation_guard_columns(plan.exclusionGuards).distinct) else Consequence.unit
              count <- plan.rootMutation match {
                case EntityDeleteRootMutation.Physical =>
                  _native_delete_count(connection, plan)
                case _: EntityDeleteRootMutation.Logical =>
                  _native_mutation_update_count(
                    connection, plan.collection, plan.entryId, plan.revisionField,
                    changes, Some(plan.expectedRevision), plan.exclusionGuards
                  )
              }
              outcome <-
                if (count == 1)
                  for {
                    _ <- versioned_mutation_checkpoint(EntityVersionedMutationCheckpoint.RootPrepared)
                    _ <- _apply_conditional_side_effects(connection, plan.sideEffects)
                    _ <- versioned_mutation_checkpoint(EntityVersionedMutationCheckpoint.SideEffectsPrepared)
                    _ <- versioned_mutation_checkpoint(EntityVersionedMutationCheckpoint.BeforePublish)
                  } yield EntityCompareAndDeleteResult.Deleted(actual)
                else if (count == 0)
                  _diagnose_observed_delete_failure(connection, plan)
                else
                  EntityVersionedMutationFailure.providerFailure(
                    "Observed Entity deletion affected an unexpected row count"
                  )
            } yield outcome
          }
    } yield result

  private def _diagnose_observed_delete_failure(
    connection: Connection,
    plan: EntityCompareAndDeletePlan
  ): Consequence[EntityCompareAndDeleteResult] =
    for {
      record <- _required_record(connection, plan.collection, plan.entryId)
      _ <- _admit_observed_delete_existing(plan, record)
      actual <- EntityVersionedMutationSupport.revision(record, plan.revisionField)
      result <-
        if (actual != plan.expectedRevision)
          Consequence.success(EntityCompareAndDeleteResult.Stale(plan.expectedRevision, actual))
        else
          actual.nextC.flatMap(_ =>
            EntityVersionedMutationFailure.providerFailure(
              "Observed Entity deletion affected no row with an unchanged revision"
            )
          )
    } yield result

  private def _admit_observed_delete_existing(
    plan: EntityCompareAndDeletePlan,
    record: Record
  ): Consequence[Unit] = {
    def _read_field_(name: String): String =
      if (config.normalizeColumnNames &&
          !SimpleEntityStorageShapePolicy.isConcurrencyRevisionStorageField(name))
        org.goldenport.record.RecordKeyNaming.toCanonicalCamelName(_column_name(name))
      else name
    val guards = plan.exclusionGuards.map {
      case EntityMutationExclusionGuard.EqualTo(fieldname, value) =>
        EntityMutationExclusionGuard.EqualTo(_read_field_(fieldname), value)
      case EntityMutationExclusionGuard.Present(fieldname) =>
        EntityMutationExclusionGuard.Present(_read_field_(fieldname))
    }
    EntityNativeMutationSupport.admitExisting(plan.entryId, record, guards)
  }

  private def _mutate_entity_direct(
    connection: Connection,
    originalplan: EntityDirectMutationPlan
  ): Consequence[EntityMutationProviderResult] =
    for {
      fields <- if (_postgresql) _pg_native_fields(connection, originalplan.collection, originalplan.entryId,
        originalplan.revisionField, originalplan.changes, originalplan.exclusionGuards)
        else Consequence.success((originalplan.revisionField, originalplan.changes, originalplan.exclusionGuards))
      (revisionfield, changes, guards) = fields
      plan = originalplan.copy(revisionField = revisionfield, changes = changes, exclusionGuards = guards)
      _ <- if (_postgresql) _pg_prepare_records(connection, Vector(plan.collection -> plan.changes)).flatMap(_ =>
        _ensure_columns(connection, plan.collection, _native_mutation_guard_columns(plan.exclusionGuards).distinct)) else Consequence.unit
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
    originalplan: EntityCompareAndSetMutationPlan
  ): Consequence[EntityMutationProviderResult] =
    for {
      fields <- if (_postgresql) _pg_native_fields(connection, originalplan.collection, originalplan.entryId,
        originalplan.revisionField, originalplan.changes, originalplan.exclusionGuards)
        else Consequence.success((originalplan.revisionField, originalplan.changes, originalplan.exclusionGuards))
      (revisionfield, changes, guards) = fields
      plan = originalplan.copy(revisionField = revisionfield, changes = changes, exclusionGuards = guards)
      _ <- if (_postgresql) _pg_prepare_records(connection, Vector(plan.collection -> plan.changes)).flatMap(_ =>
        _ensure_columns(connection, plan.collection, _native_mutation_guard_columns(plan.exclusionGuards).distinct)) else Consequence.unit
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
    val command = plan.rootMutation match {
      case EntityVersionedRootMutation.Patch(changes) => changes
      case EntityVersionedRootMutation.Replace(record) => record
    }
    val markers = command.fields.filter(_.value == Field.Value.Single(Update.SetNull))
    val markerkeys = markers.map(_.key).toSet
    val writerecord = Record(rootrecord.fields.filterNot(field => markerkeys.contains(field.key)) ++ markers)
    for {
      _ <- _pg_prepare_mutation(connection, plan.collection, plan.entryId, writerecord, plan.sideEffects)
      _ <- versioned_mutation_checkpoint(
        EntityVersionedMutationCheckpoint.RootPrepared
      )
      _ <- _update_versioned_root_record(
        connection,
        plan.collection,
        plan.entryId,
        writerecord
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
      _ <- _pg_prepare_mutation(connection, plan.root.collection, plan.root.entryId,
        plan.root.changes ++ Record.dataAuto(plan.root.revisionField -> plan.root.nextRevision.value),
        plan.sideEffects, Some(plan.successor))
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
            else if (_postgresql)
              _pg_strict_insert(connection, create.collection, create.entryId, columns).flatMap { count =>
                if (count == 1) Consequence.unit else if (count == 0)
                  Consequence.operationConflict("entity-conditional-transition", Vector(
                    Descriptor.Facet.Reason("successor-collision"), Descriptor.Facet.Policy("entity.conditional-transition.successor-create")))
                else DataStoreConditionalTransitionFailure.providerFailure("Conditional successor create affected an unexpected row count")
              }
            else _insert(
                connection,
                create.collection,
                create.entryId,
                columns
              )
        } yield ()
      case update: DataStoreConditionalSuccessor.Update =>
        _select_conditional_root(connection, update.collection, update.entryId).flatMap {
          case None => Consequence.DataStoreNotFound(update.entryId.print)
          case Some(record) =>
            EntityConditionalTransitionSupport.rootMatches(record, update.root).flatMap { matches =>
              if (matches) _update_root_record(connection, update.root)
              else Consequence.operationConflict("entity-conditional-transition", Vector(
                Descriptor.Facet.Reason("updated-successor-state-conflict"),
                Descriptor.Facet.Policy("entity.conditional-transition.successor-update")))
            }
        }
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
      case (result, EntityVersionedSideEffect.Create(collection, entryid, record)) =>
        result.flatMap { _ =>
          val columns = _record_columns(record)
          for {
            _ <- _ensure_table(connection, collection, columns)
            exists <- _exists(connection, collection, entryid)
            _ <- if (exists) Consequence.DataStoreDuplicate(entryid.print)
              else if (_postgresql) _pg_strict_insert(connection, collection, entryid, columns).flatMap { count =>
                if (count == 1) Consequence.unit
                else if (count == 0) Consequence.DataStoreDuplicate(entryid.print)
                else EntityVersionedMutationFailure.providerFailure("Side Create affected an unexpected row count")
              }
              else _insert(connection, collection, entryid, columns)
          } yield ()
        }
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
        val basesql = if (_postgresql) PostgreSqlDialectDriver._select_by_id_at_sql(_pg_table_sql(connection, collection))
          else dialect.selectByIdSql(_table_name(collection))
        val sql =
          if (dialect.name == MySqlDialectDriver.name || _postgresql)
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

  private def _record_columns(record: Record): Vector[(String, Any)] =
    _record_codec.recordColumns(record)

  private def _column_value(value: Any): Any =
    _record_codec.columnValue(value)

  private def _decode_column_value(value: Any): Any =
    _record_codec.decodeColumnValue(value)

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
      val sql = if (_postgresql) PostgreSqlDialectDriver._create_table_at_sql(_pg_table_sql(conn, collection), _pg_resolve_columns(conn, collection, columns).take)
        else dialect.createTableSql(_table_name(collection), columns)
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
    (if (_postgresql) _pg_resolve_columns(conn, collection, columns) else Consequence.success(columns)).flatMap { resolved =>
    _existing_columns(conn, collection).flatMap { existing =>
      val missing = resolved.map(_._1).filterNot(existing.contains)
      missing.foldLeft(Consequence.unit) { (z, col) =>
        z.flatMap(_ => _add_column(conn, collection, resolved.find(_._1 == col).get))
      }
    }
    }

  private def _add_column(
    conn: Connection,
    collection: CollectionId,
    column: (String, Any)
  ): Consequence[Unit] =
    Consequence {
      val sql = if (_postgresql) PostgreSqlDialectDriver._add_column_at_sql(_pg_table_sql(conn, collection), column)
        else dialect.addColumnSql(_table_name(collection), column)
      val stmt = conn.createStatement()
      try {
        stmt.execute(sql)
        ()
      } finally {
        stmt.close()
      }
    }.recoverWith { conclusion =>
      val installed = !_postgresql &&
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
      val sql = if (_postgresql) PostgreSqlDialectDriver._select_by_id_at_sql(_pg_table_sql(conn, collection))
        else dialect.selectByIdSql(_table_name(collection))
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
      val resolved = if (_postgresql) _pg_resolve_columns(conn, collection, columns).take else columns
      val sql = if (_postgresql) PostgreSqlDialectDriver._insert_at_sql(_pg_table_sql(conn, collection), resolved.map(_._1))
        else dialect.insertSql(_table_name(collection), resolved.map(_._1))
      val stmt = _prepare_statement(conn, sql)
      try {
        stmt.setString(1, id.print)
        resolved.zipWithIndex.foreach { case ((_, v), i) =>
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
      val resolved = if (_postgresql) _pg_resolve_columns(conn, collection, columns).take else columns
      val sql = if (_postgresql) {
        val address = _pg_address(conn, collection).take
        val collation = _pg_id_collation(conn, address).take
        PostgreSqlDialectDriver._upsert_at_sql(address._qualified_sql, resolved.map(_._1), collation.primarykey)
      }
        else dialect.upsertSql(_table_name(collection), resolved.map(_._1))
      val stmt = _prepare_statement(conn, sql)
      try {
        stmt.setString(1, id.print)
        resolved.zipWithIndex.foreach { case ((_, value), index) =>
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
        val resolved = if (_postgresql) _pg_resolve_columns(conn, collection, columns).take else columns
        val sql = if (_postgresql) PostgreSqlDialectDriver._update_at_sql(_pg_table_sql(conn, collection), resolved.map(_._1))
          else dialect.updateSql(_table_name(collection), resolved.map(_._1))
        val stmt = _prepare_statement(conn, sql)
        try {
          resolved.zipWithIndex.foreach { case ((_, v), i) =>
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
        val resolved = if (_postgresql) _pg_resolve_columns(conn, collection, columns).take else columns
        val sql = if (_postgresql) PostgreSqlDialectDriver._update_at_sql(_pg_table_sql(conn, collection), resolved.map(_._1)) else
          dialect.updateSql(
            _table_name(collection),
            columns.map(_._1)
          )
        val stmt = _prepare_statement(conn, sql)
        try {
          resolved.zipWithIndex.foreach { case ((_, value), index) =>
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
      val columns = if (_postgresql) _pg_record_columns(connection, collection, changes).take else _record_columns(changes)
      val revisioncolumn = if (_postgresql) _pg_field(connection, collection, revisionfield).take else _column_name(revisionfield)
      val revisionvalue = if (_postgresql) s"(${PostgreSqlDialectDriver._revision_value_sql(revisioncolumn)})" else dialect.quoteIdentifier(revisioncolumn)
      val assignments =
        columns
          .map { case (name, _) =>
            s"${dialect.quoteIdentifier(name)} = ?"
          }
          .appended(
            s"${dialect.quoteIdentifier(revisioncolumn)} = $revisionvalue + 1"
          )
          .mkString(", ")
      val expectedclause =
        expectedrevision
          .map(_ =>
            s" AND $revisionvalue = ?"
          )
          .getOrElse("")
      val exclusionclause =
        exclusionguards
          .map { guard =>
            guard match {
              case EntityMutationExclusionGuard.EqualTo(fieldname, _) =>
                val column =
                  dialect.quoteIdentifier(if (_postgresql) _pg_field(connection, collection, fieldname).take else _column_name(fieldname))
                val parameter = if (_postgresql) _pg_guard_parameter(connection, collection, fieldname).take else "?"
                s" AND ($column IS NULL OR $column <> $parameter)"
              case EntityMutationExclusionGuard.Present(fieldname) =>
                s" AND ${dialect.absentValueSql(if (_postgresql) _pg_field(connection, collection, fieldname).take else _column_name(fieldname))}"
            }
          }
          .mkString
      val equalityguards =
        exclusionguards.collect {
          case guard: EntityMutationExclusionGuard.EqualTo => guard
        }
      val tablesql = if (_postgresql) _pg_table_sql(connection, collection) else dialect.quoteIdentifier(_table_name(collection))
      val sql = s"UPDATE $tablesql SET $assignments WHERE ${dialect.quoteIdentifier("id")} = ?$expectedclause$exclusionclause AND ${dialect.entityRevisionGuardSql(revisioncolumn)}"
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

  private def _native_delete_count(
    connection: Connection,
    plan: EntityCompareAndDeletePlan
  ): Consequence[Int] =
    Consequence {
      val revisioncolumn = if (_postgresql) _pg_field(connection, plan.collection, plan.revisionField).take else _column_name(plan.revisionField)
      val revisionvalue = if (_postgresql) s"(${PostgreSqlDialectDriver._revision_value_sql(revisioncolumn)})" else dialect.quoteIdentifier(revisioncolumn)
      val exclusions = plan.exclusionGuards.map {
        case EntityMutationExclusionGuard.EqualTo(fieldname, _) =>
          val column = dialect.quoteIdentifier(if (_postgresql) _pg_field(connection, plan.collection, fieldname).take else _column_name(fieldname))
          val parameter = if (_postgresql) _pg_guard_parameter(connection, plan.collection, fieldname).take else "?"
          s" AND ($column IS NULL OR $column <> $parameter)"
        case EntityMutationExclusionGuard.Present(fieldname) =>
          s" AND ${dialect.absentValueSql(if (_postgresql) _pg_field(connection, plan.collection, fieldname).take else _column_name(fieldname))}"
      }.mkString
      val equalities = plan.exclusionGuards.collect {
        case guard: EntityMutationExclusionGuard.EqualTo => guard
      }
      val tablesql = if (_postgresql) _pg_table_sql(connection, plan.collection) else dialect.quoteIdentifier(_table_name(plan.collection))
      val sql = s"DELETE FROM $tablesql WHERE ${dialect.quoteIdentifier("id")} = ? AND $revisionvalue = ?$exclusions AND ${dialect.entityRevisionGuardSql(revisioncolumn)}"
      val statement = _prepare_statement(connection, sql)
      try {
        statement.setString(1, plan.entryId.print)
        statement.setLong(2, plan.expectedRevision.value)
        equalities.zipWithIndex.foreach { case (guard, index) =>
          statement.setObject(3 + index, _column_value(guard.value))
        }
        statement.setLong(3 + equalities.size, EntityRevision.INITIAL.value)
        statement.setLong(4 + equalities.size, Long.MaxValue)
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
      val sql = if (_postgresql) PostgreSqlDialectDriver._delete_at_sql(_pg_table_sql(conn, collection))
        else dialect.deleteSql(_table_name(collection))
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
    Consequence {
      if (_postgresql) PostgreSqlDialectDriver._select_by_id_at_sql(_pg_table_sql(conn, collection))
      else dialect.selectByIdSql(_table_name(collection))
    }.flatMap(sql => _select_with_sql(conn, collection, id, sql))
  }

  private def _select_with_sql(
    conn: Connection,
    collection: CollectionId,
    id: EntryId,
    sql: String
  ): Consequence[Option[Record]] =
    Consequence.run {
      val stmt = _prepare_statement(conn, sql)
      try {
        stmt.setString(1, id.print)
        val rs = stmt.executeQuery()
        try {
          if (rs.next())
            _record_from_result_set(collection, rs).map(record => Some(record))
          else
            Consequence.success(None)
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
      _ <- if (_postgresql) _pg_columns(conn, collection).map(_ => ()) else Consequence.unit
      _ <- _ensure_query_columns(conn, collection, directive.query)
      r <- Consequence.run {
      val sql = if (_postgresql) _search_sql(collection, directive,
        Some(_pg_table_sql(conn, collection)), name => _pg_field(conn, collection, name).take)
        else _search_sql(collection, directive)
      val stmt = _prepare_statement(conn, sql.sql)
      try {
        _bind(stmt, sql.params)
        val rs = stmt.executeQuery()
        try {
          val buf = Vector.newBuilder[Record]
          var status: Consequence[Unit] = Consequence.unit
          while (status.isSuccess && rs.next()) {
            status = _record_from_result_set(collection, rs).map { record =>
              buf += record
              ()
            }
          }
          status.map { _ =>
            val records = buf.result()
            val range = directive.limit match {
              case QueryLimit.Unbounded => ResultRange.Exact
              case QueryLimit.Limit(n) => ResultRange.Limited(n)
            }
            SearchResult(records, range, None)
          }
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
    directive: QueryDirective,
    tablesql: Option[String] = None,
    columnname: String => String = _column_name
  ): SqlDataStore.SqlStatement = {
    val select = directive.projection match {
      case QueryProjection.All =>
        "*"
      case QueryProjection.Fields(names) if names.isEmpty =>
        "*"
      case QueryProjection.Fields(names) =>
        val normalized = names.map(columnname).distinct
        ("id" +: normalized.filterNot(_ == "id")).map(dialect.quoteIdentifier).mkString(", ")
    }
    val order = directive.order match {
      case QueryOrder.None =>
        ""
      case QueryOrder.By(field, OrderDirection.Asc) =>
        s" ORDER BY ${dialect.quoteIdentifier(columnname(field))} ASC"
      case QueryOrder.By(field, OrderDirection.Desc) =>
        s" ORDER BY ${dialect.quoteIdentifier(columnname(field))} DESC"
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
      else if (_postgresql)
        s" OFFSET ${directive.offset}"
      else
        s" LIMIT -1 OFFSET ${directive.offset}"
    val where = _where_sql(directive.query, columnname)
    SqlDataStore.SqlStatement(
      s"SELECT $select FROM ${tablesql.getOrElse(dialect.quoteIdentifier(_table_name(collection)))}${where.sql}$order$limit$offset",
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
      val sql = if (_postgresql) _count_sql(collection, directive,
        Some(_pg_table_sql(conn, collection)), name => _pg_field(conn, collection, name).take)
        else _count_sql(collection, directive)
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
    directive: QueryDirective,
    tablesql: Option[String] = None,
    columnname: String => String = _column_name
  ): SqlDataStore.SqlStatement = {
    val where = _where_sql(directive.query, columnname)
    SqlDataStore.SqlStatement(
      s"SELECT COUNT(*) FROM ${tablesql.getOrElse(dialect.quoteIdentifier(_table_name(collection)))}${where.sql}",
      where.params
    )
  }

  private def _where_sql(query: Query, columnname: String => String = _column_name): SqlDataStore.SqlStatement =
    query match {
      case Query.Empty =>
        SqlDataStore.SqlStatement.empty
      case Query.Expr(expr) =>
        _expr_sql(expr, columnname) match {
          case SqlDataStore.SqlStatement.Empty => SqlDataStore.SqlStatement.empty
          case x => x.copy(sql = s" WHERE ${x.sql}")
        }
    }

  private def _expr_sql(expr: EntityQuery.Expr, columnname: String => String = _column_name): SqlDataStore.SqlStatement =
    expr match {
      case EntityQuery.True =>
        SqlDataStore.SqlStatement.Empty
      case EntityQuery.False =>
        SqlDataStore.SqlStatement("1 = 0")
      case EntityQuery.And(items) =>
        _join_expr(items, "AND", columnname)
      case EntityQuery.Or(items) =>
        _join_expr(items, "OR", columnname)
      case EntityQuery.Not(item) =>
        val x = _expr_sql(item, columnname)
        if (x.isEmpty) SqlDataStore.SqlStatement.Empty else x.copy(sql = s"NOT (${x.sql})")
      case EntityQuery.Eq(path, value) =>
        _binary(path, "=", value, columnname)
      case EntityQuery.Ne(path, value) =>
        _binary(path, "<>", value, columnname)
      case EntityQuery.Gt(path, value) =>
        _binary(path, ">", value, columnname)
      case EntityQuery.Gte(path, value) =>
        _binary(path, ">=", value, columnname)
      case EntityQuery.Lt(path, value) =>
        _binary(path, "<", value, columnname)
      case EntityQuery.Lte(path, value) =>
        _binary(path, "<=", value, columnname)
      case EntityQuery.Contains(path, value, caseinsensitive) =>
        _like(path, s"%$value%", caseinsensitive, columnname)
      case EntityQuery.StartsWith(path, value, caseinsensitive) =>
        _like(path, s"$value%", caseinsensitive, columnname)
      case EntityQuery.EndsWith(path, value, caseinsensitive) =>
        _like(path, s"%$value", caseinsensitive, columnname)
      case EntityQuery.Like(path, pattern, caseinsensitive) =>
        _like(path, pattern, caseinsensitive, columnname)
      case EntityQuery.IsNull(path) =>
        SqlDataStore.SqlStatement(s"${dialect.quoteIdentifier(columnname(path))} IS NULL")
      case EntityQuery.IsNotNull(path) =>
        SqlDataStore.SqlStatement(s"${dialect.quoteIdentifier(columnname(path))} IS NOT NULL")
      case _ =>
        SqlDataStore.SqlStatement.Empty
    }

  private def _join_expr(
    items: Vector[EntityQuery.Expr],
    op: String,
    columnname: String => String = _column_name
  ): SqlDataStore.SqlStatement = {
    val xs = items.map(item => _expr_sql(item, columnname)).filterNot(_.isEmpty)
    if (xs.isEmpty)
      SqlDataStore.SqlStatement.Empty
    else
      SqlDataStore.SqlStatement(
        xs.map(x => s"(${x.sql})").mkString(s" $op "),
        xs.flatMap(_.params)
      )
  }

  private def _binary(path: String, op: String, value: Any, columnname: String => String = _column_name): SqlDataStore.SqlStatement =
    SqlDataStore.SqlStatement(s"${dialect.quoteIdentifier(columnname(path))} $op ?", Vector(_column_value(value)))

  private def _like(path: String, pattern: String, caseinsensitive: Boolean, columnname: String => String = _column_name): SqlDataStore.SqlStatement =
    if (caseinsensitive)
      SqlDataStore.SqlStatement(s"LOWER(${dialect.quoteIdentifier(columnname(path))}) LIKE LOWER(?)", Vector(pattern))
    else
      SqlDataStore.SqlStatement(s"${dialect.quoteIdentifier(columnname(path))} LIKE ?", Vector(pattern))

  private def _column_ref(path: String): String =
    dialect.quoteIdentifier(_column_name(path))

  private def _bind(stmt: PreparedStatement, params: Vector[Any]): Unit =
    params.zipWithIndex.foreach { case (value, index) =>
      stmt.setObject(index + 1, value)
    }

  private def _record_from_result_set(
    collection: CollectionId,
    rs: java.sql.ResultSet
  ): Consequence[Record] =
    _record_codec.recordFromResultSetC(collection, rs)

  private def _column_name(name: String): String =
    _record_codec.columnName(name)
}

object SqlDataStore {
  private[sql] final case class EntityCreateAddress(table: String, exists: Boolean,
    schema: Option[String] = None, oid: Option[Long] = None) {
    private[sql] def _qualified_sql: String = schema.map(PostgreSqlDialectDriver.quoteIdentifier).map(_ + ".").getOrElse("") + PostgreSqlDialectDriver.quoteIdentifier(table)
    private[sql] def _same_relation(other: EntityCreateAddress): Boolean =
      if (oid.nonEmpty && other.oid.nonEmpty) oid == other.oid else schema == other.schema && table == other.table
  }
  private[sql] final case class EntityCreateCollation(charset: Option[String], collation: String,
    namespace: Option[String] = None, primarykey: Option[String] = None)

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
  case object Postgresql extends DialectSelection {
    def resolve(jdbcUrl: String): SqlDialectDriver =
      if (dialectFromJdbcUrl(jdbcUrl) == Postgresql) PostgreSqlDialectDriver
      else throw new IllegalArgumentException("PostgreSQL dialect requires a PostgreSQL JDBC URL")
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
    else if (lower.startsWith("jdbc:postgresql:"))
      Postgresql
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
    driverClassName.orElse(if (resolveddialect eq PostgreSqlDialectDriver) Some("org.postgresql.Driver") else None).foreach(hikariconfig.setDriverClassName)
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
      driverClassName.orElse(if (resolveddialect eq PostgreSqlDialectDriver) Some("org.postgresql.Driver") else None).foreach(hikariconfig.setDriverClassName)
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
