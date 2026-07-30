# CML Application Mode Capability Consideration

Date: 2026-07-30

Status: consideration

Scope: CNCF CML component capability, assembly selection, and runtime
resolution for application operation forms

## Context

The ArtScene standalone launch investigation exposed a configuration boundary
problem. A component-global datastore default such as `local-default` makes a
personal local database look like an intrinsic ArtScene default, although that
choice is valid only for one form of operation. Conversely, a shared external
database is required when the same component is operated for multiple users.

This distinction is not specific to ArtScene. CNCF needs a framework-level
concept that lets a component:

- declare which application operation forms it supports;
- provide defaults that apply only within a selected form;
- let an assembly select one form for a deployment; and
- reject an incompatible composition before component startup.

The working name in this journal is **ApplicationMode**.

This journal is exploratory and non-normative. Its contents require promotion
to design, specification, and Executable Specifications before they become an
implementation contract.

## Boundary From OperationMode

The existing `OperationMode` describes the runtime execution posture:

- `production`
- `demo`
- `develop`
- `test`

`ApplicationMode` describes how an application is operated and how its state is
shared. The initial standard values under consideration are:

- `standalone`
- `multi-user`

The two axes are independent. For example, both `develop + standalone` and
`production + standalone` are meaningful, but they need different ownership
and persistence rules for their local database.

The existing HTTP-side `WebApplicationMode` is evidence that the concept
already exists at one projection boundary. It should be evaluated for promotion
to a common CNCF model instead of becoming a second competing mode vocabulary.

## Proposed Capability Model

A component should declare a supported ApplicationMode set, an optional
default, and mode-specific profiles. The component's category is derived from
that declaration:

- no mode dependency: mode-agnostic;
- one supported mode: fixed-mode;
- more than one supported mode: selectable-mode.

There should be no independent `dual-mode` boolean. A second representation of
the same fact can disagree with the supported set.

The initial ArtScene capability under consideration is:

- supported modes: `standalone`, `multi-user`;
- default mode: `standalone`;
- standalone application datastore policy: `local-default`;
- multi-user application datastore policy: `external-required`.

This does not make `local-default` an unconditional ArtScene or CNCF global
default. The datastore policy becomes effective only after `standalone` has
been selected.

## CML As the Declaration Source

ApplicationMode capability affects development, generation, packaging, and
runtime validation. It should therefore be expressible at the CML stage and
carried forward as typed generated metadata.

A provisional CML shape is:

```dox
# COMPONENT
## ArtScene
### APPLICATION MODES
#### standalone
default: true
datastore.application.policy: local-default
#### multi-user
datastore.application.policy: external-required
```

The exact grammar remains open. The important semantic requirements are:

- the supported mode identifiers are explicit;
- at most one supported mode is the default;
- mode-specific datastore requirements are typed semantic fields;
- invalid combinations are rejected during generation; and
- `project.yaml` does not duplicate the capability declaration.

`project.yaml` should remain concerned with project coordinates, build, and
publication metadata unless a separate design decision broadens its role.

## Generation and Runtime Projection

The intended information flow is:

```text
CML
  -> Cozy typed ApplicationMode capability
  -> development component descriptor
  -> packaged component-descriptor.json
  -> CNCF selected-mode profile
  -> resolved runtime configuration
```

CNCF runtime should consume the generated typed descriptor. It should not parse
CML directly.

A possible generated representation is:

```json
{
  "application": {
    "modes": {
      "supported": ["standalone", "multi-user"],
      "default": "standalone",
      "profiles": {
        "standalone": {
          "datastores": {
            "application": {
              "policy": "local-default"
            }
          }
        },
        "multi-user": {
          "datastores": {
            "application": {
              "policy": "external-required"
            }
          }
        }
      }
    }
  }
}
```

This JSON is illustrative, not a settled descriptor schema.

## Assembly Selection

The assembly selects the ApplicationMode for a concrete deployment:

```yaml
application:
  mode: standalone
```

The assembly selection and the component capability have separate
responsibilities:

- CML/component descriptor: what the component can support and the defaults
  scoped to each supported mode;
- assembly: which mode this deployment selects and any deployment-specific
  overrides;
- CNCF runtime: capability validation, profile selection, precedence
  resolution, and startup failure reporting.

