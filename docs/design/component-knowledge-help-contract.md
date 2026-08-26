# Component Knowledge Help Contract

Status: current P595-DOC05-A design boundary

## Purpose

`ComponentKnowledgeHelpContract` is a public, value-only transport-adapter
input for Component Help and Direct-AI discovery. It is constructed solely
from caller-supplied `ResolvedComponentKnowledge`, a caller-supplied
`ComponentDevelopmentContextOutcome`, and optional caller-supplied
`FrameworkDocumentationProfile` / expected `FrameworkProductVersion`
evidence. It does not add a resolver, persistence format, manifest schema, or
manifest codec.

The existing `ComponentKnowledgeManifestConsumerContract` is the sole public
manifest projection source. The Help contract derives Direct-AI manifest
evidence through that existing value and does not reconstruct or modify it.
The resolved Phase 58 pairs are retained privately only to establish exact
membership for an access request. Repository, physical source, normalized
path, archive, cache, and raw content fields are never public Help values.

## Navigation Channels

The canonical discovery link has relation `describedby` and media type
`application/vnd.cncf.component-knowledge+json;version=1`. A
`ComponentKnowledgeHelpManifestRoute` binds the supplied Component identifier
and logical release to:

```text
/help/{component}/knowledge/{logicalRelease}/manifest.json
```

Every `ComponentKnowledgeHelpResourceRoute` uses the same route prefix plus
`/resources/` and the existing manifest-validated relative logical path. The
route model, rather than ad-hoc caller strings, is the source for human links,
HTTP discovery/resource descriptors, CLI inspection descriptors, and Direct-AI
access selection. Component and release segments are deterministically
path-segment encoded; resource logical paths are never normalized, joined,
resolved, or selected again.

Human navigation and Direct-AI navigation contain the same ordered resource
inventory: logical path, kind, role, language, media type, availability,
integrity, authorization, and deterministic route. The shared manifest
identity gives Component and release identity without presenting normal
navigation as Subcomponent topology. Human navigation contains no raw
resolver fields. Direct-AI navigation additionally exposes the existing safe
`ComponentKnowledgeManifestConsumerContract`, not bytes or raw resource
content.

## State and Development Evidence

Help preserves every existing Phase 58 availability value exactly:
`Available`, `Restricted`, `Unavailable`, `Missing`, `Stale`,
`Incompatible`, and `Corrupt`. Availability, integrity, and authorization are
separate fields and no Help state rewrites them.

`ComponentDevelopmentContextOutcome.Ready` projects to Help `Ready` only when
it retains the supplied knowledge. `Incomplete` projects its existing
`development-resource-incomplete` code and issue facts (logical path,
availability, integrity, authorization). Help performs no additional
readiness calculation, fallback, or source choice.

## Framework Developer/Toolchain Navigation

Framework documentation is deliberately separate from operator-oriented
Component Help. When framework evidence is supplied,
`ComponentKnowledgeHelpFrameworkDeveloperToolchainNavigation` retains the
caller-supplied `FrameworkPublicationContext` unchanged, including product,
version, publication identity, and availability. Expected product/version
evidence is explicitly reported as `Exact`, `Mismatch`, or `ProfileAbsent`;
it never resolves an alias such as `latest`.

The framework snapshot is represented as `Absent` or as separate
developer/toolchain resource navigation. `Online` remains online and is never
claimed to be local. An absent framework snapshot is not a Component Help,
manual, or development-context precondition. CML/Cozy-like framework material
is not appended to the ordinary Component operator resource inventory.

## Access Boundary

`accessC(composition, route, request)` admits only a route already projected
by this contract and a request whose resource equals the exact retained
resolved resource for that logical path. A Component/release/logical-path
mismatch, a foreign resource, an unknown identity, and traversal-like or
noncanonical route value are rejected through `Consequence` before access is
delegated.

For an admitted request, `accessC` invokes only
`ResolvedComponentResourcesConsumerProjection.access` with consumer
`DirectAi`. The established `ComponentResourceAuthorizationPolicy` remains
sole authority for bytes, authorization, availability, integrity, archive, and
managed-cache decisions. This contract does not inspect or transform request
bytes; scan a source; mount, fetch, cache, or re-resolve; choose a fallback;
or grant activation, operation, MCP, disclosure, deployment, or Admin
authority.

## Transport Boundary

The deterministic HTTP and CLI values are descriptors only. There is no
Http4s endpoint or CLI parser wiring in this Step, because the current runtime
does not retain resolved knowledge suitable for a live endpoint. A later
transport adapter must receive an already-created Help contract; it must not
re-resolve resources to synthesize one.

## Executable Specification

`org.goldenport.cncf.knowledge.ComponentKnowledgeHelpContractSpec` is the
paired executable specification. It covers canonical equal Help/AI inventory
and discovery, all availability values with independent integrity and
authorization facts, existing development outcomes, exact multi-release and
hostile-route rejection, authorization delegation, framework
match/mismatch/absent-snapshot evidence, deterministic HTTP/CLI descriptors,
and the absence of Admin or Phase 60 behavior.

`org.goldenport.cncf.projection.ResolvedComponentResourcesConsumerSpec` adds
the `DirectAi` consumer-neutral inventory and access-delegation evidence only.
