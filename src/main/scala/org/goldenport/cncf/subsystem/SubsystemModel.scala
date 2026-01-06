package org.goldenport.cncf.subsystem

import cats.data.NonEmptyVector
import org.goldenport.protocol.spec as spec

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SubsystemModel(
  name: String,
  tier: SubsystemTier,
  kind: SubsystemKind,
  components: Vector[String] = Vector.empty
)

sealed trait SubsystemTier
object SubsystemTier {
  case object Domain extends SubsystemTier
}

sealed trait SubsystemKind
object SubsystemKind {
  case object Service extends SubsystemKind
}

object DefaultSubsystemProvider {
  def helloWorld(): SubsystemModel =
    SubsystemModel(
      name = "hello-world",
      tier = SubsystemTier.Domain,
      kind = SubsystemKind.Service,
      components = Vector.empty
    )
}

object HelloWorldSubsystemMapping {
  def toServiceDefinitionGroup(
    subsystem: SubsystemModel
  ): spec.ServiceDefinitionGroup = {
    val op = spec.OperationDefinition.Instance(
      spec.OperationDefinition.Specification(
        name = "ping",
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition()
      )
    )
    val service = spec.ServiceDefinition(
      name = subsystem.name,
      operations = spec.OperationDefinitionGroup(
        operations = NonEmptyVector.of(op)
      )
    )
    spec.ServiceDefinitionGroup(
      services = Vector(service)
    )
  }
}
