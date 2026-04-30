package org.goldenport.cncf.feed

import java.time.format.DateTimeFormatter
import scala.xml.{Elem, Node, NodeSeq, Text}

/*
 * @since   Apr. 30, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
object AtomFeedRenderer {
  def render(feed: AtomFeed): String =
    _feed(feed).toString

  private def _feed(feed: AtomFeed): Elem =
    <feed xmlns="http://www.w3.org/2005/Atom">
      <id>{feed.id}</id>
      <title>{feed.title}</title>
      <updated>{_instant(feed.updated)}</updated>
      {feed.links.map(_link)}
      {feed.authors.map(_person("author", _))}
      {feed.entries.map(_entry)}
    </feed>

  private def _entry(entry: AtomEntry): Elem =
    <entry>
      <id>{entry.id}</id>
      <title>{entry.title}</title>
      <updated>{_instant(entry.updated)}</updated>
      {entry.published.toVector.map(x => <published>{_instant(x)}</published>)}
      {entry.links.map(_link)}
      {entry.authors.map(_person("author", _))}
      {entry.summary.map(x => <summary>{Text(x)}</summary>).getOrElse(NodeSeq.Empty)}
      {entry.contentHtml.map(x => <content type="html">{Text(x)}</content>).getOrElse(NodeSeq.Empty)}
    </entry>

  private def _link(link: AtomLink): Elem = {
    val attrs0 = scala.xml.Null
    val attrs1 = scala.xml.Attribute(null, "href", link.href, attrs0)
    val attrs2 = link.rel.map(x => scala.xml.Attribute(null, "rel", x, attrs1)).getOrElse(attrs1)
    val attrs3 = link.mediaType.map(x => scala.xml.Attribute(null, "type", x, attrs2)).getOrElse(attrs2)
    Elem(null, "link", attrs3, scala.xml.TopScope, minimizeEmpty = true)
  }

  private def _person(
    label: String,
    person: AtomPerson
  ): Elem =
    Elem(
      null,
      label,
      scala.xml.Null,
      scala.xml.TopScope,
      minimizeEmpty = false,
      (Vector(<name>{person.name}</name>) ++
        person.uri.map(x => <uri>{x}</uri>).toVector ++
        person.email.map(x => <email>{x}</email>).toVector)*
    )

  private def _instant(value: java.time.Instant): String =
    DateTimeFormatter.ISO_INSTANT.format(value)
}
