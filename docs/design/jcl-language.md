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

JobDefinition should retain:

- JCL source;
- normalized diagnostics profile;
- typed accepted flow, emitted-event, and same-Job continuation model values;
- an explicit semantic compilation plan for later runtime consumption;
- version / revision / hash;
- lifecycle state;
- target binding metadata.

When a Job instance starts from a JobDefinition, the existing submission
surface captures the definition id, version, revision, hash, source, format,
and declared profile as one immutable snapshot. A later management update
advances the current definition independently; it cannot alter the already
submitted Job's source, tasks, or continuation trace.

`JobDefinitionEntity` alone uses the Phase 69.3 compatibility bridge for
definition identity. Key equality is trimmed-string equality. New direct IDs
encode the normalized key losslessly with the versioned label
`v1_<utf16-length-in-hex>_<each-UTF-16-unit-in-four-hex-digits>`, so `a-b` and
`a_b` are distinct. Reads load a new direct ID first. On a miss, legacy records
are found by an exact persisted normalized-key search because a complete historic
three-argument `EntityId` includes non-reconstructible timestamp and entropy.
Cache, new-ID, and legacy candidates must all carry the exact requested normalized
key; a mismatch is not an alias. Create rejects only an exact existing key,
including a matching legacy record, and permits the collision pair without
migration.

This is not an EntityId/UniversalId, ID-generation, or store-index redesign.
That general persisted-identity decision remains deferred beyond Phase 69.3.

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
