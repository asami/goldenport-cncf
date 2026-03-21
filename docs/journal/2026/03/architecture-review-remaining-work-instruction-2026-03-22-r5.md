# Architecture Review Remaining Work Instructions (R5)

status=open
date=2026-03-22
owner=development-thread
scope=remaining-items-after-r4

## Purpose

Define only the remaining architecture-review follow-up work after:

- command parse-path unification (`class CncfRuntime` as canonical path),
- HTTP canonical/compatibility route executable coverage,
- selector contract decision log and spec/manual alignment.

This document is a work instruction for unresolved items only.

## Remaining Item

### `ARCH-R5-P1-01` Legacy config executable specs are still pending

#### Problem

Legacy config behavior is documented, but core executable specs remain `pending`:

- `src/test/scala/org/goldenport/cncf/config/ConfigResolverSpec.scala`
- `src/test/scala/org/goldenport/cncf/config/source/ConfigSourcesSpec.scala`
- `src/test/scala/org/goldenport/cncf/config/source/ConfigSourceSpec.scala`

This leaves architecture-level behavior without regression guards.

#### Required Change

Replace pending tests with executable specifications that verify:

1. `ConfigSource.project(cwd)` and `ConfigSource.cwd(cwd)` effective behavior.
2. Source ordering contract from `ConfigSources.standard(...)`:
   `HOME < PROJECT < CWD < ENV < ARGS`.
3. Resolver merge determinism and overwrite semantics for same keys.
4. Missing file handling remains graceful (no hard failure for absent optional sources).

#### Style and Rules

- Follow repository executable-spec policy:
  - Given / When / Then structure
  - Property-Based Testing (ScalaCheck) where applicable
- Keep behavior implementation-first and consistent with:
  - `docs/spec/config-resolution.md`
  - current legacy package behavior (`org.goldenport.cncf.config.*`)

#### Acceptance Criteria

- No `pending` remains in the three target spec files.
- Specs pass consistently in local `sbt --batch`.
- Spec text and assertions match current documented legacy behavior.

#### Verification Command

- `sbt --batch "testOnly org.goldenport.cncf.config.ConfigResolverSpec org.goldenport.cncf.config.source.ConfigSourcesSpec org.goldenport.cncf.config.source.ConfigSourceSpec"`
- `sbt --batch compile`

## Out of Scope (Already Addressed)

- Parse/run mismatch for `--path-resolution` command route.
- class/object command parser authority clarification.
- HTTP canonical slash route and compatibility dot route check.
- Selector public contract vs compatibility-policy documentation.

## Definition of Done

The item is done when all acceptance criteria above are satisfied and
test evidence is attached in the development thread.
