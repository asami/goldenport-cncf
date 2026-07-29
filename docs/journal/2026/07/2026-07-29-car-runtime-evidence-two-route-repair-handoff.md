# CAR Runtime Evidence Two-Route Repair Handoff

Date: 2026-07-29

## Document Position

This journal entry records the observed ArtScene dependency failure and the
agreed repair procedure. It is an implementation handoff, not a normative
change by itself.

The stable boundary must be promoted to `docs/design`, and executable
requirements must be promoted to `docs/spec`, according to
`docs/rules/document-lifecycle.md`.

## Summary

The ordinary ArtScene `cncf server` failure is not primarily a Supervisor or
SPI-wiring defect. The immediate failure is CAR runtime admission.

ArtScene still resolves dependency coordinates whose locally available CARs
predate the required `car-runtime-manifest.json`. Current CNCF intentionally
rejects those archives before component discovery or SPI installation. This is
the correct packaged-CAR behavior; CNCF must not silently accept or rewrite an
old immutable CAR.

The repair must support both normal component-development routes:

1. package and publish the dependency as a locally resolved CAR; and
2. use the dependency's development directory directly without building and
   publishing a CAR after every edit.

The second route needs explicit runtime evidence too. It must not become an
admission bypass.

## Confirmed Current State

### Packaged CAR route

Cozy currently generates top-level `car-runtime-manifest.json` while building
the completed CAR staging tree. Schema `cncf.car-runtime-manifest.v1`
identifies the CAR coordinate, records the CNCF compatibility range, and
contains the exact SHA-256 inventory of every other regular archive entry.

CNCF validates that evidence before packaged component discovery and
classloading. Missing manifest, coordinate contradiction, incompatible CNCF
range, file-set difference, digest mismatch, or invalid ABI evidence rejects
the CAR.

This route is correct and must remain the archive-integrity authority for local
and remote published CARs.

### Development-directory route

sbt-cozy already provides:

- `cozyRuntimeClasspathFile`; and
- `cozyPrepareRuntime`.

`cozyPrepareRuntime` compiles the project and writes:

```text
target/cncf.d/runtime-classpath.txt
```

CNCF's component-development repository currently validates only that the
development directory exists and that this classpath file is present and
non-empty. It then reads descriptors from `car.d` or `src/main/car` and loads
classes from the prepared classpath.

There is no development runtime manifest admission yet.

### Why the packaged manifest cannot be copied unchanged

The packaged manifest proves an immutable archive byte set. A development
directory is deliberately mutable: recompiling changes class output without
changing the logical component identity or compatibility contract.

Applying the packaged archive inventory to the development tree would make the
manifest stale after normal editing and compilation. That contradicts the
intended operation in which runtime evidence is normally prepared once near
the start of SNAPSHOT development and regenerated only when its contract
inputs change.

The two routes therefore share identity and runtime-compatibility semantics,
but require different integrity scopes.

## Agreed Target Model

| Source route | Runtime evidence location | Integrity scope |
| --- | --- | --- |
| Packaged/local-published CAR | top-level `car-runtime-manifest.json` inside the CAR | exact immutable archive file set and digests |
| Development directory | `target/cncf.d/car-runtime-manifest.json` beside `runtime-classpath.txt` | stable development contract inputs; mutable compiled class bytes are excluded |

Keep packaged schema `cncf.car-runtime-manifest.v1` unchanged. Introduce a
distinct development schema, provisionally:

```text
cncf.car-development-runtime-manifest.v1
```

Using the same filename is intentional: both files are the runtime admission
manifest for their source kind. CNCF selects the validator from the explicit
source route and must not accept a development manifest as packaged archive
evidence, or a packaged manifest as development evidence.

The development manifest should contain at least:

- `sourceKind: development-directory`;
- CAR name, version, and component name;
- accepted CNCF minimum, optional maximum, excluded, and tested versions;
- the project-relative runtime classpath evidence path and its digest;
- the project-relative component and assembly descriptor identities and
  digests when present;
- ABI or component-export evidence sufficient to reject a contradictory
  component before classloading; and
- a deterministic evidence digest over the stable development contract.

