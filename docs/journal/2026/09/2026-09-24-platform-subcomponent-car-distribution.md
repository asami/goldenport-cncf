# Platform Subcomponent CAR Distribution

Date: 2026-09-24

## Decision

CAR will support platform-specific artifacts as first-class Subcomponent distribution content.

The logical model and physical packaging are deliberately separated:

- Subsystem/Subcomponent describes the logical system.
- CAR describes a physical distribution unit.
- A Subcomponent may be embedded in a parent CAR or delivered by a separate CAR without changing its logical identity.

The initial reference implementation is the textus-control-center macOS Menu Bar application. The design is intentionally generalized beyond .app bundles.

## Coordination

cncf-launcher resolves and extracts platform Subcomponents. textus-launcher performs Textus/macOS installation integration. textus-control-center supplies the reference native Subcomponent and operational API.
