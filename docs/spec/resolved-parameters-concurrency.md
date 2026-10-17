# Resolved parameters under concurrent reads

Current status: accepted for CC4-S8; Phase 4 remains open

Owner: parent Phase4 workflow

Update rule: parent changes after actual baseline and independent evidence.

## Contract

Shared `ResolvedParameters` may receive overlapping configuration reads. Each
`get` must return the same immutable resolved `ConfigurationValue` and source
as a sequential read. Local entries retain precedence over inherited entries;
an inherited entry retains its original source, and a missing key returns
`None`. Observational usage tracking must not cause a configuration read to
throw.

Usage tracking records each successfully resolved key once. Non-overlapping
reads establish first-use order, including inherited entries observed through
a child. Missing keys do not add usage entries. No deterministic order is
specified for overlapping reads.

The `Vector` returned by `usedEntries` is a detached snapshot: subsequent
reads do not change an earlier returned vector.

A snapshot taken during overlapping reads is coherent: it contains unique
keys, and every entry retains an existing immutable resolved value and source.
After all successful reads have completed, usage tracking contains exactly
once every successfully resolved key. Concurrent snapshots impose no
deterministic order among overlapping calls.

## Executable specification

[ResolvedParametersConcurrencySpec](../../src/test/scala/org/goldenport/cncf/config/ResolvedParametersConcurrencySpec.scala)
documents sequential precedence, exact values and component sources, missing
keys, first-use deduplication, and detached snapshots using 50 successful
bounded generated samples.

Its concurrent regression owns one eight-worker executor and performs at most
2,000 rounds. Every round creates a new shared parameter set with 64 immutable
entries and eight tasks, each reading eight disjoint keys after a shared
barrier. Barrier waits are bounded to two seconds, future waits to five
seconds, and executor termination to five seconds after `shutdownNow` in
`finally`. Returned values and sources are compared with the known input.
Each worker also obtains a public `usedEntries` snapshot while sibling reads
may still be in progress, verifying unique keys and exact input values and
sources. After all futures have joined, the final snapshot must contain
exactly all 64 keys once. Worker exceptions propagate through the original
future failure, retaining their nested cause and stack.

## Verification and status updates

The parent workflow owns execution, diagnosis, verification, and any status
update after examining actual results. Focused post-fix verification and
independent CC4-S8 Step acceptance passed. Full Phase acceptance remains
separate. A passing diagnostic does not prove the
absence of a race. The earlier HTTP500 caller remains unconfirmed; this
shared-tracking repair does not prove that cause or claim that HTTP500 is
repaired.

The contract uses the existing configuration API and introduces no new
configuration, security, or execution mode.
