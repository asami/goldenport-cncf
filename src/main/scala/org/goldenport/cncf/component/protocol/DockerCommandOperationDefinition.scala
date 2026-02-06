package org.goldenport.cncf.component.protocol

import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.protocol.*
import org.goldenport.protocol.operation.*
import org.goldenport.process.ShellCommand
import org.goldenport.cncf.action.*
import org.goldenport.cncf.component.{CommandParameterMappingRule, ShellCommandComponent}

/*
 * Docker-based command execution.
 *
 * This is a specialization of ShellCommandOperationDefinition
 * that injects `docker run` semantics declaratively.
 *
 * @since   Feb.  6, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class DockerCommandOperationDefinition
  extends ShellCommandOperationDefinition {

  def dockerCommandSpecification: DockerCommandSpecification

  /**
   * Override shell specification to prepend docker run command.
   */
  override def shellSpecification: ShellCommandSpecification =
    dockerCommandSpecification

  /**
   * Docker image name to run.
   */
  def imageName: String

  /**
   * Base docker command.
   *
   * Default:
   *   docker run --rm <image>
   */
  // protected def docker_base_command: Vector[String] =
  //   Vector(
  //     "docker",
  //     "run",
  //     "--rm",
  //     imageName
  //   )

}

abstract class DockerCommandSpecification extends
    ShellCommandSpecification with DockerCommandSpecification.Core.Holder {
}
object DockerCommandSpecification {
  case class Core(
    /** Docker image to run */
    imageName: String,

    /** docker run options (e.g. --rm, --network, --platform) */
    dockerOptions: Vector[String] = Vector("--rm"),

    /** Volume mappings */
    volumes: Vector[DockerVolume] = Vector.empty,

    /** Outputs to collect from container */
    outputs: DockerOutputs = DockerOutputs()
  )
  object Core {
    trait Holder {
      def dockerCore: Core

      def imageName = dockerCore.imageName
      def dockerOptions = dockerCore.dockerOptions
      def volumes = dockerCore.volumes
      def outputs = dockerCore.outputs
    }
  }

  case class Instance(
    core: ShellCommandSpecification.Core,
    dockerCore: DockerCommandSpecification.Core
  ) extends DockerCommandSpecification

  def apply(
    imagename: String,
    dockeroptions: Vector[String] = Vector.empty,
    volumes: Vector[DockerVolume] = Vector.empty,
    outputs: DockerOutputs = DockerOutputs.empty,
    mappingRule: CommandParameterMappingRule = CommandParameterMappingRule.Default,
    workDirHint: Option[Path] = None,
    envHint: Map[String, String] = Map.empty
  ): DockerCommandSpecification = {
    val basecommand: Vector[String] = {
      Vector("docker", "run") ++
      dockeroptions ++
      volumes.flatMap(_.toArgs) ++
      Vector(imagename)
    }
    Instance(
      ShellCommandSpecification.Core(
        basecommand,
        mappingRule,
        workDirHint,
        envHint
      ),
      DockerCommandSpecification.Core(
        imagename,
        dockeroptions,
        volumes,
        outputs
      )
    )
  }
}

final case class DockerVolume(
  host: String,
  container: String,
  readOnly: Boolean = false
) {
  def toArgs: Vector[String] = {
    val mapping = if (readOnly) s"$host:$container:ro" else s"$host:$container"
    Vector("-v", mapping)
  }
}

final case class DockerOutputs(
  files: Vector[DockerFileOutput] = Vector.empty,
  directories: Vector[DockerDirectoryOutput] = Vector.empty
)
object DockerOutputs {
  val empty = DockerOutputs()
}

final case class DockerFileOutput(
  name: String,
  path: String
)

final case class DockerDirectoryOutput(
  name: String,
  path: String,
  mode: DirectoryMode = DirectoryMode.Materialized
)

enum DirectoryMode {
  case Materialized  // Bag[FileContent] / Tree[Bag]
  case Raw           // DirectoryFileSystemView
}

