package org.goldenport.cncf.http

import java.time.ZoneId
import java.util.Locale

import io.circe.Json

import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.goldenport.record.Record

/*
 * @since   Jul. 17, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
enum WebApplicationMode(val name: String) {
  case Standalone extends WebApplicationMode("standalone")
  case MultiUser extends WebApplicationMode("multi-user")

}

object WebApplicationMode {
  def fromSubsystemUserMode(mode: SubsystemUserMode): WebApplicationMode = mode match {
    case SubsystemUserMode.Standalone => WebApplicationMode.Standalone
    case SubsystemUserMode.MultiUser => WebApplicationMode.MultiUser
  }

  def parse(value: String): Option[WebApplicationMode] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).flatMap {
      case "standalone" => Some(WebApplicationMode.Standalone)
      case "multi-user" | "multi_user" | "multiuser" => Some(WebApplicationMode.MultiUser)
      case _ => None
    }
}

final case class WebDisplayFormatPolicyId private (name: String) {
  require(
    name.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*"),
    s"Invalid Web display format policy identifier: $name"
  )
}

object WebDisplayFormatPolicyId {
  val LOCALIZED_MEDIUM = WebDisplayFormatPolicyId("localized-medium")
  val APPLICATION_DEFAULT = WebDisplayFormatPolicyId("application-default")

  private val _name_pattern = "[a-z][a-z0-9]*(?:-[a-z0-9]+)*".r

  def parse(value: String): Option[WebDisplayFormatPolicyId] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).filter { x =>
      _name_pattern.pattern.matcher(x).matches()
    }.map(WebDisplayFormatPolicyId(_))
}

final case class WebExecutionProjectionPolicy(
  dateFormat: WebDisplayFormatPolicyId = WebDisplayFormatPolicyId.LOCALIZED_MEDIUM,
  dateTimeFormat: WebDisplayFormatPolicyId = WebDisplayFormatPolicyId.APPLICATION_DEFAULT,
  publicCapabilities: Vector[String] = Vector.empty
) {
  def selectPublicCapabilities(effective: Iterable[String]): Vector[String] = {
    val allowed = publicCapabilities.iterator.map(SecuritySubject.normalize).filter(_.nonEmpty).toSet
    effective.iterator
      .map(SecuritySubject.normalize)
      .filter(allowed.contains)
      .toSet
      .toVector
      .sorted
  }
}

final case class WebExecutionSubjectProjection(
  authenticated: Boolean,
  displayName: Option[String]
) {
  def toRecord: Record =
    Record.data(
      "authenticated" -> authenticated,
      "displayName" -> displayName.orNull
    )

  def toJson: Json =
    Json.obj(
      "authenticated" -> Json.fromBoolean(authenticated),
      "displayName" -> displayName.fold(Json.Null)(Json.fromString)
    )
}

object WebExecutionSubjectProjection {
  val ANONYMOUS = WebExecutionSubjectProjection(false, None)

  def create(authenticated: Boolean, displayName: Option[String]): WebExecutionSubjectProjection =
    WebExecutionSubjectProjection(
      authenticated,
      displayName.map(_.trim).filter(_.nonEmpty)
    )
}

final case class WebExecutionFormatProjection(
  date: WebDisplayFormatPolicyId,
  dateTime: WebDisplayFormatPolicyId
) {
  def toRecord: Record =
    Record.data(
      "date" -> date.name,
      "dateTime" -> dateTime.name
    )

  def toJson: Json =
    Json.obj(
      "date" -> Json.fromString(date.name),
      "dateTime" -> Json.fromString(dateTime.name)
    )
}

final case class WebExecutionProjection(
  locale: String,
  timezone: String,
  format: WebExecutionFormatProjection,
  applicationMode: WebApplicationMode,
  subject: WebExecutionSubjectProjection,
  capabilities: Vector[String]
) {
  def toRecord: Record =
    Record.data(
      "locale" -> locale,
      "timezone" -> timezone,
      "format" -> format.toRecord,
      "applicationMode" -> applicationMode.name,
      "subject" -> subject.toRecord,
      "capabilities" -> capabilities
    )

  def toJson: Json =
    Json.obj(
      "locale" -> Json.fromString(locale),
      "timezone" -> Json.fromString(timezone),
      "format" -> format.toJson,
      "applicationMode" -> Json.fromString(applicationMode.name),
      "subject" -> subject.toJson,
      "capabilities" -> Json.fromValues(capabilities.map(Json.fromString))
    )

  def toPageContextRecord: Record = Record.data("execution" -> toRecord)

  def toPageContextJson: Json = Json.obj("execution" -> toJson)
}

object WebExecutionProjection {
  def create(
    locale: Locale,
    timezone: ZoneId,
    policy: WebExecutionProjectionPolicy,
    applicationMode: WebApplicationMode,
    subject: WebExecutionSubjectProjection,
    effectiveCapabilities: Iterable[String]
  ): WebExecutionProjection =
    WebExecutionProjection(
      locale.toLanguageTag,
      timezone.getId,
      WebExecutionFormatProjection(policy.dateFormat, policy.dateTimeFormat),
      applicationMode,
      subject,
      policy.selectPublicCapabilities(effectiveCapabilities)
    )
}
