package org.simplemodeling.componentframework

import scala.util.{Try, Success, Failure}
import cats.*
import cats.data.*
import cats.free.*
import cats.implicits.*
import org.simplemodeling.componentframework.unitofwork.UnitOfWork
import org.simplemodeling.componentframework.unitofwork.UnitOfWork.*

/*
 * @since   Apr. 11, 2025
 * @version Apr. 15, 2025
 * @author  ASAMI, Tomoharu
 */
class ExecutionContext {
  def unitOfWork: UnitOfWork = ???
  def unitOfWorkInterpreter[T]:(UnitOfWorkOp ~> Id) = ???
  def unitOfWorkTryInterpreter[T]:(UnitOfWorkOp ~> Try) = ???
  def unitOfWorkEitherInterpreter[T](op: UnitOfWorkOp[T]): Either[Throwable, T] = ???

  def commit(): Unit = ???
  def abort(): Unit = ???
  def dispose(): Unit = ???

  def toToken: String = ???
}

object ExecutionContext {
  def create() = new ExecutionContext()

  def createWithCurrentDateTime(dt: String): ExecutionContext = create()
}
