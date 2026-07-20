package org.goldenport.cncf.servicecontainer

import scala.collection.concurrent.TrieMap

import org.goldenport.Consequence

/*
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ServiceContainerRegistryEntry private[servicecontainer] (
  definition: ServiceContainerDefinition.RuntimeOwned,
  status: ServiceContainerStatus,
  endpoint: Option[ServiceContainerEndpoint],
  revision: Long
) {
  def key: ServiceContainerRegistryKey = definition.registryKey
}

abstract class ServiceContainerRegistry {
  def get(key: ServiceContainerRegistryKey): Option[ServiceContainerRegistryEntry]

  def entries: Vector[ServiceContainerRegistryEntry]

  def registerC(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): Consequence[ServiceContainerRegistryEntry]

  def updateC(
    key: ServiceContainerRegistryKey,
    expectedrevision: Long,
    status: ServiceContainerStatus,
    endpoint: Option[ServiceContainerEndpoint]
  ): Consequence[ServiceContainerRegistryEntry]

  def removeC(
    key: ServiceContainerRegistryKey,
    expectedrevision: Long
  ): Consequence[ServiceContainerRegistryEntry]
}

object ServiceContainerRegistry {
  def inMemory(): ServiceContainerRegistry =
    new InMemoryServiceContainerRegistry
}

final class InMemoryServiceContainerRegistry extends ServiceContainerRegistry {
  private val _entries = TrieMap.empty[ServiceContainerRegistryKey, ServiceContainerRegistryEntry]

  def get(key: ServiceContainerRegistryKey): Option[ServiceContainerRegistryEntry] =
    _entries.get(key)

  def entries: Vector[ServiceContainerRegistryEntry] = synchronized {
    _entries.values.toVector.sortBy(_.key.print)
  }

  def registerC(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): Consequence[ServiceContainerRegistryEntry] = synchronized {
    val key = definition.registryKey
    _entries.get(key) match {
      case Some(entry) if entry.definition == definition =>
        Consequence.success(entry)
      case Some(_) =>
        ServiceContainerDiagnostics.incompatibleExistingServiceC(key)
      case None =>
        val entry = ServiceContainerRegistryEntry(
          definition,
          ServiceContainerStatus.Declared,
          None,
          0L
        )
        _entries.put(key, entry)
        Consequence.success(entry)
    }
  }

  def updateC(
    key: ServiceContainerRegistryKey,
    expectedrevision: Long,
    status: ServiceContainerStatus,
    endpoint: Option[ServiceContainerEndpoint]
  ): Consequence[ServiceContainerRegistryEntry] = synchronized {
    _entries.get(key) match {
      case None =>
        Consequence.resourceNotFound(s"Managed service is not registered: ${key.print}.")
      case Some(entry) if entry.revision != expectedrevision =>
        ServiceContainerDiagnostics.revisionConflictC(key)
      case Some(_) if status == ServiceContainerStatus.Ready && endpoint.isEmpty =>
        Consequence.argumentPolicyViolation(
          "endpoint",
          "service-container.ready-state",
          "ready service has a credential-free endpoint",
          "missing"
        )
      case Some(entry) =>
        val next = entry.copy(
          status = status,
          endpoint = endpoint,
          revision = entry.revision + 1L
        )
        _entries.put(key, next)
        Consequence.success(next)
    }
  }

  def removeC(
    key: ServiceContainerRegistryKey,
    expectedrevision: Long
  ): Consequence[ServiceContainerRegistryEntry] = synchronized {
    _entries.get(key) match {
      case None =>
        Consequence.resourceNotFound(s"Managed service is not registered: ${key.print}.")
      case Some(entry) if entry.revision != expectedrevision =>
        ServiceContainerDiagnostics.revisionConflictC(key)
      case Some(entry) =>
        _entries.remove(key)
        Consequence.success(entry)
    }
  }
}
