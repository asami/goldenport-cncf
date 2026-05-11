# OpenTelemetry Export Policy

This note is the Phase 24 OB-06 policy for CNCF OpenTelemetry integration.

OpenTelemetry is an export boundary. CNCF's internal observability model remains
authoritative: CallTree, structured diagnostics, diagnostic payload summaries and
references, runtime metrics, Job/Task context, and local admin views stay
CNCF-native. OTEL receives bounded, redacted projections only.

## Signals

Trace export maps CNCF execution structure to OTEL spans:

- Action execution becomes a root span when no parent trace exists.
- CallTree flow nodes become child spans where the CallTree is available.
- Action, UoW, space, and I/O kinds are carried as attributes, not as separate
  CNCF persistence records.
- Job, Task, and Saga context is included when present; absent context is
  projected as `none` where useful for diagnostics.

Metrics export maps the OB-05 `RuntimeMetricsSnapshot` to OTEL metrics:

- counter-like points become monotonic sums;
- duration averages become gauge points;
- labels stay limited to the low-cardinality label sets declared in the CNCF
  metrics catalog.

Structured diagnostics and `Conclusion` records may be exported as trace events
or log-style records in later work. Message text is evidence only and must not
become the semantic grouping key.

## Correlation Attributes

The default CNCF OTEL projection uses these attributes:

- `cncf.component`
- `cncf.service`
- `cncf.operation`
- `cncf.job.id`
- `cncf.task.id`
- `cncf.saga.id`
- `cncf.diagnostic.key`
- `cncf.detail_code`

Payload summaries and references use `cncf.payload.<kind>.*` attributes. Raw
request/result bodies are not exported by default.

## Safety

Redaction and CML confidentiality filtering happen before OTEL export.

Default export excludes:

- raw request bodies;
- raw result/response payload bodies;
- high-cardinality metric labels such as entity ids, job ids, payload ids,
  request parameters, user ids, and session ids.

Payload summaries and payload references may be exported. Payload reference
resolution remains governed by CNCF's system-admin observability route and any
future production authorization/retention policy.

## Runtime Config

OpenTelemetry export is disabled by default.

```text
textus.observability.otel.enabled=false
textus.observability.otel.endpoint=
textus.observability.otel.protocol=otlp-http
textus.observability.otel.traces.enabled=true
textus.observability.otel.metrics.enabled=true
textus.observability.otel.logs.enabled=false
```

V1 supports OTLP HTTP only. In develop/test mode, enabling OTEL without an
endpoint defaults to `http://127.0.0.1:4318`. In production, an explicit endpoint
is required.

Export failures are non-fatal. CNCF records export outcome metrics under
`otel.export` and continues the business operation.

## Demo Topology

`cncf-samples` contains two OB-06 drivers:

- `13-observability-jaeger`: one-container Jaeger all-in-one proof for direct
  OTLP trace export.
- `13.a-observability-stack-lab`: OpenTelemetry Collector, Jaeger, Prometheus,
  and Grafana. CNCF sends OTLP to the Collector only; the Collector routes
  traces and metrics to the demo backends.

CNCF does not depend on Jaeger, Prometheus, Grafana, or S3 APIs directly.
