package org.goldenport.cncf.repository

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{Cursor, DataStore, QueryDirective, ResultRange, SearchResult, SearchableDataStore}
import org.goldenport.cncf.datastore.DataStoreSpace

/*
 * @since   Jan.  6, 2026
 *  version Jan. 10, 2026
 *  version Feb. 21, 2026
 * @version Mar. 11, 2026
 * @author  ASAMI, Tomoharu
 */
trait RepositorySupport {
  protected def executionContext: ExecutionContext

  protected final def unitOfWork = executionContext.runtime.unitOfWork

  protected final def datastore: DataStoreSpace = executionContext.dataStoreSpace

  // protected final def searchableDatastore: SearchableDataStore =
  //   unitOfWork.searchableDatastore.getOrElse(
  //     throw new IllegalStateException("SearchableDataStore is required for search operations")
  //   )

  protected final def searchRecords(
    collection: DataStore.CollectionId,
    directive: QueryDirective
  ): Consequence[SearchResult] =
    datastore.search(collection, directive)

  protected final def searchForView(
    collection: DataStore.CollectionId,
    directive: QueryDirective
  ): Consequence[SearchResult] =
    searchRecords(collection, directive)

  protected final def mapRecords[A](
    result: SearchResult
  )(f: Record => A): (Vector[A], ResultRange, Option[Cursor]) =
    (result.records.map(f), result.range, result.cursor)
}
