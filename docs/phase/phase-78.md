# Phase 78 - Component Capability External Contract

status=closed
planned_at=2026-09-16
reconciled_at=2026-09-18
development_item=DEV-011
predecessor=[Phase 77.2](phase-77.2.md)
checklist=[Phase 78 Checklist](phase-78-checklist.md)

## Package metadata amendment — 2026-10-15

Current corrected work: **CAP-78-PKG01**. This replaces the earlier
Application-owned package assumption; it does not create another parallel item.

The [Component Usage package contract](../spec/component-usage-capability-package.md)
replaces the Application-owned package proposal. Extend public metadata acquisition
for the reviewed Component's inductive Usage family without merging it into the
deductive Component ABI. Reuse Directory/CAR resource handling. Delete obsolete
Application bridge symbols, preserve existing native Component discovery and expose
actual owner/resource/acquisition context to CBD.

Execution order: settle the corrected typed model and owner/source mapping ->
Cozy source/IR/request and CNCF Directory acquisition -> CBD ingestion/catalog/query
through one real Directory path -> normal CAR and parity -> Phase 11.1 Web/MCP.
Migrate affected producer/consumer call sites together. Delete old symbols and
use compilation to expose missed callers; do not retain aliases, deprecated
forwarders, dual readers or automatic old-format conversion. This is pre-release
work, so update CML/JSON fixtures and regenerate outputs instead of maintaining
compatibility. Existing Component provided/required compatibility semantics are
unchanged; they are not compatibility with the removed Application model.

Earlier accepted Steps keep their historical evidence. The current amendment
is accepted at the Step boundary: implementation, Step validation and independent
protected Step review are complete. The local Step commit is being prepared.
It is required before the owning Phase closes. The coordinated plan covers
the revised semantic/ownership/wire scope; the earlier transport-only estimate
does not establish its acceptance. Each owner retains its existing
review/full-validation duties; CBD aggregate full validation remains Phase 11.1.
Phase full review, full validation and local release remain pending.

## Phase closure — 2026-10-16

Closure binding: `cncf78-phase-closure-20261016-01@1`.

All six declared Steps, including CAP-78-PKG01, have accepted local commits.
The current independent whole-Phase review accepted all 25 checklist criteria
and all five completion conditions without blockers or follow-up findings.
The configured full suite completed with 4,568 successful tests in 597 suites,
zero failed tests or aborted suites. Its 13 canceled, one ignored and 46 pending
tests retain those states and are not claimed as executed. Main and Test
compilation succeeded. The local Phase release binds this closure and the
accepted CAR/source-layout and authorization-fixture corrections.

The earlier Step sections preserve the pending states at their recording time;
this section supplies the current Phase completion state. The original Phase
base, accepted Step history and separation of Component Usage from Component
Capability remain authoritative. No artifact publication or CNCF main merge
is claimed. The project version remains `0.5.3-SNAPSHOT`.

Cozy Phase 64 is complete and integrated into its canonical main. Textus CBD
Support Phase 11 still owns its final review and local release. Its aggregate
full validation remains deferred to Phase 11.1; neither Phase 11.1 nor the
transferred CBD Job work in Phase 14 is executed or accepted by this closure.

## Purpose

Make provided and required Component Capability a first-class, externally
consumable Component specification contract. Capability remains a
non-instantiated model IR; CNCF admits and exposes its identity, semantics,
provenance, semantic grounding references, and explicit realization mapping
without executing Capability itself or parsing CML.

The reconciled SimpleModeling model distinguishes:

```text
Component Usage Capability
  = inductively derived Component usage capability

Component Capability
  = architectural capability exposed/provided/required by Components
```

CNCF Phase 78 owns the latter. It must not absorb Component Usage Capability
planning semantics, Use Case grouping, or usage-observation state into the
runtime Component contract.

## Model continuity

The intended continuity is:

