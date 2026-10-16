# CNCF-Cozy Generation Compatibility Specification

Generation admission requires exact CNCF and Cozy coordinates and checked-in
or published evidence. Numeric version similarity is not compatibility proof.
The versioned resource has exact fields and pair statuses; the packaged
0.5.1/0.3.0 pair is `unproven`. The result is typed as `Supported`,
`Unsupported`, or `Incompatible` and carries structured diagnostics plus
evidence owner/location. Missing CNCF, missing Cozy, both missing, unsupported
coordinates, unsupported pairs, known-unproven pairs, and explicit pair
incompatibility remain distinguishable.

Compile target and `packaging.car.runtime.cncf` runtime range remain independent.
Evidence schema/invariant validation and pure generation admission are
operational in CV-02. CV-03 owns explicit-input resolution/wiring at the actual
generation invocation and rejects absent or contradictory invocation sources.
CV-04 owns descriptor path/target/schema and catalog validation. CV-05 owns
metadata and required source/output identity and access validation. CV-06A owns CAR project-contract
and package-gate consistency.

For a CAR project, `project.yaml` is the package gate's authority for the exact
Cozy generator, exact CNCF compile dependency, and CNCF runtime range.
`build.cozyVersion` must be non-empty. `build.dependencies.compile` must
resolve exactly one `org.goldenport::goldenport-cncf:<version>` or
`org.goldenport:goldenport-cncf_3:<version>` coordinate. The exact compile
target must be within the declared minimum and optional maximum, absent from
`excluded`, and present in `tested`. An inverted range is invalid.

Packaging must also resolve exactly one CNCF runtime descriptor from the actual
input JARs. Its runtime identity must be `cncf`; its module organization,
artifact, and version must identify the selected compile dependency; and its
descriptor version must equal that compile target. Missing, multiple, or
contradictory compile targets, missing required runtime metadata,
compile-target/range contradictions, and missing, duplicated, or mismatched
resolved artifact evidence fail with stable typed diagnostics before archive
acceptance. Operation defaults may configure legacy or partial package
invocations, but they must neither replace project-owned CAR compatibility
metadata nor supersede the resolved JAR evidence during strict CAR admission.

Integrated CAR lint and the Review Provider must report the same project-only
acceptance or typed rejection as the package gate. Publication must apply that
decision before repository output and project its catalog runtime range from
the accepted contract even when merged operation defaults disagree.
Resolved-JAR identity remains a package-only requirement.

When `target/cozy/generation-provenance.json` exists, package admission validates
known exact versions, source identity/readability and actual generated Scala
path inventory against the accepted CAR contract, then packages the selected
metadata path directly as top-level `generation-provenance.json`. Direct v1 and
aggregate v2 are supported. Nongenerated/legacy sources may omit metadata;
`src/main/car/generation-provenance.json` remains reserved.

For the sbt-cozy backend, Cozy's bridge validates the selected delegated v1
manifest/output pairs and writes the v2 installed-output aggregate, excluding
disposable delegate paths. Exact versions, canonical source/path uniqueness,
union and actual source/output access remain required. sbt-cozy removes delegate
work after that action succeeds; incremental reuse requires the final metadata.

Runtime activation must neither load Cozy nor evaluate generation
compatibility or provenance. CNCF runtime range, canonical coordinate and ABI
independently govern activation under CV-06C2.

A project-contract CAR package must contain top-level
`car-runtime-manifest.json` using schema `cncf.car-runtime-manifest.v1`.
It identifies the canonical CAR artifact name, version and component and
reproduces the accepted CNCF minimum, optional maximum, excluded and tested
metadata. Cozy rejects a source-managed runtime manifest and writes the
document after staging the selected package.

Before component discovery or classloading, packaged-CAR extraction rejects
a missing or unsupported runtime manifest, canonical descriptor/release
coordinate contradiction, inverted range, executing CNCF version below minimum,
above maximum or explicitly excluded. Tested values are nonempty evidence,
rather than a runtime allowlist. ABI format `cozy.car.abi-manifest.v2`, matching
canonical component namespace/id/version, ABI version `1` and an export of that
component are required. Existing extraction owns actual unsafe/duplicate ZIP
path rejection and the required main JAR; JSON regular-file/read/format failures
remain failures. Expanded canonical CARs use the same runtime and ABI gate.
CNCF treats `generation-provenance.json`, when present, as opaque metadata.

Cozy independently admits the current selected prebuilt CAR's manifest,
canonical coordinate, ABI, accepted runtime metadata and required packaged
provenance before repository writes. Selected project source readability,
generated Scala path inventory and exact CNCF/Cozy pair retain their meanings.
The selected archive is published directly; ordinary missing, malformed,
contradictory, unsafe-path and I/O failures remain operation failures.


