# Retained authority specification pending Cozy recovery

Date: 2026-09-30
Status: source added; NOT compiled or executed

Historical status above: resolved after user-reported Cozy repair and resume.
Cozy compile/export 358 passed; unchanged pending spec compiled and all five
authority configurations passed in focused-359 (239/239, 26 suites). See the
retained-authority-resumed journal in this directory for final evidence. This
does not close the Phase or remaining production requirements.

Previous goal turn progressed with focused-355 (238/238). Continued active
Phase 2 / admitted CNCF Phase 88 under existing simplified parent recovery.

Added one Given/When/Then spec with five authority misuse configurations to
ManagedEventObservationIsolationSpec: foreign host with identical actual provider,
service, policy, thread AND lease; wrong thread with joined terminal thread;
actual revoked lease; explicitly released lease; escaped preparation attempted
both on the released original host and a fresh admitted host. Expectations:
zero provider writes/commits/readbacks and fresh full-field inspection showing
open Workflow pair and unchanged cursor/retained facts. These are expectations,
NOT verified outcomes. Production sources unchanged.

Invocation 356 stopped before CNCF test compilation because the configured Cozy
development classpath was stale. No suites/tests ran. SBT/wrapper 1/1 and
lock=released; summary/log read. Attempted the same ordinary derived-output
recovery used in 343: compile then cozyExportRuntimeClasspath in Cozy, serialized
through the shared wrapper. Invocation 357 failed Cozy compile with 13 errors;
classpath export was not reached. Both processes terminal, locks released.
No concurrent SBT, isolated cache, generator bypass, source edit or publish.

Read-only inspection confirms SourceDeclaration(id) and AssetDeclaration(id,
mediaType) no longer expose sha256, but CrossMediaProjection/Receipt still read
value.sha256. VisualPage Asset references likewise fail in MediaPresentation,
SummarySlidesPdf and VideoStoryboardReview. Cozy has extensive unrelated dirty
source/doc changes. Their semantic repair is outside the admitted Phase88
mutation scope: preserved all changes, no compatibility-field guess or bypass.

356 log: /tmp/skill.cncf.d/cncf-sbt-dd059b42aa97264c9293d64ac0e42e2c870c6c1536e4765bbe0a823729914685-d3f0d303530bf0d74f6acc8ebac67ce1/61548-20260930T122739Z.log
357 log: /tmp/skill.cncf.d/cncf-sbt-ef91c276535fef64c267a0774e9308c17ef7cfcdc50b4211dbc5c0cb50c3070f-ffff7d7a4643f1c8d2d876b715e6d2f0/61973-20260930T122826Z.log
Adjacent .summary.json files read; 357 argv: --batch compile cozyExportRuntimeClasspath.
Current UNVALIDATED isolation spec SHA-256:
7834c2f2235dd75d0531e1ad91dcf2da0ebced7406b561df4b1d8dda3ff71fea.
Focused-355's 238/238 describes the previous source hash, not this added spec.

Next required: Cozy owner resolves the sha256 contract/caller mismatch and
successfully compiles/exports the project-owned runtime classpath, or user
explicitly expands this task's mutation scope to include that repair. Then run
the unchanged 26 focused suites on current CNCF sources with a fresh invocation.
Do not claim authority closure or accumulate unverified production changes.
Retained absence/key-free cases, transient-only completion, deterministic common
routing, native cleanup/crash, application/UoW and CLI/MCP/restart/management
remain open. S12/P2-U15/P2-U16/P2-U05 unchecked. No commit/acceptance/goal closure.
First turn observing this Cozy source blocker; Phase goal remains active.
