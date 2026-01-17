Spec Style Rules
==================================

status=published
published_at=2025-12-26

# PURPOSE
This document defines the standard style for writing Executable Specifications
in SimpleModeling.

- This document defines the standard style for writing Executable Specifications
- in SimpleModeling.

The goal is to ensure that Executable Specifications are:
- Readable as documents
- Executable as tests
- Authoritative as design artifacts

These rules apply to **Executable Specifications**.

Given / When / Then is treated as a semantic description DSL
for Executable Specifications and is independent of any BDD/TDD interpretation.

---

# CORE PRINCIPLE

A specification in SimpleModeling is not a test of execution,
but a **description of semantic interpretation**.

Specifications define *what a model means*,
not *what a system does at runtime*.

---

# STRUCTURE

Specifications MUST be written using:

- `AnyWordSpec`
- `Matchers`
- `GivenWhenThen`

This combination provides:
- Natural-language structure
- Clear assertions
- Executable specification logging

---

# EXECUTABLE SPECIFICATION MODEL

Executable Specification is the integration of specification text and executable verification that defines semantic behavior for the project.
It pairs normative rules with runnable tests so that documentation and automation stay synchronized.

There are two kinds of Executable Specifications.

- **Bottom-up (specification/structure-driven)** specifications focus on individual interfaces or components and describe their semantic responsibilities through hierarchical `should` / `when` / `should` blocks.
- **Top-down Scenario Specifications** focus on higher-level flows or scenarios and coordinate multiple components to describe an end-to-end behavior.

Scenario Specifications MUST be placed under the `SCENARIO` package to clearly distinguish their role in describing use cases rather than individual interfaces.

Given / When / Then is the canonical semantic description style for Executable Specifications.
It is a description DSL and not a classification of specification type; both bottom-up and scenario specifications share the same DSL.

## Executable Specification Display and Tagging

Executable Specifications MUST follow the display, Given/When/Then, DSL keyword, and tagging conventions defined in `docs/rules/executable-spec-display-and-tagging-rules.md`.
That document defines the mandatory presentation and machine-readable metadata rules that ensure each Executable Specification remains identifiable and traceable.

---

## CANONICAL EXECUTABLE SPECIFICATION MODEL

---
### Completion Forms of Executable Specifications

Executable Specifications may be completed in one of two valid forms.

#### 1. Specification-Document–Backed Form

- A written specification document exists (e.g., design or normative spec).
- Executable Specifications serve as executable confirmation of that document.
- Bidirectional references between the specification document and the Executable Specifications MAY exist.

#### 2. Executable-Only Form

- No separate written specification document exists.
- Executable Specifications themselves constitute the complete and authoritative specification.
- Given / When / Then clauses define the entire normative behavior.

Both forms are equally valid.

The absence of a written specification document does not reduce
the normative status, authority, or contractual role of an Executable Specification.

---

### 1. Normativity

Executable Specifications express normative behavior using `must`, `should`, and `can`.
`must` marks mandatory rules, `should` marks expected behavior, and `can` marks permitted or optional behavior.
Specification documents are authoritative; Executable Specifications serve as executable confirmation of those rules.

### 2. Metadata Binding

All specification metadata (spec name, example ID, rule IDs, phase) is encoded via `afterWord` annotations.
`taggedAs` MUST NOT be used when binding metadata because `afterWord` statements are the canonical binding mechanism.
The standard metadata format is:

```
in spec:<spec-name>, example:<example-id>, rules:<R1,R2,...>, phase:<phase>
```

### 3. Semantic Structure

Given / When / Then is the semantic display DSL for Executable Specifications.
Tests SHOULD use ScalaTest `GivenWhenThen` and describe each clause explicitly; they MAY be implemented through comments or descriptive text.
No specification clause should return values or embed code blocks that hide the semantic narrative.

### 4. Canonical Example

`PathResolutionSpec`'s E1 example illustrates the canonical model: the example title surfaces the Example ID, the `afterWord` encodes the metadata, and the body presents clear Given / When / Then reasoning.
All other examples MUST follow this form.

