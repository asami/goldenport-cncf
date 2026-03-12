package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Feb. 25, 2026
 * @version Mar. 11, 2026
 * @author  ASAMI, Tomoharu
 */
class DataStoreSpace {
  private var _entity_stores: Vector[DataStore] = Vector.empty

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
  ): Consequence[SearchResult] = ???
}

object DataStoreSpace {
  def create(conf: ResolvedConfiguration): DataStoreSpace = {
    val dss = new DataStoreSpace()
    val mds = DataStore.inMemorySearchable() // TODO
    dss.addDataStore(mds)
  }
}
