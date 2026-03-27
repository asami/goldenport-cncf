# Subsystem Shared Job Engine Mini Low Instruction

## Goal

Implement the first subsystem-shared `JobEngine` for CNCF.

This work exists to unblock builtin `JobControl`.

## Read First

- [/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/subsystem-shared-job-engine-direction.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/subsystem-shared-job-engine-direction.md)
- [/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/jobcontrol-builtin-component-mini-low-instruction.md](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/jobcontrol-builtin-component-mini-low-instruction.md)
- [/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/03/05-a-job-control-builtin-direction.md](/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/03/05-a-job-control-builtin-direction.md)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/Component.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/Component.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala)
- [/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/job/JobEngine.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/job/JobEngine.scala)

## Required Outcome

Make components in the same subsystem share one `JobEngine`.

The first version must allow:

- one component submits a job
- another component in the same subsystem reads that job
- another component in the same subsystem controls that job

## Rules

- Keep this minimal.
- Keep it in-memory.
- Do not redesign into distributed job execution.
- Do not add persistence redesign.
- Do not change Cozy/CML or generator code.
- Do not broaden this into a generic subsystem state redesign.

## Stop Conditions

Stop immediately if any of these becomes necessary:

- distributed job backend design
- major subsystem architecture rewrite
- security redesign
- CML / generator changes

If blocked, report only:

- the exact missing capability
- the exact file or command where it blocked
- which files were changed before stopping

## Suggested Steps

1. Add one shared `JobEngine` to `Subsystem` or its bootstrap path.
2. Make component initialization use that shared engine instead of creating isolated engines.
3. Keep component/job APIs unchanged where possible.
4. Verify with at least two components in one subsystem:
   - submit from one
   - read/control from another
5. Only after that, return to builtin `JobControl`.

## Minimum Verification

Confirm one concrete cross-component case:

- component A creates a job
- component B reads or controls the same job successfully

## Report Back Only

- what files you changed
- where the shared engine now lives
- how component initialization was changed
- what cross-component verification succeeded
- what remains unfinished, if anything
