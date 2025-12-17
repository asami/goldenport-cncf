package org.simplemodeling.componentframework

/*
 * @since   Apr. 11, 2025
 * @version Apr. 11, 2025
 * @author  ASAMI, Tomoharu
 */
sealed trait Consequence[+T] {
}

object Consequence {
  case class Success[T](value: T) extends Consequence[T]
  case class Error[T](conclusion: Conclusion) extends Consequence[T]
}
