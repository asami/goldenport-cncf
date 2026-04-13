package org.goldenport.cncf.util

import org.goldenport.Consequence

/*
 * @since   Apr.  9, 2026
 *  version Apr.  9, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Internal polling utilities for tests, executable specs, and demos.
 *
 * CNCF will likely need a richer application-facing asynchronous abstraction
 * for CQRS-oriented eventual consistency, probably centered around jobs or a
 * similar runtime contract. Until that abstraction is clarified, polling-based
 * waiting stays as an internal utility and should not be treated as a stable
 * public API.
 */
object AwaitSupport {
  final case class Policy(
    timeoutMillis: Long = 3000L,
    pollMillis: Long = 10L
  ) {
    require(timeoutMillis >= 0L, s"timeoutMillis must be >= 0: $timeoutMillis")
    require(pollMillis > 0L, s"pollMillis must be > 0: $pollMillis")
  }

  def awaitOption[A](
    message: String,
    policy: Policy = Policy()
  )(
    thunk: => Option[A]
  ): Consequence[A] = {
    val deadline = System.currentTimeMillis() + policy.timeoutMillis
    var result = thunk
    while (result.isEmpty && System.currentTimeMillis() < deadline) {
      Thread.sleep(policy.pollMillis)
      result = thunk
    }
    result match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.operationInvalid(message)
    }
  }

  def awaitCondition(
    message: String,
    policy: Policy = Policy()
  )(
    predicate: => Boolean
  ): Consequence[Unit] =
    awaitOption(message, policy) {
      if (predicate) Some(()) else None
    }
}
