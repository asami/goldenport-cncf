# Event Builtin Component Direction

## Decision

CNCF should provide a builtin component named `event`.

This component should expose two services:

- `event`
- `event-admin`

This is the event-side counterpart to builtin `job-control`.

It should also fit the upcoming `Component.port` direction, where protocol operations are facades over typed service traits.

## Why

Framework event observation already exists through lower-level APIs such as:

- `component.eventStore.query(...)`

But samples such as `05.a-job-control-lab` should not depend on direct framework API calls for their mainline observation path.

The external event surface should be available as builtin command/API routes.

## Component And Service Shape

### Component

- `event`

### Services

#### `event`

Application-facing event read surface.

Minimum operations:

- `load-event`
- `search-event`

Purpose:

- read one event
- read event records relevant to application use-cases

#### `event-admin`

Administrative event observation surface.

Minimum operations:

- `load-event-store-status`
- `search-event-log`
- `load-job-events`

Purpose:

- administrative observation
- debugging
- lifecycle event inspection
- support for labs such as `05.a-job-control-lab`

## First Priority

The first priority is not a full event subsystem design.

It is to make builtin event observation available so job lifecycle events can be observed without direct `eventStore` access from samples.

That means the minimal practical target is:

- `event-admin.load-job-events`

with:

- one job id input
- event records filtered to that job

## Selector Style

Help and usage should follow the current selector direction:

- formal names remain formal
- CLI/REST selectors use kebab-case

Examples:

- `event`
- `event.event`
- `event.event-admin`
- `event.event-admin.load-job-events`

## Sample Impact

### `05.a-job-control-lab`

Should eventually use:

- builtin `job-control` for submit/control/history
- builtin `event` for lifecycle event observation

This will remove the remaining direct `component.eventStore.query(...)` usage from the sample.

## Scope Boundary

This first builtin `event` component should stay small.

It does not need:

- replay
- subscription management
- distributed transport
- event schema registry

The first line is observation only.
