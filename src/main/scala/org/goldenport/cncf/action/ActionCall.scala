package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.protocol.*
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.datatype.PathName
import org.goldenport.record.Record
import org.goldenport.text.Presentable
import org.goldenport.util.StringUtils.objectToSnakeName
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.CollaboratorComponent
import org.goldenport.cncf.backend.collaborator.Collaborator
import org.goldenport.cncf.security.SecuritySubject

/*
 * @since   Apr. 11, 2025
 *  version Apr. 14, 2025
 *  version Dec. 31, 2025
 *  version Jan.  1, 2026
 *  version Jan.  2, 2026
 *  version Jan. 22, 2026
 *  version Feb. 21, 2026
 *  version Apr.  7, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ActionCall()
  extends Behavior
  with Presentable {
  def name: String = objectToSnakeName("ActionCall", this)
  def accesses: Vector[ResourceAccess] = Vector.empty
  def authorize()(using ExecutionContext): Consequence[Unit] =
    _authorize_usage().flatMap { _ =>
      _authorize_authentication_requirement()
    }.flatMap { _ =>
      val delegatedAccess = if (_framework_access_policy.isDefined) None else _declared_access
      delegatedAccess.fold(
        _declared_entities match
          case Vector() => Consequence.unit
          case xs =>
            getFactory[Component.Factory].map { factory =>
              xs.foldLeft(Consequence.unit) { (z, entityName) =>
                z.flatMap(_ => factory.authorize_operation_entity(action, entityName, core).getOrElse(Consequence.unit))
              }
            } getOrElse {
              Consequence.failure(s"Operation entity authorization is declared but no factory authorizer is available: ${action.name}")
            }
      ) { access =>
        getFactory[Component.Factory].flatMap(_.authorize_operation_access(action, access, core)) getOrElse {
          Consequence.failure(s"Operation access is declared but no factory authorizer is available: ${action.name}")
        }
      }
    }

  def execute(): Consequence[OperationResponse]

  def commit(): Consequence[UnitOfWork.CommitResult] = {
    val uow = executionContext.runtime.unitOfWork
    uow.record("ActionCall.commit")
    uow.commit()
  }

  override def print: String = s"ActionCall(${action.display})"
  override def display: String = action.display
  override def show: String = correlationId.fold(display)(cid => s"$display@${cid.show}")

  def request = action.request
  def arguments: List[Argument] = action.arguments
  def switches: List[Switch] = action.switches
  def properties: List[Property] = action.properties
  def args: List[String] = action.args

  private def _declared_access =
    component.flatMap(_.operationDefinitions.find(x => _normalize_name(x.name) == _normalize_name(action.name))).flatMap(_.access)

  private def _declared_entities =
    component.flatMap(_.operationDefinitions.find(x => _normalize_name(x.name) == _normalize_name(action.name))).map { op =>
      if (op.entityNames.nonEmpty) op.entityNames else op.entityName.toVector
    }.getOrElse(Vector.empty)

  private def _normalize_name(p: String): String =
    Option(p).getOrElse("").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")

  private def _authorize_usage()(using ExecutionContext): Consequence[Unit] = {
    val subject = SecuritySubject.current
    val componentName = _component_name
    val operationName = action.name
    val componentDecision =
      componentName match
        case Some(name) if subject.hasUsageCapability("component", name, "use") => Consequence.unit
        case _ => Consequence.unit
    val operationDecision =
      if (operationName.nonEmpty && subject.hasUsageCapability("operation", operationName, "invoke"))
        Consequence.unit
      else
        Consequence.unit
    componentDecision.flatMap(_ => operationDecision)
  }

  private def _authorize_authentication_requirement()(using ExecutionContext): Consequence[Unit] =
    _framework_access_policy match
      case Some("authenticated_only") | Some("authenticated-only") =>
        if (SecuritySubject.current.isAuthenticated)
          Consequence.unit
        else
          Consequence.securityAuthenticationRequired(s"Authenticated user is required: ${action.name}")
      case Some("anonymous_only") | Some("anonymous-only") =>
        if (SecuritySubject.current.isAnonymous)
          Consequence.unit
        else
          Consequence.securityPermissionDenied(s"Anonymous user is required: ${action.name}")
      case _ =>
        Consequence.unit

  private def _framework_access_policy: Option[String] =
    _declared_access.flatMap(x => Option(x.policy).map(_.trim.toLowerCase(java.util.Locale.ROOT))).collect {
      case m @ ("authenticated_only" | "authenticated-only" | "anonymous_only" | "anonymous-only") => m
    }

  private def _component_name: Option[String] =
    component.flatMap(_.componentDefinitionRecords.headOption).flatMap(_record_name).orElse(
      component.map(_.getClass.getSimpleName.stripSuffix("Component"))
    )

  private def _record_name(record: Record): Option[String] =
    record.getString("name").orElse(record.getString(PathName(Vector("name"))))
}

abstract class FunctionalActionCall extends ActionCall {
  protected def build_Program: ExecUowM[OperationResponse]

  final override def execute(): Consequence[OperationResponse] =
    build_Program.value.foldMap(executionContext.runtime.unitOfWorkInterpreter).flatMap(identity)
}

abstract class ProcedureActionCall extends ActionCall {
  override def execute(): Consequence[OperationResponse]
}

object ActionCall {
  final case class Core(
    action: Action,
    executionContext: ExecutionContext,
    component: Option[Component],
    correlationId: Option[CorrelationId]
  ) {
    def getFactory[A <: Component.Factory]: Option[A] =
      component.flatMap(_.factoryOption).map(_.asInstanceOf[A])

    def getCollaborator: Option[Collaborator] = component.flatMap {
      case m: CollaboratorComponent => Some(m.collaborator)
      case _ => None
    }

    inline def collaborator: Collaborator = getCollaborator getOrElse {
      Consequence.failUninitializedState.RAISE
    }

    def createCollaboratorActionCallCore(opname: String): CollaboratorActionCall.Core =
      CollaboratorActionCall.Core(collaborator, opname)
  }
  object Core {
    trait Holder {
      def core: Core

      def action: Action = core.action
      def executionContext: ExecutionContext = core.executionContext
      def component: Option[Component] = core.component
      def correlationId: Option[CorrelationId] = core.correlationId
      def getFactory[A <: Component.Factory]: Option[A] = core.getFactory[A]
    }
  }
}
