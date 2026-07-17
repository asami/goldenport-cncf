package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationValue}

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
enum ComponentConfigurationRequirement {
  case Required
  case Optional
}

enum ComponentConfigurationConfidentiality {
  case Public
  case Confidential
  case Secret
}

enum ComponentConfigurationProvenance {
  case Component
  case Subsystem
  case Runtime
  case Absent
}

trait ComponentConfigurationDecoder[A] {
  def decode(value: ConfigurationValue): Consequence[A]
}

object ComponentConfigurationDecoder {
  val string: ComponentConfigurationDecoder[String] =
    new ComponentConfigurationDecoder[String] {
      def decode(value: ConfigurationValue): Consequence[String] =
        value match {
          case ConfigurationValue.StringValue(v) => Consequence.success(v)
          case _ => Consequence.configurationInvalid("declared component configuration requires a string value")
        }
    }

  val int: ComponentConfigurationDecoder[Int] =
    new ComponentConfigurationDecoder[Int] {
      def decode(value: ConfigurationValue): Consequence[Int] =
        value match {
          case ConfigurationValue.NumberValue(v) if v.isValidInt =>
            Consequence.success(v.toInt)
          case ConfigurationValue.StringValue(v) =>
            v.toIntOption.fold[Consequence[Int]](
              Consequence.configurationInvalid("declared component configuration requires an integer value")
            )(Consequence.success)
          case _ =>
            Consequence.configurationInvalid("declared component configuration requires an integer value")
        }
    }

  val boolean: ComponentConfigurationDecoder[Boolean] =
    new ComponentConfigurationDecoder[Boolean] {
      def decode(value: ConfigurationValue): Consequence[Boolean] =
        value match {
          case ConfigurationValue.BooleanValue(v) => Consequence.success(v)
          case ConfigurationValue.StringValue(v) =>
            v.trim.toLowerCase(java.util.Locale.ROOT) match {
              case "true" | "yes" | "on" | "1" => Consequence.success(true)
              case "false" | "no" | "off" | "0" => Consequence.success(false)
              case _ => Consequence.configurationInvalid("declared component configuration requires a boolean value")
            }
          case _ =>
            Consequence.configurationInvalid("declared component configuration requires a boolean value")
        }
    }

  private[config] val _secret_reference: ComponentConfigurationDecoder[SecretReference] =
    new ComponentConfigurationDecoder[SecretReference] {
      def decode(value: ConfigurationValue): Consequence[SecretReference] =
        value match {
          case ConfigurationValue.StringValue(v) => SecretReference.fromConfiguration(v)
          case _ => Consequence.configurationInvalid("declared component configuration requires a secret reference")
        }
    }
}

final class ComponentConfigurationKey[A] private (
  val name: String,
  val decoder: ComponentConfigurationDecoder[A],
  val requirement: ComponentConfigurationRequirement,
  val confidentiality: ComponentConfigurationConfidentiality
)

object ComponentConfigurationKey {
  def required[A](
    name: String,
    decoder: ComponentConfigurationDecoder[A]
  ): ComponentConfigurationKey[A] =
    _create(name, decoder, ComponentConfigurationRequirement.Required, ComponentConfigurationConfidentiality.Public)

  def optional[A](
    name: String,
    decoder: ComponentConfigurationDecoder[A]
  ): ComponentConfigurationKey[A] =
    _create(name, decoder, ComponentConfigurationRequirement.Optional, ComponentConfigurationConfidentiality.Public)

  def requiredString(name: String): ComponentConfigurationKey[String] =
    required(name, ComponentConfigurationDecoder.string)

  def optionalString(name: String): ComponentConfigurationKey[String] =
    optional(name, ComponentConfigurationDecoder.string)

  def requiredInt(name: String): ComponentConfigurationKey[Int] =
    required(name, ComponentConfigurationDecoder.int)

