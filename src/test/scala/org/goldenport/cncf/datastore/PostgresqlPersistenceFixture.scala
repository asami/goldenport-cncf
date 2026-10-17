package org.goldenport.cncf.datastore

import java.io.PrintWriter
import java.lang.reflect.{InvocationHandler, InvocationTargetException, Method, Proxy}
import java.sql.{Connection, SQLFeatureNotSupportedException}
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import javax.sql.DataSource
import org.goldenport.Consequence
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.{PostgreSqlDialectDriver, SqlDataStore, SqlDialectDriver}
import org.goldenport.cncf.entity.EntityCreateFailureSafetyFixture
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalFixture.success
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.postgresql.ds.PGSimpleDataSource
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import scala.util.control.NonFatal

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
private[cncf] object PostgresqlPersistenceFixture {
  final class LivePostgresqlContainer extends PostgreSQLContainer(DockerImageName.parse("postgres:17.11-bookworm")) {
    withDatabaseName("cncf_phase96_native")
    withUsername("cncf")
    withPassword("cncf-private-native-fixture")
    withCommand("postgres", "-c", "fsync=on", "-c", "synchronous_commit=on")
  }

  final case class StoredTable(columns: Vector[(String, String)], rows: Vector[Map[String, Any]])

  enum AcknowledgementMode {
    case Normal, Reject, Indeterminate, Throw, Null, RejectRoot, ThrowRoot, NullRoot, RejectVersioned, RejectConditional
  }

  final class ConnectionFaults {
    @volatile private var _fail_rollback = false
    private val _rollback_attempts = new AtomicInteger()
    private val _abort_attempts = new AtomicInteger()
    private val _unsafe_restores = new AtomicInteger()
    def failRollback: Boolean = _fail_rollback
    def rollbackAttempts: Int = _rollback_attempts.get()
    def abortAttempts: Int = _abort_attempts.get()
    def unsafeRestores: Int = _unsafe_restores.get()
    def arm(failRollback: Boolean): Unit = {
      _fail_rollback = failRollback
      _rollback_attempts.set(0)
      _abort_attempts.set(0)
      _unsafe_restores.set(0)
    }
    def wrap(connection: Connection): Connection = {
      var unfinished = false
      Proxy.newProxyInstance(classOf[Connection].getClassLoader, Array(classOf[Connection]), new InvocationHandler {
        def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
          val arguments = Option(args).getOrElse(Array.empty[AnyRef])
          method.getName match {
            case "rollback" =>
              _rollback_attempts.incrementAndGet()
              if (_fail_rollback) throw new java.sql.SQLException("owned-fixture-rollback-refused")
            case "abort" => _abort_attempts.incrementAndGet()
            case "setAutoCommit" if arguments.headOption.contains(java.lang.Boolean.TRUE) && unfinished =>
              _unsafe_restores.incrementAndGet()
            case _ => ()
          }
          val result = try method.invoke(connection, arguments*) catch {
            case error: InvocationTargetException => throw error.getCause
          }
          method.getName match {
            case "setAutoCommit" if arguments.headOption.contains(java.lang.Boolean.FALSE) => unfinished = true
            case "commit" | "rollback" | "abort" => unfinished = false
            case _ => ()
          }
          result
        }
      }).asInstanceOf[Connection]
    }
  }

  final class FaultDataSource(underlying: DataSource, val faults: ConnectionFaults) extends DataSource {
    def getConnection(): Connection = faults.wrap(underlying.getConnection())
    def getConnection(username: String, password: String): Connection = faults.wrap(underlying.getConnection(username, password))
    def getLogWriter(): PrintWriter = underlying.getLogWriter()
    def setLogWriter(writer: PrintWriter): Unit = underlying.setLogWriter(writer)
    def getLoginTimeout(): Int = underlying.getLoginTimeout()
    def setLoginTimeout(seconds: Int): Unit = underlying.setLoginTimeout(seconds)
    def getParentLogger(): Logger = underlying.getParentLogger()
    def unwrap[T](iface: Class[T]): T = underlying.unwrap(iface)
    def isWrapperFor(iface: Class[?]): Boolean = underlying.isWrapperFor(iface)
  }

  final class AcknowledgementStore(dialect: SqlDialectDriver, dataSource: DataSource)
      extends SqlDataStore(dialect, dataSource) {
    @volatile private var _mode = AcknowledgementMode.Normal
    private val _commit_attempts = new AtomicInteger()
    private val _side_attempts = new AtomicInteger()
    private val _root_attempts = new AtomicInteger()
    def commitAttempts: Int = _commit_attempts.get()
    def sideAttempts: Int = _side_attempts.get()
    def rootAttempts: Int = _root_attempts.get()
    def arm(mode: AcknowledgementMode): Unit = {
      _mode = mode
      _commit_attempts.set(0)
      _side_attempts.set(0)
      _root_attempts.set(0)
    }
    override protected def create_EntityCheckpoint(plan: EntityCreatePlan, checkpoint: EntityCreateCheckpoint): Consequence[Unit] =
      checkpoint match {
        case EntityCreateCheckpoint.BeforeSideWrite(_) => _side_attempts.incrementAndGet(); Consequence.unit
        case EntityCreateCheckpoint.BeforeRootWrite =>
          _root_attempts.incrementAndGet()
          if (_mode == AcknowledgementMode.RejectRoot) EntityVersionedMutationFailure.providerFailure("owned-fixture-root-after-side")
          else if (_mode == AcknowledgementMode.ThrowRoot) throw new IllegalStateException("owned-fixture-root-after-side-throw")
          else if (_mode == AcknowledgementMode.NullRoot) null
          else Consequence.unit
        case _ => Consequence.unit
      }
    override protected def versioned_mutation_checkpoint(checkpoint: EntityVersionedMutationCheckpoint): Consequence[Unit] =
      if (_mode == AcknowledgementMode.RejectVersioned && checkpoint == EntityVersionedMutationCheckpoint.BeforePublish)
        EntityVersionedMutationFailure.providerFailure("owned-fixture-versioned-before-publish")
      else Consequence.unit
    override protected def conditional_transition_checkpoint(checkpoint: DataStoreConditionalTransitionCheckpoint): Consequence[Unit] =
      if (_mode == AcknowledgementMode.RejectConditional && checkpoint == DataStoreConditionalTransitionCheckpoint.BeforePublish)
        DataStoreConditionalTransitionFailure.providerFailure("owned-fixture-conditional-before-publish")
      else Consequence.unit
    override protected def versioned_mutation_commit(connection: Connection): Consequence[Unit] =
      _acknowledge(connection, conditional = false)
    override protected def conditional_transition_commit(connection: Connection): Consequence[Unit] =
      _acknowledge(connection, conditional = true)
    private def _acknowledge(connection: Connection, conditional: Boolean): Consequence[Unit] = {
      _commit_attempts.incrementAndGet()
      if (_mode == AcknowledgementMode.Reject) {
        if (conditional) DataStoreConditionalTransitionFailure.transactionFailure("owned-fixture-rejected-commit")
        else EntityVersionedMutationFailure.transactionFailure("owned-fixture-rejected-commit")
      } else {
        connection.commit()
        _mode match {
          case AcknowledgementMode.Indeterminate =>
            if (conditional) DataStoreConditionalTransitionFailure.transactionIndeterminate("owned-fixture-ack-loss-after-real-commit")
            else EntityVersionedMutationFailure.transactionIndeterminate("owned-fixture-ack-loss-after-real-commit")
          case AcknowledgementMode.Throw => throw new IllegalStateException("owned-fixture-ack-throw-after-real-commit")
          case AcknowledgementMode.Null => null
          case _ => Consequence.unit
        }
      }
    }
  }

  final class Database private[cncf](jdbcurl: String, username: String, password: String, tokens: AtomicInteger) {
    private var _stores = Vector.empty[SqlDataStore]
    def token(prefix: String): String = {
      val label = prefix.map(character =>
        if ((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z') ||
            (character >= '0' && character <= '9') || character == '_') character else '_')
      s"cncf_${tokens.incrementAndGet()}_$label"
    }
    def source(searchPath: Option[String] = None): PGSimpleDataSource = {
      val datasource = new PGSimpleDataSource()
      datasource.setURL(jdbcurl)
      datasource.setUser(username)
      datasource.setPassword(password)
      searchPath.foreach(datasource.setCurrentSchema)
      datasource
    }
    def open(normalizeColumns: Boolean = false, searchPath: Option[String] = None): SqlDataStore = {
      val store = if (searchPath.nonEmpty) new SqlDataStore(PostgreSqlDialectDriver, source(searchPath),
        config = SqlDataStore.Config(normalizeColumnNames = normalizeColumns))
      else SqlDataStore.jdbc(jdbcurl, SqlDataStore.Postgresql, Some(username), Some(password), Some("org.postgresql.Driver"),
        config = SqlDataStore.Config(normalizeColumnNames = normalizeColumns))
      _stores :+= store
      store
    }
    def openFault(normalizeColumns: Boolean = false): EntityCreateFailureSafetyFixture.SqlStore = {
      val store = new EntityCreateFailureSafetyFixture.SqlStore(PostgreSqlDialectDriver, source(),
        SqlDataStore.Config(normalizeColumnNames = normalizeColumns))
      _stores :+= store
      store
    }
    def openAcknowledgement(faults: ConnectionFaults = new ConnectionFaults()): AcknowledgementStore = {
      val store = new AcknowledgementStore(PostgreSqlDialectDriver, new FaultDataSource(source(), faults))
      _stores :+= store
      store
    }
    def configuration(prefix: String, extras: (String, String)*): ResolvedConfiguration =
      PostgresqlPersistenceFixture.configuration(connectionValues(prefix) ++ extras)
    def parameters(prefix: String, extras: (String, String)*): ResolvedParameters =
      ResolvedParameters.fromComponentConfig((connectionValues(prefix) ++ extras).toMap, "owned-native-fixture", None)
    def connectionValues(prefix: String): Vector[(String, String)] = Vector(
      s"$prefix.jdbc.url" -> jdbcurl, s"$prefix.jdbc.user" -> username,
      s"$prefix.jdbc.password" -> password, s"$prefix.jdbc.driver" -> "org.postgresql.Driver")
    def statement(sql: String): Unit = withConnection { connection =>
      val statement = connection.createStatement()
      try { statement.execute(sql); () } finally statement.close()
    }
    def scalar(sql: String): String = withConnection { connection =>
      val statement = connection.createStatement()
      try {
        val rows = statement.executeQuery(sql)
        try { rows.next(); rows.getString(1) } finally rows.close()
      } finally statement.close()
    }
    def withConnection[A](body: Connection => A): A = {
      val connection = source().getConnection()
      try body(connection) finally connection.close()
    }
    def snapshotAll(): Map[(String, String), StoredTable] = withConnection { connection =>
      val statement = connection.createStatement()
      val tables = try {
        val rows = statement.executeQuery("SELECT n.nspname,c.relname FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE c.relkind='r' AND n.nspname NOT LIKE 'pg_%' AND n.nspname <> 'information_schema' ORDER BY n.nspname,c.relname")
        try {
          val selected = Vector.newBuilder[(String, String)]
          while (rows.next()) selected += rows.getString(1) -> rows.getString(2)
          selected.result()
        } finally rows.close()
      } finally statement.close()
      tables.map { case (schema, table) =>
        val qualified = quote(schema) + "." + quote(table)
        val reader = connection.createStatement()
        try {
          val rows = reader.executeQuery(s"SELECT * FROM $qualified ORDER BY ${quote("id")}")
          try {
            val metadata = rows.getMetaData
            val columns = (1 to metadata.getColumnCount).toVector.map(index => metadata.getColumnLabel(index) -> metadata.getColumnTypeName(index))
            val records = Vector.newBuilder[Map[String, Any]]
            while (rows.next()) records += columns.zipWithIndex.map { case ((name, _), index) => name -> rows.getObject(index + 1) }.toMap
            (schema -> table) -> StoredTable(columns, records.result())
          } finally rows.close()
        } finally reader.close()
      }.toMap
    }
    def close(): Unit = {
      var firsterror = Option.empty[Throwable]
      try {
        _stores.foreach { store =>
          try success(store.closeC()) catch {
            case NonFatal(error) => firsterror match {
              case Some(first) => if (first ne error) first.addSuppressed(error)
              case None => firsterror = Some(error)
            }
          }
        }
      } finally _stores = Vector.empty
      firsterror.foreach(error => throw error)
    }
  }

  def quote(identifier: String): String = PostgreSqlDialectDriver.quoteIdentifier(identifier)
  def configuration(values: Seq[(String, String)]): ResolvedConfiguration =
    ResolvedConfiguration(Configuration(values.map { case (key, value) => key -> ConfigurationValue.StringValue(value) }.toMap), ConfigurationTrace.empty)
  def context(store: DataStore): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(store)
    context
  }
  def revision(value: Long): EntityRevision = success(EntityRevision.createC(value))
  def seedCanonical(session: EntityCreateFailureSafetyFixture.Session, preserveDeletionPolicy: Boolean = false): Unit = {
    session.seed()
    given ExecutionContext = session.base.context
    given org.goldenport.cncf.entity.EntityPersistentCreate[EntityCreateFailureSafetyFixture.Input] = session.persistent
    Vector(session.base.id, session.base.unrelatedId).foreach { identity =>
      val entry = DataStore.EntryId(identity)
      val stored = success(session.base.provider.load(session.base.dataCollection, entry)).get
      val complemented = session.base.context.runtime.entityCreateDefaultsPolicy
        .complementCreateRecord[EntityCreateFailureSafetyFixture.Input](stored, identity, org.goldenport.cncf.entity.EntityCreateOptions.default)
      val shaped = Record(complemented.fields.filter(field =>
        !preserveDeletionPolicy || stored.keySet.contains("aliveness") || field.key != "aliveness"))
      val placeholders = Vector("content", "deleted_at", "deletedAt", "deleted_by", "deletedBy", "trace_id", "correlation_id")
        .filterNot(shaped.getAny(_).nonEmpty)
        .map(name => name -> org.simplemodeling.model.directive.Update.SetNull)
      success(session.base.provider.save(session.base.dataCollection, entry,
        shaped ++ Record.dataAuto(placeholders*)))
    }
  }
  def seedDirect(session: EntityCreateProviderFixture.DirectSession): Unit = {
    session.seed()
    given ExecutionContext = session.context
    success(session.store.save(session.rootCollection, session.oldEntry, Record.dataAuto("body_id" -> session.oldEntry.print)))
    success(session.store.save(session.sideCollection, session.oldEntry, Record.dataAuto("title" -> "Retained side title")))
  }
  def versionedPlan(collection: DataStore.CollectionId, entryId: DataStore.EntryId, changes: Record,
    expectedRevision: Long = 1L, sideEffects: Vector[EntityVersionedSideEffect] = Vector.empty): EntityVersionedMutationPlan =
    EntityVersionedMutationPlan(collection, entryId, "cncf_revision",
      org.goldenport.cncf.entity.EntityConcurrencyPolicy.Optimistic,
      org.goldenport.cncf.entity.EntityWritePolicy.WriteIfChanged,
      org.goldenport.cncf.entity.RevisionPreconditionPolicy.ObservedRequired, Some(revision(expectedRevision)),
      EntityVersionedRootMutation.Patch(changes), sideEffects = sideEffects)

  final class MutationSession(token: String, val store: SqlDataStore) {
    val rootCollection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "native", s"${token}_root"))
    val sideCollection: DataStore.CollectionId = DataStore.CollectionId(s"${token}_side")
    val successorCollection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "native", s"${token}_successor"))
    val rootEntry: DataStore.EntryId = DataStore.StringEntryId("selected")
    val sideEntry: DataStore.EntryId = DataStore.StringEntryId("selectedbody")
    val successorEntry: DataStore.EntryId = DataStore.StringEntryId("successor")
    val oldEntry: DataStore.EntryId = DataStore.StringEntryId("unrelated")
    val executionContext: ExecutionContext = context(store)
    val componentOwner: DataStoreComponentOwner = success(DataStoreComponentOwner.create("native-fixture"))
    def seed(revisionValue: Any = 1L, status: String = "open"): Unit = {
      given ExecutionContext = executionContext
      success(store.create(rootCollection, rootEntry, Record.dataAuto("title" -> "Before", "status" -> status,
        "cncf_revision" -> revisionValue, "body_id" -> sideEntry.print, "aliveness" -> "alive", "successor_id" -> "")))
      success(store.create(sideCollection, sideEntry, Record.dataAuto("content" -> "Before body")))
      success(store.create(rootCollection, oldEntry, Record.dataAuto("title" -> "Unrelated", "status" -> "open", "cncf_revision" -> 91L,
        "aliveness" -> "alive", "successor_id" -> "")))
      success(store.create(sideCollection, oldEntry, Record.dataAuto("content" -> "Unrelated body")))
      success(store.create(successorCollection, oldEntry, Record.dataAuto("title" -> "Unrelated successor", "cncf_revision" -> 91L)))
    }
    def sideEffect(content: String = "Changed complete body"): EntityVersionedSideEffect.Save =
      EntityVersionedSideEffect.Save(sideCollection, sideEntry, Record.dataAuto("content" -> content))
    def updatePlan(title: String = "After", expectedRevision: Long = 1L): EntityVersionedMutationPlan =
      versionedPlan(rootCollection, rootEntry, Record.dataAuto("title" -> title), expectedRevision, Vector(sideEffect()))
    def conditionalPlan(bind: Boolean = false, expectedRevision: Long = 1L,
      rootEntryId: DataStore.EntryId = rootEntry, successorEntryId: DataStore.EntryId = successorEntry,
      expectedFields: Vector[DataStoreConditionalExpectedField] = Vector(DataStoreConditionalExpectedField("status", success(DataStoreConditionalValue.from("open"))))
    ): DataStoreConditionalTransitionPlan = {
      val root = success(DataStoreConditionalRoot.create(componentOwner, rootCollection, rootEntryId, "cncf_revision",
        Some(revision(expectedRevision)), expectedFields,
        Record.dataAuto("status" -> "closed", "successor_id" -> successorEntryId.print), revision(expectedRevision + 1)))
      val successor = if (bind) DataStoreConditionalSuccessor.Bind(componentOwner, successorCollection,
        successorEntryId, "cncf_revision", EntityRevision.INITIAL)
      else DataStoreConditionalSuccessor.Create(componentOwner, successorCollection, successorEntryId,
        "cncf_revision", Record.dataAuto("id" -> successorEntryId.print, "title" -> "Successor", "cncf_revision" -> 1L))
      success(DataStoreConditionalTransitionPlan.create(root, successor, Vector(sideEffect())))
    }
    def createPlan(): EntityCreatePlan = EntityCreatePlan(rootCollection, DataStore.StringEntryId("fresh"),
      Record.dataAuto("title" -> "Created", "cncf_revision" -> 1L, "body_id" -> "freshbody"),
      Vector(EntityVersionedSideEffect.Save(sideCollection, DataStore.StringEntryId("freshbody"), Record.dataAuto("content" -> "Fresh complete body"))))
    def load(reader: DataStore = store): Record = success(reader.load(rootCollection, rootEntry)(using executionContext)).get
  }
}

