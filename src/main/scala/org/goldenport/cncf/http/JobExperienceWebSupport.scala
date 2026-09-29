package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.cncf.job.{JobExperienceScope, JobId}
import org.goldenport.cncf.naming.NamingConventions

/*
 * Canonical path and escaping support for the server-rendered Job experience.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object JobExperienceWebSupport {
  def detailPath(scope: JobExperienceScope, jobId: JobId): String =
    detailPath(scope, jobId.value)

  def detailPath(scope: JobExperienceScope, jobId: String): String = scope match {
    case JobExperienceScope.Mine(None) => s"/web/system/jobs/${pathSegment(jobId)}"
    case JobExperienceScope.Mine(Some(application)) => s"/web/${pathSegment(application)}/jobs/${pathSegment(jobId)}"
    case JobExperienceScope.Application(application) => s"/web/${pathSegment(application)}/admin/jobs/${pathSegment(jobId)}"
    case JobExperienceScope.Operator => s"/web/system/admin/jobs/${pathSegment(jobId)}"
  }

  def listPath(scope: JobExperienceScope): String = scope match {
    case JobExperienceScope.Mine(None) => "/web/system/jobs"
    case JobExperienceScope.Mine(Some(application)) => s"/web/${pathSegment(application)}/jobs"
    case JobExperienceScope.Application(application) => s"/web/${pathSegment(application)}/admin/jobs"
    case JobExperienceScope.Operator => "/web/system/admin/jobs"
  }

  def notificationPath(application: Option[String]): String =
    application.map(value => s"/web/${pathSegment(value)}/notifications").getOrElse("/web/system/notifications")

  def pathSegment(value: String): String =
    URLEncoder.encode(Option(value).getOrElse(""), StandardCharsets.UTF_8).replace("+", "%20")

  def escape(value: String): String =
    Option(value).getOrElse("")
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")

  def nextHref(scope: JobExperienceScope, cursor: String, filters: Map[String, String]): String =
    _href(listPath(scope), filters + ("cursor" -> cursor))

  def notificationHref(application: Option[String], cursor: String, filters: Map[String, String]): String =
    _href(notificationPath(application), filters + ("cursor" -> cursor))

  def pollingUrl(scope: JobExperienceScope, jobId: String): String = {
    val path = NamingConventions.toNormalizedPath("job_control", "job_experience", "get_job_experience")
    val values = Map(
      "id" -> jobId,
      "scope" -> scope.visibilityKey.takeWhile(_ != ':')
    ) ++ scope.applicationOption.map(value => "application" -> value)
    _href(s"/rest/v1$path", values)
  }

  private def _href(path: String, values: Map[String, String]): String = {
    val query = values.toVector
      .filter { case (_, value) => Option(value).exists(_.trim.nonEmpty) }
      .sortBy(_._1)
      .map { case (key, value) => s"${pathSegment(key)}=${pathSegment(value)}" }
      .mkString("&")
    if (query.isEmpty) path else s"$path?$query"
  }
}
