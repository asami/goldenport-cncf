# Per-test execution-mode producer follow-up

Status: PLANNED; implementation and acceptance pending
Decision date: 2026-11-02
Contract: [test execution-mode declarations](../spec/test-execution-mode-declarations.md)
Consumer: sm-workflow development-mode validation VM-07

This is a follow-up to closed Phase 103, coordinated with its existing standard-
annotation migration. Preserve its accepted history and ADMISSION/Slow/
DoNotDiscover direction. Phase 107 consumes the mode behavior/authorization
coverage; do not require all of Phase 107 before the declaration handoff.

- [ ] TM-01: freeze the typed mode-set field, class/per-test resolution,
  annotation/CML mapping, supported ABI and required-semantics handling.
  Source declarations are authoritative; the build and consumer do not duplicate them.
- [ ] TM-02: extend normalized discovery and exact binding with mode variants;
  preserve feature, duration, prerequisites, logical references and provenance.
- [ ] TM-03: connect one harmless real test through the shared SBT/build.sbt
  integration and sm-workflow VM-07, with actual child-mode propagation and
  per-test/mode results; then cover mixed-mode suites and forked execution.
- [ ] TM-04: qualify TM-A01..05 and hand off concrete supported build wiring,
  consumer revision and remaining limitations. Unknown/unsupported declarations,
  missing setup and failed variants cannot silently reduce required coverage.

Use one connected implementation/specification batch and integrated review.
Existing runtime owns commands, process limits, serialization and result capture.
No independent scheduler, suite registry, global property mutation workaround,
consumer source reflection or per-Operation comparison parameter is introduced.
