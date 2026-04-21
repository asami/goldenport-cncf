# Event Reception Latest Processing Spec (for Cozy Handoff)

Status: draft note (non-normative)  
Updated: 2026-04-21

Supersession note:
- This note remains the runtime-oriented summary of the current implementation.
- Event reception policy selection and continuation-mode redesign are now
  summarized in:
  - `docs/notes/event-reception-policy-selection.md`
- Read this note together with the newer policy-selection note when working on
  Phase 13 event/job semantics.

## 1. Purpose

This note summarizes the current CNCF runtime behavior for:

- CML Event definition intake
- Reception routing
- Action/StateMachine/Direct listener execution
- Entity-side event subscription and memory handling

Primary target is Cozy-side consistency during model/code generation handoff.

## 2. Canonical Runtime Flow

Current reception flow:

1. `ReceptionInput` is received.
2. `CmlEventDefinition` is matched by `name/kind/selectors`.
3. Event is published through `EventBus` (persistent/ephemeral policy preserved).
4. Reception routes to listeners:
   - StateMachine listener path
   - Direct event listener path
   - Entity event subscription path
5. Result is returned as `ReceptionResult(outcome, dispatchedCount, persisted, reason)`.

## 3. Event Categories

`CmlEventDefinition.category`:

- `ActionEvent`
- `NonActionEvent`

Interpretation:

- `ActionEvent`: actionable event, normally resolved into Action/ActionCall by listener logic.
- `NonActionEvent`: no direct action route required; listener-owned handling.

## 4. Reception Input Contract

`ReceptionInput` fields:

- `name: String`
- `kind: String`
- `payload: Map[String, Any]`
- `attributes: Map[String, String]`
- `persistent: Boolean`

Matching rules are deterministic:

- `name` exact match
- `kind` exact match when specified
- each selector key/value exact match on `attributes`

## 5. Security and Execution Context

Ingress security is resolved by `IngressSecurityResolver`:

- Operation path: `Subsystem.execute(request)` resolves security from request properties.
- Reception path:
  - `receiveAuthorized` uses provided `ExecutionContext`
  - `receiveSecured` resolves context from `ReceptionInput.attributes`

The resolved `ExecutionContext` is propagated into listener execution.

## 6. Listener Model

Reception supports three listener surfaces:

1. `StateMachineEventListener`
2. `DirectEventListener` (StateMachine bypass route)
3. `EntityEventSubscription` (entity-focused route)

All can coexist for the same event.

## 7. Entity Event Subscription Model

### 7.1 Route Type

`EntityEventSubscription.route`:

- `Direct`
- `PubSub`

### 7.2 Activation Mode

`EntityActivationMode`:

- `ActivateOnReceive` (load-through on receive)
- `KeepResident` (memory-resident only)

### 7.3 Entity Resolution

Entity path uses `EntitySpace` and collection wiring:

- `ActivateOnReceive`: `EntityCollection.resolve(id)` (memory miss -> store fallback -> memory put)
- `KeepResident`: memory only; miss is failure

## 8. Deterministic Guard Rails (Subscription Registration Time)

`EntitySubscriptionLimit` is enforced when calling `registerEntitySubscription`:

- `maxTotalSubscriptions`
- `maxSubscriptionsPerEntity`
- `maxDeclaredTargetUpperBound`

`EntityEventSubscription.declaredTargetUpperBound` is mandatory and validated at registration time.

Additional rule:

- `route == PubSub` requires `activationMode == KeepResident` (registration error otherwise).

## 9. Working Set Integration

`ComponentFactory` now computes working-set entity names from runtime plans/default working sets and stores them in `Component`.

`ComponentFactory.createEventReception(...)` passes:

- `entitySpace`
- `workingSetEntities`

Reception normalization rule:

- If entity is in `workingSetEntities` and subscription route is `PubSub`,
  activation mode is normalized to `KeepResident`.

So, working-set pub/sub subscriptions are memory-resident by construction.

## 10. Failure / Drop Semantics

Current semantics:

- unknown event definition -> `Failure` (`event.reception`)
- selector/kind non-target -> `Dropped` (deterministic)
- no listener route for matched event -> `Failure` (subscription mismatch)
- policy denial -> `Failure`
- keep-resident memory miss -> `Failure`

## 11. Cozy Handoff Requirements

For Cozy-side generation, provide enough metadata to produce:

1. `CmlEventDefinition`:
   - `name`
   - `category`
   - `kind` (optional)
   - `selectors`
2. Listener registration intent:
   - StateMachine listener / direct listener / entity subscription
3. For entity subscriptions:
   - `route` (`Direct` or `PubSub`)
   - `declaredTargetUpperBound`
   - activation policy intent (`KeepResident` vs `ActivateOnReceive`)
   - target resolver expression/model
4. Working-set marker per entity (for pub/sub resident behavior)

## 12. Current Open Extension Points

- automatic ComponentFactory registration of entity subscriptions from Cozy metadata
- generated target resolver implementations
- richer policy mapping per event type/listener class
- telemetry separation for state-machine dispatch vs direct listener dispatch

## 13. Related Follow-Up Notes

- `docs/notes/event-reception-policy-selection.md`
- `docs/design/event-driven-job-management.md`
- `docs/phase/phase-13.md`
