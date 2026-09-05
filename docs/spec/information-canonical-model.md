# Information Canonical Model Specification

This specification defines the canonical Information model boundary.

## Canonical model

1. `src/main/cozy/information.cml` MUST remain the sole Information
   domain-model source.
2. The canonical root runtime type MUST be the generated
   `org.goldenport.cncf.information.entity.Information`.
3. The generated Information output family MUST retain these canonical type
   names: `org.goldenport.cncf.information.entity.Information`,
   `org.goldenport.cncf.information.entity.read.Information`,
   `org.goldenport.cncf.information.entity.operation.Information`,
   `org.goldenport.cncf.information.entity.aggregate.Information`,
   `org.goldenport.cncf.information.entity.view.Information`,
   `org.goldenport.cncf.information.entity.view.summary.Information`, and
   `org.goldenport.cncf.information.entity.view.detail.Information`.
4. The generated Information input family MUST retain these canonical type
   names: `org.goldenport.cncf.information.entity.create.Information`,
   `org.goldenport.cncf.information.entity.update.Information`, and
   `org.goldenport.cncf.information.entity.query.Information`.
5. Information value types MUST use these generated canonical names:
   `org.goldenport.cncf.information.value.InformationImportContext`,
   `org.goldenport.cncf.information.value.InformationValidationIssue`,
   `org.goldenport.cncf.information.value.InformationIdentityBinding`,
   `org.goldenport.cncf.information.value.InformationResolutionCandidate`,
   `org.goldenport.cncf.information.value.InformationPublicationStatus`,
   `org.goldenport.cncf.information.value.InformationConflict`,
   `org.goldenport.cncf.information.value.InformationFieldEvent`,
   `org.goldenport.cncf.information.value.InformationSpaceSnapshot`, and
   `org.goldenport.cncf.information.value.InformationSpaceCounts`.
6. Information powertypes MUST use these generated canonical names:
   `org.goldenport.cncf.information.value.InformationLifecycleState`,
   `org.goldenport.cncf.information.value.InformationBindingStatus`,
   `org.goldenport.cncf.information.value.InformationPublicationState`,
   `org.goldenport.cncf.information.value.InformationConflictState`, and
   `org.goldenport.cncf.information.value.InformationFieldState`.
7. Information lifecycle descriptors MUST use the generated
   `domain.statemachine.*` package, including generated
   `domain.statemachine.informationLifecycle`.
8. The root Information identity MUST be the generated entity's
   `org.simplemodeling.model.datatype.EntityId`. RDF subjects, external
   identifiers, Tag ids, and Knowledge node ids MUST remain distinct
   identifiers and MUST NOT replace that entity identity.

## Runtime and data boundary

9. CML-generated codecs MUST be the normal runtime/data boundary for generated
   Information entities and values, including record persistence and decoding.
10. `InformationSpace` MUST orchestrate lifecycle and persistence around the
   generated Information entity. It MUST NOT define or expose a competing
   Information domain model.
11. No handwritten root `Information` type, handwritten `InformationId`, or
   public Information alias or facade MAY be introduced or retained as a
   second Information model.
12. `InformationSupport.scala` MAY provide non-CML helpers, domain extraction
   support, lifecycle construction support, and capabilities, but MUST NOT
   define a root Information runtime type.

## Persisted-record migration

13. The narrow persisted-record admission owned by
    `InformationPersistenceMigration` MUST remain migration-only. It MAY
    recognize and canonicalize the supported historical persisted shape
    before generated decoding, without rewriting the physical record.
14. That migration admission MUST NOT be treated as, or documented as, a
    public transport or API compatibility model. No legacy JSON compatibility
    promise is established by this specification.
15. This specification MUST NOT claim rejection behavior that the generated
    codec does not establish. Persisted-record migration and public wire/API
    compatibility are separate boundaries.

## Executable evidence

The following executable specifications provide the current evidence for this
contract and MUST remain the named evidence set for the boundary:

- `InformationCmlCanonicalContractSpec`
- `InformationCanonicalRuntimeReferenceSpec`
- `GeneratedInformationRuntimeAdoptionSpec`
- `InformationPersistenceMigrationSpec`
