---
title: RuntimeProtocol Config Integration Plan
---

# Core Config Inventory

- **`org.goldenport.configuration.Configuration` / `ConfigurationValue`** (`../simplemodeling-lib/src/main/scala/org/goldenport/configuration/Configuration.scala`, `ConfigurationValue.scala`): Boring carrier for resolved key/value pairs with typed accessors. CNCF can treat this as the post-merge source of truth for runtime options.
- **`org.goldenport.configuration.ResolvedConfiguration`** (`.../ResolvedConfiguration.scala`): Couples the final configuration with `ConfigurationTrace` and exposes `get[T](key)` via `ValueReader`). It is the object returned by the resolver and what RuntimeProtocol should query for defaults.
- **`org.goldenport.configuration.ConfigurationResolver` / `DefaultConfigurationResolver`** (`.../ConfigurationResolver.scala`): Central resolution engine that orders `ConfigurationSource`s (Resource, Home, Project, Cwd, Environment, Arguments), merges via `MergePolicy`, and emits `ResolvedConfiguration`. Its documented ordering already matches the CLI-precedence requirement (arguments win last).
- **`org.goldenport.configuration.MergePolicy`** (`.../MergePolicy.scala`): Key-based overwrite + trace updates used by the resolver. No semantics beyond deterministic merge, so CNCF can reuse it verbatim when combining new sources.
- **`org.goldenport.configuration.ConfigurationSources.standard`** (`.../source/ConfigurationSources.scala`): Factory that produces the exact source list used by the resolver, including `ConfigurationSource.env` (sys.env) and `ConfigurationSource.args` (user CLI map). These sources include origin metadata that can later explain where a runtime value came from via `ConfigurationTrace`.
- **`org.goldenport.configuration.source.ConfigurationSource` variants** (`.../source/ConfigurationSource.scala`): 
  - `File` / `ResourceConfigurationSource` for discovered files
  - `Env` for environment variables
  - `Args` for CLI-derived key/value pairs
  CNCF only needs the `Env` and `Args` parts for runtime parameter resolution, but existing infrastructure already supports file-based overrides if Stage 2 needs them later.
- **`org.goldenport.configuration.ConfigurationTrace`** (`.../trace/ConfigurationTrace.scala`): Optional trace structure that records the origin and merge history for each key. Could be surfaced via logs for GlobalProtocol instrumentation if needed.

# Mapping to RuntimeProtocol Inputs

1. **Runtime parameter vocabulary** (`GlobalParameterGroup.runtimeParameters`) remains authoritative: each `ParameterDefinition` describes the canonical name, aliases, kind (property/switch) and multiplicity of runtime options (baseurl, log_backend, etc.).
2. **Resolved configuration** provides values keyed by string (e.g., `runtime.http.baseurl`). CNCF should map those keys to the runtime parameter names declared in `GlobalParameterGroup` so the values can seed Stage 1.
3. **Precedence guarantee**: `ConfigurationResolver.default.resolve(ConfigurationSources.standard(..., args = cliMap, env = sys.env))` already applies the ordering Resource→...→Environment→Arguments. Because CLI-derived values populate the `Args` source last, they naturally win over earlier config layers. CNCF therefore only needs to ensure config-to-runtime mapping occurs before CLI overrides are merged.
4. **RuntimeParameterParser** can consume both the CLI `args: Seq[String]` and the `ResolvedConfiguration` by:
   - Building the `args` map for `ConfigurationSource.Args` from the CLI tokens that concern runtime parameters.
   - Querying `ResolvedConfiguration.get[String](runtimeKey)` for every catalog entry to fill in defaults before Stage 1 runs.
   - Feeding both CLI tokens and config-derived tokens to `ProtocolEngine` in field order that respects CLI precedence (i.e., config tokens only appear when CLI values for the same canonical name are absent).

# Integration Flow Diagram (textual)

1. **Input collection**
   - CLI `args` arrive at `CncfRuntime` (e.g., `cncf client --baseurl ...`).
   - Compose `Map[String, String]` of runtime-parameter-looking key/values (use `GlobalParameterGroup.runtimeNameMap` to recognize relevant options).
