package org.goldenport.cncf.datastore

import org.goldenport.record.Record

/*
 * @since   Mar. 27, 2026
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final case class DataStoreSeedEntry(
  collection: DataStore.CollectionId,
  record: Record
)

final case class DataStoreSeed(
  entries: Vector[DataStoreSeedEntry]
)
