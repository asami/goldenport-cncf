/*
 * @since   Mar. 28, 2026
 * @version Mar. 28, 2026
 * @author  ASAMI, Tomoharu
 */
package docs.journal.`2026`.`03`

/**
 * Direction for entity access source observability.
 *
 * Problem
 * - When debugging load/search behavior, it is currently hard to tell where the
 *   data was obtained from:
 *   - EntitySpace working set
 *   - EntityStore layer
 *   - DataStore layer
 * - It is also hard to tell when visibility filtering removed a record that was
 *   otherwise present in lower layers.
 *
 * Current assumptions
 * - EntitySpace is shared and remains a resident working set inside the
 *   subsystem/component lifetime.
 * - EntityStore/DataStore are the source-of-truth side for persistence.
 * - EntitySpace acts as a resident working set cache, not as the only truth.
 *
 * Goal
 * - Record where entity access was satisfied from.
 * - Record when fallback happened.
 * - Record when search results were filtered by visibility policy.
 * - Expose the same facts in:
 *   - per-operation trace / call tree
 *   - aggregate metrics service
 *
 * Source model
 * Introduce a stable source vocabulary for entity access:
 * - entity-space
 * - entity-store
 * - data-store
 * - miss
 *
 * Trace / call tree
 * For one operation execution, record the concrete path.
 *
 * Load trace points
 * - entity.load.start
 * - entity.load.try.entity-space
 * - entity.load.hit.entity-space
 * - entity.load.fallback.entity-store
 * - entity.load.hit.entity-store
 * - entity.load.hit.data-store
 * - entity.load.miss
 *
 * Search trace points
 * - entity.search.start
 * - entity.search.try.entity-space
 * - entity.search.hit.entity-space
 * - entity.search.fallback.entity-store
 * - entity.search.hit.entity-store
 * - entity.search.hit.data-store
 * - entity.search.filtered.visibility
 * - entity.search.miss
 *
 * Minimum trace attributes
 * - component
 * - service
 * - operation
 * - entity
 * - source
 * - query or id summary
 * - raw-count
 * - visible-count
 * - filtered-count
 *
 * Metrics
 * Metrics are aggregate counters rather than per-request detail.
 *
 * Minimum counters
 * - entity.load.hit.entity-space
 * - entity.load.hit.entity-store
 * - entity.load.hit.data-store
 * - entity.load.miss
 * - entity.search.hit.entity-space
 * - entity.search.hit.entity-store
 * - entity.search.hit.data-store
 * - entity.search.miss
 * - entity.search.filtered.visibility
 *
 * Optional latency metrics
 * - entity.load.latency.entity-space
 * - entity.load.latency.entity-store
 * - entity.load.latency.data-store
 * - entity.search.latency.entity-space
 * - entity.search.latency.entity-store
 * - entity.search.latency.data-store
 *
 * Where to emit
 * Minimum hooks should be placed at the actual routing boundaries:
 * - ActionCallFeaturePart
 *   - start / try / fallback decisions
 * - UnitOfWorkInterpreter
 *   - direct path execution boundary
 * - EntityStore
 *   - hit/miss at store/data-store side
 *   - visibility filtering counts
 * - EntitySpace / Collection / Realm
 *   - resident hit/miss if cheaply available
 *
 * Builtin service direction
 * The aggregate counters should eventually be exposed through a builtin
 * metrics surface rather than ad hoc logging.
 *
 * Example future routes
 * - metrics.metrics.load-metrics
 * - metrics.metrics.load-entity-access-metrics
 *
 * Short-term implementation policy
 * - First, add source/fallback/visibility trace events.
 * - Second, add in-memory counters keyed by the stable source vocabulary.
 * - Third, expose the counters through a builtin metrics service.
 *
 * Non-goals for the first step
 * - no distributed metrics backend
 * - no OpenTelemetry redesign
 * - no persistence for metrics
 * - no CML or generator changes
 */
object entity_access_source_metrics_and_trace_direction
