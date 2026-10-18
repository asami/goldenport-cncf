# Phase 103: Validation/Test Metadata ABI and Discovery

status=in-progress
planned_at=2026-10-06
driver=sm-workflow Phase 5
consumer=sm-workflow, textus-cbd-support, Cozy/CML projection
static_analysis_owner=textus-cbd-support on Mac mini
checklist=[Phase 103 Checklist](phase-103-checklist.md)

## Goal

Deliver one CNCF-owned, versioned Validation/Test Metadata ABI and deterministic
operation-level discovery interface. Executable Specifications/test operations
carry the declarations; consumers query the same normalized metadata without
maintaining separate authoritative TestSuite membership files.

Canonical declaration semantics: [V1 specification](../spec/validation-test-metadata.md)
and [design](../design/validation-test-metadata.md).
Planning input: [ABI proposal](../notes/validation-test-metadata-abi-proposal.md).
Decision and planning history: [producer journal](../journal/2026/10/2026-10-06-validation-test-metadata-abi.md).
Promote the proposal into the normal spec/design/Executable Specification layers
as implementation starts; this Phase is the work plan, not a competing ABI spec.

## Responsibility and dependency boundaries

- CNCF owns purpose/feature/duration/execution-requirement vocabulary, public Scala
  ABI, normalized class/operation merge semantics and deterministic discovery.
- [sm-workflow Phase 5](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-5.md)
  drives this producer work and consumes discovery for query/select/execute,
  runtime evidence and cost/convergence guards. Phase 6 connects AI/skills;
  Phase 7 applies it across repositories and Development Artifact Overlays.
