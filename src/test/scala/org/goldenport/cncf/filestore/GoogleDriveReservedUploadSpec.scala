package org.goldenport.cncf.filestore

import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter}
import org.goldenport.datatype.MimeType
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 25, 2026
 * @version Nov.  6, 2026
 */
final class GoogleDriveReservedUploadSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoogleDriveFixture.*

  private val _root = FileStoreRef(parent.store, "shared_drive_01")
  private val _generate_url = s"$driveOrigin/drive/v3/files/generateIds?count=1&space=drive&type=files"

  "Optional Google Drive reserved upload" should {
    "preparation admission and session issue" which {
      "reserve through parent GET, generated ID GET and session POST with one authorization" in {
        Given("a Shared Drive collection and fixed or refreshed synthetic managed credentials")
        val credentials = Vector(fixedCredentials(), refreshCredentials())
        When("each preparation runs through the admitted native interpreter")
        val outcomes = credentials.map { credential =>
          val fixture = new Harness(request => if (request.path == oauthUrl) jsonResponse(tokenJson("synthetic-access-marker"))
            else _prepare_answer(request))
          (fixture.evaluate(store(credential).prepareReservedDirectUpload(parent, "label", MimeType("application/octet-stream"), 3)).TAKE, fixture)
        }
        Then("only the ordered metadata sequence runs and its server ID and observed volume are retained")
        outcomes.zipWithIndex.foreach { case ((reservation, fixture), index) =>
          reservation shouldBe _reservation()
          reservation.toString shouldBe "DirectFileUploadReservation(<redacted>)"
          reservation.upload.toString shouldBe "DirectFileUpload(<redacted>)"
          val requests = fixture.requests.filterNot(_.path == oauthUrl)
          requests.map(_.method) shouldBe Vector("GET", "GET", "POST")
          requests.head.path shouldBe _stat_url(parent)
          requests(1).path shouldBe _generate_url
          requests.last.path shouldBe s"$driveOrigin/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true&fields=$fields"
          requests.foreach { request =>
            request.headers("Authorization") shouldBe "Bearer synthetic-access-marker"
            request.properties should contain (Property("http.max-response-bytes", "65536", None))
            request.bag shouldBe None
          }
          requests.last.headers("X-Upload-Content-Type") shouldBe "application/octet-stream"
          requests.last.headers("X-Upload-Content-Length") shouldBe "3"
          parser.parse(requests.last.text.get).toOption.get shouldBe Json.obj("id" -> Json.fromString(reference.key),
            "name" -> Json.fromString("label"), "mimeType" -> Json.fromString("application/octet-stream"),
            "parents" -> Json.arr(Json.fromString(parent.key)))
          fixture.requests.count(_.path == oauthUrl) shouldBe index
        }
      }

      "reject invalid preparation declarations before credential exchange or driver acquisition" in {
        Given("foreign or unsafe parents, names, MIME and negative declared lengths")
        val declarations = Vector((FileStoreRef("foreign", parent.key), "label", MimeType("application/octet-stream"), 3L),
          (FileStoreRef(parent.store, "../bad"), "label", MimeType("application/octet-stream"), 3L),
          (parent, "", MimeType("application/octet-stream"), 3L), (parent, "label", MimeType(folderMime), 3L),
          (parent, "label", MimeType("invalid"), 3L), (parent, "label", MimeType("application/octet-stream"), -1L))
        When("each declaration is prepared with refresh credentials")
        val outcomes = declarations.map { case (ref, label, mime, length) =>
          val fixture = new Harness(_ => throw new AssertionError("Invalid input must be pure"))
          (fixture.evaluate(store(refreshCredentials()).prepareReservedDirectUpload(ref, label, mime, length)), fixture)
        }
        Then("all declarations fail without credentials or HTTP")
        outcomes.foreach { case (outcome, fixture) =>
          outcome.isSuccess shouldBe false
          fixture.requests shouldBe empty
          fixture.acquisitions shouldBe 0
        }
      }

      "stop before ID generation or session initiation when the parent is not the exact collection" in {
        Given("a file, foreign parent identity, trashed collection and inaccessible parent responses")
        val responses = Vector(jsonResponse(metadata(id = parent.key)),
          jsonResponse(metadata(id = "foreign", mime = folderMime, size = None)),
          jsonResponse(_parent().deepMerge(Json.obj("trashed" -> Json.True))), textResponse("provider-body-marker", 404))
        When("preparation reads each supposed parent")
        val outcomes = responses.map { response =>
          val fixture = new Harness(_ => response)
          (fixture.evaluate(store().prepareReservedDirectUpload(parent, "label", MimeType("application/octet-stream"), 3)), fixture)
        }
        Then("none issues a generated ID request or upload URL")
        outcomes.foreach { case (outcome, fixture) =>
          outcome.isSuccess shouldBe false
          fixture.requests.map(_.path) shouldBe Vector(_stat_url(parent))
        }
      }

      "reject malformed or nonnew generated IDs without issuing a session" in {
        Given("missing, null, coerced, unsafe, duplicate, extra and existing IDs or wrong response discriminators")
        val documents = Vector(Json.Null, Json.arr(), Json.obj(), Json.obj("ids" -> Json.Null),
          Json.obj("ids" -> Json.arr()), Json.obj("ids" -> Json.arr(Json.fromInt(1))),
          _generated(""), _generated("../unsafe"), _generated(parent.key), _generated(_root.key),
          Json.obj("ids" -> Json.arr(Json.fromString(reference.key), Json.fromString(reference.key))),
          Json.obj("ids" -> Json.arr(Json.fromString(reference.key), Json.fromString("extra"))),
          _generated(reference.key).deepMerge(Json.obj("space" -> Json.Null)),
          _generated(reference.key).deepMerge(Json.obj("space" -> Json.fromString("appDataFolder"))),
          _generated(reference.key).deepMerge(Json.obj("kind" -> Json.fromInt(1))),
          _generated(reference.key).deepMerge(Json.obj("kind" -> Json.fromString("foreign"))))
        When("the native ID GET returns each invalid JSON value")
        val outcomes = documents.map { document =>
          val fixture = new Harness(request => if (request.path == _generate_url) jsonResponse(document) else jsonResponse(_parent()))
          (fixture.evaluate(store().prepareReservedDirectUpload(parent, "label", MimeType("application/octet-stream"), 3)), fixture)
        }
        Then("all fail with the fixed metadata diagnostic and only two GETs")
        outcomes.foreach { case (outcome, fixture) =>
          failure(outcome).cause.getMessage shouldBe Some("Google Drive file metadata is invalid")
          disclosures(failure(outcome)).foreach(output => output should not include "../unsafe")
          fixture.requests.map(_.method) shouldBe Vector("GET", "GET")
        }
      }

      "retain generated-ID provider failures and strict session Location admission" in {
        Given("failed, malformed and wrong-content-type ID responses and hostile session Locations")
        val responses = Vector(textResponse("provider-body-marker", 403), textResponse("provider-body-marker", 503),
          textResponse("broken-json"), textResponse(_generated(reference.key).noSpaces, mime = "text/plain"))
        When("each ID response or session Location is interpreted")
        val outcomes = responses.map { response =>
          val fixture = new Harness(request => if (request.path == _generate_url) response else jsonResponse(_parent()))
          (fixture.evaluate(store().prepareReservedDirectUpload(parent, "label", MimeType("application/octet-stream"), 3)), fixture)
        }
        val hostile = new Harness(request => if (request.method == "POST") initiation("https://attacker.invalid/session") else _prepare_answer(request))
        val rejected = hostile.evaluate(store().prepareReservedDirectUpload(parent, "label", MimeType("application/octet-stream"), 3))
        Then("no failed ID response creates a session and no hostile URL becomes a retained binding")
        outcomes.foreach { case (outcome, fixture) =>
          outcome.isSuccess shouldBe false
          fixture.requests.map(_.method) shouldBe Vector("GET", "GET")
          disclosures(failure(outcome)).foreach(output => output should not include "provider-body-marker")
        }
        failure(outcomes.head._1).status shouldBe failure(Consequence.securityPermissionDenied("denied")).status
        failure(outcomes(1)._1).status shouldBe failure(Consequence.serviceUnavailable("unavailable")).status
        rejected.isSuccess shouldBe false
        hostile.requests.map(_.method) shouldBe Vector("GET", "GET", "POST")
      }
    }

    "retained binding and completion observation" which {
      "keep pending offsets confined to one empty existing-session PUT" in {
        Given("provider-confirmed empty, partial and full prefixes for the retained reservation")
        val ranges = Vector(None -> 0L, Some("bytes=0-0") -> 1L, Some("bytes=0-2") -> 3L)
        When("the status operation observes each 308")
        val outcomes = ranges.map { case (range, offset) =>
          val fixture = new Harness(_ => textResponse("", 308, headers = range.fold(Record.empty)(value => Record.data("Range" -> value))))
          (fixture.evaluate(store().inspectReservedDirectUpload(_reservation())).TAKE, fixture, offset)
        }
        Then("only provider offsets are returned, without metadata GET, original bytes or another POST")
        outcomes.foreach { case (outcome, fixture, offset) =>
          outcome shouldBe DirectFileUploadStatus.Pending(offset)
          fixture.requests.map(_.path) shouldBe Vector(sessionUrl)
          fixture.requests.head.method shouldBe "PUT"
          fixture.requests.head.headers("Content-Range") shouldBe "bytes */3"
          bytes(fixture.requests.head.bag.get) shouldBe empty
        }
      }

      "complete only with independent bound metadata and preserve absent or typed provider SHA" in {
        Given("same-size session metadata and independent file metadata with absent or distinct checksums")
        val digests = Vector(None, Some("B" * 64))
        When("inspection completes and then independently reads the reserved file")
        val outcomes = digests.map { digest =>
          val observed = digest.fold(_rooted(metadata(version = Some("9"))))(value =>
            _rooted(metadata(version = Some("9"))).deepMerge(Json.obj("sha256Checksum" -> Json.fromString(value))))
          val fixture = new Harness(request => if (request.method == "PUT")
            jsonResponse(metadata().deepMerge(Json.obj("sha256Checksum" -> Json.fromString("a" * 64)))) else jsonResponse(observed))
          (fixture.evaluate(store().inspectReservedDirectUpload(_reservation())).TAKE, fixture, digest)
        }
        Then("server stat wins over session observations and missing SHA stays unknown rather than verified")
        outcomes.foreach { case (outcome, fixture, digest) =>
          outcome match {
            case DirectFileUploadStatus.Completed(value) =>
              value.reference shouldBe reference
              value.storageRoot shouldBe Some(_root)
              value.version shouldBe Some("9")
              value.contentDigest shouldBe digest.map(value => FileStoreContentDigest.sha256C(value).TAKE)
            case _ => fail("Expected independent completion")
          }
          fixture.requests.map(_.method) shouldBe Vector("PUT", "GET")
          fixture.requests.last.path shouldBe _stat_url(reference)
          fixture.requests.last.bag shouldBe None
          fixture.requests.foreach(_.headers("Authorization") shouldBe "Bearer synthetic-access-marker")
          bytes(fixture.requests.head.bag.get) shouldBe empty
        }
      }

      "reject hostile restored declarations and references before any credential acquisition" in {
        Given("null, foreign, unsafe and nonnew restored reservation bindings")
        val reservation = _reservation()
        val uploads = Vector(null, reservation.upload.copy(parent = FileStoreRef("other", parent.key)),
          reservation.upload.copy(uploadUri = "https://attacker.invalid/upload"),
          reservation.upload.copy(uploadUri = sessionUrl + "&upload_id=duplicate"),
          reservation.upload.copy(protocol = "other"), reservation.upload.copy(name = ""),
          reservation.upload.copy(mediaType = MimeType(folderMime)), reservation.upload.copy(mediaType = null),
          reservation.upload.copy(byteLength = -1))
        val invalid = uploads.map(upload => reservation.copy(upload = upload)) ++ Vector(null,
          reservation.copy(fileReference = null), reservation.copy(fileReference = parent), reservation.copy(fileReference = _root),
          reservation.copy(fileReference = FileStoreRef("foreign", reference.key)),
          reservation.copy(fileReference = FileStoreRef(parent.store, "../unsafe")),
          reservation.copy(storageRoot = null), reservation.copy(storageRoot = Some(null)),
          reservation.copy(storageRoot = Some(FileStoreRef("foreign", _root.key))),
          reservation.copy(storageRoot = Some(FileStoreRef(parent.store, "unsafe/path"))))
        When("each restored value is inspected using refresh credentials")
        val outcomes = invalid.map { value =>
          val fixture = new Harness(_ => throw new AssertionError("Invalid restoration must be pure"))
          (fixture.evaluate(store(refreshCredentials()).inspectReservedDirectUpload(value)), fixture)
        }
        Then("none refreshes a token, reaches an untrusted URI or acquires an HTTP driver")
        outcomes.foreach { case (outcome, fixture) =>
          outcome.isSuccess shouldBe false
          fixture.requests shouldBe empty
          fixture.acquisitions shouldBe 0
          disclosures(failure(outcome)).foreach(output => output should not include "synthetic-session-marker")
        }
      }

      "reject client-claimed completion identities before reading a different file" in {
        Given("an otherwise matching session completion whose file ID differs from the retained server ID")
        val fixture = new Harness(_ => jsonResponse(metadata(id = "client_claimed_file")))
        When("inspection checks that session result")
        val outcome = fixture.evaluate(store().inspectReservedDirectUpload(_reservation()))
        Then("it fails without a metadata GET or new create")
        outcome.isSuccess shouldBe false
        fixture.requests.map(_.method) shouldBe Vector("PUT")
      }

      "reject final identity, parent, volume, MIME, size, kind and trashed mismatches" in {
        Given("session completion followed by incompatible independent provider metadata")
        val documents = Vector(_rooted(metadata(id = "foreign")), _rooted(metadata(name = "wrong")),
          _rooted(metadata(parents = Some(Vector("wrong")))), _rooted(metadata(parents = Some(Vector(parent.key, "extra")))),
          _rooted(metadata(), Some(FileStoreRef(parent.store, "another_drive"))),
          metadata(), _rooted(metadata(mime = "image/png")), _rooted(metadata(size = Some("4"))),
          _rooted(metadata(size = None)),
          _rooted(metadata(mime = folderMime, size = None)), _rooted(metadata()).deepMerge(Json.obj("trashed" -> Json.True)),
          _rooted(metadata()).deepMerge(Json.obj("sha256Checksum" -> Json.Null)))
        When("each final observation is read from the retained reserved file endpoint")
        val outcomes = documents.map { document =>
          val fixture = new Harness(request => if (request.method == "PUT") jsonResponse(metadata()) else jsonResponse(document))
          (fixture.evaluate(store().inspectReservedDirectUpload(_reservation())), fixture)
        }
        Then("no mismatching or malformed final observation produces completion")
        outcomes.foreach { case (outcome, fixture) =>
          outcome.isSuccess shouldBe false
          fixture.requests.map(_.method) shouldBe Vector("PUT", "GET")
          fixture.requests.last.path shouldBe _stat_url(reference)
        }
      }

      "distinguish an absent My Drive root from an observed Shared Drive root" in {
        Given("a parent without driveId and final metadata with absent, unexpected or malformed driveId")
        val prepared = new Harness(request => if (request.path == _stat_url(parent)) jsonResponse(_parent(None)) else _prepare_answer(request))
        val documents = Vector(metadata(), _rooted(metadata()), metadata().deepMerge(Json.obj("driveId" -> Json.Null)),
          metadata().deepMerge(Json.obj("driveId" -> Json.fromInt(1))), metadata().deepMerge(Json.obj("driveId" -> Json.fromString(""))),
          metadata().deepMerge(Json.obj("driveId" -> Json.fromString("unsafe/path"))))
        When("preparation and independent completion observe those roots")
        val reservation = prepared.evaluate(store().prepareReservedDirectUpload(parent, "label", MimeType("application/octet-stream"), 3)).TAKE
        val outcomes = documents.map { document =>
          val fixture = new Harness(request => if (request.method == "PUT") jsonResponse(metadata()) else jsonResponse(document))
          fixture.evaluate(store().inspectReservedDirectUpload(reservation))
        }
        Then("only absence matches absence, and present malformed roots are not coerced")
        reservation.storageRoot shouldBe None
        outcomes.head.TAKE match {
          case DirectFileUploadStatus.Completed(value) => value.storageRoot shouldBe None
          case _ => fail("Expected My Drive completion")
        }
        outcomes.tail.foreach(_.isSuccess shouldBe false)
      }

      "reconcile an expired session only against the retained file binding" in {
        Given("session 404 followed by matching, foreign or genuinely missing reserved-file metadata")
        val responses = Vector(jsonResponse(_rooted(metadata())), jsonResponse(_rooted(metadata(id = "foreign"))), textResponse("", 404))
        When("inspection reads the known file after the expired session")
        val outcomes = responses.map { response =>
          val fixture = new Harness(request => if (request.method == "PUT") textResponse("", 404) else response)
          (fixture.evaluate(store().inspectReservedDirectUpload(_reservation())), fixture)
        }
        Then("only matching stat metadata recovers completion and two actual 404s retain Expired")
        outcomes.head._1.TAKE match {
          case DirectFileUploadStatus.Completed(value) => value.reference shouldBe reference
          case _ => fail("Expected completion reconciliation")
        }
        outcomes(1)._1.isSuccess shouldBe false
        outcomes.last._1.TAKE shouldBe DirectFileUploadStatus.Expired
        outcomes.foreach { case (_, fixture) =>
          fixture.requests.map(_.method) shouldBe Vector("PUT", "GET")
          fixture.requests.last.path shouldBe _stat_url(reference)
        }
      }

      "propagate status and independent metadata failures without manufacturing completion or expiry" in {
        Given("denied or unavailable sessions, and failed metadata after either completion or session 404")
        val cases = Vector(403 -> 200, 503 -> 200, 200 -> 404, 200 -> 403, 200 -> 503, 404 -> 403, 404 -> 503)
        When("inspection receives each native provider status pair")
        val outcomes = cases.map { case (sessioncode, filecode) =>
          val fixture = new Harness(request => if (request.method == "PUT") jsonResponse(metadata(), sessioncode)
            else textResponse("provider-body-marker", filecode))
          (fixture.evaluate(store().inspectReservedDirectUpload(_reservation())), fixture, sessioncode, filecode)
        }
        Then("every case remains a structured failure with the existing status mapping and no new POST")
        outcomes.foreach { case (outcome, fixture, sessioncode, filecode) =>
          outcome.isSuccess shouldBe false
          fixture.requests.map(_.method) shouldBe (if (sessioncode == 403 || sessioncode == 503) Vector("PUT") else Vector("PUT", "GET"))
          val code = if (sessioncode == 403 || sessioncode == 503) sessioncode else filecode
          val expected = if (code == 403) Consequence.securityPermissionDenied("denied")
            else if (code == 404) Consequence.resourceNotFound("missing") else Consequence.serviceUnavailable("unavailable")
          failure(outcome).status shouldBe failure(expected).status
          disclosures(failure(outcome)).foreach(output => output should not include "provider-body-marker")
        }
      }
    }

    "generated identity and managed authorization" which {
      "carry generated safe IDs into both provider creation and retained reservation identity" in {
        Given("a generator of safe new opaque Drive IDs independent of parent and volume")
        val ids = Gen.nonEmptyListOf(Gen.oneOf("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-".toVector))
          .map(chars => "new_" + chars.take(100).mkString)
        When("at least forty generated IDs pass through the native preparation sequence")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(40), Prop.forAll(ids) { id =>
          val fixture = new Harness(request => if (request.path == _generate_url) jsonResponse(_generated(id)) else _prepare_answer(request))
          val reservation = fixture.evaluate(store().prepareReservedDirectUpload(parent, "label", MimeType("application/octet-stream"), 3)).TAKE
          val posted = parser.parse(fixture.requests.last.text.get).toOption.get.hcursor.get[String]("id").toOption
          reservation.fileReference == FileStoreRef(parent.store, id) && posted.contains(id) &&
            fixture.requests.map(_.method) == Vector("GET", "GET", "POST") && fixture.requests.forall(_.bag.isEmpty)
        })
        Then("every generated identity survives without discards, original bytes or caller-chosen IDs")
        result.passed shouldBe true
        result.succeeded should be >= 40
        result.discarded shouldBe 0
      }

      "reuse the native owning service-account strategy for the whole preparation sequence" in GoogleServiceAccountFixture.withRoot { root =>
        Given("the existing native service-account owner with a generated synthetic key and controlled HTTP driver")
        val owner = new GoogleServiceAccountFixture.Harness(root)
        var requests = Vector.empty[Request]
        val driver = new HttpDriver {
          def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
            val request = Request("GET", path, None, None, headers, properties)
            requests :+= request
            _prepare_answer(request)
          }
          def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
            if (path == oauthUrl) owner.driver.post(path, body, headers, properties)
            else {
              val request = Request("POST", path, body, None, headers, properties)
              requests :+= request
              _prepare_answer(request)
            }
          }
          def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            throw new AssertionError("Preparation must not PUT original bytes")
        }
        val uow = new UnitOfWork(owner.context()) { override def httpDriver: HttpDriver = driver }
        val provider = store(GoogleDriveCredentials.managedConnectionC("shared-drive").TAKE)
        When("the existing active-UoW interpreter prepares a reserved upload")
        val reservation = new UnitOfWorkInterpreter(uow).evaluateInActiveUnitOfWorkC(
          provider.prepareReservedDirectUpload(parent, "label", MimeType("application/octet-stream"), 3)).TAKE
        Then("one native service token exchange serves all three business requests and returns no credentials")
        reservation shouldBe _reservation()
        owner.driver.requests.map(_.path) shouldBe Vector(oauthUrl)
        requests.map(_.method) shouldBe Vector("GET", "GET", "POST")
        requests.foreach(_.headers("Authorization") shouldBe "Bearer synthetic-service-token")
        reservation.toString should not include "synthetic-service-token"
      }
    }
  }

  private def _reservation(root: Option[FileStoreRef] = Some(_root)): DirectFileUploadReservation =
    DirectFileUploadReservation(DirectFileUpload(parent, "label", MimeType("application/octet-stream"), 3,
      "googleDriveResumable", sessionUrl), reference, root)

  private def _stat_url(reference: FileStoreRef): String =
    s"$driveOrigin/drive/v3/files/${reference.key}?supportsAllDrives=true&fields=$fields"

  private def _rooted(document: Json, root: Option[FileStoreRef] = Some(_root)): Json =
    root.fold(document)(value => document.deepMerge(Json.obj("driveId" -> Json.fromString(value.key))))

  private def _parent(root: Option[FileStoreRef] = Some(_root)): Json =
    _rooted(metadata(id = parent.key, mime = folderMime, size = None), root)

  private def _generated(id: String): Json = Json.obj("ids" -> Json.arr(Json.fromString(id)),
    "space" -> Json.fromString("drive"), "kind" -> Json.fromString("drive#generatedIds"))

  private def _prepare_answer(request: Request): HttpResponse =
    if (request.path == _stat_url(parent)) jsonResponse(_parent())
    else if (request.path == _generate_url) jsonResponse(_generated(reference.key))
    else if (request.method == "POST") initiation()
    else throw new AssertionError("Unexpected native preparation request")
}
