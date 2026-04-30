package org.goldenport.cncf.feed

import java.time.Instant

/*
 * @since   Apr. 30, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AtomPerson(
  name: String,
  uri: Option[String] = None,
  email: Option[String] = None
)

final case class AtomLink(
  href: String,
  rel: Option[String] = None,
  mediaType: Option[String] = None
)

final case class AtomEntry(
  id: String,
  title: String,
  updated: Instant,
  published: Option[Instant] = None,
  links: Vector[AtomLink] = Vector.empty,
  summary: Option[String] = None,
  contentHtml: Option[String] = None,
  authors: Vector[AtomPerson] = Vector.empty
)
final case class AtomFeed(
  id: String,
  title: String,
  updated: Instant,
  links: Vector[AtomLink],
  entries: Vector[AtomEntry],
  authors: Vector[AtomPerson] = Vector.empty
)
