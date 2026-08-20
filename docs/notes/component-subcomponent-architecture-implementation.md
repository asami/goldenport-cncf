# Component and SubComponent Architecture Implementation Proposal

status = proposed, non-normative; RSC-01 frozen successor handoff
date = 2026-08-20
phase = Phase 58

This note remains a provisional implementation proposal until Phase 58.9. The
RSC-01 sections below are the frozen successor handoff for Phase 58.1 through
Phase 58.8: they record the current owners, reconcile the two proposals, and
state the invariants that successor implementation must preserve. They do not
create a runtime, archive, schema, repository, launcher, or consumer contract.

After the Phase 58.1 through Phase 58.8 series evidence exists, Phase 58.9
must promote the verified architecture and normative behavior to:

- `docs/design/component-subcomponent-architecture.md`; and
- `docs/spec/component-subcomponent-architecture.md`.

The existing
`component-resource-subcomponent-implementation.md` is an input to this
handoff. It no longer independently decides whether a declared Subcomponent
is a Component; the reconciliation is recorded in RSC-01 below.

## Purpose

Define one Component composition model in which a parent Component remains an
executable CAR and every declared Subcomponent is independently a Component
with its own CAR. The model makes the parent relationship, role,
implementation technology, release membership, integrity, repository
admission, runtime boundary, and platform deployment responsibility explicit.

The proposal distinguishes three concepts that must not be conflated:

- a **Component** is the fundamental CBD unit with a canonical Component
  identity;
- a **CAR** is the immutable, self-describing artifact for one Component
  snapshot; and
- a **Subsystem** is an executable composition of one or more Components. A
  single executable Component may be treated as an implicit
  single-Component Subsystem.

## Composition Classes

### Parent Component

A parent Component has a primary CNCF Runtime implementation and a primary
CAR. It is not a container-only artifact. Its CAR carries the authoritative
composition registry for the Subcomponents that structurally belong to it.

### Subcomponent Component

Every declared Subcomponent is a Component with its own canonical Component
identity, primary CAR, metadata, documentation, and parent reference. Its
parent registry records membership and the release evidence needed by the
parent release. A registry reference does not copy mutable child identity
metadata and does not grant authority.

Each Subcomponent CAR has a CNCF-side Component representation for identity,
metadata, Help, diagnostics, MCP-readable information, and explicitly
admitted operations. A Documentation or SourceCode Subcomponent can therefore
be described and queried independently even when the parent is the only
Component activated for ordinary runtime work. Its information payload is not
itself a Component, is non-authoritative, and is non-executable.

A Subcomponent may also carry an artifact for another execution platform.
Examples include:

| Role | Example implementation artifact | Deployment owner |
| --- | --- | --- |
| `presentation` | React or KMP application | Web or mobile platform |
| `batch` | Scala/JVM batch application | Batch scheduler/runtime |
| `function` | Scala.js or other function artifact | Cloud-function platform |
| `Documentation` | authored documentation artifact | documentation delivery surface |
| `SourceCode` | admitted SourceCode artifact | development tooling |

Role is separate from implementation technology. The CNCF Runtime admits and
describes the CAR; it does not silently deploy an external platform artifact.
Platform-native deployment remains explicit and outside the CAR admission
path.

## RSC-01 Evidence Snapshot

The snapshot was read at the Phase authority HEAD. The repositories other than
the mutation root were read only. Cozy had unrelated pre-existing dirty paths;
those working-tree changes were excluded and the tracked HEAD below was used.

