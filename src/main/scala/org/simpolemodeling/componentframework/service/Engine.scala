package org.simplemodeling.componentframework.service

import scala.util.{Try, Failure}
import scala.util.control.NonFatal
import org.simplemodeling.componentframework.*

/*
 * @since   Apr. 11, 2025
 * @version Apr. 15, 2025
 * @author  ASAMI, Tomoharu
 */
class Engine() {
  def run[R <: Result](op: OperationCall[R])(using ec: ExecutionContext): Try[R] = try {
    observe_enter(op)
    val r = op()
    observe_leave(op, r)
    r
  } catch {
    case e: Throwable =>
      observe_leave(op, e)
      Failure(e)
  } finally {
  }

  def execute[R <: Result](op: OperationCall[R])(using ec: ExecutionContext): Try[R] = {
    try {
      observe_enter(op)
      val r = op()
      ec.commit()
      observe_leave(op, r)
      r
    } catch {
      case NonFatal(e) =>
        ec.abort()
        observe_leave(op, e)
        Failure(e)
    } finally {
      ec.dispose()
    }
  }

  def toExecutionContext(p: String): Try[ExecutionContext] = ???

  def toExecutionContext(p: Array[Byte]): Try[ExecutionContext] = ???


  protected def observe_enter[R <: Result](op: OperationCall[R]): Unit = {
  }

  protected def observe_leave[R <: Result](op: OperationCall[R], result: Try[R]): Unit = {
  }

  protected def observe_leave[R <: Result](op: OperationCall[R], e: Throwable): Unit = {
  }
}

object Engine {
  def create(): Engine = new Engine()
}
