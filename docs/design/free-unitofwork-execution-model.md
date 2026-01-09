======================================================================
Free & UnitOfWork Execution Model
— Execution as Intent for Humans and AI
======================================================================

1. Introduction
----------------------------------------------------------------------

This document describes the design philosophy of the **Free × UnitOfWork
execution model** used in the Cloud-Native Component Framework (CNCF).

The purpose of this model is not to introduce a functional programming
technique for its own sake, but to establish an execution architecture in
which **execution is treated as intent, not as immediate side effects**.

This architecture is designed to be:
- cognitively manageable for humans, and
- structurally understandable, analyzable, and generatable by AI.

----------------------------------------------------------------------

2. Problem Statement: Limitations of Conventional Execution Models
----------------------------------------------------------------------

In conventional application designs, the following concerns are often
intertwined:

- domain logic
- execution control
- transactions
- retries, logging, tracing, monitoring
- environment-dependent behavior

This entanglement causes several problems:

- domain logic becomes harder to read and review
- execution behavior becomes implicit and fragile
- testing requires heavy mocking
- AI cannot reliably understand or transform the code

In short, execution becomes opaque.

----------------------------------------------------------------------

3. Overview of the Free & UnitOfWork Execution Model
----------------------------------------------------------------------

CNCF separates execution into the following layers:

Action (intent)
  ↓
Free[UnitOfWorkOp]
  ↓
Interpreter (execution policy)
  ↓
UnitOfWork / Driver (side effects)

Key properties:

- Actions do not execute anything
- Actions describe *what should be done*, not *how it is done*
- Side effects occur only inside the Interpreter and Drivers

----------------------------------------------------------------------

4. Structural Separation of Concerns
----------------------------------------------------------------------

With Free × UnitOfWork, Separation of Concerns is enforced structurally,
not by convention.

- There is no place to write Quality Attributes inside Actions
- Quality Attributes can only be implemented in the Interpreter
- Domain logic cannot accidentally absorb execution policy

This is not a guideline — it is a physical constraint of the design.

----------------------------------------------------------------------

5. Pushing Quality Attributes into the Interpreter
----------------------------------------------------------------------

Quality Attributes such as:

- transactions (commit / abort)
- retries and timeouts
- logging, tracing, metrics
- authorization and validation
- dry-run and simulation

are implemented exclusively in the Interpreter layer.

Actions remain purely declarative descriptions of business intent.

----------------------------------------------------------------------

6. What Became Simpler by Using Free
----------------------------------------------------------------------

Using Free enabled the following simplifications:

- Actions read as business scenarios
- commit / abort logic is centralized
- test execution is fast and deterministic
- fake drivers can be injected naturally
- declarative and direct execution can coexist
- dry-run becomes a natural execution mode

----------------------------------------------------------------------

7. Failure Modes Without Free
----------------------------------------------------------------------

Without Free, systems tend to suffer from:

- scattered execution boundaries
- brittle tests and mocks
- irreversible commitment to direct execution
- semantic divergence between CLI, HTTP, and AI entry points
- systems that cannot explain their own behavior

Such systems are difficult to evolve and unsafe for AI assistance.

----------------------------------------------------------------------

8. Free & UnitOfWork and AI Compatibility
----------------------------------------------------------------------

A Free program exists as a structure *before execution*.

This enables AI to:

- explain what an Action will do
- perform dry-run analysis
- detect dangerous operations
- propose safe modifications
- restructure execution flows

This is impossible when execution happens immediately.

----------------------------------------------------------------------

9. Automatic Detection of Dangerous Operations
----------------------------------------------------------------------

Before execution, the UnitOfWork analyzes the Free structure to detect:

- irreversible operations
- external side effects
- large blast-radius behavior
- contextual mismatches (e.g., write in read-only actions)

Detection happens *before* side effects occur.

----------------------------------------------------------------------

10. Action Metadata and CML
----------------------------------------------------------------------

Action properties such as:

- command vs query
- read-only
- external effect allowance
- approval requirements

are defined in CML and compiled into ActionMeta.

These metadata are interpreted during execution validation, not embedded
in domain logic or UnitOfWorkOp definitions.

----------------------------------------------------------------------

11. Enforcing Command / Query Semantics at Runtime
----------------------------------------------------------------------

Command / Query separation is enforced by execution-time validation,
not by developer discipline.

If a Query Action contains a write operation, execution is rejected
before any side effect occurs.

This turns CQRS from a guideline into an enforced contract.

----------------------------------------------------------------------

12. Conclusion: Execution as Intent
----------------------------------------------------------------------

Free × UnitOfWork embodies the principle of:

  Execution as Intent

Execution is expressed as structured intent, and only the Interpreter
is responsible for turning that intent into side effects.

This execution model provides a common, safe foundation for humans and
AI to share, analyze, and evolve the same codebase.

======================================================================
END OF DOCUMENT
======================================================================
