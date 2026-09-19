# Phase 64 / Phase 77 Ownership Alignment

Date: 2026-09-20
Status: normative planning clarification

Phase 64 scope reduction was reconciled against the latest Phase 77.

Phase 64 owns Composite StateMachine semantics and only the semantic WorkflowDefinition/WorkflowInstance identity, lifecycle/progression and correlation model required to hand off into Phase 77. It does not own an independently durable WorkflowInstance store, persisted revision/history, suspension state, Continuation identity or replay/stale-result protection.

Phase 77 owns the provider-neutral independently durable WorkflowInstance persistence SPI and its persisted revision/history/current-suspension contract, plus StateMachine API/SPI provider runtime, ActionExecution, bounded advance, durable Continuation/resume, typed Workflow Protocol Value Objects and JSON encoding, Generic Skill projection and sm-workflow consumer handoff.

A consuming component such as sm-workflow supplies the concrete datastore/provider, migration, retention and lease policy; this does not move the generic persistence SPI contract out of CNCF Phase 77.

This removes the previous ambiguity where Phase 64 appeared to own a separate WorkflowInstance persistence SPI while Phase 77 also defined the durable store semantics.
