package org.goldenport.cncf.entity.runtime

import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * Static runtime metadata for an entity.
 *
 * This is intended to become the descriptor-side contract used by CNCF
 * bootstrap instead of program-side ad hoc providers.
 *
 * @since   Mar. 27, 2026
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityRuntimeDescriptor(
  entityName: String,
  collectionId: EntityCollectionId,
  memoryPolicy: EntityMemoryPolicy,
  partitionStrategy: PartitionStrategy,
  maxPartitions: Int,
  maxEntitiesPerPartition: Int,
  workingSet: Option[WorkingSetDescriptor] = None,
  aggregateNames: Vector[String] = Vector.empty,
  viewNames: Vector[String] = Vector.empty
) {
  def toPlan: EntityRuntimePlan[Any] =
    EntityRuntimePlan(
      entityName = entityName,
      memoryPolicy = memoryPolicy,
      workingSet = workingSet.map(_.toDefinition),
      partitionStrategy = partitionStrategy,
      maxPartitions = maxPartitions,
      maxEntitiesPerPartition = maxEntitiesPerPartition
    )
}

final case class WorkingSetDescriptor(
  entityName: String,
  entityIds: Vector[String]
) {
  def toDefinition: WorkingSetDefinition[Any] =
    WorkingSetDefinition(entityName = entityName, entities = Vector.empty)
}
