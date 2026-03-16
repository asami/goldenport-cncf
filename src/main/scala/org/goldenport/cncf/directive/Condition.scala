package org.goldenport.cncf.directive

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
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
}
