# Job User and Operator Experience Specification

status=accepted
phase=69.6
slice=JM69-08A

## Scope and compatibility

`job_control.job_experience` is a projection over canonical Job management. It
does not introduce a Job store, cache, lifecycle, retention clock, or result
state. Existing Job management APIs, case-class arities, cursor wire values for
the empty default policy, protocol names, and control outcomes remain valid.

`mine`, `application`, and `operator` are the only scopes. An application is
normalized with `NamingConventions` and rejects blank, separator, and traversal
input. Scope never accepts a subject or recipient selector. Mine requires a
non-anonymous session-first owner even when that caller has broad capabilities.
Application requires canonical broad read capability and stored
`web.application-job=true` plus stored normalized `web.app`. Operator requires
`job_admin` or `content_admin`. A cursor combines the nonempty scoped visibility
key with the management filter fingerprint; it consequently rejects a changed
scope, application, caller, authorization set, snapshot, or malformed token.

## Projection, results, diagnostics, and controls

The public view keeps canonical status and maps it to Queued, Running, Paused,
Cancelled, Completed, or Failed. Server-resolved Japanese receives Japanese
labels; all other locales receive English. Active Jobs report indeterminate
progress and recorded task count. They do not infer completed-task counts or a
percentage from a bounded task page.

Live successful results are displayed as escaped text. Failed results use the
fixed safe failure wording. Pending terminal results are unavailable, and
unavailable-after-restart remains distinguishable. No result is reconstructed,
retained, or polled after it is terminal/unavailable.

Records and HTML expose bounded Job id, status, persistence, origin, timestamps,
retry/count fields, and the admitted controls. They omit subject/session, input,
debug/request/calltree data, free-form failures, task error messages, timeline
notes, and diagnostic payload references. Missing and denied exact reads both
return the same operation-not-found result. Operator diagnostics use bounded
canonical task and timeline pages and expose only ids, kinds, status, and time.

Runtime Submitted/Running Jobs may offer Cancel/Suspend; Suspended offers
Cancel/Resume; Failed/Cancelled offers Retry; Succeeded offers none. Durable
records advertise no unsupported control. A control reauthorizes the exact
scoped Job and calls the engine once under the existing control policy. The
engine owns all transition and replay semantics.

## Notification inbox and forwarding

`UserNotificationInboxProvider` is optional and additive to the existing send
SPI. Providers own persistence, deduplication, expiry, unread/read state,
authoritative recipient/application filtering, and opaque cursor identity.
CNCF validates provider page identity, count bounds, recipient/application,
unread state, expiry, and same-origin absolute-path links before projecting.
Provider absence, inconsistency, rejection, or exception is structured as an
unavailable provider result and never changes a Job.

Public notification views omit recipient, dedupe key, provider identity, and
private metadata. Exact read/mark-read treats denied, missing, and expired as
not found. Mark-read calls the provider once after exact admission and preserves
provider first-read/replay semantics. Only `notification_admin` or
`content_admin` can use the bounded provider update entry; it cannot alter
identity, recipient, application, creation time, dedupe key, or read time.

Forwarding has no process-global sent set. Every matching event carries its
stable `(jobId, trigger)` dedupe key to the provider, which owns replay and
restart idempotence. Forwarded title/body contain bounded job identity, status,
and safe recovery wording. A false acceptance, provider failure, absence, or
nonfatal exception records a forwarding failure and remains retry-eligible.

## Protocol and Web

The descriptor service is `job_control.job_experience`: `list_my_jobs`,
`list_application_jobs`, `list_operator_jobs`, `get_job_experience`,
`get_job_diagnostics`, `control_job_experience`, `list_my_notifications`,
`get_my_notification`, `mark_notification_read`, and `get_operator_catalog`.
Reads explicitly project GET; `control_job_experience` and
`mark_notification_read` explicitly project POST. The shared codec rejects
invalid scope, boolean, integer, status, and origin input.

