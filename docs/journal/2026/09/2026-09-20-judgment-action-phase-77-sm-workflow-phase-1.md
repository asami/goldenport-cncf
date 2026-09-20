# JudgmentAction for Phase 77 and sm-workflow Phase 1

Date: 2026-09-20
Status: accepted planning decision

## Decision

CNCF Phase 77 introduces `JudgmentAction` as a provider-neutral Workflow Action
semantic. `OperationAction` performs deterministic work; `JudgmentAction`
requests a contextual decision from typed goal, context, alternatives, and
criteria, and accepts a typed `JudgmentResult` containing decision, rationale,
and evidence.

The judgment worker does not control Workflow progression. CNCF StateMachine
guards and transitions interpret the result and retain authority over the next
state and Action.

Codex is the initial external reference worker through the Generic Skill and
durable Continuation boundary. Codex is not part of the canonical Action type.
jev, a human, a local model, or another Provider may replace it later without
changing Workflow definitions or transition semantics.

`sm-workflow` Phase 1 consumes this common contract and specializes it for
software-development judgments. It must preserve deterministic operations as
operations and use `JudgmentAction` only where contextual judgment is actually
required.
