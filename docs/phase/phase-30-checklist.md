# Phase 30 — CAR Component API Artifacts Checklist

This document contains detailed task tracking and decisions for Phase 30. It
complements the summary-level phase document (`phase-30.md`).

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document holds summary status only.
- A DONE item here must also be checked in the phase dashboard.
- Investigation and rejected alternatives belong in a Phase 30 journal.
- Design documents are updated only after executable behavior has settled.
- A flattened classpath is diagnostic support, not completion evidence.

## CA-01: Open Phase 30 and Freeze the Packaging Contract

Status: DONE

### Objective

Separate the missing CAR API artifact/classloader work from the completed
Phase 29 typed invocation and multi-instance selection model.

### Detailed Tasks

- [x] Add the Phase 30 dashboard and checklist.
- [x] Keep Phase 29 closed and record its flattened-classpath smoke limitation
      as Phase 30 input.
- [x] Select `spi/` as the existing canonical CAR location for component API
      JARs.
- [x] Decide that CML owns contract requirements while `build.sbt` owns
      dependent CAR coordinates.
- [x] Decide that consumers must not compile against provider implementation
      JARs or provider source projects.
- [x] Select ArtScene/textus-scraper as the end-to-end development driver.

### Decisions

- `cozyCarDependencies` is the canonical sbt declaration surface.
- API artifacts are part of the CAR contract, not ordinary component-local
  libraries.
- The existing `cozySpiJars` archive path is reused rather than introducing a
  second competing API directory.
- The same API artifact must be used for consumer compilation and assembly
  class identity.
- Local SNAPSHOT development resolves APIs from `cozyPublishLocalCar` output.

## CA-02: Generate Component API Metadata

Status: DONE

### Objective

Generate an explicit, closed public contract description for every
component-specific typed API.

### Detailed Tasks

- [x] Derive provided component APIs from CML service SPI properties.
- [x] Derive required component APIs and multiplicity from consumer CML.
- [x] Compute the transitive public type closure for API methods.
- [x] Include request, response, datatype, powertype, socket, socket-set, proxy,
      and contract metadata classes.
- [x] Reject public signatures that depend on `impl` or non-public persistence
      types.
- [x] Generate `component-api-descriptor.json` deterministically.
- [x] Record contract name, API class, artifact path, version, package set, and
      ABI hash.
- [x] Add Cozy/simple-modeler executable generation specifications.

### Acceptance Criteria

- The textus-scraper model produces a complete `TextusScraperApi` closure.
- The ArtScene model produces a required API declaration with multiplicity
  `*`.
- Generated metadata contains no implementation package references.

### Implementation Evidence

- simple-modeler derives the descriptor model directly from the parsed CML
  model graph and rejects implementation, entity, and persistence types at the
  public contract boundary.
- Cozy binds the generated model to the sbt module/version identity and emits
  `target/cozy/component-api-descriptor.json`.
- sbt-cozy forwards module/version settings and installs the descriptor as a
  deterministic generation side output.
- textus-scraper generates the complete `TextusScraperApi` closure, including
  its companion contract, provider, proxy, socket, socket-set, request,
  response, and nested public value classes.
- ArtScene generates a required `TextusScraperApi` declaration with
  multiplicity `*` and `required = true`.
- Full simple-modeler, Cozy, sbt-cozy, textus-scraper, and ArtScene test suites
  pass with the development generator path.

## CA-03: Build and Package Component API JARs

Status: OPEN

### Objective

Turn generated public API closure metadata into a contract-only JAR embedded in
the provider CAR.

### Detailed Tasks

- [ ] Add `cozyComponentApiJar` to sbt-cozy.
- [ ] Package only class files listed by generated API closure metadata.
- [ ] Include Scala companion, nested, and TASTy files required by the public
      API.
- [ ] Feed generated API JARs into the existing `cozySpiJars` packaging path.
- [ ] Add API descriptor and ABI hash to the CAR.
- [ ] Run API JAR generation automatically from `cozyBuildCar` and
      `cozyPublishLocalCar`.
- [ ] Keep main-JAR compatibility while ensuring shared parent-first loading
      selects the API JAR class identity.
- [ ] Add archive-content and forbidden-class executable specifications.

### Acceptance Criteria

- `textus-scraper.car` contains `spi/textus-scraper-api.jar`.
- The API JAR contains `TextusScraperApi`, `Socket`, `SocketSet`, public request
  and response types, and required public datatypes.
- The API JAR contains no `ComponentFactory` or `impl` class.

## CA-04: Resolve Dependent CAR APIs for Compilation

Status: OPEN

### Objective

Let consumers compile by declaring dependent CAR coordinates only.

### Detailed Tasks

