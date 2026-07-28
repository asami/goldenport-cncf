# Phase 51 CV-01 Version-Source Inventory

Status: historical CV-01 implementation evidence plus the current accumulated
Phase 51 source state. The CV-01 baseline sections preserve the values accepted
at that stage and do not decide the later CV-02 compatibility or ownership
policy.

## Current accumulated Phase 51 source state

The current CI-01 accumulator supersedes the CV-01 baseline for worktree and
validation decisions:

| Repository / boundary | Current source state |
| --- | --- |
| CNCF | artifact `0.5.2-SNAPSHOT`; `simplemodeling-model` dependency `0.2.1-SNAPSHOT` |
| Cozy | artifact `0.3.1-SNAPSHOT`; SimpleModeler dependency `1.1.25-SNAPSHOT` |
| sbt-cozy | artifact `0.1.16-SNAPSHOT` |
| simple-modeler | artifact `1.1.25-SNAPSHOT` |
| ArtScene | component `0.1.2-SNAPSHOT`; CNCF compile and runtime minimum/tested `0.5.2-SNAPSHOT` |
| simplemodeling-model | artifact `0.2.1-SNAPSHOT`; CI-01 exact-identity source and specification are modified |

CI-01 REVIEW_FIX passed all 351 CNCF tests across the six focused suites and
all six simplemodeling-model `EntityIdSpec` scenarios, with `Test/compile`
passing in both repositories. The freshly packaged model JAR preceded the old
Ivy-local artifact on the CNCF validation classpath. No
simplemodeling-model SNAPSHOT was published. The later canonical-contract
REVIEW_FIX gate passed all 27 CNCF scenarios across four suites plus
`Test/compile`, without publishing an artifact.
The compliance-only REVIEW_FIX then passed all six `EntityIdSpec` scenarios
and all 112 CNCF scenarios across the four repaired large specifications,
with `Test/compile` passing in both repositories.

## Development-source admission (CV-01 baseline)

At CV-01 closure, Phase 51 implementation changes were admitted only in the
development SNAPSHOT source repositories then in scope. The owning
repositories had been transitioned before the CV-01 docs/spec changes were
reapplied:

| Repository | Authoritative version file | Development version |
| --- | --- | --- |
| CNCF | `build.sbt` (`root` version) | `0.5.2-SNAPSHOT` |
| Cozy | `build.sbt` (`version`) | `0.3.1-SNAPSHOT` |
| sbt-cozy | `build.sbt` (`ThisBuild / version`) | `0.1.16-SNAPSHOT` |
| simple-modeler | `build.sbt` (`version`) | `1.1.25-SNAPSHOT` |
| ArtScene | `project.yaml` (`project.component.version`) | `0.1.2-SNAPSHOT` |
| simplemodeling-model | unchanged authoritative build | release and clean; inventory-only |

At that baseline, no unavailable inter-repository SNAPSHOT was introduced.
CNCF generation and consumer compile inputs remained on released prerequisites
where a local publication would have been required: Cozy’s CNCF dependency was
`0.5.1`, ArtScene’s CNCF compile dependency was `0.5.1`, and consumer sbt-cozy
defaults were released coordinates. No `publishLocal` or `publish` was run
during CV-01.

## Effective version-source inventory (CV-01 baseline)

| Repository / stage | Effective artifact and source | Override, precedence, or gap |
| --- | --- | --- |
| CNCF build | Own artifact `0.5.2-SNAPSHOT`; Scala `3.3.8`; Cozy generator `0.3.0`; model dependency `0.2.0` | Own version is a development admission. Generator and model are separate released prerequisites. |
| CNCF CML generation | `generateInformationCmlModel` invokes pinned Cozy `0.3.0` for `src/main/cozy/information.cml` with the root `version.value`, generated runtime descriptor, and its SHA-256 | Target selection is explicit and the build contract rejects absent inputs before launch. |
| CNCF runtime descriptor | `generateCncfRuntimeDescriptor` writes `META-INF/cncf/runtime.yaml`; the generation input task consumes that exact output and computes its SHA-256 | Descriptor generation and CML invocation are one explicit dependency path. |
| Cozy build and CLI | Own `0.3.1-SNAPSHOT`; CNCF target `0.5.1`; model `0.2.0`; CLI accepts `--runtime`, `--cncf-version`, `--cncf-runtime-descriptor`, and model version | CLI, project config, environment, bridge overrides, and delegated command selection remain mutable/ambient until later policy work. |
| Cozy sbt bridge | Forwards runtime, CNCF version, model version, and optional descriptor | `generation.versions.*`, `cozyGenerationVersionOverrides`, `.cozy/config.yaml`, environment, and Coursier/delegate settings can alter inputs. |
| Cozy scaffold/CAR | Emits `build.cozyVersion`, exact CNCF compile dependency, and `packaging.car.runtime.cncf`; the Cozy scaffold's generated sbt plugin default is `0.1.15-SNAPSHOT` | This generated default is distinct from a consumer project's plugin override; compile target and runtime range have distinct meanings but no single Phase 51 precedence contract yet. |
| sbt-cozy | Own `0.1.16-SNAPSHOT`; descriptor extraction reads `META-INF/cncf/runtime.yaml` and rejects absent/multiple descriptors | The scaffold default is `0.1.15-SNAPSHOT`; ArtScene explicitly selects the current `0.1.16-SNAPSHOT` development plugin so its packaged-CAR runtime resolver is available without an ambient override. |
| simple-modeler | Own `1.1.25-SNAPSHOT`; scaffold fallback remains `sampleVersion("CNCF_VERSION", "cncf-version.conf", "0.4.8")` | Environment wins over file, file over fallback; fallback is stale. Generated scaffold has no model dependency. |
| simplemodeling-model | Release and clean; no independent CNCF generation/version input found | Inventory-only; it owns model semantics, not Phase 51 compatibility. |
| ArtScene | Own `0.1.2-SNAPSHOT` from `project.yaml`; Scala `3.3.8` from `project.yaml`; CNCF compile dependency remains released `0.5.1`; effective sbt-cozy plugin defaults to explicit development `0.1.16-SNAPSHOT` from `project/plugins.sbt` | `CNCF_VERSION`, `CNCF_VERSION_FILE`, and fallback `0.5.1-SNAPSHOT` are mutable/ambient script inputs; the plugin property/environment expression remains an emergency override. `project.yaml` owns current ArtScene component/build metadata; the component remains development SNAPSHOT while its Scala, compile, and runtime declarations are effective build inputs. |
| simplemodeling-model build and delegation | Own release `0.2.0`; `project/plugins.sbt` applies sbt-cozy `0.1.14`; `build.sbt` enables `CozyPlugin`, selects backend `cozy`, and delegates to `/Users/asami/src/dev2025/cozy` through `cozyDelegateProjectDir` | The plugin and delegate path are effective build inputs for simplemodeling-model. They describe how its build/generation is delegated, not an additional CNCF artifact/version source. The repository remains release-valued and inventory-only. |

### Tracked-source reconciliation history

The following rows are a chronological reconciliation record rather than one
coherent CV-01 snapshot. Individual values reflect the owning Phase 51 stage
that last reconciled that source, including later CV-06/CV-07 updates. The
current accumulated source-state table above remains authoritative for
worktree and validation decisions.

