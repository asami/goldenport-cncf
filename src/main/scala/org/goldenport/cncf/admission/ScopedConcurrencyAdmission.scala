package org.goldenport.cncf.admission

import java.util.concurrent.atomic.AtomicBoolean

import org.goldenport.Consequence
import org.goldenport.cncf.context.ScopeContext

/*
 * Runtime-owned concurrency admission for bounded component/provider work.
 *
 * @since   Jul. 18, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ConcurrencyScopeId private (value: String) {
  def print: String = value
}

object ConcurrencyScopeId {
  private val _pattern = "[a-z][a-z0-9-]{0,63}".r

  def parseC(value: String): Consequence[ConcurrencyScopeId] = {
    val text = Option(value).map(_.trim.toLowerCase).getOrElse("")
    text match {
      case _pattern() => Consequence.success(ConcurrencyScopeId(text))
      case _ => Consequence.argumentFormatError("scope", "lowercase concurrency scope name", value)
    }
  }
}

final case class ConcurrencyGrant(
  scope: ConcurrencyScopeId,
  maxConcurrent: Int
)

/**
 * A runtime-installed, per-scope, nonblocking concurrency gate.
 *
 * A permit is acquired before a component/provider starts work and must be
 * released at the end of that work. `withPermitC` is the preferred lifecycle
 * boundary for synchronous work because it releases the permit on both
 * success and failure.
 */
final class ScopedConcurrencyAdmission private (
  private val _states: Map[ConcurrencyScopeId, ScopedConcurrencyAdmission.State]
) {
  def acquireC(scope: ConcurrencyScopeId): Consequence[ConcurrencyLease] =
    synchronized {
      _states.get(scope).map { state =>
        if (state.inflight < state.maxconcurrent) {
          state.inflight += 1
          Consequence.success(new ConcurrencyLease(this, scope))
        } else {
          Consequence.serviceUnavailable(s"Concurrency admission is saturated: scope=${scope.print}")
        }
      }.getOrElse(
        Consequence.argumentPolicyViolation(
          "scope",
          "concurrency.admission.grant",
          "runtime-installed concurrency grant",
          scope.print
        )
      )
    }

  def withPermitC[A](scope: ConcurrencyScopeId)(body: => Consequence[A]): Consequence[A] =
    acquireC(scope).flatMap { lease =>
      try body
      finally lease.release()
    }

  private[admission] def release(scope: ConcurrencyScopeId): Unit =
    synchronized {
      _states.get(scope).foreach { state =>
        state.inflight = math.max(0, state.inflight - 1)
      }
    }
}

object ScopedConcurrencyAdmission {
  private final class State(
    val maxconcurrent: Int,
    var inflight: Int = 0
  )

  def createC(grants: Vector[ConcurrencyGrant]): Consequence[ScopedConcurrencyAdmission] = {
    val scopes = grants.map(_.scope)
    if (scopes.distinct.size != scopes.size)
      Consequence.argumentPolicyViolation(
        "grants",
        "concurrency.admission.grant",
        "unique concurrency scope grants",
        "duplicate"
      )
    else {
      grants.foldLeft(Consequence.unit) { (z, grant) =>
        z.flatMap { _ =>
          if (grant.maxConcurrent > 0)
            Consequence.unit
          else
            Consequence.argumentPolicyViolation(
              "maxConcurrent",
              "concurrency.admission.limit",
              "positive maximum concurrency",
              grant.maxConcurrent.toString
            )
        }
      }.map { _ =>
        new ScopedConcurrencyAdmission(
          grants.map(x => x.scope -> new State(x.maxConcurrent)).toMap
        )
      }
    }
  }

  def resolveC(scope: ScopeContext): Consequence[ScopedConcurrencyAdmission] =
    scope.scopedConcurrencyAdmissionOption match {
      case Some(admission) => Consequence.success(admission)
      case None => Consequence.serviceUnavailable("Scoped concurrency admission is not configured")
    }

  def withPermitC[A](
    scope: ScopeContext,
    key: ConcurrencyScopeId
  )(body: => Consequence[A]): Consequence[A] =
    resolveC(scope).flatMap(_.withPermitC(key)(body))
}

final class ConcurrencyLease private[admission] (
  admission: ScopedConcurrencyAdmission,
  scope: ConcurrencyScopeId
) extends AutoCloseable {
  private val _released = new AtomicBoolean(false)

  def release(): Unit =
    if (_released.compareAndSet(false, true))
      admission.release(scope)

  override def close(): Unit = release()
}
