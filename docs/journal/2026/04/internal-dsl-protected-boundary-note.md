# Internal DSL Protected Boundary Note

Date: 2026-04-07

## Summary

If CNCF moves to an internal DSL-centric authorization model, the first design task
is to define the protected boundary precisely.

The protected boundary should be placed at the framework-owned domain/system access
APIs used by `ActionCall` logic, rather than at CLI or HTTP entry points.

In current CNCF implementation terms, the most natural boundary candidates are:

- `EntityStoreSpace`;
- `UnitOfWorkInterpreter`;
- framework-owned system service engines such as event/job/control engines.

`ActionCall` implementations should be guided toward these protected APIs and away
from direct low-level runtime access.

## Observed Current Access Paths

Current application code still reaches storage and query behavior through a mix of:

- generated action calls;
- handwritten `ComponentFactory` action-call implementations;
- entity store helpers;
- action-engine authorization hooks.

The concrete persistence/search execution path already converges on framework-owned
interpreter layers:

- `UnitOfWorkInterpreter`
- `EntityStoreSpace`

This is a strong sign that the authorization boundary should also converge there.

## Proposed Protected Boundary

### Domain Object Side

For domain objects, the protected boundary should sit in front of:

- create;
- load;
- save;
- update;
- delete;
- search;
- searchDirect;
- loadDirect.

In current implementation, these operations appear as `UnitOfWorkOp.EntityStore*`
operations interpreted by `UnitOfWorkInterpreter`, which delegates to
`EntityStoreSpace`.

This suggests the following architectural rule:

1. `ActionCall` logic should not treat `EntityStoreSpace` as an unrestricted store.
2. Domain-object access should be exposed through protected internal DSL entry
   points.
3. Those entry points should perform authorization before the underlying
   `EntityStoreSpace` or `UnitOfWorkInterpreter` operation is executed.

### System Object Side

For system objects, the protected boundary should sit in front of framework-owned
engines and stores, such as:

- job control;
- event publication/replay/introspection;
- system configuration and diagnostics;
- runtime control/admin APIs.

The same principle applies:

- `ActionCall` logic uses protected internal DSL;
- the DSL checks capability-oriented authorization;
- direct low-level engine access should not be the normal application path.

## Default Authorization Flow

The intended future flow is:

1. `ActionCall` requests domain/system behavior through internal DSL.
2. Internal DSL classifies the target as system object or domain object.
3. Internal DSL applies default authorization:
   - system capability authorization for system objects;
   - resource authorization for domain objects;
   - `SimpleEntity` default policy for `SimpleEntity`.
4. Internal DSL delegates to framework execution/storage layers only after
   authorization succeeds.

## Position of ActionCall.authorize()

`ActionCall.authorize()` should remain useful, but its role should narrow.

It should be used for:

- coarse-grained action admission;
- explicit exception declarations from generated metadata;
- promotion/degradation/bypass semantics.

It should not be the only enforcement point for ordinary domain resource access.

Ordinary authorization should also hold when a handwritten action calls the domain
object DSL incorrectly or from an unexpected entry point.

## Transitional Interpretation of Current Runtime

The current runtime is transitional:

- `ActionCall.authorize()` exists and is active;
- `ENTITY` / `ACCESS` metadata exists;
- `OperationAccessPolicy` contains helpers for `manager_only` and
  `SimpleEntity` owner-oriented checks;
- application action logic can still perform low-level store access patterns.

This is acceptable in the short term, but the long-term boundary should be pushed
downward into protected internal DSL APIs.

## Near-Term Design Target

The next implementation target should be:

1. identify or introduce internal DSL entry points for domain object access;
2. ensure those DSL entry points own the authorization checks;
3. make direct raw-store access non-canonical for handwritten application logic;
4. keep `SERVICE` / `OPERATION` authorization declarations for exceptional cases
   only.

## Working Rule

Until the full internal DSL is in place, the working rule should be:

- continue using `ActionCall.authorize()` and operation metadata for protection;
- treat them as transitional enforcement;
- design new domain/system APIs so that authorization can migrate into the DSL
  layer without changing the external model.
