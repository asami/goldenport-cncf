package org.goldenport.cncf.entity.runtime

import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.security.{EntityApplicationDomain, EntityOperationKind, EntityUsageKind}
import org.goldenport.schema.Schema

/*
 * Static metadata for CNCF entity operations.
 *
 * It aggregates runtime policy and the effective static entity schema used by
 * Web/Admin/Form and other meta operations. The schema is normally derived from
 * the generated companion Schema and may be adjusted by descriptor or
 * application policy before being stored here.
 *
 * @since   Mar. 27, 2026
 * @version Apr. 24, 2026
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
  workingSetPolicy: Option[WorkingSetPolicy] = None,
  workingSetPolicySource: Option[WorkingSetPolicySource] = None,
  schema: Option[Schema] = None,
  aggregateNames: Vector[String] = Vector.empty,
  viewNames: Vector[String] = Vector.empty,
  usageKind: EntityUsageKind = EntityUsageKind.default,
  operationKind: EntityOperationKind = EntityOperationKind.default,
  applicationDomain: EntityApplicationDomain = EntityApplicationDomain.default
) {
  def withSchema(p: Schema): EntityRuntimeDescriptor =
    copy(schema = Some(p))

  def toPlan: EntityRuntimePlan[Any] =
    EntityRuntimePlan(
      entityName = entityName,
      memoryPolicy = memoryPolicy,
      workingSet = workingSet.map(_.toDefinition),
      workingSetPolicy = workingSetPolicy,
      workingSetPolicySource = workingSetPolicySource,
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
