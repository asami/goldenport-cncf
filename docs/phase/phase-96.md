# Phase 96: Display Model Projection and Protocol

status=closed
closed_at=2026-10-17
planned_at=2026-09-29
checklist=phase-96-checklist.md
development_plan=../journal/2026/10/2026-10-03-phase-96-development-plan-proposal.md
development_branch=codex/phase-96-display-service
execution_baseline=6cbe877c53c116639feb11fa80546e79c051804c
execution_note=../journal/2026/10/2026-10-03-phase-96-isolated-worktree.md
driver=KnowledgeHubProject/nict-editing-studio-app
driver_phase=KnowledgeHubProject/nict-editing-studio-app Phase 4
integration_acceptance_phase=KnowledgeHubProject/nict-editing-studio-app Phase 5
server_reference_phase=KnowledgeHubProject/nict-editing-studio Phase 2
abstract_ui_dependency=asami/cozy Phase 74 semantic authority; Cozy Phase 76 producer ABI
strategy=textus-knowledge-workbench/docs/strategy/knowledge-application-integration.md

## Current final Phase acceptance — 2026-10-17

Phase 96 and all eleven Stage ledgers are CLOSED; all original 54 checklist
outcomes and thirteen Phase acceptance criteria are reconciled without changing
their text. P01–P10 and P11A have eleven actual ordinary delivery commits;
P11A is `a9dc024dac9aeaaf69ed541c4c0a0bfa6ef029c5`. P11B is final Phase
validation/release bookkeeping, not another implementation Step or review.

PLAN epoch 1 retains exactly one comprehensive Phase review,
`phase96-full-review-001@1` (Sol high, PASS). Two focused Phase closure reviews
retain the first narrowing finding and final
`phase96-final-repair-focused-review-002@1` PASS with zero blockers. Phase
repair 2 is accepted, nonconverging 0; current typed review ledger is revision 45.

Actual final CNCF repository-full validation: **638 suites / 5,670 succeeded / 0 failed / 0 aborted; 5 canceled, 1 ignored, 46 pending**.
SBT and wrapper exited 0, with serialized lock released. All seventeen mandatory
native PostgreSQL suites passed 424 positive cases in this run, using the
pinned 17.11 server/durability contract; canceled/ignored/pending cases are
not substitutes for required PostgreSQL proof. The original full run's 23
failures, failed focused run, and historical evidence limitations are retained.
Goldenport full validation (47 suites/383 passed/6 ignored) and separately
accepted Cozy Phase 76 full validation (179 suites/2,484 passed/9 canceled)
are reused on their actual relevant program contexts. The unchanged core
producer received exactly one approved local SNAPSHOT refresh, no source edit.

The [final acceptance journal](../journal/2026/10/2026-10-17-phase-96-final-acceptance.md)
maps the original criteria and delivery history. All 25 accepted Hygiene records
and one Development Candidate remain once in their original canonical journals;
both unpersisted ledgers are empty. These are nonblocking follow-ups, not fixes
silently included in this release.

This is local release content under `Phase-Closure-Binding: phase96-final-closure-20261017-001@1`.
It establishes committed closure only when that exact release commit and native
committed-document receipt succeed; authored status alone does not complete the
Goal. No main integration, push, publication or successor execution is included.
Editing Studio Phase 2 owns application semantics; app Phase 4 owns prototype
feedback and app Phase 5 owns real-server aggregate/E2E acceptance. That consumer
acceptance remains separate; CNCF has not deferred its own full validation.

The dated sections below preserve earlier snapshots and their then-pending
statements. This current record supersedes their status, not their history.

## Historical P96-11A Step acceptance — 2026-10-17

P96-11 remains IN_PROGRESS, current step P96-11A accepted; its exact local
delivery Step commit is pending. The first three original P96-11 outcomes are
accepted: reproducible consumer artifacts, explicit ownership and focused
validation/Step evidence correspondence. All 54 criterion texts and the original
13 Phase acceptance criteria are retained; the checklist has 52 checked/2 pending
and current_entry=P96-11. P96-11B's final two outcomes remain open.

Fresh independent Sol medium ordinary Step review
`phase96-p96-11a-step-review-001@1` returned PASS with zero Current Boundary
Blockers, Hygiene or Development Candidates. Parent admission advanced typed
review ledger 40 to 41. Actual focused attempt
`phase96-p96-11a-consumer-handoff-focused-001` passed 8/8 tests in one suite,
with zero failed, aborted, canceled, ignored or pending, Test compilation and
generation covered, SBT0/wrapper0 and serial lock released. CHP1 actually passed
100 generated cases (minimum 100, zero discards, one worker), with lawful
reducing shrinkers. One unspecified deprecation warning is nonblocking.

Accepted P01..10 history and P10 commit
`53ecdfadb00003b995b81ab9548a54b0a4880cab` remain intact. Prior P10 focused
2-suite/21-test and same 50-suite/407-test evidence, including all five native
PostgreSQL 17.11 suites and durability/SQL NULL logical-omission boundaries,
was independently assessed for reuse for unchanged product scope, not rerun
for these fixtures/docs. Pure supplied-value/codec examples establish neither
native persistence, live continuation authority nor consumer app completion.
P10 repair 6/nonconverging 0 remains historical; P11A Step repair 0,
nonconverging 0, Phase repair 0 and full Phase review count 0 remain unchanged.

Original authority, Goal binding, baseline and PLAN epoch 1 remain unchanged.
After the actual ordinary local P11A delivery commit, the epoch's sole full
Phase review is frozen Sol high across the original base through all Step
commits, followed by final repository-full validation, distinct Phase release
and consistent ledger/closure records for P11B. These gates remain owed;
neither this acceptance nor its projection closes the Stage or Phase.

## Historical P96-11 consumer handoff authoring — 2026-10-17

The following is the initial authoring snapshot, superseded by current P11A
acceptance above; its pending statements describe that earlier checkpoint.