For an assembly containing multiple components, the selected mode is currently
assumed to be assembly- or subsystem-wide. Every mode-aware component must
support that mode. Mode-agnostic components ignore it. CNCF should reject an
incompatible composition before class loading or datastore initialization.

Whether nested subsystems may select distinct modes is an open design question.

## Configuration Precedence

The proposed precedence, from strongest to weakest, is:

1. explicit test or runtime override;
2. explicit assembly override;
3. the selected ApplicationMode profile;
4. generic component defaults;
5. neutral CNCF defaults.

Only layer 3 supplies the ArtScene `local-default` or `external-required`
policy described above. Moving `local-default` to generic component defaults
would erase the standalone/multi-user distinction and is therefore not
appropriate.

## Development Semantics

ApplicationMode capability must be available when launching a CAR development
directory. Development must not require `buildCar` merely to obtain the
descriptor.

A target command shape is:

```text
cncf --application-mode standalone . server
```

CML generation or compilation should produce a development descriptor and
runtime classpath under generated output. The launcher should use that
development projection. If the generated projection is missing or stale, the
launch should fail with a diagnostic instead of silently falling back to an
older packaged CAR.

The initial OperationMode and ApplicationMode matrix is:

| OperationMode | ApplicationMode | Datastore expectation |
| --- | --- | --- |
| `develop` | `standalone` | project- or target-owned local development database |
| `production` | `standalone` | user-owned persistent local database |
| `develop` | `multi-user` | explicitly configured development external database |
| `production` | `multi-user` | explicitly configured shared external database |

The exact local database path and lifecycle require a separate design decision.
In particular, a development launch must not accidentally hide or overwrite a
user's production standalone data.

## Locale and Timezone Boundary

`ja-JP` and `Asia/Tokyo` are not inherent properties of `standalone`.
ApplicationMode should not hardcode them.

They may be appropriate fallback values for a particular standalone deployment
assembly. In multi-user operation, account or request preferences may override
deployment fallbacks. CNCF's framework-level defaults should remain neutral
unless a separate localization design establishes otherwise.

## Compatibility and Migration

The following compatibility effects need explicit treatment:

- removing a supported mode is a breaking capability change;
- changing the default mode can change deployment behavior;
- changing a mode's datastore policy can change data ownership and is
  operationally breaking;
- adding a supported mode is normally additive, subject to descriptor schema
  compatibility;
- existing `WebApplicationMode` and application-specific mode keys should be
  migrated or projected through one canonical CNCF concept;
- legacy assemblies without an explicit selection need a documented resolution
  rule and diagnostic.

ArtScene migration should remove unconditional global datastore policy once its
CML capability and assembly selection are available.

## Open Questions

1. What is the final CML grammar for supported modes, the default, and typed
   mode profiles?
2. Which CML/Cozy model owns `ApplicationModeCapability`?
3. Where does the generated descriptor schema live, and how is it versioned?
4. Is ApplicationMode always selected once per assembly/subsystem, or may a
   nested subsystem select another mode?
5. Are `standalone` and `multi-user` a closed initial vocabulary or extensible
   identifiers with CNCF-standard semantics?
6. How is a mode-agnostic component represented without confusing absence with
   a generation error?
7. What exact path, owner, retention, backup, and migration rules apply to a
   standalone local database in development and production?
8. How are old HTTP/application mode settings recognized during migration, and
   when are compatibility aliases removed?
9. Which resolved configuration and diagnostics are exposed by help,
   inspection, and launch output?

## Promotion and Implementation Handoff

Before implementation, this consideration should be promoted into:

- a CNCF design document defining `ApplicationMode`, capability composition,
  profile resolution, and compatibility;
- a CML/Cozy specification defining authoring grammar, validation, and
  generated descriptor projection;
- CNCF runtime specifications for assembly selection, precedence, startup
  validation, and diagnostics;
- launcher specifications for development-directory projection and stale
  generation detection; and
- ArtScene specifications for standalone personal-local and multi-user
  shared-external datastore behavior.

Executable Specifications should cover at least capability classification,
default validation, multi-component compatibility, precedence, the complete
OperationMode/ApplicationMode matrix, and development launch without
`buildCar`.

## Phase 53 Planning Decision — 2026-07-30

This consideration is selected as the new Phase 53:

- `docs/phase/phase-53.md`
- `docs/phase/phase-53-checklist.md`
- `docs/notes/cml-application-mode-capability-specification-proposal.md`

