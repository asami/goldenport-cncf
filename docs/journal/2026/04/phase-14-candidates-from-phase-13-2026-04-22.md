# Phase 14 Candidates Derived from Phase 13

## Summary

Phase 13 is closed in CNCF.

This note records the items that remain intentionally outside the Phase 13
runtime contract and are therefore the natural candidates for the next phase.

The point of this note is separation:

- Phase 13 stays closed and stable.
- Phase 14 candidates are tracked explicitly rather than being left implicit in
  closed Phase 13 documents.

## Closed in Phase 13

The following are already established and should not be reopened as Phase 14
baseline work:

- same-subsystem sync-inline event reception
- async new-job continuation baseline
- async failure disposition classification
- event/job/admin inspection visibility baseline
- typed rule provenance and precedence
- bundle and participant factory construction model
- real runtime componentlet identity as the canonical runtime participant model

Phase 14 should build on these contracts rather than revisiting them.

## Phase 14 Candidate Work

### 1. Dead-Letter and Poison-Event Handling

Phase 13 stops at failure classification and visibility.

Still open:

- dead-letter destination model
- poison-event criteria
- operator recovery flow after repeated failure
- inspection/admin visibility for dead-letter state

Reason for Phase 14:
- this extends async failure handling beyond classification into lifecycle
  control, which was explicitly kept out of Phase 13.

### 2. Retry Orchestration

Phase 13 classifies async failures as `Retryable` or `Terminal`, but does not
implement the orchestration layer.

Still open:

- retry scheduling strategy
- retry budget / retry limit policy
- backoff model
- operator-triggered retry controls
- visibility of retry attempt history

Reason for Phase 14:
- this changes operational behavior, not just metadata or classification.

### 3. Final Saga Identity Standardization

Phase 13 keeps saga relation semantics but does not finalize one canonical
`sagaId` contract.

Still open:

- canonical saga id field and propagation rule
- relation between correlation id and saga id
- inspection naming and projection stability
- replay/lineage implications once saga id is formalized

Reason for Phase 14:
- the current relation model is sufficient for Phase 13, but final saga
  standardization is a separate contract decision.

### 4. ABAC Execution in Event Rule Matching

Phase 13 freezes the precedence rule that future ABAC belongs inside explicit
rule matching, but does not execute ABAC conditions.

Still open:

- ABAC condition model in reception matching
- evaluation timing and subject/resource context mapping
- failure/deny semantics
- diagnostics for ABAC-based non-match or deny outcomes

Reason for Phase 14:
- this is a policy-execution feature, not a prerequisite for the current event
  runtime.

### 5. External / Multi-Subsystem Event Transport

Phase 13 finalizes semantic/runtime handling for same-subsystem and async job
continuation, but not inter-subsystem transport.

Still open:

- transport boundary and ingress contract
- source-subsystem trust and validation model
- delivery and retry behavior across subsystem boundaries
- external event persistence and inspection semantics

Reason for Phase 14:
- this is a larger transport/runtime boundary problem and should not be mixed
  into the Phase 13 closure contract.

### 6. Broader Metrics / OpenTelemetry Integration

Phase 13 provides operator-facing inspection surfaces and traceable metadata but
stops short of broader telemetry export.

Still open:

- event/job metric export set
- OpenTelemetry span/attribute integration strategy
- correlation between builtin inspection and external telemetry backends

Reason for Phase 14:
- this is observability expansion, not core event/runtime contract closure.

### 7. Authorization Expansion on Top of the Event Runtime

Phase 13 stabilizes the event runtime and componentlet identity model. Broader
authorization layering on top of that runtime is still open.

Still open:

- finer-grained authorization around event inspection and job inspection
- operator/admin authorization expansion
- event-specific authorization policy surfaces beyond the current baseline

Reason for Phase 14:
- the runtime contract is now stable enough that stronger authorization policy
  can be layered without changing participant semantics.

## Recommended Ordering

If Phase 14 starts from the Phase 13 closure state, the pragmatic order is:

1. retry orchestration
2. dead-letter / poison-event handling
3. final saga identity standardization
4. ABAC execution in explicit rule matching
5. external / multi-subsystem event transport
6. broader telemetry and authorization expansion

Reasoning:
- retry/dead-letter build directly on the disposition contract already fixed in
  Phase 13
- saga and ABAC change semantic contracts and should come after the operational
  failure path is complete
- external transport is broader and riskier than same-subsystem/runtime-local
  hardening

## Non-Goals for Phase 14 Planning

The following should not be reopened as candidate work unless a concrete defect
is found:

- reverting bundle/participant construction back to flat component vectors
- reverting runtime componentlet identity to root alias projection
- changing child-job submission timing from post-outer-commit
- weakening event/job/admin field naming already stabilized in Phase 13

## References

- [phase-13.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-13.md)
- [phase-13-checklist.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-13-checklist.md)
- [phase-13-closure-result-2026-04-22.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-closure-result-2026-04-22.md)
