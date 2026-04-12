package org.goldenport.cncf.operation

/*
 * @since   Mar. 22, 2026
 *  version Mar. 28, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CmlOperationField(
  name: String,
  datatype: String,
  multiplicity: String = "1"
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
  entityApplicationDomain: Option[String] = None
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
  parameters: Vector[CmlOperationField] = Vector.empty
)
