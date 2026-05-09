package org.goldenport.cncf.datastore

import java.util.concurrent.atomic.AtomicLong
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.id.UniversalId
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.config.{ConfigurationAccess, ResolvedParameter}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.goldenport.record.Record

/*
 * @since   Feb. 25, 2026
 *  version Apr. 15, 2026
 * @version May. 10, 2026
 * @author  ASAMI, Tomoharu
 */
class DataStoreSpace {
  private var _entity_stores: Vector[DataStore] = Vector.empty
  private val _inject_sequence = new AtomicLong(0L)

  def addDataStore(ds: DataStore): DataStoreSpace = {
    _entity_stores = _entity_stores :+ ds
    this
  }

  def dataStore(cid: DataStore.CollectionId): Consequence[DataStore] =
    Consequence.successOrServiceProviderByKeyNotFound(
      _entity_stores.find(_.isAccept(cid))
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
          case success: Consequence.Success[A] =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result))
          case failure: Consequence.Failure[A] =>
            calltree.leave(Map(
              "outcome" -> "failure",
              "status" -> failure.conclusion.status.webCode.code.toString,
              "error" -> failure.conclusion.display
            ))
          case other =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(other))
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave()
          throw e
      }
    } else {
      body
    }
  }

  private def _with_calltree_c[A](
    label: String,
    attributes: Map[String, String],
    calltreeKind: String = "space"
  )(
    body: => Consequence[A]
  )(using ctx: ExecutionContext): Consequence[A] = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> calltreeKind))
      try {
        val result = body
        result match {
          case success: Consequence.Success[A] =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result))
            success
          case failure: Consequence.Failure[A] =>
            calltree.leave(Map(
              "outcome" -> "failure",
              "status" -> failure.conclusion.status.webCode.code.toString,
              "error" -> failure.conclusion.display
            ))
            failure
        }
      } catch {
        case e: Throwable =>
          calltree.leave()
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
      "query" -> _truncate_calltree_text(_redact_sensitive_text(directive.query.toString), 2000),
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

  private def _redact_sensitive_text(value: String): String = {
    val sensitive = "(?i)(password|passwd|secret|token|session|authorization|cookie|credential|api[_-]?key)"
    val jsonLike = (s"""("?$sensitive"?\\s*[:=]\\s*)("[^"]*"|'[^']*'|[^,}\\]\\s]+)""").r
    val formLike = (s"""($sensitive)(\\s*[=:]\\s*)([^&\\s,}\\]]+)""").r
    val jsonRedacted = jsonLike.replaceAllIn(value, m => s"${m.group(1)}***")
    formLike.replaceAllIn(jsonRedacted, m => s"${m.group(1)}${m.group(2)}***")
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
    val sqlitepath = _get_string(
      conf,
      "textus.datastore.sqlite.path",
      "cncf.datastore.sqlite.path"
    )
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
    val ds = sqlitepath match {
      case Some(path) =>
        SqlDataStore.sqlite(
          path,
          config = SqlDataStore.Config(
            normalizeColumnNames = sqlnormalizecolumns
          )
        )
      case None => DataStore.inMemorySearchable()
    }
    dss.addDataStore(ds)
    dss
  }

  private def _get_string(
    conf: ResolvedConfiguration,
    primary: String,
    compatibility: String
  ): Option[String] =
    ConfigurationAccess.getString(conf, primary).orElse(ConfigurationAccess.getString(conf, compatibility))
}
