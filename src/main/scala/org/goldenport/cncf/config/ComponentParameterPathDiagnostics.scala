package org.goldenport.cncf.config

import org.goldenport.{Consequence}
import org.goldenport.observation.{Cause, Descriptor}

/*
 * Payload-safe failures for dynamic initialization parameter-path schema
 * registration.
 *
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentParameterPathDiagnostics {
  def rejected[A](
    message: String,
    path: String
  ): Consequence.Failure[A] =
    _failure(message, Cause.Kind.Policy, "rejected", path)

  def ambiguous[A](
    message: String,
    path: String
  ): Consequence.Failure[A] =
    _failure(message, Cause.Kind.Conflict, "ambiguous", path)

  private def _failure[A](
    message: String,
    kind: Cause.Kind,
    reason: String,
    path: String
  ): Consequence.Failure[A] =
    Consequence.configurationInvalid(
      message,
      kind,
      Vector(
        Descriptor.Facet.Policy(ComponentParameterDiagnostics.POLICY),
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Parameter.argument(path)
      )
    )
}
