package org.goldenport.cncf.servicecontainer

import java.net.URI
import scala.util.Try

import org.goldenport.Consequence
import org.goldenport.observation.{Cause, Descriptor}

/*
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ServiceContainerOwnerId private (value: String) {
  def print: String = value
}

object ServiceContainerOwnerId {
  private val _pattern = "[a-z][a-z0-9.-]{0,127}".r

  def parseC(value: String): Consequence[ServiceContainerOwnerId] = {
    val text = Option(value).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
    text match {
      case _pattern() => Consequence.success(ServiceContainerOwnerId(text))
      case _ => Consequence.argumentFormatError("owner", "lowercase logical service owner", value)
    }
  }
}

enum ServiceContainerOwnerKind(val name: String) {
  case ComponentRuntime extends ServiceContainerOwnerKind("component-runtime")
  case SubsystemRuntime extends ServiceContainerOwnerKind("subsystem-runtime")
}

final case class ServiceContainerOwner(
  kind: ServiceContainerOwnerKind,
  id: ServiceContainerOwnerId
) {
  def print: String = s"${kind.name}:${id.print}"
}

final case class ServiceContainerId private (value: String) {
  def print: String = value
}

object ServiceContainerId {
  private val _pattern = "[a-z][a-z0-9-]{0,63}".r

  def parseC(value: String): Consequence[ServiceContainerId] = {
    val text = Option(value).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
    text match {
      case _pattern() => Consequence.success(ServiceContainerId(text))
      case _ => Consequence.argumentFormatError("service", "lowercase logical service name", value)
    }
  }
}

final case class ServiceContainerRegistryKey(
  owner: ServiceContainerOwner,
  serviceId: ServiceContainerId
) {
  def print: String = s"${owner.print}/${serviceId.print}"
}

final case class ServiceContainerEndpoint private (uri: URI) {
  def print: String = uri.toASCIIString

  def safeAuthority: String = {
    val port = Option.when(uri.getPort >= 0)(s":${uri.getPort}").getOrElse("")
    s"${uri.getScheme}://${uri.getHost}${port}"
  }
}

object ServiceContainerEndpoint {
  private val _schemes = Set("http", "https")

  def parseC(value: String): Consequence[ServiceContainerEndpoint] = {
    val text = Option(value).map(_.trim).getOrElse("")
    val candidate = Try(new URI(text).normalize()).toOption
    candidate match {
      case Some(uri) if
          uri.isAbsolute &&
          _schemes.contains(Option(uri.getScheme).map(_.toLowerCase(java.util.Locale.ROOT)).getOrElse("")) &&
          Option(uri.getHost).exists(_.nonEmpty) &&
          uri.getUserInfo == null &&
          uri.getQuery == null &&
          uri.getFragment == null =>
        Consequence.success(ServiceContainerEndpoint(uri))
      case _ =>
        Consequence.argumentPolicyViolation(
          "endpoint",
          "service-container.endpoint-safety",
          "credential-free absolute HTTP(S) endpoint without query or fragment",
          "invalid"
        )
    }
  }
}

final case class ServiceContainerImage private (value: String) {
  def print: String = value
}

object ServiceContainerImage {
  private val _repository_segment_pattern = "[a-z0-9]+(?:[._-]+[a-z0-9]+)*".r
  private val _registry_pattern = "[a-z0-9]+(?:[.-][a-z0-9]+)*(?::[0-9]{1,5})?".r
  private val _tag_pattern = "[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}".r
  private val _digest_pattern = "sha256:[a-fA-F0-9]{64}".r

  def parseC(value: String): Consequence[ServiceContainerImage] = {
    val text = Option(value).map(_.trim).getOrElse("")
    if (_is_valid_reference(text))
      Consequence.success(ServiceContainerImage(text))
    else
      Consequence.argumentFormatError("image", "safe OCI-like container image identity", "invalid")
  }

  private def _is_valid_reference(value: String): Boolean = {
    val digestparts = value.split("@", -1).toVector
    val digestvalid = digestparts match {
      case Vector(_) => true
      case Vector(_, _digest_pattern()) => true
      case _ => false
    }
    if (
      value.isEmpty ||
      value.length > 512 ||
      value.exists(x => x.isControl || x.isWhitespace) ||
      value.startsWith("/") ||
      value.endsWith("/") ||
      value.contains("//") ||
      !digestvalid
    )
      false
    else {
      val base = digestparts.head
      val slashindex = base.lastIndexOf('/')
      val colonindex = base.lastIndexOf(':')
      val hastag = colonindex > slashindex
      val name = if (hastag) base.substring(0, colonindex) else base
      val tag = Option.when(hastag)(base.substring(colonindex + 1))
      val segments = name.split("/", -1).toVector
      val registryvalid = segments.headOption.exists { segment =>
        if (segments.size > 1 && (segment.contains('.') || segment.contains(':') || segment == "localhost"))
          _registry_segment_valid(segment)
        else
          _repository_segment_pattern.matches(segment)
      }
      val repositoryvalid = segments.drop(1).forall(_repository_segment_pattern.matches)
      registryvalid && repositoryvalid && tag.forall(_tag_pattern.matches)
    }
  }

  private def _registry_segment_valid(value: String): Boolean =
    _registry_pattern.matches(value) &&
      value.split(":", -1).toVector.drop(1).headOption.forall { port =>
        Try(port.toInt).toOption.exists(x => x >= 1 && x <= 65535)
      }
}

final case class ServiceContainerPortName private (value: String) {
  def print: String = value
}

object ServiceContainerPortName {
  private val _pattern = "[a-z][a-z0-9-]{0,31}".r

  def parseC(value: String): Consequence[ServiceContainerPortName] = {
    val text = Option(value).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
    text match {
      case _pattern() => Consequence.success(ServiceContainerPortName(text))
      case _ => Consequence.argumentFormatError("portName", "lowercase logical port name", value)
    }
  }
}

final case class ServiceContainerPort private (
  name: ServiceContainerPortName,
  containerPort: Int
)

object ServiceContainerPort {
  def createC(
    name: ServiceContainerPortName,
    containerport: Int
  ): Consequence[ServiceContainerPort] =
    if (containerport < 1 || containerport > 65535)
      Consequence.argumentLimitExceeded("containerPort", 65535, containerport, "service-container.port")
    else
      Consequence.success(ServiceContainerPort(name, containerport))
}

enum ServiceContainerReusePolicy {
  case CreateOrReuse
  case RequireExisting
}

enum ServiceContainerCleanupPolicy {
  case Keep
  case Stop
  case Remove
}

final case class ServiceContainerVolumeName private (value: String) {
  def print: String = value
}

object ServiceContainerVolumeName {
  private val _pattern = "[a-z0-9][a-z0-9._-]{0,127}".r

  def parseC(value: String): Consequence[ServiceContainerVolumeName] = {
    val text = Option(value).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
    text match {
      case _pattern() => Consequence.success(ServiceContainerVolumeName(text))
      case _ => Consequence.argumentFormatError("volume", "safe named-volume identity", "invalid")
    }
  }
}

final case class ServiceContainerMountPath private (value: String) {
  def print: String = value
}

object ServiceContainerMountPath {
  private val _segment_pattern = "[A-Za-z0-9._-]+".r

  def parseC(value: String): Consequence[ServiceContainerMountPath] = {
    val text = Option(value).map(_.trim).getOrElse("")
    val segments = text.split("/", -1).toVector.drop(1)
    if (
      !text.startsWith("/") ||
      text.length < 2 ||
      text.length > 512 ||
      text.exists(x => x.isControl || x.isWhitespace) ||
      segments.exists(x => x == "." || x == ".." || !_segment_pattern.matches(x))
    )
      Consequence.argumentFormatError(
        "mountPath",
        "safe absolute container path without traversal",
        "invalid"
      )
    else
      Consequence.success(ServiceContainerMountPath(text))
  }
}

final case class ServiceContainerVolume private (
  name: ServiceContainerVolumeName,
  target: ServiceContainerMountPath
)

object ServiceContainerVolume {
  def createC(
    name: ServiceContainerVolumeName,
    target: ServiceContainerMountPath
  ): Consequence[ServiceContainerVolume] =
    Consequence.success(ServiceContainerVolume(name, target))
}

sealed abstract class ServiceContainerPersistence

object ServiceContainerPersistence {
  case object Ephemeral extends ServiceContainerPersistence

  final case class NamedVolumes private[servicecontainer] (
    volumes: Vector[ServiceContainerVolume]
  ) extends ServiceContainerPersistence

  def namedVolumesC(
    volumes: Vector[ServiceContainerVolume]
  ): Consequence[ServiceContainerPersistence] =
    if (volumes.isEmpty)
      Consequence.argumentMissing("volumes")
    else if (
      volumes.map(_.name).distinct.size != volumes.size ||
      volumes.map(_.target).distinct.size != volumes.size
    )
      Consequence.argumentPolicyViolation(
        "volumes",
        "service-container.persistence",
        "unique named volumes",
        "duplicate"
      )
    else
      Consequence.success(new NamedVolumes(volumes))
}

sealed abstract class ServiceContainerReadinessProbe {
  def portName: ServiceContainerPortName
}

object ServiceContainerReadinessProbe {
  final case class Http private[servicecontainer] (
    portName: ServiceContainerPortName,
    path: String,
    expectedStatuses: Set[Int]
  ) extends ServiceContainerReadinessProbe

  final case class Tcp private[servicecontainer] (
    portName: ServiceContainerPortName
  ) extends ServiceContainerReadinessProbe

  def httpC(
    portname: ServiceContainerPortName,
    path: String,
    expectedstatuses: Set[Int] = Set(200)
  ): Consequence[ServiceContainerReadinessProbe] = {
    val normalized = Option(path).map(_.trim).getOrElse("")
    if (!normalized.startsWith("/") || normalized.exists(x => x.isControl || x.isWhitespace))
      Consequence.argumentFormatError("readinessPath", "absolute HTTP path", "invalid")
    else if (expectedstatuses.isEmpty || expectedstatuses.exists(x => x < 100 || x > 599))
      Consequence.argumentPolicyViolation(
        "expectedStatuses",
        "service-container.readiness",
        "non-empty HTTP status set in range 100..599",
        "invalid"
      )
    else
      Consequence.success(new Http(portname, normalized, expectedstatuses))
  }

  def tcp(portname: ServiceContainerPortName): ServiceContainerReadinessProbe =
    new Tcp(portname)
}

final case class ServiceContainerReadinessPolicy private (
  probe: ServiceContainerReadinessProbe,
  timeoutMillis: Long,
  intervalMillis: Long
)

object ServiceContainerReadinessPolicy {
  def createC(
    probe: ServiceContainerReadinessProbe,
    timeoutmillis: Long,
    intervalmillis: Long
  ): Consequence[ServiceContainerReadinessPolicy] =
    if (timeoutmillis <= 0L)
      Consequence.argumentLimitExceeded("timeoutMillis", 1L, timeoutmillis, "service-container.readiness")
    else if (intervalmillis <= 0L || intervalmillis > timeoutmillis)
      Consequence.argumentPolicyViolation(
        "intervalMillis",
        "service-container.readiness",
        s"positive interval not greater than ${timeoutmillis}",
        intervalmillis
      )
    else
      Consequence.success(ServiceContainerReadinessPolicy(probe, timeoutmillis, intervalmillis))
}

enum ServiceContainerStatus {
  case Declared
  case Absent
  case Created
  case Starting
  case Ready
  case Unhealthy
  case Stopped
  case Failed
}

enum ServiceContainerTransition(val name: String) {
  case Inspect extends ServiceContainerTransition("inspect")
  case Create extends ServiceContainerTransition("create")
  case Reuse extends ServiceContainerTransition("reuse")
  case Start extends ServiceContainerTransition("start")
  case CheckReadiness extends ServiceContainerTransition("check-readiness")
  case Stop extends ServiceContainerTransition("stop")
  case Restart extends ServiceContainerTransition("restart")
  case Remove extends ServiceContainerTransition("remove")
}

enum ServiceContainerOwnershipMode(val name: String) {
  case External extends ServiceContainerOwnershipMode("external")
  case RuntimeOwned extends ServiceContainerOwnershipMode("runtime-owned")
}

sealed abstract class ServiceContainerDefinition {
  def serviceId: ServiceContainerId
  def ownershipMode: ServiceContainerOwnershipMode
}

object ServiceContainerDefinition {
  final case class External private[servicecontainer] (
    serviceId: ServiceContainerId,
    endpoint: ServiceContainerEndpoint
  ) extends ServiceContainerDefinition {
    def ownershipMode: ServiceContainerOwnershipMode = ServiceContainerOwnershipMode.External
  }

  final case class RuntimeOwned private[servicecontainer] (
    serviceId: ServiceContainerId,
    owner: ServiceContainerOwner,
    image: ServiceContainerImage,
    ports: Vector[ServiceContainerPort],
    readiness: ServiceContainerReadinessPolicy,
    persistence: ServiceContainerPersistence,
    reusePolicy: ServiceContainerReusePolicy,
    cleanupPolicy: ServiceContainerCleanupPolicy
  ) extends ServiceContainerDefinition {
    def ownershipMode: ServiceContainerOwnershipMode = ServiceContainerOwnershipMode.RuntimeOwned

    def registryKey: ServiceContainerRegistryKey =
      ServiceContainerRegistryKey(owner, serviceId)
  }

  def external(
    serviceid: ServiceContainerId,
    endpoint: ServiceContainerEndpoint
  ): ServiceContainerDefinition =
    new External(serviceid, endpoint)

  def runtimeOwnedC(
    serviceid: ServiceContainerId,
    owner: ServiceContainerOwner,
    image: ServiceContainerImage,
    ports: Vector[ServiceContainerPort],
    readiness: ServiceContainerReadinessPolicy,
    persistence: ServiceContainerPersistence = ServiceContainerPersistence.Ephemeral,
    reusepolicy: ServiceContainerReusePolicy = ServiceContainerReusePolicy.CreateOrReuse,
    cleanuppolicy: ServiceContainerCleanupPolicy = ServiceContainerCleanupPolicy.Stop
  ): Consequence[ServiceContainerDefinition.RuntimeOwned] = {
    val names = ports.map(_.name)
    val numbers = ports.map(_.containerPort)
    if (ports.isEmpty)
      Consequence.argumentMissing("ports")
    else if (names.distinct.size != names.size || numbers.distinct.size != numbers.size)
      Consequence.argumentPolicyViolation(
        "ports",
        "service-container.port",
        "unique logical names and container ports",
        "duplicate"
      )
    else if (!names.contains(readiness.probe.portName))
      Consequence.argumentPolicyViolation(
        "readiness",
        "service-container.readiness",
        "probe references a declared logical port",
        readiness.probe.portName.print
      )
    else
      Consequence.success(new RuntimeOwned(
        serviceid,
        owner,
        image,
        ports,
        readiness,
        persistence,
        reusepolicy,
        cleanuppolicy
      ))
  }
}

object ServiceContainerDiagnostics {
  def gatewayUnavailableC[A](serviceid: ServiceContainerId): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      s"Service-container gateway is unavailable for ${serviceid.print}.",
      Cause.Kind.Exhaustion,
      _facets(serviceid, "gateway-unavailable")
    )

  def imageUnavailableC[A](serviceid: ServiceContainerId): Consequence.Failure[A] =
    Consequence.resourceNotFound(
      s"Managed service image is unavailable for ${serviceid.print}.",
      _facets(serviceid, "image-unavailable")
    )

  def ownershipConflictC[A](key: ServiceContainerRegistryKey): Consequence.Failure[A] =
    _conflict_c(
      "service-container ownership",
      _key_facets(key, "ownership-conflict")
    )

  def incompatibleExistingServiceC[A](key: ServiceContainerRegistryKey): Consequence.Failure[A] =
    _conflict_c(
      "service-container compatibility",
      _key_facets(key, "incompatible-existing-service")
    )

  def requiredExistingServiceC[A](key: ServiceContainerRegistryKey): Consequence.Failure[A] =
    Consequence.resourceNotFound(
      s"Required managed service does not exist: ${key.print}.",
      _key_facets(key, "required-existing-service")
    )

  def portConflictC[A](serviceid: ServiceContainerId): Consequence.Failure[A] =
    _conflict_c(
      "service-container port",
      _facets(serviceid, "port-conflict")
    )

  def startupFailureC[A](serviceid: ServiceContainerId): Consequence.Failure[A] =
    Consequence.resourceInvalid(
      s"Managed service startup failed for ${serviceid.print}.",
      Cause.Kind.Unknown,
      _facets(serviceid, "startup-failure")
    )

  def readinessTimeoutC[A](serviceid: ServiceContainerId): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      s"Managed service readiness timed out for ${serviceid.print}.",
      Cause.Kind.Timeout,
      _facets(serviceid, "readiness-timeout")
    )

  def unhealthyC[A](serviceid: ServiceContainerId): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      s"Managed service is unhealthy: ${serviceid.print}.",
      Cause.Kind.Inconsistency,
      _facets(serviceid, "unhealthy")
    )

  def revisionConflictC[A](key: ServiceContainerRegistryKey): Consequence.Failure[A] =
    _conflict_c(
      "service-container registry revision",
      _key_facets(key, "revision-conflict")
    )

  private def _conflict_c[A](
    name: String,
    facets: Vector[Descriptor.Facet]
  ): Consequence.Failure[A] = {
    val Consequence.Failure(conclusion) = Consequence.operationConflict[A](name, facets)
    val cause = conclusion.observation.cause.copy(kind = Some(Cause.Kind.Conflict))
    val observation = conclusion.observation.copy(cause = cause)
    Consequence.Failure(conclusion.copy(observation = observation))
  }

  private def _facets(
    serviceid: ServiceContainerId,
    reason: String
  ): Vector[Descriptor.Facet] =
    Vector(
      Descriptor.Facet.Reason(reason),
      Descriptor.Facet.Policy(s"service-container.${reason}"),
      Descriptor.Facet.Id(serviceid.print)
    )

  private def _key_facets(
    key: ServiceContainerRegistryKey,
    reason: String
  ): Vector[Descriptor.Facet] =
    Vector(
      Descriptor.Facet.Reason(reason),
      Descriptor.Facet.Policy(s"service-container.${reason}"),
      Descriptor.Facet.Id(key.print)
    )
}
