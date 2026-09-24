# Phase 91: Execution Model and Failure Model

## Goal

Introduce Failure Model as an explicit Component contract and make it resolvable from Execution Model defaults through Component, Service, and Operation scopes.

## Scope

- Define `ExecutionModel`, `FailureModel`, and `ResolvedFailureModel`.
- Define in-scope/out-of-scope failure semantics.
- Define inheritance and override resolution across Component -> Service -> Operation.
- Define Execution Model default Failure Models.
- Expose the resolved model as runtime/development metadata usable by CAR tooling and workflow consumers.
- Define the boundary between Failure Model, required robustness, and implementation mechanism.
- Ensure OUT_OF_SCOPE failures cannot justify speculative defensive mechanisms.
- Provide fixtures/executable specifications for resolution behavior.

## Integration

- Cozy/CML Phase 72 provides authoring and generated model metadata.
- sm-workflow consumes ResolvedFailureModel when instructing AI implementation.
- CAR development uses the CML-derived resolved model as part of the implementation contract.

## Non-goal

This phase does not prescribe a universal catalog of implementation mechanisms such as hashes, locks, retries, or rollback. Those mechanisms follow from explicit robustness requirements.
