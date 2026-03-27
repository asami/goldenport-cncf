# Event Builtin Component Mini Low Instruction

## Goal

Implement the first builtin `event` component for CNCF.

This work is for the external event observation surface that samples should use instead of direct `component.eventStore.query(...)`.

## Read First

- [/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/event-builtin-component-direction.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/event-builtin-component-direction.md)
- [/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/03/job-lifecycle-event-and-history-direction.md](/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/03/job-lifecycle-event-and-history-direction.md)
- [/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/03/05-a-job-control-builtin-direction.md](/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/03/05-a-job-control-builtin-direction.md)
- [/Users/asami/src/dev2026/cncf-samples/docs/phase/samples/05.a-job-control-lab.md](/Users/asami/src/dev2026/cncf-samples/docs/phase/samples/05.a-job-control-lab.md)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/Component.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/Component.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/event/EventStore.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/event/EventStore.scala)
- [/Users/asami/src/dev2026/cncf-samples/samples/05.a-job-control-lab/src/main/scala/org/sample/jobcontrol/JobControlDemo.scala](/Users/asami/src/dev2026/cncf-samples/samples/05.a-job-control-lab/src/main/scala/org/sample/jobcontrol/JobControlDemo.scala)

## Required Outcome

Add a builtin component named `event` that exposes two services:

- application-facing `event`
- admin-facing `event-admin`

The first version must make these capabilities externally callable:

- load one event
- search event records
- load job lifecycle events for one job id

## Service Direction

Use two services.

### `event`

Application-facing read surface.

Minimum operations:

- `load-event`
- `search-event`

### `event-admin`

Administrative observation surface.

Minimum operations:

- `load-event-store-status`
- `search-event-log`
- `load-job-events`

Keep selectors kebab-case in help/usage.

## Rules

- Keep this small.
- Reuse existing `EventStore` APIs.
- Follow the builtin component style already used by `AdminComponent` and `JobControlComponent`.
- Do not redesign the whole event subsystem.
- Do not add CML or generator work for this task.
- Do not add distributed event transport.

## Stop Conditions

Stop immediately if any of these becomes necessary:

- a major event model redesign
- a broad security redesign
- Cozy/CML or generator changes
- distributed event architecture

If blocked, report only:

- the exact missing capability
- the exact file or command where it blocked
- which files were changed before stopping

## Suggested Steps

1. Create builtin `EventComponent` under the builtin component package.
2. Define the two services and their minimal operations.
3. Route those operations to the existing `EventStore` APIs.
4. Make sure the builtin component is discoverable with the normal builtin component path.
5. Verify help output for:
   - `event`
   - `event.event`
   - `event.event-admin`
6. Verify at least one admin observation route:
   - `event.event-admin.load-job-events`

## Minimum Verification

At minimum, confirm:

- `command help event`
- `command help event.event`
- `command help event.event-admin`

And confirm that the component can execute:

- one event read/search operation
- one `load-job-events` operation

The verification may use an existing sample that emits job lifecycle events.

## Report Back Only

- what files you changed
- what services were added
- what operations were added
- what help commands succeeded
- what real event command succeeded
- what remains unfinished, if anything
