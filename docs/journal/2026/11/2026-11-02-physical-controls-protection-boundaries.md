# Protection boundaries when physical controls are combined with Textus

Date: 2026-11-02
Status: independent design assessment; selectable options, not implemented profiles

## Purpose and separation

Record how far physical controls can protect specified information, under which
assumptions, and at what cost. This is separate from the incident-driven
[post-intrusion assessment](2026-11-02-post-intrusion-protection-and-physical-control-options.md),
which retains attack scenarios and Textus ownership. It is also part of the
[Textus-wide disclosure response](2026-11-02-customer-information-disclosure-textus-response.md).

The option comparison and conditional guarantee below were moved from the
post-intrusion journal on 2026-11-02. This document is the single discussion
location for those details; the original conclusions and qualifications remain.

“Complete protection” means a defined confidentiality/integrity guarantee against
specified attacker capabilities with an explicitly trusted physical remainder.
It does not mean universal immunity to theft, disaster, custodian collusion,
hardware defects, cryptographic failure or authorized disclosure. Recoverability
and continuous availability are separately selected requirements.

## Options and limits

Costs below are qualitative relative choices, not prices or a cumulative ladder.
Options can be combined, and each addresses a different part of the threat.

| Option | Protection obtained | Residual risk / limitation | Cost and use impact |
| --- | --- | --- | --- |
| Standard identity and resource separation | Network access or one employee account does not grant other accounts' resources or management Operations | Attacker can still exercise the compromised identity's legitimate rights; privileged host compromise can bypass application checks | Low relative cost; normal online usability |
| Mandatory USB FIDO2 security key for login | Password theft alone and ordinary credential phishing are insufficient for the selected authentication path | Stolen post-login session, compromised endpoint, stolen authenticator plus required activation factor, or weaker recovery path remain relevant. A generic USB device is not a FIDO authenticator | Low to medium; provisioning, spares and protected recovery procedures |
| Physical authentication for selected sensitive operations | Existing session alone cannot authorize designated export, key release or sharing changes when the authorization path enforces fresh physical proof | A touch alone does not verify the exact transaction. An infected endpoint can misrepresent the operation; bind proof to the operation and use a trusted confirmation path where that threat is in scope | Medium; friction limited to sensitive actions, not every normal request |
| HSM or isolated key service | Key material is harder to extract from the ordinary application host | A compromised caller with permission to decrypt can still obtain plaintext or use permitted cryptographic operations. Key custody and key-use authorization are separate | Medium to high; service integration, access policy, availability and recovery work |
| Dedicated viewing/approval workstation and separate administration | General employee endpoint compromise does not directly control the sensitive viewing/key-use environment | Dedicated device compromise, transfer channels, administrators and permitted exports remain in scope for separate controls | High; separate hardware, accounts, maintenance and workflows |
| Disconnected encrypted SSD in locked storage, with separately held keys | Remote control of the online network cannot directly read, modify or erase the disconnected media; encryption adds protection if media is stolen | Connected/unlocked periods are different. Theft, disaster, media failure, key loss and copies already online remain risks. An offline backup cannot undo an earlier disclosure | Modest media expense but high handling/restore burden; no immediate online search or AI processing of the isolated content |
| Isolated processing plus multiple-person key release/export approval | A single online account compromise or single custodian is insufficient for the selected release path if enforcement is independent | Collusion, physical intrusion, compromise of the trusted workstation, photography/transcription and authorized release remain outside or require additional controls | Very high operational burden; deliberate, delayed access |

