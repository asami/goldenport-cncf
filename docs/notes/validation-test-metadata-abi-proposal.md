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
