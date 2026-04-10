# Component Activation Policy Note

## Purpose

Record the current activation-policy direction for CNCF runtime behavior.

The immediate goal is to distinguish clearly between:

- discovery
- search eligibility
- activation

This note is meant to support both:

- sample simplification
- production-safe runtime behavior

## Problem Statement

Early CNCF development benefited from permissive behavior:

- discover components aggressively
- make discovered components available quickly

That approach was effective during bootstrap, but it now creates noise in a more mature runtime with:

- subsystem descriptors
- component and subsystem archives
- assembly warnings
- wiring
- admin observability

The open question is no longer only "can the runtime find the component?".
It is also:

- should the found component become active automatically?

## Agreed Direction

### 1. Development-Time Sources May Auto-Activate

The following sources are allowed to auto-activate:

- `--discover=classes`
- `car.d`
- `sar.d`

Rationale:

- these are development-time or debug-time shapes
- their purpose is immediate execution of the current work
- requiring explicit activation here would reduce DX without much benefit

### 2. Packaged Artifacts Should Not Auto-Activate By Default

Packaged artifacts should remain discoverable and searchable, but should not auto-activate by default.

This includes:

- `repository.d/*.car`
- packaged subsystem artifacts that are not explicitly selected

Rationale:

- packaged artifacts behave more like deployable inventory than like an active work area
- auto-activating every discovered packaged artifact in a search repository increases ambiguity and noise
- explicit selection is more suitable for predictable CLI and production behavior

### 3. Packaged Components Should Be Explicitly Selected

The intended direction is:

- packaged subsystem artifact
  - explicitly selected by subsystem name
- packaged component artifact
  - explicitly selected by component name
- packaged search repository
  - `repository.d`
- active packaged source
  - `component.d`

The current runtime shape is:

- `--textus.runtime.component=<name>`
  - search packaged component repositories
  - activate the packaged component source that contains the selected component
- `--repository-dir <path>`
  - add packaged artifacts to the search repository
- `--component-dir <path>`
  - add packaged artifacts to the active component source

This keeps component and subsystem activation conceptually symmetric.

### 4. Discovery Does Not Imply Activation

The following concepts must remain distinct:

- discovery
  - the runtime can find an artifact or source
- search eligibility
  - the artifact may participate in resolution or assembly lookup
- activation
  - the artifact becomes part of the effective runtime set

The intended runtime rule is:

- not everything discovered becomes active

## Intended Baseline Policy

- `--discover=classes`
  - discover + activate
- `car.d`
  - discover + activate
- `sar.d`
  - discover + activate
- `repository.d/*.car`
  - discover/search only
  - no default activation
- `component.d/*.car`
  - active packaged source
- packaged subsystem artifact
  - explicit subsystem selection

## DX And Operations

This direction is intended to improve both DX and operational safety.

Expected benefits:

- active development remains fast
- packaged artifacts do not become noisy by default
- subsystem and component activation become easier to explain
- the same policy can be used consistently in samples and production

## Follow-Up

Implementation follow-up should include:

- component activation policy in runtime behavior
- possible explicit component-selection parameter
- review of sample options such as:
  - `--component-repository` as a legacy search alias
  - `--repository-dir`
  - `--component-dir`
  - `--no-default-components`
- removal of now-unnecessary options where the new baseline behavior is sufficient