- [ ] Add `CarDependency` and `cozyCarDependencies` to sbt-cozy.
- [ ] Resolve local and remote CAR artifacts through canonical CAR repositories.
- [ ] Read each dependency's component API descriptor.
- [ ] Match consumer required contracts to provider published contracts.
- [ ] Extract matched API JARs into a managed cache.
- [ ] Add matched API JARs, and only those JARs, to the compile classpath.
- [ ] Exclude provider `component/main.jar` and implementation libraries.
- [ ] Generate or validate `assembly-descriptor.yaml` component dependencies
      from the same CAR dependency declarations.
- [ ] Fail deterministically for missing, ambiguous, version-incompatible, and
      ABI-incompatible contracts.
- [ ] Add an sbt-cozy scripted provider/consumer fixture without sbt
      `dependsOn`.

### Acceptance Criteria

- ArtScene compiles without a `RootProject` dependency on textus-scraper.
- ArtScene's compile classpath contains `textus-scraper-api.jar` but not the
  textus-scraper implementation JAR.
- Repeating the build uses the managed API cache deterministically.

## CA-05: Assembly API Classloader

Status: OPEN

### Objective

Install one API class identity before any consumer or provider component is
instantiated.

### Detailed Tasks

- [ ] Extend `CarExtractor` to expose declared `spi/*.jar` API artifacts.
- [ ] Resolve assembly descriptors and API artifacts before component
      instantiation.
- [ ] Build an assembly API classloader above component-local classloaders.
- [ ] Parent consumer and provider component classloaders with the same API
      loader.
- [ ] Use descriptor-declared API packages for parent-first loading instead of
      relying only on the broad `.api.` naming heuristic.
- [ ] Never scan API JARs for component factories or bundle definitions.
- [ ] Validate duplicate contract/version/ABI hash combinations.
- [ ] Preserve existing CAR behavior when no component API artifact is
      declared.
- [ ] Return structured startup failures for missing or conflicting APIs.

### Acceptance Criteria

- Consumer and provider `classOf[TextusScraperApi]` values are identical.
- `TextusScraperApi.SocketSet` can be constructed before SPI assembly wiring.
- API conflicts fail before partial component startup.

## CA-06: Development and Packaged Startup Parity

Status: OPEN

### Objective

Use the same API artifact and classloader model for local source development
and packaged CAR execution.

### Detailed Tasks

- [ ] Produce the provider API JAR in its normal target directory during
      development.
- [ ] Resolve dependency APIs from local published CARs for consumer builds.
- [ ] Give development component classloaders the assembly API loader parent.
- [ ] Keep packaged dependencies in the normal CAR repository path.
- [ ] Verify `.cncf/launcher.yaml` project development mode without manual
      dependency `--component-dev-dir` options.
- [ ] Reject missing local SNAPSHOT CAR dependencies with an actionable error.
- [ ] Remove flat-classpath execution from standard validation evidence.

### Acceptance Criteria

- `cncf server` and packaged CAR startup use the same API identity model.
- Development startup requires no dependency CAR copying or classpath
  flattening.

## CA-07: ArtScene and textus-scraper Verification

Status: OPEN

### Objective

Prove the complete contract with the Phase 29 development-driver application.

### Detailed Tasks

- [ ] Publish textus-scraper locally with its API JAR.
- [ ] Replace ArtScene's source-project dependency with
      `cozyCarDependencies`.
- [ ] Generate and compile ArtScene from the resolved scraper API.
- [ ] Start ArtScene through standard `cncf server`.
- [ ] Verify assembly installation of `TextusScraperApi.SocketSet`.
- [ ] Verify typed scraping calls use canonical CNCF operation/action dispatch.
- [ ] Run fetch, candidate, Web, and timeline smoke checks.
- [ ] Confirm the user's normal ArtScene datastore is preserved by test
      configuration.
- [ ] Run CNCF, Cozy, sbt-cozy, textus-scraper, and ArtScene regression suites.

### Acceptance Criteria

- ArtScene starts without `NoClassDefFoundError`.
- ArtScene imports no textus-scraper implementation package.
- Standard launcher execution replaces the current flat-classpath workaround.

## CA-08: Design Promotion and Phase Closure

Status: OPEN

### Objective

Promote the verified artifact and classloader contract to authoritative design
and developer documentation.

### Detailed Tasks

- [ ] Update `docs/design/component-dependency-loading.md`.
- [ ] Update `docs/design/typed-component-api-and-multi-instance-spi.md`.
- [ ] Update the CAR/component developer guide.
- [ ] Document `cozyCarDependencies`, local SNAPSHOT publication, and API
      conflict diagnostics.
- [ ] Add a factual Phase 29 correction note describing the original
      flattened-classpath smoke boundary.
- [ ] Complete all Phase 30 validation and close the dashboard.

### Acceptance Criteria

- Published documentation matches executable packaging and startup behavior.
- Phase 30 closes with ArtScene/textus-scraper standard-launcher evidence.
