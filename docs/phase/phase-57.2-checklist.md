# Phase 57.2 Checklist - Explicit Async Migration and Projection Alignment

status=planned
phase=[Phase 57.2 - Explicit Async Migration and Projection Alignment](phase-57.2.md)
predecessor=[Phase 57.1](phase-57.1.md)
successor=[Phase 57.3](phase-57.3.md)

## AES-04: Explicit Asynchronous Migration

Stage Status:
- Current status: PLANNED
- Entry rule: Phase 57.1 is DONE.
- Completion rule: Every inventoried Job-dependent caller declares explicit
  intent and retains its required lifecycle.

- [ ] Migrate Job-ID, await, persistence, continuation, and control consumers.
- [ ] Reject ambiguous or unsupported intent deterministically.
- [ ] Preserve authorization, transaction, UnitOfWork, and context boundaries.

## AES-05: Transport and Projection Alignment

Stage Status:
- Current status: PLANNED
- Entry rule: AES-04 is DONE.
- Completion rule: Every public projection reports the admitted execution
  mode without heuristic inference.

- [ ] Align Request, HTTP/Form, Help, Admin, diagnostics, metrics, and CallTree.
- [ ] Preserve exact direct responses and explicit Job metadata.
- [ ] Run focused and representative integration once.
- [ ] Review once and commit the accepted Phase.
