# CAR Distribution Verification: CNCF Consumer Notes

Recorded: 2026-10-09.
Role: non-normative investigation and planning notes.
Work: document the consumer boundary for [Phase 104](../phase/phase-104.md).
Behavior authority:
[shared specification](../../../cozy/docs/spec/car-distribution-verification.md).

## Adopted direction

The human selected verification at download/cache admission, rather than
internal-file verification during every CAR load. External SHA-256 detects
download corruption. An optional external signature authenticates completed
CAR bytes against a trusted, namespace-authorized publisher key.

Unsigned CARs are accepted after checksum verification. A present invalid
signature is not treated as unsigned. Retrieval errors do not establish that
a signature is absent. Public CAR signing is optional, including repository
distribution; this supersedes the earlier discussion's mandatory-signing idea.

Repositories distribute publisher-key information authenticated from a trust
root registered by the client operator. A key bundled with a CAR, or merely
downloaded alongside it, is not automatically trusted.

## Observed implementation entry points

Read-only inspection on 2026-10-09 found:

- ComponentRepository's default local cache under the configured user cache path.
- _fetch_standard_component_artifact delegates canonical releases to
  _fetch_canonical_component_artifact.
- The canonical fetch path first tries an existing artifact, otherwise copies
  remote bytes into a sibling temporary file, moves that file to the final
  cache path, then checks the canonical artifact descriptor.
- _copy_remote streams bytes through ordinary URLConnection/Files.copy.
- The fetch path currently collapses NonFatal errors into None. Future checksum
  and signature rejection must remain a distinguishable failure, not disappear
  into another lookup or unsigned fallback.
- CarRuntimeAdmission currently keeps identity, runtime and simple ABI checks.
  DevelopmentCarRuntimeAdmission also consumes ABI metadata; its role must be
  inspected before the loading-time metadata requirement is reconciled.

These are investigation starting points, not a frozen implementation manifest.
Other discovery/download paths must be inventoried before changing a helper.

The intended insertion point is after download completion and before a file
becomes a usable cache entry. The cache publication mechanism must expose the
same bytes that were verified. Ordinary cache hits reuse admitted bytes under
the unchanged-cache model; editable development outputs remain separate.

## ABI metadata distinction

The existing loading-time ABI check reads declarations, not class signatures
or a previous released ABI. ABI metadata is useful to catalogs and user tools;
those consumers validate the data they actually use. Cozy's release-contract
comparison through cozy lint abi remains independent.

The future consumer work removes the loading-only mandatory metadata gate and
updates its existing generation-compatibility documentation/specifications.
This note does not itself alter the current ABI admission contract.

## Pending choices shared with Cozy Phase 76

- Signature format and supported verification library; OpenPGP is a candidate.
- Checksum text syntax, signature-presence discovery and release URL mapping.
- Signed namespace/key metadata, bootstrap trust and trust-root rotation.
- TUF reuse versus a smaller supported mechanism; do not build custom
  cryptographic primitives.
- Key expiry/revocation, refresh frequency and already-admitted cache behavior.
- Migration for old repository artifacts without the new checksum sidecar.
- Cache write ownership, concurrent retrieval, failure visibility and updates.
- Actual catalog/user-tool use of ABI metadata and expanded-CAR behavior.

Provisional operational suggestions are recorded as choices to evaluate, not
additional approved requirements. Detailed reasoning and primary sources are
in [Cozy's discussion notes](../../../cozy/docs/notes/car-distribution-verification.md).

## Handoff

Cozy Phase 76 supplies completed archives, checksums, optional signatures and
repository-operation preparation. CNCF Phase 104 consumes that contract through
actual trust/retrieval/cache APIs. Match the shared wire formats and fixtures,
then record each side's independent implementation and test outcomes.
