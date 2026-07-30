# Phase 52 P52-COMP-01 - Public Constant Canonicalization

Status: implementation and slice validation complete; Phase 52 closure pending

## Attributable Decision

On 2026-07-30, the Phase 52 owner selected a breaking migration from public
PascalCase constants to lower-camel canonical names, without compatibility
aliases. The admitted migration set is CNCF, `textus-sample-apps` (including
Cwitter and Notice Board), Knowledge Editor, and Semantic Integration Engine.

## Contract

- The 158 selected `RuntimeConfig` public values use lower-camel names.
- The 36 Information Model facade values use lower-camel names.
- ``InformationCapabilities.`import` `` is escaped because `import` is a
  Scala keyword.
- Lower-camel constants used as Scala pattern alternatives are enclosed in
  backticks so they remain stable-identifier patterns rather than variable
  bindings.
- Configuration keys, capability strings, Information state values, defaults,
  and runtime behavior remain unchanged.
- Downstream source callers resolve `goldenport-cncf` `0.5.2-SNAPSHOT`.

## Slice Boundary

The initial caller inventory undercounted the migration. The corrected
inventory contains 93 Scala files: 64 files with member replacements, 28
version-only companions, and the SIE runtime-compatibility specification. The
additional SIE specification follows the dependency metadata from
`0.5.1-SNAPSHOT` to `0.5.2-SNAPSHOT`, so generation sees one selected runtime
contract. SIE's older sbt-cozy plugin needs the same explicit
`generation.versions.cncf` override already used by Knowledge Editor; without
it the delegated generator defaults to `0.5.1` while the extracted descriptor
correctly reports `0.5.2-SNAPSHOT`. Existing uncommitted Phase 52 work overlaps
some of those files; only the canonicalization identifier, header,
specification, and metadata hunks belong to P52-COMP-01.

The SIE current-development dependency audit is part of this boundary: its two
runtime contract rows must match the same `0.5.2-SNAPSHOT` source of truth.
The Docker KS-14 runbook's current-runtime statement follows that same source
of truth; version-comparison fixture values remain intentionally unchanged.

The migration explicitly excludes compatibility aliases, old-name reflection
tables, configuration-schema changes, CAR adoption, and source changes outside
the four admitted repositories.

## Implementation Evidence Required

Before review, the slice requires zero old declaration/reference matches,
unchanged right-hand-side values, an exact ordered
`InformationCapabilities.all` vector, focused framework and downstream
compilation/specification evidence, and `git diff --check`. Full suites remain
reserved for the Phase 52 release-commit stage.

## Implementation Validation

Recorded on 2026-07-30 JST:

- Framework `Test/compile` passed. The focused runtime and information suite
  passed 100/100 tests across 9 suites, and `publishLocal` installed
  `goldenport-cncf` `0.5.2-SNAPSHOT` for admitted consumers.
- Cwitter `Test/compile` passed; its focused auth flow passed 1/1.
- Knowledge Editor `Test/compile` passed; its focused factory, scenario, and
  migration suite passed 127/127.
- SIE required an explicit `generation.versions.cncf` bridge override to carry
  the exact project dependency into its older delegated generator. After that
  metadata-only correction, `clean Test/compile` passed: 64 generated, 92 main,
  and 33 test Scala sources compiled. `InformationKnowledgeEngineProviderSpec`
  passed 3/3 and `ProjectRuntimeCompatibilitySpec` passed 1/1.
- The public-declaration gate found no remaining PascalCase public values. The
  qualified old-name gate found only `RuntimeConfig.DebugAuthConfig`, which is
  a type rather than a migrated value. `git diff --check` passed in all four
  admitted repositories.

## Closure Relationship

P52-COMP-01 is complete as a source and focused-validation slice. It remains
uncommitted only because the enclosing Phase 52 release gate also covers exact
Entity ID canonicalization and must pass its independent review and full-suite
validation before a grouped release commit is safe. This records the completed
constant migration without falsely certifying the broader phase.
