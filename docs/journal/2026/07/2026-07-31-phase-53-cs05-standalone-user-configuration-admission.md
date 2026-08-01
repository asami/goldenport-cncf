# Phase 53 CS-05: Standalone-User Configuration Admission

## Starting decision

On 2026-07-31, the owner approved the strict v1 wire format below. The public
profile is named `StandaloneUserProfile`: it is admitted only for standalone
operation and is neither read nor used as fallback in multi-user operation.
Each
individual user field is optional so a `.cncf` document can be a partial
overlay; unknown envelope, `user`, subsystem, and field keys fail closed.
Fixed `UserId` requiredness and migration semantics remain later CS-05
semantic-resolution work.

```yaml
apiVersion: textus/v1
kind: StandaloneUserProfile
user:
  id: user-1
  displayName: Example User
  locale: ja-JP
  timezone: Asia/Tokyo
subsystems:
  textus-art-scene:
    user:
      locale: en-US
```

CS-05 keeps `ResolvedConfiguration` and `ConfigurationTrace` as the one
configuration/provenance authority. It will not introduce a parallel
effective-binding or profile-provenance resolver, or make Component code
inspect profile files, application modes, or datastore policy.

The implementation order is deliberately narrow and fail-closed:

1. Add the `textus/v1` `StandaloneUserProfile` typed document and reject malformed,
   project, and working-directory inputs.
2. Admit only HOME `~/.textus/user-profile.yaml` and HOME
   `~/.cncf/user-profile.yaml`, with the latter as the higher layer; resolve
   common then stable-Subsystem fields before environment, arguments, and the
   explicit test override layer.
3. Add `ConfigurationOrigin.ExplicitOverride` in simplemodeling-lib after
   arguments, retaining field history in the existing trace.
4. Consume the effective fixed profile only for the fixed Subsystem execution
   profile. Authenticated execution ignores both HOME profile documents.
5. Bind the resolved user formatting and Subsystem-owned datastore to the
   existing mode-free `ExecutionContext`, then expose secret-safe diagnostics.

`textus.web.application-mode` is resolved before profile admission. A profile
never selects the Web mode, Component style, provider, or datastore.

## Invariants carried from CS-04

- Descriptor admission and stable Subsystem identity occur before
  subsystem-qualified profile lookup.
- Fixed and authenticated execution use the same canonical Component-facing
  `ExecutionContext` bindings.
- Components receive effective user, formatting, authorization, datastore, and
  UnitOfWork facts only; they do not receive configuration source or operation
  mode.

## Component implementation boundary

The owner confirmed that existing `Behavior` and `ActionBehavior` protected
internal DSLs are the regular Component implementation entry points. They
already compose the resource, clock/time, identifier, configuration, process,
information, operation-evaluation, repository, browser, entity-store, blob,
datastore, HTTP, and shell-command operations over the active
`ExecutionContext`.

CS-05 will not add a new Component facade or make a breaking visibility change
to `Behavior.Core.Holder`. A later CS-05 lint slice instead detects raw
Component-implementation traversal through `executionContext`,
`execution_context`, `behaviorCore`, and `core`; framework/DSL
implementations are excluded. Missing operations are added to the internal DSL
only when an actual Component use case requires them.

## Evidence status

### CS-05A implemented admission seam

`StandaloneUserProfile` now decodes the approved strict `textus/v1` envelope
and typed optional common/user fields plus subsystem user overrides. Unknown
or malformed shapes fail closed. `StandaloneUserProfileResolver` returns the
two HOME documents as distinct layers, in `.textus` then `.cncf` order; it
does not merge them or claim effective field precedence.

Only `SubsystemExecutionProfile.Fixed` causes those two paths to be read.
Authenticated and controlled-test profiles return no layers without invoking
the injectable reader. The internal admission seam rejects PROJECT and CWD
origins before a read. No `OperationMode` or `WebApplicationMode` enters this
API.

Focused framework validation on 2026-07-31 completed with 3 suites and 7/7
tests passing. The generic file-provenance validation in simplemodeling-lib
completed with 1 suite and 1/1 test passing: a file source now retains
`sourceType=file` and its exact path in `ConfigurationTrace`.

Effective field precedence, explicit override origin, stable user migration,
runtime formatting/datastore binding, diagnostics, and launcher adoption
remain later CS-05 work.

### CS-05B owner-approved boundary, 2026-08-01

Phase 53 does not promote the remaining effective-field precedence or detailed
provenance work into `simplemodeling-lib`. It makes no generic
`ConfigurationTrace`/`ConfigurationResolution` extension, metadata carrier,
`ConfigurationOrigin.ExplicitOverride`, ambient OS-environment codec, or
parallel profile-provenance API. Phase 53 evidence remains the existing trace
fields (`key`, `origin`, `sourceType`, `sourceId`, and `history`) and the
strict standalone admission seam proven above.

