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
