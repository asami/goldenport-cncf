package org.goldenport.cncf.spi.ai.runner

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{SpiContract, SpiSelection, SpiSocket}
import org.goldenport.protocol.Property
import org.goldenport.schema.DataConfidentiality

/*
 * Provider-neutral AI runner SPI contract.
 *
 * Textus AI and other AI-capable components publish implementations of this
 * SPI; consumer components depend only on this CNCF-owned protocol.
 *
 * @since   Jul.  2, 2026
 * @version Jul.  9, 2026
 * @author  ASAMI, Tomoharu
 */
trait AiRunner {
  def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse]
  def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse]
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

final case class AiRunnerRequirement(
  provider: Option[String] = None,
  mode: Option[String] = None,
  engine: Option[String] = None,
  // Purpose and model are per-call hints, so one component can mix cheap
  // worker calls and expensive judge calls through the same AI runner socket.
  purpose: Option[String] = None,
  model: Option[String] = None,
  tools: Vector[AiTool] = Vector.empty
)

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
