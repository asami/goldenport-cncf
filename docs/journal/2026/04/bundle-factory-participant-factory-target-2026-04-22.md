# Bundle Factory / Participant Factory Target

## Summary

The runtime construction model now distinguishes:

- bundle/artifact-level construction
- participant-level construction

The canonical runtime target is:

- one `Component.BundleFactory`
- one `Component.PrimaryComponentFactory`
- zero or more `Component.ComponentletFactory`

The old flat `create_Components(...): Vector[Component]` construction contract is no longer the target model.

## Runtime Contract

- Construction-time asymmetry is explicit.
  - `Component.Bundle(primary, componentlets)`
  - `primary` and `componentlets` are separated in the API
- Runtime symmetry remains intact.
  - both primary and componentlets become runtime `Component`
  - both participate in event/action/job execution as real runtime participants
- Participant-local behavior remains participant-local.
  - `Action#createCall(core)`
  - `ComponentLogic`
  - event metadata / subscriptions / reception rules
  stay attached to the participant created by its own participant factory

## Generator Target

Generated output should now target this shape:

- generated bundle factory:
  - assembles one primary participant factory
  - assembles zero or more componentlet participant factories
- generated primary participant factory:
  - creates the primary runtime component
  - provides the primary component core/protocol
- generated componentlet participant factory:
  - creates one bundled runtime componentlet
  - provides componentlet-local protocol / operation / event / subscription / reception metadata

Generated output should not target:

- flat `Vector[Component]`
- root alias substitution for runtime componentlet identity
- metadata-only componentlet runtime participation

## CML Boundary

CML source-of-truth is outside CNCF ownership.

CNCF does not define a parallel CML grammar here.
The required mapping is:

- CML primary component
  -> generated `PrimaryComponentFactory`
- CML componentlet
  -> generated `ComponentletFactory`
- CML artifact/bundle
  -> generated `BundleFactory`

The generated descriptor/runtime contract should preserve this distinction explicitly.

## Validation Added In CNCF

Executable coverage now protects:

- explicit `primary` / `componentlets` bundle construction
- participant-local `Component.Core`
- generated-style runtime componentlet creation through `ComponentletFactory`
- same-subsystem sync reception on a generated-style componentlet while preserving the runtime componentlet name

## Follow-Up Outside CNCF

Cozy / simple-modeler should update code generation to emit:

- bundle factory
- primary participant factory
- componentlet participant factories

No compatibility bridge back to flat `Vector[Component]` is assumed.
