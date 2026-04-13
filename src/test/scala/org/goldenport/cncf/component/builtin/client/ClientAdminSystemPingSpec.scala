package org.goldenport.cncf.component.builtin.client

import cats.~>

import java.nio.charset.StandardCharsets
import org.goldenport.bag.Bag
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.component.builtin.client.ClientComponent
import org.goldenport.cncf.context.{ExecutionContext, ObservabilityContext, RuntimeContext, ScopeContext}
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.datatype.MimeBody
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpRequest, HttpResponse, HttpStatus}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.spec as spec
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 10, 2026
 *  version Feb. 15, 2026
 *  version Mar. 29, 2026
 *  version Apr. 11, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
class ClientAdminSystemPingSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with ConsequenceMatchers {

  "Client admin system ping" should {
    "route Action DSL through UnitOfWork and HttpDriver" in {
      Given("a client component with a fake HTTP driver")
      val response = _response_pong()
      val driver = new FakeHttpDriver(response)
      val harness = _build_harness(driver)

      When("the client action is executed")
      val action = new GetQuery(
        Request.ofOperation("system.ping"),
        HttpRequest.fromPath(HttpRequest.GET, "/admin/system/ping")
      )
      val result = harness.executeAction(action)

      Then("the UnitOfWork interpreter invokes the HTTP driver")
      result should be_success
      driver.calls shouldBe Vector(
        HttpCall("GET", "/admin/system/ping", None, Map.empty)
      )
      result match {
        case org.goldenport.Consequence.Success(OperationResponse.Http(res)) =>
          res.code shouldBe response.code
        case other =>
          fail(s"unexpected result: ${other}")
      }
    }

    "route CLI client ping to the client component" in {
      Given("a subsystem with the client component wired to a fake HTTP driver")
      val response = _response_pong()
      val driver = new FakeHttpDriver(response)
      val harness = _build_harness(driver)
      val _ = harness.component.withApplicationConfig(
        org.goldenport.cncf.component.Component.ApplicationConfig(
          httpDriver = Some(driver)
        )
      )

      When("the client CLI request is executed")
      val request = CncfRuntime.parseClientArgs(
        harness.subsystem,
        Array("http", "get", "/admin/system/ping")
      )
      val result = request.flatMap { req =>
        _client_action_from_request(req).flatMap(harness.executeAction)
      }

      Then("the HTTP driver is invoked with the expected path")
      result should be_success
      driver.calls shouldBe Vector(
          HttpCall("GET", s"${ClientConfig.DefaultBaseUrl}/admin/system/ping", None, Map.empty)
      )
      val _ = response
    }

    "route CLI client POST ping to the client component" in {
      Given("a subsystem with the client component wired to a fake HTTP driver")
      val response = _response_pong()
      val driver = new FakeHttpDriver(response)
      val harness = _build_harness(driver)

      When("the client CLI request is executed")
      val request = CncfRuntime.parseClientArgs(
        harness.subsystem,
        Array("http", "post", "/admin/system/ping", "-d", "pong")
      )
      val result = request.flatMap { req =>
        _client_action_from_request(req).flatMap(harness.executeAction)
      }

      Then("the HTTP driver is invoked with the expected path and body")
      result should be_success
      driver.calls shouldBe Vector(
          HttpCall("POST", s"${ClientConfig.DefaultBaseUrl}/admin/system/ping", Some("pong"), Map.empty)
      )
    }

    "append CLI key-value parameters as HTTP query parameters" in {
      Given("a subsystem with the client component wired to a fake HTTP driver")
      val response = _response_pong()
      val driver = new FakeHttpDriver(response)
      val harness = _build_harness(driver)

      When("the client CLI request includes domain key-value parameters")
      val request = CncfRuntime.parseClientArgs(
        harness.subsystem,
        Array("http", "get", "/crud/entity/search-item-record", "name=alpha", "title=Alpha")
      )
      val result = request.flatMap { req =>
        _client_action_from_request(req).flatMap(harness.executeAction)
      }

      Then("the HTTP driver is invoked with those parameters in the query string")
      result should be_success
      driver.calls shouldBe Vector(
        HttpCall(
          "GET",
          s"${ClientConfig.DefaultBaseUrl}/crud/entity/search-item-record?name=alpha&title=Alpha",
          None,
          Map.empty
        )
      )
    }
  }

