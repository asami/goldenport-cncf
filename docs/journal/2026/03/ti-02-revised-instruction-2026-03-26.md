# TI-02 Revised Instruction (textus-identity Minimal Subsystem Runtime Integration)

status=ready
published_at=2026-03-26
owner=textus-identity

## Goal

Close the revised TI-02 slice for `textus-identity` by realizing the minimal
descriptor-first Subsystem runtime path in CNCF.

At this stage, `textus-identity` is not a subsystem-owned operation surface.
It is a minimal Subsystem that owns exactly one referenced Component:
`textus-user-account`.

## Primary Targets

- `/Users/asami/src/dev2026/textus-identity`
- `/Users/asami/src/dev2025/cloud-native-component-framework`

Reference component:

- `/Users/asami/src/dev2026/textus-user-account`

Conditional supporting repos:

- `/Users/asami/src/dev2026/sbt-cozy`
- `/Users/asami/src/dev2025/cozy`

Touch support repos only if the minimal Subsystem cannot be expressed,
packaged, or loaded with current grammar and packaging support.

## Read Order

Before implementation, read in this order:

1. `/Users/asami/src/dev2026/textus-identity/docs/journal/2026/03/ti-02-handoff-2026-03-26.md`
2. `/Users/asami/src/dev2026/textus-identity/docs/journal/2026/03/ti-02-instruction-2026-03-26-revised.md`
3. `/Users/asami/src/dev2026/textus-identity/docs/journal/2026/03/ti-01-contract-freeze-2026-03-26.md`
4. `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10.md`
5. `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10-checklist.md`

## Required Policy Alignment

- Preserve reusable generic runtime mechanism from the original TI-02 slice.
- Remove or quarantine only the `textus-identity`-specific assumptions that
  conflict with the revised minimal-subsystem design.
- `textus-user-account` remains the only referenced Component in this slice.
- Related project updates and `sbt` executions do not require confirmation
  prompts.

## In-Scope

1. Descriptor-first minimal Subsystem realization
- Keep `textus-identity` as one Subsystem descriptor referencing one
  Component.
- Ensure the active path is based on the descriptor/SAR model, not the old
  subsystem-owned operation prototype.

2. CAR coordinate and resolution model
- Keep the `textus-user-account` reference as a versioned CAR coordinate.
- Close the runtime path for repository-based resolution.
- Close the external development override path for in-progress CAR use.

3. CNCF runtime hookup
- Connect the descriptor to CNCF subsystem loading.
- Ensure the referenced Component becomes the sole resolved membership of the
  resulting Subsystem.
- Keep the loading and resolution path deterministic.

4. Projection / introspection visibility
- Expose deterministic minimal-subsystem structure through existing generic
  help/describe/schema/meta surfaces where applicable.
- Validate that the runtime-visible shape reflects the revised design target.

5. Progress synchronization
- Keep Phase 10 progress docs aligned with the actual revised TI-02 state.
- Do not mark PX-01 active before revised TI-02 is actually closed.

## Out of Scope

- Multi-component subsystem design.
- New subsystem-owned identity operations.
- Token/session/provider features.
- Broad security redesign.
- Final practicalization checks in PX-01.
- Executable-spec closure work in PX-02.

## Suggested File Targets

`textus-identity`:

- `/Users/asami/src/dev2026/textus-identity/src/main/cozy/textus-identity-subsystem.cml`
- `/Users/asami/src/dev2026/textus-identity/build.sbt`
- minimal resources only if required by SAR packaging/runtime loading

`cloud-native-component-framework`:

- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/subsystem/...`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/repository/...`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/subsystem/...`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10-checklist.md`

## Required Deliverables

1. Revised TI-02 runtime path implemented for the minimal Subsystem model.
2. Deterministic resolution of the sole `textus-user-account` Component from
   descriptor to runtime membership.
3. Development override behavior implemented or explicitly proven unnecessary
   for the current slice.
4. Focused validation results with exact commands and outcomes.
5. Progress updates:
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10-checklist.md`
  - TI-02 `Status: DONE`
  - PX-01 `Status: ACTIVE`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10.md`
  - TI-02 checkbox `[x]`
  - PX-01 becomes the next active item
6. Short handoff note describing what PX-01 must validate next.

## Validation

Run focused validation only.

Examples:

```bash
cd /Users/asami/src/dev2025/cloud-native-component-framework
sbt --batch compile
sbt --batch "testOnly org.goldenport.cncf.subsystem.TextusIdentitySubsystemFactorySpec"
sbt --batch "testOnly org.goldenport.cncf.subsystem.TextusIdentitySubsystemDescriptorSpec"
```

Add one focused validation in `textus-identity` only if the SAR/descriptor side
changes materially.

## Definition of Done

TI-02 revised is DONE when:

1. The active `textus-identity` path matches the minimal descriptor-first
   Subsystem contract.
2. The Subsystem resolves exactly one referenced Component:
   `textus-user-account`.
3. The versioned CAR coordinate model is honored in runtime hookup.
4. Development override behavior is defined and handled deterministically.
5. The old prototype no longer defines the active runtime path.
6. Phase 10 docs are updated consistently.
