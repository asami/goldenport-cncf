package org.goldenport.cncf.filestore

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.config.SecretReference
import org.goldenport.cncf.oauth.{GoogleServiceAccount, OAuthConnectionRequest}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 24, 2026
 * @version Oct. 24, 2026
 */
final class GoogleDriveServiceAccountSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoogleServiceAccountFixture.*

  "Component-owned Google Drive service account" should {
    "sign actual standard JWT claims and reuse then renew a native grant without browser OAuth" in withRoot { root =>
      Given("a generated RSA key, strict private files and the actual component/native HTTP interpreter")
      val fixture = new Harness(root)
      When("two original metadata operations run before expiry and a third reaches the renewal margin")
      val first = fixture.stat()
      val second = fixture.stat()
      fixture.clock.current = observed.plusSeconds(3570)
      val third = fixture.stat()
      Then("only two native token POSTs occur, both signed by the configured key and with no delegated user")
      fixture.created.isSuccess shouldBe true
      first.TAKE.reference shouldBe reference
      Vector(second, third).foreach(_.isSuccess shouldBe true)
      fixture.driver.requests.map(_.method) shouldBe Vector("POST", "GET", "GET", "POST", "GET")
      val tokens = fixture.driver.requests.filter(_.method == "POST")
      tokens.foreach { request =>
        request.path shouldBe "https://oauth2.googleapis.com/token"
        val assertion = decodeAssertion(request.body.get)
        signatureValid(assertion, fixture.pair) shouldBe true
        val claim = claims(assertion).hcursor
        claim.get[String]("iss").toOption shouldBe Some(email)
        claim.get[String]("aud").toOption shouldBe Some(request.path)
        claim.get[String]("scope").toOption shouldBe Some(scope)
        claim.get[Long]("exp").toOption.get - claim.get[Long]("iat").toOption.get shouldBe 3600L
        claim.downField("sub").focus shouldBe None
        claim.downField("private_key").focus shouldBe None
      }
      fixture.driver.requests.filter(_.method == "GET").foreach(_.headers("Authorization") shouldBe "Bearer synthetic-service-token")
      fixture.created.TAKE.toString should not include "PRIVATE KEY"
    }

    "deny unadmitted or unproven subjects and wrong connections before any HTTP, including generated caller IDs" in withRoot { root =>
      Given("one configured business-subject allowlist independent of the service account email")
      val fixture = new Harness(root)
      When("foreign subjects, a local-only identity and a wrong connection attempt the native Drive route")
      val generated = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(Gen.choose(1, 1000000)) { suffix =>
        !fixture.stat(subjectId = s"other-$suffix").isSuccess
      })
      val local = fixture.stat(provider = false)
      val foreign = fixture.stat(connectionId = "another-connection")
      Then("no token or business request is sent")
      generated.passed shouldBe true
      generated.succeeded should be >= 100
      generated.discarded shouldBe 0
      Vector(local, foreign).foreach(_.isSuccess shouldBe false)
      fixture.driver.requests shouldBe empty
    }

    "retain native token-host admission and never fall back to another authentication mechanism" in withRoot { root =>
      Given("the configured service account with no token-host HTTP grant")
      val fixture = new Harness(root)
      When("native authorization tries to acquire its token")
      val result = fixture.stat(tokenOrigin = false)
      Then("the request fails before the driver and no ambient or OAuth alternative is tried")
      result.isSuccess shouldBe false
      fixture.driver.requests shouldBe empty
    }

    "map denied and malformed provider replies safely with one POST and no business replay" in withRoot { root =>
      Given("an actual native driver returning a controlled denial or malformed success")
      val fixture = new Harness(root)
      val responses = Vector(response(Json.obj("private-body-marker" -> Json.True), 403),
        response(Json.obj("access_token" -> Json.fromString("synthetic-secret-token"), "token_type" -> Json.fromString("Bearer"),
          "expires_in" -> Json.fromDoubleOrNull(1.5))),
        response(Json.obj("access_token" -> Json.fromString("synthetic-secret-token"), "token_type" -> Json.fromString("Bearer"),
          "expires_in" -> Json.fromInt(3601))))
      When("each explicit acquisition receives one selected bad reply")
      responses.foreach { value =>
        fixture.driver.token = value
        val result = fixture.stat()
        Then("failure diagnostics omit provider body and token, and no Drive GET is issued")
        result.isSuccess shouldBe false
        result.toString should not include "private-body-marker"
        result.toString should not include "synthetic-secret-token"
      }
      fixture.driver.requests.map(_.method) shouldBe Vector("POST", "POST", "POST")
    }

    "reject foreign endpoints, delegation and malformed credential JSON before initialization completes" in withRoot { root =>
      Given("a genuine private RSA JSON and several unsafe mutations")
      val pair = keyPair()
      val valid = keyDocument(pair)
      val variants = Vector(valid.deepMerge(Json.obj("token_uri" -> Json.fromString("https://attacker.invalid/token"))),
        valid.deepMerge(Json.obj("sub" -> Json.fromString("victim@example.invalid"))),
        valid.deepMerge(Json.obj("type" -> Json.fromString("authorized_user"))),
        valid.deepMerge(Json.obj("private_key" -> Json.fromString("private-key-marker"))))
      When("the credential parser validates each bounded document")
      val duplicate = valid.noSpaces.dropRight(1) + ",\"type\":\"service_account\"}"
      val outcomes = variants.map(value => GoogleServiceAccount.parseC(value.noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8), Vector(scope)))
      val duplicated = GoogleServiceAccount.parseC(duplicate.getBytes(java.nio.charset.StandardCharsets.UTF_8), Vector(scope))
      val accepted = GoogleServiceAccount.parseC(valid.noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8), Vector(scope))
      Then("duplicates, hostile metadata and non-service-account material are fixed safe failures")
      outcomes.foreach(_.isSuccess shouldBe false)
      duplicated.isSuccess shouldBe false
      accepted.TAKE.toString shouldBe "GoogleServiceAccount(<redacted>)"
    }

    "fail closed on weak private-file permissions, ambiguous credential sources and hostile scopes" in withRoot { root =>
      Given("an initialized fixture and the existing strict runtime secret resolver")
      val fixture = new Harness(root)
      val reference = SecretReference.fromConfiguration(fixture.source.toUri.toString).TAKE
      When("key permissions are widened or both credential sources are specified")
      Files.setPosixFilePermissions(fixture.keyFile, PosixFilePermissions.fromString("rw-r--r--"))
      val weak = GoogleDriveServiceAccount.loadC(reference, componentId, instanceId)
      Files.setPosixFilePermissions(fixture.keyFile, PosixFilePermissions.fromString("rw-------"))
      writePrivate(fixture.source, configuration(root).deepMerge(Json.obj("credentialEnvironment" -> Json.fromString("GOOGLE_APPLICATION_CREDENTIALS"))).noSpaces)
      val ambiguous = GoogleDriveServiceAccount.loadC(reference, componentId, instanceId)
      writePrivate(fixture.source, configuration(root).deepMerge(Json.obj("scopes" -> Json.arr(Json.fromString("https://attacker.invalid/scope")))).noSpaces)
      val hostile = GoogleDriveServiceAccount.loadC(reference, componentId, instanceId)
      Then("no unsafe load succeeds and selected failure cannot use the other source")
      Vector(ambiguous, hostile).foreach(_.isSuccess shouldBe false)
      fixture.driver.requests shouldBe empty
      weak.isSuccess shouldBe false
    }

    "refuse a clock rollback before cached reuse or a replacement exchange" in withRoot { root =>
      Given("a connection with one genuine token and a later successful cache use")
      val fixture = new Harness(root)
      val first = fixture.stat()
      fixture.clock.current = observed.plusSeconds(100)
      val second = fixture.stat()
      When("the clock moves back while still after issuance, then before issuance")
      fixture.clock.current = observed.plusSeconds(50)
      val later = fixture.stat()
      fixture.clock.current = observed.minusSeconds(1)
      val earlier = fixture.stat()
      Then("both rollback requests fail without another token exchange or Drive access")
      Vector(first, second).foreach(_.isSuccess shouldBe true)
      Vector(later, earlier).foreach(_.isSuccess shouldBe false)
      fixture.driver.requests.map(_.method) shouldBe Vector("POST", "GET", "GET")
    }

    "reject a different owning component or instance even for an allowed provider-authenticated subject" in withRoot { root =>
      Given("one initialized account, another native component and a second instance of the same component")
      val fixture = new Harness(root)
      val foreign = TestComponentFactory.create("org.example.OtherLake", Protocol.empty)
      withRoot { otherroot =>
        val other = new Harness(otherroot, componentInstance = "two")
        val account = fixture.created.TAKE.asInstanceOf[GoogleDriveConnectionRuntime].googleDriveServiceAccount.get
        val cached = fixture.stat()
        When("the original account receives either foreign native scope")
        val outcomes = Vector(foreign, other.created.TAKE).map { component =>
          val uow = new UnitOfWork(fixture.context(componentOverride = Some(component))) { override def httpDriver = fixture.driver }
          account.authorizationC(uow)
        }
        Then("neither component identity nor instance boundary can borrow the connection")
        cached.isSuccess shouldBe true
        other.created.TAKE.instanceId.instance shouldBe "two"
        outcomes.foreach(_.isSuccess shouldBe false)
        fixture.driver.requests.map(_.method) shouldBe Vector("POST", "GET")
      }
    }

    "reject simultaneous service-account and valid legacy OAuth configuration at component initialization" in withRoot { root =>
      Given("two individually valid configurations explicitly supplied to one component")
      When("the real component factory initializes both authentication strategies")
      val fixture = new Harness(root, oauth = true)
      Then("initialization refuses the ambiguity without token exchange or credential creation")
      fixture.created.isSuccess shouldBe false
      fixture.created.toString should include ("mutually exclusive")
      fixture.driver.requests shouldBe empty
      Files.exists(root.resolve("grant.json")) shouldBe false
    }

    "not expose a service-account connection through user OAuth status or consent" in withRoot { root =>
      Given("a service-account-only runtime retaining the old OAuth management surface")
      val fixture = new Harness(root)
      val context = fixture.context()
      val uow = new UnitOfWork(context) { override def httpDriver = fixture.driver }
      When("the old management status intent is executed")
      val result = new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.OAuthConnectionExecute(OAuthConnectionRequest.status("shared-drive")))
      Then("it remains not-configured rather than fabricating browser consent or contacting Google")
      result.isSuccess shouldBe false
      fixture.driver.requests shouldBe empty
    }
  }
}
