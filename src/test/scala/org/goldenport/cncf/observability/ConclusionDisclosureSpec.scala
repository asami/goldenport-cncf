package org.goldenport.cncf.observability

import java.time.Instant
import io.circe.{Json, parser}
import org.goldenport.Conclusion
import org.goldenport.cncf.display.*
import org.goldenport.conclusion.{Disposition, Interpretation}
import org.goldenport.http.HttpRequest
import org.goldenport.observation.{Cause, Descriptor, Observation, Phenomenon, SourcePosition, Taxonomy}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.schema.DataConfidentiality
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 */
final class ConclusionDisclosureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ConclusionDisclosure.Detail
  import Descriptor.Facet

  "Conclusion disclosure" should {
    "semantic amount and immutable vocabulary" which {
      "preserve the current record and ordered duplicate facets while selecting previous independently" in {
        Given("an application-status Conclusion with revision facets, duplicate reasons and previous history")
        val previous = _conclusion(Vector(Facet.Message("previous λ")))
        val original = _conclusion(Vector(Facet.Reason("stale-entity-revision"), Facet.Expected(2L),
          Facet.Actual(1L), Facet.FieldPath("request.changes[0].title"), Facet.Reason("second")), Some(previous))
        When("standard, full and detailed log records are projected")
        val standard = _json(ConclusionDisclosure.http(original, Detail.Standard))
        val full = _json(ConclusionDisclosure.http(original, Detail.Full))
        val logged = _json(ConclusionDisclosure.log(original))
        Then("only standard omits history and all projections retain semantic status and ordered facets")
        standard.hcursor.downField("previous").focus shouldBe None
        full.hcursor.downField("previous").focus shouldBe Some(_json(ConclusionDisclosure.http(previous, Detail.Full)))
        logged.hcursor.downField("previous").focus shouldBe Some(_json(ConclusionDisclosure.log(previous)))
        Vector(standard, full, logged).foreach { output =>
          output.hcursor.downField("status").focus shouldBe Some(_json(original.status.toRecord))
          output.hcursor.downField("status").get[String]("appStatus") shouldBe Right("")
          output.hcursor.downField("observation").downField("taxonomy").focus shouldBe
            Some(_json(original.observation.taxonomy.toRecord))
          output.hcursor.downField("interpretation").focus shouldBe Some(_json(original.interpretation.toRecord))
          output.hcursor.downField("disposition").focus shouldBe Some(_json(original.disposition.toRecord))
          val cause = output.hcursor.downField("observation").downField("cause")
          cause.get[String]("reason") shouldBe Right("stale-entity-revision")
          cause.get[Long]("expected") shouldBe Right(2L)
          cause.get[Long]("actual") shouldBe Right(1L)
          cause.get[String]("fieldPath") shouldBe Right("request.changes[0].title")
          cause.get[Vector[Json]]("facets") shouldBe Right(original.cause.descriptor.facets.map(facet => _json(facet.toRecord)))
          cause.get[Vector[Json]]("facets").toOption.get.map(_.hcursor.get[String]("reason").toOption).flatten shouldBe
            Vector("stale-entity-revision", "second")
        }
        original.previous.get should be theSameInstanceAs previous
        original.cause.descriptor.facets should have size 5
      }

      "fall back to standard for every ambiguous or invalid selector without changing execution" in {
        Given("canonical and alias query/form fields including duplicates and wrong primitives")
        val invalid = Vector(Record.data("textus.output.conclusion" -> "unknown"),
          Record.data("textus.output.conclusion" -> 1),
          Record.data("textus.output.conclusion" -> Vector("full")),
          Record.data("textus.output.conclusion" -> "full", "cncf.output.conclusion" -> "full"),
          Record.data("textus.output.conclusion" -> "full", "textus.output.conclusion" -> "standard"))
        When("the projection-only range selector reads the physical inputs")
        val selected = invalid.map(query => ConclusionDisclosure.detail(HttpRequest.fromPath(HttpRequest.GET, "/", query = query)))
        val form = ConclusionDisclosure.detail(HttpRequest.fromPath(HttpRequest.POST, "/",
          form = Record.data("cncf.output.conclusion" -> "full")))
        Then("invalid selection is standard and the single valid alias form selects full")
        selected shouldBe Vector.fill(invalid.size)(Detail.Standard)
        form shouldBe Detail.Full
      }
    }

    "server-owned physical Cookie evidence" which {
      "collect every physical cookie literal while replacing forged evidence and preserving normalized headers" in {
        Given("normalized authentication and session headers with all client-forged metadata casing variants")
        val normalized = Record.data("Authorization" -> "Bearer assembly-authorization",
          "x-textus-session" -> "normalized-session", "Accept" -> "application/json")
        val names = Vector("cncf.internal.disclosure.cookie", "CNCF.INTERNAL.DISCLOSURE.COOKIE",
          "CnCf.InTeRnAl.DiScLoSuRe.CoOkIe")
        val forged = normalized ++ Record.create(names.zip(Vector("forged-one", "forged-two", "forged-three")))
        val originalfields = forged.fields
        val cookies = Vector("preference=opaque-first; theme=opaque-second", "layout=opaque-third; empty=")
        When("the internal physical-header assembly retains actual cookie strings for disclosure discovery")
        val assembled = ConclusionDisclosure.withCookieCredentials(forged, cookies)
        val request = HttpRequest.fromPath(HttpRequest.GET, "/", header = assembled)
        val credentials = ConclusionDisclosure.credentials(Some(request))
        val direct = HttpRequest.fromPath(HttpRequest.GET, "/",
          header = normalized ++ Record.data("Cookie" -> cookies.mkString("; ")))
        Then("only server-owned Cookie evidence is retained and direct core Cookie discovery remains compatible")
        assembled.fields.filterNot(field => ConclusionDisclosure.isCookieMetadata(field.key)) shouldBe normalized.fields
        assembled.fields.filter(field => ConclusionDisclosure.isCookieMetadata(field.key)).map(_.value.single) shouldBe
          Vector(cookies.mkString("; "))
        assembled.getString("Cookie") shouldBe None
        assembled.getString("x-textus-session") shouldBe Some("normalized-session")
        credentials should contain allOf (cookies.mkString("; "), "opaque-first", "opaque-second", "opaque-third")
        credentials should not contain ""
        credentials shouldBe ConclusionDisclosure.credentials(Some(direct))
        Vector("forged-one", "forged-two", "forged-three").foreach(secret => credentials should not contain secret)
        forged.fields shouldBe originalfields
        request.header should be theSameInstanceAs assembled
      }

      "leave no private Cookie evidence when actual cookies are absent and preserve unrelated message text" in {
        Given("every forged metadata casing and a harmless header without any physical Cookie")
        val marker = "ordinary-visible-marker"
        val names = Vector("cncf.internal.disclosure.cookie", "CNCF.INTERNAL.DISCLOSURE.COOKIE",
          "CnCf.InTeRnAl.DiScLoSuRe.CoOkIe")
        val normalized = Record.create(names.map(_ -> marker)) ++ Record.data("Accept" -> "application/json")
        val originalfields = normalized.fields
        When("server-owned assembly receives no physical cookie strings")
        val assembled = ConclusionDisclosure.withCookieCredentials(normalized, Vector.empty)
        val credentials = ConclusionDisclosure.credentials(Some(HttpRequest.fromPath(HttpRequest.GET, "/", header = assembled)))
        val output = _encoded(ConclusionDisclosure.http(_conclusion(Vector(Facet.Message("safe " + marker))),
          Detail.Full, credentials = credentials))
        Then("forged metadata contributes neither a private field nor a redaction literal")
        assembled.fields shouldBe Record.data("Accept" -> "application/json").fields
        credentials shouldBe empty
        output should include ("safe " + marker)
        normalized.fields shouldBe originalfields
      }
    }

    "one security ceiling for HTTP and logs" which {
      "redact sensitive nested keys and Parameter associations while preserving safe revision facets" in {
        Given("secret-bearing records, schema-confidential fields and ordered sensitive and ordinary parameters")
        val nested = Record.data("password" -> "password-value", "nested" -> Vector(Map("api_key" -> "key-value")),
          "privateField" -> "schema-value", "empty" -> "")
        val original = _conclusion(Vector(Facet.RecordData(nested), Facet.Parameter.argument("accessToken"),
          Facet.Value("value-secret"), Facet.Expected("expected-secret"), Facet.Actual("actual-secret"),
          Facet.Input(value = Some("input-secret")), Facet.Properties(Map("innocent" -> "property-secret")),
          Facet.Parameter.argument("revision"), Facet.Expected(2L), Facet.Actual(1L)))
        val confidentiality = Map("privateField" -> DataConfidentiality.Secret)
        When("full HTTP and detailed logs project the same Conclusion")
        val outputs = Vector(ConclusionDisclosure.http(original, Detail.Full, confidentiality),
          ConclusionDisclosure.log(original, confidentiality)).map(_encoded)
        Then("every credential payload is redacted, ordinary revisions and explicit empty values remain, and sources are intact")
        outputs.foreach { output =>
          Vector("password-value", "key-value", "schema-value", "value-secret", "expected-secret", "actual-secret",
            "input-secret", "property-secret").foreach(secret => output should not include secret)
          val cause = parser.parse(output).toOption.get.hcursor.downField("observation").downField("cause")
          cause.downField("parameter").get[String]("name") shouldBe Right("accessToken")
          Vector("value", "expected", "actual", "input", "properties").foreach { key =>
            cause.get[String](key) shouldBe Right("[redacted]")
          }
          val facets = cause.get[Vector[Json]]("facets").toOption.get
          facets should have size 10
          facets(1) shouldBe _json(Facet.Parameter.argument("accessToken").toRecord)
          Vector(2 -> "value", 3 -> "expected", 4 -> "actual", 5 -> "input", 6 -> "properties").foreach { case (index, key) =>
            facets(index).hcursor.get[String](key) shouldBe Right("[redacted]")
          }
          facets(7) shouldBe _json(Facet.Parameter.argument("revision").toRecord)
          facets(8).hcursor.get[Long]("expected") shouldBe Right(2L)
          facets(9).hcursor.get[Long]("actual") shouldBe Right(1L)
          facets.head.hcursor.downField("record").get[String]("empty") shouldBe Right("")
        }
        nested.getString("password") shouldBe Some("password-value")
        original.cause.descriptor.facets(2) shouldBe Facet.Value("value-secret")
      }

      "sanitize labeled JSON YAML form Bearer Basic and literal physical credentials in innocent text" in {
        Given("physical credentials copied into otherwise unlabeled messages and labeled credential text")
        val request = HttpRequest.fromPath(HttpRequest.GET, "/", header = Record.data(
          "Authorization" -> "Bearer physical-bare-credential", "Cookie" -> "display=physical-cookie-value"),
          query = Record.data("apiKey" -> "physical-query-value"))
        val credentials = ConclusionDisclosure.credentials(Some(request))
        val text = "innocent physical-bare-credential physical-cookie-value physical-query-value " +
          "{\"password\":\"json-secret\"} token: yaml-secret\nsecret=form-secret&ok=true " +
          "bEaReR bearer-secret BASIC basic-secret"
        When("text and a full Conclusion message cross the disclosure filter")
        val safe = ConclusionDisclosure.sanitizeText(text, credentials)
        val body = _encoded(ConclusionDisclosure.http(_conclusion(Vector(Facet.Message(text))), Detail.Full,
          credentials = credentials))
        Then("all spellings and physical literal values disappear while harmless text remains")
        Vector("physical-bare-credential", "physical-cookie-value", "physical-query-value", "json-secret", "yaml-secret",
          "form-secret", "bearer-secret", "basic-secret").foreach { secret =>
          safe should not include secret
          body should not include secret
        }
        safe should include ("innocent")
      }

      "suppress unknown exceptions and HTTP internals without evaluating unknown payload text" in {
        Given("an unknown Throwable, source position and an opaque payload whose toString is forbidden")
        val opaque = new Object { override def toString: String = throw new IllegalStateException("must not stringify") }
        val original = _conclusion(Vector(Facet.Exception(new IllegalStateException("raw-secret-message")),
          Facet.SrcPos(SourcePosition("/private/source.scala", 12, 3)), Facet.Endpoint("private-endpoint"), Facet.Value(opaque),
          Facet.RecordData(Record.data("parameter" -> Record.data("name" -> opaque)))))
        When("HTTP full and detailed log projections inspect the typed values")
        val http = _encoded(ConclusionDisclosure.http(original, Detail.Full))
        val log = _encoded(ConclusionDisclosure.log(original))
        Then("neither output includes raw exception messages, and only logs retain safe exception structure")
        http should not include "raw-secret-message"
        log should not include "raw-secret-message"
        http should not include "IllegalStateException"
        http should not include "/private/source.scala"
        http should not include "private-endpoint"
        log should include ("IllegalStateException")
        http should include ("[redacted]")
      }

      "retain fixed structured diagnostics from every approved typed CNCF carrier" in {
        Given("all ten existing typed semantic exception carriers with fixed code path and reason")
        val carriers: Vector[Throwable] = Vector(
          new DisplayMutation.MutationException(DisplayMutation.Diagnostic("MUTATION", "request.fields", "invalid")),
          new DisplayService.ServiceException(DisplayService.Diagnostic("SERVICE", "request.screenId", "invalid")),
          new Display.ValidationException(Display.Diagnostic("DISPLAY", "model", "invalid")),
          new DisplayProjection.ProjectionException(DisplayProjection.Diagnostic("PROJECTION", "model", "invalid")),
          new DisplayProtocol.CodecException(DisplayProtocol.Diagnostic("PROTOCOL", "$", "invalid")),
          new DisplayRuntime.BindingException(DisplayRuntime.Diagnostic("RUNTIME", "binding", "invalid")),
          new DisplayBusinessOperation.DescriptorException(DisplayBusinessOperation.Diagnostic("BUSINESS", "bindings", "invalid")),
          new GeneratedLogicalUiAbi.AdmissionException(GeneratedLogicalUiAbi.Diagnostic("ABI", "definitions", "invalid")),
          new PresentationContext.ValidationException(PresentationContext.Diagnostic("CONTEXT", "flow", "invalid")),
          new PresentationContextCodec.CodecException(PresentationContextCodec.Diagnostic("CONTEXT_CODEC", "$", "invalid")))
        When("each original exception-bearing Conclusion is projected for HTTP and logs")
        val outputs = carriers.flatMap { carrier =>
          val current = _conclusion(Vector(Facet.Exception(carrier)))
          Vector(ConclusionDisclosure.http(current, Detail.Full), ConclusionDisclosure.log(current))
        }.map(_json)
        Then("each cause has a diagnostic code path and reason independent of raw exception suppression")
        outputs.foreach { output =>
          val diagnostic = output.hcursor.downField("observation").downField("cause").downField("diagnostic")
          diagnostic.get[String]("code").toOption.get should not be empty
          diagnostic.get[String]("path").toOption.get should not be empty
          diagnostic.get[String]("reason") shouldBe Right("invalid")
        }
      }

      "emit an explicit marker when arbitrary nested payload depth exceeds sixty-four" in {
        Given("a valid Conclusion containing eighty nested record payloads")
        val payload = (1 to 80).foldLeft[Any]("leaf-secret"){ (value, _) => Record.data("nested" -> value) }
        val original = _conclusion(Vector(Facet.Value(payload)))
        When("full HTTP projection safely bounds arbitrary nesting")
        val output = _encoded(ConclusionDisclosure.http(original, Detail.Full))
        Then("an explicit depth redaction replaces the unvisited tail")
        output should include ("[redacted:depth-limit]")
        output should not include "leaf-secret"
      }

      "preserve harmless Unicode and source values while redacting generated nested credentials through previous history" in {
        Given("a total generator of harmless Unicode text and distinct labeled credential values")
        val generated = for {
          size <- Gen.choose(1, 20)
          letters <- Gen.listOfN(size, Gen.oneOf("a", "Z", "日", "本", "Ω", "λ", "🙂"))
          number <- Gen.choose(1, 1000000)
        } yield (letters.mkString, "generated-credential-" + number)
        val property = Prop.forAll(generated) { case (text, secret) =>
          Given("an immutable generated nested record and identical previous Conclusion")
          val record = Record.data("safe" -> text, "password" -> secret,
            "nested" -> Array(Map("token" -> secret, "safe" -> text)))
          val previous = _conclusion(Vector(Facet.RecordData(record), Facet.Message("password=" + secret)))
          val original = previous.copy(previous = Some(previous))
          When("standard full and detailed log disclosure are projected")
          val outputs = Vector(ConclusionDisclosure.http(original, Detail.Standard),
            ConclusionDisclosure.http(original, Detail.Full), ConclusionDisclosure.log(original)).map(_encoded)
          Then("the secret is absent, harmless Unicode survives and the original values and identity are unchanged")
          outputs.foreach { output =>
            output should not include secret
            output should include (text)
          }
          record.getString("password") shouldBe Some(secret)
          original.previous.get should be theSameInstanceAs previous
          true
        }
        When("ScalaCheck checks at least one hundred undiscarded values")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("the total disclosure law records at least one hundred successes and zero discards")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }

  private def _conclusion(facets: Vector[Descriptor.Facet], previous: Option[Conclusion] = None): Conclusion =
    Conclusion(Conclusion.Status(appCode = Some(7401L), appStatus = Some("")),
      Observation(Phenomenon.Failure, Taxonomy.argumentInvalid, Cause.create(facets), Instant.EPOCH),
      Interpretation.domainFailure, Disposition.fix, previous)
  private def _encoded(record: Record): String = RecordEncoder.jsonC(record).TAKE
  private def _json(record: Record): Json = parser.parse(_encoded(record)).toOption.get
}
