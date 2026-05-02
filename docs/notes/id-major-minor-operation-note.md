# ID major/minor Operation Note

Date: 2026-05-02

## Purpose

This note tracks the open operational issue around `UniversalId` / `EntityId`
generation, including `major` and `minor` values.

The immediate driver is the Blog Web app work in Phase 19. BlogPost creation now
correctly avoids application-owned hard-coded `major` / `minor` values, but the
runtime still commonly supplies `sys/sys` through `ExecutionContext`. That is a
valid fallback, not a complete production operating policy.

This is an open-issue note. Once the policy is decided, the stable rule should
move into `docs/design/id.md` and related execution-context documentation.

## Current Understanding

ID generation is owned by `ExecutionContext`. Application logic must not invent
or hard-code `major`, `minor`, `timestamp`, or `entropy` for new runtime ids.

They are runtime namespace / partition labels used by the identifier system for
scaling, deployment, diagnostics, and future distributed execution. Application
code may preserve them when transforming an existing id, but new ids must come
from the current execution context.

For BlogPost:

- `entity_id` is the canonical persistence id.
- `shortid` is the normal Web/application reference id.
- `slug` is a URL/SEO name.
- `major` / `minor` are neither `shortid` nor `slug`.

`slug = shortid = EntityId.entropy` is not the general rule. `slug` may contain
URL-friendly characters such as hyphen, while `EntityId` labels and entropy must
follow the id grammar.

## Open Issue

The open issue is how CNCF should derive `ExecutionContext.major` and
`ExecutionContext.minor` in each operating mode, and how the execution-context
owned ID generation source is configured.

Known modes:

- development server started from a component dev directory;
- development server started from a subsystem dev directory;
- packaged CAR execution;
- packaged SAR execution;
- command/direct execution;
- test execution;
- future tenant-aware or partitioned runtime execution.

Today `ExecutionContext.CncfCore.major/minor` defaults to `sys/sys`. That is
useful as a framework fallback, but it should not hide missing production
namespace configuration when stable operational ids are required.

ID entropy follows the same policy as randomness:

- production/default execution uses a non-deterministic generator;
- test execution uses a deterministic generator;
- deterministic generation must still produce unique values within one
  execution context;
- fixtures must keep the returned id instead of writing entropy values such as
  `stable` directly.

## Candidate Policy

The likely policy is:

- framework fallback: `sys/sys`;
- dev execution: derive a deterministic namespace from the active component or
  subsystem descriptor unless explicitly configured;
- packaged CAR/SAR execution: derive from package descriptor metadata or runtime
  configuration;
- tests: use an explicit deterministic ID generation source;
- tenant-aware execution: tenant scope is carried separately in
  `ExecutionContext`, not encoded directly into `major` / `minor` by
  application code.

This policy still needs verification against component repository activation,
subsystem assembly descriptors, and generated SimpleEntity creation paths.

## Required Invariants

- Application code must not hard-code `major` / `minor`.
- Application code must not hard-code ID timestamp or entropy.
- New Entity ids must be generated through `ExecutionContext.idGeneration`.
- Generated component code must not bake in fixture major/minor values for new
  runtime records.
- Fixture code must not use `UniversalId.StableEntropy` for new runtime
  entities.
- Entity create paths should be safe no matter whether they enter through
  application logic, internal DSL, UnitOfWork, EntitySpace, or EntityStore.
- Existing ids may retain their original `major` / `minor`.
- Short id and slug resolution must not depend on parsing or guessing
  `major` / `minor`.
- Logical delete and tenant scope filtering must remain outside application id
  string parsing.

## Blog Phase 19 Notes

The Blog Web app should use `shortid` in ordinary Web URLs and forms:

- update: `/web/blog/update?id={shortid}`;
- public view: `/web/blog/publicblogs?post={slug}` or future slug route;
- operation responses: include `entity_id`, `shortid`, and `slug`.

The Blog component can continue to expose `entity_id` for diagnostics and API
clients, but UI links should not expose full `EntityId` by default.

The remaining Phase 19 issue is not Blog-specific. Blog simply exposed that the
runtime namespace policy is still incomplete.

## Review Checklist

When reviewing id-related changes:

- Does new entity creation get `major` / `minor` from `ExecutionContext` or a
  framework generator?
- Does new entity creation get timestamp and entropy from
  `ExecutionContext.idGeneration`?
- Is any application code constructing `EntityId` with literal production
  labels?
- Is any fixture using `stable` as runtime entity entropy?
- Are shortid and slug treated as separate application identifiers?
- Does lookup use EntitySpace / EntityStore / UnitOfWork identity helpers
  instead of parsing ids directly?
- Is `sys/sys` used only as an explicit framework fallback or test fixture?

## Open Questions

- What descriptor/config keys should define the runtime namespace for CAR and
  SAR execution?
- Should dev-dir execution fail when no namespace can be derived, or continue
  with `sys/sys` plus a diagnostic warning?
- Should `ExecutionContext.major/minor` remain string labels or be wrapped in a
  stronger runtime namespace type?
- Where should tenant/organization scope live in `ExecutionContext`, and how
  should it interact with uniqueness / identity checks?
- Which layer should perform final SimpleEntity default completion:
  UnitOfWork, EntitySpace, EntityStore, or all three as defensive boundaries?
- Should JobId, TaskId, ActionId, workflow ids, and association ids move to the
  same execution-context ID generation source in the next slice?
