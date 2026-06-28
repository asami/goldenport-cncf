CNCF Test Vocabulary
====================

Status: approved design decision

This document records why CNCF exposes executable-spec matcher vocabulary from
the main `goldenport-cncf` artifact.


Decision
--------

CNCF keeps shared executable-spec vocabulary, such as
`org.goldenport.cncf.test.CncfSpecVocabulary`, in `src/main`.

The package is intended for downstream component test suites. Production code
must not depend on `org.goldenport.cncf.test`.


Rationale
---------

Downstream CNCF component projects need common matcher vocabulary for generated
operation definitions, MCP payloads, Records, JSON responses, and other CNCF
contracts. If the vocabulary lived in CNCF `src/test`, it would not be published
and could not be reused by those projects.

A separate `goldenport-cncf-testkit` artifact would make the dependency
boundary cleaner, but it would also add another published library and another
version/repository coordination point. For now, CNCF chooses the simpler
single-artifact rule: the vocabulary is published from `goldenport-cncf`
itself, under an explicit `.test` package.


Constraints
-----------

- The vocabulary must stay small and focused on executable specification
  readability.
- The vocabulary may depend on ScalaTest matcher types because its only
  supported consumers are test suites.
- CNCF runtime and production code must not import `org.goldenport.cncf.test`.
- If the vocabulary grows into a larger testing framework, revisit a dedicated
  testkit artifact.


Consequences
------------

`goldenport-cncf` exposes a small test-support API from its main artifact. This
is intentional and preferable to adding another dependency library while the
shared vocabulary remains lightweight.