| Tracked source | Value recorded by owning stage | Ownership and lifecycle meaning | Contradiction or later mapping |
| --- | --- | --- | --- |
| Cozy `project/plugins.sbt` | `addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.14")` | This is the sbt-cozy plugin used to build Cozy itself. It is a build-tool input for the Cozy repository, not the version of the Cozy CLI artifact and not the plugin version generated into a new CAR consumer. | It differs from the CozyScaffold generated default `0.1.16-SNAPSHOT` and ArtScene's explicit `0.1.16-SNAPSHOT` consumer selection. The ownership/precedence contract belongs to CV-06; development/release admission belongs to CV-07. |
| Cozy `src/main/scala/cozy/scaffold/CozyScaffold.scala` | `_default_sbt_cozy_version = "0.1.16-SNAPSHOT"` | This is the generated CAR scaffold's default `project/plugins.sbt` coordinate when no consumer override is supplied. It describes a newly generated consumer build, not the plugin used to build Cozy itself. | It aligns newly generated CARs with the current Phase 51 sbt-cozy development contract while remaining distinct from Cozy's own build plugin input. Mutable SNAPSHOT versus immutable release handling maps to CV-07. |
| ArtScene `project/plugins.sbt` | `sbtCozyVersion` defaults to `0.1.16-SNAPSHOT`; `addSbtPlugin(... % sbtCozyVersion)` | This is the explicit development consumer build-plugin coordinate required by ArtScene's packaged-CAR runtime resolver. The property/environment expression remains an emergency override path. | ArtScene does not depend on an ambient override and does not use Cozy's own build plugin (`0.1.14`). Consumer override precedence and generated-project lifecycle meaning map to CV-06; release immutability maps to CV-07. |
| ArtScene `project.yaml` | component version `0.1.2-SNAPSHOT`; compile `org.goldenport::goldenport-cncf:0.5.1`; runtime minimum/tested `0.5.1` | `project.yaml` is the current authoritative source for ArtScene component identity, build dependency metadata, and declared CAR runtime compatibility as projected by the Cozy build. The component is still development SNAPSHOT while its CNCF compile/runtime values are released coordinates. | The component lifecycle differs from the release-valued CNCF dependency and runtime declaration. Script inputs can still select `CNCF_VERSION` independently, including fallback `0.5.1-SNAPSHOT`; this contradiction maps to CV-02/CV-03 and development/release handling to CV-07. |
| ArtScene `src/main/car/assembly-descriptor.yaml` | subsystem and ArtScene component `0.1.2-SNAPSHOT`; other components: AI runtime `0.2.1`, scraper `0.1.1`, toolchain runner `0.2.1` | This checked-in assembly descriptor identifies the packaged assembly/component set. Its self coordinate must match the current `project.yaml` component version; dependency coordinates remain independent runtime inputs. | Cozy package admission now rejects a missing or stale assembly subsystem/primary-component version instead of packaging contradictory identity. |
| ArtScene generated `component-descriptor.json` | no source-managed descriptor; packaging derives `0.1.2-SNAPSHOT` from `project.yaml` | The generated archive descriptor is packaging/runtime identity evidence for the CAR component without adding a second source authority. | The stale `src/main/car/component-descriptor.yaml` source was removed; Cozy validates the generated descriptor against the package coordinate. |
| ArtScene `src/main/catalog/car/textus-art-scene.yaml` | catalog `recommended`/`latestStable` `0.1.1`; entry version `0.1.1`; runtime minimum/tested `0.5.1` | This catalog entry describes the published/stable warehouse artifact, not the current development project version. Its `0.1.1` is a stable publication identity and its CNCF value is runtime admission metadata. | It coexists with the current project `0.1.2-SNAPSHOT` and checked-in descriptors `0.1.1`; that may reflect a release/catalog lifecycle boundary, but the sources are not reconciled here. Mapping: CV-06 for metadata ownership, CV-07 for release/catalog lifecycle. |
| ArtScene `README.md` | component version `0.1.2-SNAPSHOT`; generated-source path Scala `3.3.8` | This is human-oriented generated-project guidance projected from the current build identity. | It now agrees with current `project.yaml`; `project.yaml` remains authoritative. |
| ArtScene historical values | initial project `0.1.0-SNAPSHOT` (commit `478b557`), released `0.1.0` (commit `181c886`), released `0.1.1` (commit `73125f3`); runtime tested/minimum `0.4.13`, then `0.5.0`, then `0.5.1` | These values show the project/component and CNCF runtime compatibility lifecycle across historical releases. They are evidence, not current authority. | The stable catalog remains `0.1.1`, while current source/package self identity advances together to `0.1.2-SNAPSHOT`. Lifecycle and publication consistency map to CV-06/CV-07. |

## Lifecycle, contradictions, and gaps (CV-01 baseline)

The effective path is CML -> Cozy CLI/sbt bridge -> generated Scala -> CAR
compile dependency -> scaffold/package/review/publication -> CNCF runtime
descriptor and activation. Version evidence is duplicated across build files,
project metadata, generated files, descriptors, environment variables,
warehouse contents, and local delegate paths. SNAPSHOT versus release is now
explicit for the five implementation sources, while their unreleased
inter-repository dependencies remain released to avoid requiring publication.

Observed contradictions/gaps include CNCF `0.5.2-SNAPSHOT` versus Cozy’s
released CNCF target `0.5.1`, simple-modeler fallback `0.4.8`, ArtScene’s
`0.5.1-SNAPSHOT` script fallback, Cozy's own sbt-cozy `0.1.14`, the
CozyScaffold default `0.1.15-SNAPSHOT`, ArtScene's explicit
`0.1.16-SNAPSHOT`, and
ArtScene's `0.1.2-SNAPSHOT` project identity versus `0.1.1` descriptors and
catalog versus the README's `0.1.0-SNAPSHOT`/Scala `3.3.7`. These are recorded
evidence, not CV-02 policy decisions, and no ArtScene descriptor or Scala file
is normalized in CV-01. Descriptor availability/multiplicity and some digest
evidence exist, but complete provenance, precedence, tamper, collection
identity, and persisted-scalar contracts remain later-stage work.

## Exhaustive tracked-source scan (CV-01 baseline)

On 2026-07-27, all tracked files in the six inventoried repositories were
scanned read-only for effective version declarations and current guidance in
build, project, plugin, Scala, CNCF, Cozy, SimpleModeler, model, runtime,
catalog, descriptor, and README surfaces. The scan included generated-source
templates and shell/environment resolution because those are effective inputs
when their owning build invokes them. The result is:

| Repository | Effective tracked declarations admitted to CV-01 | Explicitly non-effective or historical material |
| --- | --- | --- |
| CNCF | `build.sbt`: artifact `0.5.2-SNAPSHOT`, Scala `3.3.8`, Cozy generator `0.3.0`, model `0.2.0`; generated runtime/catalog tasks and checked-in runtime catalog values are catalog projections, not the own development coordinate | Checked-in catalog release rows and README overview contain historical/released guidance only; no additional current CNCF/Cozy/Scala source was found in tracked descriptor or README files |
| Cozy | `build.sbt`: `0.3.1-SNAPSHOT`, Scala `2.12.18`, CNCF `0.5.1`, model `0.2.0`, SimpleModeler default `1.1.24`; `project/plugins.sbt`: build plugin `0.1.14`; CLI/bridge/scaffold sources resolve `--runtime`, `--cncf-version`, descriptor, and generated `build.cozyVersion`; `CozyScaffold.scala` emits Scala `3.3.8` and default sbt-cozy `0.1.16-SNAPSHOT` | `src/sbt-test/**` build files and checks are fixture-specific scripted tests; historical catalog entries and README prose are not current project coordinate authority |
| sbt-cozy | `build.sbt`: plugin artifact `0.1.16-SNAPSHOT`, Scala `2.12.20`; descriptor extraction and digest properties are runtime/plugin inputs | `src/sbt-test/**` release/SNAPSHOT versions are isolated fixtures; README contains no competing effective coordinate |
| simple-modeler | `build.sbt`: artifact `1.1.25-SNAPSHOT`, Scala `2.12.18`; `ScalaRealmTransformerBase.scala`: generated Scala `3.3.8`, CNCF resolution `CNCF_VERSION` -> `cncf-version.conf` -> `0.4.8` fallback, generated project version `0.0.1-SNAPSHOT` | Generated-project values are template outputs, not simple-modeler’s own artifact; scripted/test fixture coordinates and historical docs are non-effective for the current project |
| ArtScene | `project.yaml`: component `0.1.2-SNAPSHOT`, Scala `3.3.8`, CNCF compile `0.5.1`, runtime minimum/tested `0.5.1`; `project/plugins.sbt`: explicit development sbt-cozy `0.1.16-SNAPSHOT`; `scripts/lib/cncf-common.sh`: `CNCF_VERSION` -> `CNCF_VERSION_FILE` -> `0.5.1-SNAPSHOT` fallback | Checked-in assembly/catalog `0.1.1` are release/package identities; README `0.1.0-SNAPSHOT` and Scala `3.3.7` are stale human/generated guidance; historical release commits are evidence only |
| simplemodeling-model | `build.sbt`: artifact `0.2.0`, Scala `3.3.8`, `enablePlugins(CozyPlugin)`, `cozyGeneratorBackend := "cozy"`, and `cozyDelegateProjectDir := Some(file("/Users/asami/src/dev2025/cozy"))`; `project/plugins.sbt`: effective sbt-cozy plugin `0.1.14`; it owns model semantics and has no independent CNCF generation coordinate | Release and clean status is intentional inventory-only input. Its effective build path delegates Cozy generation/build support to the development Cozy repository; README/docs and test fixtures contain no additional current CNCF declaration. |

