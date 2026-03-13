CNCF Parameter Resolution Model (Current, Mar 2026)
===================================================

# Scope

This note captures the currently implemented parameter resolution and output
format behavior in CNCF runtime.

It covers:

- configuration source composition
- mode-aware runtime defaults
- format resolution path (`json` / `yaml` / `text`)
- output rendering responsibility boundary

# Responsibility Boundary

The current split is:

- Application (`ActionCall`):
  - returns domain result (`OperationResponse.RecordResponse`, scalar, etc.)
  - does not branch by output format
- CNCF runtime/protocol layer:
  - resolves effective format
  - serializes final output at CLI/HTTP boundary

# Configuration Resolution

`CncfRuntime` resolves configuration via:

- `ConfigurationSources.standard(cwd, applicationname = "cncf", args = ...)`
- `ConfigurationResolver.default.resolve(...)`

Source merge order is defined by the resolver as:

    Resource -> Home -> Project -> Cwd -> Environment -> Arguments

Effective precedence is therefore:

    Arguments > Environment > Cwd > Project > Home > Resource

Configuration discovery for CNCF uses application name `cncf` (for example
`.cncf`-scoped files by the configuration source implementation).

# Runtime Defaults (Single Authority)

`RuntimeDefaults` is the single authority for per-mode defaults:

    command         : format=yaml, logBackend=nop,    logLevel=info
    server          : format=json, logBackend=stdout, logLevel=info
    client          : format=yaml, logBackend=nop,    logLevel=info
    server-emulator : format=json, logBackend=nop,    logLevel=info
    script          : format=yaml, logBackend=nop,    logLevel=info

# Format Resolution Chain

The implemented chain is:

1. Runtime options are parsed (`RuntimeOptionsParser.extract`):
   - `--format <value>` / `--format=<value>`
   - `--json` (normalized as `format=json`)
   - `--cncf.output.format ...` or `-cncf.output.format ...`
2. Request properties are built (`RuntimeOptionsParser.properties`):
   - `options.format`
   - else `json=true` -> `"json"`
   - else `RuntimeDefaults.defaultFormat(mode)`
3. Formatter resolves effective format (`OperationResponseFormatter`):
   - take last `Request.properties("format")` (case-insensitive key)
   - validate against `{json, yaml, text}`
   - invalid explicit value -> fallback `"yaml"`
   - missing value -> mode default

# RecordResponse Rendering

For `OperationResponse.RecordResponse(record)`:

- `json` -> `Response.Json(RecordEncoder.json(record))`
- `yaml` -> `Response.Yaml(RecordEncoder.yaml(record))`
- `text` -> `Response.Scalar(record.print)`
- invalid -> YAML fallback

Non-`RecordResponse` values preserve existing `OperationResponse.toResponse`
behavior.

# Rendering Application Points

The same formatter is applied from both runtime paths:

- `Subsystem.execute(Request)`
- `Service.invokeRequest(Request)`

This guarantees a single formatting policy regardless of call entry point.

# Compatibility Notes

- `--json` remains compatible and is treated as `format=json`.
- `cncf.output.format` remains compatible through runtime option extraction.
- If format is omitted, mode default from `RuntimeDefaults` is always injected.

# Validation Checklist

- `run command ...` -> YAML by default
- `run client ...` -> YAML by default
- `run server ...` -> JSON by default
- `run command --format json ...` -> JSON
- `run command --format text ...` -> text
- `run command --format invalid ...` -> YAML fallback
- same `ActionCall` implementation works across formats without app-side branching
