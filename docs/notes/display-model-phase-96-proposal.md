# Phase 96 Display Model and DisplayService proposal

Document role: non-normative specification proposal for the Phase 96 initial
vertical slice. Proposed protocol schema: `cncf.display-model.v1`, major
version `1`; neither is frozen or admitted by this note.

The behavior described below is proposed for review. Requirement-style wording
describes the candidate contract, not an accepted implementation obligation.
Promotion into design/spec requires the corresponding review and acceptance.

## Source, authority, and scope

The concrete sample input is Editing Studio's
[ESS2-01 sample journal](../../../../Project2026/nict-editing-studio/docs/journal/2026/10/2026-10-01-display-model-phase-96-sample.md),
sample `ess.display-reference.v1` revision `1`. Its identity, state,
projection, and writable-field bindings are application-owned; they are not
generic CNCF semantics. The UI/prototype driver is
[Editing Studio app Phase 4](../../../../Project2026/nict-editing-studio-app/docs/phase/phase-4.md);
[app Phase 5](../../../../Project2026/nict-editing-studio-app/docs/phase/phase-5.md)
owns real-server integration and aggregate/E2E acceptance.
This note proposes the minimum generic contract to examine against
that sample; it does not admit a production CaptureSession, Knowledge
Candidate, Workbench review, or KnowledgeHub admission API.

Semantic View remains CNCF's meaning-preserving read contract. A Display
Projection consumes an already obtained semantic View and application
projection definition, and produces a target-neutral Cozy
`Model(Metadata, Screen)` conforming exactly to
`CozyLogicalUiRuntime.minimumContract` (`cozy.logical-ui-runtime.v1`, version
`1`). It does not change the semantic View, write the source, or expose a
public Aggregate/View runtime service. The component-scoped DisplayService
owns UI-facing Display reads and standard mutations. Ordinary Business
Operations retain their existing REST Operation authority.

## Logical protocol payload

A Display response's `data` payload carries one versioned Display object. Its
logical fields are the exact protocol schema and major version; component
identity; resource-type identity; presentation kind (`list` or `detail`);
one Cozy runtime `Model`; and Display metadata. The Cozy `Model` preserves
its own exact descriptor, provenance, screen, ordered sections/items/fields,
typed values, roles, and actions. CNCF must not add source-property names,
mutation targets, routes, callbacks, or authorization tokens to Cozy `Field`
or `Action`. The protocol codec must be lossless for every admitted Cozy value
variant and must preserve order and opaque identities. Unsupported protocol
or Cozy descriptor versions are rejected, not downgraded or silently repaired.

Display metadata is separate from the Cozy model and contains:

- exact component and resource-type identity, with per-resource opaque ID and
  expected source revision for a Detail or mutation result;
- capability flags for read/query/create/update/delete, each explicit rather
  than inferred from missing handlers;
- for each writable field ID, its accepted Display input value type,
  application binding identity, constraints needed for a generic editor, and
  the current resource's availability/editability;
- for derived/unbound fields, explicit read-only status; and
- for available Business Operation actions, an application-owned operation
  binding descriptor for the ordinary REST Operation path, not a mutation
  command or an executable Cozy `Action`.

An application binding identity names a registered server-side converter and
Entity/Aggregate update target. It is not a raw source path that a client can
arbitrarily choose. The server resolves and authorizes the binding; the
client sends only admitted field IDs and typed Display values. A formatted,
combined, or derived Display value never implies an inverse update mapping.

The protocol uses the existing CNCF REST major-version namespace and success
envelope (`data` is the business payload). The exact route, JSON member
spelling, and media-type binding must be published with the Phase 96 codec
before Flutter Core consumption; no client may infer them from this logical
contract or from the fixture. Once published for v1, codec changes follow the
existing REST compatibility rules: no breaking change within the major
version, and unknown/incompatible versions fail explicitly. This paragraph
does not authorize an application-specific CRUD REST surface.

## DisplayService behavior

`read` selects one component/resource-type/resource ID and returns its Detail
Display plus current revision/capabilities. `query` returns an ordered List
Display; ListItem IDs are opaque resource IDs and the List's DetailTarget is
the logical Detail screen ID, not a URL. A missing or foreign resource never
falls back to another component or ID. The projection must preserve the
application's deterministic field/section/item order and value conversion;
client-specific Widget/layout choices are outside CNCF.

