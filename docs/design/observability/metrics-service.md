# Metrics Service

## Purpose

OB-05 defines CNCF's in-process metrics read model and query surface.

Metrics are operational aggregates for dashboards, admin inspection, and later
export. They are not error semantics, diagnostic payload archives, or
OpenTelemetry wire contracts.

## Metrics Snapshot

The runtime metrics snapshot contains low-cardinality metric points.

Each point has:

- `scope`: metric family, such as `web.request` or `blob.operation`;
- `name`: metric name within the scope;
- `labels`: a whitelisted low-cardinality label record;
- `count`;
- `error_count`;
- optional duration summary fields.

Supported OB-05 scopes are:

- `web.request`
- `action.execution`
- `authorization.decision`
- `dsl.chokepoint`
- `validation`
- `operation-request-validation`
- `blob.operation`
- `diagnostic-payload.externalization`
- `otel.export`
- `entity-access`

Default labels exclude high-cardinality values such as raw paths, entity ids,
job ids, payload ids, request parameters, and user/session ids.

## Metrics Component

The builtin `metrics` component exposes:

- `metrics.load_entity_access_metrics`
- `metrics.load_runtime_metrics`
- `metrics.load_metrics_catalog`

`load_runtime_metrics` returns the unified in-process snapshot. The existing
entity-access operation remains compatible and is also represented in the
unified snapshot.

## Admin Projection

`/web/system/admin/observability/metrics` renders metric scope cards and compact
tables for counters, error counts, durations, and selected labels.

Metrics rows can link to OB-04 diagnostic detail pages when a whitelisted
`diagnostic_key` label maps to a supported diagnostic scope. The label remains
a grouping hint; the diagnostic detail page remains the source for structured
failure facts.

## Export Boundary

OB-05 remains the in-process source of truth. OB-06 adds an OpenTelemetry
projection boundary described in
`docs/design/observability/opentelemetry-export-policy.md`.

`metrics.load_runtime_metrics` may export the current runtime snapshot to OTLP
HTTP when `textus.observability.otel.*` is enabled. Export failures are
non-fatal and are counted under the low-cardinality `otel.export` scope.
