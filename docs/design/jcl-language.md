# JCL Language Direction

This document records the Phase 22 JCL baseline and the implemented
JM69-05A/B/C/D executable-language boundary.

JCL is the Job Control Language for CNCF Job management. The current
implementation uses JCL for submission and diagnostics. The long-term direction
is a Job orchestration language that can describe both procedural subtask flow
and Event-driven behavior.

This document is a design contract; the executable runtime contract records
the detailed bridge and immutable-snapshot invariants.

----------------------------------------------------------------------
1. Current Implemented Surface
----------------------------------------------------------------------

Canonical authoring uses a single Job root:

```yaml
job:
  name: publish-blog-post
  target:
    action: blog.publishPost
  profile:
    expectedStatus: succeeded
    eventChain:
      - action: blog.publishPost
        emits:
          - event: BlogPostPublished
            occurrence: required
            receivers:
              - action: blog.updatePublicProjection
                occurrence: required
```

The older `jobs:` root remains for batch compatibility.

Rules:

- `job:` is the canonical single-Job form.
- `jobs:` is compatibility and batch form.
- `job:` and `jobs:` together are invalid.
- `profile` is diagnostics-only.

----------------------------------------------------------------------
2. Diagnostics Profile
----------------------------------------------------------------------

`profile` declares the intended observable behavior of a Job.

It is used for:

- comparing declared behavior with observed execution;
- reconstructing candidate JCL from actual runtime behavior;
- making distributed Event receiver definitions visible as one Job-level
  Event/Action chain.

`profile` must not control or fail Job execution by itself.

The `profile.eventChain` section may declare:

- root or receiver Actions;
- emitted Events;
- receiver Actions;
- guard metadata;
- occurrence expectations: `required`, `possible`, `forbidden`.

Event receiver definitions may remain distributed across Components. JCL
provides a Job-level diagnostic view of those definitions and the actual
runtime chain.

----------------------------------------------------------------------
3. Implemented Executable Flow
----------------------------------------------------------------------

Slices JM69-05A/B/C accept a closed, bounded `flow` grammar on the canonical
single-job root and execute its plan through the existing Job/Task bridge. A
canonical action target becomes exactly one existing Job whose ordered tasks
are the root action followed by one task for each declared flow step.

The section has exactly one key, `steps`. Each step has exactly `id` and
`action`, plus optional string `parameters`:

```yaml
flow:
  steps:
    - id: import
      action: feed.import
      parameters:
        source: nightly
```

Step identifiers are non-empty, unique, and cannot be `root`; at most 32 steps
are accepted. Executable sections are legal only when the canonical `job:`
root has a `target.action`. The legacy `jobs:` batch root and a workflow target
reject executable sections. Unknown keys and empty sections fail before any
submission boundary.

Each selector is resolved and prepared through the existing action authority,
and flow parameters override same-named root parameters. The Job records the
declared step IDs in order while existing JobEngine lifecycle, persistence,
retry, cancellation, and durable-position behavior remains authoritative.

Branching, conditions, loops, parallelism, joins, waits, retries, timeouts,
cancellation, scripting, and unbounded iteration remain outside this grammar.

Example compensation metadata:

```yaml
job:
  name: import-feed
  target:
    action: feed.import
  compensation:
    action: feed.import.compensate
```

This compensation declaration does not make the whole JCL flow executable. It is
used as a Task-level cleanup hook when a later committed Task must be cleaned up.

----------------------------------------------------------------------
4. Implemented Event-Driven Flow
----------------------------------------------------------------------

Slices JM69-05A/B/C accept closed `events` and `onEvent` sections on the same
canonical root. These sections compile into typed emitted-event and same-Job
continuation values. After a named source task succeeds, its declared Event
is published through the existing EventBus with its persistent option, and
matching handlers run synchronously as explicit SameJob child tasks in
declaration order.

`events` has exactly one key, `emit`. Each emission has exactly `id`, `after`,
and `name`, plus optional `kind` and `persistent`:

```yaml
events:
  emit:
    - id: imported
      after: import
      name: FeedImported
      kind: domain
      persistent: true
```

`after` is `root` or an accepted flow-step identifier. Emission identifiers
are unique and event names are non-empty. `onEvent` has exactly one key,
`handlers`; each handler has exactly `id`, `event`, and `action`, plus optional
string `parameters`:

```yaml
onEvent:
  handlers:
    - id: project
      event: FeedImported
      action: projection.refresh
```

Handler identifiers are unique, declaration order is preserved, and each event
must name one emitted event. Emission and handler counts are bounded. Unknown,
empty, unsupported, or cross-field-unresolved forms fail before submission.
Failed source tasks publish no Event and run no continuation. JCL does not add
EventBus registrations or mutate EventReception policy/state.

----------------------------------------------------------------------
5. Relationship to JobDefinition
----------------------------------------------------------------------

