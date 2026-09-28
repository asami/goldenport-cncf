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
private[entity] trait StandardEntityObservabilityPart extends EntityStore { self: StandardEntityStore =>
  import EntityStore.*

  private[entity] def _with_datastore_calltree[A](
    operation: String,
    cid: DataStore.CollectionId,
    entryid: Option[DataStore.EntryId] = None
  )(
    body: => A
  )(using ctx: ExecutionContext): A = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(
        s"io:datastore:$operation",
        _datastore_calltree_attributes(operation, cid, entryid) ++ Map("calltree_kind" -> "io")
      )
      try {
        val result = body
        result match {
          case success: Consequence.Success[?] =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result)
            )
          case failure: Consequence.Failure[?] =>
            calltree.leave(
              Map("outcome" -> "failure") ++
                CallTreeValueSummary.failureAttributes(failure.conclusion)
            )
          case other =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(other)
            )
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "exception",
            "exception_type" -> e.getClass.getName
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _datastore_calltree_attributes(
    operation: String,
    cid: DataStore.CollectionId,
    entryid: Option[DataStore.EntryId]
  ): Map[String, String] =
    Map(
      "space" -> "datastore",
      "operation" -> operation,
      "collection" -> cid.print,
      "real_io" -> "true"
    ) ++ entryid.map(x => Map("entry_id" -> x.print)).getOrElse(Map.empty)

  private[entity] def _emit_entity_access(
    name: String,
    attributes: Record
  )(using ctx: ExecutionContext): Unit = {
    EntityAccessMetricsRegistry.shared.record(name, attributes)
    val _ = ctx.observability.emitDebug(ctx.cncfCore.scope, name, attributes)
  }

  private def _calltree_metric_attributes(
    name: String,
    attributes: Record
  ): Map[String, String] =
    (Vector("metric" -> name) ++
      attributes.asMap.toVector
        .sortBy(_._1)
        .map { case (key, value) =>
          key -> _truncate_calltree_metric_text(
            _sanitize_calltree_metric_value(key, value).toString,
            1000
          )
        }).toMap

  private def _sanitize_calltree_metric_value(
    key: String,
    value: Any
  ): Any =
    if (_is_sensitive_calltree_metric_key(key)) "***" else value

  private def _is_sensitive_calltree_metric_key(
    key: String
  ): Boolean = {
    val normalized = key.toLowerCase(java.util.Locale.ROOT)
    normalized.contains("password") ||
      normalized.contains("secret") ||
      normalized.contains("token") ||
      normalized.contains("session") ||
      normalized.contains("authorization") ||
      normalized.contains("cookie")
  }

  private def _truncate_calltree_metric_text(
    value: String,
    limit: Int
  ): String =
    if (value.length <= limit) value else value.take(limit) + "..."

  private[entity] def _emit_visibility_filtered(
    entity: String,
    rawcount: Int,
    notdeletedcount: Int,
    visiblecount: Int
  )(using ctx: ExecutionContext): Unit = {
    val filteredcount = notdeletedcount - visiblecount
    if (filteredcount > 0) {
      val _ = _emit_entity_access(
        "entity.search.filtered.visibility",
        Record.dataAuto(
          "entity" -> entity,
          "source" -> "data-store",
          "raw-count" -> rawcount,
          "notdeleted-count" -> notdeletedcount,
          "visible-count" -> visiblecount,
          "filtered-count" -> filteredcount
        )
      )
    }
  }
}
