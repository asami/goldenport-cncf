package org.goldenport.cncf.entity

import cats._
import cats.syntax.all.*
import scala.deprecatedName
import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.datatype.Identifier
import org.goldenport.record.Record
import org.goldenport.cncf.*
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import org.goldenport.cncf.directive.{Query as EntityDirectiveQuery, SearchResult}
import org.goldenport.cncf.datastore.{
  DataStore,
  DataStoreConditionalExpectedField,
  DataStoreConditionalRoot,
  DataStoreConditionalSuccessor,
  DataStoreConditionalTransitionPlan,
  DataStoreConditionalTransitionResult,
  EntityCompareAndSetMutationPlan,
  EntityDirectMutationPlan,
  EntityMutationExecutionPath,
  EntityMutationExclusionGuard,
  EntityMutationPathRequest,
  EntityMutationProviderReadback,
  EntityMutationProviderResult,
  EntityMutationReadbackRequirement,
  EntityVersionedMutationPlan,
  EntityVersionedMutationResult,
  EntityVersionedRootMutation,
  EntityVersionedSideEffect,
  Query as DataStoreQuery,
  QueryDirective,
  QueryLimit,
  QueryOrder,
  OrderDirection
}
import org.goldenport.cncf.datastore.DataStore.EntryId
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}
import org.simplemodeling.model.value.NominalScalar

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityCreateOptions(
  defaultProfiles: Set[String] = Set.empty,
  defaultValues: Record = Record.empty
) {
  def hasDefaultProfile(name: String): Boolean =
    defaultProfiles.contains(name.trim.toLowerCase(java.util.Locale.ROOT))
}
object EntityCreateOptions {
  val default: EntityCreateOptions = EntityCreateOptions()
  val sharedRecord: EntityCreateOptions = EntityCreateOptions(Set("public-read"))
}
