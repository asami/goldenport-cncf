package org.goldenport.cncf.resource

import java.net.URI
import java.util.Locale
import scala.util.Try
import org.goldenport.Consequence

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait ResourceReference {
  def scheme: String
  def print: String
}

object ResourceReference {
  final case class Url private[resource] (uri: URI) extends ResourceReference {
    def scheme: String = uri.getScheme.toLowerCase(Locale.ROOT)
    def print: String = uri.toASCIIString
  }

  final case class Urn private[resource] (
    nid: String,
    nss: String
  ) extends ResourceReference {
    def scheme: String = "urn"
    def print: String = s"urn:${nid}:${nss}"
  }

  def parseC(value: String): Consequence[ResourceReference] = {
    val text = Option(value).map(_.trim).getOrElse("")
    if (text.isEmpty)
      Consequence.argumentFormatError("reference", "absolute URL or URN", value)
    else {
      Try(new URI(text)).toOption match {
        case Some(uri) if uri.isAbsolute && uri.getScheme.equalsIgnoreCase("urn") =>
          _urn_c(text)
        case Some(uri) if uri.isAbsolute =>
          Consequence.success(Url(uri.normalize()))
        case _ =>
          Consequence.argumentFormatError("reference", "absolute URL or URN", value)
      }
    }
  }

  private val _urn_pattern = "(?i)^urn:([a-z0-9][a-z0-9-]{0,31}):(.+)$".r

  private def _urn_c(value: String): Consequence[ResourceReference] =
    value match {
      case _urn_pattern(rawnid, nss) if nss.nonEmpty && !nss.exists(_.isWhitespace) =>
        Consequence.success(Urn(rawnid.toLowerCase(Locale.ROOT), nss))
      case _ =>
        Consequence.argumentFormatError("reference", "urn:<nid>:<nss>", value)
    }
}
