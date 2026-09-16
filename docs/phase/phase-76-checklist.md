# Phase 76 Checklist - Hash Responsibility and Integrity Boundary Review

status=planned
phase=[Phase 76](phase-76.md)

## HIR-01: Inventory and Boundary Classification

Stage Status:
- Current status: OPEN
- Owner: CNCF phase coordinator with each discovered control's actual owner
- Update rule: Close only from an inventory that identifies the producer,
  consumer, value boundary, persistence status, and stated purpose of every
  admitted control.

- [ ] Inventory production hash, fingerprint, checksum, and content-digest
      controls across admitted CNCF code, records, runtime messages, artifact
      metadata, and protocol boundaries.
- [ ] Separate ordinary JVM collection equality/hash behavior from application
      integrity controls.
- [ ] Classify each use as domain identity, revision/concurrency, in-process
      comparison, persisted field, artifact/cache integrity, network/protocol
      integrity, authentication, diagnostic, or unknown.
- [ ] Record the actual producer, consumer, persistence representation, and
      compatibility dependency; reject a guessed purpose.

## HIR-02: Policy and Disposition

Stage Status:
- Current status: OPEN
- Owner: CNCF architecture owner and admitted control owners
- Update rule: Close only from an explicit disposition for every in-scope use.

- [ ] Freeze typed identity, explicit revision/version, and structural value
      equality as the default controls for internal domain data.
- [ ] Require an explicit external protocol, cache/content-addressing, or
      diagnostic rationale for every retained digest control.
- [ ] Treat a bare digest as insufficient for authentication or authorization;
      identify the owning signature/MAC or other security contract where needed.
- [ ] Mark each control retain, replace, or remove, with its reason and
      compatibility/migration consequence.

## HIR-03: CNCF Remediation

Stage Status:
- Current status: OPEN
- Owner: CNCF repository and component/protocol owners
- Update rule: Close only when every non-retained CNCF item has implemented,
  focused compatibility, and executable evidence.

- [ ] Remove or replace every non-retained CNCF control in this Phase; freeze
      exact paths, public/persistence impact, and validation evidence.
- [ ] Clean Phase 69.4's JobDefinition hash control under this Phase's admitted
      boundary without changing entity identity, persistence semantics, or
      lifecycle behavior merely to remove a digest.
- [ ] Preserve valid artifact and network controls without importing their
      digest semantics into ordinary in-process domain paths.
- [ ] Create an exact handoff only for a discovered control owned by an
      upstream or external protocol repository.

## HIR-04: Parent Review and Planning Closure

Stage Status:
- Current status: OPEN
- Owner: CNCF phase coordinator
- Update rule: Close only after the inventory, policy, remediation, validation,
  and closure evidence are independently reviewed and records agree.

- [ ] Review classification completeness, owner boundaries, and every retained
      security/integrity rationale.
- [ ] Verify no internal identity, revision, persistence, or equality use is
      justified only by a hash/fingerprint shortcut.
- [ ] Run focused and required final validation for every changed CNCF boundary.
- [ ] Reconcile strategy and phase records without changing unrelated
      active/planned Phase acceptance status.
