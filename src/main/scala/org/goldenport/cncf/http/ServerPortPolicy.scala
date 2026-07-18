package org.goldenport.cncf.http

import java.net.{InetSocketAddress, ServerSocket}
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import scala.util.Using
import scala.util.control.NonFatal
import io.circe.{Json, Printer}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.config.{ConfigurationAccess, RuntimeConfig}
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
object ServerPortPolicy {
  val CarStartPort = 18000
  val CarEndPort = 27999
  val SarStartPort = 28000
  val SarEndPort = 37999
  val AdditionalInstanceStartPort = 38000
  val AdditionalInstanceEndPort = 47999
  val RuntimeDefaultPort = 8080
  val DefaultPortKey = "textus.server.default-port"
  val PortAssignmentFileKey = "textus.server.port-assignment-file"

  enum ArtifactKind {
    case Car
    case Sar
    case Runtime

    def name: String = toString.toLowerCase
  }

  final case class ArtifactIdentity(
    kind: ArtifactKind,
    name: String
  )

  trait Availability {
    def isAvailable(port: Int): Boolean
  }

  object Availability {
    object System extends Availability {
      def isAvailable(port: Int): Boolean =
        scala.util.Try {
          Using.resource(new ServerSocket()) { socket =>
            socket.setReuseAddress(false)
            socket.bind(new InetSocketAddress("0.0.0.0", port))
          }
        }.isSuccess
    }
  }

  trait AssignmentStore {
    def defaultPort(
      identity: ArtifactIdentity,
      requestedPort: Option[Int] = None
    ): Consequence[Int]
  }

  object AssignmentStore {
    def system(subsystem: Subsystem): AssignmentStore = {
      val configured = ConfigurationAccess.getString(subsystem.configuration, PortAssignmentFileKey)
      val runtimehome = ConfigurationAccess
        .getString(subsystem.configuration, RuntimeConfig.TEST_HOME_PATH_KEY)
        .orElse(ConfigurationAccess.getString(subsystem.configuration, RuntimeConfig.RUNTIME_TEST_HOME_PATH_KEY))
        .map(Paths.get(_))
        .getOrElse(Paths.get(sys.props.getOrElse("user.home", ".")))
      val path = configured
        .map(Paths.get(_))
        .getOrElse(runtimehome.resolve(".cncf").resolve("server-port-assignments.json"))
      new FileAssignmentStore(path)
    }
  }

  final class FileAssignmentStore(path: Path) extends AssignmentStore {
    def defaultPort(
      identity: ArtifactIdentity,
      requestedPort: Option[Int] = None
    ): Consequence[Int] =
      try {
        Option(path.getParent).foreach(Files.createDirectories(_))
        val channel = FileChannel.open(
          path,
          StandardOpenOption.CREATE,
          StandardOpenOption.READ,
          StandardOpenOption.WRITE
        )
        try {
          Using.resource(channel.lock()) { _ =>
            _load(channel).flatMap { assignments =>
              assignments.find(_.identity == identity) match {
                case Some(assignment) =>
                  requestedPort match {
                    case Some(port) if port != assignment.defaultPort =>
                      Consequence.argumentInvalid(
                        s"server port assignment for ${identity.kind.name}:${identity.name} is ${assignment.defaultPort}, not ${port}"
                      )
                    case _ => Consequence.success(assignment.defaultPort)
                  }
                case None =>
                  _allocate(identity, requestedPort, assignments).map { assignment =>
                    _save(channel, assignments :+ assignment)
                    assignment.defaultPort
                  }
              }
            }
          }
        } finally {
          channel.close()
        }
      } catch {
        case NonFatal(e) =>
          Consequence.serviceUnavailable(s"server port assignment registry is unavailable: ${path}: ${e.getMessage}")
      }

    private final case class Assignment(
      identity: ArtifactIdentity,
      defaultPort: Int
    )

