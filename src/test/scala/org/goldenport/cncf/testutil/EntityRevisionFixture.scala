package org.goldenport.cncf.testutil

import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.entity.EntityConcurrencyMetadata
import org.goldenport.record.Record

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
object EntityRevisionFixture {
  def entitySeed(
    collection: DataStore.CollectionId,
    record: Record
  ): DataStoreSpace.SeedEntry =
    DataStoreSpace.SeedEntry(collection, persistedRecord(record))

  def persistedRecord(record: Record): Record =
    EntityConcurrencyMetadata.initializeForCreate(record)
}
