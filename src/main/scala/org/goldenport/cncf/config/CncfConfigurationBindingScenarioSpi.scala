package org.goldenport.cncf.config

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingResolver, ConfigurationBindingScenarioReport, ConfigurationOrigin}
import org.goldenport.configuration.ConfigurationBindingScenarioReport.{Executed, Rejected}
import org.goldenport.configuration.ConfigurationBindingScenarioRequest
import org.goldenport.configuration.ConfigurationBindingScenarioSpi

/*
 * @since   Aug.  2, 2026
 * @version Aug.  5, 2026
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
  val fixedUserIdentityChangeScenarioId: String =
    "fixed-user-change-diagnosed-explicit-migration-or-isolation-no-silent-reuse"

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
      case CncfConfigurationBindingScenarioRequest.FixedUserIdentityChange(scenarioid)
          if scenarioid == fixedUserIdentityChangeScenarioId =>
        _fixed_user_identity_change(scenarioid)
      case other => _delegate.evaluate(other)
    }

  private def _fixed_user_identity_change(
    scenarioid: String
  ): ConfigurationBindingScenarioReport =
    (for {
      subsystem <- SubsystemInstanceId.create("platform", "default")
      candidates <- StandaloneUserProfileBindingProjection.candidates(
        _conflicting_fixed_user_profiles,
        subsystem
      )
      context <- CncfConfigurationResolutionContext.forSubsystem(subsystem)
      collection <- ConfigurationBindingResolver.resolve(candidates, context.generic)
      _ <- ResolvedStandaloneUserProfile.resolve(collection)
    } yield candidates) match {
      case Consequence.Success(candidates) => Executed(scenarioid, candidates)
      case Consequence.Failure(conclusion) => Rejected(scenarioid, conclusion)
    }

  private def _conflicting_fixed_user_profiles: Vector[StandaloneUserProfileResolver.Admitted] =
    Vector(
      _profile(
        StandaloneUserProfileResolver.Layer.TextusHome,
        Path.of(".textus", "user-profile.yaml"),
        "fixture-textus-fixed-user"
      ),
      _profile(
        StandaloneUserProfileResolver.Layer.CncfHome,
        Path.of(".cncf", "user-profile.yaml"),
        "fixture-cncf-fixed-user"
      )
    )

  private def _profile(
    layer: StandaloneUserProfileResolver.Layer,
    path: Path,
    id: String
  ): StandaloneUserProfileResolver.Admitted =
    StandaloneUserProfileResolver.Admitted(
      layer,
      path,
      ConfigurationOrigin.Home,
      StandaloneUserProfile.Document(user = Some(StandaloneUserProfile.User(id = Some(id))))
    )
}