No tracked effective declaration in these six repositories was omitted from
the tables above, including simplemodeling-model's sbt-cozy plugin and Cozy
delegation/build path. The scan did not modify any source declaration, descriptor,
catalog, README, environment fallback, or generated-project template.

## Acceptance identity mapping

| Acceptance identity | Registration | Later stage |
| --- | --- | --- |
| compatible exact pair | Cozy CV-02 executable admission only | CV-02 |
| incompatible pair | Cozy CV-02 executable typed rejection only | CV-02 |
| unsupported CNCF/Cozy coordinate | Cozy CV-02 executable admission only | CV-02 |
| missing version | Cozy CV-02 executable admission only | CV-02 |
| descriptor identity mismatch | sbt-cozy CV-01 boundary observation, deferred to CV-04 | CV-04 |
| descriptor byte/digest tampering | sbt-cozy CV-01 boundary observation, deferred to CV-05 | CV-05 |
| contradictory source precedence | Cozy CV-02 production resolver executable contradiction only | CV-02 |
| SNAPSHOT vs immutable release | Cozy CV-02 production admission only | CV-02 |
| provenance/digest | sbt-cozy CV-01 boundary observation, deferred to CV-05 | CV-05 |
| CAR compile target vs runtime range separation | simple-modeler CV-01 boundary observation, deferred to CV-06 | CV-06 |
| collection identity | sbt-cozy CV-01 boundary observation, deferred to CI-01 | CI-01 |
| persisted scalar projection | Cozy CV-01 boundary observation, deferred to SP-01 | SP-01 |

## CV-01 compliance ledger

All Scala changes below are dedicated new spec files. They use prose public
case names, Given/When/Then boundaries, `should` matcher support, active
50-iteration production-boundary properties, and canceled cases for behavior
intentionally deferred beyond CV-01.

| Repository/file | Naming result | Spec-style result | Validation | Final phase commit |
| --- | --- | --- | --- | --- |
| CNCF `src/test/scala/org/goldenport/cncf/repository/Phase51Cv01AcceptanceSpec.scala` | whole-file scan passed | production catalog property and complete Given/When/Then boundaries in the production repository package; later slices remain canceled | 1 succeeded, 4 canceled; 50 property iterations | pending |
| Cozy `src/test/scala/cozy/config/Phase51Cv01AcceptanceSpec.scala` | whole-file scan passed | five parser/deferred scenarios in the production config package; production YAML provenance property | 0 succeeded, 5 canceled; 50 property iterations completed before the owning cancellation | pending |
| Cozy `src/test/scala/cozy/archive/Phase51Cv01CarCmlSourceSpec.scala` | whole-file scan passed | CAR source-resolution Given/When/Then scenario in the production archive package | 0 succeeded, 1 canceled | pending |
| sbt-cozy `src/test/scala/org/goldenport/cozy/Phase51Cv01AcceptanceSpec.scala` | whole-file scan passed | four transport/descriptor/provenance boundary scenarios place parsing after When; production config property | 0 succeeded, 4 canceled; 50 property iterations completed before the owning cancellation | pending |
| simple-modeler `src/test/scala/org/simplemodeling/parser/Phase51Cv01AcceptanceSpec.scala` | whole-file scan passed | three parser/generation-boundary scenarios in the production parser package with complete deferred boundaries; production model-parser identity property | 0 succeeded, 3 canceled; 50 property iterations completed before the owning cancellation | pending |
| ArtScene `src/test/scala/org/simplemodeling/textus/artscene/Phase51Cv01AcceptanceSpec.scala` | whole-file scan passed | YAML decoding follows When; production version property; deferred mismatch observes both real values | 2 succeeded, 1 canceled; 50 property iterations; normal CAR lint at that validation point passed with only the pre-existing missing ABI-baseline warning | pending |

The five version-file transitions, this note/checklist, and six focused
acceptance spec files across five repositories are the CV-01 changes. After
removing the six Cozy-owned admission duplicates from the CNCF spec, the
focused CV-01 total is 21
scenarios. CV-02 now implements production source
resolution and exact-pair admission in Cozy.
The CNCF property exercises the production `ComponentRepositoryIndex` parser
and renderer; it is not a test-local identity algorithm. Deferred acceptance
behavior remains canceled and mapped to its later stage.

## CV-02 compliance ledger

The CV-02 production and specification files were inspected in full after the
baseline review fix. Malformed typed fields now produce deterministic
diagnostics instead of throwing or silently dropping an invalid pair, and the
specification covers individually known coordinates without pair evidence.

| Repository/file | Naming result | Spec-style result | Validation | Final phase commit |
| --- | --- | --- | --- | --- |
| Cozy `src/main/scala/cozy/compatibility/GenerationCompatibility.scala` | whole-file scan passed | not a spec | `Test/compile` and focused CV-02 run passed | pending |
| Cozy `src/test/scala/cozy/compatibility/Phase51Cv02CompatibilitySpec.scala` | whole-file scan passed | Given/When/Then, matchers, five `which` groups, and 50-iteration property in the production compatibility package passed | 17 succeeded, 0 canceled | pending |

The independent CV-02 RE_REVIEW/PASS gate passed with no actionable finding.
The Cozy CV-02 focused run passed all 17 scenarios (17 succeeded, 0 canceled);
its property check used 50 iterations within one scenario. The documentation
diff passed `git diff --check`.

## CV-03 compliance ledger

CV-03 adds one pure build contract shared by the SBT meta-build and the root
executable specification. The root build is the sole invocation owner: Cozy is
pinned to `0.3.0`, the CNCF target comes only from `version.value`, and the
runtime descriptor comes from `generateCncfRuntimeDescriptor`.

| Repository/file | Naming result | Spec-style result | Validation | Final phase commit |
| --- | --- | --- | --- | --- |
| CNCF `project/CncfGenerationBuildContract.scala` | whole-file scan passed; public parameters camelCase and locals flatcase | not a spec | SBT meta-build and Scala 3 test configuration compiled | pending |
| CNCF `src/test/scala/org/goldenport/cncf/phase51/build/Phase51Cv03BuildIntegrationSpec.scala` | whole-file scan passed | four Given/When/Then scenarios in the production build-contract package, `should` matchers, 50-iteration property, and concurrent evaluation | 4 succeeded, 0 canceled | pending |

