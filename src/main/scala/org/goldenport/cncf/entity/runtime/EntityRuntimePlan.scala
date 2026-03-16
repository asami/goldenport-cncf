package org.goldenport.cncf.entity.runtime

/*
 * Runtime configuration for an entity.
 *
 * This object describes how an entity should be handled
 * by the CNCF runtime (memory policy, working set, partitioning).
 *
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityRuntimePlan[E](
  entityName: String,
  memoryPolicy: EntityMemoryPolicy,
  workingSet: Option[WorkingSetDefinition[E]],
  partitionStrategy: PartitionStrategy,
  maxPartitions: Int,
  maxEntitiesPerPartition: Int
)
