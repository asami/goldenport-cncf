# Display Model runtime contract

Document role: normative implementation contract for Phase 96 Display behavior.
Status: implementation contract; implementation and executable verification pending.
Independent P96-01C review passed; this documentary contract is accepted for
P96-01. Implementation, executable verification and Phase acceptance remain pending.

The [runtime design](../design/display-model-runtime.md) fixes ownership and
architecture; [Logical UI generated ABI](logical-ui-generated-abi.md) fixes
static admission. Existing [Aggregate/View semantics](../design/aggregate-view-semantic-boundary.md),
[path resolution](path-resolution.md) and
[Entity conflict/conditional transition](entity-conflict-and-conditional-transition.md)
remain governing contracts. This document fixes behavior without inventing a
physical Display wire schema, HTTP result status or mutation request/result sum.

## DSP96-R01: Typed projection and independent semantic consumption

Display projection is a pure deterministic function of an admitted definition,
canonical authorized contextual semantic View result, and explicitly registered
typed source/converter bindings. Source consumption remains application-typed
`V`, `Vector[V]` or `SearchResult[V]`; Display does not mandate generic Record.
The same immutable source inputs remain independently consumable by semantic
clients. Projection neither changes those inputs nor writes source/View state.

Preserve supplied order and scoped identities in List, Detail, sections,
fields and actions. A List holds ordered resource items and a logical
DetailTarget, without eager Detail payload. Its browse/collection semantics,
Detail's inspect/detail semantics and Actions' inert command-pattern meaning
follow the Phase 74 mapping. No `Any`, incidental `toString`, raw JSON-map
fallback, heuristic field discovery, runtime CML parsing or arbitrary
caller-selected source/converter is permitted. Missing or incompatible typed
bindings fail structurally.

## DSP96-R02: Runtime values, roles and fan-out

The closed runtime value semantics are `text(String)`, `number(BigDecimal)`,
`boolean(Boolean)`, `timestamp(Instant)` and explicit `missing`. Content may
use any admitted kind; non-content roles accept text only and title-role text
must be nonblank. Date/choice/reference/structured value extensions are not
admitted. Explicit converters may produce supported values from server-native
or opaque semantic inputs, but may not change semantic authority or introduce
new kinds. Missing is a typed value, never null or a silently omitted field.
Null/invalid runtime values or payloads are structured failures.

Each result must conform to the field's admitted `allowedKinds`. Wrong-kind,
invalid role/value, duplicate resource identity or missing binding is structured
projection failure. Scoped field/action uniqueness and at-most-one of each
non-content role remain required; multiple content fields and cross-container
ID reuse are permitted. Empty nested containers remain valid; supplied labels,
titles and order are not silently trimmed, sorted or repaired.

Registered converters may reshape/combine or fan out into several fields only
when those fields are declared. Fan-out emits in deterministic declared order
and validates every resulting value and scoped constraint; it cannot invent
fields, skip failed outputs or infer inverse writes.

## DSP96-R03: Resource identity and revision spaces

Definition screen ID, runtime resource EntityId, managed EntityRevision and
query snapshot provenance are separate. One Detail identifies its resource;
each List item identifies its resource uniquely within that List. Resource
IDs remain opaque through public presentation. Screen IDs and journal fixture
aliases must never be parsed as EntityId, datastore paths or Operation paths.
Application fixture registration must explicitly map aliases to actual typed
resource identity and ownership.

Definition/provenance revision must not stand in for observed resource revision.
A List snapshot/correlation identifies its source query, not a mutation
precondition. Runtime retains existing typed EntityId/EntityRevision; opaque
public transport representation belongs to P96-04. ABI identity/version, exact
Phase 74 descriptor, definition revision, provenance revision, wire schema/
version, Entity revision and snapshot/correlation stay independently versioned.

## DSP96-R04: Service and read

Register exactly one component-scoped service selector `display`, with
canonical read Operations `display.query` and `display.read`. Do not add
`list/get` aliases. Explicit registration is required: a conflicting display
service or duplicate Operation fails bootstrap, never last-wins replacement.
With no definitions there is no Display service. Admission and complete typed
binding join precede usable lookup/exposure.

Ordinary canonical Operation selection and authorization govern each request.
A client selects admitted screen/resource/query input; it cannot choose
arbitrary Views, Aggregate methods, implementation classes, converters or
datastore paths. Missing, foreign, undeclared or unauthorized selection fails
structurally without cross-component fallback. Read uses canonical contextual
View facilities; it exposes no public generic Aggregate/View gateway or second
View store.

Required public proof starts with Detail, then List/select/Detail. This contract
does not fix exact Display route/media/HTTP verb or JSON envelope. P96-04 must
bind the existing canonical path-resolution specification, including its
component/service/operation authority, rather than inventing aliases or a new
top-level REST namespace.

