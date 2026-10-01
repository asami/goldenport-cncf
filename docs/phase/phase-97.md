# Phase 97: AI Audit and Interaction Evidence

status=planned
planned_at=2026-10-01
depends_on=[Phase 77](phase-77.md), [Phase 90](phase-90.md)
related=[Phase 91](phase-91.md)
consumer=textus-ai-core/textus-ai-runtime, Workflow/Continuation, sm-workflow, textus-cbd-support
checklist=[Phase 97 Checklist](phase-97-checklist.md)

## Purpose
Make AI Audit a built-in CNCF cross-cutting capability for business applications using nondeterministic AI execution. Preserve communication context, actual request, actual response and later outcome/evaluation so the same evidence supports audit, deviation/runaway detection, prompt/context tuning, Human-in-the-Loop review, routing improvement and progressive determinization into Workflow/rules/programs.

AI Audit is not an AI execution engine.

## Core model
An AI Interaction correlates execution context (Subsystem/Component/Operation/Job/Workflow/Continuation/Action/JudgmentAction/caller), AI context (provider/model/configuration/prompt version/tools/skills/resources), actual request/wire input, actual response/wire output and metadata, and later outcome/evaluation (accept/reject, correction, retry/escalation, Admission result, downstream success/failure, human review).

Logical context and the actual wire request are distinct records.

## Architecture boundary
~~~text
AI consumer / execution
  -> CNCF AI Audit API
      -> AI Interaction / Evidence
          -> CNCF Service Bus / Journal (authoritative)
          -> Observability Projection
               -> compact/sanitized OTel trace, metric and log data
               -> auditRef = AIInteractionId
          -> Control Center / cbd-support / analysis
               -> Prompt/Context/Guard improvement
               -> Workflow/Rule/Program determinization
~~~

CNCF owns provider-neutral contracts, correlation, policy hooks and persistence integration. textus-ai-core/textus-ai-runtime and Workflow/Continuation are consumers. Applications own domain-specific evaluation semantics. cbd-support consumes evidence/KPIs rather than becoming the authoritative store.

## Observability projection
AI Audit is the source of truth for AI interaction evidence. Observability is a sanitized operational projection derived from AI Audit, not an independent detailed record.

The projection carries only operationally useful compact information such as AIInteractionId/auditRef, execution correlation, provider/model identity where permitted, duration, usage/cost summaries, result status, retry/escalation and validation status. Prompt/context/request/response bodies do not flow to OpenTelemetry by default.

OpenTelemetry traces, metrics and logs retain a back pointer to the authoritative AI Interaction through AIInteractionId/auditRef. Operational investigation therefore flows from dashboards/traces to AI Audit when detailed context is required. AI execution code should not independently duplicate detailed audit payloads into OTel.

This pattern may later generalize to other CNCF authoritative audit records: authoritative record -> sanitized observability projection -> back reference.

## Policy
Raw request/response can contain secrets, personal data or business-confidential content. Recording therefore requires classification/redaction/reference, authorization and retention policy. Metadata-only or referenced payload storage must be possible.

## Engineering feedback loop
AI execution -> Interaction evidence -> Evaluation -> anomaly/deviation detection -> prompt/context/guard tuning -> Workflow/rule/program candidate -> deterministic implementation where justified.

Correction, retry, escalation, admission rejection, validation failure, latency, cost and downstream outcome should be derivable per Component/Service/Operation/Workflow/AI Action.

## Non-goals
- Provider-specific SDK abstraction/model routing.
- Treating OpenTelemetry as authoritative audit storage.
- Event Sourcing application state from AI records.
- Automatic policy/program changes without application-owned admission/approval.
- Unrestricted raw prompt/response persistence by default.

## Experiment correlation

AI Audit does not make AI execution Experiment-aware. When an AI call occurs inside a Textus Experiment run/arm, Experiment identity is inherited from CNCF ExecutionContext/correlation context and captured automatically with the AI Interaction. The AI API does not require Experiment-specific parameters and textus-ai-runtime does not branch on Experiment semantics.

Applicable correlation includes experimentId, experimentRunId and experimentArmId (or their canonical CNCF equivalents). Experiment Observation may retain an AIInteractionId/evidenceRef when the observed implementation used AI. The Experiment record and AI Audit record remain separate authoritative records for different concerns; the reference connects them without copying prompt/request/response payloads into Experiment.
