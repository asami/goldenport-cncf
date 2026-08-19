package org.goldenport.cncf.servicecontainer

import java.net.{HttpURLConnection, InetSocketAddress, Socket, URI}
import java.io.{ByteArrayOutputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.Try

import io.circe.{HCursor, Json}
import io.circe.parser.parse
import org.goldenport.Consequence

/*
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class DockerServiceContainerGateway private[servicecontainer] (
  runner: DockerServiceContainerCommandRunner
) extends ServiceContainerGateway {
  import DockerServiceContainerGateway.*

  private[servicecontainer] def configuredExecutable: Option[String] =
    runner match {
      case x: LocalDockerServiceContainerCommandRunner => Some(x.configuredExecutable)
      case _ => None
    }

  def inspectC(
    key: ServiceContainerRegistryKey
  ): Consequence[Option[ServiceContainerInspection]] =
    _run_c(key.serviceId, _inspect_filter_arguments(key)).flatMap { result =>
      _require_success_c(result, key.serviceId, ServiceContainerTransition.Inspect).flatMap { _ =>
        val ids = result.stdout.linesIterator.map(_.trim).filter(_.nonEmpty).toVector.distinct
        ids match {
          case Vector() => Consequence.success(None)
          case Vector(id) =>
            ServiceContainerInstanceId.parseC(id).flatMap(_inspect_detail_c).map(x => Some(x.inspection))
          case _ => ServiceContainerDiagnostics.ownershipConflictC(key)
        }
      }
    }

  def createC(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): Consequence[ServiceContainerInspection] = {
    val serviceid = definition.serviceId
    _run_c(serviceid, _create_arguments(definition)).flatMap { result =>
      _require_create_success_c(result, definition.registryKey).flatMap { _ =>
        ServiceContainerInstanceId.parseC(result.stdout.linesIterator.nextOption().getOrElse(""))
          .flatMap(_inspect_detail_c)
          .map(_.inspection)
      }
    }
  }

  private def _require_create_success_c(
    result: DockerServiceContainerCommandResult,
    key: ServiceContainerRegistryKey
  ): Consequence[Unit] = {
    val error = result.stderr.toLowerCase(java.util.Locale.ROOT)
    if (
      result.exitCode != 0 &&
      error.contains("container name") &&
      error.contains("already in use")
    )
      ServiceContainerDiagnostics.ownershipConflictC(key)
    else
      _require_success_c(result, key.serviceId, ServiceContainerTransition.Create)
  }

  def startC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection] =
    _mutate_and_inspect_c(instanceid, ServiceContainerTransition.Start, Vector("start", instanceid.print))

  def checkReadinessC(
    instanceid: ServiceContainerInstanceId,
    policy: ServiceContainerReadinessPolicy
  ): Consequence[ServiceContainerReadinessResult] =
    _inspect_detail_c(instanceid).flatMap { initial =>
      val serviceid = initial.inspection.registryKey.serviceId
      val containerport = initial.inspection.ports
        .find(_.name == policy.probe.portName)
        .map(_.containerPort)
      Consequence.fromOption(
        containerport,
        s"Readiness port is unavailable for ${serviceid.print}."
      ).flatMap { port =>
        val deadline = System.nanoTime() + policy.timeoutMillis * 1000000L
        _await_readiness_c(instanceid, policy, port, deadline)
      }
    }

  def stopC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection] =
    _mutate_and_inspect_c(instanceid, ServiceContainerTransition.Stop, Vector("stop", instanceid.print))

  def restartC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection] =
    _mutate_and_inspect_c(instanceid, ServiceContainerTransition.Restart, Vector("restart", instanceid.print))

  def removeC(
    instanceid: ServiceContainerInstanceId
  ): Consequence[ServiceContainerInspection] =
    _inspect_detail_c(instanceid).flatMap { before =>
      val serviceid = before.inspection.registryKey.serviceId
      _run_c(serviceid, Vector("rm", "--force", instanceid.print)).flatMap { result =>
        _require_success_c(result, serviceid, ServiceContainerTransition.Remove)
          .map(_ => before.inspection)
      }
    }

  private def _mutate_and_inspect_c(
    instanceid: ServiceContainerInstanceId,
    transition: ServiceContainerTransition,
    arguments: Vector[String]
  ): Consequence[ServiceContainerInspection] =
    _inspect_detail_c(instanceid).flatMap { before =>
      val serviceid = before.inspection.registryKey.serviceId
      _run_c(serviceid, arguments).flatMap { result =>
        _require_success_c(result, serviceid, transition)
          .flatMap(_ => _inspect_detail_c(instanceid))
          .map(_.inspection)
      }
    }

  private def _await_readiness_c(
    instanceid: ServiceContainerInstanceId,
    policy: ServiceContainerReadinessPolicy,
    containerport: Int,
    deadline: Long
  ): Consequence[ServiceContainerReadinessResult] = {
    @tailrec
    def _poll_(): Consequence[ServiceContainerReadinessResult] =
      _inspect_detail_c(instanceid) match {
        case Consequence.Failure(conclusion) => Consequence.Failure(conclusion)
        case Consequence.Success(detail) =>
          val serviceid = detail.inspection.registryKey.serviceId
          if (detail.inspection.status != ServiceContainerStatus.Ready &&
              detail.inspection.status != ServiceContainerStatus.Starting)
            ServiceContainerDiagnostics.unhealthyC(serviceid)
          else
            detail.hostPorts.get(containerport) match {
              case None => ServiceContainerDiagnostics.portConflictC(serviceid)
              case Some(hostport) =>
                val ready = policy.probe match {
                  case x: ServiceContainerReadinessProbe.Http =>
                    _http_ready(hostport, x.path, x.expectedStatuses, policy.intervalMillis)
                  case _: ServiceContainerReadinessProbe.Tcp =>
                    _tcp_ready(hostport, policy.intervalMillis)
                }
                if (ready)
                  ServiceContainerEndpoint.parseC(s"http://127.0.0.1:$hostport")
                    .map(ServiceContainerReadinessResult.apply)
                else if (System.nanoTime() >= deadline)
                  ServiceContainerDiagnostics.readinessTimeoutC(serviceid)
                else
                  _sleep_c(serviceid, policy.intervalMillis) match {
                    case Consequence.Success(_) => _poll_()
                    case Consequence.Failure(conclusion) => Consequence.Failure(conclusion)
                  }
            }
      }
    _poll_()
  }

  private def _sleep_c(
    serviceid: ServiceContainerId,
    intervalmillis: Long
  ): Consequence[Unit] =
    try {
      Thread.sleep(intervalmillis)
      Consequence.success(())
    } catch {
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        ServiceContainerDiagnostics.gatewayUnavailableC(serviceid)
    }

  private def _inspect_detail_c(
    instanceid: ServiceContainerInstanceId
  ): Consequence[DockerInspection] =
    ServiceContainerId.parseC("docker").flatMap { serviceid =>
      _run_c(serviceid, Vector("inspect", instanceid.print)).flatMap { result =>
        _require_success_c(result, serviceid, ServiceContainerTransition.Inspect)
          .flatMap(_ => _decode_inspection_c(result.stdout))
      }
    }

  private def _run_c(
    serviceid: ServiceContainerId,
    arguments: Vector[String]
  ): Consequence[DockerServiceContainerCommandResult] =
    runner.runC(arguments) match {
      case Consequence.Success(result) => Consequence.success(result)
      case Consequence.Failure(_) => ServiceContainerDiagnostics.gatewayUnavailableC(serviceid)
    }

  private def _require_success_c(
    result: DockerServiceContainerCommandResult,
    serviceid: ServiceContainerId,
    transition: ServiceContainerTransition
  ): Consequence[Unit] =
    if (result.exitCode == 0)
      Consequence.success(())
    else {
      val error = result.stderr.toLowerCase(java.util.Locale.ROOT)
      if (transition == ServiceContainerTransition.Create &&
          (error.contains("no such image") || error.contains("pull access denied") ||
            error.contains("not found")))
        ServiceContainerDiagnostics.imageUnavailableC(serviceid)
      else if (error.contains("port is already allocated") || error.contains("address already in use"))
        ServiceContainerDiagnostics.portConflictC(serviceid)
      else if (
        transition == ServiceContainerTransition.Create ||
        transition == ServiceContainerTransition.Start ||
        transition == ServiceContainerTransition.Restart
      )
        ServiceContainerDiagnostics.startupFailureC(serviceid)
      else
        ServiceContainerDiagnostics.gatewayUnavailableC(serviceid)
    }

  private def _decode_inspection_c(value: String): Consequence[DockerInspection] =
    for {
      json <- Consequence.fromTry(Try(parse(value).fold(throw _, identity)))
      cursor <- Consequence.fromOption(json.asArray.flatMap(_.headOption).map(_.hcursor), "Docker inspection is empty")
      id <- _string_c(cursor, "Id").flatMap(ServiceContainerInstanceId.parseC)
      image <- _string_c(cursor, "Config", "Image").flatMap(ServiceContainerImage.parseC)
      labels <- _string_map_c(cursor, "Config", "Labels")
      key <- _registry_key_c(labels)
      ports <- _ports_c(labels)
      status <- _status_c(cursor)
      hostports <- _host_ports_c(cursor)
      endpoint <- _endpoint_c(hostports)
    } yield DockerInspection(
      ServiceContainerInspection(
        id,
        key,
        image,
        ports,
        ServiceContainerOwnershipLabels.observed(labels),
        status,
        endpoint
      ),
      hostports
    )

  private def _registry_key_c(
    labels: Map[String, String]
  ): Consequence[ServiceContainerRegistryKey] =
    for {
      kindtext <- Consequence.fromOption(labels.get(ServiceContainerOwnershipLabels.OWNER_KIND), "Docker owner kind is missing")
      kind <- kindtext match {
        case "component-runtime" => Consequence.success(ServiceContainerOwnerKind.ComponentRuntime)
        case "subsystem-runtime" => Consequence.success(ServiceContainerOwnerKind.SubsystemRuntime)
        case _ => Consequence.argumentInvalid("ownerKind", "component-runtime or subsystem-runtime", kindtext)
      }
      ownertext <- Consequence.fromOption(labels.get(ServiceContainerOwnershipLabels.OWNER_ID), "Docker owner id is missing")
      ownerid <- ServiceContainerOwnerId.parseC(ownertext)
      servicetext <- Consequence.fromOption(labels.get(ServiceContainerOwnershipLabels.SERVICE_ID), "Docker service id is missing")
      serviceid <- ServiceContainerId.parseC(servicetext)
    } yield ServiceContainerRegistryKey(ServiceContainerOwner(kind, ownerid), serviceid)

  private def _ports_c(
    labels: Map[String, String]
  ): Consequence[Vector[ServiceContainerPort]] = {
    val values = labels.toVector.collect {
      case (key, value) if key.startsWith(PORT_LABEL_PREFIX) =>
        key.stripPrefix(PORT_LABEL_PREFIX) -> value
    }.sortBy(_._1)
    _traverse_c(values) { case (name, value) =>
      for {
        portname <- ServiceContainerPortName.parseC(name)
        number <- Consequence.fromOption(value.toIntOption, s"Docker container port is invalid for $name")
        port <- ServiceContainerPort.createC(portname, number)
      } yield port
    }
  }

  private def _status_c(cursor: HCursor): Consequence[ServiceContainerStatus] =
    _string_c(cursor, "State", "Status").map {
      case "created" => ServiceContainerStatus.Created
      case "running" => ServiceContainerStatus.Starting
      case "restarting" => ServiceContainerStatus.Starting
      case "exited" => ServiceContainerStatus.Stopped
      case "dead" => ServiceContainerStatus.Failed
      case _ => ServiceContainerStatus.Unhealthy
    }

  private def _host_ports_c(cursor: HCursor): Consequence[Map[Int, Int]] = {
    val result = cursor.downField("NetworkSettings").downField("Ports").focus
      .flatMap(_.asObject)
      .toVector
      .flatMap(_.toVector)
      .flatMap { case (key, mappings) =>
        val containerport = key.stripSuffix("/tcp").toIntOption
        val hostport = mappings.asArray.flatMap(_.headOption)
          .flatMap(_.hcursor.get[String]("HostPort").toOption)
          .flatMap(_.toIntOption)
        for {
          container <- containerport
          host <- hostport
        } yield container -> host
      }.toMap
    Consequence.success(result)
  }

  private def _endpoint_c(
    hostports: Map[Int, Int]
  ): Consequence[Option[ServiceContainerEndpoint]] =
    hostports.values.toVector.sorted.headOption match {
      case None => Consequence.success(None)
      case Some(port) => ServiceContainerEndpoint.parseC(s"http://127.0.0.1:$port").map(Some.apply)
    }

  private def _string_c(
    cursor: HCursor,
    path: String*
  ): Consequence[String] = {
    val target = path.foldLeft(Option(cursor))((z, x) => z.flatMap(_.downField(x).success))
    Consequence.fromOption(target.flatMap(_.as[String].toOption), s"Docker inspection field is missing: ${path.mkString(".")}")
  }

  private def _string_map_c(
    cursor: HCursor,
    path: String*
  ): Consequence[Map[String, String]] = {
    val target = path.foldLeft(Option(cursor))((z, x) => z.flatMap(_.downField(x).success))
    Consequence.fromOption(target.flatMap(_.as[Map[String, String]].toOption), s"Docker inspection map is missing: ${path.mkString(".")}")
  }

  private def _traverse_c[A, B](
    values: Vector[A]
  )(f: A => Consequence[B]): Consequence[Vector[B]] =
    values.foldLeft(Consequence.success(Vector.empty[B])) { (z, x) =>
      z.flatMap(xs => f(x).map(xs :+ _))
    }
}

object DockerServiceContainerGateway {
  private val PORT_LABEL_PREFIX = "org.goldenport.cncf.port."

  def create(
    executable: String = "docker"
  ): DockerServiceContainerGateway =
    new DockerServiceContainerGateway(DockerServiceContainerCommandRunner.local(executable))

  private[servicecontainer] def create(
    runner: DockerServiceContainerCommandRunner
  ): DockerServiceContainerGateway =
    new DockerServiceContainerGateway(runner)

  private final case class DockerInspection(
    inspection: ServiceContainerInspection,
    hostPorts: Map[Int, Int]
  )

  private def _inspect_filter_arguments(
    key: ServiceContainerRegistryKey
  ): Vector[String] =
    Vector(
      "ps",
      "--all",
      "--filter", s"label=${ServiceContainerOwnershipLabels.MANAGED}=true",
      "--filter", s"label=${ServiceContainerOwnershipLabels.OWNER_KIND}=${key.owner.kind.name}",
      "--filter", s"label=${ServiceContainerOwnershipLabels.OWNER_ID}=${key.owner.id.print}",
      "--filter", s"label=${ServiceContainerOwnershipLabels.SERVICE_ID}=${key.serviceId.print}",
      "--format", "{{.ID}}"
    )

  private def _create_arguments(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): Vector[String] = {
    val labels = ServiceContainerOwnershipLabels.from(definition).values.toVector ++
      definition.ports.map(x => s"$PORT_LABEL_PREFIX${x.name.print}" -> x.containerPort.toString)
    val ports = definition.ports.flatMap { port =>
      Vector("--publish", s"127.0.0.1::${port.containerPort}")
    }
    val mounts = definition.persistence match {
      case ServiceContainerPersistence.Ephemeral => Vector.empty
      case x: ServiceContainerPersistence.NamedVolumes =>
        x.volumes.flatMap { volume =>
          Vector(
            "--mount",
            s"type=volume,source=${volume.name.print},target=${volume.target.print}"
          )
        }
    }
    Vector("create", "--name", _container_name(definition)) ++
      labels.sortBy(_._1).flatMap { case (key, value) => Vector("--label", s"$key=$value") } ++
      ports ++ mounts ++ Vector(definition.image.print)
  }

  private def _container_name(
    definition: ServiceContainerDefinition.RuntimeOwned
  ): String = {
    val prefix = s"cncf-${definition.owner.id.print}-${definition.serviceId.print}"
    val suffix = ServiceContainerOwnershipLabels.contractDigest(definition).take(12)
    s"${prefix.take(48)}-$suffix"
  }

  private def _http_ready(
    hostport: Int,
    path: String,
    expectedstatuses: Set[Int],
    timeoutmillis: Long
  ): Boolean =
    Try {
      val connection = URI.create(s"http://127.0.0.1:$hostport$path").toURL.openConnection()
        .asInstanceOf[HttpURLConnection]
      val timeout = Math.max(1L, Math.min(timeoutmillis, Int.MaxValue.toLong)).toInt
      connection.setConnectTimeout(timeout)
      connection.setReadTimeout(timeout)
      connection.setRequestMethod("GET")
      try expectedstatuses.contains(connection.getResponseCode)
      finally connection.disconnect()
    }.getOrElse(false)

  private def _tcp_ready(
    hostport: Int,
    timeoutmillis: Long
  ): Boolean =
    Try {
      val socket = new Socket()
      try {
        val timeout = Math.max(1L, Math.min(timeoutmillis, Int.MaxValue.toLong)).toInt
        socket.connect(new InetSocketAddress("127.0.0.1", hostport), timeout)
        true
      } finally socket.close()
    }.getOrElse(false)
}

private[servicecontainer] final case class DockerServiceContainerCommandResult(
  exitCode: Int,
  stdout: String,
  stderr: String
)

private[servicecontainer] abstract class DockerServiceContainerCommandRunner {
  def runC(arguments: Vector[String]): Consequence[DockerServiceContainerCommandResult]
}

private[servicecontainer] object DockerServiceContainerCommandRunner {
  def local(executable: String): DockerServiceContainerCommandRunner =
    new LocalDockerServiceContainerCommandRunner(executable)
}

private final class LocalDockerServiceContainerCommandRunner(
  executable: String
) extends DockerServiceContainerCommandRunner {
  def configuredExecutable: String = executable

  private val _maximum_captured_output_bytes = 1024 * 1024

  def runC(
    arguments: Vector[String]
  ): Consequence[DockerServiceContainerCommandResult] =
    Consequence.fromTry(Try {
      // This is the sole Docker process boundary. Argument vectors are produced
      // only from validated service-container model values and never invoke a shell.
      val process = new ProcessBuilder((executable +: arguments).asJava).start()
      val stdout = new ByteArrayOutputStream()
      val stderr = new ByteArrayOutputStream()
      val stdoutthread = _copy_thread(process.getInputStream, stdout, "cncf-docker-stdout")
      val stderrthread = _copy_thread(process.getErrorStream, stderr, "cncf-docker-stderr")
      val completed = process.waitFor(60L, TimeUnit.SECONDS)
      if (!completed) {
        process.destroyForcibly()
        process.waitFor(5L, TimeUnit.SECONDS)
        stdoutthread.join(1000L)
        stderrthread.join(1000L)
        throw new IllegalStateException("Docker command timed out")
      }
      stdoutthread.join()
      stderrthread.join()
      DockerServiceContainerCommandResult(
        process.exitValue(),
        stdout.toString(StandardCharsets.UTF_8).trim,
        stderr.toString(StandardCharsets.UTF_8).trim
      )
    })

  private def _copy_thread(
    source: InputStream,
    target: ByteArrayOutputStream,
    name: String
  ): Thread = {
    val thread = new Thread(
      () => {
        val buffer = new Array[Byte](8192)
        var captured = 0
        var count = source.read(buffer)
        while (count >= 0) {
          val remaining = _maximum_captured_output_bytes - captured
          if (remaining > 0) {
            val length = Math.min(count, remaining)
            target.write(buffer, 0, length)
            captured += length
          }
          count = source.read(buffer)
        }
      },
      name
    )
    thread.setDaemon(true)
    thread.start()
    thread
  }
}
