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
