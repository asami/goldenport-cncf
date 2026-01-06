# Subsystem Execution Modes (Notes)

status = draft  
scope = subsystem / cli / execution-model  
audience = architecture / platform / operations  

---

## 1. Background

In CNCF, Subsystems are primarily designed to run as long-lived services
and expose their functionality via REST / OpenAPI.

However, during actual system operation, we frequently encounter use cases
where we want to reuse the *same Subsystem logic* in non-server contexts,
such as:

- operational commands
- diagnostics and inspection
- data import / export
- repair and recovery tasks

Implementing these as separate CLI-only logic would lead to
duplication, divergence, and loss of consistency.

This note explores how Subsystems can be executed
in multiple modes while sharing the same core logic.

---

## 2. Problem Statement

If Subsystems are treated as “server-only” artifacts:

- operational commands tend to reimplement domain logic
- CLI tools drift away from REST semantics
- local, offline, or batch-style operations become awkward
- testing and automation become more complex

At the same time, not all use cases should go through REST:

- some tasks are better executed locally
- some tasks should not expose public APIs
- some tasks are naturally batch-oriented

---

## 3. Core Idea: Execution Mode

A **Subsystem** is an executable unit whose *logic* is independent
of how it is invoked.

The invocation style is treated as an **execution mode**.

Typical execution modes:

- **Server mode**
  - long-running process
  - exposes REST / OpenAPI
  - used by applications and external systems

- **Command mode**
  - in-process execution
  - invoked via CLI
  - used for operational and administrative tasks

- **Batch mode**
  - non-interactive execution
  - typically processes large datasets
  - used for import / export, migration, reprocessing

The same Subsystem definition should be usable in all of these modes.

## 3.1 Relationship to HelloWorld Bootstrap

The **HelloWorld Bootstrap** establishes the minimal execution path
for CNCF Subsystems by focusing exclusively on **Server mode** execution.

It deliberately avoids introducing Command or Batch execution modes
in order to keep the initial bootstrap simple and verifiable.

This document builds on that foundation by outlining how the same
Subsystem definition can later be reused in additional execution modes
without changing its core logic.

See:
- [HelloWorld Bootstrap](helloworld-bootstrap.md)

---

## 4. Separation of Concerns

The key separation is between:
