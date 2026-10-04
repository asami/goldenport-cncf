# Display Model runtime contract

Document role: normative implementation contract for Phase 96 Display behavior.
Status: implementation contract; P96-01/02/03 accepted within their scoped boundaries.
Independent P96-01C documentary review and P96-03 whole-Step review passed;
all seven P96-03 criteria are CLOSED with focused LUI-ACCEPT-01 consumer proof
accepted. Local P96-03 Step commit is PREPARED/PENDING; the separately owned
Cozy P76-05 reciprocal receipt remains pending. Final Phase acceptance and later
service, codec, wire, mutation and presentation-context work remain pending.

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

## P96-03B1: Explicit typed row projection

The public [DisplayProjection](../../src/main/scala/org/goldenport/cncf/display/DisplayProjection.scala)
boundary consumes already-authorized immutable `V` or `Vector[V]`. B1 selects
no View and performs no query; canonical contextual View registration and the
atomic component binding join remain P96-03B2. `SearchResult[V]` requires an
explicit typed adapter at the source boundary. Actual Cozy producer acceptance
and LUI-ABI-01/LUI-GEN-01/LUI-ACCEPT-01 remain PENDING.

`ValueBinding[V](bindingIds, convert)` supplies a nonempty ordered output-key
vector and `V => Consequence[Vector[DisplayValue]]`. `ActionBinding[V]` supplies
one key and `V => Consequence[ActionAvailability]`. `RowBinding[V]` supplies the
typed resource callback, value groups and row actions. `detailC` and `listC`
admit these registrations against an `AdmittedDefinitions`, exact screen ID
and exact declared View key. Their immutable `Detail[V]` and `List[V]` expose
the selected definition and `projectC`; external construction and copying
cannot bypass admission. List screen actions have separate `Vector[V]`-typed
registrations and List projection accepts an explicit optional QuerySnapshot.

Registration is per screen. Two screens may share a View key while supplying
different exact value/action key sets. Every distinct declared value key has
exactly one registered output; each distinct action key has exactly one
registration in its own row or List-screen scope. Missing, duplicate, extra,
malformed or null registrations fail before any callback. Empty declared
scopes require empty bindings, but every row still requires a resource
callback. Keys reused at several declared locations are evaluated once per
scope and their output is independently validated at every location.

Admission executes no callback. Diagnostic precedence is top-level shape and
screen selection, row/resource shape, value groups in registration order,
missing values in declaration order, row actions, then List actions. At
projection, all source/vector/row and snapshot shape checks precede callbacks.
Rows follow supplied order; resource runs first, then each value group at its
first declared field occurrence, then actions at their first declared
occurrence. Registration order cannot change callback or model order. Group
outputs correspond positionally to their explicit output-key vector; count
is checked before zipping. List screen actions follow all successful rows and
receive the complete original vector, including an empty vector.

Callbacks may explicitly combine native and opaque typed members and fan out
to declared keys. They must be pure and capture only already-authorized
immutable context. Arbitrary Scala closure purity is an application obligation;
the framework adds no effects, retries, caches, source mutation, converter
discovery, implicit string conversion, business execution or inverse mapping.
Hidden/Disabled/Enabled are inert presentation data.

The first callback Failure is propagated with its taxonomy and cause. NonFatal
invocation exceptions are redacted into `DSP96_PROJECTION_CALLBACK_FAILED`;
fatal/control exceptions are not swallowed. Null consequences/success values
or entries fail with `DSP96_PROJECTION_ELEMENT_INVALID`, and output count
defects with `DSP96_PROJECTION_OUTPUT_COUNT_INVALID`. Other registration codes
use the same prefix and `ID_INVALID`, `SCREEN_NOT_FOUND`, `DEFINITION_MISMATCH`,
`VIEW_BINDING_MISMATCH`, `BINDING_DUPLICATE`, `BINDING_UNDECLARED` or
`BINDING_MISSING`. `ProjectionException` carries `Diagnostic(code, path, reason)`
in an argument-invalid Consequence; fixed reasons and indexed paths reveal no
source payload, supplied key or callback exception message.

Only after successful callbacks and complete candidate assembly does projection
call `Display.createC`. Its original runtime kind, role, title, payload,
scoped correspondence and duplicate EntityId diagnostics pass through unchanged.
These semantic failures may occur after callbacks, whereas callback failures
short-circuit later callbacks and rows. No failure exposes a partial Display.
Resource EntityId/EntityRevision and caller snapshot remain independent of
definition, screen and producer provenance.