The server-rendered routes are `/web/system/jobs`, `/web/{app}/jobs`,
`/web/{app}/admin/jobs`, and `/web/system/admin/jobs`, with equivalent exact
detail paths. They resolve the Job-control execution context and retain both
descriptor ingress checks and scoped facade authorization. Notification pages
use the generic resolved provider; no component name is hardcoded. Private
pages and provider/error responses are no-store, and all text/attributes and
opaque Next links are escaped.

Controls and mark-read use canonical `/form/job_control/job_experience/...`
POST forms with the existing CSRF cookie/token and no identity/capability/return
URL hidden field. A successful command has the scope-specific PRG destination;
existing descriptor redirects take precedence. GET does not change read state.

Initial HTML provides headings, labelled filters, captioned tables, scoped
headers, result/progress text, empty/error states, keyboard controls, manual
refresh, and an aria-live polling state. Only active Submitted/Running exact
details poll, with same-origin credentials, one request, a five-second interval
and abort timeout, at most sixty attempts, and textContent-only updates. It
stops for paused/terminal/hidden/unload/error/redirect/invalid response or user
pause.

## Executable specification mapping

| Requirement | Executable evidence |
| --- | --- |
| UX69-01 | `JobExperienceSpec`, `JobExperienceHttpSpec` scope/filter rejection and compatibility behavior |
| UX69-02/03 | `JobExperienceSpec` canonical vocabulary, progress, result and control projection |
| UX69-04/05 | `UserNotificationInboxSpec`, `UserNotificationProviderRuntimeSpec` provider, identity, replay, and failure behavior |
| UX69-06 | `JobExperienceProtocolSpec` descriptor/OpenAPI discovery |
| UX69-07/08 | `JobExperienceWebSpec`, `JobExperienceHttpSpec` renderer, CSRF form, Web codec, and polling bounds |
| UX69-09 | All named specs use `AnyWordSpec`, `GivenWhenThen`, and matchers with adjacent clauses |

## Frozen scenario ledger

`JobExperienceSpec` carries J1 Mine session-first ownership, J2 Application and
Operator admission, J3 scoped cursor/page binding, J4 vocabulary/result safety,
J5 direct-control outcome projection, and J6 bounded diagnostics. Its adjacent
Given/When/Then clauses identify the subject, ingress, and safe outcome.

`UserNotificationInboxSpec` and `UserNotificationProviderRuntimeSpec` carry N1
provider dedupe, N2 global/application cursor lists, N3 mark-read replay, N4
bounded administrator update, N5 expiry/link/malformed page rejection, N6
provider absence/refusal/exception retry safety, and N7 forwarding redaction.
Their adjacent Given/When/Then clauses identify the provider record and the
same-or-not-found or unavailable result.

`JobExperienceProtocolSpec` carries P1 generated Help/meta and OpenAPI
discovery followed by installed resolver invocation, P2 shared parse rejection,
permission/default/bound outcomes, P3 bounded read/control and notification
read records including replay, and P4 catalog availability. `JobExperienceWebSpec`
carries W1 escaped renderer and opaque full-query Next links, W2 accessible
CSRF/localized no-JavaScript markup, and W3 bounded polling.
`JobExperienceHttpSpec` carries actual Http4s H1 Mine/Application/Operator
Web/detail/REST admission, normalized scope, count, and Next behavior; H2
forged identity plus descriptor application disablement/alias admission; H3
rendered CSRF control success, replay, and descriptor redirect priority; H4
stale-CSRF counter non-mutation; H5 provider-backed global/application inbox
GET and first-read replay PRG; H6 owner/foreign await isolation; H7 emitted
locale polling URL, terminal/unavailable JSON and first paint; and H8 admitted,
denied, form, notification, and anonymous private accessible cache outcomes.

## Phase 69.7 handoff

Retention/deletion/maintenance, durable integrity, expiry clocks, result-store
migration, provider delivery implementation, and repository-wide aggregate
validation remain owned by Phase 69.7.
