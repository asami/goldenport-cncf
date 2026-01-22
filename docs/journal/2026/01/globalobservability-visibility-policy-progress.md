---
title: GlobalObservability Visibility Policy Progress
---

# Current Implementation (Phase 2.85)

- Introduced `GlobalObservabilityGate` as a JVM-global entry gate that immediately forwards every event; it exists solely to centralize future flow-volume controls while `GlobalObservability` keeps attaching scope/package/class metadata and delegating to `ObservabilityEngine`.
- Added `VisibilityPolicy` (level, scope, package, class, backend placeholders) inside `ObservabilityEngine` with the default `AllowAll` instance.
- The engine now owns a `VisibilityPolicy` and exposes `shouldEmit(...)`; `GlobalObservability` consults `shouldEmit` before emitting, keeping the gate as a pass-through no-op.
- No filtering/filter policy is active yet—the gate and policy currently always allow, so there is no behavioral change.

# Intentionally Missing Pieces

- CLI/config wiring for `--log-level`, `--log-component`, `--log-package`, `--log-class`, and other log-related knobs (they remain TODO targets for the visibility policy).
- Runtime reconfiguration (e.g., `admin.observability.log`) is not implemented yet.
- The `VisibilityPolicy` `allows(...)` method ignores its fields and always returns `true`; future work will make this policy drive `shouldEmit`.

# Completion Condition

Phase 2.85 will be considered complete when runtime/administrative config (e.g., `admin.observability.log`) updates `ObservabilityEngine.visibilityPolicy`, and `shouldEmit` uses the policy’s scope/package/class/level hints to gate events. Until then, the gate/policy pair exists as a structural placeholder and is intentionally inert.
