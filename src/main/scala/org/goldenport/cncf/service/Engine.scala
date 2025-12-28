package org.goldenport.cncf.service

import cats.syntax.all.*

import org.goldenport.Consequence
import org.goldenport.Consequence.{Failure, Success}
import org.goldenport.Conclusion
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.security.AuthorizationDecision
import org.goldenport.cncf.security.AuthorizationEngine

/*
 * @since   Apr. 11, 2025
 *  version Apr. 15, 2025
 * @version Dec. 21, 2025
 * @author  ASAMI, Tomoharu
 */
class Engine(
  authorizationEngine: AuthorizationEngine
) {
  def run[R <: Result](op: OperationCall[R]): Consequence[R] =
    Consequence {
      val ec = op.executionContext
      observe_enter(op)
      val r = op()
      observe_leave(op, Success(r))
      r
    }

  def execute[R <: Result](op: OperationCall[R]): Consequence[R] = {
    val ec = op.executionContext

    val authresult: Consequence[Unit] =
      Consequence {
        security_authorize(op, ec)
        ()
      }

    authresult.flatMap { _ =>
      Consequence {
        observe_enter(op)
        try {
          val r = op()
          ec.runtime.commit()
          observe_leave(op, Success(r))
          r
        } catch {
          case e: Throwable =>
            ec.runtime.abort()
            observe_leave(op, Failure(Conclusion.from(e)))
            throw e
        } finally {
          ec.runtime.dispose()
        }
      }
    }
  }

  def toExecutionContext(p: String): Consequence[ExecutionContext] = ???

  def toExecutionContext(p: Array[Byte]): Consequence[ExecutionContext] = ???


  protected def observe_enter[R <: Result](op: OperationCall[R]): Unit = {
  }

  protected def observe_leave[R <: Result](op: OperationCall[R], result: Consequence[R]): Unit = {
  }

  protected def security_authorize[R <: Result](
    op: OperationCall[R],
    ec: ExecutionContext
  ): Unit = {
    op.accesses.foreach { access =>
      val decision =
        authorizationEngine.authorize(
          ec,
          access.resource,
          access.action
        )
      observe_authorization(op, decision)
    }
  }

  protected def observe_authorization[R <: Result](
    op: OperationCall[R],
    decision: AuthorizationDecision
  ): Unit = {
  }
}

object Engine {
  def create(
    authorizationEngine: AuthorizationEngine
  ): Engine =
    new Engine(authorizationEngine)
}