| Evidence repository and HEAD | Concrete owner paths/evidence | RSC successor owner |
| --- | --- | --- |
| CNCF `cloud-native-component-framework` at `308fdd6d830c9aa1fdc8e1dcb6421e1f6ec0d57e` | `docs/spec/component-identity.md`; `docs/spec/canonical-car-repository-resolution.md`; `docs/spec/component-repository-index.md`; `src/main/scala/org/goldenport/cncf/component/ComponentDescriptor.scala`; `ComponentDescriptorLoader.scala`; `CarExtractor.scala`; `SarExtractor.scala`; `component/repository/ComponentRepository.scala`; `ComponentRepositorySpace.scala`; `cli/CncfRuntimeBootstrapPart.scala`; `cli/CncfRuntimeInstanceLifecyclePart.scala`; `subsystem/GenericSubsystemDescriptor.scala`; `GenericSubsystemFactory.scala`; `projection/HelpProjection.scala`; `http/StaticFormAppRendererComponentAdminPart.scala`; `http/WebResourceRoot.scala` | Phase 58.1 identity/composition; 58.3 resolution; 58.4 mode; 58.5 policy; 58.6 lifecycle; 58.7 consumers; 58.8 integration |
| Cozy `dev2025/cozy` at `20c7761f2505a0fa8a6b484accdeba91f946fe50` | `docs/design/car-project-metadata-ownership.md`; `docs/design/component-repository-publication.md`; `src/main/scala/cozy/archive/CozyArchivePackager.scala`; `CozyCarRuntimeManifest.scala`; `ComponentRepositoryIndex.scala`; `CozyCarPublisher.scala`; `CozySarPublisher.scala`; `CarCmlSourceResolver.scala`; `src/test/scala/cozy/archive/CozyArchivePackagerCv06Spec.scala`; `src/test/scala/cozy/archive/ComponentRepositoryIndexSpec.scala` | Phase 58.1 codec; 58.2 packaging/publication; 58.8 integration |
| sbt-cozy `dev2026/sbt-cozy` at `095c9c606acefa6f542b146797d5912a066dd124` | `src/main/scala/org/goldenport/cozy/CarRuntimeClasspathResolver.scala`; `CarDependencyResolver.scala`; `src/sbt-test/cozy/development-runtime-evidence/src/main/car/component-descriptor.json`; `abi-manifest.json`; `src/sbt-test/cozy/namespace-qualified-car-repository/consumer/project.yaml`; `src/test/scala/org/goldenport/cozy/CarRuntimeClasspathResolverSpec.scala`; `CarDependencyResolverSpec.scala` | Phase 58.2 packaging/build evidence; 58.3 resolution; 58.8 integration |
| cncf-samples `dev2026/cncf-samples` at `c601252b23b2a788f9869463c833de52ddfcd771` | `samples/02.a-car-dir-lab/tmprepo/testcomp/descriptor.yaml`; `samples/02.a-car-dir-lab/tmprepo/testcomp/meta/manifest.json`; `samples/11-subsystem/subsystem.cml`; `samples/11.a-multi-component-subsystem-lab/README.md`; `samples/11.b-subsystem-bundled-component-lab/subsystem.cml`; `samples/11.c-subsystem-mixed-component-lab/README.md`; `samples/11.d-implicit-subsystem-lab/README.md`; `samples/11.e-sar-dir-lab/README.md`; `docs/phase/samples/11.a-multi-component-subsystem-lab.md` | Phase 58.2 fixtures; 58.3/58.4 resolution and composition; 58.8 integration |
| textus-sample-apps `dev2026/textus-sample-apps` at `3202c0204e57bb73b4aabfb7f0540185630b7f6b` | `cwitter/subsystem/subsystem-descriptor.yaml`; `cwitter/subsystem/web/web.yaml`; `cwitter/component/assembly-descriptor.yaml`; `cwitter/component/src/main/car/web/web.yaml`; component and subsystem source/web trees | Phase 58.7 consumer evidence; 58.8 downstream integration |

The required CNCF owner paths in this snapshot are, in full:
`src/main/scala/org/goldenport/cncf/component/ComponentDescriptor.scala`,
`src/main/scala/org/goldenport/cncf/component/ComponentDescriptorLoader.scala`,
`src/main/scala/org/goldenport/cncf/component/CarExtractor.scala`,
`src/main/scala/org/goldenport/cncf/component/SarExtractor.scala`,
`src/main/scala/org/goldenport/cncf/component/repository/ComponentRepository.scala`,
`src/main/scala/org/goldenport/cncf/component/repository/ComponentRepositorySpace.scala`,
`src/main/scala/org/goldenport/cncf/cli/CncfRuntimeBootstrapPart.scala`,
`src/main/scala/org/goldenport/cncf/cli/CncfRuntimeInstanceLifecyclePart.scala`,
`src/main/scala/org/goldenport/cncf/subsystem/GenericSubsystemDescriptor.scala`,
`src/main/scala/org/goldenport/cncf/subsystem/GenericSubsystemFactory.scala`,
`src/main/scala/org/goldenport/cncf/projection/HelpProjection.scala`,
`src/main/scala/org/goldenport/cncf/http/StaticFormAppRendererComponentAdminPart.scala`,
and `src/main/scala/org/goldenport/cncf/http/WebResourceRoot.scala`.

