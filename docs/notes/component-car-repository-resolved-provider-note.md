# Component CAR With Repository-Resolved Providers

Status: note
Date: 2026-04-26

## Purpose

A small application should be able to start from its own component CAR without
manually constructing a SAR for every development check.

This does not create a composite CAR format. A CAR remains the archive of one
component. A provider component is still a normal CAR that declares provider
capability; it is resolved from repository search sources.

## Model

The application CAR may contain component-local runtime defaults:

- `component-descriptor.json`
- `assembly-descriptor.yaml`
- `web/web.yaml`
- component-owned Web assets under `web/`

The assembly descriptor inside the component CAR declares required components
and wiring defaults. It does not contain provider component artifacts.

Provider component CAR artifacts such as `textus-user-account.car` are resolved
from:

1. the standard component repository
2. `repository.d/` during local development before the standard repository is
   populated

## Runtime Shape

The intended development startup is:

```bash
cncf server --component-file target/cwitter.car
```

The selected component CAR is the active application component. Its
component-local assembly metadata creates the synthetic subsystem descriptor.
Additional component bindings are resolved by name from repository search
sources.

## SAR Boundary

SAR remains the deployment packaging unit for subsystem assembly. SAR may bundle
or reference multiple components and override application CAR defaults.

Component CAR defaults are convenience defaults, not a second subsystem archive
format.
