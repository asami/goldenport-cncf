package org.goldenport.cncf.http

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.util.Locale
import scala.util.control.NonFatal
import cats.effect.IO
import cats.free.Free
import cats.syntax.all.*
import io.circe.Json
import org.http4s.{Charset, Header, HttpRoutes, MediaType, Method, Request, Response, ResponseCookie, SameSite, Status}
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.oauth.*
import org.goldenport.cncf.observability.ConclusionDisclosure
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.http.{HttpContext, HttpRequest}
import org.goldenport.record.{Field, Record}
import org.goldenport.record.io.RecordEncoder

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
/** Listener-local transport for the closed native capability; suppliers remain trusted host wiring. */
private[http] final class OAuthConnectionHttp private[http] (
  selectcomponent: String => Option[Component],
  managementcontext: (Request[IO], Component) => Consequence[ExecutionContext],
  callbackcontext: Component => Consequence[ExecutionContext],
  operationmode: OperationMode
) {
  import OAuthConnectionHttp.*

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req if _is_prefix(req) => _dispatch(req)
  }

  private def _is_prefix(req: Request[IO]): Boolean = {
    val segments = req.uri.path.segments.map(_.decoded()).toVector
    segments.size >= 3 && segments.head == "web" && segments(2) == "connections"
  }

  private def _dispatch(req: Request[IO]): IO[Response[IO]] = {
    val segments = req.uri.path.segments.map(_.decoded()).toVector
    val selected = for {
      component <- selectcomponent(segments(1))
      runtime <- component match {
        case capability: OAuthConnectionRuntime => Some(capability)
        case _ => None
      }
      base = s"/web/${NamingConventions.toNormalizedSegment(component.componentId.localId.value())}/connections"
      raw = req.uri.path.renderString
      if raw == base || raw.startsWith(base + "/")
    } yield new Selection(component, runtime, base)
    selected match {
      case None => IO.pure(_fixed(req, 404, "OAuth connection route is unavailable"))
      case Some(selection) =>
        val path = req.uri.path.renderString
        val configuration = selection.runtime.oauthConnection.map(_.configuration)
        val action = if (path == selection.base) Some("list")
          else configuration.flatMap { config =>
            val prefix = selection.base + "/" + config.connectionId
            Vector("status", "authorize", "callback", "disconnect").find(name => path == prefix + "/" + name)
          }
        action match {
          case None => IO.pure(_fixed(req, 404, "OAuth connection route is unavailable"))
          case Some(name) =>
            val required = if (name == "authorize" || name == "disconnect") Method.POST else Method.GET
            if (req.method != required) IO.pure(_fixed(req, 405, "OAuth connection method is not allowed", path))
            else _admit_host(req, configuration) match {
              case Consequence.Failure(conclusion) => IO.pure(_error(req, conclusion, path, None, Vector.empty))
              case Consequence.Success(_) if name == "callback" => _callback(req, selection, configuration.get)
              case Consequence.Success(_) => _management(req, selection, configuration, name, path)
            }
        }
    }
  }

  private def _management(req: Request[IO], selection: Selection,
    configuration: Option[OAuthConnectionConfiguration], action: String, path: String): IO[Response[IO]] = {
    val query = _query(req).filter(_.forall(pair => _selectors(pair._1)))
    query match {
      case None => IO.pure(_error(req, _invalid, path, None, Vector.empty))
      case Some(pairs) =>
        val origin = if (req.method == Method.POST)
          configuration.map(config => _admit_origin(req, config)).getOrElse(Consequence.unit)
          else Consequence.unit
        origin match {
          case Consequence.Failure(conclusion) => IO.pure(_error(req, conclusion, path, None, pairs))
          case Consequence.Success(_) =>
            val empty = if (req.method == Method.POST)
              req.body.take(1).compile.toVector.map(_.isEmpty).handleError(_ => false)
              else IO.pure(true)
            empty.flatMap { admitted =>
              if (!admitted) IO.pure(_error(req, _invalid, path, None, pairs))
              else IO.blocking(managementcontext(req, selection.component)).flatMap {
                case Consequence.Failure(conclusion) => IO.pure(_error(req, conclusion, path, None, pairs))
                case Consequence.Success(context) =>
                  configuration match {
                    case None =>
                      val subject = SecuritySubject.from(context.security)
                      if (subject.isAuthenticated && subject.isProviderAuthenticated)
                        IO.pure(_json(Json.obj("connections" -> Json.arr())))
                      else IO.pure(_error(req, _permission, path, Some(context), pairs))
                    case Some(config) => action match {
                      case "authorize" => _run(context, OAuthConnectionRequest.begin(config.connectionId)).map {
                        case Consequence.Success(handoff) =>
                          _json(Json.obj("authorizationUri" -> Json.fromString(handoff.authorizationUri)))
                            .addCookie(_cookie(config, handoff.browserProof, 300))
                        case Consequence.Failure(conclusion) => _error(req, conclusion, path, Some(context), pairs)
                      }
                      case _ =>
                        val request = if (action == "disconnect") OAuthConnectionRequest.forget(config.connectionId)
                          else OAuthConnectionRequest.status(config.connectionId)
                        _run(context, request).map {
                          case Consequence.Success(status) =>
                            val value = _status(config, status)
                            _json(if (action == "list") Json.obj("connections" -> Json.arr(value)) else value)
                          case Consequence.Failure(conclusion) => _error(req, conclusion, path, Some(context), pairs)
                        }
                    }
                  }
              }
            }
        }
    }
  }

  private def _callback(req: Request[IO], selection: Selection,
    configuration: OAuthConnectionConfiguration): IO[Response[IO]] = {
    IO.blocking(callbackcontext(selection.component)).flatMap {
      case Consequence.Failure(_) => IO.pure(_redirect(configuration))
      case Consequence.Success(context) =>
        val callback = for {
          fields <- _query(req)
          state <- _one(fields, "state").filter(_marker)
          code = _one(fields, "code")
          error = _one(fields, "error")
          if fields.count(_._1 == "state") == 1 && fields.count(_._1 == "code") <= 1 && fields.count(_._1 == "error") <= 1
          if code.isDefined != error.isDefined
          if code.forall(_printable(_, 8192)) && error.forall(_printable(_, 128))
          proof <- _proof(req)
        } yield OAuthConnectionRequest.complete(configuration.connectionId, state, proof, code, error)
        callback match {
          case None =>
            _log(context, _invalid, req, configuration.callbackPath, Vector.empty)
            IO.pure(_redirect(configuration))
          case Some(request) => _run(context, request).map {
            case Consequence.Success(_) => _redirect(configuration).addCookie(_cookie(configuration, "", 0))
            case Consequence.Failure(conclusion) =>
              _log(context, conclusion, req, configuration.callbackPath, Vector.empty)
              _redirect(configuration)
          }
        }
    }
  }

  private def _run[A](context: ExecutionContext, request: OAuthConnectionRequest[A]): IO[Consequence[A]] = IO.blocking {
    val uow = context.runtime.unitOfWork
    val program: ExecUowM[A] = ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, A](UnitOfWorkOp.OAuthConnectionExecute(request)))
    try new UnitOfWorkInterpreter(uow).run(program)
    catch { case NonFatal(exception) => Consequence.Failure(Conclusion.from(exception)) }
  }

  private def _admit_host(req: Request[IO], configuration: Option[OAuthConnectionConfiguration]): Consequence[Unit] = {
    val host = _single(req, "Host", 1024).flatMap(_authority)
    val expected = configuration.flatMap(config => _origin(config.configuredOrigin))
    val admitted = host.exists { authority =>
      val scheme = expected.map(_.scheme).getOrElse("https")
      val actual = new Origin(scheme, authority.host, _effective_port(scheme, authority.port))
      val matches = expected.forall(_same_origin(_, actual))
      val uri = req.uri
      val schemeagrees = uri.scheme.forall(_.value.equalsIgnoreCase(scheme))
      val authorityagrees = uri.authority.forall { value =>
        _authority(value.renderString).exists(other =>
          _same_origin(actual, new Origin(scheme, other.host, _effective_port(scheme, other.port))))
      }
      matches && schemeagrees && authorityagrees && uri.fragment.isEmpty
    }
    if (admitted && (configuration.isEmpty || expected.nonEmpty)) Consequence.unit
    else Consequence.Failure(_permission)
  }

  private def _admit_origin(req: Request[IO], configuration: OAuthConnectionConfiguration): Consequence[Unit] = {
    val admitted = for {
      expected <- _origin(configuration.configuredOrigin)
      supplied <- _single(req, "Origin", 2048).flatMap(_origin)
    } yield _same_origin(expected, supplied)
    if (admitted.contains(true)) Consequence.unit else Consequence.Failure(_permission)
  }

  private def _single(req: Request[IO], name: String, limit: Int): Option[String] =
    req.headers.headers.filter(_.name.toString.equalsIgnoreCase(name)).map(_.value).toVector match {
      case Vector(value) if value.nonEmpty && value.length <= limit && value.forall(c => c >= '!' && c <= '~') => Some(value)
      case _ => None
    }

  private def _authority(value: String): Option[Authority] = {
    try {
      val uri = new URI("https://" + value)
      val host = Option(uri.getHost)
      val port = uri.getPort
      val plain = value.matches("(?:[A-Za-z0-9.-]+|\\[[0-9A-Fa-f:]+\\])(?::[0-9]{1,5})?")
      Option.when(plain && host.nonEmpty && uri.getRawUserInfo == null && uri.getRawPath.isEmpty &&
        uri.getRawQuery == null && uri.getRawFragment == null && (port == -1 || port >= 1 && port <= 65535))(
        new Authority(host.get.toLowerCase(Locale.ROOT), port))
    } catch { case NonFatal(_) => None }
  }

  private def _origin(value: String): Option[Origin] = {
    try {
      val uri = new URI(value)
      val scheme = Option(uri.getScheme).map(_.toLowerCase(Locale.ROOT))
      val authority = Option(uri.getRawAuthority).flatMap(_authority)
      for {
        selected <- scheme.filter(name => name == "https" || name == "http")
        host <- authority
        if value.forall(c => c >= '!' && c <= '~') && uri.getRawPath.isEmpty &&
          uri.getRawQuery == null && uri.getRawFragment == null && uri.getRawUserInfo == null
        if selected == "https" || host.host == "127.0.0.1" && host.port >= 1
      } yield new Origin(selected, host.host, _effective_port(selected, host.port))
    } catch { case NonFatal(_) => None }
  }

  private def _same_origin(left: Origin, right: Origin): Boolean =
    left.scheme == right.scheme && left.host == right.host && left.port == right.port

  private def _effective_port(scheme: String, port: Int): Int =
    if (port >= 0) port else if (scheme == "https") 443 else 80

  private def _query(req: Request[IO]): Option[Vector[(String, String)]] = {
    val raw = req.uri.query.renderString
    if (raw.length > 32768 || !raw.forall(c => c >= '!' && c <= '~')) None
    else if (raw.isEmpty) Some(Vector.empty)
    else {
      val fields = raw.split("&", -1).toVector
      if (fields.size > 32) None
      else fields.foldLeft(Option(Vector.empty[(String, String)])) { (result, part) =>
        val pair = part.split("=", 2)
        for {
          previous <- result
          key <- _decode(pair.head).filter(value => value.nonEmpty && value.length <= 256)
          value <- _decode(pair.lift(1).getOrElse("")).filter(_.length <= 8192)
        } yield previous :+ (key -> value)
      }
    }
  }

  private def _decode(value: String): Option[String] = {
    try {
      val bytes = new ByteArrayOutputStream
      var index = 0
      while (index < value.length) {
        val char = value.charAt(index)
        if (char == '%') {
          if (index + 2 >= value.length) return None
          val high = Character.digit(value.charAt(index + 1), 16)
          val low = Character.digit(value.charAt(index + 2), 16)
          if (high < 0 || low < 0) return None
          bytes.write(high * 16 + low)
          index += 3
        } else {
          bytes.write(if (char == '+') ' '.toInt else char.toInt)
          index += 1
        }
      }
      val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray)).toString
      Option.when(!text.exists(c => c < ' ' || c == 127.toChar))(text)
    } catch { case NonFatal(_) => None }
  }

  private def _one(fields: Vector[(String, String)], key: String): Option[String] =
    fields.collect { case (name, value) if name == key => value } match {
      case Vector(value) => Some(value)
      case _ => None
    }

  private def _proof(req: Request[IO]): Option[String] = {
    val headers = req.headers.headers.filter(_.name.toString.equalsIgnoreCase("Cookie")).map(_.value).toVector
    if (headers.map(_.length.toLong).sum + math.max(0, headers.size - 1) > 16384 || headers.size > 64) None
    else {
      val parts = headers.flatMap(_.split(";", -1).toVector)
      if (parts.isEmpty || parts.size > 64) None
      else parts.foldLeft(Option(Vector.empty[(String, String)])) { (result, part) =>
        val pair = part.dropWhile(char => char == ' ' || char == '\t').split("=", 2)
        for {
          previous <- result
          if pair.length == 2 && pair(0).matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") &&
            pair(1).forall(c => c == '!' || c >= '#' && c <= '+' || c >= '-' && c <= ':' ||
              c >= '<' && c <= '[' || c >= ']' && c <= '~')
        } yield previous :+ (pair(0) -> pair(1))
      }.flatMap(fields => _one(fields, _cookie_name)).filter(_marker)
    }
  }

  private def _marker(value: String): Boolean = value.matches("[A-Za-z0-9_-]{43,128}")
  private def _printable(value: String, limit: Int): Boolean =
    value.nonEmpty && value.length <= limit && value.forall(c => c >= '!' && c <= '~')

  private def _status(configuration: OAuthConnectionConfiguration, status: OAuthConnectionStatus): Json = {
    val state = status.state match {
      case OAuthConnectionState.Connected => "connected"
      case OAuthConnectionState.AuthorizationRequired => "authorization-required"
      case OAuthConnectionState.Expired => "expired"
      case OAuthConnectionState.Uncertain => "uncertain"
    }
    val fields = Vector("connectionId" -> Json.fromString(configuration.connectionId),
      "provider" -> Json.fromString(if (configuration.providerKind == OAuthConnectionProviderKind.GoogleDrive) "google-drive" else "generic"),
      "state" -> Json.fromString(state), "canRefresh" -> Json.fromBoolean(status.canRefresh),
      "authorizationPending" -> Json.fromBoolean(status.authorizationPending), "updating" -> Json.fromBoolean(status.updating)) ++
      status.expiresAt.toVector.map(value => "expiresAt" -> Json.fromString(value.toString)) ++
      status.lastOutcome.toVector.map(value => "lastOutcome" -> Json.fromString(value.toString.toLowerCase(Locale.ROOT)))
    Json.obj(fields*)
  }

  private def _cookie(configuration: OAuthConnectionConfiguration, proof: String, age: Long): ResponseCookie =
    ResponseCookie(_cookie_name, proof, path = Some(configuration.callbackPath), maxAge = Some(age),
      secure = configuration.configuredOrigin.regionMatches(true, 0, "https://", 0, 8), httpOnly = true,
      sameSite = Some(SameSite.Lax))

  private def _redirect(configuration: OAuthConnectionConfiguration): Response[IO] =
    _secured(Response[IO](Status.SeeOther).putHeaders(Header.Raw(CIString("Location"), configuration.returnPath + "#oauth-returned")))

  private def _physical(req: Request[IO], path: String, query: Vector[(String, String)]): HttpRequest = {
    val normalized = Record(req.headers.headers.filterNot(header => header.name.toString.equalsIgnoreCase("Cookie") ||
      ConclusionDisclosure.isCookieMetadata(header.name.toString)).map(header => Field(header.name.toString, Field.Value.Single(header.value))).toVector)
    val headers = ConclusionDisclosure.withCookieCredentials(normalized,
      req.headers.headers.filter(_.name.toString.equalsIgnoreCase("Cookie")).map(_.value).toVector)
    HttpRequest.fromPath(HttpRequest.GET, path,
      query = Record(query.map { case (key, value) => Field(key, Field.Value.Single(value)) }),
      header = headers, context = HttpContext(None, None, None))
  }

  private def _log(context: ExecutionContext, conclusion: Conclusion, req: Request[IO],
    path: String, query: Vector[(String, String)]): Unit = {
    val credentials = ConclusionDisclosure.credentials(Some(_physical(req, path, query)))
    val _ = context.cncfCore.scope.observe_errorC("OAuth connection request failed",
      Record.data("conclusion" -> ConclusionDisclosure.log(conclusion, credentials = credentials)))
  }

  private def _error(req: Request[IO], conclusion: Conclusion, path: String,
    context: Option[ExecutionContext], query: Vector[(String, String)]): Response[IO] = {
    context.foreach(value => _log(value, conclusion, req, path, query))
    val physical = _physical(req, path, query)
    val credentials = ConclusionDisclosure.credentials(Some(physical))
    val error = StructuredHttpError.fromConclusion(conclusion, path, req.method.name, operationmode)
    _json_text(RecordEncoder.json(error._envelope_record(ConclusionDisclosure.detail(physical), credentials = credentials)),
      Status.fromInt(error.status).getOrElse(Status.InternalServerError))
  }

  private def _fixed(req: Request[IO], status: Int, message: String, path: String = "/web/connections"): Response[IO] = {
    val error = StructuredHttpError.fromMessage(message, status, path, req.method.name, operationmode)
    val result = _json_text(error.envelopeJson, Status.fromInt(status).getOrElse(Status.InternalServerError))
    if (req.method == Method.HEAD) result.withBodyStream(fs2.Stream.empty) else result
  }

  private def _json(value: Json): Response[IO] = _json_text(value.noSpaces, Status.Ok)
  private def _json_text(value: String, status: Status): Response[IO] =
    _secured(Response[IO](status).withEntity(value)
      .withContentType(`Content-Type`(MediaType.application.json, Some(Charset.`UTF-8`))))

  private def _secured(response: Response[IO]): Response[IO] = response.putHeaders(
    Header.Raw(CIString("Cache-Control"), "no-store"), Header.Raw(CIString("Pragma"), "no-cache"),
    Header.Raw(CIString("Referrer-Policy"), "no-referrer"), Header.Raw(CIString("X-Content-Type-Options"), "nosniff"))
}

private[http] object OAuthConnectionHttp {
  private val _cookie_name = "cncf-oauth-proof"
  private val _selectors = Set("textus.output.conclusion", "cncf.output.conclusion")
  private def _invalid: Conclusion = _conclusion(Consequence.argumentInvalid("OAuth connection request is invalid"))
  private def _permission: Conclusion = _conclusion(Consequence.securityPermissionDenied("OAuth connection origin is not allowed"))
  private def _conclusion(value: Consequence[Nothing]): Conclusion = value match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => throw new IllegalStateException("Structured OAuth failure is required")
  }
  private final class Selection(val component: Component, val runtime: OAuthConnectionRuntime, val base: String)
  private final class Authority(val host: String, val port: Int)
  private final class Origin(val scheme: String, val host: String, val port: Int)
}