P96-11 is IN_PROGRESS. Current step: P96-11A, ordinary consumer handoff
delivery. Its [consumer index](../spec/display-consumer-handoff.md) and
[sample/provenance journal](../journal/2026/10/2026-10-17-phase-96-consumer-handoff.md)
consolidate accepted P96-01 through P96-10 contracts without changing protocol,
ABI or runtime authority. P96-11A references the first three original P96-11
outcomes: reproducible consumer artifacts, explicit consumer ownership and
focused validation/Step acceptance correspondence. It is authored, unvalidated,
unreviewed and uncommitted; no P96-11 criterion is checked yet.

P96-11B is the final closure sub-Step referencing the last two original
outcomes: actual repository-full validation and the single Phase full review,
then criterion reconciliation and consistent release/closure records. P96-11
cannot close before those final gates. All 54 criterion texts remain unchanged,
with 49 checked/5 pending; the original 13 Phase acceptance criteria remain
unchanged. Full Phase review count is 0; final full-suite validation and release
have not run. Original authority, Goal binding, baseline, PLAN epoch 1 and
review ledger revision 40 remain unchanged. The accepted P96-10 local commit
is verified as `53ecdfadb00003b995b81ab9548a54b0a4880cab`.

## Historical P96-10 Step acceptance — 2026-10-17