  def optionalInt(name: String): ComponentConfigurationKey[Int] =
    optional(name, ComponentConfigurationDecoder.int)

  def requiredBoolean(name: String): ComponentConfigurationKey[Boolean] =
    required(name, ComponentConfigurationDecoder.boolean)

  def requiredSecretReference(name: String): ComponentConfigurationKey[SecretReference] =
    _create(
      name,
      ComponentConfigurationDecoder._secret_reference,
      ComponentConfigurationRequirement.Required,
      ComponentConfigurationConfidentiality.Secret
    )

  def optionalSecretReference(name: String): ComponentConfigurationKey[SecretReference] =
    _create(
      name,
      ComponentConfigurationDecoder._secret_reference,
      ComponentConfigurationRequirement.Optional,
      ComponentConfigurationConfidentiality.Secret
    )

  def confidentialRequired(name: String): ComponentConfigurationKey[Nothing] =
    _create(
      name,
      new ComponentConfigurationDecoder[Nothing] {
        def decode(value: ConfigurationValue): Consequence[Nothing] =
          Consequence.configurationInvalid("confidential component configuration is not available through the component runtime boundary")
      },
      ComponentConfigurationRequirement.Required,
      ComponentConfigurationConfidentiality.Confidential
    )

  private def _create[A](
    name: String,
    decoder: ComponentConfigurationDecoder[A],
    requirement: ComponentConfigurationRequirement,
    confidentiality: ComponentConfigurationConfidentiality
  ): ComponentConfigurationKey[A] = {
    require(Option(name).exists(_.trim.nonEmpty), "component configuration key name is required")
    new ComponentConfigurationKey(name, decoder, requirement, confidentiality)
  }
}

final case class ComponentConfigurationResolution[A](
  value: Option[A],
  provenance: ComponentConfigurationProvenance
)

final case class ComponentConfigurationSources(
  component: Configuration = Configuration.empty,
  subsystem: Configuration = Configuration.empty,
  runtime: Configuration = Configuration.empty
)

final class ComponentConfigurationAccess(
  sources: ComponentConfigurationSources
) {
  def resolve[A](
    key: ComponentConfigurationKey[A]
  ): Consequence[ComponentConfigurationResolution[A]] =
    key.confidentiality match {
      case ComponentConfigurationConfidentiality.Confidential =>
        Consequence.configurationInvalid(
          s"confidential declared component configuration is not available through the component runtime boundary: ${key.name}"
        )
      case ComponentConfigurationConfidentiality.Public |
          ComponentConfigurationConfidentiality.Secret =>
        _resolve_public(key)
    }

  private def _resolve_public[A](
    key: ComponentConfigurationKey[A]
  ): Consequence[ComponentConfigurationResolution[A]] =
    _lookup(key.name) match {
      case Some((value, provenance)) =>
        key.decoder.decode(value).map(v => ComponentConfigurationResolution(Some(v), provenance))
      case None =>
        key.requirement match {
          case ComponentConfigurationRequirement.Required =>
            Consequence.configurationInvalid(
              s"required declared component configuration is missing: ${key.name}"
            )
          case ComponentConfigurationRequirement.Optional =>
            Consequence.success(
              ComponentConfigurationResolution(None, ComponentConfigurationProvenance.Absent)
            )
        }
    }

  private def _lookup(
    name: String
  ): Option[(ConfigurationValue, ComponentConfigurationProvenance)] =
    sources.component.get(name).map(_ -> ComponentConfigurationProvenance.Component)
      .orElse(sources.subsystem.get(name).map(_ -> ComponentConfigurationProvenance.Subsystem))
      .orElse(sources.runtime.get(name).map(_ -> ComponentConfigurationProvenance.Runtime))
}

object ComponentConfigurationAccess {
  def apply(
    sources: ComponentConfigurationSources
  ): ComponentConfigurationAccess =
    new ComponentConfigurationAccess(sources)
}
