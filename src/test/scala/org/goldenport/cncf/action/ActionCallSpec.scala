package org.goldenport.cncf.action

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, ScopeContext, ScopeKind, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.operation.{CmlOperationAccess, CmlOperationDefinition}
import org.goldenport.protocol.Property
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionCallSpec extends AnyWordSpec with Matchers {

  "ActionCall" should {

    "allow authenticated_only for authenticated subject" in {
      given ExecutionContext = _execution_context(
        principalId = "u1",
        attrs = Map("access_token" -> "abc.def.ghi")
      )
      val call = _call("changePassword", Some(CmlOperationAccess("authenticated_only")))

      call.authorize() shouldBe Consequence.unit
    }

    "reject authenticated_only for anonymous subject" in {
      given ExecutionContext = _execution_context(
        principalId = "anonymous",
        attrs = Map("anonymous" -> "true")
      )
      val call = _call("changePassword", Some(CmlOperationAccess("authenticated_only")))

      call.authorize() shouldBe a[Consequence.Failure[_]]
    }

    "allow anonymous_only for anonymous subject" in {
      given ExecutionContext = _execution_context(
        principalId = "anonymous",
        attrs = Map("anonymous" -> "true")
      )
      val call = _call("register", Some(CmlOperationAccess("anonymous_only")))

      call.authorize() shouldBe Consequence.unit
    }

    "reject anonymous_only for authenticated subject" in {
      given ExecutionContext = _execution_context(
        principalId = "u1",
        attrs = Map("authenticated" -> "true", "access_token" -> "abc.def.ghi")
      )
      val call = _call("register", Some(CmlOperationAccess("anonymous_only")))

      call.authorize() shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _call(
    operationName: String,
    access: Option[CmlOperationAccess]
  )(using ExecutionContext): ActionCall = {
    val comp = new Component {
      override def operationDefinitions: Vector[CmlOperationDefinition] = Vector(
        CmlOperationDefinition(
          name = operationName,
          kind = "command",
          inputType = "Input",
          outputType = "Output",
          inputValueKind = "record",
          access = access
        )
      )
    }
    val act = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall =
        throw new UnsupportedOperationException("not used in ActionCallSpec")

      override def request: org.goldenport.protocol.Request =
        org.goldenport.protocol.Request(
          component = None,
          service = None,
          operation = operationName,
          arguments = Nil,
          switches = Nil,
          properties = List(Property("dummy", "x", None))
        )
    }
    new ProcedureActionCall with ActionCall.Core.Holder {
      val core = ActionCall.Core(
        action = act,
        executionContext = summon[ExecutionContext],
        component = Some(comp),
        correlationId = None
      )
      def execute(): Consequence[OperationResponse] =
        Consequence.success(OperationResponse.void)
    }
  }

  private def _execution_context(
    principalId: String,
    attrs: Map[String, String]
  ): ExecutionContext = {
    val datastorespace = DataStoreSpace.default()
    val entitystorespace = new EntityStoreSpace()
    val observability = ObservabilityContext(
      traceId = TraceId("test", "runtime"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "runtime"))
    )
    val driver = FakeHttpDriver.okText("nop")
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "action-call-spec-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver),
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitOfWorkSupplier = () => new org.goldenport.cncf.unitofwork.UnitOfWork(context),
      unitOfWorkInterpreterFn = new (org.goldenport.cncf.unitofwork.UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: org.goldenport.cncf.unitofwork.UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in test context")
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "action-call-spec-runtime"
    )
    context match {
      case i: ExecutionContext.Instance =>
        val principal = new Principal {
          def id: PrincipalId = PrincipalId(principalId)
          def attributes: Map[String, String] = attrs
        }
        i.copy(
          cncfCore = i.cncfCore.copy(
            security = SecurityContext(
              principal = principal,
              capabilities = Set.empty,
              level = SecurityLevel("user")
            )
          )
        )
      case _ =>
        context
    }
  }
}
