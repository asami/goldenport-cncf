# Phase 47 Checklist - Component Initialization Parameter Resolution

status=active
phase=[Phase 47 - Component Initialization Parameter Resolution](phase-47.md)

This checklist is the authoritative planned Phase 47 state ledger.

## CIP-01: Normative Contract

Stage Status:
- Current status: DONE
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after normative design and specification define
  initialization-time parameter resolution and distinguish it from runtime
  component configuration access.

Verified evidence:
- Normative text is defined in `docs/design/configuration-model.md`,
  `docs/design/component-runtime-boundary-capabilities.md`,
  `docs/spec/config-resolution.md`, and
  `docs/spec/component-runtime-boundary-capabilities.md`.
- Independent review found and resolved the obsolete raw
  `Component.Config.from(ResolvedConfiguration)` path.
- Documentation validation passed for the Phase 47 CIP-01 release checkpoint.

- [x] Define CNCF and component ownership of initialization parameter
  declaration, resolution, validation, and delivery.
- [x] Define the relationship to source resolution and
  `ResolvedConfiguration` without adding component-domain semantics to the raw
  store.
- [x] Define the relationship to operation-time
  `ComponentConfigurationAccess` without creating a competing runtime API.
- [x] Fix the prohibition on raw configuration-map delivery and ambient source
  lookup from component initialization code.
- [x] Record the normative design and static specification references in the
  Phase 47 dashboard.

## CIP-02: Typed Parameter Model

Stage Status:
- Current status: DONE
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after the public and protected types provide a
  typed initialization path with structured failure and safe provenance.

Verified evidence:
- Typed keys, decoders, resolutions, bounded provenance, a CNCF-protected
  resolver, and an immutable snapshot are implemented in
  `org.goldenport.cncf.config`.
- `ComponentInitializationParametersSpec` provides deterministic typed,
  failure, identity, raw-access prohibition, and provenance evidence.
- Independent review and re-review found no actionable findings.
- Focused executable specifications, full CNCF tests, and diff validation
  passed for the CIP-02 release checkpoint.

- [x] Define `ComponentParameterKey[A]` with typed decoding and
  required/optional semantics.
- [x] Define `ComponentParameterResolution[A]` and structured
  `Consequence`/`Conclusion` failures.
- [x] Define `ComponentInitializationParameters` without a raw-map accessor.
- [x] Define `ComponentParameterResolver` and its input/output contract.
- [x] Define `ComponentParameterProvenance` with bounded, non-physical source
  identities.

## CIP-03: Resolution Layers

Stage Status:
- Current status: DONE
- Owner: CNCF configuration maintainers
- Update rule: Mark DONE only after every admitted initialization source has a
  deterministic precedence position and unsupported sources cannot participate.

Verified evidence:
- `ComponentParameterResolutionLayers` encodes the five admitted sources as
  distinct CNCF-private positions and atomically separates explicit test keys
  from runtime values without retaining runtime trace metadata.
- `ComponentParameterResolutionLayersSpec` covers layer provenance,
  generated overlap precedence, fallback, malformed shadowing, trace
  exclusion, explicit test overlays, and ambient-source rejection.
- Independent review and clean re-review found no remaining actionable issues.
- Focused executable specifications passed with 11 tests, `Test/compile`
  passed, and the full CNCF suite passed with 2,227 tests across 322 suites.
- `git diff --check` passed.

- [x] Define packaged component defaults.
- [x] Define component assembly defaults.
- [x] Define subsystem/SAR component-instance settings.
- [x] Define the projection from runtime resolved configuration.
- [x] Define explicit test overlays as a test-only highest-priority layer.
- [x] Prove that request/action properties and ambient lookups are not
  resolution layers.

## CIP-04: Component-instance Context

Stage Status:
- Current status: DONE
- Owner: CNCF component runtime maintainers
- Update rule: Mark DONE only after parameter resolution is bound to component
  and component-instance identity and cross-instance leakage is impossible.

Verified evidence:
- `ComponentParameterContext` selects one coherent component identity,
  packaged descriptor owner, and admitted assembly instance metadata record.
- `ComponentParameterResolutionLayers` now requires that validated context and
  derives the subsystem-instance layer only from its selected metadata.
- `ComponentParameterContextSpec` covers primary/componentlet ownership,
  generated named-instance isolation, missing and ambiguous contexts,
  mismatched identities, SAR cross-instance exclusion, and CAR artifact/runtime
  identity separation.
- Independent review and clean re-review found no remaining actionable issues.
- Focused executable specifications passed with 13 tests, and the full CNCF
  suite passed with 2,234 tests across 323 suites.