**Current S45 purpose classification (2026-10-09): parent-classified-unreviewed.**
`E715-01-resource-consumer-subordinate` is retained caller-authored knowledge
content under the original purpose-based policy and R715-11 genuine-content
boundary; removing its resource-content fields is not selected Cozy
artifact-protection work. This current interpretation supersedes the dated
S43/S44 OPEN removal-prerequisite hypothesis, preserved below as history.
E715-01 independent connected acceptance, whole E715-03 assurance, E715-04/05
product removals, prior warnings and original Step/full-Phase review, commits
and distinct release remain OPEN. See the dated S45 classification below.


## Development Runtime Evidence (R1–R7)

The architectural context for this development-only route is
`docs/design/packaged-source-activation.md`; it does not alter the packaged
CAR admission route above.

### R1 Development evidence pair

An explicitly selected component development directory MUST contain both
`target/cncf.d/runtime-classpath.txt` and
`target/cncf.d/car-runtime-manifest.json`.

### R2 Development manifest identity and migration

Styled CML development evidence MUST use schema
`cncf.car-development-runtime-manifest.v2` and source kind
`development-directory`. Its descriptor evidence is
`target/cncf.d/component-descriptor.json`. The canonical development route
requires v2; a v1 migration manifest is rejected with explicit recovery rather
than adapted into the current descriptor/ABI contract.
A packaged manifest MUST NOT be accepted as development evidence.

### R3 Stable contract evidence

The manifest MUST match the component descriptor and ABI export evidence,
reproduce a supported CNCF runtime range and select the three declared evidence
paths. Each selected file must exist, be nonempty and readable. Descriptor/ABI
and classpath data are consumed as current inputs; no SHA-256, logical digest,
evidence digest, saved-byte comparison or transit-immutability proof is required.
The development descriptor MUST be schema version `3` with canonical component
namespace/id/version, matching ABI format v2/export identity/ABI version1.

### R4 Mutable output exclusion

Mutable compiled classes and unrelated generated files are not recursively
inventoried. Only the declared evidence paths and referenced classpath entries
are admission inputs; unrelated files are left outside this boundary.

### R5 Stale classpath rejection

Every non-empty classpath entry referenced by the prepared classpath file MUST
exist and be readable when CNCF admits it. A deleted or
invalid entry is stale development evidence and MUST be rejected before
classloading.

### R6 Fail-closed recovery

Missing, empty, malformed, contradictory, or stale development evidence MUST
fail before component discovery or classloading, identify the failing path,
and direct the developer to prepare through the cncf launcher. A development-source
failure MUST NOT fall back to a packaged CAR.

### R7 Structured admission failure

The public development-admission boundary and its component-development
repository propagation MUST preserve a structured `Consequence` failure,
including the resource-invalid classification, failed evidence path, and
recovery operation.

### R8 Multi-directory development assembly

`textus.repository.component.dev.dir` MAY contain a comma-separated set of
prepared development directories. When that set claims every component in the
selected subsystem descriptor, CNCF MUST assemble from that set and collect
its complete assembly API metadata before considering a default CAR
repository. It MUST NOT install a packaged component alongside its selected
development counterpart.

### Examples

- E1: mutable class recompilation preserves development admission (R3, R4).
- E2: missing manifest rejects the pair with structured recovery (R1, R6, R7).
- E3: a packaged manifest schema is not a development manifest (R2, R6).
- E4: manifest and descriptor/ABI coordinate contradictions are rejected (R3, R6).
- E5: an incompatible runtime range is rejected (R3, R6).
- E6: missing classpath evidence is rejected (R1, R6).
- E7: a deleted classpath entry is rejected as stale evidence (R3, R5, R6).
- E8: an invalid classpath entry is normalized into structured recovery (R5, R6, R7).
- E9: a descriptor changed after preparation is rejected as stale contract evidence (R3, R6).
- E10: a schema-v2 descriptor is rejected before development activation (R3, R6).
- E11: a v1 development manifest is rejected without a packaged fallback (R2, R3).
- E17: current path-only producer evidence admits readable classpath presentation changes without a seal (R3, R5).
- E18: independently consistent current descriptor metadata requires no saved-byte comparison (R3, R6).
- E19: unrelated generated files are excluded from runtime admission (R4).
- E20: empty interpreted classpaths and NUL-bearing entries are rejected as malformed input without integrity controls (R5, R6).

