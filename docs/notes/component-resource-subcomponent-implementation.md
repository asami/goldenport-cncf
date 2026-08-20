# Component Resource SubComponent Implementation Proposal

status = proposed, non-normative
date = 2026-07-31
phase = Phase 58

This note is a non-normative input proposal for the Phase 58 series. During
RSC-01, Phase 58 only reconciles this proposal with the broader architecture
and freezes the resulting handoff; Phase 58.1 through Phase 58.8 implement and
verify the successor scope. After the series evidence exists, Phase 58.9 must
promote the verified architecture and normative behavior to:

- `docs/design/component-resource-subcomponent.md`; and
- `docs/spec/component-resource-subcomponent.md`.

After Phase 58.9 completes canonical promotion, this note becomes historical.

## Purpose

Provide one physical-composition mechanism for large or access-controlled
Component resources while presenting a parent release-membership view over
independently identified parent and child Components.

The immediate drivers are Documentation and SourceCode. The mechanism is
resource-oriented and non-executable with respect to the information payload
carried by those child Components. It does not erase a child Component's
identity or CAR, and later Help, AI, and Admin features can share the payload
safely.

## Core Model

The following provisional concept labels are retained as legacy proposal
vocabulary only. They are not frozen schema, API, type, wire, or lifecycle
names:

- `LogicalComponentRelease`: the parent release/membership view, not a shared
  Component identity;
- `PrimaryComponentArtifact`: the parent Component's primary CAR;
- `ResourceSubComponentRole`: initial information-payload role vocabulary;
- `ResourceSubComponentReference`: a reference to an independently identified
  child Component/release and its payload/provenance;
- `ComponentCompositionManifest`: a legacy label for the parent composition
  representation;
- `ResolvedComponentResource`: a resolved child payload view with logical and
  physical provenance;
- `ResolvedComponentResources`: a resolved view over independently identified
  child payloads; and
- `ResourceResolutionState`: a legacy availability/source/provenance facet
  label, with integrity and authorization represented separately.

Initial information-payload roles:

```text
Documentation
SourceCode
```

A role is not itself a Component namespace or runtime participant. For a
declared Subcomponent, however, the declared child is an independent
Component with canonical identity and its own CAR; the role names the
information payload carried by that child.

## RSC-01 Reconciliation

This older resource proposal is reconciled by the frozen handoff in
[`component-subcomponent-architecture-implementation.md`](component-subcomponent-architecture-implementation.md).
The older proposition that a resource role does not create another Component
namespace/runtime participant is superseded for a declared Subcomponent: the
declared child is an independent Component. The payload remains a separate
information artifact and is not itself a Component.

The following resource boundaries remain inputs to successor implementation:

- Documentation and SourceCode payloads are non-authoritative and
  non-executable. They grant no activation, operation, MCP, disclosure, or
  deployment authority.
- Physical provenance, integrity, access, and security evidence remain
  distinct from logical Component identity and release membership.
- Operation mode and lifecycle/concurrency concerns remain successor inputs;
  this note does not choose their schema, API, type, wire, or state names.
- The child CAR identity and parent membership are owned by the broader RSC-01
  model; the exact archive and repository layout remains allocated to Phases
  58.1 through 58.8.

RSC01-A1 does not promote this note to a canonical design/specification and
does not add executable specifications. Exact failing-first acceptance
identities belong to RSC01-B.

## Physical Shape

The conceptual topology is a parent release membership containing an
independently identified parent CAR and independently identified child CARs:

```text
parent release membership
├── independently identified parent Component and parent CAR
├── independently identified Documentation child Component and child CAR
│   └── non-authoritative, non-executable information payload
└── independently identified SourceCode child Component and child CAR
    └── non-authoritative, non-executable information payload
```

This is a conceptual shape only. Exact archive entry names, suffixes, paths,
descriptor names, and other physical layout choices are successor decisions;
they must not be prescribed here. A child CAR's identity does not make its
payload executable or grant it authority.

## Composition Manifest

Any future composition representation must express, at information level:

