package org.goldenport.cncf.display

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import logicalui.DisplayServiceReadFixture.HttpResult
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ObservabilityContext, PrincipalId, SubjectKind, TraceId}
import org.goldenport.cncf.http.{DisplayServiceHttpRouteFixture, HttpExecutionEngine, HttpRuntimeBindingAdmissionFixture}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder, StructuredLogEvent}
import org.goldenport.cncf.protocol.HttpFailureTransportMetadata
import org.goldenport.cncf.security.*
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.observation.Descriptor
import org.goldenport.record.io.RecordEncoder
import org.http4s.{Header, Headers, Method, Request as HttpRequest, Uri}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 */
final class DisplayAuthenticatedHttpTransportSpec extends AnyWordSpec with DisplayBusinessReviewFixture.Vocabulary {
  import DisplayAuthenticatedHttpTransportSpec.*
  import DisplayMutationServiceFixture.*

  private val _changes = Vector(field("title", DisplayValue.Text("Bearer HTTP 日本 Ω")))
  private val _headers = Vector("Authorization" -> ("Bearer " + developmentToken), "Cookie" -> "display-preference=compact")
  private val _spoof = Vector("cncf.security.authenticated" -> "true", "cncf.security.principalId" -> "owner",
    "cncf.security.privilege" -> "admin", "security.authenticated" -> "true")

