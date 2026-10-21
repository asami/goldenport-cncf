package org.goldenport.cncf.workflow

import org.goldenport.Consequence

/**
 * Complete mechanical receipt JSON capacity, in UTF-8 bytes.
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
final class WorkflowMechanicalReceiptLimits private (val maxBytes: Int) {
  override def equals(other: Any): Boolean = other match {
    case value: WorkflowMechanicalReceiptLimits => maxBytes == value.maxBytes
    case _ => false
  }

  override def hashCode(): Int = maxBytes.hashCode()
}

object WorkflowMechanicalReceiptLimits {
  val maximumBytes: Int = 64 * 1024 * 1024
  val default: WorkflowMechanicalReceiptLimits = new WorkflowMechanicalReceiptLimits(1024 * 1024)

  def createC(maxBytes: Int): Consequence[WorkflowMechanicalReceiptLimits] =
    if (maxBytes >= 1 && maxBytes <= maximumBytes)
      Consequence.success(new WorkflowMechanicalReceiptLimits(maxBytes))
    else
      Consequence.configurationInvalid("Mechanical receipt max-bytes must be between 1 and 67108864")
}
