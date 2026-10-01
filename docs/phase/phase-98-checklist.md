# Phase 98 Checklist: Online Multi-Arm Experiment Routing in ActionEngine

status=planned

- [ ] P98-01 Freeze the ActionEngine/Provider/OperationEvaluation insertion boundary.
- [ ] P98-02 Define online Experiment Binding and assignment resolver contract without assuming two Arms.
- [ ] P98-03 Separate Allocation Policy from Stickiness Policy and define admitted assignment subject/key semantics.
- [ ] P98-04 Support uniform/fixed-weight multi-arm allocation as baseline and preserve an extension boundary for adaptive policies such as Thompson Sampling/UCB.
- [ ] P98-05 Propagate Experiment/Run/Arm through ExecutionContext and OperationEvaluationCorrelation.
- [ ] P98-06 Define Arm execution-plan resolution for same-operation parameter/config variation.
- [ ] P98-07 Define compatible alternate-operation routing with authorization and input/output validation.
- [ ] P98-08 Add recursion/loop protection and fail-closed invalid routing.
- [ ] P98-09 Preserve logical/physical operation and assignment identities in evaluation/observability.
- [ ] P98-10 Reuse ExperimentEvaluationSink/DeliveryRuntime for online observations.
- [ ] P98-11 Prove no-binding execution is behaviorally unchanged.
- [ ] P98-12 Prove N>2 multi-arm routing and the N=2 A/B specialization.
- [ ] P98-13 Prove same Operation M/N and Operation X/Y plan variants.
- [ ] P98-14 Prove AI-backed Arm inherits Experiment correlation into Phase 97 AI Audit without AI-runtime Experiment branching.
- [ ] P98-15 Define Observation -> Measurement -> Reward Policy boundary without embedding reward logic in ActionEngine.
- [ ] P98-16 Document offline Corpus-driven versus online request-driven Experiment boundaries.
- [ ] P98-17 Define Phase 96 Display Model handoff of server-assigned client-safe Arm/variant plus Display Instance/correlation reference.
- [ ] P98-18 Prove UI runtime renders the assigned variant without performing Arm allocation.
- [ ] P98-19 Prove Display Mutation restores authoritative Experiment/Run/Arm correlation and records mutation lifecycle/outcome.
- [ ] P98-20 Prove one Arm correlation spans Display variant -> Display Mutation -> Business Operation -> AI Audit when AI is invoked.
- [ ] P98-21 Complete focused/full tests, review and textus-experiment consumer handoff.
