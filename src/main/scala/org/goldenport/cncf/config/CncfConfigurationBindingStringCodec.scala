package org.goldenport.cncf.config

import java.nio.ByteBuffer
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import java.text.Normalizer

import scala.collection.mutable.ArrayBuffer

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.configuration.{ConfigurationBindingQualifierCodec, ConfigurationBindingReference, ConfigurationBindingStringCodec}

/*
 * @since   Aug.  3, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfConfigurationBindingQualifierCodec
  extends ConfigurationBindingQualifierCodec[CncfConfigurationTarget] {
  def decode(value: Option[String]): Consequence[CncfConfigurationTarget] =
    value match {
      case None => Consequence.success(CncfConfigurationTarget.Global)
      case Some(text) => _decode_qualified(text)
    }

  def encode(value: CncfConfigurationTarget): Consequence[Option[String]] =
    value match {
      case CncfConfigurationTarget.Global => Consequence.success(None)
      case component: CncfConfigurationTarget.ComponentClass =>
        Consequence.success(Some(s"c/${_encode_segment(component.component.name)}"))
      case subsystem: CncfConfigurationTarget.SubsystemInstance =>
        Consequence.success(Some(s"s/${_encode_segment(subsystem.subsystem.subsystem)}/${_encode_segment(subsystem.subsystem.instance)}"))
      case component: CncfConfigurationTarget.ComponentInstance =>
        Consequence.success(Some(s"i/${_encode_segment(component.subsystem.subsystem)}/${_encode_segment(component.subsystem.instance)}/${_encode_segment(component.component.name)}/${_encode_segment(component.component.instance)}"))
      case _ => Consequence.configurationInvalid("CNCF configuration binding target is invalid")
    }

  private def _decode_qualified(value: String): Consequence[CncfConfigurationTarget] =
    Option(value).filter(_.nonEmpty).fold[Consequence[CncfConfigurationTarget]](
      Consequence.configurationInvalid("CNCF configuration binding qualifier is invalid")
    ) { text =>
      val parts = text.split("/", -1).toVector
      parts.headOption match {
        case Some("c") if parts.size == 2 =>
          _decode_component_id(parts(1)).flatMap(CncfConfigurationTarget.ComponentClass.create)
        case Some("s") if parts.size == 3 =>
          for {
            subsystem <- _decode_subsystem_label(parts(1), "subsystem")
            instance <- _decode_subsystem_label(parts(2), "instance")
            identity <- SubsystemInstanceId.create(subsystem, instance)
            target <- CncfConfigurationTarget.SubsystemInstance.create(identity)
          } yield target
        case Some("i") if parts.size == 5 =>
          for {
            subsystem <- _decode_subsystem_label(parts(1), "subsystem")
            subsysteminstance <- _decode_subsystem_label(parts(2), "instance")
            componentid <- _decode_component_id(parts(3))
            componentinstance <- _decode_segment(parts(4))
            identity <- SubsystemInstanceId.create(subsystem, subsysteminstance)
            instanceid <- ComponentInstanceId.createC(componentid, componentinstance)
            target <- CncfConfigurationTarget.ComponentInstance.create(identity, instanceid)
          } yield target
        case _ => Consequence.configurationInvalid("CNCF configuration binding qualifier is invalid")
      }
    }

  private def _decode_component_id(value: String): Consequence[ComponentId] =
    _decode_segment(value).flatMap(ComponentId.parseC)

  private def _decode_subsystem_label(
    value: String,
    label: String
  ): Consequence[String] =
    _decode_segment(value).flatMap { text =>
      if (text == Normalizer.normalize(text, Normalizer.Form.NFC))
        Consequence.success(text)
      else
        Consequence.configurationInvalid(s"CNCF configuration $label identity is not NFC")
    }

  private def _decode_segment(value: String): Consequence[String] =
    Option(value).filter(_.nonEmpty).fold[Consequence[String]](
      Consequence.configurationInvalid("CNCF configuration binding qualifier segment is invalid")
    ) { text =>
      val bytes = ArrayBuffer.empty[Byte]
      var index = 0
      var valid = true
      while (valid && index < text.length) {
        val ch = text.charAt(index)
        if (_is_unreserved(ch)) {
          bytes += ch.toByte
          index += 1
        } else if (ch == '%' && index + 2 < text.length && _is_upper_hex(text.charAt(index + 1)) && _is_upper_hex(text.charAt(index + 2))) {
          val byte = Integer.parseInt(text.substring(index + 1, index + 3), 16).toByte
          if (_is_unreserved((byte & 0xff).toChar))
            valid = false
          else {
            bytes += byte
            index += 3
          }
        } else {
          valid = false
        }
      }
      if (!valid)
        Consequence.configurationInvalid("CNCF configuration binding qualifier percent encoding is invalid")
      else
        _decode_utf8(bytes.toArray)
    }

  private def _decode_utf8(bytes: Array[Byte]): Consequence[String] =
    try {
      val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
      Consequence.success(decoder.decode(ByteBuffer.wrap(bytes)).toString)
    } catch {
      case _: CharacterCodingException => Consequence.configurationInvalid("CNCF configuration binding qualifier requires UTF-8")
    }

  private def _encode_segment(value: String): String =
    value.getBytes(StandardCharsets.UTF_8).map { byte =>
      val unsigned = byte & 0xff
      if (_is_unreserved(unsigned.toChar)) unsigned.toChar.toString
      else "%" + f"$unsigned%02X"
    }.mkString

  private def _is_unreserved(value: Char): Boolean =
    value >= 'A' && value <= 'Z' ||
      value >= 'a' && value <= 'z' ||
      value >= '0' && value <= '9' ||
      value == '_' || value == '-'

  private def _is_upper_hex(value: Char): Boolean =
    value >= '0' && value <= '9' || value >= 'A' && value <= 'F'
}

final class CncfConfigurationBindingStringCodec private (
  catalog: CncfConfigurationParameterCatalog
) {
  private val _codec =
    ConfigurationBindingStringCodec.create(CncfConfigurationBindingQualifierCodec).getOrElse(
      throw new IllegalStateException("CNCF configuration binding qualifier codec is invalid")
    )

  def decode(
    value: String
  ): Consequence[ConfigurationBindingReference[CncfConfigurationTarget]] =
    _codec.decode(value).flatMap(_admit)

  def encode(
    value: ConfigurationBindingReference[CncfConfigurationTarget]
  ): Consequence[String] =
    _admit(value).flatMap(_codec.encode)

  private def _admit(
    value: ConfigurationBindingReference[CncfConfigurationTarget]
  ): Consequence[ConfigurationBindingReference[CncfConfigurationTarget]] =
    if (value == null)
      Consequence.configurationInvalid("CNCF configuration binding reference is required")
    else
      catalog.canonicalDefinition(value.parameterId).flatMap { definition =>
        if (definition.allowedTargetKinds.contains(CncfConfigurationTargetKind.of(value.target)))
          Consequence.success(value)
        else
          Consequence.configurationInvalid("CNCF configuration binding target is not admitted for canonical parameter id")
      }
}

object CncfConfigurationBindingStringCodec {
  def create(
    catalog: CncfConfigurationParameterCatalog
  ): Consequence[CncfConfigurationBindingStringCodec] =
    Option(catalog).fold[Consequence[CncfConfigurationBindingStringCodec]](
      Consequence.configurationInvalid("CNCF configuration parameter catalog is required")
    )(x => Consequence.success(new CncfConfigurationBindingStringCodec(x)))
}