## DSP96-R05: Capabilities and writable mapping

Runtime read/query/create/update/delete capability, current availability,
writable metadata and Business Operation metadata are distinct from
presentation values and static ABI. Determine them under current authorization;
metadata never grants permission. Distinguish readonly fields from unsupported
operations, with explicit structured failures rather than successful no-ops.

For every writable scoped field register accepted Display-value conversion and
the canonical Entity/Aggregate mutation target. Reuse authoritative schema and
domain constraints; Display cannot add a second validation language or weaken
them. Read conversion/binding does not imply a write target. Derived, formatted
or combined fields have no inverse unless separately explicit.

Update requires a nonempty admitted field set, no duplicate scoped locations,
and rejection of unknown/readonly/wrong-kind/constraint-invalid fields before
any write. Resolve only declared server-side mappings, never a client-supplied
property path or target. Exact capability/metadata and mutation request member
sets belong to P96-04/06; this invariant does not select those physical schemas.

## DSP96-R06: Canonical mutation and concurrency

Create uses existing canonical identity and managed-revision initialization;
new persisted managed revision starts at `1`. It does not require an observed
revision. Update/delete require client-observed revision through existing
transport-metadata authority, not a business-field parameter. Missing,
malformed or stale observations fail structurally before effects. A Display
`If-Match` adapter must be implemented explicitly; merely sending the header
does not prove binding to the strict runtime path.

Update uses `WriteIfChanged + ObservedRequired` with the canonical optimistic
comparison. Stale comparison precedes equality. A valid current-revision no-op
does not advance revision, updatedAt or mutation audit state; a changed write
advances revision exactly once through the framework. Managed fields cannot
be overwritten by Display inputs.

Strict delete must perform authorized comparison and deletion atomically through
canonical Entity/Aggregate/UnitOfWork/provider capability. No check-then-delete,
direct datastore call, component-local lock or capability downgrade is allowed.
Unsupported atomic capability fails before effects. Ordinary delete and empty
`UpdateResult`/`DeleteResult` markers are not evidence of this strict bridge or
of a versioned Display outcome. P96-06/07 must implement the admitted bridge,
request/result contract and Display transport adapter. Existing non-Display
APIs/defaults are unchanged.

Existing authorization, lifecycle/domain validation, transaction, revision,
resident coherence and View invalidation remain authoritative. A failed
pre-commit authorization/validation/conflict/capability check changes no source
or revision; Display must not patch View or Display directly to simulate a write.

## DSP96-R07: Committed outcomes and reload correspondence

Distinguish four outcome states:

| State | Required meaning |
| --- | --- |
| Pre-commit rejection | No mutation effects; structured rejection |
| Committed mutation with ready Display | Authoritative committed identity/revision/outcome plus corresponding projection |
| Committed mutation with Display pending/failed | Committed receipt retains authoritative identity/revision/outcome; projection delay/failure does not mean rollback or authorize replay |
| Indeterminate acknowledgment | Existing transaction-indeterminate/no-automatic-retry rule; new authoritative read required |

Refresh must correspond to the committed source revision or explicitly report
projection pending. An unrelated cached Display cannot be returned as that
mutation's successful refresh. Event-driven View delay must remain visible;
bounded fixture event processing cannot become a general freshness assumption.
Post-commit projection failure cannot be described as an uncommitted update,
rollback-capable failure or permission to repeat a command.

Atomic storage comparison and existing lifecycle, authorization, resident-state
coherence and committed-mutation View invalidation stay authoritative.
Indeterminate acknowledgment follows the existing no-automatic-retry/
authoritative-read rule. Exact receipt/wire sum representation is P96-06 work;
this document introduces no new HTTP status or execution envelope.

## DSP96-R08: Business Operations and context

Actions remain inert. Context may hide/disable/enable them but never authorize
execution. Business Operations execute directly through ordinary Operation
authorization and input contracts, separately from standard Display Mutation.
After successful invocation the client explicitly reloads Display; a Business
response alone is not refreshed Display evidence. No generic Display action
tunnel, push/subscription or Service Bus refresh is introduced.

Context family/source, version, admission, lifetime and continuation across
read/mutation/direct Business/reload/retry belong to P96-10, under existing
immutable evaluation/admission authority. RuntimeContext, UnitOfWork and
security capabilities must not be serialized. Phase 98 owns Experiment/Run/Arm
allocation, observation and reward; supplied arm/variant/correlation data grants
no authority. The core P96-01..P96-09 path works without Phase 98 implementation.

## Requirement-to-proof ownership

