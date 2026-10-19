<!-- cncf-phase-finding:HYG-P103-S4-001:begin -->
### HYG-P103-S4-001 — Refresh historical consumer-admission projections

Status: OPEN
Discovered: 2026-10-19, independent P103-S4 review.
Owner: CNCF documentation owner
Repository: goldenport-cncf
Locations: `docs/spec/validation-test-metadata.md:530`; `docs/design/typed-sbt-execution.md:240`

Evidence: These descriptive delivery paragraphs still say actual sm-workflow source admission/fixture delivery is future work. Current Phase 103, its checklist and consumer handoff instead record the consumer-owned K2/L3 delivery and distinguish pending CNCF integrated acceptance. The older paragraphs link that current authority; their declaration/binding semantics do not impose a different execution contract.

Disposition and nonblocking basis: Journal as documentary maintenance. Align only the delivery projection in a separately owned documentation update; preserve the normative typed-input work and historical validation evidence. This is not a current authority conflict and does not require consumer source editing.

Follow-up: Producer documentation synchronization after S4 acceptance
<!-- cncf-phase-finding:HYG-P103-S4-001:end -->

<!-- cncf-phase-finding:HYG-P103-S4-002:begin -->
### HYG-P103-S4-002 — Move the existing automatic-capture Fixture to an instance-independent owner

Status: OPEN
Discovered: 2026-10-19, independent P103-S4 review.
Owner: CNCF operation-evaluation specification owner
Repository: goldenport-cncf
Locations: `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationAutomaticCaptureSpec.scala:977`

Evidence: Fixture remains a private case class inside OperationEvaluationAutomaticCaptureSpec. The identical instance-owned declaration exists at HEAD before this Step. The shared type-modeling rule forbids instance-bearing ownership for fixture data, even when private. S4 changes the clock-failure scenario and observer, not this declaration or its type identity; no new outer-reference warning was observed in focused004's retained output.

Disposition and nonblocking basis: Existing type-modeling maintenance debt. Move it to a package-level companion under a separately scoped conformance cleanup, preserving its constructors and behavior. Do not expand the current Action-context repair.

Follow-up: Operation-evaluation specification conformance task
<!-- cncf-phase-finding:HYG-P103-S4-002:end -->

<!-- cncf-phase-finding:HYG-P103-S4-003:begin -->
### HYG-P103-S4-003 — Plan a responsibility-based split of shared Display fixture support

Status: OPEN
Discovered: 2026-10-19, independent P103-S4 review.
Owner: CNCF Display specification owner
Repository: goldenport-cncf
Locations: `src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceFixture.scala:53`

Evidence: The shared non-generated fixture support is 1,722 lines (1,675 at HEAD), exceeding the ordinary-source 1,000-line debt trigger and normal 1,500-line upper guidance. It combines provider/store adapters, normal component assembly, execution/session setup, independent physical-state oracles and shared assertion vocabulary. S4 adds the necessary actual-context observation to the existing Log/Execution responsibility and strengthens existing vocabulary; no new unrelated responsibility or unsafe structure was identified.

Disposition and nonblocking basis: Hygiene split disposition under RULE.md. This support object is consumed through nested public fixture types and imports by many existing Display specifications; a safe responsibility split requires coordinating those existing consumers and preserving static data ownership, provider setup and initialization. It is not a bounded local mechanical split and must not enter the present repair scope. Increased line count alone is not treated as a new correctness or reviewability failure.

Follow-up: Dedicated Display fixture organization task with preserved full-state and replay coverage
<!-- cncf-phase-finding:HYG-P103-S4-003:end -->

<!-- cncf-phase-finding:HYG-P103-S4-004:begin -->
### HYG-P103-S4-004 — Retain an explicit source-size disposition for ExecutionContext

Status: OPEN
Discovered: 2026-10-19, independent P103-S4 review.
Owner: CNCF execution-context owner
Repository: goldenport-cncf
Locations: `src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala:1`

Evidence: The unchanged affected edge is 1,149 lines, crossing the ordinary-source 1,000-line debt trigger. It combines context models with runtime/profile/scope rebinding and many public context transforms. S4 changes none of its bytes or accepted ownership semantics.

Disposition and nonblocking basis: Existing source-size Hygiene. A split must preserve companion-owned data identity, public transforms, runtime rebinding and existing callers; it requires separate design and consumer coordination rather than an incidental extraction in this Step.

Follow-up: Execution-context organization task; no current Phase API or lifecycle expansion
<!-- cncf-phase-finding:HYG-P103-S4-004:end -->

<!-- cncf-phase-finding:HYG-P103-FULL-001:begin -->
### HYG-P103-FULL-001 — Record the inherited ProcessExecutionModel source-size follow-up

Status: OPEN
Discovered: 2026-10-19, sole independent Phase 103 full review.
Owner: CNCF process-execution owner
Repository: goldenport-cncf
Locations: `src/main/scala/org/goldenport/cncf/processexecution/ProcessExecutionModel.scala:14`; `src/main/scala/org/goldenport/cncf/processexecution/ProcessExecutionModel.scala:334`; `src/main/scala/org/goldenport/cncf/processexecution/ProcessExecutionModel.scala:939`

Evidence: The hand-written ordinary source is 1,107 lines in the current tree, 1,105 at accepted S3 and 1,076 at the original Phase base. It already crossed RULE.md's 1,000-line source-size debt trigger. It groups public process value types/limits, protected program definitions and factories, grants/admission, request resolution and policy. S3's accepted review recorded this as inherited size/naming debt, but the canonical Phase Hygiene journal currently contains only the four S4 records. The Phase addition narrows an existing typed SBT definition inside its existing admission responsibility; no new unrelated responsibility or current correctness failure was found.

Disposition and nonblocking basis: Existing debt / Hygiene follow-up. Record an explicit canonical follow-up rather than silently lose the inherited S3 disposition. Reorganize by process-model responsibilities together with the affected public/protected factory, codec, interpreter and runtime callers; preserve package/type identities, initialization, binding/grant narrowing and serialization. A coordinated responsibility split must account for those existing consumers and protected ownership. Do not perform incidental partial extraction or broad renaming inside this completed Phase.

Follow-up: Dedicated CNCF process-execution model organization task, with an admitted split plan covering public/protected consumers and the existing capability/admission/codec regression surface.
<!-- cncf-phase-finding:HYG-P103-FULL-001:end -->