# SPEC AS TABLE OF CONTENTS

In SimpleModeling, specification files serve as **specification chapters**, organizing the semantic interpretation domain into a structured, living document.

- The spec file itself represents a chapter that covers a broad semantic responsibility or model component.
- Top-level `should` blocks define **sections** describing public responsibilities or contracts of the model or system.
- Nested `when` / `should` blocks act as **subsections**, organizing input dimensions such as positional arguments, switches, or properties that affect interpretation.
- `in` blocks are the smallest executable specification units, representing individual semantic assertions or acceptance/rejection rules.

This hierarchy reflects the semantic interpretation structure, emphasizing clarity and maintainability in executable specifications.

## HIERARCHY RULES

1. A specification file corresponds to a **specification chapter** capturing a cohesive semantic domain.
2. The first-level structure (`should` blocks) defines the **public API or semantic responsibility** of the model.
3. The second-level structure (`when` / nested `should`) organizes **input or interaction dimensions** affecting interpretation.
4. The third-level structure (`in` blocks) specifies **acceptance or rejection rules**, the executable semantic contracts.

### Example ProtocolEngine Spec TOC

```
ProtocolEngineSpec
├─ should "handle basic protocol messages"
│  ├─ when "receiving a connect message"
│  │  ├─ should "accept valid connection requests"
│  │  └─ should "reject invalid connection requests"
│  └─ when "receiving a disconnect message"
│     └─ should "close the connection gracefully"
└─ should "manage session state"
   ├─ when "session is active"
   │  └─ should "maintain correct session data"
   └─ when "session expires"
      └─ should "clean up resources"
```

This structured approach ensures that specifications remain **semantic interpretations**, not runtime tests, and serve as **living documentation** that clearly communicates model meaning and responsibilities.

---

# TWO KINDS OF SPECIFICATIONS

SimpleModeling recognizes two distinct styles of specifications, each answering different semantic questions and serving complementary purposes. These **MUST NOT** be mixed within the same specification file. Both styles must adhere to the Spec-as-TOC hierarchy rules and remain executable semantic interpretations.

## INTERFACE-DRIVEN (BOTTOM-UP) SPECIFICATIONS

**Purpose:**  
To specify the semantic contracts of individual model components or interfaces, defining their meaning in isolation and how they interpret inputs.

**Starting Point:**  
A single model interface, service, or operation under test.

**What is Specified:**  
The detailed semantic interpretation rules of the interface’s inputs, outputs, and failure modes.

**Typical Subjects:**  
- `ProtocolEngine`  
- `Operation`  
- `Service`  

**Given / When / Then Interpretation:**  
- **Given:** Model definitions, interface setup, and preconditions without execution or side effects.  
- **When:** Invocation of the interface or operation semantics (e.g., method calls).  
- **Then:** Assertions on the semantic result, including success or failure interpretations.

**Hierarchy Usage:**  
- Top-level `should` blocks define major interface responsibilities or contracts.  
- Nested `when` / `should` blocks organize input variations or parameter dimensions.  
- `in` blocks specify acceptance or rejection rules for particular semantic cases.

**Example Skeleton:**

```scala
class OperationSpec extends AnyWordSpec with GivenWhenThen {
  "Operation" should {
    "accept valid parameters" when {
      "given a positive integer" should {
        "return success" in {
          given("an operation with positive integer input")
          when("the operation is interpreted")
          then("the result is a successful semantic object")
        }
      }
      "given a negative integer" should {
        "reject the input" in {
          given("an operation with negative integer input")
          when("the operation is interpreted")
          then("the result is a failure semantic object")
        }
      }
    }
  }
}
```

---

## USECASE-DRIVEN (TOP-DOWN) SPECIFICATIONS

**Purpose:**  
To specify the semantic interpretation of complete use cases or scenarios, describing how multiple model components collaborate to fulfill a business or domain-level intention.

**Starting Point:**  
A high-level use case or scenario representing a user or system goal.

**What is Specified:**  
The overall semantic flow, including interaction sequences, input conditions, and expected outcomes.

