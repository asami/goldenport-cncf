package org.goldenport.cncf.blob

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{EntityIdentityScope, EntityStore}
import org.goldenport.cncf.id.{TextusUrn, TextusUrnResolver, UrnResolution}

/*
 * Textus URN resolver for built-in media entities.
 *
 * @since   May.  3, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class MediaUrnResolver(mediaKind: MediaKind) extends TextusUrnResolver {
  import MediaRepository.given

  def kind: String =
    mediaKind.print

  def resolve(urn: TextusUrn)(using ExecutionContext): Consequence[Option[UrnResolution]] =
    if (urn.kind != kind)
      Consequence.success(None)
    else {
      val collection = MediaEntityCollections.collection(mediaKind)
      EntityStore.standard()
        .resolveIdentity[MediaEntity](
          collection,
          urn.value,
          Vector("shortid"),
          includeEntityIdEntropy = true,
          EntityIdentityScope.CurrentContext
        )
        .map(_.map(id => UrnResolution(urn, collection, id)))
    }
}

object MediaUrnResolver {
  def all: Vector[TextusUrnResolver] =
    Vector(
      new MediaUrnResolver(MediaKind.Image),
      new MediaUrnResolver(MediaKind.Video),
      new MediaUrnResolver(MediaKind.Audio),
      new MediaUrnResolver(MediaKind.Attachment)
    )
}
