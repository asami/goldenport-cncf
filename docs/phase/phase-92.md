# Phase 92: Platform Subcomponent CAR Distribution

## Goal

Make CAR a distribution unit capable of carrying heterogeneous platform-specific Subcomponents while preserving the distinction between logical Subcomponent structure and physical packaging.

## Scope

- Define platform artifact metadata: OS, architecture, artifact kind and artifact location/reference.
- Support a Subcomponent bundled in a parent CAR.
- Support the same logical Subcomponent distributed as a separate CAR.
- Define resolution semantics independent of packaging choice.
- Define extraction/install handoff contract for launchers.
- Use textus-control-center macOS Menu Bar application as the reference case.
- Keep the model extensible to Linux, Windows and other native/desktop artifacts.

## Non-goals

- macOS-specific installation policy belongs to launcher/Textus integration.
- Application-specific status/health semantics belong to textus-control-center.
