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

## Executable Specification

`org.goldenport.cncf.knowledge.ComponentKnowledgeManifestSpec` is the paired
executable specification for DOC02-AC-01 through DOC02-AC-03, including the
hostile path/value-space property boundary.
