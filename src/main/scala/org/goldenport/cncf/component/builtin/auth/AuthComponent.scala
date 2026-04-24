package org.goldenport.cncf.component.builtin.auth

import cats.data.NonEmptyVector
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext, SubjectKind}
import org.goldenport.cncf.security.{AuthenticationProviderRuntime, AuthenticationRequest, AuthenticationResult}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.DataType

/*
 * @since   Apr. 23, 2026
 * @version Apr. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class AuthComponent() extends Component {
}

object AuthComponent {
  final case class SessionSummary(
    sessionId: Option[String],
    principalId: Option[String],
    subjectKind: String,
    securityLevel: String,
    capabilities: Vector[String],
    authenticated: Boolean,
    attributes: Map[String, String] = Map.empty
  ) {
    def toRecord: Record =
      Record.data(
        "sessionId" -> sessionId.orNull,
        "principalId" -> principalId.orNull,
        "subjectKind" -> subjectKind,
        "securityLevel" -> securityLevel,
        "capabilities" -> capabilities,
        "authenticated" -> authenticated,
        "attributes" -> Record.data(attributes.toVector.sortBy(_._1)*)
      )

    def toJson: Json =
      Json.obj(
        "sessionId" -> sessionId.map(Json.fromString).getOrElse(Json.Null),
        "principalId" -> principalId.map(Json.fromString).getOrElse(Json.Null),
        "subjectKind" -> Json.fromString(subjectKind),
        "securityLevel" -> Json.fromString(securityLevel),
        "capabilities" -> Json.arr(capabilities.map(Json.fromString)*),
        "authenticated" -> Json.fromBoolean(authenticated),
        "attributes" -> Json.obj(attributes.toVector.sortBy(_._1).map { case (k, v) =>
          k -> Json.fromString(v)
        }*)
      )
  }

  final case class LogoutSummary(
    loggedOut: Boolean,
    sessionId: Option[String]
  ) {
    def toRecord: Record =
      Record.data(
        "loggedOut" -> loggedOut,
        "sessionId" -> sessionId.orNull
      )
  }

  trait AuthService {
    def login(request: AuthenticationRequest)(using ExecutionContext): Consequence[SessionSummary]
    def logout(request: AuthenticationRequest)(using ExecutionContext): Consequence[LogoutSummary]
    def currentSession(request: AuthenticationRequest)(using ExecutionContext): Consequence[SessionSummary]
  }

  val name: String = "auth"
  val componentId: ComponentId = ComponentId(name)

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component =
      AuthComponent()

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = spec.RequestDefinition()
      val response = spec.ResponseDefinition(result = List(DataType.Named("Record")))
      val login = new AuthOperationDefinition("login", request, response, comp)
      val logout = new AuthOperationDefinition("logout", request, response, comp)
      val session = new AuthOperationDefinition("session", request, response, comp)
      val authService = spec.ServiceDefinition(
        name = "auth",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(login, logout, session)
        )
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(services = Vector(authService)),
        handler = ProtocolHandler.default
      )
      comp.withPort(Component.Port.of(new DefaultAuthService(comp)))
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(name, componentId, instanceid, protocol)
    }
  }

  private final class DefaultAuthService(component: Component) extends AuthService {
    def login(request: AuthenticationRequest)(using ExecutionContext): Consequence[SessionSummary] =
      AuthenticationProviderRuntime.login(component.logic.executionContext(), request).flatMap {
        case Some(result) =>
          result.session.flatMap(_.sessionId) match {
            case Some(_) => Consequence.success(_session_summary_(result))
            case None => Consequence.operationIllegal("auth.login", "authenticated session is missing session id")
          }
        case None =>
          Consequence.securityPermissionDenied("No authentication provider accepted the login request.")
      }

    def logout(request: AuthenticationRequest)(using ExecutionContext): Consequence[LogoutSummary] =
      AuthenticationProviderRuntime.logout(component.logic.executionContext(), request).map {
        case Some(session) => LogoutSummary(loggedOut = true, sessionId = session.sessionId.orElse(request.sessionId))
        case None => LogoutSummary(loggedOut = false, sessionId = request.sessionId)
      }

    def currentSession(request: AuthenticationRequest)(using ExecutionContext): Consequence[SessionSummary] =
      AuthenticationProviderRuntime.current_session(component.logic.executionContext(), request).map {
        case Some(result) => _session_summary_(result)
        case None => _anonymous_summary_
      }

    private def _session_summary_(
      result: AuthenticationResult
    ): SessionSummary =
      SessionSummary(
        sessionId = result.session.flatMap(_.sessionId).orElse(result.session.flatMap(_.tokenId)),
        principalId = Some(result.principalId.value),
        subjectKind = result.subjectKind.toString,
        securityLevel = result.level.value,
        capabilities = result.capabilities.toVector.map(_.name).sorted,
        authenticated = true,
        attributes = _session_attributes_(result.attributes)
      )

    private lazy val _anonymous_summary_ =
      SessionSummary(
        sessionId = None,
        principalId = Some("anonymous"),
        subjectKind = SubjectKind.Anonymous.toString,
        securityLevel = SecurityContext.Privilege.Anonymous.level.value,
        capabilities = SecurityContext.Privilege.Anonymous.capabilities.toVector.map(_.name).sorted,
        authenticated = false,
        attributes = Map.empty
      )

    private def _session_attributes_(
      attributes: Map[String, String]
    ): Map[String, String] =
      Option(attributes).getOrElse(Map.empty).filterNot { case (k, _) =>
        val lower = k.toLowerCase(java.util.Locale.ROOT)
        lower == "principalid" ||
        lower == "principal_id" ||
        lower == "authenticated" ||
        lower == "access_token" ||
        lower == "refreshtoken" ||
        lower == "refresh_token" ||
        lower == "clientid" ||
        lower == "client_id" ||
        lower == "deviceinfo" ||
        lower == "device_info" ||
        lower == "ipaddress" ||
        lower == "ip_address" ||
        lower == "useragent" ||
        lower == "user_agent"
      }
  }

  private final class AuthOperationDefinition(
    opname: String,
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    component: Component
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = opname,
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(
        if (opname == "session")
          AuthQueryAction(req, opname, component)
        else
          AuthCommandAction(req, opname, component)
      )
  }

  private final case class AuthCommandAction(
    request: Request,
    opname: String,
    comp: Component
  ) extends CommandAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      AuthCall(core, opname, comp)
  }

  private final case class AuthQueryAction(
    request: Request,
    opname: String,
    comp: Component
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      AuthCall(core, opname, comp)
  }

  private final case class AuthCall(
    core: ActionCall.Core,
    opname: String,
    comp: Component
  ) extends ActionCall {
    def execute(): Consequence[OperationResponse] = {
      val authservice = comp.port.get[AuthService].getOrElse(Consequence.RAISE.UnreachableReached)
      val request = AuthenticationRequest(_attributes_(core.action.request))
      given ExecutionContext = core.executionContext
      opname match {
        case "login" =>
          authservice.login(request).map(x => OperationResponse.RecordResponse(x.toRecord))
        case "logout" =>
          authservice.logout(request).map(x => OperationResponse.RecordResponse(x.toRecord))
        case "session" =>
          authservice.currentSession(request).map(x => OperationResponse.RecordResponse(x.toRecord))
        case other =>
          Consequence.operationInvalid(s"unsupported auth operation: $other")
      }
    }
  }

  private def _attributes_(
    request: Request
  ): Map[String, String] = {
    val props = request.properties.foldLeft(Map.empty[String, String]) { (z, p) =>
      val value = Option(p.value).map(_.toString).getOrElse("")
      if (p.name.nonEmpty && value.nonEmpty) z.updated(p.name, value) else z
    }
    request.arguments.foldLeft(props) { (z, p) =>
      val value = Option(p.value).map(_.toString).getOrElse("")
      if (p.name.nonEmpty && value.nonEmpty) z.updated(p.name, value) else z
    }
  }
}
