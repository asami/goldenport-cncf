package org.goldenport.cncf.unitofwork

import org.goldenport.id.UniversalId

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TransactionContext(
  id: TransactionContext.TransactionContextId
)

object TransactionContext {
  final case class TransactionContextId(
    major: String,
    minor: String
  ) extends UniversalId(major, minor, "transaction_context")

  object TransactionContextId {
    def generate(): TransactionContextId =
      TransactionContextId("cncf", "transaction_context")
  }

  def create(): TransactionContext =
    TransactionContext(TransactionContextId.generate())
}