The development integrity set must not hash mutable class directories or the
complete development tree. The classpath file may contain the resolved paths,
but machine-local absolute paths must not be incorporated into a portable
logical identity or compatibility digest without explicit normalization.

## Implementation Procedure

### 1. Extract a shared Cozy runtime-evidence model

Repository:

```text
/Users/asami/src/dev2025/cozy
```

Refactor the current package-private archive manifest implementation so that
the following validation and rendering concepts can be shared:

- CAR coordinate;
- CNCF runtime range;
- component export / ABI identity;
- deterministic JSON rendering;
- SHA-256 entry generation; and
- atomic publication of completed evidence.

Keep archive inventory construction owned by the packaged-CAR writer. Add a
separate development-manifest producer whose integrity input is an explicit
stable evidence set rather than a recursive development-tree scan.

The development producer must consume the accepted `project.yaml` CAR
contract. It must not infer compatibility from the running CNCF version or
from ambient repository state.

Add focused Cozy tests for:

- deterministic repeated development-manifest generation;
- exact coordinate and runtime-range rendering;
- descriptor and ABI contradiction;
- stable-input digest changes;
- exclusion of mutable class output;
- rejection of absolute or parent-escaping evidence identities; and
- atomic failure behavior.

### 2. Extend sbt-cozy runtime preparation

Repository:

```text
/Users/asami/src/dev2026/sbt-cozy
```

Preserve the existing task keys and return contract where possible. Add:

```text
cozyDevelopmentRuntimeManifest
cozyRuntimeEvidenceFiles
```

Extend `cozyPrepareRuntime` to perform, in order:

1. compile;
2. write `target/cncf.d/runtime-classpath.txt`;
3. generate `target/cncf.d/car-runtime-manifest.json`; and
4. report the complete prepared evidence set.

For compatibility with existing builds, `cozyPrepareRuntime` may continue to
return its current classpath `File` while producing the manifest as an owned
side output. `cozyRuntimeEvidenceFiles` provides the extensible `Seq[File]`
surface for future evidence without repeatedly changing the aggregate task's
type.

The task must be idempotent. It is normally run once at the beginning of a
SNAPSHOT development interval, but must be safe to rerun whenever any of the
following changes:

- project or component coordinate;
- Cozy/CNCF compatibility metadata;
- build dependency classpath;
- component, assembly, or ABI descriptor; or
- development-manifest schema.

Update generated `scripts/update-runtime-classpath.sh` compatibility behavior
to delegate to the sbt-cozy preparation task instead of remaining an
independent classpath-only producer. The user-facing recovery command should
be one command, provisionally:

```sh
sbt cozyPrepareRuntime
```

Add scripted coverage proving that a CAR project produces both evidence files
and that a contract-input change regenerates a different manifest.

### 3. Add CNCF development runtime admission

Repository:

```text
/Users/asami/src/dev2025/cloud-native-component-framework
```

Keep `CarRuntimeAdmission` as the packaged archive validator. Add a
development-source admission boundary, provisionally
`DevelopmentCarRuntimeAdmission`, and extract only genuinely shared validation
such as:

- CAR/component coordinate comparison;
- CNCF runtime-range parsing and evaluation; and
- component export / ABI identity comparison.

For an explicitly selected component development directory, validate in this
order:

1. development directory;
2. `target/cncf.d/car-runtime-manifest.json`;
3. supported development schema and `sourceKind`;
4. coordinate, runtime range, and component export / ABI evidence;
5. stable evidence identities and digests;
6. `target/cncf.d/runtime-classpath.txt`; and
7. class directories and component discovery.

No component class may be loaded before steps 1 through 6 succeed.

When either evidence file is missing, empty, contradictory, or stale, report
the exact path and instruct the developer to run the sbt-cozy preparation
command. An explicit development-directory selection must continue to fail
closed; it must not silently fall back to a packaged CAR.

Development-directory auto-detection must no longer treat a lone classpath
file as complete prepared evidence. Detection and validation should recognize
the evidence pair while still producing a useful partial-preparation error
when only one file exists.

Add focused CNCF tests for:

