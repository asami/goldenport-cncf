# Phase 33 - Resource Reference DSL and URN Provider Resolution Checklist

This checklist tracks Phase 33 implementation and evidence. The summary
dashboard is `phase-33.md`.

## RR-01: Resource Reference and Internal DSL Contract

Status: DONE

- [x] Define `ResourceReference` parsing for absolute URLs and URNs.
- [x] Define read-only `ExecutionContext.resources` DSL operations and
  structured `Consequence` failures.
- [x] Define the rule that component code does not call direct filesystem or
  network APIs for managed resource reads.
- [x] Define reference identity, content encoding, missing-resource, and
  policy-denial semantics.

Acceptance evidence:

- A CNCF design/specification document defines the model and DSL before the
  implementation is treated as stable.
- Executable specifications cover valid URL/URN parsing and malformed or
  unsupported reference rejection.

Evidence: `docs/spec/resource-reference-dsl.md`,
`docs/design/resource-reference-dsl.md`, `ResourceReferenceSpec`, and
`ResourceAccessDslSpec` passed on Jul. 16, 2026. The implementation exposes an
unconfigured `ResourceAccess` default only; provider/policy resolution remains
RR-02 through RR-04.

## RR-02: URL Provider and Policy Resolution

Status: DONE

- [x] Define a URL-scheme read-only resource provider SPI.
- [x] Bind URL scheme providers through ExecutionContext configuration.
- [x] Constrain `file:` reads to configured roots.
- [x] Constrain HTTPS reads to configured hosts.
- [x] Reject an unconfigured scheme, host, root, or provider with a structured
  policy failure.

Acceptance evidence:

- Specs prove configured URL reads and deny-by-default behavior without relying
  on ambient host access.

Evidence: `UrlResourceAccessSpec`, `RuntimeConfigSpec`, and
`ExecutionContextSpec` passed on Jul. 16, 2026. `file:` reads require both a
configured lexical root and a resolved real path below a configured real root;
`https:` reads use the configured CNCF `HttpDriver` only for exact configured
hosts. The runtime config aliases are `textus.resource.url.*`,
`textus.runtime.resource.url.*`, `cncf.resource.url.*`, and
`cncf.runtime.resource.url.*`.

## RR-03: Standard Textus URN Resolution

Status: DONE

- [x] Freeze `urn:textus:<namespace>:<resource-id>` grammar.
- [x] Define and install `TextusUrnResourceProvider` SPI.
- [x] Bind logical Textus namespaces through execution configuration.
- [x] Resolve a Textus URN without exposing a file path or remote endpoint to
  component code.
- [x] Reject unknown Textus namespaces and invalid resource identifiers.

Acceptance evidence:

- The same Textus URN resolves to in-memory test data and configured runtime
  data through unchanged component code.

Evidence: `TextusUrnResourceAccessSpec`, `RuntimeConfigSpec`, and
`ExecutionContextSpec` passed on Jul. 16, 2026. The standard provider parses
only safe logical `urn:textus:<namespace>:<resource-id>` values, maps each
configured namespace to a read-only root through
`textus.resource.urn.textus.file-roots` and its runtime/CNCF aliases, and
verifies the resolved real path remains within the selected root. This provider
layer is independent of existing Blob/entity semantic URNs.

## RR-04: Generic External URN Extension SPI

Status: DONE

- [x] Define `UrnResourceProvider` SPI for `urn:<nid>:<nss>` references.
- [x] Bind external NID providers only through explicit configuration.
- [x] Reserve `textus` for `TextusUrnResourceProvider` and prevent generic
  provider shadowing.
- [x] Verify an explicitly installed non-Textus test NID resolves correctly.
- [x] Verify unconfigured or malformed external URNs are rejected.

Acceptance evidence:

- Specs prove generic NID dispatch while the default profile exposes only the
  standard Textus URN route.

Evidence: `UrnResourceAccessSpec` and `ExecutionContextSpec` passed on Jul.
16, 2026. `UrnResourceProvider` binds only configured non-Textus NIDs through
`textus.resource.urn.providers` and its runtime/CNCF aliases. Configuration
rejects reserved `textus`, duplicate bindings, invalid provider classes, and
provider/NID mismatches before resource access is installed. The dispatcher
always routes `urn:textus:` to the dedicated standard provider first.