Reusable JCL belongs to the `system` JobDefinition Entity, not directly to a
Job instance.

The [lightweight lifecycle contract](../spec/job-definition-lifecycle-contract.md)
owns the current representation: an opaque saved Entity ID, normalized key,
JCL source and format, diagnostics profile, and ordinary lifecycle state.
Accepted model values and the semantic plan are compiled from that source for
submission. Create/update/activate/retire/get/search are the operational API.
EntityStore owns revision for internal conditional saving; there is no domain
version/revision/hash, proposal, approval, rollback, or content-history system.

Submission captures saved ID/key/source/format/profile as one immutable Job
snapshot. A later public update replaces the current definition source under
the same identity; it cannot alter the submitted Job's source, tasks, or
continuation trace. Key equality remains trimmed-string equality. Exact-key
Store-scope search obtains the actual persisted Entity and `loadDetached` uses
its saved ID. No key encoding or derived ID is used, and `a-b` and `a_b` remain
distinct. The historical Phase 69.3 version/hash/key-encoded description is
superseded by the current lifecycle contract.

The native JobEntity persists the submitted snapshot source. The original
signed durable definition envelope carries separate metadata, with no JCL body
or source-authentication claim. A fresh trusted native reader must load both
the JobEntity submitted source and the current JobDefinition, authenticate the
signed record separately, and match their ID/key/format. Neither metadata-only
comparison nor a transferred writer body proves immutable source recovery.

----------------------------------------------------------------------
6. Source Boundary
----------------------------------------------------------------------

All JCL formats are validated before semantic compilation and submission. XML
rejects every `DOCTYPE`, external general or parameter entity, external DTD,
external schema, and XInclude construct; parsing does not read files, URLs,
classpath resources, network endpoints, or entity content. HOCON rejects every
heuristic, file, URL, and classpath `include` form and rejects unresolved
substitutions without calling `resolve`. Common JCL text fields accept only
non-empty source strings, and `events.emit.persistent` accepts only a source
Boolean; numbers, textual booleans, collections, nulls, and arbitrary values
are rejected before submission.

YAML uses a safe constructor with bounded alias, recursive-key, nesting, and
input-size loader options. Tagged YAML construction fails before JCL record
conversion; ordinary untagged mappings and sequences keep their accepted source
shape.

----------------------------------------------------------------------
7. Boundaries
----------------------------------------------------------------------

JCL is scoped to one Job.

JCL is not the distributed Saga language. Saga management may reuse Job/JCL
concepts, but cross-subsystem, cross-machine, long-running coordination belongs
to Saga Entity management.

JCL `profile` remains diagnostics-only. It declares and compares observed
behavior and does not participate in executable compilation control flow.

----------------------------------------------------------------------
8. JM69-10C9 Native Process Realization
----------------------------------------------------------------------

status=selected_validation_complete
manifest=PHASE-69.7-JM69-10C9-implementation01@3
validation_status=REP006-and-ACC001-passed
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted

