# Phase 56 CID-06D Deferred-Release Compatibility Plan

## Status

- Phase: 56
- Step: CID-06 Compatibility Adapters
- Slice: CID-06D Exact Deferred Releases and Legacy Generated Core
- Slice status: ACCEPTED / REVIEWED
- Phase full validation: pending until the Phase 56 release gate

## Goal

Keep exactly four already-released CAR coordinates runnable without rewriting
or republishing their archives, while preserving strict canonical Component
identity everywhere else. The runtime registry is the executable admission
authority; the CID-01 migration ledger remains the inventory and owner record.

## Frozen Deferred-Release Registry

The runtime resource
`META-INF/cncf/component-identity-deferred-release-registry.json` contains
exactly these entries:

| Canonical Component ID | Exact release | Legacy artifact | Legacy local ID | Migration owner |
| --- | --- | --- | --- | --- |
| `org.simplemodeling.textus.Corpus` | `0.1.0` | `textus-corpus` | `Corpus` | `textus-corpus` |
| `org.simplemodeling.textus.Experiment` | `0.1.0` | `textus-experiment` | `Experiment` | `textus-experiment` |
| `org.simplemodeling.textus.GeoResolver` | `0.2.1` | `textus-georesolver` | `GeoResolver` | `textus-georesolver` |
| `org.simplemodeling.textus.Sanpomap` | `0.2.1` | `textus-sanpomap` | `Sanpomap` | `textus-sanpomap` |

Registry loading is fail-closed. The schema version, unique canonical IDs,
unique legacy artifacts, canonical `ComponentId`, validated
`ComponentReleaseCoordinate`, non-empty owner/local ID, and local-ID agreement
must all pass before any entry is usable. A parity specification compares the
four runtime entries with the four `current-release-deferred` entries in
`phase-56-cid01-car-migration-ledger.yaml`.

## Admission Classification

One registry decision classifies a raw archive descriptor before canonical
archive validation:

- `ExactDeferred`: legacy artifact and release are exact registry equality.
  The descriptor is projected through the CID-06B expected-identity adapter,
  and the registry entry is retained with the extracted CAR.
- `Strict`: schema-3 descriptors and identities absent from the registry use
  the ordinary canonical path without a compatibility scope. Deferred-release
  classification is available only to schema-2 descriptors.
- `MigrationRequired`: a registered artifact with a greater stable release or
  any `-SNAPSHOT` release receives no compatibility scope and must pass the
  strict canonical path. Old generated bare-ID code therefore fails rather
  than silently extending the deferral.
- `InventoryError`: registered artifacts with a missing descriptor schema or
  schema 1/4, lower, malformed, or semantically incomparable releases,
  duplicate entries, partial matches, and registry/ledger disagreement fail
  with a stable structured diagnostic.

Release ordering is intentionally limited to numeric `major.minor.patch` for
this registry. Exact equality remains string equality. Qualifiers other than
the explicit SNAPSHOT classification are incomparable and never admitted.

## Raw Evidence and Canonical Projection

The four frozen release archives contain legacy schema-2 descriptors and ABI
manifests but no `car-runtime-manifest.json`. CAR extraction therefore keeps
raw and effective descriptor roles separate:

1. decode the raw archive descriptor without weakening the public strict
   `ComponentDescriptorLoader.loadArchive` contract;
2. classify the raw descriptor against the exact registry;
3. validate existing runtime/ABI evidence against the raw legacy coordinate;
4. for `ExactDeferred` only, permit absence of the newer runtime manifest,
   while still validating the legacy ABI manifest; an existing but corrupt or
   disagreeing runtime manifest is never bypassed;
5. carry the projected canonical descriptor and optional exact registry entry
   into repository materialization.

Packed and expanded CAR directories use the same classification. The absence
exception is limited to the exact four entries and does not weaken ordinary
CAR runtime-manifest or integrity admission.

## Scoped Legacy Generated-Core Boundary

`ComponentId.apply` and `ComponentId.parseC` remain strict by default. A new
package-internal, nested-safe `ThreadLocal` scope is installed only around
ClassLoader scanning, factory construction, and Component/Core creation for an
`ExactDeferred` CAR. Inside that scope:

- only the entry's exact legacy local ID maps to its expected canonical
  `ComponentId`;
- qualified IDs continue through the normal strict parser;
- artifact names, normalized spellings, different case, and all other bare IDs
  remain invalid;
- `ComponentInstanceId` string construction inherits the same exact mapping
  through `ComponentId`;
- `finally` restores the previous scope, and concurrent threads cannot observe
  each other's expected identity.

Repository admission still verifies that the resulting primary Core identity
equals the projected descriptor identity. Scope success cannot override that
postcondition.

## Observability and Removal

Each admitted deferred-release CAR records one deduplicated
`AssemblyWarning` containing the canonical Component ID, exact release, legacy
artifact, migration owner, and the reason `exact-deferred-release`. Repeated
factory/component discovery does not duplicate the warning. Admin assembly
report projection remains the only public warning surface.

The removal trigger is owned by the registry entry. A greater release must
migrate its descriptor and generated Core and then remove the matching registry
entry. CID-06D does not mutate the CID-01 ledger or the four CAR repositories.

