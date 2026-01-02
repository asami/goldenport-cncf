# Design Idioms Catalog

This directory contains **design idioms** used across the SimpleModeling,
Semantic Integration Engine (SIE), and related projects.

Idioms capture *reusable structural and semantic patterns* that appear
frequently in the codebase and design discussions.

They are intended to be referenced **by name** in:

- Design documents
- Code reviews
- Architecture discussions
- AI-assisted code generation prompts

This document serves as the **index and entry point** for all idioms.

---

## What Is an Idiom?

In this project, an **idiom** is:

- A documented, reusable design pattern
- Proven by repeated use in real code
- Descriptive rather than normative

Idioms complement the rules defined in `RULE.md` but do not override them.

If a rule and an idiom conflict, the **rule always takes precedence**.

---

## How to Use Idioms

- When a known idiom applies, **reference it by name**
  (e.g. *ValueBackedAbstractObject*)
- Idioms may be cited without repeating their full structure
- Idioms are shared vocabulary, not mandatory templates

Example (design discussion):

> “This object should follow the *ValueBackedAbstractObject* idiom.”

Example (AI prompt):

> “Define a protocol request using the *ValueBackedAbstractObject* idiom.”

---

## Idiom Index

### Structural Idioms

Structural idioms describe **code shape and composition**.

- **ValueBackedAbstractObject**  
  Abstract object carrying its data as a single immutable value object.  
  → `value-backed-abstract-object.md`
- **ValueBacked**  
  Single case class aggregation for copy / transform / validation workflows.  
  → `value-backed-idiom.md`
- **Holder**  
  Stable accessor facade over a value object.  
  → `holder-idiom.md`
- **CollectionGroup**  
  Aggregation types (Collection / Group) with binary-compatibility-friendly construction.  
  → `collection-group-idiom.md`

---
---

## Idiom Naming Conventions

To keep idioms consistent and discoverable:

- Idiom names use **PascalCase**
- Names should describe the **essence of the pattern**, not a specific implementation
- Avoid embedding domain-specific terms unless essential

Examples:

- `ValueBackedAbstractObject`
- `ProtocolRequest`
- `SnapshotObject`

---

## Directory Structure

Each idiom is defined in a standalone Markdown file:

```
docs/idioms/
  README.md
  value-backed-abstract-object.md
  value-backed-idiom.md
  holder-idiom.md
  protocol-request.md        (planned)
  snapshot-object.md         (planned)
```

---

## Adding a New Idiom

When adding a new idiom:

1. Create a new Markdown file under `docs/idioms/`
2. Use a consistent structure:
   - Intent
   - Structure
   - Canonical Form
   - Variants (if any)
   - When to Use / When Not to Use
   - Known Uses
3. Add the idiom to the **Idiom Index** above
4. Prefer referencing existing idioms rather than duplicating patterns

---

## Design Intent

This idiom catalog exists to:

- Reduce repeated explanation of common patterns
- Establish shared architectural vocabulary
- Improve consistency across projects
- Enable precise collaboration with AI tools