Login authentication and content-decryption keys are different mechanisms;
requiring a FIDO key does not automatically encrypt or isolate stored content.
[FBI session-cookie guidance](https://www.fbi.gov/contact-us/field-offices/atlanta/news/cybercriminals-are-stealing-cookies-to-bypass-multifactor-authentication)
illustrates why login MFA alone is not post-login session protection.
[AWS KMS documentation](https://docs.aws.amazon.com/kms/latest/developerguide/data-protection.html)
illustrates key isolation with authorized key use as a separate condition.

A locked room protects physical access; an SSD attached to a networked, unlocked
server remains reachable through that server. Disconnection, encryption and
separate key custody have distinct purposes. Maintain and test independent
recovery copies where loss/destruction is in scope; offline storage does not
itself guarantee availability. See the
[CISA/MS-ISAC ransomware guide](https://www.cisa.gov/sites/default/files/publications/CISA_MS-ISAC_Ransomware%20Guide_S508C.pdf).

## Conditional strongest guarantee

Target statement for a selected isolated-storage option:

> Even if the online environment is fully compromised, the attacker cannot
> remotely read, modify or delete the specified disconnected data or obtain its
> separately held keys without the defined physical release procedure.

This statement requires no reachable replica, key or bypass capable of revealing
the protected content within the compromised environment. Custodians and the
physical/isolated boundary remain trusted. Guarantee changes during connection,
decryption, display and export must be explicit. Immediate online access and
complete disconnection cannot be promised for the same content simultaneously.

Protecting an original alone is insufficient if its full OCR text, audio
transcript, thumbnail, search index, AI context, logs or backups reveal the same
information online. TKL/application policy must classify these derivatives and
copies together, while allowing explicitly approved lower-sensitivity projections.

## Protection changes with the data's state

| State | Protection that can be claimed under the chosen boundary | What must remain explicit |
| --- | --- | --- |
| Disconnected, encrypted and physically secured | An online-only attacker cannot directly access that medium; separate key custody can protect confidentiality if the medium is stolen | Trusted custody, no equivalent online copy, key security, media survival and independent recovery |
| Connected but still encrypted/locked | Confidentiality may remain if the host cannot obtain or use a decryption key | Writable attached media may still be erased/corrupted; separate protected copies are needed for recovery |
| Decrypting or processing | Trusted isolated processing can preserve a boundary from the compromised ordinary network | Plaintext in memory/temp files and authority to request decryption are part of the trusted computing base; an HSM alone does not protect all outputs |
| Displayed to an authorized person | Access can be limited to the approved viewing environment | Screen capture, photography, transcription and the viewer's actions are not prevented by storage encryption |
| Exported or synchronized | Protection depends on the recipient and the resulting copy's policy | Physical release does not extend isolation automatically to an online recipient, AI service, index or backup |
| Re-locked / disconnected again | The offline boundary can be restored for the retained isolated asset | Previous exports, sessions, plaintext caches and copies must be accounted for; disconnection does not retract prior disclosure |

Physical controls are complementary rather than a single strongest setting.
Choose the trusted environment for each state and define the permitted transfer
between states. A secure room around a network-connected server does not by
itself convert online data into isolated storage.

## Textus integration and qualification

- [CNCF security architecture](../../../design/textus-security-architecture.md)
  and [Phase 107](../../../phase/phase-107.md) retain ordinary authorization.
  Physical possession is not an automatic grant of unrestricted data rights.
- [OAuth credential custody](../../../spec/oauth-credential-custody.md) describes
  the present owner-only plaintext adapter and its same-user/privileged-host
  limitation. Stronger custody requires a selected, qualified provider rather
  than re-labeling this adapter as hardware isolation.
- [TKL delivery](https://github.com/asami/textus-knowledge-lake/blob/main/docs/spec/tkl-managed-mobile-material-delivery.md)
  and [TKL Phase 2](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/phase-2.md)
  identify the owner of logical location, originals and admission. Offline
  media/derivative policies are options to design, not accepted capabilities of
  the current Drive route.
- [EAI Phase 2](https://github.com/asami/textus-enterprise-application-integration/blob/main/docs/phase/phase-2.md)
  owns relevant provider transfer mechanics; approved release still needs a
  defined recipient and retention policy.
- Authentication, recovery, client and runtime plan links remain in the
  [post-intrusion ownership mapping](2026-11-02-post-intrusion-protection-and-physical-control-options.md#textus-ownership-and-related-plans).

Before selecting an option, specify the assets and derivatives, attacker
capabilities, trusted custodians/devices, release and recovery procedures,
acceptable access delay and budget. Record equipment and ongoing operator costs
separately. A stronger option may sacrifice online search, automatic AI processing
or immediate availability for the protected content.

Qualification includes controlled custody/recovery inspection, refusal when a
required physical factor is absent, evidence of the actual connected/unlocked
behavior, and a restore exercise using safe samples. Record remaining unknowns;
never infer physical deployment acceptance from a software test alone.

This separation adds no implementation, hardware purchase, new Phase or current
demo gate. It creates no custom cryptography, per-launch unchanged-file proof,
management-hash ledger or blanket human approval requirement.

## Position within the ordinary business offering — 2026-11-02

The [small-business security decision](2026-11-02-small-business-security-defaults-and-options.md)
places these physical options beyond the normal default deployment. Ordinary
business use must meet the published baseline without HSMs, isolated rooms or
multi-custodian procedures. A configuration choice may request stronger policy,
but required equipment and operations must be explicit and actually available.
