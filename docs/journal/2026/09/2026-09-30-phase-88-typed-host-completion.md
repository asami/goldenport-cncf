# Typed Workflow completion through the managed event host

Status: bounded executable evidence; not Phase acceptance

Previous goal turn made authoritative progress with deterministic host routing
and focused-372. This turn connects the existing typed Workflow completion
record adapter to that route, under admitted CNCF Phase 88 and existing
simplified parent-direct recovery. No skill/agent changes, Cozy edits, commits,
publish, push or destructive cleanup.

WorkflowPreparedDataStoreTransition now has private[cncf] commitHostEventsC.
It admits only an effect-free prepared pair and ordinary events, requires both
resolved Workflow collection providers to be the identical prepared provider,
then delegates route selection entirely to conditionalHostEventsVerified.
Only verified actual pair records feed the existing trusted projection. Failed
or throwing postcommit projection is transaction-indeterminate; no retry or
compensation. NotMatched retains the existing typed stale-guard rejection.
Compatibility commitC and explicit local-effect composition remain unchanged.

WorkflowCompletionDataStorePersistence adds private[cncf] completeHostEventsC:
existing exact result codec, issued WorkOrder, claim token, configuration,
adjacent history and immutable issued bytes are admitted before host commit.
The new method is internal storage integration, NOT a UoW completion/delivery
permit, public Operation or activated production runtime.

Four new Given/When/Then scenarios cover eleven real managed configurations:

- Empty, transient-only, retained/transient, new durable and mixed batches
  complete actual typed claimed WorkOrders. Exact typed result, adjacent history
  and Completed claim are read back; fresh admitted scopes recover completion
  and only selected durable facts. Cursor advances only for new durable input;
  original input sequences stay unissued. Transient unsupported payload is not
  encoded/persisted. Canonical schema is provisioned before native absence reads.
- Another provider's prepared completion rejects, preserving both pending
  claims and current managed records/cursor. No provider substitution is allowed.
- Re-claim, changed actual root guard and composed local effects reject without
  publishing completion, new event or cursor advance; all current records stay
  exact. The common event entry does not silently absorb closing-program data.
- A trusted projection returning failure or throwing after native pair commit
  runs once and returns indeterminate while exact completion remains stored.
  No dispatch, retry or compensating rollback is manufactured.

Focused-373 passed 269/269 in 27 suites. Added stale guard/effect tests failed
374 because the fixture changed a literal revision column instead of the
actual binding's storage guard column. Corrected the fixture to use
original.plan.root.revisionField without changing production guards. Final
focused-375 passed 270/270 in 27 suites, zero failed/aborted/canceled/ignored/
pending, SBT/wrapper exits 0/0 and lock=released. Summaries and relevant logs
read; no source/spec edits after final validation. No full Phase review or
acceptance/closure is claimed.

Invocation: SMWF-CNCF-TYPED-HOST-COMPLETION-DIRECT-20261001-375.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-79832ede37463d5505bf9439058cb88c8b06f2cb7513af43fa4273f8422cf896-13fe2006c2769877ce629c5c2733e068/31108-20260930T154335Z.log
Adjacent summary: 31108-20260930T154335Z.summary.json.
Validated WorkflowPreparedDataStoreTransition.scala SHA-256:
8f757c4a3243609eeb4ecb85a529ba818da0639e4b9cb14d6e2716f1cf032be7.
Validated WorkflowCompletionDataStorePersistence.scala SHA-256:
47ebea7370a6ba99292b2a4a21b2974391fc75e83c1552c225139bade70ba82d.
Validated WorkflowCompletionDataStorePersistenceSpec.scala SHA-256:
8e449c2341eda70ef52d31d3f7bad76b12494d1451737d3f61d1ab91aa3711d0.

## Remaining runtime gap and next integration boundary

Current WorkflowCompletionServiceOperation.afterCommitResponseC still resumes a
fresh UoW and separately appends instance history. UnitOfWork.commit still stages
its event engine, calls transaction/event commits, then postcommit callbacks;
TransactionContext.commit is presently empty. These paths do not enlist the new
managed pair/event host in one shared transaction and are unchanged this turn.
Calling completeHostEventsC from that postcommit callback would NOT solve P2-U05
and must not be presented as a production integration.

Next work must stage a trusted completion intent in the active fresh UoW, fix
its event/data/closing-program effects, and run one admitted host/provider commit
before completing the UoW and granting original-order event delivery. No separate
raw SQLite path, skill dispatcher, caller persistence flags, or commit-then-UoW
substitute. Guard rejection/rollback/uncertainty must suppress all actual dispatch;
transient events remain memory-only and cannot become restart/replay data.
Concrete executable acceptance is required before runtime/Operation activation.

S12/P2-U15/P2-U16/P2-U05 stay unchecked. Production assembly, normal launcher
CLI, MCP equivalence, separate-process restart, management and skill packaging
remain open. Goal and Phase remain incomplete.
