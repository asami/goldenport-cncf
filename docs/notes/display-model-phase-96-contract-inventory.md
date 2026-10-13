# Phase 96 Display contract inventory

Document role: non-normative contract inventory and reconciliation input.

## P96-01C promotion and navigation

The promoted ownership, ABI/admission/provider and runtime behavior subset is
recorded in [runtime design](../design/display-model-runtime.md),
[generated ABI spec](../spec/logical-ui-generated-abi.md) and
[runtime spec](../spec/display-model-runtime.md), with the
[promotion journal](../journal/2026/10/2026-10-03-phase-96-contract-promotion.md)
retaining scope and pending evidence. Earlier candidate/no-promotion statements
are superseded only for this subset. Original-base and current-source
observations below remain historical inspection evidence. Wire examples,
mutation physical schemas and context detail remain non-normative;
implementation/executable verification and Phase acceptance are pending.
Independent P96-01C review and parent admission accept the promoted documentary
subset; the linked promotion journal records the seven P96-01 outcomes.

This P96-01A inventory records parent source inspection at CNCF base
`6cbe877c53c116639feb11fa80546e79c051804c`. These are inspection observations,
not build, test, runtime, review or acceptance evidence. P96-01B records the
concrete [ABI-v1 proposal](logical-ui-generated-abi-v1-proposal.md); no public API, design/spec promotion or Step closure is
established here. See the [Phase](../phase/phase-96.md),
[checklist](../phase/phase-96-checklist.md) and
[history](../journal/2026/10/2026-10-03-phase-96-contract-inventory.md).

## 2026-10-03 reconciliation

The [GitHub boundary correction](logical-ui-generated-abi-display-runtime-boundary.md)
supersedes the earlier binary-consumption question. CNCF owns the Scala 3
consumer ABI and runtime definitions; Cozy Phase 76 produces generated source
from Phase 74 semantics. The seven answers below retain their historical
original-base observations. Current P96-01B source observations are separately
recorded below; transport/mutation decisions are still later work.
The new [reconciliation journal](../journal/2026/10/2026-10-03-phase-96-phase-76-plan-reconciliation.md)
records the import and review boundary.

## Current P96-01B source observations

Inspection at CNCF HEAD `713ca6d0db19388d86dda8ef532815375363d830` and Cozy
Phase 76 HEAD `c6b108b8f301d4b1928e3f7f1b43d3e17f8e3739` supplies the
current proposal inputs. Exact paths and the unchanged ESS2-01 input revision
are recorded in the [proposal journal](../journal/2026/10/2026-10-03-phase-96-generated-abi-contract.md).
Source inspection is not compilation, runtime or acceptance evidence.

[GeneratedWorkflowAbi](../../src/main/scala/org/goldenport/cncf/workflow/GeneratedWorkflowAbi.scala)
provides a typed provider/versioned-value/admitC diagnostic pattern.
[ComponentFactoryWorkflowPart](../../src/main/scala/org/goldenport/cncf/component/ComponentFactoryWorkflowPart.scala)
selects the component provider first, considers its factory only when absent
and makes absence a no-op. Logical UI proposes the same selection with its
own atomic wrapper, null-resistant diagnostics and explicit no-fallback policy;
Workflow's fixture hash/revision pins are not copied into Logical UI admission.
[Component](../../src/main/scala/org/goldenport/cncf/component/Component.scala)
stores admitted Workflow metadata but has no Logical UI provider/storage yet.

[ComponentFactoryEntityPlanPart](../../src/main/scala/org/goldenport/cncf/component/ComponentFactoryEntityPlanPart.scala)
admits Workflow metadata before entity/aggregate/View creation. Logical UI's
proposed structural gate can occur there; the distinct explicit binding join
must wait for registered View/value/action bindings and finish before Display
exposure. [ViewDefinition](../../src/main/scala/org/goldenport/cncf/entity/view/ViewDefinition.scala)
and [Browser](../../src/main/scala/org/goldenport/cncf/entity/view/Browser.scala)
retain explicit named/contextual View access and application-typed `V` /
`Vector[V]`; Display does not impose a generic Record or second View store.

Current Cozy Phase 74 model/validation preserves the exact provisional
descriptor, five roles/values, scoped uniqueness and supplied order/bytes.
Its ID predicate is `value != null && value.nonEmpty && value == value.trim`;
display text uses `value != null && value.trim.nonEmpty`. The proposal uses
those concrete predicates while retaining stronger application validation.
Static definition provenance is separate from per-resource/snapshot metadata.

