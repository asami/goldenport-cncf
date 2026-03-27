# JobControl Builtin Component Mini Low Instruction

## Goal

Implement the first builtin `JobControl` component for CNCF.

This work is for the mainline external job-control surface that `05.a-job-control-lab` should use.

## Read First

- [/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/03/05-a-job-control-builtin-direction.md](/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/03/05-a-job-control-builtin-direction.md)
- [/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/03/job-lifecycle-event-and-history-direction.md](/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/03/job-lifecycle-event-and-history-direction.md)
- [/Users/asami/src/dev2026/cncf-samples/docs/phase/samples/05.a-job-control-lab.md](/Users/asami/src/dev2026/cncf-samples/docs/phase/samples/05.a-job-control-lab.md)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/Component.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/Component.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/job/JobEngine.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/job/JobEngine.scala)
- [/Users/asami/src/dev2026/cncf-samples/samples/05.a-job-control-lab/src/main/scala/org/sample/jobcontrol/JobControlDemo.scala](/Users/asami/src/dev2026/cncf-samples/samples/05.a-job-control-lab/src/main/scala/org/sample/jobcontrol/JobControlDemo.scala)

## Required Outcome

Add a builtin component named `JobControl` that exposes two services:

- application-facing job service
- admin-facing job-control service

The first version must make these capabilities externally callable:

- load/read one job status/result
- read one job history or timeline
- cancel one job
- suspend one job
- resume one job
- read lifecycle events for one job

## Service Direction

Use two services.

### `job`

Application-facing read surface.

Minimum operations:

- `load-job`
- `load-job-history`

### `job-admin`

Administrative control surface.

Minimum operations:

- `cancel-job`
- `suspend-job`
- `resume-job`
- `load-job-events`

Keep selectors kebab-case in help/usage.

## Rules

- Keep this small.
- Reuse existing `JobEngine`, `ComponentLogic`, and `EventStore` APIs.
- Follow the builtin component style already used by `AdminComponent`.
- Keep the next wiring direction compatible with `Component.port` and typed service traits.
- Do not redesign the whole job subsystem.
- Do not add CML or generator work for this task.
- Do not move sample logic into CNCF unless it belongs in builtin `JobControl`.

## Stop Conditions

Stop immediately if any of these becomes necessary:

- a major redesign of the job model
- a broad security redesign
- Cozy/CML or generator changes
- a distributed job architecture

If blocked, report only:

- the exact missing capability
- the exact file or command where it blocked
- which files were changed before stopping

## Suggested Steps

1. Create builtin `JobControlComponent` under the builtin component package.
2. Define the two services and their minimal operations.
3. Route those operations to the existing framework APIs:
   - `component.jobEngine.query(...)`
   - `component.jobEngine.queryTimeline(...)`
   - `component.logic.controlJob(...)`
   - `component.eventStore.query(...)`
4. Make sure the builtin component is discoverable with the normal builtin component path.
5. Verify help output for:
   - `job-control`
   - `job-control.job`
   - `job-control.job-admin`
6. Verify at least one read route and one control route.

## Minimum Verification

At minimum, confirm:

- `command help job-control`
- `command help job-control.job`
- `command help job-control.job-admin`

And confirm that the component can execute:

- one job read operation
- one job control operation

The read/control verification may use an existing sample job as input.

## Report Back Only

- what files you changed
- what services were added
- what operations were added
- what help commands succeeded
- what real read/control command succeeded
- what remains unfinished, if anything
