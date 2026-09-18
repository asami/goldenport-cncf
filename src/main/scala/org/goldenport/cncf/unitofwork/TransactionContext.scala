package org.goldenport.cncf.unitofwork

import java.time.{Clock, Instant}
import org.goldenport.id.UniversalId
import org.goldenport.cncf.context.{IdGenerationContext, TransactionContext => DataSourceTransactionContext}

/*
 * @since   Jan.  6, 2026
 *  version Mar. 11, 2026
 * @version Sep. 18, 2026
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
    minor: String,
    timestamp: Option[Instant] = None,
    entropy: Option[String] = None
  ) extends UniversalId(major, minor, "transaction_context", timestamp, entropy)

  object TransactionContextId {
    def generate(): TransactionContextId =
      generate(
        Clock.systemUTC(),
        IdGenerationContext.default(IdGenerationContext.DEFAULT_NAMESPACE)
      )

    def generate(
      clock: Clock,
      idGeneration: IdGenerationContext
    ): TransactionContextId =
      TransactionContextId(
        major = idGeneration.namespace.major,
        minor = idGeneration.namespace.minor,
        timestamp = Some(Instant.ofEpochMilli(clock.instant().toEpochMilli)),
        entropy = Some(idGeneration.opaqueId("transaction-context"))
      )
  }

  def create(tx: DataSourceTransactionContext): TransactionContext =
    TransactionContext(TransactionContextId.generate(), tx)

  def create(
    tx: DataSourceTransactionContext,
    clock: Clock,
    idGeneration: IdGenerationContext
  ): TransactionContext =
    TransactionContext(TransactionContextId.generate(clock, idGeneration), tx)
}
