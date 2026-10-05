# 2026-10-05 — Phase 96 ordinary Display read-service authoring

Document role: chronological implementation history, source provenance, expected
read/state traces, focused execution and scoped Step acceptance. Not normative design.
Scope: PHASE-96 / P96-05 / P96-05-READ / P96-05A.
Initial manifest: `phase96-p96-05a-implementation-manifest-001`, revision 1.
Current manifest: `phase96-p96-05a-implementation-manifest-010`, revision 1.
Current status: P96-05 is independently accepted on all four unchanged criteria,
CLOSED as the acceptance candidate for the PREPARED/PENDING local Step commit.
The [Step acceptance entry](#p96-05-step-acceptance) records whole-Step review,
the sole naming blocker repair and fresh focused PASS admitted in ledger revision 28.
Current subject13 passed 14/14 tests in two suites; subject12's 266/266 tests in
32 suites remain the retained baseline, not a fresh rerun on renamed source.
The ledger has 28 checked/26 pending outcomes across 54 unchanged criteria,
current_entry=P96-06; P96-06..11 remain OPEN. Phase 96 remains in-progress with
zero full Phase reviews; final Phase validation/release/closure and separate
Cozy/app gates remain pending.

Historical execution: the first representative completed production Compile batches and
failed Test compilation with six errors; behavioral examples did not execute.
Compile corrections are authored and passed parent static preflight. Retry002
failed normal Information CML generation on the stale original Cozy runtime
classpath before Test compilation; behavioral examples did not run. The dedicated
Cozy runtime prerequisite completed with SBT/wrapper exits 0 and the serial lock
released; the parent verified its nonempty classpath, existing entries and version
`0.3.3-SNAPSHOT`. Representative003 completed normal Information CML generation
and production Compile but failed Test compilation with three missing `create_Core`
hook errors; zero behavioral examples executed. The exact test-only unused hook
correction under `phase96-p96-05a-factory-core-fix-manifest-001` revision 1 is
authored. Representative004 completed normal Information CML generation and
production and Test compilation, then reported five visible E1..E5 bootstrap
failures requiring the query output contract. E6's header is visible; E7/E8 and
final test counts are not confirmed. SBT and wrapper exited 255 and the serial
lock was released; no final test summary, passing behavioral evidence or separate
termination cause is claimed. The canonical specified nonvoid OperationResponse
result correction under `phase96-p96-05a-output-contract-fix-manifest-001`
revision 1 is authored, with assertions inside existing successful-bootstrap
E1 and actual public-read/resolver E8. Representative005 failed Test compilation
with two `NonEmptyVector.foreach` diagnostics; SBT/wrapper exited 1 and the serial
lock was released. The parent authored the exact `.toVector` iteration fixes.
Representative006 completed normal Information generation and production/Test
compilation, then ran 8 tests in one completed suite with zero aborted/canceled/
ignored/pending: E1/E4/E5/E6/E7/E8 passed, E2 failed with `None.get` at ReadSpec
line62, and E3 observed HTTP404 instead of200 at line108. SBT/wrapper exited1 and
the serial lock was released. This is partial behavior evidence, not acceptance.
The frozen `P96-05A-HTTP-SUFFIX-001` slash-delimiter correction and three new
Given/When/Then regression groups are authored. Representative007 with
subjectrevision7 completed normal Information generation and production/Test
compilation, then ran 8 tests in one completed suite with zero aborted/canceled/
ignored/pending: E1/E4/E5/E6/E7/E8 passed, E2 failed with `None.get` at ReadSpec
line62, and E3 observed HTTP400 instead of200 at line108. SBT/wrapper exited1
and the serial lock was released. The three suffix groups compiled but did not run.
This is partial behavior evidence, not acceptance. Parent source reassessment
refuted the retained framework-Argument hypothesis because `_domain_request`
removes framework/security arguments including `textus.*` before ordinary
business dispatch. The frozen `P96-05A-HTTP-FAILURE-EVIDENCE-001` correction
added actual response-body clues and HTTP preconditions inside existing ReadSpec
E2/E3. Representative008 with subjectrevision8 completed normal Information
generation and production/Test compilation, then ran 8 tests in one completed
suite: 6 passed (E1/E4/E5/E6/E7/E8), 2 failed, with zero aborted/canceled/ignored/
pending. E2 at ReadSpec64 and E3 at123 both recorded HTTP400 with
`HTTP ingress not configured` and detailCode10799020000. SBT/wrapper exited1 and
the serial lock was released; the32suite accumulator did not run.
Parent diagnosis established that open used the generic `Protocol.empty` handler;
effective Display registration correctly preserves that handler. The frozen
`P96-05A-HTTP-INGRESS-001` correction imports ProtocolHandler and passes
`Protocol(handler = ProtocolHandler.default)` only at open's existing initialize
call. This checkpoint is fixture authoring only: generic initialize default/
signature/body, other callers and production service ownership are unchanged.
Representative009 with subjectrevision9 completed normal Information generation,
production203Scala compilation and Test compilation of the corrected fixture.
Its8tests in1completed suite had6successes(E1/E4/E5/E6/E7/E8),2failures and
0aborted/canceled/ignored/pending. InitialHTTP200checks completed before E2's
selected-identity Option guard at ReadSpec72; its printed List body is bare
`cncf.display-model.v1` with2model.items and no data/execution. E3's ListHTTP200
check completed before None.get in _list_resources404 called125; no E3 body
was printed. SBT/wrapper exited1 and lockreleased; the32suite accumulator did
not run. This is partialHTTP200progress, not acceptance.
Parent PLAN rejected the unimplemented explicit-format fixture workaround under
output-format6/7 and path-resolutionR4. Frozen `P96-05A-HTTP-JSON-SUFFIX-001`
authors the existing formatter's retainedSource.Http final-path suffix selection
below explicitrequest format and above configured/mode fallback, plus real
EnvelopeE4 precedence/compatibility coverage. Fixture.jsonURI/shape-onlyproperties/
RunMode/standardhandler and original22groups remain unchanged. This is authoring only.
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
identity/effective-consumer assertions. These E2/E3/E5 edits are authored only.

All failed001..010 receipts retain their historical failure status, and the
retained runtime prerequisite is not service acceptance. Normal build-target
Information generation remains enabled. Original Goal, three bases, epoch1,
ledger26, seven Hygiene records and candidate2 unfinished/completed1/nonconverging0
are retained. All 54 criterion texts and 24 checked/30 pending outcomes remain
unchanged; P96-05..11 stay OPEN and the full Phase review count is zero.
P96-05 acceptance remains PENDING.
All failed001..010 receipts retain their failure status;
the runtime prerequisite is not acceptance.
The current boundary contains 27 consumed workflow inputs, 26 product-stage
paths, 19 Scala target programs and 23 authored behavior groups, retaining the
original 22 groups and adding EnvelopeE4. The one task-local consumed
launcher config remains outside staging and commits.
The unchanged ledger
has 24 checked/30 pending outcomes across 54 criteria; P96-05..11 remain OPEN.
Phase96 is IN_PROGRESS, with zero full Phase reviews and no release/closure claim.

## Retained revisions and ownership

The preceding pending-status and authoring checkpoints are retained history;
the leading Current status and dated acceptance entry give the current projection.

| Input | Retained revision and role |
| --- | --- |
| Original CNCF Phase96 base | `6cbe877c53c116639feb11fa80546e79c051804c`, not replaced by a later HEAD |
| Dedicated implementation checkout | `/Users/asami/src/dev2025/cloud-native-component-framework-phase96`, `codex/phase-96-display-service` |
| P96-03 accepted consumer | `bef59e729834addd3ac00c1930034bb23a8b428f`, LUI-ACCEPT-01 focused evidence |
| P96-04 accepted pure codec | `625d533bf329e5b274b17cb27681aec3e80f9deb`, 151/151 historical focused tests, not read-service proof |
| Cozy actual LUI-GEN-01 delivery | `ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`, unchanged two-file generated definitions/provider |
| Cozy P76-06A documentary handoff | `ddeadab9a97fda818fb2842df4873dbc7adf03e1`; separate Phase76 full-review/release/closure pending |
| Parser-library accepted prerequisite | `e2119e3b6e69d4564ee8a3c5a280be4753654fa0`, read-only during this Slice |
| Driver semantic reference | `KnowledgeHubProject/nict-editing-studio-app` / `48eb43b8eb839137e17ef4c944b3db4a73d0cec3` / `docs/phase/phase-2.md` / `resource-list-detail` |

CNCF remains `org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`, Scala3.3.8.
Cozy producer coordinate remains `org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`,
Scala2.12.18. No generator, dependency refresh or other repository write is part
of this authoring. Main checkouts are preserve-only.

Actual [definitions](../../../../src/test/scala/domain/logicalui/DomainLogicalUiDefinitions.scala)
and [provider](../../../../src/test/scala/domain/logicalui/DomainLogicalUiMetadataProvider.scala)
retain ABI `cncf.logical-ui-generated.v1` / 1; semantic schema
`cozy.logical-ui-runtime.v1` / 1, authority `cozy.logical-ui-semantics.v1`,
assurance `provisional-scenario` and the complete driver above. Provenance is
`cozy.logical-ui-generated.v1` / `ess2-01` / `ess2-01-cml-1`, independently of
definition revision `ess-definition-1` and source resource revisions. Both screens
use View binding key `ess.reference`; passive resourceType `collecting-reference`
is explicitly supplied for `collecting-resources` and `capture-detail` by the
[application fixture](../../../../src/test/scala/logicalui/DisplayServiceReadFixture.scala),
not generated or inferred.

Independent all-kind fixture provenance is `cncf.public-read-all-kind-fixture` /
`typed-capture` / `fixture-1`. It admits text/number/boolean/timestamp/missing for
public transport proof; it does not attribute those fields to the actual Cozy
text-only ESS delivery. Semantic Capture values still have independent consumers.

## Frozen contract and initial implementation

[Runtime SR1..SR4](../../../spec/display-model-runtime.md#p96-05a-ordinary-component-scoped-read-service)
and [design](../../../design/display-model-runtime.md#p96-05a-prepared-effective-registration)
fix complete explicit mappings, component-first selection, no invalid fallback,
ordinary authorization/QueryAction/ProcedureActionCall and late immutable effective
Protocol/ProtocolLogic/ServiceGroup publication without copying Core. Pure
`encodeJsonC` feeds ordinary OperationResponse.Json. [Protocol R9](../../../spec/display-model-protocol.md#r9-pure-typed-json-and-framework-envelope-adapter)
and [adapter design](../../../design/display-model-protocol.md#p96-05a-typed-business-json-and-framework-metadata)
keep business Json separate from framework metadata and preserve JSON-only format
selection, job/continuation/debug semantics and exact UTF8/UTF16.

The existing semantic entity-bootstrap core.protocol fallback is unchanged.
Ordinary dispatcher discovery reads effective component.protocol. No second View
store, guessed Entity collection, public generic runtime API, source erasure/cast,
action authorization inference, mutation or alternate envelope format is added.

## Expected complete normal read trace — UNEXECUTED

These are authored fixture expectations, not observed requests/responses.
Initialize the ordinary subsystem and component
`org.goldenport.cncf.test.DisplayReadConsumer`, with immutable captures 1/revision7
and 2/revision19. Before bootstrap, definitions/runtime are absent, protocol/
services are original, flag is false and no Browser has been evaluated. During
late service getter the canonical Browser is already in this component ViewSpace
but definitions/runtime/display service/flag remain unpublished. After success,
one complete definition wrapper/runtime/effective registration is visible,
query/read resolve normally, provider counts are each 1, and all source counters
are zero. Core, unrelated services/handler/engines and subsystem are retained.

Canonical resource values, derived by existing EntityId.restore in the fixture:

```text
R1 = fixture-display-entity-ec1_7_fixture_7_display_7_Capture-1800000000000-projection_resource_1
R2 = fixture-display-entity-ec1_7_fixture_7_display_7_Capture-1800000000000-projection_resource_2
```

R1/R2 are notation for those complete opaque values, not a new identity grammar.
For each HTTP request below, include `resourceType=collecting-reference`, the
listed screenId and `textus.output.shape=envelope` as query properties. URL
encoding is ordinary Uri.withQueryParams, not a manually invented route parser.

| Sequence | Complete request selection | Expected response and source/state delta |
| --- | --- | --- |
| 1 Detail first | GET `/rest/v1/org.goldenport.cncf.test.DisplayReadConsumer/display/read.json`; screenId=`capture-detail`, resourceId=R1 | HTTP200 application/json UTF8; data OBJECT has Detail resource R1/revision STRING `7`; contextual find(R1) exactly once; zero queries/count/context-free calls |
| 2 List | GET `/rest/v1/org.goldenport.cncf.test.DisplayReadConsumer/display/query.json`; screenId=`collecting-resources`, no resourceId | HTTP200 application/json UTF8; data OBJECT has ordered R1/7 then R2/19, DetailTarget capture-detail, no snapshot; one contextual Query.fromRecord(Record.empty), no eager read/count |
| 3 Select | Read second List item's resource.entityId locally | Select exact returned R2; no source call, no source identity inferred from screen/provenance |
| 4 Selected Detail | GET same read.json; screenId=`capture-detail`, resourceId=selected R2 | HTTP200 application/json UTF8; data OBJECT has R2/revision STRING `19`; exactly one additional contextual find(R2) |

Every business data root is complete v1: schema `cncf.display-model.v1`, integer
version1, componentId above, resourceType above, exact ABI/complete semantic
descriptor/provenance above, definition screenId plus `ess-definition-1`, and
closed List/Detail model. The complete field/action shapes and bytes are the
unchanged [actual List golden](../../../../src/test/resources/display/protocol/v1/ess2-01-list.json)
and [actual Detail golden](../../../../src/test/resources/display/protocol/v1/ess2-01-detail.json),
with only componentId bound to this fixture owner and selected Detail resource
bound to R1/7 or R2/19. These are expectation references, not newly generated files.
List fields are title then stage (`draft`), no item or screen actions. Detail's
main section has no title, fields title/stage/description and inert enabled
request-review action with `ess.request-review`. Original whitespace/Japanese/
Greek/combining text and description controls are unchanged. Definition,
provenance, resource and query-correlation revisions are not interchanged.

Framework response roots are data/execution and only ordinarily applicable
job/continuation/debug. Execution interface-shape is `json`, with canonical
ordinary operation/component/service metadata; no root `result` or
`textus-execution` is invented. The formatter inserts the actual business Json,
not a quoted JSON string, and escapes non-ASCII UTF16 units before UTF8 encoding.
Structured failures keep ordinary HTTP/error mapping. In-process Http4s route
execution is planned proof, not a listening socket/server claim.

Final counters for sequences1..4 are finds `[R1,R2]`, one canonical query, zero
count/context-free/retry calls, no provider rereads, and unchanged source rows.
An independent semantic Browser query subsequently returns original typed Capture
values/titles without needing Display representation (one intentionally separate
semantic query, not an extra call by the public Display operation).

## Expected boundary traces — UNEXECUTED

| Surface | Given / When / Then expected state and response |
| --- | --- |
| Registration E1/E2 | Component-selected or factory-only-if-absent passive mappings after runtime; complete success without source calls. Absent metadata/empty definitions do not read service getter or install display |
| Registration E3 | Selected missing/throwing/null/invalid/duplicate/extra/missing-screen/List-target-mismatch mapping; typed indexed DSP96_SERVICE_ diagnostic, no fallback/Display/effective protocol publication; prior Views may remain |
| Registration E3 construction | New service factory NonFatal during preparation; fixed structured componentInvalid, original protocol/services remain, no source or partial registration |
| Registration E4/E5 | Existing normalized display/duplicate operations reject without replacement. Fixed Core success preserves identity and unrelated instances while normal effective getters/resolver agree |
| Registration E6 | Rebootstrap successful owner retains all references/counts. Foreign unregistered Browser runtime join fails before service exposure; no display Entity/store is inferred |
| Read E3/property E1 | Empty/reversed/permuted finite sources and independent all-kind declarations; complete required arrays, no snapshot, exact UTF16 units/decimal coefficient and Int scale/Boolean/Instant nanos/missing, same trusted decode semantics |
| Read E4/E5/property E2 | Missing/unknown/foreign component/type/screen, wrong branch, raw null/duplicates/primitives/canonical IDs or arbitrary View/filter/control input; no unauthorized/wrong source call or partial Display. Canonical absent resource makes only owning find fail once, no fallback |
| Read E6 | Production descriptor requires authentication. Anonymous/spoofed fields fail ordinary authorization before source. Explicit admitted authenticated caller executes under ordinary action context/UoW; enabled Action supplies no authority |
| Read E7 | Structured Browser/projector Conclusion retained by identity; NonFatal source/callback redacted to existing typed fixed diagnostic; one find, no retry/query/count/partial response |
| Read E8 | Ordinary public read then trusted original-definition decode; encodeJsonC equals business payload, encodeC parses equally, original null diagnostics equal; only query/read resolve, no default/list/get |
| Envelope E1 | Object/array/primitive/null remain exact data; normal execution owns metadata. Data-only/non-json selection and Record/Scalar/Http retain baseline behavior |
| Envelope E2 | Explicit AcceptedJob data null plus applicable job metadata; JobResult exact data plus applicable job/continuation; direct cannot infer a job from data. Inline debug stays outside data, including data-shape request |
| Envelope E3 | Nested controls/Unicode/paired and lone surrogates exact through UTF8; Boolean/empty arrays/null/numeric/revision/timestamp strings remain exact under Japanese/Tokyo runtime assumptions |

The suites are [Read E1..E8](../../../../src/test/scala/org/goldenport/cncf/display/DisplayServiceReadSpec.scala),
[registration E1..E6](../../../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryDisplayServiceSpec.scala),
[envelope E1..E3](../../../../src/test/scala/org/goldenport/cncf/protocol/OperationResponseJsonEnvelopeSpec.scala)
and [public property E1/E2](../../../../src/test/scala/org/goldenport/cncf/display/DisplayServiceReadPropertySpec.scala).
All new in bodies and property bodies have adjacent Given/When/Then and should
expectations with visible afterWord E/spec/rules/phase/slice metadata. Both
properties require >=100 successful zero-discard cases; no exact executed count
or PASS is claimed. Existing bootstrap/generated-consumer changes migrate only
passive provider helpers, preserving their existing scenario meaning/history.

## Platform-permission chronology

The initial exact six-file runtime patch was denied for substantial unvalidated
shared protocol/service-registration, dispatch and formatting disruption risk.
The parent performed the stated original-authorization-check recovery; one same
patch submission was denied again because that evidence was not informed exact
patch approval. Both full raw responses remain inert at
`/private/tmp/phase96-p96-05a-denial.txt` and
`/private/tmp/phase96-p96-05a-denial-recovery-001.txt`; exact submitted patch is
`/private/tmp/phase96-p96-05a-denied-runtime.patch`. No split/equivalent patch,
route/permission/agent switch or indirect shell mutation bypass was used.

After the parent explained the exact unvalidated runtime risk, the human directly
approved the six-file patch in the dedicated Phase96 worktree, preserving focused
validation and independent review and excluding main/push/public publication.
Native role=user occurrence is session line50945, timestamp
`2026-10-04T20:10:07.284Z`. Parent records are
`phase96-p96-05a-informed-platform-approval-001` r1 and
`phase96-p96-05a-native-goal-resume-001` r1; original active Goal, authority/bases,
PLAN_COMPLETE and ledger26 remain retained. The same implementation owner read
that native occurrence and both denials, then made one ordinary tools.apply_patch
submission of the exact retained patch. It was accepted; all six target paths
became modified. That is patch application evidence only, not compilation,
behavior safety, validation, review or Step acceptance. Remaining work stayed
inside the same initial 23-path manifest and the same editing ownership.

## Initial planned focused execution — retained UNEXECUTED authoring history

This section retains the initially planned commands and static-check scope; it
is not current successful validation. The actual first representative failure
and current corrected-validation obligations are recorded below.

Only the registered cncf_command_runner may execute these inputs, sequentially
through the shared SBT serial wrapper with normal scoped escalation and terminal
SBT/wrapper exit plus released-lock evidence. The editor runs neither command.
No full-suite run belongs to this Step; final Phase gates remain separate.

Representative logical argv:

```text
--batch
testOnly org.goldenport.cncf.display.DisplayServiceReadSpec
```

Accumulator logical argv (one SBT invocation, one testOnly argument):

```text
--batch
testOnly org.goldenport.cncf.display.DisplayProtocolCodecPropertySpec org.goldenport.cncf.component.ComponentFactoryGeneratedLogicalUiAcceptanceSpec org.goldenport.cncf.display.DisplayRuntimeSpec org.goldenport.cncf.component.ComponentFactoryDisplayRuntimeSpec org.goldenport.cncf.entity.view.ViewSpaceSpec org.goldenport.cncf.component.ComponentFactoryLogicalUiBootstrapSpec org.goldenport.cncf.display.DisplayProjectionSpec org.goldenport.cncf.display.DisplayProjectionPropertySpec org.goldenport.cncf.display.GeneratedLogicalUiAbiSpec org.goldenport.cncf.display.GeneratedLogicalUiAbiPropertySpec org.goldenport.cncf.display.DisplayModelSpec org.goldenport.cncf.display.DisplayModelPropertySpec org.goldenport.cncf.workflow.GeneratedWorkflowAbiSpec org.goldenport.cncf.component.ComponentFactoryAggregateViewBootstrapSpec org.goldenport.cncf.component.ComponentFactoryStateMachineProviderBootstrapSpec org.goldenport.cncf.component.ComponentFactoryContinuationRuntimeBootstrapSpec org.goldenport.cncf.component.ComponentFactoryRuntimePlanActivationSpec org.goldenport.cncf.display.DisplayProtocolCodecSpec org.goldenport.cncf.component.ComponentFactoryDisplayServiceSpec org.goldenport.cncf.display.DisplayServiceReadPropertySpec org.goldenport.cncf.protocol.OperationResponseJsonEnvelopeSpec org.goldenport.cncf.protocol.OperationResponseFormatterSpec org.goldenport.cncf.subsystem.SubsystemExecuteHttpSpec org.goldenport.cncf.subsystem.SubsystemOperationAuthorizationSpec org.goldenport.cncf.subsystem.resolver.OperationResolverSpec org.goldenport.cncf.component.OperationRequestActionDispatcherSpec org.goldenport.cncf.http.Http4sHttpServerDispatchSpec
```

Parent-only static checks cover exact23-path scope/preserved producer files,
whitespace, all16 whole Scala programs and naming/headers, every GWT boundary,
closed failure vocabulary and unchanged54 criteria. Independent protected-focused
Step review follows actual focused results; one full Phase review remains due
after all Step commits. No execution/review result ID, exit code, lock-release
receipt or commit is manufactured here. Append actual parent results later.

## Follow-up boundaries

Existing HYG-P96-02-001, HYG-P96-03-SPEC-TRACEABILITY-001, HYG-P96-DOC-001,
HYG-P96-PARSER-SIZE-001 and the three existing Cozy P7603 hygiene records remain
separate/unmodified. Component/semantic-bootstrap historical Any/naming/size debt
outside the frozen delta is not expanded into this Slice. No new empty HYG/DEV
journal or new Development Candidate is created. P96-06 mutation, P96-08 Business
Operation descriptors, P96-10 context, P96-11 final consumer/Phase closure, separate
Cozy final gates and appPhase5 real-server aggregate/E2E remain explicitly pending.

## 2026-10-05 — First representative validation and compile repair plan

The initial authoring and its known import/GWT corrections are complete. The
registered command runner executed the original representative logical argv
`--batch` / `testOnly org.goldenport.cncf.display.DisplayServiceReadSpec`.
Attempt `phase96-p96-05a-representative-001`, result
`phase96-p96-05a-representative-result-001` revision 1, completed with SBT exit 1,
wrapper exit 1 and shared serial lock released. The production Compile batches
completed, but Test compilation reported six errors; behavioral examples did not
execute. This is not a test PASS or behavioral safety acceptance.

The errors are the logicalui fixture's access to private[http] routes, a generated
ABI identity String shadowing the identity function, two NonEmptyVector/Vector
comparisons and two Vector/None comparisons. Parent diagnosis retains each exact
cause and failure location. The existing import correction is resolved; its
descriptive GWT tail adds no separate repair cycle.

After parent capability recheck, the current manifest is
`phase96-p96-05a-implementation-manifest-002` revision 2. It adds only the
test-only `org.goldenport.cncf.http.DisplayServiceHttpRouteFixture` to the
original 23-path scope, making 24 complete Step paths and 17 Scala programs.
The frozen compile repair owns four test Scala files plus these three existing
status/history documents. The lawful package bridge returns the real server
routes HttpApp: ordinary HTTP ingress, authorization, dispatch, formatter,
status/content-type/body bytes and source effects remain the acceptance proof.
Production routes visibility, product API, schema and expected behavior do not
change; no socket listener or synthetic/direct-dispatch substitute is introduced.

The corrected-tree representative and original 27-suite accumulator are pending.
They require fresh typed command execution and terminal serial-lock evidence;
the failed attempt remains retained and cannot be reused as successful proof.
Independent protected-focused whole-Step review, Step commit and all four
P96-05 criteria remain pending. The 54-criterion ledger remains 24 checked and
30 pending, P96-05..11 OPEN and Phase96 IN_PROGRESS with zero full Phase reviews.
Separate Cozy final gates and repository-full Phase release validation remain
pending. No main reflection, push or public publication is part of this repair.

## 2026-10-05 — Generator runtime prerequisite after representative retry

The compile corrections in the four test Scala files are authored and passed
parent static preflight; they are not yet validated by Test compilation.
The registered runner executed the unchanged representative logical argv in
attempt `phase96-p96-05a-representative-002`; result
`phase96-p96-05a-representative-result-002` revision 1 is FAILURE, SBT exit 1,
wrapper exit 1 and shared serial lock released. Normal Information CML generation
stopped with exit 2 because the configured original Cozy checkout's development
runtime classpath was stale. Test compilation and behavioral examples did not run.

Current complete-Step manifest is
`phase96-p96-05a-implementation-manifest-003` revision 1. It retains the same
24 product commit paths, 17 Scala programs and 19 behavioral scenarios, and adds
one task-local consumed validation input: `.cozy/launcher.yaml`. The supported
`runtime.dev-dir` selector resolves to the already admitted Cozy Phase76 worktree
using a config-file-relative path. This local setting is not product/release
content and must remain outside staging and commits. Global configuration,
original main checkouts and launcher source are not edited.

The parent will run the declared `cozyExportRuntimeClasspath` SBT prerequisite
in the dedicated Cozy worktree through the registered serial runner, then retry
the original representative and original 27-suite accumulator. That prerequisite
only produces ordinary target classpath/version outputs; it is not publication,
ESS producer-delivery regeneration or read-service acceptance. Cozy remains
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`; no dependency coordinate changes.
All three commands and independent protected-focused whole-Step review are
PENDING. No successful compile/test or new request/response observation is claimed.

Repair candidate 2 remains unfinished; completed Step repairs 1 and consecutive
nonconvergence 0 are retained. All 54 criteria and the 24 checked/30 pending
projection remain unchanged, P96-05..11 OPEN and Phase96 IN_PROGRESS. Final Phase
review, repository-full validation, release and closure, and separate Cozy final
gates remain pending. No main reflection, push or public publication is admitted.

## 2026-10-05 — Representative003 reached Test compilation

Dedicated Cozy prerequisite `phase96-p96-05a-cozy-runtime-prerequisite-001`
completed with SBT/wrapper 0 and serial lock released. The parent inspected the
nonempty task-produced runtime classpath and version `0.3.3-SNAPSHOT`; all entries
exist. This is runtime prerequisite evidence, not service or producer acceptance.
The supported local launcher selector remains outside staging and commits.

Original representative003 completed normal Information CML generation and
production Compile. Result `phase96-p96-05a-representative-result-003` revision 1
is FAILURE, SBT/wrapper 1 and serial lock released. Test compilation reported
three errors at the new FixtureFactory, its CapabilityFactory subclass and its
anonymous brokenfactory subclass: the existing abstract protected `create_Core`
hook was not implemented. Behavioral examples did not execute; no test PASS.
The earlier six diagnostics were not reported, but full Test compilation is not
accepted. All prior failures remain failed and their permissions consumed.

The parent froze `phase96-p96-05a-factory-core-fix-manifest-001` revision 1:
one existing test Scala file plus these three documentary status/history files.
Only the missing unused factory hook is added, matching the adjacent existing
fixture and keeping normal already-initialized component use. Production APIs,
design, behaviors, all 19 scenarios, 25 workflow paths/24 stage paths/17 Scala,
all 54 criterion texts and 24 checked/30 pending outcomes remain unchanged.
No new PLAN epoch or repository authority is created. The successfully prepared
Cozy runtime is retained; it does not need a duplicate export for this test-only
correction. Original representative004 and original 27-suite accumulator require
fresh current subject revision4 and exact typed execution, followed by independent
protected whole-Step Solhigh review. All remain PENDING; P96-05..11 stay OPEN.
Candidate2 remains unfinished, completed1/nonconverging0 retained. No main
reflection, push, public publish, full-suite or Phase release/closure is admitted.

## 2026-10-05 — Specified nonvoid output contract after behavioral bootstrap failure

Representative004 completed normal Information CML generation and production and
Test compilation, so the unused factory-hook correction no longer blocks compile.
Its registered terminal result `phase96-p96-05a-representative-result-004` revision
1 remains FAILURE: SBT and wrapper exits 255, serial lock released. Five visible
E1..E5 failures report `operation output contract is required: query` during fixture
bootstrap; E6's header is visible, but E7/E8 and final test counts are not confirmed.
There is no final test summary and no new passing behavioral evidence. No separate
cause for the exit255 termination is claimed.

The parent established `P96-05A-OUTPUT-CONTRACT-001`: the authored SR2 and bound
Operation used empty nonvoid `ResponseDefinition()`, whose empty result makes
`isSpecified` false and violates the existing OperationDefinition construction
guard. The private legacy-service fixture has the same invalid declaration.
The frozen correction uses the existing CNCF OperationDsl declaration
`ResponseDefinition(result = List(DataType.Named("OperationResponse")))` for both
bound operations and that fixture. This declares the ordinary framework result,
not a new Display schema or arbitrary business payload. Actual execution still
returns OperationResponse.Json through the pure codec and closed
`cncf.display-model.v1` protocol, with unchanged identity/revision/fields, JSON-only
envelope, ordinary authorization and complete-registration lifecycle.

Current complete-Step manifest is `phase96-p96-05a-implementation-manifest-004`
revision 1. The exact protected-boundary FIX owns eight existing product paths,
adds response assertions within existing successful bootstrap E1 and actual
public-read/resolver E8, and reconciles SR2, design and truthful status/history.
It retains 24 product/stage paths, 17 Scala programs, 19 scenarios and the one
preserved task-local launcher selector outside staging and commits. All prior
patches, failures, expected traces and seven Hygiene records remain retained.

The successful dedicated Cozy runtime prerequisite and inspected version
0.3.3-SNAPSHOT/classpath are reused, not rerun solely for serialization. After
authoring/static preflight the parent requires fresh representative005 with
validation subject revision5, then the unchanged original 27-suite accumulator
and a fresh independent Sol-high protected review of the complete Step, not
only this eight-path correction. These remain PENDING, as do Step acceptance,
commit, final Phase review, final repository validation and release/closure.
Repair candidate2 remains unfinished; completed repairs1/nonconverging0, original
Goal/bases/epoch1/ledger26 and all54 criteria (24checked/30pending) are unchanged.
P96-05..11 remain OPEN. No main reflection, push or public publication is admitted.

## 2026-10-05 — HTTP suffix diagnosis and bounded conformance repair

Representative005 (`phase96-p96-05a-representative-result-005` revision 1) failed Test compilation with two `NonEmptyVector.foreach` diagnostics. The parent inserted `.toVector` in the two existing response-contract assertion iterations without changing their assertions or scenarios. SBT/wrapper exited 1 and the serial lock was released.

Representative006 (`phase96-p96-05a-representative-result-006` revision 1) completed normal Information generation and production/Test compilation. The actual DisplayServiceReadSpec summary is 8 tests, 6 successes, 2 failures, one completed suite and zero aborted/canceled/ignored/pending. E1/E4/E5/E6/E7/E8 passed. E2 failed at selected List resource extraction (`None.get`, line62); E3 observed HTTP404 instead of200 (line108). SBT/wrapper exited1 and the serial lock was released. This is partial behavior evidence, not Step acceptance.

Parent source diagnosis identified `PathPreNormalizer.rewriteSegments`: joining HTTP path elements with dots before suffix removal lets `.json` become an operation-selector element. Existing path-resolution R4 and Display protocol R7 require representation suffixes not to change the canonical route. The bounded repair retains slash delimiters for the existing slash-aware pre-normalizer, without modifying CLI rewriteSelector, alias loading/mode policy, authentication, Display schema or actual HTTP fixture expectations. The new PathPreNormalizerHttpSuffixSpec carries canonical suffix equivalence, alias/CLI compatibility and a >=100-successful zero-discard property.

Current complete-Step manifest is `phase96-p96-05a-implementation-manifest-005` revision1:27 consumed workflow inputs,26 product-stage paths,19 Scala target programs and22 authored behavior groups. The local Cozy selector remains consumed but excluded from staging. The additional two program paths are an existing-contract repair frontier inside the original dedicated CNCF worktree; the historical informed6file approval is not generalized to bypass platform review.

This entry records authoring only. Fresh representative007 and expanded32suite accumulator, independent protected whole-Step Solhigh acceptance and Step commit remain pending. All54 criterion texts and24checked/30pending states, original Phase/Cozy/parser history and failures remain unchanged. Repository-full validation, mandatory full Phase reviews, releases, separate Cozy closure and app Phase5 aggregate/E2E acceptance remain at their own gates.

## 2026-10-05 — HTTP400 evidence gap and hypothesis reassessment

Representative007 (`phase96-p96-05a-representative-result-007` revision1, subjectrevision7) completed normal Information generation and production/Test compilation. It ran8 tests in1 completed suite, with6 successes(E1/E4/E5/E6/E7/E8),2 failures and0 aborted/canceled/ignored/pending. E2 failed at selected List extraction (`None.get`, ReadSpec62). E3 observed HTTP400 instead of200 (ReadSpec108), replacing the earlier006 HTTP404 observation. SBT/wrapper exited1 and the serial lock was released. The new suffix E1..E3 compiled but were not selected by this representative. This is partial behavior evidence, not Step acceptance.

The earlier parent candidate that a copied `textus.output.shape` Argument reaches DisplayService was refuted by additional ordinary execution-path inspection: `_execute_resolved_operation` calls `_domain_request`, which removes framework/security arguments including `textus.*`, before passing the business request to `component.logic.makeOperationRequest`. The historical private hypothesis remains preserved as a hypothesis; it is not rewritten into verified cause. The exact returned HTTP400 carrier remains unknown. No shared Subsystem or DisplayService input-relaxation patch is admitted on that candidate.

Current complete-Step manifest is `phase96-p96-05a-implementation-manifest-006` revision1, with the same27 consumed inputs,26 product-stage paths,19 Scala programs and22 behavior groups. The next frozen four-path failure-evidence correction adds actual response-body clues and successful HTTP preconditions before List selection inside existing ReadSpec E2/E3, and updates these status records. It preserves all HTTP200/object/metadata/order/UTF8/source-count/security expectations, original fixtures/routes, property >=100-successful/zero-discard gates and unchanged54 criteria (24checked/30pending). No production source is changed by this correction.

This entry records diagnostic authoring only. Fresh representative008 with subjectrevision8 remains pending. If it fails, retain the actual body/status and return to parent causal planning without weakening acceptance; a successful representative is required before the retained32-suite focused accumulator. Independent protected whole-Step Solhigh review/commit, final Phase review/test/release, separate Cozy closure and app Phase5 aggregate/E2E remain pending. The historical informed six-file approval remains scoped to its original patch; main reflection, push and public publication remain excluded.

## 2026-10-05 — Missing HTTP ingress identified and fixture assembly correction

Representative008 (`phase96-p96-05a-representative-result-008` revision1, subjectrevision8) completed normal Information generation and production/Test compilation. It ran8 tests in1 completed suite:6 successes(E1/E4/E5/E6/E7/E8),2 failures,0 aborted/canceled/ignored/pending. The new E2 pre-selection check at ReadSpec64 and E3 existing status check at123 both recorded HTTP400 with `HTTP ingress not configured` and detailCode10799020000. SBT/wrapper exited1 and the serial lock was released. Actual serial log: `/private/tmp/skill.cncf.d/a-45c839b8-70c6-4de0-b0b6-a3f16d8b6838/22245-20261005T005920Z.log`. These are failed execution/diagnostic facts, not accepted read-service validation. The32-suite accumulator was not run.

Parent source diagnosis now identifies the missing fixture transport assembly: Subsystem selects the component HttpRequest ingress before request encoding; DisplayServiceReadFixture.open called initialize with the default Protocol.empty, whose handler has empty ingress/egress collections. Effective Display registration changes services only and correctly preserves that handler. Existing Component.Factory.spec_create and Protocol.Builder use the standard ProtocolHandler.default, which includes ordinary RestIngress/RestEgress. The earlier checkpoint loosely named the factory entry create_Core; the exact inspected symbol is spec_create. No production default, fallback, business-input relaxation or framework-Argument repair is required for this missing ingress. The earlier hypothesis remains recorded as refuted, not rewritten into the cause.

Current complete-Step manifest is `phase96-p96-05a-implementation-manifest-007` revision1, retaining27 consumed inputs,26 product-stage paths,19 Scala programs and22 groups. The frozen four-path correction imports ProtocolHandler and passes Protocol(handler = ProtocolHandler.default) only at the existing open-session initialize call before bootstrap. The general initialize default Protocol.empty and all its other callers remain unchanged. Existing ReadSpec E2/E3/public property E1 provide the actual HTTP regression, and factory E2/E5 preserve handler/Core identity contracts. This is fixture authoring only until fresh execution; no production source, URI, business property, assertion, expected200/JSON/losslessness/order/source-count/security or generator is changed.

Fresh representative009 with subjectrevision9 remains pending; successful execution is required before the retained32-suite focused accumulator. Independent protected whole-Step Solhigh acceptance/commit and final Phase fullreview/validation/release, separate Cozy closure and app Phase5 aggregate/E2E remain pending. All54 criteria remain exactly24checked/30pending, P96-05..11 OPEN, repaircandidate2 unfinished/completed1/nonconverging0, originalGoal/bases/epoch1/ledger26 and7acceptedHYG retained. The task-local Cozy selector stays consumed but excluded from staging. Main reflection, push and public publication remain excluded.

## 2026-10-05 — HTTP200 progress and suffix-format contract reassessment

Representative009 (`phase96-p96-05a-representative-result-009` revision1, subjectrevision9) completed normal Information generation, production203Scala compilation and Test compilation of the corrected fixture. It ran8 tests in1 completed suite:6 successes(E1/E4/E5/E6/E7/E8),2 failures,0 aborted/canceled/ignored/pending. E2 failed its selected-identity Option guard at ReadSpec72; its printed List body is a bare cncf.display-model.v1 object with2model.items and no data/execution. E3 raised None.get in _list_resources404 called125; its body was not printed. Existing first/List HTTP200 checks completed before E2 selection, and E3 list.status200 completed before the helper, so the earlier missing-ingress boundary is passed, not a whole-slice success. SBT/wrapper exited1, lockreleased. Actual log: `/private/tmp/skill.cncf.d/a-5eb4713e-50ca-40a5-a6a9-f8a8110a902d/38261-20261005T012909Z.log`. The32-suite accumulator was not run.

The private diagnostic checkpoint initially proposed explicit JSON format in the Commandmode fixture; that candidate was never authored or accepted. Parent PLAN read the governing output-format specification General Rules6/7 and path-resolution R4: explicitproperty precedes suffix, suffix precedes mode default. Display protocolR7/R9 and runtimeSR3 require the existing GET.json/envelope contract. Adding a format property or switching fixture RunMode would avoid testing that suffix contract. Source inspection instead shows Request.ofHttpRequest retains Source.Http, normal Subsystem formatting receives that request, but OperationResponseFormatter._resolve_format considers only request/config/default and ignores its HttpRequest final-path suffix. Commanddefaultyaml therefore bypasses the Json envelope branch despite .json selection. This corrects the proposed repair, not the actual009 record or the earlier scoped008 ingress cause; framework-Argument hypothesis remains refuted.

Current complete-Step manifest is `phase96-p96-05a-implementation-manifest-008` revision1:27 consumed inputs,26 product-stage paths,19 Scala programs and23 authored behavior groups after new EnvelopeE4. The frozen7existingpath repair reads only supported suffix from Source.Http.pathParts.lastOption in the existing formatter, below explicitrequest format aliases and above configuration/mode fallback. It adds E4 behavioral precedence/unsupported-suffix/nonHTTP compatibility coverage and narrowly updates protocol spec/design plus currentstatus/journal. Fixture .json URI, shape-only properties, suppliedstandardhandler, genericemptyinitializer, businesspayload/HTTP200/envelope/losslessness/source/security/lifecycle expectations and original22groups remain unchanged. No Subsystem, CLI, globaldefault, dependency, config or schema alteration is admitted. This is authoring only until actual execution.

Fresh representative010 subjectrevision10 remains pending; only success permits the retained32-suite focused accumulator, including EnvelopeE4 and legacy formatter/HTTP/CLI consumers. Fresh independent protected completeStep Solhigh acceptance/commit and finalPhase review/validation/release, separateCozyclosure/appPhase5aggregateE2E remain pending. All54criteria remain24checked30pending, P96-05..11 OPEN, candidate2unfinished/completed1/nonconverging0, originalGoal/bases/epoch1/ledger26/7HYG retained. LocalCozyselector remains consumedexcludedstage. No main reflection, push or public publication.

## 2026-10-05 — HTTP lossless progress and fixture MIME-view correction

Representative010 (`phase96-p96-05a-representative-result-010` revision1, subjectrevision10) completed normal Information generation, production compilation of204Scala sources and Test compilation of1Scala source. It ran8 tests in1completed suite:7successes(E1/E3/E4/E5/E6/E7/E8),1failure(E2ReadSpec81),0aborted/canceled/ignored/pending. SBT/wrapper exited1 and the serial lock was released. Actual log: `/private/tmp/skill.cncf.d/a-461faba1-3a9e-4e4d-a186-917b5a776d69/55559-20261005T020731Z.log`. This is partial execution evidence, not whole-Slice acceptance. The32-suite accumulator did not run.

E3 now passes all authored finite empty/reversed Lists and exact five-kind Unicode/surrogate/decimal coefficient-scale/nanosecond HTTP-byte cases. E2 completed its initialHTTP200 and selected-identity guards and requested selectedDetail before the first Content-Type equality failed: actual `Some("MediaType(application/json)")`, expected unchanged `Some("application/json")`. Its later envelope/metadata/source/order/semantic-consumer expectations were not reached and remain unverified. No raw header string was printed. Parent source diagnosis identifies a test-fixture view error: Session.http extracts the parsed Content-Type mediaType, then uses its diagnostic `.toString` rather than MIME mainType/subType. Existing Http4sHttpServerDispatchSpec uses the latter representation. This does not establish a production Content-Type defect or authorize changing the expected MIME value.

Current complete-Step manifest is `phase96-p96-05a-implementation-manifest-009` revision1:27 consumed inputs,26 product-stage paths,19 Scala programs and23 existing behavior groups. Frozen `P96-05A-HTTP-MEDIA-TYPE-VIEW-001` changes only the existing fixture MIME-string extraction to `mainType/subType` and updates these3current-status documents. The expected application/json, original .json URI/shape-only properties/RunMode/defaultHandler/genericemptyinitializer, actual routes, all23groups and every HTTP200/envelope/UTF8/losslessness/source/security/lifecycle expectation are retained. Sharedformatter suffix precedence and EnvelopeE4 remain authored unchanged; no new converter/API/schema/config/production/CLI/Subsystem change. This checkpoint records authoring only until fresh execution.

Fresh representative011 subjectrevision11 remains pending; only success permits the retained32-suite focused accumulator, followed by fresh independent protected complete-Step Solhigh acceptance/commit. EnvelopeE4 and suffix groups have compiled but remain unexecuted. FinalPhase fullreview/validation/release, separateCozyclosure and appPhase5aggregateE2E remain pending. All54criteria remain24checked30pending, P96-05..11 OPEN, candidate2unfinished/completed1/nonconverging0, originalGoal/bases/epoch1/ledger26/7HYG retained. Failed001..010 stay failed/consumed; localCozyselector remains consumedexcludedstage. No main reflection, push or public publication.

## 2026-10-05 — Read chain success and registration conformance correction

Representative011 (`phase96-p96-05a-representative-result-011` revision1, subjectrevision11) completed normal Information generation and production/Test compilation:8tests/8successes,1completed suite,0aborted/canceled/ignored/pending, SBT0/wrapper0 and serial lock released. It establishes all existing ReadE1..E8 assertions, including Detail-first/List/select/Detail, actual application/json envelope, typed semantic consumption and the twelve finite HTTP losslessness cases. Actual log: `/private/tmp/skill.cncf.d/a-65f89717-9222-4d2f-a2ba-172baea66b8a/76008-20261005T023224Z.log`.

Accumulator001 (`phase96-p96-05a-accumulator-result-001` revision1, subjectrevision11) executed the retained32focused suites:266tests,263successes,3failures,32completed/0aborted suites and0canceled/ignored/pending; SBT1/wrapper1 and serial lock released. Actual log: `/private/tmp/skill.cncf.d/a-14d99e7a-b5bf-4181-b8a3-6e6de4a9772e/87960-20261005T023558Z.log`. Only RegistrationE2/E3/E5 failed. Other31suites and RegistrationE1/E4/E6 pass, including EnvelopeE4, suffixE1..E3 and both public-property groups. Their executed expectations require >=100 successful zero-discard cases; no separately printed exact property count is claimed. The failed accumulator is diagnostic evidence, not whole-Step acceptance. The product Goal was observed paused during checkpointing and subsequently active; original binding/bases/authority were reverified before returning to PLAN.

Parent causal planning preserves the accepted contracts. `P96-05A-REGISTRATION-EMPTY-ABI-001`: the new E2 present-empty provider contradicts LUI96-S05/S06 and actual LUI96_DEFINITIONS_MISSING. Keep production ABI rejection; distinguish absent-provider legacy success from present-empty typed failure before runtime/service getters, with no publication/fallback. `P96-05A-REGISTRATION-CONSTRUCTION-REDACTION-001`: the new Component preparation catch passes a raw private factory exception into componentInvalid(e), contrary to existing SR4 fixed structured failure. Use the existing semantic componentInvalid(message) with exactly `Display service construction failed`, no exception/cause facet or raw logging; retain no-partial-publication and strengthen E3 taxonomy/cause/message assertions. `P96-05A-REGISTRATION-SUBSYSTEM-OPTION-VIEW-001`: E5 compares Option[Subsystem] to Subsystem by reference; compare its contained reference with exists(_ eq subsystem), preserving all other Core/effective-protocol/resolver assertions. Later E5 assertions were not reached in accumulator001.

Current complete-Step manifest is `phase96-p96-05a-implementation-manifest-010` revision1:27consumed inputs,26stage paths,19Scala programs and23behavior groups. This7existingpath correction changes only the bounded Component catch, existing RegistrationE2/E3/E5 assertions and matching runtime spec/design/current-status history. It admits no ABI/provider success policy, public API/schema/default/handler/formatter/source/subsystem/config/library/Cozy change. Existing23groups, all54criterion texts and24checked/30pending states, P96-05..11 OPEN, candidate2unfinished/completed1/nonconverging0, originalGoal/3bases/epoch1/ledger26 and7acceptedHYG remain retained. LocalCozyselector stays consumed but excluded from staging; successful dedicatedCozyruntime prerequisite is reused.

This entry records authoring only. Fresh representative012 subjectrevision12 selects Registration6+Read8; only its success permits unchanged32suite accumulator002, followed by a fresh independent protected whole-Step Solhigh review of all26stage/19Scala/config/consumers/fourP96-05criteria, not only this7path repair. No Step acceptance/commit, fullPhase review or closure is claimed. SeparateCozy closure and appPhase5aggregate/E2E remain pending. Historical informed6file approval is not generalized; main reflection, push and public publication remain excluded.


## 2026-10-05 — Current focused validation completed; independent review pending

Representative012 subject12 passed 14/14 tests in 2 suites; accumulator002 subject12 passed 266/266 tests in 32 suites. Both completed with SBT0/wrapper0, released serial lock and zero failures/aborts/canceled/ignored/pending. Parent typed readers accepted current request/subject/runner and consumed permission. Registration E2/E3/E5 and their remaining assertions now execute successfully. Focused validation is accepted evidence, not independent Step acceptance. Fresh protected whole-Step Solhigh review, Step acceptance/commit, final Phase full review/validation/release and separate Cozy/app gates remain pending. P96-05..11 remain OPEN; all54 criterion texts and24checked/30pending states are retained.

Representative result: `phase96-p96-05a-representative-result-012` revision1; accumulator result: `phase96-p96-05a-accumulator-result-002` revision1; validation subject12. Actual logs: `/private/tmp/skill.cncf.d/a-abf4f8be-d60e-4813-8c2f-10aea34b7ef4/10818-20261005T032304Z.log` and `/private/tmp/skill.cncf.d/a-08b97200-5ecc-4ef8-8517-bcdf35c06c2e/13689-20261005T032642Z.log`. Both retain readable summaries and process-derived terminal lock-release evidence. The child native-origin instruction was absent; the V2 preflight explicitly accepted ordinary standing authority with origin_present=false and direct_child_user_message_required=false. This does not assert delivery or create fresh authority. Parent independently admitted original source and verified complete current result subjects.

Current manifest010 retains27consumed inputs/26stage paths/19Scala programs/23behavior groups. The documentation-only current-status projection changes no product behavior, accepted contract, fixture, command or criteria. Existing successful subject12 validations remain applicable; no SBT rerun is required for this bookkeeping. All historical failed001..010 and accumulator001 records remain failures; successful011 stays historical. Candidate2unfinished/completed1/nonconverging0, originalGoal/3bases/epoch1/ledger26 and7HYG remain retained. No independent review, Step commit, Phase acceptance, main reflection, push or public publication is claimed.

<a id="p96-05-step-acceptance"></a>

## 2026-10-05 — P96-05 independent Step acceptance

Independent protected whole-Step review `phase96-p96-05-step-review-001`
revision 1 by `/root/phase96_p96_05_step_review_001` (GPT-6.1 Sol high)
inspected all 27 consumed inputs, 26 product-stage paths, 19 complete Scala
programs and 23 behavior groups. It supported all four unchanged criteria with
the sole blocker `CPB-P96-05-NAMING-001`. The exact internal naming repair
changed seven names in three existing source programs and their direct callers.
Fresh independent focused review `phase96-p96-05-naming-rereview-001` revision 1
by `/root/phase96_p96_05_naming_rereview_001` (GPT-6.1 Sol medium) passed the
repair, direct edges and current evidence, resolving that blocker with no new
blockers, Hygiene or Development Candidates. Parent admission is
`phase96-p96-05-naming-focused-review-001` revision 1 PASS in
`phase96-review-ledger-001` revision 28, retaining the original whole-Step review.

| Actual result reference (revision 1) | Subject and successful evidence | Serial log basename |
| --- | --- | --- |
| `phase96-p96-05a-representative-result-012` | Subject12: 14/14 tests, two completed suites | `10818-20261005T032304Z.log` |
| `phase96-p96-05a-accumulator-result-002` | Subject12: 266/266 tests, 32 completed suites; retained behavioral baseline | `13689-20261005T032642Z.log` |
| `phase96-p96-05-naming-validation-result-001` | Current subject13: 14/14 tests, two completed suites after naming repair | `41470-20261005T042010Z.log` |

All three commands completed with SBT exit 0, wrapper exit 0 and serial lock
released, with zero failed/aborted/canceled/ignored/pending outcomes. The current
naming-focused command completed normal Information generation and production/
Test compilation. The 266-test baseline was not rerun on subject13; its retained
coverage and the fresh 14-test result are separately admitted by the focused
review. The executed property expectations require >=100 successes and zero
discards; no separately printed exact property-case count is claimed.

| Unchanged P96-05 criterion | Accepted executable and review evidence |
| --- | --- |
| Registered component-scoped Operations and admitted inputs | Registration E1..E6 and Read E4/E5/E8 prove assembly, collision/admission, effective consumers and closed selection; whole-Step review and focused naming closure preserve the contract |
| Detail first, then List/select/Detail through canonical View/query | Read E1/E2/E3 prove one contextual source route, versioned identity/revision, ordering and unchanged typed semantic consumption |
| Missing/foreign/undeclared/unauthorized rejection | Read E4..E7, public property E2 and registration rejection cases prove confinement, control-input rejection and ordinary authorization without a public generic runtime gateway |
| Complete public read fixture, metadata/order and semantic separation | Read E1..E8, public properties E1/E2, Envelope E1..E4 and suffix E1..E3 passed in the retained accumulator; fresh Registration6 + Read8 and independent closure preserve this complete read slice |

Actual HTTP evidence uses in-process Http4s routing and body bytes. It is not
socket/live-server E2E acceptance. All failed001..010 receipts, representative011
8/8, accumulator001 263/266, planned UNEXECUTED traces and the earlier pending
acceptance checkpoints remain chronological history.

All ten nonblocking Hygiene records remain OPEN: HYG-COZY76-P7603-MODELER-LOCAL-NAMING-001,
HYG-COZY76-P7603-MODELER-RESPONSIBILITY-001, HYG-COZY76-P7603-README-CLI-PROJECTION-001,
HYG-P96-02-001, HYG-P96-03-SPEC-TRACEABILITY-001,
HYG-P96-05-BASELINE-MAINTENANCE-001, HYG-P96-05-GIVEN-TRACEABILITY-001,
HYG-P96-05-VALIDATION-PROJECTION-001, HYG-P96-DOC-001 and HYG-P96-PARSER-SIZE-001.
No Development Candidate is admitted and this status projection closes no Hygiene.
Completed Step repairs remain 2, consecutive nonconvergence 0, convergence
narrowing and Phase repairs 0; documentary projection and the internal naming
conformance correction neither increment nor reset behavioral repair history.
Original Goal, all three bases, authority, binding and PLAN epoch 1 are retained.

P96-05 is CLOSED as the independently accepted local Step commit candidate;
that commit is PREPARED/PENDING and no commit hash or executed receipt is claimed.
All 54 criterion texts remain unchanged with 28 checked and 26 pending,
current_entry=P96-06. All six later stages P96-06..11 remain OPEN. Phase 96
remains in-progress with zero full Phase reviews; later server/reference,
mutation/context, final repository validation, Phase review/release/closure,
separate Cozy Phase 76 final gates and app Phase 5 real-server aggregate/E2E
acceptance remain pending. No main reflection, push or public publication is claimed.
