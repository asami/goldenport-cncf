package org.goldenport.cncf.context

import scala.util.Try
import cats.~>
import cats.Id
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.unitofwork.UnitOfWorkOp

/*
 * @since   Dec. 21, 2025
 * @version Jan. 10, 2026
 * @author  ASAMI, Tomoharu
 */
trait RuntimeContext {
  def unitOfWork: UnitOfWork

  def unitOfWorkInterpreter[T]: (UnitOfWorkOp ~> Id)
  def unitOfWorkTryInterpreter[T]: (UnitOfWorkOp ~> Try)
  def unitOfWorkEitherInterpreter[T](op: UnitOfWorkOp[T]): Either[Throwable, T]

  def commit(): Unit
  def abort(): Unit
  def dispose(): Unit

  def toToken: String
}