The snapshot is evidence of current owner boundaries, not an approval to edit
those repositories in RSC01-A1. No executable specification or product code
change belongs to this slice.

## Current Behavior and Ownership Inventory

This inventory records what exists today and who owns the successor decision.
The named source paths are evidence owners, not RSC01-A1 mutation targets.

### CAR/SAR layout, descriptors, manifests, integrity, and dependencies

- CNCF `ComponentDescriptor.scala` decodes descriptor identity, version,
  componentlets, runtime descriptors, extensions, and schema-versioned
  canonical identity. `ComponentDescriptorLoader.scala` locates descriptor
  files in a directory or CAR archive and probes canonical identity without
  constructing a runtime Component.
- CNCF `CarExtractor.scala` extracts a CAR, validates normalized archive entry
  paths, loads the descriptor, and resolves the current `component/`, `lib/`,
  `spi/`, and `component/collaborator/` classpath shape. `SarExtractor.scala`
  loads the subsystem descriptor and scans the current `component/` CARs,
  `extension/*.jar`, and `config` files in an expanded or archived SAR.
- Cozy `CozyArchivePackager.scala` owns package assembly from project metadata,
  generated descriptor/ABI evidence, runtime manifest, dependency metadata,
  web content, and archive entries. `CozyCarRuntimeManifest.scala` and the
  associated publisher admission validate the generated manifest and regular
  entry digests. `CozySarPublisher.scala` owns the existing SAR publication
  path.
- sbt-cozy `CarRuntimeClasspathResolver.scala` and `CarDependencyResolver.scala`
  own build/runtime classpath and dependency evidence. The development-runtime
  fixture records a schema-3 descriptor and ABI manifest; it is not a
  Subcomponent registry.
- Current CNCF owner: descriptor/extraction/admission at the paths above.
  Current Cozy/sbt-cozy owners: archive producer, package metadata, and
  build/development evidence. Successors: Phase 58.1 owns identity and
  composition fields; Phase 58.2 owns deterministic child CAR/payload
  packaging and publication completeness; Phase 58.8 owns cross-repository
  fixture evidence.

### Repository index, publication, cache/offline, and release visibility

- The canonical CNCF specs define the current qualified identity boundary,
  exact known-release cache-first resolution, and the repository index
  availability boundary. In particular, `docs/spec/canonical-car-repository-
  resolution.md` keeps cache paths and repository paths derived from the exact
  coordinate; `docs/spec/component-repository-index.md` defines the published
  discovery summary and says it does not imply installation or activation.
- CNCF `ComponentRepository.scala` owns the legacy runtime repository
  families: component directory/file, component development directory,
  subsystem development directory, Scala CLI, and standard repository. It
  exposes local repository/cache defaults, known-artifact descriptor lookup,
  and ordered repository specifications. `ComponentRepositorySpace.scala`
  turns admitted policy into ordered slots and preserves repository origin.
- CNCF `CncfRuntimeBootstrapPart.scala` separates active and search
  repository specifications and resolves a requested component archive or
  descriptor before launch. `CncfRuntimeInstanceLifecyclePart.scala` consumes
  the admitted bootstrap policy during initialization and owns shutdown of the
  resulting Subsystem.
- Cozy `ComponentRepositoryIndex.scala`, `CozyCarPublisher.scala`,
  `CozySarPublisher.scala`, and `docs/design/component-repository-
  publication.md` own index merge, detailed catalog checks, atomic publication,
  and complete-release visibility. `CarCmlSourceResolver.scala` keeps CML
  publication evidence tied to the project identity.
