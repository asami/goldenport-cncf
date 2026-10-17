# 2026-10-03 — Phase 96 static generated Logical UI ABI implementation

Document role: implementation history, not normative specification or promotion.
Scope: PHASE-96 / P96-02 / P96-02A, frozen
`phase96-p96-02a-implementation-manifest-001` revision 1 and
`phase96-p96-02a-cycle-plan-001` revision 1.
Status: implementation and executable-specification authoring complete;
parent verification, focused validation and independent review pending.
No test command or result is claimed by this record. P96-02 and Phase 96 remain open.

## Authored product paths and responsibility

- `src/main/scala/org/goldenport/cncf/display/GeneratedLogicalUiAbi.scala`:
  S01–S05 public immutable constructor surface, closed role/kind tags,
  exact descriptor, atomic admission wrapper and deterministic typed failures.
- `src/main/scala/org/goldenport/cncf/display/GeneratedLogicalUiMetadataProvider.scala`:
  auxiliary raw-definition provider with the required abstract vector member.
- `src/test/scala/org/goldenport/cncf/display/GeneratedLogicalUiAbiSpec.scala`:
  executable ABI-01..06 admission and diagnostic scenarios.
- `src/test/scala/org/goldenport/cncf/display/GeneratedLogicalUiAbiPropertySpec.scala`:
  generated preservation, kind, scoped identity and reference laws.
- `src/test/scala/logicalui/ExpectedGeneratedLogicalUiFixture.scala`:
  external-package public constructor fixture and lexical compile-time type probes.
- This journal: requirement mapping, fixture attribution and implementation status.

All six paths were absent at the admitted pre-edit boundary. There was no
inherited same-file target-program debt to migrate. No other source, contract,
build, checklist, journal or workflow-state path is owned by this slice.

## Static boundary and diagnostics

Definitions remain raw untrusted values. The final non-case
`AdmittedDefinitions` constructor is private to `GeneratedLogicalUiAbi`; only
successful admission of the entire vector creates it. Admission retains the
original vector and definition objects, with no sorting or reconstruction.
The generated ABI carries registration keys and definition provenance only.
Entity identity/revision, runtime values, contextual permissions, writable
targets, converters, service registration and discovery remain later owners.

The full semantic descriptor is compared structurally and exactly. Opaque
source provenance is checked for nonnull, nonempty, exactly trimmed text,
without producer revision or fixture-hash pinning. Display text is checked for
nonblankness and preserved with its surrounding spaces.

Private pure diagnostic helpers select the first failure lazily. Definitions
are traversed in vector order, then screen ID duplicates, then List target
references against the complete selected vector. Container null guards precede
ID/role extraction; scoped duplicates precede field/action contents. Detail
targets are deferred to the referential phase. Duplicate paths identify the
first repeated occurrence. Diagnostic reasons are bounded contract explanations
and do not interpolate rejected data.

`AdmissionException` is a typed cause carrier, never thrown by admission. The
single failure helper calls `Consequence.fail(Taxonomy.argumentInvalid,
new AdmissionException(diagnostic))`. Specifications inspect the core Failure's
taxonomy and `conclusion.observation.cause.getException` for the typed
Diagnostic, without parsing message text or introducing a parallel result API.

## External expected-generated fixture provenance

`logicalui.ExpectedGeneratedLogicalUiFixture` is explicitly hand-written
expected-generated source, not actual Cozy output. It independently constructs
the full SemanticContract/DriverReference with the S01 values through public
constructors. Its ordered ESS2-01 screens are `collecting-resources` and
`capture-detail`; view binding is `ess.reference`, definition revision is
`ess-definition-1`, and both provenance values are
`SourceProvenance("cncf.expected-generated-fixture", "ess2-01", "fixture-1")`.
These opaque labels are development fixture choices and establish no approved
producer identity. The List fields are title then stage; the Detail section
adds description and the inert request-review action. Resource aliases,
runtime values, permissions and writable metadata are absent.

The parent admitted lexical external-package ScalaTest compile-time probes
inside this owned fixture, invoked from the production-package specification.
Literal expression snippets check inaccessible constructor/factory/copy
bypasses and nonexistent closed vocabulary alternatives. They use the actual
external package scope rather than syntactically invalid package declarations.

## Requirement-to-scenario mapping and Given/When/Then evidence

Every named `in` scenario below places Given beside its setup, When before its
admission/type-interpretation action, and Then before its expectations. Tables
of negative inputs invoke the actual gate for each case and inspect each
returned diagnostic's exact code/path. Unexpected throws fail the scenario.

