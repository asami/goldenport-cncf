# Transient-only and empty native completion

Status: initial managed implementation; bounded executable evidence

Continued the active sm-workflow Phase 2 / admitted CNCF Phase 88 following
focused-363 retained transition admission. Existing simplified parent execution
recovery remains in force; shared SBT serialization and normal caches retained.

Added EntityTransientEventDataStore opt-in and opaque original-host-bound
TransientEventInvocation. DataStoreSpace accepts ordinary records only, selects
through its captured policy-bearing EventStore, rejects any durable selection,
validates the authorized effect-free Workflow pair and exact provider domain,
then invokes the separate native capability. No caller selection/policy or
generic capability fallback; no cursor or event/index lookup admission needed.

Managed SQLite performs one transaction for the pair: original host/thread/
provider/lease checks, physical id reservation, root matching, all four
checkpoints with authority rechecks, successor preparation and root update,
exact two-record transaction-captured postimages, commit, one coherent native
pair readback and final live-host recheck. Uncertain commit/readback suppresses
Verified without retry. Shared effect-free pair validation reuses the retained
policy validator; its event observation/preparation route is not invoked.
No direct Workflow SQLite access or event cursor allocation/update.

Five actual managed configurations: empty, transient with unsupported payload
and sender persistent=true, corrupt cursor, durable input and mixed input.
Empty/transient/corrupt-cursor succeed with exactly the Workflow pair verified;
explicit stale resubmission is NotMatched. No event cursor initialization is
required; malformed cursor fields are preserved without decoding. Sender flag
and unissued sequence remain unchanged in the source. Durable/mixed reject with
open pair and unchanged storage. Input event entries remain absent.

This implements storage-only completion. No transient event delivery token,
dispatch, application/UoW completion, recovery/replay or common branch router is
provided. The internal ordinary-input entry is not a public skill-side routing
instruction. Runtime must eventually select all branches deterministically.

Attempt 364 stopped at production compilation due to missing explicit import
of the new provider trait in SqlDataStore. Corrected that import. Final
focused-365 passed 242/242 in 26 suites, zero failed/aborted/canceled/ignored/
pending, SBT/wrapper 0/0, lock=released. Both final summaries/logs read; final
invocation/cwd/unchanged 26-suite argv and per-case outcomes checked. No source
edits after final validation. No commit/publish/push, skill/agent edits, full
Phase review, typed acceptance or goal closure.

Next: transient-specific rollback/revocation/competition/readback/authority
coverage, including explicit no-event/cursor access evidence and unconfigured
service rejection. Then deterministic common host/runtime routing. Other
routes' evidence cannot substitute. Native cleanup/crash, application/UoW
dispatch, production assembly/CLI/MCP/restart/management remain open.
S12/P2-U15/P2-U16/P2-U05 unchecked; Phase goal remains active.

> Local execution archive: `~/backup/journal/2026/11/cloud-native-component-framework/cncf-journal-cleanup-20261116/a-46519ec7-ea0d-401b-ac84-b5c5c87060f6/docs/journal/2026/09/2026-09-30-phase-88-transient-empty-completion.md.gz`
