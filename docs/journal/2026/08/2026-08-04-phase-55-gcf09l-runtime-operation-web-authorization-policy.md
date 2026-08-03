# Phase 55 GCF-09L — Runtime Operation and Web Authorization Policy

Date: 2026-08-04

GCF-09L moves runtime operation and Web authorization selection from repeated
raw `RuntimeConfig.from(ResolvedConfiguration)` calls into the admitted
value-only `RuntimeOperationSecurityPolicy`. It owns the canonical
`textus.operation-mode`, develop anonymous-admin, demo-assist, production-admin,
and three production-admin role values. Each is SubsystemInstance-only and
retains the established `textus.runtime.*`, `cncf.*`, and `cncf.runtime.*`
spellings as decode-only aliases.

The closed catalog rejects malformed operation modes and Boolean values,
disallowed scopes, and canonical-plus-alias collisions. The policy preserves
the established `prod`/`dev` operation-mode normalization, Boolean admission,
role token behavior, and default values. It exposes neither raw configuration,
bindings, aliases, targets, nor provenance. Missing final bindings fail
structurally; an admitted absence chooses the current default and does not
revive a conflicting raw value.

Subsystem retains the policy atomically with final binding admission. Its
operation authorization and provider interface, admin policy,
Web authorization, HTTP authorization subject/landing/structured-error and
demo-assist decisions, and the runtime landing renderer consume the one policy.
The raw diagnostic table remains a diagnostic projection, not a policy
selection path. `application-mode` and CML `ApplicationMode` remain
presentation-only vocabulary; their removal is separate compatibility work.

Independent review found that `HttpExecutionEngine.Factory.engine()` created a
default Subsystem without final binding admission, causing both public HTTP
server factories to fail at construction. REVIEW_FIX now admits the normal
empty final binding collection before the public factory returns the engine;
the unadmitted-access failure remains intact for callers that bypass that
factory. A public engine/server/loopback regression covers this path.

Review-fix validation passed: serialized `Test/compile`
`22678-20260803T180426Z`; focused catalog, policy, Web, Subsystem, and HTTP
dispatch suites passed 47/47 at `23399-20260803T180547Z`. Focused independent
re-review found that this new regression was nested under the historical Phase
53 dispatch metadata. The second REVIEW_FIX moves it into the dedicated
`HttpExecutionEngineFactorySpec` with GCF-09L/Phase 55 metadata, leaving
historical Phase 53 coverage unchanged. Serialized `Test/compile` passed at
`26138-20260803T181057Z`; extended focused validation passed 47/47 at
`26792-20260803T181208Z`. Second focused independent re-review is clean.
