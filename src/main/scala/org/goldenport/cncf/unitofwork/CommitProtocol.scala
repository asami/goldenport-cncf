package org.goldenport.cncf.unitofwork

import java.util.UUID

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TransactionContext(
  id: String
)

object TransactionContext {
  def create(): TransactionContext =
    TransactionContext(UUID.randomUUID().toString)
}

sealed trait PrepareResult {
  def isRejected: Boolean
  def reasonOption: Option[String]
}

object PrepareResult {
  case object Prepared extends PrepareResult {
    def isRejected: Boolean = false
    def reasonOption: Option[String] = None
  }

  final case class Rejected(
    reason: String
  ) extends PrepareResult {
    def isRejected: Boolean = true
    def reasonOption: Option[String] = Some(reason)
  }
}

trait CommitParticipant {
  def prepare(tx: TransactionContext): PrepareResult
  def commit(tx: TransactionContext): Unit
  def abort(tx: TransactionContext): Unit
}

trait CommitRecorder {
  def record(message: String): Unit
}

object CommitRecorder {
  val noop: CommitRecorder = new CommitRecorder {
    def record(message: String): Unit = {}
  }
}
