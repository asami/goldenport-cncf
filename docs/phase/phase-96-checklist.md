# Phase 96 Checklist: Display Model Projection and Protocol

status=in-progress
phase=[Phase 96](phase-96.md)
development_branch=codex/phase-96-display-service
execution_baseline=6cbe877c53c116639feb11fa80546e79c051804c
current_entry=P96-10
development_plan=[Development plan proposal](../journal/2026/10/2026-10-03-phase-96-development-plan-proposal.md)

## Current P96-09 Step acceptance — 2026-10-17

P96-09A's [reference contract](../spec/display-editing-studio-reference.md),
[design](../design/display-editing-studio-reference.md) and
[sample/provenance record](../journal/2026/10/2026-10-17-phase-96-editing-studio-reference.md)
have 13 executable scenarios: REF-E1..E8, REF-P1/P2 and mandatory REF-PG1..PG3.
Focused validation passed: the representative passed 10/10 tests in two suites,
and the accumulator passed 345/345 tests in 43 suites, including REF-PG1..PG3
with real PostgreSQL 17.11, fsync=on and synchronous_commit=on. Both completed
with SBT0/wrapper0, released serial locks and zero failed, aborted, canceled,
ignored or pending tests. Independent whole-Step review
`phase96-p96-09-step-review-001@1`, admitted in review ledger revision 38,
accepts all four original P96-09 criteria with no Current Boundary Blocker.
P96-09 is CLOSED on that acceptance; its local Step commit is the next operation.
All 54 criterion texts remain unchanged, with 45 checked/9 pending. P08 history
is retained and P96-10/P96-11 remain OPEN. Existing fixture-size Hygiene remains
separate. This is no Phase, consumer or production application acceptance.

## Historical P96-08 acceptance — 2026-10-16

P96-08 is independently accepted on all four original criteria. The complete
Step review `phase96-p96-08-step-review-001@1` and independent focused
closure `phase96-p96-08-step-title-focused-result-001@1`, admitted in review
ledger revision 37, resolve `CPB-P96-08-TITLE-CORRESPONDENCE-001`.
The corrected property check passed 2/2 tests in one suite; the same focused
40-suite accumulator passed 332/332, with terminal SBT0/wrapper0 and released
serial locks. Formal Step repair 1 is closed accepted; nonconverging count 0.
Known nonblocking Hygiene remains separately recorded, with no new finding.

The checklist now has 41 checked/13 pending outcomes with all 54 criterion
texts unchanged. P96-09 through P96-11 remain OPEN/not started. Phase 96
remains in-progress: its sole full Phase review, final validation or admitted
aggregate handoff, consumer acceptance and release are not established here.
Step acceptance does not itself prove a completed Git commit. Earlier P96-08
authoring/validation checkpoints below retain their historical pending status;
this current acceptance record supersedes those pending-status projections.

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

### Historical ledger checkpoint before P96-08 acceptance