2. **Configuration resolution**
   - Call `ConfigurationResolver.default.resolve(ConfigurationSources.standard(cwd, cliMap, sys.env))`.
   - Receive `ResolvedConfiguration` containing merged runtime values plus `ConfigurationTrace` if Cinemed logging is needed.
3. **Stage 1 argument materialization**
   - For each `ParameterDefinition` in `GlobalParameterGroup.runtimeParameters`:
     a. Check CLI tokens first and record `--name value` or switch presence (maintains existing `RuntimeParameterParser` behavior).
     b. If CLI lacks the parameter, query `ResolvedConfiguration.get[T](configKey)` using the value returned in step 2. Translate that value back into the same switch/property format.
     c. Fall back to the parameter’s default (defined by `ParameterDefinition` domain) only if neither CLI nor config provided a value.
   - Build the `Array[String]` fed to `_runtime_protocol_engine` such that every runtime parameter is consumed up front; unrecognized CLI tokens continue to flow to Stage 2 unchanged.
4. **ProtocolEngine dispatch**
   - Run Stage 1 (`ProtocolEngine.makeOperationRequest`) using the service/operation we already create inside `RuntimeParameterParser`.
   - Capture the `Request` to extract consumed tokens, the resolved baseUrl (from `Properties`), and the `residual` arguments (domain selectors). Because runtime parameters are defined up front, config-derived values never appear in the residual list.

# Impact on the Existing RuntimeParameterParser

- Extend `RuntimeParameterParser.parse` to accept the `ResolvedConfiguration` while keeping its log trace and result type unchanged. The parser already delegates to `ProtocolEngine`, so it merely needs a short pre-processing step that injects config-derived CLI tokens before it calls `makeOperationRequest`.
- The parser should remain responsible solely for stage 1 parsing; domain resolution still happens in `CncfRuntime.run`/`runWithExtraComponents`. No new data models are required because `Configuration` values can simply be stringified before being reinserted into the CLI token stream.
- `GlobalParameterGroup` continues to declare canonical names, so the parser can reuse `runtimeNameMap` to recognize both CLI and config values without inventing new enums.
- Consider optionally emitting `ConfigurationTrace` metadata to `_log.trace` when the trace indicates a config (non-CLI) value was used, reinforcing the `--baseurl` instrumentation already present.

# Minimal CNCF-side Additions

1. **RuntimeParameterParser input reshaping**: add a helper (or companion object) that, given `Seq[String]` and `ResolvedConfiguration`, produces the final CLI array for Stage 1. No new config loader is needed because CNCF reuses `ConfigurationResolver.default` and `ConfigurationSources.standard`.
2. **Mapping table**: a lookup from runtime config keys (e.g., `runtime.http.baseurl`) to the canonical parameter name (e.g., `baseurl`). This table can derive from `GlobalParameterGroup.runtimeNameMap` plus a static map to config key strings.
3. **Logging hook**: optionally surface `ResolvedConfiguration.trace` entries when `_log.trace` is active to show whether a value came from CLI vs config vs default; this stays within RuntimeParameterParser and does not touch domain code.

# Next-step Implementation Plan

1. Add a lightweight mapper (probably near `GlobalParameterGroup`) that correlates each runtime `ParameterDefinition` with its expected configuration key (e.g., `runtime.http.baseurl`), defaulting to `canonical` names when a config key is absent.
2. Extend `RuntimeParameterParser.parse` to accept a `ResolvedConfiguration`, collect CLI-only runtime tokens, and prepend config tokens for missing entries before calling `ProtocolEngine`.
3. Update `CncfRuntime` to resolve configuration before calling the parser (reuse `_resolve_configuration` → `ConfigurationResolver.default.resolve(ConfigurationSources.standard(cwd, cliMap, sys.env))` and pass the result in).
4. Verify that `RuntimeParameterParseResult.residual` never includes runtime parameter tokens by adding logging/assertions if desired. Ensure `baseUrl` still derives from the `Request` properties so Stage 2 remains untouched.
5. Document the new flow in `docs/journal/2026/01/runtimeprotocol-config-integration.md` (this file) and update any trace logs (e.g., `[client:parse]`) to mention config sources when relevant.

Ensuring Stage 2 purity: because all config-supplied runtime tokens are consumed before `RuntimeParameterParseResult.residual` is built, the domain resolver never sees them—the same mechanism already enforced for CLI tokens continues to apply for config values.
