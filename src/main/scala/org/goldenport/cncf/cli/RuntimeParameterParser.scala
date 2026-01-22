package org.goldenport.cncf.cli

import org.goldenport.cncf.protocol.GlobalParameterGroup
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.ProtocolEngine
import org.goldenport.protocol.Request
import org.goldenport.protocol.spec.{RequestDefinition, ResponseDefinition}
import org.slf4j.LoggerFactory

/*
 * @since   Jan. 22, 2026
 * @version Jan. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuntimeParameterParseResult(
  consumed: Vector[String],
  residual: Vector[String],
  baseUrl: Option[String]
)

final class RuntimeParameterParser {
  private val _log = LoggerFactory.getLogger(classOf[RuntimeParameterParser])
  private val _serviceName = "runtime-parameters"
  private val _operationName = "parse"
  private val _runtimeRequestDefinition =
    RequestDefinition(parameters = GlobalParameterGroup.runtimeParameters.toList)
  private val _runtimeProtocol: Protocol =
    Protocol.Builder()
      .addOperation(_serviceName, _operationName, _runtimeRequestDefinition, ResponseDefinition())
      .build()
  private val _runtimeProtocolEngine = ProtocolEngine.create(_runtimeProtocol)
  private val _baseUrlParamNameOption =
    GlobalParameterGroup.runtimeParameters.find(_.name == "baseurl").map(_.name)

  def parse(
    args: Seq[String]
  ): RuntimeParameterParseResult = {
    val runtimeArgs = (_serviceName +: _operationName +: args).toArray
    _runtimeProtocolEngine.makeOperationRequest(runtimeArgs) match {
      case Consequence.Success(req) =>
        val result = req.request
        val residualVec = _residual(result)
        val consumedVec = _consumed(result)
        val baseUrlOpt = _base_url(result)
        if (_log.isTraceEnabled) {
          _log.trace(
            s"[client:parse] runtime args consumed=${consumedVec.mkString(" ")} residual=${residualVec.mkString(" ")}"
          )
        }
        RuntimeParameterParseResult(consumedVec, residualVec, baseUrlOpt)
      case Consequence.Failure(conclusion) =>
        _log.error(s"[client:parse] runtime parameter parsing failed: ${conclusion.message}")
        RuntimeParameterParseResult(Vector.empty, args.toVector, None)
    }
  }

  private def _residual(req: Request): Vector[String] =
    req.arguments.map(arg => Option(arg.value).map(_.toString).getOrElse("")).toVector

  private def _consumed(req: Request): Vector[String] = {
    val builder = Vector.newBuilder[String]
    req.switches.foreach { sw =>
      builder += s"--${sw.name}"
    }
    req.properties.foreach { prop =>
      builder += s"--${prop.name}"
      builder += Option(prop.value).map(_.toString).getOrElse("")
    }
    builder.result()
  }

  private def _base_url(req: Request): Option[String] =
    _baseUrlParamNameOption.flatMap { baseName =>
      req.properties.collectFirst {
        case prop if prop.name == baseName => Option(prop.value).map(_.toString).getOrElse("")
      }
    }
}
