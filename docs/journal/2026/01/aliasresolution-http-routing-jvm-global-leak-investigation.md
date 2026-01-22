# Alias Resolution HTTP Routing JVM-Global Leak Investigation

## Reproduction Summary (limited by tooling)
- `sbt test` *should* exercise `AliasResolutionSpec`. Previous runs hit a nondeterministic 404 when the alias-backed router failed to find `/ping`. In this environment `sbt test` cannot start at all because the launcher repeatedly fails to open `/Users/asami/.sbt/boot/sbt.boot.lock` (`Operation not permitted`).
- `sbt "testOnly *AliasResolutionSpec"` and `sbt "testOnly *OpenApiProjectorSpec"` also abort for the same file-lock permission issue, so the 404 cannot be observed directly here.
- Running the recently tagged manual/fork spec via `sbt "testOnly *LogBackendBootstrapReplaySpec"` likewise stops at the lock error, so no live comparison was possible.

## Routing Construction / Dispatch Chain
1. `AliasResolutionSpec` exercises `Subsystem.executeHttp(HttpRequest.fromPath(...))` (see `src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala` lines 53‑74).
2. `Subsystem.executeHttp` calls `_resolve_route`.
3. `_resolve_route` takes the incoming path segments and passes them into `PathPreNormalizer.rewriteSegments(segments, _http_run_mode, _alias_resolver)` before matching the component/service/operation tuple (see `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala` lines 167‑205).
4. `_alias_resolver` and `_http_run_mode` both read `GlobalRuntimeContext.current` directly, so the current alias table and run mode are whatever the last spec left in that global var.
5. When the routing tuple resolves, `Subsystem` dispatches to the component's HTTP egress and returns the response.

## Suspected JVM-Global Leak
- `GlobalRuntimeContext.current` is a single `var` shared by all specs. Several tests mutate it (e.g., `AliasResolutionSpec`, `AdminSystemPingResolverSpec`, `AdminSystemPingExecutionSpec`) without any synchronization, and they run in parallel under `sbt test`.
- Because `_alias_resolver` and `_http_run_mode` read that shared `var` every time a request is routed, a concurrent spec can overwrite the alias table or mode while `AliasResolutionSpec` is in the middle of an HTTP request. That would leave `_resolve_route` without the expected `/ping` alias, producing a 404 even though the alias config was created moments earlier.
- No reset hook protects `GlobalRuntimeContext.current` between specs (some tests restore the previous value, but when multiple specs run simultaneously the timing isn’t deterministic), so the leak is a race rather than a retained value.

## Fix Options (ranked)
1. **Capture alias/mode per `Subsystem` instance.** Pass the alias resolver and run mode into the `Subsystem` constructor (or capture them once during `DefaultSubsystemFactory` construction) so `_alias_resolver` and `_http_run_mode` no longer consult the shared `GlobalRuntimeContext.current`. Each subsystem would carry its own resolver, eliminating cross-test interference while keeping HTTP routing semantics identical. This is the recommended fix because it localizes the dependency, keeps routing deterministic regardless of test parallelism, and does not require locking or sequential execution.
2. **Force sequential execution for specs that rely on `GlobalRuntimeContext`.** Set `Test / test / parallelExecution := false` or annotate the affected specs to run in a single thread. This would stabilize the suite but only by foregoing parallelism and still leaves the global state hazard in place.
3. **Wall-off `GlobalRuntimeContext` with finer-grained guards (thread-local or concurrency-safe wrapper).** For example, use a `ThreadLocal` to hold the current context so each spec sees its own copy. This is potentially heavier and touches more infrastructure, so it ranks lower.

## Recommended Smallest Safe Fix
- Implement Option 1: have `Subsystem` (and the factory that builds it) capture the alias resolver and run mode once during construction instead of re-reading `GlobalRuntimeContext.current` per request. This change is localized to the subsystem creation path, preserves runtime semantics, and immediately removes the cross-spec race that produces intermittent 404s in the HTTP routing tests.

*Note: No code changes were made in this investigation; the repository state remains unchanged except for this journal note. Actual test verification is blocked by the sbt boot lock permission issue documented above.*

## Implementation Update
- `Subsystem` now stores the alias resolver and run mode provided at construction time, so `_resolve_route` uses these captured fields instead of looking up `GlobalRuntimeContext.current`. `DefaultSubsystemFactory` and the runtime bootstrap explicitly pass the resolver/mode they already build, which keeps routing deterministic per subsystem and isolates HTTP routing from any JVM-global alias/mode mutations that happen later.
