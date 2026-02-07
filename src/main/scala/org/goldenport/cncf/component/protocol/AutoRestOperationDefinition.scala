package org.goldenport.cncf.component.protocol

import org.goldenport.Consequence
import org.goldenport.protocol.{Request, operation}
import org.goldenport.protocol.spec.{OperationDefinition => SpecOperationDefinition, RequestDefinition, ResponseDefinition}

/*
 * Compile-only wrapper for OpenAPI-generated operations.
 *
 * @since   Feb.  7, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class AutoRestOperationDefinition(
  op: OpenApiOperation
) extends SpecOperationDefinition {

  override def name: String =
    op.operationId

  override val specification: SpecOperationDefinition.Specification =
    SpecOperationDefinition.Specification(
      name = op.operationId,
      request = RequestDefinition(),
      response = ResponseDefinition()
    )

  override def createOperationRequest(
    request: Request
  ): Consequence[operation.OperationRequest] =
    Consequence.failure("AutoRest operation execution is not implemented")
}
