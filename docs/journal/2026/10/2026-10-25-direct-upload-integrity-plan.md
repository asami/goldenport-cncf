# Direct-upload integrity development plan

Status: planned; no implementation or live acceptance performed.

The user requested a plan for server-authorized new-file direct upload into an
allowed Drive folder, followed by independent content-hash verification. The
selected route uses backend service-account credentials and per-file resumable
URLs, never a general Google token on the phone.

Producer contribution under App Phase 6.3 / ESA63-01/02 (DU-01/02). Reconcile the existing cncf-esa63-direct worktree at implementation entry. Preserve user-delegated OAuth and add/reuse server service-account token acquisition; retain credentials server-side. Extend native direct-upload preparation/status and metadata with provider-computed SHA-256 and attributable file identity. Test both authorization strategies, new-file/URI/shared-drive constraints and absent checksum semantics. No TKL domain policy or client credential distribution.

[Contract, sequence and pending acceptance](../../../../../../Project2026/nict-editing-studio-app/docs/notes/direct-upload-integrity-plan.md).

Existing App Phase 6.3/direct-provider work is reused. Current tests and live
observations do not by themselves prove SHA-256 validation. No active worktree,
queue, credentials, deployment, live allowance, commit or release was changed
by this documentation update.
