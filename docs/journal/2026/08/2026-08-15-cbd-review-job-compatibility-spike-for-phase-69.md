# CBD Review Job Compatibility Spike — Phase 69 Reference

status=rejected-for-cbd-support
recorded_at=2026-08-15
phase_reference=[Phase 69 - Comprehensive Job Management](../../../phase/phase-69.md)

## Purpose

Record the reusable findings from an uncommitted `textus-cbd-support` design
spike without adopting the spike in CBD Support Phase 8. This journal is
reference evidence for Phase 69 inventory and contract reconciliation. It is
not Phase 69 implementation evidence and does not change Phase 69 status.

## Context

After the committed CBD Support Phase 8 review-boundary repair
`4934c50ad7a7362a4b61ef8fc0232276048cfba8`, follow-up work expanded beyond
Phase 8 closeout. The spike tried to preserve the existing synchronous
provider-document submission ABI while routing admitted documents through a
Persistent CNCF Job and CBD Entity settlement path.

The spike compiled and its focused boundary suite passed:

- `Test / compile`: invocation `37198-20260815T082709Z`, exit 0.
- six focused suites: invocation `38617-20260815T083022Z`, 61 tests, 0 failures.

These results establish that the experiment was coherent enough to execute;
they do not make it an accepted CBD Support or CNCF design.

## Explored Design

The spike explored:

- authorization and supplied-provider document admission before Job submit;
- a Persistent Job binding for Review ID, target, profile, and reuse identity;
- canonical Review response construction inside a Job Task;
- exact terminal-result binding and lazy Entity settlement;
- Review reuse identity normalized independently of correlation Review ID;
- collision handling for the same Review ID across different reuse roots;
- compatibility responses containing the canonical response and artifact
  bundle expected by the existing synchronous operation.

## Rejected Workarounds

The implementation is not adopted because several local workarounds attempted
to compensate for missing framework contracts:

1. **Synchronous wait over an asynchronous Job**

   The compatibility action waited for at most 20 seconds for Job completion.
   This hides an asynchronous protocol behind a timeout and creates avoidable
   load, retry, cancellation, and failure-semantics debt. A durable Job API
   should expose start, exact status/result retrieval, and control explicitly;
   clients that need completion should poll or await through a deliberate
   asynchronous protocol.

2. **Run snapshot reused as a Review-ID reservation**

   The spike used an immutable Review Run snapshot as a cross-reuse-root
   reservation. Run history and submission reservation are different domain
   responsibilities. A production design needs a dedicated durable submission
   intent/reservation, or an outbox/admission record with explicit ownership,
   fencing, idempotency, and recovery semantics.

3. **Bounded full-list scan used as Job correlation lookup**

   A high `listJobs` limit cannot provide completeness, stable continuation,
   or efficient parameter lookup. Phase 69 must supply exact indexed lookup
   and cursor-based enumeration rather than requiring component scans.

4. **Current-process Job result dependency**

   The current in-memory Job record can retain the successful operation result
   within one engine state, but the lightweight persistent projection cannot
   reconstruct the exact result after process restart. A component-specific
   shadow store is not an acceptable substitute for the Phase 69 durable Job
   execution/result record.

5. **Synthetic lifecycle transitions for reservation conflict**

   The spike projected an admitted Review through queued to failed in order to
   retain a reservation collision. Framework lifecycle history should describe
   actual execution, while admission conflicts need their own structured
   outcome and durable intent state.

6. **JSON field removal as reuse normalization**

   Structurally removing correlation and self-digest fields can produce a
   deterministic experimental key, but a production contract needs a named,
   versioned canonical idempotency/fingerprint schema owned by the admission
   protocol rather than a component-local normalization convention.

7. **Fixed development-profile compatibility path**

   Mapping supplied evidence into a fixed development profile preserved the
   old call shape but did not define a general server-owned execution-policy
   contract.

## Phase 69 Requirements Exposed by the Spike

This experiment reinforces the existing Phase 69 scope:

- restart-safe persistent Job, Task, input, binding, result, and timeline
  records;
- exact authorized Job lookup and cursor-based enumeration;
- typed terminal-result retrieval independent of live in-memory task objects;
- durable submission intent with explicit idempotency and collision behavior;
- admitted reconstructible execution descriptors rather than serialized live
  closures or provider objects;
- explicit asynchronous start/status/result/cancel contracts;
- stable compatibility behavior that does not claim synchronous completion by
  waiting for an arbitrary timeout;
- cross-process downstream acceptance using CBD Support only after CNCF owns
  the required persistence and query contracts.

## Disposition

- Do not commit or retain the spike implementation in CBD Support Phase 8.
- Preserve the committed Phase 8 boundary repair and its validation evidence.
- Do not change, delete, or renumber CNCF Phase 69 because of this experiment.
- Use this journal only as input to `JM69-01` inventory and failing-first
  acceptance design.
- If CBD Support later adopts the capability, design it against the completed
  CNCF asynchronous and restart-safe contracts rather than reviving the
  compatibility adapter.
