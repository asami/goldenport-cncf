---
title: Phase 2.85 GlobalProtocol Implementation Proposal
---

# Background

`docs/design/global-protocol.md` already prescribes the two-stage runtime/domain split, the canonical glossary of runtime parameters, and the rule that runtime-scoped options such as `--baseurl` must never reach the Domain Protocol. The journal entry `docs/journal/2026/01/runtime-protocol-leak-and-closure.md` records the concrete Phase 2.8 / 2.85 incident where that leakage happened and why a GlobalProtocol foundation is required to close the gap. This proposal builds on those references to show how CNCF can reuse the core simplemodeling components instead of re-inventing parsing logic while delivering the required stage boundary, especially for the CLI client surface.

# Core inventory (`../simplemodeling-lib`)
- **`org.goldenport.protocol.Protocol`** (`../simplemodeling-lib/src/main/scala/org/goldenport/protocol/Protocol.scala`): the canonical service+handler registry that exposes `argsIngress`, `ingressOf`, `enproject`, and `egress`. Use it to declare the Domain `ServiceDefinitionGroup` plus the ingress/egress handlers that already understand switches/properties/arguments via `ParameterDefinition` metadata.
- **`org.goldenport.protocol.ProtocolEngine`** (`../simplemodeling-lib/src/main/scala/org/goldenport/protocol/ProtocolEngine.scala`): wraps `ProtocolLogic` and exposes `makeOperationRequest`, `cliHelp`, `openApi`, and general projection access. CNCF can delegate Stage 2 execution to this engine once the residual argv is ready.
- **`org.goldenport.protocol.logic.ProtocolLogic`** (`../simplemodeling-lib/src/main/scala/org/goldenport/protocol/logic/ProtocolLogic.scala`): handles ingress selection, request validation, and service/operation resolution. It consumes a prebuilt `Request` (with arguments/switches/properties) and resolves it against the `Protocol` metadata, returning `OperationRequest`. CNCF should reuse it for both CLI and script surfaces instead of re-implementing resolver logic.
- **`org.goldenport.protocol.handler.ingress.Ingress`** (`../simplemodeling-lib/src/main/scala/org/goldenport/protocol/handler/ingress/Ingress.scala`): shared base for argument parsing. It can parse switches/properties/arguments according to `ParameterDefinition` maps, resolve defaults, and return the `ParsedArgs` tuple. CNCF’s GlobalProtocol parsing stage can rely on this class to keep option handling consistent with other ingress consumers.
- **`org.goldenport.protocol.spec.ParameterDefinition`** (`../simplemodeling-lib/src/main/scala/org/goldenport/protocol/spec/ParameterDefinition.scala`): enumerates argument/switch/property kinds plus ValueDomain-driven metadata. The runtime-scoped catalog (log-level, baseurl, http-driver, etc.) can be declared with `ParameterDefinition` instances, reusing existing domain-type infrastructure for defaulting/aliasing.
- **`org.goldenport.cli.parser.ArgsParser`** (`../simplemodeling-lib/src/main/scala/org/goldenport/cli/parser/ArgsParser.scala`): simple scanner for `--foo=bar`, `--foo` switches, and positional arguments. Acts as a reference implementation of the argument shape that the `Ingress` handler expects; CNCF can adapt its GlobalProtocol constants to feed the same style of data into `ProtocolLogic`.
- **`org.goldenport.protocol.Request` / `OperationRequest` / `Switch` / `Property` classes**: represent the results of CLI parsing. CNCF’s parser should produce these same types so the remainder of the stack (resolver + `Component.execute`) can operate unchanged.

# Gap analysis (CNCF-specific needs)
- `CncfRuntime` (current CLI entry) mixes runtime option parsing, configuration loading, and domain selector resolution in a single flow. There is no declarative catalog of runtime parameters, so `--baseurl` and similar switches slip into the domain argument list (see the existing `--baseurl` parsing work we already added). This violates the GlobalProtocol rule that Stage 1 must consume runtime options and Stage 2 only sees selectors.
- Runtime context defaults (`ClientConfig`, `RuntimeConfig`, sys.env) are scattered; there is no single helper that applies the CLI > config > env > default precedence chain defined in `global-protocol.md`. Without such normalization, different surfaces compute different base URLs and other runtime values.
- There is no CNCF-side artifact that declares which switches belong to Runtime vs Domain scope; that catalog must live somewhere so both CLI parsing and future configuration bindings refer to the same source of truth.
- The plan must avoid duplicating resolver logic already expressed in `ProtocolLogic` and the `OperationResolver` in CNCF; the GlobalProtocol parser should hand residual args to the existing resolver rather than reimplementing path parsing.

