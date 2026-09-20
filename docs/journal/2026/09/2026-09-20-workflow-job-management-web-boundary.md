# Workflow and Job Management Web Boundary

status=decision
date=2026-09-20
phase=[Phase 87](../../../phase/phase-87.md)

## Decision

Adopt **Aggregate / View (Read Model) / Workflow** as the three primary application-semantic axes of the CNCF Web Console. Workflow Management is added within this structure, while Job Management remains the cross-cutting asynchronous execution-management and diagnostic capability.

The decisive boundary is that Workflow Management observes **all CNCF Workflow runtime instances**, not only Workflows whose execution is managed by JobEngine.

## Rationale

The sm-workflow integration discussion exposed two independent operational needs:

1. Skills should interact with sm-workflow through MCP.
2. Humans should be able to inspect running Workflow state and completed Workflow results through the CNCF Web Console.

Making the Web surface sm-workflow-specific would duplicate runtime-management infrastructure and prevent other Workflow consumers from receiving the same operational capability. Making Workflow visibility subordinate to Job Management would also be incorrect because Workflow progression and asynchronous Job execution are different lifecycle concepts.

## Job / Workflow boundary

The retained model is:

- **Aggregate** exposes write/domain state and consistency boundaries.
- **View** exposes read/projection state and Query-facing evidence.
- **Workflow** exposes process/state progression.
- **Job** manages asynchronous execution lifecycle across those semantic axes.
- Workflow Action/Execution may have related Job occurrences according to the admitted runtime contract.
- A Workflow does not require a Job.
- A Job does not require a Workflow.
- Association is represented by stable correlation evidence, never inferred by UI heuristics.

Therefore Job-managed and non-Job-managed Workflows are equally visible in Workflow Management.

## Web Console direction

The existing CNCF Web management family should evolve as:

    CNCF Web Console
      Dashboard
        Aggregates
        Views (Read Models)
        Workflows
      Jobs (cross-cutting execution management)

Workflow Management provides generic list/detail/history/result views and cross-links to Job Management where correlation exists.

The first practical filtering requirement is Component/Subsystem origin. In particular, users must be able to select sm-workflow and obtain a focused view without introducing an sm-workflow-specific dashboard.

Additional useful filters include Workflow definition, lifecycle status, current State/GoalPhase, and time range, provided these are backed by canonical runtime metadata.

## sm-workflow role

sm-workflow is the first representative consumer and validation target:

    Codex Skill --MCP--> sm-workflow --> CNCF Workflow Runtime
                                          |
                                          +--> Workflow Web Management

Skill/MCP interaction and human/Web observation are separate presentations over the same runtime truth.

## Designed Model / Observed Model boundary

Textus CBD Support presents the Designed Model through Aggregate, View, and Workflow projections. The CNCF Web Console presents the corresponding Observed Model using the same stable identities where contracts permit.

Bidirectional navigation is required when authoritative links exist:

- CBD Support model projection -> CNCF runtime overview/instance;
- CNCF runtime evidence -> CBD Support model projection.

A missing or ambiguous mapping remains explicit. Neither surface may synthesize identity from names, timestamps, labels, or layout.

## Planning consequence

Create Phase 87 for the Workflow Management Web Console. Phase 69.6 continues to own Job-specific user/operator Web experience. Phase 86's generic candidate wording about richer UI/client integration does not own this concrete management surface; Phase 87 is the explicit owner for Workflow Web management.

Implementation should begin only from current source/runtime contracts and should avoid adding UI-only shadow registries or duplicate lifecycle state.
