package org.goldenport.cncf.entity.runtime

/*
 * Runtime configuration for an entity.
 *
 * This object describes how an entity should be handled
 * by the CNCF runtime (memory policy, working set, partitioning).
 *
 * @since   Mar. 16, 2026
 * @version Apr. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityRuntimePlan[E](
  entityName: String,
  memoryPolicy: EntityMemoryPolicy,
  workingSet: Option[WorkingSetDefinition[E]],
  workingSetPolicy: Option[WorkingSetPolicy] = None,
  workingSetPolicySource: Option[WorkingSetPolicySource] = None,
  partitionStrategy: PartitionStrategy,
  maxPartitions: Int,
  maxEntitiesPerPartition: Int
)
