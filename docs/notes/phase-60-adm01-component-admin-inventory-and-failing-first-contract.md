# Phase 60 ADM-01A — Component Admin Inventory and Failing-First Contract

status = frozen inventory/acceptance handoff, non-normative
date = 2026-08-28
phase = Phase 60
stage = ADM-01A

This note is the sole retained Phase 60 ADM-01 inventory and contract handoff.
It records current evidence and future acceptance identities for the successor
phases. It changes no behavior, API, model, resolver, route, authorization,
persistence, schema, test, or phase status. It creates or executes no future
acceptance specification.

## Authority and division

Phase 60 and `docs/phase/phase-60-checklist.md` are the authority for this
handoff. The approved split retains ADM-01 here; ADM-02 through ADM-09 belong
to successor Phases 60.1 through 60.8. This note preserves the Phase 55
configuration provenance, Phase 58 resolved-resource contract, and Phase 59
knowledge/model manifest authority.

Help is the human/AI knowledge surface. Admin is the operator-facing surface
for a concrete loaded Component and its Subsystem context. They may link to
one another, but neither is a second resolver, scanner, documentation
generator, or source of disclosure or management authority.

## Current evidence inventory

The following paths are current source evidence only. None is the future
unified Admin view model or a Phase 55/58/59 consumer unless stated by its
existing contract.

- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRendererComponentAdminPart.scala`
  contains component-scoped static-form pages, CRUD, and manual/document
  links based on `Subsystem`/`Component`. It is not the future unified Admin
  view model and is not the Phase 55/58/59 consumer.
- `src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala`
  is the existing built-in operational, CRUD, and assembly-action component.
  Its `_subsystem_wiring`, `_load_descriptor_record`, and
  `_load_wiring_from_text` inspect a supplied descriptor path/SAR/ZIP for
  current assembly introspection. These are direct physical-read locations,
  not reusable by the future Admin view.
- `src/main/scala/org/goldenport/cncf/projection/HelpProjection.scala` and
  `src/main/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHelpContract.scala`
  define the Help human/AI knowledge surface. `ComponentKnowledgeHelpContract.accessC`
  is membership/route-only and delegates actual resource enforcement to
  `ResolvedComponentResourcesConsumerProjection`; it neither reads nor
  transforms content.
- `src/main/scala/org/goldenport/cncf/config/CncfRuntimeConfigurationProjection.scala`
  produces typed `ConfigurationBindingCollection` values from already-loaded
  snapshots. It must not load physical sources or a merged configuration.
- `src/main/scala/org/goldenport/cncf/component/repository/ResolvedComponentResources.scala`
  is the sole resource resolution, provenance, availability, integrity, and
  authorization result. It grants no activation, operation, MCP, disclosure,
  or deployment authority.
- `src/main/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifest.scala`
  and
  `src/main/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifestConsumerContract.scala`
  provide the manifest and already-derived consumer authority for component,
  release, resource, knowledge, and model references. They do not provide an
  independent resolver.
- `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala` contains
  current component assembly/lookup and admitted runtime bindings. Its
  `components` and `findComponent` facts are source evidence; they do not
  permit inference of an instance or release.
- `src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala`
  `_exact_entity_id` is an existing collection-equality check. Later ADM-05
  must require a declared backing `EntityCollection` and reject a scalar
  locator, foreign canonical ID, entropy fallback, missing owner, and
  ambiguous owner. No entity-ID implementation changes occur here.

## Identity record

These axes are distinct and must not be inferred from one another:

- Component class;
- logical release;
- loaded Component instance;
- Subsystem class;
- Subsystem instance; and
- implicit Component Subsystem.

Logical release occurs in `ComponentResourceLogicalIdentity` and
`ComponentKnowledgeManifest`. Runtime lookup by component name is not
multi-version or instance authority.

## No-scan and authority boundary

Future Admin consumes typed configuration, already-resolved resource output,
the manifest/consumer contract, and runtime-owned facts. It must not scan
CARs, repositories, development directories, source/documentation trees, or
invoke the existing assembly descriptor readers. Help and Admin may link, but
neither becomes a second resolver or gains disclosure or management authority.
Visibility or membership never derives invocation, activation, operation, MCP,
disclosure, deployment, or other management authority.

## Failing-first acceptance registry

Every row below is a future target owned by the named successor phase. ADM-01
does not create or execute any listed path or test, and does not claim that it
exists today. The common rule is: materialize that exact identity before
behavior is implemented, capture attributable RED, then make that same
identity GREEN.

| Identity | Owner | Future executable specification | Required scenario focus |
| --- | --- | --- | --- |
| `ADM02-IDENTITY-VIEW-MODEL` | Phase 60.1 | `src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminViewModelSpec.scala` | codec/version/identity ambiguity/multi-instance scenarios |
| `ADM03-CONFIGURATION-COMPOSITION-NO-SCAN` | Phase 60.2 | `src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminConfigurationCompositionProjectionSpec.scala` | typed winner/override/provenance and same-resolved-resource/no-scan scenarios |
| `ADM04-CONTRACT-MODEL-READ-ONLY` | Phase 60.3 | `src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminContractModelProjectionSpec.scala` | model/diagram/contract provenance and no invocation/management authority scenarios |
| `ADM05-RUNTIME-DATASTORE-IDENTITY` | Phase 60.4 | `src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminRuntimeDatastoreProjectionSpec.scala` | lifecycle/datastore/instance isolation plus declared EntityCollection exact-ID rejection, including DEV-004 cases |
| `ADM06-DOCUMENTATION-NAVIGATION-NO-SCAN` | Phase 60.5 | `src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminDocumentationNavigationSpec.scala` | manifest-backed resource state/navigation and no documentation generation/scan/resolution scenarios |
| `ADM07-AUTHORIZED-MANAGEMENT` | Phase 60.6 | `src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminAuthorizedManagementSpec.scala` | authorization, input, lifecycle, audit, forbidden/conflict/unavailable/stale/retry scenarios |
| `ADM08-SURFACE-SECURITY` | Phase 60.7 | `src/test/scala/org/goldenport/cncf/component/admin/ComponentAdminSurfaceSecuritySpec.scala` | Web/HTTP/CLI/machine surface, redaction, source disclosure, path safety, integrity, hostile metadata, multi-user/version/instance/subsystem scenarios |
| `ADM09-CANONICAL-CLOSURE` | Phase 60.8 | `docs/design/component-admin.md`, `docs/spec/component-admin.md`, and accumulated ADM02–ADM08 suites | normative evidence and final reconciliation scenarios |

## Explicit exclusions

This handoff excludes an Admin view model, projection, routes, management
actions, scans, a new resolver, configuration authority, operation-mode
change, canonical design/spec, and any phase or checklist status update. It
also excludes all Scala, test, build, configuration, fixture, runtime, route,
package, and artifact modifications. ADM-02 through ADM-09 remain separate
successor-phase work.

Executable-spec authoring is not applicable: ADM-01 changes no executable
behavior and only registers future exact acceptance identities.