The P96-01B selection is candidate service `display`, Operations `query` and
`read`, no list/get aliases, one explicit component service, and bootstrap
failure on service/Operation conflict. Provider absence exposes no Display;
invalid selected provider or missing/mismatched bindings fails construction.
These are concrete proposal choices awaiting design/spec acceptance and
P96-02..05 proof, not existing runtime behavior or a physical HTTP contract.

## Seven inventory answers

The following source observations are historical at the original CNCF base
`6cbe877c53c116639feb11fa80546e79c051804c`; the current-source section above
and P96-01B proposal distinguish later inspection and proposal selections.

### 1. Semantic View results and values

[ViewCollection](../../src/main/scala/org/goldenport/cncf/entity/view/ViewCollection.scala)
defines `ViewBuilder[V]`, `ContextualViewBuilder[V]` and `ViewCollection[V]`;
[ViewSpace](../../src/main/scala/org/goldenport/cncf/entity/view/ViewSpace.scala)
provides contextual find/query and named views, with query returning
`Consequence[Vector[V]]`.
[ActionCallBrowserPart](../../src/main/scala/org/goldenport/cncf/action/ActionCallBrowserPart.scala)
exposes `view_load` / `view_search` under the existing ActionCall context and
wraps search values in `SearchResult[A]`. There is no single mandatory
Display-ready Record type. A component must bind its concrete semantic source
type and explicit projection; this does not justify a second View store.
The [semantic boundary](../design/aggregate-view-semantic-boundary.md) retains
Entity as source of truth, Aggregate as write projection and View as
event-driven read projection, independently consumable by semantic clients.

### 2. Reusable identity and revision

[ViewSpace](../../src/main/scala/org/goldenport/cncf/entity/view/ViewSpace.scala)
uses `org.simplemodeling.model.datatype.EntityId`.
[EntityConcurrency](../../src/main/scala/org/goldenport/cncf/entity/EntityConcurrency.scala)
uses `org.simplemodeling.model.datatype.EntityRevision` and
defines `EntitySnapshot[A]`, `EntityRevisionCarrier[A]` and
`EntityRecordSnapshot`. Create initializes `EntityRevision.INITIAL`; stale
revision failure is structured.
[EntityRevisionTransport](../../src/main/scala/org/goldenport/cncf/entity/EntityRevisionTransport.scala)
owns `If-Match`, strong `"revision-N"` tags and the observed-revision transport
property. Screen identity, protocol version, projection-definition version,
generated ABI version, Phase 74 semantic provenance and Entity revision have distinct meanings.
Opaque resource IDs must not become physical paths or route selectors.

### 3. Schema and constraint authority

[EntityRuntimeDescriptor](../../src/main/scala/org/goldenport/cncf/entity/runtime/EntityRuntimeDescriptor.scala)
carries `Option[org.goldenport.schema.Schema]`, normally from a generated
companion Schema and effective descriptor/application policy. Display input
metadata must adapt that authority with explicit component conversion and
validation; it cannot create a second domain validation engine. Existing
Operation request/response definitions use `org.goldenport.protocol.spec` types.

### 4. Canonical mutation entry and strict-delete gap

[ActionCallRepositoryPart](../../src/main/scala/org/goldenport/cncf/action/ActionCallRepositoryPart.scala)
provides `aggregate_create`, `aggregate_update`, observed-revision update and
observed aggregate commands with authorization, resolution and mutation policy.
[ActionCallEntityMutationPart](../../src/main/scala/org/goldenport/cncf/action/ActionCallEntityMutationPart.scala)
supplies canonical Entity create and
[ActionCallEntityLifecyclePart](../../src/main/scala/org/goldenport/cncf/action/ActionCallEntityLifecyclePart.scala)
supplies Entity delete.
[EntityMutationPolicy](../../src/main/scala/org/goldenport/cncf/entity/EntityMutationPolicy.scala)
owns optimistic concurrency, `WriteIfChanged`, `ObservedRequired` and strict
REST defaults. [EntityMutationResults](../../src/main/scala/org/goldenport/cncf/entity/EntityMutationResults.scala)
has empty `UpdateResult` / `DeleteResult` markers, not a complete versioned
Display receipt. An admitted result must distinguish pre-commit failure from
committed source identity/revision with Display projection pending; pending
projection must not cause replay of a committed command.

