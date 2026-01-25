package org.goldenport.cncf.component.repository

import org.goldenport.Consequence
import org.goldenport.cncf.bootstrap.BootstrapLog
import org.goldenport.cncf.observability.global.{ObservabilityScopeDefaults, PersistentBootstrapLog}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.Component.Core
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.spec as spec
import java.lang.reflect.Modifier
import scala.util.control.NonFatal

/*
 * @since   Jan. 12, 2026
 * @version Jan. 26, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentProvider {
  import ComponentSource.ClassDef

  def provide(
    source: ComponentSource,
    subsystem: Subsystem,
    origin: ComponentOrigin
  ): Consequence[Component] = {
    source match {
      case ClassDef(componentClass, _) =>
        val log = PersistentBootstrapLog.forClass(classOf[ComponentProvider.type], ObservabilityScopeDefaults.Bootstrap)
        _provide_class(componentClass, subsystem, origin, log)
    }
  }

  private def _provide_class(
    componentClass: Class[_ <: Component],
    subsystem: Subsystem,
    origin: ComponentOrigin,
    log: BootstrapLog
  ): Consequence[Component] = {
    log.info(s"instantiate class: ${componentClass.getName} method=no-arg")
    val params = ComponentCreate(subsystem, origin)
    _instantiate_noarg(componentClass) match {
      case Consequence.Success(comp) =>
        val core = _core_from_component(comp, componentClass)
        _initialize_component(comp, subsystem, core, origin)
      case Consequence.Failure(conclusion) =>
        log.warn(s"failed to instantiate class=${componentClass.getName} cause=${conclusion.message}; trying companion factory")
        _provide_via_companion_factory(componentClass, params, log, conclusion)
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

  private def _provide_via_companion_factory(
    componentClass: Class[_ <: Component],
    params: ComponentCreate,
    log: BootstrapLog,
    fallbackConclusion: org.goldenport.Conclusion
  ): Consequence[Component] = {
    _companion_factories(componentClass) match {
      case factory +: _ =>
        val components = factory.create(params)
        components.headOption match {
          case Some(comp) =>
            log.info(s"instantiated via companion factory for ${componentClass.getName}")
            Consequence.Success(comp)
          case None =>
            val message = s"companion factory for ${componentClass.getName} produced no components"
            log.warn(message)
            Consequence.failure(new RuntimeException(message))
        }
      case _ =>
        Consequence.failure(new RuntimeException(fallbackConclusion.message))
    }
  }

  private def _companion_factories(
    componentClass: Class[_ <: Component]
  ): Vector[Component.Factory] = {
    val companionName = componentClass.getName + "$"
    _load_optional_class(companionName, componentClass.getClassLoader) match {
      case Some(companionClass) =>
        _module_instance(companionClass) match {
          case Some(factory: Component.Factory) => Vector(factory)
          case _ =>
            companionClass.getDeclaredClasses.view
              .map(_resolve_factory_class)
              .collect { case Some(factory) => factory }
              .toVector
        }
      case None => Vector.empty
    }
  }

  private def _resolve_factory_class(
    cls: Class[_]
  ): Option[Component.Factory] = {
    if (!classOf[Component.Factory].isAssignableFrom(cls)) {
      None
    } else if (cls.getName.endsWith("$")) {
      _module_instance(cls).collect { case factory: Component.Factory => factory }
    } else if (Modifier.isAbstract(cls.getModifiers)) {
      None
    } else {
      _instantiate_factory_class(cls)
    }
  }

  private def _instantiate_factory_class(
    cls: Class[_]
  ): Option[Component.Factory] = {
    try {
      Some(cls.getDeclaredConstructor().newInstance().asInstanceOf[Component.Factory])
    } catch {
      case NonFatal(_) => None
    }
  }

  private def _module_instance(
    moduleClass: Class[_]
  ): Option[AnyRef] = {
    try {
      val field = moduleClass.getField("MODULE$")
      val instance = field.get(null)
      Option(instance)
    } catch {
      case NonFatal(_) => None
    }
  }

  private def _load_optional_class(
    className: String,
    loader: ClassLoader
  ): Option[Class[_]] = {
    try {
      Some(Class.forName(className, false, loader))
    } catch {
      case NonFatal(_) => None
    }
  }
}
