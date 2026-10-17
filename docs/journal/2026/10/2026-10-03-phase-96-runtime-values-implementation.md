# 2026-10-03 — Phase 96 runtime Display values implementation

Document role: implementation history, not a normative specification or design promotion.
Scope: PHASE-96 / P96-02 / P96-02B; frozen
`phase96-p96-02b-implementation-manifest-001` revision 1 and
`phase96-p96-02b-cycle-plan-001` revision 1.
Status: runtime implementation and Executable Specification authoring complete;
parent verification, combined focused validation, independent review and Step
acceptance are pending. No test execution or successful result is claimed here.
P96-02 and Phase 96 remain open. This record issues no LUI receipt.

## Authoring scope and preserved inputs

The sole product root is the Phase 96 CNCF checkout on
`codex/phase-96-display-service`. The original execution base remains
`6cbe877c53c116639feb11fa80546e79c051804c`; inspected HEAD is
`646c6115a038cb418b628de998af3236b6e4a816`, not a replacement baseline.
The private `p96-02b-pre-edit-001.json` capture's 44 path states were compared
by `(root,path)` before editing, including contents, file modes and Git index
entries. The six owned paths were absent; all 38 consumed inputs matched.
P96-02A's six reviewed uncommitted paths remain outside B's mutation ownership.

Exactly these six new product paths are authored:

- `src/main/scala/org/goldenport/cncf/display/DisplayValue.scala`
- `src/main/scala/org/goldenport/cncf/display/DisplayModel.scala`
- `src/main/scala/org/goldenport/cncf/display/Display.scala`
- `src/test/scala/org/goldenport/cncf/display/DisplayModelSpec.scala`
- `src/test/scala/org/goldenport/cncf/display/DisplayModelPropertySpec.scala`
- `docs/journal/2026/10/2026-10-03-phase-96-runtime-values-implementation.md`

All five Scala targets are new. They use the current creation-date header,
explicit structural braces, conceptual sealed abstract trunks, immutable final
case classes, public camelCase parameters, private `_snake_case` members and
flatcase private parameters/local variables. There is no inherited same-file
target-program debt to migrate.

## Runtime construction boundary

`DisplayValue` has Text, Number, Boolean, Timestamp and payload-free Missing,
using the existing generated ABI ValueKind. Text preserves its supplied String,
Number its BigDecimal, and Timestamp its Instant. Constructors form raw
candidates, including deliberate invalid null reference payloads. There is no
coercion, date/choice/structured extension or unsupported-input string fallback.

`DisplayModel` has List and Detail candidates, typed Resource, opaque optional
QuerySnapshot, ordered sections/fields and inert actions. Resource holds the
supplied existing EntityId and EntityRevision references. List items have no
eager Detail payload. Hidden, Disabled and Enabled are explicit presentation
states retaining their declared positions; availability grants no permission.

The final non-case `Display` constructor is private to its companion. Its sole
public construction gate, `createC`, selects one definition by exact screen ID
from already admitted complete definitions and validates the supplied candidate.
Success retains the selected definition and candidate references, including all
nested values, vectors, titles, labels, binding IDs, resource references and
snapshot. Construction neither admits definitions again nor joins sources,
converters, Views or Operations.

Validation follows the frozen lazy first-error order: definitions/model shape,
opaque screen ID, lookup, matching branch; then exact title, target/snapshot or
resource, item/section structures and correspondence, nested fields/actions,
List resource uniqueness, and finally top-level actions. Scoped ID validity and
duplicates, non-Content role validity/uniqueness, declared count/order and exact
label/role/binding correspondence precede value admission. Missing requires
explicit declaration. Title text must be nonblank; other text roles and Content
retain empty or whitespace text when declared. A repeated EntityId fails even
when its EntityRevision differs.

`Diagnostic(code,path,reason)` uses the closed DSP96 code set and index-based
paths. Null references have the manifest's specific ID/role/resource taxonomy
where applicable. One private semantic failure helper calls
`Consequence.fail(Taxonomy.argumentInvalid, new ValidationException(diagnostic))`.
The exception is only a typed cause carrier and is never thrown by validation.
Reasons describe bounded expected structure and include no submitted screen,
label, binding, snapshot, resource identity or runtime value text.

