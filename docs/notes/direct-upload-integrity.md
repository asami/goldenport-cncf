# Direct-upload content integrity

Status: planned; not accepted.

Producer contribution under App Phase 6.3 / ESA63-01/02 (DU-01/02). Reconcile the existing cncf-esa63-direct worktree at implementation entry. Preserve user-delegated OAuth and add/reuse server service-account token acquisition; retain credentials server-side. Extend native direct-upload preparation/status and metadata with provider-computed SHA-256 and attributable file identity. Test both authorization strategies, new-file/URI/shared-drive constraints and absent checksum semantics. No TKL domain policy or client credential distribution.

Canonical cross-project contract: [plan](../../../../Project2026/nict-editing-studio-app/docs/notes/direct-upload-integrity-plan.md).

SHA-256 here verifies transferred binary content only. Do not use it for update
tracking, management-state identity or unchanged-input certification. Preserve
existing runtime work and completed evidence; implement only the remaining delta.
