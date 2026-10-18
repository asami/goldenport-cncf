# Validation/Test Metadata V1

This specification defines declaration admission, normalized operation-level
interpretation and scoped real ScalaTest discovery. The declaration API is
[`ValidationMetadataV1`](../../src/main/scala/org/goldenport/cncf/validation/ValidationMetadataV1.scala);
the behavior contract is
[`ValidationMetadataV1Spec`](../../src/test/scala/org/goldenport/cncf/validation/ValidationMetadataV1Spec.scala).
The [design](../design/validation-test-metadata.md) records responsibility boundaries.

The discovery API is
[`ValidationDiscoveryV1`](../../src/main/scala/org/goldenport/cncf/validation/ValidationDiscoveryV1.scala),
with behavior specified by
[`ValidationDiscoveryV1Spec`](../../src/test/scala/org/goldenport/cncf/validation/ValidationDiscoveryV1Spec.scala)
and [`ValidationDiscoverySelectionSpec`](../../src/test/scala/org/goldenport/cncf/validation/ValidationDiscoverySelectionSpec.scala).

## Public declaration and compatibility

`ABI_VERSION` is the integer `1`. `Declaration` carries `abiVersion`, optional
`purposes`, a `features` set, optional `expectedDuration`, optional `durationClass`,
optional `executionRequirement` and a `requiredSemantics` set. All fields have
empty/absent defaults except `abiVersion`, which defaults to `ABI_VERSION`.
`Declaration.validateC` admits the declaration without rewriting its source facts.
`resolveC(specification, operation)` returns `Consequence[Metadata]` and performs
pure deterministic validation and normalization. Both declarations are validated
before composition; an operation override cannot hide an invalid class default.

Only ABI version `1` is supported. Required semantic names are exactly
`purposes`, `features`, `expectedDuration`, `durationClass` and
`executionRequirement`. A required semantic name declares a compatibility need;
it does not require a value for an optional field. Unknown names, including case
variants, fail admission. There is no inferred minor version or silent fallback.

Purpose values are `SMOKE`, `FOCUSED`, `ADMISSION`, `FULL`, `HEAVY`. Each is an
independent membership: no purpose implies another, including `HEAVY`.
Duration classes are `NORMAL` and `LONG_RUNNING`.

`FeatureReference(scope, identity)` preserves exact opaque scoped data. Both
strings must be nonempty, already trimmed, without control characters and at
most 512 characters. `validateC` does not normalize labels or infer relationships
to Components, Slices or Capabilities. Equal identities with different scopes
remain distinct. New scopes and feature identities are data and do not change
the ABI version. Explicit target feature populations remain consumer input;
the feature set declared by tests is not a complete application inventory.

## Operation normalization

`Metadata` carries purpose and feature sets, optional expected duration, a duration
class, an optional existing execution aggregate and `Origins(specification,
operation)`. Origins record declaration presence, including an explicit empty
declaration; they do not supply source/artifact revision provenance.

| Field | Resolution |
| --- | --- |
| `purposes` | Operation `Some(set)` replaces the specification value, including an empty set; `None` inherits; otherwise empty. |
| `features` | Set union of specification and operation references. |
| `expectedDuration` | Operation value when supplied, otherwise specification value, otherwise absent. |
| `durationClass` | Operation value when supplied, otherwise specification value, otherwise `NORMAL`. |
| `executionRequirement` | Validated canonical aggregate inherited or composed as below. |
| `origins` | Presence of the two admitted declaration options. |

`None`/`None` gives empty purposes/features, absent duration/requirement, `NORMAL`
and `Origins(false, false)`. An explicit empty operation declaration inherits
class fields whose values it does not supply. Only `purposes = Some(Set.empty)`
clears inherited purpose membership; empty feature sets do not clear features.

