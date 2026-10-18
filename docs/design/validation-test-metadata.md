# Validation/Test Metadata Interpretation

The [V1 specification](../spec/validation-test-metadata.md) promotes declaration
semantics from the [planning note](../notes/validation-test-metadata-abi-proposal.md).
The note remains history; the spec and Executable Specification define behavior.

## Stable public data and a pure interpreter

`org.goldenport.cncf.validation.ValidationMetadataV1` owns instance-independent
types for declarations, scoped feature references, normalized metadata and
declaration origins. Purpose and duration class are closed enums; feature
references remain extensible data. The public resolver delegates to the
package-private `ValidationMetadataResolution`. Neither layer uses discovery,
runtime state, providers or ScalaTest production dependencies.

The interpreter first admits both declarations with applicative Consequence
composition. It normalizes duration and capabilities into temporary validated
values before merging. Public declaration validation preserves source values;
resolution returns canonical values and does not mutate inputs. Unsupported or
malformed defaults remain failures even when an operation supplies overrides.

Purpose replacement is deliberately distinct from feature union. An optional
purpose set represents inheritance versus an explicit replacement, including
empty replacement. Origins preserves absent versus explicit empty declarations.
Duration and duration class remain independent fields. Exact nanosecond
arithmetic prevents truncation/saturation in normalization.

## Reuse the execution aggregate

The singular execution requirement is the existing
`WorkflowProtocolV1.ExecutionRequirement`, validated through its actual API.
Metadata adds a composition binding, not another provider or execution model.
Capability union, strongest existing reasoning and review OR preserve both
declarations' requirements. Risk is opaque and equality-constrained. Optional
disposition and reasoning class inherit or must agree. Structured policy
failures reject incompatible aggregates without weakening class requirements.

## Producer and consumer responsibilities

CNCF owns normalized declaration semantics, scoped discovery, pure purpose/feature
queries and stable typed selector/reference data. sm-workflow owns query input,
Slice validation plans, execution policy and observations. textus-cbd-support
owns static analysis, coverage comparison and KPI presentation. Cozy owns CML
syntax and generation; equivalent normalized mapping fixtures belong to the
producer handoff, without making generated Scala an independent authority.

Feature scope and identity are exact declarations. Neither labels nor observed
test features supply inferred application mappings or a target feature inventory.
Declared duration is operation-level data. Runtime observation scopes and their
comparability are separate consumer evidence.

## Discovery separation and trust

`ValidationDiscoveryV1` owns only fixed instance-independent public data and
delegates to package-private helpers. `ValidationDiscovery` admits scope and
coordinates registration inspection, completeness, collision invalidation,
ordering and diagnostic budgets. `ValidationArtifactEnumeration` traverses the
bounded directory/JAR namespace and admits matching local code sources.
`ValidationAnnotationInterpretation` binds effective runtime anchors to real
registered names and obtains typed declarations. `ValidationDiscoverySelection`
admits inventory/query data and applies pure queries and exact references.

Only ScalaTest core 3.2.18 is a Provided production dependency. The existing
ScalaTest Test dependency and repository test generator remain unchanged.
Public concrete no-arg flat Suites are the initial framework boundary. Actual
FunSuite/WordSpec fixtures exercise their own `testNames`; there is no independent
suite membership registry. Distinct parameter registrations and helper anchors
avoid parsing/fuzzy matching test text. A caller can invoke one concrete selector
through ScalaTest itself; the existing CNCF execution-path adapter remains S3.

The caller supplies the local artifact roots, package namespaces, component and
artifact/source revisions and loader. Enumeration does not infer global project
membership. `Class.forName` suppresses initialization while loading; admitted
suite construction/registration and provider evaluation are trusted local side
effects. Matching code-source admission prevents parent-loader shadowing from
claiming unrelated suite/provider content. Discovery owns short-lived file
streams/JARs and closes them; the caller owns every loader action and lifecycle.
There are no production loader caches, threads or retained suite objects.

Java runtime annotation interfaces avoid Scala StaticAnnotation retention
assumptions and are directly usable in Scala. Public typed declaration providers
are admitted for type, public no-arg construction and matching artifact source
before construction. A declaration is read once for each annotation source.
Provider absence (`Void.class`) and explicit `Declaration()` preserve S1 origins;
all valid metadata merge rules remain exclusively in S1. Invalid defaults cannot
be hidden by scenario overrides. Inventory rows hold only immutable typed facts.

## Logical references and scope facts

Logical component/specification/operation references remain independent of
artifact, source and operation revisions. A class annotation identity falls back
to the binary suite name and a scenario identity to its exact registered full
name; operation revision falls back through scenario/specification/artifact.
Malformed identity text uses the real source fallback only for invalid inspection
rows. Collisions preserve all selectors and invalidate all colliding metadata.
Deterministic row order is independent of artifact input order.

COMPLETE/PARTIAL/UNKNOWN describes executable enumeration within explicit scope.
Invalid metadata can coexist with COMPLETE enumeration. Unannotated operations
remain visible, valid and explicitly resolvable without gaining a purpose. Scope
diagnostics are retained even when no row is available. Per-artifact and overall
budgets include row diagnostics and provide explicit truncation, preventing
unbounded exception/source dumps.

The pure query helper consumes inventory data only. It does not re-open artifacts
or acquire a loader. Typed sets express purpose OR and feature OR, combined with
AND across dimensions; no purpose hierarchy or feature relationship is inferred.
Supplemental exact resolution preserves metadata and existing execution facts.
Consumers retain the inventory's completeness when interpreting an empty result;
no observed-feature denominator or missing-coverage claim is generated here.

