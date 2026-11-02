# Shared dev/test/demo/production security defaults

Decision date: 2026-11-02
Status: adopted product contract; implementation and deployment acceptance pending
Parent: [small-business security baseline](small-business-security-baseline-and-options.md)

## Purpose and existing vocabulary

Make ordinary development easy without implementing different authorization
engines for development and deployment. Modes select common defaults, identity
fixtures, integrations and specific system behavior; they are not an ascending
scale of security strength.
Network exposure, real data and actual recipients determine required protection.

Use existing CNCF `OperationMode` and `RuntimeOperationSecurityPolicy` through
the common configuration owner. The user-facing `dev` name maps to canonical
`develop` (`OperationMode.Develop`); `test`, `demo` and `production` retain their
existing meanings. `textus.operation-mode` is the existing operation-mode key.
Do not confuse it with CLI `RunMode` (server/client/command), Flutter debug/release
builds, UI collection/editing modes or test-suite purpose labels. No new parallel
mode registry, security engine or per-application environment parser is required.

## Common defaults

| Mode | Identity and data | Integration and exposure | Diagnostics |
| --- | --- | --- | --- |
| dev / develop | Preconfigured development users, roles and synthetic data; optional local auto-login through the common identity adapter | Loopback by default, isolated development storage and local/test providers; explicit provisioning for real-service experiments | Detailed structural errors, with credentials and private content redacted |
| test | Explicit fixture subjects, grants and isolated test data | Controlled substitutes, declared test-specific behavior and selectively enabled real integrations on test-owned resources; live access follows the verification plan | Reproducible sanitized results; no production credentials/data by default |
| demo | Demo organization and participant accounts; synthetic examples clearly distinguished from participant-generated Material | Separate demo storage/provider configuration; production-equivalent protection for participant/network access | Safe user errors and restricted sanitized operational diagnostics |
| production | Real accounts, membership and business permissions | Qualified real providers, private storage and protected public transport; no development authentication or destructive fixture reset | Sanitized audit/diagnostics with declared access and retention |

All modes use the same Operation authorization and Entity/resource/UoW
enforcement, typed input validation, credential redaction and authoritative
Material admission. Shared code does not mean identical grants: fixtures may
declare different roles, while the evaluator and business ownership semantics
stay the same. Mode alone never makes a caller an administrator or owner.
Explicitly public Operations remain possible under their ordinary declared policy.

## Mode-dependent behavior

Mode selects runtime behavior as well as defaults. Shared configuration resolves
the mode into declared policies/adapters; settings specify the capabilities,
destinations and limits. Components consume that selection instead of scattering
raw mode checks through application Operations. Reuse existing extension points.

Supported differences can include scoped development auto-login, selected
test-only external integrations or fault/clock adapters, demo presentation
assistance, and production provider bindings. These are examples, not a demand
to implement every feature. Test can use a real test endpoint for one dependency
and substitutes for others, or redirect notifications to a test recipient.
It is not limited to a wholly simulated system.

For each supported difference, declare enabled modes, the selected capability/
adapter, allowed recipient/resource scope and disabled behavior. Test does not
grant blanket external access: calls retain ordinary authority, configured
destinations, credentials and effect limits. Missing selected bindings produce
an explicit failure/unavailable result, not an unselected destination or fabricated
success. Untrusted requests cannot enable test-only hooks in an excluded mode.

The authorization evaluator is common. Declared feature availability and policy
inputs can vary by mode, so intentional availability differences may produce
different admission outcomes. Unchanged business ownership and equivalent grants
retain their meaning; neither Operation nor Entity enforcement is bypassed.

Use focused executable scenarios for intended adapter/recipient selection,
authorized positive operation, forbidden access, disabled behavior in excluded
modes and missing configuration. Attribute real external calls separately from
substitutes; stub success cannot qualify a real test integration.

## Configuration and identity boundaries

- The trusted host resolves the mode and effective policy once using the shared
  configuration precedence. A request field/header, UI toggle, fixture account
  or client-declared mode cannot weaken the server policy or select another tenant.
- Core consumes a safe client configuration and the selected account/session
  contract; Framework presents account/environment state. Neither receives
  provider secrets or becomes a second authority for server mode/authorization.
- No silent fallback from demo/production to dev authentication, anonymous
  administration, fixed shared identity or permissive storage. Missing mandatory
  configuration fails the affected startup/capability with an actionable error.
- Development convenience creates an explicitly scoped fixture identity through
  the common adapter; it does not skip authorization, impersonate a real user,
  or mark simulated authentication as provider-verified. Protected provider-only
  paths remain protected. Tests may inject test-owned identity facts only inside
  their isolated harness; clients cannot submit those facts as authority.
- A local synthetic demonstration may explicitly use the isolated development
  profile and omit interactive login. Label it as such; it is not evidence of
  participant authentication. Demo defaults do not enable that shortcut.
- Developer LAN/device experiments require an explicitly configured restricted
  exposure and test identities/data. Loopback development is not silently exposed
  by connecting a phone, changing a proxy or selecting demo. Production is not
  inferred from a release build, nor development from running a test command.
