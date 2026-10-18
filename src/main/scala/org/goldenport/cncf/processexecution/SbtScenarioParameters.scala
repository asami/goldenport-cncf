package org.goldenport.cncf.processexecution

import org.goldenport.Consequence

/** Bounded literal scenario data for the existing TestOnly adapter.
 * @since   Oct. 19, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
object SbtScenarioParameters {
  enum Value {
    case Text(value: String)
    case BooleanValue(value: Boolean)
    case Integer(value: Long)
  }

  def validateC(values: Map[String, Value]): Consequence[Map[String, Value]] =
    if (values == null || values.size > 16 || values.exists { case (name, value) =>
        name == null || !name.matches("[a-z][a-z0-9-]{0,63}") || value == null || (value match {
          case Value.Text(text) => text == null || text.isEmpty || text.length > 512 ||
            text.exists(_.isControl) || text.exists(x => x == '"' || x == '\\' || x == ';')
          case _ => false
        })
      }) {
      Consequence.argumentPolicyViolation("parameters", "sbt.execution.parameters",
        "at most 16 named nonnull typed bounded literal values", "invalid")
    } else Consequence.success(values)

  private[cncf] def renderArguments(values: Map[String, Value]): Vector[String] =
    values.toVector.sortBy(_._1).map { case (name, value) =>
      val text = value match {
        case Value.Text(text) => text
        case Value.BooleanValue(boolean) => boolean.toString
        case Value.Integer(integer) => integer.toString
      }
      s"\"-Dcncf.validation.input.$name=$text\""
    }
}