```text
Goal / Use Case
      |
      v
Component Usage Capability       (SimpleModeling / Cozy)
      |
      | requires / realized by
      v
Component Capability         (CNCF external contract)
      |
      +-- Operation
      +-- Workflow
      +-- StateMachine
```

Use Case and Component Usage Capability may be many-to-many. CNCF does not infer
that relation and does not require Use Case Group containment. CNCF receives an
admitted Component Capability projection with stable identity and provenance
from Cozy.

## Semantic grounding boundary

Capability names are not sufficient semantic identity. When Cozy supplies
admitted references to Glossary / BoK or Domain Concepts, the CNCF projection
must preserve those references and their provenance in a versioned,
runtime-independent form suitable for discovery.

```text
Component Capability
      |
      +-- semantic references -> Glossary / BoK / Domain Concept
      |
      +-- realization refs ----> Operation / Workflow / StateMachine
```

CNCF does not build, reason over, or require a complete ontology. It preserves
and exposes admitted semantic references; unsupported relations remain absent.
Ontology-like organization is a bottom-up modeling concern upstream, not a
runtime admission requirement.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| CAP-78-PKG01 | Expose Component-owned Usage metadata from Directory/CAR, separate from Component contracts. | closed |
| CAP-78-01 | Freeze Component Capability identity, vocabulary, semantic-reference shape, versioning, and compatibility boundaries. | closed |
| CAP-78-02 | Define and implement the external Component projection for provided and required Capabilities, preserving admitted semantic grounding/provenance. | closed |
| CAP-78-03 | Bind explicit realization references to admitted Operation, Workflow, and StateMachine contracts without embedding orchestration procedure. | closed |
| CAP-78-04 | Integrate admission/discovery with ComponentFactory and package/CAR metadata, including semantic references where present. | closed |
| CAP-78-05 | Prove Cozy producer and Textus CBD Support consumer interoperability with exact identity, source, ABI, semantic-reference, and realization provenance. | closed |

## CAP-78-01 acceptance

