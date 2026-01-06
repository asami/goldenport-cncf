package org.goldenport.cncf.repository

import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{Cursor, DataStore, QueryDirective, ResultRange, SelectResult, SelectableDataStore}

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait RepositorySupport {
  protected def executionContext: ExecutionContext

  protected final def unitOfWork = executionContext.runtime.unitOfWork

  protected final def datastore: DataStore =
    unitOfWork.datastore

  protected final def selectableDatastore: SelectableDataStore =
    unitOfWork.selectableDatastore.getOrElse(
      throw new IllegalStateException("SelectableDataStore is required for select operations")
    )

  protected final def selectRecords(
    directive: QueryDirective
  ): SelectResult =
    selectableDatastore.select(directive)

  protected final def selectForView(
    directive: QueryDirective
  ): SelectResult =
    selectRecords(directive)

  protected final def mapRecords[A](
    result: SelectResult
  )(f: DataStore.Record => A): (Vector[A], ResultRange, Option[Cursor]) =
    (result.records.map(f), result.range, result.cursor)
}
