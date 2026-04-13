package org.goldenport.cncf.datastore

import java.util.concurrent.atomic.AtomicLong
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.id.UniversalId
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record

/*
 * @since   Feb. 25, 2026
 *  version Apr. 10, 2026
 * @version Apr. 14, 2026
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
  ): Consequence[SearchResult] =
    dataStore(cid).flatMap {
      case m: SearchableDataStore =>
        m.search(cid, directive)
      case _ =>
        Consequence.dataStoreUnavailable(s"datastore is not searchable: ${cid.print}")
    }

  def inject(
    cid: DataStore.CollectionId,
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val entryid = _entry_id(record, cid)
    dataStore(cid).flatMap(_.create(cid, entryid, record))
  }

  def inject(
    seed: DataStoreSpace.Seed
  )(using ctx: ExecutionContext): Consequence[Unit] =
    seed.entries.foldLeft(Consequence.unit) { (z, entry) =>
      z.flatMap(_ => inject(entry.collection, entry.record))
    }

  def importSeed(
    seed: DataStoreSeed
  )(using ctx: ExecutionContext): Consequence[Unit] =
    seed.entries.foldLeft(Consequence.unit) { (z, entry) =>
      z.flatMap { _ =>
        val entryid = _entry_id(entry.record, entry.collection)
        dataStore(entry.collection).flatMap(_.create(entry.collection, entryid, entry.record)).recoverWith {
          case _ =>
            dataStore(entry.collection).flatMap(_.save(entry.collection, entryid, entry.record))
        }
      }
    }

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
    val sqlitepath = ConfigurationAccess.getString(conf, "cncf.datastore.sqlite.path")
    val sqlnormalizecolumns =
      ConfigurationAccess
        .getString(conf, "cncf.datastore.sql.normalize-column-names")
        .orElse(ConfigurationAccess.getString(conf, "cncf.datastore.sqlite.normalize-column-names"))
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
}