- the parent canonical identity and release;
- each child canonical identity and release;
- parent-child membership;
- role separately from implementation technology;
- requiredness and compatibility, as successor choices;
- physical coordinate, digest, and provenance separately from logical
  identity; and
- no authority or activation grant.

Concrete schema, version, field, type, and wire names belong to Phase 58.1;
archive placement and package layout belong to Phase 58.2. This note does not
choose them.

## Publication Completeness

Repository publication is a parent release-profile visibility condition:

```text
admit the independently identified parent CAR/release
  -> admit every required child identity and child CAR/release evidence
  -> validate membership, role, physical, and integrity evidence
  -> expose the complete parent release profile
```

A complete parent release must not become visible until required child
identities, artifacts, and integrity evidence are admitted. This completeness
condition does not merge parent and child identities or activate any child.
An incomplete upload may exist in a private staging area but must not appear as
an installable complete parent release.

Replacement of one artifact after release visibility requires a new immutable
artifact digest and a repository policy that cannot silently mutate the parent
release profile or the independently identified child release.

## Resolution

The legacy proposal's physical lookup precedence is retained as an input for a
successor resolver:

```text
explicit development directory
  -> development-local resource
  -> expanded resource artifact
  -> local Component Repository
  -> verified managed cache
  -> admitted remote Component Repository
  -> unavailable
```

Offline bundles enter as an explicit verified source rather than pretending to
be a remote repository.

Resource precedence applies only after logical identity, parent membership,
authorization, and integrity validation. A higher-precedence corrupt resource
does not silently fall through unless the final specification explicitly
admits and reports that recovery behavior.

Phase 58.3 owns the concrete availability and provenance representation;
Phase 58.5 owns the concrete authorization and integrity representation. The
resolver must preserve their orthogonality and must not activate a child as a
side effect of resolution.

## Provenance

Every resolved resource retains:

- independently identified logical Component/release identity and parent
  membership when applicable;
- logical resource identity and role;
- physical artifact coordinate and digest;
- source kind;
- repository identity when applicable;
- normalized internal path;
- resource digest;
- access/disclosure/license policy;
- resolution step and winning precedence;
- cache evidence and validation time; and
- stale, restricted, or compatibility evidence.

Help and Admin receive sanitized projections from this provenance. They do not
reconstruct it from paths or filenames.

## Resolution States

The following state tokens are retained only as provisional availability,
source, and provenance vocabulary:

- `Embedded`;
- `Development`;
- `Expanded`;
- `Local`;
- `Cached`;
- `Remote`;
- `Restricted`;
- `Unavailable`;
- `Missing`;
- `Stale`;
- `Incompatible`; and
- `Corrupt`.

The tokens do not define a frozen single enum or finalized names. Availability,
integrity, and authorization are orthogonal dimensions. Phase 58.3 chooses the
concrete availability/provenance representation, and Phase 58.5 chooses the
concrete authorization/integrity representation.

## Operation-Mode Policy

| Mode | Documentation | SourceCode | Remote |
| --- | --- | --- | --- |
| `Develop` | Resolve required resources only under an admitted explicit policy. | Use the development tree only when admitted; otherwise resolve the exact artifact under access policy. | Allowed by configured development policy. |
| `Test` | Use explicit deterministic fixtures/bundles. | Use explicit deterministic fixtures. | No implicit access. |
| `Demo` | Installed/cached or explicitly enabled remote access. | No automatic resolution. | Documentation only under explicit policy. |
| `Production` | Primary activation is independent; explicitly authorized content may resolve on demand. | Never automatically resolve, mount, or fetch. | Explicitly authorized Documentation only. |

Resolution in any mode occurs only under an admitted explicit policy; it never
activates the child or grants authority to the payload. Mode stays in
launcher/runtime resource policy and never enters Component domain behavior.

## SourceCode Role

Phase 58.2 owns child-CAR packaging and publication evidence, Phase 58.3 owns
resolution and provenance, and Phase 58.7 owns the read-only consumer boundary.
Phase 59 retains the actual documentation/source content contract and build
production rules.

