package org.goldenport.cncf.filestore

import java.util.Locale
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.datatype.MimeType
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 25, 2026
 * @version Nov.  6, 2026
 */
final class GoogleDriveContentDigestSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoogleDriveFixture.*

  "External binary SHA-256 observations" should {
    "checked value construction" which {
      "normalizes case and gives equal values the same hash without displaying the digest" in {
        Given("the same 64 ASCII hexadecimal characters in upper and lower case")
        val lower = "0123456789abcdef" * 4
        val upper = lower.toUpperCase(Locale.ROOT)
        When("the checked factory constructs both observations and one different value")
        val first = FileStoreContentDigest.sha256C(lower).TAKE
        val second = FileStoreContentDigest.sha256C(upper).TAKE
        val different = FileStoreContentDigest.sha256C("f" * 64).TAKE
        Then("algorithm and normalized value define equality and the display omits raw content identity")
        first.algorithm shouldBe "sha256"
        first.value shouldBe lower
        second.value shouldBe lower
        second shouldBe first
        second.hashCode shouldBe first.hashCode
        different should not be first
        first.equals(null) shouldBe false
        first.equals(lower) shouldBe false
        first.toString shouldBe "FileStoreContentDigest(sha256)"
      }

      "rejects absent, malformed and padded inputs with a fixed structured argument failure" in {
        Given("null, empty, wrong-length, non-ASCII, nonhexadecimal and whitespace-bearing inputs")
        val invalid = Vector(null, "", "a" * 63, "a" * 65, "g" * 64, "Ａ" * 64,
          " " + ("a" * 63), ("a" * 63) + "\n", "a" * 32 + "\t" + "a" * 31,
          " " + ("a" * 64) + " ")
        When("the factory checks every submitted digest")
        val outcomes = invalid.map(FileStoreContentDigest.sha256C)
        Then("each failure retains one argument category and safe message without echoing the input")
        val expected = failure(Consequence.argumentInvalid("Invalid file store content digest"))
        outcomes.foreach { outcome =>
          val actual = failure(outcome)
          actual.status shouldBe expected.status
          actual.observation.taxonomy shouldBe expected.observation.taxonomy
          actual.cause.getMessage shouldBe Some("Invalid file store content digest")
          disclosures(actual).foreach(output => output should not include ("a" * 63))
        }
      }

      "preserves generated valid digests through checked construction and native stat" in {
        Given("a generator of exactly 64 upper or lower case ASCII hexadecimal characters")
        val digests = Gen.listOfN(64, Gen.oneOf("0123456789abcdefABCDEF".toVector)).map(_.mkString)
        When("one hundred generated values pass through the factory and independent native metadata GET")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(digests) { value =>
          val fixture = new Harness(_ => jsonResponse(_with_digest(metadata(), Json.fromString(value))))
          val checked = FileStoreContentDigest.sha256C(value).TAKE
          val observed = fixture.evaluate(store().stat(reference)).TAKE.contentDigest
          checked.value == value.toLowerCase(Locale.ROOT) && observed.contains(checked) &&
            observed.exists(_.hashCode == checked.hashCode) && fixture.requests.map(_.method) == Vector("GET")
        })
        Then("all generated cases succeed without discards or a content download")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }

    "optional metadata compatibility" which {
      "keeps previous constructor arguments and version default usable for a binary observation" in {
        Given("an existing caller supplying the original six fields or the seventh version field")
        val mime = MimeType("application/octet-stream")
        When("that caller constructs metadata without a content digest argument")
        val defaulted = FileStoreMetadata(reference, "label", mime, Some(3L), Vector(parent), FileStoreKind.File)
        val versioned = FileStoreMetadata(reference, "label", mime, Some(3L), Vector(parent), FileStoreKind.File, Some("9"))
        Then("the prior fields retain their meaning while the digest remains unknown")
        defaulted.version shouldBe None
        defaulted.contentDigest shouldBe None
        defaulted.storageRoot shouldBe None
        versioned.version shouldBe Some("9")
        versioned.contentDigest shouldBe None
        versioned.storageRoot shouldBe None
        versioned.reference shouldBe reference
        versioned.byteLength shouldBe Some(3L)
      }
    }

    "native provider metadata" which {
      "reads the provider checksum through one independent stat GET without downloading the original" in {
        Given("a provider-computed checksum in a controlled native metadata response")
        val value = "ABCDEF0123456789" * 4
        val fixture = new Harness(_ => jsonResponse(_with_digest(metadata(), Json.fromString(value))))
        val program = store().stat(reference)
        val before = fixture.requests
        When("the stat program is interpreted independently of any upload session")
        val observed = fixture.evaluate(program).TAKE
        Then("one authenticated metadata GET requests SHA-256 and preserves its normalized typed value")
        before shouldBe empty
        observed.contentDigest shouldBe Some(FileStoreContentDigest.sha256C(value).TAKE)
        fixture.requests should have size 1
        fixture.acquisitions shouldBe 1
        val request = fixture.requests.head
        request.method shouldBe "GET"
        request.path shouldBe s"$driveOrigin/drive/v3/files/${reference.key}?supportsAllDrives=true&fields=id,name,mimeType,size,parents,trashed,version,sha256Checksum,driveId"
        request.path should not include "alt=media"
        request.headers shouldBe Map("Authorization" -> "Bearer synthetic-access-marker", "Accept" -> "application/json")
        request.text shouldBe None
        request.bag shouldBe None
      }

      "retains absence for binary files and folders without using MD5 or writable custom properties" in {
        Given("binary and folder metadata without provider SHA-256, including untrusted alternate checksum fields")
        val responses = Vector(metadata(), metadata(mime = folderMime, size = None),
          metadata().deepMerge(Json.obj("md5Checksum" -> Json.fromString("f" * 32),
            "appProperties" -> Json.obj("sha256Checksum" -> Json.fromString("a" * 64)))))
        When("each independent stat observes only its actual provider metadata")
        val observations = responses.map { response =>
          val fixture = new Harness(_ => jsonResponse(response))
          (fixture.evaluate(store().stat(reference)).TAKE, fixture.requests)
        }
        Then("unknown stays None, including for collections, with exactly one metadata request each")
        observations.foreach { case (observed, requests) =>
          observed.contentDigest shouldBe None
          requests.map(_.method) shouldBe Vector("GET")
        }
        observations(1)._1.kind shouldBe FileStoreKind.Collection
      }

      "rejects every malformed present checksum without metadata success or provider echo" in {
        Given("present numeric, null, empty, nonhexadecimal, wrong-length and whitespace checksum values")
        val malformed = Vector(Json.fromInt(1), Json.Null, Json.fromString(""), Json.fromString("g" * 64),
          Json.fromString("a" * 63), Json.fromString("a" * 65), Json.fromString(" " + "a" * 64),
          Json.fromString("a" * 63 + "\n"))
        When("the native stat parser receives each malformed observation")
        val outcomes = malformed.map { value =>
          val fixture = new Harness(_ => jsonResponse(_with_digest(metadata(name = "provider-body-marker"), value)))
          (fixture.evaluate(store().stat(reference)), fixture.requests)
        }
        Then("one fixed metadata failure replaces each invalid observation and no body is downloaded")
        val expected = failure(Consequence.resourceInvalid("Google Drive file metadata is invalid"))
        outcomes.foreach { case (outcome, requests) =>
          val actual = failure(outcome)
          actual.status shouldBe expected.status
          actual.observation.taxonomy shouldBe expected.observation.taxonomy
          actual.cause.getMessage shouldBe Some("Google Drive file metadata is invalid")
          disclosures(actual).foreach { output =>
            output should not include "provider-body-marker"
            output should not include ("a" * 63)
            output should not include ("g" * 64)
          }
          requests.map(_.method) shouldBe Vector("GET")
        }
      }

      "uses the same observation parser for native binary creation" in {
        Given("a resumable creation response whose final metadata includes provider SHA-256")
        val value = "b" * 64
        val fixture = new Harness(request => if (request.method == "POST") initiation()
          else jsonResponse(_with_digest(metadata(), Json.fromString(value))))
        val content = Bag.binary(Array[Byte](1, 2, 3))
        When("the existing create-only program uploads its borrowed binary content")
        val observed = fixture.evaluate(store().create(parent, "label", MimeType("application/octet-stream"), content)).TAKE
        Then("the completion retains the checksum without a hidden stat or content read")
        observed.contentDigest shouldBe Some(FileStoreContentDigest.sha256C(value).TAKE)
        fixture.requests.map(_.method) shouldBe Vector("POST", "PUT")
        fixture.requests.head.path should include ("fields=" + fields)
        fixture.requests.last.bag shouldBe Some(content)
      }

      "reports direct completion as metadata even when SHA-256 is absent or different" in {
        Given("a retained direct binding and same-size provider completions with absent or distinct SHA-256 observations")
        val upload = DirectFileUpload(parent, "label", MimeType("application/octet-stream"), 3,
          "googleDriveResumable", sessionUrl)
        val values = Vector(None, Some("a" * 64), Some("b" * 64))
        When("the provider inspects each completion through the existing session status operation")
        val outcomes = values.map { value =>
          val response = value.fold(metadata())(digest => _with_digest(metadata(), Json.fromString(digest)))
          val fixture = new Harness(_ => jsonResponse(response))
          (fixture.evaluate(store().inspectDirectUpload(upload)).TAKE, fixture.requests)
        }
        Then("Completed carries only the observation; it performs no expected-digest comparison or TKL registration")
        outcomes.zip(values).foreach { case ((outcome, requests), value) =>
          outcome match {
            case DirectFileUploadStatus.Completed(observed) =>
              observed.contentDigest shouldBe value.map(digest => FileStoreContentDigest.sha256C(digest).TAKE)
              observed.byteLength shouldBe Some(3L)
            case _ => fail("Expected provider completion metadata")
          }
          requests.map(_.method) shouldBe Vector("PUT")
          requests.map(_.path) shouldBe Vector(sessionUrl)
        }
      }

      "preserves child checksums and rejects a malformed child through the existing page failure" in {
        Given("one scoped child page with valid SHA-256 and another with a malformed present checksum")
        val valid = _with_digest(metadata(), Json.fromString("C" * 64))
        val invalid = _with_digest(metadata(name = "provider-body-marker"), Json.Null)
        val first = new Harness(_ => jsonResponse(Json.obj("files" -> Json.arr(valid))))
        val second = new Harness(_ => jsonResponse(Json.obj("files" -> Json.arr(invalid))))
        When("each page is read through the existing native children capability")
        val observed = first.evaluate(store().listChildren(parent, 1)).TAKE
        val rejected = second.evaluate(store().listChildren(parent, 1))
        Then("the valid child retains its digest and the malformed page keeps its fixed safe diagnostic")
        observed.items.head.contentDigest shouldBe Some(FileStoreContentDigest.sha256C("c" * 64).TAKE)
        failure(rejected).cause.getMessage shouldBe Some("Google Drive child listing is invalid")
        disclosures(failure(rejected)).foreach(output => output should not include "provider-body-marker")
        Vector(first, second).foreach { fixture =>
          fixture.requests.map(_.method) shouldBe Vector("GET")
          fixture.requests.head.bag shouldBe None
        }
      }
    }
  }

  private def _with_digest(metadata: Json, value: Json): Json =
    metadata.deepMerge(Json.obj("sha256Checksum" -> value))
}
