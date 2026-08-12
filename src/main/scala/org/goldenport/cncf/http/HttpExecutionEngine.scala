package org.goldenport.cncf.http

import java.nio.file.Path

import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.Consequence
import org.goldenport.protocol.{Request, Response}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.configuration.ConfigurationBindingCollection
import org.goldenport.record.Record

/*
 * @since   Jan.  8, 2026
 *  version Jan.  9, 2026
 *  version Mar. 19, 2026
 *  version Apr. 25, 2026
 *  version Jul.  7, 2026
 * @version Aug. 12, 2026
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
    executeWithMetadata(req).response

  def executeWithMetadata(req: HttpRequest): HttpExecutionResult =
    executeWithExecutionResponse(req).toLegacy

  def executeWithExecutionResponse(
    req: HttpRequest
  ): HttpExecutionEnvelope =
    subsystem.executeHttpWithExecutionResponse(req)

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

final case class HttpExecutionEnvelope(
  response: HttpResponse,
  metadata: RuntimeContext.ExecutionMetadata,
  executionResponse: Option[RuntimeContext.ExecutionResponseMetadata]
) {
  def toLegacy: HttpExecutionResult =
    HttpExecutionResult(
      HttpExecutionResponseProjector.project(response, metadata, executionResponse),
      metadata
    )
}

private[http] object HttpExecutionResponseProjector {
  def project(
    response: HttpResponse,
    metadata: RuntimeContext.ExecutionMetadata,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): HttpResponse =
    executionresponse match {
      case None => response
      case Some(execution) =>
        val withmode = _replace(response, "X-Textus-Execution-Mode", execution.effectiveMode.toString)
        val withkind = _replace(withmode, "X-Textus-Execution-Result", execution.responseKind.transportValue)
        execution.responseKind match {
          case RuntimeContext.ExecutionResponseKind.Direct =>
            _remove(withkind, "X-Textus-Job-Id")
          case RuntimeContext.ExecutionResponseKind.AcceptedJob |
              RuntimeContext.ExecutionResponseKind.JobResult =>
            metadata.responseJobId.orElse(metadata.debugJobId).filter(_.nonEmpty) match {
              case Some(jobid) => _replace(withkind, "X-Textus-Job-Id", jobid)
              case None => _remove(withkind, "X-Textus-Job-Id")
            }
        }
    }

  private def _replace(
    response: HttpResponse,
    name: String,
    value: String
  ): HttpResponse =
    response.withHeader(Record(
      response.header.fields.filterNot(_.key.equalsIgnoreCase(name))
    ) ++ Record.data(name -> value))

  private def _remove(
    response: HttpResponse,
    name: String
  ): HttpResponse =
    response.withHeader(Record(response.header.fields.filterNot(_.key.equalsIgnoreCase(name))))
}

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
