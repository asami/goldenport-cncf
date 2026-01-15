package org.goldenport.cncf.component.repository

import org.goldenport.Consequence
import org.goldenport.cncf.bootstrap.BootstrapLog
import org.goldenport.cncf.component.{Component, ComponentDefinition, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin, GeneratedComponent}
import org.goldenport.cncf.component.Component.Core
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.spec as spec
import scala.util.control.NonFatal

/*
 * @since   Jan. 12, 2026
 * @version Jan. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentProvider {
  import ComponentSource.{ClassDef, Definition}

  def provide(
    source: ComponentSource,
    subsystem: Subsystem,
    origin: ComponentOrigin
  ): Consequence[Component] = {
    val log = BootstrapLog.stderr
    source match {
      case Definition(defn, _) =>
        _provide_definition(defn, subsystem, origin, log)
      case ClassDef(componentClass, _) =>
        _provide_class(componentClass, subsystem, origin, log)
    }
  }

  private def _provide_definition(
    definition: ComponentDefinition,
    subsystem: Subsystem,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Consequence[Component] = {
    log.info(s"instantiate definition: ${definition.name}")
    val core = _core_from_definition(definition)
    val comp = new GeneratedComponent(core, definition)
    _initialize_component(comp, subsystem, core, origin)
  }

  private def _provide_class(
    componentClass: Class[_ <: Component],
    subsystem: Subsystem,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Consequence[Component] = {
    log.info(s"instantiate class: ${componentClass.getName} method=no-arg")
    _instantiate_noarg(componentClass) match {
      case Consequence.Success(comp) =>
        val core = _core_from_component(comp, componentClass)
        _initialize_component(comp, subsystem, core, origin)
      case Consequence.Failure(conclusion) =>
        log.warn(s"failed to instantiate class=${componentClass.getName} cause=${conclusion.message}")
        Consequence.Failure(conclusion)
    }
  }

  private def _instantiate_noarg(
    cls: Class[_ <: Component]
  ): Consequence[Component] = {
    _catch_non_fatal {
      val ctor = cls.getConstructor()
      ctor.newInstance().asInstanceOf[Component]
    }
  }

  private def _catch_non_fatal[A](f: => A): Consequence[A] = {
    try {
      Consequence.success(f)
    } catch {
      case NonFatal(e) => Consequence.failure(e)
    }
  }

  private def _initialize_component(
    comp: Component,
    subsystem: Subsystem,
    core: Core,
    origin: ComponentOrigin
  ): Consequence[Component] = {
    _catch_non_fatal {
      comp.initialize(ComponentInit(subsystem, core, origin))
    }
  }

  private def _core_from_definition(
    definition: ComponentDefinition
  ): Core = {
    val name = definition.name
    val componentId = ComponentId(name)
    val instanceId = ComponentInstanceId.default(componentId)
    Component.Core.create(name, componentId, instanceId, definition.protocol)
  }

  private def _core_from_component(
    comp: Component,
    componentClass: Class[_ <: Component]
  ): Core = {
    val name = _component_name_from_class(componentClass.getName)
    val componentId = ComponentId(name)
    val instanceId = ComponentInstanceId.default(componentId)
    val protocol = _protocol_from_component(comp)
    Component.Core.create(name, componentId, instanceId, protocol)
  }

  private def _protocol_from_component(
    comp: Component
  ): Protocol = {
    try {
      comp.protocol
    } catch {
      case NonFatal(_) => _empty_protocol()
    }
  }

  private def _empty_protocol(): Protocol = {
    Protocol(
      services = spec.ServiceDefinitionGroup(services = Vector.empty),
      handler = ProtocolHandler(
        ingresses = IngressCollection(Vector.empty),
        egresses = EgressCollection(Vector.empty),
        projections = ProjectionCollection()
      )
    )
  }

  private def _component_name_from_class(
    className: String
  ): String = {
    val simple = className.split('.').lastOption.getOrElse(className)
    val base = simple.takeWhile(_ != '$')
    if (base.nonEmpty) base else simple.replace("$", "")
  }
}
