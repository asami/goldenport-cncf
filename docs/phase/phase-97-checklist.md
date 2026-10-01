# Phase 97 Checklist: AI Audit and Interaction Evidence

status=planned

- [ ] P97-01 Freeze Interaction/Context/Request/Response/Outcome/Evaluation/Evidence terminology.
- [ ] P97-02 Define correlation to Job, Workflow, Continuation, Action/JudgmentAction and Component/Service/Operation.
- [ ] P97-03 Separate logical AI context from actual provider wire request/response.
- [ ] P97-04 Define provider-neutral execution metadata, retry/escalation and adopted/rejected outcomes.
- [ ] P97-05 Define classification, redaction/reference, authorization and retention hooks.
- [ ] P97-06 Integrate authoritative persistence with CNCF built-in Service Bus Journal.
- [ ] P97-07 Define sanitized AI Audit -> Observability projection with AIInteractionId/auditRef back pointer and no raw payloads by default.
- [ ] P97-08 Provide integration contract for textus-ai-core/textus-ai-runtime and Workflow/Continuation.
- [ ] P97-09 Prove delayed outcome/evaluation attachment including human correction and downstream failure.
- [ ] P97-09A Prove Experiment run/arm correlation is inherited from ExecutionContext without Experiment-specific AI API parameters, and that Experiment Observation can back-reference AIInteractionId.
- [ ] P97-10 Expose query/projection contract for Control Center and cbd-support KPI analysis.
- [ ] P97-11 Document progressive determinization from evidence to tuning/guard to Workflow/rule/program.
- [ ] P97-12 Validate a representative Continuation/JudgmentAction interaction end to end, including OTel projection correlation.
- [ ] P97-13 Complete review, tests and consumer handoff.
