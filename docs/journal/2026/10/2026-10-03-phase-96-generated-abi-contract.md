# 2026-10-03 — Phase 96 generated Logical UI ABI contract proposal

Document role: non-normative journal, fixture mapping and future-proof record.
Scope: PHASE-96 / P96-01 / P96-01B documentary proposal only.
Status: implementation, executable proof, generation and handoff pending.

## Proposal rationale and ownership

The [ABI-v1 proposal](../../../notes/logical-ui-generated-abi-v1-proposal.md)
makes the imported generated-source direction concrete for later acceptance.
CNCF's Scala 3 ABI holds static definitions and component registration keys;
typed semantic Views, converters, current values, authorization and mutation
targets remain runtime/application-owned. Cozy Phase 74 retains semantic
authority and Cozy Phase 76 owns deterministic generation. This avoids a
Scala 2.12 runtime dependency or copied implementation object graph.

The selected candidate package is `org.goldenport.cncf.display`, object
`GeneratedLogicalUiAbi`, auxiliary provider `GeneratedLogicalUiMetadataProvider`,
ABI identity `cncf.logical-ui-generated.v1`, integer version `1`. The proposed
service is `display` with canonical `query/read`, without list/get aliases.
These choices do not establish an accepted public API or physical HTTP codec,
route, media type or mutation schema. Structural admission and the later typed
binding join are separate bootstrap gates; no partial lookup is exposed.

This Slice does not promote notes into design/spec. The
[inventory](../../../notes/display-model-phase-96-contract-inventory.md)
distinguishes original-base observations from current source inspection. The
[checklist](../../../phase/phase-96-checklist.md) remains open with all
54 obligations retained; P96-01 is in progress. HYG-P96-DOC-001 remains a
historical record, without rewriting its finding or materializing review-ledger
dispositions here.

## Exact inspected source revisions and paths

| Input | Exact revision / role |
| --- | --- |
| Original CNCF inventory base | `6cbe877c53c116639feb11fa80546e79c051804c`; historical P96-01A inspection only |
| CNCF dedicated Phase 96 checkout | `713ca6d0db19388d86dda8ef532815375363d830`, branch `codex/phase-96-display-service`; current source inputs |
| Cozy dedicated Phase 76 checkout | `c6b108b8f301d4b1928e3f7f1b43d3e17f8e3739`; Phase 74 semantic/model inputs, read-only |
| Editing Studio checkout | `7eee26edd65727fa39807aecb84b1dbad1c6d1b6`; ESS2-01 sample input, read-only |
| Phase 74 descriptor driver | `48eb43b8eb839137e17ef4c944b3db4a73d0cec3`; historical `KnowledgeHubProject/nict-editing-studio-app`, `docs/phase/phase-2.md`, `resource-list-detail` |

Current CNCF source inspection covered
`src/main/scala/org/goldenport/cncf/workflow/GeneratedWorkflowAbi.scala`,
`component/ComponentFactoryWorkflowPart.scala`,
`component/ComponentFactoryEntityPlanPart.scala`, `component/Component.scala`,
`entity/view/ViewDefinition.scala` and `entity/view/Browser.scala` (the latter
paths relative to `src/main/scala/org/goldenport/cncf/`). Workflow supplies
typed admission and component-first/factory-fallback discovery; Component
stores admitted metadata. EntityPlan admits Workflow before entity/View
creation. Logical UI binding join must wait for required registrations and
complete before Display exposure. No Logical UI implementation is implied.

Current Cozy inspection covered `docs/spec/logical-ui-runtime-contract.md`,
`docs/design/logical-ui-runtime-contract.md`,
`src/main/scala/cozy/ui/CozyLogicalUiRuntime.scala` and
`src/main/scala/cozy/ui/CozyLogicalUiRuntimeValidation.scala`. The last file is
the concrete RuntimeValidation source: the cycle's abbreviated filename
`RuntimeValidation.scala` resolves to this path. Its exact descriptor,
roles/values, null resistance, scoped uniqueness, supplied order/bytes and
concrete trim predicates ground the proposed mapping. Workflow fixture hash
or producer-revision pins are not copied into Logical UI admission.