- A verified cache is current CNCF/Cozy evidence; an explicit offline-bundle
  owner is not yet present in the inspected repositories. Offline input is
  therefore a successor requirement, not a current behavior claim. Phase
  58.2 owns publication completeness, Phase 58.3 owns cache/offline source and
  resolution semantics, and Phase 58.8 owns cross-repository visibility
  evidence.

### Component-dev-dir versus packaged/repository resolution precedence

- `ComponentRepository.scala` distinguishes `component-dev-dir`,
  `component-dir`, `component-file`, `subsystem-dev-dir`, and standard
  repositories. `ComponentRepositorySpace.scala` records active versus search
  slots and their `ComponentOrigin` labels.
- `CncfRuntimeBootstrapPart.scala` first honors explicit activation arguments;
  otherwise it resolves a qualified component archive from search repositories
  and then a descriptor to derive the activation argument. Its runtime
  descriptor path is consumed through the admitted repository policy.
- `GenericSubsystemFactory.scala` has a concrete descriptor search chain that
  starts with the component development descriptor, then configured descriptor,
  named repository descriptor, component archive, component CAR directory,
  component development directory, and terminal configured descriptor. The
  runtime-only branch applies the same admitted-policy boundary. This is
  current precedence evidence, not the final Subcomponent resolver design.
- Successor ownership is Phase 58.3 for logical/physical resolution and
  provenance, with Phase 58.4 deciding mode-specific development composition.
  The successor may choose resolver names and APIs but may not merge discovery
  with activation or silently reinterpret the explicit development boundary.

### Help/Admin and Web-resource direct path/archive assumptions

- `HelpProjection.scala` projects an admitted Component or Subsystem from
  runtime objects, including canonical Component ID, display aliases, origin,
  artifact name/version, services, and operation selectors. It does not itself
  resolve archive or repository paths.
- `StaticFormAppRendererComponentAdminPart.scala` builds component and
  subsystem web/admin routes from runtime Components and configured
  `WebDescriptor` values. Its current assumptions include normalized component
  path segments and component-owned document/app links; direct filesystem
  ownership is not a resource provenance contract.
- `WebResourceRoot.scala` is the direct physical reader. It supports a
  directory root, a ZIP/CAR/SAR archive with a `web/` prefix, and an archive
  subtree; it rejects absolute and traversal relative paths. This makes the
  current archive-layout assumption concrete while leaving resource identity
  and authorization outside this reader.
- The cwitter evidence shows current downstream assumptions: a subsystem
  descriptor lists `cwitter` and `textus-user-account`, while `web/web.yaml`
  selects `/web/cwitter` and route aliases. Phase 58.7 owns the read-only
  consumer contract and sanitized provenance projection; Phase 58.8 owns
  downstream acceptance. No RSC01-A1 consumer code changes.

### Managed/generated source evidence and current multi-component/SAR samples

- Cozy `car-project-metadata-ownership.md` and `CozyArchivePackager.scala`
  establish project metadata and generated descriptor/runtime evidence as
  producer-owned. Generated source provenance is copied as build evidence for
  release packaging; it is not itself runtime authority.
- sbt-cozy's development-runtime fixture and its `src/sbt-test/cozy` scripted
  project provide concrete source-managed descriptor/ABI and namespace-qualified
  repository evidence. They do not currently define a generic SourceCode
  Subcomponent.
- cncf-samples provide current shapes: `11-subsystem` is one component in one
  SAR; `11.a-multi-component-subsystem-lab` hosts `alphacomp` and `betacomp`;
  `11.c-subsystem-mixed-component-lab` combines a standalone CAR and a
  bundled CAR in a SAR; `11.d-implicit-subsystem-lab` exercises an implicit
  single-component Subsystem; and `11.e-sar-dir-lab` exercises an expanded
  `sar.d` with `component/testcomp.car` and a subsystem descriptor.
- Phase 58.1 owns identity and parent membership for these future fixtures;
  Phase 58.2 owns archive production; Phase 58.3/58.4 own resolution and
  composition; Phase 58.7 owns Help/Admin projections; and Phase 58.8 owns
  their cross-repository acceptance. Source collection details remain a
  successor implementation input and are not selected here.