The CNCF Information CML build resolves exact Cozy/CNCF versions, the output
of the runtime-descriptor task, owning project directory and Information CML
source. Blank coordinates, missing descriptor/project/source and source escape
fail before launch. The five-field selected input value retains exact Cozy/CNCF
versions, the runtime descriptor path, actual source path and normalized
project-relative source identity.
The generation command carries `--runtime`, `--cncf-version`,
`--cozy-generator-version`, `--cncf-runtime-descriptor` and
`--generation-source-identity`; it consults no ambient version source.

Cozy's descriptor contract is selected only by `--cncf-runtime-descriptor` and
requires the exact target/path. `requireValidDescriptor(path, targetVersion,
source)` and `validateDescriptor(path, targetVersion, source)` read once into
`ValidatedDescriptor(path, targetVersion, config)`. They reject missing,
unreadable or malformed descriptor input and preserve root schema `1`, runtime
`cncf`, exact target and predefined Result schema `cncf.predefined-result.v1`.
CLI/bridge share typed deterministic diagnostics and target/path source
agreement; global `~/.cozy` defaults are excluded. Version-only commands remain
outside this selected contract. The parsed config supplies Result/style
semantics for the current invocation; a later new read consumes current input.
No descriptor SHA API, CLI option, field or syntax/mismatch diagnostic remains.

For delegated generation, sbt-cozy uses `Compile / dependencyClasspath`, reads
fixed `META-INF/cncf/runtime.yaml` from each distinct JAR or dependency class
directory, and writes the exact selected resource bytes. The public extraction
APIs take `classpath: Seq[File]` and return `Option[File]` or required `File`.
Missing required descriptors, multiple distinct owners (including equal-byte
owners) and ordinary resource/output I/O failures remain failures. Only the
selected dependency entries participate; there is no ambient filesystem scan.
The bridge carries `runtime.cncf.descriptor` and exact target versions, without
a descriptor SHA setting or option.

The optional ComponentStyle carrier keeps
`cncf.component-style-catalog-carrier.v1` with exactly `schemaVersion`,
`resource`, `encoding`, and `bytes`. The resource is
`META-INF/cncf/component-style-catalog.json`; base64 bytes encode the actual
packaged JSON catalog. The strict reader retains schema/resource/encoding,
payload/JSON shape, API/kind/provider, canonical ASCII identity and signed-Int
major, closed empty parameters, bundle closure/cycle/conflict and qualification
checks. The producer and reader remove descriptor/payload SHA together without
schema migration, a legacy adapter or replacement protection.

Current Cozy generation metadata at `target/cozy/generation-provenance.json`
is the nonhash v1 projection of exact CNCF/Cozy/backend/model versions, canonical
project-relative CML identity and sorted nonempty generated Scala paths. The v2
aggregate additionally records its sorted unique source/path union. Neither
schema emits descriptor/source/output/aggregate/evidence digests. Known fields,
exact versions, canonical paths, actual readable source and output inventory
remain validated; arbitrary historical extras are ignored and not re-emitted.
Generation and CML metadata read the actual selected source path. Direct final
metadata writing follows initial stage validation and provides no atomic,
rollback or prior-output protection promise after I/O starts.

The actual metadata CLI uses the selected model path, output root, exact target,
generator and source identity, plus optional model version. It derives the
compiled backend/default model versions and validates known inputs, source
access and output paths. It consumes no descriptor path or digest. CNCF's build
passes the current source/output paths, exact versions and source identity to
this validator after ordinary Information model generation and required
nonempty sorted Scala output selection. The selected build inputs contain only
cozyGeneratorVersion, cncfTargetVersion, runtimeDescriptor, source and
sourceIdentity. No pre-launch source-byte comparison or dedicated generated
Scala/provenance SHA snapshot task or test hook remains. Pure invocation/value
determinism and nonhash metadata reproducibility retain their meanings.
S42 production Information generation/current metadata validation passed in the
actual two-suite/eight-test run with 34 generated Scala paths. S43 primary
producer/reader is implemented-focused-validated-unreviewed after actual
Cozy/CNCF focused validation and native canonical repository discovery. E715-03 residual
fixtures, E715-04 composition, E715-05 provider and warning dispositions remain
OPEN, without exceptions or Step/Phase acceptance.

## Current Information Generation Additions

The normal build pins Cozy `0.3.3-SNAPSHOT`. A content edit is consumed through
the selected CML source path; deleting the source prevents command preparation.

### Optional explicit generator project

