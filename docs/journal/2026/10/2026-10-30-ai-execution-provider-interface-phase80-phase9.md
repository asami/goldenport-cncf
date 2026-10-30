# AI Execution Provider Interface: CNCF Phase 80 and sm-workflow Phase 9

Date: 2026-10-30
Status: Design proposal / implementation handoff
Related: CNCF Phase 80, sm-workflow Phase 9, CNCF WorkflowProtocolV1, WorkflowExecutionHarness, textus-ai-runtime

## Decision

Preserve the generic CNCF Workflow semantics and the existing ExecutionRequirement / ExecutionEvidence / ExecutionPlacement / ExecutionIndependence contract. Treat **API Direct** and **Agent Continuation** as execution-interface capabilities of concrete providers, not as a Workflow-wide semantic mode and not as synonyms for INLINE/DELEGATED.

sm-workflow maps application WorkClassification and requirements to a versioned, configured Provider Routing policy. The execution harness selects a provider satisfying the requirement and dispatches through the selected interface. CNCF owns the provider-neutral contract and execution evidence; sm-workflow owns software-development-specific selection and Takeover policy.

## Routes

```text
sm-workflow -> ExecutionRequirement -> Provider Routing
  -> API Direct provider -> textus-ai-runtime -> OpenAI API / local model
  -> Agent Continuation provider -> Codex / Dot / OpenClaw
       -> typed result / execution evidence
       -> Workflow validation / Review / Admission
```

(a) API Direct is a direct model invocation, not a Dot-mediated task. (b) Dot Native and (c) Dot-created Codex task are agent execution routes, subject to their distinct usage accounting. Codex may also be connected directly without Dot. Dot Native's externally initiated invocation mechanism is not assumed available until integration verification; an agent-initiated MCP/skill continuation may be used where admitted.

## Independent dimensions

- **Reasoning requirement:** abstract reasoning level, capabilities, risk, review requirement and optional application ReasoningClass.
- **Placement/independence:** INLINE/DELEGATED and OPTIONAL/REQUIRED refer to execution context and review separation, not API versus agent.
- **Execution interface capability:** provider supports direct inference API, external-agent continuation, or both; provider profiles declare actual supported interfaces and tool capabilities.
- **Concrete mapping:** model, effort, endpoint, agent, cost, locality, credentials and fallback are versioned config/policy, never StateMachine semantics.

Do not add an ExecutionMethod to generic Workflow semantics merely to distinguish these routes. First inspect existing WorkflowExecutionHarness, provider registry/SPI and configuration to determine whether interface capability can be expressed by existing typed provider metadata. Add the smallest generic extension only if a concrete gap is demonstrated.

## CNCF Phase 80 ownership

1. Inventory WorkflowExecutionHarness, provider registry/SPI, provider capability representation and existing configuration, including which Direct/Continuation paths are already executable.
2. Define application-neutral provider execution-interface capability and compatibility checks; keep orthogonal to ExecutionPlacement and independence.
3. Define typed dispatch and evidence fields sufficient to correlate interface, selected provider/profile, context identity and result with the same issued WorkOrder/Continuation identity.
4. Reuse existing Continuation issuance, one-shot completion, validation and authorization; no parallel protocol or new StateMachine transition.
5. Prove API Direct and Agent Continuation providers can satisfy an equivalent generic semantic requirement, including failure/decline and backward compatibility. Only deliver extensions justified by real sm-workflow consumer needs.

## sm-workflow Phase 9 ownership

1. Map WorkClassification, scope and ExecutionRequirement to a config-selected provider profile and execution interface. No routing-only LLM call.
2. Initial hypothesis: Parent Sol High; single-file implementation Terra High; multi-file Sol Medium; complex architecture Sol High. Treat these as replaceable config values, not normative model names.
3. Integrate API Direct through textus-ai-runtime and Agent Continuation through the existing typed continuation/harness boundary, subject to actual provider availability.
4. Keep COMPLETED / DECLINED / BLOCKED distinct. Decline or observed non-convergence triggers bounded Takeover; BLOCKED requires upstream/human decision, not blind escalation.
5. Preserve WorkOrder identity, candidate, validation history, evidence and WorkOrder-wide repair budget across Takeover. Reset only provider-local attempt/stall observations; the existing shared automatic-Fix hard limit is not reset by provider change.
6. Compare actual latency, cost, review/Admission and failure evidence when available, but do not make feedback-based policy auto-updates a prerequisite.

## Boundary and acceptance

- Both interfaces produce compatible typed semantic result/evidence and enter the same Workflow Review/Admission route.
- INLINE/DELEGATED does not implicitly select API/Agent; REQUIRED independence is still enforced.
- Switching interface or provider does not change Workflow semantics, commit authority, or expand allowed Git/SBT/process capabilities.
- Existing deterministic operations (git/sbt/test) remain Workflow-owned and authorized; free-form AI command execution is not introduced.
- Config can switch API Direct / Agent Continuation without source-code edits to the Workflow definition.
- Dot-specific limitations, billing and API availability are validated independently and are not asserted as CNCF runtime guarantees.

## References

- CNCF: docs/phase/phase-80.md; src/main/scala/org/goldenport/cncf/workflow/WorkflowProtocolV1.scala
- sm-workflow: docs/phase/phase-9.md; docs/notes/cncf-workflow-protocol-application-specialization.md

## Upper-layer orchestration boundary (2026-10-30)

The architectural authority for Dot/agent supervision versus deterministic orchestration is `asami/textus-orchestrator/docs/design/ai-agent-deterministic-orchestration.md` and its journal `docs/journal/2026/10/2026-10-30-dot-agent-orchestration-boundary.md`. Dot/Astra is intended for human-facing intent clarification and bounded non-routine judgment; textus-orchestrator owns routine participant routing, coordination and correlation without replacing owning Workflow state/Admission. Bulk development inference uses metered API Direct or admitted Agent Continuation. Do not depend on temporary Dots allowance or undocumented autonomous local execution behavior. CNCF Phase 80 and sm-workflow Phase 9 keep their existing respective generic execution-contract and application routing responsibilities.
