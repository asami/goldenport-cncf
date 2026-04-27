package org.goldenport.cncf.component.builtin.blob

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.bag.BinaryBag
import org.goldenport.cncf.association.{AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.datatype.ContentType
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.schema.{Multiplicity, XBlob, XBoolean}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for BL-03A Blob user-facing metadata and payload operations.
 *
 * @since   Apr. 26, 2026
 * @version Apr. 27, 2026
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
        "externalUrl"
      )
      registerParameters("sourceMode").multiplicity shouldBe Multiplicity.One
      registerParameters("kind").multiplicity shouldBe Multiplicity.One
      registerParameters("payload").datatype shouldBe XBlob
      registerParameters("payload").kind shouldBe org.goldenport.protocol.spec.ParameterDefinition.Kind.Argument

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
      registered.getString("displayUrl").getOrElse("") should include ("/web/blob/content/")

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
        Property("sourceEntityId", "product-42", None),
        Property("id", blob1Id, None),
        Property("role", "galleryImage", None),
        Property("sortOrder", "2", None)
      ))))
      val attach2 = _record(_success(subsystem.executeOperationResponse(_request(
        "attach_blob_to_entity",
        Property("sourceEntityId", "product-42", None),
        Property("id", blob2Id, None),
        Property("role", "galleryImage", None),
        Property("sortOrder", "1", None)
      ))))
      val attach1Again = _record(_success(subsystem.executeOperationResponse(_request(
        "attach_blob_to_entity",
        Property("sourceEntityId", "product-42", None),
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
        Property("sourceEntityId", "product-42", None),
        Property("role", "galleryImage", None)
      ))))

      Then("Blob metadata is returned in association order")
      listed.getInt("fetchedCount") shouldBe Some(2)
      val rows = listed.getVector("data").getOrElse(Vector.empty).collect { case r: Record => r }
      rows.flatMap(_.getString("id")) shouldBe Vector(blob2Id, blob1Id)

      When("detaching one Blob")
      val detached = _record(_success(subsystem.executeOperationResponse(_request(
        "detach_blob_from_entity",
        Property("sourceEntityId", "product-42", None),
        Property("id", blob1Id, None),
        Property("role", "galleryImage", None)
      ))))

      Then("only the association is removed")
      detached.getInt("detachedCount") shouldBe Some(1)
      val remaining = _record(_success(subsystem.executeOperationResponse(_request(
        "list_entity_blobs",
        Property("sourceEntityId", "product-42", None),
        Property("role", "galleryImage", None)
      ))))
      val remainingRows = remaining.getVector("data").getOrElse(Vector.empty).collect { case r: Record => r }
      remainingRows.flatMap(_.getString("id")) shouldBe Vector(blob2Id)
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
    }

    "reject unsafe external URL Blob registrations" in {
      Given("a default subsystem")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
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
        Property("sourceEntityId", "admin-product-42", None),
        Property("id", blob1Id, None),
        Property("role", "galleryImage", None)
      )))
      _success(subsystem.executeOperationResponse(_request(
        "attach_blob_to_entity",
        Property("sourceEntityId", "admin-product-42", None),
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
        Property("sourceEntityId", "admin-product-42", None),
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
    }

    "delete a managed payload when metadata persistence fails" in {
      Given("a Blob service with a working BlobStore and failing metadata repository")
      given ExecutionContext = ExecutionContext.test()
      val store = new RecordingBlobStore
      val service = new BlobComponent.DefaultBlobService(
        store,
        new FailingBlobRepository,
        AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      )
      val request = BlobComponent.RegisterBlobRequest(
        id = _blob_entity_id("orphan_check"),
        kind = BlobKind.Image,
        sourceMode = BlobSourceMode.Managed,
        filename = Some("orphan.png"),
        contentType = Some(ContentType.IMAGE_PNG),
        payload = Some(Bag.binary("orphan".getBytes(StandardCharsets.UTF_8))),
        externalUrl = None
      )

      When("managed registration stores payload but cannot persist metadata")
      val result = service.registerBlob(request)

      Then("the registration fails and the stored payload is compensated")
      result shouldBe a[Consequence.Failure[_]]
      store.putRefs should have size 1
      store.deletedRefs shouldBe store.putRefs
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
      Given("a Blob service with a BlobStore that fails delete")
      given ExecutionContext = ExecutionContext.test()
      val id = _blob_entity_id("delete_payload_failure")
      val ref = BlobStorageRef("recording", BlobStorageRef.DefaultContainer, id.value)
      val blob = Blob(
        id = id,
        kind = BlobKind.Image,
        sourceMode = BlobSourceMode.Managed,
        filename = Some("delete-failure.png"),
        contentType = Some(ContentType.IMAGE_PNG),
        byteSize = Some(14),
        digest = Some("sha256:test"),
        storageRef = Some(ref),
        externalUrl = None,
        accessUrl = BlobUrl.cncfRoute(ref),
        createdAt = java.time.Instant.parse("2026-04-27T00:00:00Z"),
        updatedAt = java.time.Instant.parse("2026-04-27T00:00:00Z")
      )
      val store = new RecordingBlobStore(failDelete = true)
      val repository = new RecordingBlobRepository(blob)
      val service = new BlobComponent.DefaultBlobService(
        store,
        repository,
        AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      )

      When("admin_delete_blob cannot delete the managed payload")
      val result = service.adminDeleteBlob(BlobComponent.AdminDeleteBlobRequest(id, force = false))

      Then("the delete reports failure without leaving visible Blob metadata")
      result shouldBe a[Consequence.Failure[_]]
      repository.deletedIds shouldBe Vector(id)
      service.getBlobMetadata(id) shouldBe a[Consequence.Failure[_]]
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

  private def _blob_entity_id(minor: String): EntityId =
    EntityId("cncf", minor, EntityCollectionId("cncf", "builtin", "blob"))

  private final class RecordingBlobStore(failDelete: Boolean = false) extends BlobStore {
    var putRefs: Vector[BlobStorageRef] = Vector.empty
    var deletedRefs: Vector[BlobStorageRef] = Vector.empty

    def name: String = "recording"

    def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = {
      val ref = BlobStorageRef(name, BlobStorageRef.DefaultContainer, request.id.value)
      val bytes = payload.openInputStream().readAllBytes()
      putRefs = putRefs :+ ref
      Consequence.success(BlobPutResult(
        id = request.id,
        storageRef = ref,
        contentType = request.contentType,
        byteSize = bytes.length.toLong,
        digest = "sha256:test",
        accessUrl = BlobUrl.cncfRoute(ref),
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
      Consequence.success(BlobUrl.cncfRoute(ref))

    def status(): Consequence[BlobStoreStatus] =
      Consequence.success(BlobStoreStatus("recording", available = true))
  }

  private final class FailingBlobRepository extends BlobRepository {
    def create(blob: BlobCreate)(using ExecutionContext): Consequence[Blob] =
      Consequence.stateConflict(s"blob metadata create failed: ${blob.id.value}")

    def get(id: EntityId)(using ExecutionContext): Consequence[Blob] =
      Consequence.operationNotFound(s"blob metadata:${id.value}")

    def list(offset: Int = 0, limit: Option[Int] = None)(using ExecutionContext): Consequence[Vector[Blob]] =
      Consequence.success(Vector.empty)

    def delete(id: EntityId)(using ExecutionContext): Consequence[Unit] =
      Consequence.stateConflict(s"blob metadata delete failed: ${id.value}")
  }

  private final class RecordingBlobRepository(initial: Blob) extends BlobRepository {
    private var values: Map[String, Blob] = Map(initial.id.value -> initial)
    var deletedIds: Vector[EntityId] = Vector.empty

    def create(blob: BlobCreate)(using ExecutionContext): Consequence[Blob] =
      Consequence.stateConflict(s"blob metadata create not supported: ${blob.id.value}")

    def get(id: EntityId)(using ExecutionContext): Consequence[Blob] =
      values.get(id.value).map(Consequence.success).getOrElse(Consequence.operationNotFound(s"blob metadata:${id.value}"))

    def list(offset: Int = 0, limit: Option[Int] = None)(using ExecutionContext): Consequence[Vector[Blob]] =
      Consequence.success(values.values.toVector.drop(offset).take(limit.getOrElse(Int.MaxValue)))

    def delete(id: EntityId)(using ExecutionContext): Consequence[Unit] = {
      deletedIds = deletedIds :+ id
      values = values - id.value
      Consequence.unit
    }
  }
}
