package org.goldenport.cncf.servicecontainer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import scala.collection.mutable

import org.goldenport.Consequence

/*
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ServiceContainerInstanceId private (value: String) {
  def print: String = value
}

object ServiceContainerInstanceId {
  private val _pattern = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}".r

  def parseC(value: String): Consequence[ServiceContainerInstanceId] = {
    val text = Option(value).map(_.trim).getOrElse("")
    text match {
      case _pattern() => Consequence.success(ServiceContainerInstanceId(text))
      case _ => Consequence.argumentFormatError("instanceId", "opaque safe provider container identity", "invalid")
    }
  }
}

final case class ServiceContainerOwnershipLabels private (
  values: Map[String, String]
) {
  def matches(key: ServiceContainerRegistryKey): Boolean =
    values.get(ServiceContainerOwnershipLabels.MANAGED) == Some("true") &&
      values.get(ServiceContainerOwnershipLabels.OWNER_KIND) == Some(key.owner.kind.name) &&
      values.get(ServiceContainerOwnershipLabels.OWNER_ID) == Some(key.owner.id.print) &&
      values.get(ServiceContainerOwnershipLabels.SERVICE_ID) == Some(key.serviceId.print)

  def matches(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): Boolean =
    matches(definition.registryKey) &&
      values.get(ServiceContainerOwnershipLabels.CONTRACT_DIGEST) ==
        Some(ServiceContainerOwnershipLabels.contractDigest(definition))
}

object ServiceContainerOwnershipLabels {
  val MANAGED = "org.goldenport.cncf.managed"
  val OWNER_KIND = "org.goldenport.cncf.owner-kind"
  val OWNER_ID = "org.goldenport.cncf.owner-id"
  val SERVICE_ID = "org.goldenport.cncf.service-id"
  val CONTRACT_DIGEST = "org.goldenport.cncf.contract-digest"
  private val _keys = Set(MANAGED, OWNER_KIND, OWNER_ID, SERVICE_ID, CONTRACT_DIGEST)

  def from(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): ServiceContainerOwnershipLabels = {
    val key = definition.registryKey
    ServiceContainerOwnershipLabels(Map(
      MANAGED -> "true",
      OWNER_KIND -> key.owner.kind.name,
      OWNER_ID -> key.owner.id.print,
      SERVICE_ID -> key.serviceId.print,
      CONTRACT_DIGEST -> contractDigest(definition)
    ))
  }

  private[servicecontainer] def observed(values: Map[String, String]): ServiceContainerOwnershipLabels =
    ServiceContainerOwnershipLabels(values.filter { case (key, _) => _keys.contains(key) })

  def contractDigest(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): String = {
    val text = Vector(
      definition.registryKey.print,
      definition.image.print,
      definition.ports.sortBy(_.name.print).map(x => s"${x.name.print}:${x.containerPort}").mkString(","),
      _readiness_text(definition.readiness),
      _persistence_text(definition.persistence),
      definition.reusePolicy.toString,
      definition.cleanupPolicy.toString
    ).mkString("\n")
    val bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
    bytes.map(x => f"${x & 0xff}%02x").mkString
  }

  private def _readiness_text(policy: ServiceContainerReadinessPolicy): String = {
    val probe = policy.probe match {
      case x: ServiceContainerReadinessProbe.Http =>
        s"http:${x.portName.print}:${x.path}:${x.expectedStatuses.toVector.sorted.mkString(",")}"
      case x: ServiceContainerReadinessProbe.Tcp =>
        s"tcp:${x.portName.print}"
    }
    s"${probe}:${policy.timeoutMillis}:${policy.intervalMillis}"
  }

  private def _persistence_text(persistence: ServiceContainerPersistence): String =
    persistence match {
      case ServiceContainerPersistence.Ephemeral => "ephemeral"
      case x: ServiceContainerPersistence.NamedVolumes =>
        s"named:${x.volumes.map(_.print).sorted.mkString(",")}"
    }
}

final case class ServiceContainerInspection private[servicecontainer] (
  instanceId: ServiceContainerInstanceId,
  registryKey: ServiceContainerRegistryKey,
  image: ServiceContainerImage,
  ports: Vector[ServiceContainerPort],
  ownershipLabels: ServiceContainerOwnershipLabels,
  status: ServiceContainerStatus,
  endpoint: Option[ServiceContainerEndpoint]
)

enum ServiceContainerCompatibility {
  case Compatible
  case OwnershipConflict
  case IncompatibleDefinition
}

object ServiceContainerCompatibility {
  def evaluate(
    definition: ServiceContainerDefinition.RuntimeOwned,
    inspection: ServiceContainerInspection
  ): ServiceContainerCompatibility =
    if (!inspection.ownershipLabels.matches(definition.registryKey))
      OwnershipConflict
    else if (
      !inspection.ownershipLabels.matches(definition) ||
      inspection.registryKey != definition.registryKey ||
      inspection.image != definition.image ||
      inspection.ports.sortBy(_.name.print) != definition.ports.sortBy(_.name.print)
    )
      IncompatibleDefinition
    else
      Compatible

  def requireCompatibleC(
    definition: ServiceContainerDefinition.RuntimeOwned,
    inspection: ServiceContainerInspection
  ): Consequence[ServiceContainerInspection] =
    evaluate(definition, inspection) match {
      case Compatible => Consequence.success(inspection)
      case OwnershipConflict => ServiceContainerDiagnostics.ownershipConflictC(definition.registryKey)
      case IncompatibleDefinition =>
        ServiceContainerDiagnostics.incompatibleExistingServiceC(definition.registryKey)
    }
}

final case class ServiceContainerReadinessResult(
  endpoint: ServiceContainerEndpoint
)

abstract class ServiceContainerGateway {
  def inspectC(
    key: ServiceContainerRegistryKey
  ): Consequence[Option[ServiceContainerInspection]]

  def createC(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): Consequence[ServiceContainerInspection]

  def startC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection]

  def checkReadinessC(
    instanceid: ServiceContainerInstanceId,
    policy: ServiceContainerReadinessPolicy
  ): Consequence[ServiceContainerReadinessResult]

  def stopC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection]

  def restartC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection]

  def removeC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection]
}

object FakeServiceContainerGateway {
  def create(
    readinessendpoints: Map[ServiceContainerRegistryKey, ServiceContainerEndpoint] = Map.empty,
    initialinspections: Vector[ServiceContainerInspection] = Vector.empty,
    failuretransitions: Set[(ServiceContainerTransition, ServiceContainerRegistryKey)] = Set.empty
  ): FakeServiceContainerGateway =
    new FakeServiceContainerGateway(readinessendpoints, initialinspections, failuretransitions)
}

final class FakeServiceContainerGateway private (
  readinessendpoints: Map[ServiceContainerRegistryKey, ServiceContainerEndpoint],
  initialinspections: Vector[ServiceContainerInspection],
  failuretransitions: Set[(ServiceContainerTransition, ServiceContainerRegistryKey)]
) extends ServiceContainerGateway {
  private val _containers = mutable.LinkedHashMap.from(
    initialinspections.sortBy(_.registryKey.print).map(x => x.instanceId -> x)
  )
  private val _transitions = mutable.ArrayBuffer.empty[(ServiceContainerTransition, ServiceContainerRegistryKey)]

  def transitions: Vector[(ServiceContainerTransition, ServiceContainerRegistryKey)] = synchronized {
    _transitions.toVector
  }

  def inspections: Vector[ServiceContainerInspection] = synchronized {
    _containers.values.toVector.sortBy(_.registryKey.print)
  }

  def inspectC(
    key: ServiceContainerRegistryKey
  ): Consequence[Option[ServiceContainerInspection]] = synchronized {
    _record(ServiceContainerTransition.Inspect, key)
    _failure_c[Option[ServiceContainerInspection]](ServiceContainerTransition.Inspect, key)
      .getOrElse(Consequence.success(_containers.values.find(_.registryKey == key)))
  }

  def createC(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): Consequence[ServiceContainerInspection] = synchronized {
    val key = definition.registryKey
    _record(ServiceContainerTransition.Create, key)
    _failure_c[ServiceContainerInspection](ServiceContainerTransition.Create, key).getOrElse {
      _containers.values.find(_.registryKey == key) match {
        case Some(_) => ServiceContainerDiagnostics.incompatibleExistingServiceC(key)
        case None =>
          val instanceid = _instance_id(definition)
          val inspection = ServiceContainerInspection(
            instanceid,
            key,
            definition.image,
            definition.ports,
            ServiceContainerOwnershipLabels.from(definition),
            ServiceContainerStatus.Created,
            None
          )
          _containers.put(instanceid, inspection)
          Consequence.success(inspection)
      }
    }
  }

  def startC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection] = synchronized {
    _transition_update_c(instanceid, ServiceContainerTransition.Start)(_.copy(
      status = ServiceContainerStatus.Starting,
      endpoint = None
    ))
  }

  def checkReadinessC(
    instanceid: ServiceContainerInstanceId,
    policy: ServiceContainerReadinessPolicy
  ): Consequence[ServiceContainerReadinessResult] = synchronized {
    _containers.get(instanceid) match {
      case None => _not_found_c(instanceid)
      case Some(inspection) =>
        _record(ServiceContainerTransition.CheckReadiness, inspection.registryKey)
        _failure_c[ServiceContainerReadinessResult](
          ServiceContainerTransition.CheckReadiness,
          inspection.registryKey
        ).getOrElse {
          readinessendpoints.get(inspection.registryKey) match {
            case None => ServiceContainerDiagnostics.unhealthyC(inspection.registryKey.serviceId)
            case Some(endpoint) =>
              _containers.put(instanceid, inspection.copy(
                status = ServiceContainerStatus.Ready,
                endpoint = Some(endpoint)
              ))
              Consequence.success(ServiceContainerReadinessResult(endpoint))
          }
        }
    }
  }

  def stopC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection] = synchronized {
    _transition_update_c(instanceid, ServiceContainerTransition.Stop)(_.copy(
      status = ServiceContainerStatus.Stopped,
      endpoint = None
    ))
  }

  def restartC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection] = synchronized {
    _transition_update_c(instanceid, ServiceContainerTransition.Restart)(_.copy(
      status = ServiceContainerStatus.Starting,
      endpoint = None
    ))
  }

  def removeC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection] = synchronized {
    _containers.get(instanceid) match {
      case None => _not_found_c(instanceid)
      case Some(inspection) =>
        _record(ServiceContainerTransition.Remove, inspection.registryKey)
        _failure_c[ServiceContainerInspection](ServiceContainerTransition.Remove, inspection.registryKey)
          .getOrElse {
            _containers.remove(instanceid)
            Consequence.success(inspection)
          }
    }
  }

  private def _transition_update_c(
    instanceid: ServiceContainerInstanceId,
    transition: ServiceContainerTransition
  )(f: ServiceContainerInspection => ServiceContainerInspection): Consequence[ServiceContainerInspection] =
    _containers.get(instanceid) match {
      case None => _not_found_c(instanceid)
      case Some(inspection) =>
        _record(transition, inspection.registryKey)
        _failure_c[ServiceContainerInspection](transition, inspection.registryKey).getOrElse {
          val result = f(inspection)
          _containers.put(instanceid, result)
          Consequence.success(result)
        }
    }

  private def _record(
    transition: ServiceContainerTransition,
    key: ServiceContainerRegistryKey
  ): Unit =
    _transitions += transition -> key

  private def _instance_id(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): ServiceContainerInstanceId =
    ServiceContainerInstanceId.parseC(
      s"fake-${ServiceContainerOwnershipLabels.contractDigest(definition).take(24)}"
    ).toOption.get

  private def _failure_c[A](
    transition: ServiceContainerTransition,
    key: ServiceContainerRegistryKey
  ): Option[Consequence.Failure[A]] =
    Option.when(failuretransitions.contains(transition -> key)) {
      transition match {
        case ServiceContainerTransition.Create =>
          ServiceContainerDiagnostics.imageUnavailableC(key.serviceId)
        case ServiceContainerTransition.Start | ServiceContainerTransition.Restart =>
          ServiceContainerDiagnostics.startupFailureC(key.serviceId)
        case ServiceContainerTransition.CheckReadiness =>
          ServiceContainerDiagnostics.readinessTimeoutC(key.serviceId)
        case _ =>
          ServiceContainerDiagnostics.gatewayUnavailableC(key.serviceId)
      }
    }

  private def _not_found_c[A](
    instanceid: ServiceContainerInstanceId
  ): Consequence[A] =
    Consequence.resourceNotFound(s"Managed service container is not found: ${instanceid.print}.")
}
