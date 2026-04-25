# Assembly Descriptor

## Purpose

An assembly descriptor is the descriptor-oriented export of a resolved runtime assembly.

It is separate from the subsystem descriptor:

- subsystem descriptor
  - human-authored intent
  - required component list
  - explicit settings and explicit wiring when the author wants to pin them
- assembly descriptor
  - runtime-resolved operational plan
  - selected non-builtin components
  - resolved ports and wiring bindings
  - resolved glue metadata
  - assembly warnings and selection information

## Admin Surface

The CLI/admin retrieval operation is:

```bash
cncf command admin.assembly.descriptor --format yaml
```

The returned document is intended for review, version control, editing, and later re-application as an assembly descriptor.
It is also the descriptor representation of the wiring diagram.

## Placement And Override

The primary deployment placement is the top level of the SAR.

A component CAR may also carry `assembly-descriptor.*` as component-local
runtime defaults. This is for one-component application startup and for
component-provided defaults reused by a SAR. In that placement, the descriptor
declares required components and wiring defaults, but it must not embed provider
component artifacts. Provider component CARs are still resolved from the standard
component repository or `repository.d`. A component CAR with this metadata can
therefore be selected by name, for example `cncf --textus.component=<component-name> server`,
without embedding provider component CARs.

The intended packaged layout is:

```text
<subsystem>.sar
  subsystem-descriptor.yaml
  assembly-descriptor.yaml
```

The subsystem descriptor remains the authored intent.
The assembly descriptor records the resolved operational plan that should be reused for reproducible execution when present.

The descriptor can also be overridden through runtime configuration.
The intended configuration key is:

```text
textus.assembly.descriptor=<path>
```

The CLI form should use the same key:

```bash
--textus.assembly.descriptor=<path>
```

This override is intended for debugging, experiments, and temporary cases where the operator needs to test a descriptor outside the packaged SAR.

Effective assembly precedence is:

1. component CAR `assembly-descriptor.*` defaults
2. selected SAR `subsystem-descriptor.*`
3. selected SAR `assembly-descriptor.*`
4. configured `textus.assembly.descriptor`, including the `--textus.assembly.descriptor=<path>` CLI form

Later sources override earlier sources by the merge rules below. The effective
assembly source keeps provenance for the selected descriptor while preserving
merged defaults required for runtime wiring. When no assembly wiring is present,
the runtime falls back to convention or subsystem descriptor wiring.


## Component Defaults And Deployment Overrides

When a component CAR is used as a deemed subsystem, its `assembly-descriptor.*`
provides component-local defaults. When a SAR uses that component, those CAR
defaults remain available and the SAR may override only the changed deployment
items.

Merge rules are field-oriented, not whole-document replacement:

- `components` merge by component name.
- `extensions` and `config` merge by key.
- `security.authentication.providers` merge by provider name.
- `security.message_delivery.providers` merge by provider name.
- `operationAuthorization` merges by operation selector.
- `wiring` merges by binding selector: `from.component`, `from.service`, `from.operation`, and optional `from.api`.

A SAR that uses the same user-account provider as the component default does not
need to repeat that provider binding. A SAR that changes the provider can define
an entry with the same provider name, such as `user-account`, and only that
provider binding is replaced.

## Current Shape

The current YAML shape is:

```yaml
kind: assembly-descriptor
subsystem: <subsystem-name>
version: <subsystem-version>
components:
  - name: <component-name>
    origin: <component-origin>
ports: []
wiring: []
source:
  wiring: {}
runtime:
  builtin_components:
    - name: admin
      origin: builtin
diagnostics:
  warnings:
    status: ok
    warning_count: 0
    warnings: []
```

`components` contains the selected application-level components that should be part of the reusable descriptor body.
Builtin components are normally runtime-provided and are therefore placed under `runtime.builtin_components` instead of being mixed into `components`.

`wiring` is the resolved wiring binding list.
It is the primary wiring representation in the assembly descriptor because this document represents the resolved operational plan.

`source.wiring` preserves the raw subsystem descriptor wiring block when available.
It is kept as provenance, not as the primary re-application shape.

`diagnostics.warnings` contains assembly warnings and related diagnostic information.
It is separated from the descriptor body because warnings are inspection output, not the reusable operational plan itself.

## Report Boundary

`admin.assembly.report` remains the operational inspection surface.
It may include loaded runtime components and other observability details in a report-friendly shape.

`admin.assembly.descriptor` is the descriptor-facing projection.
It should avoid report-only structure where possible and should keep generated runtime details separate from the reusable descriptor body.

## Visual Projection

Future visual output such as SVG or a web dashboard diagram should be a projection of the same resolved assembly model.
The visual projection must not become a separate semantic source of truth.

The current CLI/admin visual operation is:

```bash
cncf command admin.assembly.diagram
```

It returns a Mermaid `flowchart` projection of the same resolved assembly wiring.
This is the current text-based visual representation and is intended to be replaceable or complemented by SVG/web dashboard rendering later.
