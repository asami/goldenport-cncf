---
title: ProtocolEngine Runtime Config Injection Proposal
---

# Current Core Capabilities

1. **`org.goldenport.protocol.ProtocolEngine`** (`../simplemodeling-lib/src/main/scala/org/goldenport/protocol/ProtocolEngine.scala`) wraps `ProtocolLogic` and exposes `makeOperationRequest(args: Array[String])`, `cliHelp`, etc. It simply delegates to `ProtocolLogic.makeOperationRequest`, so any injection point must either live in `ProtocolLogic` or in the `Request`/`ArgsIngress` it consumes.

2. **`org.goldenport.protocol.logic.ProtocolLogic.makeOperationRequest(args: Array[String])`** (`../simplemodeling-lib/src/main/scala/org/goldenport/protocol/logic/ProtocolLogic.scala`) orchestrates ingress selection (`protocol.argsIngress(args)`), encodes the request, and resolves the operation. It does not currently accept pre-populated properties or metadata; everything comes from the ingress result populated via `Ingress.parse_params`.

3. **`org.goldenport.protocol.handler.ingress.ArgsIngress.parse_params`** (`../simplemodeling-lib/src/main/scala/org/goldenport/protocol/handler/ingress/Ingress.scala`) is the workhorse that transforms CLI tokens into `Argument`, `Switch`, and `Property` instances according to the `ParameterDefinition` list provided by the runtime protocol. It tracks which keys have been consumed. There is no hook today to inject values before parsing, but this method already returns the property list that `RuntimeParameterParser` examines for `baseurl`.

4. **`Request` / `OperationRequest` (e.g., `../simplemodeling-lib/src/main/scala/org/goldenport/protocol/Request.scala`)** simply carry whatever `ArgsIngress` emits. They have no merging or precedence logic.


# Proposed Extension

To ensure CLI > Config > Default for runtime parameters while keeping parsing inside `ProtocolEngine`, add a lightweight property-injection hook that feeds `ArgsIngress` before it runs rather than modifying CNCF code to re-parse the arguments.

## API Shape

1. **`ProtocolEngine.makeOperationRequest(args: Array[String], initialProperties: Map[String, String])`** (new overload, default arguments keep old signature).
   - Defaults to the existing behavior when `initialProperties` is empty.
   - Internally converts `initialProperties` into a form `ArgsIngress` can consume (e.g., by creating a synthetic `Property` list or by prepending `--key=value` tokens before `argsIngress` is invoked).
   - Returns the same `Consequence[OperationRequest]`.
2. **`ProtocolLogic.makeOperationRequest(args: Array[String], initialProperties: Map[String, String])`** with identical semantics, forwarded by the new `ProtocolEngine` overload.
   - It can call a new helper `_encode_with_properties(op: OperationDefinition, args, initialProperties)` that merges the synthetic properties with what `parse_params` produces.

# Precedence Guarantees

- CLI tokens continue to dominate because they still arrive in the `args: Array[String]` and are parsed first.
- `initialProperties` represents the configuration-derived values (env/config defaults). They are only used when CLI does not already provide a value for that canonical parameter because the `ArgsIngress` parser would already create a `Property` or `Switch` for the CLI token, and merging logic can ignore duplicates by giving priority to actual CLI output.
- Since `MergePolicy` already enforces deterministic overwrites, CNCF need only ensure that when both CLI and `initialProperties` mention the same canonical name, the CLI version is used (e.g., by letting `initialProperties` populate the parser before `argsIngress` runs but then re-parsing CLI tokens after that).


# Alternatives Considered

1. **Prepending `--key=value` strings to the CLI array before calling the existing API** – doable but brittle because it requires CNCF to synthesize tokens manually, risk duplicating parsing logic, and still does not officially express the config-merge contract inside `ProtocolEngine`.
2. **Building a custom `Request` and bypassing `ProtocolEngine` entirely** – violates the goal of reusing `ProtocolEngine` and would require CNCF to duplicate resolver semantics, increasing maintenance burden.


# Recommendation

Add the two overloads (`ProtocolEngine.makeOperationRequest` and `ProtocolLogic.makeOperationRequest`) that accept `initialProperties: Map[String, String]`. The implementation can wrap or decorate `ArgsIngress.parse_params` so that CLI tokens override any pre-filled properties. This keeps the API backward-compatible (old callers continue calling the zero-arg version) and centralizes config injection in core, preserving existing precedence semantics. Tests/documentation can mention that `initialProperties` should contain CLI-defaulted runtime entries (e.g., `baseurl`) and that they will never appear in the residual argument vector because they are consumed before domain parsing.
