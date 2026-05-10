# Error Detail Code Policy

This document is the Phase 23 EM-03 normative policy for CNCF-visible
`DetailCode`.

## Role

`DetailCode` is a numeric `Long` semantic error detail code derived from
structured `Conclusion` data. It is displayed beside the Web/API status code so
users, operators, and AI-assisted diagnostics can correlate a compact number
with the observed failure shape and likely response.

`DetailCode` is not an HTTP status code, CLI exit code, human-readable message,
or stored string label.

## Canonical Source

The canonical error facts remain `Conclusion`:

- `Observation.taxonomy.category`
- `Observation.taxonomy.symptom`
- `Observation.cause.kind`
- `Interpretation.kind`
- `Disposition.userAction`
- `Disposition.responsibility`

Readable paths, labels, and explanations are projection-only. They are derived
from `Conclusion` and the catalog at display time and must not become separate
authoritative state.

## Numeric Generation

EM-03 uses a fixed two-digit slot layout:

```text
category symptom cause interpretation userAction responsibility
CC       SS      AA    II             UU         RR
```

The numeric value is:

```text
CC * 10^10 + SS * 10^8 + AA * 10^6 + II * 10^4 + UU * 10^2 + RR
```

Rules:

- values come from the EM-02 canonical catalog;
- missing cause is encoded as `unknown` (`99`);
- missing user action or responsibility is encoded as `00`;
- messages, exception text, field values, parameter names, and free-form labels
  never participate in generation;
- descriptor/facet data remains diagnostic projection data in EM-03 and is not
  encoded into the numeric code.

## Status Model

`Conclusion.Status` carries generated `webCode` plus generated numeric
`DetailCode`. `Conclusion` materialization generates both deterministically
from the full `Conclusion`; projections read the stored `Status.webCode` and
`Status.detailCode`.

Explicit `webCode` or `DetailCode` overrides are not allowed in the materialized
status. Applications that need local error identifiers may attach `appCode` and
`appStatus`; these do not override `webCode` or `DetailCode`.

`Status.strategies` is removed. Reaction and handling guidance belong to
`Disposition`; runtime retry or compensation policies belong to Job/Event/
Operation runtime policy, not to the error model.

## Projection

Web/API structured error projections expose:

- `status`: numeric Web/API status;
- `statusText`: protocol status reason phrase such as `Bad Request`;
- `detailCode`: numeric semantic detail code stored in `Conclusion.Status` when
  a `Conclusion` is available;
- `appCode`: optional application-defined numeric code;
- `appStatus`: optional application-defined status label.

Response error records and error envelopes must carry the same `detailCode`.
Renderers and protocol adapters read `Conclusion.Status.detailCode`; they do not
derive a second response-local code.

Legacy/message-only errors without a `Conclusion` do not carry `detailCode`.
`codeSource`, `http.xxx`, and string detail-code lists are not part of the
active EM-05 projection contract.

Observability, runtime dashboards, and admin diagnostics project the same
structured error axes: taxonomy category/symptom, cause kind, interpretation,
disposition, Web/API status, status text, numeric detail code, optional
application code/status metadata, and structured facets. Dashboard grouping
keys are derived summaries and are not semantic error identifiers.

## Compatibility

CNCF is pre-stable. Phase 23 may renumber or adjust detail-code generation while
the error model converges. Completed slices must still be deterministic and
tested.
