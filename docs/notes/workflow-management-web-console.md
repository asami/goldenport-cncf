# Workflow Management Web Console

status = proposed, non-normative
date = 2026-09-20
target_phase = 87

## Purpose

Define the detailed design direction for adding Workflow Management to the existing CNCF Web Console. The UI is a generic projection of CNCF Workflow runtime evidence. It must not become a second Workflow execution model.

## Management model

The Web Console has three related but distinct views:

- **Dashboard**: runtime-wide overview and navigation.
- **Job Management**: asynchronous execution lifecycle, result, diagnostics, recovery/control, and Job-specific operational evidence.
- **Workflow Management**: process/state progression, current position, history, continuation/action evidence, result, and cross-runtime correlation.

Jobs and Workflows are peers. Their relationship is correlation, not containment as a universal rule.

## Visibility invariant

Every WorkflowInstance admitted to the CNCF Workflow runtime is eligible for Workflow Management visibility subject to authorization, retention, and redaction policy.

Visibility MUST NOT depend on whether the Workflow was started from JobEngine, whether any Workflow Action created a Job, whether the current step is synchronous, externally continued, AI-driven, human-driven, or Job-backed, or whether the consumer is sm-workflow or another Component.

This permits one generic runtime view over sm-workflow, KnowledgeHub, Editing Studio, and later Workflow consumers.

## Job relationship

A Workflow Action/Execution may optionally correlate with a Job.

    WorkflowInstance
      |
      +-- progression/state history
      |
      +-- ActionExecution
            |
            +-- no Job
            |
            +-- related JobId
                  |
                  +-- Job Management detail

The canonical correlation should be bidirectional when the underlying contracts provide enough evidence: Workflow detail to related Job detail, and Job detail to related WorkflowInstance / ActionExecution.

The UI must not infer association from timestamps, names, labels, or scanning bounded Job lists. Correlation must use stable runtime identity/evidence.

## Workflow list projection

Candidate fields include WorkflowInstance identity, Component/Subsystem origin, Workflow definition identity/version, lifecycle status, current state, GoalPhase or equivalent consumer projection when canonically exposed, timestamps, correlation/parent identity, and failure/attention indication.

Initial filters should include Component/Subsystem, Workflow definition, lifecycle status, current state/GoalPhase, and time range where supported.

sm-workflow is therefore not a special screen. Selecting Component = sm-workflow yields an sm-workflow-focused operational view.

## Workflow detail projection

The detail view should be built from canonical runtime evidence and may expose identity and definition metadata, current state/progression position, state/transition/progression history, ActionExecution and Continuation history, participant/provider evidence where authorized, related Jobs, retries/failures/suspensions/waits/diagnostics, terminal result/evidence, timestamps, and correlation chain.

A graphical StateMachine/Workflow visualization is useful only when it is a projection of admitted definition plus runtime occurrence evidence. It must not become an independently editable execution truth.

## Runtime/API boundary

The Web layer should consume a canonical management/read contract. REST may be the browser-facing transport, while MCP remains the AI/Skill-facing presentation.

    Workflow Runtime
      +-- Web / REST -> human management
      +-- MCP -------> Skill / AI
      +-- typed API -> programmatic consumers

All presentations must observe the same Workflow identity and lifecycle semantics.

## sm-workflow

sm-workflow runs as a server-side Workflow consumer and exposes Skill integration through MCP. Its Workflow instances are registered/observed through CNCF runtime contracts and therefore appear automatically in Workflow Management.

    Codex Skill
        |
        | MCP
        v
    sm-workflow server / application workflow
        |
        v
    CNCF Workflow Runtime
        |
        +-- Workflow Management Web Console
        +-- optional Job correlations

The Web Console is for human observation/operation; MCP is for Skill/AI interaction. Neither presentation owns Workflow semantics.

## Acceptance direction

Executable Specifications should demonstrate at least:

- a Workflow with no Job is listed and inspectable;
- a Workflow with one or more related Jobs is listed independently and links to those Jobs;
- unrelated standalone Jobs remain valid Job Management subjects;
- component filtering isolates sm-workflow without special-case code;
- unauthorized/redacted Workflow evidence is not leaked;
- list/detail/history projections are deterministic from canonical runtime records;
- a representative completed Workflow retains result/history according to policy.
