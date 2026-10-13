# Phase 96 P96-03B2 contextual typed Display runtime

Date: 2026-10-03. Slice: PHASE-96 / P96-03 / P96-03B2.
Authority: frozen parent Implementation Manifest
`phase96-p96-03b2-implementation-manifest-001` revision 1.
Status: implementation and executable specifications authored; B2 parent
validation, independent review and handoffs PENDING. No passing B2 result,
Step acceptance, checklist closure or LUI receipt is claimed.

## Bounded implementation and rationale

[DisplayRuntime](../../../../src/main/scala/org/goldenport/cncf/display/DisplayRuntime.scala)
closes heterogeneous typed Browser/projector capabilities inside one immutable
Component registry. Candidates retain each application's `V` through its
resource/value/action registrations; compiled Detail/List Entries retain the
same Browser and matching B1 projector. Factories evaluate no source and
binding admission evaluates no converter or availability callback. Complete
screen coverage and identity membership precede publication, with definition
order determining runtime screen order.

[ViewSpace](../../../../src/main/scala/org/goldenport/cncf/entity/view/ViewSpace.scala)
adds only identity membership over existing default and named handles. Binding
keys remain application keys, not generic typed lookup names. Equal handles
do not prove ownership; deliberately shared registered handles do. Contextual
read/query retains exact caller inputs/context and invokes one source method
before B1 projection. List has no invented snapshot/count, Detail checks the
requested resource identity, and source Failure retains its Conclusion and
cause. NonFatal source exceptions use fixed redacted diagnostics while B1
projection keeps its existing independent failure boundary.

The [binding provider](../../../../src/main/scala/org/goldenport/cncf/display/DisplayBindingProvider.scala)
is separate from static metadata. Component-first selection waits until
ordinary semantic and working-set/planner/event setup completes; only the
final success tail publishes definitions, complete runtime and bootstrap
flag. Failed late binding leaves legacy setup effects intact but publishes
no Display state. Successful repeat retains original objects without getter
rereads. Single-owner bootstrap remains the assumption; no retry or lock is
introduced. Runtime infrastructure does not grant authorization or execute
business actions.

The external [typed runtime fixture](../../../../src/test/scala/logicalui/TypedDisplayRuntimeFixture.scala)
uses B1 Capture rows, explicit Browser/ViewCollection/ViewSpace registration,
observable contextual calls and an unrelated Badge type. It is development
data, not production Entity admission or actual Cozy generation. A metadata
bootstrap fixtures now supply lawful typed runtime bindings, retaining their
existing assertions, getter counts, precedence and E12/E13 property thresholds.

## Executable authoring map — not yet validated

Each behavior block uses Given adjacent to setup, When adjacent to the
specified interpretation, and Then adjacent to semantic expectations. IDs
identify authored cases, not passing results.

| ID | Suite and behavior | Given / When / Then evidence |
| --- | --- | --- |
| R1 | [DisplayRuntimeSpec](../../../../src/test/scala/org/goldenport/cncf/display/DisplayRuntimeSpec.scala): contextual Detail | Registered Capture and complete ESS / runtime read plus independent typed find / exact model, resource/revision and caller identities, once per request, zero context-free/count calls |
| R2 | Same suite: query/select/read | Ordered rows plus independent empty query / Display and semantic query, then selected read / ordered resources/fields, target, snapshot None, empty valid List, no eager Detail/count, exact selected identity |
| R3 | Same suite: heterogeneous ownership | Capture default and Badge named handles, equal and foreign handles, explicit sharing / mixed binding admission and typed reads / definition order, distinct values, identity-only rejection and lawful sharing |
| R4 | Same suite: admission matrix | Null/invalid/duplicate/missing/extra candidates and counted callbacks / complete binding and B1 branch/key/converter/action gates / deterministic code/path and unchanged B1 diagnostics, no source/callback or partial registry |
| R5 | Same suite: request matrix | Complete local runtime and valid ID/query / unknown/malformed/wrong-branch/null input requests / local structured diagnostics before any source call |
| R6 | Same suite: failure preservation | Contextual denial/failure, thrown/null source and failing projector plus fatal/control signals / one invocation per request / original Conclusions/causes, fixed redaction, B1 null diagnostics, no retry/later callback, fatal/control propagation |
| R7 | Same suite: requested identity | Faulty unrelated and lawful requested sources / mismatched and repeated Detail reads / mismatch rejection, unchanged revision, inert action availability and independent semantics |
| R8 | Same suite: public typing | External Capture Browser/row / actual negative compile probes and successful typed read / no mismatched-V/action/subclass/Registry construction/copy escape and real Display model |
| RP1 | Same suite: generated laws | True finite definition/registration/row permutations, empty rows, Unicode, revisions and separate component values / repeated queries, reads and independent semantic calls / deterministic models/order, typed identity and component isolation; passed and >=100 successes required |
| B1 | [ComponentFactoryDisplayRuntimeSpec](../../../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryDisplayRuntimeSpec.scala): actual late View ordering | ViewDefinition plus existing ViewBootstrapPerson typed EntityCollection / normal bootstrap then contextual read/query / duplicate registration proves factory-created View before publication, complete ESS runtime, no bootstrap source reads |
| B2a | Same suite: absent metadata | Throwing runtime capability at component/factory / normal legacy bootstrap / zero runtime getter reads, unchanged services and no Display runtime |
| B2b | Same suite: missing bindings | Valid metadata with absent/empty runtime provider / normal bootstrap / missing-provider or missing-screen diagnostic and no definitions/runtime/flag |
| B3a | Same suite: precedence | Separate static/runtime capabilities at both locations / normal bootstrap / component-first/factory-only choice, exactly one chosen getter and no concatenation |
| B3b | Same suite: no rescue | Empty/null/throwing component provider with valid factory / normal bootstrap / selected structured/redacted failure, one selected read, zero fallback and no publication |
| B3c | Same suite: factory-only failures | Valid static metadata with empty/null/throwing runtime factory / normal bootstrap / original selected runtime failure after one getter read and no publication |
| B4a | Same suite: invalid last binding | Installed typed handle, valid List and incompatible Detail key / full normal bootstrap / B1 diagnostic, no prefix publication or source calls, legacy registration retained |
| B4b | Same suite: earlier Workflow gate | Valid UI, invalid Workflow and counted runtime getter / normal bootstrap / unchanged earlier failure, zero later getter reads and no Display publication |
| B5 | Same suite: repeat and isolation | Independent equal-screen components plus foreign handle / first bootstrap, repeat, foreign failure and contextual reads / Option/registry/definition identity, no reread, distinct data/revisions and previous component retained |

