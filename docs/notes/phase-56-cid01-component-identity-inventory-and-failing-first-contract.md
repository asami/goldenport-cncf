# Phase 56 CID-01 - Component Identity Inventory and Failing-first Contract

status=promoted to docs/design/component-identity.md and docs/spec/component-identity.md by CID-08
date=2026-08-07
phase = [Phase 56](../phase/phase-56.md)
checklist = [Phase 56 checklist](../phase/phase-56-checklist.md)
ledger = [CAR migration ledger](phase-56-cid01-car-migration-ledger.yaml)
step = CID-01 - inventory and executable contract freeze
slices = CID-01A, CID-01B, CID-01C, CID-01D, CID-01E

This note is the historical Phase-56 inventory and failing-first contract.
CID-08 promoted its verified rules to
`docs/design/component-identity.md` and `docs/spec/component-identity.md`, which
are now normative. The inventory and stable rule IDs remain evidence; they are
not a second identity authority.

## Sole authority contract

The only identity authority is the pair `namespace + local id`. A release
version is independent release metadata. Display names, summaries, titles, and
localizations are non-identifying presentation metadata. Derived fields never
author identity and must be recomputed or verified at every boundary.

The frozen authoring shape is one `project:` mapping at the Phase schema paths:

```yaml
project:
  namespace: org.simplemodeling.textus
  id: UserAccount
  component:
    version: 0.6.0-SNAPSHOT
    displayName: Textus User Account
```

The exact target type names are `ComponentNamespace`, `ComponentLocalId`,
`ComponentId`, and `ComponentInstanceId`; implementation belongs to CID-02 and
later. The qualified ID is `namespace + "." + exact local id`.

## Stable CID-01 rules

The following rules were the stable working-spec rules frozen by CID-01.
CID-08 has promoted their verified behavior to the final normative design and
specification; these IDs remain historical traceability keys. The rule IDs are
domain-scoped, so the same numeric ID in CNCF, Cozy, and sbt-cozy is not an
alternate cross-domain rule.

### CNCF component identity rules

| Rule | Stable rule |
| --- | --- |
| CID01-R1 | `namespace + id` is the sole Component identity authority; version and display metadata are not identity. |
| CID01-R2 | `ComponentId(String)` is a compatibility parser: a bare local ID such as `UserAccount` is rejected. |
| CID01-R3 | `Component.Core.create` rejects a `name` that diverges from the exact `ComponentId` identity. |
| CID01-R4 | `ComponentId(String)` admits an exact namespace-qualified spelling such as `org.simplemodeling.textus.UserAccount` without punctuation normalization. |
| CID01-R5 | `ComponentInstanceId.default` retains the exact qualified Component ID. |
| CID01-R6 | Distinct namespaces with the same local ID remain isolated through subsystem admission. |
| CID01-R7 | An ambiguous bare compatibility alias is rejected with a diagnostic naming the ambiguity, bare alias, and every candidate qualified ID. |

### Cozy project rules

| Rule | Stable rule |
| --- | --- |
| CID01-R1 | Project authoring has one canonical `project.namespace`, `project.id`, `project.component.version`, and `project.component.displayName` source; derived names are not second inputs. |
| CID01-R2 | Scaffold, archive, publisher, descriptor, and build projections are complete and recomputed from that canonical project source. |
| CID01-R3 | Word, acronym, digit, package, artifact, class, and path projections use the shared deterministic projection contract. |
| CID01-R4 | A collision is rejected only within the projection scope that must be unique; equal human-facing filenames may coexist across namespace-qualified repository keys. |
| CID01-R5 | Any supplied derived value that disagrees with the canonical project source is rejected. |
| CID01-R6 | Legacy identity lint is version-sensitive: the exact frozen release may defer, while a semantically greater version with legacy identity requires migration; lower, uncomparable, or malformed versions are separate errors. |

### sbt-cozy coordinate rules

| Rule | Stable rule |
| --- | --- |
| CID01-R1 | SBT receives one canonical identity authority from `project.namespace`, `project.id`, and `project.component.version`. |
| CID01-R2 | Organization, module, CAR filename, Maven coordinate, and version are deterministic projections of that authority. |
| CID01-R3 | Descriptor and manifest emit qualified identity metadata and verify every materialized projection. |
| CID01-R4 | CAR dependency declarations retain namespace-qualified identity. |
| CID01-R5 | Repository and cache destinations retain namespace-qualified identity. |
| CID01-R6 | Human-facing filename collisions do not collapse namespace-qualified coordinates. |
| CID01-R7 | Only actual admitted project metadata participates in coordinate generation; scenario fixtures do not create production identity behavior. |
| CID01-R8 | Generated descriptor and manifest identity fields must agree exactly. |
| CID01-R9 | Semantic identity conflicts are rejected rather than normalized or silently reconciled. |

