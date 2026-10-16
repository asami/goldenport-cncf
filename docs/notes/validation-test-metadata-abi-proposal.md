# Validation/Test Metadata ABI Proposal

- Date: 2026-10-06
- Status: Provisional design
- Producer: CNCF
- Consumers: sm-workflow, textus-cbd-support, Cozy/CML projection
- Related: Phase 99 Finding Disposition ABI, Executable Specification tagging rules

## Purpose

Define one CNCF-owned semantic ABI for validation/test metadata attached directly to executable specifications and test programs. The metadata is the source-level declaration of what a validation operation proves and when it should participate in validation. Separate TestSuite registry/YAML files are not the source of truth.

The primary consumers are:

- sm-workflow: runtime discovery, purpose/feature selection, execution evidence and cost guards;
- textus-cbd-support: static Test Architecture analysis, lint, KPI and change review;
- Cozy/CML: model-native properties projected to the same normalized semantic ABI.

## Source-of-truth boundary

Validation metadata belongs with the executable specification/test operation it describes.

~~~text
Executable Specification / test program
  + Validation Metadata
       -> CNCF normalized ABI/discovery
            -> sm-workflow runtime consumer
            -> cbd-support static-analysis consumer
~~~

Hand-written Scala uses CNCF public annotations. CML uses Cozy-owned properties mapped to the same semantic model. Generated Scala is a projection when needed and is not an independent authority when CML is authoritative.

No consumer-specific suite membership file is required for ordinary selection.

## Normalized metadata

The minimum normalized operation-level model is conceptually:

~~~text
ValidationMetadata
  purposes: Set[ValidationPurpose]
  features: Set[ValidationFeature]
  expectedDuration: Option[Duration]
  durationClass: NORMAL | LONG_RUNNING
  executionRequirements: Set[ExecutionRequirement]
~~~

Initial ValidationPurpose values:

- SMOKE
- FOCUSED
- ADMISSION
- FULL
- HEAVY

These are semantic membership values, not an ordinal inclusion hierarchy. One operation may belong to multiple purposes.

CNCF defines the meanings and representation/merge rules. Consumer policy remains outside the ABI. In particular, ADMISSION <= 1 minute and FULL <= 10 minutes are sm-workflow engineering policies, not CNCF constants.

ValidationFeature is extensible project/application data. Adding a feature identifier does not require an ABI version change.

## Annotation granularity

Metadata MUST be attachable at both class/specification level and operation/scenario level.

Operation/scenario is the resolved execution unit and primary selection granularity. Class-level metadata provides defaults/shared context.

Initial merge semantics:

- purposes: operation declaration, when present, overrides the class default; otherwise inherit class purposes;
- features: union class and operation feature sets;
- expectedDuration: operation value overrides class default;
- durationClass: operation value overrides class default;
- executionRequirements: use the CNCF execution-requirement merge/override semantics selected by that contract; do not invent a second provider model here.

The resolved operation metadata MUST be deterministic and queryable without AI.

## Scala representation

Exact annotation names/syntax are implementation-phase decisions, but the public shape should support the equivalent of:

~~~scala
@ValidationFeature("goal-phase")
@ValidationPurposes(Array(ADMISSION, FULL))
class GoalPhaseSpec:
  @ValidationPurposes(Array(SMOKE, ADMISSION, FULL))
  @ExpectedDuration("2s")
  def basicLifecycle(): Unit = ???

  @ValidationPurposes(Array(HEAVY))
  @ExpectedDuration("2h")
  @ValidationDurationClass(LONG_RUNNING)
  def exhaustiveRecovery(): Unit = ???
~~~

The ABI MUST support the same test operation participating in several purposes without duplicated test programs.

## CML representation

CML should expose model-native properties/metadata mapped to the same normalized model. CNCF does not prescribe Cozy parser syntax in this note. Cozy owns source syntax and generation/projection, while CNCF owns normalized semantics and public Scala/runtime ABI.

This follows the Phase 99 Finding Disposition pattern: one semantic model, language/model-native representations.

## Discovery and interpretation

CNCF should expose a deterministic interpretation/discovery API that yields resolved operation-level ValidationMetadata for admitted component/test artifacts. Consumers MUST NOT parse Scala annotation source text independently when a CNCF normalized interpretation is available.

The discovery boundary should preserve stable executable-specification/test-operation identity sufficient for:

- selection;
- static analysis;
- execution evidence;
- duration observations;
- metadata-diff review.

## Consumer boundary

### sm-workflow

sm-workflow owns selection/execution policy:

- SMOKE/ADMISSION/FULL are fixed project-level queries over metadata;
- HEAVY is an explicit project-level query;
- FOCUSED combines FOCUSED membership with Slice-selected feature(s);
- runtime receipts, actual duration, warnings and convergence are sm-workflow evidence, not source annotations.

### textus-cbd-support

cbd-support owns static review/analysis policy:

- missing/invalid metadata;
- feature/purpose coverage shape;
- suspicious FULL/HEAVY bias;
- ADMISSION coverage reduction in a diff;
- duration declaration versus admitted runtime evidence;
- Test Architecture KPI and human-review presentation.

