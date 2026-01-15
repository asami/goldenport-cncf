package org.goldenport.cncf.component.protocol

import org.goldenport.Consequence
import org.goldenport.protocol.*
import org.goldenport.protocol.spec.*
import org.goldenport.cncf.action.Action

/*
 * @since   Jan. 14, 2026
 * @version Jan. 14, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ComponentOperationDefinition() extends OperationDefinition {
  final def createOperationRequest(req: Request): Consequence[Action] = {
    create_Action(req)
  }

  protected def create_Action(req: Request): Consequence[Action]
}

object ComponentOperationDefinition {
  case class Instance(specification: OperationDefinition.Specification) extends ComponentOperationDefinition {
    override protected def create_Action(req: Request): Consequence[Action] =
      Consequence {
        ???
      }
  }
}
