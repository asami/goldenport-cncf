package org.goldenport.cncf.datatype

import org.goldenport.id.UniversalId

/*
 * @since   Apr. 11, 2025
 * @version Feb. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityId(
  major: String,
  minor: String,
  collection: EntityCollectionId
) extends UniversalId(major, minor, "entity", collection.name)

final case class EntityCollectionId(
  major: String,
  minor: String,
  name: String,
) extends UniversalId(major, minor, "entity_collection", name)
