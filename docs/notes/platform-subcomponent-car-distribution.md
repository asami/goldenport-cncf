# Platform Subcomponent CAR Distribution

## Purpose

Extend CAR distribution so a logical Subcomponent may carry platform-specific native artifacts such as a macOS application.

## Model

Subsystem/Subcomponent is the logical structure. CAR is a physical distribution unit. Do not encode bundled/separate as an intrinsic Subcomponent property.

A Subcomponent may be distributed either:

- bundled in the parent CAR; or
- in a separate physical CAR.

Both forms resolve to the same logical Subcomponent at installation/runtime.

Platform metadata should identify at least OS, architecture, artifact kind and artifact location/reference. The mechanism must not be macOS-specific; it should be reusable for Linux/Windows/native/Flutter desktop artifacts.

## Reference case

textus-control-center provides the first reference case: its macOS Menu Bar application is a presentation Subcomponent, normally bundled in the control-center CAR, while remaining eligible for separate-CAR distribution.

## Function Subcomponent profile

Cloud-function realizations use the same Platform Subcomponent distribution model.

For an Application Service:

- the primary CNCF Container realization is complete and contains every Operation;
- the function realization contains only Operations admitted as function-eligible;
- one provider-neutral TypeScript function core is generated/developed at source level;
- packaging copies that common core into each platform-specific Function Subcomponent CAR together with its adapter and deployment metadata;
- each platform Function Subcomponent CAR is self-contained.

Initial platform profiles are local/test, AWS, and Google Cloud. The exact encoded role/profile vocabulary is subject to the Phase 92 schema freeze.

The local adapter implements the CNCF Function ABI boundary so the generated function can be tested locally, including integration with a local CNCF Domain Subsystem. It does not emulate AWS or Google Cloud. Provider-specific adapter contract tests and bounded live-cloud acceptance tests cover provider behavior.

The same logical Operation identity is preserved across Container and Function realizations. Function candidacy is supplied by the Application Operation contract (Phase 75); Phase 92 owns packaging/distribution and does not redefine candidacy.