| Requirement | Actual scenario in GeneratedLogicalUiAbiSpec | Given setup / When action / Then expectation |
| --- | --- | --- |
| ABI-01; S01/S02/S05 | admits the external expected-generated ESS2-01 pair verbatim | External provider vector / admitC(input) / original vector and object identity plus exact fixture fields |
| ABI-01/02/06; S02–S04 | admits unreferenced Details and empty nested containers without text repair | Detail-only and spaced empty containers / two admissions / exact preservation |
| ABI-01; S05 type boundary | prevents external construction factory and copy bypasses by type interpretation | External fixture / verifyAdmissionTypeBoundary and public gate / type rejection and full admission |
| ABI-02; S05 null boundaries | guards every root and nested null or optional-container boundary before dereference | Root/nested/value/Option null table / gate for each vector / exact typed diagnostics |
| ABI-02; S03 scalars | rejects empty padded and null opaque scalars with their declared taxonomy | Malformed scalar mutations / gate for every mutation / exact code/path with no repair |
| ABI-02; S03 text | rejects blank display text while preserving all nonblank supplied spacing | Null/blank and spaced nonblank text / independent admissions / exact failures and preservation |
| ABI-03; S01 | rejects each independently changed or null ABI and semantic descriptor member | Full independent descriptor mutation table / each admission / exact compatibility diagnostics |
| ABI-03; S01 provenance | accepts arbitrary valid producer source and revision labels independently of ABI versions | Arbitrary valid provenance/revision labels / full admission / unchanged raw data |
| ABI-04; S04 | rejects the second repeated ID in every declared screen section field and action scope | Later duplicate IDs in seven scopes / complete admissions / second-occurrence paths |
| ABI-04; S04 roles | rejects repeated non-content roles in either field container | All four non-content roles in List/Detail / each admission / exact repeated-role path |
| ABI-04; S04 reuse | allows cross-container IDs binding reuse and multiple content roles | Independent reused field/action containers / full admission / preserved reuse |
| ABI-05; S03 | preserves all five exact role and kind tags and every content subset order | All enum alternatives and unique subset permutations / admissions / preserved order and exact tags |
| ABI-05; S03 | rejects empty null and repeated kinds before non-content compatibility | Invalid kind and incompatible role table / each admission / specific kind or role diagnostic |
| ABI-05; S03 type boundary | excludes invented role and kind alternatives at compile time | External fixture / verifyClosedVocabulary / type rejection with no decoder |
| ABI-06; S04 | resolves forward and backward targets while preserving provider order | Both pair orders and unreferenced Detail / complete admissions / exact vector preservation |
| ABI-06; S04 | rejects null malformed self missing wrong-kind and cross-vector targets | Invalid target selections plus independent Detail / each admission / specific unresolved-target code/path |
| ABI-02..06; S05 precedence | orders shape ABI contract provenance scalars structure content and vector references | Simultaneous failures and later invalid elements / each gate twice / exact first diagnostics and determinism |
| ABI-02; S05 safe diagnostics | keeps attacker-controlled rejected values out of cause messages and diagnostics | Attacker marker in rejected ID / repeated admission / typed equality and marker-free bounded messages |

| Property law | Actual scenario in GeneratedLogicalUiAbiPropertySpec | Given setup / When action / Then expectation |
| --- | --- | --- |
| Opaque identity/revision and order | retains generated Unicode punctuation case source revisions and collection permutations verbatim | Unique Unicode/punctuation/case IDs, arbitrary revisions, rotated/reversed collections / generated full admissions through Test.check / passed and succeeded >= 100 |
| Content kind subsets and duplicate determinism | admits every generated nonempty unique content subset permutation and rejects its repeated member | Generated kind bitmask/subset permutation and repeated index / valid and invalid admissions through Test.check / preservation, exact duplicate path, equal typed diagnostics and >= 100 successes |
| Non-content text-only law | requires exactly Text for every generated non-content role allowance | Generated non-content role/kind permutation / admission through Test.check / independently stated singleton-Text success or exact ROLE_INVALID and >= 100 successes |
| Scoped reuse and arbitrary later duplicate | allows reuse across containers and rejects a duplicate at any generated later index in its owning scope | Generated unique reused containers and seven duplicate scopes / repeated full admissions through Test.check / permitted reuse, correct duplicate scope/index, deterministic diagnostics and >= 100 successes |
| Complete-vector target resolution | resolves generated target identities independently of order and rejects removal of the matching Detail | Generated opaque pair in both orders / complete and removed-target admissions through Test.check / preserved vector order, exact target failure and >= 100 successes |

Property expectations come from each stated law and generated construction,
not from production validators. ScalaCheck is configured for at least 100
successful cases per scenario. The declarations above describe authored
expectations; they are not execution evidence.

## Pending parent work

The selected focused validation remains the parent/registered runner's command
for the two suites, followed by parent scope/compliance checks and independent
review. The parent reported a command-authority infrastructure mismatch before
SBT started; no test failure or successful test evidence exists. This worker
did not execute validation, review, staging, commit, publication or deployment.
No checklist item, Step or Phase is closed, and no LUI-ABI-01 receipt is issued.

No additional implementation dependency, same-file debt or separate HYG/DEV
candidate was observed within the admitted authoring scope. Runtime P96-02B,
bootstrap/projection P96-03 and actual Cozy producer acceptance retain their
existing separate ownership; they are not omissions from this static slice.

## P96-02 acceptance update — 2026-10-03

The earlier pending statements record authoring-time status. The combined
A+B focused run subsequently passed all four suites and 41 tests, with terminal
SBT/wrapper success and shared-lock release. Independent complete-accumulator
review `phase96-p96-02-step-review-001` revision 1 returned PASS with all six
P96-02 obligations supported and no current blocker. See the
[parent acceptance record](2026-10-03-phase-96-runtime-values-implementation.md#p96-02-acceptance--2026-10-03)
for the actual command, evidence, requirement mapping and remaining owners.
HYG-P96-02-001 is a nonblocking display-metadata follow-up in the
[canonical Hygiene journal](2026-10-03-phase-96-hygiene-follow-up.md).
This is pure ABI/value acceptance, not actual Cozy generation, ComponentFactory
projection integration, LUI receipt issuance, full-suite or Phase closure.
