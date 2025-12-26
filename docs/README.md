Documentation Notes
for Cloud-Native Component Framework

This directory contains design and specification documents
for the Cloud-Native Component Framework.

These documents are intended primarily for:

    - framework maintainers
    - future contributors
    - returning future self
    - AI agents consuming the raw text

They are NOT marketing documents
and are NOT optimized for web rendering.


----------------------------------------------------------------------
1. Purpose of This Directory
----------------------------------------------------------------------

The documents under docs/ define:

    - design intent
    - architectural boundaries
    - normative specifications
    - responsibilities and non-responsibilities

They exist to preserve reasoning and prevent accidental coupling
between components, runtimes, and applications.


----------------------------------------------------------------------
2. Document Style
----------------------------------------------------------------------

Documents in this directory intentionally use a
plain-text–oriented specification style.

Section headers follow this format:

----------------------------------------------------------------------
N. Section Title
----------------------------------------------------------------------

This format is chosen because:

    - it is readable in terminals and editors
    - it survives copy & paste and partial extraction
    - it is resilient to formatting loss
    - it is easy for AI systems to parse reliably

Markdown heading syntax is intentionally avoided here.


----------------------------------------------------------------------
3. Relationship to README.md
----------------------------------------------------------------------

README.md at the repository root serves a different purpose.

    - README.md
        * overview
        * positioning
        * high-level architecture
        * entry point for new readers

    - docs/*
        * detailed design intent
        * specifications
        * rationale and constraints
        * maintainer-oriented knowledge

Do not duplicate README content here.
Do not move design specifications into README.md.


----------------------------------------------------------------------
4. Normative vs Informative Documents
----------------------------------------------------------------------

Some documents in this directory are normative.

A normative document:

    - defines required behavior
    - constrains future implementations
    - should be changed carefully

Examples:
    - config-resolution.md

Other documents may be informative or exploratory.
They should be clearly marked as such when applicable.


----------------------------------------------------------------------
5. Expected Documents
----------------------------------------------------------------------

Typical documents in this directory include:

    - config-resolution.md
    - execution-context.md
    - component-model.md
    - job-management.md
    - async-model.md
    - glossary.md

Not all documents must exist at all times.
Add documents when design intent needs to be preserved.


----------------------------------------------------------------------
6. Final Note
----------------------------------------------------------------------

These documents exist to make the framework:

    - boring
    - predictable
    - maintainable

If a design decision cannot be explained clearly here,
it is probably not ready to be implemented.