### Machine-precise release deferral rule

The same rule is repeated in the Phase document, checklist, and each of the
four deferred ledger records:

`effective_version == current_release (exact release equality) => deferred; semantically comparable and effective_version > current_release while legacy identity remains => migration-required; effective_version < current_release OR versions are uncomparable OR effective_version is malformed => separate version/inventory error, never deferred or migration-required.`

### Validation and projection rules

* A namespace has at least two dot-separated lowercase ASCII segments. Every
  segment matches `[a-z][a-z0-9]*`. Java and Scala reserved segments are
  rejected. Namespace input is neither normalized nor escaped.
* A local ID is ASCII UpperCamel matching `[A-Z][A-Za-z0-9]*`. Separators,
  whitespace, Unicode normalization, and escaping are rejected.
* A word boundary is inserted after a lower-case letter or digit before an
  upper-case letter, and before the final upper-case letter of an acronym when
  it is followed by lower-case text. Digits remain with their preceding token.
  The frozen examples are `UserAccount -> user-account/useraccount`,
  `HTTPGateway -> http-gateway/httpgateway`,
  `OAuth2Client -> oauth2-client/oauth2client`, and
  `HTTP2Gateway -> http2-gateway/http2gateway`.
* Maven group is exactly the namespace. The artifact is the final namespace
  segment plus `-` plus kebab local ID. The JVM package is the namespace plus
  `.` plus the lower-flat local ID. The generated class is the local ID plus
  `Component`; a local path segment is kebab local ID. A CAR filename is
  `artifact + "-" + version + ".car"`. The Scala suffix remains a Maven
  tooling projection (`_3` in the current Scala 3 builds).
* Equality is exact namespace plus exact local ID. Version and display metadata
  are excluded. No namespace is guessed from an organization, package,
  artifact, project name, alias, or filename; invalid canonical inputs are
  rejected.
* If distinct canonical IDs collide in a unique scoped projection, admission
  rejects the collision. The same human-facing filename is allowed for
  different namespaces only when repository and Maven keys retain the full
  namespace. An ambiguous compatibility alias is rejected.

## Existing authority and consumer inventory

The following inventory names the current source of each value, its current
shape, and the owner/stage that must adopt the frozen contract. A value marked
`legacy-independent-derived-fields` is evidence of the counterexample shape;
it is not a canonical identity.

