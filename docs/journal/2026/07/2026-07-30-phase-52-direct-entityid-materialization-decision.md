# Phase 52 - Direct EntityId Materialization Decision

Status: accepted Phase 52 design clarification

## Decision

`EntityId` materializes its default timestamp and entropy when its public
constructor is called. The timestamp is the current instant normalized to the
canonical millisecond domain; entropy is a newly generated delimiter-safe
opaque token. These defaults make direct construction unique and preserve the
same identity through `copy`, canonical String parsing, JSON, and Record
boundaries.

## Context

Runtime creation paths use their injected `IdGenerationContext` before they
construct an `EntityId`. The public model constructor nevertheless retains its
materializing defaults for direct callers and source compatibility. Replacing
them with `StableTimestamp` and `StableEntropy` makes distinct direct calls
collide; requiring all public callers to provide fields changes the established
constructor contract.

The normative Phase 52 EID-02 note states that default generation materializes
timestamp and entropy at construction and normalizes the current instant.
Structured Record decoding remains parsing, not generation: it requires
explicit canonical timestamp and entropy and never substitutes either value.

## Evidence

`EntityIdSpec` captures the normalized current-time interval around direct
construction, proves distinct generated entropy and unequal repeated IDs, and
retains copy/transport round-trip assertions.
