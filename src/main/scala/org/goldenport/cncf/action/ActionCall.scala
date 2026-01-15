package org.goldenport.cncf.action

import scala.reflect.ClassTag
import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.util.StringUtils.objectToSnakeName
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.security.{Action as SecurityAction, SecuredResource}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.unitofwork.UnitOfWork

/*
 * @since   Apr. 11, 2025
 *  version Apr. 14, 2025
 *  version Dec. 31, 2025
 *  version Jan.  1, 2026
 *  version Jan.  2, 2026
 * @version Jan. 15, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ActionCall()
  extends ActionCall.Core.Holder
  with OperationCallDataStorePart {
  def name: String = objectToSnakeName("ActionCall", this)
  def accesses: Vector[ResourceAccess] = Vector.empty

  def execute(): Consequence[OperationResponse]

  def commit(): Consequence[UnitOfWork.CommitResult] = {
    val uow = executionContext.runtime.unitOfWork
    uow.record("ActionCall.commit")
    uow.commit()
  }

  /** Returns the ApplicationContext bound to the current ExecutionContext.
    *
    * This is a protected utility for Action implementations to access
    * application-level services (e.g., SIE context) in a type-safe manner.
    *
    * The context is expected to be injected at bootstrap time via
    * Component.ApplicationConfig and propagated into ExecutionContext
    * at ActionCall creation time.
    *
    * Failure to find or match the expected ApplicationContext type
    * indicates a configuration or wiring error and results in an exception.
    */
  protected def application_context[T <: Component.ApplicationContext](
    using ct: ClassTag[T]
  ): T =
    executionContext.application match {
      case Some(a) if ct.runtimeClass.isInstance(a) =>
        a.asInstanceOf[T]
      case Some(other) =>
        throw new IllegalStateException(
          s"Unexpected ApplicationContext: ${other.getClass.getName}, expected: ${ct.runtimeClass.getName}"
        )
      case None =>
        throw new IllegalStateException(
          s"ApplicationContext not found, expected: ${ct.runtimeClass.getName}"
        )
    }
}

abstract class FunctionalActionCall extends ActionCall {
  protected def build_Program: ExecUowM[OperationResponse]

  final override def execute(): Consequence[OperationResponse] =
    build_Program.value.foldMap(executionContext.runtime.unitOfWorkInterpreter)
}

abstract class ProcedureActionCall extends ActionCall {
  override def execute(): Consequence[OperationResponse]
}

object ActionCall {
  final case class Core(
    action: Action,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  )
  object Core {
    trait Holder {
      def core: Core

      def action: Action = core.action
      def executionContext: ExecutionContext = core.executionContext
      def correlationId: Option[CorrelationId] = core.correlationId
    }
  }
}