## Exact operation intent and current authority

`ValidationOperationBindingV1` owns a final privately constructed
`BoundOperation`, with public original operation and typed SbtExec intent.
It reuses the existing public exact resolver for both query-selected and
supplemental references, admits a logical WorkspaceId and exact literal selector,
and constructs intent only. It preserves metadata, provenance and existing
execution requirements, including unannotated or out-of-query operations.
It creates no executor, provider/reasoning policy or runtime evidence model.

`SbtScenarioSelection` extends the existing target with a trailing defaulted
option. Ordinary construction remains source compatible; extractor arity
changes and precompiled SNAPSHOT compatibility is not claimed. Accepted class
grammar is the existing host policy. Literal test names are exact trimmed,
control-free and bounded to 512 characters; quotes, backslashes and semicolons
are unsupported rather than escaped. Compile/Test reject scenario intent.

The existing interpreter remains unchanged. At actual admission, the current
context supplies identical binding, host definition and actual owner grant.
Only then does `ProcessProgramDefinition` derive an immutable narrower tagged
definition for a class already in the host allowlist. It retains binding,
capability, safe identity, executable, environment, limits and projections.
Only arguments/argument policy and private selection metadata narrow. The
existing empty request site validates grant/finite limits, and generic tagged
execution/caller overrides remain forbidden. The host codec remains class-only.

The existing decoder gains a defaulted optional scenario without changing
`Verified`. It requires agreement with one exact selected class and one test
in a complete ordinary successful summary; selection/count mismatch becomes
`ScenarioMismatch`. Other conservative report refusals remain. The result
retains the exact scenario target. The suite-only report does not independently
name the operation, and parsing cannot authenticate forged logs. Declared
requirements are neither execution grants nor runtime evidence.

## One normalized CML projection

`ValidationCmlMappingV1.Properties` carries the declaration fields with exact
purpose/duration-class strings and typed duration amount/unit. `declarationC`
maps those fields and uses the existing declaration admission. Duration
conversion uses BigInt unit multipliers, rejects nonpositive/overflowed nanos
before constructing FiniteDuration, and accepts Long maximum nanos. Explicit
nulls and unknown labels fail through structured policies. `resolveC` admits
both optional property sets and invokes the existing S1 resolver once. Cozy
retains syntax/parser/generator ownership; there is no duplicate merge logic.

## Portable shared example and consumer boundaries

The one packaged [synthetic resource](../../src/main/resources/META-INF/cncf/validation/v1/consumer-fixture.json)
and [guide](../fixtures/validation-test-metadata/README.md) are common input for
runtime/static consumer understanding. A private typed specification reader
projects declaration sources through the public mapper and queries public
inventory data. It never executes or discovers the synthetic class names and
adds no normative JSON inventory codec or authoritative suite registry.

Complete baseline/candidate scope and population revisions permit a sole
ADMISSION removal to appear as 1 -> 0 while preserving the same operation and
other metadata. PARTIAL/UNKNOWN variants retain gaps and cannot establish
absence. The explicit target feature population includes an uncovered feature;
revisioned feature mappings stay consumer facts. Observed tags never become a
population denominator. Runtime evidence is empty, and CNCF computes no KPI.

The actual static owner is the cbd-support
[October 6 journal](https://github.com/asami/textus-cbd-support/blob/main/docs/journal/2026/10/2026-10-06-validation-test-metadata-static-analysis.md).
Its closed Phase 14 is native Job integration, not metadata ownership. The
[handoff](../notes/validation-test-metadata-consumer-handoff.md) preserves this
workstream and the actual sm-workflow consumer connection obligation.
Static application completion is outside producer acceptance.

## Remaining connected delivery

P103-S1 is accepted in `ac443ed353cc682251e4d17bf4e64a1285955798`.
P103-S2 is accepted in `2ec6da1265d14430cf522443869a0b9b2647ea00`. The in-process
ScalaTest selector case is framework evidence only. S3 authors binding to the
existing typed CNCF path, CML equivalence and shared complete/incomplete static
fixture/handoff. Focused validation passed 79 tests across eight suites;
separate real top-level literal parameter selection passed one test in one
suite (total 80 tests across nine suites, zero failures or aborts, SBT/wrapper
exit 0, shared lock released). Independent protected-focused S3 Step review
returned PASS with zero Current Boundary Blockers, Hygiene or Development
Candidate findings and no evidence gaps. S3 is accepted in local Step commit
`1df07da9be8716edf0e0462bcba221ca8d020c60`.

The actual runtime adapter belongs to sm-workflow Phase 6.2: discovery and
purpose/feature selection feed the same producer exact binder for selected and
supplemental references, followed by native typed execution and exact outcome
consumption. Consumer implementation does not require this producer task to
edit sm-workflow, and neither whole Phase depends on the other's whole closure.
The [handoff](../notes/validation-test-metadata-consumer-handoff.md) records actual
K2 CML and supplementary L3 Slice-plan evidence and isolated consumer acceptance.
That delivery supports exact zero-input scenario selection; acceptance of
separately scoped nonempty typed-input work is not attributed to Phase 103 even
when that work coexists in the current tree. The normalized CML and synthetic
static example contracts above are unchanged. Independent integrated CNCF S4 review
passed; its exact local Step acceptance commit remains pending. Full001 failed and the corrected
observation tests passed focused004; required full002 is running with explicit
Test fork and both native DB flags, with outcome UNKNOWN. Required full
validation, one full Phase review and release remain open; the
[phase checklist](../phase/phase-103-checklist.md) owns current delivery status.
