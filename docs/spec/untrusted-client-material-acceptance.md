# Untrusted-client acceptance for Material applications

Status: specified; connected acceptance pending
Decision date: 2026-11-02

This is a concrete acceptance profile of the existing
[Textus security architecture](../design/textus-security-architecture.md) and
[Entity authorization model](../design/entity-authorization-model.md).
It does not introduce a new authorization engine or identity provider.
Treat mobile binaries, browser code and direct API callers as untrusted.

Rationale: [incident applicability and operational dispositions](../journal/2026/11/2026-11-02-mobile-security-incidents-and-textus-response.md).
The journal separates out-of-scope mechanisms, standard operational controls
and required consumer acceptance; it does not establish implementation proof.

For Web, storage, operational systems and external integrations together, see
the [Textus-wide disclosure assessment](../journal/2026/11/2026-11-02-customer-information-disclosure-textus-response.md).
It records broader coverage mapping without claiming those controls are all
implemented or silently extending this acceptance profile.

## Four required outcomes

| ID | Given / When | Required outcome |
| --- | --- | --- |
| UC-01 — No server secrets in clients | Inspect the distributable Android APK and its packaged resources/configuration, and the corresponding iOS/Web artifacts when accepting those platforms | No service-account private key, server OAuth client secret, administrative credential or shared privileged token is shipped. Public client IDs and API URLs are not secrets. User-scoped runtime credentials remain in the shared platform credential facility and out of ordinary logs, queue records and UI models. |
| UC-02 — Object and owner isolation | Authenticated user A can read/update an authorized Material; the caller substitutes B's Material/resource/owner/tenant ID through the public API | B's private metadata/originals cannot be read or changed, discovered in list/search, or exposed through a download/access reference. Supplied owner/tenant fields cannot reassign authority. Explicitly authorized sharing still works under the declared policy. |
| UC-03 — Authoritative upload completion | A caller claims success without completing the reserved transfer, uses another user's delivery reference, or supplies a wrong file/size/digest or incomplete provider proof | No unverified original becomes an admitted Material. The Material owner checks the existing reservation, caller and independently obtained storage evidence under its delivery contract. Unknown verification stays pending/action-required/failure as appropriate, never successful registration. |
| UC-04 — Separate operation and data authority | A valid user session invokes a forbidden operation, or an allowed operation against a forbidden resource; repeat through internal service delegation | Operation denial prevents protected action execution; object denial prevents unauthorized disclosure/mutation. Authentication or successful entry authorization alone grants no data authority. Server service credentials cannot widen the caller's authorized result. |

For UC-02/04, exercise the deployed public ingress using crafted requests,
not only hidden UI controls or direct calls to a policy helper. Include a
positive authorized control and assert both the response and resulting state.
Use stable denial semantics without disclosing private resource details.
Use synthetic users/fixtures; do not put real secret values into evidence.

For UC-03, preserve the selected delivery contract: the client does not gain a
new file-ID, owner, digest-proof or internal-authority parameter merely to test
denial. Unsupported fields are rejected or otherwise cannot affect the result
under the actual input contract. Test omitted bytes, wrong reserved contents,
cross-user references and unavailable provider verification at their real
boundaries. Existing independently verified size/SHA-256 obligations remain;
client-computed hashes alone are not server verification.

## Ownership

- CNCF owns authenticated context propagation, Operation authorization and the
  canonical internal DSL/UnitOfWork Entity/resource authorization boundaries.
  Preserve originating subject and explicit bounded delegation across internal
  service/async calls. Untrusted input cannot select System/ServiceInternal.
- textus-user-account remains the account/session authority.
- Flutter Core owns shared credential custody, authenticated transport,
  redaction and the TKL delivery client. Application Framework presents those
  capabilities; application code supplies Book/UI composition, not another
  credential store or permission engine.
- Studio declares application operation/resource policy through CNCF, including
  access to source originals through downstream Workbench/TKL calls.
- TKL owns Material identity, owner/destination/reservation bindings and
  admission from actual provider evidence. Provider mechanics may move to EAI
  under its accepted handoff; a provider credential is not Material authority.

## Delivery and evidence

[CNCF Phase 107](../phase/phase-107.md) owns common authorization gaps.
Consumer delivery uses existing qualified capabilities without waiting for all
of Phase 107. Any missing common enforcement is assigned to CNCF, not copied
into App, Studio or TKL handlers. TKL-specific admission remains in TKL.

App Phase 6.10 and Server Phase 5 accept the mobile path with TKL; App Phase 7
extends it to the Web ingress and distributable. Core's authentication workstream
supplies the shared client portion. TKL's current delivery workstream and later
EAI migration both retain UC-01..04.

Reuse relevant existing evidence after checking its actual implementation,
configuration, ingress and scenario scope. Record missing coverage as open;
document changes do not grant acceptance. Add focused executable scenarios for
gaps and one integrated review within the owning delivery. Do not create a
review cycle per ID, rerun unrelated suites, or require APK anti-debugging,
attestation, new management hashes or copied-file comparisons.

## Standard protection allocation — 2026-11-02

UC-01..04 are STANDARD obligations under the
[small-business baseline](small-business-security-baseline-and-options.md).
They do not depend on users enabling advanced hardening or configuring each
guard. Stronger sharing/access settings cannot weaken these boundaries.