- a valid prepared development component;
- missing classpath;
- missing development manifest;
- unsupported or packaged-only schema;
- coordinate contradiction;
- incompatible CNCF range;
- descriptor or evidence digest mismatch;
- mutable class recompilation without contract invalidation; and
- explicit development source never falling back to a CAR.

### 4. Promote the agreed contract to design and spec

Update the authoritative documents after the implementation shape is reviewed:

- `docs/design/packaged-source-activation.md`;
- `docs/design/generation-compatibility-contract.md`; and
- `docs/spec/generation-compatibility-contract.md`.

The documents must state that:

- both packaged and development sources pass runtime admission;
- only packaged CARs claim complete archive integrity;
- development manifests claim stable contract evidence, not immutable build
  output;
- source route selects the supported schema and integrity semantics;
- explicit development-directory selection fails closed; and
- Cozy/sbt-cozy own evidence production while CNCF owns runtime admission.

### 5. Rebuild the ArtScene dependency chain

Affected dependency CARs currently include:

```text
textus-ai-runtime
textus-scraper
textus-toolchain-runner
textus-user-notification
```

For the local-publish route:

1. select the next coordinated SNAPSHOT versions;
2. update each owner project to the current Cozy/CNCF contract;
3. build a manifest-bearing CAR;
4. validate the CAR before publication;
5. publish it to the local CAR repository;
6. update ArtScene assembly dependency coordinates; and
7. run ordinary repository-resolved `cncf server`.

For the development-directory route:

1. run the sbt-cozy runtime-preparation task in each component under active
   development;
2. select that component through the existing development-directory
   configuration;
3. verify that CNCF admits the development manifest before loading classes;
4. verify that assembly resolution uses the development component; and
5. verify that the runtime does not substitute an older packaged CAR.

`textus-user-notification` already has user-owned uncommitted work. Do not
rewrite, discard, or include that work implicitly. Inspect it and obtain an
explicit implementation scope before changing or publishing that repository.

### 6. Run two-route ArtScene acceptance

The repair is not complete until the same relevant ArtScene behavior passes
through both dependency supply routes.

Local-publish acceptance:

- all selected dependency CARs contain valid packaged manifests;
- ArtScene resolves the updated SNAPSHOT coordinates from the local
  repository;
- no pre-manifest fallback is accepted;
- assembly component loading succeeds; and
- service/SPI installation proceeds beyond CAR admission.

Development-directory acceptance:

- all explicitly selected development dependencies have both prepared evidence
  files;
- invalid or incomplete preparation fails before classloading;
- the assembly reports the development origin for those components;
- an available older packaged CAR is not selected instead; and
- service/SPI installation proceeds beyond development admission.

An SPI failure after successful component admission is a separate runtime
wiring finding. Do not diagnose it as the original missing-manifest failure.

## Repository Order

The expected implementation and local-publication order is:

1. Cozy runtime-evidence producer;
2. sbt-cozy development preparation;
3. CNCF development admission;
4. local publication of the updated Cozy/sbt-cozy/CNCF stack as required;
5. dependency CAR owner updates and local publication;
6. ArtScene assembly-coordinate update; and
7. two-route ArtScene acceptance.

All independent top-level sbt invocations must use the shared serialized SBT
runner. Do not start multiple repository builds concurrently.

## Completion Conditions

This handoff is complete when:

- packaged CAR admission remains strict and accepts newly published evidenced
  CARs;
- development-directory admission requires the prepared classpath and
  development manifest;
- `cozyPrepareRuntime` produces the complete current development evidence set;
- future development evidence can be added through the aggregate task without
  inventing another preparation workflow;
- ArtScene starts with updated locally published dependency CARs;
- ArtScene also starts with explicitly selected prepared development
  dependencies;
- neither route silently falls back across source kinds; and
- design/spec promotion, focused tests, full affected-repository tests, and
  clean review are complete.

## Out of Scope

This repair does not:

- weaken admission for historical CARs;
- mutate or synthesize manifests inside published archives;
- treat version similarity as compatibility evidence;
- make Cozy a CNCF runtime dependency;
- hash mutable development class output as immutable archive evidence; or
- establish that every later SPI or service-wiring failure has the same cause.