The focused specification and `Test/compile` passed. Serialized cold
`clean generateInformationCmlModel` and repeated
`generateInformationCmlModel` runs both selected Cozy `0.3.0`, CNCF
`0.5.2-SNAPSHOT`, and the same generated descriptor path. The 25 generated
Scala files had identical per-file SHA-256 hashes after both runs. The durable
`verifyInformationCmlGenerationDeterminism` build acceptance repeats this
actual Cozy invocation and digest comparison automatically before the full
`Test / test` task. Its REVIEW_FIX run verified identical snapshots for all 25
generated Scala files. The independent CV-03 RE_REVIEW/PASS gate passed with
no actionable finding.

## CV-04 compliance ledger

CV-04 adds one Cozy descriptor contract used by CLI preflight, sbt-bridge
delegation, and predefined Result loading. The CNCF build passes the SHA-256
of the exact generated descriptor; sbt-cozy already extracts and publishes the
same descriptor path/digest pair.

| Repository/file | Naming result | Spec-style result | Focused validation | Final phase commit |
| --- | --- | --- | --- | --- |
| Cozy `src/main/scala/cozy/Cozy.scala` | whole-file scan passed | not a spec | CV-04 descriptor entry-point regressions passed | pending |
| Cozy `src/main/scala/cozy/CozyCliBootstrap.scala` | whole-file scan passed | not a spec | CLI/bridge diagnostic parity passed | pending |
| Cozy `src/main/scala/cozy/config/CozyProjectYamlConfig.scala` | whole-file scan passed | not a spec | project/global-default source cases passed | pending |
| Cozy `src/main/scala/cozy/compatibility/CncfRuntimeDescriptorContract.scala` | whole-file scan passed | not a spec | descriptor/schema/digest properties passed | pending |
| Cozy `src/main/scala/cozy/modeler/PredefinedResultCatalog.scala` | whole-file scan passed | not a spec | descriptor catalog regressions passed | pending |
| Cozy `src/main/scala/cozy/runtime/CozySbtBridge.scala` | whole-file scan passed | not a spec | bridge forwarding/conflict regressions passed | pending |
| Cozy `src/test/scala/cozy/BridgeContractSpec.scala` | whole-file scan passed | Given/When/Then, `which` grouping, matchers, and 50-iteration project-relative identity property passed | bridge contract regressions passed | pending |
| Cozy `src/test/scala/cozy/Phase51Cv04TargetValidationSpec.scala` | whole-file scan passed | CLI-preflight usecase Given/When/Then spec in the production Cozy facade package, matchers, and 50+ digest-mutation property passed | CV-04 focused scenarios passed | pending |

CNCF `project/CncfGenerationBuildContract.scala`, its CV-03 executable spec,
and the sbt-cozy descriptor boundary remain recorded in their owning rows
above. The independent CV-04 RE_REVIEW/PASS gate passed with no actionable
finding and accepted the complete five-repository diff identity.

## CV-05 compliance ledger

CV-05A binds generation, catalog loading, and provenance to one validated
descriptor byte snapshot. It also captures CML bytes once, makes the modeler
consume an isolated materialization of those bytes, clears stale provenance,
and publishes a validated replacement atomically. CNCF-aware entry points
require a canonical project-relative source identity, reject drive-prefixed
identities, and keep filesystem and invalid-input failures inside the typed
diagnostic boundary. Its direct validation CLI exposes the same authoritative
validator. CV-05B resolves and preserves that identity plus the pre-launch
source digest at the CNCF build boundary, requires validator success after
generation, and includes provenance bytes in cold/repeated snapshots.

The independent CV-05 REVIEW found one CNCF build-contract naming issue. Its
REVIEW_FIX renamed `generationProvenancePath` to
`GENERATION_PROVENANCE_PATH`, then passed the three CV-05B scenarios and
`Test/compile` through serialized SBT execution. The subsequent independent
read-only RE_REVIEW/PASS gate found no actionable finding and accepted these
complete implementation-state content identities:

| Repository | Accepted content identity |
| --- | --- |
| CNCF | `e25d02359e53b9d4f7d146cb8cedeaed6dd2d93e96fe5d1f8ee286ef441df435` |
| Cozy | `7326287bf827ba22809f6c78207992edcb99bea69fe9c88c2debf1977b235d45` |
| sbt-cozy | `139ff6afad050dc73917933cefe453cc44350fd5dcbdae9784b3a2a2e0200a77` |
| simple-modeler | `5d4a3d02619a9510fefab9ba2841ad2421babf461044381c5c4c47895ede555b` |
| ArtScene | `ac53c0d0f8bc5a9e2a9fbd96941e94596483bf75afa2fb23a02c28d07f13f3b8` |

These identities precede this documentation-only closure update. Final phase
commit evidence remains pending until the Phase 51 release-commit stage.

## CV-06A compliance ledger

CV-06A fixes the CAR package-gate ownership boundary. Unmerged `project.yaml`
owns the exact `build.cozyVersion`, the unique CNCF compile coordinate, and the
minimum/maximum/excluded/tested runtime declaration. Merged Cozy operation
defaults remain available for unrelated packaging settings but cannot replace
those project-owned compatibility facts. The package gate independently
resolves exactly one CNCF descriptor from the actual input JARs and requires
its runtime/module/version identity to match the declared compile target. The
accepted JAR version also drives range admission directly, without a second
merged-default resolution.

The independent CV-06A REVIEW recorded six actionable findings. REVIEW_FIX
strengthened artifact identity and CAR classification, removed ambient
re-resolution, added negative archive-gate evidence, split interface and
workflow specifications into their production packages, and returned the four
completion boxes to review-pending state. Serialized validation completed 4
suites and 50 tests with zero failed or canceled, and `Test/compile` succeeded.
The following RE_REVIEW found that artifact evidence still inferred an absent
root descriptor version and that earlier accumulated specs remained in
phase-only packages. The second REVIEW_FIX requires the root descriptor
`version`, adds evaluator and archive-output rejection evidence, moves every
affected spec to its production responsibility package, and splits the Cozy
CV-01 inventory between config and archive responsibilities. Its serialized
validation passed 9 Cozy suites with 87 succeeded and 6 expected cancellations,
3 CNCF suites with 8 succeeded and 4 expected cancellations, and 1
simple-modeler suite with 3 expected cancellations; all three `Test/compile`
commands passed with zero failures. The subsequent accumulated-change
RE_REVIEW found that the
ten-scenario Cozy CV-05 provenance spec needed large-surface `which` grouping
and that CV-01 documentation still counted five specs after the Cozy
responsibility split. REVIEW_FIX grouped those behaviors by reproducible
evidence, contradictory evidence, and source/owning-build identity, corrected
the count to six spec files across five repositories, and passed all 10 focused
scenarios plus `Test/compile`. The next fresh RE_REVIEW found that Cozy's
canonical CAR metadata/scaffold documents omitted the implemented CV-06A
package-gate contract and that twelve modified header-bearing Cozy Scala files
had stale `@version` markers. REVIEW_FIX documents the exact generator,
unmerged project authority, resolved CNCF JAR identity, and runtime-range gate
across the affected Cozy design/spec surfaces, normalizes every such marker to
`Jul. 28, 2026`, and passes all 51 tests in the four focused CV-06A suites plus
`Test/compile`. A clean independent RE_REVIEW then accepted CV-06A and closed
the slice.

