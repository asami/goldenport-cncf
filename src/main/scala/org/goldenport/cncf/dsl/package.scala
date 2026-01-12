package org.goldenport.cncf

/*
 * @since   Jan. 11, 2026
 * @version Jan. 11, 2026
 * @author  ASAMI, Tomoharu
 */
package object dsl {
  import org.goldenport.Consequence
  import org.goldenport.protocol.operation.OperationResponse

  def result_success(value: String): Consequence[OperationResponse] = {
    Consequence.success(OperationResponse.Scalar(value))
  }
}
