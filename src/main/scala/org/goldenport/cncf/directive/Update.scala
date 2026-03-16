package org.goldenport.cncf.directive

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
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
}
