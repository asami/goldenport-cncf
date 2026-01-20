package org.goldenport.cncf.component.builtin.client

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.{Component, ComponentInit}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import java.net.URL
import java.nio.charset.StandardCharsets

/*
 * @since   Jan. 10, 2026
 * @version Jan. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ClientComponent() extends Component {
}

object ClientComponent {
  val name: String = "client"
  val componentId = ComponentId(name) // TODO static

  object Factory extends Component.Factory {
    protected def create_Components(params: ComponentCreate): Vector[Component] = {
      Vector(_client())
    }

    private def _client(): Component = {
      ClientComponent()
    }

    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = {
      val request = spec.RequestDefinition()
      val response = spec.ResponseDefinition()
      val opget = new ClientGetOperationDefinition(request, response)
      val oppost = new ClientPostOperationDefinition(request, response)
      val service = spec.ServiceDefinition(
        name = "http",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opget, oppost)
        )
      )
      val services = spec.ServiceDefinitionGroup(
        services = Vector(service)
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

  private final class ClientGetOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "get",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      _path(req).map { path =>
        val baseurl = _baseurl(req)
        val url = _build_url(baseurl, path)
        new GetQuery(
          req,
          // "system.ping",
          HttpRequest.fromUrl(HttpRequest.GET, new URL(url))
        )
      }
    }
  }

  private final class ClientPostOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "post",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      _path(req).map { path =>
        val baseurl = _baseurl(req)
        val url = _build_url(baseurl, path)
        val body = _body(req)
        new PostCommand(
          req,
          // "system.ping",
          HttpRequest.fromUrl(
            method = HttpRequest.POST,
            url = new URL(url),
            body = body
          )
        )
      }
    }
  }

  private def _baseurl(req: Request): String =
    req.properties.find(_.name == "baseurl").map(_.value.toString)
      .getOrElse(ClientConfig.DefaultBaseUrl)

  private def _path(req: Request): Consequence[String] =
    req.arguments.find(_.name == "path").map(_.value.toString) match {
      case Some(path) => Consequence.success(path)
      case None => Consequence.failure("path argument is required")
    }

  private def _build_url(baseurl: String, path: String): String = {
    val base = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
    val suffix = if (path.startsWith("/")) path else s"/${path}"
    s"${base}${suffix}"
  }

  private def _body(req: Request): Option[Bag] =
    req.properties.find(_.name == "-d").flatMap(_body_from_property_)

  private def _body_from_property_(p: Property): Option[Bag] = {
    val charset = StandardCharsets.UTF_8
    p.value match {
      case b: Bag => Some(b)
      case s: String => Some(Bag.text(s, charset))
      case _ => None
    }
  }
}
