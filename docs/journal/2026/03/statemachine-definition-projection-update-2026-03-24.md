# StateMachine Definition Projection Update

status=implemented
published_at=2026-03-24
owner=cncf-runtime

## Summary

Added additive runtime contract support for Cozy-generated state machine definition metadata.

This enables `Component` to expose canonical state machine definitions independently from transition rules.

## Main Changes

### 1. New metadata type

File:
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/statemachine/CmlStateMachineDefinition.scala`

Added:

- `CmlStateMachineDefinition(name, states, events)`

### 2. Component contract extension

File:
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/Component.scala`

Added additive hook:

- `def stateMachineDefinitions: Vector[CmlStateMachineDefinition] = Vector.empty`

### 3. Meta projection enhancement

File:
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/StateMachineProjection.scala`

Enhanced projection output:

- `states` now includes aggregated states from `stateMachineDefinitions`
- `events` merges transition events + definition events
- new `definitions` section is emitted

### 4. Projection test update

File:
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/projection/StateMachineProjectionSpec.scala`

Added test case:

- projects `stateMachineDefinitions` into `states/events/definitions`

## Validation

Executed:

- `sbt --batch "compile;testOnly org.goldenport.cncf.projection.StateMachineProjectionSpec;publishLocal"`

Result:

- pass
