package org.goldenport.cncf.datastore

import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicLong
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.id.UniversalId
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.config.{ConfigurationAccess, ResolvedParameter}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.goldenport.cncf.datastore.sql.SqlDataStoreIdentity
import org.goldenport.cncf.subsystem.{SystemNodeDataStoreBinding, SystemNodeResourceLease}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder

/*
 * @since   Feb. 25, 2026
 *  version Apr. 15, 2026
 *  version May. 11, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
class DataStoreSpace {
  // Nested ActionCalls restore the caller's component-scoped datastore.
  final case class Binding private[DataStoreSpace] (dataStore: Option[DataStore])

  private var _entity_stores: Vector[DataStore] = Vector.empty
  private val _inject_sequence = new AtomicLong(0L)
  private val _scoped_entity_store = new ThreadLocal[DataStore]()

  def addDataStore(ds: DataStore): DataStoreSpace = {
    _entity_stores = _entity_stores :+ ds
    this
  }

  def useDataStore(ds: DataStore): DataStoreSpace = {
    _entity_stores = Vector(ds)
    this
  }

  def bindDataStore(ds: DataStore): Unit =
    _scoped_entity_store.set(ds)

  def clearBoundDataStore(): Unit =
    _scoped_entity_store.remove()

  def captureBinding(): Binding =
    Binding(Option(_scoped_entity_store.get()))

  def restoreBinding(binding: Binding): Unit =
    binding.dataStore match {
      case Some(datastore) => bindDataStore(datastore)
      case None => clearBoundDataStore()
    }

  def useApplicationDataStore(
    params: org.goldenport.cncf.config.ResolvedParameters,
    componentName: String,
    name: String = "application"
  ): DataStoreSpace =
    useApplicationDataStore(ComponentDataStore.Environment(params), componentName, name)

  def useApplicationDataStore(
    environment: ComponentDataStore.Environment,
    componentName: String,
    name: String
  ): DataStoreSpace =
    ComponentDataStore.resolveForDataStoreSpace(environment, ComponentDataStore.Request(componentName, name)) match {
      case Some(datastore) => useDataStore(datastore)
      case None => this
    }

  def bindApplicationDataStore(
    environment: ComponentDataStore.Environment,
    componentName: String,
    name: String
  ): Unit =
    ComponentDataStore.resolveForDataStoreSpace(environment, ComponentDataStore.Request(componentName, name)) match {
      case Some(datastore) => bindDataStore(datastore)
      case None => clearBoundDataStore()
    }

  private[cncf] def bindManagedApplicationDataStoreC(
    environment: ComponentDataStore.Environment,
    componentName: String,
    name: String,
    binding: SystemNodeDataStoreBinding,
    lease: SystemNodeResourceLease,
    key: SqlDataStoreIdentity.HmacKey
  ): Consequence[Unit] =
    ComponentDataStore.resolveManagedForDataStoreSpaceC(
      environment,
      ComponentDataStore.Request(componentName, name),
      binding,
      lease,
      key
    ).map {
      case Some(datastore) => bindDataStore(datastore)
      case None => clearBoundDataStore()
    }

  def dataStore(cid: DataStore.CollectionId): Consequence[DataStore] =
    Consequence.successOrServiceProviderByKeyNotFound(
      Option(_scoped_entity_store.get()).filter(_.isAccept(cid))
        .orElse(_entity_stores.find(_.isAccept(cid)))
    )("datastore", cid.print)

  def search(
    cid: DataStore.CollectionId,
    directive: QueryDirective
  )(using ctx: ExecutionContext): Consequence[SearchResult] =
    dataStore(cid).flatMap {
      case m: SearchableDataStore =>
        _with_calltree_c("io:datastore:search", _datastore_calltree_attributes("search", cid, directive, m), "io") {
          m.search(cid, directive)
        }
      case _ =>
        Consequence.dataStoreUnavailable(s"datastore is not searchable: ${cid.print}")
    }

  def count(
    cid: DataStore.CollectionId,
    directive: QueryDirective
  )(using ctx: ExecutionContext): Consequence[Int] =
    dataStore(cid).flatMap {
      case m: SearchableDataStore =>
        _with_calltree_c("io:datastore:count", _datastore_calltree_attributes("count", cid, directive, m), "io") {
          m.count(cid, directive)
        }
      case _ =>
        Consequence.dataStoreUnavailable(s"datastore is not countable: ${cid.print}")
    }

  def totalCountCapability(
    cid: DataStore.CollectionId
  ): Consequence[TotalCountCapability] =
    dataStore(cid).map {
      case m: SearchableDataStore =>
        m.totalCountCapability(cid)
      case _ =>
        TotalCountCapability.Unsupported
    }

  def entityMutationProviderCapabilities(
    cid: DataStore.CollectionId
  ): Consequence[EntityMutationProviderCapabilities] =
    dataStore(cid).map {
      case provider: EntityVersionedMutationDataStore =>
        provider.entityMutationProviderCapabilities
      case _ =>
        EntityMutationProviderCapabilities.empty
    }

  def selectEntityMutationPath(
    cid: DataStore.CollectionId,
    request: EntityMutationPathRequest
  ): Consequence[EntityMutationExecutionPath] =
    entityMutationProviderCapabilities(cid).flatMap(
      EntityMutationPathPlanner.selectC(_, request)
    )

  def mutateEntityDirect(
    plan: EntityDirectMutationPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityMutationProviderResult] =
    _with_calltree_c(
      "space:datastore:entity-direct-mutation",
      _datastore_space_attributes(
        "entity-direct-mutation",
        plan.collection
      ),
      "space"
    ) {
      for {
        datastore <- dataStore(plan.collection)
        result <- datastore match {
          case provider: EntityVersionedMutationDataStore =>
            EntityNativeMutationSupport
              .requireCapabilities(
                provider.entityMutationProviderCapabilities,
                "entity-direct-mutation",
                EntityMutationProviderFeature.DirectAlwaysWrite,
                plan.readbackRequirement
              )
              .flatMap(_ => provider.mutateEntityDirect(plan))
          case _ =>
            EntityNativeMutationSupport.unsupported(
              "entity-direct-mutation",
              EntityMutationProviderFeature.DirectAlwaysWrite
            )
        }
        admitted <-
          EntityMutationProviderResult.validateReadbackC(
            result,
            plan.readbackRequirement
          )
      } yield admitted
    }

  def compareAndSetEntity(
    plan: EntityCompareAndSetMutationPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityMutationProviderResult] =
    _with_calltree_c(
      "space:datastore:entity-compare-and-set-mutation",
      _datastore_space_attributes(
        "entity-compare-and-set-mutation",
        plan.collection
      ),
      "space"
    ) {
      for {
        datastore <- dataStore(plan.collection)
        result <- datastore match {
          case provider: EntityVersionedMutationDataStore =>
            EntityNativeMutationSupport
              .requireCapabilities(
                provider.entityMutationProviderCapabilities,
                "entity-compare-and-set-mutation",
                EntityMutationProviderFeature.OptimisticCompareAndSet,
                plan.readbackRequirement
              )
              .flatMap(_ => provider.compareAndSetEntity(plan))
          case _ =>
            EntityNativeMutationSupport.unsupported(
              "entity-compare-and-set-mutation",
              EntityMutationProviderFeature.OptimisticCompareAndSet
            )
        }
        admitted <-
          EntityMutationProviderResult.validateReadbackC(
            result,
            plan.readbackRequirement
          )
      } yield admitted
    }

  def mutateVersionedEntity(
    plan: EntityVersionedMutationPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityVersionedMutationResult] =
    _with_calltree_c(
      "space:datastore:entity-versioned-mutation",
      _datastore_space_attributes(
        "entity-versioned-mutation",
        plan.collection
      ),
      "space"
    ) {
      for {
        datastore <- dataStore(plan.collection)
        _ <- _ensure_same_provider(datastore, plan.sideEffects)
        result <- datastore match {
          case provider: EntityVersionedMutationDataStore =>
            provider.mutateVersionedEntity(plan)
          case _ =>
            Consequence.operationInvalid(
              "entity-versioned-mutation",
              Vector(
                Descriptor.Facet.Reason("unsupported-capability"),
                Descriptor.Facet.Capability(
                  "datastore.entity-versioned-mutation"
                )
              )
            )
        }
      } yield result
    }

  def conditionalTransition(
    plan: DataStoreConditionalTransitionPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[DataStoreConditionalTransitionResult] =
    EntityConditionalTransitionSupport.validate(plan).flatMap { admitted =>
      _with_calltree_c(
        "space:datastore:entity-conditional-transition",
        _datastore_space_attributes(
          "entity-conditional-transition",
          admitted.root.collection
        ),
        "space"
      ) {
        for {
          _ <- _ensure_conditional_component(admitted)
          providers <- _resolve_conditional_providers(admitted)
          rootprovider <- providers.headOption
            .map(Consequence.success)
            .getOrElse(
              Consequence.operationInvalid(
                "entity-conditional-transition",
                Vector(
                  Descriptor.Facet.Reason("missing-provider-domain"),
                  Descriptor.Facet.Capability(
                    "datastore.entity-conditional-transition"
                  )
                )
              )
            )
          _ <- _ensure_conditional_provider_domain(rootprovider, providers)
          result <- rootprovider match {
            case provider: EntityConditionalTransitionDataStore =>
              provider
                .conditionalTransition(admitted)
                .recoverWith(
                  DataStoreConditionalTransitionFailure.normalizeProvider
                )
            case _ =>
              Consequence.operationInvalid(
                "entity-conditional-transition",
                Vector(
                  Descriptor.Facet.Reason("unsupported-capability"),
                  Descriptor.Facet.Capability(
                    "datastore.entity-conditional-transition"
                  )
                )
              )
          }
        } yield result
      }
    }

  def inject(
    cid: DataStore.CollectionId,
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    _with_calltree("space:datastore:inject", _datastore_space_attributes("inject", cid)) {
      val entryid = _entry_id(record, cid)
      dataStore(cid).flatMap(_.create(cid, entryid, record))
    }
  }

  def inject(
    seed: DataStoreSpace.Seed
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_calltree("space:datastore:inject-seed", Map("space" -> "datastore", "operation" -> "inject-seed", "entry_count" -> seed.entries.size.toString)) {
      seed.entries.foldLeft(Consequence.unit) { (z, entry) =>
        z.flatMap(_ => inject(entry.collection, entry.record))
      }
    }

  def importSeed(
    seed: DataStoreSeed
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_calltree("space:datastore:import-seed", Map("space" -> "datastore", "operation" -> "import-seed", "entry_count" -> seed.entries.size.toString)) {
      seed.entries.foldLeft(Consequence.unit) { (z, entry) =>
        z.flatMap { _ =>
          val entryid = _entry_id(entry.record, entry.collection)
          dataStore(entry.collection).flatMap(_.create(entry.collection, entryid, entry.record)).recoverWith {
            case _ =>
              dataStore(entry.collection).flatMap(_.save(entry.collection, entryid, entry.record))
          }
        }
      }
    }

  private def _with_calltree[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => A
  )(using ctx: ExecutionContext): A = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "space"))
      try {
        val result = body
        result match {
          case success: Consequence.Success[?] =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result))
          case failure: Consequence.Failure[?] =>
            calltree.leave(
              Map("outcome" -> "failure") ++
                CallTreeValueSummary.failureAttributes(failure.conclusion)
            )
          case other =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(other))
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "exception",
            "exception_type" -> e.getClass.getName
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _with_calltree_c[A](
    label: String,
    attributes: Map[String, String],
    calltreekind: String = "space"
  )(
    body: => Consequence[A]
  )(using ctx: ExecutionContext): Consequence[A] = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> calltreekind))
      try {
        val result = body
        result match {
          case success: Consequence.Success[A] =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result))
            success
          case failure: Consequence.Failure[A] =>
            calltree.leave(
              Map("outcome" -> "failure") ++
                CallTreeValueSummary.failureAttributes(failure.conclusion)
            )
            failure
        }
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "exception",
            "exception_type" -> e.getClass.getName
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _datastore_space_attributes(
    operation: String,
    cid: DataStore.CollectionId
  ): Map[String, String] =
    Map(
      "space" -> "datastore",
      "operation" -> operation,
      "collection" -> cid.print
    )

  private def _ensure_same_provider(
    rootprovider: DataStore,
    effects: Vector[EntityVersionedSideEffect]
  ): Consequence[Unit] =
    effects
      .map(_.collection)
      .distinct
      .foldLeft(Consequence.unit) { (z, collection) =>
        z.flatMap { _ =>
          dataStore(collection).flatMap { provider =>
            if (provider eq rootprovider)
              Consequence.unit
            else
              Consequence.operationInvalid(
                "entity-versioned-mutation",
                Vector(
                  Descriptor.Facet.Reason("provider-domain-mismatch"),
                  Descriptor.Facet.Capability(
                    "datastore.entity-versioned-mutation"
                  )
                )
              )
          }
        }
      }

  private def _ensure_conditional_component(
    plan: DataStoreConditionalTransitionPlan
  ): Consequence[Unit] =
    (plan.root.collection, plan.successor.collection) match {
      case (
          DataStore.CollectionId.EntityStore(_),
          DataStore.CollectionId.EntityStore(_)
          ) if plan.root.componentOwner == plan.successor.componentOwner =>
        Consequence.unit
      case (
          DataStore.CollectionId.EntityStore(_),
          DataStore.CollectionId.EntityStore(_)
          ) =>
        _conditional_admission_failure("component-owner-mismatch")
      case _ =>
        _conditional_admission_failure("entity-collection-required")
    }

  private def _resolve_conditional_providers(
    plan: DataStoreConditionalTransitionPlan
  ): Consequence[Vector[DataStore]] = {
    val collections =
      plan.sideEffects
        .map(_.collection)
        .appended(plan.successor.collection)
        .prepended(plan.root.collection)
        .distinct
    collections.foldLeft(Consequence.success(Vector.empty[DataStore])) {
      (result, collection) =>
        for {
          providers <- result
          provider <- dataStore(collection)
        } yield providers :+ provider
    }
  }

  private def _ensure_conditional_provider_domain(
    rootprovider: DataStore,
    providers: Vector[DataStore]
  ): Consequence[Unit] =
    if (providers.forall(_ eq rootprovider))
      Consequence.unit
    else
      _conditional_admission_failure("provider-domain-mismatch")

  private def _conditional_admission_failure(
    reason: String
  ): Consequence[Unit] =
    Consequence.operationInvalid(
      "entity-conditional-transition",
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Capability(
          "datastore.entity-conditional-transition"
        )
      )
    )

  private def _entry_id(
    record: Record,
    cid: DataStore.CollectionId
  ): DataStore.EntryId =
    record.getAny("id") match {
      case Some(m: UniversalId) => DataStore.StringEntryId(m.print)
      case Some(s: String) => DataStore.StringEntryId(s)
      case Some(v) => DataStore.StringEntryId(v.toString)
      case None =>
        val seq = s"n${_inject_sequence.incrementAndGet()}"
        DataStore.DataStoreEntryId("sys", seq, cid.collectionName)
    }

  private def _datastore_calltree_attributes(
    kind: String,
    cid: DataStore.CollectionId,
    directive: QueryDirective,
    store: SearchableDataStore
  )(using ctx: ExecutionContext): Map[String, String] = {
    val base = Map(
      "collection" -> cid.print,
      "source" -> "data-store",
      "datastore" -> store.getClass.getSimpleName.stripSuffix("$"),
      "operation" -> kind,
      "real_io" -> "true",
      "query" -> _calltree_query_summary_json(directive),
      "limit" -> directive.limit.toString,
      "offset" -> directive.offset.toString
    )
    if (_calltree_sql_enabled) {
      store match {
        case m: SqlDataStore =>
          val sql =
            if (kind == "count")
              m.debugCountSql(cid, directive)
            else
              m.debugSearchSql(cid, directive)
          base ++ Map(
            "sql" -> _truncate_calltree_text(_redact_sensitive_text(sql.sql), 8000),
            "sql_params" -> _truncate_calltree_text(_redact_sensitive_text(sql.params.map(_.toString).mkString("[", ", ", "]")), 4000)
          )
        case _ =>
          base ++ Map("sql" -> "unavailable")
      }
    } else {
      base ++ Map("sql" -> "disabled")
    }
  }

  private def _calltree_sql_enabled(
    using ctx: ExecutionContext
  ): Boolean = {
    val keys = Vector(
      "textus.debug.calltree.sql",
      "textus.runtime.debug.calltree.sql",
      "cncf.debug.calltree.sql",
      "cncf.runtime.debug.calltree.sql",
      "x-textus-debug-calltree-sql"
    )
    keys.exists { key =>
      ctx.runtime.resolvedParameters.get(key).exists { param =>
        _truthy(ResolvedParameter.format_value(param.value))
      }
    }
  }

  private def _truthy(
    value: String
  ): Boolean =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "1" | "yes" | "on" => true
      case _ => false
    }

  private def _truncate_calltree_text(
    value: String,
    limit: Int
  ): String =
    if (value.length <= limit) value else value.take(limit) + "..."

  private def _calltree_query_summary_json(
    directive: QueryDirective
  ): String =
    _truncate_calltree_text(RecordEncoder.json(CallTreeValueSummary.recordSummary(Record.dataAuto(
      "query" -> _redact_sensitive_text(directive.query.toString),
      "limit" -> directive.limit,
      "offset" -> directive.offset
    ), includeInline = true)), 4000)

  private def _redact_sensitive_text(value: String): String = {
    val sensitive = "(?i)(password|passwd|secret|token|session|authorization|cookie|credential|api[_-]?key)"
    val jsonlike = (s"""("?$sensitive"?\\s*[:=]\\s*)("[^"]*"|'[^']*'|[^,}\\]\\s]+)""").r
    val formlike = (s"""($sensitive)(\\s*[=:]\\s*)([^&\\s,}\\]]+)""").r
    val jsonredacted = jsonlike.replaceAllIn(value, m => s"${m.group(1)}***")
    formlike.replaceAllIn(jsonredacted, m => s"${m.group(1)}${m.group(2)}***")
  }
}

object DataStoreSpace {
  final case class SeedEntry(
    collection: DataStore.CollectionId,
    record: Record
  )

  final case class Seed(
    entries: Vector[SeedEntry]
  )

  def default(): DataStoreSpace =
    new DataStoreSpace().addDataStore(DataStore.inMemorySearchable())

  def create(conf: ResolvedConfiguration): DataStoreSpace = {
    val dss = new DataStoreSpace()
    val datastorekind = _get_string(
      conf,
      "textus.datastore.kind",
      "cncf.datastore.kind"
    ).map(_.trim.toLowerCase(java.util.Locale.ROOT))
    val sqlitepath = _get_string(
      conf,
      "textus.datastore.path",
      "cncf.datastore.path"
    ).orElse(_get_string(
      conf,
      "textus.datastore.sqlite.path",
      "cncf.datastore.sqlite.path"
    ))
    val sqlnormalizecolumns =
      _get_string(
        conf,
        "textus.datastore.sql.normalize-column-names",
        "cncf.datastore.sql.normalize-column-names"
      ).orElse(_get_string(
        conf,
        "textus.datastore.sqlite.normalize-column-names",
        "cncf.datastore.sqlite.normalize-column-names"
      ))
        .exists(_.trim.equalsIgnoreCase("true"))
    val ds = datastorekind match {
      case Some("in-memory" | "inmemory" | "memory") =>
        DataStore.inMemorySearchable()
      case Some("local" | "sqlite") =>
        sqlitepath.map { path =>
          _ensure_parent(path)
          SqlDataStore.sqlite(
            path,
            config = SqlDataStore.Config(
              normalizeColumnNames = sqlnormalizecolumns
            )
          )
        }.getOrElse(
          Consequence.configurationInvalid(
            "textus.datastore.path is required when textus.datastore.kind is local or sqlite"
          ).RAISEC
        )
      case _ =>
        sqlitepath match {
          case Some(path) =>
            _ensure_parent(path)
            SqlDataStore.sqlite(
              path,
              config = SqlDataStore.Config(
                normalizeColumnNames = sqlnormalizecolumns
              )
            )
          case None => DataStore.inMemorySearchable()
        }
    }
    dss.addDataStore(ds)
    dss
  }

  private def _ensure_parent(path: String): Unit =
    Option(Paths.get(path).toAbsolutePath.normalize.getParent).foreach(Files.createDirectories(_))

  private def _get_string(
    conf: ResolvedConfiguration,
    primary: String,
    compatibility: String
  ): Option[String] =
    ConfigurationAccess.getString(conf, primary).orElse(ConfigurationAccess.getString(conf, compatibility))
}
