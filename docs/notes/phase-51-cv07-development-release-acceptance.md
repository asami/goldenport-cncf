# Phase 51 CV-07 Development and Release Acceptance

Status: review fixes implemented and focused validation complete; independent
RE_REVIEW pending.

## Purpose

CV-07 connects the pure exact-pair admission implemented in CV-02 to the
development, generation, packaging, and publication boundaries. It does not
add another CNCF or Cozy version source. Existing project, owning-build bridge,
and CLI exact coordinates remain authoritative.

The contract distinguishes:

- pair mutability: whether either exact coordinate contains `SNAPSHOT`;
- output lifecycle: whether the owning CNCF/CAR output version is a development
  SNAPSHOT or an immutable release;
- pair source: explicit project/bridge/CLI input or the published default; and
- artifact identity: the requested Cozy coordinate versus the Cozy binary that
  actually executed.

## Acceptance Rules

### Explicit development

A mutable generation pair is an explicit development opt-in only when:

1. the exact pair comes from an agreeing project, owning-build bridge, or CLI
   source;
2. compatibility evidence marks that exact pair `proven`;
3. the owning output is a development SNAPSHOT; and
4. the executing Cozy `BuildInfo.version` equals the selected generator
   version.

Successful mutable admission emits one deterministic diagnostic containing the
development lifecycle, selected source, exact pair, and evidence owner. A
mutable pair is never selected through the published-default path.

No additional boolean permission is introduced. The explicit SNAPSHOT
coordinate is the opt-in, while the output version prevents the same mutable
pair from crossing into release output.

### Immutable release

An immutable release output is admitted only when:

1. the CNCF target and Cozy generator are both immutable coordinates;
2. compatibility evidence marks the exact pair `proven`;
3. the executing Cozy binary identifies the selected generator version;
4. generated output carries valid provenance for that pair; and
5. CAR package, review, and publication observe the same project-owned pair.

For a CML-backed release CAR, missing generation provenance is an unresolved
generator identity and therefore a release error. Non-generated legacy CAR
input remains outside that generated-output requirement, but it cannot claim
generation evidence.

### Published default

The compatibility evidence resource owns at most one `publishedDefault` exact
pair. The default must:

- reference an existing `proven` pair record;
- contain only immutable coordinates; and
- remain absent when no immutable pair has completed publication evidence.

Callers cannot inject a separate default pair. Project, bridge, and CLI values
must still agree and take precedence only as provenance among equal explicit
values. When no explicit value exists, an absent, mutable, unproven, or
otherwise invalid published default fails with a typed diagnostic.

## Execution Boundaries

### Cozy compatibility authority

Extend the existing compatibility model rather than creating a second
lifecycle validator.

- The evidence schema carries the optional evidence-owned
  `publishedDefault`.
- Source resolution consumes that evidence instead of accepting a
  caller-owned default.
- One acceptance result reports pair, source, output lifecycle, admission,
  evidence owner, and development notice or typed rejection.
- Expected and executing Cozy versions are compared before source output is
  accepted.

The current explicitly exercised development pairs are recorded as proven only
after their existing real-generation evidence is retained:

- CNCF `0.5.2-SNAPSHOT` with Cozy `0.3.1-SNAPSHOT`;
- CNCF `0.5.1` with Cozy `0.3.1-SNAPSHOT` for the ArtScene development CAR.

The existing `0.5.1` / `0.3.0` release pair remains unproven and must not become
the published default merely because both coordinates are immutable.

### CNCF build

`CncfGenerationBuildContract` continues to own the exact CNCF target and Cozy
generator constants. Its production command additionally carries the expected
Cozy generator version into Cozy preflight. The CNCF target version is the
owning output version for this build.

Failure to launch the selected coordinate reports that coordinate and the
manual recovery action. A later provenance check remains mandatory, but it is
not the first place where an executing-version mismatch is discovered.

### sbt-cozy and CAR generation

sbt-cozy reads `build.cozyVersion` from unmerged `project.yaml`, passes it as
the expected generator version, and includes it in incremental generation
state. For Coursier delegation, the requested delegate coordinate must equal
that project-owned version. For an explicit development project directory,
Cozy's executing `BuildInfo.version` must still equal it.

The CAR component version is the owning output version:

- a SNAPSHOT component may use a proven explicit mutable pair;
- a release component may use only a proven immutable pair.