A suite [ComponentFactoryLogicalUiBootstrapSpec](../../../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryLogicalUiBootstrapSpec.scala)
retains every E1..E13 behavior block and its existing adjacent clauses. Only
provider fixture helpers gain lawful runtime installers. E2/E3/E4/E9/E10
still use normal bootstrap; E5/E12/E13 remain pure selector cases. Invalid
metadata and earlier Workflow failures retain their existing nonpublication
expectations. E12/E13 continue to require at least 100 successful cases each.

## Parent validation and acceptance handoff — PENDING

The parent selected one fifteen-suite accumulator command covering this
runtime, new bootstrap suite, ViewSpace, A metadata, B1 projection, static ABI,
Display values and canonical Workflow/Aggregate/View/StateMachine/Continuation/
runtime-plan bootstrap consumers. The implementation worker executes no SBT,
tests, scripts, runtime, diff verification or self-review. Parent mechanical
preflight, compilation, all discovered fifteen suites, every test/property,
terminal SBT/wrapper success and lock-release receipt remain PENDING.

B1's historical `phase96-p96-03b1-test-002` result was seven suites and 67
successful tests, including E1..E11 and P1/P2 with >=100 successes each,
SBT/wrapper exits 0 and lock release, after the finite-permutation and compile
type-qualification authoring repairs. It is not independent B1 acceptance and
does not substitute for B2's changed bootstrap validation.

C actual producer acceptance and LUI-ABI-01/LUI-GEN-01/LUI-ACCEPT-01 remain
PENDING. The next parent PLAN prepares LUI-ABI-01 from completed A/B evidence
before actual C work. All seven P96-03 checklist obligations and later Phase
service/wire/mutation/context work remain required. HYG-P96-DOC-001 and
HYG-P96-02-001 dispositions remain unchanged. No new ledger projection,
repair-budget reset, source-root expansion or independent acceptance is made.

## Separate incidental maintenance debt

Directly required API reading encountered pre-existing multiple April 2026
`@version` lines in preserve-only
`src/main/scala/org/goldenport/cncf/entity/view/ViewDefinition.scala`.
No edit is made there: that header maintenance is outside this manifest's
owned paths and awaits separate parent disposition. No new finding ID or
Hygiene/Development Candidate ledger projection is issued by this worker.

## 2026-10-04 — Additional repair and focused validation

The earlier authored/pending sections above preserve their historical state.
After the original prevalidation repair batch `001` exhausted `3/3`, the user
directed `追加修正して` (additional repair). A separately approved batch `002`
admitted one repair and its revalidation; that batch consumed `1/1` and passed.
The exhausted batch `001` and its failed attempts remain preserved, with no
reset or independent acceptance implied.

The additional repair changed only the test fixture entropy literal
`late-view-person` to `late_view_person` in
[ComponentFactoryDisplayRuntimeSpec](../../../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryDisplayRuntimeSpec.scala).
The preceding attempt `004` had compiled and run 133 tests, with 132 passing
and one failing at B1's EntityIdFixtureBridge before bootstrap because the
fixture bridge parts were invalid. This correction changed no assertions,
product behavior or product code; the original fifteen selected suites were
retained.

Command request `phase96-p96-03b2-command-request-005` revision `1` and result
`phase96-p96-03b2-command-result-005` revision `1` bind validation subject
`phase96-p96-03b2-validation-subject-001` revision `5`. The actual result was
133/133 passing tests, 15 completed suites and 0 aborted, SBT/wrapper exits
`0` and serial lock released. Total time was `71 s`, finishing
`2026-10-04 01:39:31 JST`. Compilation was the focused command's prerequisite.
The exact fifteen-suite `testOnly` command, working-tree source dependency
and evidence locations are recorded in the
[LUI-ABI-01 handoff](2026-10-04-phase-96-logical-ui-abi-handoff.md#executed-focused-evidence).

Result: `/private/tmp/skill.cncf.d/a-9106b454-1048-4145-8839-d7cfaab37714/command-result.json`.
Log: `/private/tmp/skill.cncf.d/a-1827cead-29b7-4d38-8efa-91761478bb5f/1234-20261003T163817Z.log`.
Summary: `/private/tmp/skill.cncf.d/a-1827cead-29b7-4d38-8efa-91761478bb5f/1234-20261003T163817Z.summary.json`.

A/B1/B2 are now authored and focused-validated, not independently accepted.
LUI-ABI-01 is prepared as CNCF's local working-tree input for the Cozy owner;
Cozy consumption, actual LUI-GEN-01 generation and P96-03C/LUI-ACCEPT-01
remain pending. P96-03 stays open. No repository full test, independent Step
review, Step commit or Phase closure is established by this result.
