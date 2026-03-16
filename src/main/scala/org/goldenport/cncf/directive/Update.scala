package org.goldenport.cncf.directive

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}

/*
 * @since   Mar. 16, 2026
 * @version Mar. 17, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait Update[+A] {
  def fold[B](
    onNoop: => B,
    onSet: A => B,
    onSetNull: => B
  ): B

  def isNoop: Boolean =
    fold(true, _ => false, false)

  def isSet: Boolean =
    fold(false, _ => true, false)

  def isSetNull: Boolean =
    fold(false, _ => false, true)
}

object Update {
  // Marker trait for Cozy-generated update directive objects:
  // case class Person(...) extends Update.Shape
  trait PatchShape extends Product
  trait Shape extends PatchShape

  case object Noop extends Update[Nothing] {
    def fold[B](onNoop: => B, onSet: Nothing => B, onSetNull: => B): B =
      onNoop
  }

  final case class SetValue[A](value: A) extends Update[A] {
    def fold[B](onNoop: => B, onSet: A => B, onSetNull: => B): B =
      onSet(value)
  }

  case object SetNull extends Update[Nothing] {
    def fold[B](onNoop: => B, onSet: Nothing => B, onSetNull: => B): B =
      onSetNull
  }

  def noop[A]: Update[A] =
    Noop

  def set[A](value: A): Update[A] =
    SetValue(value)

  def setNull[A]: Update[A] =
    SetNull

  def hasChange(patch: PatchShape): Boolean =
    patch.productIterator.exists {
      case u: Update[?] => !u.isNoop
      case _ => false
    }

  private def _encoder[A](using ev: Encoder[A]): Encoder.AsObject[Update[A]] =
    Encoder.AsObject.instance {
      case Noop =>
        JsonObject(
          "op" -> Json.fromString("noop")
        )
      case SetValue(value: A @unchecked) =>
        JsonObject(
          "op" -> Json.fromString("set"),
          "value" -> ev(value)
        )
      case SetNull =>
        JsonObject(
          "op" -> Json.fromString("setNull")
        )
    }

  private def _decoder[A](using ev: Decoder[A]): Decoder[Update[A]] =
    Decoder.instance { c =>
      c.downField("op").as[String].flatMap {
        case "noop" =>
          Right(Update.noop[A])
        case "set" =>
          c.downField("value").as[A].map(Update.set)
        case "setNull" =>
          Right(Update.setNull[A])
        case other =>
          Left(DecodingFailure(s"unknown update op: $other", c.history))
      }
    }

  given [A](using enc: Encoder[A], dec: Decoder[A]): Codec.AsObject[Update[A]] =
    Codec.AsObject.from(_decoder(using dec), _encoder(using enc))
}