The [JX1..JX9 companion](../spec/job-cross-process-acceptance-contract.md#jcl-native-process-companion)
and [native composition](job-cross-process-acceptance.md#jcl-native-process-composition)
use real public JobControl Actions in the original configured Subsystem and
engine. The JCL-private prepared-task decorator selects Root for the initial
root, Child for each finite step, and Continuation for each same-Job handler.
Existing delegate values, native context and every execution/canonical/admission
callback are retained, with missing Own/PerTask transaction metadata supplied
locally. The original engine owns actual previous/source parent IDs and its
explicit compensation override; the strict projector/codec stay unchanged.

The actual first Root has no parent/reference. Each finite Child references
the immediately preceding actual task ID as parent and relation reference.
Pending recipe IDs form the same closed declared chain. Started/outcome
boundaries compare only the accumulated real prefix, with all N distinct IDs
required at completion. Independent declared position and command selectors
own expected topology/targets; observed metadata cannot become its own oracle.

The real sequential writer exits before the independently constructed reader.
Only bounded independent selectors and opaque Job IDs cross that boundary.
Stored A -> submitted A -> current B/Retired is proved by the two native typed
Entities plus separately authenticated signed metadata. Real scalar/context/
canonical effects, exact paid facts, privacy, foreign refusal, zero reader
replay/write and owned resource closure remain part of the same seven GWT
scenarios and ordinary seeded property. At initial authoring no validation had run for C9; the current selected results below supersede that status.
The [journal](../journal/2026/10/2026-10-06-phase-69.7-job-jcl-cross-process.md)
retains both prior mismatches and Manifest3. C10 actual event/continuation
processes, security/started-work decision, actual CBD full Review/fresh recovery,
whole-Step independent M2/commit, aggregate/full-Phase validation/review and
local release remain OPEN.

## Current C9 selected validation — 2026-10-06

Initial `authored_validation_pending` / `planned-not-run` remains authoring
history and is superseded by `status=selected_validation_complete` and
`validation_status=REP006-and-ACC001-passed`. Current
`review_status=original-wholeStep-protected-M2-pending` and
`acceptance_status=not-accepted` remain unchanged.

Actual [REP006 representative result](../../.codex-workflow/phases/PHASE-69.7/JM69-10C9-representative06-result.json) completed6 suites/60 tests,
60 succeeded, aborted0/failed0/canceled0/ignored0/pending0/compilerwarnings0.
Actual [ACC001 accumulator result](../../.codex-workflow/phases/PHASE-69.7/JM69-10C9-accumulator01-result.json) completed129 suites/1057 tests,
1057 succeeded, aborted0/failed0/canceled0/ignored0/compilerwarnings0, plus
only the original `SqliteDataStoreSpec` pending1. Both typed readers, SBT and
wrappers exited0; both execution permissions were consumed and actual
`lock=released` was verified by the parent. These are selected-validation
results, with no full-repository, whole-Step or full-Phase acceptance inferred.

JX6 retains four typed pre-execution refusals and complete unchanged native
baselines/effects. The original malformed-YAML refusal retains `ParserException`,
the `expected the node content` diagnostic and exact `Taxonomy(System,Corrupted)`
(System-Corrupted). Draft/Retired exact messages and the unsupported exact-key
message retain `argumentInvalid`. This records existing branch-specific public
consequences; it introduces no parser/taxonomy policy and weakens no negative
assertion. Source A/current B, separate signed metadata, JX1..JX9, privacy,
no-reader-replay, cleanup and the ordinary seeded min24 property remain unchanged.

Stage remains IN_PROGRESS; JM69-09 DONE, JM69-10 OPEN and Phase69.7 IN_PROGRESS.
All original wider acceptance obligations above remain open, including actual
C10 processes, security/positive started-work decision, actual CBD full Review
and independent fresh recovery, compatibility/guidance promotion, protected
whole-JM10 M2/local acceptance commit, predecessor reciprocal deferred-full,
aggregate full CNCF/downstream validation, sole epoch1 full Phase review and
final local release. Original history, allowances, counters and HYG records
remain; selected validation neither resets them nor records review acceptance.

----------------------------------------------------------------------
9. JM69-10C10 Event and Continuation Process Consumers
----------------------------------------------------------------------

status=authored_validation_pending
manifest=PHASE-69.7-JM69-10C10-implementation01@1
validation_status=planned-not-run
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted

The [EC1..EC9 companion](../spec/job-cross-process-acceptance-contract.md#jcl-event-and-continuation-native-process-companion)
and [native process composition](job-cross-process-acceptance.md#jcl-event-and-continuation-native-process-composition)
consume the implemented Event-driven flow without production changes. Public
inline JobControl YAML and actual CommandActions in the native Subsystem drive
the same EventBus and SameJob continuation bridge. The fixture installs one
existing observer before submission; JCL adds no subscription.

Each handler remains an Action Continuation parent/reference-bound to its
actual emitting source, with inherited root values and its own overrides.
Root and finite Child retain their native topology. The source remains
Running while nested handlers complete and returns its own scalar after the
chain. Genuine progressive native checkpoints and independent selectors prove
this topology; per-continuation work tickets prove the dynamic payment formula
in the companion. Original strict native projection, transaction and lifecycle
responsibilities remain unchanged.

Source failure emits nothing. Observer failure occurs after append and retains
EventBus's existing typed previous-cause conclusion. First-handler failure
prevents later handlers. Real UoW observations distinguish failed commands,
successful delegate commits and source wrapper failure. Ephemeral failure
cases introduce no persisted failed recovery, retry or compensation policy.

Persistent=true is the existing in-memory EventStore append option. It does
not make this Subsystem EventStore disk-backed: the independent fresh reader
has selected eventcount0 even after a Stored writer event. Readers restore
signed Job descriptors/payments and positive native inline JobEntity debug,
with no saved-definition source invented, zero replay/write and the original
scalar facade/foreign refusal. Process identity, bounded receipt, owned
cleanup and ordinary seeded min24 generation remain executable oracles.

The [C10 journal](../journal/2026/10/2026-10-06-phase-69.7-job-jcl-event-cross-process.md)
records authoring and pending8REP/130ACC. Previous C9 results/failures remain
historical evidence. Stage C10 IN_PROGRESS; JM69-09 DONE, JM69-10 OPEN and
Phase69.7 IN_PROGRESS. Whole-Step protected M2, actual CBD full Review/fresh
recovery, security/positive started-work decision, compatibility/promotion,
predecessor deferred-full, aggregate/full-Phase validation/review and release
remain OPEN. Original [three stable HYG findings](../journal/2026/10/2026-10-01-phase-69.7-hygiene-follow-up.md)
retain their separate ownership; authoring records no Step/Phase acceptance.