private[cncf] trait PostgresqlAcceptance extends BeforeAndAfterAll { self: Suite =>
  import PostgresqlPersistenceFixture.*
  private var _postgresql_container: Option[LivePostgresqlContainer] = None
  private val _tokens = new AtomicInteger()
  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (!sys.env.get("CNCF_LIVE_POSTGRESQL_TEST").contains("true"))
      throw new IllegalStateException("Required native PostgreSQL acceptance needs CNCF_LIVE_POSTGRESQL_TEST=true")
    val container = new LivePostgresqlContainer()
    try {
      container.start()
      val database = new Database(container.getJdbcUrl, container.getUsername, container.getPassword, _tokens)
      try {
        if (database.scalar("SHOW server_version_num") != "170011")
          throw new IllegalStateException("The required PostgreSQL baseline is not selected")
        if (database.scalar("SHOW fsync") != "on" || database.scalar("SHOW synchronous_commit") != "on")
          throw new IllegalStateException("Native persistence requires fsync and synchronous_commit enabled")
      } finally database.close()
      _postgresql_container = Some(container)
    } catch {
      case NonFatal(error) => container.stop(); throw error
    }
  }
  override protected def afterAll(): Unit =
    try {
      _postgresql_container.foreach(_.stop())
      _postgresql_container = None
    } finally super.afterAll()
  protected final def with_postgresql[A](body: Database => A): A =
    _postgresql_container match {
      case Some(container) =>
        val database = new Database(container.getJdbcUrl, container.getUsername, container.getPassword, _tokens)
        try body(database) finally database.close()
      case None => throw new IllegalStateException("Real PostgreSQL is required; unavailable execution supplies no acceptance")
    }
}
