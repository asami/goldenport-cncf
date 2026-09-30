# AI Audit as Business AI Improvement Evidence

- Date: 2026-10-01
- Status: Design decision / Phase 97 planning input

Business applications using AI need more than request logging. CNCF will treat AI Audit as a built-in cross-cutting runtime capability and preserve AI communication context, actual request, actual response and later outcome/evaluation as correlated AI Interaction evidence.

The evidence has two purposes: operational audit/detection of AI deviation or runaway behavior, and engineering feedback. The latter supports prompt/context tuning, guards, provider/routing improvement, Human-in-the-Loop review and progressive conversion of sufficiently stable AI work into deterministic Workflow, rules or programs.

The common mechanism belongs in CNCF rather than being reimplemented by textus-ai-core/textus-ai-runtime, Workflow Continuation or each application. Authoritative selected records use the CNCF built-in Service Bus Journal boundary.

Observability is downstream of AI Audit. AI Audit projects only compact, sanitized operational information into OpenTelemetry. OTel carries AIInteractionId/auditRef as a back pointer to the authoritative record plus useful metadata/metrics such as correlation identity, provider/model where permitted, duration, usage/cost summary, status, retry/escalation and validation outcome. Raw context/request/response bodies remain in policy-controlled Audit storage and are not exported to OTel by default. Operational investigation can therefore start in Grafana/Jaeger and follow auditRef back to the detailed AI Interaction.

Phase 97 owns the generic runtime contract. textus-ai-core/textus-ai-runtime and Workflow/Continuation are primary producers/consumers; cbd-support and Control Center are analysis/visibility consumers. Candidate-Admission can attach admission/review outcomes as later evidence without merging Admission semantics into AI Audit.
