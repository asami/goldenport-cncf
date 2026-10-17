# CNCF-Cozy Generation Compatibility Authority

Cozy owns the generation-boundary compatibility contract. CNCF supplies an
exact target coordinate to compile generated Scala; Cozy supplies the exact
generator coordinate. CAR runtime compatibility is a separate range owned by
CAR metadata and runtime activation. Cozy is not a runtime dependency.

The canonical typed contract and admission semantics are implemented in
`cozy.compatibility.GenerationCompatibility` in the Cozy SNAPSHOT repository.
Evidence uses the machine-readable `cozy.generation-compatibility.v1` shape
with exact structured coordinates, a `proven`, `unproven`, or `incompatible`
pair status, and owner/location. The observed `0.5.1`/`0.3.0` pair is currently
recorded as unproven, not admitted. Cozy validates the packaged resource and
returns typed diagnostics for malformed evidence before admission.

Sources are ordered project contract, owning-build bridge, CLI, and published
default. Explicit contradictions reject deterministically; environment and
stale fallbacks are not release-generation sources. Development admits explicit
SNAPSHOT coordinates, while release generation requires immutable coordinates.
CV-02 owns pure generation compatibility admission: missing coordinates,
unsupported coordinates, unproven or incompatible pairs, source contradiction,
and development/release lifecycle admission. CV-03 owns resolving and wiring
explicit inputs at the actual generation invocation, including rejecting absent
or contradictory invocation sources. CV-04 owns runtime descriptor
path/target/schema and catalog validation. CV-05 owns metadata and required
source/output identity and access validation.

CV-06A adds a CAR package-gate authority boundary. The unmerged
`project.yaml` metadata owns `build.cozyVersion`, the unique CNCF compile
coordinate under `build.dependencies.compile`, and
`packaging.car.runtime.cncf`. Cozy may continue to use merged operational
configuration for unrelated packaging behavior, but global or operation
defaults cannot override these project-owned compatibility fields for a CAR.

`cozy.compatibility.CarMetadataCompatibility` parses this contract and returns
typed deterministic diagnostics. `CozyArchivePackager` resolves CNCF evidence
from the actual main and library input JARs, applies the project contract before
building the archive, and reuses the same version ordering for runtime
admission. This keeps the generator coordinate, compile target, runtime range,
and resolved artifact as distinct facts while requiring them to form one
coherent package decision. Strict admission accepts exactly one JAR descriptor
whose runtime is `cncf` and whose module organization, artifact, and version
identify the selected compile dependency. Once that evidence is accepted, the
same exact version drives CAR runtime-range admission; merged operation
defaults are not consulted for a second compatibility decision.

CV-06B reuses the project-only portion of this decision in integrated CAR lint,
the Review Provider, and publication. Review preserves the package diagnostic
codes and reports the accepted generator, compile coordinate, and runtime
range. Publication evaluates the decision before repository writes and
projects catalog runtime metadata from the accepted contract. Only packaging
adds resolved-JAR identity evidence.

CV-06C1 validates known exact versions, source identity/readability and actual
generated Scala paths for selected `target/cozy/generation-provenance.json`,
then packages that actual path directly as top-level `generation-provenance.json`.
Direct v1 and aggregate v2 are supported. Metadata remains build-time data;
nongenerated/legacy CAR sources may omit it, and generic `src/main/car` cannot
provide the reserved entry. No unchanged-transit snapshot is required.

sbt-cozy installs managed Scala and asks Cozy to validate the selected delegated
v1 pairs and write the final v2 source/path aggregate. Disposable delegate work
is excluded from installed output. Versions, canonical identities, actual access,
unique claims and complete path union remain required. Delegate work is removed
after bridge success, and incremental generation uses the final metadata.

CV-06C2 separately owns runtime activation. CNCF admits the CAR from its
canonical coordinate, supported ABI and runtime range without loading Cozy or
using generation compatibility/provenance as a runtime dependency.

Cozy writes top-level `car-runtime-manifest.json` with unchanged schema
`cncf.car-runtime-manifest.v1` after staging the selected package. It records
the canonical CAR artifact name/version/component and accepted CNCF minimum,
optional maximum, excluded and tested versions. The reserved runtime manifest
cannot originate from `src/main/car`.

