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

1. the standard `simplemodeling.org` component repository for published
   standard components
2. `repository.d/` when a provider CAR has been obtained locally and should be
   searchable without becoming an active component by itself
3. local `.textus.conf` development-directory overrides when the provider
   component is being developed at the same time as the application component

`.textus.conf` is a local development file and must not be committed. Shared
documentation should describe the expected keys; each developer keeps concrete
paths in their own checkout.

## Runtime Shape

The intended development startup is:

```bash
cncf --component-dev-dir . server
```

The selected component development directory is the active application
component. Its component-local assembly metadata creates the synthetic subsystem
descriptor. Additional component bindings are resolved by name from repository
search sources or from `.textus.conf` development-directory overrides.

## SAR Boundary

SAR remains the deployment packaging unit for subsystem assembly. SAR may bundle
or reference multiple components and override application CAR defaults.

Component CAR defaults are convenience defaults, not a second subsystem archive
format.