The build passes `cncf.cozyGeneratorRoot` and `CNCF_COZY_GENERATOR_ROOT` to
`selectedGeneratorRoot`. An explicitly present property takes precedence over
the environment. With neither present, selection is `None` and the exact
artifact invocation remains the default. A selected value is trimmed,
canonicalized, and must name a directory containing a regular `build.sbt`.
Blank, missing, non-directory, missing-build, or uncanonicalizable selections
fail with the selected path and deterministic recovery guidance. An invalid
property never falls through to the environment or artifact invocation.

The build resolves this selection once before deleting or creating generation
output, and uses the same selected root for generation and metadata validation.
For a selected root, both commands insert
`--runtime-dev-dir <canonical-root>` immediately after
`--runtime <unchanged-exact-Cozy-version>`. The supported launcher owns existing
classpath, exact version, readiness validation, and direct Java execution.
This selection does not discover a root, refresh a generator, invoke nested
SBT, or change the declared generator version.

Executable specifications:

- [ComponentStyleRuntimeDescriptorProjectionSpec](../../src/test/scala/org/goldenport/cncf/component/ComponentStyleRuntimeDescriptorProjectionSpec.scala)
  decodes the actual generated resource and its four-field catalog carrier.
- [Phase51Cv03BuildIntegrationSpec](../../src/test/scala/org/goldenport/cncf/phase51/build/Phase51Cv03BuildIntegrationSpec.scala)
  specifies normalized inputs, invocation/failure behavior, and optional root selection.
- [Phase51Cv05BuildProvenanceSpec](../../src/test/scala/org/goldenport/cncf/phase51/build/Phase51Cv05BuildProvenanceSpec.scala)
  specifies notice severity, current generation/validation arguments, containment,
  and consumption of the current edited source.

### Historical build-prerequisite handoff — 2026-10-14

This current-generation compatibility alignment is a necessary CAP-78-05 and
CBD CAP-11-01 prerequisite. These behavior specifications do not establish
Step or Phase acceptance. Dedicated Cozy Capability production and actual CNCF
native Directory/CAR discovery followed by CBD consumption remain required.
The independent packaged/development runtime-manifest, ABI, and activation
contracts above retain their own subsequent synchronization and proof gates.

