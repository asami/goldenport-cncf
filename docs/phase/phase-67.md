# Phase 67 - CNCF Testability and Explicit Test Invocation

status=planned
planned_at=2026-08-15
depends_on=[Phase 57.5](phase-57.5.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 67 Checklist](phase-67-checklist.md)
foundations=[Test policy](../spec/test-policy.md), [Execution determinism](../design/execution-determinism.md), and [CNCF developer guide](../notes/cncf-developer-guide.md)

## Purpose

Make framework-owned test execution explicit, parameter-driven, reproducible,
and observable without requiring a caller to construct a temporary YAML test
descriptor for the ordinary isolated command case.

Phase 67 inventories and improves the complete CNCF testability surface:
CLI/bootstrap, configuration and descriptor projection, test home and
datastore isolation, deterministic execution, Component and SPI stubs,
assembly/source resolution, external fixtures, and test observability. It
normalizes new typed controls into the existing configuration and
`RuntimeTestDescriptor` semantics; it does not create a second runtime,
security, configuration, assembly, datastore, or observability model.

## Dependency and Scheduling

Phase 67 begins after Phase 57.5 closes. It is a separately selectable
planning branch: it does not renumber, reopen, or change the existing Phase
58--66 dependency chain. Only one Phase may be active at a time.

## Problem Statement

The current public integration path uses
`--textus.test.descriptor=<path>` to prove that a command is controlled test
execution. That is appropriate for structured assembly and provider overlays,
but it makes the ordinary case require a caller-created YAML file even when
the only intent is explicit isolated test execution.

`kind: test-descriptor` remains a document discriminator, not a CLI flag.
Phase 67 provides explicit CLI parameters for ordinary test controls while
retaining descriptor files for structured overlays that cannot be represented
unambiguously by stable parameters.

## Testability Inventory

| Surface | Current evidence | Phase 67 outcome |
| --- | --- | --- |
| Test activation and routing | `CncfRuntime`, `RuntimeConfig`, `cncf test`, and descriptor-path options | One explicit public test invocation contract; no ambient activation. |
| Configuration and descriptor projection | `RuntimeTestDescriptor` and resolved configuration precedence | Typed CLI inputs normalize into the same model; conflicts and provenance are deterministic. |
| State isolation | test-home controls and logical runtime/component datastore keys | Test-owned local state is selected without changing JVM `user.home` or production state. |
| Deterministic execution | execution profiles, clock, IDs, random, scheduler, ordering, and assumptions | Stable parameter projection, replayability, and no hidden seed/clock discovery. |
| Component doubles | built-in `MessageDeliveryStubComponent` and in-memory resource/provider test profiles | Define a reusable Component stub contract, lifecycle/reset/isolation rules, and a clear boundary from production Components. |
| SPI/provider tests | descriptor-backed SPI binding selection and existing provider runtime specs | Define safe test provider/stub installation and selection evidence without production fallback or direct socket mutation. |
| Composition and resolution | Component CAR, development-directory, assembly, and repository admission | Report selected source/freshness/integrity; never build, publish, activate, or remotely resolve implicitly. |
| External fixtures | provider/driver seams for process, network, filesystem, and datastores | Keep fixtures explicit, bounded, deterministic where possible, and separate from unit specifications. |
| Test observability | `ExecutionContext` test helpers, CallTree, diagnostics, metrics, and retained execution views | Provide deterministic, bounded test evidence for activation, controls, stub/SPI selection, outcomes, and cleanup with redaction. |
| Consumer guidance | framework Specifications and developer/test policy material | Prove the public contract through CLI, CAR, development-directory, SPI, stub, and representative downstream acceptance. |

## Selected Direction

- Extend the existing explicit `cncf test` path with stable parameter families
  for ordinary controls: test home, logical runtime/component datastores,
  execution profile, deterministic execution controls, Component test doubles,
  and source selection where a flat parameter is sufficient.
- TST-02 freezes exact spellings, types, defaults, precedence, duplicate
  handling, compatibility, and limits before implementation. The ordinary
  isolated case must not require a caller-created descriptor file.
- Accepted parameters normalize into the existing configuration and test
  descriptor projection. An equivalent parameterized and descriptor-backed
  invocation must expose the same effective non-secret configuration and
  structured outcome.