# Proposed CNCF implementation structure
1. **`org.goldenport.cncf.protocol.GlobalProtocolCatalog` (new file under `src/main/scala/org/goldenport/cncf/protocol/`)**  
   - Defines a `Vector[ParameterDefinition]` covering the runtime-scoped parameters documented in `global-protocol.md` (log_level, log_backend, baseurl, http_driver, env, etc.).  
   - Declares whether each parameter is consumed or forwarded, the config key (e.g. `runtime.log.level`), and default values (including mode-dependent defaults for baseurl).  
   - Provides helper methods to build `Map[String, ParameterDefinition]` for switches/properties so the parser knows which tokens to consume in Stage 1.  
   - Implements a `preprocessCliArgs(args: Seq[String]): Consequence[(Vector[String], Seq[String])]` that returns `(consumedRuntimeArgs, residualDomainArgs)` for instrumentation or debugging (trace logs).  

2. **`org.goldenport.cncf.cli.GlobalProtocolParser` (new file under `src/main/scala/org/goldenport/cncf/cli/`)**  
   - Uses the catalog to parse CLI switches by delegating to `org.goldenport.protocol.handler.ingress.Ingress` (or a thin wrapper that enforces runtime-only parameters).  
   - Resolves CLI options (using `ClientConfig`/`RuntimeConfig` plus `sys.env`) with a single helper `resolveRuntimeValue(cli: Option[String], config: Option[String], env: Option[String], default: String)` so every runtime field follows CLI > Config > Env > Default precedence.  
   - Produces a `GlobalRuntimeContext` payload (run mode, log-level, log-backend, resolved baseurl, http-driver choice, alias resolver) and the residual `Array[String]` for Stage 2.  
   - Emits `[client:parse]`/`[runtime:trace]` logs similar to the instrumentation already added, ensuring options can be audited without leaking into residual arguments.  

3. **`CncfRuntime` integration**  
   - Replace the manual `run`/`runWithExtraComponents` option handling with the global parser results. The Stage 1 result feeds `GlobalRuntimeContext` and `GlobalProtocolCatalog` ensures domain args no longer include runtime switches.  
   - Pass residual arguments into the existing `ProtocolEngine`/`ProtocolLogic` pipeline (either directly via `GlobalProtocolParser` or by building a `Request` that is handed to `ProtocolEngine.makeOperationRequest`).  
   - Keep the `Component.ts` discovery/resolver logic untouched; only the argument feed changes.  

4. **Optional helpers**  
   - A small `GlobalProtocolContextBinder` (maybe under `context/`) that takes the parsed values and wires them into `GlobalRuntimeContext` so the rest of the runtime sees a unified picture.  
   - Light configuration loader hooking the catalog to `.cncf/config.yaml` entries (Phase 2.85 can stub the loader with the existing `RuntimeConfig`/`ClientConfig` methods, deferring full schema validation until Phase 3).  

# `--baseurl` leakage fix plan
- Baseurl must be declared as a runtime-scoped parameter in `GlobalProtocolCatalog` with default “`http://localhost:8080`” for the client mode and the Cncf-wide default for others.  
- `GlobalProtocolParser` consumes `--baseurl` in Stage 1, resolves CLI/config/env precedence, emits trace logs (`[client:parse] resolvedBaseUrl=… (cli=… config=… env=…)`), and stores it in `GlobalRuntimeContext`.  
- Residual domain args forwarded to Stage 2 contain only selectors (`component.service.operation` + positional args), so the existing `parseCommandArgs`/`OperationResolver` pipeline never sees `--baseurl`.  
- Client URLs are built after Stage 1 using the context-provided baseurl, preventing the CLI from writing the base URL into the path tokens. The `GlobalProtocolParser` should therefore drive `CncfRuntime.prepareClientRequest`, not the current inline `--baseurl` parsing logic.  
- This plan follows the Uniform Parameter rule from `global-protocol.md` and closes the incident logged in `runtime-protocol-leak-and-closure.md` by ensuring runtime options cannot reappear after Stage 1.

# Next steps
1. Create `GlobalProtocolCatalog` and `GlobalProtocolParser` skeletons that reference the core simplemodeling types described above.  
2. Wire `CncfRuntime` (both `run` and `runWithExtraComponents`) to consume the parser output before dispatching to `ProtocolEngine`.  
3. Add trace instrumentation showing the split between runtime arguments, the resolved `GlobalRuntimeContext`, and the residual domain arguments for auditing/demo purposes.  
4. Extend the catalog to cover additional runtime parameters as Phase 2.85 progresses (e.g. `--env`, `--http-driver`), keeping the CLI/domain split intact.  
5. After the implementation compiles, revisit `Phase 2.85` journal notes to document the fix and retire the `runtime-protocol-leak-and-closure` action items.