Expected duration is a `scala.concurrent.duration.FiniteDuration`, strictly
positive and representable as a signed `Long` count of nanoseconds. Resolution
returns a nanosecond-unit `FiniteDuration`, with no saturation or rounding.
Zero and negative values fail. The interpreter checks representability using
exact arithmetic. Duration class and duration override independently.
Scala `FiniteDuration` construction excludes values outside its nanosecond range;
such values are outside this typed ABI's constructible input space. The
Executable Specification exercises `Long.MaxValue` nanoseconds and positive
unit normalization, and rejects constructible zero/negative durations. An
upstream constructor exception is not a CNCF domain rejection.
Duration describes each resolved operation. A class default inherited by several
operations does not become aggregate class/query time. Measured runtime,
queue/setup/body boundaries, attempts, timeout and observations belong to
sm-workflow evidence and are absent from this declaration model.

## Existing execution requirement composition

The singular `executionRequirement` is
`WorkflowProtocolV1.ExecutionRequirement`, an existing aggregate of capability,
risk, reasoning, review and optional execution facts. It is not a second
requirement model or a provider selector. Its existing `validateC` is reused.
Capabilities are canonicalized by exact identity, distinct and sorted.

An absent requirement inherits the other validated canonical requirement. When
both declarations supply requirements:

- Capabilities form a distinct identity-sorted union.
- Reasoning takes the strongest existing value in order `Routine`, `Standard`,
  `Deep`, `Critical`; review is the Boolean OR of `reviewRequired`.
- Opaque `risk` values must equal. No risk ordering is invented.
- Optional `disposition` and `reasoningClass` inherit when only one is present.
  When both are present they must equal in full, including disposition origin.
- An incompatible risk, disposition or reasoning class fails interpretation;
  operation metadata cannot weaken the specification requirement.

## Admission failures

Explicit nulls are malformed values, never absence: boundary options, `Some`
members, declaration sets, set elements, enum members, durations, feature
references and existing execution requirement facts are admitted without leaking
`NullPointerException` or `MatchError`. Optional absent values are valid.
Independent validation uses Consequence's applicative facilities so both
declarations are checked before any merge.

Known failures use `Consequence.argumentPolicyViolation` with the relevant
parameter/field and a stable policy identifier:

| Policy suffix under `validation.metadata.` | Meaning |
| --- | --- |
| `null` | Malformed option or explicit null option member. |
| `declaration` | Null declaration at internal declaration admission. |
| `abi` | Unsupported `abiVersion`. |
| `required-semantics` | Null required-semantic set or unsupported required name. |
| `purposes` | Null purpose member. |
| `feature` | Null feature set/reference or invalid scoped feature text. |
| `duration` | Nonpositive or unrepresentable nanosecond duration. |
| `execution-requirement` | Existing requirement validator rejects the supplied aggregate. |
| `execution-requirement.risk` | Incompatible opaque risk values. |
| `execution-requirement.disposition` | Incompatible optional disposition values. |
| `execution-requirement.reasoningClass` | Incompatible optional reasoning classes. |

Failure diagnostics are structured domain outcomes, with no string-only error
envelope, thrown declaration-validation exception or extension of the core API.

## Runtime annotations and typed providers

The Java runtime annotations in `org.goldenport.cncf.validation.annotation` are
usable directly from Scala 3. `ValidationSpecification` targets types, is
inherited, and has `identity = ""`, `revision = ""` and
`declaration = classOf[java.lang.Void]` defaults. `ValidationScenario` targets
methods and requires `name`; its identity/revision/declaration defaults are the
same. These are Java annotation interfaces with runtime retention.