## Executable Acceptance Matrix

- `E1`: load exactly four registry entries and prove registry/ledger parity.
- `E2`: project each exact legacy descriptor to its registered canonical ID
  while preserving non-identity metadata.
- `E3`: classify exact, greater, SNAPSHOT, lower, malformed, incomparable,
  missing, and partial-match coordinates deterministically.
- `E4`: adapt exact legacy local IDs through both `ComponentId` and
  `ComponentInstanceId` only inside their expected scopes.
- `E5`: prove nested restoration, exception cleanup, and cross-thread isolation.
- `E6`: load a packed legacy CAR fixture through descriptor, ABI,
  ClassLoader, factory, Core, repository admission, and canonical artifact
  metadata without a runtime manifest.
- `E7`: repeat the real boundary through an expanded CAR directory.
- `E8`: reject a higher/SNAPSHOT legacy factory, an unregistered legacy
  descriptor, a wrong artifact, and a wrong local ID without fallback.
- `E9`: preserve ordinary strict schema-3 admission and existing CID-06A/B/C
  behavior unchanged.
- `E10`: emit one deduplicated exact-deferred-release warning and expose it
  through the existing Admin assembly report.

## Planned Files

Production/resource:

- `src/main/resources/META-INF/cncf/component-identity-deferred-release-registry.json`
- `src/main/scala/org/goldenport/cncf/component/ComponentIdentityDeferredReleaseRegistry.scala`
- `src/main/scala/org/goldenport/cncf/component/ComponentIdentityDeferredReleaseScope.scala`
- `src/main/scala/org/goldenport/cncf/component/Component.scala`
- `src/main/scala/org/goldenport/cncf/component/ComponentDescriptorLoader.scala`
- `src/main/scala/org/goldenport/cncf/component/CarExtractor.scala`
- `src/main/scala/org/goldenport/cncf/component/CarRuntimeAdmission.scala`
- `src/main/scala/org/goldenport/cncf/component/ComponentIdentityCompatibilityObserver.scala`
- `src/main/scala/org/goldenport/cncf/component/repository/ComponentRepository.scala`

Executable specification/fixture:

- `src/test/scala/org/goldenport/cncf/component/Phase56DeferredReleaseCompatibilitySpec.scala`
- one package-local legacy generated-Core fixture under
  `src/test/scala/org/simplemodeling/textus/corpus/`
- bounded repository/Admin evidence in existing CID-06 target specs only when
  required by E6-E10.

Lifecycle documentation:

- this plan;
- the primary CID-06 plan;
- `phase-56.md` and `phase-56-checklist.md`.

## Non-goals

- No namespace inference from artifact, display, package, or class names.
- No general acceptance of schema-2 CARs.
- No SNAPSHOT or future-release compatibility deferral.
- No CAR rewrite, republish, repository-index schema change, or source-repo
  mutation.
- No global or inheritable Component identity relaxation.
- No CID-06 Step commit, CID-07 lint, or Phase full test.

## Exit Criteria

CID-06D can become `ACCEPTED / REVIEWED` only after E1-E10 pass, an
independent review has no actionable finding, any admitted finding is repaired
and independently re-reviewed, registry/ledger parity is exact, no scope leaks
after success/failure/concurrency, and the lifecycle docs retain the Step
commit as pending.

## Review-Fix Record

CID-06D production and executable-spec implementation is complete. The exact
four-entry JSON authority, fail-closed registry loading and classification,
raw/effective archive descriptor separation, exact missing-runtime-manifest
exception with mandatory ABI validation, nested thread-local generated-Core
scope, repository scan/factory/Core wrapper, canonical primary-Core
postcondition, and deduplicated AssemblyReport warning are implemented.
`Phase56DeferredReleaseCompatibilitySpec` owns E1-E10, including registry/
ledger parity, packed and expanded legacy Corpus CAR fixtures, strict negative
release/artifact/local-ID cases, nested cleanup, and cross-thread isolation.
RF-CID06D-001 through RF-CID06D-004 are applied: deferral is restricted to
schema 2, an existing non-regular runtime-manifest path fails admission, and
the specification now uses semantic `which` groups with executable
Given/When/Then boundaries and the expanded negative evidence.

Historical invocation `66284` failed during compilation. Invocation `66671`
compiled and reported three failures. Invocation `67421` completed five suites
with 109 tests passed, no warnings, `sbt_exit=0`, `wrapper_exit=0`, and
`lock=released`. That evidence predates and is superseded by these review
fixes. Post-fix invocation `74557-20260808T044055Z` completed the same five
suites with 109 tests passed, no warnings, `sbt_exit=0`, `wrapper_exit=0`, and
`lock=released`. Independent focused re-review verified the exact repaired
hashes, closed RF-CID06D-001 through RF-CID06D-004 without new findings, and
returned PASS with `FULL_REVIEW_REQUIRED=no`. CID-06E is also
accepted/reviewed; the CID-06 Step commit and Phase full validation remain
pending. Current status: `ACCEPTED / REVIEWED`.
