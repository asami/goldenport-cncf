# Subsystem Shared Job Engine Direction

## Problem

Builtin `JobControl` cannot manage jobs created by other components while each component owns its own `JobEngine`.

Current shape:

- `Component.Core.create(...)`
  creates `InMemoryJobEngine.create()` per component
- job submission is component-local
- builtin `JobControl` sees only its own component-local jobs

That blocks a real external job-control API.

## Decision

Move job management from component scope to subsystem scope.

The first target shape is:

- one shared `JobEngine` per `Subsystem`
- each component uses the subsystem-shared engine
- builtin `JobControl` uses the same shared engine

## Why

Job control is inherently cross-component once exposed as builtin API.

Needed behaviors:

- one component submits a job
- another component reads status/history
- builtin `JobControl` cancels, suspends, resumes the same job

That requires a shared registry/engine.

## First Implementation Line

The first implementation line is:

1. `Subsystem` owns one `JobEngine`
2. component initialization receives that shared engine
3. `Component.Core.create(...)` no longer invents isolated job engines for normal subsystem runtime
4. builtin `JobControl` can read/control a job created by another component in the same subsystem

## Scope

Keep the first change minimal:

- in-memory only
- one shared engine per subsystem
- no distributed job backend
- no persistence redesign

## Affected Areas

- `Subsystem`
- `SubsystemFactory`
- `Component.Core.create(...)`
- component bootstrap / initialization path
- builtin `JobControl` verification path

## Follow-up

After this is in place:

- finish builtin `JobControl`
- rewrite `05.a-job-control-lab` to use builtin APIs
- move `JobControlDemo` to the advanced lab path
