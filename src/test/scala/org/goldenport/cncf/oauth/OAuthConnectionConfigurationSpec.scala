package org.goldenport.cncf.oauth

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardOpenOption}
import java.nio.file.attribute.PosixFilePermissions
import scala.jdk.CollectionConverters.*
import scala.util.Using
import io.circe.{Json, parser}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.config.{RuntimeSecretResolver, SecretReference}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.observability.ConclusionDisclosure
import org.goldenport.record.io.RecordEncoder
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
final class OAuthConnectionConfigurationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OAuthConnectionConfigurationSpec.*

  "Private runtime secret resolution" should {
    "explicit no-follow host admission" which {
      "reads copied material only when the runtime resolves an explicit private file" in {
        Given("R1 docs/spec/oauth-connection-configuration.md: a process-owned 0700 directory and 0600 secret")
        _with_root { root =>
          val file = _write(root.resolve("private.json"), _bytes("synthetic-secret-marker"))
          val reference = _reference(file.toUri.toString)
          val resolver = RuntimeSecretResolver.privateFile
          val principal = root.getFileSystem.getUserPrincipalLookupService
            .lookupPrincipalByName(ProcessHandle.current().info().user().get())
          When("the private runtime resolver resolves the selected reference")
          val material = resolver.resolveSecret(reference).TAKE
          val first = material._copy_bytes
          first(0) = 'X'.toByte
          val second = material._copy_bytes
          val names = _names(root)
          val owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS)
          Then("independent copies preserve the material and the reference and material displays remain redacted")
          new String(second, StandardCharsets.UTF_8) shouldBe "synthetic-secret-marker"
          owner shouldBe principal
          names shouldBe Set("private.json")
          reference.toString shouldBe "SecretReference(<redacted>)"
          material.toString shouldBe "SecretMaterial(<redacted>)"
        }
      }

      "requires exact file and parent modes without repairing either" in {
        Given("R1: unsafe ordinary and special mode bits on an existing private file and parent")
        _with_root { root =>
          val file = _write(root.resolve("private.json"), _bytes("synthetic-secret-marker"))
          val reference = _reference(file.toUri.toString)
          When("resolution encounters each unsafe mode, resetting 0600 before each file case")
          val files = Vector(420, 256, 448, 388, 0, 2432).map { mode =>
            Files.setPosixFilePermissions(file, _file_permissions)
            Files.setAttribute(file, "unix:mode", Integer.valueOf(mode), LinkOption.NOFOLLOW_LINKS)
            val outcome = RuntimeSecretResolver.privateFile.resolveSecret(reference)
            (outcome, _mode(file), mode)
          }
          Files.setPosixFilePermissions(file, _file_permissions)
          val parents = Vector(493, 488, 511, 449, 320, 960).map { mode =>
            Files.setPosixFilePermissions(root, _directory_permissions)
            Files.setAttribute(root, "unix:mode", Integer.valueOf(mode), LinkOption.NOFOLLOW_LINKS)
            val outcome = RuntimeSecretResolver.privateFile.resolveSecret(reference)
            (outcome, _mode(root), mode)
          }
          Files.setPosixFilePermissions(root, _directory_permissions)
          val retained = Files.readAllBytes(file).toVector
          Then("every mode is refused with fixed configuration failure and remains unchanged")
          (files ++ parents).foreach { case (outcome, actual, expected) =>
            _same_failure(outcome, _secret_invalid)
            actual shouldBe expected
          }
          retained shouldBe _bytes("synthetic-secret-marker").toVector
        }
      }

      "rejects missing, empty and oversized secrets while accepting both inclusive byte limits" in {
        Given("R1: absent material and private files at the read bounds")
        _with_root { root =>
          val sizes = Vector(0, 1, 131072, 131073, 150000)
          val files = sizes.map(size => _write(root.resolve(s"size-$size"), Array.fill[Byte](size)(65)))
          When("the runtime resolves the bounded files and an absent path")
          val outcomes = files.map(file => RuntimeSecretResolver.privateFile.resolveSecret(_reference(file.toUri.toString)))
          val missing = RuntimeSecretResolver.privateFile.resolveSecret(_reference(root.resolve("missing").toUri.toString))
          val names = _names(root)
          Then("only 1 through 131072 bytes succeed and absence creates no file")
          outcomes.zip(sizes).foreach { case (outcome, size) =>
            if (size >= 1 && size <= 131072) outcome.TAKE._copy_bytes.length shouldBe size
            else _same_failure(outcome, _secret_invalid)
          }
          _same_failure(missing, _secret_invalid)
          names shouldBe sizes.map(size => s"size-$size").toSet
        }
      }

      "rejects non-file, relative, non-normal and decorated URI references without fallback" in {
        Given("R1: only one explicit canonical private secret exists")
        _with_root { root =>
          val file = _write(root.resolve("private.json"), Array[Byte](1))
          val uri = file.toUri.toString
          val references = Vector("vault://synthetic-secret-marker", "https://example.invalid/secret", "relative.json",
            "file:relative.json", uri + "?synthetic-query-marker", uri + "#synthetic-fragment-marker",
            "file://authority" + file.toString, "file://user@authority" + file.toString,
            root.resolve("x/../private.json").toUri.toString, "file:/", "file:%")
          When("each opaque reference requests runtime resolution")
          val outcomes = references.map(value => RuntimeSecretResolver.privateFile.resolveSecret(_reference(value))) :+
            RuntimeSecretResolver.privateFile.resolveSecret(null)
          val names = _names(root)
          Then("every unsupported locator is a fixed failure and no fallback or directory creation occurs")
          outcomes.foreach(_same_failure(_, _secret_invalid))
          names shouldBe Set("private.json")
        }
      }

      "refuses directories, symbolic and dangling links, ancestor aliases and hardlinks" in {
        Given("R1: selected paths with unsafe types or links beside an untouched private sentinel")
        _with_root { root =>
          val actual = Files.createDirectory(root.resolve("actual"), _directory_attribute)
          val nested = Files.createDirectory(actual.resolve("nested"), _directory_attribute)
          val sentinel = _write(nested.resolve("sentinel"), _bytes("synthetic-secret-marker"))
          val symlink = Files.createSymbolicLink(root.resolve("symlink"), sentinel)
          val dangling = Files.createSymbolicLink(root.resolve("dangling"), root.resolve("absent"))
          val alias = Files.createSymbolicLink(root.resolve("alias"), actual)
          val hardlink = Files.createLink(root.resolve("hardlink"), sentinel)
          val paths = Vector(actual, symlink, dangling, alias.resolve("nested/sentinel"),
            hardlink, sentinel, root.resolve("absent-parent/private.json"), sentinel.resolve("child"))
          When("the runtime admits each exact selected path without following or repairing it")
          val outcomes = paths.map(file => RuntimeSecretResolver.privateFile.resolveSecret(_reference(file.toUri.toString)))
          val retained = Files.readAllBytes(sentinel).toVector
          val links = Vector(symlink, dangling, alias).map(Files.isSymbolicLink)
          val absent = Files.exists(root.resolve("absent-parent"), LinkOption.NOFOLLOW_LINKS)
          Then("unsafe objects remain intact and no missing ancestor is created")
          outcomes.foreach(_same_failure(_, _secret_invalid))
          retained shouldBe _bytes("synthetic-secret-marker").toVector
          links shouldBe Vector(true, true, true)
          absent shouldBe false
        }
      }
    }
  }

  "Trusted OAuth connection configuration" should {
    "closed schema and provider identity" which {
      "loads Google preset configuration bound to the exact component and instance without creating a credential" in {
        Given("R2,R3,R4: a private operator configuration, an absent credential and an explicit component instance")
        _with_root { root =>
          val component = ComponentId("org.example.EditingStudio")
          val instance = ComponentInstanceId(component, "studio-one")
          val document = _document(root, component)
          val reference = _save(root, document)
          When("the trusted host loads configuration without a network interpreter or runtime session")
          val configuration = OAuthConnectionConfiguration.loadC(reference, component, instance).TAKE
          val names = _names(root)
          Then("metadata has exact admitted bindings, configured origin and callback, while custody has no grant yet")
          configuration.componentId shouldBe component
          configuration.instanceId shouldBe instance
          configuration.connectionId shouldBe _connection
          configuration.ownerId shouldBe _owner
          configuration.providerKind shouldBe OAuthConnectionProviderKind.GoogleDrive
          configuration.redirectUri shouldBe "http://127.0.0.1:8123" + _callback(component, _connection)
          configuration.configuredOrigin shouldBe "http://127.0.0.1:8123"
          configuration.callbackPath shouldBe _callback(component, _connection)
          configuration.returnPath shouldBe "/web/editing-studio/home"
          configuration.scopes shouldBe _scopes
          configuration.toString shouldBe "OAuthConnectionConfiguration(<redacted>)"
          names shouldBe Set("operator.json")
        }
      }

      "distinguishes a generic provider named google-drive from the preset and preserves requested scopes" in {
        Given("R3: a closed generic object with the same Google name and endpoints and a public client")
        _with_root { root =>
          val component = _component
          val generic = Json.obj("name" -> Json.fromString("google-drive"),
            "authorizationEndpoint" -> Json.fromString(OAuthProvider.googleDrive.authorizationEndpoint),
            "tokenEndpoint" -> Json.fromString(OAuthProvider.googleDrive.tokenEndpoint))
          val document = _document(root, component).mapObject(_.remove("clientSecret").add("provider", generic))
          val reference = _save(root, document)
          When("the host loads an equivalent-name generic configuration")
          val configuration = OAuthConnectionConfiguration.loadC(reference, component, ComponentInstanceId.default(component)).TAKE
          val request = configuration._client.beginC("s" * 43, "v" * 43).TAKE
          Then("generic provenance is retained and no preset-only authorization parameters or scopes are added")
          configuration.providerKind shouldBe OAuthConnectionProviderKind.Generic
          configuration.scopes shouldBe _scopes
          request.authorizationUri should not include "access_type="
          request.authorizationUri should not include "prompt="
          configuration._client._connection_binding.hcursor.get[Vector[String]]("requestedScopes").toOption shouldBe Some(_scopes)
        }
      }

      "admits fixed HTTPS generic endpoints without discovery or network execution" in {
        Given("R3: a provider whose explicit endpoint names are reserved invalid domains")
        _with_root { root =>
          val provider = Json.obj("name" -> Json.fromString("private-provider"),
            "authorizationEndpoint" -> Json.fromString("https://authorization.invalid/authorize"),
            "tokenEndpoint" -> Json.fromString("https://token.invalid/exchange"))
          val document = _document(root, _component).mapObject(_.add("provider", provider)
            .add("redirectUri", Json.fromString("https://studio.example" + _callback(_component, _connection))))
          val reference = _save(root, document)
          When("only trusted configuration loading is requested")
          val outcome = OAuthConnectionConfiguration.loadC(reference, _component, _instance)
          val names = _names(root)
          Then("the host admits the HTTPS origin with no endpoint discovery, interpreter or credential creation")
          outcome.TAKE.configuredOrigin shouldBe "https://studio.example"
          outcome.TAKE.providerKind shouldBe OAuthConnectionProviderKind.Generic
          names shouldBe Set("operator.json")
        }
      }

      "rejects unknown, missing, null, duplicate and non-object schema shapes including downloaded Google JSON" in {
        Given("R2: a closed operator schema and malformed root, field and provider shapes")
        _with_root { root =>
          val document = _document(root, _component)
          val fields = document.asObject.get
          val provider = Json.obj("name" -> Json.fromString("google-drive"),
            "authorizationEndpoint" -> Json.fromString("https://example.invalid/auth"),
            "tokenEndpoint" -> Json.fromString("https://example.invalid/token"))
          val malformed = fields.keys.toVector.filterNot(_ == "clientSecret").map(key => document.mapObject(_.remove(key))) ++
            fields.keys.toVector.map(key => document.mapObject(_.add(key, Json.Null))) ++
            Vector(Json.Null, Json.arr(document), Json.fromString("synthetic-secret-marker"),
              document.mapObject(_.add("extra", Json.fromString("synthetic-secret-marker"))),
              document.mapObject(_.add("schema", Json.fromString("cncf.oauth-connection/2"))),
              document.mapObject(_.add("provider", Json.fromString("other"))),
              document.mapObject(_.add("provider", provider.mapObject(_.add("extra", Json.True)))),
              document.mapObject(_.add("provider", provider.mapObject(_.remove("tokenEndpoint")))),
              document.mapObject(_.add("provider", provider.mapObject(_.add("tokenEndpoint", Json.Null)))),
              document.mapObject(_.add("provider", provider.mapObject(_.add("tokenEndpoint", Json.fromString("http://example.invalid/token"))))),
              Json.obj("web" -> Json.obj("client_id" -> Json.fromString("synthetic-secret-marker"))))
          val duplicate = document.noSpaces.dropRight(1) + ",\"clientId\":\"synthetic-secret-marker\"}"
          val nestedduplicate = document.mapObject(_.add("provider", provider)).noSpaces
            .replace("\"name\":\"google-drive\"", "\"name\":\"google-drive\",\"name\":\"google-drive\"")
          val payloads = malformed.map(value => _bytes(value.noSpaces)) ++ Vector(_bytes(duplicate), _bytes(nestedduplicate),
            Array[Byte](0xc3.toByte, 0x28.toByte), _bytes("{synthetic-secret-marker"))
          When("each malformed private document is loaded")
          val outcomes = payloads.map(bytes => _load_bytes(root, bytes))
          val names = _names(root)
          Then("all decode failures use the fixed configuration category without creating any credential")
          outcomes.foreach(_same_failure(_, _configuration_invalid))
          names shouldBe Set("operator.json")
        }
      }

      "delegates client identifier, optional secret and scope admission to OAuth client semantics" in {
        Given("R2: invalid client and scope inputs, including present null secrets and non-string scope elements")
        _with_root { root =>
          val document = _document(root, _component)
          val changes = Vector("clientId" -> Json.fromString(""), "clientId" -> Json.fromString("x" * 1025),
            "clientId" -> Json.fromString("has space"), "clientSecret" -> Json.Null,
            "clientSecret" -> Json.fromString(""), "clientSecret" -> Json.fromString("x" * 16385),
            "scopes" -> Json.arr(), "scopes" -> Json.arr(Json.fromString("scope:a"), Json.fromString("scope:a")),
            "scopes" -> Json.arr(Json.fromString("has space")), "scopes" -> Json.arr(Json.fromInt(1)),
            "scopes" -> Json.fromString("scope:a"), "scopes" -> Json.arr(Json.fromString("x" * 1025)),
            "scopes" -> Json.fromValues((1 to 17).map(index => Json.fromString(s"scope:$index"))))
          When("the closed documents request invalid existing OAuth values")
          val outcomes = changes.map { case (key, value) =>
            _load(root, document.mapObject(_.add(key, value)))
          }
          Then("client validation cannot be weakened and reports only the connection configuration failure")
          outcomes.foreach(_same_failure(_, _configuration_invalid))
        }
      }
    }

    "component identity and fixed browser paths" which {
      "rejects null and mismatched component-instance bindings before resolving any file" in {
        Given("R4: an unresolved reference, null arguments and an instance of another component")
        val reference = _reference("file:/synthetic-absent-path-marker")
        val other = ComponentId("org.example.OtherOwner")
        When("the host supplies an invalid explicit identity binding")
        val outcomes = Vector(OAuthConnectionConfiguration.loadC(null, _component, _instance),
          OAuthConnectionConfiguration.loadC(reference, null, _instance),
          OAuthConnectionConfiguration.loadC(reference, _component, null),
          OAuthConnectionConfiguration.loadC(reference, _component, ComponentInstanceId.default(other)))
        Then("identity admission fails before a private file lookup can determine the failure")
        outcomes.foreach(_same_failure(_, _configuration_invalid))
      }

      "refuses wrong callback paths, decorated redirects and unadmitted origins" in {
        Given("R4: callback identity and existing OAuth redirect rules are both required")
        _with_root { root =>
          val callback = _callback(_component, _connection)
          val redirects = Vector("http://127.0.0.1:8123" + callback + "/", "http://127.0.0.1:8123/wrong",
            "http://127.0.0.1:8123" + callback.replace(_connection, "other"),
            "http://127.0.0.1:8123" + callback.replace("editing-studio", "other-owner"),
            "http://127.0.0.1:8123" + callback + "?code=synthetic-secret-marker",
            "http://127.0.0.1:8123" + callback + "#fragment", "http://localhost:8123" + callback,
            "http://127.0.0.1" + callback, "http://example.invalid:8123" + callback,
            "https://example.invalid:8443" + callback, "https://user@example.invalid" + callback,
            "http://127.0.0.1:8123" + callback.replace("callback", "%63allback"))
          When("the configured redirect differs from the fixed admitted callback contract")
          val outcomes = redirects.map(redirect => _load(root, _document(root, _component)
            .mapObject(_.add("redirectUri", Json.fromString(redirect)))))
          Then("no request-derived, decorated, alias or encoded callback is accepted")
          outcomes.foreach(_same_failure(_, _configuration_invalid))
        }
      }

      "admits only fixed single-leading-slash return paths with nonempty ASCII segments" in {
        Given("R4: unsafe return destinations and one valid path using all admitted punctuation")
        _with_root { root =>
          val invalid = Vector("", "/", "relative", "//host/path", "/web//home", "/web/home/", "/.", "/..",
            "/web/../home", "/web/./home", "/web/%2e", "/web?next=home", "/web#home", "/web\\home",
            "/web/has space", "/web/é", "/web/line\nmarker", "/web/tab\tmarker")
          When("the host loads each fixed return path")
          val outcomes = invalid.map(path => _load(root, _document(root, _component)
            .mapObject(_.add("returnPath", Json.fromString(path)))))
          val valid = _load(root, _document(root, _component)
            .mapObject(_.add("returnPath", Json.fromString("/AZaz09._~-/home"))))
          Then("only admitted path segments survive and no redirect parameters are interpreted")
          outcomes.foreach(_same_failure(_, _configuration_invalid))
          valid.TAKE.returnPath shouldBe "/AZaz09._~-/home"
        }
      }

      "preserves at least 100 generated valid connection, owner, scope and component-instance bindings" in {
        Given("R2,R3,R4: generated admitted identity lengths, ordered scopes and named component instances")
        _with_root { root =>
          val generated = for {
            length <- Gen.choose(1, 128)
            suffix <- Gen.choose(0, 1000000)
            ownerlength <- Gen.choose(1, 256)
          } yield ("a" * length, suffix, "o" * ownerlength)
          val property = Prop.forAll(generated) { case (connection, suffix, owner) =>
            val component = ComponentId(s"org.example.Owner$suffix")
            val instance = ComponentInstanceId(component, s"instance-$suffix")
            val scopes = Vector(s"scope:$suffix", "scope:fixed")
            val document = _document(root, component, connection).mapObject(_.add("ownerId", Json.fromString(owner))
              .add("scopes", Json.fromValues(scopes.map(Json.fromString))))
            val reference = _save(root, document)
            OAuthConnectionConfiguration.loadC(reference, component, instance).toOption.exists { configuration =>
              configuration.componentId == component && configuration.instanceId == instance &&
                configuration.connectionId == connection && configuration.ownerId == owner &&
                configuration.scopes == scopes && configuration.callbackPath == _callback(component, connection) &&
                configuration._client._connection_binding.hcursor.get[Vector[String]]("requestedScopes").toOption.contains(scopes)
            }
          }
          When("the generated bindings are loaded from the same private host fixture")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          val names = _names(root)
          Then("every round trip preserves identity and scope order with at least 100 successes and zero discards")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
          names shouldBe Set("operator.json")
        }
      }

      "rejects invalid connection and owner bounds without coercion" in {
        Given("R2: empty, oversized, non-ASCII and whitespace logical identities")
        _with_root { root =>
          val connections = Vector("", "-bad", "has space", "é", "a" * 129, "bad/path")
          val owners = Vector("", "has space", "tab\tmarker", "line\nmarker", "é", "o" * 257)
          When("each document requests an invalid logical binding")
          val outcomes = connections.map(value => _load(root, _document(root, _component)
            .mapObject(_.add("connectionId", Json.fromString(value))))) ++
            owners.map(value => _load(root, _document(root, _component).mapObject(_.add("ownerId", Json.fromString(value)))))
          Then("all logical identity violations use the fixed configuration failure")
          outcomes.foreach(_same_failure(_, _configuration_invalid))
        }
      }
    }

    "private custody composition and diagnostics" which {
      "preserves resolver and store admission failures while never creating or repairing a credential" in {
        Given("R1,R5,R6: private source admission and independently protected credential targets")
        _with_root { root =>
          val file = _write(root.resolve("grant.json"), _bytes("synthetic-existing-record-marker"))
          val original = Files.readAllBytes(file).toVector
          val document = _document(root, _component)
          val reference = _save(root, document)
          Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))
          When("loading encounters unsafe credential custody, then an unsafe source and then an absent credential parent")
          val unsafecredential = OAuthConnectionConfiguration.loadC(reference, _component, _instance)
          Files.setPosixFilePermissions(file, _file_permissions)
          Files.setPosixFilePermissions(root.resolve("operator.json"), PosixFilePermissions.fromString("rw-r--r--"))
          val unsafesource = OAuthConnectionConfiguration.loadC(reference, _component, _instance)
          val missingparent = _load(root, document.mapObject(_.add("credentialFile",
            Json.fromString(root.resolve("absent/grant.json").toString))))
          val malformedpaths = Vector("relative.json", root.resolve("nested/../grant.json").toString,
            file.toUri.toString, root.getRoot.toString).map(path => _load(root, document
              .mapObject(_.add("credentialFile", Json.fromString(path)))))
          val retained = Files.readAllBytes(file).toVector
          val names = _names(root)
          Then("source and custody keep their own fixed structured failures and neither target nor directories are changed")
          _same_failure(unsafecredential, _store_invalid)
          _same_failure(unsafesource, _secret_invalid)
          _same_failure(missingparent, _store_invalid)
          malformedpaths.foreach(_same_failure(_, _configuration_invalid))
          retained shouldBe original
          names shouldBe Set("operator.json", "grant.json")
        }
      }

      "leaves existing credential record interpretation to the selected store" in {
        Given("R5: a protected existing credential containing an invalid record")
        _with_root { root =>
          val file = _write(root.resolve("grant.json"), _bytes("synthetic-existing-record-marker"))
          val reference = _save(root, _document(root, _component))
          When("the host constructs configuration, then explicitly asks its store to load the record")
          val configuration = OAuthConnectionConfiguration.loadC(reference, _component, _instance)
          val loaded = configuration.TAKE._store.loadC()
          val retained = Files.readAllBytes(file).toVector
          Then("configuration performs no grant operation and the existing store owns record failure without mutation")
          configuration.isSuccess shouldBe true
          _same_failure(loaded, Consequence.resourceInvalid("OAuth credential storage record is invalid"))
          retained shouldBe _bytes("synthetic-existing-record-marker").toVector
        }
      }

      "redacts malformed material and selected paths from actual standard, full and log Conclusion projections" in {
        Given("R6: synthetic secret, parser, path and client markers in malformed private configuration")
        _with_root { root =>
          val marker = "synthetic-secret-marker"
          val malformed = _document(root, _component).mapObject(_.add("extra", Json.fromString(marker)))
          val reference = _save(root, malformed)
          When("actual configuration, source and custody failures reach the disclosure projections")
          val invalid = OAuthConnectionConfiguration.loadC(reference, _component, _instance)
          val source = OAuthConnectionConfiguration.loadC(_reference(root.resolve("synthetic-path-marker").toUri.toString), _component, _instance)
          val custody = _load(root, _document(root, _component).mapObject(_.add("credentialFile",
            Json.fromString(root.resolve("synthetic-path-marker/grant.json").toString))))
          val unavailable = RuntimeSecretResolver.privateFile.resolveSecret(_reference(root.resolve("synthetic-path-marker" + ("x" * 300)).toUri.toString))
          val configuration = _load(root, _document(root, _component)).TAKE
          val conclusions = Vector(invalid, source, custody, unavailable).map(_failure)
          val outputs = conclusions.flatMap(_disclosures) ++ conclusions.map(_.display) ++
            Vector(configuration.toString, configuration._client.toString, configuration._store.toString, reference.toString)
          Then("fixed structured categories remain useful and no projection contains input material, filesystem paths or exceptions")
          _same_failure(invalid, _configuration_invalid)
          _same_failure(source, _secret_invalid)
          _same_failure(custody, _store_invalid)
          _same_failure(unavailable, Consequence.serviceUnavailable("Private secret file is unavailable"))
          outputs.foreach { output =>
            Vector(root.toString, "synthetic-path-marker", marker, "synthetic-client-secret-marker", _owner, _connection)
              .foreach(value => output should not include value)
          }
          conclusions.foreach(_.getException shouldBe None)
          conclusions.foreach { conclusion =>
            _disclosures(conclusion).foreach { output =>
              val json = parser.parse(output).toOption.get
              json.hcursor.downField("status").focus shouldBe
                parser.parse(RecordEncoder.json(conclusion.status.toRecord)).toOption
              json.hcursor.downField("observation").downField("taxonomy").focus shouldBe
                parser.parse(RecordEncoder.json(conclusion.observation.taxonomy.toRecord)).toOption
            }
          }
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

object OAuthConnectionConfigurationSpec {
  private val _component = ComponentId("org.example.EditingStudio")
  private val _instance = ComponentInstanceId.default(_component)
  private val _connection = "synthetic-drive-connection"
  private val _owner = "synthetic-owner-marker"
  private val _scopes = Vector("scope:second", "scope:first")
  private val _directory_permissions = PosixFilePermissions.fromString("rwx------")
  private val _file_permissions = PosixFilePermissions.fromString("rw-------")
  private val _directory_attribute = PosixFilePermissions.asFileAttribute(_directory_permissions)
  private def _configuration_invalid: Consequence[Nothing] =
    Consequence.configurationInvalid("OAuth connection configuration is invalid")
  private def _secret_invalid: Consequence[Nothing] =
    Consequence.configurationInvalid("Private secret file configuration is invalid")
  private def _store_invalid: Consequence[Nothing] =
    Consequence.configurationInvalid("OAuth credential storage configuration is invalid")
  private def _bytes(value: String): Array[Byte] = value.getBytes(StandardCharsets.UTF_8)
  private def _reference(value: String): SecretReference = SecretReference.fromConfiguration(value).TAKE
  private def _callback(component: ComponentId, connection: String): String =
    s"/web/${NamingConventions.toNormalizedSegment(component.localId.value())}/connections/$connection/callback"
  private def _document(root: Path, component: ComponentId, connection: String = _connection): Json = Json.obj(
    "schema" -> Json.fromString("cncf.oauth-connection/1"), "connectionId" -> Json.fromString(connection),
    "ownerId" -> Json.fromString(_owner), "provider" -> Json.fromString("google-drive"),
    "clientId" -> Json.fromString("synthetic-client"), "clientSecret" -> Json.fromString("synthetic-client-secret-marker"),
    "redirectUri" -> Json.fromString("http://127.0.0.1:8123" + _callback(component, connection)),
    "scopes" -> Json.fromValues(_scopes.map(Json.fromString)),
    "credentialFile" -> Json.fromString(root.resolve("grant.json").toString),
    "returnPath" -> Json.fromString("/web/editing-studio/home"))
  private def _save(root: Path, document: Json): SecretReference = {
    val file = _write(root.resolve("operator.json"), _bytes(document.noSpaces))
    _reference(file.toUri.toString)
  }
  private def _load(root: Path, document: Json): Consequence[OAuthConnectionConfiguration] =
    OAuthConnectionConfiguration.loadC(_save(root, document), _component, _instance)
  private def _load_bytes(root: Path, bytes: Array[Byte]): Consequence[OAuthConnectionConfiguration] = {
    val file = _write(root.resolve("operator.json"), bytes)
    OAuthConnectionConfiguration.loadC(_reference(file.toUri.toString), _component, _instance)
  }
  private def _write(file: Path, bytes: Array[Byte]): Path = {
    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) Files.setPosixFilePermissions(file, _file_permissions)
    Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    Files.setPosixFilePermissions(file, _file_permissions)
    file
  }
  private def _mode(file: Path): Int =
    Files.getAttribute(file, "unix:mode", LinkOption.NOFOLLOW_LINKS).asInstanceOf[Number].intValue() & 4095
  private def _with_root[A](body: Path => A): A = {
    val base = Files.createDirectories(Paths.get("target", "oauth-configuration-spec")).toRealPath()
    val root = Files.createTempDirectory(base, "configuration-", _directory_attribute).toRealPath()
    try body(root) finally {
      Files.setPosixFilePermissions(root, _directory_permissions)
      Using.resource(Files.walk(root)) { stream =>
        stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => Files.deleteIfExists(path))
      }
    }
  }
  private def _names(directory: Path): Set[String] =
    Using.resource(Files.list(directory))(_.iterator().asScala.map(_.getFileName.toString).toSet)
  private def _failure(outcome: Consequence[?]): Conclusion = outcome match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => throw new AssertionError("Expected a structured OAuth configuration failure")
  }
  private def _disclosures(conclusion: Conclusion): Vector[String] = Vector(
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Standard),
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Full),
    ConclusionDisclosure.log(conclusion)).map(RecordEncoder.json)
}
