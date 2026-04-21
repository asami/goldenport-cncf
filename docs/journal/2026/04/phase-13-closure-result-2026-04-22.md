# Phase 13 Closure Result

Date: 2026-04-22
Target: `/Users/asami/src/dev2025/cloud-native-component-framework`

## Summary

This slice closes Phase 13 as a framework/runtime phase.

The remaining work was not a new event feature. It was closure/hardening on top
of the already accepted runtime:

- async failure disposition is now treated as a fixed contract
- event/job/admin visibility uses one stable field contract
- policy selection precedence is treated as a closed rule
- bundle/provider discovery is hardened with deterministic bundle validation
- runtime componentlet identity is treated as a formal rule, not only a
  currently working behavior

## Runtime Closure

The following are now treated as fixed Phase 13 runtime contracts:

### Async failure disposition

- `NotApplicable`
- `Retryable`
- `Terminal`

The intended split remains:

- event surfaces expose dispatch contract/base disposition
- job surfaces expose final failure disposition

### Policy precedence

The runtime order is fixed as:

1. explicit rule
2. compatibility mapping
3. subsystem default

Future ABAC belongs inside explicit-rule conditions and does not become a
separate override tier.

### Bundle/participant construction

The construction model is now explicit:

- `BundleFactory`
- `PrimaryComponentFactory`
- `ComponentletFactory`

Runtime participant behavior remains symmetric after construction, but
construction-time asymmetry is now part of the framework contract.

### Componentlet runtime identity

The formal rule is:

- real runtime componentlets are first-class runtime `Component`
- metadata-only componentlets are not runtime participants
- source/target identity always uses resolved runtime participant name
- root alias must not replace runtime componentlet identity

## Hardening Added

Added bundle validation at construction time:

- primary is required
- null participants are rejected
- primary must not appear inside componentlets
- duplicate participant names fail deterministically

This closes the malformed-bundle path before bootstrap/discovery proceeds.

## Executable Protection Added

Added/extended protection for:

- malformed bundle rejection
- generated-style bundle construction
- generated-style runtime componentlet identity preservation
- provider companion bundle-factory discovery path

## Bookkeeping Result

`phase-13.md` and `phase-13-checklist.md` are now aligned with the actual
runtime/spec state.

Phase 13 should be treated as closed in CNCF.

## Deferred Beyond Phase 13

Still outside this phase:

- dead-letter / poison-event handling
- retry orchestration
- finalized saga-id standardization
- ABAC execution itself
- inter-subsystem external transport

These are not blockers for Phase 13 closure.
