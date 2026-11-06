# CNCF producer plan — App Phase 6.3 direct-upload integrity

status=closed
completed_at=2026-10-27
parent=NICT Editing Studio App Phase 6.3 / ESA63-01/02

## Current follow-on completion — 2026-10-27

This bounded App Phase 6.3 contribution is accepted at source `37096764`
and closed by the App's current ordinary completion. Original criteria and DU
obligations below are satisfied at their stated source/controlled/live scopes.
The App's canonical completion/acceptance records own the connected disposition;
detailed execution evidence remains local, outside Git. Earlier source-authoring
and pending-gate prose below is dated history, superseded by this current result.
No unrelated owning Phase, canonical main checkout, public release or production
rollout is marked complete. Failed sessions and consumed execution grants remain
unchanged; no new device/Google action is implied.


## Direct new-file upload and SHA-256 verification — planned delta

Contract and connected acceptance: [direct-upload integrity plan](/Users/asami/src/Project2026/nict-editing-studio-app/docs/notes/direct-upload-integrity-plan.md).

Producer contribution under App Phase 6.3 / ESA63-01/02 (DU-01/02). Reconcile the existing cncf-esa63-direct worktree at implementation entry. Preserve user-delegated OAuth and add/reuse server service-account token acquisition; retain credentials server-side. Extend native direct-upload preparation/status and metadata with provider-computed SHA-256 and attributable file identity. Test both authorization strategies, new-file/URI/shared-drive constraints and absent checksum semantics. No TKL domain policy or client credential distribution.

- [x] Implement the owning DU items and affected contract/behavior specifications.
- [x] Hand off actual evidence and limitations; this planning addition claims no implementation, live upload or Phase closure.

Selected-checkout adoption: imported from the read-only canonical CNCF producer
plan on 2026-10-25; the literal App contract above governs this consumer checkout.
The canonical planned history remains intact. The additive metadata foundation
(checked optional SHA-256 and native metadata parser/specification) is now
**in progress, not validated or accepted**. Full DU-01 remains open. Native
service-account and delegated OAuth support already exist and are reused.
DU-02 reservation/file binding and independent SHA-256 verification, DU-03
bounded client streams/UI and DU-04 connected validation/review/live proof remain
pending. No file-ID reservation, TKL verification or live acceptance is supplied
by this metadata foundation.

## Canonical producer plan before App adoption — retained history

The following original planning text remains historical; the accepted App
completion above owns its stated disposition. The November integration has its
own qualification gates and does not reopen that delivery.

```text
status=planned
parent=NICT Editing Studio App Phase 6.3 / ESA63-01/02


## Direct new-file upload and SHA-256 verification — planned delta

Contract and connected acceptance: [direct-upload integrity plan](../../../../Project2026/nict-editing-studio-app/docs/notes/direct-upload-integrity-plan.md).

Producer contribution under App Phase 6.3 / ESA63-01/02 (DU-01/02). Reconcile the existing cncf-esa63-direct worktree at implementation entry. Preserve user-delegated OAuth and add/reuse server service-account token acquisition; retain credentials server-side. Extend native direct-upload preparation/status and metadata with provider-computed SHA-256 and attributable file identity. Test both authorization strategies, new-file/URI/shared-drive constraints and absent checksum semantics. No TKL domain policy or client credential distribution.

- [ ] Implement the owning DU items and affected contract/behavior specifications.
- [ ] Hand off actual evidence and limitations; this planning addition claims no implementation, live upload or Phase closure.
```
