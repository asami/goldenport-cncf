package org.goldenport.cncf.protocol

import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 28, 2026
 * @version May. 31, 2026
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
        "<record><data><type>component</type><name>domain</name><summary>Component domain</summary></data><execution><interfaceShape>record</interfaceShape><operation>domain.meta.describe</operation></execution></record>"
      )
    }

    "render scalar envelope data without result or textus-execution roots" in {
      Given("a scalar response and a json envelope request format")
      val request = _request("domain.status", shape = "envelope", format = "json")
      val response = OperationResponse.Scalar("ok")

      When("formatting the response")
      val formatted = OperationResponseFormatter.toResponse(request, response, RunMode.Command)

      Then("the canonical envelope uses data and execution roots")
      formatted match {
        case Response.Json(value) =>
          value should include (""""data":"ok"""")
          value should include (""""execution"""")
          value should not include (""""result"""")
          value should not include ("textus-execution")
        case other =>
          fail(s"unexpected response: ${other}")
      }
    }

    "project JobId scalar into job metadata in canonical envelope" in {
      Given("a JobAsync scalar JobId response")
      val request = _request("domain.command", shape = "envelope", format = "json")
      val response = OperationResponse.Scalar("cncf-job-1")

      When("formatting the response")
      val formatted = OperationResponseFormatter.toResponse(request, response, RunMode.Command)

      Then("JobId is metadata and data is null")
      formatted match {
        case Response.Json(value) =>
          value should include (""""data":null""")
          value should include (""""job":{"id":"cncf-job-1","status":"accepted"}""")
          value should not include (""""result"""")
        case other =>
          fail(s"unexpected response: ${other}")
      }
    }

    "render job and continuation metadata for JobSyncWithAsyncCont envelope" in {
      Given("a synchronous primary response with JobSyncWithAsyncCont metadata")
      val request = _request(
        "domain.command",
        shape = "envelope",
        format = "json",
        mode = Some("job-sync-with-async-cont")
      )
      val response = OperationResponse.RecordResponse(Record.data("status" -> "ok"))
      val metadata = RuntimeContext.ExecutionMetadata(responseJobId = Some("cncf-job-primary"))

      When("formatting the response")
      val formatted = OperationResponseFormatter.toResponse(request, response, RunMode.Command, metadata)

      Then("primary data, primary job, and continuation intent are separate roots")
      formatted match {
        case Response.Json(value) =>
          value should include (""""data":{"status":"ok"}""")
          value should include (""""execution":{"interfaceShape":"record","operation":"domain.command","mode":"JobSyncWithAsyncCont","interface":"sync","managedByJob":true,"asyncContinuation":true}""")
          value should include (""""job":{"id":"cncf-job-primary"}""")
          value should include (""""continuation":{"mode":"event-async-same-job-task","policy":"async-same-job"}""")
          value should not include ("textus-execution")
        case other =>
          fail(s"unexpected response: ${other}")
      }
    }

    "render inline debug under debug root" in {
      Given("a response with inline calltree metadata")
      val request = _request("domain.command", shape = "envelope", format = "json")
      val response = OperationResponse.RecordResponse(Record.data("status" -> "ok"))
      val metadata = RuntimeContext.ExecutionMetadata(
        inlineCallTree = Some(Record.data("job_id" -> "cncf-job-debug", "nodes" -> Vector(Record.data("label" -> "root"))))
      )

      When("formatting the response")
      val formatted = OperationResponseFormatter.toResponse(request, response, RunMode.Command, metadata)

      Then("debug information is metadata outside data")
      formatted match {
        case Response.Json(value) =>
          value should include (""""data":{"status":"ok"}""")
          value should include (""""debug":{"calltree"""")
          value should not include ("textus-execution")
        case other =>
          fail(s"unexpected response: ${other}")
      }
    }
  }

  private def _request(
    operation: String,
    shape: String = "data",
    format: String = "xml",
    mode: Option[String] = None
  ): Request =
    Request.ofOperation(operation).copy(
      properties =
        List(
          Property("textus.output.format", format, None),
          Property("textus.output.shape", shape, None)
        ) ++ mode.map(x => Property("textus.command.execution-mode", x, None)).toList
    )
}
