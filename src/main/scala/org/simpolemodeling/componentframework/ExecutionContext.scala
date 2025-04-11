package org.simplemodeling.componentframework

import scala.util.{Try, Success, Failure}
import cats._
import cats.data._
import cats.free._
import cats.implicits._
import org.simplemodeling.componentframework.unitofwork.UnitOfWork
import org.simplemodeling.componentframework.unitofwork.UnitOfWork._

class ExecutionContext {
  def unitOfWork: UnitOfWork = ???
  def unitOfWorkInterpreter[T]:(UnitOfWorkOp ~> Id) = ???
  def unitOfWorkTryInterpreter[T]:(UnitOfWorkOp ~> Try) = ???
  def unitOfWorkEitherInterpreter[T](op: UnitOfWorkOp[T]): Either[Throwable, T] = ???

  def commit(): Unit = ???
  def abort(): Unit = ???
  def dispose(): Unit = ???
}

object ExecutionContext {
  def create() = new ExecutionContext()

  def createWithCurrentDateTime(dt: String): ExecutionContext = create()
}
