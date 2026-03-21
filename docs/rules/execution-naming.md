# Execution Naming Rules (CNCF)

## Core guidance

- ActionCall execution must use `execute`.
- Scenario / Program / UnitOfWork execution must use `run`.
- `prepare` exists for executable specs, verification, and future extension.
- Do not mix `execute` and `run` semantics.

## Rules

- `prepare` builds semantic structure and does not execute.
- `execute` runs a prepared ActionCall.
- `run` drives interpreter / scenario / workflow execution.

No-mix policy:
- Do not mix the meaning of `prepare` / `execute` / `run`.
- ActionCall execution must be centralized in `execute`.
- Scenario driving must be centralized in `run`.

## Rationale

- ActionCall is an execution unit; `execute` expresses its responsibility.
- Scenario / Program is an execution-path aggregate; `run` expresses its responsibility.
- `prepare` is retained for executable specs and future extension.
