# Phase 57.4 Checklist - Build and Publication Compatibility Retirement

status=planned
phase=[Phase 57.4 - Build and Publication Compatibility Retirement](phase-57.4.md)
predecessor=[Phase 57.3](phase-57.3.md)
successor=[Phase 57.5](phase-57.5.md)

## AES-06B: Build/Publication Compatibility Retirement

Stage Status:
- Current status: PLANNED
- Entry rule: Phase 57.3 is DONE.
- Completion rule: Canonical build/publication evidence and a fresh active
  local warehouse replace unreleased legacy production paths.

- [ ] Inventory Cozy, sbt-cozy, packager, publisher, lint, and index writers.
- [ ] Remove unauthorized legacy conversion, v1 output, alias, and fallback.
- [ ] Preserve the full local warehouse as a timestamped forensic sibling when
  requested; do not read it from active runtime lookup.
- [ ] Create a clean active warehouse.
- [ ] Fresh-build and publish canonical `-SNAPSHOT` libraries/plugins/CARs in
  dependency order.
- [ ] Verify representative schema 3, ABI v2, index v2 metadata and runtime
  consumption.
- [ ] Review once and commit the accepted migration.
