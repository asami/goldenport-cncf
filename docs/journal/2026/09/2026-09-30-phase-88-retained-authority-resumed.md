# Retained authority validation resumed after Cozy repair

Date: 2026-09-30
Status: bounded focused evidence; Phase remains active

User reported Cozy repaired and explicitly resumed the task. Current read-only
inspection confirms the four nonexistent value.sha256 references are removed
while SourceDeclaration/AssetDeclaration keep their current declaration contract.
No Cozy source repair was performed by this task.

Invocation 358 ran ordinary Cozy compile and cozyExportRuntimeClasspath,
SBT/wrapper 0/0, lock=released. Read final log and summary before CNCF validation.
No generator bypass, isolated cache, publish or concurrent top-level SBT.

Invocation 359 then compiled and ran the pending authority spec on the unchanged
CNCF source hash. All five misuse configurations passed: foreign host with the
same actual provider/service/policies/thread/lease, wrong thread (joined and
terminal), actual revoked lease, explicitly released lease, and escaped
preparation on both its released original view and a newly admitted view.
All reject before provider write/commit/readback. Fresh admitted-scope inspection
finds both Workflow records open, exact unchanged cursor/retained envelope/index
fields and absent alias/fresh event/index entries.

Focused-359: 239/239, 26 suites, zero failed/aborted/canceled/ignored/pending,
SBT/wrapper 0/0, lock=released. Exact final log/summary invocation, cwd, unchanged
26-suite argv and per-configuration outcomes read. No source edits after this
validation. This resolves the former pending authority-spec status, not every
authority scenario or Phase acceptance.

358 log: /tmp/skill.cncf.d/cncf-sbt-127b15347e1b462d2cbce6179920b35c1a48e5d9d95b2e7785e5f38cb906903e-710bcd370a1074a6631a5ab69cb2fd7f/4074-20260930T144316Z.log
359 invocation: SMWF-CNCF-RETAINED-AUTHORITY-RESUME-DIRECT-20260930-359.
359 cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
359 log: /tmp/skill.cncf.d/cncf-sbt-a52683ac5b6cecee783ba07f8904a0fb94177b76b240b8966bf2a3139ef886c1-ea0710fa649b31e718de12aa8f513272/4723-20260930T144352Z.log
Adjacent .summary.json files read.
Validated ManagedEventObservationIsolationSpec.scala SHA-256:
7834c2f2235dd75d0531e1ad91dcf2da0ebced7406b561df4b1d8dda3ff71fea.

The previous blocked condition is resolved; user resume made the goal active.
Existing simplified parent recovery and shared serial wrapper remain in force.
No commit/publish/push, skill/agent edits, full review or typed Phase acceptance.

Next: retained absence/key-free and invalid Workflow transition coverage, then
transient-only/empty completion without cursor work and deterministic common
host/runtime routing. Public skills/AI must not choose storage branches.
Native cleanup/crash uncertainty, application/UoW dispatch and production
assembly/CLI/MCP/restart/management remain open. S12/P2-U15/P2-U16/P2-U05 unchecked.
