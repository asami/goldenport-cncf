package org.goldenport.cncf.operation.evaluation

import java.nio.charset.StandardCharsets

import org.goldenport.Consequence
import org.goldenport.schema.DataConfidentiality

/*
 * Transaction-aware buffer for application-owned evaluation evidence.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationEvaluationSupplementalPolicy(
  maximumIntentCount: Int = 64,
  maximumIntentBytes: Int = 64 * 1024,
  maximumAggregateBytes: Int = 256 * 1024,
  maximumConfidentiality: DataConfidentiality = DataConfidentiality.Internal
) {
  require(maximumIntentCount > 0, "maximumIntentCount must be positive")
  require(maximumIntentBytes > 0, "maximumIntentBytes must be positive")
  require(maximumAggregateBytes >= maximumIntentBytes, "maximumAggregateBytes must admit one intent")
}

final class OperationEvaluationSupplementalBuffer(
  policy: OperationEvaluationSupplementalPolicy = OperationEvaluationSupplementalPolicy()
) {
  import OperationEvaluationSupplementalBuffer.{AttemptEntry, State}

  private var _attempts: Map[OperationEvaluationAttemptId, AttemptEntry] = Map.empty
  private var _intent_count = 0
  private var _aggregate_bytes = 0

  def stageC(intent: OperationEvaluationSupplementalIntent): Consequence[Unit] =
    synchronized {
      val attemptid = intent.fact.correlation.attemptId
      val entry = _attempts.getOrElse(attemptid, AttemptEntry.open(intent.fact.correlation))
      if (entry.state != State.Open)
        Consequence.stateInvalid(
          s"operation evaluation supplemental buffer attempt is ${entry.state.token}"
        )
      else if (entry.correlation.exists(_ != intent.fact.correlation))
        Consequence.stateConflict(
          "operation evaluation supplemental buffer cannot mix correlations for one attempt"
        )
      else if (_intent_count >= policy.maximumIntentCount)
        Consequence.argumentLimitExceeded(
          "supplementalIntents",
          policy.maximumIntentCount,
          _intent_count + 1,
          "operation-evaluation.supplemental-buffer"
        )
      else if (!_confidentiality_allowed(intent.fact.confidentiality))
        Consequence.securityPermissionDenied(
          s"Operation evaluation supplemental confidentiality exceeds policy: ${intent.fact.confidentiality.label}"
        )
      else {
        val bytes = intent.toRecord.print.getBytes(StandardCharsets.UTF_8).length
        if (bytes > policy.maximumIntentBytes)
          Consequence.argumentLimitExceeded(
            "supplementalIntentBytes",
            policy.maximumIntentBytes,
            bytes,
            "operation-evaluation.supplemental-buffer"
          )
        else if (_aggregate_bytes + bytes > policy.maximumAggregateBytes)
          Consequence.argumentLimitExceeded(
            "supplementalAggregateBytes",
            policy.maximumAggregateBytes,
            _aggregate_bytes + bytes,
            "operation-evaluation.supplemental-buffer"
          )
        else {
          _attempts = _attempts.updated(
            attemptid,
            entry.copy(
              intents = entry.intents :+ intent,
              aggregatebytes = entry.aggregatebytes + bytes
            )
          )
          _intent_count += 1
          _aggregate_bytes += bytes
          Consequence.unit
        }
      }
    }

  def markCommitted(attemptid: OperationEvaluationAttemptId): Unit =
    synchronized {
      val entry = _attempts.getOrElse(attemptid, AttemptEntry.empty(State.Open))
      if (entry.state == State.Open)
        _attempts = _attempts.updated(attemptid, entry.copy(state = State.Committed))
    }

  def releaseCommitted(
    attemptid: OperationEvaluationAttemptId
  ): Vector[OperationEvaluationSupplementalIntent] =
    synchronized {
      _attempts.get(attemptid) match {
        case Some(entry) if entry.state == State.Committed =>
          _clear_attempt(attemptid, entry, State.Released)
          entry.intents
        case _ =>
          Vector.empty
      }
    }

  def discard(attemptid: OperationEvaluationAttemptId): Unit =
    synchronized {
      val entry = _attempts.getOrElse(attemptid, AttemptEntry.empty(State.Open))
      if (entry.state != State.Released)
        _clear_attempt(attemptid, entry, State.Discarded)
    }

  private def _clear_attempt(
    attemptid: OperationEvaluationAttemptId,
    entry: AttemptEntry,
    state: State
  ): Unit = {
    _intent_count = math.max(0, _intent_count - entry.intents.size)
    _aggregate_bytes = math.max(0, _aggregate_bytes - entry.aggregatebytes)
    _attempts = _attempts.updated(attemptid, entry.cleared(state))
  }

  private def _confidentiality_allowed(confidentiality: DataConfidentiality): Boolean =
    OperationEvaluationSupplementalBuffer._confidentiality_rank(confidentiality) <=
      OperationEvaluationSupplementalBuffer._confidentiality_rank(policy.maximumConfidentiality)
}

object OperationEvaluationSupplementalBuffer {
  private final case class AttemptEntry(
    correlation: Option[OperationEvaluationCorrelation],
    state: State,
    intents: Vector[OperationEvaluationSupplementalIntent],
    aggregatebytes: Int
  ) {
    def cleared(nextstate: State): AttemptEntry =
      copy(state = nextstate, intents = Vector.empty, aggregatebytes = 0)
  }

  private object AttemptEntry {
    def open(correlation: OperationEvaluationCorrelation): AttemptEntry =
      AttemptEntry(Some(correlation), State.Open, Vector.empty, 0)

    def empty(state: State): AttemptEntry =
      AttemptEntry(None, state, Vector.empty, 0)
  }

  private enum State(val token: String) {
    case Open extends State("open")
    case Committed extends State("committed")
    case Released extends State("released")
    case Discarded extends State("discarded")
  }

  private def _confidentiality_rank(confidentiality: DataConfidentiality): Int =
    confidentiality match {
      case DataConfidentiality.Public => 0
      case DataConfidentiality.Internal => 1
      case DataConfidentiality.Personal => 2
      case DataConfidentiality.Sensitive => 3
      case DataConfidentiality.Secret => 4
    }
}
