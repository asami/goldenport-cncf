package org.goldenport.cncf

import java.time.Instant
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/**
 * Explicit fixture-only materialization of canonical generic EntityIds.
 *
 * Production issuance must use a concrete subtype or IdGenerationContext.
 *
 * @since   Sep. 17, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
object EntityIdFixtureBridge {
  def fromParts(
    major: String,
    minor: String,
    collection: EntityCollectionId,
    entropy: String
  ): EntityId =
    EntityId.bridgeFromParts(major, minor, collection, Instant.EPOCH, entropy)
      .toOption
      .getOrElse(throw new IllegalArgumentException("Invalid EntityId fixture bridge parts"))
}
