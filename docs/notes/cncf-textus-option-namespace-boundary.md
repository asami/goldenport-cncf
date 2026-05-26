# CNCF And Textus Option Namespace Boundary

## Purpose

This note defines how `cncf.*` and `textus.*` names are used across launchers
and runtime configuration.

The short rule is:

- `textus` is the product/user-facing namespace.
- `cncf` is the component-developer and framework-internal namespace.

## Product And Runtime Naming

The product-facing name is Textus. User/operator-facing runtime configuration
therefore uses `textus.*` as the primary spelling.

Examples:

```text
textus.datastore.sqlite.path
textus.web.descriptor
textus.knowledge.rdf.current-prefix
```

Where historical `cncf.*` runtime keys exist, they may remain compatibility
aliases. New user-facing runtime documentation should prefer `textus.*`.

The runtime configuration resolver treats `.textus` as the primary directory
and `.cncf` as compatibility input when both names are part of the runtime
configuration search.

## Developer Launcher Naming

The `cncf` command is a component-developer launcher. Its CLI options may use
`cncf` names when the option is specifically about controlling CNCF developer
startup or forwarding CNCF runtime configuration.

Examples:

```text
cncf --config etc/launcher/debug.yaml dev server
cncf --cncf-config etc/debug.yaml dev server
cncf dev server --project-dev .
cncf dev server --runtime-dev-dir ../cloud-native-component-framework
```

In this context:

- `--config` means launcher config;
- `--cncf-config` means a CNCF runtime config file to forward to the runtime;
- `--project-dev` means a local component development project;
- `--runtime-dev-dir` means a local CNCF runtime checkout.

Using `cncf` here is not a product-facing runtime naming decision. It reflects
that the command is for component developers and is controlling the CNCF
runtime startup boundary.

## User Launcher Naming

The `textus` command is the component-user launcher. Its configuration and help
should use Textus-oriented names.

Examples:

```text
textus --config etc/launcher.yaml server textus-blog
textus runtime current
textus runtime config show
```

`textus --config` is a Textus launcher config file. It is not the same thing as
CNCF runtime `--cncf.config.files`.

If a user-facing runtime config forwarding option is added to `textus` later,
it should use the `textus` spelling unless there is a precise reason to expose
the lower-level CNCF name.

## File Responsibility

Use this responsibility split:

```text
.cozy/config.yaml            build and publish defaults
.cncf/launcher.yaml          cncf developer launcher defaults
.textus/config.yaml          textus launcher and product runtime defaults
.cncf/config.yaml            CNCF runtime compatibility/project config
project.yaml                 artifact metadata and runtime compatibility
```

For development debug layouts, split launcher and runtime concerns:

```text
etc/launcher/debug.yaml      launcher debug override
etc/debug.yaml               runtime debug config
```

The launcher debug file may point at the runtime debug file. The runtime debug
file should not be parsed by the launcher.

## Format Boundary

Launcher config supports only lightweight config formats:

- YAML / YML;
- properties / props;
- lightweight conf.

Runtime config may support richer formats such as JSON, XML, and full HOCON
through the runtime configuration stack.

Do not add a runtime configuration dependency to the launchers just to parse a
convenience format. The launchers must remain bootstrap tools.

## Collision Policy

Avoid defining both `cncf.*` and `textus.*` names for a new semantic setting
unless there is an explicit compatibility need.

When the same runtime semantic value has both names:

- `textus.*` is primary for product-facing runtime configuration;
- `cncf.*` is compatibility or developer-internal;
- deterministic precedence must be documented at the boundary that reads the
  values.
