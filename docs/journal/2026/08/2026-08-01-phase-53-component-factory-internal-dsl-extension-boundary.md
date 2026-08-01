# Phase 53: Component.Factory Internal DSL and Extension Boundary

Date: 2026-08-01

Status: review fixes applied; focused validation and re-review clean

## Question that triggered the review

`Component.Factory` exposes public methods that accept `ActionCall.Core` or a
`UnitOfWork`. Through those values, framework implementation code can reach an
`ExecutionContext` and other internal runtime state. This initially appeared to
conflict with the Phase 53 statement that a factory is construction-only and
mode-free.

The concrete examples included `create_aggregate_behavior(action, core)` and
the authorization and entity-security queries on `Component.Factory`.

## Structural finding

These methods are not a general Component-facing runtime API. They are invoked
from framework internal DSL machinery, principally `ActionCallFeaturePart`.
That feature part has the self type:

```scala
trait ActionCallFeaturePart extends BehaviorFeaturePart {
  self: ActionCall.Core.Holder =>
  // ...
}
```

`ActionCall.Core` contains the action, execution context, optional component,
and optional correlation ID required by that internal machinery. Passing the
Core follows from the current Holder-based internal composition. Replacing it
with `ActionCall`, or changing the Core boundary, is a separate API-design
question and is not required to settle the Factory extension boundary.

`create_aggregate_behavior`, in particular, is a factory operation in the
ordinary sense: it selects and returns an `AggregateBehavior` using the action
and the component-owned behavior bindings available through the internal call
context. Its need for internal evidence does not make the operation contrary to
the factory role.

## Decision

Do not reduce `ActionCall`/`ActionCall.Core` visibility merely to make the
Factory appear construction-only. The internal DSL must be able to use runtime
evidence to perform aggregate selection, authorization, access-policy
resolution, and related framework work. Hiding that evidence would break those
operations or require redundant forwarding facades.

Do not introduce a new Component facade or a defensive indirection layer for
this purpose. Existing `Behavior` and `ActionBehavior` DSLs remain the normal
Component implementation entry points. Direct traversal of raw context from a
Component implementation can be governed by lint without constraining the
framework's own DSL implementation.

The actual defect discovered here is the public extension surface:

- public methods use snake_case despite the public camelCase naming rule;
- methods that expose framework internal inputs are overridable even though no
  current Component extension contract requires overriding them.

The selected treatment is to rename these public methods to camelCase and make
them `final`:

| Current name | Selected name |
| --- | --- |
| `aggregate_collection_bindings` | `aggregateCollectionBindings` |
| `aggregate_behavior_bindings` | `aggregateBehaviorBindings` |
| `create_aggregate_from_record` | `createAggregateFromRecord` |
| `create_aggregate_behavior` | `createAggregateBehavior` |
| `authorize_operation_access` | `authorizeOperationAccess` |
| `authorize_operation_entity` | `authorizeOperationEntity` |
| `authorize_unit_of_work` | `authorizeUnitOfWork` |
| `entity_usage_kind` | `entityUsageKind` |
| `entity_operation_kind` | `entityOperationKind` |
| `entity_application_domain` | `entityApplicationDomain` |
| `service_operation_model` | `serviceOperationModel` |
| `entity_access_mode` | `entityAccessMode` |
| `entity_access_relations` | `entityAccessRelations` |

Each finalized method must carry a program comment that records both:

1. the framework-internal DSL purpose of the method; and
2. the intended operating policy: keep the method final while there is no
   concrete Component-development override requirement. If such a requirement
   appears, review it then and introduce the narrow visibility and input
   contract needed by that use case.

This is deliberate observation-based extensibility: do not publish an override
contract speculatively, but do not rule out a future extension point supported
by an actual Component-development need.

## Methods that remain overridable

`serviceFactory` and `initializationParameterDeclarations` remain non-final.
They do not accept `ActionCall.Core` or otherwise expose the runtime context,
and overriding them is their intended Component factory role. Existing code
already uses these extension points.

Protected construction and initialization hooks such as `create_Core`,
`create_Component`, and `initialize_component_c` also retain their intended
override role. Their protected internal naming is outside the public camelCase
correction described above.

Consequently, after the selected finalization there should be no remaining
public, non-final Factory method that both exposes the internal call structure
and exists as an unintended override point. The remaining non-final public
methods are the two explicit configuration/factory extension points above.

## Scope and follow-up

This decision does not:

- change the shape or visibility of `ActionCall.Core`;
- replace the Core parameter with `ActionCall`;
- prohibit the framework's internal DSL from accessing `ExecutionContext`;
- add a Component API facade;
- make a broad breaking visibility change to `Behavior.Core.Holder`; or
- decide an extension mechanism before a concrete override case exists.

The parent task should implement the thirteen rename/final changes, update all
framework call sites, add the required purpose-and-operation comments, and run
focused compile/tests plus the Phase 53 final validation. If external CAR source
compatibility is part of the admitted release surface, the parent task must
also record the migration impact of the public renames and finalization.

## Implementation evidence

CS-05D renamed and finalized the thirteen methods, updated all framework call
sites, and added
`ComponentFactoryInternalDslExtensionBoundarySpec`. The specification checks
the final camelCase public surface, the absence of legacy aliases, the two
intentional public override points, and the exact JVM-visible non-final
declared-method set. A compile-time `Factory` subclass overrides each of the
three protected construction hooks, which proves their Scala-level
overrideability; Scala protected hooks are public in the JVM bytecode shape, so
JVM reflection alone cannot prove that source-level visibility.

Focused serialized validation passed on 2026-08-01:

```text
Test/compile; testOnly \
  org.goldenport.cncf.component.ComponentFactoryInternalDslExtensionBoundarySpec

3 tests succeeded; 0 failed; 0 aborted.
```

The external-CAR migration impact is recorded separately in
[Component.Factory public API finalization migration](2026-08-01-component-factory-public-api-finalization-migration.md).
