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
path/schema/runtime/exact-target and predefined Result validation. CV-05 owns
current generation metadata and output-path inventory validation.

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

CV-06C1 packages an existing
`target/cozy/generation-provenance.json` as the top-level CAR entry
`generation-provenance.json`. Cozy reruns its authoritative source-readability,
declared-input, and project-relative generated-artifact inventory validation and
requires the recorded CNCF target and Cozy generator to match the accepted CAR
project contract before writing the archive. The packaged bytes remain
build-time metadata. Cozy owns parsing and validating the current generation
metadata and its path inventory before packaging.
Non-generated and legacy CAR sources may omit them. The generic
`src/main/car` path cannot provide this reserved entry.

sbt-cozy installs generated Scala into the owning project's managed-source
target and delegates final evidence rebinding back to Cozy. Cozy validates the
isolated delegated manifest/output, excludes the disposable delegate work tree
while computing final project-relative artifact evidence, and atomically
publishes the rebound manifest. sbt-cozy removes delegate work only after this
bridge action succeeds. Incremental generation checks the final manifest
alongside model metadata. Because `cozy.generation-provenance.v1` carries one
CML source identity, multiple delegated manifests fail without arbitrary
selection.

CV-06C2 separately owns runtime activation. CNCF must admit the CAR from its
runtime range, ABI, and archive-integrity evidence without loading Cozy or
using generation compatibility as a runtime dependency.

Cozy materializes that CNCF-owned evidence as the top-level
`car-runtime-manifest.json` document with schema
`cncf.car-runtime-manifest.v1`. The document records the CAR
name/version/component coordinate, the accepted
`packaging.car.runtime.cncf` minimum, optional maximum, excluded and tested
versions, and a sorted SHA-256 inventory of every other regular archive entry.
The manifest cannot originate from `src/main/car`; Cozy derives it only after
the final archive staging tree, including placeholder files, is complete.

For a packaged `.car`, CNCF validates the manifest coordinate and supported
schema, admits its own executing version against minimum/maximum/excluded,
requires a supported `cozy.car.abi-manifest.v1` sidecar whose CAR coordinate
and exported component match the descriptor, and verifies the exact file set
and every digest before exposing the extracted component to discovery or
classloading. `tested` remains evidence of verified versions rather than an
allowlist; versions inside the declared range remain admissible unless
excluded. The CAR-style directory path remains a development override and does
not claim packaged-archive integrity. Generation provenance is just another
opaque integrity-protected entry: CNCF does not parse it or link Cozy classes.

Development-directory activation is a separate runtime admission route. Cozy
and sbt-cozy prepare `target/cncf.d/runtime-classpath.txt` together with
`target/cncf.d/car-runtime-manifest.json`, using schema
`cncf.car-development-runtime-manifest.v1` and source kind
`development-directory`. The development manifest records the CAR coordinate,
runtime range, component/ABI evidence, and digests of stable evidence files.
It deliberately excludes mutable compiled class bytes and is not accepted as
packaged-CAR archive evidence. CNCF validates this pair before any component
classloading; explicit development selection fails closed with
`sbt cozyPrepareRuntime` recovery guidance and never substitutes a packaged
CAR.

## Current CNCF Information Invocation

For CNCF-owned CML generation, `build.sbt` is the invocation authority. It
resolves the pinned Cozy generator coordinate, the root project's effective
`version.value` as the CNCF compile target, the runtime descriptor produced by
`generateCncfRuntimeDescriptor`, and the project-owned Information CML source
before launching Cozy. The descriptor task is an explicit dependency of input
resolution. The cross-compiled build helper retains five fields: generator
version, target version, descriptor path, source path, and normalized
project-relative source identity. It rejects blank coordinates, absent files,
and sources outside the project. Versions remain explicit; environment,
catalog, generated output, and filesystem discovery do not supply them.

Cold, repeated, and concurrent command evaluations use the same selected paths
and versions. A later content edit is read through the current selected CML
path, while a removed source fails command preparation. This path-based
contract does not capture descriptor/CML bytes or use content-derived control
tokens. CV-04 delegates descriptor preflight to Cozy's current
`requireValidDescriptor(path, targetVersion, source)` contract. Cozy validates
readability, root schema `1`, runtime `cncf`, exact target version, and the
predefined Result schema before emission. CLI/bridge structured diagnostics
retain code, source, expected, actual, message, and corrective action. Project,
bridge, and request descriptor/target contradictions fail explicitly; global
`~/.cozy` operation defaults do not participate.

The runtime descriptor's ComponentStyle carrier is transport for the actual
packaged JSON catalog. Its `cncf.component-style-catalog-carrier.v1` shape has
exactly `schemaVersion`, `resource`, `encoding`, and `bytes`; the resource is
`META-INF/cncf/component-style-catalog.json` and the encoding is `base64`.
The resource specification decodes that carrier and compares the original
packaged payload. It does not establish a content-derived identity or revision.

CV-05 stores generation metadata at
`target/cozy/generation-provenance.json`, outside generated Scala. Schema
`cozy.generation-provenance.v1` records the exact CNCF target, executing Cozy
version, compiled simple-modeler backend version, selected
`simplemodeling-model` version, normalized project-relative CML identity, and
sorted generated Scala paths. Machine-local absolute paths, timestamps, and
output-root names do not define metadata identities.

Cozy owns schema/declared-input validation, source readability, and the current
output-path inventory. The normal CNCF task retains Cozy `0.3.3-SNAPSHOT`,
passes the selected descriptor and source identity to generation, and then
executes `generation-provenance-validate` with that source path, output root,
exact target/generator, and source identity. Both process failures remain
fatal. CNCF selects a non-empty sorted Scala result and accepts it only after
the authoritative Cozy validator succeeds. Source/resource task dependencies
remain the ordinary build prerequisites. No descriptor/source/output/evidence
hash, source drift guard, provenance snapshot comparison, or separate
cold/repeated verification task participates.

Source identities are explicitly selected, non-empty, and project-relative;
absolute, parent-escaping, and Windows drive-prefixed identities are rejected.
The bridge derives an identity only from its explicit owning project directory.
Missing or unreadable current source/output and invalid expected inputs remain
typed Cozy diagnostics. Generation consumes the selected source path and
records current metadata according to the producer's stage validation.

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
CNCF's build executes ordinary actual Information generation and current
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