The unchanged Editing Studio input is
[2026-10-01-display-model-phase-96-sample.md](../../../../../../Project2026/nict-editing-studio/docs/journal/2026/10/2026-10-01-display-model-phase-96-sample.md),
sample `ess.display-reference.v1`, revision `1`. Raw semantic/display values
and proposed replay examples remain in that journal and are not rewritten.
App Phase 4 owns prototype feasibility; app Phase 5 owns real-server
integration/aggregate E2E. Historical descriptor provenance does not change
that current ownership.

## ESS2-01 development fixture mapping

This mapping leaves ESS2-01 unchanged and grants no production KnowledgeHub
or Knowledge Candidate contract. It records the static shape the later
hand-written expected-generated fixture and actual Cozy generation must share.

| Definition/container | Ordered fields / roles | Target |
| --- | --- | --- |
| List `collecting-resources` | `title` / title, `stage` / status | DetailTarget `capture-detail`; no eager Detail payload |
| Detail `capture-detail`, section `main` | `title` / title, `stage` / status, `description` / description | Selected resource identity |

List title is `Collecting Resources`; Detail title is `Capture Detail`.
Labels remain `Title`, `Capture stage`, `Description` in sample order.
Each non-content field has exactly `Vector(text)` as its proposed allowed
kinds. List screen and item actions are empty. The Detail action definition
has ID `request-review`, label `Request Review`; current authorized runtime
bindings decide presence, with initial enabled presence only for the first
sample resource and removal after review_pending as recorded in the sample.
Displayed enabled presence never authorizes execution.

| Explicit opaque key | Fixture meaning | Ownership / inverse write |
| --- | --- | --- |
| `ess.reference` | View binding for the development List and selected Detail semantic source | Application registers typed source and canonical contextual View access |
| `ess.title` | Field converter from semantic title to text in List and Detail | Writable target Entity/Aggregate title, explicit byte-preserving text conversion; nonblank domain title |
| `ess.stage` | Field converter from workflowState to displayed stage text | Readonly; derived value has no inverse mapping |
| `ess.description` | Detail field converter from semantic description to text | Writable target Entity/Aggregate description, explicit byte-preserving text conversion; empty permitted |
| `ess.request-review` | Inert Detail action binding to the separately admitted development Business Operation | Direct ordinary Operation/REST invocation, authorization/revision boundary, then Display reload |

These keys are component registrations, not property expressions, routes,
class names or guessed setters. Reuse of `ess.title` and `ess.stage` across
containers is explicit. Generated static metadata contains no writable target,
constraint map, resource value, capability result or callback. Runtime input
validation remains application/domain-owned and may be stronger than the
ABI's concrete Phase 74 trim predicates.

The initial resources `capture:book-1` and `capture:record-2` remain journal
fixture aliases in their stated order. A later runtime fixture must explicitly
map each alias to a real typed EntityId with its owning collection; it must
not fabricate EntityId by parsing aliases. Runtime source data retains
EntityRevision. SourceProvenance of static definitions identifies definition
source/revision, not per-resource revision. Dynamic Detail metadata separately
preserves the source revision and List metadata the source snapshot. Sample
`snapshot-1`/`snapshot-2` labels are not EntityRevision values, and the sample's
dynamic runtime provenance must not be copied into static definitions.

ESS2-01 admits proposed read/query/update and unsupported create/delete.
Title and description remain writable targets; stage remains readonly.
Unsupported responses do not satisfy positive CRUD evidence. A distinct
declared development fixture must prove positive create/delete; no silent
ESS2-01 expansion or production admission occurs in this Slice.

## Unexecuted acceptance matrix — P96-01B-ABI-v1

Every row is future executable proof, currently UNEXECUTED. The table records
the frozen scenarios; no product test, fixture compilation, generation or
runtime projection has run in P96-01B. When implemented, use Given/When/Then
at setup/action/expectation boundaries, should vocabulary and property-based
checks for laws. Do not add tests of Markdown wording, checklist closure or
source text. Concrete executable suite paths/commands must be bound to the
implemented artifacts by the owning Step, not claimed by this documentary record.

