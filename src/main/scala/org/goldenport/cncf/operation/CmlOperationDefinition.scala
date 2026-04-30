package org.goldenport.cncf.operation

import org.goldenport.cncf.security.OperationAuthorizationRule

/*
 * @since   Mar. 22, 2026
 *  version Mar. 28, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CmlOperationAssociationBinding(
  domain: String,
  targetKind: String,
  createsAssociation: Boolean = false,
  detachesAssociation: Boolean = false,
  roles: Vector[String] = Vector.empty,
  parameters: Vector[String] = Vector.empty,
  sourceEntityIdMode: String = "none",
  sourceEntityIdParameters: Vector[String] = Vector.empty,
  sourceEntityIdResultFields: Vector[String] = CmlOperationAssociationBinding.defaultSourceEntityIdResultFields,
  targetIdParameters: Vector[String] = Vector.empty,
  sortOrderParameters: Vector[String] = Vector.empty
) {
  def isAutomaticCreate: Boolean =
    createsAssociation && sourceEntityIdMode != "none"
}

object CmlOperationAssociationBinding {
  val SourceEntityIdModeEntityCreateResult: String = "entity-create-result"
  val SourceEntityIdModeParameter: String = "parameter"
  val SourceEntityIdModeResultField: String = "result-field"
  val SourceEntityIdModeNone: String = "none"

  val defaultSourceEntityIdResultFields: Vector[String] =
    Vector("entity_id", "entityId", "id")
}

final case class CmlOperationChildEntityBinding(
  name: String,
  entityName: String,
  inputParameter: String,
  parentIdField: String,
  relationshipName: Option[String] = None,
  sourceEntityIdMode: String = CmlOperationAssociationBinding.SourceEntityIdModeNone,
  sourceEntityIdParameters: Vector[String] = Vector.empty,
  sourceEntityIdResultFields: Vector[String] = CmlOperationAssociationBinding.defaultSourceEntityIdResultFields,
  childIdField: Option[String] = Some("id"),
  sortOrderField: Option[String] = None,
  createsEntity: Boolean = false,
  failurePolicy: String = CmlOperationChildEntityBinding.FailurePolicyCompensateParentOnCreate
) {
  def isAutomaticCreate: Boolean =
    createsEntity && sourceEntityIdMode != CmlOperationAssociationBinding.SourceEntityIdModeNone
}

object CmlOperationChildEntityBinding {
  val FailurePolicyCompensateParentOnCreate: String = "compensate-parent-on-create"
  val FailurePolicyKeepParent: String = "keep-parent"
}

final case class CmlOperationImageBinding(
  mediaKind: String = "image",
  acceptsUpload: Boolean = false,
  acceptsExistingBlobId: Boolean = false,
  acceptsArchiveBlobId: Boolean = false,
  createsAttachment: Boolean = false,
  detachesAttachment: Boolean = false,
  roles: Vector[String] = Vector.empty,
  parameters: Vector[String] = Vector.empty,
  sourceEntityIdMode: String = CmlOperationAssociationBinding.SourceEntityIdModeNone,
  sourceEntityIdParameters: Vector[String] = Vector.empty,
  sourceEntityIdResultFields: Vector[String] = CmlOperationAssociationBinding.defaultSourceEntityIdResultFields,
  targetIdParameters: Vector[String] = Vector.empty,
  sortOrderParameters: Vector[String] = Vector.empty
) {
  def toAssociationBinding: CmlOperationAssociationBinding =
    CmlOperationAssociationBinding(
      domain = "blob_attachment",
      targetKind = "blob",
      createsAssociation = createsAttachment,
      detachesAssociation = detachesAttachment,
      roles = roles,
      parameters = parameters,
      sourceEntityIdMode = sourceEntityIdMode,
      sourceEntityIdParameters = sourceEntityIdParameters,
      sourceEntityIdResultFields = sourceEntityIdResultFields,
      targetIdParameters = targetIdParameters,
      sortOrderParameters = sortOrderParameters
    )
}

final case class CmlEntityRelationshipDefinition(
  name: String,
  kind: String,
  sourceEntityName: String,
  targetEntityName: String,
  targetModelKind: String = CmlEntityRelationshipDefinition.TargetModelKindEntity,
  sourceRole: Option[String] = None,
  targetRole: Option[String] = None,
  multiplicity: Option[String] = None,
  storageMode: String = CmlEntityRelationshipDefinition.StorageAssociationRecord,
  parentIdField: Option[String] = None,
  valueField: Option[String] = None,
  sortOrderField: Option[String] = None,
  associationDomain: Option[String] = None,
  targetKind: Option[String] = None,
  lifecyclePolicy: Option[String] = None
)

object CmlEntityRelationshipDefinition {
  val KindAssociation: String = "association"
  val KindAggregation: String = "aggregation"
  val KindComposition: String = "composition"

  val StorageAssociationRecord: String = "association-record"
  val StorageChildParentIdField: String = "child-parent-id-field"
  val StorageEmbeddedValueObject: String = "embedded-value-object"

  val TargetModelKindEntity: String = "entity"
  val TargetModelKindValue: String = "value"

  val LifecycleIndependent: String = "independent"
  val LifecycleDependent: String = "dependent"
}

final case class CmlOperationField(
  name: String,
  datatype: String,
  multiplicity: String = "1",
  label: Option[String] = None,
  controlType: Option[String] = None,
  placeholder: Option[String] = None,
  help: Option[String] = None,
  required: Option[Boolean] = None
)

final case class CmlOperationAccess(
  policy: String,
  resource: Option[String] = None,
  target: Option[String] = None,
  mode: Option[String] = None,
  relation: Option[String] = None,
  operationModel: Option[String] = None,
  entityUsage: Option[String] = None,
  entityOperationKind: Option[String] = None,
  entityApplicationDomain: Option[String] = None,
  condition: Option[String] = None
)

final case class CmlOperationDefinition(
  name: String,
  kind: String,
  summary: Option[String] = None,
  execution: Option[String] = None,
  implementation: Option[String] = None,
  entityName: Option[String] = None,
  entityNames: Vector[String] = Vector.empty,
  inputType: String,
  inputSummary: Option[String] = None,
  inputDescription: Option[String] = None,
  outputType: String,
  outputSummary: Option[String] = None,
  outputDescription: Option[String] = None,
  inputValueKind: String,
  access: Option[CmlOperationAccess] = None,
  parameters: Vector[CmlOperationField] = Vector.empty,
  operationAuthorization: Option[OperationAuthorizationRule] = None,
  childEntityBindings: Vector[CmlOperationChildEntityBinding] = Vector.empty,
  associationBinding: Option[CmlOperationAssociationBinding] = None,
  imageBinding: Option[CmlOperationImageBinding] = None
)

trait ChildEntityBindingOperationDefinition {
  def childEntityBindings: Vector[CmlOperationChildEntityBinding]
}

trait AssociationBindingOperationDefinition {
  def associationBinding: CmlOperationAssociationBinding
}

trait ImageBindingOperationDefinition {
  def imageBinding: CmlOperationImageBinding
}
