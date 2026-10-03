# Phase 96 Checklist: Display Model Projection and Protocol

status=in-progress
phase=[Phase 96](phase-96.md)
development_branch=codex/phase-96-display-service
execution_baseline=6cbe877c53c116639feb11fa80546e79c051804c
current_entry=P96-02
development_plan=[Development plan proposal](../journal/2026/10/2026-10-03-phase-96-development-plan-proposal.md)

## Ledger boundary

This is the operational ledger for the existing Phase 96, derived from the
GitHub development-plan proposal and the Phase's existing acceptance criteria.
The source was imported from commit
`1d33ac74081df9ff9df81e52ac1c0a04571e4327`; provenance and reconciliation
are recorded in the [worktree journal](../journal/2026/10/2026-10-03-phase-96-isolated-worktree.md#development-plan-import-and-checklist-bootstrap).
The GitHub update through `713ca6d0db19388d86dda8ef532815375363d830` supersedes
binary-consumption assumptions. The [reconciliation record](../journal/2026/10/2026-10-03-phase-96-phase-76-plan-reconciliation.md)
retains the original 47 obligations and adds explicit generated-ABI handoff proof.
The original execution baseline above is preserved; this import is not a rebaseline.

P96-01 is CLOSED on documentary contract evidence; P96-02 through P96-11 remain
OPEN. The accepted subset is fixed by the design/spec linked below. This ledger
does not claim implementation, passing executable proof, LUI handoff issuance
or Phase closure. Unpromoted wire examples and later physical bindings remain
candidates for their owning Steps.
The [Display proposal](../notes/display-model-phase-96-proposal.md) and
[Presentation Context proposal](../notes/display-protocol-presentation-context-proposal.md)
remain non-normative inputs.

For each checked item, attach design/spec, executable-spec, validation or
review evidence appropriate to the claimed outcome. Record an explicit
candidate ID and relocation target for any admitted deferral; never silently
remove an item or close a stage with unchecked items. Stage status is updated
from the checklist, not inferred from a handoff or a passing fixture.

## Internal checkpoints and validation

These checkpoints group work inside Phase 96; they are not new Phases or
additional full-review boundaries.

| Checkpoint | Stages | Observable result |
| --- | --- | --- |
| A | P96-01 through P96-03 | CNCF ABI/admission/hand-written fixture and projection; LUI-ABI-01 -> Cozy generation LUI-GEN-01 -> CNCF acceptance LUI-ACCEPT-01 |
| B | P96-04 through P96-05 | Versioned codec, component-scoped Detail first, then List read proof |
| C | P96-06 through P96-08 | Update first, create/delete, direct Business Operation and reload |
| D | P96-09 through P96-11 | Reference fixture, context compatibility and consumer/release evidence |

Use focused Executable Specifications and affected compilation for intermediate
Steps and child/dependency handoffs. Such handoffs do not require a full-test
run. CNCF's repository-level release validation remains distinct from app
Phase 5's final real-server aggregate/E2E acceptance. The proposal's full-suite
and review wording is tracked at Phase closure, not repeated at every
checkpoint: normal Step acceptance and one Phase full review per admitted
PLAN epoch, with bounded focused repair/re-review where required.

Cozy Phase 76 owns the planned producer prerequisite. LUI-ABI-01 is an
intermediate CNCF contract handoff; it does not wait for Phase 96 closure.
LUI-ACCEPT-01 is shared focused evidence, not proof of whole-Phase closure.
Any additional dependency on uncommitted Phase 88 work or a new upstream
capability remains an explicit dependency decision. The GitHub import brings
committed work only; it does not adopt the dirty main checkout.

## P96-01: Contract inventory and generated ABI mapping

Stage Status:

- Current status: CLOSED
- Owner: CNCF Display contract owners
- Update rule: Advance only from recorded inventory and contract reconciliation; the checklist below is the closure basis.

Inventory work is recorded in the [contract inventory](../notes/display-model-phase-96-contract-inventory.md)
and [inventory journal](../journal/2026/10/2026-10-03-phase-96-contract-inventory.md).
P96-01A records original-base source inventory. P96-01B records the historical
non-normative [ABI-v1 proposal](../notes/logical-ui-generated-abi-v1-proposal.md)
and its [fixture/proof journal](../journal/2026/10/2026-10-03-phase-96-generated-abi-contract.md).
P96-01C promotes the selected ownership, ABI/provider/admission and runtime
behavior into [runtime design](../design/display-model-runtime.md),
[generated ABI spec](../spec/logical-ui-generated-abi.md) and
[runtime spec](../spec/display-model-runtime.md), with a
[promotion journal](../journal/2026/10/2026-10-03-phase-96-contract-promotion.md).
Independent review and parent admission support all seven documentary outcomes;
the [acceptance record](../journal/2026/10/2026-10-03-phase-96-contract-promotion.md#p96-01-documentary-acceptance)
maps each checked item to its evidence. P96-02/03 implementation,
generation and all LUI receipts remain pending; physical wire/codec,
mutation request/result/adapter/strict-delete implementation and context
decisions retain their later owners. Only P96-01's documentary obligations are
closed; no later implementation or proof obligation is checked.

- [x] An inventory identifies the canonical semantic View result/value type, reusable identity/revision types, schema/constraint authority, Entity/Aggregate mutation entry, ComponentFactory/provider registration, JSON/version conventions and the exact Cozy Phase 74 semantic subset mapped into CNCF-owned ABI types.
- [x] A minimum CNCF-to-Cozy vocabulary map records List/Detail/Section/Field/Value/Action, roles, identity/version ownership, editor metadata placement and explicitly unsupported concepts without weakening semantic Views.
- [x] The journal's proposed value kinds are reconciled with the actual Cozy closed vocabulary; date/choice/reference or structured values are not accepted merely because the proposal names them.
- [x] One canonical read/query Operation naming and routing contract is selected from the competing `list/get` and `query/read` proposals, including component service registration/collision handling; no parallel alias API is assumed.
- [x] Fixture provenance and editable-field/update-target mappings distinguish ESS2-01 from the journal's KnowledgeCandidate-like example, and accepted design/spec scope records resolved ownership, dependencies and remaining decisions.

- [x] Existing StateMachine/Workflow generated-ABI/provider patterns are mapped to Logical UI, including discovery precedence, absent/duplicate/invalid-provider behavior and fail-closed definition admission before runtime lookup.
- [x] Semantic View data, Logical UI definition/configuration and Display Model instances have distinct ownership; LUI-ABI-01 names the exact ABI/version, expected-generated fixture, provider surface, supported subset, diagnostics and focused acceptance needed by Cozy Phase 76.

## P96-02: Logical UI generated ABI and Display Model values

Stage Status:

- Current status: OPEN
- Owner: CNCF Display model owners
- Update rule: Advance only from the admitted value contract and pure construction specifications; the checklist below is the closure basis.

- [ ] Immutable application-neutral model types cover admitted List/items, Detail, sections, fields, typed values, roles, actions and structured diagnostics, conforming to the mapped Phase 74 semantics through CNCF-owned types, without importing Cozy implementation classes or creating a second semantic authority.
- [ ] Display protocol version, generated ABI version, source reference/revision, Phase 74 semantic provenance and Logical UI definition version have distinct documented meanings; opaque IDs and container scopes are preserved.
- [ ] Display capabilities, writable-field bindings and Business Operation metadata remain separate from presentation values and generated metadata and cannot create authorization or client-chosen mutation authority.
- [ ] Given/When/Then construction/validation specifications and property-based checks cover valid/invalid admitted values and identity/scope preservation without server state, Flutter/TFAF types or target-specific layout objects.

- [ ] Versioned immutable Logical UI definition values and a bounded generated metadata-provider surface are implemented separately from runtime Display instances; the Scala 3 ABI has no Cozy or Flutter runtime type dependency.
- [ ] Admission specifications cover incompatible ABI versions, invalid identities/provenance, unknown kinds, duplicate scoped definitions and broken references; a hand-written expected-generated fixture compiles against the ABI.

## P96-03: ComponentFactory admission and Display Projection

Stage Status:

- Current status: OPEN
- Owner: CNCF Display projection owners
- Update rule: Advance only from explicit admitted definitions and deterministic projection evidence; the checklist below is the closure basis.

- [ ] Component-owned definitions use the canonical registration/assembly mechanism and declare source View, target kind/version, field/section/order/role/action mappings; arbitrary runtime discovery and field-name heuristics are excluded.
- [ ] Pure projection consumes canonical semantic View results and admitted Logical UI definitions plus registered application converters/bindings; direct, combined and one-to-many mappings are supported only where admitted, without a general expression language.
- [ ] Explicit typed conversion covers required scalar and opaque/server-native values; missing/unsupported converters return structured diagnostics rather than incidental `toString` or arbitrary JSON-map fallback.
- [ ] List/Detail fixtures and property-based specifications prove deterministic output, ordering, value conversion and unchanged independent semantic View consumption.

- [ ] ComponentFactory discovers and admits the hand-written generated-shape provider before component-scoped lookup; absent, duplicate and invalid provider cases follow the P96-01 contract without runtime CML parsing or heuristic reflection over Cozy.
- [ ] LUI-ABI-01 hands the fixed ABI/provider/source fixture and focused projection proof to Cozy Phase 76 without requiring Phase 96 completion or a public release.
- [ ] LUI-GEN-01 and LUI-ACCEPT-01 bind actual Cozy-generated source to the same fixture: Scala 3 compilation, ComponentFactory admission, failure diagnostics and equivalent List/Detail projection pass without a Cozy runtime dependency; evidence is shared with Cozy P76-05.

## P96-04: Protocol codec and compatibility boundary

Stage Status:

- Current status: OPEN
- Owner: CNCF Display protocol owners
- Update rule: Advance only from the published physical binding and codec compatibility proof; the checklist below is the closure basis.

- [ ] The exact schema/version, metadata member sets, REST envelope/route/media-type binding and canonical encoding are recorded separately from the generated ABI, Phase 74 semantic contract, definition/context versions and Entity revision.
- [ ] Deterministic List/Detail golden fixtures and property-based round trips preserve admitted typed values, opaque identities, scoped fields, order and all required metadata without leaking domain objects or Flutter internals.
- [ ] Structured rejection specifications cover incompatible protocol/ABI/semantic-contract versions, unknown closed kinds, malformed/missing members and duplicate/null inputs according to the admitted strictness policy.
- [ ] After LUI-ACCEPT-01, the first presentation-client codec handoff records provenance, supported subset, compatibility rules and focused evidence; later mutation/context artifacts remain tracked by P96-06/P96-10/P96-11 rather than implied complete.

## P96-05: Component-scoped DisplayService read path

Stage Status:

- Current status: OPEN
- Owner: CNCF Component/Display service owners
- Update rule: Advance only from registered public read Operations and internal canonical View delegation; the checklist below is the closure basis.

- [ ] Component-scoped DisplayService Operations are registered through the admitted assembly/routing contract and resolve only admitted projection identities and object/query inputs.
- [ ] A Detail read first, followed by List/select/Detail, returns versioned protocol objects with source identity/revision through canonical View/query facilities, without a second View store or public Aggregate/View runtime API.
- [ ] Read-boundary specifications reject missing/foreign/undeclared identities and unauthorized access; client inputs cannot choose arbitrary View names, converters, Aggregate Operations, datastore paths or implementation classes.
- [ ] Public-service fixture execution and focused specifications record the complete read slice, stable ordering/metadata and semantic-consumer separation.

## P96-06: Standard Display Mutation contract

Stage Status:

- Current status: OPEN
- Owner: CNCF Display mutation contract owners
- Update rule: Advance only from the admitted mutation schema, binding and error contract; the checklist below is the closure basis.

- [ ] Typed create/update/delete inputs/results identify admitted resource/projection definitions, explicit capability/availability, scoped editable fields, accepted values and constraints; Business Operations are excluded.
- [ ] Every editable field has an explicit registered converter and Entity/Aggregate update target; derived/combined/unbound fields have no inferred inverse mapping and metadata cannot weaken domain validation.
- [ ] Observed revision is bound through the existing transport-metadata contract and an admitted Display adapter, not a new business input; missing/malformed/stale revision and changed-definition behavior are specified.
- [ ] Structured failure and result fixtures cover duplicate/unknown/read-only fields, wrong values, constraints, forbidden/unsupported mutation, conflict, create identity/revision and deletion receipt.
- [ ] The accepted contract distinguishes pre-commit failure from committed-write/Display-pending outcomes, defines read-after-write correspondence and prevents command replay after committed projection failure.

## P96-07: Mutation bridge to Entity/Aggregate runtime

Stage Status:

- Current status: OPEN
- Owner: CNCF Entity/Aggregate and Display service owners
- Update rule: Advance only from canonical mutation delegation and executable lifecycle evidence; the checklist below is the closure basis.

- [ ] The bridge deterministically resolves admitted field bindings into canonical Entity/Aggregate mutation input, reusing authorization, revision, domain validation, UnitOfWork/transaction and structured failure mechanisms without direct Display datastore writes.
- [ ] Update-first specifications prove Detail/Edit/Save/refreshed Detail, current-revision no-op policy, stale-before-equality conflict and source revision correspondence under the admitted policy.
- [ ] Positive create-to-Detail/List and delete-to-List paths are proven for a development fixture that admits them; ESS2-01's unsupported create/delete results alone do not satisfy these paths.
- [ ] Authorization/validation/conflict/capability failures leave the resource unchanged, and a successful committed mutation followed by projection delay/failure preserves its committed outcome without replay.

## P96-08: Business Operation Action descriptors

Stage Status:

- Current status: OPEN
- Owner: CNCF Operation/Display boundary owners
- Update rule: Advance only from descriptor separation and direct Operation/reload proof; the checklist below is the closure basis.

- [ ] Descriptive action bindings reference existing admitted CNCF Operation identities/input metadata and remain distinct from standard Display Mutation, arbitrary URLs and executable Cozy callbacks.
- [ ] One reference Business Operation executes through the ordinary CNCF Operation/REST authorization/input boundary, never through a DisplayService proxy or generic action tunnel.
- [ ] Successful direct invocation followed by explicit DisplayService reload yields the corresponding fresh Display state; a successful Business response alone is not treated as refreshed Display.
- [ ] Boundary specifications verify that displayed actions grant no capability and add no push/subscription, Service Bus refresh or second Operation protocol.

## P96-09: Editing Studio reference fixture

Stage Status:

- Current status: OPEN
- Owner: CNCF and Editing Studio server integration owners
- Update rule: Advance only from reproducible server-side reference traces and fixtures; the checklist below is the closure basis.

- [ ] A declared development-only resource fixture records IDs, semantic state, source revisions, display/input values, readonly/editable bindings, constraints and Operation availability; any difference from ESS2-01 has explicit provenance.
- [ ] The semantic List to DisplayService to List/select/Detail to standard update to canonical mutation to refreshed Detail path has deterministic request/response/state-transition evidence.
- [ ] Create/Detail/List, delete/List and ordinary Business Operation/direct invocation/reload traces use admitted fixture capabilities and include structured failure cases.
- [ ] Focused scenario specifications demonstrate the generic server contract without production Knowledge Candidate/KnowledgeHub admission, Flutter UI completion or app Phase 5 aggregate acceptance claims.

## P96-10: Presentation Context and Phase 98 compatibility hook

Stage Status:

- Current status: OPEN
- Owner: CNCF context/protocol owners
- Update rule: Advance only from reconciled context admission/continuation scope and compatibility evidence; the checklist below is the closure basis.

- [ ] The existing Presentation Context proposal is reconciled with the journal's optional variant/opaque Display Instance correlation hook; each admitted family maps to its ExecutionContext source, permitted readers/updaters, version, lifetime and propagation scope.
- [ ] Physical context binding, strict known-family validation, bounded unknown-family policy and continuation across read/mutation/direct Business Operation/reload/retry are recorded against existing immutable evaluation/admission contracts.
- [ ] Focused fixtures cover admitted optional context preservation, absent/unsupported/expired context, flow isolation and retry/reload behavior without serializing RuntimeContext, security capabilities or UnitOfWork.
- [ ] Phase 98 retains Experiment/Run/Arm allocation, observation and reward ownership; receiving client context cannot assign an arm, create authority or bypass admission, and the core P96-01 through P96-09 slice works without Phase 98 implementation.

## P96-11: Consumer handoff and Phase closure

Stage Status:

- Current status: OPEN
- Owner: CNCF Phase 96 release and integration owners
- Update rule: Advance only when the checklist below and all preceding stage ledgers have complete evidence or explicit admitted dispositions; this checklist is the closure basis.

- [ ] Consumer artifacts freeze the admitted schema/version, DisplayService Operation identities, List/Detail and mutation JSON examples, Business Operation descriptor, context compatibility, unsupported subset and non-goals with reproducible fixture provenance.
- [ ] TFC/TFAF handoff states transport/runtime versus visual-realization ownership; Editing Studio Phase 2 owns application semantics, app Phase 4 prototype feedback and app Phase 5 real-server aggregate/E2E acceptance.
- [ ] Focused validation and Step acceptance evidence cover the ABI/admission/provider/generated-fixture/model/projection/value/codec/read/mutation/security/conflict/Business/separation/platform/context specification matrix.
- [ ] CNCF repository-level full-suite release validation and the single required Phase full review for the admitted PLAN epoch are recorded, with any admitted fixes closed by focused re-review and no extra full review invented for checkpoints.
- [ ] Every Phase 96 acceptance criterion is reconciled against P96-01 through P96-10; all remaining items have explicit accepted evidence or candidate/relocation records, and the release commit, journal and Phase/strategy closure records agree.

## Existing Phase acceptance coverage

| Acceptance boundary | Evidence-owning stages |
| --- | --- |
| Separate semantic View, Logical UI definition and Display instances; unaffected semantic consumers | P96-01, P96-02, P96-03, P96-09 |
| Phase 74 semantic conformance; versioned deterministic minimum protocol | P96-01, P96-02, P96-04 |
| CNCF generated ABI, ComponentFactory admission and Cozy round-trip handoffs | P96-01, P96-02, P96-03 |
| Platform-neutral model and protocol; no Flutter dependency | P96-02, P96-04, P96-09 |
| Reference List/Detail and standard mutation | P96-05, P96-06, P96-07, P96-09 |
| Component-scoped DisplayService; Aggregate/View runtime internal | P96-03, P96-05, P96-07 |
| Direct Business Operations and client-initiated reload | P96-08, P96-09 |
| Frozen consumer handoff and independent repository closure | P96-04, P96-10, P96-11 |
