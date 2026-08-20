# Phase 58.4 Checklist - Operation-Mode and Development Composition

status=planned
phase=[Phase 58.4 - Operation-Mode and Development Composition](phase-58.4.md)
predecessor=[Phase 58.3](phase-58.3.md)
successor=[Phase 58.5](phase-58.5.md)

## RSC-05: Operation-Mode and Development Composition

Stage Status:
- Current status: PLANNED
- Owner: CNCF launcher, runtime, and development resolver maintainers
- Entry rule: Phase 58.3 RSC-04 is DONE.
- Completion rule: Operation mode selects one runtime-owned composition policy without entering Component domain code or deploying platform-specific child artifacts.

- [ ] Implement Develop precedence across explicit directory, development-local, expanded, local, cache, and remote sources.
- [ ] Define structured development-readiness failure for required missing, stale, corrupt, or incompatible resources.
- [ ] Keep Test deterministic with no implicit remote access.
- [ ] Require explicit Demo policy for remote Documentation retrieval.
- [ ] Keep Production primary-only capable.
- [ ] Require explicit child activation and platform-native deployment handoff for Subcomponents that carry external-platform artifacts.
- [ ] Prevent automatic Production source resolution, mounting, or fetch.
- [ ] Keep `OperationMode` out of Component implementation APIs.
- [ ] Verify development and packaged parity.

Evidence:
- Pending.
