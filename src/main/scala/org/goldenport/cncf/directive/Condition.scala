package org.goldenport.cncf.directive

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}

/*
 * @since   Mar. 16, 2026
 * @version Mar. 17, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait Condition[-A] {
  def accepts(value: A): Boolean
}

object Condition {
  case object Any extends Condition[Any] {
    def accepts(value: Any): Boolean = true
  }

  final case class Is[A](expected: A) extends Condition[A] {
    def accepts(value: A): Boolean = value == expected
  }

  final case class In[A](candidates: Set[A]) extends Condition[A] {
    def accepts(value: A): Boolean = candidates.contains(value)
  }

  final case class Predicate[A](f: A => Boolean) extends Condition[A] {
    def accepts(value: A): Boolean = f(value)
  }

  def any[A]: Condition[A] =
    Any.asInstanceOf[Condition[A]]

  def is[A](value: A): Condition[A] =
    Is(value)

  def in[A](values: Set[A]): Condition[A] =
    In(values)

  def predicate[A](f: A => Boolean): Condition[A] =
    Predicate(f)

  private def _encoder[A](using ev: Encoder[A]): Encoder.AsObject[Condition[A]] =
    Encoder.AsObject.instance {
      case Any =>
        JsonObject(
          "op" -> Json.fromString("any")
        )
      case Is(expected: A @unchecked) =>
        JsonObject(
          "op" -> Json.fromString("is"),
          "expected" -> ev(expected)
        )
      case In(candidates: Set[A @unchecked]) =>
        JsonObject(
          "op" -> Json.fromString("in"),
          "candidates" -> Encoder.encodeVector[A].apply(candidates.toVector)
        )
      case Predicate(_) =>
        throw new IllegalArgumentException("Condition.Predicate is not serializable")
    }

  private def _decoder[A](using ev: Decoder[A]): Decoder[Condition[A]] =
    Decoder.instance { c =>
      c.downField("op").as[String].flatMap {
        case "any" =>
          Right(Condition.any[A])
        case "is" =>
          c.downField("expected").as[A].map(Condition.is)
        case "in" =>
          c.downField("candidates").as[Vector[A]].map(xs => Condition.in(xs.toSet))
        case "predicate" =>
          Left(DecodingFailure("Condition.Predicate is not supported for decoding", c.history))
        case other =>
          Left(DecodingFailure(s"unknown condition op: $other", c.history))
      }
    }

  given [A](using enc: Encoder[A], dec: Decoder[A]): Codec.AsObject[Condition[A]] =
    Codec.AsObject.from(_decoder(using dec), _encoder(using enc))
}