A changed expected coordinate invalidates incremental reuse. Cache contents,
local publication, or a development project directory cannot turn a mismatched
binary into the requested coordinate.

### Package, review, and publication

The project-only CV-06 decision is reused. For release output it additionally
requires:

- immutable `build.cozyVersion` and CNCF compile target;
- evidence-backed exact-pair release admission;
- valid generation provenance when the project has CML generation input; and
- exact provenance/project generator agreement.

The package gate runs before archive output, and publication runs the same
project decision before repository writes. Development publication continues
through the local SNAPSHOT path and cannot update stable selectors.

The normal sbt-cozy publication path passes the CAR produced by
`cozyBuildCar` to Cozy as a prebuilt archive. Publication therefore admits a
prebuilt declared CAR only after revalidating its package-generated
`car-runtime-manifest.json`: CAR identity, accepted runtime range, exact
archive path set, and every SHA-256 digest must agree. A generated release CAR
must also match an immutable snapshot of
`target/cozy/generation-provenance.json` after that evidence has been
revalidated against the owning project's current CML and generated Scala
output for the accepted exact CNCF/Cozy pair. Publication snapshots the
prebuilt CAR before admission and publishes that same byte snapshot. An
arbitrary, stale, or concurrently replaced archive is rejected before
warehouse writes.

## Artifact Resolution and Recovery

Artifact presence is proved by successful resolution and execution of the
exact requested coordinate, followed by executing-version comparison. A stale
cache or incorrectly populated local repository is therefore detected as an
identity mismatch rather than accepted by filename.

An unavailable exact generator produces a deterministic diagnostic containing:

- the exact requested coordinate;
- whether it came from project, bridge, CLI, or published default;
- the owning output lifecycle;
- the evidence location; and
- one of these recovery actions:
  - publish the exact proven SNAPSHOT to the configured development repository;
  - select an already published proven immutable pair; or
  - publish compatibility evidence and the immutable artifact before making a
    new pair the default.

Deleting caches or silently selecting another version is not a recovery path.

## Implementation Units

### Cozy

- Extend `GenerationCompatibilityEvidence` with the evidence-owned default and
  strict invariants.
- Add one production resolve/admit/report boundary for explicit development and
  immutable release.
- Add executing-generator identity validation to CLI and sbt-bridge generation
  preflight.
- Apply release admission to generated CAR package, integrated lint/Review,
  and publication.
- Keep structured diagnostics stable and add manual recovery text.

### sbt-cozy

- Read and forward project-owned `build.cozyVersion`.
- Require Coursier delegate and project metadata to select the same exact
  generator.
- Add the expected generator and lifecycle inputs to incremental state.
- Preserve exact-coordinate diagnostics for absent delegate artifacts.
- Reject more than one CNCF compile dependency declaration, including
  duplicate aliases that resolve to the same version.
- Resolve component-local Maven dependencies declared by a packaged CAR for
  the flat SBT test runtime without loading that CAR's source project.

### CNCF

- Pass expected Cozy generator identity in the production generation command.
- Preserve exact-coordinate and recovery diagnostics when launch fails.
- Extend the build-contract executable specification without introducing
  environment or catalog fallback.

### ArtScene

- Use the existing development SNAPSHOT project as the representative CAR.
- Prove explicit mutable-pair admission.
- Prove that changing only the component output to release rejects the mutable
  generator before archive/publication output.

## Executable Specification Matrix

### Cozy compatibility

- explicit proven SNAPSHOT pair plus SNAPSHOT output succeeds and reports a
  development notice;
- the same mutable pair plus release output fails;
- a proven immutable pair plus release output succeeds;
- mutable, unproven, missing, and invalid published defaults fail;
- a published default must reference one proven immutable pair;
- requested and executing Cozy versions must match;
- diagnostic ordering is deterministic.

### Cozy package and publication

- a generated release CAR without provenance fails before archive output;
- a generated release CAR with mutable generator provenance fails;
- a generated release CAR with proven immutable provenance passes;
- publication rejects the same invalid inputs before warehouse writes;
- publication rejects an arbitrary prebuilt CAR that bypassed package
  admission;
- publication verifies runtime-manifest identity, path-set, and digest
  evidence before accepting a package-built prebuilt CAR;