The downstream SourceCode profile is expected to normalize admitted managed
source collected from paths such as `target/scala-*/src_managed/**` into stable
logical paths such as:

```text
generated-source/main
generated-source/test
```

The raw `target` directory, class files, incremental caches, logs, temporary
files, downloaded caches, and host-specific state are never generic
SubComponent inputs.

## Help and Admin Consumer Boundary

Phase 58.7 owns the shared read-only consumer contract.

Phase 59 Help uses it to:

- locate Documentation resources;
- resolve manuals, Scaladoc, model resources, and AI manifests;
- link exact source evidence when authorized; and
- report availability and integrity.

Phase 60 Component Admin uses it to:

- display primary/Documentation/SourceCode composition;
- show exact version, digest, location class, access, integrity, and provenance;
- distinguish installed, cached, remote, restricted, missing, stale, and
  corrupt resources; and
- navigate authorized Documentation content.

Neither consumer walks archive layouts, cache directories, development
directories, or repository storage directly.

## Security

- Inventory visibility and content visibility are distinct permissions.
- Restricted source may be visible as a role/state without revealing content.
- Repository credentials are runtime configuration, not manifest content.
- Digests and safe artifact identities may appear in diagnostics; source,
  secrets, tokens, signed URLs, and host paths may not.
- A manifest never grants authority.
- Path validation occurs before extraction or content access.
- Integrity is verified after retrieval and before publication to consumers.

## Lifecycle and Concurrency

Resource artifacts are immutable release evidence. Multiple Component
instances may share verified cached bytes while retaining instance-independent
logical provenance.

The resolver must define:

- in-flight resolution coalescing;
- cache atomicity;
- refresh and stale detection;
- unload versus shared-cache ownership;
- runtime shutdown behavior;
- cancellation races; and
- failure isolation between releases.

## Diagnostics

Structured diagnostics should include:

- independently identified Component/release identity and parent membership
  when applicable;
- role and logical resource identity;
- safe source kind and repository identity;
- provisional availability/source/provenance outcome;
- integrity/compatibility/access outcome;
- elapsed time; and
- common `ConclusionDiagnostics`.

They must exclude content, credentials, signed access material, source paths on
the host, and arbitrary provider output.

## Repository Ownership

The existing proposed owner table is retained as historical evidence of
repository concerns. It does not override the frozen successor allocations;
packaging/evidence, resolution/provenance, and consumer-boundary ownership
follow Phases 58.2, 58.3, and 58.7.

| Repository | Proposed ownership |
| --- | --- |
| CNCF | Runtime model, resolver, provenance, policy, lifecycle, Help/Admin consumer API |
| Cozy | Archive layout, composition/resource manifests, validation, repository artifact model |
| sbt-cozy | Build tasks, development evidence, fixture/resource artifact production |
| Component Repository owners | Staging, atomic visibility, exact retrieval, cache/repository evidence |
| downstream Components | Content inputs and representative acceptance |

## Provisional Successor Sequence

This sequence is retained as a successor input, not as an implementation
assignment to Phase 58 as a whole. Concrete choices remain with the allocated
Phase 58.1 through Phase 58.8 successors.

1. Freeze existing behavior and failing-first identities.
2. Implement identity, manifest, codec, and hostile-input validation.
3. Implement deterministic fixture packaging.
4. Implement atomic repository admission/visibility.
5. Implement resolver and provenance.
6. Implement operation-mode and development policies.
7. Implement security, integrity, lifecycle, and observability.
8. Prove shared Help/Admin consumer APIs.
9. Run cross-repository and downstream acceptance.
10. Promote verified behavior to design/specification.

## Open Issues

- exact artifact suffix and coordinate vocabulary;
- concrete composition of the already separate availability/provenance and
  authorization/integrity representations, allocated to Phases 58.3 and 58.5;
- signature authority and key rotation;
- optional versus required role semantics;
- immutable release repair policy;
- remote fallback after corrupt higher-precedence evidence;
- cache retention and offline-bundle format;
- inventory/content authorization vocabulary; and
- final API type names.
