# Executable Specification Display and Tagging Rules
status=draft

## Purpose

This document defines mandatory rules for how Executable Specifications
are displayed, structured, and tagged when written as executable tests.

The goal is to ensure that executable tests function as
**first-class specifications**, such that:

- each test visibly declares which specification it represents,
- governing rules and examples are explicit and stable,
- test output and failure messages are interpretable as specifications,
- and AI agents can reliably classify, trace, and analyze specifications.

These rules define **presentation and metadata conventions**.
They do not prescribe implementation behavior or development process
(TDD/BDD are explicitly out of scope).

This document extends `docs/rules/spec-style.md` by describing the operational
requirements that keep Executable Specifications visible, traceable, and
machine-readable.

---

## afterWord Metadata Rules

### Rule A.1 afterWord Mandatory

`afterWord` is the canonical mechanism for binding specification metadata.
Each Executable Specification MUST append an `afterWord` clause that includes
the metadata string in the standard format:

```
in spec:<spec-name>, example:<example-id>, rules:<R1,R2,...>, phase:<phase>
```

This string is both visible in test output and machine-readable for tooling.

### Rule A.2 taggedAs Prohibited

`taggedAs` MUST NOT be used for metadata binding.
It is prohibited because it cannot guarantee the standardized metadata format
and hinders AI-assisted traceability.

### Rule A.3 Metadata Content Requirements

`afterWord` metadata MUST include:

- `spec:<spec-name>`
- `example:<example-id>`
- `rules:<R1,R2,...>`
- `phase:<phase>`

Additional contextual keywords (e.g. `kind:Structural`) MAY be added but are optional.

---

## 1. Specification Identity Display Rules

### Rule 1.1 Mandatory Spec Identity

Each executable specification MUST visibly identify:

- the specification name or identifier,
- the Example identifier (E<n>).

This identity MUST appear at the highest visible level of the test
(e.g. the outer test description).

Example (ScalaTest WordSpec):

```
"E1_SingleOperationComponentOmission" should { ... }
```

The Example identifier (E<n>) is the primary specification identity.
It MUST NOT be hidden in nested clauses.

### Rule 1.2 Spec Name Visibility

When possible, the specification name SHOULD also be visible
using higher-level grouping constructs (e.g. `when`, `afterWord`).

Example (conceptual):

```
"Path Resolution" when { ... }
```

This ensures that test output naturally presents
**Spec → Example** structure.

---

## WordSpec Structure Rules

### Rule W.1 Keyword Usage

`must`, `should`, and `can` continue to express specification strength; use them per Rule 2.

### Rule W.2 afterWord Structure

Executions MUST follow the structure:

```
"<spec sentence>" must <afterWord> { ... }
```

When `afterWord` is applied, `in {}` MUST NOT be used on the same clause.

### Rule W.3 Sentence Layout

Every example description SHOULD read as a specification sentence that binds
the spec document through `afterWord`. For example:

```
"E1 Single-operation Component Omission" must in spec:path-resolution, example:E1, rules:R2,R5, phase:2.8 { ... }
```

This layout ensures WordSpec output aligns with the canonical metadata model.

---

## 2. Specification Strength Keywords

### Rule 2.1 Semantic Meaning of Keywords

Executable Specifications MAY use the following DSL keywords
to express specification strength:

- `must`   : normative / mandatory behavior
- `should`: expected behavior
- `can`    : permitted or optional behavior

These keywords express **specification semantics**, not test severity.

### Rule 2.2 Mapping to Specification Rules

- Behavior directly governed by normative rules (R<n>)
  SHOULD use `must`.
- Example-level expected outcomes SHOULD use `should`.
- Extension points or permissive behavior MAY use `can`.

---

## 3. Sentence-Structure Keywords

### Rule 3.1 Structural Keywords

The following keywords MAY be used to structure specification sentences:

- `when`  : condition or context
- `that`  : result restriction or qualification
- `which` : property or attribute description

These keywords are used purely for **readability and semantic structure**.
They do NOT imply BDD or process semantics.

### Rule 3.2 afterWord Usage

`afterWord` MAY be used to anchor specification names
or domains in test output.

Its recommended use is to stabilize the visible specification label.

### Rule 3.3 Specification Binding Display

When an Executable Specification is bound to a normative specification document,
the specification name or identifier MUST be displayed using sentence-structure
DSL constructs such as `when`, `which`, `that`, or `afterWord`.

The binding MUST be visible in the test description itself, not only in comments
or tags, so that test output reads as a specification sentence.