    private def _load(channel: FileChannel): Consequence[Vector[Assignment]] = {
      channel.position(0)
      if (channel.size() == 0) {
        Consequence.success(Vector.empty)
      } else if (channel.size() > Int.MaxValue) {
        Consequence.serviceUnavailable(s"server port assignment registry is too large: ${path}")
      } else {
        val buffer = ByteBuffer.allocate(channel.size().toInt)
        while (buffer.hasRemaining && channel.read(buffer) >= 0) ()
        val text = new String(buffer.array(), StandardCharsets.UTF_8)
        parse(text) match {
          case Left(error) =>
            Consequence.serviceUnavailable(s"invalid server port assignment registry: ${path}: ${error.message}")
          case Right(json) =>
            val cursor = json.hcursor
            val schemaversion = cursor.get[String]("schemaVersion").toOption
            if (!schemaversion.contains("textus.server-port-assignments.v1")) {
              Consequence.serviceUnavailable(s"unsupported server port assignment registry schema: ${path}")
            } else cursor.downField("assignments").focus.flatMap(_.asArray) match {
              case None => Consequence.serviceUnavailable(s"server port assignment registry has no assignments array: ${path}")
              case Some(values) =>
                values.foldLeft(Consequence.success(Vector.empty): Consequence[Vector[Assignment]]) { (z, value) =>
                  z.flatMap { assignments =>
                    val c = value.hcursor
                    val kind = c.get[String]("artifactKind").toOption.flatMap(_artifact_kind)
                    val name = c.get[String]("artifactName").toOption.map(_.trim).filter(_.nonEmpty)
                    val port = c.get[Int]("defaultPort").toOption
                    (kind, name, port) match {
                      case (Some(k), Some(n), Some(p)) =>
                        Consequence.success(assignments :+ Assignment(ArtifactIdentity(k, n), p))
                      case _ =>
                        Consequence.serviceUnavailable(s"invalid server port assignment entry: ${path}")
                    }
                  }
                }.flatMap(_validate_assignments)
            }
        }
      }
    }

    private def _allocate(
      identity: ArtifactIdentity,
      requestedPort: Option[Int],
      assignments: Vector[Assignment]
    ): Consequence[Assignment] = {
      val (start, end) = _range(identity.kind)
      val used = assignments.map(_.defaultPort).toSet
      val candidate = requestedPort.orElse(
        (start to end).find(port => !used.contains(port))
      )
      candidate match {
        case Some(port) if !_is_valid_default_port(identity.kind, port) =>
          Consequence.argumentInvalid(
            s"${identity.kind.name.toUpperCase} default server port must be in range ${start}-${end}: ${port}"
          )
        case Some(port) if used.contains(port) =>
          Consequence.argumentInvalid(s"server port ${port} is assigned to another CAR/SAR artifact")
        case Some(port) => Consequence.success(Assignment(identity, port))
        case None => Consequence.serviceUnavailable(s"no unassigned ${identity.kind.name.toUpperCase} default server port in range ${start}-${end}")
      }
    }

    private def _validate_assignments(
      assignments: Vector[Assignment]
    ): Consequence[Vector[Assignment]] = {
      val duplicateidentities = assignments.groupBy(_.identity).exists(_._2.size > 1)
      val duplicateports = assignments.groupBy(_.defaultPort).exists(_._2.size > 1)
      val invalidport = assignments.exists(x => !_is_valid_default_port(x.identity.kind, x.defaultPort))
      if (duplicateidentities || duplicateports || invalidport)
        Consequence.serviceUnavailable(s"inconsistent server port assignment registry: ${path}")
      else
        Consequence.success(assignments)
    }

