# Phase 59 DOC-01A — Source-to-Package Inventory and Failing-First Acceptance Registry

status = working DOC-01A handoff
date = 2026-08-23
phase = Phase 59 — Component Documentation Contract Inventory
step = DOC-01A-HANDOFF
slice = DOC-01A1-source-to-package-handoff
kind = non-normative inventory and acceptance evidence

This note is the single DOC-01A source-to-package handoff. It is an inventory
of current evidence and a registry of future failing-first acceptance
identities. It creates no behavior, public contract, schema, API, type, wire
format, package layout, or runtime policy. The [Phase 59](../phase/phase-59.md)
and [Phase 59 checklist](../phase/phase-59-checklist.md) remain the current
scope authority. Phase 59.1 may extend the DOC-01 inventory, but it MUST NOT
silently reinterpret accepted DOC-01A evidence.

## Authority and Inputs

The current DOC-01A scope is governed by the [Phase 59 phase
document](../phase/phase-59.md) and its [DOC-01A
checklist](../phase/phase-59-checklist.md). The working proposal
[Component Documentation Knowledge Package Implementation](component-documentation-knowledge-package-implementation.md)
is non-normative and is used only as context.

The canonical Phase 58 inputs admitted read-only by this handoff are:

- [Component and Subcomponent Architecture design](../design/component-subcomponent-architecture.md)
  and [specification](../spec/component-subcomponent-architecture.md).
- [Component Resource Subcomponent design](../design/component-resource-subcomponent.md)
  and [specification](../spec/component-resource-subcomponent.md).

The accepted baseline is `HEAD 36e22bf6877d0fc01a5b5fbae56b91bf208a4510`
with tracked diff hash
`151383942f4e67a8037d566963317eabd9605cd3291ad70ecb28d24bf441e216`.
Those baseline and diff identities are evidence inputs, not permission to
rewrite any existing file.

## Evidence Snapshot

The four repositories were inspected read-only at these branches and heads:

| Repository role | Repository | Branch | HEAD | Working-tree observation |
| --- | --- | --- | --- | --- |
| DOC-01A handoff owner and sole mutation repository | `cloud-native-component-framework` | `main` | `36e22bf6877d0fc01a5b5fbae56b91bf208a4510` | Parent-owned Phase 59 planning diff and successor notes are preserved. |
| CAR documentation lint, archive projection, and publisher semantics | `cozy` | `main` | `238a078869b2d5ceffe0bf3677355b7e9bff3c83` | Clean at inspection. |
| SBT generation, package, and publication wiring | `sbt-cozy` | `main` | `4af1446a96e181cf86860533d00957e6aff3b87c` | Pre-existing dirty `src/test/scala/org/goldenport/cozy/CarDependencyResolverSpec.scala`; excluded from new acceptance evidence and not modified. |
| SmartDox parsing and HTML/PDF projection | `smartdox` | `Scala12` | `b84e7959011d1989a0d5ad660cf808eebb85268b` | Pre-existing dirty `docs/phase/phase-8-checklist.md`; excluded from new acceptance evidence and not modified. |

External repositories were inspected read-only. Their dirty files are excluded
from mutation and from claims that depend on clean-tree acceptance. No external
repository change is part of this handoff.

## Phase 58 Admitted Input

DOC-01A records, but neither changes nor reimplements, the following closed
Phase 58 authority:

- canonical Component/Subcomponent identity and CAR membership;
- logical identity versus physical identity and provenance;
- required-release completeness as separate from runtime activation;
- deterministic resolver outcomes;
- orthogonal authorization, integrity, and availability;
- runtime-owned `Develop`, `Test`, `Demo`, and `Production` policy;
- read-only Help/Admin consumer projection;
- no independent scan and no second resolver.

Membership grants no activation, operation, MCP readiness, deployment, or
disclosure. Operation mode does not enter Component-domain APIs. The Phase 58
resolver and its resolved-resource/provenance result remain the only runtime
resolution authority.

## CNCF Inventory

The following paths are current CNCF evidence. “Gap” records the source-to-
package question handed to a later Phase; it is not an implementation defect
to repair in DOC-01A.

