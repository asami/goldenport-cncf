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

The primary operational placement is the top level of the SAR.

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

Resolution order should be:

1. configured `textus.assembly.descriptor`, including the `--textus.assembly.descriptor=<path>` CLI form
2. `assembly-descriptor.*` at the top level of the selected SAR
3. convention-based assembly from the subsystem descriptor

The current implementation detects the configured or SAR-top assembly descriptor and exposes it as source/provenance in `admin.assembly.descriptor`.
It does not yet replace the subsystem wiring resolution with the assembly descriptor contents.

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
