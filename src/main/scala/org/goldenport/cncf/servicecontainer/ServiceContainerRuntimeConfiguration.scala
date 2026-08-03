package org.goldenport.cncf.servicecontainer

import java.util.Locale

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingCollection, ResolvedConfiguration}
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationTarget, RuntimeConfig}

/*
 * @since   Jul. 20, 2026
 * @version Jul. 30, 2026
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

  private[servicecontainer] def createC(
    configuration: ResolvedConfiguration
  ): Consequence[Option[ServiceContainerRuntime]] =
    _create(
      _value(configuration, _driver_keys),
      _value(configuration, _docker_executable_keys)
    )

  def createForRuntime(
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget]
  ): Consequence[Option[ServiceContainerRuntime]] =
    if (bindings == null)
      Consequence.configurationInvalid("runtime service-container configuration bindings are required")
    else
      for {
        driver <- bindings.value(CncfConfigurationParameterCatalog.serviceContainerDriver)
        executable <- bindings.value(CncfConfigurationParameterCatalog.serviceContainerDockerExecutable)
        runtime <- _create(driver, executable)
      } yield runtime

  private def _create(
    driver: Option[String],
    configuredexecutable: Option[String]
  ): Consequence[Option[ServiceContainerRuntime]] =
    driver.map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("none") match {
      case "none" | "disabled" => Consequence.success(None)
      case "docker" =>
        _docker_executable_c(configuredexecutable).map { executable =>
          Some(ServiceContainerRuntime.create(
            ServiceContainerRegistry.inMemory(),
            DockerServiceContainerGateway.create(executable)
          ))
        }
      case value =>
        Consequence.argumentInvalid("serviceContainerDriver", "none or docker", value)
    }

  private def _docker_executable_c(
    configuredexecutable: Option[String]
  ): Consequence[String] = {
    val executable = configuredexecutable.map(_.trim).getOrElse("docker")
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
