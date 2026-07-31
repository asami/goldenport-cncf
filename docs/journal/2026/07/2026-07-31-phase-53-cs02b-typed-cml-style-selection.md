# Phase 53 CS-02B: Typed CML Component Style Selection

Date: 2026-07-31

Status: implementation evidence

## Decision history

The Phase 53 planning record assigns the typed `COMPONENT` CML semantic model
to Kaleidox.  CS-02A deliberately established the CNCF-owned versioned catalog
without making Kaleidox load or interpret that catalog.  This slice closes the
gap between authored CML and the typed semantic model only.

## Implemented boundary

`ComponentDefinition.componentStyle` preserves the trimmed authored `STYLE`
subsection value as an optional, unversioned string.  A component without a
`STYLE` remains compatible with `None`.

Kaleidox does not append a catalog version, choose a provider, load CNCF
metadata, validate availability, expand capabilities, or generate a runtime
descriptor.  Cozy consumes the typed field directly in its component-only CML
evidence; it does not add a parallel parser.

## Evidence and remaining work

The Kaleidox executable specification proves two distinct explicit selections
and legacy absence.  Cozy proves that its existing two component-only fixtures
retain `full-fledged-with-standalone` and `domain-only` through the public
typed model.  The latter remains CML evidence only; catalog availability is a
later CS-02 slice.

CS-02 remains in progress.  Shared catalog consumption, unknown-style
rejection, capability-definition validation, descriptor generation, and
component-only generation independence are intentionally not completed here.