Executable authoring is in [DisplayProjectionSpec](../../src/test/scala/org/goldenport/cncf/display/DisplayProjectionSpec.scala)
(E1–E11) and [DisplayProjectionPropertySpec](../../src/test/scala/org/goldenport/cncf/display/DisplayProjectionPropertySpec.scala)
(P1/P2, each requiring at least 100 successful generated cases). The external
[typed fixture](../../src/test/scala/logicalui/TypedDisplayProjectionFixture.scala)
consumes unchanged hand-written expected-generated definitions. Historical
parent execution `phase96-p96-03b1-test-002` passed the selected seven suites
and 67 tests, including E1..E11 and P1/P2 with at least 100 successes each.
SBT and wrapper exited 0 and the shared lock was released. Two pre-review
authoring repairs preceded that result: the finite permutation generator,
then compile-time type qualification. This is historical focused validation,
not independent B1 acceptance, actual generated producer proof, Step
acceptance, service integration or checklist closure.

## P96-03B2: Component-scoped contextual typed Browser binding

[DisplayRuntime](../../src/main/scala/org/goldenport/cncf/display/DisplayRuntime.scala)
joins a canonical `Browser[V]` to its matching immutable B1 `Detail[V]` or
`List[V]`. Public `detail[V](screenId, viewBindingId, browser, row)` and
`list[V](screenId, viewBindingId, browser, row, actions)` construct unevaluated
closed `Binding` candidates. List screen actions retain `Vector[V]` typing.
The candidates expose screen and View binding IDs but no execution API.
Registry construction and copying are closed; its private heterogeneous
entries retain each source's type with its projector. No erased source value,
cast, reflection, generic type lookup, Record/JSON bridge or adapter is added.

The auxiliary [DisplayBindingProvider](../../src/main/scala/org/goldenport/cncf/display/DisplayBindingProvider.scala)
exposes `displayBindings(component): Vector[DisplayRuntime.Binding]`.
Static metadata and runtime binding providers are separate capabilities.
With absent metadata bootstrap skips runtime provider lookup and getters.
With admitted metadata it selects the component capability first, considering
the factory only when absent, then reads exactly one selected getter. An
invalid, null or throwing selected result cannot fall back to the factory.
The getter may finish explicit typed View registration in this Component.

The package-private complete binding gate validates nonnull admitted
definitions, ViewSpace and vector; candidate entries and exactly trimmed
nonempty IDs; then duplicate and undeclared screens in candidate order and
missing screens in definition order. Every declared screen needs exactly one
candidate. Entries compile in definition order. Each source must be nonnull
and already registered by reference identity in the Component's own default
or named ViewSpace Browser maps before B1 registration admission. View binding
IDs are application keys, not lookup names. Equivalent handles are insufficient;
the same handle may deliberately be registered in two Components and owned by
both. No source, converter or availability callback runs during admission.
B1's branch, key, converter and action diagnostics pass through unchanged.

`Component.displayRuntime` exposes an optional complete immutable Registry.
Bootstrap retains all earlier Workflow, StateMachine, Continuation, revision
and concurrency gates and their order. Existing entity/Aggregate/View,
working-set, planner and event setup succeeds before runtime provider lookup.
Only the final success tail publishes admitted definitions, the one complete
registry reference and the collectionsBootstrapped flag, in that order.
Earlier legacy setup effects are not rolled back on late failure. This is
single-owner bootstrap with no added retry, locking or parallel-bootstrap
guarantee. Successful repeat returns the same definitions and Registry objects
without rereading either provider. No metadata means no runtime or new service.

Registry `screenIds` preserves definition order. Its `readC(screenId,
resourceId)(using ctx)` and `queryC(screenId, query)(using ctx)` validate exact
screen selection, nonnull resource/query and context, then use only their own
registry. Unknown screens and read-on-List/query-on-Detail fail before source
execution. A valid request invokes exactly `find_with_context` or
`query_with_context` once with the unchanged caller context and input, then
projects its typed result. No context-free call, count, retry, cache, source
mutation, eager Detail or business action is added. Browser query returns
`Vector[V]`; List snapshot is `None`. Other canonical result shapes require an
explicit application-owned typed adapter. Successful Detail identity must
equal the requested EntityId; observed source revisions are retained.

Browser Failure retains the original Conclusion, taxonomy and cause. A null
Consequence is runtime element-invalid; a successful null source passes to
B1 source validation. Only NonFatal exceptions from Browser invocation become
fixed redacted source failure; projection remains outside that catch and fatal
or control exceptions propagate. Registry is read-only infrastructure, not
authorization: contextual sources and later ordinary Operation routing own
access decisions, and action availability grants no permission.

`DisplayRuntime.BindingException` carries `Diagnostic(code, path, reason)` in
`Consequence.fail(Taxonomy.argumentInvalid, carrier)` and is never thrown.
The closed `DSP96_RUNTIME_` suffixes are `ELEMENT_INVALID`, `ID_INVALID`,
`PROVIDER_MISSING`, `PROVIDER_FAILED`, `SCREEN_UNDECLARED`, `SCREEN_DUPLICATE`,
`SCREEN_MISSING`, `SOURCE_NOT_REGISTERED`, `SCREEN_NOT_FOUND`,
`OPERATION_UNSUPPORTED`, `SOURCE_FAILED` and `SOURCE_ID_MISMATCH`. Indexed
paths use `bindings[i]` and its `screenId`, `viewBindingId` or `browser`, or
`definitions[i].screenId`; root/input paths are `definitions`, `viewSpace`,
`bindings`, `screenId`, `resourceId`, `query`, `context`, `source` and
`model.resource.entityId`. Missing or throwing provider uses `bindings`.
Fixed reasons expose no supplied IDs, source payloads or exception messages.

