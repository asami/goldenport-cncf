# Phase 53 CS-03: Development Projection Parity

## Decision

`cozyPrepareRuntime` writes development runtime manifest v2. The v2 evidence
uses the generated `target/cncf.d/component-descriptor.json`, so development
and packaged CAR paths consume the same schema-v2 component-style projection.

CNCF retains acceptance of digest-valid manifest v1 directories. Style-less
legacy CML continues to write that form; its runtime loader consumes the same
historic source descriptor identity `src/main/car/component-descriptor.json`
that the manifest digests.

## Authority and compatibility boundary

- A v2 descriptor must be schema version 2 and contain the exact
  catalog-matching component-style snapshot before development activation.
- A generated style snapshot remains the only descriptor authority.
- CML without a selected style remains a legacy route: its source descriptor
  is copied for development activation only when it omits `componentStyle` and
  declares numeric schema version 1 or no schema version. A style-less CML
  source cannot supply schema v2 or `componentStyle` through either
  `component-descriptor.json` or `componentDescriptorJson`.

## Evidence

- `DevelopmentCarRuntimeAdmissionSpec` proves strict v2 descriptor rejection,
  migration acceptance, stale evidence rejection, and recovery guidance.
- `CozyCarRuntimeManifestSpec` proves packaged/development descriptor parity
  and the style-less CML source-descriptor fallback, including actual generated
  style-less metadata through the v1 manifest route and v2/style-bearing source
  rejection.
- `CozyArchivePackagerSpec` proves the packaged source and
  `componentDescriptorJson` fail closed for style-less CML when either tries to
  introduce a v2 or style-bearing descriptor.
- Post-fix independent review passed after the source-authority and legacy
  loader checks were added. Full validation passed on 2026-07-31: framework
  `sbt --batch test` 2,687/2,687; Cozy 779/779; Kaleidox 123/123; and
  simplemodeling-lib 388/388.
