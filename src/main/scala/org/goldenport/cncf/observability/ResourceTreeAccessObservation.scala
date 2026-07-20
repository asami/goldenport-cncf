package org.goldenport.cncf.observability

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.resource.{ResourceTreeAccess, ResourceTreeLimits, ResourceTreeQuery, ResourceTreeQueryResult, ResourceTreeReference, ResourceTreeSnapshot}
import org.goldenport.record.Record

/*
 * @since   Jul. 17, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object ResourceTreeAccessObservation {
  def observed(
    access: ResourceTreeAccess
  )(using context: ExecutionContext): ResourceTreeAccess =
    new ResourceTreeAccess {
      def snapshot(
        reference: ResourceTreeReference,
        limits: ResourceTreeLimits
      ): Consequence[ResourceTreeSnapshot] = {
        val provider = access.providerMetadata(reference)
        val chokepoint = DslChokepointContext(
          domain = "resource-tree",
          operation = "snapshot",
          resourceName = Some(reference.name),
          attributes = Record.dataAuto(
            "resource_tree.name" -> reference.name,
            "resource_tree.max_depth" -> limits.maxDepth,
            "resource_tree.max_entries" -> limits.maxEntries,
            "resource_tree.max_file_bytes" -> limits.maxFileBytes,
            "resource_tree.max_total_bytes" -> limits.maxTotalBytes
          ) ++ Record.dataAuto(provider.safeAttributes*)
        )
        val result = DslChokepointRunner.run(chokepoint) {
          DslChokepointRunner.phase(chokepoint, DslChokepointPhase.Resolve) {
            access.snapshot(reference, limits)
          }
        }
        result match {
          case Consequence.Success(_) =>
            RuntimeDashboardMetrics.recordResourceTreeSnapshot(
              tree = reference.name,
              provider = provider.family,
              error = false
            )
          case Consequence.Failure(conclusion) =>
            RuntimeDashboardMetrics.recordResourceTreeSnapshot(
              tree = reference.name,
              provider = provider.family,
              error = true,
              diagnostic = Some(ConclusionDiagnostics.classify(conclusion))
            )
        }
        result
      }

      override def query(query: ResourceTreeQuery): Consequence[ResourceTreeQueryResult] = {
        val provider = access.providerMetadata(query.reference)
        val chokepoint = DslChokepointContext(
          domain = "resource-tree",
          operation = "query",
          resourceName = Some(query.reference.name),
          attributes = _query_attributes(query, provider.safeAttributes)
        )
        val result = DslChokepointRunner.run(chokepoint) {
          DslChokepointRunner.phase(chokepoint, DslChokepointPhase.Query) {
            access.query(query)
          }
        }
        result match {
          case Consequence.Success(value) =>
            RuntimeDashboardMetrics.recordResourceTreeQuery(
              tree = query.reference.name,
              provider = provider.family,
              selector = query.selector.kind,
              limits = value.query.limits,
              visiteddirectories = Some(value.visitedDirectoryCount),
              matchedentries = Some(value.entries.size),
              error = false
            )
          case Consequence.Failure(conclusion) =>
            RuntimeDashboardMetrics.recordResourceTreeQuery(
              tree = query.reference.name,
              provider = provider.family,
              selector = query.selector.kind,
              limits = query.limits,
              error = true,
              diagnostic = Some(ConclusionDiagnostics.classify(conclusion))
            )
        }
        result
      }

      override def providerMetadata(reference: ResourceTreeReference) =
        access.providerMetadata(reference)
    }

  private def _query_attributes(
    query: ResourceTreeQuery,
    providerattributes: Vector[(String, String)]
  ): Record =
    Record.dataAuto(
      "resource_tree.name" -> query.reference.name,
      "resource_tree.selector" -> query.selector.kind,
      "resource_tree.max_depth" -> query.limits.maxDepth,
      "resource_tree.max_visited_directories" -> query.limits.maxVisitedDirectories,
      "resource_tree.max_entries" -> query.limits.maxEntries,
      "resource_tree.max_entry_bytes" -> query.limits.maxEntryBytes,
      "resource_tree.max_total_bytes" -> query.limits.maxTotalBytes
    ) ++ Record.dataAuto(providerattributes*)
}