| Repository/file | Naming result | Spec-style result | Focused validation | Final phase commit |
| --- | --- | --- | --- | --- |
| Cozy `src/main/scala/cozy/compatibility/CarMetadataCompatibility.scala` | whole-file scan and current header passed | not a spec | latest review-fix CV-06A set: 51 passed, zero failed/canceled | pending |
| Cozy `src/main/scala/cozy/archive/CozyArchivePackager.scala` | whole-file scan and current header passed | not a spec | latest review-fix CV-06A set: 51 passed, zero failed/canceled | pending |
| Cozy `src/test/scala/cozy/compatibility/CarMetadataCompatibilitySpec.scala` | whole-file scan passed | interface-driven Given/When/Then behaviors, complete diagnostic matrix including absent root artifact version, and 50-iteration exact-version property in the production package | latest review-fix CV-06A set: 51 passed, zero failed/canceled | pending |
| Cozy `src/test/scala/cozy/archive/CozyArchivePackagerCv06Spec.scala` | whole-file scan passed | archive-workflow Given/When/Then behaviors covering ambient defaults, false artifact identity, absent root descriptor version, and range-gate rejection in the production package | latest review-fix CV-06A set: 51 passed, zero failed/canceled | pending |
| Cozy `src/main/scala/cozy/Cozy.scala` | whole-file scan passed | not a spec | CV-01/CV-04/CV-05/catalog/bridge: 38 passed, 6 expected canceled | pending |
| Cozy `src/main/scala/cozy/CozyCliBootstrap.scala` | whole-file scan passed | not a spec | CV-01/CV-04/CV-05/catalog/bridge: 38 passed, 6 expected canceled | pending |
| Cozy `src/main/scala/cozy/config/CozyProjectYamlConfig.scala` | whole-file scan passed | not a spec | CV-01/CV-04/CV-05/catalog/bridge: 38 passed, 6 expected canceled | pending |
| Cozy `src/main/scala/cozy/compatibility/CncfRuntimeDescriptorContract.scala` | whole-file scan passed | not a spec | CV-01/CV-04/CV-05/catalog/bridge: 38 passed, 6 expected canceled | pending |
| Cozy `src/main/scala/cozy/modeler/PredefinedResultCatalog.scala` | whole-file scan passed | not a spec | CV-01/CV-04/CV-05/catalog/bridge: 38 passed, 6 expected canceled | pending |
| Cozy `src/main/scala/cozy/modeler/GenerationProvenance.scala` | whole-file scan passed | not a spec | ten CV-05 scenarios passed; five-suite rerun passed 38 with 6 expected cancellations, including direct CLI validation | pending |
| Cozy `src/main/scala/cozy/runtime/CozySbtBridge.scala` | whole-file scan passed | not a spec | CV-01/CV-04/CV-05/catalog/bridge: 38 passed, 6 expected canceled | pending |
| Cozy `src/main/scala/cozy/scaffold/CozyScaffold.scala` | whole-file scan passed | not a spec | CV-01/CV-04/CV-05/catalog/bridge: 38 passed, 6 expected canceled | pending |
| Cozy `src/test/scala/cozy/modeler/ModelerSpecSupport.scala` | whole-file scan passed | fixture support, not a spec | `Test/compile` passed | pending |
| Cozy `src/test/scala/cozy/Phase51Cv04TargetValidationSpec.scala` | whole-file scan passed | CLI-preflight usecase Given/When/Then spec in the production Cozy facade package; matcher scan passed | CV-04 regressions passed | pending |
| Cozy `src/test/scala/cozy/modeler/Phase51Cv05GenerationProvenanceSpec.scala` | whole-file scan passed | ten Given/When/Then scenarios in the production modeler package grouped by reproducible evidence, contradictory evidence, and source/owning-build identity; matchers, 50-iteration source-identity property, unknown-field tamper coverage, future-schema precedence, and direct CLI validation | current grouping rerun: ten passed, zero failed/canceled; `Test/compile` passed | pending |
| CNCF `project/CncfGenerationBuildContract.scala` | whole-file scan passed | not a spec | cross-compiled build contract; CV-03/CV-05B seven-scenario run and `Test/compile` passed | pending |
| CNCF `src/test/scala/org/goldenport/cncf/phase51/build/Phase51Cv03BuildIntegrationSpec.scala` | whole-file scan passed | four Given/When/Then scenarios in the production build-contract package, matchers, 50-iteration normalization property, and concurrent planning evidence | four passed, zero failed/canceled | pending |
| CNCF `src/test/scala/org/goldenport/cncf/phase51/build/Phase51Cv05BuildProvenanceSpec.scala` | whole-file scan passed | three Given/When/Then scenarios in the production build-contract package, matchers, 50-iteration source-drift property, validation-command and provenance-snapshot coverage | three passed, zero failed/canceled; real generation validated 25 Scala files plus provenance | pending |

## CV-06B compliance ledger

CV-06B shares the unmerged project-only compatibility decision across package,
integrated CAR lint/Review, and publication. Resolved-JAR identity remains an
additional package-only gate. Publication runs the project decision before
repository writes and projects the accepted runtime range into the catalog
instead of consulting merged operation defaults.

| Repository/file | Naming result | Spec-style result | Focused validation | Final phase commit |
| --- | --- | --- | --- | --- |
| Cozy `src/main/scala/cozy/compatibility/CarMetadataCompatibility.scala` | whole-file scan and current header passed | not a spec | review-fix five-suite set: 42 passed, zero failed/canceled; `Test/compile` passed | pending |
| Cozy `src/main/scala/cozy/lint/CozyCarLint.scala` | whole-file scan and current header passed | not a spec | review-fix five-suite set: 42 passed, zero failed/canceled | pending |
| Cozy `src/main/scala/cozy/archive/CozyCarPublisher.scala` | whole-file scan and current header passed | not a spec | review-fix five-suite set: 42 passed, zero failed/canceled | pending |
| Cozy `src/main/scala/cozy/review/CozyCarReviewProvider.scala` | whole-file scan and current header passed | not a spec | review-fix five-suite set: 42 passed, zero failed/canceled | pending |
| Cozy `src/test/scala/cozy/compatibility/CarMetadataCompatibilitySpec.scala` | whole-file scan passed | project-decision Given/When/Then behavior added | review-fix five-suite set: 42 passed, zero failed/canceled | pending |
| Cozy `src/test/scala/cozy/lint/CozyCarLintSpec.scala` | REVIEW_FIX whole-file scan passed | accepted/rejected integrated-lint Given/When/Then behavior, corrected setup boundary, and 50-case production-lint property | review-fix five-suite set: 42 passed, zero failed/canceled | pending |
| Cozy `src/test/scala/cozy/CozyCarPublisherSpec.scala` | REVIEW_FIX whole-file scan passed | publication preflight/catalog projection, corrected setup boundaries, and 50-case production-publication property | review-fix five-suite set: 42 passed, zero failed/canceled | pending |
| Cozy `src/test/scala/cozy/review/CozyCarReviewProviderSpec.scala` | unchanged executable parity behavior | provider/direct-lint parity behavior covers the added decision | review-fix five-suite set: 42 passed, zero failed/canceled | pending |
| ArtScene `project.yaml`, `build.sbt`, and `Phase51Cv01AcceptanceSpec.scala` | project/spec scan and diff check passed | project-owned `build.cozyVersion` is asserted and drives sbt-cozy's versioned Coursier delegate | 2 passed, 1 expected canceled; `Test/compile` and normal CAR lint at that validation point passed with only the existing ABI-baseline warning | pending |

## CV-06C1 compliance ledger

CV-06C1 reuses Cozy's authoritative provenance validator at package admission.
An existing manifest must still match its source, complete generated Scala
artifact set, aggregate output digest, evidence digest, accepted CNCF compile
target, and exact Cozy generator. Accepted bytes are copied unchanged to the
top-level CAR entry `generation-provenance.json` from the same immutable
snapshot used for validation; provenance remains optional for non-generated
and legacy CAR sources and remains outside runtime activation.

| Repository/file | Naming result | Spec-style result | Focused validation | Final phase commit |
| --- | --- | --- | --- | --- |
| Cozy `src/main/scala/cozy/archive/CozyArchivePackager.scala` | prior accepted whole-file scan plus CV-06C1 identifiers/current header passed; fresh review pending | not a spec | CV-06C1 three-suite set: 29 passed, zero failed/canceled; `Test/compile` passed | pending |
| Cozy `src/main/scala/cozy/modeler/GenerationProvenance.scala` | prior accepted whole-file scan plus CV-06C1 identifiers/current header passed; fresh review pending | not a spec | CV-06C1 three-suite set: 29 passed, zero failed/canceled; `Test/compile` passed | pending |
| Cozy `src/test/scala/cozy/archive/CozyArchivePackagerCv06Spec.scala` | prior accepted whole-file scan plus CV-06C1 helper/local naming passed; fresh review pending | Given/When/Then scaffold-to-package byte round trip and contradictory-target rejection use matchers at the production archive boundary | CV-06C1 three-suite set: 29 passed, zero failed/canceled; `Test/compile` passed | pending |

