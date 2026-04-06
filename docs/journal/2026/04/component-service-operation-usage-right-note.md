# Component / Service / Operation Usage Right

Date: 2026-04-07

## Purpose

This note captures the coarse-grained usage right model for:

- component
- service
- operation

This is distinct from entity/resource authorization.

## Design Position

The intended split is:

- coarse-grained usage right
  - checked at the `ActionCall` chokepoint
- fine-grained resource authorization
  - checked at the `UnitOfWorkInterpreter` chokepoint

This means:

- component / service / operation usage is an admission problem
- entity / system object access is an execution authorization problem

## Capability Form

The current direction is to express usage right through capability patterns of
the following form:

- `component:{name}:use`
- `service:{name}:invoke`
- `operation:{name}:invoke`

Normalized variants such as dot or underscore notation may be accepted
internally, but the semantic shape is:

- `{TARGET_KIND}`
- `{TARGET_NAME}`
- `{ACTION}`

## Current Implementation Position

At the current stage:

- canonical capability matching support is being introduced through
  `SecuritySubject`
- `ActionCall` is the intended enforcement point
- the default policy remains open unless explicit tightening is introduced later

This is intentional.

For now, ordinary systems can remain usable without a large amount of usage-right
configuration.

## Relationship to Create

At the current phase, this coarse-grained usage right is also the main control
boundary for `create`.

This means the current design assumes:

- service usage right
- operation usage right

can provide the main admission boundary for creation, while ordinary entity-side
authorization remains focused on:

- read
- update
- delete
- search/list

## Future Direction

Later phases may tighten usage-right enforcement and move selected targets toward:

- explicit deny-by-default;
- generated service-level metadata;
- generated operation-level capability requirements.
