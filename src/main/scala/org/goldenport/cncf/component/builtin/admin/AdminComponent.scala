package org.goldenport.cncf.component.builtin.admin

import cats.data.NonEmptyVector
import java.nio.file.Paths
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall, Query, ResourceAccess}
import org.goldenport.cncf.component.{Component, ComponentInit}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.component.ComponentLogic
import org.goldenport.configuration.ConfigurationResolver
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.configuration.ConfigurationSources
import org.goldenport.configuration.ConfigurationOrigin
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec

/*
 * @since   Jan.  7, 2026
 * @version Jan. 20, 2026
 * @author  ASAMI, Tomoharu
 */
class AdminComponent() extends Component {
}

object AdminComponent {
  val name: String = "admin"
  val componentId = ComponentId(name) // TODO static

  object Factory extends Component.Factory {
    protected def create_Components(params: ComponentCreate): Vector[Component] = {
      Vector(AdminComponent())
    }

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = spec.RequestDefinition()
      val response = spec.ResponseDefinition()
      val opPing = new PingOperationDefinition(request, response)
      val opComponentList = new ComponentListOperationDefinition(
        request,
        response,
        params.subsystem
      )
      val opConfigShow = new ConfigShowOperationDefinition(
        request,
        response,
        params.subsystem
      )
      val opVariationList = new VariationListOperationDefinition(
        request,
        response,
        params.subsystem
      )
      val opExtensionList = new ExtensionListOperationDefinition(
        request,
        response,
        params.subsystem
      )
      val serviceSystem = spec.ServiceDefinition(
        name = "system",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opPing)
        )
      )
      val serviceComponent = spec.ServiceDefinition(
        name = "component",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opComponentList)
        )
      )
      val serviceConfig = spec.ServiceDefinition(
        name = "config",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opConfigShow)
        )
      )
      val serviceVariation = spec.ServiceDefinition(
        name = "variation",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opVariationList)
        )
      )
      val serviceExtension = spec.ServiceDefinition(
        name = "extension",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opExtensionList)
        )
      )
      val services = spec.ServiceDefinitionGroup(
        services = Vector(
          serviceSystem,
          serviceComponent,
          serviceConfig,
          serviceVariation,
          serviceExtension
        )
      )
      val protocol = Protocol(
        services = services,
        handler = ProtocolHandler(
          ingresses = IngressCollection(Vector(RestIngress())),
          egresses = EgressCollection(Vector(RestEgress())),
          projections = ProjectionCollection()
        )
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(
        name,
        componentId,
        instanceid,
        protocol
      )
    }
  }

  private final class PingOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "ping",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      val _ = req
      Consequence.success(ComponentLogic.PingAction(req))
    }
  }

  private final class ComponentListOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      Consequence.success(ComponentListAction(req, subsystem))
    }
  }

  private final class VariationListOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      val _ = req
      Consequence.success(VariationListAction(req, subsystem))
    }
  }

  private final class ExtensionListOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      Consequence.success(ExtensionListAction(req, subsystem))
    }
  }

  private final class ConfigShowOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "show",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      Consequence.success(ConfigShowAction(req, subsystem))
    }
  }

  private final case class ComponentListAction(
    request: Request,
    subsystem: Subsystem
  ) extends Query() {
//    val name = "component.list"

    def createCall(core: ActionCall.Core): ActionCall =
      ComponentListActionCall(core, subsystem)
  }

  private final case class ComponentListActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val comps = subsystem.components
      val text = _component_lines_(comps, "Components")
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private final case class ConfigShowAction(
    request: Request,
    subsystem: Subsystem
  ) extends Query() {
//    val name = "config.show"

    def createCall(core: ActionCall.Core): ActionCall =
      ConfigShowActionCall(core, subsystem)
  }

  private final case class ConfigShowActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      _config_snapshot_().map { text =>
        OperationResponse.Scalar(text)
      }
    }
  }

  private final case class VariationListAction(
    request: Request,
    subsystem: Subsystem
  ) extends Query() {
//    val name = "variation.list"

    def createCall(core: ActionCall.Core): ActionCall =
      VariationListActionCall(core, subsystem)
  }

  private final case class VariationListActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      _config_snapshot_().map { text =>
        OperationResponse.Scalar(_variation_lines_(text))
      }
    }
  }

  private final case class ExtensionListAction(
    request: Request,
    subsystem: Subsystem
  ) extends Query() {
//    val name = "extension.list"

    def createCall(core: ActionCall.Core): ActionCall =
      ExtensionListActionCall(core, subsystem)
  }

  private final case class ExtensionListActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    override def action: Action = core.action

    def execute(): Consequence[OperationResponse] = {
      val comps = subsystem.components
      val text = _extension_lines_(comps)
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private def _component_origin_(comp: Component): String = {
    comp.origin.label
  }

  private def _component_lines_(
    comps: Seq[Component],
    header: String
  ): String = {
    val lines = Vector.newBuilder[String]
    lines += s"${header} (total: ${comps.size})"
    lines += ""
    comps.foreach { comp =>
      val origin = _component_origin_(comp)
      lines += s"- ${comp.name}"
      lines += s"  class : ${comp.getClass.getName}"
      lines += s"  origin: ${origin}"
      lines += ""
    }
    lines.result().mkString("\n").trim
  }

  private def _config_snapshot_(): Consequence[String] = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val sources = ConfigurationSources.standard(cwd)
    ConfigurationResolver.default.resolve(sources).map { resolved =>
      val lines = Vector.newBuilder[String]
      lines += "Config Snapshot"
      lines += ""
      resolved.configuration.values.toVector.sortBy(_._1).foreach {
        case (key, value) =>
          val source = resolved.trace.get(key).map(r => _origin_(r.origin)).getOrElse("unknown")
          lines += s"${key} = ${_value_(value)}"
          lines += s"  source: ${source}"
          lines += ""
      }
      lines.result().mkString("\n").trim
    }
  }

  private def _variation_lines_(
    configSnapshot: String
  ): String = {
    val lines = Vector.newBuilder[String]
    lines += "Variation Points"
    lines += ""
    configSnapshot.split("\n").foreach { line =>
      if (line.trim.nonEmpty && !line.startsWith("Config Snapshot")) {
        if (!line.startsWith("  ")) {
          val parts = line.split("=", 2)
          if (parts.length == 2) {
            val key = parts(0).trim
            val value = parts(1).trim
            lines += s"- ${key}"
            lines += s"  value : ${value}"
          }
        } else if (line.trim.startsWith("source:")) {
          lines += s"  ${line.trim}"
          lines += ""
        }
      }
    }
    lines.result().mkString("\n").trim
  }

  private def _extension_lines_(
    comps: Seq[Component]
  ): String = {
    val lines = Vector.newBuilder[String]
    lines += "Extension Points"
    lines += ""
    comps.foreach { comp =>
      val origin = _component_origin_(comp)
      lines += s"- component: ${comp.name}"
      lines += s"  class : ${comp.getClass.getName}"
      lines += s"  origin: ${origin}"
      lines += ""
    }
    lines.result().mkString("\n").trim
  }

  private def _value_(value: ConfigurationValue): String = {
    value match {
      case ConfigurationValue.StringValue(v) => v
      case ConfigurationValue.NumberValue(v) => v.toString
      case ConfigurationValue.BooleanValue(v) => v.toString
      case ConfigurationValue.ListValue(vs) => vs.map(_value_).mkString("[", ", ", "]")
      case ConfigurationValue.ObjectValue(vs) =>
        vs.map { case (k, v) => s"${k}=${_value_(v)}" }.mkString("{", ", ", "}")
      case ConfigurationValue.NullValue => "null"
    }
  }

  private def _origin_(origin: ConfigurationOrigin): String = {
    origin match {
      case ConfigurationOrigin.Arguments => "cli"
      case ConfigurationOrigin.Environment => "env"
      case ConfigurationOrigin.Default => "default"
      case ConfigurationOrigin.Home => "file"
      case ConfigurationOrigin.Project => "file"
      case ConfigurationOrigin.Cwd => "file"
      case ConfigurationOrigin.Resource => "resource"
    }
  }
}
