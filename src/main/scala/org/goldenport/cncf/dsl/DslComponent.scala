package org.goldenport.cncf.dsl

import scala.collection.mutable.ListBuffer
import cats.data.NonEmptyVector
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}

/**
 * DSL-facing Component base.
 *
 * This is a thin facade over the existing CNCF Component and routing model.
 * It intentionally introduces no new execution model.
 */
/*
 * @since   Jan. 11, 2026
 * @version Jan. 11, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class DslComponent(componentName: String)
  extends Component
  with OperationDsl {
  private val _operations = ListBuffer.empty[(String, spec.OperationDefinition)]

  override protected def dslDomain: String = componentName

  override protected def register_operation(
    domain: String,
    service: String,
    op: spec.OperationDefinition
  ): Unit = {
    val _ = domain
    _operations.append((service, op))
  }

  override lazy val core: Component.Core = {
    val protocol = _build_protocol_()
    val componentId = ComponentId(componentName)
    val instanceId = ComponentInstanceId.default(componentId)
    Component.Core.create(componentName, componentId, instanceId, protocol)
  }

  protected final def buildProtocol(
    serviceName: String,
    operations: NonEmptyVector[spec.OperationDefinition]
  ): Protocol = {
    OperationDsl.buildProtocol(serviceName, operations)
  }

  private def _build_protocol_(): Protocol = {
    val grouped = _operations.groupBy(_._1)
    val services = grouped.toVector.map { case (serviceName, ops) =>
      val opdefs = ops.map(_._2).toVector
      val nonEmpty = NonEmptyVector.fromVectorUnsafe(opdefs)
      spec.ServiceDefinition(
        name = serviceName,
        operations = spec.OperationDefinitionGroup(operations = nonEmpty)
      )
    }
    if (services.isEmpty) {
      throw new IllegalStateException("DslComponent requires at least one operation definition")
    }
    OperationDsl.buildProtocolFromServices(services)
  }
}
