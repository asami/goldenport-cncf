# Phase 121 checklist — Firebase notification delivery and ingress attestation

status=planned

Owner: CNCF. Plan: [Phase 121](phase-121.md).

## Stage: 121.1 Notification delivery

Stage Status:
- Current status: OPEN
- Owner: CNCF
- Update rule: update from observed results for the P121-01 checklist below.

- [ ] P121-01a: A registered test installation receives a minimal prompt for its authenticated account, and opening it reads the authorized resource.
- [ ] P121-01b: Recipient preferences, opt-out, rotation and account switching are respected. Duplicate or missed delivery leaves one durable inbox item available through ordinary navigation.
- [ ] P121-01c: A failed FCM submission retains the accepted inbox item and reports the transport outcome separately.

## Stage: 121.2 Ingress attestation

Stage Status:
- Current status: OPEN
- Owner: CNCF
- Update rule: update from observed results for the P121-02 checklist below.

- [ ] P121-02a: Observation on an isolated endpoint records verification outcomes before enforcement is enabled.
- [ ] P121-02b: The enforced route accepts valid evidence and refuses missing, invalid or wrong-application evidence. A valid token with an unauthorized user/resource still receives an authorization refusal.

## Stage: 121.3 Deployment and handoff

Stage Status:
- Current status: OPEN
- Owner: CNCF
- Update rule: update from observed results for the P121-03 checklist below.

- [ ] P121-03a: An isolated deployment exercises the enabled route with host-managed credentials, destinations and effect policy. The deployment with the integration disabled supports ordinary authenticated access.
- [ ] P121-03b: The handoff records actual client delivery, qualified platforms, transport failure behavior and applicable validation and independent review.

## Phase closure

- [ ] The owning Steps are accepted or their outstanding work is explicitly dispositioned. Applicable validation, integrated review, the consumer handoff and release are recorded before marking the Phase closed.
