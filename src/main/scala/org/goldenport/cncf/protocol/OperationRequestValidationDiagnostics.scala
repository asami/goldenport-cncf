package org.goldenport.cncf.protocol

import org.goldenport.Conclusion
import org.goldenport.cncf.observability.ValidationDiagnostics

/*
 * Common diagnostics for OperationDefinition request parsing failures.
 *
 * This is intentionally component-neutral. Component-specific metrics may use
 * the same structured cause/facet data to derive narrower labels.
 *
 * @since   Apr. 29, 2026
 * @version Apr. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object OperationRequestValidationDiagnostics {
  type Classification = ValidationDiagnostics.Classification

  def classify(conclusion: Conclusion): Classification =
    ValidationDiagnostics.classify(conclusion)
}