- textus-cbd-support owns static Test Architecture analysis, lint, KPI and human
  review. Its implementation runs on Mac mini as separate consumer work. Publish
  the same ABI/fixtures for it; completion of its UI/KPI work is not a producer
  or sm-workflow initial-runtime gate. The owner is its
  [October 6 metadata static-analysis journal](https://github.com/asami/textus-cbd-support/blob/main/docs/journal/2026/10/2026-10-06-validation-test-metadata-static-analysis.md);
  its closed Phase 14 concerns native Job integration and is not reopened.
- Cozy owns CML property syntax and generation. CNCF supplies normalized mapping
  semantics and fixtures; this phase does not implement a Cozy parser.
- [Phase 99](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-99.md)
  supplies the analogous Finding Disposition producer/consumer pattern. Reuse
  established contracts where applicable without merging the two vocabularies
  or requiring unrelated Phase 99 completion merely for similarity.
- Existing execution-requirement semantics remain authoritative. This phase
  defines their metadata binding, not a second provider/execution model.

## Work status

Step P103-S1 passed focused validation and independent protected conformance
review: 17 declaration scenarios and 16 existing execution-contract regressions
passed across three suites, with no review findings. Its public declaration ABI,
callable normalization, Executable Specifications and normative declaration docs
are accepted in local Step commit `ac443ed353cc682251e4d17bf4e64a1285955798`.
Full Phase validation and acceptance remain pending.
The [implementation journal](../journal/2026/10/2026-10-18-phase-103-validation-metadata.md)
records the current handoff. The checklist remains the completion ledger.

S1 progresses VM-01 and the interpretation portion of VM-02/VM-03. It delivers
no annotations, framework discovery, inventory or invocation.

Step P103-S2 authors Java runtime annotations usable from Scala, admitted typed
declaration providers, real ScalaTest artifact/package discovery, scoped logical
references and revision provenance, COMPLETE/PARTIAL/UNKNOWN inventory, typed
framework selectors and pure query/reference resolution. Actual passing
FunSuite/WordSpec fixtures and behavior specifications are authored, including
one in-process parameter-case selector execution. S2 focused validation passed
36 discovery/selection scenarios and 17 S1 declaration regressions across three
suites, with zero failures or aborts and successful serialized SBT terminal
completion and lock release. Initial independent protected-focused review found
one descendant-symlink completeness defect. The bounded correction reports
skipped links without following them and adds directory-link and class-link
regressions. Fresh independent focused review accepted closure with no remaining
blocker, Hygiene or Development Candidate findings. S2 is
accepted in local Step commit `2ec6da1265d14430cf522443869a0b9b2647ea00`.

Step P103-S3 authors exact typed scenario selection, the public operation binder,
current-context grant/host-allowlist narrowing through the existing execution
path, and conservative exactly-one-test result interpretation. It also authors
normalized CML mapping equivalence and one packaged synthetic portable
static-consumer fixture with complete comparable baseline/candidate and
PARTIAL/UNKNOWN variants. The [shared guide](../fixtures/validation-test-metadata/README.md)
and [consumer handoff](../notes/validation-test-metadata-consumer-handoff.md)
map consumer responsibilities. Focused validation passed 79 tests across eight
suites; separate real top-level literal parameter selection passed one test in
one suite (total 80 tests across nine suites, zero failures or aborts).
SBT/wrapper exit codes were 0 and the shared lock was released. Independent
protected-focused S3 Step review returned PASS with zero Current Boundary
Blockers, Hygiene or Development Candidate findings and no evidence gaps.
S3 is accepted in local Step commit `1df07da9be8716edf0e0462bcba221ca8d020c60`. No runtime consumer
acceptance is inferred.

As of October 19, P103-S4 is IN-PROGRESS. The actual consumer connection is
delivered by [sm-workflow Phase 6.2](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-6.2.md),
which owns its implementation and dependency refresh. The source-admission
prerequisite is resolved by that consumer-owned delivery; this CNCF task neither
authorizes nor performs sm-workflow source edits. Neither whole Phase waits for
the other's whole completion. The [handoff](../notes/validation-test-metadata-consumer-handoff.md)
records actual discovery -> purpose/feature query -> identical exact producer
binding for selected/supplemental references -> native typed execution/outcomes.
The K2 CML scenario has COMPLETE ascii/empty/unicode inventory, query-selected
ascii and supplemental unicode, ordinary Test 3/3 and both exact TestOnly 1/1.
Its independent consumer acceptance and isolated Step commit
`b69a60a0fc3dd5e9e7df87fd08b2549f41cef220`, plus supplementary L3 Slice-plan
acceptance at `157388724869c21649936d4b2de7508571507605`, establish concrete
consumer evidence. They do not accept the dirty primary sm-workflow tree or
release Phase 6.2. Independent CNCF integrated S4 review passed; its exact local
Step acceptance commit remains pending. The synthetic example remains static input, never runtime proof.

The accepted Phase 103 operation binding is exact zero-input scenario selection.
Separately scoped nonempty typed-input work may coexist in the current tree;
its acceptance is not attributed to Phase 103.

The first broader producer validation failed and omitted the required native
PostgreSQL/MySQL flags; targeted non-DB reproduction also failed. Bounded
Action-context and Display observer corrections are authored. The October 19
closure-focused validation passed compilation and 161/161 tests across 19
suites, with no failures, aborts, canceled or pending tests, SBT/wrapper exit 0
and the shared lock released. This resolves the original seven and additional
three failures on that focused surface, including the List property/JUnit
failure; independent integrated S4 review has since passed, with its exact local Step acceptance commit pending. The subsequent
full001 invocation enabled PostgreSQL/MySQL and passed compilation, then failed:
6,853 tests across 861 suites, 6,851 passed and two failed, no suite aborts,
five canceled, one ignored and 46 pending; SBT/wrapper exit 1, lock released.
The remaining C3-C2 historical-caller/current-Action no-op oracle and C3-H5
direct SPI observer bypass were test observation defects, not native DB failures.
Both were corrected while retaining ownership, no-replay and full-state assertions.
Focused004 passed compilation and 173/173 tests across 21 suites, including the
previous 19 and both failure suites, private helper compilation and actual
Action/caller history assertions. It had no failures, aborts, canceled, ignored
or pending tests; SBT/wrapper exit 0, lock released. Required full002 is running
with explicit Test fork and both native DB flags; its outcome remains UNKNOWN.
Required full validation, one full Phase review and release acceptance remain open.

VM-01..04 and all full Phase checks remain obligations; internal Step handoffs
do not split the Phase.

## Development items

- [ ] VM-01: Freeze the versioned normalized metadata and representation contract.
- [ ] VM-02: Implement public Scala declarations and deterministic operation discovery.
- [ ] VM-03: Verify merge, identity, compatibility and discovery through Executable Specifications.
- [ ] VM-04: Deliver runtime/static consumer handoffs and document acceptance.

Detailed tasks and evidence belong in the [checklist](phase-103-checklist.md).

## Completion boundary

The producer ABI and discovery path are usable through public APIs, with stable
operation identities and metadata origin/revision sufficient for selection,
evidence and static analysis. Executable Specifications verify the declared
merge rules and invalid/unsupported input behavior. sm-workflow's concrete
runtime consumer demonstrates discovery -> purpose/feature query -> typed
operation binding; CNCF records its handoff without claiming all of sm-workflow
Phase 5 complete. A shared static-consumer fixture is supplied to the
[cbd-support metadata static-analysis workstream](https://github.com/asami/textus-cbd-support/blob/main/docs/journal/2026/10/2026-10-06-validation-test-metadata-static-analysis.md)
on Mac mini without waiting for the cbd-support application implementation.

Discovery scope/completeness, unannotated operations, feature references and
operation revision provenance are part of the shared consumer contract. Declared
operation duration units/scope remain distinct from runtime measurement input.

Class/specification defaults and operation/scenario overrides, multiple purposes,
feature union, duration semantics and existing execution-requirement composition
are covered. The supported Scala test framework must expose real executable
scenario identities; discovering a class label alone is not sufficient.
Independent review and required repository validation close the connected
producer change. Group implementation/specification and review around this
boundary rather than starting a separate review cycle for every helper.

## Non-goals

No sm-workflow one-minute/ten-minute budget constants, cbd-support KPI scores,
authoritative suite registry, runtime result fields in annotations, AI-dependent
metadata interpretation, content-integrity proofs or global build-tool lock.
Missing optional metadata is distinct from malformed/unknown required semantics;
consumer coverage policy decides what a missing required selection means.


sm-workflow retains a Slice validation management file for acceptance conditions,
metadata queries, supplemental operation references and typed parameters. Phase
103 supports stable operation discovery/reference resolution; it neither bans
that plan nor duplicates its policy or test declarations in another registry.
