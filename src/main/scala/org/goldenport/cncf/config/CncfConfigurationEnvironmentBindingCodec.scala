package org.goldenport.cncf.config

import java.nio.ByteBuffer
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import java.text.Normalizer

import scala.collection.mutable.ArrayBuffer

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.configuration.{CanonicalParameterId, ConfigurationBindingReference}

/*
 * @since   Aug.  3, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationEnvironmentBindingCodec private (
  catalog: CncfConfigurationParameterCatalog
) {
  private val _prefix = "TEXTUS_BINDING_"
  def decode(
    value: String
  ): Consequence[ConfigurationBindingReference[CncfConfigurationTarget]] =
    _split(value).flatMap { case (marker, fields) =>
      for {
        targetfieldcount <- _target_field_count(marker)
        target <- _decode_target(marker, fields.take(targetfieldcount))
        parameterid <- _decode_parameter(fields.drop(targetfieldcount).mkString("_"))
        reference <- ConfigurationBindingReference.create(parameterid, target)
        admitted <- _admit(reference)
      } yield admitted
    }

  private def _target_field_count(marker: String): Consequence[Int] =
    marker match {
      case "G" => Consequence.success(0)
      case "C" => Consequence.success(1)
      case "S" => Consequence.success(2)
      case "I" => Consequence.success(4)
      case _ => Consequence.configurationInvalid("CNCF configuration environment binding target is invalid")
    }

  def encode(
    value: ConfigurationBindingReference[CncfConfigurationTarget]
  ): Consequence[String] =
    _admit(value).flatMap { reference =>
      _encode_target(reference.target).map { case (marker, fields) =>
        _prefix + (marker +: (fields :+ _encode_parameter(reference.parameterId))).mkString("_")
      }
    }

  private def _split(value: String): Consequence[(String, Vector[String])] =
    Option(value).filter(_.startsWith(_prefix)).fold[Consequence[(String, Vector[String])]](
      Consequence.configurationInvalid("CNCF configuration environment binding name is invalid")
    ) { text =>
      val fields = text.drop(_prefix.length).split("_", -1).toVector
      if (fields.size < 2 || fields.exists(_.isEmpty))
        Consequence.configurationInvalid("CNCF configuration environment binding name is invalid")
      else
        Consequence.success(fields.head -> fields.tail)
    }

  private def _encode_target(
    value: CncfConfigurationTarget
  ): Consequence[(String, Vector[String])] =
    value match {
      case CncfConfigurationTarget.Global => Consequence.success("G" -> Vector.empty)
      case component: CncfConfigurationTarget.ComponentClass =>
        Consequence.success("C" -> Vector(_encode_identity(component.component.name)))
      case subsystem: CncfConfigurationTarget.SubsystemInstance =>
        Consequence.success("S" -> Vector(
          _encode_identity(subsystem.subsystem.subsystem),
          _encode_identity(subsystem.subsystem.instance)
        ))
      case component: CncfConfigurationTarget.ComponentInstance =>
        Consequence.success("I" -> Vector(
          _encode_identity(component.subsystem.subsystem),
          _encode_identity(component.subsystem.instance),
          _encode_identity(component.component.name),
          _encode_identity(component.component.instance)
        ))
      case _ => Consequence.configurationInvalid("CNCF configuration environment binding target is invalid")
    }

  private def _decode_target(
    marker: String,
    fields: Vector[String]
  ): Consequence[CncfConfigurationTarget] =
    (marker, fields) match {
      case ("G", Vector()) => Consequence.success(CncfConfigurationTarget.Global)
      case ("C", Vector(component)) =>
        _decode_component_id(component).flatMap(CncfConfigurationTarget.ComponentClass.create)
      case ("S", Vector(subsystem, instance)) =>
        for {
          subsystemvalue <- _decode_subsystem_identity(subsystem, "subsystem")
          instancevalue <- _decode_subsystem_identity(instance, "instance")
          identity <- SubsystemInstanceId.create(subsystemvalue, instancevalue)
          target <- CncfConfigurationTarget.SubsystemInstance.create(identity)
        } yield target
      case ("I", Vector(subsystem, instance, component, componentinstance)) =>
        for {
          subsystemvalue <- _decode_subsystem_identity(subsystem, "subsystem")
          instancevalue <- _decode_subsystem_identity(instance, "instance")
          componentid <- _decode_component_id(component)
          componentinstancevalue <- _decode_identity(componentinstance)
          identity <- SubsystemInstanceId.create(subsystemvalue, instancevalue)
          instanceid <- ComponentInstanceId.createC(componentid, componentinstancevalue)
          target <- CncfConfigurationTarget.ComponentInstance.create(
            identity,
            instanceid
          )
        } yield target
      case _ => Consequence.configurationInvalid("CNCF configuration environment binding target is invalid")
    }

  private def _decode_parameter(value: String): Consequence[CanonicalParameterId] =
    Option(value).filter(_.nonEmpty).fold[Consequence[CanonicalParameterId]](
      Consequence.configurationInvalid("CNCF configuration environment parameter token is invalid")
    ) { token =>
      val builder = new StringBuilder
      var index = 0
      var valid = true
      while (valid && index < token.length) {
        token.charAt(index) match {
          case ch if ch >= 'A' && ch <= 'Z' => builder.append(ch.toLower)
          case ch if ch >= '0' && ch <= '9' => builder.append(ch)
          case '_' if index + 1 < token.length && token.charAt(index + 1) == 'D' =>
            builder.append('.')
            index += 1
          case '_' if index + 1 < token.length && token.charAt(index + 1) == 'H' =>
            builder.append('-')
            index += 1
          case _ => valid = false
        }
        index += 1
      }
      if (!valid)
        Consequence.configurationInvalid("CNCF configuration environment parameter token is invalid")
      else
        CanonicalParameterId.parse(builder.toString).flatMap { parameterid =>
          if (_encode_parameter(parameterid) == token)
            Consequence.success(parameterid)
          else
            Consequence.configurationInvalid("CNCF configuration environment parameter token is not canonical")
        }
    }

  private def _encode_parameter(value: CanonicalParameterId): String =
    value.value.map {
      case '.' => "_D"
      case '-' => "_H"
      case ch => ch.toUpper.toString
    }.mkString

  private def _decode_component_id(value: String): Consequence[ComponentId] =
    _decode_identity(value).flatMap(ComponentId.parseC)

  private def _decode_subsystem_identity(
    value: String,
    label: String
  ): Consequence[String] =
    _decode_identity(value).flatMap { identity =>
      if (identity == Normalizer.normalize(identity, Normalizer.Form.NFC))
        Consequence.success(identity)
      else
        Consequence.configurationInvalid(s"CNCF configuration $label identity is not NFC")
    }

  private def _decode_identity(value: String): Consequence[String] =
    Option(value).filter(x => x.nonEmpty && x.length % 2 == 0 && x.forall(_is_upper_hex)).fold[Consequence[String]](
      Consequence.configurationInvalid("CNCF configuration environment identity token is invalid")
    ) { token =>
      val bytes = ArrayBuffer.empty[Byte]
      token.grouped(2).foreach(x => bytes += Integer.parseInt(x, 16).toByte)
      try {
        val decoder = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
        val identity = decoder.decode(ByteBuffer.wrap(bytes.toArray)).toString
        if (_encode_identity(identity) == token)
          Consequence.success(identity)
        else
          Consequence.configurationInvalid("CNCF configuration environment identity token is not canonical")
      } catch {
        case _: CharacterCodingException => Consequence.configurationInvalid("CNCF configuration environment identity requires UTF-8")
      }
    }

  private def _encode_identity(value: String): String =
    value.getBytes(StandardCharsets.UTF_8).map(byte => f"${byte & 0xff}%02X").mkString

  private def _admit(
    value: ConfigurationBindingReference[CncfConfigurationTarget]
  ): Consequence[ConfigurationBindingReference[CncfConfigurationTarget]] =
    if (value == null)
      Consequence.configurationInvalid("CNCF configuration environment binding reference is required")
    else
      catalog.canonicalDefinition(value.parameterId).flatMap { definition =>
        if (definition.allowedTargetKinds.contains(CncfConfigurationTargetKind.of(value.target)))
          Consequence.success(value)
        else
          Consequence.configurationInvalid("CNCF configuration environment target is not admitted for canonical parameter id")
      }

  private def _is_upper_hex(value: Char): Boolean =
    value >= '0' && value <= '9' || value >= 'A' && value <= 'F'
}

object CncfConfigurationEnvironmentBindingCodec {
  def create(
    catalog: CncfConfigurationParameterCatalog
  ): Consequence[CncfConfigurationEnvironmentBindingCodec] =
    Option(catalog).fold[Consequence[CncfConfigurationEnvironmentBindingCodec]](
      Consequence.configurationInvalid("CNCF configuration parameter catalog is required")
    )(x => Consequence.success(new CncfConfigurationEnvironmentBindingCodec(x)))
}
