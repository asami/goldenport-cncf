package org.goldenport.cncf.unitofwork

import org.goldenport.id.UniversalId
import org.goldenport.cncf.context.{TransactionContext => DataSourceTransactionContext}

/*
 * @since   Jan.  6, 2026
 * @version Mar. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TransactionContext(
  id: TransactionContext.TransactionContextId,
  tx: DataSourceTransactionContext
) {
  def prepare(): Unit = {}
  def commit(): Unit = {}
  def abort(): Unit = {}
}

object TransactionContext {
  final case class TransactionContextId(
    major: String,
    minor: String
  ) extends UniversalId(major, minor, "transaction_context")

  object TransactionContextId {
    def generate(): TransactionContextId =
      TransactionContextId("cncf", "transaction_context")
  }

  def create(tx: DataSourceTransactionContext): TransactionContext =
    TransactionContext(TransactionContextId.generate(), tx)
}