| Repository / path | Symbol or field | Current shape | Target owner / stage |
| --- | --- | --- | --- |
| `simplemodeling-lib/src/main/scala/org/goldenport/util/StringUtils.scala` | `StringUtils.toKebabCase` and private tokenizer | Generic kebab conversion already handles lower/digit-to-upper and acronym-final boundaries; no namespace/local-ID validator or full qualified `Component` identity exists. | simplemodeling-lib generic foundation, if needed; CID-02 |
| CNCF `src/main/scala/org/goldenport/cncf/component/Component.scala` | `ComponentId(name: String)` | One free-form `name`; `ComponentId("UserAccount")` is accepted as globally complete. | CNCF typed identity; CID-02/CID-05 |
| CNCF `Component.scala` | `ComponentInstanceId(name, instance)`, `default`, `normalizeLabel`, `canonicalKey` | Bare raw component names are copied, then UniversalId punctuation is normalized to `_`; dotted qualified `ComponentId` input is rejected before an instance can be built, and no exact typed `ComponentId` field is retained. | CNCF instance identity and compatibility adapter; CID-02/CID-05/CID-06 |
| CNCF `Component.scala` | `Component.Core.name`, `componentId`, `instanceId`, `Core.create` | `name` and `componentId.name` are independently supplied and can diverge. | CNCF runtime single authority; CID-05 |
| CNCF `component/ComponentDescriptor.scala` and `DescriptorRecordLoader.scala` | `ComponentDescriptor.name`, `componentName`, `version`; Record codecs and aliases (`component`, `componentName`, `name`) | Legacy descriptor fields are decoded independently; version is carried beside name. | CNCF descriptor admission and compatibility; CID-04/CID-06 |
| CNCF `subsystem/GenericSubsystemDescriptor.scala` | `GenericSubsystemComponentBinding.componentName`, `runtimeComponentName`, `toComponentDescriptor`; `GenericSubsystemDescriptor` load/Record codecs and aliases | Bindings author a bare/kebab/prefixed component string plus optional version, instance, and coordinate. | CNCF subsystem admission; CID-05/CID-06 |
| CNCF `subsystem/GenericSubsystemFactory.scala` | `resolveDescriptorC`, runtime descriptor resolution, component archive/dev-dir/repository branches | Factory resolves descriptor and component names through legacy paths and materializes bindings. | CNCF factory and admission; CID-05/CID-06 |
| CNCF `component/repository/ComponentRepository.scala` | standard component paths, cache roots, artifact/descriptor/version resolution | Repository and cache paths are keyed by component/artifact names; dependency and archive lookup do not carry a typed namespace. | CNCF repository/index/cache/dependency keys; CID-04/CID-05 |
| CNCF `repository/ComponentRepositoryIndex.scala` | `ComponentRepositoryIndexEntry.artifactId`, `identity`, catalog paths | CAR/SAR index identity is `(kind, artifactId)` and catalog paths are artifact-scoped. | CNCF repository index with namespace-retaining keys; CID-04 |
| CNCF `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala` and `src/main/scala/org/goldenport/cncf/component/ComponentSpace.scala` | component assembly/admission, `_component_space`, `findComponent`, name locators | Assembly injects and locates components by names; operation routing and authorization selectors use component name strings. | CNCF subsystem assembly, admission, routing, and resolvers; CID-05 |
| CNCF `subsystem/resolver/OperationResolver.scala` | route component selector and resolver maps | Route keys contain component/service/operation names and normalized selector matching. | CNCF routing/resolver adoption; CID-05 |
| CNCF `projection/HelpProjection.scala`, `projection/MetaProjectionSupport.scala` | help/describe tree component labels and metadata | Help and projection paths expose runtime `component.name` as presentation and lookup metadata. | CNCF Help/Admin/projection identity projection; CID-05/CID-08 |
| CNCF `component/builtin/admin/AdminComponent.scala` | built-in Admin `name`/`ComponentId` and selectors | Admin is a component with a separately authored name and ID. | CNCF Admin identity and presentation boundary; CID-05 |
| CNCF `config/ComponentParameterDiagnostics.scala`, `observability/*Diagnostics.scala` | component and instance diagnostic fields | Diagnostics retain component name/instance strings and can display normalized IDs. | CNCF canonical diagnostics and compatibility warnings; CID-05/CID-06 |
| `cozy/src/main/scala/cozy/config/CozyProjectYamlConfig.scala` and `cozy/src/main/scala/cozy/scaffold/CozyScaffold.scala` | project config, scaffold identity, `component.className`, `project.scalaPackage` | `project.yaml` is the visible authoring source, but name, class, package, organization, and project name are independently available/compatible fields. | Cozy canonical project authoring and projections; CID-03 |
| `cozy/src/main/scala/cozy/archive/*` and `cozy/src/main/scala/cozy/CozyCarPublisher.scala` | CAR archive descriptor, runtime manifest, archive filename, `CozyCarPublisher` / `RepositoryArtifactPublisher` | Archive, descriptor, and publication destinations derive from legacy project/component fields and version. | Cozy archive descriptor and publisher; CID-04/CID-07 |
| `cozy/src/main/scala/cozy/archive/ComponentRepositoryIndex.scala`, `cozy/src/main/scala/cozy/RepositoryArtifactCatalog.scala`, and `cozy/src/main/scala/cozy/lint/*` | repository index, CAR lint, project/release diagnostics | Repository/catalog and lint classify artifact/project names; version-sensitive migration state is not yet the CID-01 contract. | Cozy repository index/lint and migration diagnostics; CID-04/CID-07 |
| `sbt-cozy/src/main/scala/org/goldenport/cozy/CozyPlugin.scala` | `CozyProjectConfig`, `moduleName`, `organization`, `version`, `cozyCarName`, manifest metadata, repository destinations | SBT reads `project.component.version` (or a literal in legacy builds), defaults `cozyCarName` to `${moduleName}-${version}`, and publishes under artifact/version paths. | sbt-cozy build metadata, CAR filename, manifest, and destinations; CID-03/CID-04 |
| `sbt-cozy/src/main/scala/org/goldenport/cozy/CarDependencyResolver.scala` | `CarDependency(name, version)` and repository URL/path construction | CAR dependency keys carry name/version but no namespace-qualified identity. | sbt-cozy dependency/repository keys; CID-04 |
| CAR projects under `/Users/asami/src/dev2026/textus-*` | top-level `project.yaml` `project.name`, `organization`, `scalaPackage`, `component.name`, `className`, `version` | All 18 records in the [ledger](phase-56-cid01-car-migration-ledger.yaml) use `legacy-independent-derived-fields`; 14 are SNAPSHOT migration-required and four are release-deferred. | Each CAR repository owner; CID-07 |
| launcher/CBD/BoK consumers (later edges) | launcher configs and scripts, Textus CBD Support catalog records, Textus BoK component identities | Consumers accept project/artifact/descriptor spellings and transport catalog evidence without owning canonical Component identity. | launcher, CBD, and BoK maintainers; CID-08 |

