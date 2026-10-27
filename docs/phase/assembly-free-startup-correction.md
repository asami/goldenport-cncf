# Assembly-free startup correction

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime/configuration development
- Contract: [assembly-free standalone startup](../spec/assembly-free-standalone-startup.md)
- Consumer: sm-workflow installed startup; CBD Phase 12
- Allocation: bounded correction of the Phase 53/55 startup boundary; no reopening of their accepted work or new numbered Phase
- Handoff: [2026-10-27](../journal/2026/10/2026-10-27-assembly-free-startup-handoff.md)

## Main route and acceptance checklist

- [ ] AF-C1: Reconcile current source and the selected installed artifact. Trace
  startup mode, profile admission, effective security wiring, subsystem identity
  and storage selection. Preserve concurrent edits and existing acceptance.
- [ ] AF-C2: Connect standalone intent -> admitted user identity -> effective
  fixed-user context -> normal Operation startup without a consumer-authored
  assembly. Cover the preflight localSubject gate and final Subsystem gate
  together; reuse typed resolution and existing runtime identity mechanisms.
- [ ] AF-C3: Qualify missing/conflicting identity diagnostics, user/role/capability
  equivalence, disabled fallback, multi-user and controlled-test separation, and
  intentionally selected assembly validation. Do not add unrelated guard layers.
- [ ] AF-C4: Deliver affected focused executable specifications and actual artifact
  identity to sm-workflow. Reuse unaffected results; run broader checks only for
  actual changed-scope requirements. Coordinate normal runtime-owned local
  publication when needed; a new runner outside sm-workflow is not the solution.
- [ ] AF-C5: Consume sm-workflow's isolated installed qualification and supported
  same-Session adoption evidence, retaining user/subsystem/storage identity,
  history, accepted work, repair counts and previous successful checks.

AF-C1/C2 establish the main route before broad configuration cleanup. AF-C3 and
the necessary focused verification precede consumer adoption. AF-C5 closes this
correction only, not a consumer Phase. Missing delivery does not stop CBD's
already working temporary route or unrelated CNCF work.

No live Session changes, publication, tests or implementation are claimed by
this plan. Pending criteria remain pending until actual evidence is recorded.