## Independent identity and version spaces

| Space | Owner / representation | Sample or current evidence | Meaning |
| --- | --- | --- | --- |
| Generated ABI identity/version | GeneratedLogicalUiAbi | `cncf.logical-ui-generated.v1`, integer `1` | Static source-constructor compatibility |
| Phase 74 descriptor/schema/version | SemanticContract | `cozy.logical-ui-runtime.v1`, integer `1`; semantic authority `cozy.logical-ui-semantics.v1`; assurance `provisional-scenario` | Exact separate semantic descriptor, including its immutable DriverReference |
| Descriptor driver revision | DriverReference | `48eb43b8eb839137e17ef4c944b3db4a73d0cec3`, `docs/phase/phase-2.md`, `resource-list-detail` | Historical semantic driver provenance, never Entity state |
| Generated-source provenance/revision | SourceProvenance | Hand-written expected-generated ESS2-01 fixture: producer `cncf.expected-generated-fixture`, source `ess2-01`, revision `fixture-1` | Opaque source correlation; not actual Cozy generation proof |
| Definition screen/revision | Definition | `capture-detail` / `ess-definition-1` | Static screen identity and definition revision, never parsed as resource identity |
| Runtime resource identity | Existing typed EntityId | Explicit test-only `restore` with major `fixture`, minor `display`, collection `Capture`, constant epoch millisecond and supplied entropy | Opaque identity, compared through existing equality |
| Managed resource revision | Existing typed EntityRevision | Positive `createC(Long)` values such as `7`; `INITIAL` is `1` | One resource state revision, independent of every source/definition label |
| Source-query snapshot | QuerySnapshot.reference | `source-query:Ω-1` or absent | Optional opaque query correlation, not a mutation precondition |
| Display protocol schema/version | P96-04 | Reserved; no constant or codec selected in B | Cannot be inferred from ABI version, EntityRevision or Scala class layout |

Runtime fixture identity uses `EntityId.restore` only with explicitly supplied
major/minor, EntityCollectionId, Instant and entropy; helper code checks its
Consequence success. No screen parsing, random issuance or clock lookup occurs.
The exact value scenario uses a high-precision decimal
`12345678901234567890.123456789012345678901234567890`, an Instant with
`123456789` nanoseconds, Unicode/whitespace text, Boolean and explicit Missing.
These are test/implementation-history samples, not new public fixture admission.

Capabilities, writable field mappings and constraints, and concrete Business
Operation execution metadata are separate later-owned models. B provides no
empty map or Boolean stand-in for them, no no-op CRUD and no authority inferred
from Enabled availability or successful construction. P96-03 owns typed source/
converter projection and ComponentFactory binding readiness; P96-04 owns wire
members; P96-06/08 own mutation/Business concrete metadata and execution.

## Requirement and actual Given/When/Then authoring evidence

Both suites use AnyWordSpec, Matchers and GivenWhenThen, with afterWord metadata
`spec:display-model-runtime`, visible example ID, governing R identifiers and
`phase:96`. Every registered behavior has Given adjacent to fixture/generator
setup, When adjacent to factory/type-probe/property evaluation, and Then adjacent
to observable expectations. Each property law uses ScalaCheck `Test.check`
with at least 100 successful cases required, `result.passed shouldBe true` and
successful-count expectations. These are authored obligations, not test receipts.

