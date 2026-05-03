package org.goldenport.cncf.blob

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{EntityIdentityScope, EntityStore}
import org.goldenport.cncf.id.{TextusUrn, TextusUrnResolver, UrnResolution}

/*
 * Textus URN resolver for Blob entities.
 *
 * Blob URNs use EntityId entropy as the stable document-facing value in v1:
 * urn:textus:blob:{entropy}
 *
 * @since   May.  3, 2026
 * @version May.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class BlobUrnResolver extends TextusUrnResolver {
  import BlobRepository.given

  def kind: String = TextusUrn.BlobKind

  def resolve(urn: TextusUrn)(using ExecutionContext): Consequence[Option[UrnResolution]] =
    if (urn.kind != kind)
      Consequence.success(None)
    else
      EntityStore.standard()
        .resolveIdentity[Blob](
          BlobRepository.CollectionId,
          urn.value,
          Vector("shortid"),
          includeEntityIdEntropy = true,
          EntityIdentityScope.CurrentContext
        )
        .map(_.map(id => UrnResolution(urn, BlobRepository.CollectionId, id)))
}