**Typical Subjects:**  
- `Usecase`  
- `Workflow`  
- `Scenario`  

**Given / When / Then Interpretation:**  
- **Given:** Contextual setup describing the scenario environment and preconditions.  
- **When:** The triggering event or semantic request initiating the use case interpretation.  
- **Then:** Assertions on the final semantic state or outcome of the use case interpretation.

**Hierarchy Usage:**  
- Top-level `should` blocks define major use case responsibilities or goals.  
- Nested `when` / `should` blocks organize scenario variants or system states.  
- `in` blocks specify semantic acceptance or rejection criteria for each scenario step or outcome.

**Example Skeleton:**

```scala
class UsecaseSpec extends AnyWordSpec with GivenWhenThen {
  "Usecase" should {
    "process a valid request" when {
      "the user is authenticated" should {
        "complete successfully" in {
          given("a logged-in user and a valid request")
          when("the use case is executed")
          then("the semantic interpretation succeeds")
        }
      }
      "the user is not authenticated" should {
        "reject the request" in {
          given("an unauthenticated user and a valid request")
          when("the use case is executed")
          then("the semantic interpretation fails")
        }
      }
    }
  }
}
```

---

## COMPARISON TABLE

| Aspect               | Interface-Driven (Bottom-Up)               | Usecase-Driven (Top-Down)                   |
|----------------------|-------------------------------------------|---------------------------------------------|
| **Purpose**          | Specify semantics of individual interfaces | Specify semantics of complete use cases     |
| **Viewpoint**        | Component-level, isolated interface semantics | Scenario-level, holistic domain semantics    |
| **Granularity**      | Fine-grained, detailed input/output rules  | Coarse-grained, end-to-end semantic flows   |
| **Stability**        | More stable, tied to interface contracts   | More volatile, reflecting evolving use cases|
| **Naming**           | Named after interfaces or operations        | Named after use cases or scenarios           |

---

Both kinds of specifications are **executable semantic interpretations** that answer different questions about the model meaning. They provide complementary perspectives and together ensure comprehensive semantic documentation.

---

# Executable Specs in CNCF

## Spec kinds

- Unit specs: narrow, component-local behavior
- Route specs: wiring and boundary traversal
- Scenario specs: primary, end-to-end CNCF behavior

Primary scenario example:
- ArgsToStringScenarioSpec

## Scenario-first guidance

- Scenario specs may start with stubs.
- Scenario specs are refined incrementally.
- This staged approach is intentional and encouraged in CNCF.

## Scenario Spec as Executable Requirement

Scenario Specs are executable requirements validating externally observable CNCF behavior at the system boundary (Protocol → Component → Job / Result).  
Scenario Specs MUST NOT depend on internal mechanisms, classes, or execution strategies.

### Placement and Package Rule (MUST)

- Ordinary Executable Specs (unit / component-level) MUST reside in the same package as their production code. This is governed by the general `PACKAGE PLACEMENT RULE`.
- Requirement-level Scenario Specs MUST be placed under a dedicated `SCENARIO` package.
- The use of the uppercase package name `SCENARIO` is intentional to emphasize semantic distinction from ordinary specs.
- The `SCENARIO` package is reserved exclusively for requirement-level specifications.

### Naming and Structure Rules (MUST)

- Scenario Spec class names MUST end with `ScenarioSpec`.
- Scenario Specs MUST use Given / When / Then structure.
- Scenario Specs MUST assert only public inputs and outputs (Args / Request in, JobId / Status / Result out).

### Prohibited Dependencies (MUST NOT)

- Scenario Specs MUST NOT reference internal classes (e.g. JobRecord, InMemoryJobEngine).
- Scenario Specs MUST NOT assert on execution mechanisms, timing precision, thread counts, or log output.
- Scenario Specs MUST NOT bypass Protocol ingress/egress for result observation.

## Scenario Spec as Executable Requirement

Scenario Specs are executable requirements validating externally observable CNCF behavior at the system boundary (Protocol → Component → Job / Result).  
Scenario Specs MUST NOT depend on internal mechanisms, classes, or execution strategies.

