package org.goldenport.cncf.protocol

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import cats.data.NonEmptyVector
import org.goldenport.datatype.I18nString
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{CommandExecutionMode, CommandInterfaceMode}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{
  ExecutionContext,
  GlobalRuntimeContext,
  RuntimeContext,
  ScopeContext,
  ScopeKind
}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.{
  Configuration,
  ConfigurationTrace,
  ConfigurationValue,
  ResolvedConfiguration
}
import org.goldenport.cncf.job.JobId
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 28, 2026
 *  version May. 31, 2026
 *  version Jun. 29, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationResponseFormatterSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e9 = afterWord(
    "in spec:action-execution-semantics, example:E9, rules:R5,R6,R13, phase:57.2, slice:AES-05A"
  )

  "OperationResponseFormatter response contracts" should {
    "format structured responses" which {
      "render a YAML RecordResponse without replacing Unicode characters" in {
        Given("a record response with Japanese text and yaml request format")
        val request = _request("domain.prompt", format = "yaml")
        val response = OperationResponse.RecordResponse(
          Record.dataAuto(
            "title"  -> "府中散歩",
            "prompt" -> "府中散歩をSVGに描画してください。"
          )
        )

        When("formatting the response")
        val formatted = OperationResponseFormatter.toResponse(request, response, RunMode.Command)

        Then("the YAML response preserves Unicode text")
        formatted match {
          case Response.Yaml(value) =>
            value should include("府中散歩")
            value should include("SVG")
            value should not include "????"
          case other =>
            fail(s"unexpected response: ${other}")
        }
      }

      "render a RecordResponse as XML" in {
        Given("a record response and an xml request format")
        val request = _request("domain.meta.describe")
        val response = OperationResponse.RecordResponse(
          Record.dataAuto(
            "type"    -> "component",
            "name"    -> "domain",
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
    }

    "format canonical envelope, job, and debug responses" which {
      "render an envelope RecordResponse as XML" in {
        Given("a record response and an xml envelope request format")
        val request = _request("domain.meta.describe", shape = "envelope")
        val response = OperationResponse.RecordResponse(
          Record.dataAuto(
            "type"    -> "component",
            "name"    -> "domain",
            "summary" -> "Component domain"
          )
        )

        When("formatting the response")
        val formatted = OperationResponseFormatter.toResponse(request, response, RunMode.Command)

        Then("the protocol response is XML")
        formatted shouldBe Response.Xml(
          "<record><data><type>component</type><name>domain</name><summary>Component domain</summary></data><execution><interface-shape>record</interface-shape><operation>domain.meta.describe</operation></execution></record>"
        )
      }

      "render scalar envelope data without result or textus-execution roots" in {
        Given("a scalar response and a json envelope request format")
        val request  = _request("domain.status", shape = "envelope", format = "json")
        val response = OperationResponse.Scalar("ok")

        When("formatting the response")
        val formatted = OperationResponseFormatter.toResponse(request, response, RunMode.Command)

        Then("the canonical envelope uses data and execution roots")
        formatted match {
          case Response.Json(value) =>
            value should include(""""data":"ok"""")
            value should include(""""execution"""")
            value should not include """"result""""
            value should not include "textus-execution"
          case other =>
            fail(s"unexpected response: ${other}")
        }
      }

      "E9 execution response transport metadata" must _e9 {
      "project explicitly accepted JobId scalar into job metadata in canonical envelope" in {
        Given("a JobAsync scalar JobId response with explicit accepted-job metadata")
        val request = _request("domain.command", shape = "envelope", format = "json")
        val jobid = JobId(
          major = "cncf",
          minor = "job",
          timestamp = Some(Instant.parse("2026-07-16T00:00:00Z")),
          entropy = Some("formatter_spec")
        ).value
        val response = OperationResponse.Scalar(jobid)
        val metadata = RuntimeContext.ExecutionMetadata(
          responseJobId = Some(jobid)
        )
        val executionresponse = Some(RuntimeContext.ExecutionResponseMetadata(
          admittedMode = CommandExecutionMode.JobAsync.toString,
          effectiveMode = CommandExecutionMode.JobAsync,
          interfaceMode = CommandInterfaceMode.Async,
          managedByJob = true,
          responseKind = RuntimeContext.ExecutionResponseKind.AcceptedJob
        ))

        When("formatting the response")
        val formatted = OperationResponseFormatter.toResponse(
          request,
          response,
          RunMode.Command,
          metadata,
          executionresponse
        )

        Then("JobId is metadata and data is null")
        formatted match {
          case Response.Json(value) =>
            value should include(""""data":null""")
            value should include(s""""job":{"id":"$jobid","status":"accepted"}""")
            value should not include """"result""""
          case other =>
            fail(s"unexpected response: ${other}")
        }
      }

      "keep a JobId-looking direct scalar as data without a job root" in {
        Given("a direct scalar whose value has the JobId syntax")
        val request = _request("domain.command", shape = "envelope", format = "json")
        val jobid = JobId(
          major = "cncf",
          minor = "job",
          timestamp = Some(Instant.parse("2026-07-16T00:00:00Z")),
          entropy = Some("direct_scalar")
        ).value
        val response = OperationResponse.Scalar(jobid)
        val metadata = RuntimeContext.ExecutionMetadata.empty

        When("formatting the direct response")
        val formatted = OperationResponseFormatter.toResponse(
          request,
          response,
          RunMode.Command,
          metadata,
          Some(RuntimeContext.ExecutionResponseMetadata.direct)
        )

        Then("the scalar remains data and the formatter does not infer a Job")
        formatted match {
          case Response.Json(value) =>
            value should include(s"\"data\":\"$jobid\"")
            value should include("\"response-kind\":\"direct\"")
            value should not include "\"job\""
          case other =>
            fail(s"unexpected response: ${other}")
        }
      }

      "keep a JobId-looking scalar as data when the no-metadata overload receives a Job request mode" in {
        Given("a JobId-shaped scalar and a request that asks for a Job execution mode without runtime metadata")
        val request = _request(
          "domain.command",
          shape = "envelope",
          format = "json",
          mode = Some("job-async")
        )
        val jobid = JobId(
          major = "cncf",
          minor = "job",
          timestamp = Some(Instant.parse("2026-07-16T00:00:00Z")),
          entropy = Some("no_metadata_scalar")
        ).value

        When("the no-metadata formatter overload formats the scalar")
        val formatted = OperationResponseFormatter.toResponse(
          request,
          OperationResponse.Scalar(jobid),
          RunMode.Command
        )

        Then("the scalar remains data and no Job root is inferred from request configuration")
        formatted match {
          case Response.Json(value) =>
            value should include(s"\"data\":\"$jobid\"")
            value should not include "\"job\""
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
        val metadata = RuntimeContext.ExecutionMetadata(
          responseJobId = Some("cncf-job-primary")
        )
        val executionresponse = Some(RuntimeContext.ExecutionResponseMetadata(
          admittedMode = CommandExecutionMode.JobSyncWithAsyncCont.toString,
          effectiveMode = CommandExecutionMode.JobSyncWithAsyncCont,
          interfaceMode = CommandInterfaceMode.Sync,
          managedByJob = true,
          asyncContinuation = true,
          responseKind = RuntimeContext.ExecutionResponseKind.JobResult
        ))

        When("formatting the response")
        val formatted =
          OperationResponseFormatter.toResponse(
            request,
            response,
            RunMode.Command,
            metadata,
            executionresponse
          )

        Then("primary data, primary job, and continuation intent are separate roots")
        formatted match {
          case Response.Json(value) =>
            value should include(""""data":{"status":"ok"}""")
            value should include(
              """"execution":{"interface-shape":"record","operation":"domain.command","requested-mode":"job-sync-with-async-cont","admitted-mode":"JobSyncWithAsyncCont","effective-mode":"JobSyncWithAsyncCont","interface":"sync","managed-by-job":true,"async-continuation":true,"response-kind":"job-result"}"""
            )
            value should include(""""job":{"id":"cncf-job-primary"}""")
            value should not include """"status":"accepted"""
            value should include(
              """"continuation":{"mode":"event-async-same-job-task","policy":"async-same-job"}"""
            )
            value should not include "textus-execution"
          case other =>
            fail(s"unexpected response: ${other}")
        }
      }
      }

      "render inline debug under debug root" in {
        Given("a response with inline calltree metadata")
        val request  = _request("domain.command", shape = "envelope", format = "json")
        val response = OperationResponse.RecordResponse(Record.data("status" -> "ok"))
        val metadata = RuntimeContext.ExecutionMetadata(
          inlineCallTree = Some(Record.data(
            "job_id" -> "cncf-job-debug",
            "nodes"  -> Vector(Record.data("label" -> "root"))
          ))
        )

        When("formatting the response")
        val formatted =
          OperationResponseFormatter.toResponse(request, response, RunMode.Command, metadata)

        Then("debug information is metadata outside data")
        formatted match {
          case Response.Json(value) =>
            value should include(""""data":{"status":"ok"}""")
            value should include(""""debug":{"calltree"""")
            value should not include "textus-execution"
          case other =>
            fail(s"unexpected response: ${other}")
        }
      }
    }

    "format execution-profile responses" which {
      "format records from execution-profile assumptions despite conflicting raw aliases" in {
        Given(
          "a global runtime with Japanese/Tokyo execution assumptions and conflicting raw configuration aliases"
        )
        _with_global_runtime(Locale.JAPAN, ZoneId.of("Asia/Tokyo")) {
          val request = _request("domain.locale", format = "json")
          val response = OperationResponse.RecordResponse(
            Record.dataAuto(
              "message" -> I18nString(NonEmptyVector(
                (Locale.ENGLISH, "Hello"),
                Vector((Locale.JAPAN, "こんにちは"))
              )),
              "count"     -> 12345,
              "updatedAt" -> Instant.parse("2026-04-05T00:00:00Z")
            )
          )

          When("the operation response formats the transformed record")
          val formatted = OperationResponseFormatter.toResponse(request, response, RunMode.Command)

          Then("I18n and temporal values use execution-profile locale and timezone")
          formatted match {
            case Response.Json(value) =>
              value should include("\"message\":\"こんにちは\"")
              value should include("\"count\":12345")
              value should include("\"updated_at\":\"4月5日 9時00分 (JST)\"")
            case other =>
              fail(s"unexpected response: ${other}")
          }
        }
      }
    }
  }

  private def _with_global_runtime[A](locale: Locale, timezone: ZoneId)(body: => A): A = {
    val resolvedconfiguration = ResolvedConfiguration(
      Configuration(Map(
        "textus.execution.locale"   -> ConfigurationValue.StringValue(locale.toLanguageTag),
        "textus.execution.timezone" -> ConfigurationValue.StringValue(timezone.getId),
        "textus.locale"             -> ConfigurationValue.StringValue("fr-FR"),
        "cncf.locale"               -> ConfigurationValue.StringValue("de-DE"),
        "textus.timeZone"           -> ConfigurationValue.StringValue("America/New_York"),
        "cncf.timezone"             -> ConfigurationValue.StringValue("Europe/Paris")
      )),
      ConfigurationTrace.empty
    )
    val config = RuntimeConfig.from(resolvedconfiguration)
    val core = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "gcf-09o-formatter-spec",
      parent = None,
      observabilitycontext = ExecutionContext.create().observability,
      httpdriveroption = Some(config.httpDriver)
    ).core
    val global = new GlobalRuntimeContext(
      core = core,
      config = config,
      aliasResolver = AliasResolver.empty,
      runtimeMode = RunMode.Command,
      commandExecutionMode = None,
      runtimeVersion = org.goldenport.cncf.CncfVersion.current,
      subsystemName = GlobalRuntimeContext.SubsystemName,
      subsystemVersion = org.goldenport.cncf.CncfVersion.current,
      resolvedConfiguration = resolvedconfiguration
    )
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(global)
    try body
    finally GlobalRuntimeContext.current = previous
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
