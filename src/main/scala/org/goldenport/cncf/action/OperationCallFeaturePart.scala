package org.goldenport.cncf.action

import org.goldenport.id.UniversalId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait OperationCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def execution_context: ExecutionContext =
    executionContext
}

trait OperationCallDataStorePart extends OperationCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def ds_get(id: UniversalId): Option[DataStore.Record] = {
    val datastore = execution_context.runtime.unitOfWork.datastore
    datastore.load(id)
  }

  protected final def ds_put(id: UniversalId, record: DataStore.Record): Unit = {
    val datastore = execution_context.runtime.unitOfWork.datastore
    datastore.store(id, record)
  }

  protected final def ds_delete(id: UniversalId): Unit = {
    val datastore = execution_context.runtime.unitOfWork.datastore
    datastore.delete(id)
  }
}
