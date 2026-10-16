# Declared native Decision completion Operation

The sm-workflow Phase 2 / CNCF Phase 88 producer slice adds
`WorkflowDecisionCompletionServiceOperation`, using only opaque managed
Decision publication/completion and instance bindings. It does not expose a
WorkOrder SPI, claim token, raw provider or legacy continuation/UoW fallback.
Default WorkOrder completion APIs remain unchanged.

An application codec and pure closing selector are bound to an explicitly
declared Provided Operation. Public results must match its Component,
Workflow/version/result type and exact issued Decision completion Operation.
Structural admission runs before selector invocation. The adjacent instance
and unchanged base are checked in full canonical storage form; the existing
managed claim and atomic native transition remain authoritative. Response
projection runs after completion commit and cannot undo or replay its result.
This machinery supplies no human verification, consent or command permission.

A new actual Service executable scenario runs with normal response and with
post-commit response projection rejection. It covers malformed, stale,
foreign-owner and AI-dispatch refusal before selection/interpretation, exact
answer/closing retention, replay refusal, shared single pool and returned
leases. Existing native Decision reopen and owned-claim release examples are
included in the accumulator. Human evidence is explicitly a fixture.

Invocation 595 passed all 131 tests in nine suites, zero failed/aborted/
canceled/ignored/pending. Actual generation/provenance and compilation ran.
SBT and wrapper exits 0, shared serial lock released; saved summary and the
new scenario inspected. Diff whitespace check clean. Parent direct execution
honors the user's limited recovery while retaining scoped escalation and the
canonical serial wrapper. No agent/commit/push/publication or artifact refresh.

Log: `/tmp/skill.cncf.d/a-fb09a195-2d98-44b8-9256-846469bb0989/7267-20261001T113725Z.log`.
Summary: `/tmp/skill.cncf.d/a-fb09a195-2d98-44b8-9256-846469bb0989/7267-20261001T113725Z.summary.json`.

Next is producer artifact refresh and consumer native question/answer
integration. This is not production human verification, consumer CLI/server
acceptance, full Phase validation or release.
