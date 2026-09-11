# Durable Job Management Query Contract

status=normative
phase=JM69-04A
source-authority=`PHASE-69.2 / JM69-04 / JM69-04A`

This contract defines the canonical authorized cursor-page boundary for job
management. It is additive to the existing single-job query facade and does
not alter `listJobs(limit, persistentOnly)`.

## R1 Request and summary vocabulary

`JobManagementQuery` accepts only `persistentOnly` (default `true`), one
optional `JobStatus`, one optional `JobDataOrigin`, a positive `limit` no
greater than 100, and an optional opaque continuation cursor. There is no
text search, administrative search, task, timeline, result, input, control,
retention, protocol, Help, HTTP, CLI, or payload selector.

A `JobManagementSummary` contains only the job id, status, persistence,
origin, created time, updated time, and optional scheduled-start time. It
contains no submitter or raw subject identity, `JobInput`, full
`OperationResponse`/result, calltree, debug parameters, task data, or timeline
data.

## R2 Candidate precedence, authorization, and ordering

For each job id, the current live `JobRecord` is the candidate when present;
otherwise the retained durable terminal projection is the candidate. Live
durable records take precedence over live runtime records for the same id.
Candidates are de-duplicated by job id.

The `JobQueryPolicy` is evaluated for every candidate before any filter,
summary, count, snapshot fingerprint, or cursor is derived. A denied candidate
is omitted without an authorization error, so it cannot affect observed page
entries, counts, or continuations. Authorized candidates matching the bounded
request filters are ordered by `updatedAt` descending and then `JobId`
ascending.

## R3 Continuations and concurrent mutation

The cursor is URL-safe Base64 of a deterministic versioned payload. It binds a
version, canonical request-filter fingerprint, caller-visibility fingerprint,
authorized filtered snapshot fingerprint, and next offset. Fingerprints are
SHA-256 digests; the token carries neither raw subject data nor job payload
content.

Every continuation recomputes the authorized sorted snapshot. A malformed or
unsupported-version token, filter mismatch, caller-visibility mismatch, or
out-of-range offset fails with an `invalid cursor` Consequence. A changed
authorized snapshot fails with an `expired snapshot` Consequence. It never
returns a page assembled from two snapshots. If durable records are unchanged
across restart and the same authorized visibility is restored, the canonical
ordered snapshot and cursor binding remain usable; this contract adds neither
durable cursor storage nor a signing-key facility.

## R4 Outcomes and exclusions

Success returns bounded summaries, an authorized total count, and a next cursor
only when another page exists. Invalid request limits and invalid continuations
fail structurally; denied candidates remain unobserved. This contract does not
change `query(jobId)`, `queryVisible(jobId)`, `listJobs`, durable record format,
recovery, controls, exact result/payload retrieval, task/timeline retrieval,
or any transport surface.