The parent's additional inspection found that
[UnitOfWorkOp.EntityStoreDelete](../../src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala)
carries only ID/authorization,
[EntityStoreSpace](../../src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala)
delegates `delete(id)`, and ordinary soft-delete in
[StandardEntityLifecycleQueryPart](../../src/main/scala/org/goldenport/cncf/entity/StandardEntityLifecycleQueryPart.scala)
passes `None` to effective expected revision. Normal delete is not proof of
client-observed safe delete. Strict Display delete needs an admitted canonical
bridge; neither ad hoc check-then-delete races nor direct datastore mutation
are a substitute.

### 5. Component registration and resolver ambiguity

[Component](../../src/main/scala/org/goldenport/cncf/component/Component.scala)
owns ViewSpace beside EntitySpace/AggregateSpace.
[ComponentFactoryDescriptorPart](../../src/main/scala/org/goldenport/cncf/component/ComponentFactoryDescriptorPart.scala)
bootstraps declared/named Views.
[TagComponent](../../src/main/scala/org/goldenport/cncf/component/builtin/tag/TagComponent.scala)
demonstrates `Component.SinglePrimaryBundleFactory`, ServiceDefinition,
OperationDefinitionGroup, Protocol and `Component.Core.create`.
The [architecture](../design/cncf-architecture-overview.md) assigns Component
installation/resolver rebuild to Subsystem and internal binding installation
to Component. Display definitions remain explicit component-owned data;
DisplayService does not expose a new public Aggregate/View runtime gateway.
[OperationResolver](../../src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala)
groups component/service; duplicate Operation selectors resolve `Ambiguous`,
not safe last-wins installation. P96-01B now selects the proposed registration,
collision and absence policy in the ABI-v1 note; implementation remains pending.

### 6. JSON, version and transport binding

[CNCF build declarations](../../build.sbt) use Scala `3.3.8` and Circe `0.14.6`.
[OperationResponseFormatter](../../src/main/scala/org/goldenport/cncf/protocol/OperationResponseFormatter.scala)
handles raw HTTP response content and RecordResponse data/envelope formatting;
RecordResponse passes through `RuntimeContext.transformRecord`. A physical
Display codec still needs explicit losslessness/envelope decisions.
[Http4sHttpServer](../../src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala)
applies observed-revision conversion to PUT with
`_is_entity_revision_rest_request`; Display has not acquired that binding.
Sending `If-Match` alone does not establish a Display mutation adapter.

### 7. Cozy Phase 74 semantic reference and generated ABI boundary

Cozy's [runtime spec](../../../cozy-phase76/docs/spec/logical-ui-runtime-contract.md),
[design](../../../cozy-phase76/docs/design/logical-ui-runtime-contract.md) and
[public model](../../../cozy-phase76/src/main/scala/cozy/ui/CozyLogicalUiRuntime.scala)
are the semantic/reference contract. Their Scala classes are not CNCF runtime imports. Introducing commit:
`d3ddb86f4251ea07714ff2ca586c441258201b06`.
The exact descriptor is schema `cozy.logical-ui-runtime.v1`, version `1`,
semantic schema `cozy.logical-ui-semantics.v1`, assurance
`provisional-scenario`; driver repository
`KnowledgeHubProject/nict-editing-studio-app`, revision
`48eb43b8eb839137e17ef4c944b3db4a73d0cec3`, path `docs/phase/phase-2.md`,
scenario `resource-list-detail`. Cozy reference admission compares the full
descriptor structurally and exactly. CNCF's generated ABI has its own versioned
admission contract and an explicit mapping to this semantic reference.

`Model = Metadata + Screen`, `Metadata = ContractDescriptor + SourceProvenance`,
`Screen = ResourceList | ResourceDetail`; immutable data also includes
`ListItem`, `Section`, `Field`, `Action` and `DetailTarget`.
Closed values are Text, Number (`BigDecimal`), Boolean, Timestamp (`Instant`),
Missing (`TextValue`, `NumberValue`, `BooleanValue`, `TimestampValue`,
`MissingValue`). Closed roles are content/title/subtitle/description/status.
Content accepts all five values; other roles require Text; title requires
nonblank text. Proposed date/choice/reference/structured kinds are not admitted.
An explicit conversion may yield an existing value; a new kind requires
separate Cozy authority.

IDs/provenance are nonempty and exactly trimmed but otherwise opaque. List
item IDs are unique per List, section IDs per Detail, field IDs per item or
section, action IDs per owning screen or item. Non-content roles occur at
most once per field container. IDs may recur across containers; supplied
vectors/order/text are preserved without sorting, trimming or repair.
`Screen.id` is presentation identity; `ListItem.id` and
`ResourceDetail.resourceId` are resource identity. `DetailTarget.screenId`
is logical screen identity, not a URL. `Action(id, label, enabled)` is inert
`CommandPattern` data and grants no execution or mutation authority.

