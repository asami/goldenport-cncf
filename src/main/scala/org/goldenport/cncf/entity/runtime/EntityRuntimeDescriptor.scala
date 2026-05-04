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
 *  version Apr. 24, 2026
 * @version May.  4, 2026
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
  entityKind: EntityKind = EntityKind.default,
  usageKind: EntityUsageKind = EntityUsageKind.default,
  operationKind: EntityOperationKind = EntityOperationKind.default,
  applicationDomain: EntityApplicationDomain = EntityApplicationDomain.default,
  entityKindExplicit: Boolean = false,
  operationKindExplicit: Boolean = false
) {
  def withSchema(p: Schema): EntityRuntimeDescriptor =
    copy(schema = Some(p))

  def effectiveWorkingSetPolicy: Option[WorkingSetPolicy] =
    workingSetPolicy.orElse {
      if (entityKindExplicit) entityKind.defaultWorkingSetPolicy else None
    }

  def effectiveOperationKind: EntityOperationKind =
    if (operationKindExplicit)
      operationKind
    else if (entityKindExplicit)
      entityKind.runtimePolicy.legacyOperationKind
    else
      operationKind

  def effectiveWorkingSetPolicySource: Option[WorkingSetPolicySource] =
    workingSetPolicySource.orElse {
      if (entityKindExplicit && effectiveWorkingSetPolicy.nonEmpty)
        Some(WorkingSetPolicySource.Cml)
      else
        None
    }

  def toPlan: EntityRuntimePlan[Any] =
    EntityRuntimePlan(
      entityName = entityName,
      memoryPolicy = memoryPolicy,
      workingSet = workingSet.map(_.toDefinition),
      workingSetPolicy = effectiveWorkingSetPolicy,
      workingSetPolicySource = effectiveWorkingSetPolicySource,
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

object EntityRuntimeDescriptor {
  def apply(
    entityName: String,
    collectionId: EntityCollectionId,
    memoryPolicy: EntityMemoryPolicy,
    partitionStrategy: PartitionStrategy,
    maxPartitions: Int,
    maxEntitiesPerPartition: Int,
    workingSet: Option[WorkingSetDescriptor],
    workingSetPolicy: Option[WorkingSetPolicy],
    workingSetPolicySource: Option[WorkingSetPolicySource],
    schema: Option[Schema],
    aggregateNames: Vector[String],
    viewNames: Vector[String],
    entityKind: EntityKind,
    usageKind: EntityUsageKind,
    operationKind: EntityOperationKind,
    applicationDomain: EntityApplicationDomain,
    entityKindExplicit: Boolean
  ): EntityRuntimeDescriptor =
    EntityRuntimeDescriptor(
      entityName = entityName,
      collectionId = collectionId,
      memoryPolicy = memoryPolicy,
      partitionStrategy = partitionStrategy,
      maxPartitions = maxPartitions,
      maxEntitiesPerPartition = maxEntitiesPerPartition,
      workingSet = workingSet,
      workingSetPolicy = workingSetPolicy,
      workingSetPolicySource = workingSetPolicySource,
      schema = schema,
      aggregateNames = aggregateNames,
      viewNames = viewNames,
      entityKind = entityKind,
      usageKind = usageKind,
      operationKind = operationKind,
      applicationDomain = applicationDomain,
      entityKindExplicit = entityKindExplicit,
      operationKindExplicit = !entityKindExplicit || operationKind != entityKind.legacyOperationKind
    )

  def apply(
    entityName: String,
    collectionId: EntityCollectionId,
    memoryPolicy: EntityMemoryPolicy,
    partitionStrategy: PartitionStrategy,
    maxPartitions: Int,
    maxEntitiesPerPartition: Int,
    workingSet: Option[WorkingSetDescriptor],
    workingSetPolicy: Option[WorkingSetPolicy],
    workingSetPolicySource: Option[WorkingSetPolicySource],
    schema: Option[Schema],
    aggregateNames: Vector[String],
    viewNames: Vector[String],
    usageKind: EntityUsageKind,
    operationKind: EntityOperationKind,
    applicationDomain: EntityApplicationDomain
  ): EntityRuntimeDescriptor =
    EntityRuntimeDescriptor(
      entityName = entityName,
      collectionId = collectionId,
      memoryPolicy = memoryPolicy,
      partitionStrategy = partitionStrategy,
      maxPartitions = maxPartitions,
      maxEntitiesPerPartition = maxEntitiesPerPartition,
      workingSet = workingSet,
      workingSetPolicy = workingSetPolicy,
      workingSetPolicySource = workingSetPolicySource,
      schema = schema,
      aggregateNames = aggregateNames,
      viewNames = viewNames,
      entityKind = EntityRuntimeDescriptor.legacyEntityKind(operationKind),
      usageKind = usageKind,
      operationKind = operationKind,
      applicationDomain = applicationDomain,
      entityKindExplicit = false,
      operationKindExplicit = true
    )

  def legacyEntityKind(
    operationKind: EntityOperationKind
  ): EntityKind =
    operationKind match {
      case EntityOperationKind.Task => EntityKind.Task
      case EntityOperationKind.Resource => EntityKind.Master
    }
}
