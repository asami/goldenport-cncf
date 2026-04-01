package org.goldenport.cncf.operation

/*
 * @since   Mar. 22, 2026
 *  version Mar. 28, 2026
 * @version Apr.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CmlOperationField(
  name: String,
  datatype: String,
  multiplicity: String = "1"
)

final case class CmlOperationDefinition(
  name: String,
  kind: String,
  summary: Option[String] = None,
  execution: Option[String] = None,
  implementation: Option[String] = None,
  inputType: String,
  inputSummary: Option[String] = None,
  inputDescription: Option[String] = None,
  outputType: String,
  outputSummary: Option[String] = None,
  outputDescription: Option[String] = None,
  inputValueKind: String,
  parameters: Vector[CmlOperationField] = Vector.empty
)
