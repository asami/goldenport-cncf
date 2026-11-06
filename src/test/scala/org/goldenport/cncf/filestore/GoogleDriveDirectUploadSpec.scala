package org.goldenport.cncf.filestore

import org.goldenport.datatype.MimeType
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 25, 2026
 * @version Nov.  6, 2026
 */
final class GoogleDriveDirectUploadSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoogleDriveFixture.*

  "Optional Google Drive direct upload" should {
    "prepare one narrow session without reading or sending original bytes" in {
      Given("managed synthetic credentials and the trusted original declaration")
      val fixture = new Harness(_ => initiation())
      When("preparation interprets the native program")
      val upload = fixture.evaluate(store().prepareDirectUpload(parent, "label", MimeType("application/octet-stream"), 3)).TAKE
      Then("only the initial POST occurs and the returned binding has a redacted display")
      upload shouldBe _upload()
      upload.toString shouldBe "DirectFileUpload(<redacted>)"
      fixture.requests.map(_.method) shouldBe Vector("POST")
      fixture.requests.head.bag shouldBe None
      fixture.requests.head.headers("X-Upload-Content-Length") shouldBe "3"
      fixture.requests.head.headers("Authorization") shouldBe "Bearer synthetic-access-marker"
    }

    "inspect the existing session and independently confirm the completed provider object" in {
      Given("a retained binding whose upload was completed by another client")
      val fixture = new Harness(_ => jsonResponse(metadata()))
      When("the server inspects provider status without resending original bytes")
      val result = fixture.evaluate(store().inspectDirectUpload(_upload())).TAKE
      Then("the provider metadata confirms that binding through one authenticated empty PUT")
      result match {
        case DirectFileUploadStatus.Completed(value) =>
          value.reference shouldBe reference
          value.parents shouldBe Vector(parent)
          value.byteLength shouldBe Some(3L)
        case _ => fail("Expected confirmed completion")
      }
      fixture.requests.map(_.path) shouldBe Vector(sessionUrl)
      fixture.requests.head.method shouldBe "PUT"
      fixture.requests.head.headers("Content-Range") shouldBe "bytes */3"
      bytes(fixture.requests.head.bag.get) shouldBe empty
    }

    "preserve provider-confirmed offsets across the declared nonempty range" in {
      Given("resumable uploads with provider-confirmed prefixes")
      val property = Prop.forAll(Gen.choose(2, 1000000)) { length =>
        val end = length / 2 - 1
        val fixture = new Harness(_ => textResponse("", 308, headers = Record.data("Range" -> s"bytes=0-$end")))
        When("inspection observes that prefix")
        val result = fixture.evaluate(store().inspectDirectUpload(_upload(length))).TAKE
        Then("only the next confirmed byte offset is returned, without a second session")
        result == DirectFileUploadStatus.Pending(end + 1L) && fixture.requests.size == 1
      }
      Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property).passed shouldBe true
      val empty = new Harness(_ => textResponse("", 308))
      empty.evaluate(store().inspectDirectUpload(_upload())).TAKE shouldBe DirectFileUploadStatus.Pending(0)
      val full = new Harness(_ => textResponse("", 308, headers = Record.data("Range" -> "bytes=0-2")))
      full.evaluate(store().inspectDirectUpload(_upload())).TAKE shouldBe DirectFileUploadStatus.Pending(3)
      val expired = new Harness(_ => textResponse("", 404))
      expired.evaluate(store().inspectDirectUpload(_upload())).TAKE shouldBe DirectFileUploadStatus.Expired
    }

    "reject untrusted session bindings before credentials or HTTP" in {
      Given("foreign stores, hostile URIs and incompatible declarations")
      val invalid = Vector(
        _upload().copy(parent = FileStoreRef("other", parent.key)),
        _upload().copy(uploadUri = "https://attacker.invalid/upload"),
        _upload().copy(uploadUri = sessionUrl + "&upload_id=duplicate"),
        _upload().copy(protocol = "other"), _upload().copy(byteLength = -1),
        _upload().copy(mediaType = MimeType(folderMime))
      )
      invalid.foreach { upload =>
        val fixture = new Harness(_ => throw new AssertionError("No HTTP for invalid binding"))
        When("a provider receives the forged restored binding")
        val result = fixture.evaluate(store(refreshCredentials()).inspectDirectUpload(upload))
        Then("a structured failure occurs without exposing or using it")
        result.isSuccess shouldBe false
        fixture.requests shouldBe empty
        fixture.acquisitions shouldBe 0
        disclosures(failure(result)).foreach(output => output should not include "synthetic-session-marker")
      }
    }

    "reject malformed progress and completed objects that do not match preparation" in {
      Given("a retained declaration and invalid provider progress or metadata")
      val responses = Vector("bytes=1-1", "bytes=0-3", "bytes=0-01", "bytes=0-9223372036854775807", "bad")
        .map(value => textResponse("", 308, headers = Record.data("Range" -> value))) ++
        Vector(jsonResponse(metadata(name = "different")), jsonResponse(metadata(size = Some("4"))),
          jsonResponse(metadata(parents = Some(Vector("other")))), textResponse("", 403), textResponse("", 503))
      responses.foreach { response =>
        val fixture = new Harness(_ => response)
        When("provider inspection interprets the incompatible observation")
        val result = fixture.evaluate(store().inspectDirectUpload(_upload()))
        Then("it never becomes confirmed success or another upload")
        result.isSuccess shouldBe false
        fixture.requests.size shouldBe 1
      }
    }
  }

  private def _upload(length: Long = 3): DirectFileUpload = DirectFileUpload(parent, "label",
    MimeType("application/octet-stream"), length, "googleDriveResumable", sessionUrl)
}