```scala
import org.goldenport.cncf.validation.ValidationDeclarationProvider
import org.goldenport.cncf.validation.ValidationMetadataV1.*
import org.goldenport.cncf.validation.annotation.*
import org.scalatest.funsuite.AnyFunSuite

class OrderDefaults extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(
    purposes = Some(Set(Purpose.ADMISSION, Purpose.FULL)),
    features = Set(FeatureReference("component:orders", "approval")))
}
class HeavyOrderCase extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.HEAVY)))
}
@ValidationSpecification(identity = "orders:approval", declaration = classOf[OrderDefaults])
class OrderSpec extends AnyFunSuite {
  test("approval case 1") { checkApproval(1) }
  test("approval case 2") { checkApproval(2) }

  @ValidationScenario(name = "approval case 1", identity = "approval:1")
  def firstAnchor(): Unit = ()

  @ValidationScenario(name = "approval case 2", identity = "approval:2",
    declaration = classOf[HeavyOrderCase])
  def secondAnchor(): Unit = ()

  def checkApproval(caseNumber: Int): Unit = { require(caseNumber > 0) }
}
```

An annotation's empty identity/revision explicitly requests the fallback below;
nonempty values must be exact trimmed, control-free text of at most 512
characters. `Void.class` supplies no declaration. An explicit provider returning
`Declaration()` supplies an empty declaration, preserving S1 origins. Merely
having an identity anchor does not assign purpose membership or create a
declaration origin. No Scala source or annotation JSON is parsed.

A provider is a public concrete subclass of `ValidationDeclarationProvider`
with a public no-arg constructor. Its code source must match the selected local
artifact containing the executable suite. Type, constructor and provenance are
admitted before construction; `declaration` is called once per interpreted
annotation source. Invalid type/constructor/provenance, a thrown provider
exception, null value or an S1-invalid declaration yields bounded diagnostics
and invalid metadata. A bad specification provider invalidates every actual
operation even when a scenario would replace its fields. A bad scenario provider
invalidates that operation. Valid composition always calls the S1 resolver.

Scenario anchors are effective public instance no-arg `Unit`/void methods from
`getMethods`, including effective inherited methods. An unannotated override
does not acquire the replaced method's annotation. `name` must equal one actual
registered full ScalaTest test name. Orphans, duplicate anchors for a name and
invalid method shapes invalidate the containing suite's actual operations;
they never create a nonexistent operation. Anchors bind metadata only and their
methods are not invoked. Parameterized cases use distinct concrete registrations
and optional distinct helper anchors, delegating bodies as in the example.
WordSpec anchors name full names such as `orders should approve case 1`.

## Explicit local discovery scope and completeness

`discoverC(scope, loader)` returns `Consequence[Inventory]` from caller-admitted
local artifacts and one caller-owned `ClassLoader`. `DiscoveryScope(identity,
revision, artifacts)` contains a `Vector[ArtifactScope]`; each artifact supplies
`componentIdentity`, `artifactIdentity`, `artifactRevision`, `sourceRevision`,
`root: Path` and `packagePrefix`. Roots are canonical absolute existing
non-symlink local directories or regular JARs. Namespaces are nonempty Java
package names without wildcards/traversal. Enumeration visits `.class` entries
only in that package and descendants, without JAR extraction, remote access or
classpath-wide membership inference. Streams and JAR files close on all paths.
Duplicate `(componentIdentity, artifactIdentity)` keys are rejected.

Class loading uses `Class.forName(name, false, loader)`. Discovery supports
public concrete no-arg flat ScalaTest `Suite`s, including actual `AnyFunSuite`
and `AnyWordSpec`. Ordinary classes and abstract suites are ignored. Executable
suite code source must match its admitted artifact, so a parent-loader shadow
cannot be claimed as content of a JAR. An admitted suite is constructed once to
read its real `testNames` and `nestedSuites`; test bodies do not run. Nonpublic,
nonconstructible or nested suites are explicitly unsupported and make enumeration
partial. Discovery of trusted local suites/providers can run constructor,
registration and provider code; it is not an untrusted-code sandbox.
Production never creates, configures, closes or globally retains the loader.