### Placement and Package Rule (MUST)

- Ordinary Executable Specs (unit / component-level) MUST reside in the same package as their production code. This is governed by the general `PACKAGE PLACEMENT RULE`.
- Requirement-level Scenario Specs MUST be placed under a dedicated `SCENARIO` package.
- The use of the uppercase package name `SCENARIO` is intentional to emphasize semantic distinction from ordinary specs.
- The `SCENARIO` package is reserved exclusively for requirement-level specifications.

### Naming and Structure Rules (MUST)

- Scenario Spec class names MUST end with `ScenarioSpec`.
- Scenario Specs MUST use Given / When / Then structure.
- Scenario Specs MUST assert only public inputs and outputs (Args / Request in, JobId / Status / Result out).

### Prohibited Dependencies (MUST NOT)

- Scenario Specs MUST NOT reference internal classes (e.g. JobRecord, InMemoryJobEngine).
- Scenario Specs MUST NOT assert on execution mechanisms, timing precision, thread counts, or log output.
- Scenario Specs MUST NOT bypass Protocol ingress/egress for result observation.

---

# CML AND SCENARIO SPEC OPERATIONS

## ROLE OF CML

CML (Canonical Modeling Language) serves as the canonical source of truth for usecases and their slices. It defines the authoritative semantic structure and decomposition of usecases that Scenario Specifications must align with.

## CANONICAL SCENARIO SPECS (CML-BASED)

Canonical Scenario Specifications are named following the rule `<Usecase><Slice>ScenarioSpec`. Each ScenarioSpec must match the slice name exactly as defined in CML. The presence of a matching ScenarioSpec is required to consider the slice fully specified. Missing ScenarioSpecs indicate incomplete semantic documentation and must be addressed to maintain authoritative interpretation.

## PROVISIONAL SCENARIO SPECS (BOTTOM-UP)

Provisional Scenario Specifications are exploratory or hypothesis-driven specifications created to investigate semantic interpretations or propose new slices. They are explicitly marked either by the `ProvisionalScenarioSpec` suffix or by placement within a `provisional` subpackage. These specs are non-authoritative and serve as living drafts until promoted or discarded.

## PROMOTION AND LIFECYCLE

Promotion from provisional to canonical status occurs when a provisional ScenarioSpec is reviewed, accepted, and its semantics are incorporated into the CML slice definition. The lifecycle steps are:

- Provisional ScenarioSpec is created for exploration.
- Upon approval, the corresponding CML slice is defined or updated.
- A canonical ScenarioSpec named `<Usecase><Slice>ScenarioSpec` is created or updated to match CML.
- The provisional ScenarioSpec is deleted or archived if rejected.

This process ensures that canonical Scenario Specifications remain the authoritative semantic interpretations aligned with the CML model.

```
ProvisionalScenarioSpec
        ↓ promote
   CML Slice Definition
        ↓ generate/update
 Canonical ScenarioSpec
        ↓ delete if rejected
```

---

# GIVEN / WHEN / THEN SEMANTICS

The `GivenWhenThen` DSL is mandatory for top-level behavior descriptions.

Each clause has a **SimpleModeling-specific meaning**:

## GIVEN
Describes:
- Model definitions
- Service / operation specifications
- Preconditions for interpretation

`given` MUST NOT:
- Perform execution
- Contain side effects
- Depend on runtime state

---

## WHEN
Describes:
- A semantic request
- Interpretation invocation (e.g. `makeRequest`)

`when` MUST:
- Express *meaningful intent*
- Avoid execution semantics

---

## THEN
Describes:
- The semantic result
- The resolved model object
- Success or failure of interpretation

`then` MUST:
- Assert on model structure or type
- Treat failure as a valid specification outcome

`then` MUST NOT:
- Execute operations
- Perform IO
- Validate runtime behavior

---

# LOGGING AND OUTPUT

The text passed to `given`, `when`, and `then` MUST be written
as human-readable specification sentences.

