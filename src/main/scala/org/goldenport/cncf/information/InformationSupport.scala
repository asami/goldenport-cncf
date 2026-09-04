package org.goldenport.cncf.information

import java.time.Instant
import org.goldenport.datatype.Identifier
import org.goldenport.record.Record
import org.simplemodeling.model.value.LifecycleAttributes

/** Non-CML Information support definitions and lifecycle construction helpers. */
private[information] object InformationLifecycleSupport {
  def lifecycleAttributes(updatedAt: Instant): LifecycleAttributes =
    LifecycleAttributes(
      updatedAt,
      updatedAt,
      Identifier("system"),
      Identifier("system"),
      org.simplemodeling.model.statemachine.PostStatus.default,
      org.simplemodeling.model.statemachine.Aliveness.default
    )

  def updatedLifecycleAttributes(
    information: org.goldenport.cncf.information.entity.Information,
    updatedAt: Instant
  ): LifecycleAttributes =
    information.lifecycleAttributes.copy(
      updatedAt = updatedAt,
      updatedBy = Identifier("system")
    )
}

final case class PaperInformation(
  title: String,
  authors: Vector[String],
  publicationIdentity: Option[String] = None,
  venue: Option[String] = None,
  publicationDate: Option[String] = None,
  abstractText: Option[String] = None,
  keywords: Vector[String] = Vector.empty,
  citations: Vector[String] = Vector.empty,
  resolverHooks: Vector[String] = Vector.empty
)

object PaperInformation {
  def from(record: Record): PaperInformation =
    PaperInformation(
      title = record.getString("title").getOrElse("").trim,
      authors = _strings(record, "authors") ++ record.getString("author").toVector.map(_.trim).filter(_.nonEmpty),
      publicationIdentity = record.getString("publicationIdentity").orElse(record.getString("doi")).filter(_.trim.nonEmpty),
      venue = record.getString("venue").filter(_.trim.nonEmpty),
      publicationDate = record.getString("publicationDate").orElse(record.getString("date")).filter(_.trim.nonEmpty),
      abstractText = record.getString("abstract").filter(_.trim.nonEmpty),
      keywords = _strings(record, "keywords"),
      citations = _strings(record, "citations"),
      resolverHooks = _strings(record, "resolverHooks")
    )

  private def _strings(
    record: Record,
    key: String
  ): Vector[String] =
    record.getString(key).toVector.flatMap { value =>
      value.split("[,\\n]").toVector.map(_.trim).filter(_.nonEmpty)
    }
}

final case class WebResourceInformation(
  title: String,
  url: Option[String] = None,
  canonicalUrl: Option[String] = None,
  finalUrl: Option[String] = None,
  siteName: Option[String] = None,
  publisher: Option[String] = None,
  author: Option[String] = None,
  retrievedAt: Option[String] = None,
  summary: Option[String] = None,
  language: Option[String] = None,
  keywords: Vector[String] = Vector.empty,
  links: Vector[String] = Vector.empty,
  sourceUrl: Option[String] = None
)

object WebResourceInformation {
  def from(record: Record): WebResourceInformation =
    WebResourceInformation(
      title = record.getString("title").getOrElse("").trim,
      url = _string(record, "url"),
      canonicalUrl = _string(record, "canonicalUrl"),
      finalUrl = _string(record, "finalUrl"),
      siteName = _string(record, "siteName"),
      publisher = _string(record, "publisher"),
      author = _string(record, "author"),
      retrievedAt = _string(record, "retrievedAt"),
      summary = _string(record, "summary"),
      language = _string(record, "language"),
      keywords = _strings(record, "keywords"),
      links = _strings(record, "links"),
      sourceUrl = _string(record, "sourceUrl")
    )

  private def _string(
    record: Record,
    key: String
  ): Option[String] =
    record.getString(key).map(_.trim).filter(_.nonEmpty)

  private def _strings(
    record: Record,
    key: String
  ): Vector[String] =
    record.getString(key).toVector.flatMap { value =>
      value.split("[,\\n]").toVector.map(_.trim).filter(_.nonEmpty)
    }
}

object InformationCapabilities {
  val read = "information:read"
  val `import` = "information:import"
  val edit = "information:edit"
  val validate = "information:validate"
  val resolve = "information:resolve"
  val confirm = "information:confirm"
  val reject = "information:reject"
  val publish = "information:publish"
  val conflictRead = "information:conflict:read"
  val conflictResolve = "information:conflict:resolve"
  val auditRead = "information:audit:read"

  val all: Vector[String] = Vector(
    read,
    `import`,
    edit,
    validate,
    resolve,
    confirm,
    reject,
    publish,
    conflictRead,
    conflictResolve,
    auditRead
  )
}
