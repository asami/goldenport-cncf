# ID work inventory and November 10 demo boundary

Snapshot on 2026-11-06. [Phase 120](../../../phase/phase-120.md) is planned only.

| Workstream | Evidence and demo disposition |
| --- | --- |
| CNCF main | ID design, delivery-index spec, managed-container spec and inventory note are documentation changes. The earlier event-index Scala/test edits were withdrawn. No ID rewrite, event-index cutover or seed migration belongs in the demo build. |
| Shared model and core | simplemodeling-model main and Studio's phase5-opaque-owner/model both point to 51b3e9d with clean working trees. goldenport-core (simplemodeling-lib) main and phase5-opaque-owner/core both point to 2ccdd2c with clean working trees. Keep both as-is for this issue; no version bump, generator change or rebuilt shared SNAPSHOT for the workaround. |
| Studio / TKL | The active Studio Phase 5 checkout at 53dcc9a has staged participant integration, dependency declarations, fixtures and HTTP specs. Its CNCF provider-origin/direct-upload and TKL participants already carry separate commits (377f882e, ca073867, 13a3347). Keep these delivery changes separate from the ID inventory. |
| User Account | The Phase 5 producer at ae7b644 has a catalog change. Preserve its authentication integration; defer changing principal issuance, public handle or stored account identity. The main checkout's federation design is separate. |
| Mobile App | The current ESA6.10 work includes authenticated submission, account switching and local material preservation. Its journal records five new cases, an overlapping 86-case affected run and Android debug APK compilation. Two-phone, public HTTPS and end-to-end qualification remain open. Preserve ongoing work. |

## Confirmed demo blocker

Studio's retained run `repair-validation-002-01-stdout.log` in
`ess5-studio-tkl-integration-20261106` reports 177/179 passing. Both failures
are participant B's own findForRequest, for direct Drive and mediated multipart.
Two rows exist. B's document owner matches B; its SQL owner matches neither
participant. A's row matches A at both layers. B receives HTTP 200 with
found=false. This establishes a storage/lookup mismatch, not a need to edit
the shared model. A succeeding case does not qualify arbitrary accounts.

## Temporary demo disposition

1. First choice for the controlled demo: pre-provision two ordinary demo
   accounts and rehearse the real supported paths. Admit only accounts whose
   issued principal survives the existing write/reload/owner comparison exactly,
   with distinct full account IDs and principals. Check both can retrieve their
   own records and neither can retrieve the other's, including after re-login.
   Preserve the failing arbitrary-ID cases for Phase 120. Never change an issued
   ID, accept client-supplied ownership, elevate a participant, or remove the
   owner visibility filter to make the demo pass.
2. If the two-account rehearsal cannot pass, isolate any temporary correction
   to the Studio/TKL delivery boundary that actually writes or resolves the
   record. Require exact, unique server-authenticated account attribution on
   write and read, atomic/repeatable lookup, both transfer paths and cross-user
   denial. Keep original-data checksum checks. If that boundary cannot enforce
   the contract, leave the affected demonstration path out of the rehearsal.
3. Record which accounts/paths were qualified without secrets, the residual
   unsupported case, and the removal condition. The workaround is a demo
   constraint, not general product acceptance. No workaround was applied or
   qualified in this planning session.
