# Server Port Allocation

Status: current

## Purpose

Textus CAR and SAR servers may run concurrently on one machine. Each artifact
therefore owns one stable default port. The default is also a machine-local
application number. Additional instances use a separate dynamic range instead
of reserving a large block beside every application default.

## Default Ranges

| Purpose | Start | End |
| --- | ---: | ---: |
| CAR application defaults | 18000 | 27999 |
| SAR application defaults | 28000 | 37999 |
| Additional CAR/SAR instances | 38000 | 47999 |

CAR defaults are assigned consecutively from `18000`. SAR defaults are assigned
consecutively from `28000`. The value is the artifact's application number
within its archive kind. A normal single instance uses this stable endpoint.

When that default is already in use, CNCF treats the new process as an
additional instance of the artifact and selects the first available port from
`38000-47999`. The additional-instance range is operational and dynamic; it is
not part of the artifact's stable application identity. Startup fails when no
port in the additional-instance range is available.

Assignments are persisted per machine in:

```text
~/.cncf/server-port-assignments.json
```

The first launch assigns the lowest unassigned port in the applicable CAR or
SAR default range. Later launches of the same artifact retain that default. The
registry is structured runtime data and may be inspected to determine all
machine-local application numbers. CNCF locks it while allocating a new default
so concurrent launches cannot claim the same port.

Every production artifact declares `textus.server.default-port` in its authored
CAR or SAR definition. The value must be in the applicable archive range. For a
modern CAR, `project.yaml` is canonical and Cozy projects the value into the
packaged component descriptor:

```yaml
project:
  component:
    config:
      textus.server.default-port: "18025"
```

Legacy CARs that do not use `project.yaml` declare the same config in their
authored `component-descriptor.json`. A SAR declares it at the top level of
`subsystem-descriptor.yaml`:

```yaml
subsystem: example-subsystem
config:
  textus.server.default-port: "28000"
```

The machine registry mirrors and reserves declared defaults. It is a runtime
coordination store, not the source of artifact assignments. Startup rejects a
declared default already owned by another artifact instead of silently
renumbering it. Undeclared local development artifacts receive the next
sequential machine-local default as a fallback. `textus.server.port-assignment-file`
can relocate that store when the runtime home is managed externally.

A bare CNCF runtime with no CAR or SAR activation retains port `8080` for
framework compatibility.

## Official Artifact Registry

CNCF defines port ranges and resolution behavior but does not own the list of
Textus official artifacts. The official CAR/SAR default-port catalog is managed
by Textus Control Center in
`docs/spec/default-server-port-registry.md`. Assigned values are written to the
artifact definition files. The machine registry mirrors them so launch order
does not change established application numbers.

## Artifact Classification

CNCF classifies an invocation from its canonical activation properties and
loaded descriptor:

- `textus.component`, component file/dev-dir/CAR-dir activation, or a `.car`
  descriptor classifies the artifact as CAR;
- `textus.subsystem`, subsystem file/dev-dir/SAR-dir activation, or a `.sar`
  descriptor classifies the artifact as SAR;
- an invocation without either activation uses the bare runtime default.

The allocation policy belongs to CNCF runtime. Components and applications do
not calculate or reserve server ports themselves.

## Explicit Override

`textus.server.port` is the canonical explicit override. The legacy
`cncf.server.port` name remains readable while it is part of the runtime
compatibility surface. Configuration and JVM system-property values have this
precedence:

1. resolved `textus.server.port` configuration;
2. resolved `cncf.server.port` configuration;
3. `-Dtextus.server.port`;
4. `-Dcncf.server.port`;
5. artifact `textus.server.default-port`;
6. the machine-local CAR/SAR assignment registry.

An explicit `textus.server.port` is not silently changed when occupied.
Operator intent is fixed, so the underlying bind failure is reported. An
artifact default is different: when it is occupied, CNCF allocates the new
instance from the shared additional-instance range beginning at `38000`.

The selected automatic port is printed when the HTTP server has bound
successfully. Clients connecting to one of several local servers should use
that endpoint or an explicitly configured base URL.