- Retain `--textus.test.descriptor=<path>` for advanced structured assembly,
  SPI/provider, and nested overlays. Do not flatten arbitrary YAML or
  assembly structure into unbounded CLI flags.
- Define a framework-owned test-double contract for Component and SPI/provider
  use: explicit installation, declared selection, deterministic recording,
  reset/cleanup, isolation, safe observability, and no production activation.
- Extend test observability only through existing CallTree, diagnostics,
  metrics, and configuration-provenance authorities. Test evidence must be
  deterministic, bounded, queryable by executable specifications, and redacted.
- Reject ambiguous mixtures, duplicate declarations, unsupported controls, and
  production-mode attempts deterministically. No parameter or ambient file may
  silently enable controlled execution, fixture wiring, remote access, or
  credential inheritance for ordinary `cncf command`.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| TST-01 | Inventory and contract freeze | CLI, descriptor, configuration, isolation, deterministic execution, Component/SPI stubs, resolution, fixture, and observability behavior is classified with failing-first acceptance identities. | planned |
| TST-02 | Parameterized invocation and test-double contract | Public parameter vocabulary plus Component/SPI test-double selection, lifecycle, evidence, precedence, conflict, and compatibility contracts are frozen. | planned |
| TST-03 | CLI/bootstrap and test-double implementation | `cncf test` accepts admitted typed controls without a required caller-created descriptor, and framework-owned Component/SPI doubles follow the accepted contract. | planned |
| TST-04 | Isolation and deterministic controls | Test home, logical datastores, profiles, time, IDs, random, scheduler, ordering, assumptions, double lifecycle, and cleanup are isolated and reproducible. | planned |
| TST-05 | Composition, resolution, fixture, and observability boundary | Parameterized common cases, descriptor-only structured overlays, Component source policy, fixture seams, and redacted test observability are proven. | planned |
| TST-06 | Guidance, downstream acceptance, and closure | CLI/help/provenance guidance, framework Specifications, CAR/development/SPI/stub cases, downstream acceptance, review, validation, and release records agree. | planned |

## Acceptance

- An ordinary isolated test command selects admitted controls by explicit
  parameters without a caller-created descriptor file.
- Equivalent parameterized and descriptor-backed invocations resolve to the
  same effective non-secret configuration, execution profile, resource
  selection, and structured outcome.
- Component and SPI/provider test doubles are explicitly declared, selected,
  initialized, observed, reset, and cleaned up; they cannot become implicit
  production providers or cross-test mutable state.
- Test CallTree, diagnostics, metrics, and provenance expose activation,
  normalized controls, selected stubs/providers, source selection, outcome,
  and cleanup evidence without leaking secrets, payloads, credentials, or
  unbounded host paths.
- Ordinary production command execution cannot gain controlled execution,
  test-owned state, fixture, assembly, provider, or authorization behavior
  from ambient files or undeclared parameters.
- Component CAR and development-directory selection remain explicit and do not
  fall back to builds, publication, remote resolution, or activation.
- Framework, CLI, Component CAR/development-directory, Component/SPI stub,
  and representative downstream acceptance prove the public contract.

## Non-Goals

- Removing descriptor-file support or treating YAML descriptors as deprecated.
- Automatic test discovery, ambient test configuration, hidden deterministic
  seeds/clocks, or implicit local/remote fixtures, repositories, credentials,
  providers, and Component activation.
- Flattening arbitrary assembly, SPI, provider, security, or Component policy
  into CLI flags.
- Application-specific doubles, mocks, fixture services, or changing
  production authorization, Component operation semantics, CAR publication, or
  launcher ownership.
- Replacing executable specifications with CLI smoke tests.

## Planning References

- [Phase 67 Checklist](phase-67-checklist.md)
- [CNCF Development Strategy](../strategy/cncf-development-strategy.md)
- [Test policy](../spec/test-policy.md)
- [Execution determinism](../design/execution-determinism.md)
- [CNCF developer guide](../notes/cncf-developer-guide.md)
- [Phase 57.5](phase-57.5.md)
- [Phase 58 series, closing in Phase 58.9](phase-58.9.md)
