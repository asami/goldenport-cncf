package org.goldenport.cncf.oauth

import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardOpenOption}
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.util.Using
import io.circe.{Json, parser}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod}
import org.goldenport.cncf.observability.ConclusionDisclosure
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Protocol}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
final class OAuthCredentialStoreSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OAuthCredentialStoreSpec.*

  "E1 OAuth credential custody admission" should afterWord(
    "in spec:oauth-credential-custody, example:E1, rules:R1, phase:PHASE-3") {
    "explicit private host configuration" which {
      "admits an absent selected file without creating a directory or record" in {
        Given("R1/E1 docs/spec/oauth-credential-custody.md: a canonical private directory and an absent credential")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val client = _client()
          val principal = root.getFileSystem.getUserPrincipalLookupService
            .lookupPrincipalByName(ProcessHandle.current().info().user().get())
          When("the store is constructed, loaded and forgotten")
          val store = _store(file, client)
          val loaded = store.loadC()
          val forgotten = store.forgetC()
          val names = _names(root)
          val actualowner = Files.getOwner(root, LinkOption.NOFOLLOW_LINKS)
          val mode = Files.getPosixFilePermissions(root)
          Then("absence is honest and idempotent under the actual process user's exact private directory")
          loaded.TAKE shouldBe None
          forgotten.isSuccess shouldBe true
          names shouldBe empty
          actualowner shouldBe principal
          mode shouldBe PosixFilePermissions.fromString("rwx------")
        }
      }

      "rejects malformed paths and logical bindings without creating missing parents" in {
        Given("R1/E1 docs/spec/oauth-credential-custody.md: null, relative, non-normal and missing-parent paths and malformed identities")
        _with_root { root =>
          val client = _client()
          val file = root.resolve("selected.json")
          val paths = Vector[Path](null, Paths.get("relative.json"), root.resolve("x/../selected.json"),
            root.getRoot, root.resolve("missing/selected.json"))
          val connections = Vector(null, "", "-bad", "bad name", "é", "x" * 129)
          val owners = Vector(null, "", "has space", "line\nmarker", "tab\tmarker", "é", "x" * 257)
          When("the host explicitly selects each invalid configuration")
          val outcomes = paths.map(PrivateFileOAuthCredentialStore.createC(_, _connection, _owner, client)) ++
            connections.map(PrivateFileOAuthCredentialStore.createC(file, _, _owner, client)) ++
            owners.map(PrivateFileOAuthCredentialStore.createC(file, _connection, _, client)) ++
            Vector(PrivateFileOAuthCredentialStore.createC(file, _connection, _owner, null))
          val names = _names(root)
          Then("every unsafe configuration is refused with the fixed structured failure and no discovery or directory creation")
          outcomes.foreach(_same_failure(_, _configuration_failure))
          names shouldBe empty
        }
      }

      "admits the inclusive logical identifier limits without deriving an OS user from the ingress owner" in {
        Given("R1/E1 docs/spec/oauth-credential-custody.md: a 128-character connection and 256-character ingress owner")
        _with_root { root =>
          When("the explicit host file is admitted with those independent logical bindings")
          val outcome = PrivateFileOAuthCredentialStore.createC(root.resolve("selected.json"),
            "a" * 128, "o" * 256, _client())
          Then("both bounded values are accepted without an OS-account lookup for the ingress owner")
          outcome.isSuccess shouldBe true
        }
      }

      "refuses parent modes other than exactly 0700 including extra special permission bits" in {
        Given("R1/E1 docs/spec/oauth-credential-custody.md: an actual process-owned parent with unsafe POSIX modes")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val modes = Vector(493, 488, 511, 449, 320, 960)
          When("each parent mode is offered to the factory")
          val outcomes = modes.map { mode =>
            Files.setAttribute(root, "unix:mode", Integer.valueOf(mode), LinkOption.NOFOLLOW_LINKS)
            PrivateFileOAuthCredentialStore.createC(file, _connection, _owner, _client())
          }
          Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
          val present = Files.exists(file, LinkOption.NOFOLLOW_LINKS)
          Then("owner-only, executable-directory access is exact rather than a permission subset")
          outcomes.foreach(_same_failure(_, _configuration_failure))
          present shouldBe false
        }
      }

      "rejects non-directory parents, parent symlinks and symlink ancestors instead of canonicalizing them" in {
        Given("R1/E1 docs/spec/oauth-credential-custody.md: actual literal and linked directory paths")
        _with_root { root =>
          val actual = Files.createDirectory(root.resolve("actual"), _directory_attribute)
          val nested = Files.createDirectory(actual.resolve("nested"), _directory_attribute)
          val alias = Files.createSymbolicLink(root.resolve("alias"), actual)
          val regular = _write_raw(root.resolve("regular"), Array[Byte](1))
          val paths = Vector(alias.resolve("selected.json"), alias.resolve("nested/selected.json"),
            regular.resolve("selected.json"))
          When("the selected filename has a linked or non-directory ancestor")
          val outcomes = paths.map(PrivateFileOAuthCredentialStore.createC(_, _connection, _owner, _client()))
          val actualnames = _names(actual)
          val nestednames = _names(nested)
          Then("all aliases and non-directory parents are rejected without creating any credential")
          outcomes.foreach(_same_failure(_, _configuration_failure))
          actualnames shouldBe Set("nested")
          nestednames shouldBe empty
        }
      }
    }
  }

  "E2 OAuth credential custody filesystem safety" should afterWord(
    "in spec:oauth-credential-custody, example:E2, rules:R1,R2, phase:PHASE-3") {
    "admission on every operation" which {
      "rechecks parent permissions on load, save and forget after construction" in {
        Given("R1,R2/E2 docs/spec/oauth-credential-custody.md: an admitted store followed by an unsafe parent mode")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val client = _client()
          val credential = _exchange(client)
          val store = _store(file, client)
          store.saveC(credential).TAKE
          val before = Files.readAllBytes(file).toVector
          Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-x---"))
          When("all three custody operations request the same file")
          val outcomes = Vector(store.loadC(), store.saveC(credential), store.forgetC())
          Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
          val after = Files.readAllBytes(file).toVector
          val names = _names(root)
          Then("the changed parent is refused and the existing credential is retained byte for byte")
          outcomes.foreach(_same_failure(_, _configuration_failure))
          after shouldBe before
          names shouldBe Set("selected.json")
        }
      }

      "refuses every unsafe selected-file mode without repairing, overwriting or deleting it" in {
        Given("R1,R2/E2 docs/spec/oauth-credential-custody.md: a valid record whose selected file changes mode")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val client = _client()
          val credential = _exchange(client)
          val store = _store(file, client)
          store.saveC(credential).TAKE
          val before = Files.readAllBytes(file).toVector
          When("factory, load, save and forget encounter modes other than exactly 0600")
          val observations = Vector(420, 256, 448, 388, 0, 2432).map { mode =>
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
            Files.setAttribute(file, "unix:mode", Integer.valueOf(mode), LinkOption.NOFOLLOW_LINKS)
            val results = Vector(PrivateFileOAuthCredentialStore.createC(file, _connection, _owner, client),
              store.loadC(), store.saveC(credential), store.forgetC())
            val retainedmode = Files.getAttribute(file, "unix:mode", LinkOption.NOFOLLOW_LINKS).asInstanceOf[Number].intValue() & 4095
            (results, retainedmode, mode)
          }
          Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
          val after = Files.readAllBytes(file).toVector
          val names = _names(root)
          Then("all unsafe modes are fixed configuration failures and no permission repair or content mutation occurs")
          observations.foreach { case (outcomes, retainedmode, mode) =>
            outcomes.foreach(_same_failure(_, _configuration_failure))
            retainedmode shouldBe mode
          }
          after shouldBe before
          names shouldBe Set("selected.json")
        }
      }

      "rejects a directory at the selected filename without removing its contents" in {
        Given("R1,R2/E2 docs/spec/oauth-credential-custody.md: a selected directory containing an unrelated sentinel")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val client = _client()
          val store = _store(file, client)
          Files.createDirectory(file, _directory_attribute)
          val sentinel = _write_raw(file.resolve("material"), Array[Byte](9))
          When("factory and all custody operations encounter a directory instead of a regular file")
          val outcomes = Vector(PrivateFileOAuthCredentialStore.createC(file, _connection, _owner, client),
            store.loadC(), store.saveC(_exchange(client)), store.forgetC())
          val retained = Files.readAllBytes(sentinel).toVector
          Then("the selected directory and domain sentinel remain intact")
          outcomes.foreach(_same_failure(_, _configuration_failure))
          retained shouldBe Vector[Byte](9)
        }
      }

      "refuses both a regular-target symlink and a dangling-target symlink on every operation" in {
        Given("R1,R2/E2 docs/spec/oauth-credential-custody.md: selected symlinks to an existing sentinel and an absent file")
        Vector(false, true).foreach { dangling =>
          _with_root { root =>
            val file = root.resolve("selected.json")
            val client = _client()
            val store = _store(file, client)
            val destination = root.resolve("sentinel")
            if (!dangling) _write_raw(destination, Array[Byte](11))
            Files.createSymbolicLink(file, destination)
            When("factory, load, save and forget interpret the exact selected symlink")
            val outcomes = Vector(PrivateFileOAuthCredentialStore.createC(file, _connection, _owner, client),
              store.loadC(), store.saveC(_exchange(client)), store.forgetC())
            val retained = Files.isSymbolicLink(file)
            val sentinel = if (dangling) None else Some(Files.readAllBytes(destination).toVector)
            Then("the symlink is never followed, overwritten or removed and its destination is unchanged")
            outcomes.foreach(_same_failure(_, _configuration_failure))
            retained shouldBe true
            sentinel shouldBe (if (dangling) None else Some(Vector[Byte](11)))
          }
        }
      }

      "rejects multiply linked credentials and retains both names and their bytes" in {
        Given("R1,R2/E2 docs/spec/oauth-credential-custody.md: one actual saved credential with a second Unix hard link")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val client = _client()
          val credential = _exchange(client)
          val store = _store(file, client)
          store.saveC(credential).TAKE
          val alias = Files.createLink(root.resolve("hardlink"), file)
          val before = Files.readAllBytes(file).toVector
          When("factory and all custody operations encounter the shared inode")
          val outcomes = Vector(PrivateFileOAuthCredentialStore.createC(file, _connection, _owner, client),
            store.loadC(), store.saveC(credential), store.forgetC())
          val selected = Files.readAllBytes(file).toVector
          val linked = Files.readAllBytes(alias).toVector
          val names = _names(root)
          Then("hard-link sharing is a fixed configuration refusal without mutation through either name")
          outcomes.foreach(_same_failure(_, _configuration_failure))
          selected shouldBe before
          linked shouldBe before
          names shouldBe Set("selected.json", "hardlink")
        }
      }

      "rejects null and foreign-client grants before any file or staging write" in {
        Given("R2/E2 docs/spec/oauth-credential-custody.md: a store and a grant owned by a separate equivalent client")
        _with_root { root =>
          val client = _client()
          val store = _store(root.resolve("selected.json"), client)
          val foreign = _exchange(_client())
          When("null and foreign-instance credentials are offered for saving")
          val outcomes = Vector(store.saveC(null), store.saveC(foreign))
          val names = _names(root)
          Then("neither input receives persistence authority even when client configuration is equivalent")
          outcomes.foreach(_same_failure(_, _record_failure))
          names shouldBe empty
        }
      }

      "reports ordinary filesystem IO failure as unavailable without submitted paths or exception messages" in {
        Given("R2/E2 docs/spec/oauth-credential-custody.md: an overlong literal filename rejected by the actual filesystem")
        _with_root { root =>
          val file = root.resolve("synthetic-path-marker-" + ("x" * 300))
          When("the selected file cannot be inspected by the host filesystem")
          val outcome = PrivateFileOAuthCredentialStore.createC(file, _connection, _owner, _client())
          Then("IO failure is not reported as absence or leaked as a native exception")
          _same_failure(outcome, _unavailable_failure)
          _disclosures(_failure(outcome)).foreach { output =>
            output should not include "synthetic-path-marker"
            output should not include root.toString
          }
        }
      }
    }
  }

  "E3 OAuth credential custody restoration" should afterWord(
    "in spec:oauth-credential-custody, example:E3, rules:R3,R4, phase:PHASE-3") {
    "native grants across independent clients" which {
      "saves an exchanged grant and restores it into a separately constructed equivalent client" in {
        Given("R3,R4/E3 docs/spec/oauth-credential-custody.md: two clients with equivalent identities and independently selected stores")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val first = _client()
          val second = _client(secret = Some("rotated-client-secret-marker"))
          val credential = _exchange(first)
          val writer = _store(file, first)
          val reader = _store(file, second)
          When("the native grant is saved and then loaded by the second client")
          val saved = writer.saveC(credential)
          val restored = reader.loadC().TAKE.get
          val contents = parser.parse(Files.readString(file, StandardCharsets.UTF_8)).toOption.get
          val permissions = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS)
          val names = _names(root)
          Then("the exact grant and identity bindings survive while client-secret rotation remains independent")
          saved.isSuccess shouldBe true
          restored.accessToken shouldBe _access
          restored.refreshToken shouldBe Some(_refresh)
          restored.grantedScopes shouldBe _scopes
          restored.expiresAt shouldBe _observed.plusSeconds(3600)
          restored._client should be theSameInstanceAs second
          contents shouldBe _record(first)
          contents.asObject.get.keys.toSet shouldBe Set("schema", "connectionId", "ownerId", "client", "accessToken",
            "refreshToken", "grantedScopes", "expiresAt")
          contents.hcursor.downField("client").focus.get.asObject.get.keys.toSet shouldBe Set("providerId",
            "authorizationEndpoint", "tokenEndpoint", "clientId", "redirectUri", "requestedScopes")
          contents.noSpaces should not include "clientSecret"
          contents.noSpaces should not include "synthetic-client-secret-marker"
          permissions shouldBe PosixFilePermissions.fromString("rw-------")
          names shouldBe Set("selected.json")
        }
      }

      "persists refresh rotation and uses the newest restored token in an actual native refresh" in {
        Given("R3,R4/E3 docs/spec/oauth-credential-custody.md: an exchanged grant and a provider refresh rotating both tokens")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val first = _client()
          val second = _client()
          val store = _store(file, first)
          val initial = _exchange(first)
          store.saveC(initial).TAKE
          val rotation = new Harness(_ => _response("rotated-access-marker", Some("newest-refresh-marker"), Some(Vector("scope:b"))))
          When("the rotated native grant is saved, independently loaded and refreshed")
          val rotated = rotation.evaluate(first.refresh(initial, _observed.plusSeconds(10))).TAKE
          store.saveC(rotated).TAKE
          val restored = _store(file, second).loadC().TAKE.get
          val next = new Harness(_ => _response("next-access-marker", None, None))
          val refreshed = next.evaluate(second.refresh(restored, _observed.plusSeconds(20))).TAKE
          val form = _decode(next.requests.head.text.get)
          val contents = Files.readString(file, StandardCharsets.UTF_8)
          val names = _names(root)
          Then("the next POST uses only the latest refresh material and the narrowed grant remains authoritative")
          restored.accessToken shouldBe "rotated-access-marker"
          restored.refreshToken shouldBe Some("newest-refresh-marker")
          restored.grantedScopes shouldBe Vector("scope:b")
          form("grant_type") shouldBe "refresh_token"
          form("refresh_token") shouldBe "newest-refresh-marker"
          next.requests.size shouldBe 1
          refreshed.refreshToken shouldBe Some("newest-refresh-marker")
          refreshed.grantedScopes shouldBe Vector("scope:b")
          contents should include ("newest-refresh-marker")
          contents should not include _refresh
          names shouldBe Set("selected.json")
        }
      }

      "loads expired grants without consulting a clock or exchanging them" in {
        Given("R3/E3 docs/spec/oauth-credential-custody.md: an actual exchange observed in the distant past")
        _with_root { root =>
          val client = _client()
          val credential = _exchange(client, observed = Instant.parse("2000-01-01T00:00:00Z"))
          val store = _store(root.resolve("selected.json"), client)
          When("the old grant is saved and loaded by custody alone")
          store.saveC(credential).TAKE
          val restored = store.loadC().TAKE.get
          Then("custody retains the precise expiry and leaves refresh policy to the native runtime")
          restored.expiresAt shouldBe Instant.parse("2000-01-01T01:00:00Z")
          restored.canRefresh shouldBe true
        }
      }

      "omits absent refresh material rather than manufacturing a refresh capability" in {
        Given("R3/E3 docs/spec/oauth-credential-custody.md: a native exchange without refresh material")
        _with_root { root =>
          val client = _client()
          val credential = _exchange(client, refresh = None)
          val file = root.resolve("selected.json")
          val store = _store(file, client)
          When("the grant is saved and restored, and its refresh request is interpreted")
          store.saveC(credential).TAKE
          val restored = store.loadC().TAKE.get
          val contents = parser.parse(Files.readString(file, StandardCharsets.UTF_8)).toOption.get
          val runtime = new Harness(_ => throw new AssertionError("Absent refresh material must not request HTTP"))
          val outcome = runtime.evaluate(client.refresh(restored, _observed))
          Then("the record omits refreshToken and the native protocol requires authorization without HTTP")
          contents.asObject.get.contains("refreshToken") shouldBe false
          restored.refreshToken shouldBe None
          restored.canRefresh shouldBe false
          _same_failure(outcome, Consequence.securityPermissionDenied("OAuth connection requires authorization"))
          runtime.requests shouldBe empty
        }
      }

      "round-trips at least one hundred generated native grants with entered scope order and zero discards" in {
        Given("R3,R4/E3 docs/spec/oauth-credential-custody.md: generated RFC6750 access tokens and optional printable refresh tokens")
        _with_root { root =>
          val first = _client()
          val second = _client()
          val file = root.resolve("selected.json")
          val writer = _store(file, first)
          val reader = _store(file, second)
          val generated = for {
            size <- Gen.choose(1, 64)
            chars <- Gen.listOfN(size, Gen.oneOf("abcXYZ019._~+/-".toVector))
            padding <- Gen.choose(0, 3)
            refresh <- Gen.option(Gen.listOfN(24, Gen.oneOf("abcXYZ019!#$%&'()*+,-./:;<=>?@[]^_`{|}~".toVector)).map(_.mkString))
            scopes <- Gen.oneOf(Vector("scope:a"), Vector("scope:b"), Vector("scope:b", "scope:a"))
            seconds <- Gen.choose(0, 86400)
          } yield (chars.mkString + ("=" * padding), refresh, scopes, _observed.plusSeconds(seconds.toLong))
          When("each generated native grant crosses the real private filesystem into the equivalent client")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(generated) {
            case (access, refresh, scopes, observed) =>
              val credential = _exchange(first, access, refresh, Some(scopes), observed)
              val saved = writer.saveC(credential)
              reader.loadC().toOption.flatten.exists { restored =>
                saved.isSuccess && restored.accessToken == access && restored.refreshToken == refresh &&
                  restored.grantedScopes == scopes && restored.expiresAt == observed.plusSeconds(3600) &&
                  (restored._client eq second) && _names(root) == Set("selected.json")
              }
          })
          Then("every generated round trip retains exact grant meaning without a discarded input")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }
    }
  }

  "E4 OAuth credential custody strict records" should afterWord(
    "in spec:oauth-credential-custody, example:E4, rules:R3, phase:PHASE-3") {
    "closed bounded UTF8 records" which {
      "accepts exactly 131072 bytes and refuses an extra byte, empty data and malformed UTF8" in {
        Given("R3/E4 docs/spec/oauth-credential-custody.md: exact-bound JSON padding, over-bound and malformed byte sequences")
        _with_root { root =>
          val client = _client()
          val file = root.resolve("selected.json")
          val store = _store(file, client)
          val text = _record(client).noSpaces
          val full = (text + (" " * (131072 - text.getBytes(StandardCharsets.UTF_8).length))).getBytes(StandardCharsets.UTF_8)
          _write_raw(file, full)
          When("the exact bounded record and invalid byte sequences are loaded")
          val accepted = store.loadC()
          val rejected = Vector(full :+ ' '.toByte, Array.emptyByteArray,
            Array(0xc3.toByte, 0x28.toByte), Array(0xef.toByte, 0xbb.toByte, 0xbf.toByte) ++ text.getBytes(StandardCharsets.UTF_8))
            .map { bytes => _write_raw(file, bytes); store.loadC() }
          Then("the byte cap is inclusive and neither truncation nor decoder replacement admits invalid data")
          accepted.TAKE.get.accessToken shouldBe _access
          rejected.foreach(_same_failure(_, _record_failure))
        }
      }

      "refuses duplicate keys at both levels instead of applying last-key-wins semantics" in {
        Given("R3/E4 docs/spec/oauth-credential-custody.md: duplicate root and nested client fields")
        _with_root { root =>
          val client = _client()
          val text = _record(client).noSpaces
          val rootduplicate = "{\"accessToken\":\"duplicate-marker\"," + text.drop(1)
          val clientduplicate = text.replace("\"client\":{", "\"client\":{\"clientId\":\"duplicate-marker\",")
          When("each duplicate-key record is offered for load, replacement and forget")
          val observations = Vector(rootduplicate, clientduplicate).map { value =>
            _refuse_record(root, client, value.getBytes(StandardCharsets.UTF_8))
          }
          Then("every operation refuses the record and retains its original bytes")
          observations.foreach { case (outcomes, before, after) =>
            outcomes.foreach(_same_failure(_, _record_failure))
            after shouldBe before
          }
        }
      }

      "rejects unknown, missing, null and malformed semantic fields without replacing or forgetting the record" in {
        Given("R3/E4 docs/spec/oauth-credential-custody.md: malformed closed record shapes and strict token, scope and expiry values")
        _with_root { root =>
          val client = _client()
          val valid = _record(client)
          val fields = valid.asObject.get
          val missing = fields.keys.filterNot(_ == "refreshToken").map(key => valid.mapObject(_.remove(key))).toVector
          val nulls = fields.keys.map(key => valid.mapObject(_.add(key, Json.Null))).toVector
          val access = Vector("", "has space", "line\nmarker", "é", "=prefix", "middle=padding", "a" * 16385)
            .map(value => valid.mapObject(_.add("accessToken", Json.fromString(value))))
          val refresh = Vector("", "has space", "line\nmarker", "é", "r" * 16385)
            .map(value => valid.mapObject(_.add("refreshToken", Json.fromString(value))))
          val scopes = Vector(Json.arr(), Json.arr(Json.fromString("scope:a"), Json.fromString("scope:a")),
            Json.arr(Json.fromString("unrequested")), Json.arr(Json.Null), Json.fromString("scope:a"),
            Json.arr(Json.fromString("has space")), Json.arr(Json.fromString("quote\"")),
            Json.arr(Json.fromString("slash\\")), Json.arr(Json.fromString("é")))
            .map(value => valid.mapObject(_.add("grantedScopes", value)))
          val expiry = Vector(Json.fromString("2026-10-21T01:00:00+00:00"),
            Json.fromString("2026-10-21T01:00:00.000Z"), Json.fromString("not-an-instant"), Json.fromInt(1), Json.True)
            .map(value => valid.mapObject(_.add("expiresAt", value)))
          val shapes = Vector(Json.Null, Json.arr(valid), Json.obj(),
            valid.mapObject(_.add("schema", Json.fromString("cncf.oauth-credential/2"))),
            valid.mapObject(_.add("unexpected", Json.fromString("untrusted-marker"))),
            valid.mapObject(_.add("clientSecret", Json.fromString("synthetic-client-secret-marker"))),
            valid.mapObject(_.add("client", client._connection_binding.mapObject(_.add("unexpected", Json.True)))),
            valid.mapObject(_.add("client", client._connection_binding.mapObject(_.remove("clientId")))))
          val malformed = (missing ++ nulls ++ access ++ refresh ++ scopes ++ expiry ++ shapes)
            .map(_.noSpaces.getBytes(StandardCharsets.UTF_8)) ++ Vector("{".getBytes(StandardCharsets.UTF_8))
          When("load, save and forget interpret each malformed semantic record")
          val observations = malformed.map(_refuse_record(root, client, _))
          Then("the fixed record failure preserves every submitted record rather than repairing or discarding it")
          observations.foreach { case (outcomes, before, after) =>
            outcomes.foreach(_same_failure(_, _record_failure))
            after shouldBe before
          }
        }
      }

      "uses existing validators to reject null restoration inputs and to bind a restored grant to this exact client" in {
        Given("R3/E4 docs/spec/oauth-credential-custody.md: private restoration with null Option, null content and null expiry")
        val client = _client()
        When("the client restores invalid grants and one valid grant")
        val outcomes = Vector(client._restore_credential_c(null, None, _scopes, _observed),
          client._restore_credential_c(_access, null, _scopes, _observed),
          client._restore_credential_c(_access, Some(null), _scopes, _observed),
          client._restore_credential_c(_access, None, null, _observed),
          client._restore_credential_c(_access, None, Vector("unrequested"), _observed),
          client._restore_credential_c(_access, None, _scopes, null))
        val restored = client._restore_credential_c(_access, None, _scopes, _observed).TAKE
        Then("malformed saved data uses the custody failure and valid restoration retains the exact configured client")
        outcomes.foreach(_same_failure(_, _record_failure))
        restored._client should be theSameInstanceAs client
        restored.expiresAt shouldBe _observed
      }
    }
  }

  "E5 OAuth credential custody identity binding" should afterWord(
    "in spec:oauth-credential-custody, example:E5, rules:R3,R4, phase:PHASE-3") {
    "explicit reauthorization on changed identity" which {
      "refuses each changed provider, endpoint, client, redirect, scope order, owner and connection without replacing the record" in {
        Given("R3,R4/E5 docs/spec/oauth-credential-custody.md: one native grant bound to its exact saved OAuth identity")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val original = _client()
          _store(file, original).saveC(_exchange(original)).TAKE
          val before = Files.readAllBytes(file).toVector
          val variants = Vector(
            _client(provider = OAuthProvider.createC("changed-provider", _authorization, _token).TAKE),
            _client(provider = OAuthProvider.createC("google-drive", _authorization + "-changed", _token).TAKE),
            _client(provider = OAuthProvider.createC("google-drive", _authorization, _token + "-changed").TAKE),
            _client(clientid = "changed-client"), _client(redirect = "http://127.0.0.1:8124/oauth/callback"),
            _client(scopes = _scopes.reverse), _client(scopes = Vector("scope:a")))
          When("each changed current configuration attempts load, save and forget of the selected file")
          val outcomes = variants.flatMap { client =>
            val store = _store(file, client)
            Vector(store.loadC(), store.saveC(_exchange(client)), store.forgetC())
          } ++ Vector((_connection + "-changed", _owner), (_connection, _owner + "-changed")).flatMap {
            case (connection, owner) =>
              val store = PrivateFileOAuthCredentialStore.createC(file, connection, owner, original).TAKE
              Vector(store.loadC(), store.saveC(_exchange(original)), store.forgetC())
          }
          val after = Files.readAllBytes(file).toVector
          val names = _names(root)
          Then("saved configuration never becomes a fallback for a changed current configuration and the record is retained")
          outcomes.foreach(_same_failure(_, _record_failure))
          after shouldBe before
          names shouldBe Set("selected.json")
        }
      }
    }
  }

  "E6 OAuth credential custody lifecycle" should afterWord(
    "in spec:oauth-credential-custody, example:E6, rules:R5,R6, phase:PHASE-3") {
    "atomic replacement and exact local forget" which {
      "retains only the current atomic target and forgets that file without deleting domain data or other credentials" in {
        Given("R5,R6/E6 docs/spec/oauth-credential-custody.md: an owned credential beside unrelated domain and credential files")
        _with_root { root =>
          val client = _client()
          val file = root.resolve("selected.json")
          val store = _store(file, client)
          val sentinel = _write_raw(root.resolve("Material"), "domain-sentinel".getBytes(StandardCharsets.UTF_8))
          val other = _write_raw(root.resolve("other-credential.json"), _record(client).noSpaces.getBytes(StandardCharsets.UTF_8))
          store.saveC(_exchange(client)).TAKE
          When("a second actual native grant replaces the credential and then the selected credential is forgotten twice")
          store.saveC(_exchange(client, access = "replacement-access-marker")).TAKE
          val savednames = _names(root)
          val savedmode = Files.getPosixFilePermissions(file)
          val saved = store.loadC().TAKE.get
          val first = store.forgetC()
          val second = store.forgetC()
          val after = store.loadC()
          val domain = Files.readString(sentinel, StandardCharsets.UTF_8)
          val unrelated = Files.readAllBytes(other).toVector
          val remaining = _names(root)
          Then("exclusive private staging installs a 0600 target without residue and forget removes only the selected file")
          saved.accessToken shouldBe "replacement-access-marker"
          savedmode shouldBe PosixFilePermissions.fromString("rw-------")
          savednames shouldBe Set("selected.json", "Material", "other-credential.json")
          first.isSuccess shouldBe true
          second.isSuccess shouldBe true
          after.TAKE shouldBe None
          domain shouldBe "domain-sentinel"
          unrelated shouldBe _record(client).noSpaces.getBytes(StandardCharsets.UTF_8).toVector
          remaining shouldBe Set("Material", "other-credential.json")
        }
      }

      "reports uncertainty after a real successful rename when directory durability confirmation fails" in {
        Given("R5,R6/E6 docs/spec/oauth-credential-custody.md: an old grant and a private durability capability that fails after rename")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val client = _client()
          val store = _store(file, client)
          store.saveC(_exchange(client)).TAKE
          var confirmed = Vector.empty[(Path, Set[String], String)]
          val durability = new OAuthCredentialDurability {
            def forceDirectory(directory: Path): Unit = {
              confirmed = confirmed :+ ((directory, _names(directory), PosixFilePermissions.toString(Files.getPosixFilePermissions(file))))
              throw new IOException("synthetic-path-marker synthetic-access-marker synthetic-refresh-marker")
            }
          }
          val uncertain = new PrivateFileOAuthCredentialStore(file, _connection, _owner, client, durability)
          val replacement = _exchange(client, access = "renamed-access-marker", refresh = Some("renamed-refresh-marker"))
          When("the actual staging file is forced and renamed, followed by a failing parent-directory sync")
          val outcome = uncertain.saveC(replacement)
          val restored = store.loadC().TAKE.get
          val bytes = Files.readString(file, StandardCharsets.UTF_8)
          val names = _names(root)
          Then("the outcome is explicitly uncertain while readback sees the new tokens and the renamed private staging mode")
          _same_failure(outcome, _uncertain_failure)
          restored.accessToken shouldBe "renamed-access-marker"
          restored.refreshToken shouldBe Some("renamed-refresh-marker")
          confirmed shouldBe Vector((root, Set("selected.json"), "rw-------"))
          bytes should include ("renamed-refresh-marker")
          bytes should not include _refresh
          names shouldBe Set("selected.json")
          _failure(outcome).getException shouldBe None
        }
      }

      "reports uncertainty after actual deletion and treats the subsequent admitted absence as idempotent" in {
        Given("R5,R6/E6 docs/spec/oauth-credential-custody.md: a saved credential and a failing directory durability capability")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val client = _client()
          _store(file, client).saveC(_exchange(client)).TAKE
          val sentinel = _write_raw(root.resolve("Material"), Array[Byte](12))
          var calls = 0
          var present = true
          val durability = new OAuthCredentialDurability {
            def forceDirectory(directory: Path): Unit = {
              calls += 1
              present = Files.exists(file, LinkOption.NOFOLLOW_LINKS)
              throw new IOException("synthetic-path-marker synthetic-refresh-marker")
            }
          }
          val store = new PrivateFileOAuthCredentialStore(file, _connection, _owner, client, durability)
          When("forget deletes the exact target before its parent sync fails, and absence is then read and forgotten")
          val outcome = store.forgetC()
          val readback = store.loadC()
          val repeated = store.forgetC()
          val material = Files.readAllBytes(sentinel).toVector
          val names = _names(root)
          Then("uncertainty never claims deletion failed and no unrelated data or repeated sync is requested")
          _same_failure(outcome, _uncertain_failure)
          present shouldBe false
          calls shouldBe 1
          readback.TAKE shouldBe None
          repeated.isSuccess shouldBe true
          material shouldBe Vector[Byte](12)
          names shouldBe Set("Material")
        }
      }

      "keeps filesystem and binding admission active even with the private durability capability" in {
        Given("R1,R3,R5/E6 docs/spec/oauth-credential-custody.md: the same private host capability with unsafe parent or foreign owner binding")
        _with_root { root =>
          val file = root.resolve("selected.json")
          val client = _client()
          val credential = _exchange(client)
          _store(file, client).saveC(credential).TAKE
          val before = Files.readAllBytes(file).toVector
          var calls = 0
          val durability = new OAuthCredentialDurability {
            def forceDirectory(directory: Path): Unit = { calls += 1 }
          }
          val foreign = new PrivateFileOAuthCredentialStore(file, _connection, _owner + "-changed", client, durability)
          val store = new PrivateFileOAuthCredentialStore(file, _connection, _owner, client, durability)
          When("binding mismatch and then unsafe parent permissions request mutations through that capability")
          val mismatch = Vector(foreign.saveC(credential), foreign.forgetC())
          Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-x---"))
          val unsafe = Vector(store.loadC(), store.saveC(credential), store.forgetC())
          Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
          val after = Files.readAllBytes(file).toVector
          Then("the durability seam cannot bypass either admission boundary or mutate the stored record")
          mismatch.foreach(_same_failure(_, _record_failure))
          unsafe.foreach(_same_failure(_, _configuration_failure))
          calls shouldBe 0
          after shouldBe before
        }
      }
    }
  }

  "E7 OAuth credential custody diagnostic safety" should afterWord(
    "in spec:oauth-credential-custody, example:E7, rules:R7, phase:PHASE-3") {
    "redacted displays and actual structured projections" which {
      "omits paths, saved tokens, invalid records and exception markers from standard, full and log disclosures" in {
        Given("R7/E7 docs/spec/oauth-credential-custody.md: actual unsafe configuration, malformed record, IO and uncertain-update failures")
        _with_root { root =>
          val client = _client()
          val file = root.resolve("synthetic-path-marker.json")
          val store = _store(file, client)
          val credential = _exchange(client)
          _write_raw(file, "synthetic-record-marker synthetic-access-marker synthetic-refresh-marker".getBytes(StandardCharsets.UTF_8))
          When("real custody failures are projected through native HTTP standard, full and log disclosure")
          val malformed = store.loadC()
          Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))
          val unsafe = store.loadC()
          Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
          _write_raw(file, _record(client).noSpaces.getBytes(StandardCharsets.UTF_8))
          val durability = new OAuthCredentialDurability {
            def forceDirectory(directory: Path): Unit = throw new IOException("synthetic-exception-marker")
          }
          val uncertain = new PrivateFileOAuthCredentialStore(file, _connection, _owner, client, durability).saveC(credential)
          val unavailable = PrivateFileOAuthCredentialStore.createC(root.resolve("synthetic-path-marker" + ("x" * 300)),
            _connection, _owner, client)
          val conclusions = Vector(malformed, unsafe, uncertain, unavailable).map(_failure)
          val projections = conclusions.flatMap(_disclosures)
          val displays = Vector(store.toString, client.toString, credential.toString)
          Then("fixed structured categories survive while every sensitive value and native exception remains undisclosed")
          _same_failure(malformed, _record_failure)
          _same_failure(unsafe, _configuration_failure)
          _same_failure(uncertain, _uncertain_failure)
          _same_failure(unavailable, _unavailable_failure)
          (projections ++ displays).foreach { output =>
            Vector(root.toString, "synthetic-path-marker", "synthetic-record-marker", _access, _refresh,
              "synthetic-exception-marker", "synthetic-client-secret-marker", _owner, _connection)
              .foreach(marker => output should not include marker)
          }
          conclusions.foreach(_.getException shouldBe None)
          conclusions.foreach { conclusion =>
            _disclosures(conclusion).foreach { output =>
              val json = parser.parse(output).toOption.get
              json.hcursor.downField("status").focus shouldBe Some(parser.parse(RecordEncoder.json(conclusion.status.toRecord)).toOption.get)
              json.hcursor.downField("observation").downField("taxonomy").focus shouldBe
                Some(parser.parse(RecordEncoder.json(conclusion.observation.taxonomy.toRecord)).toOption.get)
            }
          }
          store.toString shouldBe "PrivateFileOAuthCredentialStore(<redacted>)"
        }
      }
    }
  }

  private def _same_failure(actual: Consequence[?], expected: Consequence[?]): Unit = {
    val conclusion = _failure(actual)
    val reference = _failure(expected)
    conclusion.status shouldBe reference.status
    conclusion.observation.taxonomy shouldBe reference.observation.taxonomy
    conclusion.cause.getMessage shouldBe reference.cause.getMessage
  }
}

