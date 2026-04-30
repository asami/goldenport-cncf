package org.goldenport.cncf.feed

import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter
import org.goldenport.Consequence
import org.goldenport.cncf.config.{ResolvedParameter, ResolvedParameters, RuntimeConfig}
import org.goldenport.protocol.Request
import org.goldenport.record.Record

/*
 * @since   Apr. 30, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
object AtomFeedProjection {
  final case class Config(
    title: String,
    baseUrl: String,
    selfPath: String,
    entryPathPrefix: String
  ) {
    def selfUrl: String = joinUrl(baseUrl, selfPath)
    def entryUrl(slug: String): String = joinUrl(baseUrl, s"${entryPathPrefix.stripSuffix("/")}/$slug")
  }

  val SiteBaseUrlKeys: Vector[String] =
    Vector(
      RuntimeConfig.SiteBaseUrlKey,
      "cncf.site.base-url",
      RuntimeConfig.RuntimeSiteBaseUrlKey,
      "cncf.runtime.site.base-url"
    )

  def resolveSiteBaseUrl(
    request: Request,
    params: ResolvedParameters
  ): Consequence[String] =
    _request_property(request, SiteBaseUrlKeys*)
      .orElse(SiteBaseUrlKeys.iterator.flatMap(params.get).map(x => ResolvedParameter.format_value(x.value)).find(_.trim.nonEmpty))
      .map(_normalize_base_url)
      .filter(_.nonEmpty)
      .map(Consequence.success)
      .getOrElse(Consequence.argumentMissing("textus.site.base-url/cncf.site.base-url"))

  def project(
    config: Config,
    rows: Vector[Record]
  ): Consequence[AtomFeed] =
    _sequence(rows.map(_entry(config, _))).map { entries0 =>
      val entries = entries0.sortBy { e =>
        val publishedOrder = e.published.map(x => -x.toEpochMilli).getOrElse(Long.MaxValue)
        (-e.updated.toEpochMilli, publishedOrder, e.id)
      }
      AtomFeed(
        id = config.selfUrl,
        title = config.title,
        updated = entries.headOption.map(_.updated).getOrElse(Instant.EPOCH),
        links = Vector(
          AtomLink(config.selfUrl, rel = Some("self"), mediaType = Some("application/atom+xml"))
        ),
        entries = entries
      )
    }

  def joinUrl(
    baseUrl: String,
    path: String
  ): String = {
    val base = _normalize_base_url(baseUrl)
    val p = Option(path).getOrElse("").trim
    if (p.isEmpty) base
    else s"${base.stripSuffix("/")}/${p.stripPrefix("/")}"
  }

  private def _entry(
    config: Config,
    record: Record
  ): Consequence[AtomEntry] = {
    val content = _string_option(record, "content")
    val summary = _string_option(record, "summary", "description")
    for {
      slug <- _string(record, "slug")
      title <- _string(record, "title")
      updated <- _instant(record, "updated_at", "updatedAt").orElse(_instant(record, "created_at", "createdAt"))
        .map(Consequence.success)
        .getOrElse(Consequence.argumentMissing("updated_at/created_at"))
      published = _instant(record, "created_at", "createdAt")
      url = config.entryUrl(slug)
    } yield AtomEntry(
      id = url,
      title = title,
      updated = updated,
      published = published,
      links = Vector(AtomLink(url, rel = Some("alternate"), mediaType = Some("text/html"))),
      summary = summary,
      contentHtml = content
    )
  }

  private def _request_property(
    request: Request,
    names: String*
  ): Option[String] =
    names.iterator.flatMap { name =>
      request.properties.reverseIterator.collectFirst {
        case prop if prop.name.equalsIgnoreCase(name) =>
          Option(prop.value).map(_.toString.trim).getOrElse("")
      }
    }.find(_.nonEmpty)

  private def _string(
    record: Record,
    names: String*
  ): Consequence[String] =
    _string_option(record, names*).map(Consequence.success).getOrElse(Consequence.argumentMissing(names.mkString("/")))

  private def _string_option(
    record: Record,
    names: String*
  ): Option[String] =
    names.iterator.flatMap(record.getAny).map(_.toString.trim).find(_.nonEmpty)

  private def _instant(
    record: Record,
    names: String*
  ): Option[Instant] =
    names.iterator.flatMap(record.getAny).flatMap(_to_instant).nextOption()

  private def _to_instant(value: Any): Option[Instant] =
    value match {
      case i: Instant => Some(i)
      case z: ZonedDateTime => Some(z.toInstant)
      case o: OffsetDateTime => Some(o.toInstant)
      case l: LocalDateTime => Some(l.toInstant(ZoneOffset.UTC))
      case d: LocalDate => Some(d.atStartOfDay().toInstant(ZoneOffset.UTC))
      case d: java.util.Date => Some(d.toInstant)
      case s: String =>
        val text = s.trim
        if (text.isEmpty) None
        else
          _parse_instant(text)
            .orElse(scala.util.Try(OffsetDateTime.parse(text).toInstant).toOption)
            .orElse(scala.util.Try(LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)).toOption)
            .orElse(scala.util.Try(LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC)).toOption)
      case other =>
        _to_instant(other.toString)
    }

  private def _parse_instant(text: String): Option[Instant] =
    scala.util.Try(Instant.parse(text)).toOption.orElse(
      scala.util.Try(DateTimeFormatter.ISO_INSTANT.parse(text, Instant.from)).toOption
    )

  private def _sequence[A](xs: Vector[Consequence[A]]): Consequence[Vector[A]] =
    xs.foldLeft(Consequence.success(Vector.empty[A])) {
      case (z, x) => z.flatMap(values => x.map(values :+ _))
    }

  private def _normalize_base_url(value: String): String =
    Option(value).getOrElse("").trim.stripSuffix("/")
}
