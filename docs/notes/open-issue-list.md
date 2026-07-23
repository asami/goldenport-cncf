# Open Issue List

Date: 2026-05-02

## Purpose

This note is the lightweight index for active CNCF design and operation issues
that are not ready to become `docs/design` decisions.

Use this list for issues that need discussion, implementation experiments, or
driver validation before they are promoted, deferred, or closed.

## Status Values

- `OPEN`: active question; no accepted decision yet.
- `ACTIVE`: currently being implemented or validated.
- `DEFERRED`: intentionally moved out of the current phase.
- `CLOSED`: resolved; keep only while useful as history.

## Issues

| ID | Status | Topic | Note | Phase / Driver | Next Step |
| --- | --- | --- | --- | --- | --- |
| OI-2026-05-02-001 | CLOSED | `ExecutionContext`-owned ID generation and `major` / `minor` runtime namespace policy | `id-major-minor-operation-note.md` | Phase 19 / Blog Web app | Policy promoted to `docs/design/id.md`: `major` / `minor` are operational partition keys, default namespace is `single/global`, and remaining descriptor-default work is deferred to Runtime Namespace Descriptor Defaults. |
| OI-2026-07-23-001 | OPEN | Protected Entity DSL atomic `claim-or-load` / create-if-absent | `journal/2026/07/entity-internal-dsl-claim-or-load-handoff-2026-07-23.md` | Future Entity DSL slice / CBD Support Phase 8 P8-42 driver | Define typed claim result and immutable-identity predicate; implement datastore-native atomic admission through UnitOfWork; prove concurrent SQLite and shared-datastore profiles preserve authorization, CallTree/audit, and View invalidation without component SQL/JDBC. |

## Intake Rule

Add an issue here when a local note exposes a decision that affects more than
one component, phase, or runtime entry point.

Do not use this file for ordinary implementation TODOs. Those belong in the
phase checklist or in the owning design note.

## Promotion Rule

When an issue is resolved:

- move stable design text into the relevant `docs/design` document;
- update the owning phase checklist if the issue blocked a phase;
- change the status here to `CLOSED` or remove the row after the phase closes.