These sentences are considered part of the specification itself,
since they appear in test execution output.

---

# FAILURE AS SPECIFICATION

Failure cases are first-class specifications.

Specifications SHOULD explicitly define:
- Missing services
- Unknown operations
- Invalid parameter interpretations

A failure result is a **valid semantic outcome**.

---

# STYLE RULES

## MUST
- Use `GivenWhenThen`
- Use explicit `given / when / then` blocks
- Keep one semantic contract per test case
- Assert only on semantic results

## SHOULD
- Keep setup inline within `given`
- Use descriptive sentences in DSL calls
- Treat specs as documentation first

## MUST NOT
- Use `beforeEach` for hidden setup
- Test execution or side effects
- Mix runtime concerns into specifications

---

## PACKAGE PLACEMENT RULE

Executable Specifications **MUST** be placed in the same package as the production code they specify.

This rule treats specifications as first-class semantic definitions rather than auxiliary test artifacts.

### RATIONALE

- Co-locating specs with production code makes the semantic responsibility explicit.
- Readers (human or AI) can discover authoritative behavior by navigating the package structure alone.
- This avoids artificial separation such as `*.spec` packages that obscure ownership and intent.

### EXAMPLES

```scala
// Production code
package org.goldenport.protocol.handler.projection

class McpGetManifestProjection { ... }
```

```scala
// Executable Specification
package org.goldenport.protocol.handler.projection

class McpGetManifestProjectionSpec
  extends AnyWordSpec
     with Matchers
     with GivenWhenThen {
  ...
}
```

Specifications placed in `*.spec` or parallel test-only packages are considered **legacy layout** and SHOULD be migrated when touched.

---

# EXAMPLES

Typical Executable Specifications include:
- `CliEngineSpec`
- `CliLogicSpec`
- `OperationDefinitionSpec`

These specs define the authoritative behavior
of the model interpretation layer.

---

## Related Operational Rules

`docs/rules/specification-linking-and-rule-numbering-rules.md` defines the operational conventions that apply to Executable Specifications when they are paired with design documents and normative specifications.  
It is the applied rule set that builds on the style defined in this document.

---


# MARKDOWN FOR AI-ASSISTED DOCUMENTATION

Program-internal documents intended for AI-assisted development, design reasoning, semantic analysis, or MCP/RAG interaction **MUST** be written in Markdown.

## RATIONALE

Markdown provides a structured, widely supported, and diffable format that is compatible with AI language models, semantic tooling, and collaborative workflows. Its plain-text nature enables effective version control, easy review, and reliable parsing for automated or AI-assisted processes.

## SCOPE

This rule applies to internal documents such as specifications, rules, design notes, TODO lists, and any artifact intended for AI-assisted development, semantic analysis, or MCP/RAG workflows.  
It **explicitly excludes** public-facing SmartDox documents, which are governed by separate authoring and publication rules.

## FRONT MATTER

YAML Front Matter **MAY** be used for document metadata.  
Front Matter is ignored by Markdown renderers and does not affect document semantics.

# NOTES

This style intentionally differs from traditional BDD.
While the structure is similar, the intent is semantic,
not behavioral or execution-oriented.

## Hardcoded Behavior Preservation (Normative)

Executable Specifications MUST preserve the observable behavior of any previously
hard-coded, implicit, or special-cased logic that is removed, generalized, or replaced
by generic mechanisms.

When such hard-coded behavior is eliminated from the implementation, there MUST exist
at least one corresponding Executable Specification Example that captures the same
semantic behavior using only the current generic logic.

This rule is normative and defines the contractual role of Executable Specifications:

- Code MAY change.
- Implementation strategies MAY change.
- Executable Specification Examples MUST remain as behavioral contracts.

An Executable Specification Example corresponding to removed hard-coded behavior MUST:

- Describe the original conditions using Given.
- Exercise only the current generic mechanism using When.
- Assert the preserved semantic outcome using Then.
- Be uniquely identifiable by specification name, Example ID, and rule reference.

An Executable Specification that violates this requirement is considered incomplete.
