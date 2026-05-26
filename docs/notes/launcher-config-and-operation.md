# Launcher Configuration And Operation

## Purpose

This note defines the intended configuration and operation boundary for the
`cncf` and `textus` launchers.

The launchers are intentionally small entrypoints. They resolve a CNCF runtime,
prepare repository and classpath arguments, and invoke the CNCF runtime. They
must stay lightweight and must not depend on `simplemodeling-lib` or CNCF
runtime libraries for configuration parsing.

## Launcher Roles

`cncf` is the component-developer launcher.

It is used for:

- starting a local component development project;
- selecting a local CNCF runtime checkout;
- preparing runtime classpath files;
- checking component development directories;
- resolving local CAR/SAR dependencies during development.

`textus` is the component-user launcher.

It is used for:

- starting an application component by artifact name;
- selecting a published or locally published CAR/SAR;
- running user-facing server, client, and command modes;
- managing runtime selection from a user/operator perspective.

The two launchers intentionally duplicate a small amount of runtime selection
and repository logic. They are separately distributed entrypoints and should
remain independent unless the shared logic becomes large enough to justify a
dedicated launcher-core artifact.

## Launcher Config Files

Launcher config and CNCF runtime config are separate responsibilities.

`cncf` launcher config:

```text
~/.cncf/launcher.yaml
$PROJECT/.cncf/launcher.yaml
```

`textus` launcher config:

```text
~/.textus/config.yaml
$PROJECT/.textus/config.yaml
```

`cncf dev --project-dev <dir>` uses `<dir>/.cncf/launcher.yaml` as the project
launcher config. This keeps the developer launcher settings close to the
development project without reusing runtime configuration files.

The `textus` user-facing launcher intentionally uses `.textus/config.yaml` as
its standard config location. CNCF runtime configuration may also read
`.textus/config.*` later through the runtime configuration resolver. When the
same file location is involved, the boundary is still phase-based: launcher
config is parsed by the launcher before runtime selection, while runtime config
is resolved by CNCF after startup.

Explicit launcher config files are supplied with:

```text
--config <file>
--launcher-config <file>
```

The explicit files are merged after the standard home/project launcher config
files and therefore have higher precedence than standard launcher config.

## Supported Launcher Config Formats

Launcher config supports only lightweight formats:

- YAML / YML;
- Java properties;
- props;
- lightweight conf using dotted `key = value` or `key: value` entries.

The launcher does not parse JSON, XML, or full HOCON. Those formats belong to
the CNCF runtime configuration layer, where richer configuration dependencies
are acceptable.

This restriction is intentional. The launcher must be able to run before CNCF
runtime libraries are loaded.

## CNCF Runtime Config Files

CNCF runtime config is read by the runtime after the launcher has selected and
started it. Runtime config remains governed by `docs/spec/config-resolution.md`.

Standard runtime config may use `.textus/config.*` and compatibility
`.cncf/config.*` according to runtime config resolution rules.

For the `cncf` developer launcher, explicit runtime config is forwarded with:

```text
--cncf-config <file>
```

The launcher forwards this to the runtime as:

```text
--cncf.config.files=<file>
```

This is separate from launcher `--config`.

## Recommended Development Layout

For a development application such as `textus-knowledge-editor`, use:

```text
.cncf/launcher.yaml          shared developer launcher settings
.cncf/config.yaml            shared CNCF runtime settings
etc/launcher/debug.yaml      local launcher debug override
etc/debug.yaml               local or project runtime debug settings
```

`etc/launcher/debug.yaml` commonly contains a local CNCF runtime checkout:

```yaml
runtime:
  dev-dir: /path/to/cloud-native-component-framework
cncf:
  config:
    file: etc/debug.yaml
```

`etc/debug.yaml` contains runtime configuration passed to CNCF. It may use the
runtime configuration formats and naming rules.

Project teams may keep local debug files out of Git and commit example files
such as `etc/launcher/debug.yaml.example`.

## Repository Operation

`~/.cncf/local` is developer-owned local publish state.

It is populated by:

```text
sbt cozyPublishLocalCar
sbt cozyPublishLocalSar
```

`~/.cncf/cache` is runtime-managed remote artifact cache.

Snapshot CAR/SAR artifacts are local-only by default. A missing snapshot should
fail with an instruction to publish locally, not fall through to cache or public
repositories. Release artifacts may resolve through explicit repositories,
local, cache, and public repositories according to launcher policy.

## Target Operation

`cncf dev server` defaults to the current directory as a project development
target:

```text
cncf dev server
cncf dev server --project-dev <dir>
```

Project-dev targets are local source projects. They are not looked up from
CAR/SAR repositories.

Packaged targets are explicit:

```text
cncf dev server --name artifact[:version]
cncf dev server --car-file path/to/component.car
cncf dev server --project-car path/to/project
```

`textus server artifact[:version]` is the normal user-facing packaged artifact
startup route.