| Scenario | Requirement / supplied Given | Actual When | Observable Then |
| --- | --- | --- | --- |
| E1 Detail preservation | R01/R02/R03: admitted ESS2-01, explicitly restored typed resource and exact nested values | `Display.createC(admitted,input)` | Same definition/model/resource/entity/revision/section/field/value references and exact text |
| E2 ordered List | R01/R03: multiple distinct resources, logical target, independent query snapshot | List factory call | Original ordered item vector, typed identities/revisions, target/snapshot and independent static revisions; no eager Detail member |
| E3 closed values | R02: all five Content declarations, precise BigDecimal and nanosecond Instant | Factory call for all supplied values | Exact kind/payload/reference preservation and explicit Missing |
| E4 empty containers | R01/R02/R03: empty List, empty sections/fields/actions, None and exact optional title | Three factories against normally admitted empty declarations | Same supplied candidates and vectors, preserved optional shapes and positive initial revision |
| E5 scoped IDs/roles | R01/R02: separate containers reuse IDs and repeat Content, deliberate local duplicates | Valid factories plus independent duplicate candidate factories | Reuse succeeds, duplicate section/field/action IDs and each non-Content role fail at second occurrence |
| E6 null boundaries | R02/R03: null wrapper/model, vectors, elements, ordinary text, typed resources, Option/Some(null), role/availability, reference payloads | Independent root/nested/payload factory calls | Structured exact code/path without uncaught null dereference or partial Display |
| E7 correspondence | R01/R02/R03: malformed/missing screen, wrong branches, title/target/section/field/action shape/count/order/label/role/binding | Each invalid candidate factory | Exact ID/lookup/mismatch code/path; missing/extra/reordered data cannot bypass declaration |
| E8 value/role | R02: text-only non-Content roles, incompatible kinds/Missing, blank Title, permitted other empty/whitespace text | Separate valid and invalid candidate factories | Kind admission precedes role check; blank Title fails, allowed text remains untrimmed |
| E9 resource uniqueness | R03: separately restored equal IDs with different revisions and distinct IDs with same revision | Valid List and duplicate/null-resource-member factories | Existing typed equality rejects second same ID independent of revision; absent typed members fail specifically |
| E10 actions | R05/R08: all three states and exact inert bindings in Detail/List/item action containers | Two pure factories | All states/order/bindings retained; compile-time executor/capability members unavailable |
| E11 first failure | R01/R02/R03: simultaneous early invalid members/later null siblings and marker text | Same candidate factory called twice | Stable first code/path/reason and bounded typed cause message without marker text |
| E12 construction privacy | R01/R02: local lexical external-package compile-time probe in the same spec file | Probe interpretation plus ordinary factory | Public candidates/factory usable; private constructor/apply/copy/unchecked bypass unavailable |
| E13 / P1 values | R01/R02: bounded Unicode/whitespace, arbitrary decimal/instant/Boolean/Missing and empty vectors | Generated admitted definitions/candidates evaluated through Test.check and createC | Exact values and original model/nested references preserved |
| E14 / P2 order | R01/R02/R03: joint field/section/action/resource permutations, including empty vectors and either definition order | Generated pair admission and List/Detail factories through Test.check | Supplied order/scoped identity retained in all containers |
| E15 / P3 scopes | R01/R02: unique cross-container reused IDs, arbitrary earlier member, duplicate scope and non-Content role | Valid reuse and repeated invalid factory calls through Test.check | Reuse succeeds; second field/action/section/role occurrence fails with stable exact path |
| E16 / P4 resources | R03: unique typed resource Lists including empty and arbitrary different positive revisions | Unique and repeated-ID factories through Test.check | Typed identity uniqueness independent of revision, equal diagnostics and unchanged inputs |
| E17 / P5 revision spaces | R03: independently generated opaque source/definition/snapshot labels and positive EntityRevision | Static admission and runtime factory through Test.check | ABI/descriptor/static revisions/resource revision/snapshot retained separately with no substitution |
| E18 / P6 invalid values | R02: generated wrong kinds/roles, Missing and blank Titles | Same failed candidate factory twice through Test.check | Equal typed diagnostics and original failed field/value retained without normalization/omission |

## Pending parent acceptance

The parent-selected combined four-suite focused command covers A+B. It must run
through the registered serialized SBT runner and provide all four discovered
successful suites, nonzero behavior cases, six runtime property laws, compilation,
terminal lock release and persistent receipt. The parent owns diff/scope/naming/
GWT verification and independent review of all twelve P96-02 accumulated paths.
This worker has performed no SBT, validation, self-review, staging, commit,
publication, deployment or transition work.