Connected P96-10 A+B is independently accepted on all four original criteria.
Original whole-Step review `phase96-p96-10-step-review-001@1` and fresh focused
closure `phase96-p10-step-repair-focused-result-001@1`, admitted in review
ledger revision 40, resolve both Current Boundary Blockers. Step repair 6 is
closed accepted; nonconverging count 0. The
[acceptance record](../journal/2026/10/2026-10-17-phase-96-presentation-context.md#p96-10-step-acceptance-2026-10-17)
maps the unchanged criteria to the existing spec/design and executable suites.
Current focused validation passed 21/21 tests in two suites; the unchanged
accumulator passed 407/407 in 50 suites, including five actual native
PostgreSQL 17.11 suites with fsync=on and synchronous_commit=on. Both completed
with SBT0/wrapper0, released serial locks and zero failed, aborted, canceled,
ignored or pending tests. Prior B 5-suite/32-test evidence remains distinct.

P96-10 is CLOSED; the unchanged 54-criterion checklist has 49 checked/5 pending
and current_entry=P96-11. P96-11 remains OPEN. P96-09's actual local Step commit
is `4209af1ec5098985623c5693b5618ea661f70a63`; P96-10's local Step commit is
next, not completed. Original Goal/authority, base and PLAN epoch remain
unchanged. Both P96-10 Hygiene items remain separate and nonblocking. No full
Phase review has been consumed (count 0); repository-full validation, the
single full Phase review, release, P96-11 and consumer acceptance remain owed.
This Step acceptance does not accept the whole Phase or a consumer.

## Historical P96-09 Step acceptance — 2026-10-17

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

## Goal

Add a presentation-facing runtime layer above CNCF semantic View Models: Display Projection, Display Model and a versioned Display Model Protocol suitable for generic presentation clients.

The existing CNCF View/Read Model remains the meaning-preserving semantic contract. Display Model is separate and must not replace or weaken semantic Views.

## Architecture

Semantic/Application Model
  -> CNCF View Model
       -> semantic consumers
       -> Display Projection
            -> Display Model
            -> Display Model Protocol
            -> presentation clients

Display Model instances conform to the target-neutral Abstract/Logical UI semantics established by Cozy Phase 74. CNCF owns the Scala 3 runtime ABI consumed by generated component code; Cozy Phase 76 generates that ABI usage through the ordinary CML producer path.

## Development-driver Phase relationship

The UI/prototype driver is
[nict-editing-studio-app Phase 4](../../../../Project2026/nict-editing-studio-app/docs/phase/phase-4.md).
It owns UI-driven protocol/model proposals and fixture-backed client feasibility.
[App Phase 5](../../../../Project2026/nict-editing-studio-app/docs/phase/phase-5.md)
owns real-server integration and final aggregate/end-to-end acceptance. The
2026-10-02 scope decision supersedes the earlier Phase 4 integration gate.
[nict-editing-studio Phase 2](../../../../Project2026/nict-editing-studio/docs/phase/phase-2.md)
is the server-side development child: it supplies concrete application
DisplayModel content, semantic projection, admitted mutation contracts and
deterministic reference fixtures before the client is connected.

Phase 96 is a generic infrastructure dependency, not the owner of Editing
Studio domain semantics. Develop the minimum projection/protocol contract from
the concrete server fixtures; do not require the application to wait for all
Phase 96 scope before its first contract handoff. Cozy
[Phase 74, Abstract UI Runtime Contract](https://github.com/asami/cozy/blob/main/docs/phase/phase-74.md)
owns the shared target-neutral vocabulary. Flutter Core consumes the versioned
protocol/runtime fixtures, and TFAF owns visual realization.

Handoffs identify the protocol/vocabulary version, fixture provenance and
focused verification evidence. Scoped child/dependency handoffs do not require
full-test runs; app Phase 5 owns final real-server aggregate/E2E validation for this
integration line. Phase 96 retains its independent repository acceptance:
app acceptance does not imply closure of unrelated generic scope.

Use an existing admitted resource or an explicitly development-only fixture.
The real Workbench/KnowledgeHub admission gate in server Phase 1.2 is separate;
no production Knowledge Candidate/admission API is invented here to bypass it.
Preparation is recorded in the non-normative
[Phase 96 Display proposal](../notes/display-model-phase-96-proposal.md),
Editing Studio's
[ESS2-01 sample journal](../../../../Project2026/nict-editing-studio/docs/journal/2026/10/2026-10-01-display-model-phase-96-sample.md),
and the [preparation history](../journal/2026/10/2026-10-01-display-model-phase-96-preparation.md).
These records do not freeze the contract or close a Phase/stage.

The 2026-10-02 [UI-driven Presentation Context proposal](../notes/display-protocol-presentation-context-proposal.md)
adds the server-side handoff for presentation-relevant ExecutionContext
projection, generic context families and validated continuation. Core carries
context, TFAF uses supplied arm/variant identity to realize registered UI, and
the app remains unaware of experiment logic. This is proposed Phase 96 scope
for review against existing admission rules; no runtime or protocol is accepted.

## Development checklist

Historical checkpoint sequence before P96-08 acceptance; the Current P96-08
acceptance above is authoritative for the present completion state.

P96-01 source inventory is recorded in the non-normative
[contract inventory](../notes/display-model-phase-96-contract-inventory.md)
and [inventory journal](../journal/2026/10/2026-10-03-phase-96-contract-inventory.md).

The [Phase 96 checklist](phase-96-checklist.md) incorporates P96-01 through
P96-11 from the [development-plan proposal](../journal/2026/10/2026-10-03-phase-96-development-plan-proposal.md).
Internal checkpoints A through D group the Logical UI ABI/producer round trip,
protocol/read, mutation/Business Operation separation, and fixture/context/consumer closure.
The [ABI boundary proposal](../notes/logical-ui-generated-abi-display-runtime-boundary.md)
and [reconciliation record](../journal/2026/10/2026-10-03-phase-96-phase-76-plan-reconciliation.md)
record the 2026-10-03 GitHub correction. P96-01A remains historical inventory
work; its earlier documentary review does not accept the revised ABI plan.
P96-01 is CLOSED after independent documentary contract review and parent
admission, recorded in the [acceptance journal](../journal/2026/10/2026-10-03-phase-96-contract-promotion.md#p96-01-documentary-acceptance).
Its selected contracts are [runtime design](../design/display-model-runtime.md),
[generated ABI spec](../spec/logical-ui-generated-abi.md) and
[runtime spec](../spec/display-model-runtime.md). P96-02 is CLOSED on its
separately accepted pure ABI/model proof. P96-03 is CLOSED with all seven
unchanged obligations checked; P96-04 is CLOSED at local Step commit `625d533`,
P96-05 is independently accepted on all four unchanged criteria and CLOSED at
local Step commit `4898201d5679408d6e44510024a6feaea442a66c`. The current checklist entry
is P96-08, with 37 checked/17 pending outcomes across 54 unchanged criteria;
P96-06 is independently accepted and CLOSED at local Step commit
`1356e41cdee41dd40bc2212c1ef0297ab8875206`. P96-07 is independently accepted
and CLOSED on all four criteria in ledger revision 34, at local Step commit
`3436aef7ae5bb2b3a12000c064159e11791b5282`. P96-08 is in-progress:
A's inert descriptor/ordinary metadata-read boundary is independently accepted
through `phase96-p96-08a-protected-review-001@1`, retaining the original
23-suite/168-test PASS. P96-08B direct canonical Business execution and explicit
fresh Detail reload specifications are authored. B representative001 failed
Test compilation; representative002 compiled and completed with 2 passes and
8 failures; representative003 completed with 9 passes and 1 failure.
Representative004 passed 10/10 tests in two suites, resolving the POST
form-carrier cause. Accumulator001 completed 40 suites/332 tests with 331
passes and one BRP1 trim-empty Title failure before mutation. Unit 4's
lawful-title source repair passed property001 (1 suite/2 tests) and the same
accumulator002 (40 suites/332 tests). Both completed with all tests passing,
zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released serial
lock; parent typed result readers accepted the prior assertions. Independent
whole-Step review `phase96-p96-08-step-review-001@1` returned FINDINGS for
`CPB-P96-08-TITLE-CORRESPONDENCE-001`, missing exact initial/reloaded semantic
Title comparisons; no production corruption is alleged. First formal Step
repair 1's first property attempt completed 1 suite/2 tests with 1 PASS/1 FAIL
(SBT1/wrapper1, serial lock released): BRP1 failed before Business on the first
newline/NUL revision-16 raw-store/decoded-title mismatch; BRP2 passed. The same
consumed repair's decoded-View observation correction is now focused-validated.
`phase96-p96-08-step-title-property-002` passed 1 suite/2 tests and
`phase96-p96-08-step-title-accumulator-001` passed the same 40 suites/332 tests.
Both had zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released
serial lock; parent typed result readers accepted both results. Fresh independent
focused M2 closure and exact Step acceptance/commit remain pending; the earlier
B passes remain evidence of the prior assertions.
All four P96-08 criteria are unchecked; P96-09 through P96-11 remain
OPEN, without successor implementation or Phase closure. The
[P96-06 post-commit chronology](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-post-commit-status-reconciliation-2026-10-16)
reconciles the earlier acceptance-time pending status. The
[P96-05 acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance)
records current 14/14 focused evidence, the retained 266/266 baseline and
independent whole-Step/focused review closure in ledger revision 28. A/B1/B2 focused
history and the issued LUI-ABI-01 remain retained. Accepted Cozy P76-04
LUI-GEN-01 delivery at `ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`
is consumed unchanged by the authored P96-03C specification. The
[consumer journal](../journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md)
retains the first-pass unexecuted commands as history and records their
parent-admitted current results: GC1..GC6/GCP1 passed 7/7 tests in one suite,
the original accumulator passed 133/133 in 15 suites, source comparisons
exited 0 and complete classpath inspection found no cozy_2.12 runtime jar.
Both commands retain terminal SBT/wrapper exit 0 and released serial lock.
Independent protected-focused whole-Step review `phase96-p96-03-step-review-001`
revision 1 by `/root/phase96_p96_03_step_review_001` passed all 28 paths and
19 programs, with zero blockers and nonblocking
HYG-P96-03-SPEC-TRACEABILITY-001 retained. Parent admission in
`phase96-review-ledger-001` revision 19 accepts all seven criteria, including
criterion 7's LUI-ACCEPT-01 focused consumer proof, as recorded in the
[acceptance entry](../journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md#2026-10-04--p96-03-independent-step-acceptance).
Local P96-03 Step commit is `bef59e7`; the separate Cozy P76-05 reciprocal
receipt is accepted at `3546755`, and P76-06A documentary handoff at `ddeadab9`.
Phase96 remains IN_PROGRESS
with zero full Phase reviews; no full-suite run, release or Phase closure is claimed.

P96-06 complete A+B typed mutation contract, bindings, transport metadata,
source-bearing result candidates and standalone codecs are focused validated
and independently accepted in the [mutation spec](../spec/display-mutation.md),
[design](../design/display-mutation.md) and
[Step acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-step-acceptance).
Current `phase96-p96-06b-validation-subject-001@4` passed 5/5 representative tests
and 98/98 accumulator tests with SBT/wrapper exit 0 and serial lock released.
Original whole-Step review `phase96-p96-06-step-review-001@1` plus independent
`phase96-p96-06-descriptor-focused-review-001@1` PASS, admitted in ledger revision
31, support all five unchanged criteria. P96-06 is CLOSED at local Step commit
`1356e41cdee41dd40bc2212c1ef0297ab8875206`, recorded in the
[post-commit chronology](../journal/2026/10/2026-10-05-phase-96-display-mutation.md#p96-06-post-commit-status-reconciliation-2026-10-16).
Historical A subject4 8/8 plus 57/57,
B subject3 8/8 plus 97/97 and failed/unexecuted records remain distinct; A alone
was not Step acceptance. Completed repairs 6/nonconverging 0/accepted are retained.
P96-07 now has independent whole-Step and focused repair acceptance for
canonical atomic writes, strict deletion, positive create/delete lifecycle,
ordinary route exposure and the actual selected contextual source/reload pair;
pure preparation and ReloadedDisplay candidates were not substituted for
those effects. The [P96-07 acceptance entry](../journal/2026/10/2026-10-16-phase-96-display-list-lifecycle-integration.md#p96-07-step-acceptance-2026-10-16)
records all four criteria and required PG1–PG12 correspondence. Existing
read-v1 remains unchanged. The Phase has 37 checked/17 pending criteria,
current_entry=P96-08, zero full Phase reviews and pending final
validation/release/closure. P96-07's local Step commit is
`3436aef7ae5bb2b3a12000c064159e11791b5282`; P96-08..11 and separate Cozy/app
gates remain distinct remaining work.

### Historical P96-08A acceptance and P96-08B authoring

Current status: P96-08 is in-progress with A independently accepted through
`phase96-p96-08a-protected-review-001@1` and original 23-suite/168-test PASS.
The
[descriptor specification](../spec/display-business-operation-descriptor.md)
and [decided design](../design/display-business-operation-descriptor.md) freeze
the inert scoped Action-to-Operation descriptor and optional ordinary
`display.describeBusiness` metadata read. The
[provenance journal](../journal/2026/10/2026-10-16-phase-96-business-operation-descriptor.md)
records A's superseded authoring history and current acceptance/B chronology.
P96-08B authors ordinary `business.requestReview`, canonical stage-only
Entity/Aggregate/alias mutation and explicit fresh Detail reload, with distinct
development provenance `cncf.development.display-business` /
`canonical-review-<token>` / `fixture-1`; this is not an ESS2 `workflowState`
mapping. Representative001 failed Test compilation; representative002 compiled
and ran BR1–BR7/BRPG1–BRPG3 with 2 passes and 8 failures. Representative003
completed with 9 passes and 1 failure: legitimate owner POST returned 400 for
missing `resourceId` before Business execution. That positive is not a Business
success, and preceding production negatives are not yet accepted proof.
Prior repairs prepared declared schema before baseline, supplied existing
Aggregate UserPermission authorization and observed actual accepted provider
principals and native/full-save attempts. The current repair uses a standard
UrlForm body for exact supplied ordinary Business POST selectors, omitting
them from query while preserving omitted/duplicate/invalid values. GET and
non-Business query behavior, framework/test/forged facts and actual session
header remain unchanged. Representative004 subsequently passed 10/10 tests
in two suites, resolving that cause. Accumulator001 completed 40 suites/332
tests with 331 passes, one BRP1 failure and zero aborted/canceled/ignored/pending.
The generated trim-empty Title failed before mutation with `DSP96_ROLE_INVALID`,
not at permission or persistence; the unchanged Title contract requires
nonblank text. Unit 4 (`VAL-P96-08B-LAWFUL-TITLE-DOMAIN-004`) already authors a
fixed `T` prefix retaining the exact Unicode/control payload through all
shrinks, including empty payload, and the original newline/NUL revision-16
witness across all six target/representation combinations through the same
complete commit/reload oracle. All independent state/title oracles, at least
100 successes per law and zero discards remain required. The source repair
passed `phase96-p96-08b-business-review-property-001` (1 suite/2 tests) and
`phase96-p96-08b-business-review-accumulator-002` (the same 40 suites/332 tests).
Both completed with all tests passing, zero failed/aborted/canceled/ignored/pending,
SBT0/wrapper0 and released serial lock; parent typed result readers accepted
the results for the prior assertions. Independent whole-Step review
`phase96-p96-08-step-review-001@1` returned FINDINGS for
`CPB-P96-08-TITLE-CORRESPONDENCE-001`: missing comparison of initial logical,
initial Display and reloaded Display semantic Title to `sample.title`.
No production corruption is alleged. First formal Step repair 1's
`phase96-p96-08-step-title-property-001` completed 1 suite/2 tests with 1 PASS/
1 FAIL, SBT1/wrapper1 and serial lock released. BRP1 failed before Business at
the first newline/NUL revision-16 initial assertion; BRP2 passed. The parent
had prescribed raw DataStore `utf16:0054000a0000` as a logical observation.
The same consumed repair's correction is now focused-validated: read the title
from already captured `before.resident.views.selected.record`, still against
independent `sample.title`. Initial Display and the already fetched reload JSON
retain their exact comparisons without another read. Earlier B passes remain
evidence of the prior assertions. `phase96-p96-08-step-title-property-002`
passed 1 suite/2 tests and `phase96-p96-08-step-title-accumulator-001` passed the
same 40 suites/332 tests. Both had zero failed/aborted/canceled/ignored/pending,
SBT0/wrapper0 and released serial lock; parent typed result readers accepted
both results. Fresh independent focused M2 closure remains pending before exact
Step acceptance/commit. All four Step criteria remain
unchecked. A does not close P96-08 or authorize its Step commit, Phase
acceptance, production application admission or later consumer closure.

P96-04A-WIRE implements the [strict read protocol](../spec/display-model-protocol.md)
and its [design](../design/display-model-protocol.md). The
[protocol acceptance entry](../journal/2026/10/2026-10-04-phase-96-protocol-contract.md#2026-10-05--p96-04-independent-step-acceptance)
records the actual October 4 representative 8/8 and accumulator 143/143 results:
151/151 focused tests in 18 suites, both terminal SBT/wrapper exit 0 and serial
lock released. All three properties passed their >=100 successful/zero-discard
expectations, without a separately printed exact case count claim.
Independent whole-Step review `phase96-p96-04-step-review-001` revision 1 by
`/root/phase96_p96_04_step_review_001` passed all 15 paths, all five complete
Scala programs and all four criteria; parent admission in
`phase96-review-ledger-001` revision 25 accepts the four unchanged obligations.
The journal retains planned UNEXECUTED authoring history and accepts DSP-WIRE-01
only as the strict first List/Detail read snapshot codec handoff; registered
DisplayService Operations and live HTTP proof retain P96-05 ownership.
P96-04 is CLOSED with accepted local Step commit `625d533bf329e5b274b17cb27681aec3e80f9deb`.
At P96-04 acceptance the unchanged 54-criterion ledger had 24 checked and 30 pending
outcomes, with current_entry=P96-05 and P96-05 through P96-11 OPEN;
Phase 96 remains IN_PROGRESS with zero full Phase reviews. Later service,
mutation, Business Operation, context and aggregate acceptance, separate Cozy
Phase 76 final release/closure and app Phase 5 aggregate/E2E acceptance remain
pending. The seven prior Hygiene records remain OPEN.

### P96-05A read-service authoring and output contract repair

Current status: P96-05 is independently accepted on all four criteria and CLOSED
at local Step commit `4898201d5679408d6e44510024a6feaea442a66c`. The
[dated acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance)
records whole-Step review `phase96-p96-05-step-review-001` revision 1, the sole
`CPB-P96-05-NAMING-001` repair and fresh independent focused PASS
`phase96-p96-05-naming-focused-review-001` revision 1 in ledger revision 28.
Current subject13 passed 14/14 tests in two suites with normal generation and
production/Test compilation; subject12's 266/266 tests in 32 suites remain the
retained baseline, not a rerun on renamed source. All three successful commands
have SBT/wrapper exit 0 and serial lock released. This accepts actual in-process
Http4s route proof, not socket/live-server E2E. All ten Hygiene records stay OPEN;
no Development Candidate is admitted. Completed repairs 2/nonconverging 0,
narrowing and Phase repairs 0 are retained. At P96-05 acceptance the ledger had 28 checked/26 pending
outcomes across 54 unchanged criteria, current_entry=P96-06. P96-06 was IN_PROGRESS;
P96-07..11 remained OPEN. Phase 96 remains in-progress with zero full Phase reviews, and later
Phase/server, separate Cozy final and app Phase 5 aggregate/E2E gates remain pending.

The following authoring/failure and pending-status prose is retained historical
checkpoint evidence, superseded for current status by that acceptance entry.

The frozen initial Slice adds passive explicit resourceType/screen mappings,
normal query/read Operations, late prepared effective protocol/services without
Core replacement, and the selected typed JSON/envelope adapter. Its authoritative
contracts are [runtime SR1..SR4](../spec/display-model-runtime.md#p96-05a-ordinary-component-scoped-read-service)
and [protocol R9](../spec/display-model-protocol.md#r9-pure-typed-json-and-framework-envelope-adapter),
with corresponding runtime/protocol design. The
[dated read-service journal](../journal/2026/10/2026-10-05-phase-96-display-read-service.md)
records source revisions, expected request/response/state traces, platform
permission history and the parent-selected focused command inputs.

The original 23-path initial manifest and revisions 001..004 remain retained
authoring history. Current `phase96-p96-05a-implementation-manifest-010` revision 1
contains 27 consumed workflow inputs, 26 product-stage paths, 19 Scala target
programs and 23 authored behavior groups, including the HTTP package bridge,
bounded HTTP suffix repair and formatter precedence Envelope E4. The task-local consumed validation input,
`.cozy/launcher.yaml`, stays outside staging and commits. Its supported
`runtime.dev-dir` selects the admitted Cozy Phase76 worktree.
The first
representative completed production Compile batches but failed Test compilation
with six errors; behavioral examples did not execute. The compile corrections are
authored and passed parent static preflight. Retry002 failed normal Information CML
generation because the configured original Cozy runtime classpath was stale;
Test compilation and behavioral examples did not run. The dedicated Cozy runtime
prerequisite completed with SBT/wrapper exits 0 and the serial lock released;
the parent verified its nonempty classpath, existing entries and version
`0.3.3-SNAPSHOT`. Representative003 completed normal Information CML generation
and production Compile but failed Test compilation with three missing `create_Core`
hook errors; zero behavioral examples executed. The exact test-only unused hook
correction under `phase96-p96-05a-factory-core-fix-manifest-001` revision 1 is
authored. Representative004 completed normal Information CML generation and
production and Test compilation, then reported five visible E1..E5 bootstrap
failures requiring the query output contract. E6's header is visible; E7/E8 and
final test counts are not confirmed. SBT and wrapper exited 255 and the serial
lock was released; there is no final test summary or passing behavioral evidence,
and no separate termination cause is claimed. The canonical specified nonvoid
OperationResponse result correction under
`phase96-p96-05a-output-contract-fix-manifest-001` revision 1 is authored,
including assertions inside existing successful-bootstrap E1 and public-read/
resolver E8. Representative005 failed Test compilation with two
`NonEmptyVector.foreach` diagnostics; SBT/wrapper exited 1 and the serial lock
was released. The parent authored the exact `.toVector` iteration corrections.
Representative006 completed normal Information generation and production/Test
compilation, then ran 8 tests in one completed suite with zero aborted/canceled/
ignored/pending: E1/E4/E5/E6/E7/E8 passed, while E2 failed with `None.get` at
ReadSpec line 62 and E3 observed HTTP404 instead of200 at line 108. SBT/wrapper
exited 1 and the serial lock was released. These six successes are partial
behavior evidence, not Step acceptance.

The frozen `P96-05A-HTTP-SUFFIX-001` correction preserves slash delimiters in
`PathPreNormalizer.rewriteSegments` before the existing suffix extraction;
unchanged CLI selector and alias policy retain their ownership. The correction
and three new Given/When/Then regression groups are authored. Representative007
with subject revision7 completed normal Information generation and production/Test
compilation, then ran 8 tests in one completed suite with zero aborted/canceled/
ignored/pending: E1/E4/E5/E6/E7/E8 passed, E2 failed with `None.get` at ReadSpec
line 62, and E3 observed HTTP400 instead of200 at line 108. SBT/wrapper exited 1
and the serial lock was released. The three suffix groups compiled but did not run.
These six successes are partial behavior evidence, not Step acceptance.

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

The original 22 behavior groups are retained; Envelope E4 makes 23.
All 23 behavior groups, including Lifecycle E1..E6, envelope E1..E4, property
E1/E2 and suffix E1..E3, executed successfully in representative012 and
accumulator002. The public properties require >=100 successes and zero discards
through ordinary public Operations/HTTP; the suffix property requires the same
counts for actual HTTP pre-normalization and CLI preservation. No separately
printed exact property case count is claimed.
Delivered Cozy sources
are unchanged; independent semantic consumers remain typed. No server/socket,
source regeneration, dependency refresh or external repository mutation is claimed.

P96-05 stays OPEN/in-progress with all four criteria unchecked until focused
execution and independent protected Step acceptance. P96-06..11 stay OPEN; all
54 criterion texts and the 24 checked/30 pending projection are retained. Phase
review count stays zero and the final full-suite/review/release/closure gates,
separate Cozy Phase76 closure and app Phase5 real-server aggregate/E2E remain pending.

## Initial scope

Implement the minimum infrastructure required for the Editing Studio List -> Detail -> standard Mutation vertical slice.

Phase 96 also defines the consumer-side Logical UI generated ABI. Cozy itself remains Scala 2.12 and is not a runtime binary dependency of Scala 3 CNCF. As with other CML model elements, Cozy emits Scala 3 source/metadata against CNCF-owned public ABI types and ComponentFactory discovery/bootstrap seams.

Phase 96 also establishes the component-scoped `DisplayService` as the UI-facing public service. Aggregate and View runtime access remain internal for this phase; ordinary Business Operations remain independently published through the existing CNCF Operation/REST mechanism.

- CNCF-owned Scala 3 Logical UI generated ABI types/metadata corresponding to the admitted Cozy Phase 74 semantic subset;
- generated metadata/provider discovery through the existing ComponentFactory pattern, with admission/validation before runtime use;
- a hand-written CNCF consumer fixture that freezes the exact Scala 3 source shape Cozy Phase 76 must generate;
- Display Model identity/version metadata;
- Display Projection SPI/API from semantic View Models;
- CNCF List and Detail Display Model instances conforming to the admitted Cozy Phase 74 semantics;
- Section/Field/displayable Value representation;
- presentation roles such as title/subtitle/status where admitted by the Abstract UI contract;
- component-scoped `DisplayService` access Operations for Display Object read/query and standard mutation;
- standard Display Mutation contracts for create/update/delete, including identity/revision and editable-field/constraint metadata;
- deterministic mapping of standard Display Mutation to Entity/Aggregate resource mutation;
- standard mutation result/projection suitable for List -> Detail -> Editor -> Save/Delete UI patterns;
- Business Operation descriptors may be presented as actions, but Business Operations are invoked directly through their existing REST Operation interface rather than tunneled through DisplayService;
- deterministic protocol codec/representation;
- diagnostics for unsupported/incompatible projection or protocol versions; and
- focused fixture/proof suitable for later textus-flutter-core consumption.

### PostgreSQL-required persistence handoff

PostgreSQL is the required primary practical Knowledge Store for this
CNCF / KnowledgeHub integration line, as recorded in the
[architecture decision](../design/postgresql-primary-knowledge-store.md) and
[current adoption history](../journal/2026/10/2026-10-14-postgresql-primary-knowledge-store.md).
The [native persistence contract PG1–PG12](../spec/postgresql-phase-96-persistence.md)
requires real positive PostgreSQL create/update/delete, revision/conflict,
root/body atomicity, rollback/ACK and authoritative readback through normal
ComponentDataStore and SystemNode-managed selection before the current P96-07
mutation/reference handoff can be accepted. Registered Entity AND Aggregate,
ordinary routes and selected contextual committed reload remain mandatory.

MySQL may coexist; SQLite and memory regression obligations remain required.
JSONB, text/recursive-query/aggregation and later pgvector/hybrid search justify
the primary-store decision, while broader Knowledge features remain later
scope. RDF retains its semantic and external-Knowledge-Graph responsibilities.
This decision changes no global datastore default or production migration.

Earlier C4C readiness snapshot (2026-10-16, before whole-Step acceptance):
C4C focused representative and expanded
accumulator validation, including native PostgreSQL evidence, and fresh
independent whole-twelve-path auxiliary assurance have completed with no current
auxiliary boundary blocker. The
[C4C management chronology](../journal/2026/10/2026-10-16-phase-96-display-list-lifecycle-integration.md#c4c-current-readiness-and-candidate-reconciliation-2026-10-16)
supersedes the earlier draft-only/pending readiness checkpoint while preserving
its history. That snapshot established focused evidence availability, not
whole-P96-07 acceptance, and retained four OPEN criteria and 33 checked/21
pending outcomes at current_entry=P96-07.

Historical P96-07 acceptance checkpoint before P96-08 closure (2026-10-16): complete protected whole-Step review
and independent focused PASS after one bounded test repair support all four
criteria and PG1–PG12. Parent admission in ledger revision 34 resolved all
three conformance blockers. Current 4-suite/21-test success supplements
earlier 39-suite/529-test and 138-suite/2127-test validation, including native
PostgreSQL and retained backend regression; five historical pending cases in
the wider accumulator remain distinct. All 54 criterion texts are preserved,
with 37 checked/17 pending outcomes and current_entry=P96-08. The local
P96-07 Step commit subsequently completed at
`3436aef7ae5bb2b3a12000c064159e11791b5282`; the preceding acceptance and
preparation history is retained.
PLAN epoch 1, zero full Phase reviews and original authority, Goal binding,
base and review/repair history remain. P96-08 is in-progress with A independently
accepted (`phase96-p96-08a-protected-review-001@1`, original 23 suites/168 tests
PASS). B representative001 failed Test compilation and representative002
compiled with 2 passes/8 failures; representative003 completed with 9 passes/1
failure. Its missing-resource owner positive was not a Business success, and
preceding production negatives were not accepted proof at that checkpoint.
Representative004 subsequently passed 10/10 tests in two suites, resolving the
form-carrier cause. Accumulator001 completed 40 suites/332 tests with 331
passes and one BRP1 trim-empty Title failure before mutation. Unit 4's
lawful-title source repair passed
`phase96-p96-08b-business-review-property-001` (1 suite/2 tests) and
`phase96-p96-08b-business-review-accumulator-002` (the same 40 suites/332 tests).
Both completed with all tests passing, zero failed/aborted/canceled/ignored/pending,
SBT0/wrapper0 and released serial lock; parent typed result readers accepted
the results for the prior assertions. Independent whole-Step review
`phase96-p96-08-step-review-001@1` returned FINDINGS for
`CPB-P96-08-TITLE-CORRESPONDENCE-001`, missing exact initial/reloaded semantic
Title comparisons; no production corruption is alleged. First formal Step
repair 1's first property attempt completed 1 suite/2 tests with 1 PASS/1 FAIL
(SBT1/wrapper1, serial lock released): BRP1 failed before Business on the first
newline/NUL revision-16 raw-store/decoded-title mismatch; BRP2 passed. The same
consumed repair's decoded-View observation correction is now focused-validated.
`phase96-p96-08-step-title-property-002` passed 1 suite/2 tests and
`phase96-p96-08-step-title-accumulator-001` passed the same 40 suites/332 tests.
Both had zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released
serial lock; parent typed result readers accepted both results. Fresh independent
focused M2 closure and exact Step acceptance/commit remain pending; the earlier
B passes remain evidence of the prior assertions.
P96-09..11, separate Cozy/server/app
acceptance and the original aggregate, full-Phase review, final validation
and release gates remain pending.

## Projection rules

Display Projection is semantic-to-presentation projection, not DTO renaming only.

It may:

- map semantic properties to presentation roles, e.g. product_name -> title;
- combine or reshape semantic properties;
- project one semantic value into multiple display fields where needed;
- convert JSON and other server-native/opaque values into displayable typed values;
- select labels, ordering and presentation metadata; and
- expose available actions through abstract Action descriptors.

The Display Model boundary must not require a presentation client to know domain/application meaning.

## Protocol boundary

The protocol carries abstract presentation semantics, values, actions, identity and compatibility metadata. It must not carry Flutter Widget trees or make CNCF depend on Flutter/TFAF.

Semantic clients may continue to consume CNCF View Models independently.

## Producer/consumer ABI boundary

Use the same mechanism as existing CML-generated StateMachine/Workflow contracts:

```text
CML / Cozy semantic model
  -> Cozy producer projection/code generation (Scala 2.12 implementation)
  -> generated Scala 3 source + bounded metadata/provider surface
  -> CNCF public Logical UI ABI
  -> ComponentFactory discovery/admission
  -> component Logical UI definitions
  -> Display Projection + semantic View
  -> Display Model instance
```

CNCF must not import `cozy_2.12`, reflect Cozy runtime classes, copy Cozy runtime objects as a second authority, or introduce a special cross-built Logical UI library solely for this integration. Cozy Phase 74 remains semantic authority/reference; CNCF Phase 96 owns runtime consumer types and admission; Cozy Phase 76 owns producer mapping and source generation.

The consumer ABI should follow existing generated-ABI conventions: closed/versioned definition values, deterministic order, stable identity/provenance, provider-neutral metadata, ComponentFactory discovery, fail-closed admission, and an actual generated producer fixture pinned to fixed development coordinates for cross-repository acceptance.

## Cross-Phase development handoffs

These are intermediate development handoffs, not a requirement to close one
whole Phase before starting the other. Exact artifacts and evidence are
tracked by the [checklist](phase-96-checklist.md) and the
[ABI proposal](../notes/logical-ui-generated-abi-display-runtime-boundary.md).

| Handoff | Producer -> consumer | Required result |
| --- | --- | --- |
| LUI-ABI-01 | CNCF P96-01..03 -> Cozy Phase 76 | Versioned ABI, provider/admission contract, hand-written expected-generated Scala 3 fixture and focused List/Detail projection proof |
| LUI-GEN-01 | Cozy Phase 76 -> CNCF P96-03 | Actual generated definitions/provider, fixed producer/consumer revisions and deterministic generation evidence |
| LUI-ACCEPT-01 | CNCF P96-03 -> both Phases | Scala 3 compilation, ComponentFactory admission and equivalent List/Detail projection for that generated fixture |

After LUI-ACCEPT-01, CNCF continues P96-04 protocol and the remaining
read/mutation/context work. Cozy can close its producer scope once its own
checklist and validation are complete; it does not wait for CNCF's later REST,
mutation or app E2E work. Each Phase retains its own closure requirements.
The same focused acceptance evidence can be referenced by both ledgers.
A development handoff identifies usable source/artifact coordinates; it does
not require a public release or full-test run at every handoff.

## Development proof

Use the concrete resource fixture supplied by Editing Studio Phase 2 to prove
the following. A Knowledge Candidate example remains explicitly development-
only until its production contract is admitted:

1. a hand-written/generated-shape Logical UI ABI fixture -> ComponentFactory admission/discovery;
2. semantic List View + admitted Logical UI definition -> List Display Model;
3. semantic Detail View + admitted Logical UI definition -> Detail Display Model;
4. semantic property -> presentation-role mapping;
5. opaque/server-native value -> displayable value conversion;
6. Detail Display Object -> standard update mutation -> Entity/Aggregate update -> refreshed Display Object;
7. create/delete metadata sufficient for standard client interaction patterns;
8. one Business Operation action descriptor whose execution target remains the ordinary REST Operation API;
9. deterministic encode/decode fixture for a future Flutter Core client; and
10. a Cozy Phase 76 generated producer fixture accepted through the same CNCF ABI with no Cozy runtime binary dependency.

The later integration acceptance is replacement of the Android mock source with a CNCF Display Model source without rewriting standard List/Detail UI.

## Acceptance criteria

Phase 96 completes when:

- semantic View Model, Logical UI definition and Display Model are distinct public concepts;
- CNCF exposes the versioned Scala 3 Logical UI generated ABI and ComponentFactory admission/discovery route;
- a real Cozy Phase 76 generated producer fixture compiles against CNCF, is admitted through ComponentFactory and yields the same List/Detail projection as the hand-written fixture, without CNCF depending on `cozy_2.12`;
- Display Projection produces Cozy Phase 74-conformant runtime models;
- the minimum List/Detail/Value/Action protocol is versioned and deterministic;
- no Flutter-specific Widget semantics enter CNCF;
- semantic View consumers remain unaffected;
- the reference fixture proves List, Detail and standard Display Mutation;
- DisplayService is component-scoped and exposes only the Display access/mutation boundary;
- Aggregate/View runtime access remains internal in this phase;
- Business Operations remain direct CNCF Operations and are not wrapped by DisplayService;
- the initial post-Business-Operation consistency rule is client-initiated Display reload; and
- the protocol is sufficiently frozen for textus-flutter-core client/runtime implementation.

## Initial synchronization rule

Standard Display Mutation may return/reproject the resulting Display Object because the mutation is part of the Display contract.

Business Operations are different. A presentation client invokes the existing Business Operation REST endpoint directly. After success, the initial protocol requires the client to reload the affected Display Object/View through DisplayService. Push invalidation, subscriptions and Service Bus-driven refresh are future extensions, not Phase 96 requirements.

## Standard UI implication

Display Mutation metadata is intentionally sufficient for a generic presentation runtime to realize standard interaction patterns from configuration: List -> Create Editor -> Detail, Detail -> Edit -> Save -> Detail, and Detail -> Delete confirmation -> List. CNCF defines target-neutral semantics and validation; TFAF owns Flutter visual realization.

## Non-goals

- Flutter client implementation.
- TFAF visual design.
- Complete abstract UI vocabulary.
- Replacing CNCF View/Read Model.
- Application-specific Editing Studio UI implementation.
- Encoding a target Widget tree.

## References

- docs/journal/2026/09/2026-09-29-display-model-protocol-direction.md
- docs/journal/2026/09/2026-09-27-operation-client-ui-continuity.md
- asami/cozy Phase 74 semantic authority
- asami/cozy Phase 76 Logical UI producer ABI/code generation

## Online Experiment presentation handoff

Phase 96 does not own Experiment assignment, but the Display Model Protocol must be able to carry a server-resolved presentation variant for CNCF Phase 98 online experiments.

CNCF assigns the subject to an Experiment Arm. DisplayService/Display Projection exposes only the client-safe Experiment presentation context required to render the assigned UI variant, together with a Display Instance/correlation reference. The presentation client must not perform random assignment or choose another Arm.

The client may use the assigned Arm/variant to select between target-specific UI realizations that cannot be expressed solely by target-neutral Display Model fields. Detailed Experiment/Run/assignment state remains server-side where possible.

Standard Display Mutation must preserve the Display Instance/correlation reference. CNCF restores the authoritative Experiment/Run/Arm assignment from server-side context and records mutation lifecycle/outcome against that Arm. The client-supplied Arm value is not the authority for later mutation or Business Operation evaluation.

This permits one Experiment assignment to correlate:
Display variant -> user interaction -> Display Mutation -> Entity/Aggregate mutation -> Business Operation -> optional AI Interaction -> downstream business outcome.

TFAF and other clients render the assigned variant; CNCF owns assignment, correlation, observation and subsequent routing.

### Multi-arm presentation semantics

Display Model does not assume binary A/B experiments. The client-safe presentation variant identifies the server-assigned Arm from an N-arm Experiment. A/B UI testing is simply the two-arm case. Allocation policy, including future adaptive allocation, remains server-side; the presentation client only renders the assigned variant.
