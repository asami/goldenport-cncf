# Phase 104 Checklist: CAR Download Verification and Repository Trust

Status: PLANNED
Phase: [Phase 104](phase-104.md)
Closure basis: implementation and actual verification evidence below.
Planning documents alone do not complete the feature.

## CDV-01: Consumer contract and path selection

- [ ] Identify the actual remote CAR download, cache-hit, replacement and expanded-CAR paths.
- [ ] Freeze the shared checksum syntax, signature format/library and signature-presence discovery with Cozy Phase 76.
- [ ] Define initial trust configuration, authenticated key metadata and namespace authorization.
- [ ] Decide trust-root update, key expiry/revocation, existing-cache effects and old-release migration.
- [ ] Define cache ownership, permissions, concurrent retrieval and successful-entry visibility without introducing repeated digest proofs.

## CDV-02: Repository key information

- [ ] Load only explicitly trusted repository roots from client configuration.
- [ ] Verify publisher-key metadata and select an authorized active key for the CAR namespace.
- [ ] Reject untrusted, altered or expired required key information without an unchecked fallback.
- [ ] Exercise authorized key updates and the selected revocation procedure.

## CDV-03: Download verification and cache admission

- [ ] Retrieve CAR bytes into temporary storage and verify the required external SHA-256 sidecar.
- [ ] Accept unsigned CARs after checksum success and verify signatures when present.
- [ ] Distinguish declared unsigned state from signature/metadata retrieval failures.
- [ ] Preserve structured failure results instead of treating verification failure as ordinary absence or unsigned acceptance.
- [ ] Register the same verified bytes and keep incomplete/failed downloads unavailable to loaders.
- [ ] Exercise checksum mismatch, malformed/missing sidecars, signature failure, namespace/key mismatch and registration I/O failure.
- [ ] Reuse unchanged admitted cache without per-load whole-archive verification; verify replacements and new downloads.

## CDV-04: Runtime and metadata responsibilities

- [ ] Preserve requested/actual component identity and executing-runtime range checks.
- [ ] Preserve safe extraction paths, required execution resources and ordinary parsing/I/O failures.
- [ ] Remove mandatory loading-time ABI metadata presence/simple checks and reconcile affected callers/specs/design.
- [ ] Demonstrate that ABI metadata remains available to real catalog/user-tool consumers.
- [ ] Preserve Cozy ABI public-contract lint and avoid restoring internal inventory or class-loading SHA checks.
- [ ] Record the treatment of expanded CARs and editable development outputs without calling them download-verified cache.

## CDV-05: End-to-end acceptance and operation

- [ ] Prove unsigned and signed Cozy-prepared artifacts through a repository download/cache fixture.
- [ ] Prove that CAR plus SHA replacement fails signature verification and a replacement self-generated key is not trusted.
- [ ] Use Given/When/Then and property-based checks for byte mutation/checksum behavior and actual cache reuse/update.
- [ ] Complete operator instructions for initial trust, namespace enrollment, key rotation/revocation and cache reacquisition.
- [ ] Run required affected-repository validation and independent review, recording actual results.
- [ ] Check all acceptance conditions and record remaining obligations before Phase closure.

## Current evidence

On 2026-10-09 the consumer Phase, checklist, notes and journal were written.
No implementation, Executable Specification, SBT result, key-management
execution, public deployment or cross-repository runtime acceptance is claimed.
Every implementation item remains OPEN. Existing evidence from unrelated Goals
is not reused as acceptance of this feature.
