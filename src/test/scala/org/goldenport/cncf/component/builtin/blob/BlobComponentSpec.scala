package org.goldenport.cncf.component.builtin.blob

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.datatype.ContentType
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.schema.{Multiplicity, XBlob}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for BL-03A Blob user-facing metadata and payload operations.
 *
 * @since   Apr. 26, 2026
 * @version Apr. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class BlobComponentSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "Builtin Blob component" should {
    "be installed in the default subsystem" in {
      Given("the default command-mode subsystem")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))

      When("looking up the Blob component")
      val component = subsystem.findComponent("blob")

      Then("the builtin Blob component is available")
      component.map(_.name) shouldBe Some("blob")
    }

    "expose public request and response metadata for Blob operations" in {
      Given("the default command-mode subsystem")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val blob = subsystem.findComponent("blob").getOrElse(fail("missing Blob component"))

      When("reading the protocol metadata for user-facing Blob operations")
      val service = blob.protocol.services.services.find(_.name == "blob").getOrElse(fail("missing Blob service"))
      val operations = service.operations.operations.toVector.map(op => op.name -> op).toMap
      val register = operations.getOrElse("register_blob", fail("missing register_blob operation"))
      val read = operations.getOrElse("read_blob", fail("missing read_blob operation"))
      val metadata = operations.getOrElse("get_blob_metadata", fail("missing get_blob_metadata operation"))

      Then("register_blob exposes its accepted user-facing input fields")
      val registerParameters = register.request.parameters.map(p => p.name -> p).toMap
      registerParameters.keySet should contain allOf (
        "sourceMode",
        "kind",
        "blobId",
        "filename",
        "contentType",
        "payload",
        "externalUrl"
      )
      registerParameters("sourceMode").multiplicity shouldBe Multiplicity.One
      registerParameters("kind").multiplicity shouldBe Multiplicity.One
      registerParameters("payload").datatype shouldBe XBlob
      registerParameters("payload").kind shouldBe org.goldenport.protocol.spec.ParameterDefinition.Kind.Argument

      Then("read_blob is declared as a Blob payload operation")
      read.response.result shouldBe List(XBlob)
      metadata.response.result.map(_.name) shouldBe List("BlobMetadata")
      register.response.result.map(_.name) shouldBe List("BlobMetadata")
    }

    "register, read, and describe a managed Blob payload" in {
      Given("a default subsystem and a managed Blob registration request")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val bytes = "managed image".getBytes(StandardCharsets.UTF_8)
      val register = _request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary(bytes))),
        properties = List(
          Property("blobId", "blob-managed-1", None),
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "photo.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      )

      When("register_blob is executed")
      val registered = _record(_success(subsystem.executeOperationResponse(register)))

      Then("metadata is returned without embedding payload bytes")
      registered.getString("blobId") shouldBe Some("blob-managed-1")
      registered.getString("sourceMode") shouldBe Some("managed")
      registered.getString("kind") shouldBe Some("image")
      registered.getString("storageRef") should not be empty
      registered.getString("displayUrl").getOrElse("") should include ("/web/blob/content/")

      When("get_blob_metadata is executed")
      val metadata = _record(_success(subsystem.executeOperationResponse(_blob_request("get_blob_metadata", "blob-managed-1"))))

      Then("the same Blob metadata is available through the metadata operation")
      metadata.getString("blobId") shouldBe Some("blob-managed-1")
      metadata.getString("filename") shouldBe Some("photo.png")
      metadata.getString("contentType") shouldBe Some(ContentType.IMAGE_PNG.header)

      When("read_blob is executed")
      val read = _success(subsystem.executeOperationResponse(_blob_request("read_blob", "blob-managed-1")))

      Then("managed Blob read returns the stored binary payload")
      read match {
        case OperationResponse.Http(response: HttpResponse.Binary) =>
          response.contentType shouldBe ContentType.IMAGE_PNG
          response.bag.openInputStream().readAllBytes().toVector shouldBe bytes.toVector
        case other =>
          fail(s"expected binary HTTP response but got $other")
      }
    }

    "register external URL Blob metadata without payload storage" in {
      Given("a default subsystem and an external URL Blob registration request")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val url = "https://example.test/manual.pdf"
      val register = _request(
        "register_blob",
        Property("blobId", "blob-external-1", None),
        Property("sourceMode", "external_url", None),
        Property("kind", "attachment", None),
        Property("filename", "manual.pdf", None),
        Property("contentType", "application/pdf", None),
        Property("externalUrl", url, None)
      )

      When("register_blob is executed")
      val registered = _record(_success(subsystem.executeOperationResponse(register)))

      Then("metadata points to the external URL and has no managed storage ref")
      registered.getString("blobId") shouldBe Some("blob-external-1")
      registered.getString("sourceMode") shouldBe Some("external_url")
      registered.getString("externalUrl") shouldBe Some(url)
      registered.getString("storageRef") shouldBe None
      registered.getString("displayUrl") shouldBe Some(url)

      When("read_blob is executed for the external URL Blob")
      val read = _success(subsystem.executeOperationResponse(_blob_request("read_blob", "blob-external-1")))

      Then("the read operation resolves external URL Blobs through an HTTP redirect")
      read match {
        case OperationResponse.Http(response: HttpResponse.Text) =>
          response.status shouldBe org.goldenport.http.HttpStatus.SeeOther
          response.headerValue("Location") shouldBe Some(url)
        case other =>
          fail(s"expected redirect HTTP response but got $other")
      }
    }

    "fail deterministically for invalid registration metadata and missing Blob ids" in {
      Given("a default subsystem")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))

      When("register_blob receives an unsupported kind")
      val invalidKind = subsystem.executeOperationResponse(_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "spreadsheet", None),
        Property("externalUrl", "https://example.test/a.xlsx", None)
      ))

      Then("the result is a deterministic failure")
      invalidKind shouldBe a[Consequence.Failure[_]]

      When("get_blob_metadata references an unknown Blob id")
      val missing = subsystem.executeOperationResponse(_blob_request("get_blob_metadata", "missing-blob"))

      Then("missing metadata is reported as failure")
      missing shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _request(operation: String, properties: Property*): Request =
    _request(operation, Nil, properties.toList)

  private def _request(
    operation: String,
    arguments: List[Argument],
    properties: List[Property]
  ): Request =
    Request.of(
      component = "blob",
      service = "blob",
      operation = operation,
      arguments = arguments,
      properties = properties.toList
    )

  private def _blob_request(operation: String, blobId: String): Request =
    _request(operation, Property("blobId", blobId, None))

  private def _record(response: OperationResponse): Record =
    response match {
      case OperationResponse.RecordResponse(record) => record
      case other => fail(s"expected record response but got $other")
    }

  private def _success[A](value: Consequence[A]): A =
    value match {
      case Consequence.Success(v) => v
      case Consequence.Failure(c) => fail(s"unexpected failure: $c")
    }
}
