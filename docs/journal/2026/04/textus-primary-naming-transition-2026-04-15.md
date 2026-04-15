# Textus Primary Naming Transition

Date: 2026-04-15

## Context

The product-facing name is moving toward **CozyTextus**.

Historically, runtime options, local configuration directories, and some
system properties used the `cncf` name directly:

- `.cncf/config.*`
- `--cncf.config.file`
- `cncf.server.port`
- `cncf.http.baseurl`
- `CNCF_*` development launcher variables

This naming was acceptable while CNCF was the only visible framework identity,
but it is no longer the right user-facing default for CozyTextus.

## Decision

Use `textus` as the primary external spelling for runtime configuration and
development launcher integration.

Keep `cncf` as compatibility input where existing scripts, deployments, and
developer habits may still depend on it.

The intended rule is:

- `textus` is primary.
- `cncf` is compatibility.
- When both are supplied for the same semantic setting, `textus` wins.

## Applied Runtime Rules

Runtime configuration directories:

- `.textus/` is primary.
- `.cncf/` remains as compatibility fallback.
- Within the same HOME / PROJECT / CWD scope, `.cncf` is loaded first and
  `.textus` is loaded after it, so `.textus` overrides duplicate keys.

Explicit runtime configuration options:

- `--textus.config.file`
- `--textus.config.files`

are primary.

The older options:

- `--cncf.config.file`
- `--cncf.config.files`

remain supported as compatibility inputs.

System properties:

- `textus.config.file`
- `textus.server.port`
- `textus.http.baseurl`

are primary.

The older properties:

- `cncf.config.file`
- `cncf.server.port`
- `cncf.http.baseurl`

remain fallback inputs.

Standard runtime configuration files are evaluated in this order:

1. `config.conf`
2. `config.props`
3. `config.properties`
4. `config.json`
5. `config.yaml`
6. `config.xml`

`props` and `properties` are treated as `conf`-compatible formats.

## Sample Application Impact

`textus-sample-app` was updated to use:

- `.textus/config.yaml`
- `TEXTUS_*` launcher variables as primary names
- `target/textus.d/runtime-classpath.txt`
- `-Dtextus.server.port`
- `-Dtextus.http.baseurl`

Existing `CNCF_*` launcher variables are still accepted as fallback values in
the sample scripts.

## Documentation Updates

The following documents were updated to make the rule explicit:

- `docs/spec/config-resolution.md`
- `docs/design/configuration-model.md`
- `docs/design/global-protocol.md`

`config-resolution.md` is the normative place for source discovery,
precedence, standard file names, and compatibility behavior.

`configuration-model.md` records the broader design rule:
new documentation and examples should prefer `textus.*`, while `cncf.*` remains
a compatibility alias.

`global-protocol.md` now uses `.textus/config.yaml` in examples and describes
`.cncf/config.*` as compatibility input.

## Notes

Package names and class names under `org.goldenport.cncf` were not changed in
this step.

This work only changes user-facing runtime names and development launcher
defaults. Internal package and artifact naming can be evaluated separately when
the CozyTextus product packaging plan is ready.

