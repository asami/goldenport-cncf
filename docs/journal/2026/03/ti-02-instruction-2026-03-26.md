# TI-02 Instruction (Minimal Subsystem Realization for textus-identity)

status=ready
published_at=2026-03-26
owner=textus-identity

## Goal

Realize the minimal executable Subsystem configuration in CNCF.

At this stage, `textus-identity` is defined as a Subsystem that owns exactly
one Component: `textus-user-account`.

The implementation goal is not broad identity orchestration yet. The goal is
to establish the minimal Subsystem model and runtime connection path:

1. define the Subsystem in CML
2. declare `textus-user-account` as the used Component
3. connect that descriptor to CNCF
4. make the resulting Subsystem distributable as the smallest SAR shape
   containing only a descriptor file

## Primary Targets

- `/Users/asami/src/dev2026/textus-identity`
- `/Users/asami/src/dev2025/cloud-native-component-framework`

Reference component:

- `/Users/asami/src/dev2026/textus-user-account`

Conditional supporting repos:

- `/Users/asami/src/dev2026/sbt-cozy`
- `/Users/asami/src/dev2025/cozy`

Touch support repos only if the minimal Subsystem cannot be represented or
loaded with current grammar/packaging/runtime capabilities.

## Design Authority

This instruction supersedes the earlier TI-02 runtime-integration wording for
the current execution slice.

The design authority for this slice is:

- minimal Subsystem = one Subsystem descriptor + one referenced Component
- `textus-identity` currently references only `textus-user-account`
- Subsystem packaging is SAR
- the initial SAR shape is expected to contain only one descriptor file
- the referenced Component is identified as a versioned CAR artifact
- runtime resolution supports both:
  - repository-based CAR acquisition for normal use
  - external development parameter override for in-progress CAR use

## Required Policy Alignment

- TI-01 remains the semantic contract baseline, but TI-02 in this slice is
  primarily packaging/bootstrap/runtime wiring.
- Do not re-embed or duplicate `textus-user-account` implementation inside
  `textus-identity`.
- Do not require multiple Components, provider adapters, sessions, or token
  issuance in order to close this slice.
- Related project updates and `sbt` executions do not require confirmation
  prompts.

## Minimal Architecture to Implement

### 1. Subsystem descriptor in CML

Create or refine CML so that it defines:

- Subsystem name
- Subsystem package/name metadata as needed
- used Component entry for `textus-user-account`
- component identity expressed as versioned CAR coordinates

The descriptor should stay intentionally minimal.

### 2. SAR minimal shape

The expected first practical SAR form is:

```text
textus-identity.sar
  descriptor.(cml or runtime-accepted descriptor file)
```

No bundled CAR is required for this slice unless runtime absolutely requires
it. Prefer descriptor-only SAR.

### 3. Component reference model

`textus-user-account` must be referenced as a versioned CAR artifact, in the
same general style as Java/Scala Maven coordinates.

The design target is equivalent to:

```text
org.simplemodeling.car:textus-user-account:<version>
```

Exact descriptor syntax should follow the existing grammar/runtime contract if
already available.

### 4. Resolution policy

Normal operation assumption:

- CAR is fetched from a component repository such as
  `simplemodeling.org/car`

Development-time assumption:

- runtime can inspect an external parameter for development override
- if present, the override points to a development CAR source/location
- that override is used instead of the normal repository lookup

This override must be explicit and externalized. Do not hardcode local
development directories in the subsystem factory as the steady-state design.

### 5. CNCF connection path

The descriptor must be connected into CNCF using one coherent path.

Acceptable examples:

- SAR intake path
- direct descriptor loading path that is structurally identical to SAR loading
- bootstrap/factory path that consumes the descriptor and resolves the CAR

Avoid a one-off handwritten `textus-identity` factory that bypasses the
descriptor/CAR resolution model unless it is a temporary bridge and clearly
marked as such.

## In-Scope

1. Minimal Subsystem descriptor design
- Define the smallest valid CML/descriptor for `textus-identity`.
- Ensure it names exactly one used Component: `textus-user-account`.

