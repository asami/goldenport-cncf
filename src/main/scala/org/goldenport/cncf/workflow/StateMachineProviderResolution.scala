package org.goldenport.cncf.workflow

import org.goldenport.Consequence

/*
 * Explicit Required-SPI-to-Provider selection only.
 *
 * This boundary validates identities and selects a Provider without invoking
 * it. Dispatch, action execution, and continuation handling remain outside
 * this slice.
 *
 * @since   Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
trait StateMachineProviderSource {
  def stateMachineProviderBindings: Vector[StateMachineProviderBinding] =
    Vector.empty

  def stateMachineProviders: Vector[StateMachineProvider] =
    Vector.empty
}

final class StateMachineProviderResolver private (
  private val _provider_identities: Map[
    StateMachineRequiredOperationIdentity,
    ProviderIdentity
  ],
  private val _providers: Map[ProviderIdentity, StateMachineProvider]
) {
  def resolve(
    requiredOperation: StateMachineRequiredOperationIdentity
  ): Consequence[StateMachineProvider] =
    _provider_identities.get(requiredOperation) match {
      case Some(provideridentity) =>
        _providers.get(provideridentity).map(Consequence.success).getOrElse(
          Consequence.operationNotFound(
            s"state-machine provider:${provideridentity.value}"
          )
        )
      case None =>
        Consequence.operationNotFound(
          s"state-machine required provider binding:${requiredOperation.capability}"
        )
    }
}

object StateMachineProviderResolver {
  val empty: StateMachineProviderResolver =
    new StateMachineProviderResolver(Map.empty, Map.empty)

  def create(
    bindings: Vector[StateMachineProviderBinding],
    providers: Vector[StateMachineProvider]
  ): Consequence[StateMachineProviderResolver] =
    _duplicate_required_identity(bindings) match {
      case Some(identity) =>
        Consequence.operationConflict(
          s"state-machine required provider binding:${identity.capability}",
          Vector.empty
        )
      case None =>
        _duplicate_provider_identity(providers) match {
          case Some(identity) =>
            Consequence.operationConflict(
              s"state-machine provider:${identity.value}",
              Vector.empty
            )
          case None =>
            bindings.find(binding => !providers.exists(_.identity == binding.provider)) match {
              case Some(binding) =>
                Consequence.operationNotFound(
                  s"state-machine provider:${binding.provider.value}"
                )
              case None =>
                Consequence.success(
                  new StateMachineProviderResolver(
                    bindings.map(binding => binding.requiredOperation -> binding.provider).toMap,
                    providers.map(provider => provider.identity -> provider).toMap
                  )
                )
            }
        }
    }

  private def _duplicate_required_identity(
    bindings: Vector[StateMachineProviderBinding]
  ): Option[StateMachineRequiredOperationIdentity] =
    bindings
      .groupBy(_.requiredOperation)
      .collectFirst { case (identity, entries) if entries.size > 1 => identity }

  private def _duplicate_provider_identity(
    providers: Vector[StateMachineProvider]
  ): Option[ProviderIdentity] =
    providers
      .groupBy(_.identity)
      .collectFirst { case (identity, entries) if entries.size > 1 => identity }
}