  "Authenticated Display HTTP metadata" should {
    "production provider-authenticated Detail mutation and Business descriptors" which {
      "carry actual Authorization and Cookie headers through ordinary descriptors one update and explicit revision-two read" in {
        Given("a production multi-user SQLite component with an exact Bearer provider and ordinary provider-required Operation rules")
        val provider = new BearerProvider
        val session = open(backend = "sqlite", businessSetup = Some(DisplayBusinessReviewFixture.Setup(production = true)),
          authenticationProvider = Some(provider))
        try {
          _secure(session)
          val before = session.rows()
          val tables = session.physical()
          val resident = session.resident
          session.log.reset()
          When("actual Http4s GETs describeMutation and describeBusiness with both physical transport headers")
          val mutationdescriptor = _http(session, "describeMutation")
          val businessdescriptor = _http(session, "describeBusiness")
          Then("both descriptors succeed without Browser reload Business invocation conversion or writes")
          _ok(mutationdescriptor)
          _ok(businessdescriptor)
          mutationdescriptor.data.hcursor.get[String]("schema") shouldBe Right("cncf.display-mutation-descriptor.v1")
          businessdescriptor.data.hcursor.downField("actions").downN(0).downField("operation")
            .get[String]("operation") shouldBe Right("requestReview")
          checkPreserved(session, before, tables, resident)
          session.log.events.count(_.event == "business") shouldBe 0
          session.log.events.count(_.event == "capture") shouldBe 0
          session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 0

          When("the same authenticated route reads the current selected resource")
          val initial = _http(session, "read")
          Then("the ordinary contextual Detail reports revision one")
          _ok(initial)
          initial.data.hcursor.downField("model").downField("resource").get[String]("revision") shouldBe Right("1")
          val wanted = session.expected(before, tables, _changes, true, 2L)
          session.log.reset()
          session.lifecycleObservers.foreach(_.reset())

          When("one actual PUT carries the canonical mutation, a strong revision-one observation and contradictory output-only detail flags")
          val started = Instant.now()
          val updated = _http(session, "update", payload = session.payload(_changes),
            headers = _headers :+ ("If-Match" -> "\"revision-1\""),
            extra = Vector("textus.output.conclusion" -> "full", "cncf.output.conclusion" -> "standard"))
          val ended = Instant.now()
          Then("the normal Entity provider commits exactly one update with its corresponding projection")
          _ok(updated)
          updated.data.hcursor.get[String]("outcome") shouldBe Right("updated")
          updated.data.hcursor.downField("resource").get[String]("entityId") shouldBe Right(session.component.id.value)
          updated.data.hcursor.downField("resource").get[Long]("revision") shouldBe Right(2L)
          updated.data.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
          updated.data.hcursor.downField("projection").get[String]("type") shouldBe Right("ready")
          // The update observer has no capture callback; its native update plan and full state prove the single commit.
          session.log.events.count(_.event == "capture") shouldBe 0
          session.log.events.count(_.event == "find") shouldBe 1
          session.log.events.count(_.event == "business") shouldBe 0
          session.log.validations shouldBe 1
          session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 1
          session.lifecycleObservers.map(_.writes).sum shouldBe 0
          session.log.events.foreach { event =>
            event.context.security.principal.id shouldBe PrincipalId("owner")
            event.context.security.subjectKind shouldBe SubjectKind.Service
            SecuritySubject.from(event.context.security).isProviderAuthenticated shouldBe true
          }
          checkState(session, DisplayBusinessReviewFixture.Expected(wanted.rows, wanted.physical),
            Some(DisplayBusinessReviewFixture.AuditWindow(started, ended)))
          session.component.realm.get(session.component.unrelatedId) shouldBe resident.unrelated
          session.component.memory.get(session.component.unrelatedId) shouldBe resident.memoryunrelated

          When("the authenticated client explicitly reads after the one completed mutation")
          val fresh = _http(session, "read")
          Then("the new Detail reports revision two and the independent initial response remains revision one")
          _ok(fresh)
          fresh.data.hcursor.downField("model").downField("resource").get[String]("revision") shouldBe Right("2")
          initial.data.hcursor.downField("model").downField("resource").get[String]("revision") shouldBe Right("1")
          session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 1
          checkState(session, DisplayBusinessReviewFixture.Expected(wanted.rows, wanted.physical),
            Some(DisplayBusinessReviewFixture.AuditWindow(started, ended)))
          provider.accepted shouldBe 5
        } finally session.close()
      }

      "reject missing wrong or Cookie-only authentication before Browser Business conversion or writes despite spoofed facts" in {
        Given("the production fixture with a Bearer-only provider and both ordinary authentication requirements")
        val provider = new BearerProvider
        val session = open(backend = "sqlite", businessSetup = Some(DisplayBusinessReviewFixture.Setup(production = true)),
          authenticationProvider = Some(provider))
        try {
          _secure(session)
          val credentials = Vector(Vector.empty[(String, String)], Vector("Cookie" -> "cncf_session=canonical-review-owner-session"),
            Vector("Authorization" -> "Bearer wrong-development-token", "Cookie" -> "display-preference=compact"))
          for (operation <- Vector("read", "describeMutation", "describeBusiness", "update"); headers <- credentials) {
            Given("complete selected unrelated physical and resident state before an unauthenticated actual request")
            val before = session.rows()
            val tables = session.physical()
            val resident = session.resident
            session.log.reset()
            session.lifecycleObservers.foreach(_.reset())
            When("the actual HTTP route receives untrusted authentication claims without the exact Bearer credential")
            val response = _http(session, operation, headers = headers ++
              (if (operation == "update") Vector("If-Match" -> "\"revision-1\"") else Vector.empty),
              payload = if (operation == "update") session.payload(_changes) else "", extra = _spoof)
            Then("normal Operation authentication rejects before all semantic and canonical effects")
            response.status should be >= 400
            _redacted(response)
            checkPreserved(session, before, tables, resident)
            session.log.events shouldBe empty
            session.log.queries shouldBe empty
            session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 0
          }
          provider.accepted shouldBe 0
        } finally session.close()
      }

      "retain unknown-field and exact selector rejection after successful provider authentication" in {
        Given("a production component with valid Authorization and Cookie transport metadata")
        val session = open(backend = "sqlite", businessSetup = Some(DisplayBusinessReviewFixture.Setup(production = true)),
          authenticationProvider = Some(new BearerProvider))
        try {
          _secure(session)
          val requests: Vector[() => HttpResult] = Vector(
            () => _http(session, "read", extra = Vector("unknownDomainField" -> "smuggled")),
            () => _http(session, "read", extra = Vector("Accept" -> "application/json")),
            () => _http(session, "read", extra = Vector("Content-Type" -> "application/json")),
            () => _http(session, "read", extra = Vector("resourceType" -> resourceType)),
            () => _http(session, "read", extra = Vector("screenId" -> screenId)),
            () => _http(session, "read", extra = Vector("resourceId" -> session.component.id.value)),
            () => _http(session, "read", selectors = Some(Vector("resourceType" -> resourceType, "screenId" -> " ",
              "resourceId" -> session.component.id.value))),
            () => _http(session, "describeMutation", extra = Vector("screenId" -> screenId)),
            () => _http(session, "describeMutation", extra = Vector("unknownDomainField" -> "smuggled")),
            () => _http(session, "describeBusiness", extra = Vector("screenId" -> screenId)),
            () => _http(session, "describeBusiness", extra = Vector("unknownDomainField" -> "smuggled")),
            () => _http(session, "describeBusiness", extra = Vector("Accept" -> "application/json")),
            () => _http(session, "describeBusiness", extra = Vector("Content-Type" -> "application/json")),
            () => _http(session, "update", headers = _headers :+ ("If-Match" -> "\"revision-1\""),
              payload = session.payload(_changes), extra = Vector("screenId" -> screenId)),
            () => _http(session, "update", headers = _headers :+ ("If-Match" -> "\"revision-1\""),
              payload = session.payload(_changes).replaceFirst("\\{", "{\"unknownDomainField\":true,")),
            () => _http(session, "update", headers = _headers :+ ("If-Match" -> "\"revision-1\""),
              payload = session.payload(Vector(field("undeclared", DisplayValue.Text("smuggled"))))))
          requests.foreach { request =>
            Given("a complete independent baseline before an unknown field malformed selector or duplicate selector")
            val before = session.rows()
            val tables = session.physical()
            val resident = session.resident
            session.log.reset()
            session.lifecycleObservers.foreach(_.reset())
            When("the real HTTP route authenticates valid transport carriers and then admits the closed domain input")
            val response = request()
            Then("closed input admission still rejects before Browser Business conversion or canonical writes")
            response.status should be >= 400
            _redacted(response)
            checkPreserved(session, before, tables, resident)
            session.log.events.count(_.event == "business") shouldBe 0
            session.log.events.count(_.event == "capture") shouldBe 0
            session.log.queries shouldBe empty
            session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 0
          }
        } finally session.close()
      }

      "preserve case-insensitive transport carriers for at least 100 ordinary authenticated descriptor requests" in {
        Given("a production component and generated physical Authorization and Cookie header casing")
        val provider = new BearerProvider
        val session = open(backend = "sqlite", businessSetup = Some(DisplayBusinessReviewFixture.Setup(production = true)),
          authenticationProvider = Some(provider))
        try {
          _secure(session)
          val before = session.rows()
          val tables = session.physical()
          val resident = session.resident
          val headers = for {
            authorization <- Gen.oneOf("Authorization", "authorization", "AUTHORIZATION", "aUtHoRiZaTiOn")
            cookie <- Gen.oneOf("Cookie", "cookie", "COOKIE", "cOoKiE")
            operation <- Gen.oneOf("describeMutation", "describeBusiness")
          } yield (Vector(authorization -> ("Bearer " + developmentToken), cookie -> "display-preference=compact"), operation)
          val property = Prop.forAll(headers) { case (carriers, operation) =>
            session.log.reset()
            When("actual Http4s dispatches a generated authenticated descriptor request")
            val response = _http(session, operation, headers = carriers)
            Then("normal authentication and closed metadata admission succeed without any semantic write or read")
            _ok(response)
            checkPreserved(session, before, tables, resident)
            session.log.events.count(_.event == "business") shouldBe 0
            true
          }
          When("ScalaCheck checks at least 100 undiscarded physical carrier combinations")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("every generated descriptor remains authenticated inert and case-insensitive")
          withClue(result.toString.replace(developmentToken, "<redacted>")) { result.passed shouldBe true }
          result.succeeded should be >= 100
        } finally session.close()
      }

      "preserve actual semantic malformed-field duplicate-selector invalid-title and stale-revision errors without writes" in {
        Given("the ordinary production Bearer route, native SQLite state and independently current revision one")
        val provider = new BearerProvider
        val session = open(backend = "sqlite", businessSetup = Some(DisplayBusinessReviewFixture.Setup(production = true)),
          authenticationProvider = Some(provider))
        try {
          _secure(session)
          val before = session.rows()
          val tables = session.physical()
          val resident = session.resident
          val malformedpayload = session.payload(Vector(field("undeclared", DisplayValue.Text("safe"))))
          val requests: Vector[(() => HttpResult, String, String)] = Vector(
            (() => _http(session, "update", headers = _headers :+ ("If-Match" -> "\"revision-1\""),
              payload = malformedpayload),
              "DSP96_MUTATION_FIELD_UNKNOWN", "request.fields.location"),
            (() => _http(session, "describeMutation", extra = Vector("screenId" -> screenId)),
              "DSP96_MUTATION_ELEMENT_INVALID", "request.screenId"))
          requests.foreach { case (request, code, path) =>
            Given("the unchanged full selected unrelated physical and resident baseline")
            session.log.reset()
            session.lifecycleObservers.foreach(_.reset())
            When("actual authenticated Http4s dispatch receives the malformed semantic input")
            val response = request()
            Then("its 4xx body carries the exact typed diagnostic while every canonical state dimension is preserved")
            response.status shouldBe 400
            val error = _error(response)
            error.downField("conclusion").downField("observation").downField("cause").downField("diagnostic")
              .get[String]("code") shouldBe Right(code)
            error.downField("conclusion").downField("observation").downField("cause").downField("diagnostic")
              .get[String]("path") shouldBe Right(path)
            _redacted(response)
            checkPreserved(session, before, tables, resident)
            session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 0
          }

          Given("the original current revision and a blank title rejected by the actual canonical input validator")
          val titleconclusion = conclusion(domainFailure)
          val titlemessage = titleconclusion.cause.getMessage.get
          val titletaxonomy = io.circe.parser.parse(RecordEncoder.jsonC(titleconclusion.observation.taxonomy.toRecord).TAKE).toOption.get
          val titlefacet = io.circe.parser.parse(RecordEncoder.jsonC(Descriptor.Facet.Message(titlemessage).toRecord).TAKE).toOption.get
          session.log.reset()
          When("an authenticated update attempts the invalid title")
          val invalidtitle = _http(session, "update", headers = _headers :+ ("If-Match" -> "\"revision-1\""),
            payload = session.payload(Vector(field("title", DisplayValue.Text(" ")))))
          Then("the domain Conclusion survives HTTP projection and the attempted update writes nothing")
          invalidtitle.status shouldBe 400
          val titleobservation = _error(invalidtitle).downField("conclusion").downField("observation")
          titleobservation.downField("taxonomy").focus shouldBe Some(titletaxonomy)
          titleobservation.downField("cause").get[String]("message") shouldBe Right(titlemessage)
          titleobservation.downField("cause").get[Vector[io.circe.Json]]("facets")
            .map(_.filter(_.hcursor.downField("message").focus.isDefined)) shouldBe Right(Vector(titlefacet))
          session.log.validations shouldBe 1
          session.log.conversions shouldBe Vector("title")
          checkRows(session.rows(), before)
          session.physical() shouldBe tables
          session.resident shouldBe resident
          session.log.commands shouldBe 0
          session.log.events.count(_.event == "find") shouldBe 0
          session.log.events.count(_.event == "capture") shouldBe 0
          session.log.events.count(_.event == "business") shouldBe 0
          session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 0

          Given("an independently stale revision two observation against the still-current revision one")
          session.log.reset()
          When("an authenticated update uses the stale If-Match validator")
          val stale = _http(session, "update", headers = _headers :+ ("If-Match" -> "\"revision-2\""),
            payload = session.payload(_changes))
          Then("the original conflict keeps numeric expected/actual values and all state remains unchanged")
          stale.status shouldBe 409
          val cause = _error(stale).downField("conclusion").downField("observation").downField("cause")
          cause.get[String]("reason") shouldBe Right("stale-entity-revision")
          cause.get[Long]("expected") shouldBe Right(2L)
          cause.get[Long]("actual") shouldBe Right(1L)
          checkPreserved(session, before, tables, resident)
          session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 0
        } finally session.close()
      }

      "keep HTTP amount independent of sanitized full history in structured and textual failure logs" in {
        Given("the real authenticated descriptor availability failure with constructed canonical Conclusion history")
        val session = open(backend = "sqlite", businessSetup = Some(DisplayBusinessReviewFixture.Setup(production = true)),
          authenticationProvider = Some(new BearerProvider))
        val previousbackend = LogBackendHolder.backend
        val backend = new RecordingBackend
        try {
          _secure(session)
          val before = session.rows()
          val tables = session.physical()
          val resident = session.resident
          val previous = conclusion(domainFailure).copy(observation = conclusion(domainFailure).observation.copy(
            cause = conclusion(domainFailure).cause.addFacet(Descriptor.Facet.Message("historical " + developmentToken))))
          val original = conclusion(domainFailure).copy(previous = Some(previous))
          session.log.availability = Consequence.Failure(original)
          LogBackendHolder.install(backend)
          val bodies = Vector("standard", "full").map { detail =>
            Given("the unchanged failure source and an isolated recording backend for one detail selection")
            backend.clear()
            When("the actual production Bearer descriptor route selects the requested Conclusion amount")
            val response = _http(session, "describeMutation", extra = Vector("textus.output.conclusion" -> detail))
            Then("status/current semantics are stable while only full returns previous and every log contains sanitized history")
            response.status shouldBe 400
            val error = _error(response)
            error.get[String]("conclusionDetail") shouldBe Right(detail)
            error.downField("conclusion").downField("previous").focus.isDefined shouldBe (detail == "full")
            if (detail == "standard") error.get[Vector[String]]("conclusionOmitted") shouldBe Right(Vector("previous"))
            val events = backend.events.filter(_.message.contains("execute_failed"))
            events should have size 1
            val attributes = RecordEncoder.jsonC(events.head.attributes).TAKE
            attributes should include ("previous")
            attributes should include ("historical")
            events.head.message should include ("previous")
            backend.lines.filter(_.contains("execute_failed")).mkString should include ("historical")
            Vector(response.text, attributes, events.head.message, backend.lines.mkString).foreach(_ should not include developmentToken)
            events.head.attributes.getString("request") shouldBe None
            checkPreserved(session, before, tables, resident)
            error.downField("conclusion").downField("observation").downField("cause").focus.get
          }
          Then("amount never changes the current semantic cause or original history")
          bodies(0) shouldBe bodies(1)
          original.previous.get should be theSameInstanceAs previous
          previous.cause.getMessage shouldBe conclusion(domainFailure).cause.getMessage
          previous.cause.descriptor.facets.last shouldBe Descriptor.Facet.Message("historical " + developmentToken)
        } finally {
          previousbackend.foreach(LogBackendHolder.install)
          session.close()
        }
      }

      "sanitize physical credentials in supplied trace and correlation failure attributes without changing source identities or replaying" in {
        Given("an authenticated production descriptor failure whose supplied observability identities contain its known Bearer credential")
        val token = "trace_17"
        val associatedvalue = "associated-private-value"
        val exceptionmessage = "unknown-exception-private-value"
        val exception = new IllegalStateException(exceptionmessage)
        val source = conclusion(domainFailure)
        val previous = source.copy(observation = source.observation.copy(
          cause = source.cause.addFacet(Descriptor.Facet.Message("safe failure history"))))
        val original = source.copy(observation = source.observation.copy(cause = source.cause
          .addFacet(Descriptor.Facet.Parameter.argument("accessToken"))
          .addFacet(Descriptor.Facet.Value(associatedvalue))
          .addFacet(Descriptor.Facet.Exception(exception))), previous = Some(previous))
        val originalfacets = original.cause.descriptor.facets
        val trace = TraceId("fixture", "displaynormal", Some(fixedInstant), Some(token))
        val correlation = CorrelationId("fixture", "displaynormal", Some(fixedInstant), Some(token))
        val supplied = ObservabilityContext(trace, None, Some(correlation))
        val traceprint = trace.print
        val correlationprint = correlation.print
        val provider = new BearerProvider(token)
        val session = open(backend = "sqlite", businessSetup = Some(DisplayBusinessReviewFixture.Setup(production = true)),
          authenticationProvider = Some(provider))
        val previousbackend = LogBackendHolder.backend
        val backend = new RecordingBackend
        try {
          _secure(session)
          val before = session.rows()
          val tables = session.physical()
          val resident = session.resident
          session.log.reset()
          session.lifecycleObservers.foreach(_.reset())
          session.log.availability = Consequence.Failure(original)
          LogBackendHolder.install(backend)
          When("one actual provider-authenticated Http4s descriptor request carries those physical trace and correlation identities")
          val response = _http(session, "describeMutation", headers = Vector("Authorization" -> ("Bearer " + token)),
            observabilitycontext = supplied)
          Then("the one execute_failed event retains the exact sanitized supplied identities and every textual log surface excludes the credential")
          response.status shouldBe 400
          val events = backend.events.filter(_.message.contains("execute_failed"))
          events should have size 1
          val attributes = events.head.attributes
          attributes.getString("component") shouldBe Some(session.component.name)
          attributes.getString("service") shouldBe Some("display")
          attributes.getString("operation") shouldBe Some("describeMutation")
          attributes.getString("traceId") shouldBe Some(traceprint.replace(token, "[redacted]"))
          attributes.getString("correlationId") shouldBe Some(correlationprint.replace(token, "[redacted]"))
          backend.events.foreach { event =>
            RecordEncoder.jsonC(event.attributes).TAKE should not include token
            event.message should not include token
          }
          backend.lines.mkString should not include token
          response.text should not include token
          Vector(associatedvalue, exceptionmessage).foreach { secret =>
            backend.events.foreach { event =>
              RecordEncoder.jsonC(event.attributes).TAKE should not include secret
              event.message should not include secret
            }
            backend.lines.mkString should not include secret
            response.text should not include secret
          }
          val actionlines = backend.lines.filter(_.contains("Action failed:"))
          actionlines should have size 1
          actionlines.head should include (source.cause.getMessage.get)
          actionlines.head should include ("safe failure history")
          actionlines.head should include ("previous")
          actionlines.head should include ("[redacted]")
          provider.accepted shouldBe 1
          checkPreserved(session, before, tables, resident)
          session.log.events.count(_.event == "business") shouldBe 0
          session.log.events.count(_.event == "capture") shouldBe 0
          session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 0
          supplied.traceId should be theSameInstanceAs trace
          supplied.correlationId.get should be theSameInstanceAs correlation
          trace.print shouldBe traceprint
          correlation.print shouldBe correlationprint
          trace.print should include (token)
          correlation.print should include (token)
          original.cause.descriptor.facets shouldBe originalfacets
          original.cause.getMessage shouldBe source.cause.getMessage
          original.cause.descriptor.facets should contain (Descriptor.Facet.Value(associatedvalue))
          original.getException.get should be theSameInstanceAs exception
          exception.getMessage shouldBe exceptionmessage
          original.previous.get should be theSameInstanceAs previous
        } finally {
          previousbackend.foreach(LogBackendHolder.install)
          session.close()
        }
      }

      "preserve the authenticated descriptor failure and every native state dimension when the log backend throws" in {
        Given("an ordinary authenticated descriptor failure and a test-owned backend whose writes throw a NonFatal exception")
        val provider = new BearerProvider
        val session = open(backend = "sqlite", businessSetup = Some(DisplayBusinessReviewFixture.Setup(production = true)),
          authenticationProvider = Some(provider))
        val previousbackend = LogBackendHolder.backend
        val original = conclusion(domainFailure)
        val originalfacets = original.cause.descriptor.facets
        var attempts = 0
        val backend = new LogBackend {
          override def writeLine(line: String): Unit = {
            attempts += 1
            throw new IllegalStateException("fixture log write failed")
          }
        }
        try {
          _secure(session)
          val before = session.rows()
          val tables = session.physical()
          val resident = session.resident
          session.log.reset()
          session.lifecycleObservers.foreach(_.reset())
          session.log.availability = Consequence.Failure(original)
          LogBackendHolder.install(backend)
          When("one real provider-authenticated Http4s descriptor request encounters the failing log sink")
          val response = _http(session, "describeMutation")
          Then("logging cannot replace the original semantic HTTP failure cause retry the operation or write native or Business state")
          response.status shouldBe 400
          attempts should be > 0
          val cause = _error(response).downField("conclusion").downField("observation").downField("cause")
          cause.get[String]("message") shouldBe Right(original.cause.getMessage.get)
          _error(response).downField("conclusion").downField("observation").downField("taxonomy").focus shouldBe
            Some(io.circe.parser.parse(RecordEncoder.jsonC(original.observation.taxonomy.toRecord).TAKE).toOption.get)
          _redacted(response)
          provider.accepted shouldBe 1
          checkPreserved(session, before, tables, resident)
          session.log.events.count(_.event == "business") shouldBe 0
          session.log.events.count(_.event == "capture") shouldBe 0
          session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 0
          session.lifecycleObservers.map(_.writes).sum shouldBe 0
          conclusion(session.log.availability) should be theSameInstanceAs original
          original.cause.descriptor.facets shouldBe originalfacets
        } finally {
          previousbackend.foreach(LogBackendHolder.install)
          session.close()
        }
      }

      "render actual typed failures as YAML or legacy plain and strip every internal response header" in {
        Given("the production provider-authenticated descriptor route and an invalid duplicate selector")
        val session = open(backend = "sqlite", businessSetup = Some(DisplayBusinessReviewFixture.Setup(production = true)),
          authenticationProvider = Some(new BearerProvider))
        try {
          _secure(session)
          val before = session.rows()
          val tables = session.physical()
          val resident = session.resident
          When("actual HTTP clients request YAML and explicit text/plain for the same typed failure")
          val yamlwire = _wire(session, "describeMutation", headers = _headers :+ ("Accept" -> "application/yaml"),
            extra = Vector("screenId" -> screenId))
          val plainwire = _wire(session, "describeMutation", headers = _headers :+ ("Accept" -> "text/plain"),
            extra = Vector("screenId" -> screenId))
          Then("YAML retains the Conclusion once and plain retains its legacy diagnostic message and status")
          val yaml = yamlwire._1
          val plain = plainwire._1
          yamlwire._2 shouldBe empty
          plainwire._2 shouldBe empty
          yaml.status shouldBe 400
          yaml.contentType shouldBe Some("application/yaml")
          yaml.text should include ("conclusion:")
          yaml.text should include ("DSP96_MUTATION_ELEMENT_INVALID")
          plain.status shouldBe 400
          plain.contentType shouldBe Some("text/plain")
          plain.text should include ("DSP96_MUTATION_ELEMENT_INVALID")
          plain.text should not include "conclusion:"
          _redacted(yaml)
          _redacted(plain)
          checkPreserved(session, before, tables, resident)
        } finally session.close()
      }
    }

    "the existing List fixture under explicit provider-required query rules" which {
      "query a real List with Bearer and Cookie and reject missing or wrong Bearer before its Browser" in {
        Given("a separate existing SQLite List fixture in its test operation mode without Business or reference-profile expansion")
        val provider = new BearerProvider
        val session = open(backend = "sqlite", lifecycle = Some(LifecycleSetup(withList = true)),
          authenticationProvider = Some(provider))
        try {
          _secure(session)
          val before = session.rows()
          val tables = session.physical()
          val resident = session.resident
          session.log.reset()
          When("the actual query route receives the exact Bearer and a coexisting Cookie without ControlledTest authentication facts")
          val response = _http(session, "query")
          Then("provider-authenticated query consumes the existing contextual List Browser and preserves canonical rows")
          _ok(response)
          response.data.hcursor.downField("definition").get[String]("screenId") shouldBe Right(DisplayMutationServiceListFixture.listScreenId)
          response.data.hcursor.downField("model").downField("items").focus.get.asArray.get should have size 2
          session.log.queries should have size 1
          SecuritySubject.from(session.log.queries.head._2.security).isProviderAuthenticated shouldBe true
          checkRows(session.rows(), before)
          session.physical() shouldBe tables
          session.log.validations shouldBe 0
          session.log.commands shouldBe 0
          session.log.events.count(_.event == "capture") shouldBe 0
          session.component.realm.get(session.component.unrelatedId) shouldBe resident.unrelated
          val accepted = provider.accepted
          for (headers <- Vector(Vector("Cookie" -> "cncf_session=canonical-review-owner-session"),
              Vector("Authorization" -> "Bearer wrong-development-token", "Cookie" -> "display-preference=compact"))) {
            Given("the complete current state before missing or wrong Bearer credentials with forged client claims")
            val state = session.resident
            session.log.reset()
            When("the actual List route applies both authenticated and provider-authentication requirements")
            val rejected = _http(session, "query", headers = headers, extra = _spoof)
            Then("no Browser query conversion or write runs and all canonical and resident state remains unchanged")
            rejected.status should be >= 400
            _redacted(rejected)
            checkPreserved(session, before, tables, state)
            session.log.events shouldBe empty
            session.log.queries shouldBe empty
          }
          provider.accepted shouldBe accepted
        } finally session.close()
      }
    }
  }

