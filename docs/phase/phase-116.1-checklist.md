# Phase 116.1 checklist — Bounded automatic retirement of local CAR expansions

status=planned
execution_started=false

Owner: CNCF. Plan: [Phase 116.1](phase-116.1.md). All items are OPEN.

## P116.1-01 — Eligibility and alias lifecycle

- [ ] Freeze the supported local publication trigger, eligibility rules, alias ownership and expiry/retirement criteria, and bounds/continuation in the owning cache specification.
- [ ] An owned, obsolete validation alias may stop protecting a superseded expansion; a deliberately selected alternate or unknown alias remains protected. Neither digest age, release name nor a pending flag alone authorizes retirement.

## P116.1-02 — Bounded automatic reclamation

- [ ] A successful new CAR expansion reclaims eligible previous expansions on the ordinary local route without operator intervention, within the declared bounds; repeated publications make progress beyond the first batch. Completed hits perform no unrelated inventory.
- [ ] Cross-JVM leases and independently current references protect old readers; recheck under existing exclusion. When protection ends, reclamation can be retried. Preserve the newest and all legitimately current payloads from this retirement path.
- [ ] Failed publication preserves the previous usable selection. Cleanup failure or contention is reported and retryable without invalidating a successful publication.
- [ ] Assert that source CARs, caller originals, repositories, validation records and installations remain untouched. Do not use general collection with source-deletion eligibility for this extraction-only path.

## P116.1-03 — Qualification and handoff

- [ ] Fixture cases cover same-coordinate replacement, multiple historical validation paths plus a legitimate alternate path, protected readers, retry, and publication/deletion failure.
- [ ] Qualify the exact installed runtime and local launcher against disposable fixtures; record reclaimed/retained/skipped bytes and reasons, and verify offline re-expansion from a retained source.
- [ ] Record affected executable checks, integrated review and repository-required final validation before closure. No production or developer cache sweep is part of acceptance.
