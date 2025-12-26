package org.simplemodeling.componentframework.context

import scala.util.Try
import cats.~>
import cats.Id
import org.simplemodeling.componentframework.unitofwork.UnitOfWork
import org.simplemodeling.componentframework.unitofwork.UnitOfWork.UnitOfWorkOp

/*
 * @since   Dec. 21, 2025
 * @version Dec. 21, 2025
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
