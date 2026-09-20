# Workflow Management Web Console

status = proposed, non-normative
date = 2026-09-20
target_phase = 87

## Purpose

Define the detailed design direction for adding Workflow Management to the existing CNCF Web Console. The UI is a generic projection of CNCF Workflow runtime evidence. It must not become a second Workflow execution model.

## Management model

The Web Console uses three primary application-semantic axes:

- **Aggregate**: write/domain state, Aggregate instances, constituent Entities, events, consistency boundaries, and related Commands.
- **View (Read Model)**: projection state, source Entities/Aggregates, Queries, freshness/version evidence, and read-side diagnostics.
- **Workflow**: process/state progression, current position, history, continuation/action evidence, result, and cross-runtime correlation.

These axes provide the runtime/Observed Model counterpart of the Aggregate, View, and Workflow projections in Textus CBD Support.

**Job Management** is not a fourth application-model axis. It is the cross-cutting execution-management and diagnostic layer for asynchronous work. A Job may be reached from an Aggregate command, View projection/update, or Workflow action when stable correlation evidence exists. Jobs and Workflows remain independent runtime concepts: their relationship is correlation, not universal containment.

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

The canonical correlation should be bidirectional when the underlying contracts provide enough evidence: Workflow detail to related Job detail, and Job detail to related WorkflowInstance / ActionExecution. Equivalent stable links should connect Jobs to affected Aggregates and Views when the runtime contract exposes those identities.

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

All presentations must observe the same Aggregate, View, Workflow, and lifecycle identities.

## Designed Model / Observed Model navigation

Textus CBD Support owns the designed-model projections; the CNCF Web Console owns runtime observation and management. Where stable model/runtime identities and authorized destinations are available, navigation should be bidirectional:

- CBD Support Aggregate/View/Workflow model -> corresponding CNCF runtime overview or instance;
- CNCF Aggregate/View/Workflow runtime evidence -> corresponding CBD Support model projection.

The UI must expose an unavailable or ambiguous mapping rather than infer one from names, labels, or diagram position.

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

- Aggregate, View, and Workflow are available as the primary Dashboard navigation axes;
- Job Management remains a cross-cutting execution/diagnostic layer rather than a fourth model axis;
- stable Designed Model / Observed Model links are preserved in both directions where available;
- a Workflow with no Job is listed and inspectable;
- a Workflow with one or more related Jobs is listed independently and links to those Jobs;
- unrelated standalone Jobs remain valid Job Management subjects;
- component filtering isolates sm-workflow without special-case code;
- unauthorized/redacted Workflow evidence is not leaked;
- list/detail/history projections are deterministic from canonical runtime records;
- a representative completed Workflow retains result/history according to policy.
