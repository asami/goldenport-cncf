# Error Taxonomy Catalog

status = normative
phase = 23
slice = EM-02

## 1. Purpose

This document records the EM-02 canonical Error Model vocabulary. It
supersedes the temporary `org.goldenport.provisional.*` error-model packages.

Formal runtime locations:

- `org.goldenport.observation`: `Observation`, `Taxonomy`, `Cause`, and
  observation-side axes.
- `org.goldenport.conclusion`: `Interpretation` and `Disposition`.
- `org.goldenport.Conclusion`: the public aggregate failure semantic record.

Compatibility aliases for `org.goldenport.provisional.*` are not provided
before CNCF stable.

## 2. Taxonomy Category

Canonical `Taxonomy.Category` order and numbers:

1. `argument`
2. `property`
3. `value`
4. `record`
5. `entity`
6. `resource`
7. `reference`
8. `state`
9. `security`
10. `operation`
11. `service`
12. `service-provider`
13. `component`
14. `subsystem`
15. `system`
16. `configuration`
17. `datastore`
18. `network`
99. `out-of-control`

EM-02 fixes prior pre-stable spelling/rendering defects:

- `record` is no longer rendered as `value`.
- `network` is no longer rendered as `operation`.
- `subsystem` is no longer rendered as `subsytem`.

## 3. Taxonomy Symptom

Canonical `Taxonomy.Symptom` order and numbers:

1. `syntax-error`
2. `format-error`
3. `missing`
4. `redundant`
5. `unexpected`
6. `unsupported`
7. `domain-value`
8. `invalid`
9. `illegal`
10. `not-found`
11. `unavailable`
12. `conflict`
13. `invalid-reference`
14. `duplicate`
15. `authentication-required`
16. `permission-denied`
17. `corrupted`
90. `null-pointer`
91. `unreachable-reached`
92. `uninitialized-state`
93. `invariant-violation`
94. `precondition-violation`
95. `postcondition-violation`
96. `not-implemented`
97. `impossible-state`

`domain-value` is retained as reserved vocabulary, but new validation paths
should generally use `invalid` plus structured `Cause` and `Descriptor.Facet`
data.

## 4. Cause Kind

Canonical `Cause.Kind` order and numbers:

1. `format`
2. `limit`
3. `policy`
4. `capability`
5. `permission`
6. `guard`
7. `relation`
8. `conflict`
9. `inconsistency`
10. `exhaustion`
11. `timeout`
12. `corruption`
99. `unknown`

`Cause.Kind` is the coarse mechanism classification. Machine-readable
specifics belong in `Descriptor.Facet`, not in message text.

## 5. Interpretation and Disposition

Canonical `Interpretation.Kind` order and numbers:

1. `success`
2. `domain-failure`
3. `defect`
4. `configuration-failure`
5. `system-failure`
6. `network-failure`
7. `external-service-failure`

Canonical `Disposition.UserAction` order and numbers:

1. `fix-input`
2. `retry-now`
3. `retry-later`
4. `escalation`

Canonical `Disposition.Responsibility` order and numbers:

1. `user`
2. `application-admin`
3. `system-admin`
4. `developer`

## 6. Status, Detail Codes, and Strategies

`Conclusion.Status` remains the status carrier with:

- `webCode`
- `detailCodes`
- `strategies`

EM-02 inventories these fields but does not define deterministic detail-code
generation. Detail-code generation and projection policy are owned by EM-03.
