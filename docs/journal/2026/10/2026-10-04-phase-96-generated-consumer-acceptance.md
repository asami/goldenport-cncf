# 2026-10-04 — Phase 96 actual generated consumer preparation

Document role: non-normative chronological source, sample/provenance and proof record.
Scope: PHASE-96 / P96-03 / P96-03C; consumer partner Cozy PHASE-76 / P76-05.
Manifest: `phase96-p96-03c-implementation-manifest-001` revision 1.
Current status: independent whole-P96-03 review PASS and parent admission complete
for all 28 paths and 19 programs; all seven criteria CLOSED and LUI-ACCEPT-01
focused consumer proof accepted. Local Step commit PREPARED/PENDING, not executed;
separately owned Cozy P76-05 reciprocal receipt pending. Phase 96 remains IN_PROGRESS.

The preparation sections through the original pending-receipt boundary below
retain the 2026-10-04 first-pass, unexecuted history. The later actual-execution
entry supersedes that execution status and records the proof-candidate position
at that time. The final dated acceptance entry records current parent admission;
all preceding preparation, execution and pending-receipt statements remain history.

## Accepted producer input

LUI-ABI-01 was issued from the retained CNCF A/B1/B2 focused evidence in the
[ABI handoff journal](2026-10-04-phase-96-logical-ui-abi-handoff.md).
Parent `phase96-cozy76-p76-04-acceptance-handoff-001` r1 admits actual
LUI-GEN-01 delivery at producer commit
`ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3` on
`codex/phase-76-logical-ui-producer`. Generation source HEAD was
`8a4c1794b7ad4557af5b4260d1233acd07520c6e`, with two ordinary CLI runs,
two-run/golden/delivery byte correspondence and 254/254 tests across six
completed suites. Producer SBT/wrapper exits 0 and shared lock release were
parent-admitted. Exact commands, logs, the accepted coordinate M0 correction
and the unresolved second-run Logback observation remain in the
[producer journal](../../../../../cozy-phase76/docs/journal/2026/10/2026-10-04-phase-76-generated-handoff.md).
No Phase76 ESS2-01 Logical UI producer is rerun by this slice and no new
logger cause is asserted. Ordinary CNCF build-hook execution is recorded in
the later actual-execution entry.

Correct producer coordinate is `org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`,
Scala 2.12.18. Consumer coordinate remains
`org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`, Scala 3.3.8, ABI
`cncf.logical-ui-generated.v1` version 1. Consumer branch is
`codex/phase-96-display-service`, HEAD
`fed26152045b1326e87de8a6929dc84e2fc05b18`; original Phase base is
`6cbe877c53c116639feb11fa80546e79c051804c`. The explicitly uncommitted
28-path A/B1/B2/B3/C accumulator is required source context, not represented
by HEAD alone. The preceding 21 paths and 15 Scala programs are preserved.
Cozy, the library and original main worktrees remain read-only.

## Actual source mapping

The two committed producer delivery resources are actual first-run output,
2771 and 329 bytes respectively, UTF-8/LF with final newline according to
accepted producer evidence. Their selected text is copied unmodified into
ordinary CNCF Test sources using apply_patch; no manual header, surrogate,
generator adapter or build change is added. Parent consumer-byte inspection
and compilation remain pending.

| Producer resource under cozy-phase76 | CNCF consumer source |
| --- | --- |
| `src/test/resources/logicalui/generated/ess2-01/DomainLogicalUiDefinitions.scala.txt` | [DomainLogicalUiDefinitions.scala](../../../../src/test/scala/domain/logicalui/DomainLogicalUiDefinitions.scala) |
| `src/test/resources/logicalui/generated/ess2-01/DomainLogicalUiMetadataProvider.scala.txt` | [DomainLogicalUiMetadataProvider.scala](../../../../src/test/scala/domain/logicalui/DomainLogicalUiMetadataProvider.scala) |

Actual provenance is `cozy.logical-ui-generated.v1` / `ess2-01` /
`ess2-01-cml-1`; handwritten expected provenance is
`cncf.expected-generated-fixture` / `ess2-01` / `fixture-1`. Both definitions
retain `ess-definition-1` and `ess.reference`. Semantic output comparison
never normalizes or overwrites provenance and never asserts full Definition
equality. Resource EntityId/EntityRevision remains distinct from all these
static revisions. ESS2-01 remains development-only; no production Entity,
positive create/delete, mutation or Business Operation invocation is admitted.

