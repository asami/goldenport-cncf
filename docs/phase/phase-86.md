# Phase 86: Advanced Workflow Runtime and Composition

Status: planned
Planned: 2026-09-20

## Goal

Add advanced Workflow runtime facilities after the Phase 64 -> 64.1 -> 64.2 -> 77 -> sm-workflow executable-specification path has established real consumer evidence.

This Phase is not a prerequisite for CNCF Phase 77 or sm-workflow Phase 1.

## Candidate scope

- Workflow-to-Workflow invocation/composition runtime beyond the Phase 77 type/correlation compatibility constraint;
- parent/child WorkflowInstance lifecycle management and durable child-completion waiting;
- richer Workflow persistence/retention/lease administration where not already owned elsewhere;
- advanced operational observability and administration;
- remote Workflow transport / service discovery / connector integration;
- richer UI/client integration not covered by the basic Continuation protocol;
- operational facilities discovered from sm-workflow connectivity/use.

## Constraints

- Reuse Phase 77 typed Workflow protocol Value Objects.
- Preserve direct Scala typed composition and wire encoding equivalence.
- Do not introduce a second Workflow language.
- Do not duplicate Retry/Timeout/lifecycle-control phases or Phase 85 transaction/recovery responsibilities.
- Refine concrete work from operational evidence rather than speculative feature completeness.
