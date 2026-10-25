# CNCF producer plan — App Phase 6.3 direct-upload integrity

status=planned
parent=NICT Editing Studio App Phase 6.3 / ESA63-01/02


## Direct new-file upload and SHA-256 verification — planned delta

Contract and connected acceptance: [direct-upload integrity plan](../../../../Project2026/nict-editing-studio-app/docs/notes/direct-upload-integrity-plan.md).

Producer contribution under App Phase 6.3 / ESA63-01/02 (DU-01/02). Reconcile the existing cncf-esa63-direct worktree at implementation entry. Preserve user-delegated OAuth and add/reuse server service-account token acquisition; retain credentials server-side. Extend native direct-upload preparation/status and metadata with provider-computed SHA-256 and attributable file identity. Test both authorization strategies, new-file/URI/shared-drive constraints and absent checksum semantics. No TKL domain policy or client credential distribution.

- [ ] Implement the owning DU items and affected contract/behavior specifications.
- [ ] Hand off actual evidence and limitations; this planning addition claims no implementation, live upload or Phase closure.