## Authored contextual acceptance scenarios

The external [consumer fixture](../../../../src/test/scala/logicalui/GeneratedDisplayConsumerFixture.scala)
uses the actual Domain provider and a handwritten expected counterpart, counts
metadata/runtime getter reads and retains installed typed source handles.
It reuses TypedDisplayRuntimeFixture.install/bindings and the existing Capture
rows. Negative vectors are derived from actual definitions only in fixture
code. The [consumer specification](../../../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedLogicalUiAcceptanceSpec.scala)
uses normal ComponentFactory.bootstrapC for every runtime publication; each
subsystem is shut down in try/finally. No private selection/admission bypass,
reflection, cast, Any bridge or Cozy runtime import is introduced.

| Scenario | Authored observable contract | Execution |
| --- | --- | --- |
| GC1 | Actual complete bootstrap once, idle source, source-ordered definitions/identities, contextual List/select/Detail and exact ESS models | UNEXECUTED |
| GC2 | Independent actual/expected handles and equal models; separate ABI/descriptor/screen/revision/View/screen comparisons and distinct provenance assertions | UNEXECUTED |
| GC3 | Exact Unicode/whitespace, reverse supplied order, empty List/snapshot None, same caller inputs/context once, independent typed semantic calls counted separately | UNEXECUTED |
| GC4 | Repeat Option/registry/admitted/vector/definition identity without reread; distinct component sources/resources/revisions | UNEXECUTED |
| GC5 | Actual-derived ABI2, duplicate screen index2, second invalid revision and broken List target retain authoritative diagnostics without fallback/runtime evaluation/publication | UNEXECUTED |
| GC6 | Valid actual metadata plus wrong last Detail View key preserves B1 diagnostic, no Display prefix/publication/evaluation, earlier semantic handle may remain | UNEXECUTED |
| GCP1 | At least100 successful undiscarded actual/expected normal-bootstrap cases with distinct resources, positive revisions, Unicode/whitespace, Draft/Ready/Held, Hidden/Disabled/Enabled and finite permutations including empty | UNEXECUTED |

Every scenario has adjacent Given setup, When bootstrap/query/read/semantic
action and Then should expectations. The property adds those clauses within
each generated case as well as around Test.check and its success/discard
expectations. New handwritten fixture/spec headers use Oct. 4, 2026; actual
generated files retain producer text and no added header. Naming follows
UpperCamel types, camelCase public APIs/parameters, _snake_case private
members and flatcase locals/private parameters. No existing Scala file is
edited for unrelated debt or production behavior.

## Exact planned representative — unexecuted

Parent repository: `/Users/asami/src/dev2025/cloud-native-component-framework-phase96`.
Logical SBT argv is `--batch` followed by these commands in order:

```text
show Test / fullClasspath
testOnly org.goldenport.cncf.component.ComponentFactoryGeneratedLogicalUiAcceptanceSpec
```

The parent routes this through the registered serial command runner with
scoped native permissions. Successful testOnly must supply ordinary Compile/
Test compilation and seven authored scenarios including the property threshold.
Parent inspects the actual classpath for absence of a cozy_2.12 dependency;
no source-text or classpath-name executable assertion substitutes for that
inspection. No separate compile command is selected. Actual outcomes, logs,
test/property metrics, terminal exits and lock-release evidence are pending.

## Exact planned accumulator — unexecuted

A separate sequential request has logical argv `--batch` followed by exactly:

```text
testOnly org.goldenport.cncf.display.DisplayRuntimeSpec org.goldenport.cncf.component.ComponentFactoryDisplayRuntimeSpec org.goldenport.cncf.entity.view.ViewSpaceSpec org.goldenport.cncf.component.ComponentFactoryLogicalUiBootstrapSpec org.goldenport.cncf.display.DisplayProjectionSpec org.goldenport.cncf.display.DisplayProjectionPropertySpec org.goldenport.cncf.display.GeneratedLogicalUiAbiSpec org.goldenport.cncf.display.GeneratedLogicalUiAbiPropertySpec org.goldenport.cncf.display.DisplayModelSpec org.goldenport.cncf.display.DisplayModelPropertySpec org.goldenport.cncf.workflow.GeneratedWorkflowAbiSpec org.goldenport.cncf.component.ComponentFactoryAggregateViewBootstrapSpec org.goldenport.cncf.component.ComponentFactoryStateMachineProviderBootstrapSpec org.goldenport.cncf.component.ComponentFactoryContinuationRuntimeBootstrapSpec org.goldenport.cncf.component.ComponentFactoryRuntimePlanActivationSpec
```

