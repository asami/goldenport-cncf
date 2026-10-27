# Phase 117 Checklist

status=planned
plan=[Phase 117](phase-117.md)
All items are OPEN; P117 IDs are the producer completion ledger.

- [ ] P117-01 (C1): Define typed build/test/analyze requests, DSL signatures, attempts/results and diagnostics; align with sm-workflow Phase 18 W1 without moving common Workflow policy into CNCF.
- [ ] P117-02 (C1): Select the real qualification project, SDK and build target/mode; distinguish missing environment from product validation failure.
- [ ] P117-03 (C2): Connect all three DSLs to UoW and existing managed execution; intent construction performs no external effect and no arbitrary shell input is introduced.
- [ ] P117-04 (C2/C3): Preserve actual success, nonzero exit, pre-start rejection, timeout, cancellation and unconfirmed termination with retained evidence.
- [ ] P117-05 (C3): Test results retain selection coverage, counts, failures, zero/all-skipped cases, missing terminal events and late errors without invented success.
- [ ] P117-06 (C3): Analysis retains diagnostics/configuration; build results identify selected target/mode and actual artifacts, rejecting stale artifacts as proof of a successful build.
- [ ] P117-07 (C4): Executable Specifications and required repository checks pass; existing SBT DSL/results/selection and shared process behavior retain their meaning.
- [ ] P117-08 (C4): Real Flutter analyze/test/build execute on the selected project/SDK/target, with honest coverage and failure-path evidence distinct from fixture results.
- [ ] P117-09 (C4): Independent integrated review closes required findings; preserve actual prior failures and applicable verification.
- [ ] P117-10 (C5): Return the usable revision/artifact, usage examples, environment requirements, validation/review references and limitations to sm-workflow Phase 18 through the normal authorized delivery route.

Record actual revision and evidence when closing items. Producer completion is
separate from consumer Phase acceptance; no implementation or test result is
claimed by this planning update.
