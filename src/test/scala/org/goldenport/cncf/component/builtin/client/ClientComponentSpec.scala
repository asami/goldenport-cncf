package org.goldenport.cncf.component.builtin.client

import java.net.URL
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.{Component, ComponentId, ComponentCreate, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.component.builtin.client.ClientComponent
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.protocol.spec as spec
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec
import cats.{Id, ~>}

/*
 * @since   Jan. 10, 2026
 *  version Jan. 21, 2026
 * @version Feb. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class ClientComponentSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with ConsequenceMatchers
  with TableDrivenPropertyChecks {

  "ClientComponent" should {
    "construct HTTP POST via ComponentLogic" in {
      // TODO Re-enable when test can inject UnitOfWork/HttpDriver without reflection hacks.
      pending
    }

    "construct HTTP request via Command/Query entry" in {
      // TODO Re-enable when request/action entry paths share stable wiring without test-only hacks.
      pending
    }
  }

  private final case class HttpCall(
    method: String,
    url: String,
    body: Option[String],
    headers: Map[String, String]
  )

  private final class FakeHttpDriver extends HttpDriver {
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[HttpCall]

    def calls: Vector[HttpCall] =
      buffer.toVector

    def get(path: String): HttpResponse = {
      buffer += HttpCall("GET", path, None, Map.empty)
      HttpResponse.notFound()
    }

    def post(
      path: String,
      body: Option[String],
      headers: Map[String, String]
    ): HttpResponse = {
      buffer += HttpCall("POST", path, body, headers)
      HttpResponse.notFound()
    }

    def put(
      path: String,
      body: Option[String],
      headers: Map[String, String]
    ): HttpResponse = {
      buffer += HttpCall("PUT", path, body, headers)
      HttpResponse.notFound()
    }
  }

  private def _client_component(
    driver: FakeHttpDriver
  ): ClientComponent = {
    val params = ComponentCreate(
      TestComponentFactory.emptySubsystem("client-component-spec"),
      ComponentOrigin.Builtin
    )
    val component = ClientComponent.Factory.create(params).collectFirst {
      case c: ClientComponent => c
    }.getOrElse {
      fail("client component factory did not produce ClientComponent")
    }
    val _ = component.withApplicationConfig(
      Component.ApplicationConfig(httpDriver = Some(driver))
    )
    _bind_uow_(component, driver)
    component
  }

  private def _bind_uow_(
    component: ClientComponent,
    driver: HttpDriver
  ): Unit = {
    val uow = _uow_(driver)
    val field = classOf[Component].getDeclaredFields.find(_.getName.contains("_unit_of_work")).getOrElse {
      fail("Component private field _unit_of_work was not found")
    }
    field.setAccessible(true)
    field.set(component, uow)
  }

  private def _uow_(
    driver: HttpDriver
  ): UnitOfWork = {
    val datastore = DataStore.noop()
    val eventengine = EventEngine.noop(datastore)
    val base = ExecutionContext.create()
    val runtime = new RuntimeContext(
      core = RuntimeContext.core(
        name = "client-component-spec",
        parent = None,
        observabilityContext = base.observability,
        httpDriverOption = Some(driver)
      ),
      unitOfWorkSupplier = () => throw new UnsupportedOperationException("unitOfWork supplier is not used in this spec runtime"),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Id) {
        def apply[A](fa: UnitOfWorkOp[A]): Id[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in this spec runtime")
      },
      unitOfWorkTryInterpreterFn = new (UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not used in this spec runtime")
      },
      unitOfWorkEitherInterpreterFn = new (UnitOfWorkOp ~> RuntimeContext.EitherThrowable) {
        def apply[A](fa: UnitOfWorkOp[A]): Either[Throwable, A] =
          Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not used in this spec runtime"))
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "client-component-spec-runtime"
    )
    val context = ExecutionContext.withRuntimeContext(base, runtime)
    new UnitOfWork(context, datastore, org.goldenport.cncf.entity.EntityStore.noop(), eventengine)
  }

  private def _bootstrap_core(): Component.Core = {
    val name = "bootstrap"
    val componentId = ComponentId(name)
    val instanceId = ComponentInstanceId.default(componentId)
    Component.Core.create(name, componentId, instanceId, Protocol.empty)
  }

  private def _execute_request(
    component: ClientComponent,
    request: Request
  ): Consequence[OperationResponse] =
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        val call = component.logic.createActionCall(action)
        component.logic.execute(call)
      case _ =>
        Consequence.failure("OperationRequest must be Action")
    }
}
