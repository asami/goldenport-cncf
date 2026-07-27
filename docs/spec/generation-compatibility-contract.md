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
CV-04 owns runtime descriptor target/schema/digest validation only. CV-05 owns
provenance and provenance/digest tampering. CV-06A owns CAR project-contract
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

When `target/cozy/generation-provenance.json` exists, package admission must
rerun the authoritative validation of its source, generated Scala artifacts,
aggregate output digest, and evidence digest. Its recorded CNCF target and Cozy
generator must equal the accepted CAR project contract. Successful packaging
must preserve the bytes unchanged as the top-level
`generation-provenance.json` CAR entry; validation or contract mismatch must
fail before archive creation. Validation and archive writing must consume the
same immutable byte snapshot. Non-generated and legacy CAR sources may omit
this metadata. `src/main/car/generation-provenance.json` is reserved and must
be rejected rather than admitted through generic CAR source packaging.

For the normal sbt-cozy backend, each delegated Cozy run first produces and
validates its own isolated evidence. sbt-cozy must then ask Cozy's bridge
authority to rebind that evidence to the installed project-relative Scala
output, publish it at the owning project's `target/cozy` path, and remove the
delegate work tree. Incremental reuse requires this final side output. Schema
v1 represents one source identity; a CAR generation containing multiple
delegated v1 provenance documents must fail explicitly rather than select one.

Runtime activation must neither load Cozy nor evaluate generation
compatibility or provenance. CNCF runtime range, ABI, and archive-integrity
evidence independently govern activation under CV-06C2.

A project-contract CAR package must contain top-level
`car-runtime-manifest.json` using schema
`cncf.car-runtime-manifest.v1`. It must identify the same CAR name, version,
and component as `component-descriptor.json`; reproduce the accepted CNCF
minimum, optional maximum, excluded, and tested metadata; specify algorithm
`SHA-256`; and list every other regular archive entry exactly once with a
lowercase 64-hex digest. Cozy must reject a source-managed runtime manifest and
generate the document from the completed staging tree.

Before component discovery or classloading, packaged-CAR extraction must reject
a missing or unsupported runtime manifest, coordinate contradiction, inverted
range, executing CNCF version below minimum, above maximum, or explicitly
excluded, unsafe or duplicate integrity path, file-set difference, or digest
mismatch. The `tested` values must be non-empty but do not form a runtime
allowlist. CNCF must also require `abi-manifest.json` format
`cozy.car.abi-manifest.v1`, ABI version `1`, the same CAR name/version, and an
export of the packaged component. CAR-style development directories do not
claim packaged archive integrity and remain outside this packaged-CAR gate.
`generation-provenance.json`, when present, is hashed as opaque bytes and must
not be semantically evaluated by CNCF.

The CNCF Information CML build resolves its invocation from the pinned Cozy
generator version, the root build's effective CNCF artifact version, the output
of the CNCF runtime-descriptor task, the CNCF project directory, and the
Information CML source. Resolution must fail before Cozy is launched when
either coordinate is blank, the descriptor/project/source is unavailable, or
the source is outside the owning project. Resolution captures the
project-relative source identity and pre-launch SHA-256. The supported command
must carry the same values as `--runtime`, `--cncf-version`,
`--cncf-runtime-descriptor`, `--cncf-runtime-descriptor-sha256`, and
`--generation-source-identity`; it must not consult ambient version sources.

The resolution and command plan are deterministic for cold, repeated, and
concurrent evaluations. Cold and repeated generation of unchanged CML with the
same explicit inputs must emit the same generated Scala file set and content.
Before source emission, Cozy validates the readable descriptor file and supplied
lowercase hexadecimal SHA-256, root schema `1`, runtime identity `cncf`, exact
target version, and predefined Result schema
`cncf.predefined-result.v1`. CLI and sbt-bridge invocation use the same
validator and deterministic structured diagnostic fields. Project defaults,
owning-build bridge settings, and request arguments must agree on target,
descriptor, and digest values; contradictory values fail before generation.
Global `~/.cozy` operation defaults are not generation sources. CV-05 remains
the owner of generated-output provenance and provenance tampering.

For complete CNCF descriptor-contract generation, Cozy packages
`target/cozy/generation-provenance.json` using
`cozy.generation-provenance.v1`. It records the exact target and descriptor
digest; exact Cozy, simple-modeler backend, and `simplemodeling-model`
versions; an explicitly selected non-empty project-relative CML identity and
digest; sorted relative
generated Scala identities and file digests; an aggregate output digest; and a
canonical evidence digest. Provenance is metadata only and is not embedded in
generated Scala.

Cold and repeated generation from the same logical inputs must emit
byte-identical provenance across different output roots. Machine-local paths,
timestamps, output-root names, and unstable traversal order are forbidden.
Validation must reject malformed schemas, contradictory expected inputs,
changed CML bytes, changed generated file identities or bytes, and provenance
fields inconsistent with the evidence digest. CV-05A implements this Cozy
producer/validator core. CV-05B pins Cozy `0.3.1-SNAPSHOT` for the explicit
development workflow and, after generation, invokes
`generation-provenance-validate` with the resolved output root, target,
descriptor digest, generator coordinate, source identity, and pre-launch
source digest. Validator success is required before generated Scala is
accepted; CNCF must not reconstruct or partially validate Cozy-owned evidence.

The build's cold/repeated snapshot must include both the complete sorted
generated Scala identity/digest set and the SHA-256 of
`target/cozy/generation-provenance.json`. Missing provenance, empty Scala
output, source drift after input resolution, validator rejection, or any
snapshot difference must fail deterministically.

One immutable descriptor byte snapshot supplies validation, the predefined
Result catalog, and the recorded descriptor digest. Changing the descriptor
path after that snapshot cannot change the current generation contract.
Missing or unreadable source/output evidence and invalid expected inputs return
typed deterministic diagnostics rather than raw exceptions.

The CML identity and bytes are captured before generation, and absolute,
parent-escaping, or Windows drive-prefixed identities are rejected. The modeler
and CML metadata producer consume an isolated materialization of the captured
bytes. Cozy clears stale provenance before generation and atomically publishes
a replacement only after complete validation confirms that the original source
still resolves to those bytes. Replacement, removal, unreadability, or failed
final validation must therefore leave no newly committed provenance and cannot
associate output with a later source revision.
