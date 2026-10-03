# Workflow / Agent Runtime Competitive Reference

Date: 2026-10-04

## Context

CNCF Workflow, sm-workflow, and TEAI are being developed as a deterministic enterprise execution foundation that can incorporate AI judgment without making an AI agent framework the authority over business workflow state.

A comparison was made against current AI workflow / agent technologies: LangGraph, Dify, CrewAI, OpenClaw, and ZeroClaw. The purpose is not to select a replacement for CNCF Workflow, but to clarify architectural boundaries and identify useful external design references.

## Current Position

### LangGraph

LangGraph is the most direct competitive/design reference for CNCF Workflow among the products examined.

Relevant conceptual correspondences include:

- graph/node vs. Workflow/Action
- graph state vs. Workflow state
- checkpoint/persistence vs. durable workflow execution
- interrupt/resume vs. Continuation
- human-in-the-loop vs. Continuation and Admission
- deterministic nodes vs. ordinary Action
- agentic/LLM nodes vs. JudgmentAction
- subgraph vs. Subworkflow

The important difference is scope.

LangGraph is primarily an orchestration framework for long-running, stateful agents. CNCF Workflow is intended to be a general enterprise workflow facility that remains meaningful without AI. AI judgment is one possible action type inside the workflow rather than the organizing principle of the workflow itself.

For this reason, LangGraph should be treated as an ongoing competitive reference for CNCF Workflow, especially when reviewing Continuation, persistence, durable execution, JudgmentAction, subworkflow, and human interaction semantics.

Features should not be copied merely because LangGraph has them. The useful question is: what problem is LangGraph solving, and what is the natural solution in the more general CNCF execution model?

Candidate-Admission is also intentionally broader than a simple interrupt/resume mechanism. It models promotion of a candidate result into authoritative state through review/judgment/admission.

### Dify

Dify is primarily a competitive reference for an AI application/workflow platform.

Its workflow, agent runtime, model/tool integration, state handling, and operational UI are useful sources of design ideas. However, introducing Dify as an orchestration layer below CNCF Workflow would duplicate substantial workflow authority.

Therefore:

- do not introduce Dify as a required CNCF execution layer;
- observe its solutions and incorporate useful concepts where they fit CNCF semantics;
- use it as a competitive/product reference rather than as the architectural foundation.

### CrewAI

CrewAI is primarily a competitive reference for multi-agent collaboration and agent-oriented workflow.

The distinction between Crew and Flow, role-based collaboration, delegation, and model assignment are useful design references for sm-workflow and JudgmentAction scenarios.

Its workflow responsibilities overlap with sm-workflow/CNCF Workflow if used as the general workflow engine. A localized CrewAI execution could still be useful as an implementation of a specialized JudgmentAction when autonomous multi-agent deliberation is actually required.

Therefore CrewAI is not currently a general execution dependency.

### OpenClaw

OpenClaw is currently the primary/reference agent runtime to use for practical experimentation.

The immediate objective is to gain operational experience rather than prematurely optimize the runtime choice. Areas to observe include:

- long-running operation;
- tool and shell execution;
- sandbox and permission policy;
- Continuation boundary;
- local/cloud model routing;
- failure and restart behavior;
- CPU/memory/operational overhead.

TEAI is already intended to remain loosely coupled to OpenClaw. OpenClaw must therefore not become an architectural prerequisite of TEAI or CNCF Workflow.

### ZeroClaw

ZeroClaw is a promising lightweight alternative agent runtime.

It may be particularly attractive for small enterprise deployments where CNCF Workflow already owns durable workflow semantics, state transitions, Continuation, Admission, human approval, and observability. In such an environment the lower-level agent runtime can remain comparatively small.

A possible future operational profile is:

- development / advanced agent operation: OpenClaw;
- small enterprise production where lightweight operation is important: ZeroClaw.

This is an evaluation hypothesis, not a product decision.

ZeroClaw support should not trigger speculative abstraction work now. First use OpenClaw in practice. If runtime replacement becomes useful, validate the existing loose boundary and add only the minimum adapter/difference handling required.

## Architectural Principle

The authority boundary remains:

```text
Enterprise Application
        |
        v
   CNCF Workflow
   - workflow/state
   - durable execution
   - Continuation
   - Admission
   - human interaction
   - observability
        |
        +---- ordinary deterministic actions
        |
        +---- JudgmentAction
                    |
                    v
              AI / Agent Runtime
              - OpenClaw
              - ZeroClaw
              - direct model/runtime
              - specialized multi-agent runtime when justified
```

TEAI may use an agent runtime for integration/AI execution, but the runtime must not become the owner of enterprise workflow semantics.

## Competitive Reference Classification

| Technology | Primary reference area |
| --- | --- |
| LangGraph | CNCF Workflow / Continuation / JudgmentAction |
| Dify | AI application and workflow platform |
| CrewAI | multi-agent collaboration / agent-oriented workflow |
| OpenClaw | primary/reference agent runtime for current experiments |
| ZeroClaw | lightweight alternative agent runtime, especially for operational-cost-sensitive deployment |

## Follow-up

1. Continue practical OpenClaw use before deciding runtime abstractions.
2. Treat LangGraph as an ongoing CNCF Workflow design comparison target.
3. Periodically inspect Dify and CrewAI for useful concepts without importing their workflow authority.
4. Evaluate ZeroClaw when a concrete production/operational requirement makes lightweight runtime operation relevant.
5. A future OpenClaw-to-ZeroClaw substitution test can also serve as an architectural test of the CNCF Workflow / TEAI loose-coupling boundary.
