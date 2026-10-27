# Assembly-free standalone startup

Status: adopted target contract; implementation and installed qualification pending.
Adopted: 2026-10-27.
Plan: [startup correction](../phase/assembly-free-startup-correction.md).
Evidence: [investigation and handoff](../journal/2026/10/2026-10-27-assembly-free-startup-handoff.md).

## Boundary

An installed Component consumer must not create or select a local
`.textus/assembly.yaml` merely to invoke CNCF Operations. An intentionally
selected assembly remains a supported deployment override. The absence of an
unselected assembly is not a configuration error. Installed component selection
and the consumer project's CAR/SAR structure are separate concerns.

CNCF owns configuration resolution, execution-mode selection, fixed-user context,
security wiring and stable runtime identity. sm-workflow owns its installed
composition/setup and Session/storage adoption. Skills do not synthesize assembly
files or deployment identities to satisfy runtime prerequisites.

## Normal startup contract

1. Load the normal admitted configuration sources and resolve the explicit
   standalone intent using existing typed bindings and precedence.
2. Resolve the fixed-user identity through the supported normal user-profile/
   configuration path. Reading that source must not require a previously
   populated descriptor localSubject.
3. Establish the effective fixed-user execution context and security wiring,
   then validate the resolved context. Reuse existing typed models and bindings;
   do not create a second configuration resolver or arbitrary flat-key parser.
4. Resolve stable subsystem identity from the installed composition and supported
   runtime configuration. A user-authored assembly file must not be its only
   source. Existing internal descriptors may remain an implementation mechanism.
5. Report a genuinely missing or conflicting identity as that specific problem,
   with supported configuration guidance, rather than requesting a dummy assembly.

User identity is not authorization. Preserve explicitly selected roles,
capabilities, security level and disabled privilege fallback. Do not derive
privileges from a username, process environment or the fact that startup is local.
Do not silently move an authenticated/multi-user execution into fixed-user mode.
Controlled-test execution remains explicit. Preserve validation and precedence
for an intentionally selected missing, invalid or conflicting assembly override.

The correction must cover both preflight and final Subsystem construction.
Removing only the early guard, or merely creating a HOME profile, does not prove
that effective authentication wiring has been established.

## Existing runtime adoption

Preserve effective subsystem identity, user identity, storage bindings and grants
when replacing a temporary assembly-based setup. Do not silently fall back to a
new default subsystem name or datastore. Preserve the owning sm-workflow Session,
Plan, accepted results, history, repair counts and successful checks. Any real
identity/storage change uses its supported explicit migration route.

Keep a working temporary configuration until the replacement has passed isolated
installed qualification. Do not mutate private Session rows, restart a workflow,
re-Submit accepted work or rerun successful validation to reconstruct evidence.
This correction is independent of unaffected ongoing development.

## Acceptance

The owning plan proves installed invocation without local assembly or descriptor
override, accurate missing-identity diagnostics, security equivalence, explicit
override behavior and stable same-Session adoption. Documentation alone does not
establish an available installed route.