Current own Step acceptance — 2026-10-15: actual dedicated Cozy production,
canonical CNCF public ComponentFactory native Directory/CAR discovery and
source-free CBD consumption, focused validation and independent protected own
Step conformance are now accepted in
[CAP-78-05 acceptance](../phase/phase-78.md#cap-78-05-acceptance--2026-10-15).
Behavior specifications alone do not establish whole-Phase closure. Independent
packaged/development runtime-manifest, ABI and activation contract requirements
remain unchanged, with their separate synchronization and proof gates still owed.

### Packaged Compile source-project exports

The root enables `Compile / exportJars`; standard SBT `packageBin` and attributed
`exportedProducts` supply its single main JAR through native source-project
dependencies. Source-linked CBD root and output-exclusion fixture use that JAR
for normal generation and CAR inputs. Test source fixtures and transitive
dependencies retain their native Compile/Test mapping. Strict runtime descriptor
resolution, ABI, and declared versions remain unchanged. Packaged/development
runtime-manifest synchronization and its separate proof gates remain owed;
this source export establishes no Step or Phase closure.

## Closure Validation Requirements

Phase closure MUST execute the following ownership-preserving evidence:

- CNCF ordinary production Information generation and current metadata validation through `build.sbt`;
- Cozy exact-pair, descriptor, package, provenance, runtime-manifest, scaffold,
  review-evidence, and publication specifications;
- sbt-cozy scripted CAR publication and review-evidence projects;
- representative generated CAR compilation, packaging, development
  publication, CAR lint, and focused runtime behavior; and
- final full accumulated suites after independent review is clean.

The representative CAR's exact CNCF compile coordinate MUST be included in
its non-empty `packaging.car.runtime.cncf.tested` evidence. An unsupported
CNCF-Cozy generation pair MUST fail before generated source compilation or
packaging. An unsupported CNCF runtime MUST fail packaged-CAR activation
without interpreting Cozy provenance. A compatible packaged-CAR admission
MUST be executable without Cozy classes.

Missing `car-runtime-manifest.json` is not a legacy compatibility fallback.
An independently released pre-manifest CAR MUST remain rejected until its
owner publishes a newly evidenced artifact. Validation MUST NOT rewrite an
immutable dependency archive, synthesize compatibility from its version, or
weaken CNCF admission to make an older downstream assembly pass.

## 2026-10-09 S41 initial implementation handoff (historical)

**P715-02-S41-DESCRIPTOR-HANDOFF** is implemented-unvalidated-unreviewed under
the [frozen S41 Implementation Manifest](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s41-implementation-manifest.json).
The parent owns actual focused validation, independent review and acceptance.
The prior S40 source-connected failure remains recorded with zero Textus
suites/tests; planned normal Cozy/plugin/CNCF SNAPSHOT publishLocal prerequisites
lead to Textus's unchanged default binary dependencies and establish no
source-bound integration success. E715-01 connected validation/review,
E715-03..05 and source/output/snapshot removal remain OPEN. Dated decisions and
validation remain history; this handoff closes no Step or Phase.

## 2026-10-09 S42 initial information generation handoff (historical)

**P715-02-S42-INFORMATION-GENERATION-DIRECT / R715-09 / E715-02 generation**
is **implemented-unvalidated-unreviewed** under the
[frozen S42 Implementation Manifest](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s42-implementation-manifest.json).
The five selected inputs and real generation/metadata validation remain; source
SHA/drift and the dedicated output/provenance snapshot model/task/test hook are
removed. Existing CV03 retains five cases and normalization PBT; CV05 retains
three cases, including current invocation and outside-project rejection.
Parent-selected production generation and both existing suites have not run
for S42. Independent review and Step/Phase acceptance remain OPEN. S41 and
prior refusal/validation history retain their attribution; remaining archive/
runtime E715-03, composition E715-04 and provider E715-05 remain OPEN.

## 2026-10-09 S43 approved CNCF runtime initial implementation handoff (historical)

The CNCF portion of **P715-02-S43-CAR-RUNTIME-DIRECT / R715-09 / E715-03**
is **implemented-unvalidated-unreviewed** under the
[frozen five-path remainder Implementation Manifest](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s43-approved-cncF-remainder-implementation-manifest.json).
The actual owning human explicitly approved removal of CNCF CAR SHA inventory,
full-file-set comparison and digest comparison for these five files while
retaining coordinates, runtime range, ABI, required files, safe paths and I/O
validation. The [informed approval](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s43-informed-cncF-human-approval.json)
supplied the recovery condition for one renewed scoped permission submission.
The [prior platform denial](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s43-cncF-platform-denial.json)
and raw rejected request/response remain history; Textus-specific approval was
not presented as CNCF approval. This submission applied the exact five edits.

The primary reader now consumes unchanged v1 schema/car/runtime range and
canonical ABI metadata without archive SHA inventory/file-set/digest checks.
Existing extraction owns required main JAR, actual ZIP safe-path/duplicate and
I/O behavior. CNCF never evaluates Cozy provenance or loads Cozy. The existing
admission suite retains ten cases and range PBT after deleting its single
protection-only tamper case. The common fixture retains public write and actual
ZIP construction, canonical descriptor/ABI behavior and caller-supplied runtime
manifest handling. No new suite, case or replacement inventory was introduced.

Cozy's [actual accepted two-suite/28-test result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s43-validation-1-result.json)
remains dated own-root native package/publication/provenance evidence. CNCF's
parent-selected existing admission suite and native repository canonical packed/
expanded discovery case have not run for S43. Parent owns actual validation and
independent review; no connected validation, Step or Phase acceptance is claimed.

Whole E715-03 remains **OPEN**: ComponentRepositoryCarSpec
`_write_packaged_runtime_evidence` / `_write_runtime_classpath` and
ComponentInitializationBootstrapSpec's development writer still emit protection
fixtures. CozyCarRuntimeManifestSpec `_consumer_contract` carrier resource
metadata remains original E715-01 work. Composition E715-04, provider E715-05,
warning disposition, independent Step/full-Phase review, exact local commits
and distinct release remain **OPEN**. Dated S40/S41/S42 initial records remain
history. S41 actual 21 suites/181 tests and S42 actual two suites/eight tests
with production generation/current metadata for 34 Scala paths retain their
attribution. Original Goal/authority/binding/bases/epoch/ledger 5 and repair
14/streak 0 remain unchanged. S36 native/movie/hearing/end-card/EOF evidence is
reused; full/broader validation remains deferred-not-run to PHASE-71.7.

## S43 approved primary runtime actual focused validation — 2026-10-09

The primary producer/reader portion of **P715-02-S43-CAR-RUNTIME-DIRECT /
R715-09 / E715-03** is **implemented-focused-validated-unreviewed**. This
parent-admitted actual result supplements the preserved dated denied, partial
and unvalidated handoffs. The exact informed CNCF approval enabled the single
renewed scoped five-file implementation; the earlier raw request/refusal remains
history and Textus-specific approval was not substituted for CNCF approval.

The [accepted Cozy result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s43-validation-1-result.json) records invocation
`a7703154-ac40-456c-bf5f-ef7d473fa578`, executor session **78070**,
`CozyCarRuntimeManifestSpec` **17 tests** and `CozyArchivePackagerCv06Spec`
**11 tests**: **2 suites / 28 passed tests**. The [accepted CNCF result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s43-validation-2-result.json)
records invocation `dec6e72e-acc1-4cee-b0b6-d0fa0ab41db4`, executor session
**51720** with terminal tool exit **0**, `CarRuntimeAdmissionSpec` **10 tests**
and the actual existing `ComponentRepositoryCarSpec` canonical packed/expanded
repository case **1 test**: **2 suites / 11 passed tests**. That selected native
case actually ran through discovery, factory loading and materialization for
packed and expanded CARs. Both invocations ended with **SBT 0 / wrapper 0 /
serial lock released**. Combined evidence is **4 completed suites / 39 distinct
passed tests**, with failed, aborted, canceled, ignored and pending counts all
**zero**, and no warnings or errors in either invocation.

These are two own-root focused validations, including existing native Cozy
package/publication/provenance behavior and the actual CNCF repository case.
They do not establish broad end-to-end or full integration validation. The
primary contract retains unchanged v1 schema/car/runtime range, canonical ABI
format v2/export/ABI version1, actual required files/main JAR, ZIP safe paths/
duplicates and JSON/I/O failures. Cozy required packaged provenance and selected
project inputs retain their meanings; CNCF never parses provenance or loads Cozy.
No new case, suite, inventory certificate or replacement protection was added.

Whole **E715-03 remains OPEN** for original repository/bootstrap fixture
emissions: `ComponentRepositoryCarSpec` `_write_packaged_runtime_evidence` /
`_write_runtime_classpath` and `ComponentInitializationBootstrapSpec`'s
development writer. Original E715-01 carrier resource metadata in
`CozyCarRuntimeManifestSpec` `_consumer_contract`, E715-04 composition, E715-05
provider and prior unsuppressed plugin/task/cycle/logging/deprecation warning
classification/repair remain **OPEN**. Original independent whole-Step/full-Phase
reviews, acceptance, exact local commits and distinct release remain **OPEN**.

S40 denials/source-bound failure, S41 actual 21 suites/181 tests and S42 actual
two suites/eight tests with production generation/current metadata for 34 Scala
paths retain their attribution. Original Goal/authority/binding/bases/epoch,
review ledger **5**, repair **14 / streak 0**, pending M2 cycles
**6/7/8/10/11/13**, all seven Stage Status/Owner/Update triples and closure
checkboxes retain their state. S36 unchanged native/movie/smooth hearing/end-card/
EOF evidence is reused. Repository-full SBT and broader integration remain
**deferred-not-run to PHASE-71.7**. This documentation editor ran no tests,
self-review, source edit, Git mutation or workflow transition. No Step, Phase
or all-removal acceptance is claimed.

## S44 runtime fixture paths implementation handoff — 2026-10-09

The existing repository/bootstrap fixture portion of
**P715-02-S44-RUNTIME-FIXTURE-PATHS / R715-09 / E715-03** is
**implemented-unvalidated-unreviewed** under the
[frozen S44 Implementation Manifest](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s44-implementation-manifest.json).
This finite immediate-caller cleanup follows the already-admitted production
reader contracts under the original Phase invocation and the actual direction
「外部ライブラリも修正して」. S43's informed CNCF five-file production approval,
successful implementation, original scoped refusal and raw audit remain history;
that exact five-file permission is not extended to additional production checks.
S44 changes private test-fixture output and documentation only; no production
source, security check, public API, ABI or schema is changed.

`ComponentRepositoryCarSpec` `_write_runtime_classpath` and
`ComponentInitializationBootstrapSpec` `_write_parameter_repository_fixture`
emit unchanged v2/sourceKind/canonical CAR/runtime metadata with exactly this
ordered path-only development evidence:

```json
[{"path":"target/cncf.d/runtime-classpath.txt"},{"path":"target/cncf.d/component-descriptor.json"},{"path":"src/main/car/abi-manifest.json"}]
```

`ComponentRepositoryCarSpec` `_write_packaged_runtime_evidence` retains v1
canonical CAR coordinate/runtime metadata and actual canonical ABI, without
the obsolete recursive full-file inventory and integrity entries. Per-entry
SHA/logical SHA, aggregate evidence SHA/integrity, solely-used digest helpers,
their import and logical-classpath digest local are removed from these fixtures.
The two Scala version headers record Oct. 9 and retain prior history. Actual
classpath, descriptor copies, artifact alias/release, ABI exports, JAR/ZIP
construction, factory loading, repository materialization and temporary
repository lifecycle remain. The existing **80 repository / 18 bootstrap**
scenarios, Given/When/Then boundaries and assertions are unchanged, including
canonical packed/expanded discovery and independent E715-05 ABI-hash assertions.

The parent-selected complete `ComponentRepositoryCarSpec` and
`ComponentInitializationBootstrapSpec` focused invocation has **not run for
S44**. Parent owns diff verification, actual validation and independent review.
S43's actual own-root Cozy 28 / CNCF 11 results (four suites / 39 distinct
passed tests, zero failures/aborts/canceled/ignored/pending, terminal SBT 0 /
wrapper 0 / released locks) retain their attribution; they are not S44 results.
Whole **E715-03 remains OPEN** pending selected fixture validation and original
independent assurance. No Step, Phase or all-removal acceptance is claimed.