## Ownership Map

| Concern | Current owner/evidence | Frozen successor owner |
| --- | --- | --- |
| Canonical Component identity and descriptor projection | CNCF `ComponentDescriptor.scala`, `ComponentDescriptorLoader.scala`; `docs/spec/component-identity.md` | Phase 58.1 |
| CAR extraction and current CAR runtime admission | CNCF `CarExtractor.scala` and its `CarRuntimeAdmission` seam | Phase 58.2 for child packaging; Phase 58.3 for resolution/activation boundary |
| SAR extraction and subsystem archive shape | CNCF `SarExtractor.scala`; Cozy `CozySarPublisher.scala` | Phase 58.2 |
| Project metadata, archive manifests, integrity/dependencies | Cozy `CozyArchivePackager.scala`, `CozyCarRuntimeManifest.scala`, `car-project-metadata-ownership.md` | Phase 58.1 for fields; Phase 58.2 for package validation |
| Repository index/publication/release visibility | Cozy `ComponentRepositoryIndex.scala`, `CozyCarPublisher.scala`, `component-repository-publication.md` | Phase 58.2 |
| Runtime repository/cache and known-artifact lookup | CNCF `ComponentRepository.scala`, `ComponentRepositorySpace.scala`, `canonical-car-repository-resolution.md` | Phase 58.3 |
| Bootstrap and lifecycle activation | CNCF `CncfRuntimeBootstrapPart.scala`, `CncfRuntimeInstanceLifecyclePart.scala` | Phase 58.3 and 58.6 |
| Parent composition and Subsystem assembly | CNCF `GenericSubsystemDescriptor.scala`, `GenericSubsystemFactory.scala`; cncf-samples 11.* | Phase 58.1 for registry membership; Phase 58.4 for composition; 58.8 for integration |
| Help/Admin/Web resources | CNCF `HelpProjection.scala`, `StaticFormAppRendererComponentAdminPart.scala`, `WebResourceRoot.scala`; textus-sample-apps cwitter | Phase 58.7 |
| Build/generated source evidence | sbt-cozy resolver/fixtures; Cozy generation metadata and packager | Phase 58.2 for packaging evidence; Phase 58.7/58.8 for consumer/integration evidence |

## Proposal Conflict Ledger and Reconciliation

| Existing proposition | Conflict | Frozen reconciliation |
| --- | --- | --- |
| The architecture proposal makes every declared Subcomponent an independently identifiable Component with a CAR. | The resource proposal said a resource role does not create another Component namespace or runtime participant. | For a declared Subcomponent, the architecture proposal wins: the child is an independent Component with canonical identity and its own CAR. The resource/payload role is not itself a Component and does not become executable. |
| The architecture proposal distinguishes the parent registry from child identity and runtime activation. | The resource proposal modeled one logical Component release with physical Documentation/SourceCode artifacts. | A parent logical release may include child membership and payload references, but the declared child retains its independent logical identity, release evidence, and CAR. Physical payload provenance remains separate from logical identity. |
| The resource proposal offered a concrete precedence chain and a single resolution-state vocabulary. | The broader proposal left resolver machinery and state names open. | Preserve the distinctions (development, expanded, local, cache, remote, offline, missing, stale, corrupt, restricted) as evidence and inputs. Phase 58.3 chooses the resolver API and concrete state representation; authorization, integrity, and availability remain separate dimensions. |
| The architecture proposal separates publication, discovery, and activation. | The resource proposal used “publication completeness” and mode tables that could be read as automatic runtime policy. | Publication completeness is an admission/release-visibility condition. Discovery/availability does not activate a child. Mode policy is a successor input for Phase 58.4, and no mode automatically grants operation, MCP, disclosure, or deployment authority. |
| The architecture proposal permits external-platform artifacts carried by a child. | The resource proposal focused on non-executable resources and did not define platform deployment. | Retain the distinction: the child has CNCF CAR identity/metadata; explicit platform deployment remains owned by the external platform and CNCF never silently falls back to it. |
| Parent membership and Subsystem composition appeared together in both notes. | A registry membership edge could be mistaken for general Subsystem membership. | Parent relationship is a distinct relation. A Subsystem may compose Components without making them parent/child members, and a child may be described without being activated in a Subsystem. |
| The resource proposal suggested one state enum for availability/integrity/access. | The architecture concerns require independent authorization, integrity, and availability decisions. | Do not collapse those dimensions into one enum/state. Exact types and diagnostics are successor-owned. |

