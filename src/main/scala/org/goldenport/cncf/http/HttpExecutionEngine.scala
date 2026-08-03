package org.goldenport.cncf.http

import java.nio.file.Path

import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.Consequence
import org.goldenport.protocol.{Request, Response}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.configuration.ConfigurationBindingCollection

/*
 * @since   Jan.  8, 2026
 *  version Jan.  9, 2026
 *  version Mar. 19, 2026
 *  version Apr. 25, 2026
 *  version Jul.  7, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpExecutionEngine(
  subsystem: Subsystem,
  webDescriptorOption: Option[WebDescriptor] = None,
  private val _web_descriptor_configuration_path_option: Option[Option[Path]] = None,
  private val _runtime_component_dev_dirs_option: Option[Vector[Path]] = None
) {
  lazy val webDescriptor: WebDescriptor =
    webDescriptorOption.getOrElse(WebDescriptorResolver
      .resolve(subsystem)
      .toOption
      .getOrElse(WebDescriptor.empty))

  def execute(req: HttpRequest): HttpResponse =
    subsystem.executeHttp(req)

  def executeWithMetadata(req: HttpRequest): HttpExecutionResult =
    subsystem.executeHttpWithMetadata(req)

  def execute(req: Request): Consequence[Response] =
    subsystem.execute(req)

  def runtimeSubsystem: Subsystem = subsystem

  private[http] def webDescriptorConfigurationPathOption: Option[Option[Path]] =
    _web_descriptor_configuration_path_option

  private[http] def runtimeComponentDevDirs: Option[Vector[Path]] =
    _runtime_component_dev_dirs_option
}

final case class HttpExecutionResult(
  response: HttpResponse,
  metadata: RuntimeContext.ExecutionMetadata
)

object HttpExecutionEngine {
  import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}

  object Factory { // TODO
    def subsystem(): Subsystem = {
      val subsystem = DefaultSubsystemFactory.default()
      subsystem.admitRuntimeConfigurationBindingsC(ConfigurationBindingCollection.empty).getOrElse(
        throw new IllegalStateException("default HTTP Subsystem configuration binding admission failed")
      )
      subsystem
    }

    def engine(): HttpExecutionEngine =
      new HttpExecutionEngine(subsystem())

    def forRuntime(subsystem: Subsystem): Consequence[HttpExecutionEngine] =
      for {
        descriptor <- WebDescriptorResolver.resolveForRuntimeSubsystem(subsystem)
        path <- subsystem.runtimeWebDescriptorPathC
        componentdevdirs <- subsystem.runtimeComponentDevDirsC
      } yield new HttpExecutionEngine(subsystem, Some(descriptor), Some(path), Some(componentdevdirs))
  }
}