| Completeness | Meaning within the explicit artifact/package scope |
| --- | --- |
| `COMPLETE` | Every admitted namespace was successfully enumerated and supported suites inspected, including an explicit empty scope or successfully empty namespace. |
| `PARTIAL` | Some enumeration is usable, but traversal/loading/linkage/constructor/unsupported-suite failures leave a gap. |
| `UNKNOWN` | No nonempty artifact scope could be enumerated, for example a scope consisting solely of an invalid/unreadable JAR. |

Invalid metadata alone does not reduce completeness of executable enumeration.
Completeness says nothing about all application tests, feature populations or
adequacy of declared metadata. Scope diagnostics survive empty inventories and
queries. Diagnostics have fixed known codes `enumeration`, `loading`,
`provenance`, `unsupported-suite`, `suite-construction`, `annotation`, `anchor`,
`provider`, `metadata`, `identity`, `ambiguity`, `truncated`. Each source/message
is control-free and at most 512 characters. At most 32 diagnostics per artifact
and 256 overall are retained, including operation diagnostics; an explicit final
truncation diagnostic reports omitted details. Raw exception text and stacks
are not exposed.

## Identity, provenance and actual operation rows

`OperationReference(component, specification, operation)` is a logical reference.
Component comes from the artifact scope. Specification is the annotation's
identity or the suite binary class name; operation is its scenario annotation's
identity or exact registered full test name. IDs contain neither revisions nor
hashes. Equal names in different components are different references.

`Provenance(artifactIdentity, artifactRevision, sourceRevision,
operationRevision, abiVersion = 1)` preserves independent explicit revisions.
An operation revision uses scenario revision, otherwise specification revision,
otherwise artifact revision. Revision-only changes leave logical IDs stable.
Malformed annotation identity/revision text invalidates interpretation, retaining
the real class/test name fallback for inspection.

Each `Operation` has reference, provenance,
`ScalaTestSelector(suiteClassName, testName)`, optional normalized `metadata` and
diagnostics. Selectors have framework coordinates only. Invalid interpretation
retains actual rows with `metadata = None`; valid unannotated rows have
`Some(empty Metadata)` and `Origins(false, false)`. Duplicate logical references
retain all real rows and invalidate metadata on every collision. Neither suite
instances, loaders, callbacks nor runtime functions enter inventory equality.
Rows sort by component/specification/operation, then artifact identity and suite/
test selector. The returned scope orders artifacts by component/artifact identity,
so artifact input order does not alter inventory equality or row/diagnostic semantics.

## Pure queries and supplemental resolution

`SelectionQuery(purposes = Set.empty, features = Set.empty)` supplies typed
dimensions to `selectC(inventory, query): Consequence[Vector[Operation]]`.
Matching is OR within purposes, OR within features, AND between dimensions.
An empty dimension adds no filter. Thus FOCUSED with `{featureA, featureB}`
selects FOCUSED operations covering either scoped reference, without assigning
extra purpose memberships. Only valid unambiguous normalized operations are
returned, in deterministic order. Empty queries include valid unannotated rows.

`resolveC(inventory, reference): Consequence[Operation]` requires one exact,
present, valid, unambiguous logical reference. Supplemental references can bind
an unannotated operation or a purpose outside a query; resolution preserves
metadata and execution requirements. Absent (`validation.discovery.absent`),
invalid (`validation.discovery.invalid`) and ambiguous
(`validation.discovery.ambiguous`) are distinct structured policies. Queries
perform no artifact I/O or loader actions. They do not reinterpret an empty
selection from PARTIAL/UNKNOWN scope as evidence of missing coverage; the source
inventory retains its completeness and diagnostics.

Public boundaries reject null and malformed scopes, inventories, queries,
references, selectors, diagnostics and unsupported provenance ABI through
`Consequence.argumentPolicyViolation` policies under `validation.discovery.`.
Identity/revision and test-name text is exact nonempty trimmed control-free
text of at most 512 characters; suite/package names follow Java binary/package
grammar. No generic record replaces these fixed public models.

