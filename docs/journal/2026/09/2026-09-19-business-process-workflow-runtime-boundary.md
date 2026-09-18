# Business Process and Workflow Runtime Boundary

Date: 2026-09-19
Status: design direction

## Context

SimpleModeling Business Modeling now treats Business Process as the Business-layer organizing construct.

A Business Process may:

- define its own Capability;
- contain Participants with their own Capabilities;
- group related Use Cases;
- group related Workflows; and
- operate across multiple Problem Domains and therefore across multiple Bounded Contexts.

CNCF must preserve a clear runtime boundary for this model.

## Decision

Business Process is not a CNCF runtime primitive and is not synonymous with Workflow.

The intended continuity is:

```text
Business Process
  -> groups Use Cases
  -> groups Workflows
       -> StateMachine semantics
       -> Action / Participant
       -> Component Operation / Job / SubWorkflow
       -> CNCF runtime
```

Workflow is the executable coordination model consumed by CNCF. Business Process remains a higher-level Business Model that may span human work, organizations, external systems, multiple applications, and multiple Problem Domains.

CNCF therefore does not attempt to execute a complete Business Process merely because one or more Workflows are related to it.

## Problem Domain Boundary

A Business Process may cross multiple Problem Domains.

Bounded Context is a Domain Modeling boundary for semantic/model consistency inside a Problem Domain. It is not a CNCF Workflow ownership or runtime partitioning rule.

Consequently:

- Workflow boundaries need not equal Business Process boundaries;
- Workflow boundaries need not equal Bounded Context boundaries;
- one Business Process may be supported by multiple Workflows owned by different Components;
- a Workflow may interact with domain subjects from more than one admitted context through explicit Component contracts; and
- runtime ownership remains defined by Component, Workflow definition/instance, Operation, StateMachine, and other CNCF contracts rather than by Business Process.

## Semantic Metadata

Where generated or published Workflow metadata carries Business-model provenance, CNCF should preserve stable references without interpreting them as execution semantics.

Candidate references include:

- related Business Process identity;
- related Use Case identities;
- purpose/goal;
- related Problem Domain or Bounded Context identities when admitted by the generated model; and
- source provenance.

These references support navigation, review, observability, and higher-level projections. They do not create a second runtime authority.

## Design Rule

> Business Process organizes business intent and activity. Workflow provides executable coordination. CNCF executes Workflow semantics and preserves higher-level provenance without turning Business Process or Bounded Context into runtime boundaries.

This direction complements the existing rule that CNCF preserves authoritative Workflow semantics while higher-level tools own human-oriented projections.