[Cozy build declarations](../../../cozy-phase76/build.sbt) identify
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`, Scala `2.12.18`.
CNCF's Scala 3 build declares no Cozy runtime dependency;
`cozyGeneratorVersion` is generation support. The generated-source boundary is the selected direction, so this is no longer
an open binary-distribution dependency. P96-01B records existing generated
ABI/provider patterns and the candidate CNCF consumer fixture mapping; Cozy Phase 76
owns generator implementation. Cross-building or copying Cozy classes is excluded.

## Minimum conceptual map and unsupported subset

This map is reconciliation input, not an accepted public API or alias set.

| CNCF concept | Cozy concept / boundary |
| --- | --- |
| Concrete semantic query values `Vector[V]` / `SearchResult[A]` | Admitted CNCF List definition + View -> ordered List Display values with Phase 74 List semantics |
| Concrete semantic object `V` | Admitted CNCF Detail definition + View -> Detail Display values with Phase 74 Detail semantics |
| Explicit grouping and source conversion | `Section`, `Field`, one of five `DisplayValue` kinds |
| Declared presentation meaning | Closed role in its field container |
| Source/projection identity and compatibility | Phase 74 semantic provenance, CNCF ABI/definition versions and separate Display/source metadata |
| Displayed Business action | Inert Cozy `Action`; target binding outside Cozy, direct ordinary REST Operation and client Display reload |
| Editor, constraints, writable field/update target | CNCF metadata outside Cozy fields/actions; Entity/Aggregate remains mutation authority |

Unsupported Cozy members include raw JSON/`Any`, semantic property paths,
date/choice/reference/structured value extensions, images, Widget/layout/style,
URLs/callbacks and executable Operations. Display does not infer an inverse
mapping for derived/combined fields. The
[Display proposal](display-model-phase-96-proposal.md) uses `query/read`, while
the [GitHub development-plan proposal](../journal/2026/10/2026-10-03-phase-96-development-plan-proposal.md)
uses conceptual `list/get`. P96-01B's [ABI-v1 proposal](logical-ui-generated-abi-v1-proposal.md)
selects `display` with canonical `query/read` and explicit registration/conflict
rules; no parallel aliases are proposed. This selection is neither an accepted
public API nor a physical route/media-type/codec decision.

## Fixture provenance and edit targets

The [ESS2-01 sample journal](../../../../Project2026/nict-editing-studio/docs/journal/2026/10/2026-10-01-display-model-phase-96-sample.md)
records `ess.display-reference.v1`, revision `1`, a development-only fixture,
not production Knowledge Candidate/KnowledgeHub admission. IDs such as
`capture:book-1` remain opaque. List snapshot labels such as `snapshot-1` are
not Entity revisions. Sample data remains journal/non-normative.

| Fixture / scoped Display locator | Semantic target | Writable / conversion |
| --- | --- | --- |
| ESS2-01 `capture-detail/main/title` | Entity/Aggregate `title` | update; `TextValue.text` to semantic text, byte-preserving; nonblank title |
| ESS2-01 `capture-detail/main/description` | Entity/Aggregate `description` | update; `TextValue.text` to semantic text, byte-preserving; empty permitted |
| ESS2-01 `capture-detail/main/stage` | derived from `workflowState` | read-only; no inverse mapping |
| GitHub KnowledgeCandidate-like example | title/status/summary; capturedAt | proposed editable title/status/summary and readonly capturedAt; distinct from ESS2-01 |

ESS2-01 advertises read/query/update and disables create/delete. Its unsupported
responses do not satisfy positive create-to-Detail/List and delete-to-List proof.
An explicitly declared development fixture admitting those paths is still
required; neither silent ESS mutation nor production admission is authorized.
The sample `request-review` Business Operation remains development-only,
separately admitted and invoked through ordinary REST followed by Display reload.

## Context scope and P96-01B reconciliation frontier

The [Presentation Context proposal](display-protocol-presentation-context-proposal.md)
retains context projection/admission/continuation across read, mutation, direct
Business Operation, reload and retry. Family source, allowed readers/updaters,
version, lifetime, propagation and bounded unknown-family handling remain open.
Existing [evaluation design](../design/operation-evaluation-capture.md) and
[spec](../spec/operation-evaluation-capture.md) retain immutable evaluation and
admission authority. No RuntimeContext, UnitOfWork or security capability is
serialized. The optional Phase 98 variant/opaque Display Instance correlation
hook grants no arm allocation or production authority; Experiment/Run/Arm,
observation and reward ownership remain Phase 98's. Core P96-01..P96-09 must
work without Phase 98 implementation.

P96-01B records the concrete non-normative generated ABI/provider proposal,
canonical query/read naming, registration/collision/absence, minimum supported
map/converters, fixture ownership and future executable proof matrix. The
proposal does not issue LUI-ABI-01. Later P96-01 promotion must establish accepted
design/spec scope and reconcile runtime/transport/mutation before Step closure;
P96-02/03 implement the ABI and normal fixture admission/projection handoff. Strict-delete
bridging, source snapshot/revision correspondence, physical envelope/codec and
Display `If-Match` adapter decisions remain explicit required boundaries;
context family/admission/continuation decisions remain tracked. This inventory
does not choose them. Their owning obligations remain P96-04 (codec), P96-06/07
(revision adapter, strict atomic delete, committed-write/Display-pending and
read-after-write correspondence), P96-07/09 (positive create/delete and fixture),
P96-10 (context/Phase 98 hook) and P96-11 (final validation).

All eleven stages and the original 47 checklist obligations remain required,
with added ABI/provider/generated-fixture handoff obligations:
pure values/projection, deterministic codec, Detail then List, mutation schema,
update then positive create/delete, committed-write/Display-pending behavior,
direct Business Operation/reload, reproducible development fixture, context
compatibility and consumer/release evidence. Focused child/dependency handoffs,
CNCF repository release validation and app Phase 5 real-server aggregate/E2E
are distinct obligations. App Phase 4 is prototype feasibility only; no
aggregate full-suite deferral is admitted and app E2E cannot close CNCF.

## P96-01C outcome map

This map relates the documentary promotion to all seven existing P96-01
obligations, in checklist order. Independent review and parent admission support
these documentary outcomes; the checklist records P96-01 closure. This map is
not passing executable proof or acceptance of any later Step.

| P96-01 obligation | Promoted outcome / remaining ownership |
| --- | --- |
| Canonical source inventory | Original-base/current-source observations above retained; design and DSP96-R01/R03/R05/R06 retain typed View, Entity identity/revision, schema and canonical mutation authority; implementation is pending |
| Minimum CNCF-to-Cozy vocabulary | LUI96-S01..S04 fixes List/Detail/Section/Field/Value/Action, five roles/kinds, scoped identity and independent versions; editor/constraints/write targets remain runtime/application registrations |
| Value-kind reconciliation | LUI96-S03 and DSP96-R02 admit only text/number/boolean/timestamp/missing; date/choice/reference/structured kinds remain excluded |
| One read/query Operation contract | DSP96-R04 fixes component-scoped display.query/read, explicit registration, collision failure and no aliases; P96-04 physical canonical binding and P96-05 public proof remain pending |
| Fixture/edit targets and accepted scope | DSP96-R05..R07 and fixture boundary retain ESS2-01 title/description targets, readonly stage, unsupported create/delete and separate positive fixture; documentary design/spec review and parent admission are recorded in the promotion journal |
| Generated-provider patterns | LUI96-S05..S07 fixes closed/null-resistant diagnostics, component-first/no-shadowed-factory access, no fallback, complete atomic vector and later typed join before lookup |
| Separate ownership and LUI-ABI-01 | Design plus LUI96-S08/DSP96-R01 separates definitions, semantic Views and Display; all three exact handoffs require actual P96-02/03 and Cozy Phase 76 evidence, still pending |

The [runtime bounded-decision table](../spec/display-model-runtime.md#bounded-later-decisions)
assigns exact wire members/encoding/envelope/media/HTTP binding to P96-04;
typed registration implementation to P96-02/03; concrete mutation requests/
results, Display If-Match adapter and strict-delete bridge to P96-06/07;
context families/admission/lifetime/continuation to P96-10. These later
prerequisites cannot reopen the fixed invariants or erase Phase requirements.
P96-03 generated round trip, P96-05 public read, P96-06/07 strict revision/no-op/
committed-pending proof, P96-08 direct Business/reload, P96-09 ESS2-01 plus
positive create/delete and P96-11 consumer/release evidence remain pending.
All 54 requirements are preserved: seven P96-01 documentary outcomes are checked,
and 47 later obligations remain unchecked. P96-01 is CLOSED; ten stages remain OPEN.
