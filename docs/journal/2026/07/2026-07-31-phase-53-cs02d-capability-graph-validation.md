# Phase 53 CS-02D - Capability Graph Validation

date=2026-07-31
status=completed
phase=53
scope=CS-02D

## Decision

`capabilityBundles` is a closed, catalog-local directed graph. Each bundle has
an identity, a canonical `bundles` vector, and a canonical terminal
`capabilities` vector. The initial `domain.full@1` bundle has an empty nested
bundle vector.

The catalog admits no duplicate bundle identity or entry, unknown nested
bundle, cycle, duplicate expanded terminal capability, or multiple major
versions of one unversioned identity in a bundle or style closure. Terminal
capability identifiers remain catalog payload values; CS-02D does not invent a
separate global registry for them.

The review-fix also freezes one shared catalog identity grammar: each identity
part is ASCII `[a-z0-9-]+`; styles may be unqualified or family-qualified; and
capabilities, bundles, and Subsystem capabilities are family-qualified with a
canonical positive `@major`. These catalog identifiers are not UniversalIds,
whose label grammar is intentionally different.

## Rules

- CS02D-R1: every bundle definition and every style closure is validated,
  including otherwise unreachable bundles.
- CS02D-R2: duplicate, unknown, cyclic, or major-incompatible graph inputs
  fail before descriptor projection or explicit style admission.

## Ownership

The framework owns the Scala 3 catalog model, recursive expansion, and the
runtime-descriptor resource projection. Cozy remains a Scala 2 consumer of
that exact digest-pinned payload and validates the same graph before explicit
CML style admission. Kaleidox continues to own only the unversioned typed CML
selection.

## Evidence

- Framework `ComponentStyleCatalogSpec` rejects duplicate, unknown, cyclic,
  and version-incompatible graph definitions; the focused framework run passed
  4/4 tests and `Test/compile` after regenerating the runtime descriptor.
- Cozy `ComponentStyleCatalogSpec` sends the same invalid graph classes through
  a digest-pinned descriptor carrier; the focused Cozy run passed 21/21 tests
  and `Test/compile`.
- The updated Cozy generator was published locally as
  `org.simplemodeling:cozy_2.12:0.3.1-SNAPSHOT` only so the framework's normal
  local source-generation dependency could validate the same catalog schema.
- The follow-up parity repair passed Cozy `Test/compile` and 22/22 focused
  catalog/CML tests, then framework descriptor generation, `Test/compile`,
  and 8/8 focused catalog/projection tests. Both consumers accept the same
  family-qualified ASCII style corpus and reject Unicode identity parts and
  noncanonical major versions.
- The executable-specification follow-up gives every carrier, graph, grammar,
  and overflow example its own CS02A/CS02D-bound `afterWord` metadata and
  Given authority trace. It passed Cozy `Test/compile` with 40/40 focused
  tests and framework descriptor generation, `Test/compile`, and 18/18
  focused catalog/projection tests without compiler warnings.

## Boundary

This closes the CS-02 capability-definition rejection criterion only. Explicit
component-only generation and the `project.yaml` no-duplication proof remain
the separate CS-02E slice. Development and packaged runtime parity remain
CS-03.
