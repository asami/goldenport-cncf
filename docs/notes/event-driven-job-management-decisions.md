# Event-Driven Job Management — Design Decisions

This note records **why** the Event-Driven Job Management framework
was intentionally frozen at Phase 1–2, and which alternatives were
explicitly deferred.

This document is not a specification.
The canonical design is defined in:
docs/design/event-driven-job-management.md

---

## Why Event-Driven Job Management Exists in CNCF

Most web applications lack a coherent job or long-transaction model.
Asynchronous processing is often implemented as:

- fire-and-forget background tasks
- ad-hoc database tables
- cron-based retries
- opaque worker queues

This makes it difficult to answer basic operational questions:

- What is the current state of a job?
- Which steps have completed?
- Why did it fail?
- Can it be replayed or explained?

The CNCF job framework exists to make execution **explainable,
replayable, and observable by design**, without requiring distributed
infrastructure.

---

## Why Phase 1–2 Is “Enough” (For Now)

Phase 1–2 intentionally assumes:

- single-node execution
- in-process JobEngine
- append-only JobEventJournal
- pure JobState projection

This already provides:

- deterministic job state derivation
- idempotent event handling
- clear separation between execution facts and observability
- operationally debuggable jobs

For the majority of enterprise and web applications, this is a
significant improvement over existing approaches.

Introducing clustering or actor-based distribution at this stage
would dramatically increase complexity without proportional benefit.

---

## Why Actor / Cluster Support Is Deferred

Actor-based job engines solve a **different problem**:

- high availability
- distributed ownership
- cross-node coordination

These concerns become relevant only when:

- job volume or SLA demands HA
- the JobEngine itself becomes a SPOF in practice
- operational maturity justifies cluster complexity

Until then, actorization would impose:

- higher conceptual load
- harder debugging
- more failure modes
- heavier operational requirements

The current design already satisfies the **preconditions** for
future actorization (event sourcing, idempotency, replay),
so deferring this choice does not block evolution.

---

## Why Events Are Intentionally Stratified

The framework distinguishes conceptually between:

- **Framework/Internal Events**
  - execution facts
  - lifecycle transitions
- **Domain Events**
  - business-level facts
  - potentially shared via service bus
- **Coordination Events**
  - future concern for distributed orchestration

Not all events are meant to be published externally.
Not all external events should affect job state.

This separation avoids accidental coupling between
internal execution mechanics and domain-level integration.

---

## What This Freeze Means

By freezing at Phase 1–2:

- the job framework becomes a stable foundation
- further development can proceed without re-litigating fundamentals
- future extensions are explicit and intentional

This is not a limitation.
It is a conscious decision to favor **clarity and operability**
over speculative generality.

---

## Future Revisit Criteria

Actorization or distributed job coordination should be revisited only if:

- JobEngine availability becomes a real operational bottleneck
- Multiple nodes must actively advance the same job
- Organizational maturity supports distributed debugging

Until then, the current design is considered complete.
