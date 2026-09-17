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
  private val _default_timestamp = Instant.EPOCH
  private val _default_entropy = "fixture"

  def fromParts(
    major: String,
    minor: String,
    collection: EntityCollectionId,
    timestamp: Option[Instant] = Some(_default_timestamp),
    entropy: Option[String] = Some(_default_entropy)
  ): EntityId = {
    val canontimestamp = timestamp.getOrElse(_default_timestamp)
    val canonentropy = entropy.getOrElse(_default_entropy)
    EntityId.bridgeFromParts(major, minor, collection, canontimestamp, canonentropy)
      .toOption
      .getOrElse(throw new IllegalArgumentException("Invalid EntityId fixture bridge parts"))
  }
}
