package org.goldenport.cncf.resource

import java.io.InputStream
import java.nio.charset.StandardCharsets
import scala.util.Using
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ResourceReadLimits(maximumBytes: Int = 1048576) {
  require(maximumBytes >= 1 && maximumBytes <= 16777216,
    "resource read maximumBytes must be between 1 and 16777216")
}

object ResourceReadLimits {
  val default: ResourceReadLimits = ResourceReadLimits()

  def createC(maximumBytes: Int): Consequence[ResourceReadLimits] =
    if (maximumBytes >= 1 && maximumBytes <= 16777216)
      Consequence.success(ResourceReadLimits(maximumBytes))
    else
      Consequence.argumentInvalid("maximumBytes", "an integer between 1 and 16777216", maximumBytes)
}

/** Acquisition owns and closes its stream, including when the finite read fails. */
private[cncf] object ResourceReadAcquisition {
  def readC(stream: InputStream, limits: ResourceReadLimits): Consequence[Vector[Byte]] =
    try {
      val bytes = Using.resource(stream)(_.readNBytes(limits.maximumBytes + 1))
      if (bytes.length > limits.maximumBytes)
        Consequence.resourceInvalid("resource exceeds the runtime byte limit")
      else
        Consequence.success(bytes.toVector)
    } catch {
      case NonFatal(_) => Consequence.resourceInvalid("configured resource cannot be read")
    }

  /** Count UTF-8 bytes before allocating the encoded copy of a preowned test value. */
  def utf8C(value: String, limits: ResourceReadLimits): Consequence[Vector[Byte]] = {
    var index = 0
    var count = 0L
    while (index < value.length && count <= limits.maximumBytes) {
      val character = value.charAt(index)
      if (character <= 0x7f) count += 1
      else if (character <= 0x7ff) count += 2
      else if (Character.isHighSurrogate(character) && index + 1 < value.length &&
        Character.isLowSurrogate(value.charAt(index + 1))) {
        count += 4
        index += 1
      } else if (Character.isSurrogate(character)) count += 1 // UTF-8 encoder replacement byte
      else count += 3
      index += 1
    }
    if (count > limits.maximumBytes)
      Consequence.resourceInvalid("resource exceeds the runtime byte limit")
    else
      Consequence.success(value.getBytes(StandardCharsets.UTF_8).toVector)
  }
}
