# Phase 13 Sync-Inline Hardening Result

Date: 2026-04-21
Target: `/Users/asami/src/dev2025/cloud-native-component-framework`

## Summary

This slice hardened the same-subsystem sync-inline baseline after the first
real sample app flow exposed framework gaps.

The resulting framework contract is:

- one `FunctionalActionCall` program runs in one `UnitOfWork`
- same-subsystem sync reception stays inline and does not spawn a child job
- persistent event commit happens once, at the outer action commit
- persisted event metadata remains framework-owned
- subsystem-owned event facilities are rebound consistently when bootstrapped
  components are added back to the subsystem

## Runtime Fixes

- `ComponentLogic` now builds action-scoped `UnitOfWork` with the component's
  effective `EventStore`
- the runtime `unitOfWorkInterpreter` now interprets single operations instead
  of re-running and auto-committing each operation independently
- `EventReception` sync-inline path dispatches across subsystem-owned
  receptions but leaves commit timing to the outer action
- `Subsystem.add` now rebinds event store / job engine / event reception
  registry back to subsystem-owned runtime services
- component-scoped source metadata uses the real runtime component name

## Regression Protection

The hardening is protected by the existing sync-inline specs plus an added
subsystem binding regression in:

- [EventReceptionSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/event/EventReceptionSpec.scala)
- [ComponentFactoryEventReceptionBootstrapSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/ComponentFactoryEventReceptionBootstrapSpec.scala)

Key behaviors now protected:

- no early commit before inline dispatch finishes
- one persisted event after outer commit
- rollback on inline follow-up failure
- real source/target component names in event metadata
- shared subsystem event store visible from bootstrapped components

## Next Step

With sync-inline semantics fixed, the next Phase 13 slice moves to
async/new-job continuation while keeping the sync baseline unchanged.