/** Synthetic grants use ordinary component admission and the native UnitOfWork interpreter. */
object OAuthCredentialStoreSpec {
  private val _authorization = "https://accounts.google.com/o/oauth2/v2/auth"
  private val _token = "https://oauth2.googleapis.com/token"
  private val _connection = "synthetic-drive-connection"
  private val _owner = "synthetic-ingress-owner"
  private val _access = "synthetic-access-marker"
  private val _refresh = "synthetic-refresh-marker"
  private val _scopes = Vector("scope:a", "scope:b")
  private val _observed = Instant.parse("2026-10-21T00:00:00Z")
  private val _directory_attribute = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
  private def _configuration_failure: Consequence[Nothing] =
    Consequence.configurationInvalid("OAuth credential storage configuration is invalid")
  private def _record_failure: Consequence[Nothing] =
    Consequence.resourceInvalid("OAuth credential storage record is invalid")
  private def _unavailable_failure: Consequence[Nothing] =
    Consequence.serviceUnavailable("OAuth credential storage is unavailable")
  private def _uncertain_failure: Consequence[Nothing] =
    Consequence.stateConflict("OAuth credential storage update outcome is uncertain")

  private final case class Request(text: Option[String])

  private final class Harness(answer: Request => HttpResponse) {
    private var _requests = Vector.empty[Request]
    private val _context: ExecutionContext = {
      val owner = ComponentId("org.example.OAuthCustodyOwner")
      val admission = HttpExecutionAdmission.fromRuntimeC("https://oauth2.googleapis.com/",
        HttpExecutionLimits(1000, 1000, 1048576),
        Map(owner -> HttpExecutionGrant(Vector("https://oauth2.googleapis.com"), Set(HttpExecutionMethod.Post)))).TAKE
      val base = ExecutionContext.create()
      val root = ScopeContext.Instance(base.cncfCore.scope.core.copy(httpExecutionAdmissionOption = Some(admission)))
      val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
      component.withScopeContext(root)
      base.withScope(component.scopeContext)
    }
    private val _driver = new HttpDriver {
      def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        throw new AssertionError("OAuth custody fixture uses POST only")
      def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
        val request = Request(body)
        _requests = _requests :+ request
        answer(request)
      }
      def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        throw new AssertionError("OAuth custody fixture uses POST only")
    }
    def requests: Vector[Request] = _requests
    def evaluate[A](program: ExecUowM[A]): Consequence[A] = {
      val uow = new UnitOfWork(_context) {
        override def httpDriver: HttpDriver = _driver
      }
      new UnitOfWorkInterpreter(uow).run(program)
    }
  }

  private def _client(provider: OAuthProvider = OAuthProvider.googleDrive, clientid: String = "synthetic-client",
    secret: Option[String] = Some("synthetic-client-secret-marker"), redirect: String = "http://127.0.0.1:8123/oauth/callback",
    scopes: Vector[String] = _scopes): OAuthClient = OAuthClient.createC(provider, clientid, secret, redirect, scopes).TAKE
  private def _store(file: Path, client: OAuthClient): PrivateFileOAuthCredentialStore =
    PrivateFileOAuthCredentialStore.createC(file, _connection, _owner, client).TAKE
  private def _exchange(client: OAuthClient, access: String = _access, refresh: Option[String] = Some(_refresh),
    scope: Option[Vector[String]] = None, observed: Instant = _observed): OAuthCredential = {
    val runtime = new Harness(_ => _response(access, refresh, scope))
    val state = "synthetic-state-0123456789abcdefghijklmnopqrstu"
    val request = client.beginC(state, "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk").TAKE
    runtime.evaluate(client.complete(request, state, "synthetic-code-marker", observed)).TAKE
  }
  private def _response(access: String, refresh: Option[String], scopes: Option[Vector[String]]): HttpResponse = {
    val json = Json.obj((Vector("access_token" -> Json.fromString(access), "token_type" -> Json.fromString("Bearer"),
      "expires_in" -> Json.fromInt(3600)) ++ refresh.map(value => "refresh_token" -> Json.fromString(value)) ++
      scopes.map(value => "scope" -> Json.fromString(value.mkString(" "))))*)
    HttpResponse.Text(HttpStatus.Ok, ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
      Bag.text(json.noSpaces, StandardCharsets.UTF_8), Record.empty)
  }
  private def _record(client: OAuthClient): Json = Json.obj(
    "schema" -> Json.fromString("cncf.oauth-credential/1"), "connectionId" -> Json.fromString(_connection),
    "ownerId" -> Json.fromString(_owner), "client" -> client._connection_binding,
    "accessToken" -> Json.fromString(_access), "refreshToken" -> Json.fromString(_refresh),
    "grantedScopes" -> Json.fromValues(_scopes.map(Json.fromString)),
    "expiresAt" -> Json.fromString(_observed.plusSeconds(3600).toString))
  private def _refuse_record(root: Path, client: OAuthClient,
    bytes: Array[Byte]): (Vector[Consequence[?]], Vector[Byte], Vector[Byte]) = {
    val file = root.resolve("selected.json")
    val store = _store(file, client)
    _write_raw(file, bytes)
    val before = Files.readAllBytes(file).toVector
    val outcomes = Vector(store.loadC(), store.saveC(_exchange(client)), store.forgetC())
    (outcomes, before, Files.readAllBytes(file).toVector)
  }
  private def _write_raw(file: Path, bytes: Array[Byte]): Path = {
    Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
    file
  }
  private def _with_root[A](body: Path => A): A = {
    val base = Files.createDirectories(Paths.get("target", "oauth-credential-spec")).toRealPath()
    val root = Files.createTempDirectory(base, "custody-", _directory_attribute).toRealPath()
    try body(root) finally {
      Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
      Using.resource(Files.walk(root)) { stream =>
        stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => Files.deleteIfExists(path))
      }
    }
  }
  private def _names(directory: Path): Set[String] =
    Using.resource(Files.list(directory))(_.iterator().asScala.map(_.getFileName.toString).toSet)
  private def _decode(form: String): Map[String, String] = form.split("&", -1).map { part =>
    val pair = part.split("=", 2)
    URLDecoder.decode(pair(0), StandardCharsets.UTF_8) -> URLDecoder.decode(pair(1), StandardCharsets.UTF_8)
  }.toMap
  private def _failure[A](outcome: Consequence[A]): Conclusion = outcome match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => throw new AssertionError("Expected a structured custody failure")
  }
  private def _disclosures(conclusion: Conclusion): Vector[String] = Vector(
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Standard),
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Full),
    ConclusionDisclosure.log(conclusion)).map(RecordEncoder.json)
}