`create`, `update`, and `delete` are distinct standard Display mutations and
are accepted only if the selected resource type advertises the corresponding
capability. A disabled capability is a structured unsupported result, not a
successful no-op. Create takes admitted typed initial field inputs and returns
the created ID, revision, and projected Detail. Update takes the exact target
identity and a nonempty set of unique admitted scoped field locators
and typed values; delete takes target identity. Update/delete additionally
require an observed revision through framework transport metadata. On
success the owning Entity/Aggregate validates and commits atomically, then
the current semantic View is projected again. No mutation patches a View or
Display directly. The resulting revision and Display must agree with the
next read once the View has caught up. A failed pre-commit
validation/conflict/authorization/capability check has no source mutation and
returns a structured failure through the existing CNCF error model. A
post-commit projection failure cannot be represented as an uncommitted update.

The service must reject missing/foreign identity, stale revision, duplicate
field ID, unknown or read-only field, wrong Display value type, violated
application constraint, and unsupported protocol/Cozy descriptor. These
failure categories stay distinct; neither an opaque string-only failure nor
best-effort conversion is sufficient. Authorization is evaluated by the
existing component/Operation security path and cannot be granted by Display
metadata or a Cozy action descriptor.

Business Operation actions are descriptive. Invocation uses the ordinary
CNCF REST Operation interface and its own input/authorization contract, never
DisplayService's standard mutation endpoint. After a successful Business
Operation the initial client consistency rule is explicit DisplayService
reload; a displayed action or a successful REST response does not itself
promise a refreshed Display. Push invalidation is deferred.

## Required proof and release boundary

The first focused proof replays the ESS2-01 fixture: ordered List and matching
Detail, `title`/`status`/`description` roles, exact Cozy descriptor, title
update at managed revision `1` and reprojected revision `2`, read-only `stage`
rejection, stale revision conflict, explicit unsupported create/delete,
deterministic lossless codec round-trip, and Business Operation/direct-REST
plus reload separation. Property-based checks cover identity preservation,
unique field IDs, value-type rejection, and codec round-trip over admitted
values. Phase 96 acceptance additionally publishes the exact v1 physical
codec and route binding, demonstrates the component-scoped DisplayService
without public Aggregate/View runtime APIs, and supplies focused evidence to
Flutter Core and the app parent. No child full-test run is a prerequisite to
the scoped handoff; the app parent owns aggregate/end-to-end verification.

## Preparation findings and proposed wire binding

The existing contracts impose three constraints on this proposal:

- [Canonical REST resolution](../spec/path-resolution.md) uses
  `/rest/v1/{component}/{service}/{operation}`. The proposed generated service
  selector is `display`, with no new top-level REST namespace.
- [Entity revision and Aggregate mutation](../spec/entity-conflict-and-conditional-transition.md)
  start persisted managed revision at `1`. Observed revision is transport
  metadata, not a business parameter; the strict Aggregate APIs are
  `aggregate_update_observed` and `aggregate_command_observed`.
- Cozy IDs are container-scoped. A Detail edit therefore needs the logical
  screen ID, section ID, and field ID as well as resource identity. The
  producer's snapshot provenance is distinct from managed Entity revision.

The following is a candidate operation binding, subject to Phase 96 review:

| Operation | HTTP method | Proposed request binding | Result |
| --- | --- | --- | --- |
| `display.query` | GET | `resourceType`, `screenId` query properties | List Display |
| `display.read` | GET | `resourceType`, `screenId`, `resourceId` query properties | Detail Display |
| `display.create` | POST | registered resource type and typed initial field inputs | created Detail Display |
| `display.update` | PUT | scoped field inputs in JSON; strong `If-Match` header | current Detail Display |
| `display.delete` | POST | resource identity in JSON; strong `If-Match` header | deletion receipt; client reloads List |

POST for the delete Operation fits the current Operation dispatcher, which
supports GET/POST/PUT. The first sample advertises create/delete as unsupported.
Queries are a bounded deterministic List in this slice; pagination/filter
grammar is not inferred from app fields. Business actions carry registered
component/service/operation selectors and their admitted input descriptor,
resolved against the ordinary REST binding. A metadata string must not become
an arbitrary remote invocation URL.

For the proposed first update, the body would be:

```json
{
  "schema": "cncf.display-mutation.v1",
  "version": 1,
  "resourceType": "collecting-reference",
  "resourceId": "capture:book-1",
  "definitionVersion": "ess.display-reference.v1:1",
  "screenId": "capture-detail",
  "changes": [
    {
      "sectionId": "main",
      "fieldId": "title",
      "value": {"type": "text", "text": "日本の民話・改訂"}
    }
  ]
}
```

The request has `Content-Type: application/json` and
`If-Match: "revision-1"`; the component selector comes from the registered
route, not an editable JSON field. `definitionVersion` identifies the exact
application projection/binding definition, separately from wire schema
version and Entity revision. A changed definition is rejected before resolving
field inputs against a different mapping. A field locator is unique as the
tuple `(screenId, sectionId, fieldId)`, not merely `fieldId`.

