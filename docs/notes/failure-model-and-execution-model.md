# Failure Model and Execution Model

## Purpose

Model the failures that a Component is responsible for instead of allowing implementations to add speculative defensive behavior.

## Model

- `ExecutionModel` describes the execution assumptions and selects a default Failure Model.
- `FailureModel` declares failures that are in scope or out of scope.
- Failure Model can be specified at Component, Service, and Operation level.
- Service inherits the Component model and Operation inherits the Service model.
- Overrides are expressed as deltas; consumers receive a fully resolved model.
- `ResolvedFailureModel` is the effective contract after defaults and overrides are applied.

Resolution order:

```
ExecutionModel default
  -> Component FailureModel
  -> Service FailureModel
  -> Operation FailureModel
  -> ResolvedFailureModel
```

An OUT_OF_SCOPE failure is a negative constraint: implementations must not add mechanisms solely to defend against it.

Robustness follows:

```
Execution Model -> Failure Model -> Required Robustness -> Mechanism
```

CNCF owns the runtime/meta-model representation and resolution semantics. CML/Cozy owns authoring. Workflow/AI consumers use only the resolved contract.
