# Phase 98 Checklist: Online Experiment Routing in ActionEngine

status=planned

- [ ] P98-01 Inventory the ActionEngine/Provider/OperationEvaluation execution chokepoint and freeze the insertion boundary.
- [ ] P98-02 Define online Experiment Binding and admitted assignment resolver contract.
- [ ] P98-03 Define stable assignment inputs/unit/key semantics without application-specific user modeling.
- [ ] P98-04 Propagate Experiment/Run/Arm through ExecutionContext and existing OperationEvaluationCorrelation.
- [ ] P98-05 Define Arm Execution Plan resolution for same-operation parameter/config variation.
- [ ] P98-06 Define compatible alternate-operation routing with authorization and input/output validation.
- [ ] P98-07 Add recursion/loop protection and fail-closed behavior for invalid routing.
- [ ] P98-08 Preserve logical and physical operation identities in evaluation and observability.
- [ ] P98-09 Reuse ExperimentEvaluationSink/DeliveryRuntime for online observations.
- [ ] P98-10 Prove no-binding execution is behaviorally unchanged.
- [ ] P98-11 Prove same Operation M/N online A/B routing.
- [ ] P98-12 Prove Operation X/Y online A/B routing.
- [ ] P98-13 Prove AI-backed Arm inherits Experiment correlation into Phase 97 AI Audit without AI-runtime Experiment branching.
- [ ] P98-14 Document offline Corpus-driven versus online request-driven Experiment boundaries.
- [ ] P98-15 Define Phase 96 Display Model handoff of server-assigned client-safe Arm/variant plus Display Instance/correlation reference.
- [ ] P98-16 Prove UI runtime renders the assigned variant without performing Arm assignment.
- [ ] P98-17 Prove Display Mutation restores authoritative Experiment/Run/Arm correlation server-side and records mutation lifecycle/outcome.
- [ ] P98-18 Prove one Arm correlation spans Display variant -> Display Mutation -> Business Operation -> AI Audit when AI is invoked.
- [ ] P98-19 Complete focused/full tests, review and textus-experiment consumer handoff.