## Frozen RSC-01 Decisions and Invariants

The following decisions are frozen for Phase 58 successors:

1. Every declared Subcomponent is an independent CNCF Component with canonical
   identity and its own CAR.
2. Documentation and SourceCode are initial role vocabulary for the
   information payload. The payload is non-authoritative and non-executable
   and is not itself a Component.
3. The parent composition registry references child canonical identity and
   membership; it does not duplicate mutable identity or grant activation,
   operation, MCP, disclosure, or deployment authority.
4. Role is separate from implementation technology.
5. An external-platform child still has a CNCF CAR identity/metadata surface.
   Deployment of its platform artifact is explicit; CNCF never silently falls
   back to automatic platform deployment.
6. Parent relationship is separate from general Subsystem membership.
7. Logical release identity is separate from physical artifact identity,
   digest, path, and provenance.
8. Publication completeness is separate from runtime activation.
9. Discovery/availability is separate from activation.
10. Resolved provenance retains logical identity plus physical source evidence.
11. Authorization, integrity, and availability are orthogonal dimensions.
    Phase 58 must not prematurely collapse them into one enum/state.
12. Concrete registry schema, APIs, type names, wire formats, lifecycle
    machinery, and resolver implementation are owned by Phase 58.1 through
    Phase 58.8, but cannot reinterpret these invariants.

## Bounded Successor Vocabulary

The following terms remain intentionally unresolved implementation vocabulary.
Each is allocated to a successor and is not a reason to reopen the frozen
invariants:

| Successor | Vocabulary left for implementation | Constraint |
| --- | --- | --- |
| 58.1 | registry schema/version, field names, role encoding, child-reference and unknown-field types | Must represent independent identity, parent membership, role/technology separation, and orthogonal release/physical identity |
| 58.2 | child CAR/payload layout, manifest locations, archive suffixes, publication transaction and visibility selectors | Must preserve deterministic packaging, integrity evidence, and publication/activation separation |
| 58.3 | resolver API/types, source-kind and provenance records, cache/offline source model, concrete precedence diagnostics | Must preserve discovery/availability versus activation and retain logical plus physical evidence |
| 58.4 | operation-mode names, development composition policy, explicit external-platform handoff API | Must not make mode or payload an authority grant |
| 58.5 | authorization, integrity, disclosure, signature, key-rotation, and diagnostic representations | Must keep authorization, integrity, and availability orthogonal |
| 58.6 | lifecycle/coalescing/cancellation state types, cache ownership, refresh, and observability records | Must not change identity or activation boundaries |
| 58.7 | Help/Admin consumer API and sanitized projection type names, resource route integration | Consumers must use resolved provenance and must not walk physical storage directly |
| 58.8 | cross-repository fixture coordinates, adapter wiring, and end-to-end evidence format | Must prove the frozen contract without adding a new architectural interpretation |

Successor implementation may choose schema, API, type, and wire names within
these allocations. It may not use an unresolved name choice to reinterpret an
invariant.

## Exact Acceptance Boundary

RSC01-A1 records inventory and reconciles the proposals only. The exact
failing-first acceptance identities and matrix are owned by the separate
RSC01-B slice. They are deliberately not authored here. They must be recorded
by RSC01-B before successor implementation Phases 58.1 through 58.8 consume
this handoff, and must not be deferred from those successor phases.

## Promotion and Closure

This note is intentionally provisional. Phase 58 may not treat it as a
normative implementation shortcut. Once the model, codecs, resolver behavior,
cross-repository packaging, and executable acceptance are verified across the
Phase 58.1 through Phase 58.8 successors, Phase 58.9 must promote the accepted
architecture to `docs/design` and the behavior to `docs/spec`, update dependent
Phase 59 and Phase 60 contracts, and mark this proposal historical.
