# Component Activation Lifecycle Specification

Status: normative

## Authority and scope

This specification freezes the Phase 70 post-assembly activation contract for
CA70-01A and the admitted CA70-02 / CA70-02A accumulator. The requirements in
this document are normative. The ten-path accumulator is owned by Phase 70
under `P70-DEC-ACCUMULATOR-ADMISSION-003`; its implementation and executable
specifications remain subject to the required review and release evidence.

The earlier Phase 70.1 transfer is superseded for those ten paths. Its
untracked planning files remain preserved without source, implementation,
validation, or acceptance authority. Phase 70 retains the supplier-only
consumer handoff. No Textus BoK source or consumer behavior is accepted by
this specification or by an inherited validation receipt.

An **assembly** is one managed construction of a `Subsystem` for a selected
run mode. A **managed server assembly** is an assembly selected for
`RunMode.Server`. **Activation** is post-assembly work performed only by an
eligible opted-in Component. It is neither Component construction nor
initialization.

## Rules

### R1 Opt-in public capability

Activation is an opt-in Component capability distinct from
`Component.initialize` / `Component.initializeC`. The intended public seam is:

```scala
ComponentActivation.activateC(context: ComponentActivationContext): Consequence[Unit]
```

Only a Component that opts in to `ComponentActivation` is eligible for this
callback. A Component that does not opt in MUST retain its existing behavior.
Construction, `Component.initialize`, `Component.initializeC`, discovery,
bootstrap, direct add/upsert, and a coordinator request MUST NOT by themselves
invoke activation.

### R2 Framework-owned activation context

`ComponentActivationContext` is framework-owned. It carries only:

- the admitted `Subsystem`;
- resolved typed runtime configuration;
- the managed run mode;
- a bounded deadline; and
- cooperative cancellation.

The context MUST NOT expose construction-time parameters, `ComponentCreate`,
`ComponentInit`, direct implementation lookup, factory lookup, repository
lookup, or ClassLoader lookup. It is not a general configuration, factory, or
implementation escape hatch.

### R3 Post-assembly placement

The activation coordinator MUST begin only after all of the following
have completed successfully for the assembly:

1. `ComponentFactory` bootstrap;
2. Subsystem context injection and runtime-service binding;
3. Component-space admission and resolver rebuilding;
4. runtime-extra and default-component admission;
5. SPI resolution; and
6. `StartupImport`.

The coordinator MUST complete successfully before `CncfRuntime._run` can
dispatch `ServerOperation`, and before `Http4sHttpServer` publishes readiness
or bound URLs. No activation callback may observe a partially assembled
component graph as a successful managed-server state.

Decision `P70-DEC-R3-LIFECYCLE-DISPATCH-004` records the verified direct
selection of `add-lifecycle-dispatch-path`: the canonical Server branch in
`CncfRuntimeInstanceLifecyclePart._run` owns this gate after completed
assembly and before `ServerOperation` construction or dispatch. A failed gate
uses the ordinary CLI failure rendering. The direct legacy `startServer` path
retains its explicit compatibility gate, while `ServerOperation` does not
perform a duplicate post-dispatch activation.

### R4 Deterministic, sequential, once-only execution

The final `ComponentSpace.components` admission/upsert order is the
deterministic activation order. The coordinator MUST examine that order,
select opted-in Components from it, and invoke their callbacks sequentially.
It MUST not begin a later callback before the preceding callback has completed
successfully.

On an initial accepted managed server assembly, each eligible Component runs
exactly once. The initial request preserves the final admission/upsert order.
Once that request reaches either a successful or failing terminal result, the
per-Subsystem coordinator retains that exact terminal result. Every repeat
MUST return it without invoking a completed, failing, later, or newly upserted
callback. Component creation, initialization, discover/bootstrap, direct
add/upsert, and repeated coordinator requests do not authorize another
callback.

### R5 Component-boundary-only work

The coordinator MUST call only the opted-in capability; it MUST NOT route
activation through arbitrary `Operation` or `Action` dispatch. During
activation, cross-component work may use only already-admitted `Subsystem` and
Component public API facilities. It MUST NOT resolve another Component through
an implementation object or factory lookup.

### R6 Mode isolation and controlled tests

Ordinary automatic activation is Server-mode-only. `Command`, `Client`,
`Script`, and `ServerEmulator` assemblies MUST remain non-activating. A test
may exercise activation only through an explicit controlled-test admission
seam; test or emulator construction alone is not admission. That seam MUST
not widen normal runtime execution or the existing controlled-test security
boundary.

### R7 Timeout, cancellation, failure, readiness, and cleanup

Activation has the bounded deadline and cooperative cancellation carried by
its context. This contract selects no duration or cancellation-policy value.

On timeout or cancellation, the coordinator MUST prevent every later
activation callback, initiate managed Subsystem cleanup, keep readiness and
bound URLs unpublished, and return one bounded structured failure. A callback
that completes after timeout or cancellation MUST NOT convert that assembly to
ready state.

The coordinator MUST execute callbacks sequentially through an internal
daemon-capable bounded worker. At terminality it MUST cancel and interrupt the
outstanding callback work without waiting for a noncooperative callback to
finish, so the caller receives the terminal result by the deadline and no
later callback begins. This internal policy adds no public timeout or
cancellation API, global fallback, or shadow state store.

