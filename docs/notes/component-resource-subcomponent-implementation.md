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
Component resources while preserving one logical Component identity.

The immediate drivers are Documentation and SourceCode. The mechanism is
resource-oriented and non-executable so later Help, AI, and Admin features can
share it safely.

## Core Model

Provisional model concepts are:

- `LogicalComponentRelease`: authoritative Component coordinate and version;
- `PrimaryComponentArtifact`: executable primary CAR;
- `ResourceSubComponentRole`: initial closed role vocabulary;
- `ResourceSubComponentReference`: exact physical artifact membership;
- `ComponentCompositionManifest`: root release composition;
- `ResolvedComponentResource`: one logical resource plus physical provenance;
- `ResolvedComponentResources`: one resolved logical resource space; and
- `ResourceResolutionState`: availability and integrity state.

Initial roles:

```text
Documentation
SourceCode
```

A role does not create another Component namespace or runtime participant.

## Physical Shape

The provisional topology is:

```text
logical Component release
├── primary component.car
│   ├── component/
│   ├── runtime/
│   ├── car-runtime-manifest.json
│   ├── component-composition-manifest.json
│   └── minimal resource diagnostics
├── documentation resource artifact
│   ├── resource-manifest.json
│   └── documentation resources
└── source-code resource artifact
    ├── resource-manifest.json
    └── source resources
```

The exact archive suffix and repository coordinate shape are implementation
decisions. They must not imply executable CAR semantics.

## Composition Manifest

Provisional fields include:

```yaml
schemaVersion: cncf.component-composition.v1
component:
  name: example
  version: 1.0.0
artifacts:
  - role: Documentation
    coordinate: ...
    parent: ...
    version: 1.0.0
    required: true
    digest:
      algorithm: SHA-256
      value: ...
    access: public
  - role: SourceCode
    coordinate: ...
    parent: ...
    version: 1.0.0
    required: true
    digest:
      algorithm: SHA-256
      value: ...
    access: restricted
```

The composition manifest references authoritative identity; it does not
redefine Component metadata, grant authorization, or make a resource
executable.

## Publication Completeness

Repository publication is a logical-release transaction:

```text
admit primary
  -> admit every required Resource SubComponent
  -> validate identity/version/role/parent/digest/access metadata
  -> publish one complete logical release index entry
```

An incomplete upload may exist in a private staging area but must not appear as
an installable complete release.

Replacement of one artifact after release visibility requires a new immutable
artifact digest and a repository policy that cannot silently mutate the
logical release.

## Resolution

One resolver owns physical lookup:

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

## Provenance

Every resolved resource retains:

- logical Component/release identity;
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

The initial state vocabulary must distinguish at least:

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

Exact names remain provisional. Availability, integrity, and authorization may
be better modeled as separate dimensions than one enum; Phase 58 must decide
through executable use cases.

## Operation-Mode Policy

| Mode | Documentation | SourceCode | Remote |
| --- | --- | --- | --- |
| `Develop` | Automatically resolve required resources. | Use development tree when admitted; otherwise resolve exact artifact under access policy. | Allowed by configured development policy. |
| `Test` | Use explicit deterministic fixtures/bundles. | Use explicit deterministic fixtures. | No implicit access. |
| `Demo` | Installed/cached or explicitly enabled remote access. | No automatic resolution. | Documentation only under explicit policy. |
| `Production` | Primary activation is independent; authorized content may resolve on demand. | Never automatically resolve, mount, or fetch. | Explicit authorized Documentation only. |

Mode stays in launcher/runtime resource policy and never enters Component
domain behavior.

## SourceCode Role

Phase 58 implements the generic role and artifact mechanics. Phase 59 owns the
actual documentation/source content contract and build production rules.

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

Phase 58 provides a shared read-only consumer contract.

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

- Component/release identity;
- role and logical resource identity;
- safe source kind and repository identity;
- resolution state;
- integrity/compatibility/access outcome;
- elapsed time; and
- common `ConclusionDiagnostics`.

They must exclude content, credentials, signed access material, source paths on
the host, and arbitrary provider output.

## Repository Ownership

| Repository | Proposed ownership |
| --- | --- |
| CNCF | Runtime model, resolver, provenance, policy, lifecycle, Help/Admin consumer API |
| Cozy | Archive layout, composition/resource manifests, validation, repository artifact model |
| sbt-cozy | Build tasks, development evidence, fixture/resource artifact production |
| Component Repository owners | Staging, atomic visibility, exact retrieval, cache/repository evidence |
| downstream Components | Content inputs and representative acceptance |

## Implementation Sequence

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
- whether availability/integrity/authorization are one state or orthogonal
  dimensions;
- signature authority and key rotation;
- optional versus required role semantics;
- immutable release repair policy;
- remote fallback after corrupt higher-precedence evidence;
- cache retention and offline-bundle format;
- inventory/content authorization vocabulary; and
- final API type names.
