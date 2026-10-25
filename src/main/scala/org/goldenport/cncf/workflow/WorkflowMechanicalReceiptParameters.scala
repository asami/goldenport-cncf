package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}

/**
 * Optional declaration resolved by normal Component initialization.
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
object WorkflowMechanicalReceiptParameters {
  private val _decoder = ComponentParameterDecoder[WorkflowMechanicalReceiptLimits] {
    case ConfigurationValue.NumberValue(value) if value.isWhole && value.isValidInt =>
      WorkflowMechanicalReceiptLimits.createC(value.toInt)
    case ConfigurationValue.StringValue(value) if value != null && value.matches("[0-9]+") =>
      BigInt(value) match {
        case number if number.isValidInt => WorkflowMechanicalReceiptLimits.createC(number.toInt)
        case _ => _invalid_c
      }
    case _ => _invalid_c
  }

  val maxBytes: ComponentParameterKey[WorkflowMechanicalReceiptLimits] =
    ComponentParameterKey.optional("workflow.mechanical-receipt.max-bytes", _decoder)

  def resolveC(parameters: ComponentInitializationParameters): Consequence[Option[WorkflowMechanicalReceiptLimits]] =
    if (parameters == null)
      Consequence.configurationInvalid("Mechanical receipt Component parameters are missing")
    else parameters.resolve(maxBytes).map(_.value)

  private def _invalid_c: Consequence[WorkflowMechanicalReceiptLimits] =
    Consequence.configurationInvalid("Mechanical receipt max-bytes requires an exact ASCII decimal integer in range")
}