- publication validates current owning-project source/generated output and
  requires exact packaged/current provenance byte equality;
- SNAPSHOT local publication remains isolated from stable catalog selectors.

### sbt-cozy

- project `build.cozyVersion` is forwarded to generation;
- a mismatched Coursier delegate version fails before execution;
- a changed generator coordinate invalidates incremental reuse;
- an absent coordinate reports the exact artifact and recovery action;
- a development project delegate still undergoes executing-version validation.

### CNCF and ArtScene

- CNCF command planning carries the expected generator and exact target;
- a launch failure names the exact unresolved generator;
- ArtScene SNAPSHOT development accepts its explicit proven pair;
- an ArtScene release-output fixture rejects its mutable generator;
- cache/local mismatch cannot be hidden by successful path resolution.

## Validation

Focused implementation validation uses serialized SBT only:

- Cozy compatibility, package, lint/Review, publisher, and provenance specs;
- sbt-cozy generation-state, delegate, and actual-Cozy bridge specs;
- CNCF generation build-contract specs;
- ArtScene Phase 51 acceptance and normal CAR lint;
- `Test/compile` for every modified Scala repository; and
- `git diff --check` for all Phase 51 repositories.

Full suites remain deferred to the final Phase 51 release gate.

## Documentation Promotion

This note is the current implementation specification during CV-07. After
implementation and clean re-review:

- promote verified ownership and lifecycle behavior to
  `docs/design/generation-compatibility-contract.md`;
- promote machine-facing inputs, diagnostics, and failure semantics to
  `docs/spec/generation-compatibility-contract.md`;
- update Cozy ownership/scaffold documentation;
- record the exact executable evidence in the Phase 51 checklist and
  compliance ledger.

## Implementation Evidence

The IMPLEMENT stage now connects the planned contract at each production
boundary:

- Cozy compatibility evidence owns `publishedDefault`, which is currently
  absent, and records the two proven development pairs;
- Cozy CLI and sbt bridge compare the selected exact Cozy coordinate with the
  executing `BuildInfo.version`;
- generated release CAR packaging and CAR metadata admission reject mutable
  pairs, while generated release packaging also requires final provenance;
- sbt-cozy derives the generator and CNCF target from unmerged `project.yaml`,
  rejects contradictory delegate or bridge values, and includes both values in
  incremental generation state; and
- the CNCF production command carries `--cozy-generator-version` and reports
  exact generator/target coordinates with deterministic recovery when launch
  fails.

Serialized focused validation completed:

- Cozy prebuilt-admission/provenance focused set: 26 passed;
- sbt-cozy dependency/resolver focused set: 28 passed;
- CNCF generation build and provenance: 8 passed; and
- ArtScene release-CAR assembly acceptance: 8 passed with zero failures or
  cancellations.

The Cozy package/publication matrix now proves that an actual CAR command owns
its lifecycle from the project component version, that a publication request
must equal that version, and that immutable generated output requires the
accepted exact pair and provenance. sbt-cozy requires CAR/SAR projects to own
both exact generation coordinates and names the unavailable generator plus its
recovery action.

The ArtScene representative no longer uses a source-project dependency on
`textus-scraper`. It resolves the immutable `textus-scraper@0.1.1` CAR through
sbt-cozy, extracts its packaged runtime JARs, resolves the CAR-owned
`dependencies.local` Maven coordinates for tests, and keeps only the contract
API JAR on the production classpath. The focused run therefore exercises
ArtScene and jsoup-backed scraping without rebuilding or republishing that
released dependency.

ArtScene defaults its build plugin to the explicit Phase 51 development
coordinate `sbt-cozy 0.1.16-SNAPSHOT`; no property or environment override is
required for the normal development build. The existing override inputs remain
available for deliberate emergency selection.

The stale source-managed ArtScene component descriptor was removed.
`project.yaml` remains the single component-version authority, and the
acceptance spec now verifies that no duplicate descriptor path exists.

The complete `cncf-car-lint` pass exits successfully for ArtScene and accepts
the exact `0.5.1 / 0.3.1-SNAPSHOT` metadata contract. Its existing ABI-baseline
and nominal-string-wrapper warnings remain visible. It also reports the
deliberate `sbt-cozy 0.1.16-SNAPSHOT` development selection as a warning; the
explicit mutable coordinate is the CV-07 development opt-in, not a release
claim.
