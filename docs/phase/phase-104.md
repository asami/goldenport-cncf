# Phase 104: CAR Download Verification and Repository Trust

status=planned
planned_at=2026-10-09
current_status=OPEN
producer=Cozy Phase 76
checklist=[Phase 104 Checklist](phase-104-checklist.md)

## Goal

Verify downloaded CAR bytes before admission to the local component cache:
use an external SHA-256 sidecar for download corruption detection and, when
present, an external signature verified with a trusted, namespace-authorized
publisher key. Unsigned CARs remain accepted after checksum verification.

The adopted behavior contract is the
[shared CAR distribution specification](../../../cozy/docs/spec/car-distribution-verification.md).
This Phase is CNCF's consumer work ledger, not another wire-format authority.
[Cozy Phase 76](../../../cozy/docs/phase/phase-76.md) owns distribution preparation
and the coordinated repository-operation plan.

## Development items

| ID | Outcome | Status |
| --- | --- | --- |
| CDV-01 | Bind the shared signature/checksum/key contract to CNCF's actual download, trust configuration and cache lifecycle. | OPEN |
| CDV-02 | Resolve and verify repository-signed namespace/publisher key information from a configured trust root. | OPEN |
| CDV-03 | Check downloaded CARs before publishing them into the usable cache, with no downgrade on verification failure. | OPEN |
| CDV-04 | Preserve identity/runtime checks and move ABI metadata validation to its actual producers and consumers. | OPEN |
| CDV-05 | Prove the producer-to-cache path and document initial trust, key lifecycle and cache operation. | OPEN |

Detailed implementation and evidence obligations belong in the
[checklist](phase-104-checklist.md). The next work is CDV-01; execution priority
has not been selected. Creating this plan changes no active Goal or session.

## Ownership and dependencies

- Cozy prepares the completed CAR, external checksum and optional signature.
- Repository operation publishes artifacts and authenticated publisher-key
  information; it owns namespace enrollment and key update/revocation procedure.
- CNCF owns trust configuration, retrieval, verification and usable-cache admission.
- Runtime loading retains component identity, runtime range, safe paths,
  required execution resources and ordinary I/O validation.
- ABI metadata remains available to user tools; its current mandatory loading-time
  presence/simple checks are reconciled without deleting the metadata or Cozy ABI lint.

The joint contract must decide signature format and library, checksum syntax,
signature-presence discovery, signed key metadata, trust-root updates, revocation
effects on existing cache, and migration from older releases without sidecars.
CNCF does not infer these decisions independently of Cozy Phase 76.

## Acceptance boundary

A complete download is verified in temporary storage before cache registration.
Unsigned CARs pass checksum verification; signed CARs additionally require a
valid signature from an authorized key. Failures remain failures and never
become unsigned acceptance or a successful cache entry.

The consumer validates the same bytes it registers. Ordinary cache hits do not
repeat whole-archive checksum/signature verification under the specified
unchanged-cache ownership model; replacement and new downloads are verified.
Runtime compatibility remains checked for the executing CNCF version.

Acceptance uses actual repository download fixtures and Executable
Specifications, including corrupted content, CAR plus checksum replacement,
untrusted keys, signature retrieval failures, key lifecycle, failed cache
registration, reuse and update. The Cozy producer and CNCF consumer require
separate evidence; one repository's tests do not establish both outcomes.

## Exclusions and current-contract transition

Do not restore CAR-internal SHA inventories/file-set/digest checks, add a custom
classloader, require every public CAR to be signed, or require signing during
normal local development. Post-extraction continuous tamper monitoring,
SAR/media expansion and general build-output protection are outside this Phase.

The existing generation-compatibility contract still requires simple ABI
admission checks. This planned Phase does not silently change that contract.
CDV-04 must update its affected spec/design, callers and executable specifications
together when implemented. Active Cozy Phase 71.5 removal work, refusal records,
ledgers and review history remain unchanged and are not transferred here.

## References

- [Consumer implementation notes](../notes/car-distribution-verification.md)
- [Planning record](../journal/2026/10/2026-10-09-car-distribution-verification-plan.md)
- [Shared design](../../../cozy/docs/design/car-distribution-verification.md)
- [Existing generation compatibility specification](../spec/generation-compatibility-contract.md)

Implementation, tests, key generation, publication and deployment have not
started. This documentation task performs no staging, commit or push.