### E715-01-resource-consumer-subordinate — OPEN

The original producer fixture is
`/Users/asami/src/dev2025/cozy/src/test/scala/cozy/archive/CozyCarRuntimeManifestSpec.scala::_consumer_contract`.
Its required fields remain `resources[].sha256`, `resources[].integrity` and
`resources[].provenance.matchingDigest`. The concrete consumer model is
`/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifestConsumerContract.scala`;
its codec is
`/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifestConsumerContractCodec.scala`.
The codec `_resource` requires sha256/integrity, `_provenance` requires
matchingDigest, and the model fields are nonoptional. Actual Textus projection
and serialization callers are
`/Users/asami/src/dev2026/textus-cbd-support/src/main/scala/org/simplemodeling/textus/cbdsupport/runtime/ComponentKnowledgeProjection.scala`
and
`/Users/asami/src/dev2026/textus-cbd-support/src/main/scala/org/simplemodeling/textus/cbdsupport/impl/ComponentFactory.scala`.
This concrete existing fixture invokes that shared consumer shape. Cozy carrier
resource fields cannot be removed alone or reported compatible. Parent must
freeze a coordinated consumer-model/codec/indexed-production-caller and existing
specification unit under the same finite E715-01 authority. This remains a
**current original Phase obligation**, not Hygiene or a deferred Development
Candidate; S44 does not implement that dependency edge.

