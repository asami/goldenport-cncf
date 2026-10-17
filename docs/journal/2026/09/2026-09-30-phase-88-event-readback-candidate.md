# Phase 88 S11 authoritative event readback

Date: 2026-09-30
Status: accepted for P2-U14; P2-U05 remains open

The accepted S10 batch inserted already-issued EventRecords in the guarded
Workflow transaction, but its public success projection did not itself verify
each inserted event. S11 retains the canonical Create bytes at
preparation and, only after a Transitioned provider acknowledgement, loads
every event identity from that same borrowed datastore. Missing or divergent
`event_json` is transaction-indeterminate before the Workflow projection runs.
It does not infer rollback or safe replay after a committed write.

The Executable Spec adds memory fault injection for missing and divergent
post-commit readback, asserts no precommit read and no successful projection,
and retains the existing memory/SQLite success and collision examples. The
changed code is in CNCF WorkflowPreparedDataStoreTransition.scala and
WorkflowPreparedDataStoreTransitionSpec.scala. Their SHA-256 hashes at
acceptance were respectively
`805773bb7a4100001ec5180b963fbfe1815d4d029165ab9e8accb28f1621856e`
and `5b499973b8e613b5deda7cb1bd09247ad9a4ee29cf8edb3071f702b8af0e0a55`.

The focused SBT attempt `SMWF-P2-CNCF-P88-S11-FOCUSED-20260930-281`
passed 45/45 tests in three suites. Its accepted command-execution receipt is
`/tmp/skill.cncf.d/cncf-command-execution-fd13a6a5d85e6871057b4d43f65b7ace9ede9a74ce11e35e4381533f99c63216-5c042f745f194d92d8860d6e7193717d/command-execution-sha256-07852fbe9e372490d018ab9173fd6545a8bc6f53cfbe166a2a1e0ed04840af3a`.
The related regression attempt `SMWF-P2-CNCF-P88-S11-REGRESSION-20260930-282`
passed 1191 tests in 143 suites with zero failures or aborts; eight existing
opt-in MySQL cases were canceled and nine placeholders remained pending. Its
accepted receipt is
`/tmp/skill.cncf.d/cncf-command-execution-9420e7b9a45b003ea6b372b8c6212cf20e0648579682088b5e470ed408894dd9-72853b06dd78207ec4e887b96d1dc29f/command-execution-sha256-4e910e59ee155275b60861d6bb85f2080b3989313ad96603570939b6050f0ca1`.
Both receipts report successful SBT and wrapper exit, released serialization
lock, and the same complete pre-documentation tree SHA-256
`99047c6527bbcfd4c8369ee2b0b33f9e46892adeb6f63a281f5b8ee9dd65cdf9`.
Only documentation was edited after these receipts; the code and test hashes
above are unchanged. This closes P2-U14, not the broader producer requirement.

The current conditional plan guards the two Workflow records and admits
Create-only side effects. A global EventStore sequence cursor would be a
third independently guarded record; the plan cannot claim its atomicity by
merely attaching another Create. A later producer slice must add and prove a
canonical multi-guard transaction capability, then sequence/dedup and active
UnitOfWork enlistment before P2-U05 can close. No artifact refresh, commit or
publication was performed here.
