package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import scala.util.control.NonFatal

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
enum ComponentParameterRequirement {
  case Required
  case Optional
}

enum ComponentParameterConfidentiality {
  case Public
  case Confidential
  case Secret
}

enum ComponentParameterProvenance(val token: String) {
  case PackagedDefault extends ComponentParameterProvenance("packaged-default")
  case AssemblyDefault extends ComponentParameterProvenance("assembly-default")
  case SubsystemInstance extends ComponentParameterProvenance("subsystem-instance")
  case RuntimeConfiguration extends ComponentParameterProvenance("runtime-configuration")
  case TestOverlay extends ComponentParameterProvenance("test-overlay")
  case Absent extends ComponentParameterProvenance("absent")
}

trait ComponentParameterDecoder[A] {
  def decode(value: ConfigurationValue): Consequence[A]
}

object ComponentParameterDecoder {
  def apply[A](f: ConfigurationValue => Consequence[A]): ComponentParameterDecoder[A] =
    new ComponentParameterDecoder[A] {
      def decode(value: ConfigurationValue): Consequence[A] = f(value)
    }

  val string: ComponentParameterDecoder[String] =
    _delegate(ComponentConfigurationDecoder.string)

  val int: ComponentParameterDecoder[Int] =
    _delegate(ComponentConfigurationDecoder.int)

  val boolean: ComponentParameterDecoder[Boolean] =
    _delegate(ComponentConfigurationDecoder.boolean)

  private[config] val _secret_reference: ComponentParameterDecoder[SecretReference] =
    _delegate(ComponentConfigurationDecoder._secret_reference)

  private def _delegate[A](
    decoder: ComponentConfigurationDecoder[A]
  ): ComponentParameterDecoder[A] =
    ComponentParameterDecoder(decoder.decode)
}

final class ComponentParameterKey[A] private (
  val name: String,
  val requirement: ComponentParameterRequirement,
  val confidentiality: ComponentParameterConfidentiality,
  decoder: ComponentParameterDecoder[A]
) {
  private val _decoder = decoder

  private[config] def decode_value(
    value: ConfigurationValue
  ): Consequence[A] =
    _decoder.decode(value)
}

object ComponentParameterKey {
  def required[A](
    name: String,
    decoder: ComponentParameterDecoder[A]
  ): ComponentParameterKey[A] =
    _create(
      name,
      decoder,
      ComponentParameterRequirement.Required,
      ComponentParameterConfidentiality.Public
    )

  def optional[A](
    name: String,
    decoder: ComponentParameterDecoder[A]
  ): ComponentParameterKey[A] =
    _create(
      name,
      decoder,
      ComponentParameterRequirement.Optional,
      ComponentParameterConfidentiality.Public
    )

  def requiredString(name: String): ComponentParameterKey[String] =
    required(name, ComponentParameterDecoder.string)

  def optionalString(name: String): ComponentParameterKey[String] =
    optional(name, ComponentParameterDecoder.string)

  def requiredInt(name: String): ComponentParameterKey[Int] =
    required(name, ComponentParameterDecoder.int)

  def optionalInt(name: String): ComponentParameterKey[Int] =
    optional(name, ComponentParameterDecoder.int)

  def requiredBoolean(name: String): ComponentParameterKey[Boolean] =
    required(name, ComponentParameterDecoder.boolean)

  def optionalBoolean(name: String): ComponentParameterKey[Boolean] =
    optional(name, ComponentParameterDecoder.boolean)

  def requiredSecretReference(name: String): ComponentParameterKey[SecretReference] =
    _create(
      name,
      ComponentParameterDecoder._secret_reference,
      ComponentParameterRequirement.Required,
      ComponentParameterConfidentiality.Secret
    )

  def optionalSecretReference(name: String): ComponentParameterKey[SecretReference] =
    _create(
      name,
      ComponentParameterDecoder._secret_reference,
      ComponentParameterRequirement.Optional,
      ComponentParameterConfidentiality.Secret
    )

  def confidentialRequired(name: String): ComponentParameterKey[Nothing] =
    _create(
      name,
      ComponentParameterDecoder(_ =>
        Consequence.configurationInvalid(
          "confidential component initialization parameter is not available through the component boundary"
        )
      ),
      ComponentParameterRequirement.Required,
      ComponentParameterConfidentiality.Confidential
    )

  private def _create[A](
    name: String,
    decoder: ComponentParameterDecoder[A],
    requirement: ComponentParameterRequirement,
    confidentiality: ComponentParameterConfidentiality
  ): ComponentParameterKey[A] = {
    val normalized = Option(name).map(_.trim).getOrElse("")
    require(normalized.nonEmpty, "component initialization parameter key name is required")
    new ComponentParameterKey(normalized, requirement, confidentiality, decoder)
  }
}

