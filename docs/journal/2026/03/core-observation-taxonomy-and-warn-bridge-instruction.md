# Core Instruction: Observation Taxonomy and Warn Bridge

## Status

This document is a provisional implementation request from CNCF to goldenport-core.
It is non-normative and intended for coordination.

## Purpose

CNCF needs structured observation data for component class-loading failures, and a
standard bridge from `Observation` to warning logs.

This request intentionally excludes dedicated factory shortcuts such as:

- `Observation.componentClassLoadError(...)`

The immediate target is:

1. taxonomy/facet vocabulary support
2. `Observation -> warn` bridge support

## Background

In `component.d` discovery, class loading may fail with errors such as:

- `NoClassDefFoundError`
- `ClassNotFoundException`
- `LinkageError`

At runtime, CNCF must skip incompatible artifacts after observing the failure.
Today this is handled mostly as ad-hoc warning strings. We want structured
observation semantics in core so CNCF can emit consistent warning records.

## Requested Scope

### 1. Taxonomy and Facet Vocabulary (core)

Add or standardize vocabulary for component-loading failures so callers can avoid
message-only diagnostics.

Expected minimum outcome:

- A clear taxonomy mapping for load-time class failures under component/repository context.
- Facets that can represent class-loading context without custom parsing.

Suggested taxonomy usage (example policy, final naming is core-owned):

- category: `Component`
- symptom:
  - `Unavailable` for dependency missing / classpath gap
  - `Invalid` for binary incompatibility / linkage mismatch
  - `Corrupted` for malformed artifacts when applicable

Suggested facets (use existing ones where possible, add only if necessary):

- class name
- artifact identity (file/jar/car name)
- repository type (e.g. `component-dir`)
- exception class/message (already possible via `Descriptor.Facet.Exception`)

Constraints:

- Observation remains descriptive only (no control-flow semantics encoded here).
- Do not require CNCF-side parsing of free-form message strings.

### 2. Observation-to-Warn Bridge (core)

Provide a stable, reusable way to turn `Observation` into warning output material.

Expected minimum outcome:

- A canonical compact warning message renderer for `Observation`.
- A canonical key-value attribute projection suitable for structured logging.

The bridge should allow CNCF to do:

- `observe_warn(renderedMessage)`
- optionally attach projected attributes when backend supports structure

Suggested API shape (illustrative):

```scala
object ObservationRender {
  def warnMessage(observation: Observation): String
}

object ObservationProject {
  def warnAttributes(observation: Observation): Map[String, String]
}
```

Notes:

- API naming is not fixed; behavior contract is the requirement.
- Message rendering should be deterministic and short enough for bootstrap logs.
- Attribute keys should be stable and machine-consumable.

## Non-Goals

- No dedicated per-case convenience factory in this request.
- No CNCF-specific logging backend behavior in core.
- No interpretation/disposition decisions from observation data.

## Acceptance Criteria

1. CNCF can represent component class-load failures using core taxonomy/facet vocabulary.
2. CNCF can generate warning text from `Observation` using core-provided rendering.
3. CNCF can project structured warning attributes from `Observation` without custom string parsing.
4. Existing `Consequence/Conclusion/Observation` semantics remain backward compatible.

## CNCF-side Integration Intent (for reference)

Once core support is available, CNCF `ComponentRepository` will:

1. create `Observation` with the new/standardized taxonomy and facets
2. call the core warn bridge to get message (+ attributes when available)
3. emit warning and continue discovery (skip invalid/incompatible artifact)