A static-analysis warning does not mutate metadata automatically.

## Static declaration versus runtime evidence

Annotations/properties contain stable declarations/policy only. They MUST NOT store observations such as last runtime, pass/fail, execution count, Candidate revision, warning state or convergence history.

~~~text
source metadata = declaration
runtime datastore/evidence = observation
~~~

## Metadata changes are architecture changes

Changing purposes/features can alter validation coverage without changing test logic. Consumers SHOULD therefore expose metadata deltas explicitly. For example ADMISSION,FULL -> FULL is a reduction of routine admission coverage and is reviewable Test Architecture change.

## Compatibility

The ABI must be versioned. New feature IDs are data, not ABI changes. New optional metadata fields should be additive where possible. Unknown required purpose/semantic values fail closed at admission/interpretation rather than silently changing selection.

## Non-goals

- defining sm-workflow time budgets in CNCF;
- defining cbd-support quality scores in CNCF;
- maintaining a second TestSuite registry;
- runtime execution receipts in annotations;
- AI-dependent metadata interpretation;
- requiring strict SMOKE subset ADMISSION subset FULL subset HEAVY nesting.


## Consumer coordination refinements (2026-10-06)

Implementation plan: [CNCF Phase 103](../phase/phase-103.md). Runtime consumer:
sm-workflow Phase 5/6/7. Static consumer: textus-cbd-support Phase 14, implemented
on Mac mini. These clarifications incorporate that consumer's planning feedback.

### Feature identity and target population

Feature IDs are explicit project/application data with their declared scope and
references. Preserve source mappings to Component/Slice/Capability when provided;
do not guess correspondence from names or test text. Consumers receive an
explicit target feature population/revision from their Component/Slice or policy
input separately from the features mentioned by tests. CNCF discovery must not
pretend that observed test features are a complete application feature inventory.

### Discovery scope and completeness

Discovery describes its admitted artifact/component scope and whether enumeration
is COMPLETE, PARTIAL or UNKNOWN for that scope. Include discovered executable
operations with absent annotations as distinguishable entries, plus bounded
interpretation diagnostics; do not silently filter them out of the population.
COMPLETE enumeration does not mean complete metadata or adequate test coverage.

Invalid/unsupported required semantics must not become an executable normalized
selection. Preserve the affected identity/source and diagnostics for static
review. Consumers distinguish an invalid declaration, absent optional metadata,
an unannotated operation and an incomplete inventory. Missing evidence is not
proof of no tests; no scan outside the declared scope or integrity certificate
is required to establish these typed facts.

### Operation identity, revision and provenance

Return stable scoped operation references separately from their declaring source/
artifact revision and the applicable ABI version. Consumers must be able to join
metadata, execution evidence and before/after architecture changes without
parsing IDs, inventing hashes or equating same-named scenarios across projects.
Changing purpose/feature metadata is visible as a declaration revision/change
without necessarily changing logical operation identity. Parameterized cases and
scenario granularity need an explicit supported mapping during implementation.

### Duration scope and consumer-owned observation

Expected duration describes the resolved operation/scenario in an explicit
Duration representation/unit. A class-level default inherited by operations is
not an aggregate class/query execution estimate. Freeze treatment of units and
measurement boundaries in the producer spec and shared consumer examples.

Measured runtime duration is a separate sm-workflow evidence contract, not a new
observation field in ValidationMetadata. It identifies the measured operation or
query, artifact/revision, environment/input context, measurement interval/scope,
outcome/timeout/retry/attempt and source execution reference. Build, queue, setup
and test-body scopes must not be silently mixed. Unknown granularity remains
unknown; consumers compare only compatible scopes and never allocate query time
to every operation. CNCF owns declaration semantics; sm-workflow owns observed
facts; cbd-support owns comparison/KPI presentation.

### Shared handoff fixture

Provide a complete scoped inventory, explicit target features and baseline/
Candidate metadata where one feature loses its sole ADMISSION membership.
Runtime selection observes the metadata change and applies its coverage policy;
static review can display 1 -> 0 with source/revision/policy references. No runtime
receipt means Unknown verification status. Add partial-inventory and unannotated
operation cases to prevent false absence/coverage claims. This fixture is shared
with both consumers, not a dependency on completing their entire applications.


## Slice validation plans complement declarations

The management-file approach is retained by sm-workflow as a Slice validation
plan. It holds acceptance conditions, metadata selection criteria, explicit
supplemental Test Operation references and typed execution parameters. This is
separate from CNCF metadata declaring each test's purpose, feature and execution
requirements; the plan does not copy or override those declarations.

Normally a plan selects FOCUSED plus feature and supplements uncovered conditions
with operation references. CNCF exposes stable operation identity/typed invocation
contracts for both query and direct-reference resolution; the latter need not
change the operation's purpose membership. Existing execution requirements still
apply. Actual selections/parameters/results belong to runtime Evidence. CNCF
Phase 103 owns the declaration/discovery boundary, not the Slice plan's acceptance
policy, file format or lifecycle.
