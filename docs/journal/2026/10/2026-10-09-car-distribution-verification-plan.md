# CAR Distribution Verification Consumer Planning

Date: 2026-10-09.
Work: documentation-only consumer planning in a side conversation.
Outcome: created CNCF Phase 104, checklist, notes and this journal.

## Human direction

The human adopted checksum verification after a CAR finishes downloading into
temporary storage, optional signature verification, then registration of the
same accepted bytes into local cache. Unsigned CARs remain accepted after
checksum verification. Signed CARs require a trusted, authorized publisher key.

The repository distributes publisher keys through authenticated key information
derived from a separately registered repository trust root. Signature format,
key metadata format and operational update/revocation details remain planning
choices. No public-signature requirement was adopted.

The human then requested Phase, notes and journal in CNCF as well as Cozy.
This consumer plan is the response to that additional documentation request.

## Responsibility split

[Cozy Phase 76](../../../../../cozy/docs/phase/phase-76.md) owns completed
distribution artifacts and the coordinated repository-operation plan.
[CNCF Phase 104](../../../phase/phase-104.md) owns trust configuration,
download verification, usable-cache admission and the runtime/metadata boundary.

The shared behavior authority remains
[Cozy's CAR distribution specification](../../../../../cozy/docs/spec/car-distribution-verification.md).
CNCF does not introduce a second, independently editable checksum/signature
wire contract. Its [notes](../../../notes/car-distribution-verification.md)
record concrete consumer entry points and outstanding design choices.

## Current observations and future transition

Read-only inspection found a canonical fetch path that downloads into a
temporary file, moves it to cache, then checks descriptor identity. Verification
will need to precede successful cache publication. Error collapsing into None
also needs a bounded reconciliation so failed verification cannot masquerade
as ordinary absence.

Current generation-compatibility documents still require ABI metadata
presence/simple checks. The future plan preserves ABI metadata and Cozy ABI
lint, while moving validation to actual metadata producers/consumers and
removing the loading-only mandatory gate. That current contract is unchanged
by writing the plan.

## Preserved boundaries

Only new planning documents were added. Existing source changes, Phase and Goal
state, rejection records, ledgers, reviews and uncommitted work were preserved.
No source edit, SBT invocation, key generation, publication, deployment,
staging, commit or push was performed.

[Phase 104 checklist](../../../phase/phase-104-checklist.md) keeps every
implementation and acceptance item OPEN. No scheduling priority or active Goal
was selected. This record does not resume or complete inherited main-thread work.
