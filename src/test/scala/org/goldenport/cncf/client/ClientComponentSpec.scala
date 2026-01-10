package org.goldenport.cncf.client

import java.net.URL
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.{Component, ComponentInitParams}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 10, 2026
 * @version Jan. 11, 2026
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
      val table = Table(
        ("path", "body", "expectedurl"),
        ("/admin/system/ping", "pong", "http://localhost:8080/admin/system/ping")
      )

      forAll(table) { (path, body, expectedurl) =>
        Given("a client component with a fake HTTP driver")
        val driver = new FakeHttpDriver
        val component = _client_component(driver)

        val request = Request(
          component = Some("client"),
          service = Some("http"),
          operation = "post",
          arguments = List(
            Argument("path", path, None)
          ),
          switches = Nil,
          properties = List(
            Property("baseurl", "http://localhost:8080", None),
            Property("-d", Bag.text(body, StandardCharsets.UTF_8), None)
          )
        )

        When("the ComponentLogic creates and executes the action call")
        val result = _execute_request(component, request)

        Then("the HTTP driver receives the constructed POST request")
        result should be_success
        driver.calls shouldBe Vector(
          HttpCall("POST", expectedurl, Some(body), Map.empty)
        )
      }
    }

    "construct HTTP request via Command/Query entry" in {
      val table = Table(
        ("operation", "path", "body", "expectedcall"),
        ("get", "/admin/system/ping", None, HttpCall(
          "GET",
          "http://localhost:8080/admin/system/ping",
          None,
          Map.empty
        )),
        ("post", "/admin/system/ping", Some("pong"), HttpCall(
          "POST",
          "http://localhost:8080/admin/system/ping",
          Some("pong"),
          Map.empty
        ))
      )

      forAll(table) { (operation, path, body, expectedcall) =>
        Given("a request-based entry and a command/query-based entry")
        val requestDriver = new FakeHttpDriver
        val requestComponent = _client_component(requestDriver)
        val actionDriver = new FakeHttpDriver
        val actionComponent = _client_component(actionDriver)

        val request = Request(
          component = Some("client"),
          service = Some("http"),
          operation = operation,
          arguments = List(
            Argument("path", path, None)
          ),
          switches = Nil,
          properties = List(
            Property("baseurl", "http://localhost:8080", None)
          ) ::: body.map { value =>
            Property("-d", Bag.text(value, StandardCharsets.UTF_8), None)
          }.toList
        )

        val action = operation match {
          case "post" =>
            new PostCommand(
              "system.ping",
              HttpRequest.fromUrl(
                method = HttpRequest.POST,
                url = new URL(expectedcall.url),
                body = body.map(v => Bag.text(v, StandardCharsets.UTF_8))
              )
            )
          case _ =>
            new GetQuery(
              "system.ping",
              HttpRequest.fromUrl(
                method = HttpRequest.GET,
                url = new URL(expectedcall.url)
              )
            )
        }

        When("the ComponentLogic executes via Request-based entry")
        val requestResult = _execute_request(requestComponent, request)

        When("the ComponentLogic executes via Command/Query-based entry")
        val actionCall = actionComponent.logic.createActionCall(action)
        val actionResult = actionComponent.logic.execute(actionCall)

        Then("request-based entry routes to the HTTP driver")
        requestResult should be_success
        requestDriver.calls shouldBe Vector(expectedcall)

        Then("command/query-based entry routes to the same HTTP driver shape")
        actionResult should be_success
        actionDriver.calls shouldBe Vector(expectedcall)
      }
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
  }

  private def _client_component(
    driver: FakeHttpDriver
  ): ClientComponent = {
    val params = ComponentInitParams(Subsystem("client-component-spec"))
    val component = ClientComponent.Factory.create(params).collectFirst {
      case c: ClientComponent => c
    }.getOrElse {
      fail("client component factory did not produce ClientComponent")
    }
    val _ = component.withApplicationConfig(
      Component.ApplicationConfig(httpDriver = Some(driver))
    )
    component
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
