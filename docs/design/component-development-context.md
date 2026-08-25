# Resolved Component Knowledge Composition

Status: current S02 design boundary

## Purpose

P594-DOC04-A-S01 defines the value-only resolved-knowledge foundation for
Phase 59.4. `ResolvedComponentKnowledge.composeC` composes a caller-supplied
candidate `ComponentKnowledgeManifest` and caller-supplied Phase 58
`ResolvedComponentResources` into immutable, admitted entry/resource pairs.

The caller supplies both values. `ComponentKnowledgeManifest.createC` remains
the sole authority for their binding: it validates that every manifest entry
matches exactly one supplied Phase 58 resource and its safe evidence. After
that validation, composition pairs each admitted manifest entry with that
exact original `ResolvedComponentResource`. A duplicate, absent, or ambiguous
logical-identity pairing is rejected; the composition never chooses a fallback
resource.

## Evidence and Privacy Boundary

Each internal pair preserves the resolver-owned resource as supplied. It
therefore retains its Phase 58 logical identity, complete provenance including
repository, normalized path, and physical source evidence, availability,
integrity, authorization, and all activation, operation, MCP, disclosure, and
deployment authority flags. The composition neither redacts nor changes that
internal source value.

The serializable `ComponentKnowledgeManifest` and public
`ComponentKnowledgeManifestConsumerContract` remain safe-provenance-only
values. This S01 contract copies no physical provenance into either public
shape and makes no schema or codec change to them.

## Non-Operational Semantics

This is not a second resolver. It performs no scan, path/archive/cache/
repository/network operation, content read, source selection, activation,
authority grant, disclosure action, or runtime operation. It does not inspect
resource content or alter any Phase 58 state.

## S02 Typed Development Context

P594-DOC04-A-S02 adds a value-only `ComponentDevelopmentContext` over a
caller-supplied S01 `ResolvedComponentKnowledge`. It does not call the Phase
58 resolver or add a resolver, resource scan, source-precedence rule, content
read, physical-path operation, repository/cache/archive/network access, or
authority operation. `OperationMode` is not an input to, import of, or public
member of this knowledge-package contract; a future runtime or launcher may
select required resources through its existing policy before calling it.

The candidate declares exact logical-identity assignments in exactly these
typed categories: `Manual`, `Model`, `API`, `Configuration`, `Example`,
`Source`, `GeneratedSource`, `Scaladoc`, `Test`, `Provenance`, and
`DependencyDocumentation`. `Source` is a content category, not a request to
select a resource source. An assignment binds its category to one already
admitted `ComponentResourceLogicalIdentity`; classification is therefore
identity-bound and never inferred from a physical path. The candidate also
declares a distinct, nonempty vector of required logical identities.

Candidate validation rejects empty assignments, a repeated category/identity
pair, a repeated required identity, an assignment or requirement unknown to
the supplied S01 knowledge, and a required identity lacking a declared
assignment. The closed candidate shape has no source-selection field or
operation. Structural violations are returned through `Consequence`; there is
no fallback resource.

The resulting context retains the supplied `ResolvedComponentKnowledge` and
deterministically ordered classified pairs. Each classified pair is the exact
S01 entry and Phase 58 resource, retaining all resolver fields rather than
projecting, redacting, or rewriting them.

A required resource is ready only when its preserved availability is
`Available`, integrity is `Verified`, and authorization is `Granted`. Any
required `Restricted`, `Unavailable`, `Missing`, `Stale`, `Incompatible`, or
`Corrupt` availability, or an integrity or authorization deficiency, produces
a successful structured `Incomplete` result. Its failure code is exactly
`development-resource-incomplete`; it has one typed issue per non-ready
required logical identity. Each issue retains the exact S01/Phase 58 pair and
exposes the unchanged availability, integrity, and authorization facts.
Optional classified resources remain visible in every state and cannot by
themselves make the result incomplete.

The serializable `ComponentKnowledgeManifest` and public
`ComponentKnowledgeManifestConsumerContract` remain unchanged. This S02
contract adds no schema or codec and does not make a public safe-provenance
projection.

## Deferred Slices

Framework snapshot/profile treatment and closed-network Documentation Hub work
remain explicitly deferred. Help, HTTP, CLI, CBD Support, BoK, activation, and
runtime behavior remain outside S02.
