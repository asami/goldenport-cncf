# Operational Orchestration and Work Orchestration

Date: 2026-11-11
Status: Architectural decision / responsibility boundary

## Two independent command paths

Textus distinguishes two orthogonal kinds of orchestration:

1. **Operational Orchestration (運用の指揮)** — Textus Control Center owns the operational control plane: subsystem inventory, health, deployment/runtime status, startup, shutdown, restart and recovery through authorized Supervisor/launcher mechanisms.
2. **Work Orchestration (仕事の指揮)** — textus-orchestrator coordinates work across independent subsystems and participants: accepted goals, operation dispatch, workflow continuation, AI/human participant routing, execution correlation, and handoff. The owning CNCF Workflow still owns progression; the orchestrator is not a duplicate workflow engine.

These relationships cross but are not a hierarchy. Control Center **observes and operationally manages textus-orchestrator itself**, as well as textus-eai, sm-workflow, Knowledge Lake and other subsystems. Conversely, textus-orchestrator **may invoke authorized Control Center management operations** when work requires operational action. It also invokes EAI and sm-workflow operations to perform integration and development tasks.

```text
Dot / OpenClaw / Human
         |
         v
  textus-orchestrator  -----> Control Center -----> Supervisor / Docker / launchers
         |                       |
         +----> textus-eai       +---- monitors/manages ----+
         |                       |                          |
         +----> sm-workflow      +-- orchestrator, EAI, sm-workflow,
         |                           Knowledge Lake, other subsystems
         +----> other subsystems
```

## Independent subsystem principle

- EAI is a standalone integration component with its own Scheduled, Event-driven and On-demand triggers, integration workflows, external connectors, controlled persistence/log/resource lifecycle and sandbox policies. It must not depend on orchestrator to run its basic jobs.
- sm-workflow owns development workflow semantics and evidence, not system-wide orchestration.
- Control Center provides direct human-facing operational UI/API independently of orchestrator; orchestrator is also an observable managed target.
- Orchestrator is the preferred common coordination entry for Dot/OpenClaw when a task spans subsystems, but **not** a mandatory gateway for every subsystem call.
- If orchestrator stops, standalone EAI/sm-workflow jobs should continue. If Control Center stops, other subsystems should continue; external Supervisor/restart policy recovers Control Center.

## Authorization, state and observability

- Management actions from AI must cross Control Center's authorized operations; no unrestricted process control.
- Control Center may display subsystem Job/Workflow/Continuation/evidence status through CNCF management operations/events; source-of-truth state remains with the owning subsystem.
- CNCF Operation, Job, Workflow, Continuation, Authorization and Observability provide shared contracts. Avoid a second scheduler or state-machine framework where existing CNCF primitives suffice.
- Saga remains responsible for cross-subsystem long-transaction consistency/compensation, distinct from orchestrator's participant/work coordination.
- Human admission is required for consequential actions according to existing policy; merely requesting a task from an agent grants no additional authority.

## Example flows

**Operations:** Dot/OpenClaw -> orchestrator -> Control Center management Operation -> Supervisor -> subsystem recovery -> typed result/evidence -> orchestrator -> requesting participant.

**Integration:** EAI scheduler -> Gmail/Slack ingestion -> normalization/material handling, entirely standalone. For cross-system synthesis: Dot/OpenClaw -> orchestrator -> EAI Operation -> AI participant -> candidate/admission.

**Development:** Dot/OpenClaw -> orchestrator -> sm-workflow -> heavy tests/CBD Support review -> evidence -> AI/human judgment.

## Implementation note

This journal records architectural intent, not implementation completion. Reconcile it with each repository's planned phases before scheduling work. In particular, external delivery of typed Continuations to Dot is not yet verified as a public API capability.

## CNCF-specific implications

This is an application architecture distinction, not a new core Workflow mode. Reuse CNCF Operation, Job, Workflow, Continuation, Authorization, Observability and Saga boundaries. Subsystems expose management and work operations independently; ownership of runtime state remains local.
