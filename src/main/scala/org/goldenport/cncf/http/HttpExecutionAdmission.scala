package org.goldenport.cncf.http

import java.net.URI
import java.util.Locale
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.effect.{ExternalEffectClassification, ExternalEffectKind, ExternalEffectRoute}
import org.goldenport.protocol.Property

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] enum HttpExecutionMethod {
  case Get, Post, Put
}

private[cncf] final case class HttpExecutionLimits(
  connectTimeoutMillis: Int,
  readTimeoutMillis: Int,
  responseBytes: Long
)

private[cncf] final case class HttpExecutionGrant(
  origins: Vector[String],
  methods: Set[HttpExecutionMethod]
)

private[cncf] final case class AdmittedHttpExecution(absoluteUrl: String, properties: Vector[Property])

/** Compiled host authority; unvalidated declarations cannot construct this value. */
private[cncf] final class HttpExecutionAdmission private (
  private[http] val baseUrl: String,
  private[http] val limits: HttpExecutionLimits,
  private[http] val grants: Map[ComponentId, HttpExecutionGrant]
) {
  private def _resolve_c(
    owner: ComponentId,
    method: HttpExecutionMethod,
    path: String,
    properties: Vector[Property]
  ): Consequence[AdmittedHttpExecution] = for {
    grant <- grants.get(owner).fold(HttpExecutionAdmission._deny[HttpExecutionGrant]("grant"))(Consequence.success)
    _ <- if (grant.methods.contains(method)) Consequence.success(()) else HttpExecutionAdmission._deny[Unit]("method")
    destination <- HttpExecutionAdmission._destination(baseUrl, path)
    _ <- if (grant.origins.contains(HttpExecutionAdmission._origin(destination))) Consequence.success(())
      else HttpExecutionAdmission._deny[Unit]("destination")
    controls <- HttpExecutionAdmission._properties(limits, properties)
  } yield AdmittedHttpExecution(HttpExecutionAdmission._absolute(destination), controls)
}