The Component Capability design and specification were accepted on 2026-10-05
by independent protected Step review at GPT-6.1 Sol high. Rules R1-R10 and
expected examples E1-E10 establish the semantic contract. Static checks passed
for the four-document scope, including 20 checks, 30 local links/anchors, and
`git diff --check`. The [CAP-78-01 checklist](phase-78-checklist.md#cap-78-01-semantic-contract)
records the closure basis. CAP-78-02 through CAP-78-05 retain the projection,
binding, exposure, and executable interoperability obligations.

## CAP-78-02 acceptance and implementation boundary

Historical pre-CAP-78-05 status — recorded before 2026-10-15: the acceptance
and pending interoperability wording below is retained. Current producer/consumer
acceptance is recorded in [CAP-78-05 acceptance](#cap-78-05-acceptance--2026-10-15).

The [R11/R12 contract](../spec/component-capability.md#r11-concrete-v1-format)
and metadata package provide the external v1 projection, strict codec, selected
reference scope and pure admission primitives. The
[codec](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityProjectionCodecSpec.scala)
and [admission](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityAdmissionSpec.scala)
specifications cover CPJ-01..09 and CPA-01..16 with a hand-authored conformance
input. On 2026-10-05, serial SBT validation passed codec 9/9 and admission
16/16, including successful Main and Test compilation. Independent protected
Step review at GPT-6.1 Sol high accepted the complete 16-file Step and its
42-path contract boundary with no blockers, Hygiene or Development Candidates.
Static source/document checks and `git diff --check` passed. The
[CAP-78-02 checklist](phase-78-checklist.md#cap-78-02-external-projection-and-admission)
records the closure basis. Participant Phase release validation and full review
remain pending. Actual target adapters are accepted in CAP-78-03. Canonical
Factory/package/CAR acquisition and discovery are accepted in CAP-78-04.
Real Cozy/CBD interoperability remains CAP-78-05 with Cozy CAP-64-05 and
CBD Support Phase 11, as reflected in the current status below.

## CAP-78-03 acceptance

Stage Status:
- Current status: DONE
- Owner: CNCF Operation/Workflow/StateMachine contract owners
- Current step: Actual typed target adapters and CPT01..32 specifications accepted.
- Update rule: Retain acceptance established by focused validation and independent
  protected Step conformance; the local Step commit binds this acceptance.
- Closure basis: The four checked criteria in the
  [CAP-78-03 checklist](phase-78-checklist.md#cap-78-03-realization-mapping),
  accepted focused validation and independent protected Step conformance.

[R13](../spec/component-capability.md#r13-actual-typed-target-adapter) defines
the internal explicit acquired metadata and native-selector binding contract.
The [adapter](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityTargetAdapterSpec.scala)
and [binding](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityTargetBindingSpec.scala)
specifications cover actual three-family selection, original-object/source
retention, fail-closed finite acquisition and unchanged R11/R12 admission.
The five modules and their responsibility split are described in the
[design](../design/component-capability.md#actual-typed-target-adapter-structure).
Focused validation passed and independent protected Step conformance accepted
CAP-78-03 on 2026-10-14. The local Step commit binds this acceptance.

Historical pre-CAP-78-05 status — 2026-10-14: the pending producer/consumer
wording below is retained; see [current CAP-78-05 acceptance](#cap-78-05-acceptance--2026-10-15).

CAP-78-04 has accepted canonical metadata acquisition/provider and
ComponentFactory/package/CAR discovery. CAP-78-05 and Cozy CAP-64-05 retain
normal producer/admission/CBD interoperability; CBD Support Phase 11 retains
ingestion, catalog and queries. Each participant's Phase full validation,
full review and local release remain separately owed. Cozy Phase 64 main
integration follows its separate completion and release. These authored typed
fixtures do not close any of those obligations.

## CAP-78-04 canonical package and discovery

Stage Status:
- Current status: DONE
- Owner: CNCF ComponentFactory and packaging owners
- Current step: R14 canonical Directory/CAR acquisition, public native
  provider/catalog, Factory entry and CPL01..16, CPD01..14, CPF01..06 accepted.
- Update rule: Retain acceptance established by focused validation and independent
  protected Step conformance on 2026-10-14; the local Step commit binds this acceptance.
- Closure basis: The three checked criteria in the
  [CAP-78-04 checklist](phase-78-checklist.md#cap-78-04-componentpackage-exposure),
  accepted focused validation and independent protected Step conformance on 2026-10-14.

Historical pre-CAP-78-05 status — 2026-10-14: the acceptance and pending
producer/consumer wording below is retained; see
[current CAP-78-05 acceptance](#cap-78-05-acceptance--2026-10-15).

[R14](../spec/component-capability.md#r14-canonical-package-and-public-native-discovery)
is the package/discovery contract; the
[design](../design/component-capability.md#canonical-package-and-public-discovery-structure)
locates module responsibilities. Focused validation passed and independent
protected Step conformance accepted CAP-78-04 on 2026-10-14. The local Step
commit binds this acceptance. CAP-78-05, Cozy CAP-64-05 and CBD Phase 11 retain
normal producer/consumer proof; participant full validation, review and local
release remain owed. Phase 78 is incomplete.

## CAP-78-05 acceptance — 2026-10-15

Stage Status:
- Current status: DONE
- Owner: CNCF Phase 78 with Cozy Phase 64 and CBD Support Phase 11
- Current step: Actual normal Cozy producer, canonical CNCF public
  ComponentFactory Directory/CAR discovery, and source-free CBD consumption accepted.
- Update rule: Retain acceptance established by focused validation and independent
  protected own Step conformance on 2026-10-15; the local Step commit binds this acceptance.
- Closure basis: The original four checked criteria in the
  [CAP-78-05 checklist](phase-78-checklist.md#cap-78-05-cross-repository-acceptance),
  accepted focused validation and independent protected own Step conformance.

The accepted path uses actual dedicated Cozy production through normal generation,
compiled generated accessors, canonical CNCF public ComponentFactory discovery
of Directory and CAR products, and source-free Textus CBD Support consumption
without semantic inference or CML reparsing. Exact Component source revision,
producer version, generated ABI, package/release version, projection schema,
native target contracts, and CBD management revision remain distinct provenance
domains. Admitted semantic references and realization provenance are preserved.

Common native stagedJob and fresh UnitOfWork compatibility is part of this
accepted CAP-78-05 Step. Full transferred CBD Job acceptance in CBD Phase 14
remains unaccepted context; the standalone Test fixture is not an accepted
production deliverable. Positive ForwardUseCase production remains unadmitted.

Focused validation passed and independent protected own Step conformance accepted
CAP-78-05 on 2026-10-15. The local Step commit binds this acceptance. Each
participant's Phase full review, full validation and local release remain pending,
as does authorized Cozy Phase 64 main integration. CBD Phase 11 CAP-11-02 catalog
and CAP-11-03 queries remain mandatory. Phase 78 remains in progress.

## Ownership boundary

- SimpleModeling defines the conceptual role of Application Capability as the
  application structure / management model and its relation to Use Case.
- Cozy owns Capability syntax, Application/Component Capability IR, source
  normalization, validation, terminology/domain references, and generated
  Component Capability projection production.
- CNCF owns the Component Capability semantic contract, ABI/version admission,
  compatibility, realization references, package exposure, and
  runtime-independent discovery.
- Textus CBD Support owns catalog ingestion and presentation.
- Runtime invocation, authorization, state guards, and availability checks
  remain separate from Capability identity.

## Required behavior

- Provided and required Component Capabilities have stable qualified identity.
- Capability is non-instantiated and is not directly executable.
- Explicit realization references identify admitted Operation, Workflow, or
  StateMachine contracts; realization is mapping, not execution ordering.
- Admitted semantic references from Cozy survive CNCF normalization,
  packaging/CAR exposure, and discovery without name inference.
- Absence of a semantic reference or relation remains absence; CNCF does not
  synthesize ontology, grouping, actor, goal, or domain semantics.
- Application Capability lifecycle/development status is not projected into the
  Component Capability ABI unless a future contract explicitly requires a
  separate traceability surface.

## Completion conditions

- Design and specification documents define identity, provided and required
  declarations, semantic grounding references, realization mapping,
  compatibility, and failure boundaries.
- Executable specifications admit a valid generated projection and reject
  missing, duplicate, ambiguous, malformed, incompatible, or dangling admitted
  references as defined by the contract.
- ComponentFactory and the selected package/CAR surface expose matching
  semantics without name inference or CML reparsing.
- A real Cozy fixture is discoverable through CNCF and consumable by Textus CBD
  Support with exact source, ABI, semantic-reference, and realization
  provenance.
- Focused validation and review evidence are recorded without claiming
  downstream Phase closure.

## Non-goals

- Application Capability planning, lifecycle, or development-management state.
- Use Case Group or Use Case Map semantics.
- Building, completing, or reasoning over an ontology.
- Runtime Capability instances or Capability persistence.
- CML parsing or CML syntax ownership in CNCF.
- Workflow orchestration semantics inside Capability declarations.
- Permission, Authorization, Availability, or Guard consolidation.
- cbd-support UI/search implementation.

## References

- [Component Capability design](../design/component-capability.md)
- [Component Capability Specification](../spec/component-capability.md)
- [Development note](../notes/component-capability-external-contract-proposal.md)
- [Development item journal](../journal/2026/09/2026-09-16-component-capability-external-contract-development-item.md)
- [Phase 78 Checklist](phase-78-checklist.md)