## CV-06C2 compliance ledger

CV-06C2 adds the CNCF-owned runtime admission boundary and the Cozy-owned
runtime manifest used by that boundary. The goal-phase baseline review found
that `buildCar` could still emit an archive without this mandatory manifest
when project metadata did not classify the project as a CAR. The review fix
keeps generic metadata inspection optional, but makes the concrete CAR
packaging boundary require one accepted CAR contract and one generated runtime
manifest.

| Repository-managed file | Naming check | Executable-specification check | Validation | Final phase commit |
| --- | --- | --- | --- | --- |
| Cozy `src/main/scala/cozy/compatibility/CarMetadataCompatibility.scala` | whole-file scan and current header passed | not a spec | expanded archive/compatibility set: 51 passed, zero failed/canceled; `Test/compile` passed | pending |
| Cozy `src/main/scala/cozy/archive/CozyArchivePackager.scala` | whole-file scan and current header passed | not a spec | expanded archive/compatibility set: 51 passed, zero failed/canceled; `Test/compile` passed | pending |
| Cozy `src/main/scala/cozy/scaffold/CozyScaffold.scala` | whole-file scan and current header passed | not a spec | expanded archive/compatibility set: 51 passed, zero failed/canceled; `Test/compile` passed | pending |
| Cozy `src/test/scala/cozy/archive/CozyArchivePackagerCv06Spec.scala` | whole-file scan passed | 11 of 11 Given/When/Then packaging-boundary behaviors cover required project directory, actual CAR classification, invalid classification, and no archive output | expanded archive/compatibility set: 51 passed, zero failed/canceled; `Test/compile` passed | pending |
| Cozy `src/test/scala/cozy/CozyArchivePackagerSpec.scala` | whole-file scan and current header passed | 32 of 32 archive behaviors have explicit Given/When/Then boundaries and five responsibility-level `which` sections | RE_REVIEW fix package/scaffold/modeler set: 51 passed, zero failed/canceled; `Test/compile` passed | pending |
| Cozy `src/test/scala/cozy/ComponentApiJarPackagerSpec.scala` | whole-file scan and current header passed | 8 of 8 component-API packaging behaviors have explicit Given/When/Then boundaries and two responsibility-level `which` sections | expanded archive/compatibility set: 51 passed, zero failed/canceled; `Test/compile` passed | pending |
| Cozy `src/test/scala/cozy/CarPackagingSpecSupport.scala` | whole-file scan and current header passed | structured fixture support, not a spec; parses and deep-merges project YAML without regex and supplies evidence through actual library JAR inputs | expanded archive/compatibility set: 51 passed, zero failed/canceled; `Test/compile` passed | pending |

## CV-07 compliance ledger

CV-07 applies the exact generation pair to command lifecycle, package,
publication, delegated generator, and representative dependency boundaries.
The REVIEW_FIX keeps source-project development state out of immutable CAR
dependency validation and adds executable package/recovery evidence.

| Repository-managed file | Naming check | Executable-specification check | Focused validation | Final phase commit |
| --- | --- | --- | --- | --- |
| Cozy `src/main/scala/cozy/compatibility/GenerationCompatibilityBoundary.scala` | whole-file scan and current header passed | not a spec | Cozy CV-07 set: 32 passed, zero failed/canceled | pending |
| Cozy `src/main/scala/cozy/compatibility/CarMetadataCompatibility.scala` | whole-file scan and current header passed | not a spec | Cozy CV-07 set: 32 passed, zero failed/canceled | pending |
| Cozy `src/main/scala/cozy/archive/CozyArchivePackager.scala` | whole-file scan and private-parameter naming passed | not a spec | Cozy CV-07 set: 32 passed, zero failed/canceled | pending |
| Cozy `src/main/scala/cozy/archive/CozyCarPublisher.scala` | whole-file scan and current header passed | not a spec | Cozy CV-07 set: 32 passed, zero failed/canceled | pending |
| Cozy `src/test/scala/cozy/compatibility/Phase51Cv07DevelopmentReleaseAcceptanceSpec.scala` | whole-file scan passed | Given/When/Then behaviors cover command lifecycle, missing component/scaffold versions, and CNCF value generation | Cozy CV-07 set: 32 passed, zero failed/canceled | pending |
| Cozy `src/test/scala/cozy/archive/CozyArchivePackagerCv06Spec.scala` | whole-file scan and private-helper naming passed | Given/When/Then package matrix covers immutable pair provenance absence and successful immutable packaging | Cozy CV-07 set: 32 passed, zero failed/canceled | pending |
| Cozy `src/test/scala/cozy/CozyCarPublisherSpec.scala` | whole-file scan passed | Given/When/Then publication behaviors cover project/request version mismatch and immutable evidence | Cozy CV-07 set: 32 passed, zero failed/canceled | pending |
| sbt-cozy `src/main/scala/org/goldenport/cozy/CozyPlugin.scala` | whole-file scan and current header passed | not a spec | delegated generator set: 25 passed, zero failed/canceled | pending |
| sbt-cozy `src/test/scala/org/goldenport/cozy/CozyDelegatedGeneratorSpec.scala` | whole-file scan passed | Given/When/Then behaviors cover exact CAR/SAR authority and unavailable-generator recovery | delegated generator set: 25 passed, zero failed/canceled | pending |
| ArtScene `src/test/scala/org/simplemodeling/textus/artscene/impl/ArtSceneLauncherAssemblySpec.scala` | whole-file REVIEW_FIX scan and current header passed | 4 of 4 Given/When/Then assembly behaviors verify self-version alignment, packaged API isolation, exact released AI runtime selection, and exclusion of source-project implementation | latest REVIEW_FIX identity/lifecycle/assembly gate: 17 passed, zero failed/canceled; `Test/compile` passed; actual CAR package passed in the maintained CV-08 path | pending |

### Final accumulator ledger additions

These entries complete the path-level ledger for all 51 modified Scala files;
earlier CV ledgers above retain the remaining paths.