    private def _save(
      channel: FileChannel,
      assignments: Vector[Assignment]
    ): Unit = {
      val json = Json.obj(
        "schemaVersion" -> Json.fromString("textus.server-port-assignments.v1"),
        "assignments" -> Json.arr(assignments.sortBy(_.defaultPort).map { assignment =>
          Json.obj(
            "artifactKind" -> Json.fromString(assignment.identity.kind.name),
            "artifactName" -> Json.fromString(assignment.identity.name),
            "defaultPort" -> Json.fromInt(assignment.defaultPort)
          )
        }*)
      )
      val bytes = (Printer.spaces2SortKeys.print(json) + "\n").getBytes(StandardCharsets.UTF_8)
      channel.truncate(0)
      channel.position(0)
      val buffer = ByteBuffer.wrap(bytes)
      while (buffer.hasRemaining) channel.write(buffer)
      channel.force(true)
    }
  }

  def resolve(
    subsystem: Subsystem,
    availability: Availability = Availability.System,
    assignmentStore: Option[AssignmentStore] = None
  ): Consequence[Int] =
    _explicit_port(subsystem) match {
      case Some(value) => _parse_port(value)
      case None =>
        sys.props
          .get(Http4sHttpServer.PORT_PROPERTY_KEY)
          .orElse(sys.props.get(Http4sHttpServer.LEGACY_PORT_PROPERTY_KEY)) match {
          case Some(value) => _parse_port(value)
          case None => _automatic_port(subsystem, availability, assignmentStore.getOrElse(AssignmentStore.system(subsystem)))
        }
    }

  def artifactIdentity(subsystem: Subsystem): Option[ArtifactIdentity] = {
    val kind = artifactKind(subsystem)
    if (kind == ArtifactKind.Runtime) {
      None
    } else {
      val configuration = subsystem.configuration
      val configuredname = kind match {
        case ArtifactKind.Car =>
          ConfigurationAccess.getString(configuration, RuntimeConfig.ComponentNameKey)
            .orElse(ConfigurationAccess.getString(configuration, RuntimeConfig.RuntimeComponentNameKey))
        case ArtifactKind.Sar =>
          ConfigurationAccess.getString(configuration, RuntimeConfig.SubsystemNameKey)
            .orElse(ConfigurationAccess.getString(configuration, RuntimeConfig.RuntimeSubsystemNameKey))
        case ArtifactKind.Runtime => None
      }
      val name = configuredname
        .orElse(subsystem.descriptor.map(_.subsystemName))
        .orElse {
          if (kind == ArtifactKind.Car)
            subsystem.components.iterator
              .flatMap(_.artifactMetadata)
              .find(_.archivePath.exists(_.toLowerCase.endsWith(".car")))
              .map(_.name)
          else
            None
        }
        .getOrElse(subsystem.name)
        .trim
        .toLowerCase
      Option(name).filter(_.nonEmpty).map(ArtifactIdentity(kind, _))
    }
  }

  def artifactKind(subsystem: Subsystem): ArtifactKind = {
    val configuration = subsystem.configuration
    val subsystemkeys = Vector(
      RuntimeConfig.SubsystemNameKey,
      RuntimeConfig.RuntimeSubsystemNameKey,
      RuntimeConfig.SubsystemDescriptorKey,
      RuntimeConfig.RuntimeSubsystemDescriptorKey,
      RuntimeConfig.SubsystemFileKey,
      RuntimeConfig.RuntimeSubsystemFileKey,
      RuntimeConfig.SubsystemDevDirKey,
      RuntimeConfig.RuntimeSubsystemDevDirKey,
      RuntimeConfig.SubsystemSarDirKey,
      RuntimeConfig.RuntimeSubsystemSarDirKey
    )
    val componentkeys = Vector(
      RuntimeConfig.ComponentNameKey,
      RuntimeConfig.RuntimeComponentNameKey,
      RuntimeConfig.ComponentFileKey,
      RuntimeConfig.RuntimeComponentFileKey,
      RuntimeConfig.ComponentDevDirKey,
      RuntimeConfig.ComponentCarDirKey
    )
    if (subsystemkeys.exists(ConfigurationAccess.getString(configuration, _).nonEmpty)) {
      ArtifactKind.Sar
    } else if (componentkeys.exists(ConfigurationAccess.getString(configuration, _).nonEmpty)) {
      ArtifactKind.Car
    } else {
      subsystem.descriptor
        .flatMap { descriptor =>
          val filename = descriptor.path.getFileName.toString.toLowerCase
          if (filename.endsWith(".sar")) Some(ArtifactKind.Sar)
          else if (filename.endsWith(".car")) Some(ArtifactKind.Car)
          else
            descriptor.assemblyDescriptor.flatMap { source =>
              source.source.trim.toLowerCase match {
                case "sar" => Some(ArtifactKind.Sar)
                case "component-car" => Some(ArtifactKind.Car)
                case _ => None
              }
            }
        }
        .getOrElse(ArtifactKind.Runtime)
    }
  }

