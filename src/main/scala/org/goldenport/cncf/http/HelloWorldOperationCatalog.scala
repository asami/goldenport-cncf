package org.goldenport.cncf.http

import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.spec.OperationDefinition

/*
 * @since   Jan.  8, 2026
 * @version Jan.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class HelloWorldOperationCatalog(
  subsystem: Subsystem
) extends OperationCatalog with DefaultHttpExecutionEngine.ComponentServiceResolver {
  def resolve(route: HttpRouter.Route): OperationCatalog.Result = {
    val components = subsystem.components
    (for {
      component <- components.get(route.component)
      service <- component.protocol.services.services.find(_.name == route.service)
      operation <- service.operations.operations.find(_.name == route.operation)
    } yield operation) match {
      case Some(op) => OperationCatalog.Found(op)
      case None => OperationCatalog.NotFound
    }
  }

  def singleService(component: String): Option[String] = {
    subsystem.components.get(component).flatMap { c =>
      val services = c.protocol.services.services
      if (services.size == 1) Some(services.head.name) else None
    }
  }
}
