package org.goldenport.cncf.spi.ai.runner

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{ComponentSelector, SpiContract, SpiSelection, SpiSocket, SpiTraceMetadata, SpiTraceSupport, StandardSpiSocketSet}
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.goldenport.schema.DataConfidentiality

/*
 * Provider-neutral AI runner SPI contract.
 *
 * Textus AI and other AI-capable components publish implementations of this
 * SPI; consumer components depend only on this CNCF-owned protocol.
 *
 * @since   Jul.  2, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
trait AiRunner {
  def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse]
  def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse]
  def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse]
}

object AiRunner {
  def traced(
    underlying: AiRunner,
    metadata: SpiTraceMetadata
  ): AiRunner =
    _TracedAiRunner(underlying, metadata)

  private final case class _TracedAiRunner(
    underlying: AiRunner,
    base: SpiTraceMetadata
  ) extends AiRunner {
    def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
      SpiTraceSupport.trace(base.withOperation("generate"), _generate_attributes)(underlying.generate(req))

    def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
      SpiTraceSupport.trace(base.withOperation("generateRecord"), _record_attributes)(underlying.generateRecord(req))

    def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse] =
      SpiTraceSupport.trace(base.withOperation("chat"), _chat_attributes)(underlying.chat(req))
  }

  private def _generate_attributes(response: AiGenerateResponse): Map[String, String] =
    _clean(Map(
      "result_type" -> "ai_generate_response",
      "model" -> response.model.getOrElse("")
    ))

  private def _record_attributes(response: AiRecordResponse): Map[String, String] =
    _clean(Map(
      "result_type" -> "ai_record_response",
      "model" -> response.model.getOrElse(""),
      "field_count" -> response.record.fields.size.toString
    ))

  private def _chat_attributes(response: AiChatResponse): Map[String, String] =
    _clean(Map(
      "result_type" -> "ai_chat_response",
      "model" -> response.model.getOrElse(""),
      "role" -> response.message.role
    ))

  private def _clean(values: Map[String, String]): Map[String, String] =
    values.filter(_._2.nonEmpty)
}

trait AiRunnerSocket extends SpiSocket[AiRunner] {
  private var _ai_runner: Option[AiRunner] = None

  def aiRunner: AiRunner =
    _ai_runner.getOrElse(
      throw new IllegalStateException("AI runner SPI is not installed.")
    )

  def withAiRunner(spi: AiRunner): this.type = {
    _ai_runner = Some(spi)
    this
  }

  override def spiContract: SpiContract[AiRunner] =
    SpiContract("ai-runner", classOf[AiRunner])

  override def spiSelection: SpiSelection =
    SpiSelection()

  override def isSpiInstalled: Boolean =
    _ai_runner.nonEmpty

  override def installSpi(spi: AiRunner): Unit =
    withAiRunner(spi)
}

trait AiRunnerSocketSet extends StandardSpiSocketSet[AiRunner] {
  def aiRunner(
    selector: ComponentSelector = ComponentSelector()
  )(using ExecutionContext): Consequence[AiRunner] =
    resolve(selector)

  override def spiContract: SpiContract[AiRunner] =
    SpiContract("ai-runner", classOf[AiRunner])
}

final case class AiRunnerRequirement(
  provider: Option[String] = None,
  mode: Option[String] = None,
  engine: Option[String] = None,
  // Purpose and model are per-call hints, so one component can mix cheap
  // worker calls and expensive judge calls through the same AI runner socket.
  purpose: Option[String] = None,
  // A required purpose must be resolved by the selected runtime before it can
  // inherit any component-level provider selection.
  purposeRequired: Boolean = false,
  model: Option[String] = None,
  tools: Vector[AiTool] = Vector.empty,
  // Caller-selected execution intent. Providers and models remain runtime policy.
  executionClass: Option[AiExecutionClass] = None
)

enum AiExecutionClass(val id: String):
  case SimpleWork extends AiExecutionClass("simple-work")
  case StandardWork extends AiExecutionClass("standard-work")
  case StandardConsideration extends AiExecutionClass("standard-consideration")
  case DeepConsideration extends AiExecutionClass("deep-consideration")

object AiExecutionClass:
  def parse(s: String): Option[AiExecutionClass] =
    Option(s).map(_.trim.toLowerCase(java.util.Locale.ROOT)).flatMap {
      case "simple-work" => Some(SimpleWork)
      case "standard-work" => Some(StandardWork)
      case "standard-consideration" => Some(StandardConsideration)
      case "deep-consideration" => Some(DeepConsideration)
      case _ => None
    }

enum AiTool(val id: String):
  case UrlContext extends AiTool("url_context")
  case WebSearch extends AiTool("web_search")
  case Unknown(raw: String) extends AiTool(raw)

object AiTool:
  final case class ParseResult(
    tools: Vector[AiTool],
    unknown: Vector[String]
  )

  def parse(s: String): Option[AiTool] =
    Option(s).map(_.trim.toLowerCase(java.util.Locale.ROOT).replace('-', '_')).flatMap {
      case "url_context" | "urlcontext" | "url" => Some(UrlContext)
      case "web_search" | "websearch" | "google_search" | "googlesearch" | "search" => Some(WebSearch)
      case _ => None
    }

  def parseResult(s: String): ParseResult =
    val tokens =
      Option(s).toVector
        .flatMap(_.split("[,\\s]+").toVector)
        .map(_.trim)
        .filter(_.nonEmpty)
    val pairs = tokens.map(token => token -> parse(token))
    ParseResult(
      tools = pairs.map {
        case (_, Some(tool)) => tool
        case (token, None) => Unknown(token)
      }.distinct,
      unknown = pairs.collect { case (token, None) => token }.distinct
    )

  def parseList(s: String): Vector[AiTool] =
    parseResult(s).tools

final case class AiRunnerTracePolicy(
  promptConfidentiality: DataConfidentiality = DataConfidentiality.Internal,
  responseConfidentiality: DataConfidentiality = DataConfidentiality.Internal
) {
  def calltreePrompt(text: String): String =
    if (promptConfidentiality.shouldRedactByDefault) "***" else text

  def calltreeResponse(text: String): String =
    if (responseConfidentiality.shouldRedactByDefault) "***" else text
}

final case class AiGenerateRequest(
  prompt: String,
  temperature: Option[Double] = None,
  maxTokens: Option[Int] = None,
  requirement: AiRunnerRequirement = AiRunnerRequirement(),
  trace: AiRunnerTracePolicy = AiRunnerTracePolicy(),
  metadata: Map[String, String] = Map.empty,
  properties: Vector[Property] = Vector.empty
)

final case class AiGenerateResponse(
  text: String,
  model: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class AiRecordRequest(
  prompt: String,
  schema: Record,
  temperature: Option[Double] = None,
  maxTokens: Option[Int] = None,
  requirement: AiRunnerRequirement = AiRunnerRequirement(),
  trace: AiRunnerTracePolicy = AiRunnerTracePolicy(),
  metadata: Map[String, String] = Map.empty,
  properties: Vector[Property] = Vector.empty
)

final case class AiRecordResponse(
  record: Record,
  model: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class AiChatRequest(
  messages: Vector[AiMessage],
  temperature: Option[Double] = None,
  maxTokens: Option[Int] = None,
  requirement: AiRunnerRequirement = AiRunnerRequirement(),
  trace: AiRunnerTracePolicy = AiRunnerTracePolicy(),
  metadata: Map[String, String] = Map.empty,
  properties: Vector[Property] = Vector.empty
)

final case class AiChatResponse(
  message: AiMessage,
  model: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class AiMessage(
  role: String,
  content: String
)