private[cncf] object HttpExecutionAdmission {
  val classification: ExternalEffectClassification =
    ExternalEffectClassification(Set(ExternalEffectKind.Http), ExternalEffectRoute.Managed)

  private val _controlled_keys = Set("http.connect-timeout-ms", "http.read-timeout-ms",
    "http.connect-timeout-seconds", "http.read-timeout-seconds", "http.timeout-seconds",
    "http.max-response-bytes", "http.follow-redirects")

  def fromRuntimeC(
    baseUrl: String,
    limits: HttpExecutionLimits,
    grants: Map[ComponentId, HttpExecutionGrant]
  ): Consequence[HttpExecutionAdmission] = for {
    base <- _uri(baseUrl).flatMap(_validate_absolute)
    _ <- if (Option(base.getRawQuery).isEmpty) Consequence.success(()) else _deny[Unit]("destination")
    _ <- if (limits.connectTimeoutMillis > 0 && limits.readTimeoutMillis > 0 && limits.responseBytes > 0)
      Consequence.success(()) else _deny[Unit]("limits")
    compiled <- grants.toVector.foldLeft(Consequence.success(Map.empty[ComponentId, HttpExecutionGrant])) {
      case (acc, (owner, grant)) => for {
        prior <- acc
        _ <- ComponentId.parseC(owner.name).toOption.filter(_ == owner)
          .fold(_deny[Unit]("grant"))(_ => Consequence.success(()))
        _ <- if (grant.origins.nonEmpty && grant.methods.nonEmpty) Consequence.success(()) else _deny[Unit]("grant")
        origins <- grant.origins.foldLeft(Consequence.success(Vector.empty[String])) { (result, text) => for {
          priororigins <- result
          uri <- _uri(text).flatMap(_validate_absolute)
          _ <- if (Option(uri.getRawQuery).isEmpty && Set("", "/").contains(Option(uri.getRawPath).getOrElse("")))
            Consequence.success(()) else _deny[Unit]("destination")
          origin = _origin(uri)
          _ <- if (!priororigins.contains(origin)) Consequence.success(()) else _deny[Unit]("grant")
        } yield priororigins :+ origin }
      } yield prior.updated(owner, HttpExecutionGrant(origins.sorted, grant.methods))
    }
  } yield new HttpExecutionAdmission(_absolute(base), limits, compiled)

  def resolveC(
    context: ExecutionContext,
    method: HttpExecutionMethod,
    path: String,
    properties: Vector[Property]
  ): Consequence[AdmittedHttpExecution] = {
    val scope = context.cncfCore.scope
    for {
      admission <- scope.httpExecutionAdmissionOption.fold(_deny[HttpExecutionAdmission]("unconfigured"))(Consequence.success)
      owner <- _component(scope).fold(_deny[ComponentId]("component"))(Consequence.success)
      admitted <- admission._resolve_c(owner, method, path, properties)
    } yield admitted
  }

  private def _component(scope: ScopeContext): Option[ComponentId] = scope match {
    case context: Component.Context => Some(context.component.componentId)
    case _ => scope.parent.flatMap(_component)
  }

  private def _deny[A](reason: String): Consequence[A] =
    Consequence.argumentPolicyViolation("http", s"http.execution.$reason", "host-authorized bounded HTTP execution", "unapproved")

  private def _uri(text: String): Consequence[URI] = {
    if (text.exists(c => c.isControl || c == '\\')) _deny("destination")
    else try Consequence.success(new URI(text))
      catch { case NonFatal(_) => _deny("destination") }
  }

  private def _validate_absolute(uri: URI): Consequence[URI] = {
    val scheme = Option(uri.getScheme).map(_.toLowerCase(Locale.ROOT))
    val host = Option(uri.getHost).filter(x => x.nonEmpty && x.forall(_ <= 127))
    val port = uri.getPort
    val authority = Option(uri.getRawAuthority)
    val validport = host.exists { value =>
      authority.exists { raw =>
        raw == value || (raw.startsWith(value + ":") && raw.drop(value.length + 1).matches("[0-9]+") && port >= 1 && port <= 65535)
      }
    }
    if (uri.isAbsolute && !uri.isOpaque && scheme.exists(Set("http", "https")) && host.nonEmpty &&
        Option(uri.getRawUserInfo).isEmpty && Option(uri.getRawFragment).isEmpty && validport)
      Consequence.success(uri)
    else _deny("destination")
  }

  private def _destination(base: String, path: String): Consequence[URI] = for {
    requested <- _uri(path)
    _ <- if (!requested.isOpaque && Option(requested.getRawFragment).isEmpty &&
        Option(requested.getRawUserInfo).isEmpty && (requested.isAbsolute || Option(requested.getRawAuthority).isEmpty) &&
        !path.startsWith("//")) Consequence.success(()) else _deny[Unit]("destination")
    resolved <- try _validate_absolute(new URI(base).resolve(requested))
      catch { case NonFatal(_) => _deny[URI]("destination") }
  } yield resolved

  private def _origin(uri: URI): String = {
    val scheme = uri.getScheme.toLowerCase(Locale.ROOT)
    val host = uri.getHost.toLowerCase(Locale.ROOT)
    val defaultport = if (scheme == "https") 443 else 80
    val port = if (uri.getPort == -1) defaultport else uri.getPort
    s"$scheme://$host" + (if (port == defaultport) "" else s":$port")
  }

  private def _absolute(uri: URI): String = {
    val text = uri.toASCIIString
    uri.getScheme.toLowerCase(Locale.ROOT) + text.substring(text.indexOf(':'))
  }

  private def _properties(limits: HttpExecutionLimits, properties: Vector[Property]): Consequence[Vector[Property]] = {
    val controls = properties.filter(x => _controlled_keys.contains(x.name.toLowerCase(Locale.ROOT)))
    val pairs = controls.map(x => x.name.toLowerCase(Locale.ROOT) -> String.valueOf(x.value).trim)
    val redirects = pairs.filter(_._1 == "http.follow-redirects")
    if (redirects.exists(_._2.equalsIgnoreCase("true"))) _deny("redirect")
    else if (pairs.map(_._1).distinct.size != pairs.size || redirects.exists(!_._2.equalsIgnoreCase("false"))) _deny("limits")
    else {
      val values = pairs.filterNot(_._1 == "http.follow-redirects")
      val parsed = values.map { case (key, text) =>
        val number = text.toLongOption
        val valid = if (key == "http.max-response-bytes") number.filter(_ >= 0)
          else if (key.endsWith("-seconds")) number.filter(x => x > 0 && x <= Int.MaxValue / 1000).map(_ * 1000)
          else number.filter(x => x > 0 && x <= Int.MaxValue)
        valid.map(key -> _)
      }
      if (parsed.exists(_.isEmpty)) _deny("limits")
      else {
        val numbers = parsed.flatten.toMap
        val generic = numbers.get("http.timeout-seconds").toVector
        val connect = (Vector(limits.connectTimeoutMillis.toLong) ++ generic ++
          numbers.get("http.connect-timeout-ms") ++ numbers.get("http.connect-timeout-seconds")).min
        val read = (Vector(limits.readTimeoutMillis.toLong) ++ generic ++
          numbers.get("http.read-timeout-ms") ++ numbers.get("http.read-timeout-seconds")).min
        val bytes = math.min(limits.responseBytes, numbers.getOrElse("http.max-response-bytes", limits.responseBytes))
        Consequence.success(properties.filterNot(x => _controlled_keys.contains(x.name.toLowerCase(Locale.ROOT))) ++ Vector(
          Property("http.connect-timeout-ms", connect.toString, None),
          Property("http.read-timeout-ms", read.toString, None),
          Property("http.max-response-bytes", bytes.toString, None),
          Property("http.follow-redirects", "false", None)))
      }
    }
  }
}