## RR-05: Deterministic Test Providers and Observability

Status: DONE

- [x] Add in-memory URL/Textus-URN/external-URN provider fixtures.
- [x] Bind providers through deterministic ExecutionContext test profiles.
- [x] Record provider identity and policy-safe resolution diagnostics.
- [x] Verify resource contents and provider settings are not leaked in ordinary
  failure output.

Acceptance evidence:

- Tests execute without host-file or external-network dependencies.

Evidence: `ResourceAccessTestProfileSpec`, resource provider specs, and
`ExecutionContextSpec` passed on Jul. 16, 2026. The explicit
`ResourceAccessTestProfile` installs in-memory URL, Textus-URN, and external
URN providers only through `ExecutionContext.withResourceAccessTestProfile`.
`ExecutionContext.resources` records `dsl:resource.read` and its resolve phase
with only scheme/provider family/provider identity/configured-state attributes;
the executable spec proves complete URL/URN references and resource contents
are absent from those attributes and ordinary missing-resource failures.

## RR-06: SIE Consumer Migration

Status: DONE

- [x] Replace SIE BoK metadata direct filesystem reads with the CNCF resource
  internal DSL.
- [x] Replace SIE BoK source direct filesystem reads with the CNCF resource
  internal DSL.
- [x] Bind SIE default/test BoK references through `urn:textus` configuration.
- [x] Verify metadata-only and source-enabled SIE paths using in-memory and
  configured provider bindings.

Acceptance evidence:

- SIE has no direct `Files.readString` path for managed BoK metadata or source
  content.
- Cross-repository tests demonstrate the same logical BoK URN under test and
  runtime bindings.

Evidence: CNCF `ResourceReferenceSpec` and `TextusUrnResourceAccessSpec` passed
12 tests on Jul. 16, 2026. SIE `BokKnowledgeSourceSpec`,
`BokComponentMetadataSpec`, and `HtmlSiteIndexerSpec` passed 17 tests on Jul.
16, 2026. `BokKnowledgeSourceReader` and `BokComponentMetadataReader` now
accept `ExecutionContext`, derive only safe child references through
`ResourceReference.resolveC`, and read through `ExecutionContext.resources`.
SIE's configured fixture roots and explicit in-memory Textus provider both use
the same `urn:textus:bok:<resource-id>` source contract.

## RR-07: Closure

Status: DONE

- [x] Run focused CNCF and SIE regression suites.
- [x] Run CAR lint/review for the SIE consumer migration.
- [x] Record verification evidence in this checklist and phase dashboard.
- [x] Update strategy and phase status, then commit the validated work.

Acceptance evidence:

- Jul. 17, 2026: CNCF focused Phase 33 suites passed 65 tests:
  `ResourceReferenceSpec`, `ResourceAccessDslSpec`, `UrlResourceAccessSpec`,
  `TextusUrnResourceAccessSpec`, `UrnResourceAccessSpec`,
  `ResourceAccessTestProfileSpec`, `ExecutionContextSpec`, and
  `RuntimeConfigSpec`.
- Jul. 17, 2026: SIE focused consumer suites passed 57 tests:
  `BokKnowledgeSourceSpec`, `BokComponentMetadataSpec`,
  `HtmlSiteIndexerSpec`, and `ComponentFactorySpec`. The complete suites also
  passed after the final migration: 1,877 CNCF tests and 172 SIE tests.
- Jul. 17, 2026: `cncf-car-lint` reported no SIE CAR failure and verified the
  component code has no obvious CNCF internal-DSL bypass. The remaining
  `abi.baseline.missing` and development `sbt-cozy` SNAPSHOT warnings concern
  future CAR publication readiness and are outside this closed phase.
- Documentation/CML review confirms `ingestBokKnowledgeSource` documents the
  ResourceReference DSL boundary, accepts only an absolute URL or configured
  `urn:textus` source root, and does not claim direct filesystem or network
  access.
