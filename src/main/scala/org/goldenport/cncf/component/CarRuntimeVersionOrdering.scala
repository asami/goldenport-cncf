package org.goldenport.cncf.component

/*
 * Shared ordering for packaged and development CAR runtime ranges.
 *
 * @since   Jul. 29, 2026
 * @version Jul. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[component] object CarRuntimeVersionOrdering {
  def compare(leftVersion: String, rightVersion: String): Int = {
    def _parts_(value: String): Vector[String] =
      value.split("[.\\-+_]").toVector.map(_.trim).filter(_.nonEmpty)
    def _number_(value: String): Option[BigInt] =
      if (value.forall(_.isDigit)) Some(BigInt(value)) else None
    def _compare_part_(left: String, right: String): Int =
      (_number_(left), _number_(right)) match {
        case (Some(a), Some(b)) => a.compare(b)
        case (Some(_), None) => 1
        case (None, Some(_)) => -1
        case (None, None) => left.compareToIgnoreCase(right)
      }
    def _remaining_(parts: Vector[String], index: Int): Int =
      parts.drop(index).find(_.nonEmpty).map { value =>
        _number_(value) match {
          case Some(number) => number.signum
          case None => -1
        }
      }.getOrElse(0)

    val leftparts = _parts_(leftVersion)
    val rightparts = _parts_(rightVersion)
    (0 until math.max(leftparts.length, rightparts.length)).foldLeft(0) {
      case (0, index) if index >= leftparts.length => -_remaining_(rightparts, index)
      case (0, index) if index >= rightparts.length => _remaining_(leftparts, index)
      case (0, index) => _compare_part_(leftparts(index), rightparts(index))
      case (result, _) => result
    }
  }
}