[DisplayRuntimeSpec](../../src/test/scala/org/goldenport/cncf/display/DisplayRuntimeSpec.scala)
authors R1..R8 and RP1, with RP1 requiring at least 100 successful cases.
[ComponentFactoryDisplayRuntimeSpec](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryDisplayRuntimeSpec.scala)
authors B1..B5 including actual factory-created View ordering. The external
[typed runtime fixture](../../src/test/scala/logicalui/TypedDisplayRuntimeFixture.scala)
is development-only and does not claim actual Cozy generation or Entity
admission. B2 parent validation and independent review remain PENDING. C actual
producer acceptance and LUI-ABI-01/LUI-GEN-01/LUI-ACCEPT-01 remain PENDING; the
next parent PLAN prepares LUI-ABI-01 from completed A/B evidence before C work.

## P96-03C: Actual generated consumer proof

The unchanged static ABI and contextual runtime contracts above are consumed
by [ComponentFactoryGeneratedLogicalUiAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedLogicalUiAcceptanceSpec.scala).
Its actual [definitions](../../src/test/scala/domain/logicalui/DomainLogicalUiDefinitions.scala)
and [provider](../../src/test/scala/domain/logicalui/DomainLogicalUiMetadataProvider.scala)
are the unmodified two-file LUI-GEN-01 delivery. The external
[consumer fixture](../../src/test/scala/logicalui/GeneratedDisplayConsumerFixture.scala)
supplies application-owned typed sources and bindings, not another production
ABI, projector or generator implementation.

GC1 covers normal bootstrap, complete publication and contextual List/select/
Detail identity and exact model correspondence. GC2 compares actual and
handwritten expected models while asserting their different provenance
separately. GC3 preserves Unicode/whitespace, reversed order, empty List and
independent typed semantic consumption. GC4 covers repeat identity and
component isolation. GC5 derives invalid ABI/duplicate/revision/target vectors
only in the fixture and compares normal bootstrap to authoritative admission.
GC6 preserves the typed last-binding diagnostic and no Display publication,
without requiring rollback of earlier semantic registration. GCP1 requires
at least 100 successful undiscarded actual/expected normal-bootstrap cases
with positive resource revisions, ESS states/availability and finite row
permutations including empty. No definition provenance is normalized for
comparison and no source is evaluated at bootstrap.

The [consumer journal](../journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md)
records the accepted Cozy P76-04 delivery at
`ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3` and corrected producer coordinate
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT` (Scala 2.12.18). It retains
source mapping, samples/provenance and historical planned commands, followed
by actual parent-admitted current execution. Representative request/result
`phase96-p96-03c-representative-request-001` r1 /
`phase96-p96-03c-representative-result-001` r1 passed GC1..GC6/GCP1, 7/7 tests
in one suite with Compile/Test success. GCP1's executed expectations require
at least 100 successes and zero discards; no separately printed exact count
is claimed. Both source comparisons exited 0, and the complete Test classpath
inspection found no cozy_2.12 runtime jar. Accumulator request/result
`phase96-p96-03c-accumulator-request-001` r1 /
`phase96-p96-03c-accumulator-result-001` r1 passed the unchanged 15 suites and
133/133 tests. Both terminal results retain SBT/wrapper exit 0 and released
serial lock. This is 140 focused tests across 16 suites, not a full-suite run.
Ordinary Compile/Test ran the pre-existing CNCF Information CML generator
hook; no Phase76 ESS2-01 Logical UI producer was explicitly rerun.

LUI-ABI-01 is issued and producer LUI-GEN-01 is accepted. Independent
whole-Step review `phase96-p96-03-step-review-001` revision 1 by
`/root/phase96_p96_03_step_review_001` passed all 28 paths and 19 programs;
parent admission is recorded in `phase96-review-ledger-001` revision 19.
All seven P96-03 criteria are CLOSED, including criterion 7's accepted
LUI-ACCEPT-01 focused consumer proof, recorded in the
[acceptance entry](../journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md#2026-10-04--p96-03-independent-step-acceptance).
Local Step commit is PREPARED/PENDING, not executed; the separately owned
Cozy P76-05 reciprocal receipt remains pending. Zero blockers and nonblocking
HYG-P96-03-SPEC-TRACEABILITY-001 are retained. Earlier A/B1/B2 status statements
describe historical slice state; this section records current acceptance without
changing their normative contracts. Final Phase acceptance and later service,
codec, wire, mutation and presentation-context work remain pending; no full
Phase review or closure is claimed.
