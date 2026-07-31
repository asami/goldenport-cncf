# Phase 53 CS-01C - Assembly, Mode, Operational State, and Style Boundary

Status: CS-01C in progress.  This is a non-normative, source-backed inventory
and boundary record.  It does not change a runtime, launcher, operational
filesystem rule, or public wire schema.

## Assembly and configuration ownership

`CncfRuntime._resolve_configuration` and
`CncfRuntime._runtime_standard_config_sources` own CNCF runtime source
selection.  They currently supply `cncf` sources before `textus` sources at
each HOME, PROJECT, and CWD origin.  The generic `ConfigurationResolver`
preserves supplied order within an origin, so the current same-origin result
makes `.textus` stronger than `.cncf`.

`CncfRuntime._with_configured_assembly_descriptor_configuration` supplies
assembly descriptor values only for unresolved keys.  Descriptor overlays are
owned by `GenericSubsystemDescriptor` and `GenericSubsystemFactory`.  Startup
is owned by `CncfRuntime._initialize`: resolve configuration and invocation,
build `RuntimeConfig`, create the scoped Subsystem, bootstrap components,
resolve SPI, then run startup import.

## Mode and launcher authority

`RuntimeConfig` owns `textus.operation-mode` (current default `develop`).
`RuntimeContext` carries it and `ExecutionContext.CncfCore.Holder` exposes it
to current runtime consumers.  `ComponentLogic` currently copies it into the
component runtime context.  `OperationAuthorization` and `WebDescriptor` also
have mode-aware security behavior; CS-01 does not remove or reclassify that
behavior before its security effect is inventoried.

`WebExecutionProjection` defines `WebApplicationMode`, and
`WebExecutionResolution` currently resolves
`textus.web.execution.application-mode` with an unconditional standalone
default.  The selected Phase 53 public semantic is instead
`textus.web.application-mode`, resolved before FixedUserProfile with only a
conditional, traceable direct-Component standalone default.  This is a target
boundary, not a claim that the current key already implements it.

`cncf-launcher` selects runtime/artifacts and forwards runtime configuration
paths and development arguments.  `textus-launcher` selects CNCF runtime and
forwards mode and other arguments.  Neither launcher owns FixedUserProfile
parsing, ComponentStyle selection, or WebApplicationMode resolution.

Runtime inspection is currently represented by
`StaticFormAppRendererSystemAdminPart`, which exposes masked resolved
configuration and effective OperationMode.  Style/capability matches,
WebApplicationMode source, fixed-user provenance, and datastore binding sets
are not currently its inspection contract.

## Operational-state path matrix

| Owner | Current source-backed paths | Current lifecycle evidence | Phase 53 boundary |
| --- | --- | --- | --- |
| CNCF launcher | `~/.cncf/launcher.yaml`, `config.yaml`, `version`, `runtimes/`, `catalog/`, `cache/`, `local/`, `launcher/server-evidence.json`, `launcher/supervisor-state.json` | `server-evidence.json` is atomically replaced, retention-bounded, and recovers malformed content; `supervisor-state.json` is atomically replaced but reports unavailable malformed state without retention recovery | read-only inventory; no migration, deletion, backup, or general permission rule is added |
| Textus launcher | `~/.textus/config.yaml`, `version`; shared runtime/cache/local state under `~/.cncf` | launcher forwards runtime state selection | read-only inventory; no sibling traversal or rewrite |
| server port policy | `~/.cncf/server-port-assignments.json` | updated under file lock | no lifecycle change |
| component datastore | `~/.cncf/<component>/<datastore>.db` | Subsystem/runtime datastore owner | no Component-owned selection, migration, or deletion rule |
| profile resolution target | `~/.textus/user-profile.yaml`, `~/.cncf/user-profile.yaml` | Phase 53 selected fixed-user inputs | read-only discovery only; no traversal, rewrite, rename, migration, backup, or sibling deletion |
| control-center | `~/.cncf/textus-control-center` locator/credential state | owned outside this phase; launchers admit its standalone credential only with owner-read/owner-write (`0600` equivalent) POSIX permissions | no admission or mutation beyond that existing credential check |

Current sources do not define a general permission, backup, migration, or
deletion contract for `user-profile.yaml`, general configuration, databases,
local publication state, or launcher state.  The standalone control-center
credential check above is a narrow existing exception and must not be
generalized.  CS-01 does not infer `0600`, backup, or destructive operations
for any other state.

## Supported ComponentStyle freeze

The following semantics are already selected by the Phase 53 plan and are
frozen for later implementation:

- the initial selectable built-in style is
  `full-fledged-with-standalone`;
- CNCF owns built-in style definitions; `ComponentProvider` and
  `ComponentRepository` are not style-discovery mechanisms;
- the style provides `domain.full@1`, `user.multi-user@1`, and
  `user.fixed-context-compatible@1`;
- it requires `user-context.current@1`, `datastore.persistent@1`,
  `datastore.transactional@1`, and
  `datastore.optimistic-concurrency@1`;
- `domain.full@1` expands deterministically into Entity, Aggregate, Command,
  Query, domain-event, projection, persistence, transaction, and optimistic
  concurrency capabilities; and
- style metadata does not select operating mode, fixed user, locale, or
  datastore policy.

A future Metadata Factory may use the same provider/schema/capability
contract, but registration, discovery, packaging, external-provider conflict
resolution, and replacement of a CNCF built-in are not Phase 53 work.

## Explicitly not frozen

No current source or selected Phase 53 document establishes a ComponentStyle
wire `apiVersion`, descriptor `schemaVersion`, field names, JSON nesting, or
Scala type/API.  Those details remain open until the catalog and descriptor
behavioral contract is registered in CS-01 and implemented in CS-02.  CS-01
therefore must not use a guessed `cncf.textus/v1`, schema number, or accessor
name as acceptance evidence.

The exact migration classification of every OperationMode consumer, general
operational permissions/backup policy, and external-provider conflict policy
also remain open.  They prevent the corresponding CS-01 checklist items from
being marked complete.

## Scope and exclusions

CS-01C changes no production source, launcher behavior, operational-state
file, `docs/design`, or `docs/spec`.  Kaleidox remains read-only.  Before the
CS-02 change to `ComponentDefinition` or its parser, Phase 53 must record a
scope reset admitting `/Users/asami/src/dev2025/kaleidox` as the CML production
owner.  `simplemodeling` and `simplemodeling-lib` are not CML-owner candidates.
