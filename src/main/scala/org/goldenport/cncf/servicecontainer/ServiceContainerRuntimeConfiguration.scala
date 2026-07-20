package org.goldenport.cncf.servicecontainer

import java.util.Locale

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.config.RuntimeConfig

/*
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object ServiceContainerRuntimeConfiguration {
  val DRIVER_KEY = "textus.service-container.driver"
  val DOCKER_EXECUTABLE_KEY = "textus.service-container.docker.executable"

  private val _driver_keys = Vector(
    DRIVER_KEY,
    "textus.runtime.service-container.driver",
    "cncf.service-container.driver",
    "cncf.runtime.service-container.driver"
  )
  private val _docker_executable_keys = Vector(
    DOCKER_EXECUTABLE_KEY,
    "textus.runtime.service-container.docker.executable",
    "cncf.service-container.docker.executable",
    "cncf.runtime.service-container.docker.executable"
  )

  def createC(
    configuration: ResolvedConfiguration
  ): Consequence[Option[ServiceContainerRuntime]] =
    _value(configuration, _driver_keys).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("none") match {
      case "none" | "disabled" => Consequence.success(None)
      case "docker" =>
        _docker_executable_c(configuration).map { executable =>
          Some(ServiceContainerRuntime.create(
            ServiceContainerRegistry.inMemory(),
            DockerServiceContainerGateway.create(executable)
          ))
        }
      case value =>
        Consequence.argumentInvalid("serviceContainerDriver", "none or docker", value)
    }

  private def _docker_executable_c(
    configuration: ResolvedConfiguration
  ): Consequence[String] = {
    val executable = _value(configuration, _docker_executable_keys).map(_.trim).getOrElse("docker")
    if (
      executable.isEmpty ||
      executable.length > 1024 ||
      executable.exists(x => x.isControl || x.isWhitespace)
    )
      Consequence.argumentFormatError(
        "serviceContainerDockerExecutable",
        "non-empty executable path without whitespace",
        "invalid"
      )
    else
      Consequence.success(executable)
  }

  private def _value(
    configuration: ResolvedConfiguration,
    keys: Vector[String]
  ): Option[String] =
    keys.iterator.flatMap(RuntimeConfig.getString(configuration, _)).toSeq.headOption
}
