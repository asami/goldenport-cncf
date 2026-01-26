# Phase 3.1 — Execution / Orchestration Hub Foundation

status = active

This document defines the **design intent, scope, and boundaries**
of Phase 3.1 in the CNCF roadmap.

Phase 3.1 establishes the **Execution / Orchestration Hub**
as the architectural core of Phase 3.

---

## Purpose

The purpose of Phase 3.1 is to establish CNCF as an
**execution-shape–agnostic orchestration hub**.

CNCF must be able to execute Components regardless of:

- packaging form
- dependency size
- runtime environment
- deployment topology

without leaking those differences into the execution model.

---

## Position in Phase 3

Phase 3.1 is the **foundation phase** of Phase 3.

- All subsequent Phase 3 work depends on this phase
- No AI Agent integration is assumed here
- No model-driven generation is assumed here

Phase 3.1 answers the question:

> Can CNCF execute *realistic components*
> without caring how they are built or packaged?

---

## Baseline Execution Form

Phase 3.1 defines **Fat JAR Component** as the baseline execution form.

This choice is intentional:

- Fat JAR represents worst-case dependency aggregation
- Scala 2 / legacy ecosystems are explicitly included
- ClassLoader isolation becomes unavoidable

If CNCF can handle this case correctly,
other execution forms become strictly easier.

---

## Execution Hub Responsibilities

In Phase 3.1, the Execution Hub is responsible for:

- Loading Components
- Invoking Operations
- Managing execution lifecycle
- Containing failures
- Returning Observations instead of crashing

The Execution Hub is NOT responsible for:

- Building components
- Managing Docker environments
- Performing RPC directly
- AI Agent orchestration

---

## Failure Semantics

Execution failure must be:

- captured as Observation
- returned through normal execution paths
- never allowed to terminate the CNCF runtime

Failure is treated as **data**, not as control flow.

---

## Out of Scope (Explicit)

The following are intentionally excluded from Phase 3.1:

- Docker Component execution
- Antora integration
- CML → Component generation
- AI Agent Hub integration
- Performance optimization

These are addressed in later Phase 3.x work.

---

## Relationship to Checklist

Detailed task tracking and execution status are managed in:

- `phase-3.1-checklist.md`

This document must remain stable once Phase 3.1 is underway.
Execution details, experiments, and iteration notes belong elsewhere.
