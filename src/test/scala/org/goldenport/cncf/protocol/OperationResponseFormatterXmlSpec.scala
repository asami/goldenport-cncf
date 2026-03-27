package org.goldenport.cncf.protocol

import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.cli.RunMode
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 28, 2026
 * @version Mar. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationResponseFormatterXmlSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "OperationResponseFormatter XML support" should {
    "render a RecordResponse as XML" in {
      Given("a record response and an xml request format")
      val request = _request("domain.meta.describe")
      val response = OperationResponse.RecordResponse(
        Record.dataAuto(
          "type" -> "component",
          "name" -> "domain",
          "summary" -> "Component domain"
        )
      )

      When("formatting the response")
      val formatted = OperationResponseFormatter.toResponse(request, response, RunMode.Command)

      Then("the protocol response is XML")
      formatted shouldBe Response.Xml(
        "<record><type>component</type><name>domain</name><summary>Component domain</summary></record>"
      )
    }

    "render an envelope RecordResponse as XML" in {
      Given("a record response and an xml envelope request format")
      val request = _request("domain.meta.describe", shape = "envelope")
      val response = OperationResponse.RecordResponse(
        Record.dataAuto(
          "type" -> "component",
          "name" -> "domain",
          "summary" -> "Component domain"
        )
      )

      When("formatting the response")
      val formatted = OperationResponseFormatter.toResponse(request, response, RunMode.Command)

      Then("the protocol response is XML")
      formatted shouldBe Response.Xml(
        "<record><textus-execution><interface-shape>record</interface-shape></textus-execution><data><type>component</type><name>domain</name><summary>Component domain</summary></data></record>"
      )
    }
  }

  private def _request(
    operation: String,
    shape: String = "data"
  ): Request =
    Request.ofOperation(operation).copy(
      properties =
        List(
          Property("textus.output.format", "xml", None),
          Property("textus.output.shape", shape, None)
        )
    )
}
