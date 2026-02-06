package org.goldenport.cncf.component.protocol

import org.goldenport.Consequence
import org.goldenport.protocol.*
import org.goldenport.protocol.spec.*
import org.goldenport.protocol.spec.OperationDefinition.{Specification => CoreSpecification}
import org.goldenport.cncf.action.Action

/*
 * @since   Jan. 14, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ComponentOperationDefinition() extends OperationDefinition {
  def componentSpecification: ComponentOperationDefinition.Specification =
    ComponentOperationDefinition.Specification.command

  def createOperationRequest(req: Request): Consequence[Action]
}

object ComponentOperationDefinition {
  case class Instance(specification: OperationDefinition.Specification) extends ComponentOperationDefinition {
    def createOperationRequest(req: Request): Consequence[Action] = {
      Consequence.failNotImplemented
    }
  }

  /**
   * CNCF-level operation attributes.
   *
   * NOTE:
   * These attributes are defined but not yet consumed by
   * OpenAPI, REST projection, or runtime scheduling.
   */
  sealed trait OperationAttribute
  object OperationAttribute {
    // CQRS / semantics
    case object Command extends OperationAttribute
    case object Query extends OperationAttribute

    // semantics
    case object Idempotent extends OperationAttribute

    // operational
    case object HighLoad extends OperationAttribute
  }

  /**
   * CNCF extension of core Specification.
   *
   * This is value-backed and structurally symmetric with
   * org.goldenport.protocol.spec.OperationDefinition.Specification.
   */
  case class Specification(
    core: Specification.Core
  ) extends Specification.Core.Holder {
  }
  object Specification {
    val command = Specification(Core.command)

    case class Core(
      attribute: OperationAttribute
    )
    object Core {
      val command = Core(OperationAttribute.Command)

      trait Holder {
        def core: Core

        def attribute = core.attribute
      }
    }
  }
}