### Canonical and legacy spellings

| Spelling | Classification at CID-01 | Contract |
| --- | --- | --- |
| `UserAccount` / other bare UpperCamel | compatibility alias or local-ID input only | Never a globally complete ID; reject when namespace is required or ambiguous. |
| `user-account` / other kebab | derived projection or compatibility alias | Artifact/path projection; never an identity authoring input. |
| `textus-user-account` | artifact/release projection and Web compatibility alias | Final namespace leaf plus kebab local ID; `/web/textus-user-account/...` remains presentation compatibility only. |
| project name (`textus-user-account`) | project/release metadata | May name a repository, but cannot author identity independently. |
| component name (`UserAccount`, `textus-user-account`, or `aws-component`) | legacy descriptor/runtime input | Decode-only compatibility; must be verified against canonical projections. |
| `className` (`UserAccount`, `TextusAi`, etc.) | generated-class projection or legacy independent field | Target is local ID plus `Component`; current values are evidence, not authority. |
| `scalaPackage` | JVM package projection or legacy independent field | Target is namespace plus lower-flat local ID; no package-to-namespace guessing. |
| descriptor `name`, `component`, `componentName` | descriptor compatibility aliases | All resolve through one canonical adapter; disagreement and ambiguity reject. |
| `org.simplemodeling.textus.UserAccount` | canonical qualified ID | Exact namespace + `.` + local ID; punctuation is not normalized. |
| Web alias `/web/textus-user-account/...` | presentation/compatibility alias | Routes may remain supported but never become a Component ID. |

## Cross-repository acceptance registry

The exact acceptance identities below record the CID-01 freeze. Ownership is
the repository path that subsequently provided the behavior in CID-02 through
CID-07. References to pending targets describe their state at the CID-01
freeze, not their current status. A scenario-only `NotImplemented` SPI path was
a deferred boundary, not an identity algorithm or public API behavior.

