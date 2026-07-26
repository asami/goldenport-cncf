package org.goldenport.cncf.spi.supervisor

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 24, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class SupervisorSpiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Supervisor SPI" should {
    "retain only durable managed lifecycle facts in its request and result" in {
      Given("one managed lifecycle request with an opaque target and deployment identity")
      val request = _request("request-1")
      val result = _result(request.requestId)

      When("a provider accepts the request")
      val accepted = _supervisor.submit(request)(using ExecutionContext.create())

      Then("the request and result correlate without process or locator facts")
      accepted.toOption shouldBe Some(result)
      request.targetId shouldBe "textus-control-center"
      request.deploymentId shouldBe Some("standalone")
      _assert_no_process_locator_fields(request)
      _assert_no_process_locator_fields(result)
    }

    "install a selected provider through the standard single socket" in {
      Given("a component-owned Supervisor socket without a selected provider")
      val socket = new SupervisorSocket {}
      val provider = _supervisor

      When("assembly installs the selected provider")
      socket.installSpi(provider)

      Then("the consumer receives the CNCF-owned Supervisor contract")
      socket.isSpiInstalled shouldBe true
      socket.spiContract.name shouldBe Supervisor.CONTRACT_NAME
      socket.supervisor shouldBe provider
    }

    "report a structured failure when no provider is installed" in {
      Given("a required Supervisor socket before assembly installs a provider")
      val socket = new SupervisorSocket {}

      When("the consumer resolves the Supervisor through the safe accessor")
      val result = socket.supervisorC

      Then("the missing provider is reported without throwing")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include(
        "Supervisor SPI is not installed"
      )
    }
  }

  private val _supervisor: Supervisor = new Supervisor {
    def submit(request: SupervisorRequest)(using ExecutionContext): Consequence[SupervisorResult] =
      Consequence.success(_result(request.requestId))

    def lookup(requestId: String)(using ExecutionContext): Consequence[Option[SupervisorResult]] =
      Consequence.success(Some(_result(requestId)))
  }

  private def _request(requestid: String): SupervisorRequest =
    SupervisorRequest(
      requestid,
      s"idempotency-$requestid",
      "textus-control-center",
      Some("standalone"),
      SupervisorAction.Start,
      "operator-1",
      Instant.parse("2026-07-24T00:00:05Z")
    )

  private def _result(requestid: String): SupervisorResult =
    SupervisorResult(
      requestid,
      SupervisorState.Accepted,
      None,
      None,
      "textus-supervisor-standalone",
      Some("instance-1"),
      Some(Instant.parse("2026-07-24T00:00:00Z")),
      None
    )

  private def _assert_no_process_locator_fields(
    product: Product
  ): Unit = {
    val fieldnames =
      product.productElementNames.map(_.toLowerCase).toSet
    fieldnames should contain noneOf (
      "pid",
      "command",
      "environment",
      "credential",
      "port",
      "developmentdirectory",
      "executable",
      "filesystem",
      "path"
    )
  }
}