- Account/environment changes follow existing session, credential, pending-work
  and storage-isolation contracts. Never replay queued real Material into a new
  tenant, endpoint or fixture account. Seeding/reset affects only selected
  synthetic resources; demo participant originals are not fixture-reset targets.

## Ownership and migration

| Owner | Responsibility |
| --- | --- |
| CNCF | Resolve existing mode/configuration into defaults; enforce common ingress, Operation/resource and internal-delegation boundaries |
| textus-user-account | Ordinary account/session issuance and clearly scoped development/test identity adapters; no simulated provider assurance |
| Flutter Core | Common configuration consumption, environment-scoped credentials/queues and native/Web transports |
| Flutter Application Framework | Common login/navigation/account/environment presentation; no App-specific auth branches |
| TKL / EAI | Mode-appropriate provider credentials, isolated storage, authorized destinations and unchanged Material verification |
| Studio / App | Compose these facilities and demonstrate the selected deployment; domain code does not branch around security |
| sm-workflow | Select the target application's verification profile and retain the actual mode with existing execution evidence |

Current CNCF code has development/test anonymous-admin policy and
`OperationMode.allowsDebugAuth` includes demo. `AdminAuthorizationPolicy` also
selects its development rule for non-production modes. These are existing
implementation facts, not acceptance of this contract. P107-05 must explicitly
reconcile those defaults with fixture identities and participant-demo policy;
do not assume a `demo` label already provides production-equivalent protection.
Reuse existing configuration/types and evaluator. Do not merely add application
guards, a second authorization evaluator or blanket mode-based allow behavior.

## Executable acceptance allocation

| ID | Scenario | Required result |
| --- | --- | --- |
| MODE-01 | Ordinary dev startup with fixture users and local substitutes | Useful route works with minimal setup; no mandatory external IdP/cloud onboarding |
| MODE-02 | Authorized and cross-user/forbidden requests with equivalent subjects/grants on business paths enabled in all four modes | Same ownership outcomes through common Operation and Entity paths, including list/search and internal-service delegation; intentional mode-specific availability is verified separately |
| MODE-03 | Missing real authentication/configuration, forged client mode or development auth/admin shortcuts in participant demo/production | No protected execution or fallback; real login and permitted Operations still work |
| MODE-04 | Switch account/environment or reset synthetic fixtures while pending/real Material exists | No cross-account/endpoint replay, secret exposure or deletion of participant originals |
| MODE-05 | Participant phone -> TKL -> Web editing using the actual demo configuration | Real authentication, owner separation, verified registration, approved media recipients and logout hold without expert participant settings |
| MODE-06 | sm-workflow runs ordinary validation, then selects an affected mode-boundary scenario | Dev is the normal target profile; test/demo/production selection is explicit and recorded; dev success is not relabelled deployment qualification |
| MODE-07 | A mode-specific feature, including selective real test access, is enabled/disabled or lacks required configuration | Correct policy/adapter/recipient; common authorization; excluded modes cannot enter the path; no undeclared external call or silent fallback |
| MODE-08 | A test declares modes and is selected through metadata -> build.sbt/shared SBT -> sm-workflow | Required variants execute with actual target mode and separate results; no duplicate mode table, silently omitted mode or undeclared caller command |

Use focused configuration/authorization tests for the four-mode matrix, not four
copies of every suite. Existing UC/MS/SB evidence can satisfy overlapping cases.
Product execution remains with its ordinary owners; no new per-helper review,
hash/copy comparison gate or reopening of completed Phases is introduced.

The [per-test mode contract](test-execution-mode-declarations.md) owns declaration,
inheritance, policy defaults and build/result binding. Nightly selection supplies
dev and production for tests without explicit mode requirements; explicitly
declared modes remain required rather than being overwritten by those defaults.
Production-mode testing uses isolated test-owned
resources and the ordinary authorization path, not real customer deployment
access or development auto-login. Preserve separate mode results, normal SBT
serialization and finite execution limits; both required runs must pass.
Daytime validation remains dev-first. Explicit Slow/explicit-only/live-provider and actual
demo/deployment qualification retain their declared scope.
Test-specific behavior also receives focused scenarios, including the declared
lightweight nightly regression set. Dev/production success cannot cover a
test-only path. No third full-suite run is implied; required selected test-path
failures remain visible in overall nightly results, and costly/live cases retain
explicit scheduling and scope.

## November 10 NICT demonstration

The participant-facing demo uses the demo profile with production-equivalent
authentication, Operation/resource authorization, protected public transport,
private participant storage and disclosed/authorized photo/audio processing.
Use a separate demo organization/provider destination, prepared participant
accounts and ordinary login; no shared administrative session or manual Bearer
entry. Temporary/local rehearsal shortcuts cannot satisfy live P0/P1 acceptance.
The operator owns provisioning, participant notice and post-event retention/
deletion arrangements; Material is not automatically deleted as demo fixture data.

First qualify the basic phone -> stored Material -> Web -> persisted Information
route with two distinct users, then connect Fold and image import. Reuse existing
producer evidence and add only mode/configuration gaps. Do not wait for the whole
of P107, Google OIDC, passkeys, HSM or offline storage to deliver this bounded route.
