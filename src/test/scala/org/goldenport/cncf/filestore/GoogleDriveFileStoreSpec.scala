package org.goldenport.cncf.filestore

import java.io.{ByteArrayInputStream, FilterInputStream, IOException, InputStream}
import java.net.{URL, URLConnection, URLStreamHandler}
import java.util.concurrent.atomic.AtomicInteger
import cats.syntax.all.*
import io.circe.{Json, parser}
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.bag.{Bag, BagBackend, BagMetadata, BinaryBag}
import org.goldenport.cncf.http.{HttpExecutionAdmission, HttpExecutionMethod}
import org.goldenport.cncf.observability.{ConclusionDiagnostics, ConclusionDisclosure}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 */
final class GoogleDriveFileStoreSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoogleDriveFixture.*
  import GoogleDriveFileStoreSpec.*

  "Google Drive FileStore" should {
    "configured identity before provider access" which {
      "accepts only logical store names and keeps credentials out of its display" in {
        Given("trusted synthetic credentials and logical names at the admitted bounds")
        val credentials = fixedCredentials()
        val valid = Vector("a", "Drive.main_1-backup", "a" + ("x" * 127))
        val invalid = Vector("", ".drive", "drive/main", "drive name", "資料", "a" * 129)
        When("the pure store factory interprets the configured identities")
        val admitted = valid.map(name => GoogleDriveFileStore.createC(name, credentials).TAKE)
        val rejected = invalid.map(name => GoogleDriveFileStore.createC(name, credentials))
        Then("valid names survive exactly and invalid names have a fixed argument failure")
        admitted.map(_.name) shouldBe valid
        admitted.foreach { value =>
          value.toString shouldBe s"GoogleDriveFileStore(${value.name})"
          value.toString should not include "synthetic-access-marker"
          value.toString should not include "GoogleDriveCredentials"
        }
        rejected.foreach(result => failure(result).cause.getMessage shouldBe Some("Invalid Google Drive file store configuration"))
      }

      "rejects foreign stores and nonopaque Drive keys before credentials for every operation" in {
        Given("refresh credentials and references containing path, URI, Unicode, query, empty or oversized keys")
        val invalid = Vector(FileStoreRef("other-store", reference.key)) ++
          Vector("", "a/b", "https://example.invalid/id", "資料", "a?b", "a b", "a\u007fb", "a" * 257)
            .map(key => FileStoreRef(parent.store, key))
        invalid.foreach { ref =>
          val fixture = new Harness(_ => throw new AssertionError("Invalid references must not request credentials or HTTP"))
          val provider = store(refreshCredentials())
          When("stat, read and both create programs interpret the rejected reference")
          val results = Vector(fixture.evaluate(provider.stat(ref)), fixture.evaluate(provider.read(ref)),
            fixture.evaluate(provider.createCollection(ref, "label")),
            fixture.evaluate(provider.create(ref, "label", _mime, Bag.binary(Array[Byte](0)))))
          Then("all four fail with the same fixed argument message and no native requests")
          results.foreach(result => failure(result).cause.getMessage shouldBe Some("Invalid Google Drive file reference"))
          fixture.requests shouldBe empty
          fixture.acquisitions shouldBe 0
        }
      }
    }

    "independent stat and read" which {
      "uses the exact stat endpoint and preserves absent metadata observations" in {
        Given("a valid JSON object with absent size, parents and version and a tighter runtime response ceiling")
        val fixture = new Harness(_ => jsonResponse(metadata(size = None, parents = None, version = None)), bytes = 1024)
        When("the native interpreter evaluates stat once")
        val result = fixture.evaluate(store().stat(reference)).TAKE
        Then("unknown observations remain absent, and the host-tightened native GET requests only metadata")
        result.reference shouldBe reference
        result.name shouldBe "label"
        result.byteLength shouldBe None
        result.parents shouldBe empty
        result.version shouldBe None
        result.kind shouldBe FileStoreKind.File
        fixture.requests should have size 1
        val request = fixture.requests.head
        request.method shouldBe "GET"
        request.path shouldBe s"$driveOrigin/drive/v3/files/${reference.key}?supportsAllDrives=true&fields=$fields"
        request.headers shouldBe Map("Authorization" -> "Bearer synthetic-access-marker", "Accept" -> "application/json")
        request.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("1024")
        request.properties.exists(_.name.equalsIgnoreCase("http.response-body-mode")) shouldBe false
        request.bag shouldBe None
        request.text shouldBe None
      }

      "distinguishes Drive folders and preserves canonical version strings as observations" in {
        Vector("application/octet-stream" -> FileStoreKind.File, folderMime -> FileStoreKind.Collection).foreach { case (mime, kind) =>
          Given(s"valid metadata for $kind including zero size and an opaque canonical decimal version")
          val version = "18446744073709551615"
          val fixture = new Harness(_ => jsonResponse(metadata(mime = mime, size = Some("0"),
            parents = Some(Vector(parent.key, "another-parent")), version = Some(version))))
          When("stat interprets the actual provider observations")
          val result = fixture.evaluate(store().stat(reference)).TAKE
          Then("kind is MIME-derived, size zero is explicit, and parent references retain the same logical store")
          result.kind shouldBe kind
          result.mediaType shouldBe MimeType(mime)
          result.byteLength shouldBe Some(0L)
          result.parents shouldBe Vector(parent, FileStoreRef(parent.store, "another-parent"))
          result.version shouldBe Some(version)
          fixture.requests.size shouldBe 1
        }
      }

      "rejects malformed metadata without coercing provider primitives or inventing observations" in {
        Given("metadata with wrong identity, label, MIME, trash, parent, size or version shapes")
        val valid = metadata()
        val malformed = Vector(Json.Null, Json.arr(valid), Json.obj(),
          valid.deepMerge(Json.obj("id" -> Json.fromString("different-id"))),
          valid.deepMerge(Json.obj("id" -> Json.fromString("bad/id"))),
          valid.deepMerge(Json.obj("name" -> Json.fromString(""))),
          valid.deepMerge(Json.obj("name" -> Json.fromString("bad\u0085label"))),
          valid.deepMerge(Json.obj("name" -> Json.fromInt(1))),
          valid.deepMerge(Json.obj("mimeType" -> Json.fromString("text/plain; charset=UTF-8"))),
          valid.deepMerge(Json.obj("mimeType" -> Json.fromString("bad mime"))),
          valid.deepMerge(Json.obj("mimeType" -> Json.Null)),
          valid.mapObject(_.remove("trashed"))) ++
          Vector(Json.True, Json.Null, Json.fromString("false")).map(value => valid.deepMerge(Json.obj("trashed" -> value))) ++
          Vector(Json.Null, Json.fromString("parent"), Json.arr(Json.fromString(parent.key), Json.fromInt(1)),
            Json.arr(Json.fromString("bad/parent"))).map(value => valid.deepMerge(Json.obj("parents" -> value))) ++
          Vector(Json.Null, Json.fromInt(3), Json.fromString("00"), Json.fromString("+1"), Json.fromString("-1"),
            Json.fromString("1.0"), Json.fromString(" 1"), Json.fromString("9223372036854775808"))
            .map(value => valid.deepMerge(Json.obj("size" -> value))) ++
          Vector(Json.Null, Json.fromInt(1), Json.fromString("01"), Json.fromString("-1"), Json.fromString("1.5"))
            .map(value => valid.deepMerge(Json.obj("version" -> value)))
        val responses = malformed.map(jsonResponse(_)) ++ Vector(textResponse("{provider-body-marker"),
          textResponse(valid.noSpaces, mime = "text/plain"))
        When("stat interprets each malformed successful provider response")
        val results = responses.map { response =>
          val fixture = new Harness(_ => response)
          (fixture.evaluate(store().stat(reference)), fixture)
        }
        Then("every result is the fixed metadata failure after one GET, with no provider echo")
        results.foreach { case (result, fixture) =>
          val conclusion = failure(result)
          conclusion.cause.getMessage shouldBe Some("Google Drive file metadata is invalid")
          conclusion.observation.taxonomy shouldBe failure(Consequence.resourceInvalid("fixed")).observation.taxonomy
          fixture.requests.size shouldBe 1
          disclosures(conclusion).foreach(output => output should not include "provider-body-marker")
        }
      }

      "returns the exact materialized binary Bag through one media GET without a hidden stat" in {
        Given("a binary response with zero and non-UTF8 octets under the native runtime byte ceiling")
        val body = Bag.binary(Array[Byte](0, -128, -1))
        val response = HttpResponse.Binary(HttpStatus.Ok, ContentType(_mime, None), body)
        val fixture = new Harness(_ => response, bytes = 4096)
        When("read is interpreted directly")
        val result = fixture.evaluate(store().read(reference)).TAKE
        Then("the exact materialized response Bag and MIME survive without stat, decoding, copy or another HTTP request")
        result.mediaType shouldBe _mime
        result.content should be theSameInstanceAs response.bag
        bytes(result.content) shouldBe Vector[Byte](0, -128, -1)
        fixture.requests.map(_.path) shouldBe Vector(s"$driveOrigin/drive/v3/files/${reference.key}?alt=media&supportsAllDrives=true")
        fixture.requests.head.headers shouldBe Map("Authorization" -> "Bearer synthetic-access-marker", "Accept" -> "application/octet-stream")
        fixture.requests.head.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("4096")
        fixture.requests.head.properties.head shouldBe Property("http.response-body-mode", "binary", None)
        fixture.requests.head.properties.count(_.name.equalsIgnoreCase("http.response-body-mode")) shouldBe 1
      }

      "preserves a text-MIME original's arbitrary binary Bag without adapter decoding" in {
        Given("a counted driver returning a materialized binary Bag under text/plain MIME, without native transport fidelity claims")
        val payload = Vector[Byte](0, -128, -1, 65)
        val body = Bag.binary(payload.toArray)
        val mime = MimeType("text/plain")
        val response = HttpResponse.Binary(HttpStatus.Ok, ContentType(mime, Some(java.nio.charset.StandardCharsets.UTF_8)), body)
        val fixture = new Harness(_ => response)
        When("the actual native interpreter evaluates the provider's single media read")
        val result = fixture.evaluate(store().read(reference)).TAKE
        Then("the adapter selects native binary representation and passes the exact supplied MIME and Bag through once")
        result.mediaType shouldBe mime
        result.content should be theSameInstanceAs body
        bytes(result.content) shouldBe payload
        fixture.requests.size shouldBe 1
        fixture.requests.head.properties.head shouldBe Property("http.response-body-mode", "binary", None)
        fixture.requests.head.path shouldBe s"$driveOrigin/drive/v3/files/${reference.key}?alt=media&supportsAllDrives=true"
      }

      "lets consumers check trusted metadata before reading and retains exact stat then read order" in {
        Given("provider metadata matching the caller's trusted parent, MIME and length")
        val body = Bag.binary(Array[Byte](0, -1, -128))
        val fixture = new Harness(request => if (request.path.contains("alt=media"))
          HttpResponse.Binary(HttpStatus.Ok, ContentType(_mime, None), body) else jsonResponse(metadata()))
        When("a consumer composes stat, its own trusted-source check and then read")
        val result = fixture.evaluate(_checked_read(store())).TAKE
        Then("the accepted bytes come from exactly the explicit ordered stat and read calls")
        result.content should be theSameInstanceAs body
        fixture.requests.map(_.path) shouldBe Vector(
          s"$driveOrigin/drive/v3/files/${reference.key}?supportsAllDrives=true&fields=$fields",
          s"$driveOrigin/drive/v3/files/${reference.key}?alt=media&supportsAllDrives=true")
      }

      "stops at the caller check for trusted parent, MIME or length mismatch" in {
        Vector(metadata(parents = Some(Vector("other-parent"))), metadata(mime = "image/png"), metadata(size = Some("4"))).foreach { json =>
          Given("metadata that disagrees with one of the caller's trusted source observations")
          val fixture = new Harness(_ => jsonResponse(json))
          When("the consumer checks stat before attempting a content read")
          val result = fixture.evaluate(_checked_read(store()))
          Then("the caller failure prevents the media GET")
          failure(result).cause.getMessage shouldBe Some("Trusted original metadata does not match")
          fixture.requests.size shouldBe 1
          fixture.requests.head.path should not include "alt=media"
        }
      }

      "maps every representable provider read failure without retry or body disclosure" in {
        for (operation <- Vector("stat", "read"); code <- Vector(201, 302, 400, 401, 403, 404, 429, 500, 503)) {
          Given(s"a native $operation response for requested provider status $code")
          val response = textResponse("provider-body-marker", code)
          val fixture = new Harness(_ => response)
          val program = if (operation == "stat") store().stat(reference).map(_ => ()) else store().read(reference).map(_ => ())
          When("the single requested read operation is interpreted")
          val actual = failure(fixture.evaluate(program))
          Then("the fixed provider status category survives without retry, refresh replay or body echo")
          _expect_status(actual, response.code, false)
          fixture.requests.size shouldBe 1
          disclosures(actual).foreach(output => output should not include "provider-body-marker")
        }
      }
    }

    "create-only collection and binary lifecycle" which {
      "creates a collection with the exact metadata-only POST and confirms a provider-issued identity" in {
        Vector(200, 201).foreach { code =>
          Given(s"a parent and a slash/Unicode label returned as complete folder metadata with status $code")
          val label = "資料 / folder 🎵"
          val fixture = new Harness(_ => jsonResponse(metadata(id = "new_folder", name = label, mime = folderMime, size = None), code))
          When("the native collection-create program is interpreted")
          val result = fixture.evaluate(store().createCollection(parent, label)).TAKE
          Then("one create-only POST carries only label, folder MIME and parent and accepts the new collection reference")
          result.reference shouldBe FileStoreRef(parent.store, "new_folder")
          result.kind shouldBe FileStoreKind.Collection
          result.name shouldBe label
          result.parents shouldBe Vector(parent)
          fixture.requests.size shouldBe 1
          val request = fixture.requests.head
          request.method shouldBe "POST"
          request.path shouldBe s"$driveOrigin/drive/v3/files?supportsAllDrives=true&fields=$fields"
          _expect_creation_json(request, parent, label, folderMime)
          _expect_json_headers(request)
          request.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("65536")
        }
      }

      "submits zero and binary files through a single initiation and the exact same native PUT Bag" in {
        for (payload <- Vector(Vector.empty[Byte], Vector[Byte](0, -128, -1)); code <- Vector(200, 201)) {
          Given("a caller-owned known-length binary Bag and an admitted provider session")
          val body = Bag.binary(payload.toArray)
          val fixture = new Harness(request => if (request.method == "POST") initiation()
            else jsonResponse(metadata(id = "fresh_file", size = Some(payload.size.toString)), code))
          val program = store().create(parent, "label", _mime, body)
          val before = fixture.requests
          When("the native interpreter evaluates the two-shot create program")
          val result = fixture.evaluate(program).TAKE
          Then("construction had no effects and exactly POST then PUT confirms the returned physical reference")
          before shouldBe empty
          result.reference shouldBe FileStoreRef(parent.store, "fresh_file")
          result.byteLength shouldBe Some(payload.size.toLong)
          fixture.requests.map(_.method) shouldBe Vector("POST", "PUT")
          val start = fixture.requests.head
          start.path shouldBe s"$driveOrigin/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true&fields=$fields"
          _expect_creation_json(start, parent, "label", _mime.value)
          _expect_json_headers(start)
          start.headers("X-Upload-Content-Type") shouldBe "application/octet-stream"
          start.headers("X-Upload-Content-Length") shouldBe payload.size.toString
          val finish = fixture.requests.last
          finish.path shouldBe sessionUrl
          finish.bag.get should be theSameInstanceAs body
          finish.text shouldBe None
          finish.headers shouldBe Map("Authorization" -> "Bearer synthetic-access-marker", "Content-Type" -> "application/octet-stream", "Accept" -> "application/json")
          finish.headers.contains("Content-Length") shouldBe false
          fixture.requests.foreach(request => request.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("65536"))
          fixture.requests.foreach(request => request.properties.exists(_.name.equalsIgnoreCase("http.response-body-mode")) shouldBe false)
        }
      }

      "keeps duplicate labels as fresh references and never overwrites earlier content" in {
        Given("a deterministic counted Drive peer, one grouping parent and two distinct binary Bags sharing a label")
        val peer = new CreatePeer
        val fixture = new Harness(peer.respond)
        val provider = store()
        val first = Bag.binary(Array[Byte](0, -1, -128))
        val second = Bag.binary(Array[Byte](1, 2))
        When("a consumer creates a folder and two equally named files, then explicitly reads both")
        val program = for {
          folder <- provider.createCollection(parent, "collection")
          a <- provider.create(folder.reference, "same / 資料", _mime, first)
          b <- provider.create(folder.reference, "same / 資料", _mime, second)
          ac <- provider.read(a.reference)
          bc <- provider.read(b.reference)
        } yield (folder, a, b, ac, bc)
        val result = fixture.evaluate(program).TAKE
        Then("all references are provider-issued and earlier bytes remain available under their original identity")
        Set(result._1.reference, result._2.reference, result._3.reference).size shouldBe 3
        result._2.name shouldBe result._3.name
        result._2.parents shouldBe Vector(result._1.reference)
        result._3.parents shouldBe Vector(result._1.reference)
        bytes(result._4.content) shouldBe Vector[Byte](0, -1, -128)
        bytes(result._5.content) shouldBe Vector[Byte](1, 2)
        peer.contents(result._2.reference.key) shouldBe Vector[Byte](0, -1, -128)
        fixture.requests.map(_.method) shouldBe Vector("POST", "POST", "PUT", "POST", "PUT", "GET", "GET")
      }

      "borrows the actual materialized Bag only once during an admitted binary PUT" in {
        Given("a counted non-URL BinaryBag wrapper over materialized bytes and a counted provider peer")
        val opens = new AtomicInteger
        val closes = new AtomicInteger
        val body = new CountedBag(Array[Byte](0, -1, -128), Some(3L), opens, closes)
        val peer = new CreatePeer
        val fixture = new Harness(peer.respond)
        val program = store().create(parent, "label", _mime, body)
        val before = opens.get()
        When("the native interpreter admits the two-shot program and its driver consumes the borrowed PUT input")
        val result = fixture.evaluate(program).TAKE
        Then("program construction opened nothing, and only the driver opened and closed the exact submitted Bag once")
        before shouldBe 0
        body.backend shouldBe a[BagBackend.InMemory]
        fixture.requests.last.bag.get should be theSameInstanceAs body
        peer.contents(result.reference.key) shouldBe Vector[Byte](0, -1, -128)
        opens.get() shouldBe 1
        closes.get() shouldBe 1
      }

      "obtains one fresh token per logical operation while sharing create's token across both requests" in {
        Given("refresh credentials and a deterministic provider accepting all four logical store operations")
        var exchanges = 0
        val peer = new CreatePeer
        val fixture = new Harness(request => if (request.path == oauthUrl) {
          exchanges += 1
          jsonResponse(tokenJson(s"synthetic-access-$exchanges"))
        } else peer.respond(request))
        val provider = store(refreshCredentials())
        When("collection create, file create, stat and read are interpreted as one composed consumer program")
        val program = for {
          folder <- provider.createCollection(parent, "folder")
          file <- provider.create(folder.reference, "label", _mime, Bag.binary(Array[Byte](0)))
          observed <- provider.stat(file.reference)
          content <- provider.read(file.reference)
        } yield (observed, content)
        val result = fixture.evaluate(program).TAKE
        Then("exactly four OAuth exchanges precede their operations, and create initiation/completion use the same second token")
        exchanges shouldBe 4
        bytes(result._2.content) shouldBe Vector[Byte](0)
        fixture.requests.map(request => if (request.path == oauthUrl) "OAuth" else request.method) shouldBe
          Vector("OAuth", "POST", "OAuth", "POST", "PUT", "OAuth", "GET", "OAuth", "GET")
        fixture.requests.filterNot(_.path == oauthUrl).map(_.headers("Authorization")) shouldBe
          Vector("Bearer synthetic-access-1", "Bearer synthetic-access-2", "Bearer synthetic-access-2", "Bearer synthetic-access-3", "Bearer synthetic-access-4")
        fixture.requests.filter(_.path == oauthUrl).foreach { request =>
          request.properties.exists(_.name.equalsIgnoreCase("http.response-body-mode")) shouldBe false
          request.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("16384")
        }
        fixture.requests.filterNot(_.path.contains("alt=media")).foreach { request =>
          request.properties.exists(_.name.equalsIgnoreCase("http.response-body-mode")) shouldBe false
        }
        fixture.requests.last.properties.head shouldBe Property("http.response-body-mode", "binary", None)
      }

      "preserves bounded generated binary payloads and Unicode/slash label space without discards" in {
        Given("fully bounded opaque IDs, binary octets and nonempty labels including slash, quotes and Unicode")
        val inputs = for {
          size <- Gen.choose(0, 128)
          payload <- Gen.listOfN(size, Gen.choose(-128, 127).map(_.toByte))
          count <- Gen.choose(1, 12)
          parts <- Gen.listOfN(count, Gen.oneOf("a", "資料", "é", "/", " ", "\"", "\\", "🎵"))
          idsize <- Gen.choose(1, 32)
          id <- Gen.listOfN(idsize, Gen.oneOf("abcXYZ019_-".toVector))
        } yield (payload.toVector, parts.mkString, id.mkString)
        When("at least one hundred generated creations use actual native admission and POST then PUT interpretation")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(inputs) { case (payload, label, id) =>
          val body = Bag.binary(payload.toArray)
          var received = Vector.empty[Byte]
          val fixture = new Harness(request => if (request.method == "POST") initiation() else {
            received = bytes(request.bag.get)
            jsonResponse(metadata(id = id, name = label, size = Some(payload.size.toString)), 201)
          })
          val outcome = fixture.evaluate(store().create(parent, label, _mime, body)).toOption
          outcome.exists(value => value.reference == FileStoreRef(parent.store, id) && value.name == label &&
            value.byteLength.contains(payload.size.toLong)) && received == payload && fixture.requests.size == 2 &&
            (fixture.requests.last.bag.get eq body) && parser.parse(fixture.requests.head.text.get).toOption.get.hcursor.get[String]("name").toOption.contains(label)
        })
        Then("all generated creations preserve names, bytes, identities and Bag borrowing with zero discards")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }

    "reject inputs and untrusted upload sessions before submission" which {
      "rejects invalid labels, MIME, missing/negative size and URL Bags before credentials or input opening" in {
        Given("refresh credentials, counted URL content and materialized Bags without usable length")
        val opens = new AtomicInteger
        val url = _url_bag(opens)
        val materializedopens = new AtomicInteger
        val materializedcloses = new AtomicInteger
        val bytesbag = new CountedBag(Array[Byte](0), Some(1L), materializedopens, materializedcloses)
        val unsized = new CountedBag(Array[Byte](0), None, materializedopens, materializedcloses)
        val negative = new CountedBag(Array[Byte](0), Some(-1L), materializedopens, materializedcloses)
        val provider = store(refreshCredentials())
        val invalidnames = Vector("", "a\nb", "a\u0085b", "x" * 1025)
        val programs = invalidnames.flatMap(label => Vector(provider.createCollection(parent, label).map(_ => ()),
          provider.create(parent, label, _mime, bytesbag).map(_ => ()))) ++
          Vector("invalid", "text/plain; charset=UTF-8", "text/pla in", folderMime).map(mime =>
            provider.create(parent, "label", MimeType(mime), bytesbag).map(_ => ())) ++
          Vector(url, unsized, negative).map(body => provider.create(parent, "label", _mime, body).map(_ => ()))
        When("each invalid creation program is interpreted through the native runtime")
        val fixture = new Harness(_ => throw new AssertionError("Input rejection must precede all HTTP"))
        val results = programs.map(fixture.evaluate(_))
        Then("every input failure is fixed and no OAuth, Drive or URL input operation occurs")
        results.foreach(result => failure(result).cause.getMessage shouldBe Some("Invalid Google Drive file creation input"))
        fixture.requests shouldBe empty
        fixture.acquisitions shouldBe 0
        opens.get() shouldBe 0
        materializedopens.get() shouldBe 0
        materializedcloses.get() shouldBe 0
      }

      "accepts 1024 UTF16 units as a label without path normalization" in {
        Given("a valid label at the exact UTF16 boundary containing slash and Unicode")
        val label = "資料/" + ("x" * 1021)
        val fixture = new Harness(_ => jsonResponse(metadata(id = "new_folder", name = label, mime = folderMime), 201))
        When("collection creation preserves the label verbatim")
        val result = fixture.evaluate(store().createCollection(parent, label)).TAKE
        Then("the provider receives the unchanged label and its confirmed name remains exact")
        label.length shouldBe 1024
        result.name shouldBe label
        _expect_creation_json(fixture.requests.head, parent, label, folderMime)
      }

      "rejects missing, malformed and untrusted upload locations before any PUT or token disclosure to a destination" in {
        Given("provider sessions violating scheme, host, path, authority, fragment, length or required-query constraints")
        val invalid = Vector("", "relative/session?uploadType=resumable&upload_id=x", "https:opaque",
          sessionUrl.replace("www.googleapis.com", "other.example.invalid"),
          sessionUrl.replace("https:", "http:"), sessionUrl.replace("www.googleapis.com", "www.googleapis.com:8443"),
          sessionUrl.replace("www.googleapis.com", "user@www.googleapis.com"), sessionUrl + "#fragment",
          sessionUrl.replace("/upload/drive/v3/files", "/drive/v3/files/arbitrary"),
          sessionUrl.replace("/upload/drive/v3/files", "/upload/drive/v3/%66iles"),
          s"$driveOrigin/upload/drive/v3/files?uploadType=resumable", s"$driveOrigin/upload/drive/v3/files?uploadType=resumable&upload_id=",
          s"$driveOrigin/upload/drive/v3/files?upload_id=x", sessionUrl.replace("resumable", "multipart"),
          sessionUrl + "&upload_id=duplicate", sessionUrl + "&uploadType=resumable", sessionUrl + "&upload%5Fid=duplicate",
          sessionUrl + "\\bad", sessionUrl + "\n", sessionUrl + "&ordinary=" + ("x" * 16385), sessionUrl + "&broken=%zz")
        val responses = Vector(textResponse("")) ++ invalid.map(initiation(_))
        When("each creation initiation returns one of these unusable locations")
        val results = responses.map { response =>
          val fixture = new Harness(_ => response)
          (fixture.evaluate(store().create(parent, "label", _mime, Bag.binary(Array[Byte](0)))), fixture)
        }
        Then("each creation is unconfirmed after only the trusted-origin initiation, with no PUT or session URI in diagnostics")
        results.foreach { case (outcome, fixture) =>
          val conclusion = failure(outcome)
          conclusion.cause.getMessage shouldBe Some("Google Drive upload session is invalid; creation is unconfirmed")
          fixture.requests.map(_.method) shouldBe Vector("POST")
          fixture.requests.head.path should startWith(driveOrigin + "/upload/drive/v3/files?")
          disclosures(conclusion).foreach { output =>
            output should not include "synthetic-access-marker"
            output should not include "synthetic-session-marker"
            output should not include "other.example.invalid"
          }
        }
      }

      "accepts the default HTTPS port and ordinary extra session query parameters without rewriting the URI" in {
        Given("a valid provider location with explicit default port and an ordinary extra query parameter")
        val location = sessionUrl.replace("www.googleapis.com", "www.googleapis.com:443") + "&supportsAllDrives=true"
        val fixture = new Harness(request => if (request.method == "POST") initiation(location) else jsonResponse(metadata(size = Some("1"))))
        When("the native create program submits its admitted session")
        val result = fixture.evaluate(store().create(parent, "label", _mime, Bag.binary(Array[Byte](0)))).TAKE
        Then("exactly one native PUT uses the original admitted URI and metadata is confirmed")
        result.reference shouldBe reference
        fixture.requests.map(_.method) shouldBe Vector("POST", "PUT")
        fixture.requests.last.path shouldBe location
      }
    }

    "unconfirmed failures without replay" which {
      "maps every provider write-stage failure once without retry, rollback, lookup or status probe" in {
        for (stage <- Vector("collection", "initiation", "completion"); code <- Vector(302, 308, 400, 401, 403, 404, 429, 500, 503)) {
          Given(s"a provider $stage failure represented by native HTTP status $code")
          val response = textResponse("provider-body-marker", code)
          val fixture = new Harness(request => if (stage == "completion" && request.method == "POST") initiation() else response)
          val provider = store()
          val program = if (stage == "collection") provider.createCollection(parent, "label")
            else provider.create(parent, "label", _mime, Bag.binary(Array[Byte](0)))
          When("the requested create stage completes unsuccessfully")
          val conclusion = failure(fixture.evaluate(program))
          Then("the mapped failure remains unaccepted and no automatic request follows it")
          _expect_status(conclusion, response.code, true)
          fixture.requests.map(_.method) shouldBe (if (stage == "completion") Vector("POST", "PUT") else Vector("POST"))
          disclosures(conclusion).foreach(output => output should not include "provider-body-marker")
        }
      }

      "rejects malformed or mismatched completion metadata as unconfirmed for both create kinds" in {
        for (collection <- Vector(false, true)) {
          Given("full provider metadata that fails to confirm the requested create-only write")
          val mime = if (collection) folderMime else "application/octet-stream"
          val valid = metadata(id = "fresh", mime = mime, size = Some("1"))
          val invalid = Vector(Json.Null, Json.obj(), valid.deepMerge(Json.obj("name" -> Json.fromString("different"))),
            valid.deepMerge(Json.obj("mimeType" -> Json.fromString(if (collection) "application/octet-stream" else folderMime))),
            valid.deepMerge(Json.obj("parents" -> Json.arr(Json.fromString("different-parent")))),
            valid.deepMerge(Json.obj("id" -> Json.fromString("bad/id"))), valid.deepMerge(Json.obj("trashed" -> Json.True))) ++
            (if (collection) Vector.empty else Vector(valid.mapObject(_.remove("size")),
              valid.deepMerge(Json.obj("size" -> Json.fromString("2"))), valid.deepMerge(Json.obj("size" -> Json.fromInt(1)))))
          When("each malformed successful completion is interpreted")
          val outcomes = invalid.map { json =>
            val fixture = new Harness(request => if (!collection && request.method == "POST") initiation() else jsonResponse(json, 201))
            val program = if (collection) store().createCollection(parent, "label")
              else store().create(parent, "label", _mime, Bag.binary(Array[Byte](0)))
            (fixture.evaluate(program), fixture)
          }
          Then("no malformed success becomes an accepted reference, and no corrective or repeated request occurs")
          outcomes.foreach { case (result, fixture) =>
            failure(result).cause.getMessage shouldBe Some("Google Drive file creation is unconfirmed; do not retry automatically")
            fixture.requests.size shouldBe (if (collection) 1 else 2)
          }
        }
      }

      "preserves native denial independently at Drive GET, POST and PUT admission boundaries" in {
        for (method <- Vector(HttpExecutionMethod.Get, HttpExecutionMethod.Post, HttpExecutionMethod.Put)) {
          Given(s"a real Component grant omitting $method and a counted materialized input Bag")
          val opens = new AtomicInteger
          val closes = new AtomicInteger
          val payload = new CountedBag(Array[Byte](0), Some(1L), opens, closes)
          val fixture = new Harness(_ => initiation(), methods = HttpExecutionMethod.values.toSet - method)
          val provider = store()
          val program = method match {
            case HttpExecutionMethod.Get => provider.read(reference).map(_ => ())
            case HttpExecutionMethod.Post => provider.createCollection(parent, "label").map(_ => ())
            case HttpExecutionMethod.Put => provider.create(parent, "label", _mime, payload).map(_ => ())
          }
          val path = method match {
            case HttpExecutionMethod.Get => s"$driveOrigin/drive/v3/files/${reference.key}?alt=media&supportsAllDrives=true"
            case HttpExecutionMethod.Post => s"$driveOrigin/drive/v3/files?supportsAllDrives=true&fields=$fields"
            case HttpExecutionMethod.Put => sessionUrl
          }
          val controls = if (method == HttpExecutionMethod.Get) Vector.empty else Vector(Property("http.max-response-bytes", "65536", None))
          val nativeconclusion = failure(HttpExecutionAdmission.resolveC(fixture.context, method, path, controls))
          When("the native store program reaches the missing method grant")
          val actual = failure(fixture.evaluate(program))
          Then("the original native Conclusion survives, no denied driver is acquired, and the actual submitted non-URL Bag is never opened")
          actual.status shouldBe nativeconclusion.status
          actual.observation.isMatch(nativeconclusion.observation) shouldBe true
          actual.interpretation shouldBe nativeconclusion.interpretation
          actual.disposition shouldBe nativeconclusion.disposition
          ConclusionDiagnostics.classify(actual).policy shouldBe Some("http.execution.method")
          fixture.requests.map(_.method) shouldBe (if (method == HttpExecutionMethod.Put) Vector("POST") else Vector.empty)
          fixture.acquisitions shouldBe (if (method == HttpExecutionMethod.Put) 1 else 0)
          payload.backend shouldBe a[BagBackend.InMemory]
          opens.get() shouldBe 0
          closes.get() shouldBe 0
        }
      }

      "retains native transport errors at every stage and stops once without reopening or retrying" in {
        for (stage <- Vector("stat", "read", "collection", "initiation", "completion")) {
          Given(s"a controlled native $stage transport failure and a counted driver")
          val error = new IOException("synthetic-native-transport-marker")
          val fixture = new Harness(request => if (stage == "completion" && request.method == "POST") initiation() else throw error)
          val provider = store()
          val program = stage match {
            case "stat" => provider.stat(reference).map(_ => ())
            case "read" => provider.read(reference).map(_ => ())
            case "collection" => provider.createCollection(parent, "label").map(_ => ())
            case _ => provider.create(parent, "label", _mime, Bag.binary(Array[Byte](0))).map(_ => ())
          }
          When("the actual interpreter contains the driver failure")
          val actual = failure(fixture.evaluate(program))
          Then("the exact driver exception and native taxonomy survive with no request after the failing stage")
          actual.getException.get should be theSameInstanceAs error
          actual.status shouldBe Conclusion.from(error).status
          actual.observation.taxonomy shouldBe Conclusion.from(error).observation.taxonomy
          fixture.requests.size shouldBe (if (stage == "completion") 2 else 1)
        }
      }

      "keeps secret, provider-body and session markers out of enabled calltree, HTTP and log projections at all failure stages" in {
        for (stage <- Vector("stat", "read", "collection", "initiation", "completion")) {
          Given(s"enabled native calltree evidence and a controlled provider failure at $stage")
          val markers = Vector("synthetic-access-marker", "provider-body-marker", "synthetic-session-marker", sessionUrl)
          val fixture = new Harness(request => if (stage == "completion" && request.method == "POST") initiation()
            else textResponse(markers.mkString(" "), 403), enabled = true)
          val provider = store()
          val program = stage match {
            case "stat" => provider.stat(reference).map(_ => ())
            case "read" => provider.read(reference).map(_ => ())
            case "collection" => provider.createCollection(parent, "label").map(_ => ())
            case _ => provider.create(parent, "label", _mime, Bag.binary(Array[Byte](0))).map(_ => ())
          }
          When("the native program is interpreted and existing disclosure projections encode the mapped failure")
          val conclusion = failure(fixture.evaluate(program))
          val outputs = disclosures(conclusion)
          val trace = fixture.trace
          Then("native provider failure taxonomy/status remain inspectable while secrets, response bodies and session URIs are absent everywhere")
          conclusion.observation.taxonomy shouldBe failure(Consequence.securityPermissionDenied("fixed")).observation.taxonomy
          conclusion.cause.getMessage shouldBe Some("Google Drive file access was denied")
          trace should include (if (stage == "stat" || stage == "read") "uow:http:get" else "uow:http:post")
          if (stage == "completion") trace should include ("uow:http:put")
          (outputs :+ trace).foreach(output => markers.foreach(marker => output should not include marker))
          outputs.foreach { output =>
            val json = parser.parse(output).toOption.get
            json.hcursor.downField("status").focus shouldBe Some(jsonRecord(conclusion.status.toRecord))
            json.hcursor.downField("observation").downField("taxonomy").focus shouldBe Some(jsonRecord(conclusion.observation.taxonomy.toRecord))
          }
        }
      }

      "discloses a native completion transport failure without exposing its exception's session URI" in {
        Given("a valid session followed by a controlled native PUT exception containing all outbound-sensitive markers")
        val markers = Vector("synthetic-access-marker", "provider-body-marker", "synthetic-session-marker", sessionUrl)
        val error = new IOException(markers.mkString(" "))
        val fixture = new Harness(request => if (request.method == "POST") initiation() else throw error, enabled = true)
        When("the real interpreter contains the failed completion and its Conclusion is projected")
        val conclusion = failure(fixture.evaluate(store().create(parent, "label", _mime, Bag.binary(Array[Byte](0)))))
        val outputs = disclosures(conclusion) :+ fixture.trace
        Then("the native failure remains unchanged but neither HTTP amount nor log/calltree exposes the synthetic sensitive values")
        conclusion.getException.get should be theSameInstanceAs error
        conclusion.status shouldBe Conclusion.from(error).status
        conclusion.observation.taxonomy shouldBe Conclusion.from(error).observation.taxonomy
        outputs.foreach(output => markers.foreach(marker => output should not include marker))
        fixture.requests.map(_.method) shouldBe Vector("POST", "PUT")
      }

      "documents native 429 fallback and the actual 308 redirection representation" in {
        Given("the existing public HttpStatus representation used by the production HTTP driver")
        val codes = Vector(429, 308)
        When("the native driver-compatible conversion represents these provider statuses")
        val represented = codes.map(code => textResponse("", code).code)
        Then("429 has the existing 500 fallback while 308 retains its actual non-success redirection code")
        represented shouldBe codes.map(code => HttpStatus.fromInt(code).getOrElse(HttpStatus.InternalServerError).code)
        represented shouldBe Vector(500, 308)
      }
    }
  }

  private def _checked_read(provider: FileStore): ExecUowM[FileStoreContent] = for {
    metadata <- provider.stat(reference)
    _ <- ConsequenceT.fromConsequence[org.goldenport.cncf.unitofwork.ExecProgram, Unit](
      if (metadata.parents.contains(parent) && metadata.mediaType == _mime && metadata.byteLength.contains(3L))
        Consequence.success(()) else Consequence.securityPermissionDenied("Trusted original metadata does not match"))
    content <- provider.read(reference)
  } yield content

  private def _expect_creation_json(request: Request, parentref: FileStoreRef, label: String, mime: String): Unit = {
    parser.parse(request.text.get).toOption.get shouldBe Json.obj("name" -> Json.fromString(label),
      "mimeType" -> Json.fromString(mime), "parents" -> Json.arr(Json.fromString(parentref.key)))
    request.bag shouldBe None
  }

  private def _expect_json_headers(request: Request): Unit = {
    request.headers("Authorization") shouldBe "Bearer synthetic-access-marker"
    request.headers("Content-Type") shouldBe "application/json; charset=UTF-8"
    request.headers("Accept") shouldBe "application/json"
  }

  private def _expect_status(actual: Conclusion, code: Int, writing: Boolean): Unit = {
    val expected = code match {
      case 401 | 403 => Consequence.securityPermissionDenied("Google Drive file access was denied")
      case 404 => Consequence.resourceNotFound("Google Drive file was not found")
      case value if value == 429 || value >= 500 => Consequence.serviceUnavailable(
        if (writing) "Google Drive file creation is unconfirmed; do not retry automatically" else "Google Drive file service is unavailable")
      case _ => Consequence.resourceInvalid(
        if (writing) "Google Drive file creation is unconfirmed; do not retry automatically" else "Google Drive file response is invalid")
    }
    actual.status shouldBe failure(expected).status
    actual.observation.taxonomy shouldBe failure(expected).observation.taxonomy
    actual.cause.getMessage shouldBe failure(expected).cause.getMessage
  }
}

