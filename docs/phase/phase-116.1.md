# Phase 116.1 — Bounded automatic retirement of local CAR expansions

status=planned
planned_at=2026-11-24
execution_started=false
owner=CNCF

Depends on [closed Phase 116](phase-116.md). Progress: [Phase 116.1 checklist](phase-116.1-checklist.md).

## Goal

After a new local CAR expansion is successfully published, automatically reclaim eligible previous **expanded copies** within bounded work. Reuse Phase 116 generation identities, atomic publication, references and cross-JVM leases. Preserve source CARs, active readers and previous usable state on publication failure. Phase 116 stays closed.

## Motivation

Mac mini inspection on 2026-11-24 found 29 different digests for SmWorkflow 0.1.1-SNAPSHOT, accounting for about 98 GB of expanded cache payload. Two sampled historical validation builds were superseded under a shared coordinate but still selected by separate archive-path keys. Age, a matching release label or a pending-retirement flag alone cannot establish that a generation is safe to retire. The measured size is not a reclaim guarantee. ScalaDoc packaging separation is independently owned by Cozy Phase 86 and CNCF Phase 112; this lifecycle fix must work on existing CARs too.

## Delivery groups

| Step | Work | Observable outcome |
| --- | --- | --- |
| P116.1-01 | Eligibility and alias lifecycle | Define how obsolete, explicitly owned validation aliases become eligible without merging path identities or dropping a legitimate current reference. Unclassified aliases remain protected. |
| P116.1-02 | Bounded automatic reclamation | After successful publication of a new local expansion, run bounded extraction-only retirement through the existing cache owner. Retry blocked work after release or on a later eligible publication; cheap hits do not run cache-wide scans. |
| P116.1-03 | Qualification and handoff | Reproduce same-coordinate replacement and the separate-validation-path accumulation, verify live-reader and failure cases, then qualify the installed route on isolated fixtures and report measured reclamation and retained reasons. |

## Boundaries

- Remove only managed expanded CAR payloads. Do not remove source archives, caller-owned files, validation records, source trees or installations. Do not invoke unfiltered collection as an extraction-only operation.
- Before deletion, hold existing exclusive protection and recheck current references and leases across JVMs. A failed new publication must not retire the usable previous generation. Failed or contended reclamation must be observable and retryable without invalidating a successfully published result.
- No cache-wide scan on normal hits, no hard quota or reservation layer, no change to SAR semantics, and no blanket sweep of legacy aliases. Existing live cache maintenance is an independently authorized operation.

## Completion

Close only on the checklist evidence. Plan registration does not start a Session, publish a runtime, or authorize deleting any cache on the Mac mini.
