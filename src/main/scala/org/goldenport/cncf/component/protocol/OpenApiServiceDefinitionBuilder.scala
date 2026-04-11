package org.goldenport.cncf.component.protocol

import cats.data.NonEmptyVector
import org.goldenport.protocol.*
import org.goldenport.protocol.operation.*
import org.goldenport.protocol.spec as spec
import org.goldenport.schema.DataType
import org.goldenport.value.BaseContent

/*
 * OpenAPI -> ServiceDefinitionGroup transformer (compile-only).
 *
 * @since   Feb.  7, 2026
 * @version Apr. 11, 2026
 * @author  ASAMI, Tomoharu
 */
trait OpenApiServiceDefinitionBuilder {
  def build(model: OpenApiModel): spec.ServiceDefinitionGroup
}

/*
 * Default OpenAPI -> ServiceDefinitionGroup implementation (compile-only).
 *
 * NOTE: Signature/schema/parameters are intentionally ignored in this phase.
 */
final class DefaultOpenApiServiceDefinitionBuilder
    extends OpenApiServiceDefinitionBuilder {

  def build(model: OpenApiModel): spec.ServiceDefinitionGroup =
    spec.ServiceDefinitionGroup(
      services = model.services.map(_build_service)
    )

  private def _build_service(service: OpenApiService): spec.ServiceDefinition = {
    val operations = service.operations.map(_build_operation)
    val nonEmptyOperations = NonEmptyVector.fromVectorUnsafe(
      if (operations.nonEmpty) operations.toVector
      else Vector(_build_placeholder_operation(service.basePath))
    )
    spec.ServiceDefinition(
      name = service.basePath,
      operations = spec.OperationDefinitionGroup(operations = nonEmptyOperations)
    )
  }

  private def _build_operation(op: OpenApiOperation): spec.OperationDefinition =
    spec.OperationDefinition(
      content = BaseContent.simple(op.operationId),
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition(result = List(DataType.Named("HttpResponse")))
    )

  private def _build_placeholder_operation(
    serviceName: String
  ): spec.OperationDefinition =
    spec.OperationDefinition(
      content = BaseContent.simple(s"$serviceName-noop"),
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )
}