All rows are REQUIRED / PENDING, with no implementation or passing-test claim.
The [P96-01B matrix](../journal/2026/10/2026-10-03-phase-96-generated-abi-contract.md#unexecuted-acceptance-matrix--p96-01b-abi-v1)
retains full PROJ/ROUNDTRIP scenario descriptions. Owning Steps must bind real
Executable Specifications and actual commands/results, using Given/When/Then,
`should` vocabulary and property checks for deterministic projection, scoped
identity, supplied bytes/order, typed missing and unchanged semantic consumption.
Markdown wording tests are not evidence.

| Requirements | Required proof | Owner | Status |
| --- | --- | --- | --- |
| R01/R02/R03 | PROJ-01 typed values/roles, order/identity and invalid/duplicate rejection | P96-03 | PENDING |
| R01/R02/R05 | PROJ-02 combined/fan-out/opaque-source conversion, admitted kinds and no inverse/fallback | P96-03 | PENDING |
| R01/R02/R08 | PROJ-03 structured failures, contextual inert action availability and independent semantic consumption | P96-03 | PENDING |
| R01/R02/R03; ABI S08 | ROUNDTRIP-01 actual Cozy-generated source through same compile/admission/projection path, exact receipt/artifacts | Cozy Phase 76 / P96-03 | PENDING |
| R02/R03/R04 | Exact versioned codec, strict malformed/unknown rejection, golden fixtures and property round trips | P96-04 | PENDING |
| R03/R04 | Public Detail first, then List/select/Detail; registered authorized canonical service | P96-05 | PENDING |
| R05/R06/R07 | Mutation mapping, strict observed revision/delete, stale-before-equality/no-op, committed-pending and revision-corresponding refresh | P96-06/07 | PENDING |
| R08 | Direct ordinary Business Operation followed by explicit corresponding Display reload | P96-08 | PENDING |
| R01..R08 | ESS2-01 development reference and separate positive create/Detail/List and delete/List fixture | P96-09 (positive paths also P96-07) | PENDING |
| R08 | Context admission, families/lifetime/continuation, isolation and Phase 98 independence | P96-10 | PENDING |
| R01..R08 | Final consumer artifacts, focused matrix, repository release validation and Phase review/handoff reconciliation | P96-11 | PENDING |

## ESS2-01 fixture boundary

The [ESS2-01 sample journal](../../../../Project2026/nict-editing-studio/docs/journal/2026/10/2026-10-01-display-model-phase-96-sample.md)
is development-only `ess.display-reference.v1`, revision `1`. The
[inventory edit-target mapping](../notes/display-model-phase-96-contract-inventory.md#fixture-provenance-and-edit-targets)
and [ABI fixture mapping](../journal/2026/10/2026-10-03-phase-96-generated-abi-contract.md#ess2-01-development-fixture-mapping)
retain exact scoped title/description update targets and readonly derived stage:
`capture-detail/main/title` maps explicitly to Entity/Aggregate title with
byte-preserving text conversion and nonblank domain title;
`capture-detail/main/description` maps to description with byte-preserving text
conversion, empty permitted; `capture-detail/main/stage` derives from
workflowState and has no inverse mapping.

The fixture admits read/query/update and unsupported create/delete. Unsupported
responses do not satisfy positive create/delete proof; a separately declared
development fixture must admit and prove those paths. No silent ESS2-01
expansion or production Entity/app/KnowledgeHub admission occurs here. The
journal's KnowledgeCandidate-like example is a distinct proposal, not a
replacement fixture. Business `request-review` remains separately admitted,
directly invoked and followed by reload. Raw sample payloads remain in their
existing journal rather than being copied into this specification.

## Bounded later decisions

| Decision | Owner | Fixed prerequisite boundary |
| --- | --- | --- |
| Exact wire members/encoding, schema/version, envelope/media and HTTP binding | P96-04 | Closed kinds, separate identity/revision spaces, deterministic losslessness and canonical path-resolution authority |
| Typed runtime registration implementation, provider/admission and lookup/projection assembly | P96-02/03 | Static ABI S01..S08, complete atomic vector, explicit late typed join before exposure |
| Concrete mutation request/result, Display If-Match adapter and strict-delete bridge | P96-06/07 | R05..R07 mapping/domain authority, observed optimistic atomicity, no-op/replay/freshness invariants |
| Context families/source, member sets/version, admission/lifetime/continuation | P96-10 | R08 existing immutable admission, no capability serialization or Phase 98 dependency |

These decisions are prerequisites of their owning later implementations. They
cannot reopen fixed invariants or erase pending Phase acceptance items. All
54 checklist requirements and eleven stages remain required. Editing Studio
Phase 2, app Phase 4 prototype, app Phase 5 real-server aggregate/E2E, Cozy
producer acceptance and CNCF repository closure retain their separate owners.
