# ID major/minor Operation Note

Date: 2026-05-02

## Purpose

This note records the Phase 19 operational policy decision around
`UniversalId` / `EntityId` generation, including `major` and `minor` values.

The immediate driver is the Blog Web app work in Phase 19. BlogPost creation now
correctly avoids application-owned hard-coded `major` / `minor` values. The
runtime previously supplied `sys/sys` through `ExecutionContext`; that old value
is legacy implementation debt, not the target production policy.

The stable rule is now promoted to `docs/design/id.md`. This note keeps the
Phase 19 reasoning and migration guidance.

## Current Understanding

ID generation is owned by `ExecutionContext`. Application logic must not invent
or hard-code `major`, `minor`, `timestamp`, or `entropy` for new runtime ids.

They are operational runtime namespace / partition labels used by the
identifier system for scaling, deployment, diagnostics, and future distributed
execution. Application code may preserve them when transforming an existing id,
but new ids must come from the current execution context.

The accepted default namespace is:

```text
major = single
minor = global
```

This is the neutral single operating partition. It is valid in production when
the deployment intentionally has one customer/tenant/operating scope and one
global region. Multi-customer, tenant-aware, district, region, or site-separated
deployments must override it.

The primary runtime configuration keys are:

```hocon
textus.id.namespace.major = single
textus.id.namespace.minor = global
```

Resolution precedence is:

1. runtime configuration
2. SAR descriptor default
3. CAR deemed-subsystem default
4. CNCF default `single/global`

`sys/sys` should not be used for new design. Existing uses are migration debt.

For BlogPost:

- `entity_id` is the canonical persistence id.
- `shortid` is the normal Web/application reference id.
- `slug` is a URL/SEO name.
- `major` / `minor` are neither `shortid` nor `slug`.

`slug = shortid = EntityId.entropy` is not the general rule. `slug` may contain
URL-friendly characters such as hyphen, while `EntityId` labels and entropy must
follow the id grammar.

## Decided Policy

`ExecutionContext.major` and `ExecutionContext.minor` are not layer identifiers.
They are operational partition labels:

- `major`: customer, tenant, organization, or `single`.
- `minor`: region, district, site, or `global`.

System, subsystem, and component identity stays in descriptors, collection
namespace, runtime metadata, and observability context.

ID entropy follows the same policy as randomness:

- production/default execution uses a non-deterministic generator;
- test execution uses a deterministic generator;
- deterministic generation must still produce unique values within one
  execution context;
- fixtures must keep the returned id instead of writing entropy values such as
  `stable` directly.

## Deferred Implementation

Known modes:

- development server started from a component dev directory;
- development server started from a subsystem dev directory;
- packaged CAR execution;
- packaged SAR execution;
- command/direct execution;
- test execution;
- future tenant-aware or partitioned runtime execution.

The implementation baseline now feeds the chosen namespace into
`ExecutionContext.idGeneration` and uses `single/global` as the CNCF default.
The remaining work is to wire SAR/CAR descriptor defaults into the resolution
chain and continue removing old `sys/sys` fixture or compatibility references
that are not part of EntityId runtime namespace generation.

## Required Invariants

- Application code must not hard-code `major` / `minor`.
- Application code must not hard-code ID timestamp or entropy.
- New Entity ids must be generated through `ExecutionContext.idGeneration`.
- Generated component code must not bake in fixture `major` / `minor` values for new
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
- Is `sys/sys` absent from new code and treated only as compatibility debt when
  found in old implementation or fixtures?

## Open Questions

- Should `ExecutionContext.major/minor` remain string labels or be wrapped in a
  stronger runtime namespace type?
- Where should tenant/organization scope live in `ExecutionContext`, and how
  should it interact with uniqueness / identity checks?
- Which layer should perform final SimpleEntity default completion:
  UnitOfWork, EntitySpace, EntityStore, or all three as defensive boundaries?
- Should JobId, TaskId, ActionId, workflow ids, and association ids move to the
  same execution-context ID generation source in the next slice?
