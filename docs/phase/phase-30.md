# Phase 30 — CAR Component API Artifacts

status = active

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
└── assembly-descriptor.yaml
```

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
- G (NEXT): CA-07 — Verify ArtScene and textus-scraper end to end.
- H (OPEN): CA-08 — Promote the settled packaging contract and close Phase 30.

Resume hint:

- Start CA-07 with the standard ArtScene launcher path and verify typed scraper
  operation dispatch, fetch/timeline behavior, and datastore isolation.

## 6. Development Items

- [x] CA-01: Open Phase 30 and freeze the packaging contract.
- [x] CA-02: Generate provided and required component API metadata from CML.
- [x] CA-03: Build and package contract-only component API JARs.
- [x] CA-04: Resolve `cozyCarDependencies` for consumer compilation and
      assembly metadata.
- [x] CA-05: Load declared API JARs through an assembly API classloader.
- [x] CA-06: Make development-directory and packaged-CAR startup equivalent.
- [ ] CA-07: Run the ArtScene/textus-scraper standard-launcher smoke.
- [ ] CA-08: Update authoritative design and developer guidance, then close
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
- ArtScene no longer uses `RootProject(...).dependsOn(textusScraper)`;
- standard `cncf server` starts ArtScene and installs
  `TextusScraperApi.SocketSet` from the assembly;
- ArtScene fetch, candidate, Web, and timeline smoke checks pass through the
  canonical textus-scraper operation path.

## 8. Authoritative Inputs

- `docs/design/typed-component-api-and-multi-instance-spi.md`
- `docs/design/component-dependency-loading.md`
- `docs/phase/phase-29.md`
- `docs/phase/phase-29-checklist.md`
- `docs/notes/typed-component-api-and-multi-instance-spi.md`
- ArtScene startup failure:
  `NoClassDefFoundError: org/simplemodeling/textus/scraper/api/TextusScraperApi$SocketSet`
