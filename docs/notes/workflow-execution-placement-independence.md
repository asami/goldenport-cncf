# Generic Workflow Execution Placement and Independence

Status: proposed Phase 80 extension
Date: 2026-10-03

## Purpose

Extend the existing CNCF Workflow ExecutionRequirement / ExecutionEvidence contract with application-neutral execution placement and independence semantics required by Skill/Host-driven workflows.

## Existing baseline

Phase 77.2 already owns typed ExecutionRequirement with capability/risk/ReasoningLevel and typed ExecutionEvidence for Skill/Host-dispatched completion. Concrete provider/model/effort remains host/runtime policy and evidence, not StateMachine transition semantics.

## Proposed minimum extension

ExecutionRequirement should be able to express, in model/provider-independent terms:

- placement: INLINE | DELEGATED
- independence: OPTIONAL | REQUIRED

ExecutionEvidence should identify the actual execution placement and an execution-context identity/correlation sufficient to validate the required separation.

INLINE means the current execution participant/context may perform the semantic work. DELEGATED means another execution context/provider must perform it. These are runtime/provider placement requirements, not Workflow-wide execution modes and not StateMachine transition selectors.

REQUIRED independence means Admission of the completion must be able to establish the required separation from the producer/execution context. The protocol must not equate independence with a particular vendor's child-task mechanism.

## Compatibility

Existing Phase 77.2 consumers need backward-compatible defaults. Concrete worker/model/effort selection remains outside canonical Workflow semantics.

Application-specific classifications such as sm-workflow TRIVIAL, PROGRAMMING / ENGINEERING, or software-development context footprint are not CNCF vocabulary. Applications resolve those into the generic requirement.

## Requirement/evidence relationship

```text
WorkOrder
  -> ExecutionRequirement
       reasoning/capability/risk
       placement
       independence
  -> Host/Execution Harness
  -> ExecutionEvidence
       selected provider/profile
       actual placement
       execution-context identity
  -> completion validation / Admission
```

The minimum Phase 80 slice proves INLINE, DELEGATED, and REQUIRED independence. Richer provider capability negotiation, fallback/escalation, context-budget/resource routing, and Human/AI/remote participant generalization can follow from operational evidence.