## Exact typed operation binding

[`ValidationOperationBindingV1.bindC(inventory, reference, workspaceId)`](../../src/main/scala/org/goldenport/cncf/validation/ValidationOperationBindingV1.scala)
uses the existing public exact `ValidationDiscoveryV1.resolveC` and returns
`Consequence[BoundOperation]`. The final `BoundOperation` has a private
constructor and public `operation: ValidationDiscoveryV1.Operation` and
`intent: UnitOfWorkOp.SbtExec`. Its operation is unchanged, including metadata,
provenance, requirements and diagnostics. Its intent is
`SbtExec(SbtTarget(workspaceId, Some(SbtScenarioSelection(class, name))), TestOnly)`.
The logical typed workspace must be nonnull and admitted, and the framework
selector must satisfy the [typed SBT literal selection contract](typed-sbt-execution.md).
Unsupported literal rendering, null and malformed inputs fail structurally.

Query-selected and supplemental exact references use this identical method.
A supplemental HEAVY or valid unannotated operation keeps its original purposes,
declaration origins and `ExecutionRequirement`. Absent, invalid and ambiguous
references retain existing distinct discovery policies. Binding constructs
intent only: it does not launch, resolve a runtime context or grant authority.

The existing interpreter uses its actual current UnitOfWork context to obtain
Manage for the identical workspace binding and an actual component TestOnly
capability grant. The selected class must be in that current host allowlist;
the definition narrows and never expands it. One final argv item requests the
exact class/name. Compile/Test reject scenarios and `None` keeps class-only
behavior. Generic tagged execution and caller request overrides remain denied;
no host codec, request-construction site or interpreter change is introduced.

A scenario result retains the exact target and requires the conservative
ordinary successful report for one matching class and exactly one test.
`ScenarioMismatch` rejects selected-class/count disagreement. Other malformed,
truncated, wrong-suite and failed reports remain unverified. `Verified` retains
its three existing fields. A suite-only receipt does not independently name the
operation: the producer typed intent requests it. Parsing cannot authenticate
forged logs. Declarations and bound operations contain no runtime evidence.
Behavior is authored by
[`ValidationOperationBindingV1Spec`](../../src/test/scala/org/goldenport/cncf/validation/ValidationOperationBindingV1Spec.scala),
[`SbtScenarioExecutionDslSpec`](../../src/test/scala/org/goldenport/cncf/action/SbtScenarioExecutionDslSpec.scala)
and the existing selected-report specification.

## Normalized CML mapping

[`ValidationCmlMappingV1`](../../src/main/scala/org/goldenport/cncf/validation/ValidationCmlMappingV1.scala)
owns `Properties(abiVersion = 1, purposes = None, features = Set.empty,
expectedDuration = None, durationClass = None, executionRequirement = None,
requiredSemantics = Set.empty)`. Purposes and duration class use exact strings;
all other field types reuse the declaration model, except duration which uses
`DurationValue(amount: Long, unit: DurationUnit)`.

`DurationUnit` is the closed enum `NANOSECONDS`, `MICROSECONDS`, `MILLISECONDS`,
`SECONDS`, `MINUTES`, `HOURS`, `DAYS`. `declarationC(properties)` (also available
on `Properties`) admits every field through the existing declaration validator
and returns the same validated `Declaration`. No case folding, trimming or
unknown-label fallback occurs. Explicit nulls and unsupported enum labels yield
structured `validation.cml.<field>` failures; existing declaration compatibility
and requirement failures retain their metadata policies.

Positive duration conversion multiplies with `BigInt` before constructing
`FiniteDuration`. Exact nanoseconds above `Long.MaxValue`, zero and negative
amounts fail without overflow, saturation, rounding or constructor exceptions.
`Long.MaxValue` nanoseconds is valid. This normalized boundary can therefore
reject out-of-range amounts that cannot enter the direct Scala duration ABI.