  private final case class HttpCall(
    method: String,
    path: String,
    body: Option[String],
    headers: Map[String, String]
  )

  private final class FakeHttpDriver(
    response: HttpResponse
  ) extends HttpDriver {
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[HttpCall]

    def calls: Vector[HttpCall] =
      buffer.toVector

    def get(path: String): HttpResponse = {
      buffer += HttpCall("GET", path, None, Map.empty)
      response
    }

    def post(
      path: String,
      body: Option[String],
      headers: Map[String, String]
    ): HttpResponse = {
      buffer += HttpCall("POST", path, body, headers)
      response
    }

    def put(
      path: String,
      body: Option[String],
      headers: Map[String, String]
    ): HttpResponse = {
      buffer += HttpCall("PUT", path, body, headers)
      response
    }
  }

  private def _response_pong(): HttpResponse = {
    val contentType = ContentType(
      MimeType("text/plain"),
      Some(StandardCharsets.UTF_8)
    )
    HttpResponse.Text(
      HttpStatus.Ok,
      contentType,
      Bag.text("pong", StandardCharsets.UTF_8)
    )
  }

  private def _client_component(): ClientComponent = {
    val subsystem = TestComponentFactory.emptySubsystem("cncf-client-test")
    val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
    val component = ClientComponent.Factory.create(params).collectFirst {
      case c: ClientComponent => c
    }.getOrElse {
      fail("client component factory did not produce ClientComponent")
    }
    component
  }

  private def _bootstrap_core(): Component.Core = {
    val name = "bootstrap"
    val componentId = ComponentId(name)
    val instanceId = ComponentInstanceId.default(componentId)
    Component.Core.create(name, componentId, instanceId, Protocol.empty)
  }

  private def _client_action_from_request(
    req: Request
  ): Consequence[org.goldenport.cncf.action.Action] = {
    val baseurl = req.properties.find(_.name == "baseurl")
      .map(_.value.toString).getOrElse(ClientConfig.DefaultBaseUrl)
    req.arguments.find(_.name == "path").map(_.value.toString) match {
      case Some(path) =>
        val url = _append_client_query(_build_client_url(baseurl, path), req)
        req.operation match {
          case "post" =>
            _client_body(req).map { body =>
              new PostCommand(
                Request.ofOperation("system.ping"),
                HttpRequest.fromUrl(
                  method = HttpRequest.POST,
                  url = java.net.URI.create(url).toURL,
                  body = body
                )
              )
            }
          case _ =>
            Consequence.success(
              new GetQuery(
                Request.ofOperation("system.ping"),
                HttpRequest.fromUrl(
                  method = HttpRequest.GET,
                  url = java.net.URI.create(url).toURL
                )
              )
            )
        }
      case None =>
        Consequence.failure("client http path is required")
    }
  }

  private def _client_body(
    req: Request
  ): Consequence[Option[Bag]] =
    _body_property(req) match {
      case Some(property) =>
        property.value match {
          case b: Bag => Consequence.success(Some(b))
          case MimeBody(_, bag) => Consequence.success(Some(bag))
          case s: String => Consequence.success(Some(Bag.text(s, StandardCharsets.UTF_8)))
          case _ => Consequence.failure("client request body must be a MimeBody, Bag, or String")
        }
      case None =>
        Consequence.success(None)
    }

  private def _body_property(
    req: Request
  ): Option[Property] =
    List("http.body", "http.data", "-d").iterator
      .flatMap(name => req.properties.find(_.name == name))
      .toList
      .headOption

  private def _build_client_url(
    baseurl: String,
    path: String
  ): String = {
    val base = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
    val suffix = if (path.startsWith("/")) path else s"/${path}"
    s"${base}${suffix}"
  }

  private def _append_client_query(
    url: String,
    req: Request
  ): String =
    _client_query_string(req) match {
      case Some(query) => s"${url}?${query}"
      case None => url
    }

  private def _client_query_string(
    req: Request
  ): Option[String] = {
    val params = req.properties.collect {
      case Property(name, value, _) if _is_http_parameter_property(name) =>
        s"${java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8)}=${java.net.URLEncoder.encode(value.toString, java.nio.charset.StandardCharsets.UTF_8)}"
    }
    if (params.isEmpty) None else Some(params.mkString("&"))
  }

