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

      // Keep the decorator capability-transparent until query tracing is added.
      override def query(query: ResourceTreeQuery): Consequence[ResourceTreeQueryResult] =
        access.query(query)

      override def providerMetadata(reference: ResourceTreeReference) =
        access.providerMetadata(reference)
    }
}
