# 2026-10-05 — P96-06A typed Display Mutation authoring

Document role: history and fixture/validation matrix, not acceptance authority.
Identity: PHASE-96 / P96-06 / P96-06A-TYPED.
Selected manifest: phase96-p96-06a-implementation-manifest-002@1.
Original base: 6cbe877c53c116639feb11fa80546e79c051804c.
Branch: codex/phase-96-display-service. P96-05 local Step commit is
4898201d5679408d6e44510024a6feaea442a66c. PLAN epoch 1 and ledger 29 retained.

Current status (2026-10-16): complete P96-06 A+B is independently accepted on all
five unchanged criteria through original whole-Step review and descriptor-focused
PASS admitted in ledger revision 31. Current subject4 passed 5/5 representative
tests and 98/98 accumulator tests. The [Step acceptance record](#p96-06-step-acceptance)
supersedes the pending-status chronology below. The ledger has 33 checked and
21 pending outcomes across 54 unchanged criteria, current_entry=P96-07;
P96-06 is CLOSED at actual local Step commit
`1356e41cdee41dd40bc2212c1ef0297ab8875206`, recorded in the
[post-commit chronology](#p96-06-post-commit-status-reconciliation-2026-10-16).
P96-07..11 remain OPEN; Phase 96 remains in-progress with zero full Phase reviews.

## Source-witness decision history

The once-started predecessor worker rejected manifest001 before any edit as
P96-06A-PLAN-SOURCE-WITNESS-001. Display had no component/resourceType witness;
Source was a private final non-case class with reference-based inherited equality.
No predecessor edit, validation, review or repair was consumed. Parent decision
phase96-p96-06a-source-witness-decision-001@1 settled the boundary inside the
same fifteen owned paths: ReloadedDisplay(source, display) is a candidate and
private Result factories compare logical Source values before definition/identity/
revision. Existing Display/read-v1 remain unchanged. No new query/commit authority
is inferred from a source label. P96-07 must carry the actual canonical contextual
source/reload pair; B must validate nested read-v1 before constructing that pair.

## Current authored contract and fixture examples

The [spec](../../../spec/display-mutation.md) and
[design](../../../design/display-mutation.md) define pure typed preparation,
explicit targets, inert metadata, strict observations, supplied canonical receipts
and committed pending projection. At the initial A authoring checkpoint there
were four production files, a typed external fixture and four behavior/property
suites. Commands and review were UNEXECUTED/PENDING; that authoring checkpoint
made no test-success claim.

ESS Patch("  日 Ω é  ", "") preserves supplied UTF16 units and empty description.
Title maps explicitly to Entity(Capture,title), description to
Aggregate(Capture,description); stage remains unbound readonly. ESS capabilities
are read/query/update only. The separate positive-contract-1 selection explicitly
supports pure create/delete preparation and retains the same typed Patch; it
is not canonical persistence/lifecycle or Cozy mutation generation evidence.
All-five-kind NativePatch candidates are fixture-local and Content-role only.
Counters observe callback order, never persistence, transactions or application proof.

## Unexecuted scenario matrix

| ID | Authored scenario | Owner / status |
| --- | --- | --- |
| M1 / E1 | Complete ESS admission; declared descriptor order/provenance/readonly stage; malformed registrations | ContractSpec / UNEXECUTED |
| M2 / E2 | Exact native text, typed Patch and declaration-order targets with strict policy | ContractSpec / UNEXECUTED |
| M3 / E3 | Empty/duplicate/unknown/readonly/wrong-kind/null-native/managed/aliased-target rejection | ContractSpec / UNEXECUTED |
| M4 / E4 | Original forbidden Conclusion; support/disabled-hidden/current-readonly/atomic capability vetoes | ContractSpec / UNEXECUTED |
| M5 / E5 | Domain blank-title veto; conversion/domain Failure/NonFatal/null short-circuit/redaction | ContractSpec / UNEXECUTED |
| M6 / E6 | Equal logical Source succeeds; changed component/type/definition/provenance/current ID or stale revision fails | ContractSpec / UNEXECUTED |
| M7 / E7 | Separate positive typed create defaults/domain veto and strict delete with no conversion/domain callbacks | ContractSpec / UNEXECUTED |
| TYPE / E8 | External native compile-time private/typed/closed/source-bearing probes | ContractSpec / UNEXECUTED |
| T1 / E1 | Canonical real-header tuple metadata and strict update/delete policy; empty Create metadata | TransportSpec / UNEXECUTED |
| T2 / E2 | Missing/duplicate/weak/list/noncanonical/nonpositive/overflow/null/malformed/Create-header rejection | TransportSpec / UNEXECUTED |
| T3 / E3 | Operation substitution rejected and admitted Metadata excludes managed/weakened policy injection | TransportSpec / UNEXECUTED |
| R1 / E1 | Canonical Created/Updated/Unchanged/Deleted supplied receipts and invalid kind/ID/revision/selection/nulls | ResultSpec / UNEXECUTED |
| R2 / E2 | Direct Ready exact logical source/definition/ID/revision, source/null failure paths and deleted no-Display | ResultSpec / UNEXECUTED |
| R3 / E3 | One-shot reload; lag versus foreign/failed/unusable unavailable; deleted zero reload; no replay | ResultSpec / UNEXECUTED |
| R4 / E4 | Precommit failure and canonical indeterminate cause/reason with no fabricated receipt | ResultSpec / UNEXECUTED |
| P1 / E1 | Generated opaque scopes, all five kinds, Unicode/scale/nanos and declaration/registration/request permutations | PropertySpec / UNEXECUTED |
| P2 / E2 | Generated invalid changes reject before callbacks; deterministic typed replay | PropertySpec / UNEXECUTED |
| P3 / E3 | Positive revisions including INITIAL/MAXIMUM, strict tags, stale-before-equality and no wrapping | PropertySpec / UNEXECUTED |
| P4 / E4 | Generated canonical outcomes, equal-source Ready, foreign-source unavailable and retained lag receipt | PropertySpec / UNEXECUTED |

All behavior leaves use AnyWordSpec, GivenWhenThen, should matchers and visible
afterWord spec/example/rules/phase metadata. Every generated property has Given
setup, When invocation and Then expectations within each generated case as well
as the ScalaCheck run boundary. P1..P4 select minSuccessfulTests=100,
maxDiscardRatio=0 and explicitly expect succeeded>=100/discarded=0. These are
authored requirements, not executed counts.

## Parent-selected unexecuted command matrix

Representative first:

```text
testOnly org.goldenport.cncf.display.DisplayMutationContractSpec
```

Accumulator only after representative success:

```text
testOnly org.goldenport.cncf.display.DisplayMutationTransportSpec org.goldenport.cncf.display.DisplayMutationResultSpec org.goldenport.cncf.display.DisplayMutationContractPropertySpec org.goldenport.cncf.display.DisplayModelSpec org.goldenport.cncf.display.GeneratedLogicalUiAbiSpec org.goldenport.cncf.display.DisplayProjectionSpec org.goldenport.cncf.entity.EntityRevisionPreconditionSpec
```

Parent uses the registered command runner and shared serial wrapper, scoped
execution permission, normal Information CML generation and production/Test
compilation, and terminal released-lock evidence. No editor SBT/test/script/server/
network/staging/commit/publication/deployment or self-review was executed.
Parent owns exact-path diff/whole-file compliance inspection and git diff --check.
Prior P96-05 14/14 and retained 266/266 remain read-service evidence only.

## Remaining required evidence

A focused validation is pending. B codecs and fresh independent complete A+B
Step review are mandatory before all five P96-06 criteria can be accepted.
P96-07 canonical atomic write, strict delete, actual authoritative receipt and
source-bearing contextual reload proof remain pending. All 54 criterion texts,
28 checked/26 unchecked outcomes and historical failure/acceptance records remain
unchanged; P96-06 is IN_PROGRESS only. Ten accepted Hygiene IDs stay preserved
in their canonical read-only follow-up journal; DEV is empty, with no new record
invented. No Step/Phase acceptance, local commit or completion is claimed by A.

## Representative validation compile failure and frozen repair checkpoint

The parent-run `phase96-p96-06a-representative-001` invocation completed normal
Information CML generation and production compilation, then failed Test
compilation with ten errors. No suite or test ran. SBT and its wrapper exited
with status 1, and the serial lock was released. The earlier UNEXECUTED
scenario matrix remains authoring history rather than successful execution
evidence.

Frozen repair `phase96-p96-06a-compile-fix-manifest-001@1` addresses
CF-P96-06A-IMPORT-001 by qualifying binding admissions and aliasing observation
Descriptor, CF-P96-06A-CASE-TYPE-001 by explicitly retaining concrete Create,
Update and Delete request case types, and CF-P96-06A-TAXONOMY-001 by comparing
the full canonical operation-conflict taxonomy and asserting the precise
stale-entity-revision reason. These deterministic fixture/spec corrections
preserve the typed production contract and callback expectations.

Parent representative rerun and the seven-suite accumulator remain pending.
B codecs, independent complete A+B Step review, P96-07 canonical write and
source-bearing reload/lifecycle evidence, and the full Phase gates remain
required. This checkpoint claims no validation success or acceptance.

## 2026-10-05 representative fixture initialization failure checkpoint

Parent-run `phase96-p96-06a-representative-002` completed normal Information CML
generation and compilation of 207 production Scala sources and 6 Test Scala
sources. The previous ten compile errors were resolved. Fixture source
initialization then aborted the representative suite: zero tests ran, zero
suites completed and one suite aborted. SBT and its wrapper exited with status
1, and the shared serial lock was released. The retained summary and log are
`/private/tmp/skill.cncf.d/a-22fbb50c-f807-4922-b894-3a6f8ea94d9c/88761-20261005T085828Z.summary.json`
and `/private/tmp/skill.cncf.d/a-22fbb50c-f807-4922-b894-3a6f8ea94d9c/88761-20261005T085828Z.log`.

Frozen repair `phase96-p96-06a-fixture-id-fix-manifest-001@1` addresses
CF-P96-06A-COMPONENT-ID-001. The unchanged strict Component ID admission requires
at least two namespace segments and an UpperCamel local ID. The positive
`fixture.mutation` setup and different-component `fixture.foreign` candidates
violated that grammar. Their prescribed canonical fixture identities are
`org.goldenport.fixture.Mutation` and `org.goldenport.fixture.Foreign` respectively.
The different-component candidates remain distinct and retain the existing
selection, receipt, Ready and postcommit reload rejection expectations. This
literal-only repair preserves strict parsing, Given/When/Then boundaries,
should matchers, callback counts, receipt/no-replay assertions and all four
properties' minimum 100 successes and zero-discard requirements.

Parent representative rerun and the unchanged seven-suite accumulator remain
pending. B codecs, independent complete A+B Step review, P96-07 canonical atomic
write and source-bearing contextual reload/lifecycle evidence, and full Phase
gates remain required. No suite/test success, Step/Phase acceptance, commit or
publication is claimed by this checkpoint.

## 2026-10-05 accumulator parameter and Option expectation failure checkpoint

Parent-run `phase96-p96-06a-representative-003` passed all eight representative
tests. The fixture initialization failure was resolved. The subsequent
`phase96-p96-06a-accumulator-001` completed compilation and all seven suites,
with zero suites aborted: 57 tests ran, 52 passed and five failed. SBT and its
wrapper exited with status 1, and the shared serial lock was released. The
retained summary and log are
`/private/tmp/skill.cncf.d/a-72f434a9-9899-4a74-8d8b-3a085126931a/4265-20261005T092301Z.summary.json`
and `/private/tmp/skill.cncf.d/a-72f434a9-9899-4a74-8d8b-3a085126931a/4265-20261005T092301Z.log`.

Four failures were P1..P4 ScalaCheck parameter-admission failures: the shared
`withMaxDiscardRatio(0)` override is invalid because the runner requires a
strictly positive maximum discard ratio. No generated-case success is claimed
for that run. The fifth failure was R4 comparing the actual Option-valued
`Some(Inconsistency)` cause with bare `Cause.Kind.Inconsistency`.

Frozen repair `phase96-p96-06a-parameter-option-fix-manifest-001@1` addresses
CF-P96-06A-PBT-PARAMETER-001 by removing only the invalid maximum-discard
override. P1..P4 retain `withMinSuccessfulTests(100)`, their total generators
and complete property bodies, and the exact `passed`, `succeeded >= 100` and
`discarded == 0` assertions. CF-P96-06A-CAUSE-OPTION-001 changes R4 to expect
exactly `Some(Cause.Kind.Inconsistency)`, retaining the transaction-indeterminate
reason facet and exact `Taxonomy.serviceUnavailable` expectation. Existing
AnyWordSpec, Given/When/Then boundaries and should matchers remain intact.
The earlier parameter selection and unexecuted/failed checkpoints remain
historical records rather than current execution-success evidence.

Parent rerun of the failing two suites and the unchanged seven-suite accumulator
remain pending. B codecs, independent complete A+B Step review, P96-07 canonical
atomic write and source-bearing contextual reload/lifecycle evidence, and all
full Phase gates remain required. This authoring checkpoint claims no repaired
property success, Step/Phase acceptance, commit or publication.

## 2026-10-05 A focused readiness and B standalone codec authoring

The original unexecuted, compile-failed, initialization-aborted and five-failure
accumulator records above remain chronology, not current success claims.
`phase96-p96-06a-readiness-001@1` records current subject revision4 accepted
representative004 8/8 and accumulator002 57/57 results with zero failed,
canceled, ignored or pending tests and zero aborted suites. The retained current
summaries and logs are respectively
`/private/tmp/skill.cncf.d/a-42f22512-cd66-4815-a9de-2648161ee2c3/13888-20261005T094114Z.summary.json`,
`/private/tmp/skill.cncf.d/a-42f22512-cd66-4815-a9de-2648161ee2c3/13888-20261005T094114Z.log`,
`/private/tmp/skill.cncf.d/a-d9e2508f-35b5-4236-8048-3b3169216785/15966-20261005T094531Z.summary.json`
and `/private/tmp/skill.cncf.d/a-d9e2508f-35b5-4236-8048-3b3169216785/15966-20261005T094531Z.log`.
Both focused commands completed normal compilation with SBT/wrapper exit0 and
released serial lock. P1..P4 executed their retained passed=true,
succeeded>=100/discarded=0 assertions using default.withMinSuccessfulTests(100).
The invalid maximum-discard override remains only in historical text above.
Unchanged ContractSpec subject3 representative003 8/8 stays separate original
evidence and is never relabeled as subject4. A readiness is not independent
whole-Step acceptance, criterion closure or local commit authority.

Frozen `phase96-p96-06b-implementation-manifest-001@1` owns sixteen exact paths
for the same P96-06 serialization boundary. B has authored DisplayMutationProtocol
and the public DisplayMutationCodec typed facade, plus package-private JSON,
request, descriptor and result helpers. The three standalone schema/version1
member sets remain frozen. Requests keep operation external and omit componentId,
observed revision and executable targets; scoped order and all five native
representations survive. Version, outer positive Long revision and nonnegative
Int hint lengths have canonical integer spelling. Exact decimal strings use
integer coefficient/scale construction, full scale endpoints and
java.math.BigDecimal.toString. Canonical Instant text and UTF16-unit escaping
retain nanos and isolated surrogates without coercion or charset loss.

Descriptor encoding and decoding regenerate observable metadata through the
supplied Binding. Bound readonly veto survives; redundant intrinsic unbound
readonly set membership is not a wire distinction. Declared field/kind/value
order, all inert hints and support/availability tags are checked, preserving
Binding WebColumn objects and confidentiality without exposing it. No converter,
domain or authorization callback is invoked. Result decoding validates outer
source/selection and supplied receipt through receiptC, then validates nested
Ready through the unchanged DisplayProtocolCodec before constructing the
source-bearing candidate and invoking createC. Inner and outer source mismatch
are tested separately even for identical definition/ID/revision. Ready encoding
uses the admitted source/display pair through the unchanged read encoder.
Deleted has exact reload-list; Pending keeps its receipt/reason and retry=false.
Failure/indeterminate produce no invented success and use no new envelope.

The authored B behavior matrix is:

| Example / matrix | Suite | Authored behavior / execution |
| --- | --- | --- |
| E9 RQ1 | RequestCodecSpec | Empty/nonempty Create, ordered Update, opaque Delete and external operation / UNEXECUTED |
| E9 RQ2 | RequestCodecSpec | All native values, arbitrary exact UTF16, coefficient/scale endpoints and Instant nanos / UNEXECUTED |
| E9 RQ3 | RequestCodecSpec | Every applicable object duplicate/member/primitive shape and canonical scalar/tag admission / UNEXECUTED |
| E9 RQ4 | RequestCodecSpec | Selection/definition/provenance/scoped location/null/operation rejection and Binding separation / UNEXECUTED |
| E9 TYPE | RequestCodecSpec / external logicalui fixture | Typed named-argument facade and private helper/constructor bypass probes / UNEXECUTED |
| E10 D1 | DescriptorCodecSpec | Ordered fields/kinds/values/all web hints and availability/support distinctions / UNEXECUTED |
| E10 D2 | DescriptorCodecSpec | Binding regeneration, foreign/forged/reordered metadata and strict canonical hint/member rejection / UNEXECUTED |
| E10 D3 | DescriptorCodecSpec | Bound readonly veto, intrinsic unbound readonly and idle callbacks / UNEXECUTED |
| E11 RS1 | ResultCodecSpec | Created/Updated/Unchanged/Deleted receipt correspondence, original request/observation and outer scalar failures / UNEXECUTED |
| E11 RS2 | ResultCodecSpec | Unchanged read-v1 native fidelity, independent inner/outer source validation and equal logical Source / UNEXECUTED |
| E11 RS3 | ResultCodecSpec | Both Pending reasons, Deleted/no-display, invalid Ready pairing/correspondence/shape / UNEXECUTED |
| E11 RS4 | ResultCodecSpec | Structured null/failure/indeterminate separation, no fabricated receipt or callback / UNEXECUTED |
| E12 BP1 | CodecPropertySpec | Total generated UTF16/native scalars/opaque identities and request permutations / UNEXECUTED |
| E12 BP2 | CodecPropertySpec | Total generated observable Binding descriptor/availability/readonly/kind-order cases / UNEXECUTED |
| E12 BP3 | CodecPropertySpec | Total generated outcomes/projections and safe positive revision endpoints / UNEXECUTED |
| E12 BP4 | CodecPropertySpec | Total generated member/field/scalar/definition/source tampering with independent nested Ready checks / UNEXECUTED |

Each behavior leaf and each generated case has adjacent Given/When/Then clauses
with should matchers; afterWord identifies display-mutation, E9..E12, rules and
phase96. BP1..BP4 use ScalaCheck default.withMinSuccessfulTests(100) and explicitly
require passed=true, succeeded>=100 and discarded=0. Generators are total and
bounded, with no rejection filters or maximum-discard override. The fixture
reuses A and TypedDisplayProjectionFixture read-only; traces are pure callback
observations and supplied positive receipts are not production lifecycle proof.

Parent-selected B representative remains unexecuted:

```text
testOnly org.goldenport.cncf.display.DisplayMutationRequestCodecSpec
```

Only after representative success, the frozen complete fourteen-suite
accumulator remains required:

```text
testOnly org.goldenport.cncf.display.DisplayMutationRequestCodecSpec org.goldenport.cncf.display.DisplayMutationDescriptorCodecSpec org.goldenport.cncf.display.DisplayMutationResultCodecSpec org.goldenport.cncf.display.DisplayMutationCodecPropertySpec org.goldenport.cncf.display.DisplayMutationContractSpec org.goldenport.cncf.display.DisplayMutationTransportSpec org.goldenport.cncf.display.DisplayMutationResultSpec org.goldenport.cncf.display.DisplayMutationContractPropertySpec org.goldenport.cncf.display.DisplayProtocolCodecSpec org.goldenport.cncf.display.DisplayProtocolCodecPropertySpec org.goldenport.cncf.display.DisplayModelSpec org.goldenport.cncf.display.GeneratedLogicalUiAbiSpec org.goldenport.cncf.display.DisplayProjectionSpec org.goldenport.cncf.entity.EntityRevisionPreconditionSpec
```

The registered command runner, normal compilation and shared serialization lock
remain parent-owned. Editor ran no tests, SBT, project/runtime commands, network,
self-review, staging, commit, publication or deployment. Parent owns exact-path
diff verification, whole-file/GWT compliance scan, focused validation and fresh
independent protected complete A+B Step review. All five P96-06 criteria remain
unchecked and P96-06 remains IN_PROGRESS; the existing 28 checked/26 pending of
54 criteria, original Phase authority/base/Goal binding/PLAN epoch and repair3/0
history are retained. B does not reuse the consumed A five-file approval as
general authority. No Step or Phase acceptance, commit or closure is claimed.

P96-07 still owns ordinary mutation routes/descriptor exposure without changing
read-v1, canonical atomic writes/strict delete, actual canonical receipt and
selected contextual reload provenance, and positive create/delete lifecycle.
The final Phase full review/repository validation/release and reciprocal Cozy/app
aggregate gates retain their owners. No out-of-scope code or ledger criterion was
changed by B authoring.

## P96-06B representative001 compile failure and frozen correction

`phase96-p96-06b-representative-result-001@1` for
`phase96-p96-06b-validation-subject-001@1` records the actual first representative
RequestCodecSpec attempt. Normal Information generation and 213 production
sources compiled. Test compilation then failed with three fixture errors and
229 warnings; no tests ran. SBT and wrapper both exited 1 with the serial lock
released. The retained evidence is
`/private/tmp/skill.cncf.d/a-d63803ac-a78a-4854-86ef-ac1a36effa9c/45775-20261005T104425Z.summary.json`
and `/private/tmp/skill.cncf.d/a-d63803ac-a78a-4854-86ef-ac1a36effa9c/45775-20261005T104425Z.log`.

The three exact compiler errors in DisplayMutationCodecFixture were:

- At 27:118: `Found GeneratedLogicalUiAbi.identity:String; Required Json=>Json`.
- At 32:42: `Reference Diagnostic ambiguous DisplayMutation versus GeneratedLogicalUiAbi`.
- At 34:13: `Found DisplayMutation.Diagnostic; Required GeneratedLogicalUiAbi.Diagnostic`.

Frozen `phase96-p96-06b-compile-fix-manifest-001@1` corrects
`CF-P96-06B-PARSER-IDENTITY-001` with `value => value` as the parser success
argument and `CF-P96-06B-DIAGNOSTIC-IMPORT-001` with the explicit
`DisplayMutation.Diagnostic` return type. MutationException extraction and the
fixed fixture failure branches retain their existing behavior.
`CF-P96-06B-GWT-INDENT-001` changes only leading whitespace on Given clauses
in the four codec specs to match their immediately following setup/action
statement, including the nested malformed-candidate clause. Strings, bodies,
clause positions, action/expectation adjacency, execution order, should matchers
and the four min100/passed/succeeded>=100/discarded=0 property gates are retained.

This mechanical correction adds no successful execution evidence. The parent
still owns the representative rerun, then the complete fourteen-suite
accumulator after representative success, and fresh independent whole A+B
acceptance. Editor ran no tests or self-review. All P96-06 criteria and Step
acceptance remain pending; Phase full validation remains pending at the release
gate. Earlier A/B evidence and its original subjects remain retained above.

## P96-06B accumulator002 metadata fixture admission failure and frozen correction

`phase96-p96-06b-accumulator-result-002@1` for
`phase96-p96-06b-validation-subject-001@2` completed fourteen suites with
97 tests: 92 succeeded and 5 failed. All four DescriptorCodecSpec leaves and
the BP2 generated descriptor property failed in `metadataBinding` fixture
admission before any descriptor codec action. The other three codec properties
and seven ResultCodecSpec leaves passed. The retained evidence is
`/private/tmp/skill.cncf.d/a-bc77f13c-926e-4f3d-883d-ff266de9bdd1/60633-20261005T111404Z.summary.json`
and `/private/tmp/skill.cncf.d/a-bc77f13c-926e-4f3d-883d-ff266de9bdd1/60633-20261005T111404Z.log`.

The all-kind inert metadata candidate copied the original title field's
`allowedKinds` while retaining `PresentationRole.Title`. The existing
GeneratedLogicalUiAbi admission rule requires every non-Content role to have
exactly `Vector(ValueKind.Text)`, so this candidate was invalid before codec
execution. Frozen `phase96-p96-06b-metadata-fixture-fix-manifest-002@1`
addresses `CF-P96-06B-METADATA-ROLE-001` by adding
`role = PresentationRole.Content` to that candidate's title `field.copy`.
Its logical title locator, binding registration, kind order and web metadata
are retained; the production role constraint and original A fixtures remain
unchanged. Existing assertions, generators, Given/When/Then boundaries, should
matchers and all four min100/passed/succeeded>=100/discarded=0 property gates
are retained.

This correction adds no successful rerun evidence. The parent owns the focused
DescriptorCodecSpec and CodecPropertySpec rerun, then the unchanged complete
fourteen-suite A+B accumulator after focused success, and fresh independent
protected complete A+B review. Editor ran no validation or self-review.
All P96-06 criteria and Step acceptance remain pending; full validation remains
pending at the Phase release gate. Prior evidence retains its original subjects
and outcomes.

## P96-06B subject3 focused success and descriptor encoder correspondence repair

`phase96-p96-06b-representative-result-003@1` for
`phase96-p96-06b-validation-subject-001@3` records actual focused success: all
8 DescriptorCodecSpec and CodecPropertySpec tests passed. The subsequent unchanged
fourteen-suite `phase96-p96-06b-accumulator-result-003@1` passed all 97 tests.
The four generated codec properties retained and executed their min100,
passed=true, succeeded>=100 and discarded=0 gates. These results resolve the
earlier metadata fixture admission failures and retain their original subject;
they do not validate the new repair below or establish Step acceptance.

Independent whole A+B Step review `phase96-p96-06-step-review-001@1` identified
`CPB-P96-06-DESCRIPTOR-ENCODE-001`: case-class field equality accepts numerically
equal BigDecimal hints with different scale, permitting a foreign Descriptor to
be encoded as the supplied Binding's canonical decimal string. The existing
decoder already rejects the corresponding changed observable hint string.

Frozen `phase96-p96-06-descriptor-fix-manifest-001@1` adds exactly
`|| _payload(canonical) != _payload(descriptor)` to the encoder rejection
predicate, retaining the existing logical-selection, capabilities and complete
field guards, including Binding-owned confidentiality. One new D2 behavior leaf
authors otherwise-identical admitted Bindings differing only in min 1.00/1.0,
max 9.00/9.0 or step 0.10/0.1. Each variant requires exact structured foreign
encoder rejection, numeric field equality, exact own wire round trips and all
three decimal strings, unchanged WebColumn confidentiality and zero callbacks.
Given/When/Then clauses sit at the actual setup, codec action and expectation
boundaries, including each variant; should matchers and existing leaves and
generated gates are retained.

New parent-owned focused DescriptorCodecSpec validation, the unchanged
fourteen-suite accumulator after focused success, and fresh independent focused
M2 re-review remain pending. Editor ran no validation or self-review. All five
P96-06 criteria, Step acceptance and Phase release gates remain pending. This
checkpoint claims no repaired test success, acceptance, commit or closure.

## P96-06 Step acceptance

Date: 2026-10-06. This entry records parent-admitted acceptance of the complete
original A+B Step; the source-witness decision, original authority, execution
base, Goal binding and PLAN epoch remain retained. Existing Display/read-v1
bytes, members and nested admission remain unchanged.

The current `phase96-p96-06b-validation-subject-001@4` has these successful
focused results after the descriptor correspondence repair:

| Result | Executed scope | Evidence log | Terminal result |
| --- | --- | --- | --- |
| `phase96-p96-06-descriptor-representative-result-002@1` | 5/5 tests in one DescriptorCodecSpec suite | `37204-20261005T192622Z.log` | SBT 0, wrapper 0, serial lock released |
| `phase96-p96-06-descriptor-accumulator-result-001@1` | 98/98 tests in the unchanged fourteen-suite A+B accumulator | `38964-20261005T192938Z.log` | SBT 0, wrapper 0, serial lock released |

Both results have zero failed, aborted, canceled, ignored or pending outcomes.
The min/max/step descriptor regression executes all three otherwise-identical
Binding pairs (1.00/1.0, 9.00/9.0 and 0.10/0.1), proving exact observable decimal
correspondence, structured foreign encoder rejection, own wire round trips,
retained confidentiality and zero callbacks. The current accumulator executes
all eight new property laws P1..P4 and BP1..BP4 with passed=true, succeeded>=100
and discarded=0. No separately printed exact generated-case count is claimed.

Original independent whole-Step review `phase96-p96-06-step-review-001@1`
inspected all 27 paths, 20 complete Scala programs and 40 behavior leaves.
Criterion 1 had the sole blocker `CPB-P96-06-DESCRIPTOR-ENCODE-001`; criteria
2..5 passed. Fresh independent
`phase96-p96-06-descriptor-focused-review-001@1` PASS covers the exact three
repair paths, direct dependency edges and two complete programs, resolving that
sole blocker without a new finding, Hygiene, Development Candidate or focused
evidence gap. Parent admission in `phase96-review-ledger-001@31` combines the
original whole-Step review with that focused closure to accept all five
unchanged criteria:

| Criterion | Accepted contract and executable correspondence | Execution/review basis |
| --- | --- | --- |
| 1: Typed create/update/delete contract | Mutation R1/R5; ContractSpec M1/M7/TYPE, RequestCodecSpec RQ1..RQ4/TYPE, DescriptorCodecSpec D1..D3 and ResultCodecSpec RS1..RS4 | Current 98/98 accumulator, 5/5 descriptor representative and focused closure of the sole descriptor encoder blocker |
| 2: Explicit converter and update target | Mutation R1/R2; ContractSpec M1..M5/M7 and ContractPropertySpec P1/P2; DescriptorCodecSpec D1..D3 retains inert ordered metadata | Original whole-Step criterion PASS plus current 98/98 accumulator |
| 3: Existing observation transport and definition correspondence | Mutation R3; TransportSpec T1..T3, ContractSpec M6, ContractPropertySpec P3, RequestCodecSpec RQ4 and ResultCodecSpec RS1 | Original whole-Step criterion PASS plus current 98/98 accumulator |
| 4: Structured failures and outcome receipts | Mutation R1..R5; ContractSpec M3..M7, TransportSpec T2/T3, ResultSpec R1..R4, RequestCodecSpec RQ3/RQ4 and ResultCodecSpec RS1..RS4 | Original whole-Step criterion PASS plus current 98/98 accumulator |
| 5: Precommit/committed-pending separation and no replay | Mutation R4/R5; ResultSpec R2..R4, ContractPropertySpec P4, ResultCodecSpec RS2..RS4 and CodecPropertySpec BP3/BP4 | Original whole-Step criterion PASS plus current 98/98 accumulator |

A subject4 readiness 8/8 plus 57/57 and B subject3 8/8 plus 97/97 remain separate
historical evidence with their original subjects. They are not relabeled as the
current subject4 5/98 results. Initial UNEXECUTED matrices, failed/aborted
receipts and the source-witness decision above remain chronology; A readiness
alone was not whole-Step acceptance.

Completed Step repairs 6/nonconverging 0/accepted are retained. This documentary
M0 projection neither increments nor resets that history. Phase repairs remain
0 and full Phase reviews remain 0. All ten historical Hygiene records remain
OPEN; no Development Candidate is admitted. The 54 criterion texts remain
unchanged, with 33 checked/21 pending; only P96-06's five outcomes advance.
current_entry=P96-07, P96-06 is an independently accepted CLOSED candidate and
its local Step commit remains PREPARED/PENDING; no commit hash is invented.
P96-07 through P96-11 remain OPEN and Phase 96 remains in-progress.

Pure preparation, supplied receipts and ReloadedDisplay candidates do not prove
canonical writes, strict atomic deletion, the actual selected contextual
source/reload pair or positive create/delete lifecycle. P96-07 retains those
proofs and ordinary mutation route/descriptor exposure. Final repository release
validation, the single Phase full review, release/closure and separate Cozy,
server and app aggregate/E2E acceptance gates remain pending. This entry claims
no runtime/server/app or final Phase acceptance.

## P96-06 post-commit status reconciliation, 2026-10-16

The local P96-06 Step commit completed as
`1356e41cdee41dd40bc2212c1ef0297ab8875206`, containing the 26 admitted paths,
with parent `4898201d5679408d6e44510024a6feaea442a66c`. It is already an ancestor
of the current branch; no duplicate commit or new acceptance is required. The
completed record is `phase96-p96-06-step-commit-execution-001@6` in the active
ignored workflow execution directory. The [Step acceptance entry](#p96-06-step-acceptance)
above retains its acceptance-time CLOSED-candidate/PREPARED/PENDING wording as
historical chronology, superseded for current commit status by this note.

P96-06 is independently accepted and CLOSED. Its commit does not replace the
original Phase base `6cbe877c53c116639feb11fa80546e79c051804c`. All 54 criterion
texts, 33 checked/21 pending outcomes, current_entry=P96-07, original Goal
binding/authority, PLAN epoch 1 and review/repair history remain retained.
P96-07's four criteria and P96-08..11 remain OPEN; full Phase reviews remain
zero. Final validation/review/release/closure and separate Cozy/server/app
aggregate gates remain with their existing owners. This factual reconciliation
adds no execution evidence or acceptance boundary.