| Evidence paths | Current fact | Gap | Owner |
| --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/projection/HelpProjection.scala`; `src/test/scala/org/goldenport/cncf/projection/GeneratedHelpProjectionSpec.scala` | Help projects component, service, operation, and generated metadata views from the supplied Component model. | Current surfaces lack one knowledge-manifest consumer; the DOC-01A registry must not make Help a resolver. | CNCF Help/read-only presentation; Phase 59.5 consumes the accepted boundary. |
| `src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala`; `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`; `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`; `docs/design/web-layer.md` | HTTP dispatch, Web/static-form rendering, and the web-layer descriptors expose existing runtime presentation surfaces. | The separate Manual (`/man`) and Web surfaces must not become a second resolver or an implicit package scanner. | CNCF Web/HTTP read-only presentation. |
| `src/main/scala/org/goldenport/cncf/projection/OpenApiProjection.scala`; `src/test/scala/org/goldenport/cncf/spec/OpenApiProjectorSpec.scala`; `src/test/scala/org/goldenport/cncf/specification/SCENARIO/OpenApiProjectionScenarioSpec.scala` | OpenAPI projection describes existing operation/request/response and Web constraints, including multipart and locale-aware text cases. | No knowledge-manifest authority or resource resolution is established by the OpenAPI projection. | CNCF OpenAPI read-only presentation. |
| `src/main/scala/org/goldenport/cncf/projection/McpProjection.scala`; `src/main/scala/org/goldenport/cncf/mcp/McpToolCatalog.scala`; `src/test/scala/org/goldenport/cncf/spec/McpProjectorSpec.scala`; `src/test/scala/org/goldenport/cncf/mcp/McpToolCatalogSpec.scala`; `src/test/scala/org/goldenport/cncf/specification/SCENARIO/McpProjectionScenarioSpec.scala` | MCP is a deterministic projection of MCP-ready operations and rejects duplicate tool identities; it does not discover CAR resources. | RSC08 is not yet a common knowledge consumer. MCP readiness and tool projection must remain distinct from knowledge authority. | CNCF MCP read-only presentation; later DOC-07/DOC-08 consumers. |
| `src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala`; `src/main/scala/org/goldenport/cncf/http/WebResourceRoot.scala`; `src/main/scala/org/goldenport/cncf/http/StaticFormAppAssets.scala`; `src/main/scala/org/goldenport/cncf/http/StaticFormAppLayout.scala`; `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`; `src/main/scala/org/goldenport/cncf/http/StaticFormAppRendererConfig.scala`; `src/main/scala/org/goldenport/cncf/http/StaticFormAppRendererTemplatePart.scala`; `src/test/scala/org/goldenport/cncf/http/Http4sHttpServerDispatchSpec.scala`; `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`; `docs/design/web-layer.md` | Existing HTTP, WebResourceRoot, and static-form implementation/specification files establish serving and form/resource presentation boundaries. The design documents `Manual` as a separate presentation surface and documents `/man` routes; those route and descriptor paths are conventions, not current CNCF file evidence in this row. | A future knowledge package must identify its source and authority without turning Web descriptors or static-form assets into a runtime resolver. | CNCF Web/resource presentation; Manual and `/man` are read-only documented presentation boundaries. |
| `src/main/scala/org/goldenport/cncf/component/repository/ResolvedComponentResources.scala`; `src/main/scala/org/goldenport/cncf/projection/ResolvedComponentResourcesConsumerProjection.scala`; `src/test/scala/org/goldenport/cncf/component/repository/ResolvedComponentResourcesSpec.scala`; `src/test/scala/org/goldenport/cncf/projection/ResolvedComponentResourcesConsumerSpec.scala` | Resolved resources preserve the Phase 58 logical/physical result and expose a consumer projection with availability, integrity, authorization, and provenance distinctions. | There is not yet one knowledge-manifest consumer. DOC-01A records the handoff only; it must not add a scan or resolver. | Phase 58 runtime resolution/provenance/integrity/access/mode authority; CNCF consumer projection. |
| `src/main/scala/org/goldenport/cncf/component/repository/ComponentResourceAuthorizationPolicy.scala`; `src/test/scala/org/goldenport/cncf/component/repository/ComponentResourceAuthorizationSpec.scala`; `src/main/scala/org/goldenport/cncf/cli/ComponentResourceOperationModePolicy.scala`; `src/test/scala/org/goldenport/cncf/cli/ComponentResourceOperationModePolicySpec.scala` | Authorization and operation-mode policies are separate, runtime-owned policy surfaces. | Component-specific authority is separate from framework installed/online/RAG snapshot authority, which Phase 59.1 owns. No membership-based disclosure or mode leakage is admitted. | Phase 58 policy authority; Phase 59.1 framework snapshot boundary. |

## Cozy and sbt-cozy Inventory

| Evidence paths | Current fact | Gap | Owner |
| --- | --- | --- | --- |
| `cozy/docs/design/car-documentation-lint.md`; `cozy/src/main/scala/cozy/lint/CozyCarLint.scala`; `cozy/src/test/scala/cozy/lint/CozyCarLintSpec.scala` | Cozy CAR documentation lint recognizes compatibility suffixes and manual entry points, reports presence/thinness, and separates deterministic lint from semantic review. Normal lint warns while strict lint makes missing or thin documentation release-blocking. | Presence/thinness is not by itself parse/link/asset safety or source/archive equivalence. The accepted future contract must make those checks attributable. | Cozy CAR lint/content semantics. |
| `cozy/src/main/scala/cozy/archive/CozyArchivePackager.scala`; `cozy/src/test/scala/cozy/CozyArchivePackagerSpec.scala`; `cozy/src/test/scala/cozy/archive/CozyArchivePackagerCv06Spec.scala` | Archive packaging maps the configured CAR content and uses `src/main/car` content, descriptors, ABI, and generation evidence as applicable. | The source/archive projection must define normalization, provenance, exclusions, and repeated-build equivalence; DOC-01A does not change packaging. | Cozy archive projection. |
| `cozy/src/main/scala/cozy/archive/RepositoryArtifactPublisher.scala`; `cozy/src/test/scala/cozy/CozyCarPublisherSpec.scala` | Publisher writes/publishes repository artifacts and metadata from supplied package evidence. | Publisher does not synthesize Component documentation or knowledge semantics; content ownership remains with the Component and Cozy content paths. | Cozy publication/archive semantics. |
| `cozy/build.sbt` | Cozy build explicitly disables ordinary documentation artifacts (`Compile / packageDoc / publishArtifact := false` and empty `Compile / doc / sources`) while CAR documentation remains a separate content concern. | There is no Component Scaladoc packaging/profile contract here, and this handoff must not infer one from ordinary Maven docs. | Cozy build policy; sbt-cozy supplies later wiring. |
| `sbt-cozy/src/main/scala/org/goldenport/cozy/CozyPlugin.scala`; publication and bridge fixtures under `sbt-cozy/src/sbt-test/cozy/` (including `component-subcomponent-end-to-end`, `subcomponent-release-publication`, and `review-evidence`) | sbt-cozy owns SBT inputs, generation, CAR/SAR package/publication tasks, repository wiring, and publication/review fixtures; it can map managed source and task outputs into the handoff. | There is no filtered/managed-source contract or Component Scaladoc contract in this evidence. Cozy owns content semantics; sbt-cozy owns SBT task wiring and handoff, not the meaning of Component documentation. | sbt-cozy SBT inputs/tasks/handoff. |

## SmartDox Inventory

| Evidence paths | Current fact | Gap | Owner |
| --- | --- | --- | --- |
| `smartdox/src/main/scala/org/smartdox/parser/Dox2Parser.scala`; `smartdox/src/test/scala/org/smartdox/parser/DoxMarkdownParserSpec.scala` | `Dox2Parser.Config.markdown` selects the Markdown parsing profile, while `Dox2Parser.Config.default` is the default parse path used by the ordinary string/file parse APIs and by PDF input handling. | There is no Component packaging/profile, digest, or stale-projection contract in SmartDox evidence. | SmartDox parsing. |
| `smartdox/src/main/scala/org/smartdox/transformers/Dox2HtmlTransformer.scala`; `smartdox/src/test/scala/org/smartdox/transformers/Dox2DomHtmlTransformerSpec.scala` | SmartDox supplies deterministic Dox-to-HTML/DOM transformation evidence. | HTML projection does not establish Component authority, package membership, source provenance, or stale detection. | SmartDox HTML projection. |
| `smartdox/src/main/scala/org/smartdox/service/operations/PdfOperationClass.scala`; `smartdox/src/main/scala/org/smartdox/service/operations/SiteOperationClass.scala`; `smartdox/src/test/scala/org/smartdox/service/operations/SiteOperationClassSpec.scala` | The evidence has three projection/rendering paths: HTML/DOM, PDF, and site publication. PDF supports Chrome-headless, Asciidoc, and LaTeX renderer choices, with LaTeX as the default; Site delegates to the SmartDox site generator. Renderer dependencies are build/publication dependencies and never enter Component runtime. | No Component packaging/profile, source digest, attributable failure, or stale-projection contract is present. | SmartDox PDF/site projection. |

## Checklist Input-Profile Traceability

The Phase 59 checklist subjects below are explicit handoff inputs and current
gaps; they do not add a contract or implementation to DOC-01A.

| Checklist subject | Admitted current evidence | DOC-01A boundary |
| --- | --- | --- |
| Manual and `/man` | `src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala`; `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`; `src/test/scala/org/goldenport/cncf/http/Http4sHttpServerDispatchSpec.scala`; `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`; `docs/design/web-layer.md` | HTTP/static-form files are current CNCF presentation evidence. Manual ownership and `/man` route names are documented conventions in `docs/design/web-layer.md`; no absent convention directory is presented as current evidence. |
| Source filtering | `cozy/src/main/scala/cozy/archive/CozyArchivePackager.scala`; `cozy/src/test/scala/cozy/CozyArchivePackagerSpec.scala`; `docs/phase/phase-59.md` | Cozy archive source selection and exclusion behavior is packaging evidence. The Phase 59 filtering contract remains a later gap; DOC-01A adds no filter. |
| License | `docs/phase/phase-59.md` (source inclusion, exclusion, license, and authorization acceptance); `cozy/src/main/scala/cozy/archive/CozyArchivePackager.scala`; `cozy/src/test/scala/cozy/CozyArchivePackagerSpec.scala` | License is an explicit Phase 59 input subject. The admitted archive evidence has no license field/check, so this inventory claims no license behavior and adds none. |
| Restricted access | `src/main/scala/org/goldenport/cncf/component/repository/ComponentResourceAuthorizationPolicy.scala`; `src/test/scala/org/goldenport/cncf/component/repository/ComponentResourceAuthorizationSpec.scala`; `docs/spec/component-resource-subcomponent.md` | Authorization, integrity, and availability remain distinct; restricted content is represented without disclosure. No membership-derived disclosure is added. |
| `Develop`, `Test`, `Demo`, and `Production` operation-mode inputs | `src/main/scala/org/goldenport/cncf/cli/ComponentResourceOperationModePolicy.scala`; `src/test/scala/org/goldenport/cncf/cli/ComponentResourceOperationModePolicySpec.scala`; `docs/spec/component-resource-subcomponent.md` | Mode policy remains runtime-owned and outside Component-domain APIs. The Phase 58 policy table is input evidence only; DOC-01A adds no mode behavior. |

## Ownership and Conflict Boundaries

| Concern | Owning boundary | Explicit non-transfer |
| --- | --- | --- |
| Component-specific manuals, model descriptions, examples, source, and knowledge content | Component repository authors | Must not be synthesized by publisher or reinterpreted as framework authority. |
| Parse and render | SmartDox | Renderer implementation does not become runtime Component behavior or identity authority. |
| CAR lint, content rules, and archive projection | Cozy | Presence, thinness, links/assets, and archive evidence stay distinct until a later contract admits them together. |
| SBT inputs, generation tasks, package/publication tasks, and handoff evidence | sbt-cozy | Task wiring does not define Component documentation semantics. |
| Logical resolution, physical provenance, integrity, authorization, availability, and mode | Phase 58 runtime | No second resolver, independent scan, or mode parameter in the Component domain. |
| Help, Web, OpenAPI, and MCP views | CNCF read-only presentation | These views do not acquire disclosure authority or scan CAR/repository/cache content. |
| Shared framework installed/online/RAG snapshot authority | Phase 59.1 | It is separate from Component-specific authority and cannot override the Phase 58 resolved Component view. |

The boundaries freeze the following conflicts: no second resolver; no
independent scan; no runtime rendering; no `OperationMode` in the domain; no
disclosure from membership; and no authority conflation. Membership does not
grant activation, operation, MCP readiness, deployment, or disclosure.

## Authoritative Acceptance Matrix

The following eight stable group identities are frozen for successor phases.
Each identity is unique in this registry. The suites and scripts are future
acceptance locations; DOC-01A makes no test or source change in them.

| Stable group ID | Owner Phase | Repository | Exact future path(s) | Exact suite/script | Exact scenarios | Common failing-first rule |
| --- | --- | --- | --- | --- | --- | --- |
| `DOC02-KNOWLEDGE-MANIFEST-CODEC` | 59.2 | CNCF | `src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifestSpec.scala` | `org.goldenport.cncf.knowledge.ComponentKnowledgeManifestSpec` | DOC02-AC-01 deterministic identity/role/path/digest/authority/provenance codec; DOC02-AC-02 duplicate/unsafe/digest-invalid reject; DOC02-AC-03 Phase58 binding without another resolver/identity | Materialize the exact identity, record attributable RED, then GREEN the same identity. |
| `DOC03-CAR-DOCUMENTATION-LINT` | 59.3 | cozy | `src/test/scala/cozy/lint/CozyCarLintSpec.scala` | `cozy.lint.CozyCarLintSpec` | DOC03-LINT-AC-01 entries/admitted source; DOC03-LINT-AC-02 links/assets/unique/source-package; DOC03-LINT-AC-03 strict missing/thin/unparseable/stale | Materialize the exact identity, record attributable RED, then GREEN the same identity. |
| `DOC03-SOURCE-ARCHIVE-PROJECTION` | 59.3 | cozy+sbt-cozy | cozy `src/test/scala/cozy/archive/ComponentSourceArchiveProjectionSpec.scala`; sbt-cozy `src/sbt-test/cozy/component-source-archive-projection` | cozy `cozy.archive.ComponentSourceArchiveProjectionSpec` + sbt-cozy script `component-source-archive-projection` | DOC03-SRC-AC-01 normalization; DOC03-SRC-AC-02 provenance/exclusions; DOC03-SRC-AC-03 repeated-build/source-archive equivalence with policy | Materialize the exact identity, record attributable RED, then GREEN the same identity. |
| `DOC03-SCALADOC-PACKAGING` | 59.3 | sbt-cozy | `src/sbt-test/cozy/component-scaladoc-packaging` | script `component-scaladoc-packaging` | DOC03-SDOC-AC-01 generate/package; DOC03-SDOC-AC-02 disclosure; DOC03-SDOC-AC-03 missing/stale strict failure | Materialize the exact identity, record attributable RED, then GREEN the same identity. |
| `DOC03-SMARTDOX-PROJECTION` | 59.3 | smartdox | `src/test/scala/org/smartdox/service/operations/SmartDoxPublicationProjectionSpec.scala` | `org.smartdox.service.operations.SmartDoxPublicationProjectionSpec` | DOC03-DOX-AC-01 parse selected model; DOC03-DOX-AC-02 deterministic HTML/PDF profiles; DOC03-DOX-AC-03 attributable source/digest/failure without runtime renderer | Materialize the exact identity, record attributable RED, then GREEN the same identity. |
| `DOC04-RESOLVED-KNOWLEDGE-COMPOSITION` | 59.4 | CNCF | `src/test/scala/org/goldenport/cncf/knowledge/ResolvedComponentKnowledgeSpec.scala` | `org.goldenport.cncf.knowledge.ResolvedComponentKnowledgeSpec` | DOC04-AC-01 exact Phase58/no scan; DOC04-AC-02 policy preserved/no mode domain; DOC04-AC-03 structured attributable incomplete failure | Materialize the exact identity, record attributable RED, then GREEN the same identity. |
| `DOC05-HELP-RESOURCE-AUTHORITY` | 59.5 | CNCF | `src/test/scala/org/goldenport/cncf/projection/ComponentKnowledgeHelpProjectionSpec.scala` | `org.goldenport.cncf.projection.ComponentKnowledgeHelpProjectionSpec` | DOC05-AC-01 one admitted view; DOC05-AC-02 Component versus framework authority; DOC05-AC-03 auth/prod visibility truthful | Materialize the exact identity, record attributable RED, then GREEN the same identity. |
| `DOC09-SOURCE-TO-PACKAGE-END-TO-END` | 59.9 | sbt-cozy | `src/sbt-test/cozy/component-knowledge-source-to-package` | script `component-knowledge-source-to-package` | DOC09-AC-01 byte/digest/inventory equivalence; DOC09-AC-02 restricted/mode Phase58 authority; DOC09-AC-03 runtime Help without build/render toolchain | Materialize the exact identity, record attributable RED, then GREEN the same identity. |

## Failing-First Protocol

- DOC-01A records no tests and no RED result now. It registers identities only.
- A successor must materialize the exact path and identity, produce an
  attributable RED result, and make that same identity GREEN. A RED result
  must name the missing or contradicted source-to-package evidence.
- Pending, ignored, source-text-only, metadata-only, or un-attributed claims
  are not acceptance evidence.
- If an identity, owner, repository, path, or scenario changes, the successor
  returns to PLAN and updates this registry explicitly; it must not silently
  rename or replace an accepted identity.

## Successor Handoff and Non-Goals

Phase 59.1 consumes this note read-only and adds DOC-01B only: public
publication, Directive, Skill, CBD, and BoK boundary inventory. Phase 59.2
waits for the combined DOC-01 review and the accepted predecessor handoff.

This DOC-01A slice does not implement behavior, tests, manifests, packaging,
Help routes, a new resolver, a scan, runtime rendering, status closure,
external edits, SBT/Cozy/Dox commands, validation, review, staging, commit,
publication, deployment, push, cleanup, or any successor implementation. It
does not select a public API/schema/type or reinterpret Phase 58. Existing
baseline and unrelated dirty bytes remain unchanged.
