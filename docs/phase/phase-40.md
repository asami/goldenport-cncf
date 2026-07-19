# Phase 40 - HTTP/Form Typed Update Commands

Stage Status:
- Current status: IN_PROGRESS
- Current step: UT-05 executable CNCF evidence; generated binder integration remains in UT-03.
- Owner: CNCF HTTP/Form ingress and generated operation binding.
- Update rule: Update this block and `phase-40-checklist.md` whenever a stable
  work-item state changes. Close only after every checklist item is checked and
  confirmed parameter behavior is promoted to design/spec documents.

status = in_progress

## 1. Purpose

Phase 40 preserves operand-less update intent from generic HTTP/Form input
through generated typed `Update` values. It enables a repeated field to be
cleared and a compatible scalar field to be assigned null without redefining
blank Form values or adding application-specific operations.

ArtScene `Facility.fetch_methods` is the first downstream driver. The result
must remain generic CNCF request-normalization infrastructure.

## 2. Source and Planning Contract

- Handoff:
  `docs/journal/2026/07/2026-07-19-http-form-update-command-handoff.md`
- Implementation note:
  `docs/notes/http-form-typed-update-command-implementation.md`

Both are non-normative planning inputs. Implementation and executable evidence
must settle the public parameter grammar before it is promoted to canonical
design/spec documents.

## 3. Scope

- Preserve multi-value transport field occurrences until duplicate and
  conflict validation is complete.
- Parse `<field>__update_command=clear|null` as an operand-less update command.
- Retain current plain assignment and value-bearing overwrite, prepend,
  append, and remove behavior.
- Validate command compatibility against selected operation parameter metadata.
- Map repeated clear to `Update.set(empty typed collection)`.
- Map compatible scalar null to `Update.setNull`.
- Apply one grammar across URL-encoded Form, multipart fields, automatic REST,
  and JSON Record input.
- Project compatible operand-less commands into generated Form definitions and
  controls without dummy values.
- Return structured HTTP 400 failures for invalid directives.
- Validate ArtScene generated automatic REST without a bespoke operation.
- Promote the verified parameter contract to design/spec before closure.

## 4. Boundaries

- Blank Form input keeps its existing absent/no-op interpretation.
- Direct Scala callers continue to use typed `Update` values.
- Request normalization does not bypass ActionCall authorization,
  observability, validation, or persistence boundaries.
- Internal Record command/sentinel details are not public parameter syntax.
- No ArtScene-specific field, datatype, operation, or compatibility rule enters
  CNCF.
- Historical grammar proposals remain non-normative until reconciled with
  executable behavior.

## 5. Work Stack

- A (DONE): UT-01 - Audit all transport decoding and operation-binding paths;
  freeze the provisional grammar and metadata requirements.
- B (DONE): UT-02 - Implement transport-neutral directive grouping, duplicate
  preservation, and conflict validation.
- C (IN_PROGRESS): UT-03 - CNCF metadata contract, compatibility checks,
  typed clear/null mapper, and runtime projection are implemented; generator
  emission and generated null binding remain to be connected.
- D (DONE): UT-04 - The shared `ComponentLogic` request boundary,
  URL-encoded/multipart Form adaptation, REST/JSON carrier normalization,
  metadata projections, and generated Form controls are implemented.
- E (IN_PROGRESS): UT-05 - Structured HTTP 400 and transport-equivalence
  evidence is implemented; regression, authorization, and
  observability executable evidence.
- F (OPEN): UT-06 - Validate the ArtScene generated-REST clear scenario.
- G (OPEN): UT-07 - Promote confirmed parameter behavior to design/spec and
  annotate superseded historical proposals.
- H (OPEN): UT-08 - Run full validation, review, and close Phase 40.

## 6. Completion Conditions

Phase 40 closes only when:

- repeated clear reaches generated code as a typed empty collection update;
- compatible scalar null reaches generated code as `Update.setNull`;
- absent and blank Form values retain existing no-op behavior;
- value-bearing update operators remain compatible;
- invalid and conflicting directives return deterministic structured HTTP 400;
- all supported transports use one effective grammar;
- generated update forms can submit compatible clear/null commands without
  application-local controls or dummy values;
- normal ActionCall authorization and observability remain active;
- ArtScene clears `Facility.fetch_methods` through generated automatic REST;
- implementation-confirmed parameter behavior is documented under
  `docs/design` and `docs/spec`; and
- focused/full validation and scoped review have no actionable finding.

## 7. Deferred Scope

- General JSON Patch or JSON Merge Patch support.
- Arbitrary update expression languages.
- Client-side dynamic form editors beyond emitting the confirmed fields.
- Record-library redesign unrelated to the shared CNCF ingress requirement.
- Application-specific migration of stored values.
