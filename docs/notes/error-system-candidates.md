ErrorSystem Candidates (CNCF)
=============================

This document collects CNCF-specific observations and
error-handling considerations encountered during development.


This document is NOT normative.

The normative integration contract between the SimpleModeling
core library and frameworks is defined in:

    simplemodeling-lib/docs/spec/error-observation-integration.md

Its purpose is to serve as raw input for evolving the
DefaultErrorSystem in simplemodeling-lib.

Final abstraction, generalization, and incorporation decisions
belong to the SimpleModeling core library.


----------------------------------------------------------------------
1. How to Use This Document
----------------------------------------------------------------------

- Record concrete cases encountered during CNCF development
- Capture *context* and *considerations*, not final decisions
- Do NOT implement special behavior based on these notes
- Do NOT modify DefaultErrorSystem directly based on a single case

This document is a design notebook, not a specification.


----------------------------------------------------------------------
2. Recording Guidelines
----------------------------------------------------------------------

When adding a case:

- Be specific about where it occurred
- Describe what actually happened (Observation)
- Write down questions, doubts, and tentative thoughts
- Avoid premature generalization

Uncertainty is valuable. Leave it visible.


----------------------------------------------------------------------
3. Case Template
----------------------------------------------------------------------

[Case]
----------------------------------------------------------------------
Context:
  (Subsystem, component, or feature area)
  e.g.
    - Config Resolution
    - Component Assembly
    - Job Execution
    - ExecutionContext

Location:
  (Optional but recommended)
  e.g.
    - ProjectRootResolver.resolve
    - ConfigResolver.merge
    - ComponentEngine.run

Trigger:
  (What caused this situation?)
  e.g.
    - Missing file
    - Invalid configuration
    - External dependency timeout

Observation:
  (Describe the factual event)
  - Phenomenon:
  - Resource:
  - Subject:
  - Severity:
  - Additional attributes (if any):

Current Handling:
  (What happens *now* in CNCF?)
  - Returned Consequence?
  - Failure or Success?
  - Exception? (if any)

Considerations:
  (Open questions, tentative ideas)
  - Should this be retryable?
  - Is escalation appropriate?
  - Is this an error, warning, or informational event?
  - Is the Observation missing important fields?

Notes:
  (Free-form)
  - Why this felt unclear
  - Similar cases?
  - Frequency / likelihood
  - Impact on users or operators

Status:
  (Leave undecided unless clearly resolved)
  - Open
  - Observed multiple times
  - Candidate for core abstraction
  - Probably CNCF-specific


----------------------------------------------------------------------
4. Example (Illustrative Only)
----------------------------------------------------------------------

[Case]
----------------------------------------------------------------------
Context:
  Config Resolution

Location:
  ProjectRootResolver.resolve

Trigger:
  No ".sie/" or ".git/" directory found

Observation:
  - Phenomenon: ProjectRootNotFound
  - Resource: FileSystem
  - Severity: Info ? / Warning ?

Current Handling:
  - Project-level config is skipped
  - No failure returned

Considerations:
  - This is expected in single-file usage
  - Should not escalate
  - Probably not an error at all

Notes:
  - Very common in ad-hoc CLI usage

Status:
  - Observed multiple times


----------------------------------------------------------------------
5. Relationship to Core Library
----------------------------------------------------------------------

Items in this document may be periodically reviewed and
abstracted into the SimpleModeling core library:

    - Observation vocabulary refinement
    - ErrorStrategy semantics
    - DefaultErrorSystem behavior

This document itself does NOT define behavior.


----------------------------------------------------------------------
6. Design Reminder
----------------------------------------------------------------------

- CNCF records experience
- Core library defines language
- DefaultErrorSystem evolves slowly and conservatively

When in doubt:
    record > observe > accumulate > abstract (later)


See also:
    simplemodeling-lib/docs/spec/error-observation-integration.md
    for the normative integration specification.
