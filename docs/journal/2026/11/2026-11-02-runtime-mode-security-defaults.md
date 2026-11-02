# Shared runtime-mode defaults and the NICT participant demo

Date: 2026-11-02
Status: adopted specification and planning decision; no implementation acceptance

## Decision

Textus must be convenient during early development and usable by small businesses
without expert security setup. Centralize dev/test/demo/production defaults in
the shared foundations. Keep Operation and Entity/resource authorization common;
vary prepared identities, grants, provider connections, isolated data and
diagnostic presentation instead of removing enforcement.

Modes are purposes, not a security ranking. A public demo processing actual
participant photos/audio needs production-equivalent protection at those
boundaries. An isolated synthetic demonstration can use the development profile,
but cannot count as participant-login or production-readiness evidence.

Canonical contract: [shared runtime-mode security defaults](../../../spec/runtime-mode-security-defaults.md).
This extends [small-business defaults and options](2026-11-02-small-business-security-defaults-and-options.md)
and the [customer disclosure response](2026-11-02-customer-information-disclosure-textus-response.md).
Physical isolation remains a separate, optional deployment choice; it is not a
prerequisite for ordinary development or the November demo.

## Actual baseline and delivery

CNCF already has `OperationMode` (dev is an alias for develop), common configuration
and shared authorization checkpoints. Existing anonymous-admin and demo debug-auth
behavior requires explicit reconciliation; the mode name alone proves no safety.
Record this gap in P107-05 and qualify the bounded participant route with Studio
ESS5-01/02/05. Common identity handling belongs to user-account; client transport
and presentation belong to Core/Framework. TKL/EAI retain storage/provider and
Material-admission responsibilities. Apps compose rather than recreate them.

The November 10 demo retains App 6.10 -> App 7 and Server Phase 5, with the basic
authenticated phone-to-Web route first. Prepared accounts and a demo organization
reduce setup effort, without a shared privileged login or disabling ownership.
Consent/recipient disclosure and post-event data handling apply to real Material.
Fold and image import extend the qualified route afterward.

sm-workflow normally validates target applications in dev mode. A test command
does not automatically mean test mode. Deterministic fixture scenarios can select
test; affected security/configuration boundaries and actual demo/release acceptance
select demo/production. Retain mode in existing execution evidence, preserve
suite obligations and reuse applicable results rather than run all suites four times.

The subsequent nightly policy runs the declared nightly suite in both dev and
production against the same selected revision, with isolated test resources and
separate outcomes. Both must succeed for nightly success. Production mode does
not authorize using customer systems or replacing real-provider acceptance with
fixture results. Daytime remains dev-first; existing execution limits and SBT
serialization apply. sm-workflow VM-05 owns the nightly integration.

## Clarification: modes select behavior

The user clarified that each CNCF mode may change specific system behavior,
not just identity fixtures or storage defaults. Test can enable selected external
accesses, mix real test endpoints with substitutes, redirect notifications or
use controlled fault/time adapters. Mode selects declared behavior; configuration
specifies capabilities, destinations and scope. Common authorization remains
active; mode grants no blanket external access. This refines the earlier
description of test as deterministic fixtures.

MODE-07 and P107-04/05 cover selection, positive/denied calls, excluded modes and
missing bindings. MODE-02 retains common ownership semantics for paths enabled
in all modes, without requiring identical availability for mode-specific features.
sm-workflow VM-06 selects focused test-specific coverage, including the declared
lightweight nightly set beside dev/production. This is not a third full-suite
run; costly/live cases retain explicit selection and actual result attribution.
Dev/production PASS cannot cover a test-only path. Implementation remains pending.

## Plan links

- [CNCF Phase 107](../../../phase/phase-107.md): shared defaults and mode-boundary enforcement.
- [Studio Phase 5](https://github.com/KnowledgeHubProject/nict-editing-studio/blob/codex/ess3-server-editing/docs/phase/phase-5.md): real participant deployment and joined acceptance.
- [App 6.10](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/phase/phase-6.10.md) / [App 7](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/phase/phase-7.md): native then Web/Fold composition.
- [Core](https://github.com/asami/textus-flutter-core/blob/main/docs/phase/user-account-authentication-integration-plan.md) / [Framework](https://github.com/asami/textus-flutter-application-framework/blob/main/docs/phase/user-account-authentication-integration-plan.md): reusable configuration and identity presentation.
- [User Account workstream](https://github.com/asami/textus-user-account/blob/main/docs/phase/runtime-mode-authentication-integration.md): existing identity/session foundation, without reopening closed federation Phase 2.
- [sm-workflow workstream](https://github.com/asami/sm-workflow/blob/main/docs/phase/development-mode-validation-plan.md): dev-first validation and targeted mode qualification.

No executable implementation, acceptance result, Phase status or prior evidence
is changed by this decision. This is not a new general security-settings engine.

## Per-test declaration and execution ownership

The user adopted explicit per-test required modes. CNCF metadata/discovery owns
source declarations, class/per-test resolution and canonical OperationMode values.
build.sbt wires the shared mechanism that applies the selected mode to actual
tests/child processes. sm-workflow selects tests and required variants, invokes
the normal managed route and records outcomes by test/parameters/mode.
Assertions define each mode's expected behavior; results need not be identical.

[The declaration contract](../../../spec/test-execution-mode-declarations.md)
and [TM-01..04 follow-up](../../../phase/test-execution-mode-plan.md) define this
pending extension. Current V1 lacks the field, so producer/annotation/ABI and
consumer adoption must be delivered together. Closed Phase 103 stays closed;
its existing ADMISSION/standard-annotation migration remains authoritative.

Mode-unspecified tests inherit dev daily or dev/production nightly. Explicit
required sets remain required when their test is selected, including targeted
test-only or demo scenarios. A plan cannot silently drop modes, and identical
test/parameters in different modes cannot collapse to one result. No duplicate
build/consumer registry, free-form command surface or four-mode blanket replay.