Whole E715-01, E715-03, E715-04 composition, E715-05 provider, prior unsuppressed
plugin/task/cycle/logging/deprecation warning disposition, original independent
whole-Step/full-Phase review, acceptance, exact local commits and distinct
release remain **OPEN**. All Stage Status/Owner/Update triples and closure
checkboxes retain their state. Original Goal/authority/binding/bases/epoch,
review ledger **5**, repair **14 / streak 0**, pending M2 cycles
**6/7/8/10/11/13**, S40 denials/source-bound failure, S41's 21 suites/181 tests,
S42's two suites/eight tests and production metadata for 34 Scala paths remain
unchanged history. S36 native/movie/smooth-hearing/end-card/EOF evidence is
reused. Repository-full SBT and broader integration remain
**deferred-not-run to PHASE-71.7**. This editor ran no tests, self-review, Git
mutation, publication, runtime replay or workflow transition.

## S44 runtime fixture actual focused validation — 2026-10-09

The fixture portion of **P715-02-S44-RUNTIME-FIXTURE-PATHS / R715-09 /
E715-03** is **implemented-focused-validated-unreviewed**. The
[parent-admitted actual result](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s44-validation-result.json)
records serial invocation `acfa106f-ca4d-4199-be63-f69c9e8036d4`, actual executor
session **24224**, and the complete existing `ComponentRepositoryCarSpec`
and `ComponentInitializationBootstrapSpec`: **2 completed suites / 98 passed
tests**. Failed, aborted, canceled, ignored and pending counts are all **zero**;
no warnings or errors were observed. Terminal evidence is **SBT 0 / wrapper 0 /
serial lock released**. The actual existing canonical packed/expanded repository
discovery, factory loading and materialization, and development descriptor/typed
parameter bootstrap scenarios passed. This is own-root affected-suite evidence.

The dated S44 implemented-unvalidated-unreviewed handoff remains initial
history. S43's prior four-suite/39-test evidence retains separate attribution;
it is not summed with this run as 137 distinct tests. Current fixture output
retains the three ordered development paths and canonical packaged v1
coordinate/runtime/ABI metadata, actual classpath/descriptor/JAR/ZIP/lifecycle,
and all 80 repository / 18 bootstrap scenarios and assertions.

