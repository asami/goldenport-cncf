package org.goldenport.cncf.processexecution

import org.goldenport.Consequence

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
enum ProcessArgumentSlot {
  case Literal(value: String)
  case LowerHex(lengths: Set[Int], prefix: String = "", suffix: String = "")

  def validateC: Consequence[Unit] = {
    def _text_valid_(text: String): Boolean = text != null && !text.exists(_.isControl)
    val valid = this match {
      case Literal(value) => _text_valid_(value)
      case LowerHex(lengths, prefix, suffix) => lengths != null && lengths.nonEmpty &&
        lengths.forall(_ > 0) && _text_valid_(prefix) && _text_valid_(suffix)
    }
    if (valid) Consequence.unit else ProcessArgumentTemplate.invalidC
  }

  def matches(argument: String): Boolean = {
    if (argument == null) false
    else this match {
      case Literal(value) => argument == value
      case LowerHex(lengths, prefix, suffix) =>
        if (lengths == null || prefix == null || suffix == null ||
            !argument.startsWith(prefix) || !argument.endsWith(suffix) ||
            argument.length <= prefix.length + suffix.length) false
        else {
          val token = argument.substring(prefix.length, argument.length - suffix.length)
          lengths.contains(token.length) && token.forall(c =>
            (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))
        }
    }
  }
}

/** One complete ordered request argument vector, excluding the runtime prefix. */
final case class ProcessArgumentTemplate(slots: Vector[ProcessArgumentSlot]) {
  def validateC: Consequence[Unit] =
    if (slots == null || slots.exists(_ == null)) ProcessArgumentTemplate.invalidC
    else slots.foldLeft(Consequence.unit)((z, slot) => z.flatMap(_ => slot.validateC))

  def matches(arguments: Vector[String]): Boolean =
    slots != null && arguments != null && slots.size == arguments.size &&
      slots.zip(arguments).forall { case (slot, argument) => slot != null && slot.matches(argument) }
}

object ProcessArgumentTemplate {
  private[processexecution] def invalidC: Consequence[Unit] =
    Consequence.argumentPolicyViolation("argumentTemplates", "process.execution.argument-template",
      "complete runtime-declared literal and positive-length lower-hex slots", "invalid")
}
