# Phase 47 Checklist - Component Initialization Parameter Resolution

status=active
phase=[Phase 47 - Component Initialization Parameter Resolution](phase-47.md)

This checklist is the authoritative planned Phase 47 state ledger.

## CIP-01: Normative Contract

Stage Status:
- Current status: IN_PROGRESS
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after normative design and specification define
  initialization-time parameter resolution and distinguish it from runtime
  component configuration access.

- [ ] Define CNCF and component ownership of initialization parameter
  declaration, resolution, validation, and delivery.
- [ ] Define the relationship to source resolution and
  `ResolvedConfiguration` without adding component-domain semantics to the raw
  store.
- [ ] Define the relationship to operation-time
  `ComponentConfigurationAccess` without creating a competing runtime API.
- [ ] Fix the prohibition on raw configuration-map delivery and ambient source
  lookup from component initialization code.
- [ ] Record the normative design and static specification references in the
  Phase 47 dashboard.

## CIP-02: Typed Parameter Model

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after the public and protected types provide a
  typed initialization path with structured failure and safe provenance.

- [ ] Define `ComponentParameterKey[A]` with typed decoding and
  required/optional semantics.
- [ ] Define `ComponentParameterResolution[A]` and structured
  `Consequence`/`Conclusion` failures.
- [ ] Define `ComponentInitializationParameters` without a raw-map accessor.
- [ ] Define `ComponentParameterResolver` and its input/output contract.
- [ ] Define `ComponentParameterProvenance` with bounded, non-physical source
  identities.

## CIP-03: Resolution Layers

Stage Status:
- Current status: OPEN
- Owner: CNCF configuration maintainers
- Update rule: Mark DONE only after every admitted initialization source has a
  deterministic precedence position and unsupported sources cannot participate.

- [ ] Define packaged component defaults.
- [ ] Define component assembly defaults.
- [ ] Define subsystem/SAR component-instance settings.
- [ ] Define the projection from runtime resolved configuration.
- [ ] Define explicit test overlays as a test-only highest-priority layer.
- [ ] Prove that request/action properties and ambient lookups are not
  resolution layers.

## CIP-04: Component-instance Context

Stage Status:
- Current status: OPEN
- Owner: CNCF component runtime maintainers
- Update rule: Mark DONE only after parameter resolution is bound to component
  and component-instance identity and cross-instance leakage is impossible.

- [ ] Define `ComponentParameterContext` around `ComponentId`,
  `ComponentInstanceId`, descriptor, and admitted assembly metadata.
- [ ] Resolve identical parameter keys independently for separate component
  instances.
- [ ] Reject ambiguous or missing component-instance contexts structurally.
- [ ] Verify subsystem/SAR assembly cannot apply one instance's settings to
  another instance.

## CIP-05: Bootstrap Integration

Stage Status:
- Current status: OPEN
- Owner: CNCF component runtime maintainers
- Update rule: Mark DONE only after normal and special component
  initialization receive typed parameters through one CNCF-owned bootstrap
  route.

- [ ] Integrate resolution before `ComponentFactory.bootstrap` component
  construction or initialization.
- [ ] Extend the factory/bootstrap contract without exposing
  `ResolvedConfiguration` or an untyped map to components.
- [ ] Apply the same contract to special-component initialization paths.
- [ ] Preserve current behavior for components that declare no initialization
  parameters.
- [ ] Preserve operation-time `ComponentConfigurationAccess` compatibility.

## CIP-06: Secret and Confidential Parameters

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime and security maintainers
- Update rule: Mark DONE only after confidential inputs remain opaque across
  parameter APIs, failures, diagnostics, and observability.

- [ ] Define opaque secret-reference parameters separately from ordinary
  decoded values.
- [ ] Define confidential parameter metadata and redaction behavior.
- [ ] Reject embedded secret-value exposure through initialization parameter
  accessors.
- [ ] Verify failures do not expose credential material, physical source
  locations, or confidential values.

## CIP-07: Diagnostics and Observability

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after bootstrap diagnostics expose enough bounded
  facts to explain resolution without exposing payloads or physical sources.

- [ ] Define safe parameter identity and provenance summaries.
- [ ] Define structured missing, malformed, ambiguous, and rejected outcomes.
- [ ] Add optional CallTree or runtime metadata only where bootstrap lifecycle
  ownership is explicit.
- [ ] Verify default diagnostics omit raw values, credentials, physical paths,
  and unrelated configuration keys.

## CIP-08: Executable Evidence

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after deterministic executable specifications
  cover every Phase 47 acceptance boundary.

- [ ] Cover defaults and explicit override precedence.
- [ ] Cover missing required and malformed typed values.
- [ ] Cover separate `ComponentInstanceId` values with the same parameter key.
- [ ] Cover deterministic test overlays and production-source exclusion.
- [ ] Cover components with no declared initialization parameters.
- [ ] Cover secret-reference and confidential diagnostic behavior.
- [ ] Cover the absence of raw configuration-map access.
- [ ] Cover compatibility with operation-time `ComponentConfigurationAccess`.

## CIP-09: Downstream Acceptance and Closure

Stage Status:
- Current status: OPEN
- Owner: CNCF maintainers, with Textus AI or CBD Support maintainers as the
  downstream acceptance owner
- Update rule: Mark DONE only after one real consumer uses the generic
  mechanism, validation passes, review findings are resolved, and the phase
  dashboard records closure.

- [ ] Select Textus AI or CBD Support as the first downstream consumer.
- [ ] Migrate the consumer without custom configuration-source precedence.
- [ ] Verify user-wide defaults and component-instance-specific settings.
- [ ] Verify reusable Textus AI code does not interpret CBD Support component
  keys.
- [ ] Run focused CNCF and downstream executable specifications.
- [ ] Run the complete affected suites and record evidence.
- [ ] Run CNCF review and resolve all actionable findings.
- [ ] Create the validated release commit and close Phase 47.
