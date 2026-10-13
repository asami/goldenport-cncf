# EventStore ordinary-send boundary follow-up

Date: 2026-09-30
Status: two additional executable examples; not compiled or executed

## Work completed

The source inspection followed the corrected EventStore-owned kind policy from
ordinary EventEngine.emit to configured EventStore.append. No sender persistence
argument was introduced and no production managed provider was activated.

EventStorePersistenceSelectionSpec now contains eight examples (six from the
previous correction and two in this continuation). The additional examples cover:

- Transient and durable records sharing an identity across separate sends in
  either arrival order. Excluded records do not populate retained deduplication;
  later excluded sends must not retrieve a stored durable record instead.
- Repeated ordinary durable sends retain the first content and assigned sequence.
  A duplicate must not allocate a second sequence; the next distinct durable event
  receives the next sequence and the input records remain unissued.

These are candidate assertions, not test results. Whitespace checks on the
tracked diff and the new spec reported no diagnostics. No implementation change
or acceptance of managed persistence/dispatch is claimed by this continuation.

## Execution recovery inspection

The complete installed cncf-command-execution skill and its platform-review-timeout
and sbt-runner-history contracts were reread. The retained native runner history
for /root/sbt_receipt_56611006b81d2d2beaae was inspected without messaging or
restarting that runner. Its previous registered role was cncf_command_runner,
configured gpt-5.6-luna / medium; no agent was started or resumed in this turn.

The runner's renderer accepted the exact Cozy command
["--batch", "compile", "cozyExportRuntimeClasspath"] with the installed
execute-command-with-receipt.py and require_escalated. The terminal runner report
was EXECUTION_PERMISSION_DENIED_AFTER_ATTRIBUTABLE_AUTHORITY, identifying platform
rejection as unrelated to EventStore work, before SBT or lock acquisition.
The retrieved native history did not expose a separate full raw platform-reason
payload or a native permitted recovery/approval override. It does not establish a
review timeout. No recovery permission is inferred from this continuation's
"go ahead".

A provenance concern is visible in the retained rendered justification: its
immutable originating reference still describes earlier failed validation 230,
whereas the current attempt purpose describes complete-readback validation.
This discrepancy is not proof of the denial's cause. It is recorded for execution
contract diagnosis only; the existing denied request and its immutable authority
were not rewritten, rerendered, or redispatched.

A same-request retry is allowed only for the contract's proven pre-start review
timeout with explicit platform permission. That condition is absent here.
Contract provenance repair alone cannot override a substantive denial. A native
approval override or another explicit platform-supported permitted recovery for
the denied action is required before it may execute again; none was observed.
No alternate runner, fresh attempt, parent SBT, manual-terminal instruction,
cache substitution or generation-bypass command was used.

## Resume boundary

After an actual platform-permitted recovery, the already-selected Cozy generator
prerequisite must complete through the approved serialized command route, followed
by fresh validation on the then-settled candidate including all eight EventStore
selection examples and the seven multi-guard readback examples. Historical
focused-301 is not validation of this changed source. S12, P2-U15, P2-U16 and
P2-U05 remain open. No commit or publication was performed.