This is the original fifteen-suite focused accumulator with actual generated
sources now in Test configuration. Original A/B laws, property thresholds,
133/133 historical B2 success and all earlier attempts/repairs remain retained;
they do not establish these new consumer results. Parent executes neither
request concurrently and admits only terminal results including SBT/wrapper
exit 0 and shared lock release. No repository full test, Cozy regeneration,
dependency refresh or local publication is performed here.

## Pending receipt and acceptance boundary

LUI-ABI-01 is issued; P76-04 LUI-GEN-01 is producer-accepted. LUI-ACCEPT-01
remains pending actual consumer execution, classpath/source inspection and
fresh independent protected whole-28-path P96-03 Step review and exact local
acceptance commit. A later documentary evidence pass requires actual
parent-admitted results and will retain execution-time HEAD plus the disclosed
uncommitted accumulator; it must not fabricate independent acceptance or a
final commit reference. Cozy P76-05 receipt is separately pending and read-only
in this slice.

Phase 96 remains IN_PROGRESS. P96-01/02 remain CLOSED, P96-03 IN_PROGRESS
with all seven boxes unchecked and P96-04..11 OPEN. Cozy P76-01..04 remain
CLOSED, P76-05/06 OPEN and Phase76 IN_PROGRESS. Final reciprocal/full
validation, one full Phase review per admitted epoch, release and both Phase
closures retain their original owners. Same-file new-program debt is absent
at authoring; known recorded Hygiene remains separate follow-up, and no
new Hygiene or Development Candidate is admitted by this pass.

## 2026-10-04 — Actual focused execution and source/classpath inspection

The parent admitted both terminal current-request results using the complete
current validation subject `phase96-p96-03c-validation-subject-001` revision 1.
The exact representative and original fifteen-suite accumulator argv recorded
above were unchanged. The documentary editor executes no validation; these
are supplied parent-admitted results. Primary execution record revision 9 and
review ledger revision 18 are retained without edits by this pass.

Representative request `phase96-p96-03c-representative-request-001` r1 and
result `phase96-p96-03c-representative-result-001` r1 record invocation
`phase96-p96-03c-representative-001`, native session `95353`, executor exit 0.
Compile/Test succeeded and all 7/7 tests passed in one completed suite, with
zero aborted suites, failures, canceled, ignored or pending tests. SBT exit 0,
wrapper exit 0, released serial lock and consumed typed permission were
parent-admitted. Evidence locations:

- [Representative intake](/private/tmp/skill.cncf.d/a-62616a1c-69d0-413a-b6c1-b86940a3f740/command-execution-intake.json).
- [Representative actual log](/private/tmp/skill.cncf.d/a-ada0aef2-4214-43fe-b905-daa9871e0acb/36122-20261004T092121Z.log).
- [Representative summary](/private/tmp/skill.cncf.d/a-ada0aef2-4214-43fe-b905-daa9871e0acb/36122-20261004T092121Z.summary.json).

GC1..GC6 passed the actual normal-bootstrap, model/provenance equivalence,
context/Unicode/order/empty-source, repeat/component-isolation and typed
negative-publication expectations recorded in the historical scenario table.
GCP1 passed with its executed assertions requiring at least 100 successful
generated cases and zero discards. No separately printed exact property-case
count was supplied, and none is claimed here. Actual and expected definitions
retain separately asserted provenance; full Definition equality is not the
semantic comparison.

The parent inspected the complete `show Test / fullClasspath` Attributed
output at actual stdout lines 14–136: the existing Scala 3.3.8 CNCF Test
configuration contains no cozy_2.12 runtime jar. Both selected delivered-source
comparison commands exited 0, confirming that each ordinary Test Scala source
matches its selected committed producer delivery resource in the mapping
above, with no source modification or added header. This actual inspection
and successful Compile/Test establish consumer delivery compilation; no
source-text or classpath-name executable assertion substitutes for it.