  private def _secure(session: Session): Unit = {
    val descriptor = session.subsystem.descriptor.getOrElse(GenericSubsystemDescriptor(
      path = Path.of("<display-authenticated-http-fixture>"), subsystemName = session.subsystem.name,
      componentBindings = Vector(GenericSubsystemComponentBinding(session.component.name)),
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
        convention = Some("enabled"), fallbackPrivilege = Some("disabled"), providers = Vector(
          GenericSubsystemAuthenticationProviderBinding("canonical-review-auth", session.component.name, enabled = Some(true)))))))))
    val rules = Vector("query", "read", "describeMutation", "update", "describeBusiness").map(operation =>
      (session.component.name + ".display." + operation) -> OperationAuthorizationRule(
        requireAuthenticated = true, requireProviderAuthentication = true)).toMap
    session.subsystem.withDescriptor(descriptor.copy(operationAuthorization = descriptor.operationAuthorization ++ rules))
    RuntimeBindingAdmissionFixture.admit(session.subsystem)
  }

  private def _http(session: Session, operation: String, headers: Vector[(String, String)] = _headers,
    payload: String = "", extra: Vector[(String, String)] = Vector.empty,
    selectors: Option[Vector[(String, String)]] = None,
    observabilitycontext: ObservabilityContext = observability): HttpResult =
    _wire(session, operation, headers, payload, extra, selectors, observabilitycontext)._1

  private def _wire(session: Session, operation: String, headers: Vector[(String, String)] = _headers,
    payload: String = "", extra: Vector[(String, String)] = Vector.empty,
    selectors: Option[Vector[(String, String)]] = None,
    observabilitycontext: ObservabilityContext = observability): (HttpResult, Vector[String]) = {
    val selected = selectors.getOrElse(Vector("resourceType" -> resourceType,
      "screenId" -> (if (operation == "query") DisplayMutationServiceListFixture.listScreenId else screenId)) ++
      (if (operation == "read") Vector("resourceId" -> session.component.id.value) else Vector.empty))
    val parameters = selected ++ Vector("textus.output.shape" -> "envelope",
      "cncf.context.traceId" -> observabilitycontext.traceId.print,
      "cncf.context.correlationId" -> observabilitycontext.correlationId.get.print) ++ extra
    val uri = Uri.unsafeFromString(s"/rest/v1/${session.component.name}/display/$operation.json")
      .withMultiValueQueryParams(parameters.groupMap(_._1)(_._2))
    val defaults = Vector("Accept" -> "application/json", "Content-Type" ->
      (if (operation == "update") "application/json; charset=UTF-8" else "application/json"))
    val physical = headers ++ defaults.filterNot { case (name, _) => headers.exists(_._1.equalsIgnoreCase(name)) }
    val request = HttpRequest[IO](if (operation == "update") Method.PUT else Method.GET, uri,
      headers = Headers(physical.map { case (name, value) => Header.Raw(CIString(name), value) }))
    val supplied = if (operation == "update")
      request.withBodyStream(fs2.Stream.emits(payload.getBytes(StandardCharsets.UTF_8)).covary[IO]) else request
    val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(session.subsystem))
    val response = DisplayServiceHttpRouteFixture.app(server).run(supplied).unsafeRunSync()
    val bytes = response.body.compile.toVector.unsafeRunSync()
    val internalheaders = response.headers.headers.filter(header => HttpFailureTransportMetadata.isInternalHeader(header.name.toString))
      .map(_.name.toString).toVector
    HttpResult(response.status.code, response.headers.headers.find(_.name == CIString("Content-Type"))
      .map(_.value.takeWhile(_ != ';')), new String(bytes.toArray, StandardCharsets.UTF_8), bytes) -> internalheaders
  }
  private def _error(response: HttpResult): io.circe.ACursor =
    io.circe.parser.parse(response.text).toOption.get.hcursor.downField("error")
  private def _redacted(response: HttpResult): Unit = {
    withClue("HTTP diagnostics must not disclose a development Bearer credential") {
      response.text.contains(developmentToken) shouldBe false
      response.text.contains("wrong-development-token") shouldBe false
    }
  }
  private def _ok(response: HttpResult): Unit = {
    _redacted(response)
    withClue(s"HTTP ${response.status}: ${response.text.replace(developmentToken, "<redacted>")}") {
      response.status shouldBe 200
    }
  }
}

object DisplayAuthenticatedHttpTransportSpec {
  val developmentToken: String = "display-http-development-bearer-fixture-only"

  final class RecordingBackend extends LogBackend {
    private var _events = Vector.empty[StructuredLogEvent]
    private var _lines = Vector.empty[String]
    def events: Vector[StructuredLogEvent] = _events
    def lines: Vector[String] = _lines
    def clear(): Unit = { _events = Vector.empty; _lines = Vector.empty }
    override def logStructured(event: StructuredLogEvent): Unit = {
      _events :+= event
      super.logStructured(event)
    }
    override def writeLine(line: String): Unit = { _lines :+= line }
  }

  final class BearerProvider(credential: String = developmentToken) extends AuthenticationProvider {
    private var _accepted = 0
    val name: String = "canonical-review-auth"
    def accepted: Int = _accepted
    def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] = {
      val authenticated = request.attributes.exists { case (key, value) =>
        key.equalsIgnoreCase("authorization") && value == "Bearer " + credential
      }
      if (authenticated) {
        _accepted += 1
        Consequence.success(Some(AuthenticationResult(PrincipalId("owner"), subjectKind = SubjectKind.Service)))
      } else Consequence.success(None)
    }
  }
}
