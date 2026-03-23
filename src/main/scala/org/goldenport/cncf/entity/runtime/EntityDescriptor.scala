package org.goldenport.cncf.entity.runtime

import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.entity.EntityPersistent

/*
 * @since   Mar. 15, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityDescriptor[E](
  collectionId: EntityCollectionId,
  plan: EntityRuntimePlan[E],
  persistent: EntityPersistent[E]
)

final case class EntityStorage[E](
  // Loader lifecycle is owned by the storage realm (one loader per storage).
  storeRealm: EntityRealm[E],
  memoryRealm: Option[PartitionedMemoryRealm[E]] = None
)