Phase 55 ConfigurationBinding work owns effective binding and precedence,
layer/target/subsystem/field-path provenance, explicit override
representation, and ambient environment conversion. CS-05L later closes these
as explicit Phase 55 deferrals, not as implementation credit.

### CS-05E canonical Web-operation admission, 2026-08-01

CS-05E limits Web-operation selection to
`textus.web.application-mode`. The Web-resolution policy reads that key directly
rather than applying the generic compatibility-key expansion used by Web
formatting settings. `cncf.web.application-mode`, the former
`cncf.runtime.web.execution.application-mode`, and the former
`textus.web.execution.application-mode` therefore neither select nor override
the operation.

When the canonical key is absent, the Web boundary contributes a traceable
`standalone` default only for a fixed-user implicit Subsystem whose root
Component descriptor provides `user.fixed-context-compatible@1`. The trace
uses the existing `ConfigurationTrace` vocabulary: `Default`,
`derived-default`, and `textus-direct-component-standalone`. Ineligible
Subsystems fail closed. An explicit canonical value always wins.

Focused framework validation completed on 2026-08-01 with 68/68 passing tests
in 7 suites: `WebExecutionResolutionSpec`,
`StaticWebExecutionProjectionIntegrationSpec`,
`WebExecutionRuntimeProjectionSpec`, `IngressSecurityResolverSpec`,
`SubsystemExecutionProfileSpec`, `GenericSubsystemFactorySpec`, and
`SubsystemAssemblyAdmissionSpec`. Independent review then found and the
review-fix corrected implicit-Subsystem provenance, blank canonical-value
handling, and executable-spec ordering. The repair gate completed with
`Test/compile` plus 18/18 passing tests in `WebExecutionResolutionSpec`,
`StaticWebExecutionProjectionIntegrationSpec`, and
`WebExecutionRuntimeProjectionSpec`; focused re-review found no actionable
findings.

This work establishes the Web-operation admission seam independently of any
future `StandaloneUserProfile` consumption. It does not establish runtime
ordering between them, effective profile field ordering, or generic
configuration binding/provenance behavior; those boundaries remain explicitly
deferred as recorded above.

### CS-05F non-destructive admission evidence, 2026-08-01

`StandaloneUserProfileResolverSpec` now snapshots the full temporary HOME tree
as a path inventory plus SHA-256 content digests around fixed-user admission,
malformed-profile failure, and authenticated/controlled-test exclusion. The
fixture includes both admitted HOME profiles and unrelated `.textus`, `.cncf`,
PROJECT, and CWD sentinels. Fixed admission reads only the normalized
`~/.textus/user-profile.yaml` then `~/.cncf/user-profile.yaml` paths;
authenticated and controlled-test profiles read neither path. Every scenario
proves the snapshot is unchanged. Test-owned temporary-directory cleanup is
outside that assertion.

Focused validation on 2026-08-01 completed with `Test/compile` and 8/8 passing
tests in `StandaloneUserProfileResolverSpec` and `StandaloneUserProfileSpec`.
Review found that normalization needed adversarial input and that the former
public-entry test mutated JVM-global `user.home`. The repair injects a
package-private HOME lookup, supplies a `segment/..` spelling, and revalidates
the same compile/test gate. This is executable non-mutation evidence only: it
neither merges profile fields nor changes UserId migration, formatting,
datastore, launcher, or generic ConfigurationBinding behavior. Focused
re-review found no actionable findings.

### CS-05G Component mode/configuration authority removal, 2026-08-01

The repository-dead public nested `Component.Config` type and its
`from(ResolvedConfiguration)` parser were removed. That parser read the dormant
`cncf.component.mode` key and exposed CLI `RunMode` at the permanent Component
boundary. `ComponentFactoryModeBoundarySpec` now proves the source and
`Component.Factory` hook signatures exclude that key, raw resolved
configuration, CLI run mode, operation mode, Web application mode, and
Subsystem execution profile. CLI routing and the internal
`ExecutionContext.operationMode` carrier are unchanged.

Focused validation on 2026-08-01 completed with `Test/compile` and 22/22
passing tests in `ComponentFactoryModeBoundarySpec`,
`ComponentFactoryInternalDslExtensionBoundarySpec`,
`ComponentInitializationBootstrapSpec`, and `SubsystemExecutionProfileSpec`.
This is a Component-boundary cleanup only: it does not bind standalone profile
values, establish configuration precedence/provenance, or change datastore,
launcher, ArtScene, or CLI behavior. Independent review found the migration
record incomplete; its fix and focused re-review are clean.

### CS-05H evidence-ledger reconciliation, 2026-08-01

