# Conclusion HTTP disclosure

Status: normative. Scope: ordinary Subsystem HTTP and Loopback failures and
the existing HTTP server structured-error projection.

## Original execution outcome

Standard execution retains the exact `Consequence.Failure` Conclusion until
projection, together with final execution and response metadata. Resolution,
file preparation, ingress authentication, Operation authorization, presentation
restoration, evaluation admission, ActionCall, managed lease and UnitOfWork
ordering remain unchanged. Supplied-context entry points retain their context.
An explicitly returned HttpResponse is a successful explicit response.

Request-only execution returns the original failure. Existing Request-plus-HTTP
envelope APIs retain their materialized-success compatibility projection.
HTTP and Loopback project the same outcome without another execution or retry.
Public APIs, envelope arities, Job decisions and metadata headers are unchanged.

## Structured response and amount

JSON is the default structured failure format; YAML Accept selects YAML and
explicit plain clients retain their plain message and failure status. The root
`error` and its status/statusText/message/detailCode/appCode/appStatus retain
their meanings, including explicitly empty application status. `error.conclusion`
contains the existing Conclusion record vocabulary: status, observation,
taxonomy, cause, interpretation and disposition. Canonical flattened cause fields
select the first occurrence of each key, including the optional kind, matching
Record's own lookup semantics and preserving JSON object key uniqueness. Each
selected facet field comes from its already-sanitized facet. Cause additionally
carries ordered `facets`, each using the existing Facet record vocabulary; this
is the complete ordered semantic representation, preserving every sanitized
facet's multiplicity, identity and Parameter association. Duplicate facets are
neither sorted, dropped nor merged. Approved typed CNCF exception carriers
expose their fixed code/path/reason under cause `diagnostic`.

The query/form parameter `textus.output.conclusion`, or its alias
`cncf.output.conclusion`, accepts exactly `standard` or `full`. Absence defaults
to standard. Duplicate, contradictory, non-String, unknown or malformed input
falls back to standard without failing a business operation. Standard preserves
the current semantic record and omits only previous history. Full includes
previous under the same security ceiling. `error.conclusionDetail` records the
selection; `error.conclusionOmitted` explicitly names intentionally omitted
previous history. Request-free public error-record methods use standard.

Framework structured responses carry a fixed internal version marker; Http4s
consumes it to pass JSON/YAML through once and strips it and all legacy status
metadata from every wire response. The marker carries no Conclusion or secret
and is never admitted from a client header. Legacy explicit response fallback
status metadata remains supported.

## Independent security projection

One internal pure disclosure policy serves HTTP and logs. Amount, production,
debug and client mode flags do not grant authority. Recursive projection reuses
DiagnosticPayloadSummary's sensitive-key vocabulary and selected Operation
confidentiality. It covers Records, Maps, sequences, arrays, previous history
and sensitive Parameter facets' associated value/expected/actual/input/properties.
Ordinary numbers, paths, reasons, status, empty values and duplicate facets are
preserved. Original Conclusions and Records are never mutated.

Free text redacts labeled JSON, YAML and form credentials and Bearer/Basic
spellings. Available physical authorization, cookies and sensitive parameters
also provide literal credential values to redact from innocent text. Unknown
payloads are explicitly redacted without arbitrary toString or reflection.
Physical Http4s normalization preserves the original Cookie header strings as
server-owned, diagnostic-only evidence in its internal request source. All
Cookie headers contribute their full strings and every nonempty cookie value
to literal redaction, including opaque non-session cookies. Normalized
authentication headers and session precedence remain independent: raw Cookie
and Set-Cookie headers are still suppressed, and diagnostic evidence cannot
become provider attributes, application properties or arguments, persistence
data, or response headers. Clients cannot supply or replace this private
evidence through any casing of an incoming header or equivalent property name.
Regular and multipart ingress and structured decode-failure projection use the
same custody rule; credential assembly adds no body read or execution. This adds
no public header, wire schema, authentication capability or disclosure control.

Unknown exception messages are always redacted. HTTP also redacts exception
class/stack, source/origin/environment/endpoint/database/file/call-tree internals.
Logs may retain safe exception type and stack-location structure. Approved
typed diagnostic strings still cross the same safety filter. Nested payloads
are bounded at 64 with an explicit redaction marker at the boundary.

## Detailed failure observation

The existing `execute_failed` observation records full sanitized Conclusion
history independently of HTTP amount and mode, once for each failed ingress
execution. It includes safe logical route identifiers and available trusted
trace/correlation metadata, never raw Request text, headers, payload or raw
exceptions. The same sanitized Conclusion JSON is present in the observation
message and attributes, preserving detail for LogBackend's textual fallback.
Logging failure does not retry an operation or change its execution result.
Action failure text consumes the same full sanitized Conclusion projection,
without appending the original exception message. Its complete formatted text,
including action, trace and execution metadata, crosses the same textual
disclosure policy using available request credentials and field confidentiality.
The original context, identifiers and Conclusion retain their execution semantics.
Diagnostic construction and log emission are best-effort observations: NonFatal
logger, policy or encoder failures cannot replace the original operation outcome.
Log retention, sink access and deployment policy remain deployment concerns.

## Executable correspondence

`ConclusionDisclosureSpec` covers semantic fidelity, range, security, typed
diagnostics, bounded recursion and immutable generated payloads.
`DisplayAuthenticatedHttpTransportSpec` extends the real Bearer-authenticated
SQLite transport with semantic 4xx/no-write, amount/logging and YAML/plain
contracts, physical non-session Cookie secrecy across every retained backend,
and rejection of forged private diagnostic headers. `ConclusionDisclosureSpec`
also fixes immutable server-owned Cookie assembly and direct core Cookie
compatibility. `HttpFailureTransportMetadataSpec` retains legacy fallback contracts
and specifies internal marker stripping and JSON/YAML passthrough. Validation
and acceptance are parent-owned; this document records no executed result.