| Repository-managed file | Naming check | Executable-specification check | Focused validation | Final phase commit |
| --- | --- | --- | --- | --- |
| CNCF `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala` | whole-file scan and current header passed | not a spec | CNCF runtime/repository focused set passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/component/repository/ComponentRepository.scala` | whole-file scan and current header passed | not a spec | CNCF runtime/repository focused set passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/component/repository/ComponentRepositorySpace.scala` | whole-file scan, current header, and private/local naming passed after RE_REVIEW fix | not a spec | CNCF runtime/repository set: 92 passed, zero failed/canceled; `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/subsystem/GenericSubsystemFactory.scala` | whole-file scan and current header passed | not a spec | CNCF runtime/repository focused set passed | pending |
| CNCF `src/test/scala/org/goldenport/cncf/cli/CncfRuntimeConfigFileSpec.scala` | whole-file scan passed | 24 of 24 Given/When/Then behaviors use matchers in three responsibility-level `which` sections | compliance REVIEW_FIX four-suite set: 112 passed; `Test/compile` passed | pending |
| CNCF `src/test/scala/org/goldenport/cncf/component/CarRuntimeAdmissionSpec.scala` | whole-file scan passed | Given/When/Then CAR admission behaviors use matchers | CNCF runtime/repository focused set passed | pending |
| CNCF `src/test/scala/org/goldenport/cncf/component/repository/ComponentRepositoryCarSpec.scala` | whole-file scan passed | 68 of 68 Given/When/Then repository behaviors use matchers in four responsibility-level `which` sections; subsystem resolution occurs after its When boundary | latest focused REVIEW_FIX: 68 passed; `Test/compile` passed | pending |
| CNCF `src/test/scala/org/goldenport/cncf/subsystem/GenericSubsystemFactorySpec.scala` | whole-file scan passed | 10 of 10 Given/When/Then subsystem behaviors use matchers in two responsibility-level `which` sections | compliance REVIEW_FIX four-suite set: 112 passed; `Test/compile` passed | pending |
| Cozy `src/main/scala/cozy/archive/CozyCarRuntimeManifest.scala` | whole-file scan and current header passed | not a spec | Cozy archive/compatibility focused set passed | pending |
| Cozy `src/test/scala/cozy/archive/CozyCarRuntimeManifestSpec.scala` | whole-file scan passed | Given/When/Then runtime-manifest behaviors use matchers | Cozy archive/compatibility focused set passed | pending |
| Cozy `src/test/scala/cozy/modeler/ModelerScaffoldSpec.scala` | whole-file scan and current header passed | 14 of 14 Given/When/Then scaffold behaviors use matchers in three responsibility-level `which` sections | latest scaffold/generation REVIEW_FIX set: 41 passed; `Test/compile` passed | pending |
| Cozy `src/test/scala/cozy/modeler/PredefinedResultCatalogSpec.scala` | whole-file scan and current header passed | Given/When/Then runtime-catalog behaviors use matchers | Cozy package/scaffold/modeler set: 51 passed, zero failed/canceled; `Test/compile` passed | pending |
| sbt-cozy `src/main/scala/org/goldenport/cozy/CarRuntimeClasspathResolver.scala` | whole-file scan and current header passed | not a spec | sbt-cozy resolver focused set passed | pending |
| sbt-cozy `src/test/scala/org/goldenport/cozy/CarRuntimeClasspathResolverSpec.scala` | whole-file scan passed | Given/When/Then runtime-classpath behaviors use matchers | sbt-cozy resolver focused set passed | pending |
| sbt-cozy `src/test/scala/org/goldenport/cozy/CozyGenerationProvenanceIntegrationSpec.scala` | whole-file scan passed | Given/When/Then generation-provenance integration behaviors use matchers | sbt-cozy provenance focused set passed | pending |

## CI-01 compliance-ledger additions

CI-01 expands the historical Phase 51 touched-file accumulator to 78
repository-managed Scala files: 28 CNCF, 35 Cozy, 6 sbt-cozy, 3
simple-modeler, 4 ArtScene, and 2 simplemodeling-model files. A direct
tracked-plus-untracked worktree inventory confirms the same 78-file current
dirty set. The 27 paths below were not present in the earlier 51-file CV-07
ledger. This dated addition supersedes the earlier accumulator count without
rewriting the historically accurate CV-07 ledger.

