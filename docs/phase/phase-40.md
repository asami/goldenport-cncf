# Phase 40 - HTTP/Form Typed Update Commands

Stage Status:
- Current status: CLOSED
- Current step: Complete
- Owner: CNCF HTTP/Form ingress and generated operation binding.
- Update rule: Update this block and `phase-40-checklist.md` whenever a stable
  work-item state changes. Close only after every checklist item is checked and
  confirmed parameter behavior is promoted to design/spec documents.

status = closed

Post-close note (July 19, 2026): the typed update boundary was extended with
the explicit `__value` carrier and metadata-compatible `__value_or_clear` /
`__value_or_null` adaptive carriers. This does not reopen Phase 40.

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
- C (DONE): UT-03 - CNCF metadata contract, compatibility checks, typed
  clear/null mapper, generator emission, and generated null binding are
  implemented.
- D (DONE): UT-04 - The shared `ComponentLogic` request boundary,
  URL-encoded/multipart Form adaptation, REST/JSON carrier normalization,
  metadata projections, and generated Form controls are implemented.
- E (DONE): UT-05 - Structured HTTP 400, transport-equivalence, regression,
  authorization, observability, generated binding, and persistence evidence.
- F (DONE): UT-06 - ArtScene generated-REST clear, generated search read-back,
  stored empty collection, and default policy restoration are verified.
- G (DONE): UT-07 - Confirmed parameter behavior is promoted to design/spec;
  historical proposals remain explicitly non-normative.
- H (DONE): UT-08 - Full validation and scoped review are complete; Phase 40
  is closed.

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

## 8. Completion Evidence

- CNCF focused typed-update specs: 23 succeeded, 0 failed.
- Static Form update-focused specs: 18 succeeded, 0 failed.
- CNCF full suite: 2023 succeeded, 0 failed, 1 canceled, 1 ignored, and 59
  pending.
- simple-modeler full suite: 36 succeeded, 0 failed; its updated snapshot was
  published locally for downstream generation.
- Cozy `ModelerScalaGenerationSpec`: 27 succeeded, 0 failed.
- ArtScene regenerated and compiled 165 Scala sources, built
  `textus-art-scene-0.1.1-SNAPSHOT.car`, and passed the maintained component
  API assembly acceptance with generated clear/read-back/persistence/default
  policy evidence.
- Scoped review found no actionable naming, specification, or runtime finding.