P96-01 is CLOSED on documentary contract evidence; P96-02 is CLOSED on
static ABI admission and pure Display construction evidence. P96-03 is CLOSED
on all seven criteria after independent whole-Step review and parent admission;
P96-04 is CLOSED at accepted local Step commit `625d533` after focused 151/151 tests in
18 suites, independent whole-Step PASS and parent admission. The ledger has
37 checked and 17 pending outcomes with all 54 criterion texts unchanged;
current_entry=P96-08. P96-05 is independently accepted and CLOSED at local
Step commit `4898201d5679408d6e44510024a6feaea442a66c`; P96-06 is independently
accepted and CLOSED at local Step commit
`1356e41cdee41dd40bc2212c1ef0297ab8875206`. P96-07 is independently accepted
and CLOSED on all four criteria through whole-Step review and focused repair
closure in ledger revision 34, at local Step commit
`3436aef7ae5bb2b3a12000c064159e11791b5282`.
P96-08 is in-progress with A independently accepted through
`phase96-p96-08a-protected-review-001@1` and original 23-suite/168-test PASS.
B's direct canonical Business/explicit Detail specifications are authored;
B representative001 failed Test compilation; representative002 compiled and
completed with 2 passes and 8 failures; representative003 completed with 9
passes and 1 failure. Representative004 then passed 10/10 tests in two suites,
resolving the POST form-carrier cause. Accumulator001 completed 40 suites/332
tests with 331 passes and one BRP1 trim-empty Title failure before mutation.
Unit 4's lawful-title source repair passed property001 (1 suite/2 tests) and
the same accumulator002 (40 suites/332 tests). Both completed with all tests
passing, zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released
serial lock; parent typed result readers accepted the prior assertions.
Independent whole-Step review `phase96-p96-08-step-review-001@1` returned
FINDINGS for `CPB-P96-08-TITLE-CORRESPONDENCE-001`, missing exact initial/reloaded
semantic Title comparisons; no production corruption is alleged. First formal
Step repair 1's first property attempt completed 1 suite/2 tests with 1 PASS/
1 FAIL (SBT1/wrapper1, serial lock released): BRP1 failed before Business at
the first newline/NUL revision-16 raw-store/decoded-title mismatch; BRP2 passed.
The same consumed repair's decoded-View correction is now focused-validated.
`phase96-p96-08-step-title-property-002` passed 1 suite/2 tests and
`phase96-p96-08-step-title-accumulator-001` passed the same 40 suites/332 tests.
Both had zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released
serial lock; parent typed result readers accepted both results. Fresh independent
focused M2 closure and exact Step acceptance/commit remain pending; the earlier
B passes remain evidence of the prior assertions.
All four Step criteria are unchecked. P96-09 through P96-11
remain OPEN and are not started by this handoff. The
[P96-07 acceptance entry](../journal/2026/10/2026-10-16-phase-96-display-list-lifecycle-integration.md#p96-07-step-acceptance-2026-10-16)
records the criterion and PG1–PG12 correspondence, retained validation and
single accepted Step repair. Stage acceptance is not a completed commit or
Phase closure.
The [P96-06 post-commit chronology](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-post-commit-status-reconciliation-2026-10-16)
reconciles the earlier acceptance-time pending status.
The [P96-06 acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-step-acceptance)
records complete A+B acceptance of all five criteria through the original whole-Step
review and independent descriptor-focused PASS in ledger revision 31. Current
subject4 passed 5/5 representative tests and 98/98 accumulator tests; completed
repairs 6/nonconverging 0/accepted are retained.
The [P96-05 acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance)
records current subject13 14/14 focused tests, retained subject12 266/266 baseline,
whole-Step review and independent focused PASS admitted in ledger revision 28.
The local P96-04
Step commit is `625d533bf329e5b274b17cb27681aec3e80f9deb`. The accepted subset is fixed by the design/spec
linked below. P96-02's focused proof remains limited to pure construction and
static admission; P96-03's separate accepted evidence establishes ComponentFactory
integration, projection and the focused LUI consumer proof. The local P96-03
Step commit is `bef59e7`; Cozy P76-05 reciprocal receipt is accepted at `3546755`
and P76-06A documentary handoff at `ddeadab9`. Phase 96 remains in-progress with zero full Phase reviews and no
closure. Unpromoted wire examples and later physical bindings retain their owning Steps.
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
maps each checked item to its evidence. At that documentary acceptance,
P96-02/03 implementation, generation and all LUI receipts remained pending.
P96-02 and P96-03 now have their separate acceptance records below. P96-03
accepts projection, issued LUI-ABI-01, accepted producer LUI-GEN-01 and focused
consumer LUI-ACCEPT-01 proof; the separate Cozy P76-05 receipt, physical wire/codec,
mutation request/result/adapter/strict-delete implementation and context decisions
retain their later owners.

- [x] An inventory identifies the canonical semantic View result/value type, reusable identity/revision types, schema/constraint authority, Entity/Aggregate mutation entry, ComponentFactory/provider registration, JSON/version conventions and the exact Cozy Phase 74 semantic subset mapped into CNCF-owned ABI types.
- [x] A minimum CNCF-to-Cozy vocabulary map records List/Detail/Section/Field/Value/Action, roles, identity/version ownership, editor metadata placement and explicitly unsupported concepts without weakening semantic Views.
- [x] The journal's proposed value kinds are reconciled with the actual Cozy closed vocabulary; date/choice/reference or structured values are not accepted merely because the proposal names them.
- [x] One canonical read/query Operation naming and routing contract is selected from the competing `list/get` and `query/read` proposals, including component service registration/collision handling; no parallel alias API is assumed.
- [x] Fixture provenance and editable-field/update-target mappings distinguish ESS2-01 from the journal's KnowledgeCandidate-like example, and accepted design/spec scope records resolved ownership, dependencies and remaining decisions.

- [x] Existing StateMachine/Workflow generated-ABI/provider patterns are mapped to Logical UI, including discovery precedence, absent/duplicate/invalid-provider behavior and fail-closed definition admission before runtime lookup.
- [x] Semantic View data, Logical UI definition/configuration and Display Model instances have distinct ownership; LUI-ABI-01 names the exact ABI/version, expected-generated fixture, provider surface, supported subset, diagnostics and focused acceptance needed by Cozy Phase 76.

## P96-02: Logical UI generated ABI and Display Model values

Stage Status:

- Current status: CLOSED
- Owner: CNCF Display model owners
- Update rule: Advance only from the admitted value contract and pure construction specifications; the checklist below is the closure basis.

The [P96-02 acceptance record](../journal/2026/10/2026-10-03-phase-96-runtime-values-implementation.md#p96-02-acceptance--2026-10-03)
maps all six outcomes to the combined four-suite, 41-test focused result and
independent complete-accumulator review. Static ABI display-metadata maintenance
is recorded separately as nonblocking HYG-P96-02-001. This closes pure
construction/admission only, not projection, generated-producer or LUI receipts.

- [x] Immutable application-neutral model types cover admitted List/items, Detail, sections, fields, typed values, roles, actions and structured diagnostics, conforming to the mapped Phase 74 semantics through CNCF-owned types, without importing Cozy implementation classes or creating a second semantic authority.
- [x] Display protocol version, generated ABI version, source reference/revision, Phase 74 semantic provenance and Logical UI definition version have distinct documented meanings; opaque IDs and container scopes are preserved.
- [x] Display capabilities, writable-field bindings and Business Operation metadata remain separate from presentation values and generated metadata and cannot create authorization or client-chosen mutation authority.
- [x] Given/When/Then construction/validation specifications and property-based checks cover valid/invalid admitted values and identity/scope preservation without server state, Flutter/TFAF types or target-specific layout objects.

- [x] Versioned immutable Logical UI definition values and a bounded generated metadata-provider surface are implemented separately from runtime Display instances; the Scala 3 ABI has no Cozy or Flutter runtime type dependency.
- [x] Admission specifications cover incompatible ABI versions, invalid identities/provenance, unknown kinds, duplicate scoped definitions and broken references; a hand-written expected-generated fixture compiles against the ABI.

## P96-03: ComponentFactory admission and Display Projection

Stage Status:

- Current status: CLOSED
- Owner: CNCF Display projection owners
- Update rule: Advance only from explicit admitted definitions and deterministic projection evidence; the checklist below is the closure basis.

The existing A/B1/B2 accumulator and issued LUI-ABI-01 are retained. Cozy
P76-04 LUI-GEN-01 producer delivery is accepted at
`ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`, with coordinate
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`, Scala 2.12.18. P96-03C authors
the two unchanged generated consumer sources, external typed fixture and
GC1..GC6/GCP1 in the
[consumer specification](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedLogicalUiAcceptanceSpec.scala).
The [consumer journal](../journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md)
retains exact source mapping and first-pass unexecuted command history, then
records parent-admitted current representative and accumulator results:
7/7 tests in one suite plus 133/133 in the original 15 suites, both terminal
SBT/wrapper exit 0 and serial lock released. GCP1 passed its executed >=100
successful/zero-discard expectations; no separately printed exact case count
is claimed. Source comparisons exited 0 and complete Test classpath inspection
found no cozy_2.12 runtime jar. Independent protected-focused whole-Step review
`phase96-p96-03-step-review-001` revision 1 by
`/root/phase96_p96_03_step_review_001` passed all 28 paths and 19 programs;
parent admission is recorded in `phase96-review-ledger-001` revision 19.
The [acceptance entry](../journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md#2026-10-04--p96-03-independent-step-acceptance)
supports all seven unchanged criteria below. Criterion 7 accepts LUI-ACCEPT-01
as focused consumer proof; Cozy P76-05 reciprocal receipt is accepted at
`3546755`, P76-06A documentary handoff at `ddeadab9`, and local Step commit
is `bef59e7`. Zero blockers and
nonblocking HYG-P96-03-SPEC-TRACEABILITY-001 are retained. P96-03 is CLOSED;
Phase 96 remains in-progress with zero full Phase reviews and no Phase closure.

- [x] Component-owned definitions use the canonical registration/assembly mechanism and declare source View, target kind/version, field/section/order/role/action mappings; arbitrary runtime discovery and field-name heuristics are excluded.
- [x] Pure projection consumes canonical semantic View results and admitted Logical UI definitions plus registered application converters/bindings; direct, combined and one-to-many mappings are supported only where admitted, without a general expression language.
- [x] Explicit typed conversion covers required scalar and opaque/server-native values; missing/unsupported converters return structured diagnostics rather than incidental `toString` or arbitrary JSON-map fallback.
- [x] List/Detail fixtures and property-based specifications prove deterministic output, ordering, value conversion and unchanged independent semantic View consumption.

- [x] ComponentFactory discovers and admits the hand-written generated-shape provider before component-scoped lookup; absent, duplicate and invalid provider cases follow the P96-01 contract without runtime CML parsing or heuristic reflection over Cozy.
- [x] LUI-ABI-01 hands the fixed ABI/provider/source fixture and focused projection proof to Cozy Phase 76 without requiring Phase 96 completion or a public release.
- [x] LUI-GEN-01 and LUI-ACCEPT-01 bind actual Cozy-generated source to the same fixture: Scala 3 compilation, ComponentFactory admission, failure diagnostics and equivalent List/Detail projection pass without a Cozy runtime dependency; evidence is shared with Cozy P76-05.

## P96-04: Protocol codec and compatibility boundary

Stage Status:

- Current status: CLOSED
- Owner: CNCF Display protocol owners
- Update rule: Advance only from the published physical binding and codec compatibility proof; the checklist below is the closure basis.

The [protocol acceptance entry](../journal/2026/10/2026-10-04-phase-96-protocol-contract.md#2026-10-05--p96-04-independent-step-acceptance)
is the closure basis for all four unchanged criteria: actual October 4 focused
151/151 tests in 18 suites, terminal SBT/wrapper exit 0 and serial lock released,
and independent whole-Step review `phase96-p96-04-step-review-001` revision 1
PASS over all 15 paths and five complete Scala programs. Parent admission is
recorded in `phase96-review-ledger-001` revision 25. The
[strict read protocol](../spec/display-model-protocol.md) and
[design](../design/display-model-protocol.md) retain the accepted contract;
the journal preserves the earlier UNEXECUTED authoring history. DSP-WIRE-01
accepts only the strict first List/Detail read snapshot codec handoff;
registered DisplayService Operations and live HTTP proof remain with P96-05.
P96-04 is CLOSED at accepted local Step commit
`625d533bf329e5b274b17cb27681aec3e80f9deb`. All 54 criterion texts are retained with 24 checked and
30 pending; P96-05..11 remain OPEN, Phase 96 remains in-progress and full Phase
review count remains zero. Separate Cozy final release/closure and app Phase 5
aggregate/E2E gates remain pending; the seven prior Hygiene records remain OPEN.

- [x] The exact schema/version, metadata member sets, REST envelope/route/media-type binding and canonical encoding are recorded separately from the generated ABI, Phase 74 semantic contract, definition/context versions and Entity revision.
- [x] Deterministic List/Detail golden fixtures and property-based round trips preserve admitted typed values, opaque identities, scoped fields, order and all required metadata without leaking domain objects or Flutter internals.
- [x] Structured rejection specifications cover incompatible protocol/ABI/semantic-contract versions, unknown closed kinds, malformed/missing members and duplicate/null inputs according to the admitted strictness policy.
- [x] After LUI-ACCEPT-01, the first presentation-client codec handoff records provenance, supported subset, compatibility rules and focused evidence; later mutation/context artifacts remain tracked by P96-06/P96-10/P96-11 rather than implied complete.

## P96-05: Component-scoped DisplayService read path

Stage Status:

- Current status: CLOSED
- Owner: CNCF Component/Display service owners
- Update rule: Advance only from registered public read Operations and internal canonical View delegation; the checklist below is the closure basis.

P96-05 is independently accepted on all four unchanged criteria. The
[acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance)
maps their evidence to whole-Step review `phase96-p96-05-step-review-001`
revision 1 and fresh focused PASS `phase96-p96-05-naming-focused-review-001`
revision 1, admitted in `phase96-review-ledger-001` revision 28. The sole naming
blocker is resolved; current subject13 passed 14/14 tests in two suites after
normal generation and production/Test compilation. Subject12's 266/266 tests
in 32 suites remain the retained baseline, not a fresh accumulator on renamed
source. All three successful result receipts have SBT/wrapper exit 0 and serial
lock released. Local Step commit is `4898201d5679408d6e44510024a6feaea442a66c`. At P96-05 acceptance the
ledger had 28 checked/26 pending criteria, current_entry=P96-06; P96-06 was
IN_PROGRESS and five later stages remained OPEN. Phase 96 remains in-progress with zero full Phase reviews.
All ten Hygiene records remain OPEN; no Development Candidate is admitted.

The following authoring, failed execution and pending acceptance statements
are retained historical checkpoints, superseded by the acceptance entry above.
P96-05A authoring and bounded contract repair were in-progress. The original
23-path manifest and revisions 001..004 remain retained history; current
`phase96-p96-05a-implementation-manifest-010` revision 1 contains 27 consumed
workflow inputs, 26 product-stage paths, 19 Scala target programs and 23 authored
behavior groups, including the HTTP package bridge, bounded suffix repair and
formatter precedence Envelope E4.
The task-local consumed validation input, `.cozy/launcher.yaml`, remains outside
staging and commits. Its supported `runtime.dev-dir`
selects the admitted Cozy Phase76 worktree. The first representative completed production Compile batches
but failed Test compilation with six errors; no behavioral examples executed.
The compile corrections are authored and passed parent static preflight. Retry002
failed normal Information CML generation because the configured original Cozy
runtime classpath was stale; Test compilation and behavioral examples did not run.
The dedicated Cozy runtime prerequisite completed with SBT/wrapper exits 0 and
the serial lock released; the parent verified its nonempty classpath, existing
entries and version `0.3.3-SNAPSHOT`. Representative003 completed normal
Information CML generation and production Compile but failed Test compilation
with three missing `create_Core` hook errors; zero behavioral examples executed.
The exact test-only unused hook correction under
`phase96-p96-05a-factory-core-fix-manifest-001` revision 1 is authored.
Representative004 completed normal Information CML generation and production
and Test compilation, then reported five visible E1..E5 bootstrap failures
requiring the query output contract. E6's header is visible; E7/E8 and final test
counts are not confirmed. SBT and wrapper exited 255 and the serial lock was
released; there is no final test summary or passing behavioral evidence, and no
separate termination cause is claimed. The canonical specified nonvoid
OperationResponse result correction under
`phase96-p96-05a-output-contract-fix-manifest-001` revision 1 is authored,
including assertions inside existing successful-bootstrap E1 and public-read/
resolver E8. Representative005 failed Test compilation with two
`NonEmptyVector.foreach` diagnostics; SBT/wrapper exited 1 and the serial lock
was released. The parent authored the exact `.toVector` iteration corrections.
Representative006 completed normal Information generation and production/Test
compilation, then ran 8 tests in one completed suite with zero aborted/canceled/
ignored/pending: E1/E4/E5/E6/E7/E8 passed, E2 failed with `None.get` at ReadSpec
line 62, and E3 observed HTTP404 instead of200 at line 108. SBT/wrapper exited 1
and the serial lock was released. This is partial behavior evidence only.

The frozen `P96-05A-HTTP-SUFFIX-001` slash-delimiter correction and three new
Given/When/Then regression groups are authored, preserving CLI selector and
alias policy. Representative007 with subject revision7 completed normal
Information generation and production/Test compilation, then ran 8 tests in one
completed suite with zero aborted/canceled/ignored/pending: E1/E4/E5/E6/E7/E8
passed, E2 failed with `None.get` at ReadSpec line 62, and E3 observed HTTP400
instead of200 at line 108. SBT/wrapper exited 1 and the serial lock was released.
The three suffix groups compiled but did not run. This is partial behavior
evidence only.

Parent source reassessment refuted the retained framework-Argument hypothesis:
`_domain_request` removes framework/security arguments including `textus.*`
before ordinary business dispatch. The frozen `P96-05A-HTTP-FAILURE-EVIDENCE-001`
correction added actual response-body clues and HTTP preconditions inside existing
ReadSpec E2/E3. Representative008 with subject revision8 completed normal
Information generation and production/Test compilation, then ran 8 tests in one
completed suite: 6 passed (E1/E4/E5/E6/E7/E8), 2 failed, and zero were aborted,
canceled, ignored or pending. E2 at ReadSpec line64 and E3 at line123 both recorded
HTTP400 with `HTTP ingress not configured` and detailCode10799020000. SBT/wrapper
exited 1 and the serial lock was released; the 32-suite accumulator did not run.
Parent diagnosis established that `DisplayServiceReadFixture.open` used the
generic initializer's `Protocol.empty` handler; effective Display registration
correctly preserves the supplied handler. The frozen `P96-05A-HTTP-INGRESS-001`
correction imports `ProtocolHandler` and supplies
`Protocol(handler = ProtocolHandler.default)` only at the existing open-session
initialize call. This is authored fixture assembly only; the generic initializer
default/signature/body, other callers and production service ownership remain unchanged.
Representative009 with subject revision9 completed normal Information generation,
production compilation of 203 Scala programs and Test compilation of the corrected
fixture. Its eight tests in one completed suite had six successes
(E1/E4/E5/E6/E7/E8), two failures and zero aborted/canceled/ignored/pending.
Existing first/List HTTP200 checks completed before E2's selected-identity Option
guard failed at ReadSpec72. Its printed List body is a bare `cncf.display-model.v1`
object with two `model.items` and no `data`/`execution`. E3's List HTTP200 check
completed before `None.get` in `_list_resources`404 called125; its body was not
printed. SBT/wrapper exited1 and the serial lock was released. This is partial
HTTP200 progress, not whole-Slice success; the 32-suite accumulator did not run.

Parent PLAN rejected the unimplemented explicit-format fixture workaround:
output-format General Rules6/7 and path-resolution R4 already place `.json`
above the mode default. The retained `Request.Source.Http` reaches the formatter,
whose selection omitted its final-path suffix. Frozen
`P96-05A-HTTP-JSON-SUFFIX-001` restores supported HTTP suffix selection below
explicit request format and above configured/mode fallback in the existing
formatter, with real behavior Envelope E4 compatibility coverage. This is
authored repair only; original `.json` URIs, shape-only fixture properties,
RunMode, supplied standard handler and all original expectations remain unchanged.
Representative010 with subject revision10 completed normal Information generation,
production compilation of 204 Scala sources and Test compilation of one Scala
source. It ran 8 tests in one completed suite: 7 successes
(E1/E3/E4/E5/E6/E7/E8), one failure (E2 at ReadSpec81), and zero
aborted/canceled/ignored/pending. SBT/wrapper exited1 and the serial lock was
released. Actual log:
`/private/tmp/skill.cncf.d/a-461faba1-3a9e-4e4d-a186-917b5a776d69/55559-20261005T020731Z.log`.
E3 passed its complete finite empty/reversed/five-kind and exact UTF16,
decimal-scale and nanosecond HTTP cases. E2 completed its initial HTTP200 and
selected-identity guards and requested selected Detail before its first
Content-Type equality observed `Some("MediaType(application/json)")` instead of
unchanged `Some("application/json")`. No raw header string was printed; later
E2 envelope/metadata/source/order/semantic-consumer expectations remain unverified.

Parent source diagnosis identifies the existing fixture's parsed MediaType
`.toString` diagnostic view, rather than MIME mainType/subType, as
`P96-05A-HTTP-MEDIA-TYPE-VIEW-001`. The frozen correction changes only that
test-fixture String extraction and these three current-status documents. It is
authored only: expected application/json, response bytes/status, all 23 groups,
original .json URI/shape-only properties/RunMode/defaultHandler/generic empty
initializer, formatter suffix selection and Envelope E4 remain unchanged.
This does not establish a production Content-Type defect or Step acceptance.

Current status: Representative012 subject12 passed 14/14 tests in 2 suites; accumulator002 subject12 passed 266/266 tests in 32 suites. Both completed with SBT0/wrapper0, released serial lock and zero failures/aborts/canceled/ignored/pending. Parent typed readers accepted current request/subject/runner and consumed permission. Registration E2/E3/E5 and their remaining assertions now execute successfully. Focused validation is accepted evidence, not independent Step acceptance. Fresh protected whole-Step Solhigh review, Step acceptance/commit, final Phase full review/validation/release and separate Cozy/app gates remain pending. P96-05..11 remain OPEN; all54 criterion texts and24checked/30pending states are retained.

The frozen registration conformance correction preserves the existing ABI:
absent metadata succeeds without service capability reads; a present-empty
provider fails static admission with LUI96_DEFINITIONS_MISSING before runtime
or service getters. The display construction catch now uses the existing
semantic componentInvalid(message) with exactly `Display service construction failed`
and retains no raw Throwable. Registration E3 adds taxonomy/message/cause checks;
E5 compares the contained Subsystem reference while preserving its remaining
identity/effective-consumer assertions. These E2/E3/E5 edits passed representative012 and accumulator002; independent acceptance remains pending.

All failed001..010 receipts retain their historical failure status, and the
retained runtime prerequisite is not service acceptance. Normal build-target
Information generation remains enabled. Original Goal, three bases, epoch1,
ledger26, seven Hygiene records and candidate2 unfinished/completed1/nonconverging0
are retained. All 54 criterion texts and 24 checked/30 pending outcomes remain
unchanged; P96-05..11 stay OPEN and the full Phase review count is zero.
[Runtime SR1..SR4](../spec/display-model-runtime.md#p96-05a-ordinary-component-scoped-read-service)
and [protocol R9](../spec/display-model-protocol.md#r9-pure-typed-json-and-framework-envelope-adapter)
fix passive mappings, normal authorized query/read, prepared effective registration
without Core replacement and JSON-only envelope ownership. The
[read-service journal](../journal/2026/10/2026-10-05-phase-96-display-read-service.md)
retains expected traces, historical failed001..010 results, actual representative011
and accumulator001 evidence, and actual representative012 14/14 tests in 2 suites
and accumulator002 266/266 tests in 32 suites focused success; independent
whole-Step review and acceptance remain pending. The original 22 groups are retained; Envelope E4 makes
23. Accumulator001 passed Registration E1/E4/E6, Envelope E1..E4, public property
E1/E2 and suffix E1..E3; only Registration E2/E3/E5 failed, and E5's later assertions
were not reached. Properties passed their >=100-successful/zero-discard gates,
without a separately printed exact case count claim. Representative012 and
accumulator002 passed all selected tests, including Registration E2/E3/E5 and
E5's remaining assertions; independent review remains pending. All four criteria
below remain unchanged/unchecked, the ledger
stays 24 checked/30 pending across 54 criteria, P96-06..11 stay OPEN and full
Phase review/release/closure remain pending.

- [x] Component-scoped DisplayService Operations are registered through the admitted assembly/routing contract and resolve only admitted projection identities and object/query inputs.
- [x] A Detail read first, followed by List/select/Detail, returns versioned protocol objects with source identity/revision through canonical View/query facilities, without a second View store or public Aggregate/View runtime API.
- [x] Read-boundary specifications reject missing/foreign/undeclared identities and unauthorized access; client inputs cannot choose arbitrary View names, converters, Aggregate Operations, datastore paths or implementation classes.
- [x] Public-service fixture execution and focused specifications record the complete read slice, stable ordering/metadata and semantic-consumer separation.

## P96-06: Standard Display Mutation contract

Stage Status:

- Current status: CLOSED
- Owner: CNCF Display mutation contract owners
- Update rule: Advance only from the admitted mutation schema, binding and error contract; the checklist below is the closure basis.

P96-06 complete A+B follows the [mutation contract](../spec/display-mutation.md)
and [design](../design/display-mutation.md). The
[Step acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-step-acceptance)
maps all five unchanged criteria to original whole-Step review
`phase96-p96-06-step-review-001@1` and independent focused PASS
`phase96-p96-06-descriptor-focused-review-001@1`, admitted in ledger revision 31.
Current `phase96-p96-06b-validation-subject-001@4` passed 5/5 tests in one suite
and 98/98 in fourteen suites with SBT/wrapper exit 0 and serial lock released.
Historical A readiness 8/8 plus 57/57 and B subject3 8/8 plus 97/97 retain their
distinct subjects; A alone was not Step acceptance. Completed repairs
6/nonconverging 0/accepted are retained. P96-06 is independently accepted and
CLOSED at local Step commit `1356e41cdee41dd40bc2212c1ef0297ab8875206`; the
[post-commit chronology](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-post-commit-status-reconciliation-2026-10-16)
records the completed commit without changing the original Phase base. At
P96-06 acceptance, P96-07 canonical write, strict deletion, ordinary route
exposure and actual contextual reload/positive lifecycle proof remained
pending; the separate P96-07 acceptance below now establishes those outcomes.
Existing read-v1 is unchanged.

- [x] Typed create/update/delete inputs/results identify admitted resource/projection definitions, explicit capability/availability, scoped editable fields, accepted values and constraints; Business Operations are excluded.
- [x] Every editable field has an explicit registered converter and Entity/Aggregate update target; derived/combined/unbound fields have no inferred inverse mapping and metadata cannot weaken domain validation.
- [x] Observed revision is bound through the existing transport-metadata contract and an admitted Display adapter, not a new business input; missing/malformed/stale revision and changed-definition behavior are specified.
- [x] Structured failure and result fixtures cover duplicate/unknown/read-only fields, wrong values, constraints, forbidden/unsupported mutation, conflict, create identity/revision and deletion receipt.
- [x] The accepted contract distinguishes pre-commit failure from committed-write/Display-pending outcomes, defines read-after-write correspondence and prevents command replay after committed projection failure.

## P96-07: Mutation bridge to Entity/Aggregate runtime

Stage Status:

- Current status: CLOSED
- Owner: CNCF Entity/Aggregate and Display service owners
- Update rule: Advance only from canonical mutation delegation and executable lifecycle evidence; the checklist below is the closure basis.

- [x] The bridge deterministically resolves admitted field bindings into canonical Entity/Aggregate mutation input, reusing authorization, revision, domain validation, UnitOfWork/transaction and structured failure mechanisms without direct Display datastore writes.
- [x] Update-first specifications prove Detail/Edit/Save/refreshed Detail, current-revision no-op policy, stale-before-equality conflict and source revision correspondence under the admitted policy.
- [x] Positive create-to-Detail/List and delete-to-List paths are proven for a development fixture that admits them; ESS2-01's unsupported create/delete results alone do not satisfy these paths.
- [x] Authorization/validation/conflict/capability failures leave the resource unchanged, and a successful committed mutation followed by projection delay/failure preserves its committed outcome without replay.

### Required native PostgreSQL evidence within P96-07

The [PostgreSQL persistence specification](../spec/postgresql-phase-96-persistence.md)
applies the [primary Knowledge Store decision](../design/postgresql-primary-knowledge-store.md)
to these four existing criteria; the
[adoption journal](../journal/2026/10/2026-10-14-postgresql-primary-knowledge-store.md)
records provenance and current gaps. These are subordinate evidence
obligations, not additional checklist criteria or an accepted Step.

| Existing P96-07 criterion | Mandatory PostgreSQL correspondence |
| --- | --- |
| 1: canonical delegation | PG1/PG2/PG5–PG8/PG11/PG12: normal ComponentDataStore and SystemNode-managed selection, registered Entity AND Aggregate, ordinary routes, canonical authorization/domain/revision/transaction ownership. |
| 2: update-first lifecycle | PG2/PG5/PG7/PG10/PG12: native changed/no-op/stale-before-equality update, body/revision correspondence and actual selected contextual committed Detail reload. |
| 3: positive create/delete | PG3/PG4/PG6/PG8/PG9/PG11/PG12: real positive canonical create-to-Detail/List and physical/logical delete-to-List, independently durable root/body results and native duplicate/race/rollback evidence. |
| 4: failure and committed outcome | PG4–PG12: complete selected/unrelated state preservation, rollback and explicit/escaped/null post-real-commit ACK distinctions, authorization/capability failures and actual committed-pending/projection-failure outcome without replay. |

Real positive native PostgreSQL CUD, body/revision, concurrency, rollback and
ACK evidence is required, together with normal Component/SystemNode lifecycle,
registered Entity AND Aggregate and ordinary-route selected contextual
`afterCommitResponseC` reload. PostgreSQL identity grammar, unsupported-only
results, test-only JDBC or MySQL proxy evidence do not satisfy this gate.
MySQL, SQLite and memory retain their required regression coverage.

Earlier C4C readiness snapshot (2026-10-16, before whole-Step acceptance):
all four original criteria were OPEN, with 33 checked/21 pending outcomes
across the unchanged 54-criterion ledger. Focused C4C representative and expanded
accumulator validation, including native PostgreSQL evidence, and fresh
independent whole-twelve-path auxiliary assurance have completed with no current
auxiliary boundary blocker. The
[C4C management chronology](../journal/2026/10/2026-10-16-phase-96-display-list-lifecycle-integration.md#c4c-current-readiness-and-candidate-reconciliation-2026-10-16)
supersedes the earlier unvalidated atomic-create draft/no-PostgreSQL-result
checkpoint for current readiness only. Whole-P96-07 criterion/PG1–PG12
correspondence and independent protected whole-Step acceptance remain required;
all four checkboxes stayed OPEN at that auxiliary checkpoint. That snapshot
retained current_entry=P96-07, PLAN epoch 1, zero full Phase reviews and the original
authority, Goal binding, base and repair/review history. P96-08 through P96-11,
separate Cozy/server/app acceptance and the original aggregate handoff, Step
review, single full Phase review, final validation and release gates remain required.

Current P96-07 acceptance (2026-10-16): independent protected whole-Step
review `phase96-p96-07-step-review-001@1` covered all 181 owned paths and
125 target programs, supporting the four criteria and PG1–PG12 while finding
three bounded test-conformance blockers. Independent focused PASS
`phase96-p96-07-step-focused-review-001@1` resolved all three after the exact
four-test repair. Parent admission in ledger revision 34 accepts all four
criteria. Current focused 4-suite/21-test success supplements earlier
39-suite/529-test and 138-suite/2127-test evidence; the latter retains five
historical pending cases. Step repair 1 is accepted, nonconverging 0.
The exact local Step commit subsequently completed at
`3436aef7ae5bb2b3a12000c064159e11791b5282`; the preceding acceptance-time
preparation status remains historical.
Historical post-P96-07/pre-P96-08 checkpoint (superseded by Current acceptance):
The ledger then had 37 checked/17 pending criteria and current_entry=P96-08;
P96-08 is in-progress with A independently accepted through
`phase96-p96-08a-protected-review-001@1`, original 23-suite/168-test PASS,
and B's representative001 Test compilation failure and representative002
compiled result of 2 passes/8 failures retained. Representative003 completed
with 9 passes/1 failure; its missing-resource owner positive was not a Business
success, and preceding production negatives are not yet accepted proof.
Representative004 subsequently passed 10/10 tests in two suites, resolving the
form-carrier cause. Accumulator001 completed 40 suites/332 tests with 331
passes and one BRP1 trim-empty Title failure before mutation. Unit 4's
lawful-title source repair passed property001 (1 suite/2 tests) and the same
accumulator002 (40 suites/332 tests) on the prior assertions. Independent
whole-Step review `phase96-p96-08-step-review-001@1` returned FINDINGS for
`CPB-P96-08-TITLE-CORRESPONDENCE-001`, missing exact initial/reloaded semantic
Title comparisons; no production corruption is alleged. First formal Step
repair 1's first property attempt completed 1 suite/2 tests with 1 PASS/1 FAIL
(SBT1/wrapper1, serial lock released): BRP1 failed before Business at the first
newline/NUL revision-16 raw-store/decoded-title mismatch; BRP2 passed. The same
consumed repair's decoded-View correction is now focused-validated.
`phase96-p96-08-step-title-property-002` passed 1 suite/2 tests and
`phase96-p96-08-step-title-accumulator-001` passed the same 40 suites/332 tests.
Both had zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released
serial lock; parent typed result readers accepted both results. Fresh independent
focused M2 closure and exact Step acceptance/commit remain pending. Earlier B
passes remain evidence of the prior assertions;
P96-09..11 remain OPEN. Phase 96 stays in-progress, with zero full Phase
reviews and no final validation, release or aggregate acceptance claim.
See the [acceptance entry](../journal/2026/10/2026-10-16-phase-96-display-list-lifecycle-integration.md#p96-07-step-acceptance-2026-10-16).

## P96-08: Business Operation Action descriptors

Stage Status:

- Current status: CLOSED
- Owner: CNCF Operation/Display boundary owners
- Update rule: Advance only from descriptor separation and direct Operation/reload proof; the checklist below is the closure basis.

All four outcomes below are accepted through the complete Step review plus
independent focused closure in ledger revision 37. The canonical
[acceptance record](../journal/2026/10/2026-10-16-phase-96-business-operation-descriptor.md#p96-08-step-acceptance-2026-10-16)
maps each original criterion to its accepted evidence. This is Step acceptance,
not Phase/consumer release or proof of a completed Git operation.

### Historical P96-08 authoring and validation checkpoints

P96-08A's [descriptor specification](../spec/display-business-operation-descriptor.md),
[decided design](../design/display-business-operation-descriptor.md), code and
BA1–BA7 executable scenarios/laws are independently accepted through
`phase96-p96-08a-protected-review-001@1`, retaining the original 23-suite/168-test
PASS. The optional ordinary `display.describeBusiness` Operation is metadata
only. P96-08B's BR1–BR7/BRP1–BRP2/BRH1–BRH3/BRPG1–BRPG3 specifications are
authored for ordinary direct Business, canonical stage-only mutation and
explicit fresh Detail. Their development provenance is
`cncf.development.display-business` / `canonical-review-<token>` / `fixture-1`,
not an ESS2 `workflowState` mapping. Representative001 failed Test compilation;
representative002 compiled and ran BR1–BR7/BRPG1–BRPG3 with 2 passes and 8
failures. Representative003 completed with 9 passes and 1 failure: legitimate
owner POST returned 400 for missing `resourceId` before Business execution.
That positive is not a Business success, and preceding production negatives
are not yet accepted proof. Prior repairs prepared declared schema before
baseline, added existing typed Aggregate UserPermission authorization, recorded
actual accepted provider PrincipalIds and observed native/full-save attempts.
The current repair uses a standard UrlForm body for exact supplied ordinary
Business POST selectors and omits them from query, preserving supplied missing,
duplicate and invalid values. GET/non-Business query behavior, framework/test/
forged query facts and the actual session header remain unchanged.
Representative004 subsequently passed 10/10 tests in two suites, resolving
the form-carrier cause. Accumulator001 completed 40 suites/332 tests: 331
passed, one BRP1 failure, zero aborted/canceled/ignored/pending. A generated
trim-empty Title failed before mutation with `DSP96_ROLE_INVALID`, not at
permission or persistence; unchanged `Display.scala` requires nonblank Title.
Unit 4 (`VAL-P96-08B-LAWFUL-TITLE-DOMAIN-004`) already authors a fixed `T`
prefix retaining the exact raw Unicode/control payload through every shrink,
including empty payload. The original newline/NUL payload at revision 16 uses
the same complete commit/reload oracle across all six target/representation
combinations. Exact independent pre-state/reloaded-title and complete state
oracles, at least 100 successes per law and zero discards remain required.
This source repair passed `phase96-p96-08b-business-review-property-001`
(1 suite/2 tests) and `phase96-p96-08b-business-review-accumulator-002`
(the same 40 suites/332 tests). Both completed with all tests passing, zero
failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released serial lock;
parent typed result readers accepted the results for the prior assertions.
Independent whole-Step review `phase96-p96-08-step-review-001@1` returned
FINDINGS for `CPB-P96-08-TITLE-CORRESPONDENCE-001`: missing comparison of initial
logical, initial Display and reloaded Display semantic Title to the independently
supplied `sample.title`. No production corruption is alleged. First formal
Step repair 1's `phase96-p96-08-step-title-property-001` completed 1 suite/2
tests with 1 PASS/1 FAIL, SBT1/wrapper1 and serial lock released. BRP1 failed
before Business at the first newline/NUL revision-16 initial assertion; BRP2
passed. The parent-prescribed observation was raw DataStore
`utf16:0054000a0000`, not the decoded semantic View. This test observation-layer
error does not demonstrate production corruption. The same consumed repair's
correction is now focused-validated: use already captured
`before.resident.views.selected.record` against independent `sample.title`.
Initial Display and the already fetched reload JSON retain their exact
comparisons without another read. Earlier B passes remain evidence of the prior
assertions. `phase96-p96-08-step-title-property-002` passed 1 suite/2 tests and
`phase96-p96-08-step-title-accumulator-001` passed the same 40 suites/332 tests.
Both had zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released
serial lock; parent typed result readers accepted both results. Fresh independent
focused M2 closure remains pending before exact Step acceptance/commit. All four unchanged criteria below
remain unchecked. The ledger retains all 54 criterion texts, 37 checked/17
pending outcomes and zero full Phase reviews. No whole-Step
commit, Phase acceptance or closure is claimed.

### Accepted P96-08 criteria

- [x] Descriptive action bindings reference existing admitted CNCF Operation identities/input metadata and remain distinct from standard Display Mutation, arbitrary URLs and executable Cozy callbacks.
- [x] One reference Business Operation executes through the ordinary CNCF Operation/REST authorization/input boundary, never through a DisplayService proxy or generic action tunnel.
- [x] Successful direct invocation followed by explicit DisplayService reload yields the corresponding fresh Display state; a successful Business response alone is not treated as refreshed Display.
- [x] Boundary specifications verify that displayed actions grant no capability and add no push/subscription, Service Bus refresh or second Operation protocol.

## P96-09: Editing Studio reference fixture

Stage Status:

- Current status: CLOSED
- Owner: CNCF and Editing Studio server integration owners
- Update rule: Advance only from reproducible server-side reference traces and fixtures; the checklist below is the closure basis.

- [x] A declared development-only resource fixture records IDs, semantic state, source revisions, display/input values, readonly/editable bindings, constraints and Operation availability; any difference from ESS2-01 has explicit provenance.
- [x] The semantic List to DisplayService to List/select/Detail to standard update to canonical mutation to refreshed Detail path has deterministic request/response/state-transition evidence.
- [x] Create/Detail/List, delete/List and ordinary Business Operation/direct invocation/reload traces use admitted fixture capabilities and include structured failure cases.
- [x] Focused scenario specifications demonstrate the generic server contract without production Knowledge Candidate/KnowledgeHub admission, Flutter UI completion or app Phase 5 aggregate acceptance claims.

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
