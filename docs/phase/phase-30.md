# Phase 30 — CAR Component API Artifacts

status = closed

## 1. Purpose of This Document

This work document records Phase 30, which adds first-class CAR component API
artifacts and automatic dependency resolution for typed component consumers.

Phase 29 established typed component APIs, sockets, socket sets, and assembly
selection semantics. Its ArtScene driver smoke used a flattened development
classpath, however, and therefore did not prove that a packaged provider CAR
could expose its generated API to an isolated consumer component classloader.
Phase 30 closes that packaging, compile-resolution, and deployment gap without
reopening the Phase 29 API and selection model.

## 2. Phase Scope

- Let a provider CAR publish a contract-only component API JAR under `spi/`.
- Generate formal provided and required component API metadata from CML.
- Let a consumer declare dependent CAR coordinates in `build.sbt` only.
- Resolve required API JARs from dependent CARs into the consumer compile
  classpath without exposing provider implementation JARs.
- Derive or validate assembly component dependencies from the same
  `cozyCarDependencies` declarations.
- Build one assembly API classloader before component instantiation.
- Preserve one runtime class identity for each typed component API across
  consumer and provider CAR classloaders.
- Keep development-directory and packaged-CAR startup behavior equivalent.
- Verify the result with ArtScene consuming the packaged textus-scraper API
  through the standard `cncf server` route.

Scope boundaries:

- Phase 30 does not turn provider implementation classes into consumer
  libraries.
- Phase 30 does not add arbitrary application-driven CAR loading.
- Phase 30 does not replace standard CNCF SPI contracts with CAR-owned APIs.
- Phase 30 does not implement distributed component transport.
- Phase 30 does not require dynamic Playwright scraper support.
- Phase 30 does not use flattened classpaths as accepted deployment evidence.

## 3. Canonical Consumer Configuration

The consumer declares CAR dependencies in `build.sbt`:

```scala
cozyCarDependencies ++= Seq(
  CarDependency("textus-scraper", "0.1.0-SNAPSHOT"),
  CarDependency("textus-ai-runtime", "0.2.0-SNAPSHOT")
)
```

CML declares which typed component API contract is required and its
multiplicity. sbt-cozy matches those requirements against the APIs published by
the declared CAR dependencies. Consumer projects must not add provider source
projects or provider implementation JARs solely to compile a generated socket.

For local SNAPSHOT development, the provider publishes its CAR through the
normal local CAR repository:

```bash
sbt cozyPublishLocalCar
```

The consumer then resolves the API from that CAR through the same mechanism
used for packaged execution.

## 4. Target CAR Structure

```text
textus-scraper.car
├── component/main.jar
├── spi/textus-scraper-api.jar
├── component-descriptor.json
├── component-api-descriptor.json
└── component-dependencies.yaml  # when local/shared Maven dependencies exist
```

`assembly-descriptor.yaml` belongs to the consuming application or subsystem
assembly when that assembly needs an explicit descriptor. It is not a required
member of every provider CAR.

The component API JAR contains generated API traits, proxies, sockets, socket
sets, public request and response types, and their public datatype/powertype
closure. It must not contain component factories, implementation packages,
parser implementations, persistence implementations, or private resources.

## 5. Active Work Stack

- A (DONE): CA-01 — Open Phase 30 and freeze the packaging contract.
- B (DONE): CA-02 — Generate provided/required component API metadata.
- C (DONE): CA-03 — Build and package contract-only API JARs.
- D (DONE): CA-04 — Resolve dependent CAR APIs for consumer compilation.
- E (DONE): CA-05 — Add assembly-wide shared API classloading.
- F (DONE): CA-06 — Align development-directory and packaged startup.
- G (DONE): CA-07 — Verify ArtScene and textus-scraper end to end.
- H (DONE): CA-08 — Promote the settled packaging contract and close Phase 30.

Closure guidance:

- Phase 30 is closed. Select the next development item explicitly rather than
  extending the component API artifact scope implicitly.

## 6. Development Items

- [x] CA-01: Open Phase 30 and freeze the packaging contract.
- [x] CA-02: Generate provided and required component API metadata from CML.
- [x] CA-03: Build and package contract-only component API JARs.
- [x] CA-04: Resolve `cozyCarDependencies` for consumer compilation and
      assembly metadata.
- [x] CA-05: Load declared API JARs through an assembly API classloader.
- [x] CA-06: Make development-directory and packaged-CAR startup equivalent.
- [x] CA-07: Run the ArtScene/textus-scraper standard-launcher smoke.
- [x] CA-08: Update authoritative design and developer guidance, then close
      Phase 30.

Detailed task breakdown and progress tracking are recorded in
`phase-30-checklist.md`.

## 7. Completion Conditions

Phase 30 closes only after verifying that:

- a provider CAR publishes a contract-only API JAR and descriptor;
- a consumer compiles by declaring the provider CAR dependency only;
- the provider implementation JAR is absent from the consumer compile
  classpath;
- missing, ambiguous, incompatible, and ABI-conflicting API contracts fail
  deterministically;
- API JARs are not scanned as component implementations;
- consumer and provider observe the same API class identity;
- source-directory and packaged-CAR startup use the same dependency and
  classloader model;
- ArtScene production compilation and runtime assembly no longer use
  `RootProject(...).dependsOn(textusScraper)`; any source-project edge is
  restricted to test fixtures and is not deployment evidence;
- standard `cncf server` starts ArtScene and installs
  `TextusScraperApi.SocketSet` from the assembly;
- ArtScene fetch, candidate, Web, and timeline smoke checks pass through the
  canonical textus-scraper operation path.

All completion conditions were verified by CA-07. CA-08 promoted the resulting
contract to the authoritative design and developer guides and recorded the
Phase 29 evidence boundary without reopening Phase 29.

## 9. Post-Closure Maintenance

On Jul. 14, 2026, CNCF hardened its shared MCP projection boundary without
reopening Phase 30. Components now declare MCP readiness at service or
service-qualified operation granularity; CAR/SAR runtime configuration can
disable but cannot expand that surface. Named JSON arguments are normalized as
generated operation properties, and MCP tool descriptions use CML operation
metadata when protocol metadata is absent. Full CNCF tests and representative
SIE/CBD Support CAR tests verify publication, invocation, and tool ownership.

On Jul. 12, 2026, the existing CNCF `ToolchainRunner.renderWebPage` standard
SPI gained optional User-Agent and browser-profile controls. The change keeps
the pre-existing positional `metadata` argument stable and leaves ordinary
User-Agent handling literal. Reviewed site compatibility is an explicit opt-in
implemented by the provider, not hidden application-side Docker behavior.

ArtScene's Yamanashi driver verifies the result through the Phase 30 component
API artifact and assembly route:

```text
ArtScene -> TextusScraperApi.RenderPage -> ToolchainRunner SPI -> provider
```

This maintenance does not reopen Phase 30 or make dynamic scraping part of the
component API packaging scope.

## 8. Authoritative Inputs

- `docs/design/typed-component-api-and-multi-instance-spi.md`
- `docs/design/component-dependency-loading.md`
- `docs/phase/phase-29.md`
- `docs/phase/phase-29-checklist.md`
- `docs/notes/typed-component-api-and-multi-instance-spi.md`
- ArtScene startup failure:
  `NoClassDefFoundError: org/simplemodeling/textus/scraper/api/TextusScraperApi$SocketSet`
