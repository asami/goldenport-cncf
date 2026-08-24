# Component Knowledge Manifest Specification

Status: normative specification

## Schema

The only accepted root schema string is `cncf.component-knowledge.v1`.
`ComponentKnowledgeManifestCodec.decodeC` rejects malformed JSON, duplicate
object keys, another schema string, missing required fields, and values that
violate this specification through `Consequence.argumentInvalid` behavior.

`encode` emits canonical compact JSON and self-validates that result. For equal
manifest content it is byte-stable: known field order is fixed, resources use
canonical logical-identity/path ordering, and extension keys are lexical.
Unknown safe JSON object fields are retained in the corresponding `extensions`
map and re-emitted; known schema fields always take precedence over an
extension key of the same name. Every extension-object key, including nested
objects and objects in arrays, is normalized across case, punctuation,
camel-case, and compound word forms. A normalized key may not name protected
Phase 58 repository/location, physical-or-normalized path, content/bytes,
credential-token, authorization, activation, operation, MCP, deployment, or
disclosure-authority evidence.

`frameworkPublication` is an optional root field. An absent field preserves
the context-free legacy encoding byte-for-byte; an encoded context is ordered
after `logicalRelease` and before `resources`. Its product/version,
canonical URL, publication generation, document ID, optional section ID,
publication digest, availability, generated-from evidence, optional
documentation snapshot, and safe extensions are all descriptive and do not
bind a resource. Context known fields have fixed order, while retained safe
unknown fields are recursively lexical. In that context only, normalized
aliases for resource binding, resolver, scan, and read evidence are also
rejected, alongside all protected Phase 58 evidence aliases.

`modelResources` is an optional root field after `frameworkPublication` and
before `resources`; it leaves context-free and framework-only encodings
byte-stable when absent. It contains `models` and `diagrams`, each ordered by
the exact existing manifest resource logical identity it references. A model
reference admits only Entity, Powertype, StateMachine, Value, Datatype, or
Relationship evidence with `model` / `application/json`. A diagram reference
admits only ClassDiagram or StateDiagram evidence with `diagram` /
`image/svg+xml`. The referenced value must be exactly one existing resource
entry; it does not reconstruct or otherwise recompute that entry's identity,
role, media type, digest, provenance, availability, integrity, or
authorization. Context logical identities and logical paths are unique.

Every diagram has a nonempty `generatedFrom` array. Each source carries an
existing admitted model logical identity and precisely its recorded SHA-256;
source identities do not repeat. StateDiagram requires a StateMachine source;
ClassDiagram accepts one or more admitted model sources. Model-resource
extensions are retained and recursively lexical only when their normalized keys
do not name protected Phase 58, resource-binding, resolver, scan, or read
evidence. Duplicate JSON keys remain rejected by the root parser.

## Manifest and Resource Contract

A manifest has a canonical Component identifier, a nonempty logical release,
and one or more resource entries. Each resource entry has exactly one Phase 58
logical identity binding, a safe relative canonical logical path, kind, role,
optional language, media type, non-negative size, lowercase 64-hex SHA-256,
metadata, Phase 58 availability/integrity/authorization states, and safe
provenance.

`language` and binding `parentComponentId` are optional when decoding: each
may be omitted or JSON `null`, while a present non-null value must be a string.
Each binding logical release equals the manifest logical release and identifies
either the manifest Component with no parent or a declared child whose parent
is the manifest Component.

Logical paths are nonempty, trimmed, relative slash-separated text. They must
not be absolute, drive-qualified, backslash-containing, empty-segment, dot,
dot-dot, or control-character paths. Logical resource identities and canonical
logical paths are unique within a manifest.

The admissible v1 kind/role/media combinations are:

| Kind | Role | Media type |
| --- | --- | --- |
| Documentation | documentation | text/markdown |
| SourceCode | source-code | text/x-scala |
| Entity, Powertype, StateMachine, Value, Datatype, Relationship | model | application/json |
| ClassDiagram, StateDiagram | diagram | image/svg+xml |
| FrameworkDocumentation | framework-documentation | text/markdown |
| Directive | directive | application/yaml |
| SkillCatalog | skill-catalog | application/json |

Authority, stability, source, license, and disclosure are distinct metadata.
They remain distinct from availability, integrity, and authorization. Disclosure
is descriptive (`metadata-only` or `reference-only`) and is not a disclosure
authority boolean.

## Safe Provenance and Binding

Safe provenance has exactly these owned fields: source kind, artifact
coordinate, logical source, resolution step, external-deployment-required, and
matching digest. It cannot carry repository locations, physical or normalized
paths, content, credentials, activation, operation, MCP, deployment, or
disclosure authority.

`ComponentKnowledgeManifest.createC(suppliedResources, candidate)` accepts
only caller-supplied Phase 58 `ResolvedComponentResources`. Every entry must
refer to exactly one supplied logical identity and exactly match its digest,
safe provenance, availability, integrity, and authorization values. The
adapter performs no resource discovery, scan, source selection, content read,
network access, or resolver call.

`modelResources` validation is part of candidate validation only. It never
adds discovery, source selection, or a second resolver to the Phase 58
`createC` adapter.

## Framework Publication Context

A framework publication context contains a lowercase safe product token and
safe product version, a canonical absolute HTTPS URL with a required lowercase
host, no user info, query, fragment, or non-default port, safe publication
generation, absolute document and optional section URIs, a lowercase 64-hex
publication SHA-256, one of `local`, `installed`, `cached`, `online`, or
`unavailable` availability, and generated-from absolute source identity plus
lowercase 64-hex source SHA-256. `unavailable` is valid descriptive evidence
and does not change execution readiness.

An optional documentation Component snapshot contains a canonical Component
ID, safe logical release, context-matching publication SHA-256, and the same
availability vocabulary. It is descriptive publication evidence only; it is
not a Phase 58 resource binding or logical resource identity, resolver
dependency/input, authoring source or authority, activation, execution
dependency, or access grant.

`projectionFreshnessFor` compares only caller-provided source identity and
source SHA-256 with the context's generated-from fields. Both equal values
produce `current`; either difference produces `stale`. This operation performs
no identity creation or resource, resolver, filesystem, cache, repository, or
network access. Equal URL or digest values never establish authority.

## Executable Specification

`org.goldenport.cncf.knowledge.ComponentKnowledgeManifestSpec` is the paired
executable specification for DOC02-AC-01 through DOC02-AC-03, including the
hostile path/value-space property boundary.

`org.goldenport.cncf.knowledge.FrameworkPublicationContextSpec` is the paired
executable specification for DOC02B-01 deterministic framework-context codec,
safe recursive extensions, all availability values, pure freshness comparison,
optional snapshot evidence, malformed context values, protected normalized
aliases, and duplicate JSON keys.

`org.goldenport.cncf.knowledge.PortableModelResourceContextSpec` is the paired
executable specification for DOC02C-01 optional-root compatibility, portable
model and diagram typing, deterministic JSON, generated-from source/digest
validation, protected aliases and paths, and duplicate-key rejection.
