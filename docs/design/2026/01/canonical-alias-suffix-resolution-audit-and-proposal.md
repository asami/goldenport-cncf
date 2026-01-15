# Canonical/Alias Resolution Audit & Phase 2.8 Proposal

## Audit Findings

### CLI surface (`src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala`)
- `run`/`runWithExtraComponents` and `_to_request`/`parseCommandArgs` rely on `Request.parseArgs(RequestDefinition(), actualargs)`, then treat the first argument as the operation name and route it via `RunMode`, never applying any canonical-selector logic from `docs/design/canonical-alias-suffix-resolution.md`.
- `parseCommandArgs` + `_parse_component_service_operation(_string)` only support either three separate tokens (`component service operation`) or a single dot/slash-delimited string; all resolution is one-off string splitting, not table-driven, and this logic is duplicated between the normal CLI flow and the script helper `_to_request_script`.
- Errors in this flow are simple `Consequence.failure("command ...")` messages; there is no differentiation between missing component/service/operation stages, no candidate listing, and no handling for >2 dots or alias/prefix/implicit rules.

### Script surface (`src/main/scala/org/goldenport/cncf/dsl/script/ScriptRuntime.scala`)
- `ScriptRuntime.execute` always runs through `CncfRuntime.executeScript`, which calls `_to_request_script`; when the first three tokens are not `SCRIPT DEFAULT RUN`, `_to_request_script` prepends those tokens before invoking `parseCommandArgs`, effectively hard-coding the script component/service/operation. This behaviour provides the implicit rule only within this helper and is not shared elsewhere.
- There is no resolver consulted when only user arguments are supplied; `_to_request_script` acts as a fallback, not a canonical selector evaluator.

### HTTP surface (`src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`)
- `_resolve_route(req: HttpRequest)` and `_resolve_route(request: Request)` match literal path segments or request fields to `ComponentSpace` entries (`ComponentLocator.NameLocator`) and directly look up services/operations via `component.protocol.services.services.find(_.name == ...)`. There is no shared table, no alias/prefix normalization, and no dot-count classification.
- `_resolve_spec_route` has its own ad-hoc mapping for `spec`/`openapi` paths, further splitting handling from the canonical path resolver.
- Errors surface as `None`, leading to either `_not_found()` (HTTP 404) or `Consequence.failure("Operation route not found")`; neither encodes which stage failed nor offers candidate lists for ambiguous selectors.

### Shared infrastructure
- `ComponentSpace` (`ComponentSpace.scala`) only indexes components by their literal `name`; there is no concept of alias metadata, no resolution cache/table, and each lookup traverses the component list anew.
- Alias/prefix handling is not implemented anywhere.
- Implicit selector support exists only inside `ScriptRuntime` and is tightly coupled to the `SCRIPT DEFAULT RUN` tokens; there is no general implicit rule applied across surfaces.

### Duplications & Diverging behaviour
- CLI parsing, script helper, and HTTP route resolution all perform their own split/match logic; there is no common resolver and no shared error model (not-found vs ambiguous are both expressed as generic failures).
- Each surface decides independently how to handle selectors with dots (e.g. CLI treats `component.service.operation` specially, HTTP requires exactly three path segments) and there is no validation for selectors with 3+ dots or invalid characters.
- `Request.parseArgs` is the only place where `operation` is extracted, but it is ignorant of components/services and cannot feed a shared resolver.

## Central Resolver Proposal

### Placement
Introduce `org.goldenport.cncf.subsystem.resolver.OperationResolver` (or similar) as a collaborator owned by `Subsystem`. The subsystem already owns `ComponentSpace` and is the entry point for CLI, script, and HTTP surfaces, so it should create and expose the resolver when components are added. The resolver should rebuild its resolution table whenever `Subsystem.add` changes the component set so that the table is always authoritative.

### Resolution table
- Build the table at startup (and whenever components change) by walking `ComponentSpace.components`, each `component.protocol.services.services`, and each service’s `operations.operations`. Each entry records the canonical FQN `component.service.operation`, the set of declared aliases at every level, and metadata such as `ComponentOrigin` (for identifying builtin vs non-builtin) and optional prefix indexes if prefix-matching is enabled.
- The table must be immutable once published to callers; per the spec, no runtime lookup/fallback should bypass the table. Any new surface-specific resolution should consult this table only.

### API
```
sealed trait ResolutionResult
a
object ResolutionResult {
  final case class Resolved(fqn: String) extends ResolutionResult
  object NotFound { ... }
  final case class NotFound(stage: ResolutionStage, tried: Vector[String]) extends ResolutionResult
  final case class Ambiguous(candidates: Vector[String]) extends ResolutionResult
  final case class InvalidSelector(reason: String) extends ResolutionResult
}

enum ResolutionStage { Component, Service, Operation }

class OperationResolver(private val table: ResolutionTable) {
  def resolve(selector: String, allowPrefix: Boolean, allowImplicit: Boolean): ResolutionResult = ???
}
```
- `selector` is the raw first non-option argument (no suffix stripping). `allowPrefix` controls whether prefix matches are considered (must still follow "unique candidate" rule). `allowImplicit` enables the minimal-system default; selectors containing dots must pass `allowImplicit = false` (and the resolver should treat any dot as explicit intent).
- Validate the selector before lookup: classify dot counts (0,1,2) and return `InvalidSelector` for >=3 dots. Within each dot count case, the resolver should restrict candidate sets step-by-step (operation-only, service+operation, component+service+operation) and return `NotFound`/`Ambiguous` with stage information and FQN candidates. If alias matches expand the candidate set, they must follow the same ambiguity rules.