| Identity | Stable rules and documentary target | Owning path / stage |
| --- | --- | --- |
| CNCF E1 | `CID01-R1,R2`: the compatibility parser rejects bare `UserAccount`; target remains pending. Qualified admission belongs to E3 under `CID01-R4,R5`. | CNCF `component/Component.scala`; CID-02 |
| CNCF E2 | `CID01-R1,R3`: divergent `Core.name` is rejected; target remains pending. | CNCF `component/Component.scala`; CID-05 |
| CNCF E3 | `CID01-R4,R5`: qualified parsing and exact default-instance retention; target remains pending. | CNCF `component/Component.scala`; CID-02/CID-05 |
| CNCF E4 | `CID01-R6,R7`: generated namespace isolation and `SubsystemAssemblyAdmission.resolveC` rejection of an ambiguous bare alias with both candidates; target remains pending. | CNCF `subsystem/SubsystemAssemblyAdmission.scala`, `GenericSubsystemDescriptor.scala`, `GenericSubsystemFactory.scala`, `Subsystem.scala`; CID-05/CID-06 |
| Cozy E5 | `CID01-R1,R2`: canonical project-only authoring and complete deterministic projections; documentary target pending. | Cozy scaffold/archive/publisher; CID-03/CID-04 |
| Cozy E6 | `CID01-R3,R4`: deterministic acronym/digit projections and scoped collision rejection; documentary target pending at scenario-only `NotImplemented` SPI boundary. | Cozy naming/scaffold/lint plus `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/ProjectIdentityContractScenarioSpi.scala`; CID-02/CID-07 |
| Cozy E7 | `CID01-R5,R6`: disagreement rejection and version-sensitive lint classification; documentary target pending. | Cozy lint/repository; CID-07 |
| sbt-cozy E8 | `CID01-R1,R2,R3`: one authority produces coordinate projections and qualified descriptor metadata; documentary target pending at scenario-only `NotImplemented` SPI boundary. | sbt-cozy `CozyPlugin.scala` plus `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CarCoordinateContractScenarioSpi.scala`; CID-03/CID-04 |
| sbt-cozy E9 | `CID01-R4,R5,R6`: namespace-retaining dependency/repository/cache keys and filename-collision isolation; documentary target pending at the same scenario-only `NotImplemented` SPI boundary. | sbt-cozy `CarDependencyResolver.scala`, repository destinations, and `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CarCoordinateContractScenarioSpi.scala`; CID-04 |
| sbt-cozy E10 | `CID01-R7,R8,R9`: actual metadata admission, descriptor/manifest agreement, and semantic conflict rejection; documentary target pending at the same scenario-only `NotImplemented` SPI boundary. | sbt-cozy bridge/manifest generation and `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CarCoordinateContractScenarioSpi.scala`; CID-03/CID-04 |

## Historical documentary evidence and target pairings

At the CID-01 freeze, the worktrees and inventories were documentary evidence
and each CNCF E1-E4 leaf ended in `pendingUntilFixed`. CID-02 through CID-07
subsequently implemented the behavior and activated those assertions; the
current `Phase56ComponentIdentityContractSpec` has no CID-01 terminal pending
block. Cozy and sbt-cozy likewise replaced their scenario-only target
boundaries with the accepted implementations recorded by their owning Steps.

* E1 targets compatibility parsing: bare `UserAccount` rejects
  (`CID01-R1,R2`). Qualified admission belongs exclusively to E3
  (`CID01-R4,R5`).
* E2 targets rejection of a divergent `Core.name` (`CID01-R1,R3`).
* E3 targets exact qualified identity retention in the default instance
  (`CID01-R4,R5`).
* E4 targets generated namespace isolation and `resolveC` rejection of an
  ambiguous bare `UserAccount`, with a diagnostic naming both candidates
  (`CID01-R6,R7`).

CID-01 made no red-build or implementation claim: its pending assertions were
contract markers. CID-02 through CID-07 now own the accepted production
behavior, and CID-08 promotes it to the normative documents.

## Modified-program compliance ledger

| Program / artifact | CID-01 evidence and obligation | Status |
| --- | --- | --- |
| CNCF runtime and specs | Inventory froze E1-E4 as Given/When/Then target contracts; later Steps activated the assertions. | Implemented and accepted by CID-02, CID-05, and CID-06 |
| Cozy | Inventory froze project/scaffold/archive/publisher/repository/lint ownership and E5-E7 target pairings. | Implemented and accepted by CID-03, CID-04, and CID-07 |
| sbt-cozy | Inventory froze build metadata, `cozyCarName`, manifest, `CarDependency`, repository ownership, and E8-E10 pairings. | Implemented and accepted by CID-03 and CID-04 |
| 18 CAR repositories | Read-only ledger records full HEAD, dirty state, project.yaml hash/dirty state, version source, legacy fields, target identity, projections, divergence, and migration classification. | CID-07 closed 14 canonical SNAPSHOT migrations; 4 exact release deferrals remain |
| launcher / CBD / BoK | CID-01 named the later adoption edges. | CID-08 static audit complete |

## Explicit exclusions

CID-01 does not add a Component identity API, schema, generator, validator,
repository key, runtime compatibility behavior, CAR migration, version update,
publication, deployment, launcher/CBD/BoK adoption, or phase completion claim.
It does not normalize or reconcile current CAR divergence silently. Cozy and
sbt-cozy scenario-only `NotImplemented` SPI paths are deferred boundaries, not
identity algorithms or API behavior. It does not include fixture, target-output,
SAR, sample, library-only, or launcher-only projects in the CAR ledger.
CID-02 through CID-07 own the pending fixes; CID-08 owns ecosystem regression
and normative closure.
