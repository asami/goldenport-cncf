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

## Remaining connected delivery

P103-S1 is accepted in `ac443ed353cc682251e4d17bf4e64a1285955798`.
P103-S2 authors the discovery/query boundary and real framework specifications;
its parent validation and independent review are pending. The in-process
ScalaTest selector case is framework evidence only. S3 still binds selectors to
the existing typed CNCF execution path and supplies CML equivalence, shared
complete/partial static fixtures and actual runtime/static consumer handoff.
Full Phase validation/review remain open. The
[phase checklist](../phase/phase-103-checklist.md) owns delivery status.
