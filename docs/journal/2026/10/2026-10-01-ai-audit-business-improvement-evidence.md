# AI Audit as Business AI Improvement Evidence

- Date: 2026-10-01
- Status: Design decision / Phase 96 planning input

Business applications using AI need more than request logging. CNCF will treat AI Audit as a built-in cross-cutting runtime capability and preserve AI communication context, actual request, actual response and later outcome/evaluation as correlated AI Interaction evidence.

The evidence has two purposes: operational audit/detection of AI deviation or runaway behavior, and engineering feedback. The latter supports prompt/context tuning, guards, provider/routing improvement, Human-in-the-Loop review and progressive conversion of sufficiently stable AI work into deterministic Workflow, rules or programs.

The common mechanism belongs in CNCF rather than being reimplemented by textus-ai-runner, Workflow Continuation or each application. Authoritative selected records use the CNCF Service Bus Journal boundary; OpenTelemetry remains observational. Raw prompt/response capture is policy-controlled because it can contain sensitive data.

Phase 96 owns the generic runtime contract. textus-ai-runner and Workflow/Continuation are primary producers/consumers; cbd-support and Control Center are analysis/visibility consumers. Candidate-Admission can attach admission/review outcomes as later evidence without merging Admission semantics into AI Audit.
