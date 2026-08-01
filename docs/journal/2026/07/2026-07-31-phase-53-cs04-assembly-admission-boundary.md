# Phase 53 CS-04: Assembly Admission Boundary

## Decision

CS-04 separates descriptor-only assembly admission from runtime component
materialization. A schema-v2 `ComponentStyleSnapshot` remains attached to the
root Component descriptor when a direct or development-directory Component is
converted into an implicit Subsystem descriptor.

`GenericSubsystemComponentBinding.capabilities` is not a Subsystem capability
provider authority. It remains component-instance selection metadata; values
such as `html` and `same-origin` must never be parsed as
`SubsystemCapabilityId`.

## Sequencing

1. Build the complete root/dependency descriptor closure without loading a
   Component class, and reject missing, duplicate, or ambiguous bindings.
2. Add a dedicated typed Subsystem provider declaration and resolve every
   style requirement exactly once, with explicit missing, ambiguous, unknown,
   and major-version mismatch diagnostics.
3. Move that admission before `Subsystem` construction, repository build,
   class loading, SPI binding, datastore creation, and job admission.
4. Keep the later mode-free `SubsystemExecutionProfile` and user-context work
   separate from the deterministic `context.ExecutionProfile` contract.

## Implemented admission slice

The framework now accepts an explicit declaration in a Subsystem descriptor:

```yaml
subsystemCapabilities:
  providers:
    - name: persistent-store
      component: runtime-facilities
      provides:
        - datastore.persistent@1
```

`provides` is decoded as `Vector[SubsystemCapabilityId]`, so malformed
identities cannot become provider authority. Provider names must be unique;
repeated capability declarations within one provider are rejected. Providers
must name a declared component binding.

`SubsystemAssemblyAdmission.verifyC` runs after descriptor projection and
before `Subsystem(...)`. It compares every schema-v2 style requirement with
the dedicated providers and rejects missing providers, multiple exact
providers, absent provider components, and same-family/name providers with an
incompatible major. Its input is the descriptor and its static Component
descriptors only; it performs neither repository construction nor class
loading.

Successful admission records a deterministic `Report`: forward requirement to
provider assignments and the reverse provider-to-requirements index. The
report is attached only after the Subsystem is constructed from an admitted
descriptor.

The implementation now resolves explicit root/dependency descriptor metadata
and, where needed, reads a static `ComponentDescriptor` through an ordered
repository specification before the same gate. It remains descriptor-only;
repository `build` is still after admission.

## Evidence

- `GenericSubsystemDescriptorSpec` proves that a typed style snapshot survives
  the implicit descriptor projection.
- `GenericSubsystemFactorySpec` covers the direct and development projection
  paths. The expanded `Test/compile` plus descriptor/factory/admission run
  completed on 2026-07-31: 3 suites, 50/50 passed (15 pre-existing
  deprecation warnings only).
- `SubsystemAssemblyAdmissionSpec` supplies descriptor-only success,
  ambiguity, unknown-component, and major-version-mismatch cases.
- `SubsystemExecutionProfileSpec` proves `Standalone -> Fixed` and
  `MultiUser -> Authenticated` strictly inside the Web adapter boundary. The
  corresponding focused compile run completed on 2026-07-31: 5 suites,
  63/63 passed, with one unrelated pre-existing pending case.
- `IngressSecurityResolver.resolve(profile, base, attributes)` now gives fixed
  and authenticated ingress the same `ResolvedIngressSecurity` result shape.
  Fixed rejects authentication material and requires the configured local
  subject. Authenticated requires provider configuration and ingress evidence,
  and rejects a provider decline without local or privilege fallback.
  Focused compile validation completed on 2026-07-31: 4 suites, 46/46 passed.
- `SubsystemAssemblyAdmission.Report` records deterministic requirement to
  provider assignments and their reverse provider requirement index. The
  factory stores the successful report on `Subsystem` only after the
  pre-activation gate. Focused compile validation completed on 2026-07-31:
  3 suites, 45/45 passed.
- Admission now calls `resolveStaticComponentDescriptor`; the development
  repository implementation reads only generated descriptor metadata and does
  not fall back to component inference/class loading. Focused compile
  validation completed on 2026-07-31: 2 suites, 17/17 passed.
- `Http4sHttpServer` resolves `WebExecutionResolutionPolicy` at request
  ingress and passes only its projected `SubsystemExecutionProfile` to the
  security resolver. HTTP-focused compile validation completed on 2026-07-31:
  2 suites, 37/37 passed, with one unrelated pre-existing pending case.
- `GenericSubsystemFactory` completes descriptor admission before
  `RuntimeConfig` construction; only explicitly configured repositories are
  consulted for static admission metadata. `ComponentCreate` and
  `ComponentInit` now keep the raw `Subsystem` in an internal assembly carrier.
  Direct Subsystem operations select the same profile-aware resolver as HTTP;
  the explicit controlled-test profile retains request-level test facts only.
  Focused compile validation completed on 2026-07-31: 4 suites, 45/45 passed,
  with one remaining context-facade pending case.
- Component-facing fields no longer expose a raw `Subsystem`; the runtime,
  operation-mode, and core accessors on `ExecutionContext` are Scala
  `private[cncf]`. Authentication provenance is carried privately by
  `SecurityContext`/`SecuritySubject`, rather than leaked as principal
  attributes. The targeted compile run covering ingress resolution,
  operation authorization, and the component factory boundary completed on
  2026-07-31: 3 suites, 35/35 passed with no pending tests.
- The public descriptor factory now completes admission before it creates its
  default `ScopeContext`; this avoids the `ExecutionContext.create()` test
  datastore/entity-store allocation for an invalid descriptor. Static closure
  uses the complete runtime repository specification set, including defaults.
  An unconfigured Subsystem fails closed: `ControlledTest` is selected only
  when a `RuntimeTestDescriptor` is explicit. Assembly and execution carrier
  implementations are non-`Product`, and provider provenance is an internal
  weak identity association consumed by the built-in auth component. Focused
  compile validation completed on 2026-07-31: 5 suites, 78/78 passed.
- Fixed and authenticated profiles share the same resolver path after identity
  resolution. The executable specification proves that each preserves the base
  datastore, entity store, and UnitOfWork binding, while authenticated locale
  evidence updates the canonical formatting context. Focused compile
  validation completed on 2026-07-31: 4 suites, 43/43 passed.

The journal records the implemented boundary and its executable evidence.
CS-04 is complete; the next phase slice is CS-05 fixed-user and datastore
configuration admission.
