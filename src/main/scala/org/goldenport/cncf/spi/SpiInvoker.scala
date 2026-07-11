package org.goldenport.cncf.spi

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Record, RecordFormat}
import org.goldenport.record.io.RecordSourceLoader

/*
 * Provider-neutral component operation invocation through the canonical CNCF
 * action execution path.
 *
 * @since   Jul. 11, 2026
 * @version Jul. 11, 2026
 * @author  ASAMI, Tomoharu
 */
trait SpiInvoker {
  def invoke(
    binding: ResolvedSpiBinding,
    operation: SpiOperationSelector,
    request: Record
  )(using ExecutionContext): Consequence[Record]

  def invoke[S](
    contract: SpiContract[S],
    operation: SpiOperationSelector,
    request: Record,
    selector: ComponentSelector = ComponentSelector(),
    socket: Option[SpiSocketRef] = None
  )(using ExecutionContext): Consequence[Record]
}

object SpiInvoker {
  private[cncf] def _create(subsystem: Subsystem): SpiInvoker =
    RuntimeSpiInvoker(subsystem)

  private final case class RuntimeSpiInvoker(
    subsystem: Subsystem
  ) extends SpiInvoker {
    def invoke(
      binding: ResolvedSpiBinding,
      operation: SpiOperationSelector,
      request: Record
    )(using ExecutionContext): Consequence[Record] = {
      val metadata = SpiTraceMetadata(
        contract = binding.provider.contract,
        operation = operation.operation,
        socketComponent = binding.socket.map(_.component).getOrElse("component-api-resolver"),
        providerComponent = binding.provider.component,
        socketName = binding.socket.map(_.name),
        providerInstance = Some(binding.provider.instanceId.instance),
        selectorPurpose = binding.selector.purpose,
        selectorCapabilities = binding.selector.capabilities,
        selectorTags = binding.selector.tags,
        selectionProvider = binding.selection.provider,
        selectionMode = binding.selection.mode,
        selectionEngine = binding.selection.engine
      )
      SpiTraceSupport.trace(metadata, (record: Record) => Map(
        "result_type" -> "record",
        "field_count" -> record.fields.size.toString
      )) {
        subsystem
          ._invoke_spi(binding, operation, request)
          .flatMap(SpiOperationResponseCodec.toRecord)
      }
    }

    def invoke[S](
      contract: SpiContract[S],
      operation: SpiOperationSelector,
      request: Record,
      selector: ComponentSelector,
      socket: Option[SpiSocketRef]
    )(using ExecutionContext): Consequence[Record] =
      subsystem.componentApiResolver
        .resolveBinding(contract, selector, socket)
        .flatMap(invoke(_, operation, request))
  }
}

object SpiOperationResponseCodec {
  def toRecord(response: OperationResponse): Consequence[Record] =
    response match {
      case OperationResponse.RecordResponse(record) =>
        Consequence.success(record)
      case scalar: OperationResponse.Scalar[?] =>
        Consequence.success(Record.dataAuto("value" -> scalar.value))
      case _: OperationResponse.Void =>
        Consequence.success(Record.empty)
      case OperationResponse.Json(json) =>
        _load(json.noSpaces, RecordFormat.Json, "JSON")
      case OperationResponse.Yaml(yaml) =>
        _load(yaml, RecordFormat.Yaml, "YAML")
      case _: OperationResponse.Http =>
        Consequence.operationInvalid("HTTP operation response cannot be converted to an SPI Record")
      case _: OperationResponse.Opaque =>
        Consequence.operationInvalid("opaque operation response cannot be converted to an SPI Record")
    }

  private def _load(
    body: String,
    format: RecordFormat,
    label: String
  ): Consequence[Record] =
    RecordSourceLoader.load(body, format).recoverWith { conclusion =>
      Consequence.operationInvalid(s"$label operation response is not a Record: ${conclusion.display}")
    }
}