private object GoogleDriveFileStoreSpec {
  import GoogleDriveFixture.*

  private val _mime = MimeType("application/octet-stream")

  final class CountedBag(payload: Array[Byte], size: Option[Long], opens: AtomicInteger, closes: AtomicInteger) extends BinaryBag {
    val bag: Bag = Bag.fromBytes(payload)
    override def metadata: BagMetadata = BagMetadata(size = size)
    override def openInputStream(): InputStream = {
      opens.incrementAndGet()
      new FilterInputStream(bag.openInputStream()) {
        override def close(): Unit = {
          closes.incrementAndGet()
          super.close()
        }
      }
    }
  }

  private def _url_bag(opens: AtomicInteger): Bag = {
    val handler = new URLStreamHandler {
      override def openConnection(url: URL): URLConnection = new URLConnection(url) {
        def connect(): Unit = ()
        override def getInputStream(): java.io.InputStream = {
          opens.incrementAndGet()
          new ByteArrayInputStream(Array[Byte](0))
        }
      }
    }
    Bag.Instance(BagBackend.Url(new URL("counted", "drive-input", 0, "/body", handler)), BagMetadata(size = Some(1L)))
  }

  final class CreatePeer {
    private var _entries = Map.empty[String, (Json, Vector[Byte])]
    private var _sessions = Map.empty[String, Json]
    private var _next_id = 0
    def contents(id: String): Vector[Byte] = _entries(id)._2