Examples (conceptual):

- `"Path Resolution" when { ... }`
- `"E1 Single-operation Component Omission" must which("is defined in docs/spec/path-resolution.md") { ... }`
- `"E1 Single-operation Component Omission" must afterWord("in Path Resolution Specification") { ... }`

Displaying the specification binding only via comments or `taggedAs` is forbidden.

---

## 4. Given / When / Then Display Rules

### Rule 4.1 Mandatory Given / When / Then Sections

Each executable specification MUST explicitly present
Given / When / Then sections as presentation-only elements.

They can be implemented as ScalaTest `GivenWhenThen` calls, descriptive comments,
or labeled blocks, but MUST NOT contain logic-producing code blocks.
Their purpose is traceability and semantic clarity; the actual logic resides outside
these clauses in ordinary Scala code.

### Rule 4.2 Given Section Requirements

The Given section MUST declare:

- the specification file path,
- the governing rule identifiers (R<n>),
- the Example identifier (E<n>).

Example:

```
Given:
  Spec: docs/spec/path-resolution.md
  Rules: R2
  Example: E1
```

When implemented with `GivenWhenThen`, the section SHOULD call `Given("...")`
for visibility, but the clause body should remain descriptive rather than logic-bearing.

### Rule 4.3 When Section Requirements

The When section MUST describe:

- the semantic action being performed,
- in domain terms, not implementation terms.

When implemented with `GivenWhenThen`, the section SHOULD call `When("...")`
but keep the clause content focused on semantic action descriptions.

### Rule 4.4 Then Section Requirements

The Then section MUST describe:

- the expected observable result,
- using specification terminology.

When implemented with `GivenWhenThen`, the section SHOULD call `Then("...")`
while keeping the text declarative and free of result-producing code.

---

## 5. Tagging Rules (afterWord)

### Rule 5.1 AfterWord Metadata Enforcement

Metadata tagging is accomplished solely via `afterWord` (see Rule A.1).
`taggedAs` is prohibited because it cannot guarantee the standardized format
or the required visibility in test output.

### Rule 5.2 Machine-Readable Content

The `afterWord` metadata string MUST include:

- `spec:<id>`
- `example:<E<n>>`
- `rules:<R<n>,...>`
- `phase:<phase>`

Optional metadata (e.g. `kind:Structural`) may be appended for tooling purposes,
but it must still appear as part of the `afterWord` string.

### Rule 5.3 Purpose of Tags

Tags exist to support:

- selective execution,
- specification traceability,
- AI-assisted analysis and change impact detection.

They MUST NOT be used to encode implementation details.

---

## 6. Failure Message Requirements

### Rule 6.1 Specification-Level Failures

When a test fails, the failure SHOULD be interpretable
as a specification violation.

When feasible, failure messages SHOULD reference:

- the Example identifier (E<n>),
- the violated rule identifier (R<n>).

---

## 7. Prohibited Practices

The following practices are forbidden:

- Hiding Example identifiers in nested scopes only
- Encoding specification meaning only in code, not in Given
- Using Given / When / Then to describe implementation mechanics
- Writing executable tests that cannot be traced to a spec document
- Using taggedAs for test-only or implementation-only concerns

---

## Positioning

These rules define **how Executable Specifications are displayed,
structured, and annotated**.

They complement:

- spec-style.md  
  (Executable Specification writing style and conceptual model)
- specification-linking-and-rule-numbering-rules.md  
  (linking, numbering, and operational conventions)

Together, these documents ensure that executable tests serve as
authoritative, human-readable, and machine-interpretable specifications
within the SimpleModeling ecosystem.

## Hardcoded Behavior Preservation (Operational)

This section operationalizes the Hardcoded Behavior Preservation rule defined in
`spec-style.md`.

When a hard-coded or special-case behavior is removed from the implementation, the
corresponding Executable Specification Example MUST remain visible, identifiable,
and traceable in its display form.

Operational requirements:

- The WordSpec sentence MUST remain readable as a behavioral rule.
- The afterWord metadata MUST include sufficient identifiers to trace the Example to:
    - the governing specification
    - the Example ID
    - the applicable rule numbers
    - the development phase
- `taggedAs` MUST NOT be used for this purpose.

Given / When / Then clauses MUST be present and MUST be descriptive only.
They MUST NOT introduce new logic, branching, or conditions beyond what the Example
asserts.

This ensures that removed hard-coded behavior continues to exist as a contractual
Executable Specification, independent of implementation details.