Whole **E715-03 remains OPEN** until complete original assurance. The exact
**E715-01-resource-consumer-subordinate** producer/model/codec/Textus caller
edge recorded above remains **OPEN**: required `resources[].sha256`,
`resources[].integrity` and `resources[].provenance.matchingDigest` cannot be
removed from the Cozy fixture alone. E715-04 composition, E715-05 provider,
prior warning disposition, original complete Step/full-Phase independent
reviews, exact local commits and distinct release remain **OPEN**. No Step,
Phase or all-removal acceptance is claimed.

All seven Stage Status/Owner/Update triples, closure checkboxes, original
Goal/authority/base/binding/epoch, review ledger **5**, repair **14 / streak 0**,
pending M2 cycles **6/7/8/10/11/13**, refusals and earlier histories retain their
state. S36 unchanged actual native movie/hearing/end-card/EOF evidence is reused;
repository-full SBT and broader integration remain **deferred-not-run to
PHASE-71.7**. This documentation editor ran no tests, self-review, Git mutation,
publication, runtime replay or workflow transition.

## S45 authored knowledge payload purpose classification — 2026-10-09

**P715-02-S45-KNOWLEDGE-PAYLOAD-PURPOSE** is **parent-classified-unreviewed**
under the [frozen S45 revision-2 Implementation Manifest](/Users/asami/src/dev2025/cozy/.codex-workflow/phases/PHASE-71.5/p715-02-s45-implementation-manifest-r2.json).
The actual original Phase invocation and direction 「外部ライブラリも修正して」
authorize this six-document correction. S43's exact informed five-file removal
approval and refusal remain history and supply no expanded removal permission.

The inspected Cozy boundary is
`src/main/scala/cozy/archive/CozyComponentKnowledgeCarrier.scala`:
`fromProject` selects regular-file/readable JSON and `_require_consumer_contract`
checks root schema/component/release; `copyTo` transports the caller-authored
JSON; `requireDeclaredArchiveCarrier` checks the exact three-field declaration
and required entry presence. Cozy generates only that declaration. These
operations do not compute, emit or compare resource SHA, integrity or
matchingDigest fields. In `src/test/scala/cozy/archive/CozyCarRuntimeManifestSpec.scala`,
`_consumer_contract` has one call in the existing development/package carrier
projection scenario. It supplies authored content and tests selected JSON/copy
transport; it does not invoke the CNCF consumer codec or its resource validation.

The resource fields `resources[].sha256`, `resources[].integrity` and
`resources[].provenance.matchingDigest` belong to the existing CNCF knowledge
resource-domain model/codec `knowledge/ComponentKnowledgeManifestConsumerContract.scala`
and `knowledge/ComponentKnowledgeManifestConsumerContractCodec.scala`. Their
meaning links caller-supplied knowledge evidence to independently resolved
resources; `ComponentKnowledgeManifest`, `PortableModelResourceContext`,
`ComponentKnowledgeHelpContract`, `ComponentAdminDocumentationNavigationProjection`
and `ComponentAdminSurfaceSecurity` retain existing model/directive/catalog
references and Help/Admin domain state. This is genuine caller-authored knowledge
content under the original artifact-protection-removal purpose/caller rule and
R715-11 retained content. Required parser fields alone do not establish that
classification. No new protection or protection exception is introduced.

The current `E715-01-resource-consumer-subordinate` disposition is
**retained-authored-knowledge-content-classification-pending-independent-review**.
Its S43/S44 OPEN removal-prerequisite interpretation is explicitly superseded;
those dated hypotheses and actual source/refusal/test facts remain unaltered.
Selected product removal is not required for this authored payload. This does
not establish external consumer success or acceptance, change a schema, rename
the issue into Hygiene/Development Candidate, or waive original assurance.

E715-01 independent connected acceptance, whole E715-03, E715-04 composition,
E715-05 provider, prior warning disposition, original complete Step/full-Phase
independent reviews, pending M2 **6/7/8/10/11/13**, exact local commits and
distinct release remain **OPEN**. Original R715-01..15/E715-01..05, full Goal,
authority/base/binding/epoch, ledger **5**, repair **14 / streak 0**, all seven
Stage Status/Owner/Update triples and closure checkboxes retain their state.
S44's two-suite/98-test and S43's four-suite/39-test results keep separate
attribution. S40's actual source-bound failure and S41's default-binary success
retain their distinct scopes. S36 unchanged movie/hearing/end-card/EOF evidence
is reused; full/broader validation remains **deferred-not-run to PHASE-71.7**.
Original independent whole-Step/full-Phase review must assess this purpose
classification before acceptance. This editor changed no payload, source,
fixture, schema, API, scenario, assertion, status checkbox or control and ran no
tests, self-review, Git mutation, runtime, publication or workflow transition.