  private def _explicit_port(subsystem: Subsystem): Option[String] =
    ConfigurationAccess
      .getString(subsystem.configuration, Http4sHttpServer.PORT_PROPERTY_KEY)
      .orElse(ConfigurationAccess.getString(subsystem.configuration, Http4sHttpServer.LEGACY_PORT_PROPERTY_KEY))

  private def _parse_port(value: String): Consequence[Int] =
    scala.util.Try(value.trim.toInt).toOption match {
      case Some(port) if port >= 1 && port <= 65535 => Consequence.success(port)
      case _ => Consequence.argumentInvalid(s"invalid server port: ${value}")
    }

  private def _automatic_port(
    subsystem: Subsystem,
    availability: Availability,
    assignmentStore: AssignmentStore
  ): Consequence[Int] =
    artifactIdentity(subsystem) match {
      case None => Consequence.success(RuntimeDefaultPort)
      case Some(identity) =>
        ConfigurationAccess.getString(subsystem.configuration, DefaultPortKey) match {
          case Some(value) =>
            _parse_port(value)
              .flatMap(port => assignmentStore.defaultPort(identity, Some(port)))
              .flatMap(_instance_port(_, identity.kind, availability))
          case None => assignmentStore.defaultPort(identity).flatMap(_instance_port(_, identity.kind, availability))
        }
    }

  private def _instance_port(
    defaultPort: Int,
    kind: ArtifactKind,
    availability: Availability
  ): Consequence[Int] = {
    val (start, end) = _range(kind)
    if (!_is_valid_default_port(kind, defaultPort)) {
      Consequence.argumentInvalid(
        s"${kind.name.toUpperCase} default server port must be in range ${start}-${end}: ${defaultPort}"
      )
    } else if (availability.isAvailable(defaultPort)) {
      Consequence.success(defaultPort)
    } else {
      _first_available(AdditionalInstanceStartPort, AdditionalInstanceEndPort, availability)
    }
  }

  private def _range(kind: ArtifactKind): (Int, Int) =
    kind match {
      case ArtifactKind.Car => CarStartPort -> CarEndPort
      case ArtifactKind.Sar => SarStartPort -> SarEndPort
      case ArtifactKind.Runtime => RuntimeDefaultPort -> RuntimeDefaultPort
    }

  private def _is_valid_default_port(
    kind: ArtifactKind,
    port: Int
  ): Boolean = {
    val (start, end) = _range(kind)
    port >= start && port <= end
  }

  private def _artifact_kind(value: String): Option[ArtifactKind] =
    value.trim.toLowerCase match {
      case "car" => Some(ArtifactKind.Car)
      case "sar" => Some(ArtifactKind.Sar)
      case "runtime" => Some(ArtifactKind.Runtime)
      case _ => None
    }

  private def _first_available(
    start: Int,
    end: Int,
    availability: Availability
  ): Consequence[Int] =
    (start to end).find(availability.isAvailable) match {
      case Some(port) => Consequence.success(port)
      case None => Consequence.serviceUnavailable(s"no available server port in additional instance range ${start}-${end}")
    }
}