No out-of-manifest dependency, unresolved implementation decision, inherited
same-file debt or new separate-follow-up finding was encountered while authoring.
Existing HYG-P96-DOC-001 and the existing Hygiene/Development Candidate journals
are outside this mutation scope and retain their existing ownership. The later
projection, codec, service, mutation, context and actual Cozy producer proof
remain the Phase's separately required work; this constructor does not claim
their evidence or close their checklist obligations.

## P96-02 acceptance — 2026-10-03

This dated parent record supersedes only the pending acceptance/validation
status above; the original authoring history remains intact. The complete A+B
accumulator received independent `step-protected-focused` review
`phase96-p96-02-step-review-001` revision 1, boundary
`P96-02-AB-ACCUMULATOR`, from `/root/phase96_p96_02_step_review_001`.
Its result is PASS: all six P96-02 obligations supported, no current blocker.
The parent admitted that result and read back review-ledger revision 8.

Actual focused execution used the registered serialized SBT runner with:

```text
sbt --batch "testOnly org.goldenport.cncf.display.GeneratedLogicalUiAbiSpec org.goldenport.cncf.display.GeneratedLogicalUiAbiPropertySpec org.goldenport.cncf.display.DisplayModelSpec org.goldenport.cncf.display.DisplayModelPropertySpec"
```

All four suites completed successfully: 41 tests, zero failed/aborted/canceled/
ignored/pending; static ABI 18 scenarios + 5 property laws, runtime 12 scenarios
+ 6 property laws. The command compiled three runtime production and two runtime
specification sources; each runtime law required at least 100 successful cases.
SBT and wrapper exit codes were both zero and the shared lock was released.
The retained actual command result is `phase96-p96-02b-command-result-001`
revision 1, from `/root/phase96_p96_02b_sbt_001`. No test was rerun merely for
Step bookkeeping or updated workflow transport; this is not a full-suite result.

| P96-02 obligation | Accepted implementation and executable evidence |
| --- | --- |
| 1. Immutable neutral model and diagnostics | DisplayValue, DisplayModel and Display; runtime E1–E9/E11–E12 and six property laws cover List/Detail, ordered scoped contents, closed values and structured rejection. |
| 2. Distinct identity/version meanings | Static ABI descriptor/provenance and definition revisions remain separate from typed EntityId/EntityRevision and optional query snapshot; runtime E2/E9/E17 and static descriptor/provenance laws preserve them. Wire version remains P96-04-owned. |
| 3. Metadata/authority separation | Static binding identities and inert Hidden/Disabled/Enabled presentation actions carry no executor, writable mapping or capability authority; runtime E10 and compile-time probes preserve the boundary. Concrete mutation/Business models remain P96-06/08-owned. |
| 4. Pure GWT/PBT construction | Four suites exercise valid/invalid values, scoped identity, correspondence, order and stable failures without server state, Flutter/TFAF types or physical layout. |
| 5. Separate immutable ABI/provider | GeneratedLogicalUiAbi and GeneratedLogicalUiMetadataProvider compile as CNCF-owned Scala 3 types, separate from runtime instances and without a Cozy runtime dependency. |
| 6. Admission and expected-generated fixture | Static ABI scenarios/laws cover incompatible descriptors, invalid provenance/IDs, closed kinds, duplicates and broken targets; the external-package hand-written expected-generated fixture compiles and validates public-only construction. |

The nonblocking presentation follow-up HYG-P96-02-001 is persisted verbatim in
the [canonical Hygiene journal](2026-10-03-phase-96-hygiene-follow-up.md).
It concerns display metadata/identities in the two static ABI suites, not missing
behavior proof. No new Development Candidate was admitted by this review.

The [checklist](../../../phase/phase-96-checklist.md) records P96-02 CLOSED
on this construction/admission evidence; the Step commit is recorded by the
workflow only after Git succeeds. P96-03 remains OPEN and owns ComponentFactory
discovery/admission, converters/projection, actual Cozy-generated-source
acceptance and LUI-ABI-01/LUI-GEN-01/LUI-ACCEPT-01. P96-04 through P96-11 retain
codec, services, mutation, Business execution, fixture, context and consumer/
release proof. Phase 96 remains in progress; no full Phase review, repository
full-suite, consumer completion, publication or Phase closure is claimed.