Accumulator request `phase96-p96-03c-accumulator-request-001` r1 and result
`phase96-p96-03c-accumulator-result-001` r1 record invocation
`phase96-p96-03c-accumulator-001`. The unchanged original fifteen suites
passed 133/133 tests across 15 completed suites, with zero aborted suites,
failures, canceled, ignored or pending tests. SBT exit 0, wrapper exit 0,
released serial lock and consumed typed permission were parent-admitted.
Evidence locations:

- [Accumulator intake](/private/tmp/skill.cncf.d/a-573b8b05-2ff0-4133-96a5-5faccfb8d417/command-execution-intake.json).
- [Accumulator actual log](/private/tmp/skill.cncf.d/a-602fdce4-ce14-4765-8d3c-f1c2a000b50e/38289-20261004T092543Z.log).
- [Accumulator summary](/private/tmp/skill.cncf.d/a-602fdce4-ce14-4765-8d3c-f1c2a000b50e/38289-20261004T092543Z.summary.json).

Combined current focused evidence is 140/140 tests across 16 completed suites.
This is not a repository full-suite run or independent acceptance. Ordinary
CNCF Compile/Test ran its pre-existing `src/main/cozy/information.cml`
generator hook under `target/cncf-information-cml`; that build behavior is
distinct from an explicitly rerun Cozy Phase76 ESS2-01 Logical UI producer.
No Phase76 ESS2-01 regeneration, publication or dependency refresh occurred.
Original Cozy and library heads remain clean and unchanged according to the
parent's supplied current context.

## 2026-10-04 — LUI-ACCEPT-01 proof candidate, awaiting Step admission

