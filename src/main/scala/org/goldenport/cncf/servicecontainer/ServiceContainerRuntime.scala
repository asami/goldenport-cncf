package org.goldenport.cncf.servicecontainer

import org.goldenport.{Conclusion, Consequence}

/*
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
sealed abstract class ServiceContainerResolution {
  def endpoint: ServiceContainerEndpoint
}

object ServiceContainerResolution {
  final case class External(
    serviceId: ServiceContainerId,
    endpoint: ServiceContainerEndpoint
  ) extends ServiceContainerResolution

  final case class RuntimeOwned private[servicecontainer] (
    registryKey: ServiceContainerRegistryKey,
    endpoint: ServiceContainerEndpoint,
    reused: Boolean
  ) extends ServiceContainerResolution
}

final case class ServiceContainerCleanupOutcome(
  registryKey: ServiceContainerRegistryKey,
  policy: ServiceContainerCleanupPolicy,
  changed: Boolean,
  status: ServiceContainerStatus
)

abstract class ServiceContainerRuntime {
  def resolveC(
    definition: ServiceContainerDefinition
  ): Consequence[ServiceContainerResolution]

  def stopC(
    key: ServiceContainerRegistryKey
  ): Consequence[ServiceContainerCleanupOutcome]

  def restartC(
    key: ServiceContainerRegistryKey
  ): Consequence[ServiceContainerResolution.RuntimeOwned]

  def removeC(
    key: ServiceContainerRegistryKey
  ): Consequence[ServiceContainerCleanupOutcome]

  def shutdownC(): Consequence[Vector[ServiceContainerCleanupOutcome]]
}

object ServiceContainerRuntime {
  def create(
    registry: ServiceContainerRegistry,
    gateway: ServiceContainerGateway
  ): ServiceContainerRuntime =
    new DefaultServiceContainerRuntime(registry, gateway)
}

final class DefaultServiceContainerRuntime(
  registry: ServiceContainerRegistry,
  gateway: ServiceContainerGateway
) extends ServiceContainerRuntime {
  def resolveC(
    definition: ServiceContainerDefinition
  ): Consequence[ServiceContainerResolution] =
    definition match {
      case x: ServiceContainerDefinition.External =>
        Consequence.success(ServiceContainerResolution.External(x.serviceId, x.endpoint))
      case x: ServiceContainerDefinition.RuntimeOwned =>
        _resolve_owned_c(x)
    }

  def stopC(
    key: ServiceContainerRegistryKey
  ): Consequence[ServiceContainerCleanupOutcome] =
    registry.get(key) match {
      case Some(entry) => _stop_entry_c(entry)
      case None => Consequence.success(ServiceContainerCleanupOutcome(
        key,
        ServiceContainerCleanupPolicy.Stop,
        false,
        ServiceContainerStatus.Absent
      ))
    }

  def restartC(
    key: ServiceContainerRegistryKey
  ): Consequence[ServiceContainerResolution.RuntimeOwned] =
    _entry_c(key).flatMap { entry =>
      gateway.inspectC(key).flatMap {
        case None => ServiceContainerDiagnostics.requiredExistingServiceC(key)
        case Some(inspection) =>
          ServiceContainerCompatibility.requireCompatibleC(entry.definition, inspection).flatMap { compatible =>
            _start_and_await_c(entry, compatible, reused = true, restart = true)
          }
      }
    }

  def removeC(
    key: ServiceContainerRegistryKey
  ): Consequence[ServiceContainerCleanupOutcome] =
    registry.get(key) match {
      case Some(entry) => _remove_entry_c(entry)
      case None => Consequence.success(ServiceContainerCleanupOutcome(
        key,
        ServiceContainerCleanupPolicy.Remove,
        false,
        ServiceContainerStatus.Absent
      ))
    }

  def shutdownC(): Consequence[Vector[ServiceContainerCleanupOutcome]] = {
    val (outcomes, failures) = registry.entries.foldLeft(
      Vector.empty[ServiceContainerCleanupOutcome] -> Vector.empty[Conclusion]
    ) { case ((xs, errors), entry) =>
      _cleanup_entry_c(entry) match {
        case Consequence.Success(outcome) => (xs :+ outcome) -> errors
        case Consequence.Failure(conclusion) => xs -> (errors :+ conclusion)
      }
    }
    failures.reduceLeftOption(_ ++ _) match {
      case Some(conclusion) => Consequence.Failure(conclusion)
      case None => Consequence.success(outcomes)
    }
  }

  private def _resolve_owned_c(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): Consequence[ServiceContainerResolution.RuntimeOwned] =
    registry.registerC(definition).flatMap { entry =>
      gateway.inspectC(definition.registryKey).flatMap {
        case None =>
          definition.reusePolicy match {
            case ServiceContainerReusePolicy.RequireExisting =>
              ServiceContainerDiagnostics.requiredExistingServiceC(definition.registryKey)
            case ServiceContainerReusePolicy.CreateOrReuse =>
              _create_and_start_c(entry)
          }
        case Some(inspection) =>
          ServiceContainerCompatibility.requireCompatibleC(definition, inspection).flatMap { compatible =>
            _reuse_c(entry, compatible)
          }
      }
    }

  private def _create_and_start_c(
    entry: ServiceContainerRegistryEntry
  ): Consequence[ServiceContainerResolution.RuntimeOwned] =
    gateway.createC(entry.definition).flatMap { created =>
      _synchronize_registry_c(entry, ServiceContainerStatus.Created, None).flatMap { registered =>
        _start_and_await_c(registered, created, reused = false, restart = false)
      }
    }

  private def _reuse_c(
    entry: ServiceContainerRegistryEntry,
    inspection: ServiceContainerInspection
  ): Consequence[ServiceContainerResolution.RuntimeOwned] =
    _synchronize_registry_c(entry, inspection.status, inspection.endpoint).flatMap { synchronizedentry =>
      inspection.status match {
        case ServiceContainerStatus.Created | ServiceContainerStatus.Stopped |
            ServiceContainerStatus.Declared | ServiceContainerStatus.Absent =>
          _start_and_await_c(synchronizedentry, inspection, reused = true, restart = false)
        case ServiceContainerStatus.Unhealthy | ServiceContainerStatus.Failed =>
          _start_and_await_c(synchronizedentry, inspection, reused = true, restart = true)
        case ServiceContainerStatus.Starting | ServiceContainerStatus.Ready =>
          _await_ready_c(synchronizedentry, inspection, reused = true)
      }
    }

  private def _start_and_await_c(
    entry: ServiceContainerRegistryEntry,
    inspection: ServiceContainerInspection,
    reused: Boolean,
    restart: Boolean
  ): Consequence[ServiceContainerResolution.RuntimeOwned] = {
    val result =
      if (restart)
        gateway.restartC(inspection.instanceId)
      else
        gateway.startC(inspection.instanceId)
    result match {
      case Consequence.Success(started) =>
        _synchronize_registry_c(entry, ServiceContainerStatus.Starting, None).flatMap { starting =>
          _await_ready_c(starting, started, reused)
        }
      case Consequence.Failure(primary) =>
        _synchronize_registry_c(entry, ServiceContainerStatus.Failed, None) match {
          case Consequence.Success(_) => Consequence.Failure(primary)
          case Consequence.Failure(secondary) => Consequence.Failure(secondary ++ primary)
        }
    }
  }

  private def _await_ready_c(
    entry: ServiceContainerRegistryEntry,
    inspection: ServiceContainerInspection,
    reused: Boolean
  ): Consequence[ServiceContainerResolution.RuntimeOwned] =
    gateway.checkReadinessC(inspection.instanceId, entry.definition.readiness) match {
      case Consequence.Success(ready) =>
        _synchronize_registry_c(entry, ServiceContainerStatus.Ready, Some(ready.endpoint)).map { completed =>
          ServiceContainerResolution.RuntimeOwned(
            completed.key,
            ready.endpoint,
            reused
          )
        }
      case Consequence.Failure(primary) =>
        _synchronize_registry_c(entry, ServiceContainerStatus.Unhealthy, None) match {
          case Consequence.Success(_) => Consequence.Failure(primary)
          case Consequence.Failure(secondary) => Consequence.Failure(secondary ++ primary)
        }
    }

  private def _stop_entry_c(
    entry: ServiceContainerRegistryEntry
  ): Consequence[ServiceContainerCleanupOutcome] =
    gateway.inspectC(entry.key).flatMap {
      case None =>
        _synchronize_registry_c(entry, ServiceContainerStatus.Absent, None).map { _ =>
          ServiceContainerCleanupOutcome(entry.key, ServiceContainerCleanupPolicy.Stop, false, ServiceContainerStatus.Absent)
        }
      case Some(inspection) =>
        ServiceContainerCompatibility.requireCompatibleC(entry.definition, inspection).flatMap { compatible =>
          if (compatible.status == ServiceContainerStatus.Stopped)
            _synchronize_registry_c(entry, ServiceContainerStatus.Stopped, None).map { _ =>
              ServiceContainerCleanupOutcome(entry.key, ServiceContainerCleanupPolicy.Stop, false, ServiceContainerStatus.Stopped)
            }
          else
            gateway.stopC(compatible.instanceId).flatMap { _ =>
              _synchronize_registry_c(entry, ServiceContainerStatus.Stopped, None).map { _ =>
                ServiceContainerCleanupOutcome(entry.key, ServiceContainerCleanupPolicy.Stop, true, ServiceContainerStatus.Stopped)
              }
            }
        }
    }

  private def _remove_entry_c(
    entry: ServiceContainerRegistryEntry
  ): Consequence[ServiceContainerCleanupOutcome] =
    gateway.inspectC(entry.key).flatMap {
      case None =>
        registry.removeC(entry.key, entry.revision).map { _ =>
          ServiceContainerCleanupOutcome(entry.key, ServiceContainerCleanupPolicy.Remove, false, ServiceContainerStatus.Absent)
        }
      case Some(inspection) =>
        ServiceContainerCompatibility.requireCompatibleC(entry.definition, inspection).flatMap { compatible =>
          gateway.removeC(compatible.instanceId).flatMap { _ =>
            registry.removeC(entry.key, entry.revision).map { _ =>
              ServiceContainerCleanupOutcome(entry.key, ServiceContainerCleanupPolicy.Remove, true, ServiceContainerStatus.Absent)
            }
          }
        }
    }

  private def _cleanup_entry_c(
    entry: ServiceContainerRegistryEntry
  ): Consequence[ServiceContainerCleanupOutcome] =
    entry.definition.cleanupPolicy match {
      case ServiceContainerCleanupPolicy.Keep =>
        Consequence.success(ServiceContainerCleanupOutcome(
          entry.key,
          ServiceContainerCleanupPolicy.Keep,
          false,
          entry.status
        ))
      case ServiceContainerCleanupPolicy.Stop => _stop_entry_c(entry)
      case ServiceContainerCleanupPolicy.Remove => _remove_entry_c(entry)
    }

  private def _synchronize_registry_c(
    entry: ServiceContainerRegistryEntry,
    status: ServiceContainerStatus,
    endpoint: Option[ServiceContainerEndpoint]
  ): Consequence[ServiceContainerRegistryEntry] =
    if (entry.status == status && entry.endpoint == endpoint)
      Consequence.success(entry)
    else
      registry.updateC(entry.key, entry.revision, status, endpoint)

  private def _entry_c(
    key: ServiceContainerRegistryKey
  ): Consequence[ServiceContainerRegistryEntry] =
    registry.get(key) match {
      case Some(entry) => Consequence.success(entry)
      case None => Consequence.resourceNotFound(s"Managed service is not registered: ${key.print}.")
    }
}