ArtScene is the first real downstream consumer and acceptance application.

The user decision also changes the documentation order proposed earlier in
this journal. The preceding “Before implementation” promotion wording is
superseded as follows:

1. the current contract remains a non-normative proposal under `docs/notes`;
2. failing-first Executable Specifications and implementation establish the
   actual behavior;
3. cross-repository and ArtScene acceptance verify that behavior; and
4. normative `docs/design` and `docs/spec` are written only after that
   implementation and verification.

No normative design or specification is created by the Phase 53 planning
insertion. This dated decision supersedes the earlier order without rewriting
the chronological consideration record.

## ComponentStyle and ComponentMode Revision — 2026-07-30

Subsequent consideration supersedes the working ApplicationMode/profile model
above without rewriting this journal's chronological evidence.

The revised Phase 53 direction is:

- explicit-component CML selects one ComponentStyle supplied by CNCF;
- Phase 53 implements CNCF-provided built-in ComponentStyles and fixes a
  versioned provider/schema extension boundary;
- Metadata Factory contribution of additional styles is a future development
  item, not a Phase 53 implementation or completion dependency;
- CML does not redefine the selected style or copy its policy parameters;
- `ComponentMode`, not ApplicationMode, names the component operating and
  state-sharing axis;
- canonical names follow ownership: `Component*` for component scope,
  `Subsystem*` for Subsystem scope, and `User*` for user-context scope;
- `WebApplication` remains the explicit Web application-context concept, and
  `WebApplicationMode` remains its Web-specific contract while deriving from
  or validating against the selected ComponentMode;
- a selected style supplies its supported/default ComponentModes, typed
  parameter schema, configurable boundary, defaults, and compatibility rules;
- a component implementation may override typed parameter values through a
  side-effect-free ComponentFactory hook;
- assembly/runtime/test configuration may override only parameters marked
  configurable;
- policies such as component datastore policy are typed style parameters,
  not an unvalidated profile bag; and
- final style/mode/policy validation occurs after ComponentFactory loading but
  before component activation, datastore creation, binding, or job admission.

The current non-normative proposal is:

- `docs/notes/cml-component-style-execution-context-capability-specification-proposal.md`

The former
`docs/notes/cml-application-mode-capability-specification-proposal.md` path and
the earlier ApplicationMode-specific Phase 53 wording are superseded.

The future Metadata Factory item retains this direction:

- contribute additional styles through the Phase 53 metadata contract;
- keep CNCF built-ins non-replaceable;
- make provider discovery and dependency packaging explicit at generation and
  runtime boundaries; and
- require separate executable acceptance before assigning the extension to a
  later phase.

## ExecutionContext and Capability Revision — 2026-07-30

The immediately preceding ComponentMode revision was reconsidered and is
superseded as an implementation direction.

- A ComponentStyle declares versioned Component-provided capabilities and
  required Subsystem capabilities; it does not select Component operating
  mode.
- ArtScene selects `full-fledged-with-standalone`, providing
  `domain.full@1`, `user.multi-user@1`, and
  `user.fixed-context-compatible@1`.
- `WebApplicationMode` remains the Web-specific standalone/multi-user
  contract.
- Standalone resolves the normal-operation
  `~/.textus/user-profile.yaml` baseline, then applies the optional
  higher-precedence `~/.cncf/user-profile.yaml` development overlay; both
  layers support global and application-specific fields.
- Every effective profile field retains source path/input, scope, application,
  and logical-key provenance; multi-user instead resolves the authenticated
  request user.
- Both paths construct the same Component-facing `ExecutionContext`.
- Component APIs, ComponentFactory, generated DSLs, ActionCall, and domain
  policy do not receive or expose `ApplicationMode`, `ComponentMode`,
  `SubsystemMode`, `WebApplicationMode`, or `OperationMode`.
- Datastore provider, placement, endpoint/path, credentials, and lifecycle are
  Subsystem configuration, not ComponentStyle policy.
- Temporary migration adapters, if needed, remain internal/deprecated and do
  not become permanent specification.

The revised non-normative proposal and Phase 53 plan are:

- `docs/notes/cml-component-style-execution-context-capability-specification-proposal.md`;
- `docs/phase/phase-53.md`; and
- `docs/phase/phase-53-checklist.md`.
