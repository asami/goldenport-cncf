# Capability-Oriented Execution Architecture

Status: normative design
Date: 2026-10-15

## Purpose

CNCF treats access to the external world as managed execution rather than ambient JVM authority. Component/CAR code expresses effects through the CNCF internal DSL and runtime capabilities; CNCF owns interpretation, authorization, lifecycle, observability and concrete providers.

This architecture is an execution architecture. It is not identical to the Safety Sandbox Architecture. Safety Sandbox uses this execution model as one of its enforcement mechanisms.

## Core model

```text
CAR / Component code
  -> protected Internal DSL / Algebra
  -> UnitOfWork and Runtime Boundary
  -> managed Capability
  -> provider/backend
  -> External Effect
```

An External Effect is an interaction outside ordinary component-local computation, including persistent data, filesystem/resource trees, network/provider calls, process execution, clocks/environment authority, events/queues, blobs, devices or native facilities.

## Principles

1. Ordinary component code does not acquire ambient external authority merely because it runs in the JVM.
2. CNCF logical resources and capabilities are preferred over host paths, raw clients, executable paths and other physical handles.
3. UnitOfWork is the canonical boundary for framework-owned application effects where a UnitOfWork algebra exists.
4. Runtime capabilities may also implement managed effects that are not represented as UnitOfWorkOp; these remain part of the same managed execution architecture.
5. Capability declarations/definitions describe available authority. Caller authorization (Capability/Guard) and CAR execution authority are related but distinct.
6. Runtime implementations may change (local, remote, container, provider) without changing component business semantics.
7. Managed effects provide natural chokepoints for observability, deterministic testing, limits and policy.

## Existing CNCF mechanisms

The architecture includes and generalizes existing mechanisms such as:

- UnitOfWork / UnitOfWorkOp and its interpreter;
- EntitySpace / EntityStoreSpace instead of ordinary raw DataStore access;
- ResourceTreeAccess and ResourceTreeQuery instead of ambient host filesystem traversal;
- admitted ProcessExec/program definitions instead of ProcessBuilder or shell text;
- runtime configuration and bound clock facilities;
- managed provider/network facilities;
- component/CAR ClassLoader and ABI boundaries.

## Authorization relationship

Caller authorization answers: **may this subject perform this application action?**

Execution capability answers: **may this component request/use this managed external facility?**

Both may be required for one Operation. Granting a caller application authority does not grant the CAR arbitrary filesystem/network/process authority, and granting a CAR runtime capability does not authorize a caller.

## Safety Sandbox relationship

The Safety Sandbox Architecture adds three enforcement stages around this model:

```text
CAR Lint -> CAR Loading Admission -> Capability-Oriented Runtime Enforcement
```

The runtime portion of Safety Sandbox therefore reuses this architecture rather than introducing parallel filesystem/network/process abstractions.

## Hard isolation

Capability-oriented execution remains useful for Docker/process-isolated components. Container isolation changes the security boundary; it does not require a second capability vocabulary.

## Non-goals

- recreating Java SecurityManager;
- implementing SELinux-style general mandatory access control inside CNCF;
- exposing raw OS/JVM handles merely to make adapters convenient;
- building duplicate sandbox-specific error, authorization or observability stacks.
