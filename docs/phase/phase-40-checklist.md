# Phase 40 - HTTP/Form Typed Update Commands Checklist

This checklist is the authoritative Phase 40 state ledger. The summary and
closure conditions are in `phase-40.md`.

## UT-01: Contract and Ingress Audit

Status: DONE

- [x] Audit URL-encoded, multipart, automatic REST, and JSON decoding paths.
- [x] Identify where duplicate field occurrences are currently collapsed.
- [x] Freeze the provisional command grammar and metadata requirements.
- [x] Reconcile the Jul. 19 handoff with the historical April grammar notes.

## UT-02: Directive Normalization

Status: DONE

- [x] Add a transport-neutral field-occurrence/update-directive model.
- [x] Group plain assignment, value-bearing operators, and operand-less
  commands by selected operation parameter.
- [x] Reject duplicate commands and conflicts independent of request order.
- [x] Keep internal Record sentinel values outside the public interface.

## UT-03: Typed Update Mapping

Status: IN_PROGRESS

- [ ] Populate source multiplicity and null support in generated
  `CmlOperationField.update` metadata and resolve it at runtime.
- [x] Map repeated `clear` to `Update.set(empty typed collection)`.
- [x] Map compatible scalar `null` to `Update.setNull`.
- [x] Reject incompatible commands before ActionCall execution.

## UT-04: Shared Transport Integration

Status: DONE

- [x] Connect directive normalization to the shared
  `ComponentLogic.makeOperationRequest(Request)` boundary.
- [x] Preserve Form/property and REST/argument carrier positions while using
  one conflict validator and typed mapper.

- [x] Integrate URL-encoded Form input through the shared normalizer.
- [x] Integrate multipart form fields through the shared normalizer.
- [x] Integrate automatic REST/query-style operation input where supported.
- [x] Integrate JSON Record input without reinterpreting ordinary arrays as
  operand-less commands.
- [x] Project compatible commands through Form definition/help/schema metadata.
- [x] Render generated update controls that submit the canonical command
  carrier without a dummy value.

## UT-05: Executable CNCF Evidence

Status: IN_PROGRESS

- [x] Cover repeated powertype clear and optional scalar null assignment at
  the CNCF typed-mapping boundary.
- [x] Cover omitted and blank Form values as unchanged/no-op behavior.
- [ ] Cover overwrite, prepend, append, and remove regression behavior.
- [x] Cover duplicate, unknown, incompatible, and conflicting directives as
  structured HTTP 400 failures.
- [x] Prove equivalent transport semantics and order-independent validation.
- [x] Prove generated forms expose only type-compatible commands and submit the
  same grammar accepted by automatic REST.
- [ ] Prove authorization, validation observability, and persistence still use
  the normal ActionCall path.

## UT-06: ArtScene Driver Validation

Status: OPEN

- [ ] Clear `Facility.fetch_methods` through generated automatic REST.
- [ ] Confirm generated read/search no longer returns the stored override.
- [ ] Confirm ArtScene restores its default fetch policy without a bespoke
  operation.

## UT-07: Canonical Documentation Promotion

Status: OPEN

- [ ] Compare implemented behavior with the provisional note and historical
  grammar proposals.
- [ ] Promote confirmed parameter names, type rules, conflicts, transport
  behavior, and error semantics to `docs/design`.
- [ ] Promote the static parameter contract and executable-spec references to
  `docs/spec`.
- [ ] Annotate historical journal/notes where their proposal differs from the
  confirmed contract; do not rewrite historical text.

## UT-08: Verification and Closure

Status: OPEN

- [ ] Run focused HTTP/Form, request-binding, generated update, authorization,
  and observability specifications.
- [ ] Run `sbt --batch Test/compile` and the full CNCF test suite.
- [ ] Run the maintained ArtScene integration smoke.
- [ ] Run scoped review and resolve all actionable findings.
- [ ] Update strategy/phase closure evidence and close Phase 40.
