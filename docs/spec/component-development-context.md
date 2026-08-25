# Component Development Context Specification

Status: normative S02 foundation

## S01 Resolved-Knowledge Contract

`ResolvedComponentKnowledge.composeC(suppliedResources, candidate)` accepts
only caller-supplied Phase 58 `ResolvedComponentResources` and a candidate
`ComponentKnowledgeManifest`. The caller is responsible for supplying both
values. The factory first calls `ComponentKnowledgeManifest.createC`; that
existing factory is the sole binding authority.

For every admitted manifest entry, exactly one supplied resource with the same
logical identity shall exist. Its manifest digest, safe provenance,
availability, integrity, and authorization must already satisfy
`ComponentKnowledgeManifest.createC`. Composition shall retain the original
`ResolvedComponentResource` beside the entry, preserving logical identity,
full resolver provenance, availability, integrity, authorization, and every
authority/disclosure flag exactly as supplied. Duplicate, absent, ambiguous,
or mismatched evidence shall be rejected without source selection or fallback.
A caller-supplied Phase 58 `Stale` resource is valid resolver evidence and shall
be retained unchanged; it is not a fallback or source-selection case.

The manifest and `ComponentKnowledgeManifestConsumerContract` shall remain
safe-provenance-only public values. Physical repository, normalized-path, and
physical-source evidence remains internal to the supplied resolver resource;
this contract shall not add it to a manifest schema, codec, or consumer
contract.

## Prohibitions

S01 shall not create another resolver or inspect content. It shall not scan,
read, access a filesystem, archive, cache, repository, or network; select a
source; modify a Phase 58 state; grant activation, operation, MCP, disclosure,
or deployment authority; or expose `OperationMode` in this package API.

## S02 Development-Context Contract

`ComponentDevelopmentContext.composeC(knowledge, candidate)` shall accept
only caller-supplied S01 `ResolvedComponentKnowledge` and a value-only typed
candidate. It shall neither invoke nor recreate the Phase 58 resolver, and it
shall not scan or read resource content; walk filesystem paths; access an
archive, cache, repository, or network; select a source; apply source
precedence; or grant authority. `OperationMode` shall not appear in an S02
knowledge-package public API or import.

The typed category enum shall contain exactly `Manual`, `Model`, `API`,
`Configuration`, `Example`, `Source`, `GeneratedSource`, `Scaladoc`, `Test`,
`Provenance`, and `DependencyDocumentation`. A candidate assignment shall pair
one category with one exact `ComponentResourceLogicalIdentity` admitted by its
supplied S01 knowledge. `Source` names a content category and shall not carry
a source-selection, precedence, authority, or access instruction. A candidate
shall contain a distinct, nonempty vector of required logical identities.

The factory shall reject through `Consequence` an empty assignment vector, a
duplicate category/logical-identity assignment, duplicate required logical
identity, unknown assigned or required identity, or a required identity that
has no assignment. These are malformed structural conditions. The factory
shall deterministically order its classified exact pairs and retain the
original S01 pair, including every Phase 58 field, without reinterpretation.
No fallback resource shall be selected.

The outcome sum type shall be `Ready(context)` or `Incomplete(failure)`. A
required logical identity is ready only if the exact paired Phase 58 resource
has availability `Available`, integrity `Verified`, and authorization
`Granted`. Otherwise the valid factory result shall be the successful
`Incomplete` outcome, not a failed `Consequence`. Its failure code shall be
exactly `development-resource-incomplete` and it shall contain one typed issue
per non-ready required resource. Every issue shall retain its exact S01/Phase
58 pair and expose its unmodified availability, integrity, and authorization
facts. An optional non-ready resource shall remain classified but shall not
alone produce `Incomplete`.

S02 shall not modify `ComponentKnowledgeManifest`,
`ComponentKnowledgeManifestConsumerContract`, their schemas, or codecs.

## Deferred Work

Framework snapshot/profile behavior and closed-network Documentation Hub
composition remain deferred. Help, HTTP, CLI, CBD Support, BoK, and runtime
activation are not specified by S02.

## Executable Specification

`org.goldenport.cncf.knowledge.ResolvedComponentKnowledgeSpec` specifies:

- `P594-DOC04A-S01-AC01`: exact preservation of every Phase 58 availability
  state and resolver evidence.
- `P594-DOC04A-S01-AC02`: rejection of stale or mismatched manifest claims
  without a fallback resource.
- `P594-DOC04A-S01-AC03`: separation of public safe provenance from retained
  internal physical resolver evidence.

`org.goldenport.cncf.knowledge.ComponentDevelopmentContextSpec` specifies:

- `P594-DOC04A-S02-AC01`: ready typed context across all eleven categories.
- `P594-DOC04A-S02-AC02`: structured incomplete output preserves exact
  required unavailable, integrity-deficient, and authorization-deficient
  evidence.
- `P594-DOC04A-S02-AC03`: optional non-ready evidence remains visible without
  falsely preventing `Ready`.
- `P594-DOC04A-S02-AC04`: duplicate, unknown, and unassigned references are
  rejected through `Consequence` without a fallback.