For a packaged or expanded canonical CAR, CNCF validates the supported runtime
schema and descriptor/release coordinate, admits its executing version against
minimum/maximum/excluded, and requires ABI format `cozy.car.abi-manifest.v2`,
matching canonical component namespace/id/version, ABI version `1` and an
export of the component before discovery or classloading. Tested versions are
nonempty evidence rather than an allowlist. Existing extraction owns actual
main JAR and ZIP containment/duplicate rules. Required JSON regular-file, read,
format and semantic failures remain meaningful. Provenance is opaque to CNCF;
Cozy validates required packaged provenance and selected project inputs before
repository writes and publishes the current selected archive directly.

Development-directory activation remains a separate runtime admission route.
Cozy and sbt-cozy prepare `target/cncf.d/runtime-classpath.txt` with
`target/cncf.d/car-runtime-manifest.json`, using schema
`cncf.car-development-runtime-manifest.v2` and source kind
`development-directory`. The manifest records canonical CAR coordinate and
runtime range and selects descriptor, ABI and classpath evidence paths.
Selected files and classpath entries must be present, nonempty where required
and readable. Current canonical descriptor/ABI semantics are checked directly;
compiled classes and unrelated generated files are not recursively inventoried.
CNCF admits this pair before classloading, identifies failed evidence paths with
cncf-launcher preparation guidance, and never substitutes a packaged CAR.

For CNCF-owned CML generation, `build.sbt` remains the invocation authority. It
resolves exact Cozy/CNCF coordinates, the explicit runtime-descriptor task
output, project and CML source. The cross-compiled build contract normalizes
paths and retains required inputs and source containment. Its five-field value
contains exact Cozy/CNCF versions, runtimeDescriptor, source and sourceIdentity.
The generation command passes selected descriptor path and target; metadata
validation consumes actual source/output paths, versions and source identity.
Normal model generation, required nonempty sorted Scala output and process/I/O
failures remain; no source drift comparison or protection-only snapshot task
is required.

One Cozy descriptor read supplies `ValidatedDescriptor(path, targetVersion,
config)` for schema/runtime/exact-target/Result validation and semantic catalog
consumers. Both validation APIs take path, targetVersion and source.
`--cncf-runtime-descriptor` selects this contract; exact target/path are required,
while version-only commands remain outside it. Missing, unreadable, malformed
and contradictory target/path sources remain typed deterministic failures.
Descriptor SHA API/CLI/settings and pin checks are removed without replacement.

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


**Current S45 purpose classification (2026-10-09): parent-classified-unreviewed.**
`E715-01-resource-consumer-subordinate` is retained caller-authored knowledge
content under the original purpose-based policy and R715-11 genuine-content
boundary; removing its resource-content fields is not selected Cozy
artifact-protection work. This current interpretation supersedes the dated
S43/S44 OPEN removal-prerequisite hypothesis, preserved below as history.
E715-01 independent connected acceptance, whole E715-03 assurance, E715-04/05
product removals, prior warnings and original Step/full-Phase review, commits
and distinct release remain OPEN. See the dated S45 classification below.


## Current CNCF Information Invocation Additions

The normal build pins Cozy `0.3.3-SNAPSHOT`. A later CML content edit is read
through the selected path; a removed source fails command preparation.

### Explicit normal generator selection

`selectedGeneratorRoot(propertyValue, environmentValue)` is an ambient-free
helper. `build.sbt` supplies `cncf.cozyGeneratorRoot` and
`CNCF_COZY_GENERATOR_ROOT`. A present property wins over the environment,
including an invalid or blank property. With neither present, `None` retains
the exact artifact invocation. Selected values are trimmed and canonicalized,
and must identify a directory with a regular `build.sbt`; failures identify
that selected path and give deterministic recovery. They do not trigger a
fallback or root search.