2. SAR descriptor packaging
- Ensure the minimal Subsystem can be represented as SAR with only the
  descriptor file when possible.
- Add the smallest packaging/config support needed for that shape.

3. CAR coordinate declaration
- Define how the descriptor names the `textus-user-account` CAR with version.
- Keep the coordinate syntax deterministic and documented.

4. Development override path
- Define and implement the external parameter that points runtime to a
  development CAR source/location.
- Keep the override mechanism outside the descriptor's core identity.
- Document precedence between development override and repository resolution.

5. CNCF runtime hookup
- Make CNCF load the minimal Subsystem descriptor and resolve the referenced
  Component through the coordinate/override model.
- Ensure resulting Subsystem introspection shows the resolved component
  membership deterministically.

6. Focused verification
- Prove that the minimal Subsystem loads and shows `textus-user-account`
  as its only Component.
- Prove that normal resolution and development override resolution both work,
  if both are implemented in this slice.

## Out of Scope

- Multi-component subsystem design.
- Identity-provider adapter integration.
- Session/token feature implementation.
- Rich subsystem-owned operation orchestration beyond what is required to
  prove minimal Subsystem realization.
- Bundling all dependent CARs into SAR unless the runtime forces it.
- Broad repository protocol/security implementation beyond minimal lookup
  requirements for this slice.

## Suggested File Targets

`textus-identity`:

- `/Users/asami/src/dev2026/textus-identity/src/main/cozy/textus-identity-subsystem.cml`
- `/Users/asami/src/dev2026/textus-identity/src/main/resources/...` if a
  minimal runtime descriptor/config is needed
- `/Users/asami/src/dev2026/textus-identity/build.sbt` if SAR packaging or
  descriptor resource wiring is needed

`cloud-native-component-framework`:

- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/subsystem/...`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/repository/...`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/...`
- relevant focused tests under `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/...`

`sbt-cozy` / `cozy` (conditional only):

- only if current descriptor grammar or SAR packaging cannot express the
  minimal shape above

## Required Deliverables

1. Minimal `textus-identity` Subsystem descriptor in CML.
2. Frozen declaration that `textus-user-account` is the sole used Component in
   this slice.
3. Defined CAR coordinate contract for that Component reference.
4. Defined and implemented external development override parameter for CAR
   acquisition.
5. CNCF runtime path that loads the descriptor and resolves the referenced
   Component.
6. Focused validation results with exact commands.
7. Progress updates:
- `/Users/asami/src/dev2026/textus-identity/docs/phase/phase-10-checklist.md`
  - TI-02 `Status: DONE` when satisfied
- `/Users/asami/src/dev2026/textus-identity/docs/phase/phase-10.md`
  - TI-02 checkbox `[x]`
  - next active item updated accordingly
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10-checklist.md`
  - TI-02 `Status: DONE`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-10.md`
  - TI-02 checkbox `[x]`
  - next active item updated accordingly
8. Short handoff note describing what PX-01 should validate next.

## Validation

Use the lightest verification that proves the design is real.

Examples:

```bash
cd /Users/asami/src/dev2026/textus-identity
find . -maxdepth 4 -type f
```

Then run the minimum focused checks required to prove:

- Subsystem descriptor is loadable
- loaded Subsystem contains only `textus-user-account`
- Component resolution works from declared CAR coordinates
- development override parameter changes the CAR acquisition target when set
- subsystem introspection/projection reflects the single-component structure

If CNCF is touched, run only focused compile/test commands for the affected
subsystem/repository/projection path and report exact commands/results.

## Definition of Done

TI-02 is DONE when:

1. `textus-identity` is realized as the minimal executable Subsystem shape.
2. The Subsystem descriptor declares exactly one used Component:
   `textus-user-account`.
3. The Component reference is expressed as a versioned CAR contract.
4. CNCF can connect the descriptor to actual Component loading.
5. An external development override parameter can redirect CAR acquisition for
   in-progress development use.
6. The resulting Subsystem structure is visible and deterministic in runtime
   introspection.
7. Phase 10 docs reflect TI-02 completion consistently.