  private def _is_http_parameter_property(
    name: String
  ): Boolean =
    name != null &&
      name.nonEmpty &&
      name != "baseurl" &&
      name != "http.body" &&
      name != "http.data" &&
      name != "-d"

  private final case class TestHarness(
    subsystem: Subsystem,
    component: ClientComponent,
    runtime: RuntimeContext,
    interpreter: UnitOfWorkInterpreter
  ) {
    def executeAction(action: org.goldenport.cncf.action.Action): Consequence[OperationResponse] = {
      val ctx = _execution_context(runtime)
      val core = ActionCall.Core(action, ctx, None, None)
      val call = action.createCall(core)
      component.logic.execute(call)
    }

    def executeRequest(request: Request): Consequence[Response] = {
      val ctx = _execution_context(runtime)
      component.logic.makeOperationRequest(request).flatMap {
        case action: org.goldenport.cncf.action.Action =>
          val core = ActionCall.Core(action, ctx, None, None)
          val call = action.createCall(core)
          component.logic.execute(call).map(_.toResponse)
        case _ =>
          Consequence.failure("OperationRequest must be Action")
      }
    }
  }

  private def _build_harness(driver: FakeHttpDriver): TestHarness = {
    val subsystem = TestComponentFactory.emptySubsystem("cncf-client-test")
    val component = _client_component()
    subsystem.add(Seq(component))
    val base = org.goldenport.cncf.context.ExecutionContext.create()
    val bootstrap = _bootstrapRuntimeContext(driver, base.cncfCore.observability)
    val uowcontext = org.goldenport.cncf.context.ExecutionContext.withRuntimeContext(
      base,
      bootstrap
    )
    val datastore = org.goldenport.cncf.datastore.DataStore.noop()
    val eventengine = org.goldenport.cncf.event.EventEngine.noop(datastore)
    val uow = new UnitOfWork(uowcontext, eventengine, CommitRecorder.noop)
    val interpreter = new UnitOfWorkInterpreter(uow)
    val runtime = _testRuntimeContext(driver, base.cncfCore.observability, uow, interpreter)
    TestHarness(subsystem, component, runtime, interpreter)
  }

  private def _execution_context(
    runtime: RuntimeContext
  ): ExecutionContext = {
    val base = org.goldenport.cncf.context.ExecutionContext.create()
    org.goldenport.cncf.context.ExecutionContext.withRuntimeContext(
      base,
      runtime
    )
  }

  private def _testRuntimeContext(
    driver: HttpDriver,
    observability: ObservabilityContext,
    uow: UnitOfWork,
    interpreter: UnitOfWorkInterpreter
  ): RuntimeContext = {
    val idInterpreter = new (UnitOfWorkOp ~> Consequence) {
      def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
        Consequence(interpreter.execute(fa))
    }
    new RuntimeContext(
      core = _runtimeCore("client-admin-system-ping-spec-runtime", driver, observability),
      unitOfWorkSupplier = () => uow,
      unitOfWorkInterpreterFn = idInterpreter,
      commitAction = uowArg => {
        val _ = uowArg.commit()
        ()
      },
      abortAction = uowArg => {
        val _ = uowArg.rollback()
        ()
      },
      disposeAction = _ => (),
      token = "client-admin-system-ping-spec-runtime"
    )
  }

  private def _bootstrapRuntimeContext(
    driver: HttpDriver,
    observability: ObservabilityContext
  ): RuntimeContext = {
    val idInterpreter = new (UnitOfWorkOp ~> Consequence) {
      def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
        throw new UnsupportedOperationException("bootstrap runtime has no interpreter")
    }
    new RuntimeContext(
      core = _runtimeCore("client-admin-system-ping-spec-bootstrap-runtime", driver, observability),
      unitOfWorkSupplier = () => throw new UnsupportedOperationException("bootstrap runtime has no UnitOfWork"),
      unitOfWorkInterpreterFn = idInterpreter,
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "client-admin-system-ping-spec-bootstrap-runtime"
    )
  }

  private def _runtimeCore(
    name: String,
    driver: HttpDriver,
    observability: ObservabilityContext
  ): ScopeContext.Core =
    RuntimeContext.core(
      name = name,
      parent = None,
      observabilityContext = observability,
      httpDriverOption = Some(driver)
    )

}
