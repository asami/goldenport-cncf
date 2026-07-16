package org.goldenport.cncf.resource

import java.nio.ByteBuffer
import java.nio.charset.{Charset, CodingErrorAction, StandardCharsets}
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ResourceContent(
  reference: ResourceReference,
  bytes: Vector[Byte],
  mediaType: Option[String] = None,
  declaredCharset: Option[Charset] = None
) {
  def byteSize: Long = bytes.size.toLong

  def textC(charset: Charset = declaredCharset.getOrElse(StandardCharsets.UTF_8)): Consequence[String] =
    try {
      val decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
      Consequence.success(decoder.decode(ByteBuffer.wrap(bytes.toArray)).toString)
    } catch {
      case NonFatal(_) =>
        Consequence.resourceInvalid(
          s"resource content cannot be decoded as ${charset.name}: ${reference.scheme} resource"
        )
    }
}

trait ResourceAccess {
  def read(reference: ResourceReference): Consequence[ResourceContent]

  def readText(
    reference: ResourceReference,
    charset: Option[Charset] = None
  ): Consequence[String] =
    read(reference).flatMap { content =>
      charset.map(content.textC).getOrElse(content.textC())
    }
}

object ResourceAccess {
  val unavailable: ResourceAccess = new ResourceAccess {
    def read(reference: ResourceReference): Consequence[ResourceContent] =
      Consequence.serviceUnavailable(
        s"resource access is not configured for ${reference.scheme} references"
      )
  }

  def url(
    policy: ResourceUrlPolicy,
    httpdriver: org.goldenport.cncf.http.HttpDriver
  ): ResourceAccess = {
    val providers = Vector.newBuilder[UrlResourceProvider]
    if (policy.normalizedFileRoots.nonEmpty)
      providers += FileUrlResourceProvider(policy)
    if (policy.normalizedHttpsHosts.nonEmpty)
      providers += HttpsUrlResourceProvider(policy, httpdriver)
    url(policy, providers.result())
  }

  def url(
    policy: ResourceUrlPolicy,
    providers: Vector[UrlResourceProvider]
  ): ResourceAccess =
    new UrlResourceAccess(policy, providers)

  def textus(policy: TextusUrnResourcePolicy): ResourceAccess =
    textus(policy.fileProviders)

  def textus(providers: Vector[TextusUrnResourceProvider]): ResourceAccess =
    new TextusUrnResourceAccess(providers)

  def standard(
    urlpolicy: ResourceUrlPolicy,
    textuspolicy: TextusUrnResourcePolicy,
    httpdriver: org.goldenport.cncf.http.HttpDriver
  ): ResourceAccess =
    _composite(url(urlpolicy, httpdriver), textus(textuspolicy))

  private def _composite(
    urlaccess: ResourceAccess,
    textusaccess: ResourceAccess
  ): ResourceAccess =
    new ResourceAccess {
      def read(reference: ResourceReference): Consequence[ResourceContent] =
        reference match {
          case _: ResourceReference.Url => urlaccess.read(reference)
          case urn: ResourceReference.Urn if urn.nid == "textus" => textusaccess.read(reference)
          case _: ResourceReference.Urn =>
            Consequence.resourceUnsupported("URN resource providers are not configured")
        }
    }
}