`resolveC(specification: Option[Properties], operation: Option[Properties])`
maps both declarations, then calls the existing S1 resolver once. Absent/empty
origins, explicit purpose replacement, feature union, independent duration/class
overrides and existing requirement capability/reasoning/review composition have
the identical semantics described above. No CML parser, syntax, generator or
second merge/provider policy belongs to CNCF. Equivalence and property behavior
are authored by
[`ValidationCmlMappingV1Spec`](../../src/test/scala/org/goldenport/cncf/validation/ValidationCmlMappingV1Spec.scala).

## Shared portable static-consumer example

One packaged resource,
[`META-INF/cncf/validation/v1/consumer-fixture.json`](../../src/main/resources/META-INF/cncf/validation/v1/consumer-fixture.json),
is synthetic static-analysis input. It is not a suite registry, an actual
discovered runtime inventory, execution authority or a normative inventory wire
codec. The [shared guide](../fixtures/validation-test-metadata/README.md) maps
its fields to public models and consumer responsibilities. The
[`ValidationConsumerFixtureV1Spec`](../../src/test/scala/org/goldenport/cncf/validation/ValidationConsumerFixtureV1Spec.scala)
parses that resource with a private typed example reader and observes public
normalization/query/reference outcomes.

Complete baseline/candidate snapshots explicitly share a comparable scope and
population revision, with independent scope/artifact/source/operation revisions.
Their three rows are a sole ADMISSION+FULL operation, a FOCUSED operation covering
two features and a valid unannotated operation. Candidate removes only ADMISSION
from the same logical operation, preserving its selector and other metadata;
its source/artifact/changed-operation revisions advance. Complete comparable
ADMISSION selection is 1 -> 0. PARTIAL/UNKNOWN variants retain diagnostic gaps;
empty selections there cannot establish absence.

The target feature population is explicit and revisioned, includes an uncovered
feature, and stays separate from observed tags. Mappings carry their own and
target revisions. Runtime evidence is empty. The example computes no KPI and
asserts no runtime success. Consumers own comparability, application feature
population/mappings, observations, findings and execution/progression policy.
The static owner is cbd-support's
[October 6 static-analysis workstream](https://github.com/asami/textus-cbd-support/blob/main/docs/journal/2026/10/2026-10-06-validation-test-metadata-static-analysis.md),
not its closed native Job Phase 14. See the
[consumer handoff](../notes/validation-test-metadata-consumer-handoff.md).

## Phase boundary

P103-S1 is accepted at `ac443ed353cc682251e4d17bf4e64a1285955798`.
P103-S2 is accepted at `2ec6da1265d14430cf522443869a0b9b2647ea00` with annotations,
actual discovery, typed selectors and pure queries. The selector specification
demonstrates in-process `Suite.run(Some(name),
Args(reporter))` and exactly one parameter-case success. That demonstrates the
framework selector, without claiming CNCF typed process invocation.
S3 authors the existing CNCF execution-path adapter, CML mapping, shared static
fixture and consumer handoff. Focused validation passed 79 tests across eight
suites; separate real top-level literal parameter selection passed one test in
one suite (total 80 tests across nine suites, zero failures or aborts,
SBT/wrapper exit 0, shared lock released). Independent protected-focused S3
Step review returned PASS with zero Current Boundary Blockers, Hygiene or
Development Candidate findings and no evidence gaps. S3 is accepted in local
Step commit `1df07da9be8716edf0e0462bcba221ca8d020c60`. Actual sm-workflow source fixture/dependency
refresh remains required separate S4 under explicit source admission after the
producer is accepted. Full connected producer validation and
[Phase 103](../phase/phase-103.md) acceptance remain open. Static-consumer
application implementation remains outside producer acceptance.

No pass/fail, actual runtime, Candidate state, hashes, executor arguments, KPI,
suite registry or sm-workflow time-budget policy belongs to this ABI.
