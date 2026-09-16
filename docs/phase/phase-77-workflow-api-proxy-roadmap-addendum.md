# Phase 77 Addendum: Workflow API Proxy Roadmap

Status: planned / forward-compatibility clarification

## Current Phase 77 requirement

Workflow SPI runtime identity, correlation and provider contracts must remain extensible to a future caller-side Workflow API/proxy layer.

## Follow-up capability

A later phase will provide:

- generated/typed caller-side Workflow API proxy
- logical Workflow-to-Workflow binding
- LocalWorkflowBinding
- RestWorkflowBinding
- durable call correlation across callee suspension/resume
- provider compatibility/admission
- runtime/deployment transport configuration
- contract tests proving local/REST behavioral equivalence at the Workflow API boundary

## Invariant

Caller Action implementations must depend only on the generated logical Workflow API. They must not branch on local vs REST placement and must not implement Continuation/correlation mechanics themselves.

## Non-goal

Do not expand the immediate Phase 77 Skill-driven Workflow foundation to implement these connectors before the SPI/suspend/resume contract is stable.