The candidate binds accepted LUI-GEN-01 producer delivery commit
`ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`, coordinate
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`, to consumer HEAD
`fed26152045b1326e87de8a6929dc84e2fc05b18` on
`codex/phase-96-display-service`, coordinate
`org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`, Scala 3.3.8 and fixed ABI1.
The representative and accumulator request/result pairs above identify its
focused compile/bootstrap/model/negative/property and regression proof. The
source comparisons and complete classpath inspection are actual parent
evidence, not a committed consumer acceptance reference.

HEAD alone is insufficient: the explicitly uncommitted 28-path accumulator is:

```text
docs/design/display-model-runtime.md
docs/spec/display-model-runtime.md
src/main/scala/org/goldenport/cncf/component/Component.scala
src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala
src/main/scala/org/goldenport/cncf/component/ComponentFactoryEntityPlanPart.scala
src/main/scala/org/goldenport/cncf/entity/view/ViewSpace.scala
docs/journal/2026/10/2026-10-03-phase-96-contextual-display-runtime.md
docs/journal/2026/10/2026-10-03-phase-96-logical-ui-bootstrap.md
docs/journal/2026/10/2026-10-03-phase-96-typed-display-projection.md
docs/journal/2026/10/2026-10-04-phase-96-logical-ui-abi-handoff.md
src/main/scala/org/goldenport/cncf/component/ComponentFactoryDisplayPart.scala
src/main/scala/org/goldenport/cncf/display/DisplayBindingProvider.scala
src/main/scala/org/goldenport/cncf/display/DisplayProjection.scala
src/main/scala/org/goldenport/cncf/display/DisplayRuntime.scala
src/test/scala/logicalui/TypedDisplayProjectionFixture.scala
src/test/scala/logicalui/TypedDisplayRuntimeFixture.scala
src/test/scala/org/goldenport/cncf/component/ComponentFactoryDisplayRuntimeSpec.scala
src/test/scala/org/goldenport/cncf/component/ComponentFactoryLogicalUiBootstrapSpec.scala
src/test/scala/org/goldenport/cncf/display/DisplayProjectionPropertySpec.scala
src/test/scala/org/goldenport/cncf/display/DisplayProjectionSpec.scala
src/test/scala/org/goldenport/cncf/display/DisplayRuntimeSpec.scala
src/test/scala/domain/logicalui/DomainLogicalUiDefinitions.scala
src/test/scala/domain/logicalui/DomainLogicalUiMetadataProvider.scala
src/test/scala/logicalui/GeneratedDisplayConsumerFixture.scala
src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedLogicalUiAcceptanceSpec.scala
docs/journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md
docs/phase/phase-96.md
docs/phase/phase-96-checklist.md
```

P96-03 is focused-validated and awaits fresh independent protected-focused
whole-Step review covering all 28 paths and 19 target programs. All seven
criteria remain unchecked. LUI-ACCEPT-01 is a proof candidate only; final
consumer acceptance/local commit and the separately owned Cozy P76-05 receipt
remain pending. No final consumer commit or independent acceptance reference
is fabricated. Phase96 remains IN_PROGRESS, P96-01/02 CLOSED and P96-04..11
OPEN. Neither Phase full review, release nor closure is established. Earlier
attempts, repairs, first-pass unexecuted entries and original authority/base/
Goal/epoch history remain retained.

## 2026-10-04 — P96-03 independent Step acceptance

Independent protected-focused whole-Step review
`phase96-p96-03-step-review-001` revision 1 by
`/root/phase96_p96_03_step_review_001` returned PASS for all 28 owned paths and
19 target programs. Parent admission is recorded in `phase96-review-ledger-001`
revision 19 and
[acceptance handoff](../../../../.codex-workflow/phases/PHASE-96/planning/phase96-p96-03-acceptance-handoff-001.json)
revision 1. The original independent PASS is retained with zero blockers;
nonblocking HYG-P96-03-SPEC-TRACEABILITY-001 remains separate follow-up and is
not repaired or promoted by this documentary projection.

Acceptance uses the actual current validation subject
`phase96-p96-03c-validation-subject-001` revision 1 and these unchanged results:

| Request / result (revision 1 each) | Actual accepted result | Log |
| --- | --- | --- |
| `phase96-p96-03c-representative-request-001` / `phase96-p96-03c-representative-result-001` | GC1..GC6/GCP1, 7/7 tests, 1 completed suite; Compile/Test success | [Representative log](/private/tmp/skill.cncf.d/a-ada0aef2-4214-43fe-b905-daa9871e0acb/36122-20261004T092121Z.log) |
| `phase96-p96-03c-accumulator-request-001` / `phase96-p96-03c-accumulator-result-001` | Original accumulator, 133/133 tests, 15 completed suites | [Accumulator log](/private/tmp/skill.cncf.d/a-602fdce4-ce14-4765-8d3c-f1c2a000b50e/38289-20261004T092543Z.log) |

Together these are 140/140 focused tests across 16 completed suites, with no
failed or aborted suites/tests. Both actual terminal results have SBT exit 0,
wrapper exit 0 and released serial lock. GCP1 passed its executed minimum
100-success and zero-discard expectations; no separately printed exact case
count is claimed. The earlier actual-execution entry retains exact argv,
intakes and summary locations; this documentary repair executes no validation.

The two ordinary CNCF Test sources remain unmodified accepted Cozy delivery
resources: both source comparisons exited 0, and ordinary Scala 3.3.8 Compile/
Test succeeded. Complete Test/fullClasspath inspection found no cozy_2.12
runtime jar. Producer commit remains
`ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`, coordinate
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`, Scala 2.12.18. Consumer execution
HEAD remains `fed26152045b1326e87de8a6929dc84e2fc05b18` plus the disclosed
28-path uncommitted Step accumulator. The existing Information CML build hook
ran during ordinary Compile/Test; no Phase76 ESS2-01 producer rerun, dependency
refresh or publication is inferred.

All seven unchanged [P96-03 criteria](../../../phase/phase-96-checklist.md#p96-03-componentfactory-admission-and-display-projection)
are accepted and checked: explicit component registration, pure typed projection,
structured typed conversion, deterministic List/Detail and independent semantic
consumption, normal provider discovery/admission, issued LUI-ABI-01, and actual
LUI-GEN-01/LUI-ACCEPT-01 consumer correspondence without a Cozy runtime dependency.
Criterion 7 is accepted for focused consumer proof; Cozy P76-05's reciprocal
receipt remains separately pending. LUI-ACCEPT-01 is now accepted focused
consumer evidence, while the local P96-03 Step commit is PREPARED/PENDING,
not executed. No future consumer commit hash is supplied.

P96-01/02/03 are CLOSED and current_entry is P96-04; P96-04..11 remain OPEN.
The 54-item checklist has 20 checked and 34 pending criteria, with their text
preserved. Phase96 remains IN_PROGRESS with zero full Phase reviews and no
release or closure; Cozy P76-05/06 and both Phase closures remain pending.
Original authority, base, Goal, PLAN epoch, prior attempts/repairs and later
acceptance obligations are preserved. This documentary M0 projection adds no
repair cycle, resets no non-convergence count and changes no Step ownership.
