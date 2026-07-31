# Phase 53 CS-02C: Catalog Handoff and Selection Admission

Date: 2026-07-31

Status: implementation evidence

## Decision history

CS-02A made the framework the sole owner of the versioned ComponentStyle
catalog. CS-02B preserved the authored unversioned `COMPONENT` `STYLE` value
in Kaleidox without making that CML semantic owner depend on CNCF. This slice
connects those two boundaries across the existing Scala 3 runtime / Scala 2
Cozy generator boundary.

## Implemented boundary

The generated CNCF runtime descriptor carries the exact catalog resource
identity, Base64-encoded catalog bytes, and lowercase SHA-256 digest. Cozy
first validates the selected descriptor and then validates the carrier before
interpreting the catalog. It consumes that carrier only; there is no Cozy-owned
catalog source and Kaleidox does not load CNCF metadata.

An explicit `full-fledged-with-standalone` selection resolves to
`full-fledged-with-standalone@1`. An explicit unavailable `domain-only`
selection fails before model generation. A legacy component without `STYLE`
remains parse-compatible and does not require a catalog selection.

## Deferred work

This slice does not emit a Component descriptor-v2 snapshot, add style or
capability declarations to `project.yaml`, implement generic capability
definition validation, or establish development/packaged parity. Those remain
CS-02 or later Phase 53 work.