### Integration
- `Subsystem` will hold the resolver instance and expose helpers such as `resolve(selector, allowPrefix, allowImplicit)` for surfaces.
- **CLI** (`CncfRuntime`): after parsing options, hand the selector string to `subsystem.resolve`. If the resolver returns `Resolved(fqn)`, split the FQN and build the `Request` (component/service/operation). If `NotFound`/`Ambiguous`/`InvalidSelector`, surface the failure (with stage specificity and candidate list) instead of running `Request.parseArgs`. Suffix parsing might happen before calling the resolver (Phase 2.8 is only about the name portion). CLI should also respect `allowImplicit` when no selector is provided (empty args) but the implicit rule applies.
- **Script** (`ScriptRuntime`): convert script args into a selector (possibly empty) and call the resolver with `allowImplicit = true` when the minimal-system rule can kick in; otherwise require explicit selectors. This global resolver removes the need for `_to_request_script` to prepend `SCRIPT DEFAULT RUN` and instead allows a single helper to inspect the `ResolutionResult`.
- **HTTP** (`Subsystem.executeHttp`): derive a selector string from the path (concatenating the three segments) and call the resolver with `allowPrefix = false` (HTTP should require explicit names, unless a future flag allows prefix matching). Use the resolver result to look up the exact `Component/Service/Operation` objects. Existing `_resolve_spec_route` can remain for the special `spec` paths, but `OperationResolver` should also include `spec.exports.openapi` entries so that these flows can reuse the same table if desired.
- `OperationResolver` should not parse suffixes such as `.json`; suffix handling must stay in its own phase.

## Test Plan Outline (Executable Specifications)

### Fixtures
Define a minimal test component registry with at least:
1. One builtin component (e.g. `builtin.admin`) to ensure implicit rule excludes it.
2. One or two user components (e.g. `foo` and `bar`) with services/operations: each service/operation should declare canonical names plus aliases (and at least one operation with alias). Implement a small helper that wraps a `Component` with `ComponentOrigin.Repository`/`Main` to ensure implicit rules behave.
3. Optionally a service with multiple operations that share prefixes to exercise prefix rules.

### Spec sections (map directly to `docs/design/canonical-alias-suffix-resolution.md`)
1. **0-dot selectors**: Given a selector with no dot, When the resolver is asked with `allowPrefix` true/false, Then the resolver should return:
   - `Resolved` when exactly one matching operation exists (by canonical name or alias).
   - `NotFound(operation, tried = ...)` when nothing matches.
   - `Ambiguous` when multiple operations match (and include all candidate FQNs).
   - `Resolved` for a unique prefix (only allowed when the prefix result is singular).
   - `Ambiguous` when prefix matching yields >1 candidate even with `allowPrefix = true`.
2. **1-dot selectors**: service + operation. Given selectors such as `service.op` and alias equivalents, verify outcomes:
   - `NotFound(stage = Service, tried = ...)` when the service is missing.
   - `NotFound(stage = Operation, tried = ...)` when the operation is missing inside a matched service.
   - `Ambiguous` if the service alias resolves to multiple services.
3. **2-dot selectors**: component + service + operation must return `Resolved` only when the full FQN is unambiguous, `NotFound` when any sub-stage is missing, or `InvalidSelector` if there are >2 dots.
4. **Implicit default**: configure the registry so exactly one non-builtin component contains a single service and operation. When no selector is provided and `allowImplicit = true`, the resolver should `Resolved` that sole operation. Add a test showing a selector with a dot disables implicit behavior (should go through the normal resolution path).
5. **Alias uniformity**: Confirm aliases defined at component/service/operation levels are treated the same way and obey ambiguity rules. (These specs can use property-based checks: random alias duplication should lead to `Ambiguous`, unique alias to `Resolved`.)

Each spec should be written as an Executable Specification (Given/When/Then) and leverage ScalaCheck to vary selectors (prefix vs full names) while asserting deterministic results. Candidate lists in `Ambiguous` should be compared against the expected canonical FQNs.

## Risks / Phase 2.8 Follow-ups
- **Component set changes**: Subsystem allows extra components to be added after construction (`add`). The resolver must be recomputed when new components arrive; failure to rebuild would yield stale tables. Consider making `OperationResolver` immutable and replacing it whenever `add` runs.
- **Alias metadata**: The repo currently lacks a standard place to declare aliases. We must decide whether aliases live in `ServiceDefinition`/`OperationDefinition` or in a parallel registry (maybe via annotations or configuration) before the resolver can include them.
- **Implicit rule scope**: The implicit selector rule currently only applies to scripts. Expanding it requires care around CLI default execution and respecting dot presence. We must document how callers toggle `allowImplicit`.
- **Error reporting surfaces**: Presently, CLI and HTTP surfaces swallow stage detail. Integrating the resolver will require plumbing new error messages and exit codes (e.g. `ambiguous` should list FQNs). We should design how these messages propagate to users and ensure existing tests/specs are updated.
- **Suffix handling remains deferred**: The resolver explicitly omits suffix parsing. A later Phase 2.8 task must strip suffixes before calling the resolver and reconcile representation selection with canonical name errors.
