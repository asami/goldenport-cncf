# JCL Language Direction

This document defines the Phase 22 direction for JCL.

JCL is the Job Control Language for CNCF Job management. The current
implementation uses JCL for submission and diagnostics. The long-term direction
is a Job orchestration language that can describe both procedural subtask flow
and Event-driven behavior.

This document is a design contract, not a complete runtime specification.

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
3. Future Executable Flow
----------------------------------------------------------------------

The future `flow` section is reserved for procedural Job orchestration.

It may eventually express:

- sequential steps;
- subtask launch;
- branching;
- parallel execution;
- wait/join;
- retry policy;
- compensation hooks.

Example placeholder:

```yaml
job:
  name: import-feed
  target:
    action: feed.import
  flow:
    steps: []
```

JM-03B does not implement executable `flow`. JM-04 adds only the limited
explicit compensation metadata needed to bind a Task to a compensation Action;
general procedural `flow` execution remains deferred.

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
4. Future Event-Driven Flow
----------------------------------------------------------------------

The future `events` / `onEvent` section is reserved for executable
Event-driven behavior.

It may eventually express:

- conditional Event emission;
- Event reception;
- guard / condition selection;
- receiver Action or subtask launch;
- failure policy;
- continuation policy.

Example placeholder:

```yaml
job:
  name: publish-blog-post
  target:
    action: blog.publishPost
  events:
    onEvent: []
```

JM-03B does not implement executable `events` or `onEvent`.

----------------------------------------------------------------------
5. Relationship to JobDefinition
----------------------------------------------------------------------

Reusable JCL belongs to the `system` JobDefinition Entity, not directly to a
Job instance.

JobDefinition should retain:

- JCL source;
- normalized diagnostics profile;
- future executable flow placeholder;
- future executable events / onEvent placeholder;
- version / revision / hash;
- lifecycle state;
- target binding metadata.

When a Job instance starts from a JobDefinition, the Job instance should retain
the definition id, version, hash, and declared profile snapshot. This keeps
runtime audit stable even if the JobDefinition is later edited.

----------------------------------------------------------------------
6. Boundaries
----------------------------------------------------------------------

JCL is scoped to one Job.

JCL is not the distributed Saga language. Saga management may reuse Job/JCL
concepts, but cross-subsystem, cross-machine, long-running coordination belongs
to Saga Entity management.

JCL `profile` remains safe to use before executable orchestration exists because
it only declares and compares observed behavior.
