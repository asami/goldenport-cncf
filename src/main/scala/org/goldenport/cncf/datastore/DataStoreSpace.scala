package org.goldenport.cncf.datastore

import org.goldenport.Consequence

/*
 * @since   Feb. 25, 2026
 * @version Feb. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class DataStoreSpace {
  private var _entity_stores: Vector[DataStore] = Vector.empty

  def dataStore(cid: DataStore.CollectionId): Consequence[DataStore] = ???
}
