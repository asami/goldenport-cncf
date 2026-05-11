# Diagnostic Payload Externalization Policy

## Purpose

This document is the Phase 24 normative policy entry point for diagnostic
payload storage and projection.

Diagnostic payloads are the request, response, result, and derived debug values
captured for operational inspection. They may appear in CallTree, Task
calltree, execution history, Job diagnostics, Task Execution Tree, and
Web/admin/debug projections.

Diagnostic payloads are not business records and are not payload archives.
They exist to explain execution behavior, failure shape, and performance
characteristics without making diagnostics unbounded or unsafe.

## Default Rule

Diagnostics store compact summaries and references by default.

Full result/response/request bodies must not be embedded unbounded in:

- CallTree;
- Task calltree;
- execution history;
- Job diagnostics;
- Task Execution Tree;
- Web/admin/debug diagnostic records;
- metrics or telemetry export.

Small values may be stored inline when they are safe to expose and useful for
debugging. Larger values require summary metadata and, when explicitly enabled,
an external payload reference.

## Policy Terms

`inline` means the diagnostic record carries the value itself. Inline values are
for small scalar or small structured values after redaction.

`summary` means the diagnostic record carries metadata such as value kind, byte
or character size, record or element count, paging summary, status, outcome, and
truncation/externalization state.

`truncated` means the diagnostic record carries only a bounded prefix or
preview plus summary metadata. Truncation must be explicit in the projection.

`externalized` means the full diagnostic payload is written outside the primary
diagnostic record, such as to a diagnostic file or object store.

`payload reference` means the diagnostic record carries a reference to an
externalized payload. A reference is not automatically public and must be
resolved through authorization policy.

`retention` means the policy that controls how long inline, summarized, and
externalized diagnostic payloads remain available.

`redaction` means confidentiality filtering applied before any inline display,
summary generation, external write, telemetry export, or dashboard rendering.

`authorization` means access control for viewing diagnostic records and for
resolving payload references.

`cleanup` means the mechanism that removes expired externalized payloads and
any related references.

`destination` means the configured storage target for externalized payloads,
such as a local diagnostic directory or object store.

## Redaction Order

Redaction and confidentiality filtering happen before every diagnostic output
surface:

- inline display;
- summary generation;
- truncation preview;
- external file or object write;
- Web/admin/debug rendering;
- metrics aggregation;
- telemetry export.

Name-based redaction may remain as a fallback for legacy inputs, but CML
`confidentiality` metadata is the preferred source for field-level policy.

## Externalization Boundary

Externalization is opt-in/configured until production policy is implemented.

Externalized payload capture must not be enabled as a production default before
these policies exist:

- redaction before write;
- retention;
- authorization;
- cleanup;
- destination configuration;
- failure diagnostics for disabled, unavailable, or failed externalization.

OB-02 defines the concrete summary/reference model shape. OB-03 adds the
external store boundary and runtime configuration keys.

## Summary / Reference Record Shape

The canonical runtime summary model is `DiagnosticPayloadSummary`.

Stable fields are:

- `kind`: semantic payload kind such as `record`, `json`, `yaml`, `scalar`,
  `void`, `opaque`, `query`, or `conclusion`.
- `valueType`: implementation-facing value type when known.
- `status` / `outcome`: operation or payload outcome metadata.
- `sizeBytes` and `charCount`: bounded size metadata.
- `fieldCount`, `recordCount`, and `elementCount`: structural counts.
- `offset`, `limit`, `fetchedCount`, and `totalCount`: paging/result-set
  summary values.
- `inline`: small safe inline value, or `false` when the payload is summarized
  only.
- `truncated` and `truncationReason`: explicit truncation metadata.
- `externalizationStatus` / `externalizationReason`: `disabled`,
  `not_matched`, `stored`, `unavailable`, `failed`, or `not_supported`
  externalization state.
- `payloadReference`: optional external payload reference.

Record projections use snake-case keys for Web/admin/debug compatibility:
`kind`, `value_type`, `size_bytes`, `char_count`, `field_count`,
`record_count`, `element_count`, `fetched_count`, `total_count`, `inline`,
`truncated`, `truncation_reason`, `payload_reference`, and optional
`payload_href` / `external_href`.

`DiagnosticPayloadReference` can carry already-known `href`, `url`, `path`,
`ref`, `storage`, `contentType`, and `sizeBytes`. OB-03 can create references
for `local-file` and `blob-store` destinations.

## External Store Configuration

Externalization is disabled by default:

- `textus.observability.payload.externalization.enabled=false`
- destination: `local-file` or `blob-store`
- local root: `target/cncf.d/observability/payloads`
- default threshold: `1200` bytes
- default payload targets: `result,response`
- optional local cleanup: `retention.days`

Develop/test mode may use local-file externalization without an explicit
destination. Production mode must specify a destination when externalization is
enabled; missing or unknown production destinations are deterministic runtime
configuration failures, not per-payload warnings.

Selectors can restrict externalization by exact
`component.service.operation`, operation substring, and payload kind. Request
parameters such as `textus.debug.payload.externalize=true` can opt in for a
single development/test request when request override is allowed.

`blob-store` uses CNCF `BlobStore` for persistence. S3 or another object store
is therefore an implementation of BlobStore, not an observability-specific
storage API. BlobStore-backed diagnostic payload references are resolved through
the system-admin observability payload route. Non-durable in-memory BlobStore
backends are not valid externalization destinations because the generated
reference cannot be reliably resolved later.

Default inline thresholds are centralized in policy: inline bytes `1200`,
inline fields `20`, inline elements `20`, and text preview bytes `0`.
Generic scalar/string values are non-inline by default to avoid accidental
token, password, or session leakage.

Typed `RecordResponse` / generated result value-class payloads can be
secret-aware because CML/schema field metadata can identify
`confidentiality=secret|sensitive|personal` attributes before projection.
Generic `Json` and `Yaml` operation responses are not treated as secret-aware
payloads because CNCF cannot reliably bind arbitrary JSON/YAML fields back to
CML result attributes. Therefore JSON/YAML operation responses are summarized
with `inline=false` by default in diagnostics. Operations that return secret or
personal values should use a typed result model/value class rather than raw
JSON/YAML. JSON/YAML/scalar/opaque externalization is disabled by default and
requires the explicit unsafe debug option; even then, name-based redaction is
applied before the payload is written.

## Relationship to CallTree and Job Diagnostics

CallTree, Task calltree, execution history, and Job diagnostics may all refer to
payload summaries and payload references. They must not become the long-term
storage location for full action, UnitOfWork, Space, I/O, Task, or Job
result/response bodies.

CallTree action/UoW/space/I/O nodes use summary records for `request`,
`web_parameters`, `query`, `response`, and `result` payloads. Retained
execution history stores a structured result summary plus a short display
string derived from it for existing list views. Saved Job calltrees and
task-local calltree projections must contain summarized payload records, not raw
full result bodies.

The CallTree-specific projection contract is described in
`docs/design/observability/calltree-runtime-result.md`. This policy is the
cross-cutting storage and externalization rule that CallTree and Job
diagnostics must follow.

## Relationship to OpenTelemetry

OpenTelemetry export is not the internal source of truth for CNCF diagnostics.
Telemetry export must apply the same redaction and bounded-payload rules before
data leaves the CNCF runtime.

OB-06 owns the concrete OpenTelemetry boundary and export policy.

## Phase 24 Driver

`cncf-samples` sample 13 is the concrete Phase 24 observability integration
driver. Later Phase 24 slices should use it to demonstrate docker-compose
wiring to the actual observability backend.

OB-01 does not implement sample code or docker-compose changes.