| Repository-managed file | Naming check | Executable-specification check | Focused validation | Final phase commit |
| --- | --- | --- | --- | --- |
| CNCF `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` | whole-file REVIEW_FIX scan passed | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/association/AssociationModel.scala` | whole-file REVIEW_FIX scan passed | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/blob/BlobRepository.scala` | whole-file REVIEW_FIX scan passed | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/blob/MediaModel.scala` | whole-file REVIEW_FIX scan passed after method-local flatcase repair | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala` | whole-file REVIEW_FIX scan passed | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala` | whole-file REVIEW_FIX scan passed; public fields retain camelCase | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/entity/ChildEntityBindingWorkflow.scala` | whole-file REVIEW_FIX scan and compressed current-month header passed | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala` | whole-file REVIEW_FIX scan and compressed current-month header passed | not a spec | CI-01 canonical-contract REVIEW_FIX gate: 27 passed; `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/entity/EntityPersistent.scala` | whole-file REVIEW_FIX scan passed | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/entity/runtime/Collection.scala` | whole-file REVIEW_FIX scan and compressed current-month header passed | not a spec | CI-01 canonical-contract REVIEW_FIX gate: 27 passed; `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/entity/runtime/EntitySpace.scala` | whole-file REVIEW_FIX scan passed | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/http/StaticFormAppRendererComponentAdminPart.scala` | whole-file REVIEW_FIX scan and compressed current-month header passed after 163 method-local flatcase repairs | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/importer/StartupImport.scala` | whole-file REVIEW_FIX scan passed after private-helper and local flatcase repairs | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/job/JobEntity.scala` | whole-file REVIEW_FIX scan passed | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/tag/TagModel.scala` | whole-file REVIEW_FIX scan passed | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala` | whole-file REVIEW_FIX scan passed | not a spec | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedSchemaSpec.scala` | whole-file REVIEW_FIX scan passed | Given/When/Then generated-schema behavior uses matchers | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/test/scala/org/goldenport/cncf/entity/EntityDetachedRevisionSpec.scala` | whole-file REVIEW_FIX scan and compressed current-month header passed | 10 of 10 Given/When/Then behaviors cover generated, custom, raw, and same-name foreign-owner rejection in two responsibility-level `which` sections | compliance REVIEW_FIX four-suite set: 112 passed; `Test/compile` passed | pending |
| CNCF `src/test/scala/org/goldenport/cncf/entity/EntityPersistentCollectionIdentitySpec.scala` | whole-file REVIEW_FIX scan passed | Given/When/Then covers generated, legacy, custom, raw, and mismatch behavior | REVIEW_FIX 351-test gate and `Test/compile` passed | pending |
| CNCF `src/test/scala/org/goldenport/cncf/entity/runtime/EntitySpaceCollectionIdentitySpec.scala` | whole-file REVIEW_FIX scan passed | Given/When/Then covers exact, ambiguous, compatible ingress, and selected-owner scalar restoration behavior | CI-01 canonical-contract REVIEW_FIX gate: 27 passed; `Test/compile` passed | pending |
| Cozy `src/test/scala/cozy/modeler/ModelerScalaGenerationSpec.scala` | whole-file REVIEW_FIX scan passed | 27 of 27 Given/When/Then generated-source behaviors use matchers in four responsibility-level `which` sections | latest scaffold/generation REVIEW_FIX set: 41 passed; `Test/compile` passed | pending |
| simple-modeler `src/main/scala/org/simplemodeling/SimpleModeler/generator/scala/Scala3ClassGeneratorBase.scala` | whole-file REVIEW_FIX scan passed; generated public `collectionId` remains camelCase | not a spec | CI-01 generation 2-scenario set passed | pending |
| simple-modeler `src/test/scala/org/simplemodeling/SimpleModeler/transformers/scala/SimpleEntityRevisionGenerationSpec.scala` | whole-file REVIEW_FIX scan passed | Given/When/Then verifies context-aware exact-owner generation | CI-01 generation 2-scenario set passed | pending |
| ArtScene `src/main/scala/org/simplemodeling/textus/artscene/impl/ComponentFactory.scala` | whole-file REVIEW_FIX scan passed | not a spec | latest REVIEW_FIX identity/lifecycle/assembly gate: 17 passed, zero failed/canceled; `Test/compile` passed | pending |
| ArtScene `src/test/scala/org/simplemodeling/textus/artscene/ArtSceneExactCollectionIdentitySpec.scala` | whole-file REVIEW_FIX scan and current header passed | 4 of 4 Given/When/Then behaviors separately verify UnitOfWork exact-owner restoration, service scalar ingress, malformed physical-ID rejection, and typed same-name foreign-owner rejection | latest REVIEW_FIX identity/lifecycle/assembly gate: 17 passed, zero failed/canceled; `Test/compile` passed | pending |
| simplemodeling-model `src/main/scala/org/simplemodeling/model/datatype/EntityId.scala` | whole-file REVIEW_FIX scan passed | not a spec | `EntityIdSpec`: 6 passed; `Test/compile` passed | pending |
| simplemodeling-model `src/test/scala/org/simplemodeling/model/datatype/EntityIdSpec.scala` | whole-file REVIEW_FIX scan passed | 6 of 6 Given/When/Then behaviors verify exact collection-sensitive equality, parsed/materialized identity, and decoding routes | `EntityIdSpec`: 6 passed; `Test/compile` passed | pending |

The earlier ledger entries plus this table account for all 78 historically
touched Scala paths and all 78 paths still dirty in the expanded worktree.
RE_REVIEW must independently confirm this reconciliation before CI-01 can
close.

The subsequent CI-01 accumulator RE_REVIEW found compliance-only debt rather
than a new functional defect. REVIEW_FIX compressed five same-month Scala
headers to one current `@version`, completed Given/When/Then boundaries for all
68 `ComponentRepositoryCarSpec` scenarios and all six `EntityIdSpec`
scenarios, and split the four large CNCF specifications into responsibility
sections: 24 scenarios in three sections, 68 in four, and both ten-scenario
specifications in two sections each. The Cozy ledger counts above now match
the executable files at 11 and 32 scenarios. The direct current inventory
remains 78 Scala files with no stale same-month history line.
Serialized focused validation passed 6 of 6 simplemodeling-model scenarios and
112 of 112 CNCF scenarios across the four repaired specifications; both
repositories also passed `Test/compile`.

The next fresh accumulator RE_REVIEW found three remaining executable-document
issues: one subsystem resolution action preceded its `When`, while the
14-scenario scaffold specification and 27-scenario Scala-generation
specification each used one catch-all responsibility section. REVIEW_FIX moves
the resolver call after its action boundary and splits those Cozy
specifications into three and four responsibility-level sections without
changing behavior. Serialized validation passed all 41 Cozy scenarios and all
68 CNCF repository scenarios, followed by successful `Test/compile` in both
repositories.

## SP-01 compliance-ledger additions

SP-01 expands the current Phase 51 tracked-plus-untracked dirty accumulator
from the historically accurate CI-01 count of 78 to 82 repository-managed
Scala files: 30 CNCF, 35 Cozy, 6 sbt-cozy, 4 simple-modeler, 5 ArtScene, and 2
simplemodeling-model files. The four paths below complete the path-level
ledger without rewriting the earlier dated CI-01 inventory.

| Repository-managed file | Naming check | Executable-specification check | Focused validation | Final phase commit |
| --- | --- | --- | --- | --- |
| CNCF `src/main/scala/org/goldenport/cncf/entity/EntityStoreRecordProjection.scala` | whole-file REVIEW_FIX scan and current header passed | not a spec | SP-01 projection gate: 4 passed; `Test/compile` passed | pending |
| CNCF `src/test/scala/org/goldenport/cncf/entity/EntityStoreRecordProjectionSpec.scala` | whole-file REVIEW_FIX scan and current header passed | 4 of 4 Given/When/Then projection behaviors use matchers | SP-01 projection gate: 4 passed; `Test/compile` passed | pending |
| simple-modeler `src/test/scala/org/simplemodeling/SimpleModeler/transformers/scala/ValueScalaModelTransformerSpec.scala` | whole-file REVIEW_FIX scan and current header passed | 14 of 14 Given/When/Then generation behaviors use matchers in four responsibility-level `which` sections | SP-01 generation gate: 16 passed; `Test/compile` passed | pending |
| ArtScene `src/test/scala/org/simplemodeling/textus/artscene/ArtSceneNotificationIntentLifecycleSpec.scala` | whole-file REVIEW_FIX scan and current header passed | 9 of 9 Given/When/Then lifecycle behaviors use matchers in three responsibility-level `should` sections; automatic delivery, physical projection, and explicit redispatch are separate executable contracts | latest REVIEW_FIX identity/lifecycle/assembly gate: 17 passed, zero failed/canceled; `Test/compile` passed | pending |

The complete 82-file ledger remained uncommitted at SP-01 closure. The clean
accumulator RE_REVIEW independently verified every row before entering the
phase release gate.

## PHASE_TEST_FIX compliance-ledger addition

The final full gate exposed one stale generated-source expectation and expanded
the current Phase 51 tracked dirty accumulator from 82 to 83
repository-managed Scala files: 30 CNCF, 35 Cozy, 6 sbt-cozy, 5
simple-modeler, 5 ArtScene, and 2 simplemodeling-model files.

| Repository-managed file | Naming check | Executable-specification check | Focused validation | Final phase commit |
| --- | --- | --- | --- | --- |
| simple-modeler `src/test/scala/org/simplemodeling/SimpleModeler/transformers/scala/EntityCustomTypeResolutionSpec.scala` | whole-file REVIEW_FIX scan and current header passed | 7 of 7 Given/When/Then behaviors use matchers; the changed example specifies repeated generated-value write/read projection as one datastore preservation contract | targeted PHASE_TEST_FIX gate: 7 passed; `Test/compile` passed; scoped clean RE_REVIEW accepted | pending |

Before the resumed release gate, the accepted correction was the only unstaged
source delta beyond the preserved six-repository release candidate. Full
accumulated suites and final release commits remain pending.

## Final focused scenario matrix

| Repository/spec | Scenarios | Pass | Cancel | Fail |
| --- | ---: | ---: | ---: | ---: |
| CNCF CV-01 | 5 | 1 | 4 | 0 |
| Cozy CV-02 | 17 | 17 | 0 | 0 |
| ArtScene CV-01 | 3 | 2 | 1 | 0 |
| CNCF CV-03 | 4 | 4 | 0 | 0 |
| Cozy CV-04/bridge/catalog focused set | 28 | 28 | 0 | 0 |
| Cozy CV-05 provenance | 10 | 10 | 0 | 0 |
| Cozy CV-06A metadata consistency | 7 | 7 | 0 | 0 |
| Cozy CV-06A/B review-fix focused set | 42 | 42 | 0 | 0 |
| Cozy CV-06C1 provenance packaging focused set | 29 | 29 | 0 | 0 |
| Cozy CV-06C2 expanded archive/compatibility set | 51 | 51 | 0 | 0 |
| Cozy CV-07 review-fix set | 32 | 32 | 0 | 0 |
| sbt-cozy CV-07 delegated generator set | 25 | 25 | 0 | 0 |
| ArtScene CV-07 release-CAR assembly set | 6 | 5 | 1 | 0 |
| Cozy RE_REVIEW fix package/scaffold/modeler set | 51 | 51 | 0 | 0 |
| CNCF RE_REVIEW fix repository/runtime set | 92 | 92 | 0 | 0 |
| ArtScene RE_REVIEW fix identity/assembly set | 6 | 6 | 0 | 0 |
| Cozy second RE_REVIEW fix package/scaffold set | 46 | 46 | 0 | 0 |
| CNCF CV-05 build provenance | 3 | 3 | 0 | 0 |
| sbt-cozy descriptor boundary | 3 | 3 | 0 | 0 |
| CNCF CI-01 REVIEW_FIX regression set | 351 | 351 | 0 | 0 |
| simplemodeling-model CI-01 REVIEW_FIX | 6 | 6 | 0 | 0 |
| CNCF CI-01 canonical-contract REVIEW_FIX | 27 | 27 | 0 | 0 |
| simple-modeler PHASE_TEST_FIX | 7 | 7 | 0 | 0 |

The remaining CV-01 repository counts are unchanged and remain recorded above:
Cozy CV-01 is 6 scenarios, 0 pass, 6 canceled, 0 failed; sbt-cozy CV-01 is
4 scenarios, 0 pass, 4 canceled, 0 failed; and simple-modeler CV-01 is
3 scenarios, 0 pass, 3 canceled, 0 failed. `simplemodeling-model` was clean
and read-only at CV-01 closure; its current CI-01 evidence is recorded above.
