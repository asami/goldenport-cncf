package org.goldenport.cncf.component.builtin.blob

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.goldenport.{Consequence, ConsequenceException}
import org.goldenport.bag.Bag
import org.goldenport.bag.BagMetadata
import org.goldenport.bag.BinaryBag
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.security.{AuthorizationResourcePolicies, AuthorizationResourcePolicy}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthorizationBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.datatype.ContentType
import org.goldenport.http.HttpResponse
import org.goldenport.observation.Descriptor
import org.goldenport.provisional.observation.Cause
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.schema.{Multiplicity, XBlob, XBoolean, XLong}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for BL-03A Blob user-facing metadata and payload operations.
 *
 * @since   Apr. 26, 2026
 *  version Apr. 28, 2026
 * @version Apr. 29, 2026
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

    "fail startup deterministically for invalid BlobStore configuration" in {
      Given("a subsystem configuration with an unknown BlobStore backend")
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.BlobStoreBackendKey -> ConfigurationValue.StringValue("missing_backend")
        )),
        ConfigurationTrace.empty
      )

      When("the default subsystem installs builtin components")
      val failure = intercept[ConsequenceException] {
        DefaultSubsystemFactory.default(Some("command"), configuration)
      }

      Then("the BlobStore configuration error is raised at component creation time")
      failure.getMessage should include ("unknown blob store backend")
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
      val resolve = operations.getOrElse("resolve_blob_url", fail("missing resolve_blob_url operation"))
      val metadata = operations.getOrElse("get_blob_metadata", fail("missing get_blob_metadata operation"))
      val attach = operations.getOrElse("attach_blob_to_entity", fail("missing attach_blob_to_entity operation"))
      val detach = operations.getOrElse("detach_blob_from_entity", fail("missing detach_blob_from_entity operation"))
      val list = operations.getOrElse("list_entity_blobs", fail("missing list_entity_blobs operation"))
      val adminList = operations.getOrElse("admin_list_blobs", fail("missing admin_list_blobs operation"))
      val adminGet = operations.getOrElse("admin_get_blob", fail("missing admin_get_blob operation"))
      val adminAssociations = operations.getOrElse("admin_list_blob_associations", fail("missing admin_list_blob_associations operation"))
      val adminStatus = operations.getOrElse("admin_blob_store_status", fail("missing admin_blob_store_status operation"))
      val adminDelete = operations.getOrElse("admin_delete_blob", fail("missing admin_delete_blob operation"))
      val adminAttach = operations.getOrElse("admin_attach_blob_to_entity", fail("missing admin_attach_blob_to_entity operation"))
      val adminDetach = operations.getOrElse("admin_detach_blob_from_entity", fail("missing admin_detach_blob_from_entity operation"))

      Then("register_blob exposes its accepted user-facing input fields")
      val registerParameters = register.request.parameters.map(p => p.name -> p).toMap
      registerParameters.keySet should contain allOf (
        "sourceMode",
        "kind",
        "filename",
        "contentType",
        "payload",
        "externalUrl",
        "expectedByteSize",
        "expectedDigest"
      )
      registerParameters("sourceMode").multiplicity shouldBe Multiplicity.One
      registerParameters("kind").multiplicity shouldBe Multiplicity.One
      registerParameters("payload").datatype shouldBe XBlob
      registerParameters("payload").kind shouldBe org.goldenport.protocol.spec.ParameterDefinition.Kind.Argument
      registerParameters("expectedByteSize").datatype shouldBe XLong

      Then("read_blob is declared as a Blob payload operation")
      read.response.result shouldBe List(XBlob)
      resolve.response.result.map(_.name) shouldBe List("BlobAccessUrl")
      metadata.response.result.map(_.name) shouldBe List("BlobMetadata")
      register.response.result.map(_.name) shouldBe List("BlobMetadata")
      attach.request.parameters.map(_.name) should contain allOf ("sourceEntityId", "id", "role", "sortOrder")
      detach.request.parameters.map(_.name) should contain allOf ("sourceEntityId", "id", "role")
      list.request.parameters.map(_.name) should contain allOf ("sourceEntityId", "role")
      adminList.request.parameters.map(_.name) should contain allOf ("offset", "limit")
      adminList.request.parameters.map(_.name) should not contain ("sourceMode")
      adminList.response.result.map(_.name) shouldBe List("Record")
      adminGet.response.result.map(_.name) shouldBe List("BlobMetadata")
      adminAssociations.request.parameters.map(_.name) should contain allOf ("sourceEntityId", "id", "role", "offset", "limit")
      adminAssociations.response.result.map(_.name) shouldBe List("Record")
      adminStatus.request.parameters shouldBe Nil
      adminStatus.response.result.map(_.name) shouldBe List("Record")
      adminDelete.request.parameters.map(_.name) should contain allOf ("id", "force")
      adminDelete.request.parameters.find(_.name == "force").map(_.datatype) shouldBe Some(XBoolean)
      adminDelete.response.result.map(_.name) shouldBe List("Record")
      adminAttach.request.parameters.map(_.name) should contain allOf ("sourceEntityId", "id", "role", "sortOrder")
      adminAttach.response.result.map(_.name) shouldBe List("Record")
      adminDetach.request.parameters.map(_.name) should contain allOf ("sourceEntityId", "id", "role")
      adminDetach.response.result.map(_.name) shouldBe List("Record")
    }

    "publish Blob as a reusable SimpleEntity admin surface" in {
      Given("the default command-mode subsystem")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val blob = subsystem.findComponent("blob").getOrElse(fail("missing Blob component"))

      When("reading the Blob component entity metadata")
      val descriptor = blob.entityRuntimeDescriptor("blob").getOrElse(fail("missing Blob entity descriptor"))

      Then("Blob metadata is inspectable through common entity metadata")
      descriptor.collectionId.name shouldBe "blob"
      descriptor.schema.flatMap(_.columns.find(_.name.value == "id")) should not be empty
      descriptor.schema.flatMap(_.columns.find(_.name.value == "storageRef")) should not be empty
    }

    "register, read, and describe a managed Blob payload" in {
      Given("a default subsystem and a managed Blob registration request")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val bytes = "managed image".getBytes(StandardCharsets.UTF_8)
      val register = _request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary(bytes))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "photo.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      )

      When("register_blob is executed")
      val registered = _record(_success(subsystem.executeOperationResponse(register)))

      Then("metadata is returned without embedding payload bytes")
      val managedId = registered.getString("id").getOrElse(fail("registered Blob id should be present"))
      registered.getString("sourceMode") shouldBe Some("managed")
      registered.getString("kind") shouldBe Some("image")
      registered.getString("storageRef") should not be empty
      registered.getString("displayPath").getOrElse("") should include ("/web/blob/content/")
      registered.getString("downloadPath").getOrElse("") should include ("download=true")
      registered.getString("displayUrl") shouldBe empty
      registered.getString("downloadUrl") shouldBe empty

      When("get_blob_metadata is executed")
      val metadata = _record(_success(subsystem.executeOperationResponse(_blob_request("get_blob_metadata", managedId))))

      Then("the same Blob metadata is available through the metadata operation")
      metadata.getString("id") shouldBe Some(managedId)
      metadata.getString("filename") shouldBe Some("photo.png")
      metadata.getString("contentType") shouldBe Some(ContentType.IMAGE_PNG.header)

      When("read_blob is executed")
      val read = _success(subsystem.executeOperationResponse(_blob_request("read_blob", managedId)))

      Then("managed Blob read returns the stored binary payload")
      read match {
        case OperationResponse.Http(response: HttpResponse.Binary) =>
          response.contentType shouldBe ContentType.IMAGE_PNG
          response.bag.openInputStream().readAllBytes().toVector shouldBe bytes.toVector
        case other =>
          fail(s"expected binary HTTP response but got $other")
      }
    }

    "preserve backend public paths on managed Blob metadata when the BlobStore provides them" in {
      Given("a subsystem whose BlobStore has a public base path")
      val subsystem = _subsystem_with_blob_store(InMemoryBlobStore(publicBasePath = Some("/assets/blob")))
      val register = _request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary(Array[Byte](1, 2, 3)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "photo.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      )

      When("register_blob is executed")
      val registered = _record(_success(subsystem.executeOperationResponse(register)))

      Then("the Blob Entity stores a relative backend public path")
      registered.getString("displayPath").getOrElse("") should startWith ("/assets/blob/")
      registered.getString("downloadPath") shouldBe registered.getString("displayPath")
      registered.getString("displayUrl") shouldBe empty
      registered.getString("downloadUrl") shouldBe empty
      registered.getString("urlSource") shouldBe Some("backend")
    }

    "attach, list, and detach Blob metadata through generic associations" in {
      Given("a default subsystem and two registered Blob payloads")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val blob1 = _record(_success(subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("image one".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "one.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      ))))
      val blob2 = _record(_success(subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("image two".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "two.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      ))))
      val blob1Id = blob1.getString("id").getOrElse(fail("first Blob id should be present"))
      val blob2Id = blob2.getString("id").getOrElse(fail("second Blob id should be present"))

      When("attaching both Blobs to an entity")
      val attach1 = _record(_success(subsystem.executeOperationResponse(_request(
        "attach_blob_to_entity",
        Property("sourceEntityId", blob1Id, None),
        Property("id", blob1Id, None),
        Property("role", "galleryImage", None),
        Property("sortOrder", "2", None)
      ))))
      val attach2 = _record(_success(subsystem.executeOperationResponse(_request(
        "attach_blob_to_entity",
        Property("sourceEntityId", blob1Id, None),
        Property("id", blob2Id, None),
        Property("role", "galleryImage", None),
        Property("sortOrder", "1", None)
      ))))
      val attach1Again = _record(_success(subsystem.executeOperationResponse(_request(
        "attach_blob_to_entity",
        Property("sourceEntityId", blob1Id, None),
        Property("id", blob1Id, None),
        Property("role", "galleryImage", None),
        Property("sortOrder", "2", None)
      ))))

      Then("the returned records are generic Blob attachment associations")
      attach1.getString("associationDomain") shouldBe Some("blob_attachment")
      attach2.getString("targetKind") shouldBe Some("blob")
      attach1Again.getString("associationId") shouldBe attach1.getString("associationId")

      When("listing associated Blobs")
      val listed = _record(_success(subsystem.executeOperationResponse(_request(
        "list_entity_blobs",
        Property("sourceEntityId", blob1Id, None),
        Property("role", "galleryImage", None)
      ))))

      Then("Blob metadata is returned in association order")
      listed.getInt("fetchedCount") shouldBe Some(2)
      val rows = listed.getVector("data").getOrElse(Vector.empty).collect { case r: Record => r }
      rows.flatMap(_.getString("id")) shouldBe Vector(blob2Id, blob1Id)

      When("detaching one Blob")
      val detached = _record(_success(subsystem.executeOperationResponse(_request(
        "detach_blob_from_entity",
        Property("sourceEntityId", blob1Id, None),
        Property("id", blob1Id, None),
        Property("role", "galleryImage", None)
      ))))

      Then("only the association is removed")
      detached.getInt("detachedCount") shouldBe Some(1)
      val remaining = _record(_success(subsystem.executeOperationResponse(_request(
        "list_entity_blobs",
        Property("sourceEntityId", blob1Id, None),
        Property("role", "galleryImage", None)
      ))))
      val remainingRows = remaining.getVector("data").getOrElse(Vector.empty).collect { case r: Record => r }
      remainingRows.flatMap(_.getString("id")) shouldBe Vector(blob2Id)
    }

    "apply resource policies to Blob user operations" in {
      Given("a subsystem with Blob collection and association resource policies")
      val subsystem = _subsystem_with_blob_authorization()
      val payload = Bag.binary("policy image".getBytes(StandardCharsets.UTF_8))

      When("register_blob is executed without the configured collection capability")
      val deniedRegister = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", payload)),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "policy.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      ))

      Then("Blob creation is denied before metadata is created")
      deniedRegister shouldBe a[Consequence.Failure[_]]

      When("register_blob is executed by a subject with the collection create capability")
      val registered = _record(_success(subsystem.executeOperationResponse(_with_privilege(
        _request(
          "register_blob",
          arguments = List(Argument("payload", Bag.binary("policy image".getBytes(StandardCharsets.UTF_8)))),
          properties = List(
            Property("sourceMode", "managed", None),
            Property("kind", "image", None),
            Property("filename", "policy.png", None),
            Property("contentType", ContentType.IMAGE_PNG.header, None)
          )
        ),
        "application_content_manager"
      ))))
      val id = registered.getString("id").getOrElse(fail("Blob id should be present"))

      Then("Blob creation succeeds")
      registered.getString("sourceMode") shouldBe Some("managed")

      When("attach_blob_to_entity is executed without the association create capability")
      val deniedAttach = subsystem.executeOperationResponse(_request(
        "attach_blob_to_entity",
        Property("sourceEntityId", id, None),
        Property("id", id, None),
        Property("role", "galleryImage", None)
      ))

      Then("the association create policy denies it")
      deniedAttach shouldBe a[Consequence.Failure[_]]

      When("attach_blob_to_entity is executed with the association create capability")
      val attached = _record(_success(subsystem.executeOperationResponse(_with_privilege(
        _request(
          "attach_blob_to_entity",
          Property("sourceEntityId", id, None),
          Property("id", id, None),
          Property("role", "galleryImage", None)
        ),
        "application_content_manager"
      ))))

      Then("the Blob attachment is created")
      attached.getString("associationDomain") shouldBe Some("blob_attachment")

      When("list_entity_blobs is executed without the association list capability")
      val deniedList = subsystem.executeOperationResponse(_request(
        "list_entity_blobs",
        Property("sourceEntityId", id, None),
        Property("role", "galleryImage", None)
      ))

      Then("association-domain search/list policy denies it")
      deniedList shouldBe a[Consequence.Failure[_]]

      When("list_entity_blobs is executed with the association list capability")
      val listed = _record(_success(subsystem.executeOperationResponse(_with_privilege(
        _request(
          "list_entity_blobs",
          Property("sourceEntityId", id, None),
          Property("role", "galleryImage", None)
        ),
        "application_content_manager"
      ))))

      Then("associated Blob metadata remains visible through target Blob read filtering")
      listed.getInt("fetchedCount") shouldBe Some(1)

      When("detach_blob_from_entity is executed without the association delete capability")
      val deniedDetach = subsystem.executeOperationResponse(_request(
        "detach_blob_from_entity",
        Property("sourceEntityId", id, None),
        Property("id", id, None),
        Property("role", "galleryImage", None)
      ))

      Then("association-domain delete policy denies it")
      deniedDetach shouldBe a[Consequence.Failure[_]]

      When("detach_blob_from_entity is executed with the association delete capability")
      val detached = _record(_success(subsystem.executeOperationResponse(_with_privilege(
        _request(
          "detach_blob_from_entity",
          Property("sourceEntityId", id, None),
          Property("id", id, None),
          Property("role", "galleryImage", None)
        ),
        "application_content_manager"
      ))))

      Then("the Blob attachment is removed")
      detached.getInt("detachedCount") shouldBe Some(1)
    }

    "reject user-facing Blob attachment when sourceEntityId is not an EntityId" in {
      Given("a default subsystem and a registered Blob")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val blob = _record(_success(subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("image".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "invalid-source.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      ))))
      val blobId = blob.getString("id").getOrElse(fail("Blob id should be present"))

      When("attach_blob_to_entity receives a non-EntityId source")
      val result = subsystem.executeOperationResponse(_request(
        "attach_blob_to_entity",
        Property("sourceEntityId", "product-42", None),
        Property("id", blobId, None),
        Property("role", "galleryImage", None)
      ))

      Then("the operation fails before creating an association")
      result shouldBe a[Consequence.Failure[_]]
    }

    "register external URL Blob metadata without payload storage" in {
      Given("a default subsystem and an external URL Blob registration request")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val url = "https://example.test/manual.pdf"
      val register = _request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "attachment", None),
        Property("filename", "manual.pdf", None),
        Property("contentType", "application/pdf", None),
        Property("externalUrl", url, None)
      )

      When("register_blob is executed")
      val registered = _record(_success(subsystem.executeOperationResponse(register)))

      Then("metadata points to the external URL and has no managed storage ref")
      val externalId = registered.getString("id").getOrElse(fail("registered external Blob id should be present"))
      registered.getString("sourceMode") shouldBe Some("external_url")
      registered.getString("externalUrl") shouldBe Some(url)
      registered.getString("storageRef") shouldBe None
      registered.getString("displayUrl") shouldBe Some(url)

      When("resolve_blob_url is executed for the external URL Blob")
      val resolved = _record(_success(subsystem.executeOperationResponse(_blob_request("resolve_blob_url", externalId))))

      Then("the URL operation returns access URL metadata")
      resolved.getString("id") shouldBe Some(externalId)
      resolved.getString("sourceMode") shouldBe Some("external_url")
      resolved.getString("displayUrl") shouldBe Some(url)
      resolved.getString("downloadUrl") shouldBe Some(url)

      When("read_blob is executed for the external URL Blob")
      val read = subsystem.executeOperationResponse(_blob_request("read_blob", externalId))

      Then("the payload operation fails because external URL Blobs have no managed payload")
      read shouldBe a[Consequence.Failure[_]]

      Given("a subsystem with zero managed Blob byte allowance")
      val zeroLimitSubsystem = DefaultSubsystemFactory.default(
        Some("command"),
        ResolvedConfiguration(
          Configuration(Map(RuntimeConfig.BlobMaxByteSizeKey -> ConfigurationValue.StringValue("0"))),
          ConfigurationTrace.empty
        )
      )

      When("an external URL Blob is registered")
      val zeroLimitExternal = zeroLimitSubsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = Nil,
        properties = List(
          Property("sourceMode", "external_url", None),
          Property("kind", "attachment", None),
          Property("filename", "external.pdf", None),
          Property("contentType", "application/pdf", None),
          Property("externalUrl", "https://example.test/external.pdf", None)
        )
      ))

      Then("the managed payload size policy does not apply")
      zeroLimitExternal shouldBe a[Consequence.Success[_]]
    }

    "validate managed Blob expected size and digest metadata" in {
      Given("a default subsystem and explicit expected metadata")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val bytes = "validated payload".getBytes(StandardCharsets.UTF_8)
      val expectedDigest = _sha256(bytes)

      When("register_blob receives matching expectedByteSize and expectedDigest")
      val registered = _record(_success(subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary(bytes))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "attachment", None),
          Property("filename", "validated.bin", None),
          Property("contentType", "application/octet-stream", None),
          Property("expectedByteSize", bytes.length.toString, None),
          Property("expectedDigest", expectedDigest.toUpperCase(java.util.Locale.ROOT), None)
        )
      ))))

      Then("the Blob is persisted with the actual measured metadata")
      registered.getString("byteSize") shouldBe Some(bytes.length.toString)
      registered.getString("digest") shouldBe Some(expectedDigest)
      registered.getString("contentType") shouldBe Some(ContentType.APPLICATION_OCTET_STREAM.header)
    }

    "default managed Blob content type when not provided" in {
      Given("a default subsystem and a managed payload without contentType")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))

      When("register_blob is executed")
      val registered = _record(_success(subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("default type".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "binary", None),
          Property("filename", "default.bin", None)
        )
      ))))

      Then("the existing octet-stream default remains")
      registered.getString("contentType") shouldBe Some(ContentType.APPLICATION_OCTET_STREAM.header)
    }

    "reject invalid Blob content type metadata" in {
      Given("a default subsystem")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val failuresBefore = RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("content_type", 0L)
      val requestValidationBefore = RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts.getOrElse("content_type", 0L)
      val validationBefore = RuntimeDashboardMetrics.validationDiagnosticCounts.getOrElse("content_type", 0L)

      When("register_blob receives a non-MIME content type")
      val result = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("invalid type".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "binary", None),
          Property("filename", "invalid.bin", None),
          Property("contentType", "not-a-mime", None)
        )
      ))

      Then("registration fails before the payload is stored")
      result shouldBe a[Consequence.Failure[_]]
      _failure_cause_kind(result) shouldBe Some(Cause.Kind.Format)
      _failure_facets(result) should contain (Descriptor.Facet.Parameter.argument("contentType"))
      RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts.getOrElse("content_type", 0L) should be > requestValidationBefore
      RuntimeDashboardMetrics.validationDiagnosticCounts.getOrElse("content_type", 0L) should be > validationBefore
      RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("content_type", 0L) should be > failuresBefore
    }

    "reject managed Blob expected metadata mismatches and compensate payloads" in {
      Given("a default subsystem with a recording BlobStore")
      val store = new RecordingBlobStore
      val subsystem = _subsystem_with_blob_store(store)

      When("expectedByteSize does not match the stored payload")
      val expectedSizeFailuresBefore = RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("expected_size", 0L)
      val sizeMismatch = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("size".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "attachment", None),
          Property("filename", "size.bin", None),
          Property("contentType", ContentType.APPLICATION_OCTET_STREAM.header, None),
          Property("expectedByteSize", "999", None)
        )
      ))

      Then("registration fails and the payload is deleted through the ActionCall path")
      sizeMismatch shouldBe a[Consequence.Failure[_]]
      store.deletedRefs shouldBe store.putRefs
      RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("expected_size", 0L) should be > expectedSizeFailuresBefore

      val putCountBeforeInvalidDigest = store.putRefs.size

      When("expectedDigest has an invalid format")
      val digestFailuresBefore = RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("digest", 0L)
      val invalidDigest = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("digest".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "attachment", None),
          Property("filename", "digest.bin", None),
          Property("contentType", ContentType.APPLICATION_OCTET_STREAM.header, None),
          Property("expectedDigest", "sha256:test", None)
        )
      ))

      Then("registration fails before a second payload is stored")
      invalidDigest shouldBe a[Consequence.Failure[_]]
      store.deletedRefs shouldBe store.putRefs
      store.putRefs.size shouldBe putCountBeforeInvalidDigest
      RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("digest", 0L) should be > digestFailuresBefore

      val expectedSizeFailuresBeforeInvalid = RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("expected_size", 0L)

      When("expectedByteSize is fractional or negative")
      val fractionalSize = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("fractional".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "attachment", None),
          Property("filename", "fractional.bin", None),
          Property("expectedByteSize", "1.9", None)
        )
      ))
      val negativeSize = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("negative".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "attachment", None),
          Property("filename", "negative.bin", None),
          Property("expectedByteSize", "-1", None)
        )
      ))

      Then("both byte-size contracts are rejected without numeric truncation")
      fractionalSize shouldBe a[Consequence.Failure[_]]
      negativeSize shouldBe a[Consequence.Failure[_]]
      RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("expected_size", 0L) should be > expectedSizeFailuresBeforeInvalid
    }

    "apply managed Blob size and MIME-kind policy" in {
      Given("a subsystem with a small managed Blob size limit")
      val preStore = new RecordingBlobStore
      val preSubsystem = _subsystem_with_blob_store(preStore, maxByteSize = 4)
      val payloadSizeFailuresBefore = RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("payload_size", 0L)
      val genericLimitFailuresBefore = RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts.getOrElse("limit", 0L)
      val validationPayloadSizeFailuresBefore = RuntimeDashboardMetrics.validationDiagnosticCounts.getOrElse("payload_size", 0L)

      When("a known oversized payload is registered")
      val knownOversize = preSubsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("oversize".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "attachment", None),
          Property("filename", "oversize.bin", None),
          Property("contentType", "application/octet-stream", None)
        )
      ))

      Then("registration fails before BlobStore put")
      knownOversize shouldBe a[Consequence.Failure[_]]
      _failure_cause_kind(knownOversize) shouldBe Some(Cause.Kind.Limit)
      _failure_facets(knownOversize) should contain (Descriptor.Facet.Policy("blob.upload.max-byte-size"))
      preStore.putRefs shouldBe Vector.empty
      RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("payload_size", 0L) should be > payloadSizeFailuresBefore
      RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts.getOrElse("limit", 0L) shouldBe genericLimitFailuresBefore
      RuntimeDashboardMetrics.validationDiagnosticCounts.getOrElse("payload_size", 0L) should be > validationPayloadSizeFailuresBefore

      Given("a store whose measured size exceeds the configured policy")
      val postStore = new RecordingBlobStore(reportedByteSize = Some(8L))
      val postSubsystem = _subsystem_with_blob_store(postStore, maxByteSize = 4)

      When("the payload size is only known after BlobStore put")
      val postOversize = postSubsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", UnknownSizeBinaryBag("tiny".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "attachment", None),
          Property("filename", "post.bin", None),
          Property("contentType", "application/octet-stream", None)
        )
      ))

      Then("registration fails and compensates the stored payload")
      postOversize shouldBe a[Consequence.Failure[_]]
      postStore.deletedRefs shouldBe postStore.putRefs

      Given("a default subsystem")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val successfulBlobOperationsBefore = RuntimeDashboardMetrics.blobOperationSnapshot.summary.cumulative.total

      When("image and video Blob registrations use compatible MIME types")
      val image = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("image".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "image.png", None),
          Property("contentType", "image/png", None)
        )
      ))
      val video = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("video".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "video", None),
          Property("filename", "video.mp4", None),
          Property("contentType", "video/mp4", None)
        )
      ))

      Then("both registrations succeed")
      image shouldBe a[Consequence.Success[_]]
      video shouldBe a[Consequence.Success[_]]
      RuntimeDashboardMetrics.blobOperationSnapshot.summary.cumulative.total should be > successfulBlobOperationsBefore

      val mimeFailuresBefore = RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("mime_kind", 0L)
      val genericMimeFailuresBefore = RuntimeDashboardMetrics.validationDiagnosticCounts.getOrElse("mime_kind", 0L)
      When("image and video Blob registrations use incompatible or default MIME types")
      val badImage = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("bad-image".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "bad-image.bin", None),
          Property("contentType", "application/octet-stream", None)
        )
      ))
      val defaultVideo = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("default-video".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "video", None),
          Property("filename", "default-video.bin", None)
        )
      ))

      Then("both registrations fail deterministically")
      badImage shouldBe a[Consequence.Failure[_]]
      defaultVideo shouldBe a[Consequence.Failure[_]]
      _failure_cause_kind(badImage) shouldBe Some(Cause.Kind.Policy)
      _failure_facets(badImage) should contain (Descriptor.Facet.Policy("mime-kind"))
      RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("mime_kind", 0L) should be > mimeFailuresBefore
      RuntimeDashboardMetrics.validationDiagnosticCounts.getOrElse("mime_kind", 0L) should be > genericMimeFailuresBefore
    }

    "record BlobStore failures as Blob operational diagnostics" in {
      Given("a subsystem with a failing BlobStore")
      val store = new RecordingBlobStore(failPut = true)
      val subsystem = _subsystem_with_blob_store(store)
      val blobErrorsBefore = RuntimeDashboardMetrics.blobOperationSnapshot.summary.cumulative.errors

      When("managed Blob registration reaches BlobStore put")
      val result = subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("store failure".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "attachment", None),
          Property("filename", "store-failure.bin", None),
          Property("contentType", "application/octet-stream", None)
        )
      ))

      Then("the operation fails and Blob diagnostics record the error")
      result shouldBe a[Consequence.Failure[_]]
      RuntimeDashboardMetrics.blobOperationSnapshot.summary.cumulative.errors should be > blobErrorsBefore
    }

    "reject unsafe external URL Blob registrations" in {
      Given("a default subsystem")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val externalUrlFailuresBefore = RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("external_url", 0L)
      val unsafeUrls = Vector(
        "javascript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "file:///tmp/payload.png",
        "/relative/path.png",
        "//example.test/path.png",
        "https://user:pass@example.test/path.png",
        "http://localhost/path.png",
        "http://127.0.0.1/path.png",
        "http://[::1]/path.png",
        "https://example.test/has space.png"
      )

      When("register_blob receives unsafe external URLs")
      val results = unsafeUrls.map { url =>
        url -> subsystem.executeOperationResponse(_request(
          "register_blob",
          Property("sourceMode", "external_url", None),
          Property("kind", "attachment", None),
          Property("filename", "unsafe.pdf", None),
          Property("contentType", "application/pdf", None),
          Property("externalUrl", url, None)
        ))
      }

      Then("each registration fails deterministically before metadata is persisted")
      results.foreach {
        case (_, result) =>
          result shouldBe a[Consequence.Failure[_]]
      }
      RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("external_url", 0L) should be > externalUrlFailuresBefore
    }

    "reject external URL Blob payload validation fields" in {
      Given("a default subsystem and an external URL Blob request with payload validation fields")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))

      When("register_blob receives expectedByteSize or expectedDigest for external_url")
      val withSize = subsystem.executeOperationResponse(_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "attachment", None),
        Property("filename", "external.pdf", None),
        Property("contentType", "application/pdf", None),
        Property("externalUrl", "https://example.test/external.pdf", None),
        Property("expectedByteSize", "10", None)
      ))
      val withDigest = subsystem.executeOperationResponse(_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "attachment", None),
        Property("filename", "external.pdf", None),
        Property("contentType", "application/pdf", None),
        Property("externalUrl", "https://example.test/external.pdf", None),
        Property("expectedDigest", _sha256("external".getBytes(StandardCharsets.UTF_8)), None)
      ))

      Then("both requests fail because CNCF does not own an external payload")
      withSize shouldBe a[Consequence.Failure[_]]
      withDigest shouldBe a[Consequence.Failure[_]]
    }

    "expose read-only admin Blob diagnostics" in {
      Given("a default subsystem with Blob metadata and associations")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val blob1 = _record(_success(subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("admin one".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "admin-one.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      ))))
      val blob2 = _record(_success(subsystem.executeOperationResponse(_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "attachment", None),
        Property("filename", "admin-two.pdf", None),
        Property("contentType", "application/pdf", None),
        Property("externalUrl", "https://example.test/admin-two.pdf", None)
      ))))
      val blob1Id = blob1.getString("id").getOrElse(fail("first Blob id should be present"))
      val blob2Id = blob2.getString("id").getOrElse(fail("second Blob id should be present"))
      _success(subsystem.executeOperationResponse(_request(
        "attach_blob_to_entity",
        Property("sourceEntityId", blob1Id, None),
        Property("id", blob1Id, None),
        Property("role", "galleryImage", None)
      )))
      _success(subsystem.executeOperationResponse(_request(
        "attach_blob_to_entity",
        Property("sourceEntityId", blob1Id, None),
        Property("id", blob2Id, None),
        Property("role", "attachment", None)
      )))

      When("read-only admin Blob operations are executed")
      val listed = _record(_success(subsystem.executeOperationResponse(_request(
        "admin_list_blobs",
        Property("limit", "1", None)
      ))))
      val loaded = _record(_success(subsystem.executeOperationResponse(_blob_request("admin_get_blob", blob1Id))))
      val associations = _record(_success(subsystem.executeOperationResponse(_request(
        "admin_list_blob_associations",
        Property("sourceEntityId", blob1Id, None),
        Property("limit", "1", None)
      ))))
      val status = _record(_success(subsystem.executeOperationResponse(_request("admin_blob_store_status"))))

      Then("Blob metadata, associations, and store status are returned without mutation")
      listed.getInt("offset") shouldBe Some(0)
      listed.getInt("limit") shouldBe Some(1)
      listed.getInt("fetchedCount") shouldBe Some(1)
      listed.getString("hasMore") shouldBe Some("true")
      val rows = listed.getVector("data").getOrElse(Vector.empty).collect { case r: Record => r }
      rows.flatMap(_.getString("id")) should have size 1
      loaded.getString("id") shouldBe Some(blob1Id)
      associations.getInt("offset") shouldBe Some(0)
      associations.getInt("limit") shouldBe Some(1)
      associations.getInt("fetchedCount") shouldBe Some(1)
      associations.getString("hasMore") shouldBe Some("true")
      val associationRows = associations.getVector("data").getOrElse(Vector.empty).collect { case r: Record => r }
      associationRows.flatMap(_.getString("targetEntityId")) should have size 1
      status.getString("backend") shouldBe Some("in_memory")
      status.getString("available") shouldBe Some("true")
      status.getString("maxByteSize") shouldBe Some(BlobStoreConfig.DefaultMaxByteSize.toString)
      status.getString("contentRouteCachePolicy") shouldBe Some("private, max-age=60")
      status.getRecord("mimeKindPolicy").flatMap(_.getString("image")) shouldBe Some("image/*")
      status.getRecord("mimeKindPolicy").flatMap(_.getString("video")) shouldBe Some("video/*")
      status.getRecord("blobMetrics").flatMap(_.getRecord("summary")).flatMap(_.getRecord("cumulative")).flatMap(_.getString("count")) should not be empty
      status.getRecord("blobDiagnostics") should not be empty
    }

    "apply resource policies to Blob admin operations after the admin gate" in {
      Given("a subsystem with Blob admin resource policies")
      val subsystem = _subsystem_with_blob_authorization()
      val registered = _record(_success(subsystem.executeOperationResponse(_with_privilege(
        _request(
          "register_blob",
          arguments = List(Argument("payload", Bag.binary("admin policy".getBytes(StandardCharsets.UTF_8)))),
          properties = List(
            Property("sourceMode", "managed", None),
            Property("kind", "image", None),
            Property("filename", "admin-policy.png", None),
            Property("contentType", ContentType.IMAGE_PNG.header, None)
          )
        ),
        "application_content_manager"
      ))))
      val id = registered.getString("id").getOrElse(fail("Blob id should be present"))

      When("admin_blob_store_status is executed without the store status capability")
      val deniedStatus = subsystem.executeOperationResponse(_request("admin_blob_store_status"))

      Then("the store resource policy denies it")
      deniedStatus shouldBe a[Consequence.Failure[_]]

      When("admin_blob_store_status is executed with the store status capability")
      val status = _record(_success(subsystem.executeOperationResponse(_with_privilege(
        _request("admin_blob_store_status"),
        "application_content_manager"
      ))))

      Then("BlobStore diagnostics are returned")
      status.getString("backend") shouldBe Some("in_memory")

      When("admin_delete_blob is executed without the collection delete capability")
      val deniedDelete = subsystem.executeOperationResponse(_blob_request("admin_delete_blob", id))

      Then("the Blob collection delete policy denies it")
      deniedDelete shouldBe a[Consequence.Failure[_]]

      When("admin_delete_blob is executed with the collection delete capability")
      val deleted = _record(_success(subsystem.executeOperationResponse(_with_privilege(
        _request(
          "admin_delete_blob",
          Property("id", id, None),
          Property("force", "true", None)
        ),
        "application_content_manager"
      ))))

      Then("the admin delete proceeds through the existing system mutation path")
      deleted.getString("deletedBlobId") shouldBe Some(id)
      subsystem.executeOperationResponse(_blob_request("admin_get_blob", id)) shouldBe a[Consequence.Failure[_]]
    }

    "reject admin Blob delete while attached unless forced" in {
      Given("a default subsystem with an attached managed Blob")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val registered = _record(_success(subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("delete attached".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "delete-attached.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      ))))
      val id = registered.getString("id").getOrElse(fail("Blob id should be present"))
      _success(subsystem.executeOperationResponse(_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", "admin-delete-product", None),
        Property("id", id, None),
        Property("role", "galleryImage", None)
      )))

      When("admin_delete_blob is executed without force")
      val rejected = subsystem.executeOperationResponse(_blob_request("admin_delete_blob", id))

      Then("the delete fails and the metadata and association remain")
      rejected shouldBe a[Consequence.Failure[_]]
      _record(_success(subsystem.executeOperationResponse(_blob_request("admin_get_blob", id)))).getString("id") shouldBe Some(id)
      val associations = _record(_success(subsystem.executeOperationResponse(_request(
        "admin_list_blob_associations",
        Property("id", id, None)
      ))))
      associations.getInt("fetchedCount") shouldBe Some(1)
    }

    "force admin Blob delete cascades Blob associations and removes metadata" in {
      Given("a default subsystem with an attached external URL Blob")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val registered = _record(_success(subsystem.executeOperationResponse(_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "attachment", None),
        Property("filename", "forced-delete.pdf", None),
        Property("contentType", "application/pdf", None),
        Property("externalUrl", "https://example.test/forced-delete.pdf", None)
      ))))
      val id = registered.getString("id").getOrElse(fail("Blob id should be present"))
      _success(subsystem.executeOperationResponse(_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", "admin-force-product", None),
        Property("id", id, None),
        Property("role", "attachment", None)
      )))

      When("admin_delete_blob is executed with force")
      val deleted = _record(_success(subsystem.executeOperationResponse(_request(
        "admin_delete_blob",
        Property("id", id, None),
        Property("force", "true", None)
      ))))

      Then("the Blob metadata and referencing associations are removed")
      deleted.getString("deletedBlobId") shouldBe Some(id)
      deleted.getInt("deletedAssociationCount") shouldBe Some(1)
      deleted.getString("payloadDeleted") shouldBe Some("false")
      deleted.getString("sourceMode") shouldBe Some("external_url")
      subsystem.executeOperationResponse(_blob_request("admin_get_blob", id)) shouldBe a[Consequence.Failure[_]]
      val associations = _record(_success(subsystem.executeOperationResponse(_request(
        "admin_list_blob_associations",
        Property("id", id, None)
      ))))
      associations.getInt("fetchedCount") shouldBe Some(0)
    }

    "delete Blob metadata before managed payload cleanup" in {
      Given("a default subsystem with a BlobStore that fails delete")
      val store = new RecordingBlobStore(failDelete = true)
      val subsystem = _subsystem_with_blob_store(store)
      val registered = _record(_success(subsystem.executeOperationResponse(_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("delete payload failure".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "delete-failure.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      ))))
      val id = registered.getString("id").getOrElse(fail("Blob id should be present"))

      When("admin_delete_blob cannot delete the managed payload")
      val result = subsystem.executeOperationResponse(_request(
        "admin_delete_blob",
        Property("id", id, None),
        Property("force", "true", None)
      ))

      Then("the delete reports failure without leaving visible Blob metadata")
      result shouldBe a[Consequence.Failure[_]]
      store.deletedRefs shouldBe store.putRefs
      subsystem.executeOperationResponse(_blob_request("admin_get_blob", id)) shouldBe a[Consequence.Failure[_]]
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
      val missing = subsystem.executeOperationResponse(_blob_request("get_blob_metadata", _blob_entity_id("missing_blob").value))

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

  private def _blob_request(operation: String, id: String): Request =
    _request(operation, Property("id", id, None))

  private def _with_privilege(request: Request, privilege: String): Request =
    request.copy(properties = Property("cncf.security.privilege", privilege, None) :: request.properties)

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

  private def _failure_facets[A](value: Consequence[A]): Vector[Descriptor.Facet] =
    value match {
      case Consequence.Failure(c) => c.observation.cause.descriptor.facets
      case Consequence.Success(_) => fail("expected failure")
    }

  private def _failure_cause_kind[A](value: Consequence[A]): Option[Cause.Kind] =
    value match {
      case Consequence.Failure(c) => c.observation.cause.kind
      case Consequence.Success(_) => fail("expected failure")
    }

  private def _blob_entity_id(minor: String): EntityId =
    EntityId("cncf", minor, EntityCollectionId("cncf", "builtin", "blob"))

  private def _subsystem_with_blob_store(
    store: BlobStore,
    maxByteSize: Long = BlobStoreConfig.DefaultMaxByteSize
  ) = {
    val subsystem = DefaultSubsystemFactory.default(Some("command"))
    val component = subsystem.findComponent("blob").getOrElse(fail("missing Blob component"))
    component.withPort(org.goldenport.cncf.component.Component.Port.of(new BlobComponent.DefaultBlobService(store, maxByteSize)))
    subsystem
  }

  private def _subsystem_with_blob_authorization() = {
    val subsystem = DefaultSubsystemFactory.default(Some("command"))
    subsystem.withDescriptor(GenericSubsystemDescriptor(
      path = java.nio.file.Path.of("<blob-authz>"),
      subsystemName = "blob-authz",
      componentBindings = Vector(GenericSubsystemComponentBinding("blob")),
      security = Some(GenericSubsystemSecurityBinding(
        authorization = Some(GenericSubsystemAuthorizationBinding(
          resources = AuthorizationResourcePolicies(
            collections = Map(
              "blob" -> Map(
                "create" -> AuthorizationResourcePolicy(capabilities = Vector("content_manager")),
                "delete" -> AuthorizationResourcePolicy(capabilities = Vector("content_manager"))
              )
            ),
            associations = Map(
              "blobattachment" -> Map(
                "create" -> AuthorizationResourcePolicy(capabilities = Vector("content_manager")),
                "delete" -> AuthorizationResourcePolicy(capabilities = Vector("content_manager")),
                "search/list" -> AuthorizationResourcePolicy(capabilities = Vector("content_manager"))
              )
            ),
            stores = Map(
              "blobstore" -> Map(
                "status" -> AuthorizationResourcePolicy(capabilities = Vector("content_manager"))
              )
            )
          )
        ))
      ))
    ))
    subsystem
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString

  private final class RecordingBlobStore(
    failPut: Boolean = false,
    failDelete: Boolean = false,
    reportedByteSize: Option[Long] = None
  ) extends BlobStore {
    var putRefs: Vector[BlobStorageRef] = Vector.empty
    var deletedRefs: Vector[BlobStorageRef] = Vector.empty

    def name: String = "recording"

    def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = {
      val ref = BlobStorageRef(name, BlobStorageRef.DefaultContainer, request.id.value)
      val bytes = payload.openInputStream().readAllBytes()
      putRefs = putRefs :+ ref
      if (failPut)
        Consequence.stateConflict(s"blob payload put failed: ${ref.print}")
      else
        Consequence.success(BlobPutResult(
          id = request.id,
          storageRef = ref,
          contentType = request.contentType,
          byteSize = reportedByteSize.getOrElse(bytes.length.toLong),
          digest = "sha256:test",
          accessUrl = BlobAccessUrl.unresolved,
          storedAt = java.time.Instant.parse("2026-04-27T00:00:00Z")
        ))
    }

    def get(ref: BlobStorageRef): Consequence[BlobReadResult] =
      Consequence.operationNotFound(s"blob payload:${ref.print}")

    def delete(ref: BlobStorageRef): Consequence[Unit] = {
      deletedRefs = deletedRefs :+ ref
      if (failDelete)
        Consequence.stateConflict(s"blob payload delete failed: ${ref.print}")
      else
        Consequence.unit
    }

    def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] =
      Consequence.success(BlobAccessUrl.unresolved)

    def status(): Consequence[BlobStoreStatus] =
      Consequence.success(BlobStoreStatus("recording", available = true))
  }

  private final case class UnknownSizeBinaryBag(bytes: Array[Byte]) extends BinaryBag {
    val bag: Bag = Bag.binary(bytes).bag

    override def metadata: BagMetadata =
      BagMetadata()

    override def openInputStream(): java.io.InputStream =
      new ByteArrayInputStream(bytes)
  }


}
