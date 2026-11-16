# Selected CAR resource defaults

Status: specification
Updated: Nov. 16, 2026

## R1 — Selected default provenance

The selected primary CAR may supply defaults for exactly two existing runtime
setting groups: `textus.resource.url.file.roots` and
`textus.resource.read.max-bytes`. Each group includes its existing
`textus.runtime.*`, `cncf.*`, and `cncf.runtime.*` aliases. No new setting,
schema, Component, or initialization-only CAR is introduced.

Only descriptor configuration entries retaining `componentDefaultSources`
provenance after ordinary selection, expansion, and assembly overrides are
eligible. An unselected CAR, a descriptor without CAR default provenance, or
an external assembly contribution does not supply resource defaults through
this connection. Defaults overridden by an external assembly lose their CAR
provenance. Other surviving CAR entries may be carried internally, but the
runtime contribution is restricted to these two groups.

`${runtime.root}` is expanded through the existing admitted RuntimeRoot. The
existing project-root selection owner determines that root; this contract does
not introduce a current-working-directory fallback or change root selection.

## R2 — Explicit configuration precedence

The presence of any spelling of a group in supplied resolved configuration,
whether a raw flat key or nested object path, suppresses every CAR default in
that group. Raw presence includes empty, malformed, null, and typed values.
Supported effective values from JVM properties and files selected by
`textus.config.file` or `cncf.config.file` also suppress the group's CAR defaults.
The presence query reuses `ConfigurationAccess` and its existing source order:
flat key, nested object path, JVM property, then property-selected configuration
files. RuntimeConfig's existing canonical and alias resolution remains in charge
of choosing the effective value; the overlay does not copy fallback values into
resolved configuration. Empty or whitespace-only JVM properties remain ignored,
as they are by the existing resolver.

Explicit invalid input recognized by those existing sources is never repaired
using a CAR default. Explicit empty resolved roots continue to disable file
access. Existing parsing, validation, and precedence among supplied aliases
remain unchanged. A supplied root group does not suppress an otherwise absent
byte-limit group, or conversely.

The limited overlay preserves supplied values and their original trace. It is
used to construct the normal global RuntimeConfig. It does not replace the
bootstrap configuration, typed configuration snapshot, subsystem configuration,
alias resolver, source admission, or profile admission.

## R3 — Existing resource enforcement

ExecutionContext obtains resource access from the normal global runtime owner.
The existing absolute-root, outside-root, symbolic-link, and bounded-read
policies continue to apply. Supplying these defaults does not supply HTTPS,
network, URN, resource-tree, write, security, process, or datastore settings.
It grants no additional Operation authorization or application capability.

## R4 — Application preparation responsibility

The same ordinary application CAR may prepare its environment inside its
Start/prepare operation. Logical Session binding and business initialization
remain application-owned. Successful environment preparation grants no new
permission. Storage initialization order, event cursor contracts, user mode,
local-subject admission, HOME profile conflicts, invalid profiles, explicit bad
assemblies, and normal process/storage admission and selection are unchanged.

## Executable evidence

`SelectedCarResourceDefaultsSpec` exercises a real packaged descriptor through
runtime preflight and normal GlobalRuntimeContext/ExecutionContext file reads,
plus generated canonical/alias combinations and provenance exclusions. It is
focused evidence for this connection. Installed application preparation after
normal artifact publication remains a separate acceptance check.
