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

## Function Platform Profile

Phase 92 also owns the distribution contract for generated Application-tier Function Subcomponents.

The implementation must preserve these invariants:

- the primary CNCF Container artifact remains the complete realization and includes all Application Operations;
- only function-eligible Operations are projected into Function Subcomponents;
- function eligibility is an upstream semantic input from Phase 75, not a provider-specific Phase 92 decision;
- common generated TypeScript logic is shared at source/build time but copied into each platform Function Subcomponent CAR at packaging time;
- AWS, Google Cloud, and local/test realizations use thin adapters around the same common function logic;
- each platform Function Subcomponent CAR is self-contained and does not require a separate common-function CAR at runtime;
- the local/test adapter supports Function-to-local-CNCF-Domain integration tests without attempting to emulate a cloud provider;
- Container and Function realizations preserve the same logical Operation identity and admitted semantics.

Provider deployment implementation remains outside CAR admission. Phase 92 defines distribution evidence and launcher/deployer handoff, not automatic cloud deployment.

