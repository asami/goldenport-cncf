# Textus and CNCF Home Configuration Layer Decision

Date: 2026-07-30

## Context

The Textus launcher transition introduced `~/.textus` as the primary
user-facing location and retained `~/.cncf` as compatibility input. The
developer launcher was later separated from the user/operator launcher:

- `textus` is the user/operator-facing launcher;
- `cncf` is the component-developer launcher;
- `~/.textus` is used by Textus-facing configuration; and
- `~/.cncf` already contains developer and runtime-managed state such as local
  publications, caches, and installed runtimes.

The two home directories are therefore used together in practice. Treating
`~/.cncf` only as a deprecated alias does not express its continuing
developer, framework-internal, and operational role.

The previous normative runtime configuration rule loads `.cncf` first and
`.textus` second, so `.textus` wins for a duplicate key. The decision below
revises that direction. Normative specification and implementation changes are
separate follow-up work.

## Decision

The home-directory responsibilities are:

| Directory | Responsibility | Primary audience |
| --- | --- | --- |
| `~/.textus` | Canonical configuration that may be visible to an ordinary user | application user and operator |
| `~/.cncf` | Framework-internal, development, diagnostic, and operational configuration/state | component developer, operator, and CNCF runtime |

`~/.textus` is the canonical public baseline. `~/.cncf` is an admitted
higher-precedence operational override, not merely a deprecated spelling.

For the same logical configuration field, `.cncf` may override `.textus`.
Precedence remains scope-first and is ordered from low to high as follows:

```text
contract defaults
  < HOME/.textus
  < HOME/.cncf
  < PROJECT/.textus
  < PROJECT/.cncf
  < CWD/.textus
  < CWD/.cncf
  < environment
  < explicit arguments
```

Within a user profile, global values are resolved before
application-specific values in the same directory layer. For example:

```text
~/.textus global
  < ~/.textus application
  < ~/.cncf global
  < ~/.cncf application
  < explicit runtime/test override
```

This lets an ordinary user keep stable operation settings in
`~/.textus/user-profile.yaml` while a developer applies a local override in
`~/.cncf/user-profile.yaml`.

## Directory and Key Namespaces

Directory ownership and key namespaces are separate concerns.

- A user-facing semantic keeps its `textus.*` key even when its value is
  overridden from a `.cncf` file.
- A `cncf.*` key is reserved for a CNCF-specific internal, diagnostic,
  development, or implementation control.
- The same semantic must not be represented by unrelated `textus.*` and
  `cncf.*` keys merely to obtain precedence.

Example:

```yaml
# ~/.textus/config.yaml
textus:
  locale: ja
  web:
    application-mode: standalone
```

```yaml
# ~/.cncf/config.yaml
textus:
  locale: en

cncf:
  diagnostics:
    enabled: true
```

## Operational State

`~/.cncf` also owns CNCF-internal and development-time operational files. The
set includes existing facilities such as:

- developer local CAR/SAR publication state;
- downloaded artifact cache;
- installed runtime state;
- launcher and runtime-internal configuration;
- local databases used by standalone or development operation;
- runtime logs and diagnostics; and
- other CNCF-managed files that should not be presented as ordinary Textus
  user configuration.

Exact subdirectory names, ownership, lifecycle, permissions, backup policy,
and deletion safety require an inventory before normative specification.
Configuration files and runtime-managed state must remain distinct even when
both live below `~/.cncf`.

## Configuration Provenance

The existing `org.goldenport.configuration.ResolvedConfiguration` and
`ConfigurationTrace` mechanism remains the base configuration-resolution and
provenance facility. User-profile or Phase 53 code must not introduce an
independent provenance model.

Every effective field needs traceable evidence for:

- HOME, PROJECT, CWD, environment, or argument scope;
- `.textus` or `.cncf` layer;
- physical source file or explicit input identity;
- logical configuration key;
- overridden history; and
- final effective source.

The current trace model already retains key, effective value, origin, and
history. Follow-up work must verify and complete file-source metadata so
`.textus` and `.cncf` sources can be distinguished reliably.

Operator diagnostics and `explain-config` may project a sanitized trace.
Secret or confidential values must not be exposed. A Component and its
`ExecutionContext` receive only resolved values and capabilities; they do not
receive configuration paths, layer names, raw `ResolvedConfiguration`, or
source history.

## Compatibility Impact

This decision changes the runtime-directory precedence introduced by the
2026-04-15 Textus-primary naming transition:

```text
previous: .cncf compatibility < .textus primary
selected: .textus public baseline < .cncf internal/development override
```

The product/developer naming boundary remains:

- `textus` is the normal user-facing name;
- `cncf` is the framework and developer-facing name.

Existing `cncf.*` aliases for already-published user-facing settings may still
need compatibility handling, but that alias policy is distinct from
`.textus`/`.cncf` directory precedence.

## Follow-up

Before implementation:

1. inventory every existing `~/.cncf` and `~/.textus` file and owner;
2. reconcile `docs/spec/config-resolution.md` and
   `docs/design/configuration-model.md` with the selected direction;
3. define source assembly ordering for HOME, PROJECT, and CWD;
4. retain `.textus`/`.cncf` source identity and complete history through
   `ConfigurationTrace`;
5. define secret-safe `explain-config` and operator diagnostics;
6. prove user-profile global/application overlays with exact provenance;
7. prove environment and explicit arguments remain higher precedence; and
8. migrate existing compatibility behavior without silently changing
   unrelated launcher, repository, cache, or runtime-state ownership.
