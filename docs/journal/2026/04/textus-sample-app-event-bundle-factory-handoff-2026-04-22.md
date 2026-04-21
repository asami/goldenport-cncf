# Handoff: `textus-sample-app-event` as the Driver for Bundle/Componentlet Runtime

Target sample:
- `/Users/asami/src/dev2026/textus-sample-app-event`

Related CNCF repo:
- `/Users/asami/src/dev2025/cloud-native-component-framework`

Relevant CNCF commit:
- `cde6ad7 Split bundle and participant factory model`

## 1. What changed in CNCF

The runtime construction model is no longer based on one flat factory returning:

- `Vector[Component]`

The new target model is:

- one `Component.BundleFactory`
- one `Component.PrimaryComponentFactory`
- zero or more `Component.ComponentletFactory`

Construction-time asymmetry is now explicit:

- `primary`
- `componentlets`

Runtime symmetry remains:

- both primary and componentlets are real runtime `Component`
- both participate in event/action/job execution as first-class runtime participants

## 2. What this means for `textus-sample-app-event`

The sample should now be treated as the application driver for this model.

It should no longer model the generated/app runtime as:

- one root factory returning `Vector(primary, componentlet1, componentlet2, ...)`

It should instead model:

- one bundle factory
- one primary participant factory
- one factory per runtime componentlet

For the current sample shape, the intended structure is:

- bundle factory
  - primary factory for the root app component
  - componentlet factory for `public-notice`
  - componentlet factory for `notice-admin`

## 3. Runtime assumptions now fixed in CNCF

The sample may now rely on these runtime contracts:

### Construction / identity

- primary and componentlets are distinct at construction time
- componentlets are real runtime participants after construction
- runtime source/target identity uses the resolved component/componentlet name
- root alias should not replace runtime componentlet identity in event/job metadata

### Event reception

- same-subsystem sync-inline semantics are framework-owned
- same-subsystem async/new-job continuation baseline remains valid
- event metadata / policy metadata / lineage visibility remain framework-owned
- target dispatch runs on the target participant execution context

### Inspection

- builtin `event`
- builtin `job_control`
- builtin `admin.execution.diagnostics`

already expose the current Phase 13 visibility surfaces

## 4. What the sample should implement now

The sample should be updated to the new construction model first, then used as the development driver.

### A. Replace construction shape

Replace any factory shape equivalent to:

- `create_Components(...): Vector[Component]`

with:

- one bundle factory
- one primary factory
- participant-local componentlet factories

### B. Keep participant-local behavior local

Each participant factory should own:

- runtime name
- component id
- protocol/services/operations
- event definitions
- subscriptions
- reception rules
- participant-local action / `ActionCall` behavior

Do not move componentlet-local behavior back into the bundle factory.

### C. Use the sample as the real acceptance driver

The sample should verify real application behavior, not fixture-only behavior:

1. primary / `public-notice` / `notice-admin` are created through the new bundle model
2. componentlet runtime names are preserved in routing and metadata
3. same-subsystem sync-inline reception still works
4. same-subsystem async/new-job continuation still works
5. builtin event/job/admin inspection remains consistent with the sample runtime

## 5. What should be removed from the sample side

Do not reintroduce sample-side compensation for missing framework behavior.

The sample should not depend on:

- manual root-level componentlet alias substitution
- bundle-level rewriting of target identity
- app-side event metadata patching
- app-side direct dispatch compensation

The sample should consume the framework contract as-is.

## 6. Recommended update order in the sample repo

1. Introduce the bundle factory
2. Split current primary/componentlet construction into participant factories
3. Keep current runtime behavior unchanged while construction is refactored
4. Re-run same-subsystem sync scenario
5. Re-run async/new-job scenario
6. Confirm event/job/admin inspection still shows runtime componentlet identity

## 7. Scope boundary

This handoff is only about moving the sample to the new construction model and using it as the development driver.

Out of scope here:

- new ABAC work
- event retry orchestration
- dead-letter handling
- broader CML grammar redesign inside CNCF

The CML/source-of-truth side remains outside CNCF ownership.
For CNCF, the important point is that generated output should now target:

- `BundleFactory`
- `PrimaryComponentFactory`
- `ComponentletFactory`

not the old flat vector construction model.
