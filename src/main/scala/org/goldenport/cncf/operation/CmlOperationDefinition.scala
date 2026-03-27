package org.goldenport.cncf.operation

/*
 * @since   Mar. 22, 2026
 * @version Mar. 27, 2026
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
  execution: Option[String] = None,
  inputType: String,
  outputType: String,
  inputValueKind: String,
  parameters: Vector[CmlOperationField] = Vector.empty
)
