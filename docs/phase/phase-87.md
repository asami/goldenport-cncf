# Phase 87: Workflow Management Web Console

Status: planned
Planned: 2026-09-20

## Goal

Establish Aggregate, View (Read Model), and Workflow as the three primary semantic navigation axes of the CNCF Web Console, and add Workflow Management as a standard runtime-management surface. Keep Job Management as the cross-cutting asynchronous execution-management and diagnostic capability.

The management surface observes every WorkflowInstance known to the CNCF Workflow runtime regardless of whether its current or past execution is managed by JobEngine. sm-workflow is the first representative consumer, not the owner of a separate dashboard.

## Dependency

- Phase 64 / 77 Workflow runtime contracts are the semantic authority for Workflow instances and progression.
- Phase 69.6 Job user/operator experience remains the authority for Job-specific Web management.
- Phase 80/86 extensions are not prerequisites unless implementation evidence identifies a concrete required contract.

## Scope

1. Organize Dashboard navigation around Aggregate, View (Read Model), and Workflow runtime projections.
2. Keep Job Management cross-cutting: correlate Jobs with Aggregate, View, and Workflow execution evidence without treating Job as a fourth application-model axis.
3. Add a Web Console Workflow overview/list.
4. Show all observable running and retained completed Workflow instances, including instances with no Job association.
5. Provide filtering using stable runtime metadata, initially including Component/Subsystem, Workflow definition, lifecycle status, and current State/GoalPhase where available.
6. Provide Workflow instance detail with current state, transition/progression history, action/continuation evidence, result, failure/retry diagnostics, timestamps, and correlation identifiers supported by the canonical runtime.
7. Link Workflow action/execution evidence to related Jobs when a Job exists, without making Job ownership a prerequisite for Workflow visibility.
8. Provide reverse correlation from Job management to originating/related Workflow execution where canonical correlation evidence exists.
9. Reuse the CNCF Web Console, authorization, redaction, polling/update, and presentation infrastructure rather than creating an sm-workflow-specific UI.
10. Add Executable Specifications for enumeration, filtering, detail projection, Job/non-Job visibility, authorization/redaction, and representative sm-workflow metadata.

## Core boundary

Aggregate, View, and Workflow are the primary application-semantic axes. Job Management is a cross-cutting runtime-management layer over asynchronous execution:

    CNCF Web Console
      +-- Dashboard
      |     +-- Aggregates
      |     +-- Views (Read Models)
      |     +-- Workflows
      +-- Jobs

    WorkflowInstance
      +-- Action/Execution
            +-- optional related Job

- Aggregate expresses write/domain state and consistency boundaries.
- View expresses read/projection state.
- Workflow expresses process/state progression.
- Job expresses asynchronous execution lifecycle.
- A Workflow Action is not inherently a Job.
- A Job need not belong to a Workflow.
- Job-managed and non-Job-managed Workflow execution are both first-class Workflow Management subjects.

## Designed/Observed model navigation

Textus CBD Support is the Designed Model surface; the CNCF Web Console is the Observed Model surface. Phase 87 must preserve stable Aggregate/View/Workflow identity and provide bidirectional links between the two surfaces when authoritative mapping is available. Missing or ambiguous mapping must remain explicit and must not be reconstructed heuristically.

## sm-workflow acceptance

sm-workflow should be usable as a representative external consumer:

- its Workflow instances appear through the generic CNCF Workflow Management surface;
- users can filter to sm-workflow using canonical component/runtime metadata;
- no sm-workflow-specific dashboard model or shadow execution registry is introduced;
- Skill integration remains MCP-facing and independent from the human Web management surface.

## Non-goals

- Redefining Workflow/StateMachine semantics.
- Making Workflow a subtype of Job or Job a mandatory Workflow execution mechanism.
- Introducing an sm-workflow-specific Web application.
- Duplicating Phase 69.6 Job management functionality.
- Adding a second Workflow persistence, history, or observability model solely for the UI.

## Planning references

- [Phase 69.6](phase-69.6.md)
- [Phase 77](phase-77.md)
- [Phase 80](phase-80.md)
- [Phase 86](phase-86.md)
- [Workflow Management Web Console Note](../notes/workflow-management-web-console.md)
- [Workflow and Job Management Boundary Journal](../journal/2026/09/2026-09-20-workflow-job-management-web-boundary.md)