| ID | Given / When / Then required future proof | Owner / current result |
| --- | --- | --- |
| ABI-01 | Given a valid minimal List/Detail pair and an external-package expected-generated fixture; when compiled and admitted through public ABI; then compilation succeeds and the complete immutable wrapper preserves supplied values/order | P96-02/03; UNEXECUTED |
| ABI-02 | Given null roots/vectors/nested elements/Option containers/Some(null), malformed IDs/bindings/revisions/text/provenance; when structurally admitted; then deterministic structured failures occur before dereference, with no repair or byte normalization | P96-02; UNEXECUTED |
| ABI-03 | Given incompatible/null ABI and each changed/null semantic descriptor member; when admitted; then exact ABI/contract incompatibility is rejected | P96-02; UNEXECUTED |
| ABI-04 | Given scoped duplicate screen/section/field/action IDs and non-content roles, plus permitted cross-scope IDs, reusable bindings and multiple content fields; when admitted; then prohibited duplicates fail and permitted reuse survives unchanged | P96-02; UNEXECUTED |
| ABI-05 | Given unknown/null/duplicate/empty kinds and non-content role-kind mismatch; when admitted/decoded; then the closed vocabulary rejects them; valid content subsets including text+missing retain order | P96-02; UNEXECUTED |
| ABI-06 | Given missing/self/wrong-kind/cross-component Detail targets and a valid same-component target; when the full vector is admitted; then invalid targets fail and the valid target resolves; an unreferenced Detail is valid | P96-02; UNEXECUTED |
| BOOT-01 | Given absent/component/factory/both providers; when construction selects one; then component wins without reading shadowed factory, absence exposes no Display, and empty/invalid/throwing selected provider fails structurally with no fallback | P96-03; UNEXECUTED |
| BOOT-02 | Given complete definitions and explicit typed bindings, including missing/mismatched cases; when static admission and the later registration join run; then only atomic successful admission/join makes lookup usable and no partial registry is exposed | P96-03; UNEXECUTED |
| PROJ-01 | Given typed source values for text/BigDecimal/Boolean/Instant/missing and scoped roles/identities; when projected; then role constraints, deterministic ordering and resource/screen identity preservation hold, and duplicates/invalid values fail | P96-03; UNEXECUTED |
| PROJ-02 | Given application-specific combined, one-to-many and opaque-source converters; when projected through explicit keys; then only admitted kinds are emitted, with no incidental toString/raw-JSON/Any fallback or inferred inverse write | P96-03; UNEXECUTED |
| PROJ-03 | Given missing/invalid converter outputs and authorized contexts; when projected; then structured failures and hidden/disabled/enabled action presence follow the bindings without granting execution capability; independent semantic consumption remains unchanged | P96-03; UNEXECUTED |
| ROUNDTRIP-01 | Given actual Cozy-generated source for the same fixture; when compiled, admitted and projected through the same CNCF path; then it matches handwritten fixture values/IDs/order and the receipt identifies exact producer/consumer/input/output artifacts | Cozy Phase 76 / P96-03; UNEXECUTED |

Property checks cover identity/scoped reuse, supplied byte/order preservation,
closed kinds and rejection, stable diagnostic first-error paths, deterministic
projection, typed missing and unchanged semantic consumption. No Scala source
or executable specifications are edited in this Slice.

## Pending handoffs and remaining work

LUI-ABI-01 requires real P96-02/03 implementation, external expected-generated
fixture compilation, normal ComponentFactory admission and focused projection
proof. This journal and proposal do not issue it. LUI-GEN-01 remains Cozy
Phase 76 producer work. LUI-ACCEPT-01 requires actual generated files through
the same CNCF path, never a handwritten substitute. All three receipts must
name exact Git revisions/source coordinates, producer/consumer/input/output
identities and actual commands/results. Intermediate handoffs do not require
full tests or public publication.

Later P96-01 promotion must accept design/spec and reconcile runtime,
transport and mutation scope before closure. P96-02/03 implement/prove the
candidate; Cozy P76-01 consumes its mapping through normal contract acceptance;
P96-04/05 consume proposed service names only after that acceptance.
Strict atomic observed-revision delete, committed-write/Display-pending and
read-after-write correspondence remain P96-06/07 obligations, together with
the Display If-Match adapter. Codec remains P96-04; positive create/delete and
reference fixtures remain P96-07/09; context/Phase 98 hook remains P96-10;
consumer/repository release validation and app Phase 5 E2E keep their distinct
owners. No new Phase or accepted public contract is created.

Parent-selected documentary validation is `git diff --check` in the dedicated
CNCF root and complete changed-file comparison against the frozen contract
and matrix, including links and checklist/index preservation. It is reserved
to the parent and not executed by this implementation worker. Independent
review, Step transitions and closure likewise remain pending parent actions.
