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
 * @version Mar. 17, 2026
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
        Consequence.failure(s"datastore is not searchable: ${cid.print}")
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

  private def _entry_id(
    record: Record,
    cid: DataStore.CollectionId
  ): DataStore.EntryId =
    record.asMap.get("id") match {
      case Some(m: UniversalId) => DataStore.StringEntryId(m.print)
      case Some(s: String) => DataStore.StringEntryId(s)
      case Some(v) => DataStore.StringEntryId(v.toString)
      case None =>
        val seq = _inject_sequence.incrementAndGet().toString
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
    val ds = sqlitepath match {
      case Some(path) => SqlDataStore.sqlite(path)
      case None => DataStore.inMemorySearchable()
    }
    dss.addDataStore(ds)
    dss
  }
}
