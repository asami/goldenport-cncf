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
target/schema/digest validation only. CV-05 owns provenance and
provenance/digest tampering.

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
`generation-provenance.json`. Cozy reruns its authoritative source,
generated-artifact, aggregate-output, and evidence-digest validation and
requires the recorded CNCF target and Cozy generator to match the accepted CAR
project contract before writing the archive. The packaged bytes remain
build-time metadata. Validation and archive writing use one immutable byte
snapshot whose scoped temporary copy is removed on both success and failure.
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

For CNCF-owned CML generation, `build.sbt` is the invocation authority. It
resolves the pinned Cozy generator coordinate, the root project's effective
`version.value` as the CNCF compile target, the runtime descriptor produced by
`generateCncfRuntimeDescriptor`, and the project-owned Information CML source
before launching Cozy. The descriptor task is an explicit dependency of input
resolution. A pure cross-compiled build contract normalizes these inputs,
rejects blank coordinates, absent files, or a source outside the project, and
captures the descriptor digest, stable project-relative source identity, and
pre-launch source digest. No environment, catalog, generated output, or
filesystem fallback participates in this CNCF build path.

Cold, repeated, and concurrent evaluations resolve the same invocation inputs.
CV-04 makes descriptor identity an immutable part of that invocation. The
build computes SHA-256 over the exact generated descriptor and passes it as
`--cncf-runtime-descriptor-sha256`. Cozy CLI/bridge preflight validates that
digest, root schema `1`, runtime `cncf`, exact target version, and predefined
Result schema before source emission. Structured diagnostics carry a typed
code, source, expected, actual, message, and corrective action; unreadable and
malformed descriptors remain distinguishable from invalid digest syntax and
digest mismatch. Project defaults, owning-build bridge settings, and request
arguments must agree on every selected descriptor-contract value; conflicts
fail before generation with a typed source diagnostic instead of override
precedence. Ambient global `~/.cozy` operation defaults do not participate in
generation resolution. This remains independent of CAR runtime activation and
of CV-05 generated provenance.

CV-05 packages provenance as
`target/cozy/generation-provenance.json`; it does not embed mutable comments in
generated Scala. Schema `cozy.generation-provenance.v1` records the exact CNCF
target and descriptor SHA-256, executing Cozy version, compiled simple-modeler
backend version, selected `simplemodeling-model` version, stable
project-relative CML identity and SHA-256, sorted generated Scala identities
and file digests, one aggregate generated-output digest, and one canonical
evidence digest. Absolute paths, timestamps, output-root identities, and
filesystem traversal order are excluded.

Cozy owns production of and validation for this schema in CV-05A. Validation
recomputes source, generated-file, aggregate-output, and evidence digests and
rejects expected-input contradictions. CV-05B pins development generator
`0.3.1-SNAPSHOT`, passes the resolved source identity during generation, and
then invokes Cozy's authoritative `generation-provenance-validate` command
with the same target, generator, descriptor digest, source identity, and
pre-launch source digest before accepting generated Scala. It does not
reimplement evidence hashing in CNCF or claim development-local output as
released-generator evidence.

Cold and repeated build verification compares one snapshot containing the
complete sorted generated Scala identity/digest set and the packaged
provenance-file digest. A missing manifest, empty Scala result, source drift
after input resolution, validator failure, or difference in either half of
that snapshot fails the build.

The Cozy producer binds descriptor digest, validated descriptor fields,
predefined Result catalog, and provenance to one immutable descriptor byte
snapshot. CNCF-aware CLI generation requires a canonical non-empty
project-relative source identity and rejects absolute, parent-escaping, and
Windows drive-prefixed identities; the sbt bridge may derive it only relative
to its explicit owning project directory. Validation keeps missing/unreadable
source or output evidence and invalid expected inputs inside typed diagnostics.

Cozy also captures the normalized CML identity and bytes before invoking the
modeler, computes the digest from that capture, and makes generation consume an
isolated materialization of the captured bytes. It removes stale provenance
before generation and publishes replacement provenance by atomic move only
after complete validation confirms that the original source still contains the
captured bytes. Replacement, removal, unreadability, or failed final validation
therefore cannot associate output with another source revision or leave a new
manifest visible.
