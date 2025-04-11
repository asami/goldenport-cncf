package org.simplemodeling.componentframework

sealed trait Consequence[+T] {
}

object Consequence {
  case class Success[T](value: T) extends Consequence[T]
  case class Error[T](conclusion: Conclusion) extends Consequence[T]
}