`runInformationCmlGeneration` selects once before output deletion/creation and
passes the same `Option[File]` to `command` and `validationCommand`. Each
starts with `cozy --runtime <unchanged-exact-version>` and appends
`--runtime-dev-dir <canonical-root>` only for `Some`. Their current generation
and validation selectors retain their order and meaning. The supported
launcher owns prepared classpath, version and readiness checks and direct Java
execution. CNCF does not refresh that runtime, start nested SBT, silently
select canonical main, or substitute another version.

The behavioral contract is linked by
[ComponentStyleRuntimeDescriptorProjectionSpec](../../src/test/scala/org/goldenport/cncf/component/ComponentStyleRuntimeDescriptorProjectionSpec.scala),
[Phase51Cv03BuildIntegrationSpec](../../src/test/scala/org/goldenport/cncf/phase51/build/Phase51Cv03BuildIntegrationSpec.scala),
and [Phase51Cv05BuildProvenanceSpec](../../src/test/scala/org/goldenport/cncf/phase51/build/Phase51Cv05BuildProvenanceSpec.scala).
### Historical build-prerequisite handoff — 2026-10-14

This alignment is a necessary CAP-78-05/CBD CAP-11-01 build prerequisite. It
is not participant Step or Phase acceptance and does not replace the later
actual dedicated Capability producer/native Directory/CAR/CBD consumer proof.
Packaged/development runtime-manifest, ABI, and activation contracts retain
separate responsibility and require their own synchronization before that proof.

Current own Step acceptance — 2026-10-15: actual dedicated Cozy production,
canonical CNCF public ComponentFactory native Directory/CAR discovery and
source-free CBD consumption, focused validation and independent protected own
Step conformance are now accepted in
[CAP-78-05 acceptance](../phase/phase-78.md#cap-78-05-acceptance--2026-10-15).
Behavior specifications alone do not establish whole-Phase closure. Separate
Phase validation/review/release and packaged/development runtime-manifest, ABI
and activation synchronization and proof gates remain owed; their contracts
and responsibilities are unchanged.

## Downstream Validation Boundary

Closure validation follows the same ownership boundaries as production.
CNCF's build runs ordinary production Information generation and current
metadata validation. Cozy verifies
exact-pair admission, descriptor identity, package metadata, provenance,
runtime-manifest construction, review evidence, and publication. sbt-cozy
verifies the scaffold-to-build bridge, while a representative CAR verifies
generated-source compilation, packaging, local development publication, and
domain runtime behavior.

These gates are complementary. A focused fixture cannot replace the owning
production gate, and a downstream success cannot excuse an earlier typed
diagnostic. In particular, runtime activation evidence is established by CNCF
without loading Cozy, while generation and publication evidence remains
build-time data.

An archive created before `cncf.car-runtime-manifest.v1` is not silently
upgraded or admitted as compatible. Its owning project must create a new
artifact with current package evidence. Downstream validation may distinguish
that independently owned migration from the current CAR under test, but it
must record the rejection rather than bypassing the runtime admission gate.

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


### Retained explicit source checkout selection

`TEXTUS_COZY_CNCF_SOURCE_ROOT` remains an optional execution-location selector
through `cncfCozyDevelopmentDirectory`. Absence preserves ordinary execution;
a present value must be absolute, nonblank, canonical and contain `build.sbt`
and `src/main/scala/cozy/Cozy.scala`. It cannot equal or contain the consumer,
escape a lexical consumer path through a symlink, or be nested except as a
matching lexical/canonical direct `.codex-workflow/checkouts/<name>` child with
an ASCII legal leaf. Invalid explicit selection fails without fallback.

Main's `cncf.cozyGeneratorRoot` / `CNCF_COZY_GENERATOR_ROOT` selection has its
existing precedence. When absent, the admitted legacy source checkout supplies
the execution directory. Both generation and validation receive the same
`cozy --runtime <pinned-version> --runtime-dev-dir <canonical-directory>` prefix.
Directory selection changes neither exact generator/target coordinates nor
main's five-field path contract and adds no publication/bootstrap responsibility.
The existing `CncfCozyDevelopmentRuntimeSpec` E12-E22 exercises the retained
source admission and command vectors against the current path contract.