CS-05D/G prove only the typed Component parameter boundary and the absence of
the former Component-owned mode/configuration authority. They do not prove a
general side-effect-free Factory policy, effective standalone profile binding,
stable UserId migration, fixed-user formatting, datastore binding, launcher
behavior, diagnostics, or every Component execution path. Those checklist
items remain open and retain their Phase 55, datastore/launcher, or CS-06
owners. This reconciliation adds no runtime behavior or new implementation
claim.

### CS-05I runtime admission sequencing, 2026-08-01

Rules:

- **CS05I-R1:** Server bootstrap resolves the canonical Web operation before
  fixed-user HOME-profile admission and passes only the resulting mode-free
  `SubsystemExecutionProfile` to that admission boundary.
- **CS05I-R2:** Authenticated and controlled execution never invokes HOME
  profile admission; controlled-test evidence remains controlled even when the
  canonical Web operation is standalone.
- **CS05I-R3:** Fixed admission requires descriptor-owned stable Subsystem
  identity and fails closed on invalid profile admission without fallback.

CS-05I connects the existing immutable profile-admission seam to framework
bootstrap. `CncfRuntime` invokes it after Subsystem construction and before
`ComponentFactory` bootstrap. In server execution it resolves the canonical Web
operation first and passes only the resulting mode-free
`SubsystemExecutionProfile` to the admission boundary. Fixed execution requires
the descriptor-owned stable Subsystem identity and admits the two HOME layers;
authenticated and controlled execution do not invoke HOME admission.

This remains admission only: admitted documents are not merged or used to
choose effective fields. Field precedence, detailed provenance, environment
binding, fixed-user migration, datastore binding, diagnostics, and launcher
selection remain separate CS-05/Phase 55 work.

### CS-05J launcher transport boundary, 2026-08-01

Rules:

- **CS05J-R1:** CNCF and Textus launchers keep component target selection and
  CNCF runtime selection on independent axes. Neither axis determines a Web
  operation, fixed-user identity, or datastore policy.
- **CS05J-R2:** After target/runtime selection, launchers forward the runtime
  argument spelling and order without interpreting, overriding, defaulting, or
  merging canonical/noncanonical Web keys or fixed-user-shaped values. Values
  after the Textus `--` wrapper delimiter remain runtime-owned passthrough.

The executable launcher transport specifications cover packaged and
development target selection in CNCF Launcher and packaged artifact selection
plus wrapper passthrough in Textus Launcher. This slice establishes transport
neutrality only. It does not establish configuration binding, profile
precedence, effective user fields, datastore selection, or launcher-owned Web
defaults.

Focused review-fix validation on 2026-08-01 completed with `Test/compile` and
the dedicated `runMain` transport gates in both launcher repositories. Each
gate now invokes the same assertions used by its registered specification,
checks that the configured CNCF runtime classpath remains independent of the
component target/artifact, and removes its temporary fixture tree.

### CS-05K existing runtime-boundary evidence, 2026-08-01

Rules:

- **CS05K-R1:** The CS-05I profile-admission boundary does not admit fixed-user
  HOME profiles for authenticated or controlled profiles. Authenticated ingress
  does not fall back to a local fixed identity; controlled Web-request profile
  propagation is not claimed by this rule.
- **CS05K-R2:** Fixed and authenticated execution keep the same mode-free
  `DataStoreSpace` and `EntityStoreSpace` interfaces at the Component-facing
  `ExecutionContext` boundary.

`RuntimeStandaloneUserProfileAdmissionSpec` already proves zero HOME-admission
calls for authenticated and controlled profiles. `IngressSecurityResolverSpec`
proves authenticated resolution has no local-subject fallback and preserves the
same canonical datastore/entity-store instances across fixed and authenticated
construction. `SubsystemExecutionProfileSpec` keeps datastore and operating
mode out of the profile carrier. CS-05K adds no datastore selection, binding,
diagnostic, or profile-precedence behavior.

Phase 55 owns the explicitly deferred field-precedence, direct-override, and
detailed binding/provenance work. Stable fixed-user migration, datastore
ownership metadata, and operator diagnostics remain open outside this
evidence-only slice.

### CS-05L documentation closure disposition, 2026-08-01

With owner approval, CS-05 closes without new runtime behavior. The checked
Phase 55 deferrals are closed as deferred, not as implementation credit:
effective binding/precedence, explicit override, detailed provenance, stable
fixed-user migration/isolation, fixed-user formatting, and secret-safe derived
diagnostics. Phase 54 owns datastore provider/target/principal/credential and
lifecycle metadata; Phase 58 owns presentation of sanitized operator evidence;
CS-06 owns final Component-execution/ArtScene acceptance; and strategy
candidate 9.53 owns general ComponentFactory purity and capability evidence.

This supersedes earlier scheduling language that left those subjects as later
CS-05 work. It does not select a datastore, bind profile fields, migrate data,
expose diagnostics, or change Factory behavior.
