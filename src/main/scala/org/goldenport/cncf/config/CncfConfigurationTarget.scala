package org.goldenport.cncf.config

import java.text.Normalizer

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}

/*
 * @since   Aug.  2, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemInstanceId private (
  val subsystem: String,
  val instance: String
) {
  override def equals(other: Any): Boolean =
    other match {
      case that: SubsystemInstanceId =>
        subsystem == that.subsystem && instance == that.instance
      case _ => false
    }

  override def hashCode(): Int =
    31 * subsystem.hashCode + instance.hashCode

  override def toString: String =
    s"$subsystem/$instance"
}

object SubsystemInstanceId {
  private val _label_pattern = "[\\p{L}][\\p{L}\\p{N}_-]*".r

  def create(
    subsystem: String,
    instance: String
  ): Consequence[SubsystemInstanceId] =
    for {
      normalizedsubsystem <- _normalize("subsystem", subsystem)
      normalizedinstance <- _normalize("instance", instance)
    } yield new SubsystemInstanceId(normalizedsubsystem, normalizedinstance)

  def default(subsystem: String): Consequence[SubsystemInstanceId] =
    create(subsystem, "default")

  private def _normalize(
    label: String,
    value: String
  ): Consequence[String] =
    Option(value).map(Normalizer.normalize(_, Normalizer.Form.NFC)).filter(_is_valid).fold[Consequence[String]](
      Consequence.configurationInvalid(s"configuration $label identity is invalid")
    )(Consequence.success)

  private def _is_valid(value: String): Boolean =
    value == value.trim && _label_pattern.matches(value)
}

sealed trait CncfConfigurationTarget

object CncfConfigurationTarget {
  private val _component_label_pattern = "[A-Za-z][A-Za-z0-9_]*".r

  case object Global extends CncfConfigurationTarget

  final class ComponentClass private[config] (
    val component: ComponentId
  ) extends CncfConfigurationTarget {
    override def equals(other: Any): Boolean =
      other match {
        case that: ComponentClass => component.name == that.component.name
        case _ => false
      }

    override def hashCode(): Int =
      component.name.hashCode
  }

  object ComponentClass {
    def create(component: ComponentId): Consequence[ComponentClass] =
      if (_is_valid_component(component))
        Consequence.success(new ComponentClass(component))
      else
        Consequence.configurationInvalid("configuration component class target is invalid")

    def unapply(value: ComponentClass): Option[ComponentId] =
      Option(value).map(_.component)
  }

  final class SubsystemInstance private[config] (
    val subsystem: SubsystemInstanceId
  ) extends CncfConfigurationTarget {
    override def equals(other: Any): Boolean =
      other match {
        case that: SubsystemInstance => subsystem == that.subsystem
        case _ => false
      }

    override def hashCode(): Int =
      subsystem.hashCode
  }

  object SubsystemInstance {
    def create(subsystem: SubsystemInstanceId): Consequence[SubsystemInstance] =
      Option(subsystem).fold[Consequence[SubsystemInstance]](
        Consequence.configurationInvalid("configuration subsystem instance target is required")
      )(x => Consequence.success(new SubsystemInstance(x)))

    def unapply(value: SubsystemInstance): Option[SubsystemInstanceId] =
      Option(value).map(_.subsystem)
  }

  final class ComponentInstance private[config] (
    val subsystem: SubsystemInstanceId,
    val component: ComponentInstanceId
  ) extends CncfConfigurationTarget {
    override def equals(other: Any): Boolean =
      other match {
        case that: ComponentInstance =>
          subsystem == that.subsystem &&
            component.name == that.component.name &&
            component.instance == that.component.instance
        case _ => false
      }

    override def hashCode(): Int =
      31 * (31 * subsystem.hashCode + component.name.hashCode) + component.instance.hashCode
  }

  object ComponentInstance {
    def create(
      subsystem: SubsystemInstanceId,
      component: ComponentInstanceId
    ): Consequence[ComponentInstance] =
      if (subsystem == null)
        Consequence.configurationInvalid("configuration component instance target requires containing subsystem")
      else if (!_is_valid_component_instance(component))
        Consequence.configurationInvalid("configuration component instance target is invalid")
      else
        Consequence.success(new ComponentInstance(subsystem, component))

    def unapply(value: ComponentInstance): Option[(SubsystemInstanceId, ComponentInstanceId)] =
      Option(value).map(x => x.subsystem -> x.component)
  }

  private def _is_valid_component(value: ComponentId): Boolean =
    value != null && value.name.contains('.') && _is_valid_label(value.localId.value())

  private def _is_valid_component_instance(value: ComponentInstanceId): Boolean =
    value != null && _is_valid_component(value.componentId) && _is_valid_label(value.instance)

  private def _is_valid_label(value: String): Boolean =
    Option(value).exists(_component_label_pattern.matches)
}