An ordinary activation failure has the same readiness and managed-cleanup
outcome: the server is not ready and no later callback begins. The returned
failure and diagnostic may identify a safe Component identity, run mode,
elapsed time or deadline, and failure category. They MUST NOT expose
configuration values, credential material, private paths or URLs, raw
exception payloads, or BoK resource locators.

As a clarification of this existing R7 structured-diagnostic contract, an
activation diagnostic MUST expose its sanitized `Conclusion` through a public
`conclusion` accessor. Every item in that Conclusion's public `causes` sequence
MUST have an empty `getException` result. All public representations of that
diagnostic Conclusion remain subject to the redaction prohibition already
stated above: they MUST NOT expose configuration values, credential material,
private paths or URLs, raw exception payloads, or BoK resource locators.

### R8 Compatibility boundaries

This contract MUST be implementable without reopening Phase 55 initialization
or changing the `ComponentCreate` / `ComponentInit` mode boundaries.
Implementation must preserve the existing behavior covered by
`ComponentFactoryRuntimePlanActivationSpec`, `ComponentFactoryModeBoundarySpec`,
`SubsystemAssemblyAdmissionSpec`, and `RuntimeBindingAdmissionFixtureSpec`
until a later accepted slice changes behavior with its own executable
evidence.

### R9 Textus BoK handoff

Textus BoK Phase 8 is a downstream consumer only. It may use this
post-assembly Server hook, after SIE and participating Components are already
registered, to select already-registered profiles. Phase 70 MUST NOT add a
Textus BoK dependency, configuration, profile loading, publishing behavior,
or source edit. Profile selection, source identity, loading, publication, and
their consumer diagnostics remain owned by Textus BoK Phase 8.

## Executable evidence matrix

The active Phase 70 accumulator MUST maintain failing-first executable
specifications for every row below, implement the contract those
specifications describe, and provide the managed-runtime evidence indicated
below. The supplier handoff to Textus BoK remains separate from BoK source or
end-to-end consumer acceptance. The inherited CA70-01A review receipt adds no
runtime or consumer acceptance evidence.

| Evidence group | Phase 70 failing-first specification | Phase 70 implementation evidence | Managed/consumer handoff |
| --- | --- | --- | --- |
| No-opt-in compatibility | An ordinary Component has no callback, behavior, or readiness change. | Preserve that absence while adding the opt-in capability. | Regress existing non-activating server behavior. |
| Complete-graph dependency | An opted-in Component can use an already-admitted public Component/API dependency only after complete assembly. | Place the coordinator after the R3 boundary. | Demonstrate the dependency during managed server startup. |
| Deterministic multi-component order | Multiple opted-in Components observe final `ComponentSpace.components` admission/upsert order. | Invoke sequentially in that order. | Record the managed startup order. |
| Once-only | Creation, initialization, discovery/bootstrap, add/upsert, and repeated coordinator requests do not duplicate callbacks. | Persist the per-assembly once-only guarantee. | Demonstrate one callback per eligible Component. |
| Mode isolation | Command, Client, Script, and ServerEmulator do not activate. | Gate ordinary activation to Server mode. | Regress the non-Server modes. |
| Controlled-test activation | Activation occurs only through an explicit controlled-test admission seam. | Keep that seam separate from ordinary runtime paths. | Demonstrate admitted test activation and deterministic cleanup. |
| Timeout and cancellation | Timeout/cancellation stops later callbacks and rejects late completion as readiness. | Apply the bounded deadline and cooperative cancellation. | Demonstrate the timeout/cancel terminal result. |
| Failure, readiness, cleanup, and redaction | Failure produces one bounded structured result with no forbidden data, no readiness, and managed cleanup. | Route failures through the ordinary cleanup path before HTTP publication. | Demonstrate failure, unpublished readiness/bound URLs, cleanup, and redaction. |
| BoK handoff | The supplier seam exposes no Textus configuration or profile behavior. | Keep the framework implementation consumer-neutral. | Use the hook only for the Textus BoK Phase 8 already-registered-profile bootstrap. |

## Source alignment

The admitted source accumulator establishes the placement and compatibility
anchors for this contract:

- `Component.initializeC` consumes `ComponentInit` during initialization.
- `Subsystem._prepare_components_c` bootstraps Components, injects context,
  and binds runtime services; `addC` / `upsertC` then rebuild the resolver.
- `ComponentSpace.components` retains admission order, while `upsert` replaces
  an existing instance at its position or appends a new one.
- `CncfRuntime` admits runtime extras/defaults, resolves SPI, runs
  `StartupImport`, and then dispatches `_run`; its Server branch reaches
  `ServerOperation`.
- `Http4sHttpServer.start` publishes bound URLs only after the server is
  built. `Subsystem.shutdownOwned` is the managed cleanup path.
- `textus-bok:docs/phase/phase-8.md` records the downstream post-assembly
  Server-hook dependency and retains profile/bootstrap ownership in Textus
  BoK.

## References

- [Document Lifecycle](../rules/document-lifecycle.md)
- [Phase 70](../phase/phase-70.md)
- [Phase 70 Checklist](../phase/phase-70-checklist.md)
- Phase 70.1 (preserved untracked planning artifact; no acceptance authority)
- Phase 70.1 Checklist (preserved untracked planning artifact; no acceptance
  authority)
- `src/main/scala/org/goldenport/cncf/component/Component.scala`
- `src/main/scala/org/goldenport/cncf/component/ComponentSpace.scala`
- `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`
- `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeInstanceLifecyclePart.scala`
- `src/main/scala/org/goldenport/cncf/cli/ServerOperation.scala`
- `src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala`
