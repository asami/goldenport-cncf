# Phase 76 - Hash Responsibility and Integrity Boundary Review

status=planned
planned_at=2026-09-14
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#963-hash-responsibility-and-integrity-boundary-review)
checklist=[Phase 76 Checklist](phase-76-checklist.md)

## Purpose

Clean up CNCF's hash, fingerprint, checksum, and content-digest controls.
Retain only justified artifact or network integrity uses, and remove or replace
hash-based controls from domain identity, revision, persistence, and ordinary
in-process value handling.

## Parent Ownership and Scope

- Phase 76 owns inventory, classification, remediation, validation, and
  release closure for admitted controls in the CNCF repository.
- It cleans the CNCF implementation in this Phase rather than merely creating
  child remediation Phases. An upstream or external protocol boundary remains
  with its actual owner and receives a frozen handoff only when necessary.
- New domain identity remains typed identity; concurrency remains explicit
  revision/version; ordinary in-process equality remains structural value
  comparison. A digest alone is never a substitute for any of them.
- Network/authentication, artifact/cached-content, and externally verifiable
  integrity cases require a named threat or protocol contract. A bare hash is
  not an authentication mechanism.

## Work Stack

| ID | Outcome | Status |
| --- | --- | --- |
| HIR-01 | CNCF-wide inventory and owner/consumer classification of hash, fingerprint, checksum, and digest controls. | planned |
| HIR-02 | Boundary policy and per-use disposition: retain, replace, or remove. | planned |
| HIR-03 | CNCF remediation with focused compatibility and executable evidence. | planned |
| HIR-04 | Parent review, validation, strategy/checklist reconciliation, and release closure. | planned |

No work item is active. Resume at HIR-01 when this parent Phase is explicitly
selected.

## Completion Conditions

- Every admitted hash-like control has a recorded producer, consumer, data
  boundary, persistence status, and precise purpose.
- Internal domain identity, optimistic concurrency, and normal value equality
  controls are separated from digest use; every unjustified CNCF-internal
  coupling is removed or replaced by this Phase.
- Each retained transport, artifact, cache, or external-integrity control has
  an explicit protocol/threat rationale and does not silently become a domain
  identity or authorization control.
- The Phase 69.4 JobDefinition concern is cleaned under this parent Phase's
  admitted implementation boundary; other active/planned Phase status records
  remain unchanged until their own work is selected.

## Non-Goals

- Removing ordinary Scala/JVM `hashCode` implementations required for
  collection semantics.
- Replacing artifact checksums, authenticated transport controls, signatures,
  or MACs without an inventory-backed protocol decision.
- Treating equal content as equal entity, authorization, deployment, or audit
  history.
- Reopening a closed Phase, or changing an external/upstream protocol without
  its owner's explicit authority.

## References

- [Phase 69.4](phase-69.4.md)
- [Phase 74](phase-74.md)
- [Phase 76 Checklist](phase-76-checklist.md)
