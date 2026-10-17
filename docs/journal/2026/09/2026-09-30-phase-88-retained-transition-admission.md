# Retained Workflow transition admission and conflicts

Date: 2026-09-30
Status: bounded executable evidence; not Phase acceptance

Previous turn progressed with focused-361 key-free/index-absence evidence.
Continued Phase 2 / admitted CNCF Phase 88 using existing simplified parent
recovery and shared serial SBT wrapper. Only isolation spec/inspection helper
changed; production sources unchanged.

One Given/When/Then scenario covers six actual managed retained completions:
foreign Workflow owner, foreign root collection, caller-injected event Create,
reserved physical id change, external root revision conflict, external successor
revision conflict. Fresh inspection checks exact cursor/envelope/index fields,
the other open Workflow record, and the externally changed revision 9/status
external where applicable. The injected event is absent.

Foreign owner/collection/effect reject before provider execution (writes 0).
Physical-id change enters the native provider once but commits/reads back 0.
Successor conflict enters once and rolls back with commits/readbacks 0.
Root conflict returns NotMatched with one successful read-only native transaction
commit, zero readback and no Workflow/event mutation. This transaction close is
not a publication; the external root remains exact. No invocation is retried.
All cases preserve cursor 2/2 and retained event/index records.

Attempt 362 compiled but failed the new spec during fixture construction:
only root owner was changed, violating the existing pure same-component rule.
Corrected the fixture by assigning BOTH Workflow roots the same foreign owner,
so the valid pure plan reaches host owner admission and is rejected there.
No production validation was loosened. Final focused-363 passed 241/241 in
26 suites, zero failed/aborted/canceled/ignored/pending, SBT/wrapper 0/0 and
lock=released. Both final summaries/logs read; exact final invocation/cwd/
unchanged 26-suite argv and per-case outcomes checked. No source edits afterward.

Invocation: SMWF-CNCF-RETAINED-TRANSITION-DIRECT-20260930-363.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-f69e9299b4b321e85505f2bcc7630bc518894cc7eaa440c02f80457fdabc7aa9-0bd6fa63d32e4898b03c95962fdbea15/10243-20260930T145536Z.log
Adjacent summary: 10243-20260930T145536Z.summary.json.
Validated ManagedEventObservationIsolationSpec.scala SHA-256:
6a0add1035786360481e845ce2b25ec3fc37ad11330875b24a857df83b6aa6b4.

No commit/publish/push, skill/agent edits, full review, typed acceptance or Phase
closure. These six cases are not universal transition/authority proof.

Next implementation: transient-only/empty completion must write/verify the
Workflow pair through DataStoreSpace and the same managed provider WITHOUT
event cursor observation/allocation/update or persistent event/index work.
Then common host/runtime routing chooses new/retained/transient branches
deterministically from its captured EventStore selection, not skills/AI.
Native cleanup/crash, application/UoW dispatch, production assembly/CLI/MCP/
restart/management remain open. S12/P2-U15/P2-U16/P2-U05 unchecked; goal active.