- Whole-file naming and executable-specification checks passed, and
  `git diff --check` passed.

- [x] Define `ComponentParameterContext` around `ComponentId`,
  `ComponentInstanceId`, descriptor, and admitted assembly metadata.
- [x] Resolve identical parameter keys independently for separate component
  instances.
- [x] Reject ambiguous or missing component-instance contexts structurally.
- [x] Verify subsystem/SAR assembly cannot apply one instance's settings to
  another instance.

## CIP-05: Bootstrap Integration

Stage Status:
- Current status: DONE
- Owner: CNCF component runtime maintainers
- Update rule: Mark DONE only after normal and special component
  initialization receive typed parameters through one CNCF-owned bootstrap
  route.

- [x] Integrate resolution before `ComponentFactory.bootstrap` component
  construction or initialization.
- [x] Extend the factory/bootstrap contract without exposing
  `ResolvedConfiguration` or an untyped map to components.
- [x] Apply the same contract to special-component initialization paths.
- [x] Preserve current behavior for components that declare no initialization
  parameters.
- [x] Preserve operation-time `ComponentConfigurationAccess` compatibility.

Verified evidence:
- `ComponentParameterBootstrap` maps the five fixed layers after final
  participant identity and creates one immutable typed snapshot.
- Consequence-aware creation, initialization, bootstrap, and subsystem
  admission preserve structured failures before installation.
- `ComponentInitializationBootstrapSpec` covers typed delivery, all fixed
  layers, named-instance isolation, failure, componentlets, no declarations,
  special components, real repository bootstrap, repository failure
  propagation, and raw-configuration exclusion. `ComponentRepositoryCarSpec`
  additionally proves packaged CAR defaults and exact CAR/SAR factory
  `Conclusion` propagation.
- Review findings were fixed by attaching the descriptor before repository
  construction, passing one binding's metadata into each discovery context,
  preserving repository factory `Conclusion` values, and wrapping special
  component initialization failures. Re-review findings were fixed by retaining
  the packaged descriptor through repository selection and participant
  materialization, and by preserving requested CAR/SAR discovery failures
  through archive extraction. The latest re-review finding was fixed by making
  assembly API metadata/classloader preparation consequence-native, so its
  structured validation failure is not replaced by a generic component error.
  A subsequent re-review finding was fixed by retaining an exact initialized
  repository participant rather than invoking its factory a second time;
  unmatched prototypes still support named-instance rematerialization.
- The focused CIP-05 regression set passed 98 tests across
  `ComponentInitializationBootstrapSpec`,
  `ComponentRepositoryCarSpec`,
  `GeneratedComponentBundleFactorySpec`,
  `ComponentFactoryRuntimePlanActivationSpec`, and
  `GenericSubsystemFactorySpec`; the repository archive suite and the 31-test
  bootstrap/runtime set were executed separately because their established
  global work-area fixtures are process-scoped. `Test/compile` and
  `git diff --check` also passed.
- Independent review findings and each clean re-review cycle were completed;
  no actionable finding remains.
- The full CNCF suite passed with 2,248 tests across 324 suites, with no failed
  or aborted suite.

## CIP-06: Secret and Confidential Parameters

Stage Status:
- Current status: DONE
- Owner: CNCF runtime and security maintainers
- Update rule: Mark DONE only after confidential inputs remain opaque across
  parameter APIs, failures, diagnostics, and observability.

Verified evidence:
- `ComponentParameterConfidentiality` classifies public, confidential, and
  secret declarations without allowing caller-supplied decoders to claim
  secret metadata.
- Dedicated required and optional secret-reference constructors deliver only
  the opaque `SecretReference`; confidential material is rejected before
  source lookup or decoding.
- `ComponentInitializationParametersSpec`, `SecretReferenceSpec`, and
  `ComponentInitializationBootstrapSpec` cover redacted resolution, malformed
  values, confidential denial, optional absence, JVM method-surface opacity,
  and factory bootstrap delivery.
- Independent review found the JVM-visible locator accessor and
  locator-derived hash; review-fix removed both, and the clean re-review found
  no remaining actionable finding.
- The 31-test focused confidentiality/bootstrap set, `Test/compile`, compiled
  bytecode inspection, and `git diff --check` passed.
- The full CNCF suite passed with 2,251 tests across 324 suites, with no failed
  or aborted suite; 2 tests were canceled, 1 ignored, and 59 remain pending.

- [x] Define opaque secret-reference parameters separately from ordinary
  decoded values.
- [x] Define confidential parameter metadata and redaction behavior.
- [x] Reject embedded secret-value exposure through initialization parameter
  accessors.
- [x] Verify failures do not expose credential material, physical source
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