Candidate Display `data` members are `schema`, `version`, `componentId`,
`resourceType`, `definitionVersion`, `model`, and `metadata`. The `model`
encodes Cozy `metadata` and `screen`; the screen has discriminator `type`
(`list` or `detail`) and the public case-class fields. Optional section title
is absent when `None`. Runtime-derived purpose/display/pattern are reconstructed
from screen/action type rather than independently mutable wire fields.
Cozy descriptor/provenance members retain their public names and exact values.
The outer `metadata` contains explicit type capabilities, per-resource
identity/revision/entity tag and availability, scoped field bindings, and
scoped action bindings. A List supplies per-item resource metadata; its
snapshot provenance is never used as the mutation precondition.

Candidate typed values use the following closed JSON representations:

| Cozy value | Proposed JSON object |
| --- | --- |
| `TextValue` | `{"type":"text","text":"..."}` |
| `NumberValue` | `{"type":"number","number":"<decimal>"}` |
| `BooleanValue` | `{"type":"boolean","boolean":true}` |
| `TimestampValue` | `{"type":"timestamp","instant":"<Instant text>"}` |
| `MissingValue` | `{"type":"missing"}` |

Decimal text avoids client binary-float conversion. The timestamp is parsed
as `java.time.Instant` and encoded in its canonical text form. Text labels,
IDs, and field values are preserved; typed nulls, unknown tags, duplicate JSON
keys, and missing required fields fail explicitly. Array ordering remains
application ordering. Canonical member ordering and the full allowed metadata
member sets still need codec review before this candidate can be promoted.

## Revision adapter and commit/projection questions

The existing `EntityRevisionTransport` supplies the strong `"revision-N"`
representation. The current HTTP adapter's observed-revision handling is
restricted to PUT admin/entity requests; it does not automatically bind a
generated DisplayService or a Business Operation. Phase 96 therefore needs an
explicit framework adapter for the registered mutation binding. This adapter
would carry the parsed revision as CNCF metadata to the strict Aggregate path;
it must not add `expectedRevision` to ordinary domain input. Missing or malformed
observed revision is a pre-commit error. Collections used for this proof must
admit managed revision and optimistic concurrency before assembly.

The proposed standard update policy is `WriteIfChanged + ObservedRequired`
with optimistic comparison. A changed write advances revision once through
CNCF; a current-revision no-op leaves revision unchanged. Stale revision has
precedence over equality. Create has no observed-revision requirement.

View is event-driven under the accepted
[Aggregate/View boundary](../design/aggregate-view-semantic-boundary.md).
The bounded proof can process emitted events synchronously before reprojection.
The generic response proposal distinguishes committed mutation from ready
Display: if post-commit projection is unavailable, the response must retain
committed identity/revision and report Display pending through the existing
execution/diagnostic envelope. It must not report that no write occurred or
instruct the client to repeat the command. The exact pending representation
and read-your-write boundary need Phase 96 review, including compatibility
with accepted-job execution. A fresh Display may be presented only when its
source revision corresponds to the committed result.

## Remaining promotion checks

Before promoting this note, review the registered service name/collision rule,
request-definition mapping for nested typed field inputs, complete metadata
codec shape and compatibility policy, post-commit/pending representation,
deletion receipt, and server-owned binding-definition versioning. Recheck the
current Entity and execution contracts at Phase 96 start because CNCF parallel
development may change the applicable baseline. The sample and wire objects
above are preparation proposals, not published protocol versions.

## UI-driven Presentation Context extension proposal — 2026-10-02

The [ExecutionContext projection proposal](display-protocol-presentation-context-proposal.md)
records a generic context structure alongside logical Display/Operation data.
It includes admitted experiment/arm, presentation/locale and correlation
families with explicit version, lifetime and propagation rules. Core transports
context; TFAF selects registered UI definitions using supplied arm identity;
app screen code does not interpret experiments or copy context between calls.

Resolve physical codec/header binding and cross-operation continuation with
CNCF's existing admission and execution contracts. The separate context note
also links concrete Editing Studio model evolution and TKW coordination.
This extension is non-normative and does not freeze any schema or change the
existing observed-revision transport or Operation authorization boundary.

## Client prototype scope update — 2026-10-02

The user clarified that app Phase 4 ends at provisional client implementation
and feasibility exploration. It uses development-only sample models and
controllable fake transport/Operations to prove the Core/TFAF/app boundary,
context continuation and supplied-arm UI. Findings revise protocol/server-model
proposals. Completed server implementation and frozen wire contracts are not
prototype closure prerequisites; simulation does not prove real CNCF admission.
App Phase 5 consumes this prototype and owns real-server integration and final
aggregate/E2E acceptance. Dependency contracts/Phases retain separate acceptance.