final case class ComponentParameterResolution[A] private[config] (
  value: Option[A],
  provenance: ComponentParameterProvenance
)

private[cncf] final case class ComponentParameterCandidate(
  value: ConfigurationValue,
  provenance: ComponentParameterProvenance
) {
  require(
    provenance != ComponentParameterProvenance.Absent,
    "a present component initialization parameter requires present provenance"
  )
}

abstract class ComponentParameterResolver private[cncf] () {
  final def resolve[A](
    key: ComponentParameterKey[A]
  ): Consequence[ComponentParameterResolution[A]] =
    key.confidentiality match {
      case ComponentParameterConfidentiality.Confidential =>
        ComponentParameterDiagnostics.rejected(
          key,
          s"confidential component initialization parameter is not available through the component boundary: ${key.name}"
        )
      case ComponentParameterConfidentiality.Public |
          ComponentParameterConfidentiality.Secret =>
        lookup_parameter(key.name).flatMap {
          case Some(candidate) =>
            try {
              key.decode_value(candidate.value) match {
                case Consequence.Success(value) =>
                  Consequence.success(
                    ComponentParameterResolution(Some(value), candidate.provenance)
                  )
                case Consequence.Failure(_) =>
                  ComponentParameterDiagnostics.malformed(key, candidate.provenance)
              }
            } catch {
              case NonFatal(_) =>
                ComponentParameterDiagnostics.malformed(key, candidate.provenance)
            }
          case None =>
            key.requirement match {
              case ComponentParameterRequirement.Required =>
                ComponentParameterDiagnostics.missing(key)
              case ComponentParameterRequirement.Optional =>
                Consequence.success(
                  ComponentParameterResolution(None, ComponentParameterProvenance.Absent)
                )
            }
        }
    }

  protected def lookup_parameter(
    name: String
  ): Consequence[Option[ComponentParameterCandidate]]
}

final class ComponentInitializationParameters private (
  private val _entries: Vector[ComponentInitializationParameters.Entry]
) {
  def size: Int = _entries.size

  def isEmpty: Boolean = _entries.isEmpty

  def resolve[A](
    key: ComponentParameterKey[A]
  ): Consequence[ComponentParameterResolution[A]] =
    _entries.iterator.flatMap(_.resolutionFor(key)).nextOption() match {
      case Some(resolution) => Consequence.success(resolution)
      case None =>
        ComponentParameterDiagnostics.undeclared(key.name)
    }

  private[cncf] def diagnosticSummaries: Vector[ComponentParameterDiagnosticSummary] =
    _entries.map(_.diagnosticSummary)
}

object ComponentInitializationParameters {
  val empty: ComponentInitializationParameters =
    new ComponentInitializationParameters(Vector.empty)

  private[cncf] def create(
    declarations: Seq[ComponentParameterKey[?]],
    resolver: ComponentParameterResolver
  ): Consequence[ComponentInitializationParameters] = {
    val keys = declarations.toVector
    _validate_declarations(keys).flatMap { _ =>
      _sequence(keys.map(key => _resolve_entry(key, resolver))).map { entries =>
        new ComponentInitializationParameters(entries)
      }
    }
  }

  private sealed abstract class Entry {
    def diagnosticSummary: ComponentParameterDiagnosticSummary

    def resolutionFor[A](
      key: ComponentParameterKey[A]
    ): Option[ComponentParameterResolution[A]]
  }

  private final class TypedEntry[A](
    storedkey: ComponentParameterKey[A],
    resolution: ComponentParameterResolution[A]
  ) extends Entry {
    def diagnosticSummary: ComponentParameterDiagnosticSummary =
      ComponentParameterDiagnostics.summary(storedkey, resolution)

    def resolutionFor[B](
      key: ComponentParameterKey[B]
    ): Option[ComponentParameterResolution[B]] =
      if (storedkey eq key)
        Some(resolution.asInstanceOf[ComponentParameterResolution[B]])
      else
        None
  }

  private def _resolve_entry[A](
    key: ComponentParameterKey[A],
    resolver: ComponentParameterResolver
  ): Consequence[Entry] =
    resolver.resolve(key).map(new TypedEntry(key, _))

  private def _validate_declarations(
    keys: Vector[ComponentParameterKey[?]]
  ): Consequence[Unit] = {
    val duplicate = keys.groupBy(_.name).collectFirst {
      case (name, values) if values.size > 1 => name
    }
    duplicate.fold(Consequence.unit) { name =>
      ComponentParameterDiagnostics.duplicateDeclaration(name)
    }
  }

  private def _sequence[A](
    consequences: Vector[Consequence[A]]
  ): Consequence[Vector[A]] =
    consequences.foldLeft(Consequence.success(Vector.empty[A])) { (acc, consequence) =>
      acc.flatMap(values => consequence.map(values :+ _))
    }
}
