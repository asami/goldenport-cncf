package org.goldenport.cncf.observability

import org.goldenport.Conclusion

/*
 * Common diagnostics for structured validation failures.
 *
 * Compatibility facade for callers that specifically ask whether a Conclusion
 * is validation-related. General metrics/dashboard code should use
 * ConclusionDiagnostics.
 *
 * @since   Apr. 29, 2026
 * @version Apr. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object ValidationDiagnostics {
  type Classification = ConclusionDiagnostics.Classification

  def classify(conclusion: Conclusion): Classification =
    ConclusionDiagnostics.classify(conclusion)

  def isValidation(conclusion: Conclusion): Boolean =
    ConclusionDiagnostics.isValidation(conclusion)
}
