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
CV-04 owns runtime descriptor path/schema/runtime/exact-target and predefined
Result validation. CV-05 owns current generation metadata and output-inventory
validation. CV-06A owns CAR project-contract
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
rerun Cozy's authoritative validation of its readable source, declared inputs,
and project-relative generated Scala inventory. Its recorded CNCF target and Cozy
generator must equal the accepted CAR project contract. Successful packaging
must preserve the bytes unchanged as the top-level
`generation-provenance.json` CAR entry; validation or contract mismatch must
fail before archive creation. Cozy owns metadata parsing, path validation,
and projection into the archive. Non-generated and legacy CAR sources may omit
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
`target/cncf.d/component-descriptor.json`. CNCF MUST continue to admit a
digest-valid `cncf.car-development-runtime-manifest.v1` prepared before this
change, whose descriptor evidence remains
`src/main/car/component-descriptor.json`. Style-less legacy CML may continue
to prepare that v1 form; it is never upgraded to an incomplete v2 snapshot.
A packaged manifest MUST NOT be accepted as development evidence.

### R3 Stable contract evidence

The manifest MUST match the component descriptor and ABI export evidence,
reproduce a supported CNCF runtime range, verify each declared stable evidence
identity and SHA-256 digest, and use a deterministic evidence digest.
For v2, the development descriptor MUST be schema version `2` and carry the
complete catalog-matching `componentStyle` snapshot. A v1 migration descriptor
keeps its legacy descriptor shape.

### R4 Mutable output exclusion

Mutable compiled classes are not part of the development integrity set.

### R5 Stale classpath rejection

Every non-empty classpath entry referenced by the prepared classpath file MUST
exist when the producer creates evidence and when CNCF admits it. A deleted or
invalid entry is stale development evidence and MUST be rejected before
classloading.

### R6 Fail-closed recovery

Missing, empty, malformed, contradictory, or stale development evidence MUST
fail before component discovery or classloading, identify the failing path,
and direct the developer to run `sbt cozyPrepareRuntime`. A development-source
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
- E10: an incomplete schema-v2 componentStyle snapshot is rejected before development activation (R3, R6).
- E11: a digest-valid v1 development directory retains its legacy source descriptor identity during migration (R2, R3).

## Current Information Generation Contract

The CNCF Information CML build resolves its invocation from the pinned Cozy
generator version, the root build's effective CNCF artifact version, the output
of the CNCF runtime-descriptor task, the CNCF project directory, and the
Information CML source. Resolution must fail before Cozy is launched when
either coordinate is blank, the descriptor/project/source is unavailable, or
the source is outside the owning project. The five resolved fields are
`cozyGeneratorVersion`, `cncfTargetVersion`, `runtimeDescriptor`, `source`,
and normalized project-relative `sourceIdentity`. They retain the selected
paths and exact versions. The source at that path is consumed when generation
runs; editing its content after resolution does not invalidate the command
plan, while deleting it prevents both command preparations.

Generation uses `cozy --runtime <exact-Cozy-version> modeler-scala-value`
with the selected source, `--cncf-version`, `--cozy-generator-version`,
`--cncf-runtime-descriptor`, `--generation-source-identity`, and output
`--save` selector. Cold, repeated, and concurrent command evaluations retain
these same explicit selectors. No ambient version source participates.

Before source emission, Cozy validates the readable descriptor path, root
schema `1`, runtime identity `cncf`, exact target version, and predefined
Result schema `cncf.predefined-result.v1`. CLI and sbt-bridge invocation use the
same validator and deterministic structured diagnostic fields. Project defaults,
owning-build bridge settings, and request arguments must agree on target and
descriptor; contradictory values fail before generation. Global `~/.cozy`
operation defaults are not generation sources.

The descriptor transports the actual packaged ComponentStyle JSON in
`componentStyleCatalog` using `cncf.component-style-catalog-carrier.v1` with
exactly `schemaVersion`, `resource`, `encoding`, and `bytes`. The resource is
`META-INF/cncf/component-style-catalog.json`, the encoding is `base64`, and
decoding `bytes` yields that original packaged resource. Catalog meanings and
other runtime descriptor fields retain their existing contracts.

For descriptor-contract generation, Cozy writes
`target/cozy/generation-provenance.json` using
`cozy.generation-provenance.v1`. It records the exact CNCF target; exact Cozy,
simple-modeler backend, and `simplemodeling-model` versions; an explicitly
selected non-empty project-relative CML identity; and sorted project-relative
generated Scala paths. Provenance remains build-time metadata outside generated
Scala. Machine-local paths, timestamps, and output-root names are not metadata
identities. Absolute, parent-escaping, and Windows drive-prefixed source
identities are rejected.

CV-05A owns schema, expected-input, source-readability, and current output-path
inventory validation. CV-05B retains the build's pinned Cozy `0.3.3-SNAPSHOT`
and invokes `generation-provenance-validate` after ordinary generation with the
same source path, output selector, target, generator version, and source
identity. Validator success is mandatory before generated Scala is accepted;
CNCF does not reconstruct Cozy's metadata validation. The build retains normal
source/resource generator wiring and requires a non-empty sorted Scala result.
A generation or validation process failure is fatal. Current paths, versions,
and output inventory govern each stage; descriptor, source, output, and metadata
hashes, captured-byte drift checks, and a cold/repeated snapshot task do not
participate in this contract.

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

- CNCF ordinary actual Information generation and current metadata validation
  through `build.sbt`;
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
