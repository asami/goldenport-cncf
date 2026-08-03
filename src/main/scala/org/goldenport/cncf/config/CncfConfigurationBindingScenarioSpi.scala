package org.goldenport.cncf.config

import org.goldenport.configuration.ConfigurationBindingScenarioReport
import org.goldenport.configuration.ConfigurationBindingScenarioReport.{Executed, Rejected}
import org.goldenport.configuration.ConfigurationBindingScenarioRequest
import org.goldenport.configuration.ConfigurationBindingScenarioSpi

/*
 * @since   Aug.  2, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait CncfConfigurationBindingScenarioRequest
  extends ConfigurationBindingScenarioRequest[CncfConfigurationTarget]

object CncfConfigurationBindingScenarioRequest {
  final case class ConcreteTarget(
    scenarioId: String
  ) extends CncfConfigurationBindingScenarioRequest

  final case class ExternalDocument(
    scenarioId: String,
    input: CncfExternalDocumentScenarioInput
  ) extends CncfConfigurationBindingScenarioRequest

  final case class Alias(
    scenarioId: String,
    batches: Vector[CncfConfigurationDocumentBatch]
  ) extends CncfConfigurationBindingScenarioRequest

  final case class FixedUserIdentityChange(
    scenarioId: String
  ) extends CncfConfigurationBindingScenarioRequest
}

object CncfConfigurationBindingScenarioSpi
  extends ConfigurationBindingScenarioSpi[CncfConfigurationTarget] {
  private val _delegate =
    ConfigurationBindingScenarioSpi.notImplemented[CncfConfigurationTarget]

  override def evaluate(
    request: ConfigurationBindingScenarioRequest[CncfConfigurationTarget]
  ): ConfigurationBindingScenarioReport =
    request match {
      case CncfConfigurationBindingScenarioRequest.ExternalDocument(scenarioid, input) =>
        CncfConfigurationCandidateDecoder.decode(input) match {
          case org.goldenport.Consequence.Success(candidates) => Executed(scenarioid, candidates)
          case org.goldenport.Consequence.Failure(conclusion) => Rejected(scenarioid, conclusion)
        }
      case CncfConfigurationBindingScenarioRequest.Alias(scenarioid, batches) =>
        CncfConfigurationCandidateDecoder.decodeCatalog(batches) match {
          case org.goldenport.Consequence.Success(candidates) => Executed(scenarioid, candidates)
          case org.goldenport.Consequence.Failure(conclusion) => Rejected(scenarioid, conclusion)
        }
      case other => _delegate.evaluate(other)
    }
}
