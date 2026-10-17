# ScalaDoc as Documentation Subcomponent

Date: 2026-10-16
Status: design note
Planned implementation: Phase 112

## Position

ScalaDoc is an important internal specification artifact for a Component. It serves both human developers and AI source analysis. The normal distribution model should therefore treat generated ScalaDoc as documentation content associated with the Component release, not merely as incidental build output.

The existing Resource Subcomponent architecture already supplies the correct distribution and runtime boundary. Documentation is an independently identified Subcomponent/CAR in the parent release composition. Non-Production runtime policy may resolve it through Component Repository; Production does not automatically fetch or mount it.

## Information path

CML / authored Scala
-> semantic ScalaDoc in source
-> ordinary public ScalaDoc generation
-> Documentation Subcomponent
-> Component Repository
-> existing resource resolution
-> Help / Admin / Web

For AI use, the intended information descent is:

Component Knowledge / Help metadata
-> ScalaDoc when implementation-level meaning is needed
-> SourceCode Subcomponent only when source inspection is actually required.

## Source-level rule

ScalaDoc is semantic information, not a comment-coverage target. Prefer role, contract, constraints, lifecycle, relationships, usage assumptions and non-obvious design intent. Do not duplicate obvious implementation steps or require comments on every private method.

Generated Scala should preserve CML-known meaning through deterministic ScalaDoc projection where appropriate. Hand-written Component implementation follows the same semantic rule.

## Runtime rule

Web/Help/Admin consume the authoritative resolved Documentation resource. They must not independently inspect CARs, repository layouts, caches or development trees. ScalaDoc does not introduce a special resolver.

Production behavior remains governed by the existing resource Subcomponent operation-mode policy.
