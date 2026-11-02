# Post-intrusion protection and physical control options for Textus

Date: 2026-11-02
Status: design assessment and selectable deployment options; not implemented profiles or acceptance evidence

## Decision and scope

Assume that intrusion into an employee-oriented corporate network cannot always
be prevented. Describe what remains protected after specified compromises,
what equipment and procedures provide that protection, and their residual risk
and cost. Operators should be able to choose protection appropriate to the data
and use case rather than impose the strongest physical procedure on all users.

Parent: [Textus-wide customer information disclosure response](2026-11-02-customer-information-disclosure-textus-response.md).
Related detail: [mobile incidents and SDK/media egress](2026-11-02-mobile-security-incidents-and-textus-response.md).

The scope and assumptions for the strongest physical protection option are
defined in the separate [physical-controls assessment](2026-11-02-physical-controls-protection-boundaries.md).

## Incident grounding

- [Uber, 2022](https://www.uber.com/newsroom/security-update/): a contractor's
  compromised credentials and an accepted repeated MFA prompt led to access to
  additional employee accounts and internal tools. Uber's cited update did not
  establish access to its sensitive customer databases. The lesson is to limit
  post-login movement and privilege acquisition, not to equate every internal
  compromise with a confirmed customer-data breach.
- [Microsoft / Midnight Blizzard, 2024](https://www.microsoft.com/en-us/security/blog/2024/01/25/midnight-blizzard-guidance-for-responders-on-nation-state-attack/):
  a legacy test account without MFA and elevated OAuth application permissions
  enabled access to corporate mail. Test/production trust and delegated service
  permissions matter independently of the original employee password.
- [FBI/CISA Scattered Spider advisory](https://www.ic3.gov/Media/News/2023/231116.pdf):
  employee/help-desk impersonation and MFA-related social engineering illustrate
  why account recovery and authenticator replacement are security boundaries.

[NIST SP 800-207](https://csrc.nist.gov/pubs/sp/800/207/final) does not grant
implicit trust merely from network location or enterprise ownership. Apply this
to employees, devices and service identities; internal reachability is not
authorization to data or administrative Operations.

## Define the attacker and asset before selecting controls

Distinguish network presence, a stolen employee session, control of an employee
endpoint, a stolen service credential, control of an application process, host
administrator/root access, and physical/custodian compromise. These are different
capabilities. A control effective against one must not be claimed effective
against all of them.

For each selected protection policy, record:

| Dimension | Required decision |
| --- | --- |
| Assets | Originals, derived text/thumbnails, indexes, associations, keys, logs, temporary files and backups included in the boundary |
| Assumed compromise | Which network, identities, endpoints, application processes and host privileges the attacker controls |
| Trusted remainder | Isolated device, physical room/custodians, key holders, recovery mechanisms and cryptographic assumptions that remain trusted |
| Guarantees | Confidentiality, protection from unauthorized modification/deletion, and recoverability separately; do not conflate them with uninterrupted availability |
| Release conditions | Ordinary authorization, hardware authentication, isolated workstation, physical media connection and/or multiple custodians |
| Exposure period | Protection while offline/locked versus while attached, decrypted, displayed or exported |
| Cost and usability | Equipment, administration, training, spare keys, recovery drills, search/AI limitations, delay and operator effort |
| Acceptance evidence | Demonstrated route and negative cases, operational inspection/drill scope, and unresolved assumptions |

## Options overview and separate physical assessment

Ordinary identity/resource separation remains the online baseline. Selectable
additions include hardware login authentication, physical authorization for
sensitive operations, isolated key custody, dedicated workstations, disconnected
encrypted media and multiple-custodian release.

The detailed comparison of protection, residual risk and cost, the conditional
strongest guarantee, and the limits during connection/decryption/display/export
now live in the independent [physical-controls assessment](2026-11-02-physical-controls-protection-boundaries.md).
Use that document for physical option decisions instead of duplicating its
criteria here. This journal retains intrusion scenarios and project ownership.

## Textus ownership and related plans

| Boundary | Existing reference / owner | Allocation and limit |
| --- | --- | --- |
| Common identity, Operation/Entity rights and delegated service authority | [Security architecture](../../../design/textus-security-architecture.md), [Entity authorization](../../../design/entity-authorization-model.md), [UC-01..04](../../../spec/untrusted-client-material-acceptance.md), [Phase 107](../../../phase/phase-107.md) | CNCF supplies reusable enforcement. Studio/Workbench/TKL declare resource policy; internal network or System/ServiceInternal transitions must not silently widen caller rights |
| Authentication and recovery | [User Account integration design](https://github.com/asami/textus-user-account/blob/main/docs/design/external-federation-security-integration.md), [Core client specification](https://github.com/asami/textus-flutter-core/blob/main/docs/spec/user-account-authentication-client.md), [Core integration plan](https://github.com/asami/textus-flutter-core/blob/main/docs/phase/user-account-authentication-integration-plan.md) | User Account/selected IdP owns credential/session policy; Core/Framework integrates platform UI and custody. Hardware-key, recovery and sensitive-operation modes need explicit capability/evidence mapping before being offered |
| Provider credentials | [CNCF OAuth custody](../../../spec/oauth-credential-custody.md), [EAI Phase 2](https://github.com/asami/textus-enterprise-application-integration/blob/main/docs/phase/phase-2.md) | Current CNCF POSIX adapter is owner-only plaintext and explicitly does not defend against the same OS user or privileged processes. It cannot be presented as HSM/isolated custody; stronger options require an appropriate provider and deployment |
| Storage, originals and derivatives | [TKL delivery specification](https://github.com/asami/textus-knowledge-lake/blob/main/docs/spec/tkl-managed-mobile-material-delivery.md), [TKL Phase 2](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/phase-2.md) | TKL owns logical location/access and admission; EAI owns accepted provider mechanics. Offline/key-isolated storage is an option to design and qualify, not claimed as existing Drive/mobile functionality |
| Runtime confinement and recovery | [Phase 110](../../../phase/phase-110.md), [Phase 118](../../../phase/phase-118.md), [Phase 108](../../../phase/phase-108.md), [availability architecture](../../../design/availability-architecture.md) | Restrict capabilities and external execution, contain incidents and replace runtime instances. Container isolation is not protection from a compromised host administrator; runtime replacement does not revoke stolen credentials or erase prior disclosure |
| Connected client/server acceptance | [App Phase 6.10](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/phase/phase-6.10-checklist.md), [App Phase 7](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/phase/phase-7-checklist.md), [Studio Phase 5](https://github.com/KnowledgeHubProject/nict-editing-studio/blob/codex/ess3-server-editing/docs/phase/phase-5-checklist.md) | Reuse account/resource/delegation evidence; selected physical options must expose honest unavailable/locked states. No automatic addition of hardware/offline scope to the current demo phases |

Standard organizational operations own endpoint management, official toolchains,
physical access, media custody, staff/help-desk procedures and recovery drills.
Textus integrates their chosen boundaries; it does not need to implement an EDR,
door-access system, custom cryptographic hardware or a new identity provider.

## Acceptance approach and next decisions

For an actually selected deployment option, verify internal and external ingress
preserve the same required rights; employee/admin/service roles cannot cross
their grants; delegation remains bounded; account/session/provider revocation
has stated effects; and protected operational records support containment without
copying secrets or originals into logs. Test revoked and cached credentials
under their real contracts, without inventing arbitrary workflow time limits.

Physical options additionally need bounded operational acceptance: custody and
recovery-path inspection, denied release without the required physical factors,
known exposure during unlock/use, and restoration from the designated protected
copy. Do not test destructive compromise against operational private data.

Select the assets, attacker capabilities, acceptable delay and budget before
promoting an option into normative requirements and an implementation Phase.
Some options are purely deployment/operations; others need shared producer work.
This journal changes no active Phase scope, history or acceptance state. It
adds no universal anti-tamper logic, whole-file comparisons, management hashes,
per-action human approval or claim of completed Zero Trust deployment.
