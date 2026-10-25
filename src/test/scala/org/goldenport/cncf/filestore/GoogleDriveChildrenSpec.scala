package org.goldenport.cncf.filestore

import java.io.IOException
import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import io.circe.Json
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.http.{HttpExecutionAdmission, HttpExecutionMethod}
import org.goldenport.protocol.Property
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 25, 2026
 * @version Oct. 25, 2026
 */
final class GoogleDriveChildrenSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoogleDriveFixture.*

  "E1 Google Drive scoped child query" should afterWord(
    "in spec:google-drive-file-store-v1, example:E1, rules:R1, phase:ESS3-SCOPED-MATERIAL-READ-120") {
    "preserve only the selected parent and opaque query values" which {
      "uses one native metadata GET with exact shared-drive flags and encoded continuation" in {
        Given("docs/spec/google-drive-file-store-v1.md R1 E1: a selected parent and an opaque token containing query, URL and Unicode characters")
        val token = "opaque +/&=?%#資料:https://example.invalid/path"
        val fixture = new Harness(_ => jsonResponse(_page(Vector(metadata()))), enabled = true)
        val provider: FileStoreChildren = store()
        val program = provider.listChildren(parent, 7, Some(token))
        val before = fixture.requests
        When("one independently requested child page is interpreted")
        val result = fixture.evaluate(program).TAKE
        Then("only the selected scoped GET runs with managed headers, ceiling and all exact decoded query values")
        before shouldBe empty
        result.items.map(_.reference) shouldBe Vector(reference)
        result.nextPageToken shouldBe None
        fixture.requests should have size 1
        fixture.acquisitions shouldBe 1
        val request = fixture.requests.head
        request.method shouldBe "GET"
        val uri = new URI(request.path)
        uri.getScheme shouldBe "https"
        uri.getHost shouldBe "www.googleapis.com"
        uri.getRawPath shouldBe "/drive/v3/files"
        uri.getRawFragment shouldBe null
        _query(request.path) shouldBe _expected_query(7) :+ ("pageToken" -> token)
        request.path should include ("pageToken=opaque+%2B%2F%26%3D%3F%25%23")
        request.path should not include token
        request.headers shouldBe Map("Authorization" -> "Bearer synthetic-access-marker", "Accept" -> "application/json")
        request.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("65536")
        request.properties.exists(_.name.equalsIgnoreCase("http.response-body-mode")) shouldBe false
        request.text shouldBe None
        request.bag shouldBe None
        fixture.trace should include ("uow:http:get")
        Vector(token, parent.key, "synthetic-access-marker").foreach(marker => fixture.trace should not include marker)
        result.toString shouldBe "FileStoreChildPage(<redacted>)"
      }

      "omits absent tokens and respects a tighter runtime ceiling without a parent stat" in {
        Given("docs/spec/google-drive-file-store-v1.md R1 E1: a first page and a runtime ceiling tighter than metadata's ceiling")
        val fixture = new Harness(_ => jsonResponse(_page(Vector.empty)), bytes = 1024)
        When("the first page is requested through the concrete provider with its default token argument")
        val result = fixture.evaluate(store().listChildren(parent, 100)).TAKE
        Then("a single GET omits continuation and carries the host-tightened metadata bound")
        result.items shouldBe empty
        result.nextPageToken shouldBe None
        fixture.requests.map(_.method) shouldBe Vector("GET")
        _query(fixture.requests.head.path) shouldBe _expected_query(100)
        fixture.requests.head.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("1024")
      }

      "refreshes managed credentials once before exactly one page GET" in {
        Given("docs/spec/google-drive-file-store-v1.md R1 E1: synthetic refresh credentials and one valid page")
        val fixture = new Harness(request => if (request.path == oauthUrl) jsonResponse(tokenJson("synthetic-refreshed"))
          else jsonResponse(_page(Vector(metadata()))))
        When("one scoped page operation uses managed refresh authorization")
        val result = fixture.evaluate(store(refreshCredentials()).listChildren(parent, 1)).TAKE
        Then("one OAuth POST supplies the Bearer header to one page GET with no hidden metadata call")
        result.items.map(_.reference) shouldBe Vector(reference)
        fixture.requests.map(_.method) shouldBe Vector("POST", "GET")
        fixture.requests.head.path shouldBe oauthUrl
        fixture.requests.last.headers("Authorization") shouldBe "Bearer synthetic-refreshed"
        _query(fixture.requests.last.path) shouldBe _expected_query(1)
      }
    }
  }

  "E2 Google Drive independent page observations" should afterWord(
    "in spec:google-drive-file-store-v1, example:E2, rules:R2, phase:ESS3-SCOPED-MATERIAL-READ-120") {
    "retain continuation and child identity semantics" which {
      "accepts empty pages with continuation and never follows them automatically" in {
        Given("docs/spec/google-drive-file-store-v1.md R2 E2: an empty page carrying an opaque continuation at the admitted length bound")
        val token = "x" * 2048
        val fixture = new Harness(_ => jsonResponse(_page(Vector.empty, Some(token))))
        When("the empty child page is interpreted")
        val result = fixture.evaluate(store().listChildren(parent, 1)).TAKE
        Then("the empty page retains continuation exactly and no next-page request is issued")
        result.items shouldBe empty
        result.nextPageToken shouldBe Some(token)
        result.toString shouldBe "FileStoreChildPage(<redacted>)"
        result.toString should not include token
        fixture.requests.map(_.method) shouldBe Vector("GET")
      }

      "preserves distinct child identities with duplicate display labels and absent observations" in {
        Given("docs/spec/google-drive-file-store-v1.md R2 E2: equally named file and collection children with distinct identities")
        val children = Vector(metadata(size = None, version = None),
          metadata(id = "folder_02", mime = folderMime, size = None, version = None))
        val fixture = new Harness(_ => jsonResponse(_page(children).deepMerge(Json.obj("incompleteSearch" -> Json.False))))
        When("the exactly full page is interpreted without a continuation field")
        val result = fixture.evaluate(store().listChildren(parent, 2)).TAKE
        Then("metadata preserves labels, kinds, unknown size/version and parent observations and ends solely by token absence")
        result.items.map(_.name) shouldBe Vector("label", "label")
        result.items.map(_.reference) shouldBe Vector(reference, FileStoreRef(parent.store, "folder_02"))
        result.items.map(_.kind) shouldBe Vector(FileStoreKind.File, FileStoreKind.Collection)
        result.items.foreach { item =>
          item.parents shouldBe Vector(parent)
          item.byteLength shouldBe None
          item.version shouldBe None
        }
        result.nextPageToken shouldBe None
        fixture.requests should have size 1
      }

      "retains continuation on a short nonempty page without inferring completion from its count" in {
        Given("docs/spec/google-drive-file-store-v1.md R2 E2: one valid child and a continuation containing a space")
        val fixture = new Harness(_ => jsonResponse(_page(Vector(metadata()), Some("next page"))))
        When("a page smaller than the requested limit is interpreted")
        val result = fixture.evaluate(store().listChildren(parent, 10)).TAKE
        Then("the short page preserves its continuation and remains a single independent observation")
        result.items should have size 1
        result.nextPageToken shouldBe Some("next page")
        fixture.requests should have size 1
      }
    }
  }

  "E3 Google Drive malformed child pages" should afterWord(
    "in spec:google-drive-file-store-v1, example:E3, rules:R2, phase:ESS3-SCOPED-MATERIAL-READ-120") {
    "reject unusable observations without disclosing provider data" which {
      "rejects wrong parents, self references, duplicate identities, excess counts and invalid typed metadata" in {
        Given("docs/spec/google-drive-file-store-v1.md R2 E3: pages violating child membership, uniqueness, bounds or the shared metadata contract")
        val invalid = Vector(
          _page(Vector(metadata(parents = Some(Vector("other-parent"))))),
          _page(Vector(metadata(parents = None))),
          _page(Vector(metadata(id = parent.key))),
          _page(Vector(metadata(), metadata())),
          _page(Vector(metadata(), metadata(id = "other_01"), metadata(id = "other_02"))),
          _page(Vector(Json.Null)), _page(Vector(Json.fromString("provider-body-marker"))),
          _page(Vector(metadata(id = "bad/id"))), _page(Vector(metadata(name = ""))),
          _page(Vector(metadata(mime = "bad mime"))), _page(Vector(metadata(size = Some("00")))),
          _page(Vector(metadata(size = Some("9223372036854775808")))),
          _page(Vector(metadata(version = Some("01")))),
          _page(Vector(metadata().deepMerge(Json.obj("trashed" -> Json.True)))),
          _page(Vector(metadata().deepMerge(Json.obj("size" -> Json.fromInt(1))))),
          _page(Vector(metadata().deepMerge(Json.obj("parents" -> Json.arr(Json.fromInt(1)))))))
        invalid.foreach { body =>
          val fixture = new Harness(_ => jsonResponse(body))
          When("the bounded child page is interpreted")
          val result = fixture.evaluate(store().listChildren(parent, 2, Some("private-query-marker")))
          Then("the complete page is rejected as a fixed resource failure after only one request")
          _expect_invalid_page(result)
          fixture.requests.map(_.method) shouldBe Vector("GET")
        }
      }

      "rejects malformed envelopes, continuation primitives and incomplete-search observations" in {
        Given("docs/spec/google-drive-file-store-v1.md R2 E3: malformed JSON, wrong MIME and invalid page envelope fields")
        val valid = _page(Vector(metadata()))
        val invalidtokens = Vector(Json.Null, Json.fromInt(1), Json.False, Json.fromString(""),
          Json.fromString("x" * 2049), Json.fromString("private-token-marker\n"), Json.fromString("bad\u0085token"))
        val invalid = Vector(Json.Null, Json.arr(valid), Json.obj(),
          valid.deepMerge(Json.obj("files" -> Json.Null)), valid.deepMerge(Json.obj("files" -> Json.obj()))) ++
          invalidtokens.map(value => valid.deepMerge(Json.obj("nextPageToken" -> value))) ++
          Vector(Json.True, Json.Null, Json.fromString("false"), Json.fromInt(0))
            .map(value => valid.deepMerge(Json.obj("incompleteSearch" -> value)))
        val responses = invalid.map(jsonResponse(_)) ++ Vector(textResponse("{provider-body-marker"),
          textResponse(valid.noSpaces, mime = "text/plain"))
        responses.foreach { response =>
          val fixture = new Harness(_ => response, enabled = true)
          When("the malformed provider observation is interpreted")
          val result = fixture.evaluate(store().listChildren(parent, 2, Some("private-query-marker")))
          Then("no malformed success becomes a page or triggers a restart and fixed diagnostics contain no token or body")
          _expect_invalid_page(result)
          fixture.requests should have size 1
          Vector("private-query-marker", "private-token-marker", "provider-body-marker", parent.key, reference.key)
            .foreach(marker => fixture.trace should not include marker)
        }
      }
    }
  }

  "E4 Google Drive child input and native failures" should afterWord(
    "in spec:google-drive-file-store-v1, example:E4, rules:R1,R2, phase:ESS3-SCOPED-MATERIAL-READ-120") {
    "validate before managed authorization and retain existing failure meanings" which {
      "rejects invalid references, limits and tokens before refresh or driver acquisition" in {
        Given("docs/spec/google-drive-file-store-v1.md R1 E4: refresh credentials and invalid scoped listing inputs")
        val references = Vector(null, FileStoreRef("foreign", parent.key)) ++
          Vector(null, "", "a/b", "https://example.invalid/id", "資料", "x?y", "a b", "a\u007fb", "x" * 257)
            .map(key => FileStoreRef(parent.store, key))
        val queries = Vector((0, None), (-1, None), (101, None), (Int.MaxValue, None),
          (1, null), (1, Some(null)), (1, Some("")), (1, Some("x" * 2049)),
          (1, Some("private-token-marker\n")), (1, Some("bad\u0085token")))
        val inputs = references.map(ref => (ref, 1, Option.empty[String], "Invalid Google Drive file reference")) ++
          queries.map { case (limit, token) => (parent, limit, token, "Invalid Google Drive child listing input") }
        inputs.foreach { case (ref, limit, token, message) =>
          val fixture = new Harness(_ => throw new AssertionError("Invalid child input must not request refresh or HTTP"))
          When("the invalid page request is interpreted with refresh credentials")
          val result = fixture.evaluate(store(refreshCredentials()).listChildren(ref, limit, token))
          Then("a fixed structured argument failure precedes all credential and driver effects")
          val actual = failure(result)
          actual.observation.taxonomy shouldBe failure(Consequence.argumentInvalid("fixed")).observation.taxonomy
          actual.cause.getMessage shouldBe Some(message)
          disclosures(actual).foreach(output => output should not include "private-token-marker")
          fixture.requests shouldBe empty
          fixture.acquisitions shouldBe 0
        }
      }

      "retains read status categories without token, identity or response-body disclosure and without retries" in {
        Vector(201, 302, 400, 401, 403, 404, 429, 500, 503).foreach { code =>
          Given(s"docs/spec/google-drive-file-store-v1.md R2 E4: an unsuccessful native page response for status $code")
          val markers = Vector(parent.key, reference.key, "private-query-marker", "provider-body-marker", "synthetic-access-marker")
          val response = textResponse(markers.mkString(" "), code)
          val fixture = new Harness(_ => response, enabled = true)
          val expected = response.code match {
            case 401 | 403 => Consequence.securityPermissionDenied("Google Drive file access was denied")
            case 404 => Consequence.resourceNotFound("Google Drive file was not found")
            case value if value >= 500 => Consequence.serviceUnavailable("Google Drive file service is unavailable")
            case _ => Consequence.resourceInvalid("Google Drive file response is invalid")
          }
          When("the unsuccessful child read is interpreted once")
          val actual = failure(fixture.evaluate(store().listChildren(parent, 1, Some("private-query-marker"))))
          Then("the existing read taxonomy and fixed message propagate without automatic restart or sensitive disclosure")
          _expect_conclusion(actual, failure(expected))
          (disclosures(actual) :+ fixture.trace).foreach(output => markers.foreach(marker => output should not include marker))
          fixture.requests.map(_.method) shouldBe Vector("GET")
        }
      }

      "preserves native host admission and transport failures without a provider escape" in {
        Given("docs/spec/google-drive-file-store-v1.md R1 R2 E4: a native runtime denying Drive and a counted failing driver")
        val denied = new Harness(_ => throw new AssertionError("Denied GET must not acquire the driver"), origins = Vector("https://oauth2.googleapis.com"))
        val path = driveOrigin + "/drive/v3/files?q=selected"
        val expected = failure(HttpExecutionAdmission.resolveC(denied.context, HttpExecutionMethod.Get, path,
          Vector(Property("http.max-response-bytes", "65536", None))))
        val error = new IOException("synthetic-transport-marker")
        val broken = new Harness(_ => throw error)
        When("one page reaches native admission and another reaches a native transport failure")
        val denial = failure(denied.evaluate(store().listChildren(parent, 1)))
        val transport = failure(broken.evaluate(store().listChildren(parent, 1)))
        Then("the native Conclusions survive and neither failure triggers another request")
        _expect_conclusion(denial, expected)
        denial.observation.isMatch(expected.observation) shouldBe true
        denial.interpretation shouldBe expected.interpretation
        denial.disposition shouldBe expected.disposition
        denied.requests shouldBe empty
        denied.acquisitions shouldBe 0
        transport.getException.get should be theSameInstanceAs error
        transport.status shouldBe Conclusion.from(error).status
        transport.observation.taxonomy shouldBe Conclusion.from(error).observation.taxonomy
        broken.requests.map(_.method) shouldBe Vector("GET")
      }
    }
  }

  "E5 Google Drive bounded opaque query properties" should afterWord(
    "in spec:google-drive-file-store-v1, example:E5, rules:R1,R2, phase:ESS3-SCOPED-MATERIAL-READ-120") {
    "round-trip valid limits and opaque tokens without changing scope" in {
      Given("docs/spec/google-drive-file-store-v1.md R1 R2 E5: bounded limits and opaque tokens with punctuation, spaces and Unicode")
      val inputs = for {
        limit <- Gen.choose(1, 100)
        length <- Gen.choose(1, 40)
        chars <- Gen.listOfN(length, Gen.oneOf("aZ09 +/&=?%#:_-資料λ".toVector))
      } yield (limit, chars.mkString)
      When("thirty generated independent page reads preserve every query value")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(inputs) { input =>
        val (limit, token) = input
        val fixture = new Harness(_ => jsonResponse(_page(Vector(metadata()), Some(token))))
        val page = fixture.evaluate(store().listChildren(parent, limit, Some(token))).toOption
        page.exists(value => value.items.map(_.reference) == Vector(reference) && value.nextPageToken.contains(token)) &&
          fixture.requests.size == 1 && fixture.requests.head.method == "GET" &&
          _query(fixture.requests.head.path) == (_expected_query(limit) :+ ("pageToken" -> token))
      })
      Then("all generated pages retain their opaque continuation and exact parent filter without discarded cases")
      result.passed shouldBe true
      result.succeeded should be >= 30
      result.discarded shouldBe 0
    }
  }

  private def _page(items: Vector[Json], token: Option[String] = None): Json =
    Json.obj((Vector("files" -> Json.arr(items*)) ++ token.map(value => "nextPageToken" -> Json.fromString(value)).toVector)*)

  private def _query(path: String): Vector[(String, String)] =
    new URI(path).getRawQuery.split("&", -1).toVector.map { entry =>
      val pair = entry.split("=", 2)
      URLDecoder.decode(pair(0), StandardCharsets.UTF_8) -> URLDecoder.decode(pair(1), StandardCharsets.UTF_8)
    }

  private def _expected_query(limit: Int): Vector[(String, String)] = Vector(
    "q" -> s"'${parent.key}' in parents and trashed = false", "supportsAllDrives" -> "true",
    "includeItemsFromAllDrives" -> "true", "pageSize" -> limit.toString,
    "fields" -> s"nextPageToken,incompleteSearch,files($fields)")

  private def _expect_conclusion(actual: Conclusion, expected: Conclusion): Unit = {
    actual.status shouldBe expected.status
    actual.observation.taxonomy shouldBe expected.observation.taxonomy
    actual.cause.getMessage shouldBe expected.cause.getMessage
  }

  private def _expect_invalid_page(result: Consequence[FileStoreChildPage]): Unit = {
    val actual = failure(result)
    _expect_conclusion(actual, failure(Consequence.resourceInvalid("Google Drive child listing is invalid")))
    disclosures(actual).foreach { output =>
      Vector("private-query-marker", "private-token-marker", "provider-body-marker", parent.key, reference.key)
        .foreach(marker => output should not include marker)
    }
  }
}