    def respond(request: Request): HttpResponse = request.method match {
      case "POST" if request.path.contains("/upload/drive/v3/files?") =>
        val location = sessionUrl + s"&ordinary=${_sessions.size}"
        _sessions = _sessions.updated(location, parser.parse(request.text.get).toOption.get)
        initiation(location)
      case "POST" => _save(parser.parse(request.text.get).toOption.get, Vector.empty)
      case "PUT" => _save(_sessions(request.path), bytes(request.bag.get))
      case "GET" =>
        val id = request.path.takeWhile(_ != '?').split('/').last
        val (json, payload) = _entries(id)
        if (request.path.contains("alt=media")) HttpResponse.Binary(HttpStatus.Ok,
          ContentType(MimeType(json.hcursor.get[String]("mimeType").toOption.get), None), Bag.binary(payload.toArray))
        else jsonResponse(json)
      case _ => throw new AssertionError("Unexpected Drive fixture operation")
    }

    private def _save(input: Json, payload: Vector[Byte]): HttpResponse = {
      _next_id += 1
      val id = s"provider_${_next_id}"
      val json = input.deepMerge(Json.obj("id" -> Json.fromString(id), "size" -> Json.fromString(payload.size.toString),
        "trashed" -> Json.False, "version" -> Json.fromString("1")))
      _entries = _entries.updated(id, (json, payload))
      jsonResponse(json, 201)
    }
  }
}
