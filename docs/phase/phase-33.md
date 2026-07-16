# Phase 33 - Resource Reference DSL and URN Provider Resolution

Stage Status:
- Current status: OPEN
- Owner: Phase 33 Resource Reference DSL and URN Provider Resolution
- Update rule: Update this block and `phase-33-checklist.md` whenever a stable
  work-item state changes. Close the phase only when every in-scope checklist
  item is complete or explicitly relocated.

status = open

## 1. Purpose

Phase 33 implements strategy item `9.28 Resource Reference DSL and URN
Provider Resolution`. It establishes read-only resource access as an
ExecutionContext internal DSL and makes provider selection, URL policy, and
URN resolution execution configuration concerns.

The immediate consumer is SIE BoK metadata and source reading. Components must
not directly use host filesystem or network APIs for this concern.

## 2. Scope

- Define `ResourceReference` for absolute URL and URN references.
- Add a read-only `ExecutionContext.resources` internal DSL.
- Add URL-scheme resource provider SPI and policy-constrained URL resolution.
- Add the standard `urn:textus:<namespace>:<resource-id>` grammar and
  `TextusUrnResourceProvider` SPI.
- Add generic `UrnResourceProvider` SPI for explicitly configured
  `urn:<nid>:<nss>` extension points.
- Bind `urn:textus` namespaces and provider settings through execution
  configuration.
- Provide in-memory and policy-constrained file-provider test coverage.
- Migrate SIE BoK metadata/source readers as the consumer proof.

## 3. Boundaries

- `urn:textus` is the sole standard URN NID in this phase.
- Generic URN SPI support does not make arbitrary NIDs available by default.
- `urn:textus` is resolved only by the dedicated standard SPI; a generic
  provider cannot override it.
- URL access is permitted only through configured scheme policy. `file:` is
  limited to configured read-only roots and HTTPS is limited to configured
  hosts.
- This phase exposes no write, delete, list, repository-management, caching,
  credential, or synchronization API.
- Components use the internal DSL, not provider SPIs or direct Java NIO/network
  APIs.

## 4. Active Work Stack

- A (PLANNED): RR-01 - Freeze the resource-reference model and DSL contract.
- B (PLANNED): RR-02 - Implement URL provider resolution and policy binding.
- C (PLANNED): RR-03 - Implement standard `urn:textus` namespace resolution.
- D (PLANNED): RR-04 - Implement generic external-URN SPI resolution.
- E (PLANNED): RR-05 - Add deterministic test providers and executable specs.
- F (PLANNED): RR-06 - Migrate SIE BoK readers and verify the cross-repository
  consumer path.
- G (PLANNED): RR-07 - Review, document, and close Phase 33.

## 5. Development Items

- [ ] RR-01: Freeze the resource-reference model and internal DSL contract.
- [ ] RR-02: Implement URL provider resolution and deny-by-default policy.
- [ ] RR-03: Implement `urn:textus` namespace resolution through configuration.
- [ ] RR-04: Implement the generic `urn:<nid>:<nss>` extension SPI.
- [ ] RR-05: Add deterministic providers and executable specifications.
- [ ] RR-06: Migrate and verify the SIE BoK reader consumer path.
- [ ] RR-07: Complete review, documentation, verification, and closure.

Detailed task tracking and acceptance evidence are in
`phase-33-checklist.md`.

## 6. Completion Conditions

Phase 33 closes only when:

- components can read configured resources only through the ExecutionContext
  internal DSL;
- `urn:textus:<namespace>:<resource-id>` resolves through a configured
  read-only provider without exposing its physical location to component code;
- the generic URN SPI routes an explicitly configured non-Textus test NID,
  while unconfigured NIDs are rejected;
- URL policy rejects unconfigured schemes, hosts, and file roots;
- in-memory providers make consumer tests independent of host files and
  external services;
- SIE BoK metadata/source reading no longer directly calls `Files.readString`;
- every checklist item is complete or explicitly relocated with recorded
  evidence.
