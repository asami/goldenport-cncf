# Phase 75 Checklist - Component/Service Purpose and Operation Statefulness

status=planned
phase=[Phase 75](phase-75.md)

## SP75-01: Contract and Consumer Inventory

Stage Status:
- Current status: OPEN
- Owner: CNCF phase coordinator and protocol/model maintainers
- Update rule: Close only from the inventory and contract evidence below.

- [ ] Existing component/service/operation metadata, builders, serializers, generated CML
      paths, runtime lookup, and relevant inspection consumers are inventoried.
- [ ] Exact upstream/CNCF/generator paths, compatibility policy, settled shared
      edits, bounded estimates, and execution profiles are recorded.
- [ ] Owner design/spec defines purpose, resident-state meaning, default and
      override precedence, declaration provenance, and executable acceptance.
- [ ] Mixed-component omission policy is fixed explicitly; the domain fallback
      proposal and per-service explicit purposes have executable acceptance.
- [ ] Framework-owned checkable state requirements and unknown-implementation
      reporting are defined without a universal static-verification claim.

## SP75-02: Attributes and Effective Resolution

Stage Status:
- Current status: OPEN
- Owner: admitted upstream definition owners and CNCF maintainers
- Update rule: Close only from the resolved-contract specifications below.

- [ ] Component purpose accepts domain/application/both, with omission -> domain.
- [ ] Service purpose accepts domain/application; an explicit value overrides
      the component default and omission inherits a single-purpose component.
- [ ] Both components preserve distinct service purposes and use the accepted
      omission policy without inventing both as a service/statefulness value.
- [ ] Service statefulness derives domain -> stateful and application -> stateless
      unless explicitly overridden.
- [ ] Operation statefulness inherits the effective service value unless
      explicitly overridden; the complete precedence matrix has executable evidence.
- [ ] Declared versus effective values survive builder and serialized round trips.
- [ ] Invalid explicit values produce structured errors; existing query/command,
      constructors, builders, and unannotated definitions remain compatible.

## SP75-03: Generated, Runtime, and Review Consumption

Stage Status:
- Current status: OPEN
- Owner: CNCF and admitted CML generator maintainers
- Update rule: Close only from producer/consumer and state-requirement evidence below.

- [ ] Representative generated CML component/service/operation metadata preserves
      declarations and effective values; generated Scala compiles against its owner API.
- [ ] Domain, application, and mixed-component fixtures preserve component
      defaults, explicit service overrides, and effective operation statefulness.
- [ ] Definition lookup and existing applicable inspection surfaces expose the
      same effective purpose/statefulness and their source of resolution.
- [ ] A stateless application command updates durable domain data without requiring
      authoritative memory retained across invocations.
- [ ] A stateful fixture exposes its resident-state requirement, and a known
      stateless declaration requiring that state produces a structured mismatch.
- [ ] Runtime consumption preserves current invocation/routing/security/storage
      behavior and reports unverified dependencies instead of asserting eligibility.
- [ ] Cloud-function candidacy is distinguished from deployment eligibility;
      purpose and statefulness overrides do not force a provider or instance lifecycle.

## SP75-04: Acceptance and Closure

Stage Status:
- Current status: OPEN
- Owner: CNCF phase coordinator
- Update rule: Close only when every validation, review, and release gate below is checked.

- [ ] Given/When/Then and property-based specifications cover resolution,
      round trips, invalid values, independent query/command, and compatibility.
- [ ] Changed development producer artifacts are refreshed before downstream
      generated/runtime validation, with exact source/artifact identities recorded.
- [ ] Focused and required final suites pass for the frozen admitted repositories.
- [ ] Accepted owner design/spec, consumer handoff, strategy, and phase references
      are aligned; no downstream adoption is claimed without consumer evidence.
- [ ] Final review and any bounded repairs have accepted closure evidence.
- [ ] Release records and phase closure are recorded only after all gates pass.
