# Component Knowledge Help Contract Specification

Status: normative P595-DOC05-A contract

## R1. Inputs and Public Projection

`ComponentKnowledgeHelpContract.createC(knowledge, developmentContext,
frameworkEvidence)` shall accept only caller-supplied
`ResolvedComponentKnowledge`, caller-supplied
`ComponentDevelopmentContextOutcome`, and optional caller-supplied framework
profile / expected product-version evidence. It shall validate the existing
Component knowledge manifest and derive the public Direct-AI manifest only by
calling `ComponentKnowledgeManifestConsumerContract.fromManifestC`.

The contract shall add no manifest schema or codec and shall not expose
resolved raw resource fields. Human values shall contain no repository,
physical-source, normalized-path, archive, cache, content, or normal
Subcomponent-topology fields. Direct-AI values shall expose the existing safe
consumer contract, not raw content or physical provenance.

## R2. Deterministic Discovery and Resource Routes

The discovery descriptor shall have exact relation `describedby` and exact
media type `application/vnd.cncf.component-knowledge+json;version=1`. Its
manifest path shall be logically bound to the supplied Component and release:

```text
/help/{component}/knowledge/{logicalRelease}/manifest.json
```

Each resource path shall be below the same prefix as
`/resources/{logicalPath}`, where `logicalPath` is exactly an existing
manifest-validated relative logical path. The route descriptor shall be the
only route source for the Help inventory, Direct-AI inventory, HTTP descriptor,
and CLI inspection descriptor. HTTP and CLI descriptors are descriptive
transport inputs and shall not wire an endpoint or parser.

Human and Direct-AI navigation shall expose identical canonical-order resource
navigation entries, manifest identity, and release. Entries shall preserve
logical path, kind, role, language, media type, availability, integrity,
authorization, and route exactly as projected from the existing consumer
contract.

## R3. State and Development-Context Projection

The Help contract shall preserve `Available`, `Restricted`, `Unavailable`,
`Missing`, `Stale`, `Incompatible`, and `Corrupt` exactly. It shall retain
availability, integrity, and authorization as distinct facts.

It shall project `ComponentDevelopmentContextOutcome.Ready` as Help `Ready`
only for the same supplied knowledge. It shall project
`ComponentDevelopmentContextOutcome.Incomplete` using its exact existing
failure code and issues. The expected code is
`development-resource-incomplete`. No Help readiness rule, resource fallback,
or state rewrite is permitted.

## R4. Direct-AI Access

`accessC` shall accept a supplied composition, an already projected canonical
resource route, and a supplied Phase 58 access request. Before delegation it
shall require that the route and the request resource identify one exact
retained `ResolvedComponentKnowledge` pair. Unknown identity, component or
release mismatch, another supplied resource, traversal-like/noncanonical
route selection, and a resource not equal to the exact retained value shall
fail closed through `Consequence`.

For an admitted pair, `accessC` shall delegate to
`ResolvedComponentResourcesConsumerProjection.access` with
`ComponentResourceConsumer.DirectAi`. The existing authorization policy shall
remain responsible for restricted/unavailable/unverified evidence, policy
rejection, bytes, digest, archive, and cache checks. The contract shall not
scan, fetch, mount, cache, re-resolve, fall back, inspect or transform hostile
bytes, or grant activation, operation, MCP, disclosure, deployment, or Admin
authority.

## R5. Framework Toolchain Navigation

Framework profile output shall be a separate developer/toolchain navigation
value, never part of the operator Component resource inventory. It shall retain
the supplied `FrameworkPublicationContext`, including product, version,
publication identity, and availability, and report expected product/version as
`Exact`, `Mismatch`, `NoExpectation`, or `ProfileAbsent` as applicable.

The contract shall not claim an `Online` publication is local, select a latest
alias, alter Component manual selection, or make an absent framework snapshot
a Component Help precondition. Framework CML/Cozy-like resource entries remain
developer/toolchain navigation only.

## R6. Exclusions

This Step shall not alter `ComponentKnowledgeManifest` v1 or its codec,
`ComponentDevelopmentContext`, legacy Help projection, HTTP server, CLI
dispatch/parser, Phase 58 resolver, Phase 60/Admin/CBD/BoK behavior, or any
runtime lifecycle behavior. It shall not create an endpoint that re-resolves
resources.

## Executable Specification

`org.goldenport.cncf.knowledge.ComponentKnowledgeHelpContractSpec` specifies:

- `P595-DOC05-A-AC01`: deterministic, identical canonical human/AI inventory,
  discovery relation/media type, and HTTP/CLI descriptors, including a
  ScalaCheck ordering property.
- `P595-DOC05-A-AC02`: all availability states, separate integrity and
  authorization facts, and exact Ready/Incomplete development evidence.
- `P595-DOC05-A-AC03`: exact resource/release selection and hostile route
  rejection without fallback.
- `P595-DOC05-A-AC04`: Direct-AI policy delegation, restricted behavior, and
  no raw/physical/authority leak.
- `P595-DOC05-A-AC05`: framework expected-version match/mismatch and
  online/absent-snapshot evidence separate from operator navigation.
- `P595-DOC05-A-AC06`: deterministic Help link and CLI-inspection descriptors.
- `P595-DOC05-A-AC07`: no Admin action, endpoint/parser wiring, or Phase 60
  behavior.

`org.goldenport.cncf.projection.ResolvedComponentResourcesConsumerSpec`
specifies consumer-neutral `DirectAi` inventory and access delegation.
