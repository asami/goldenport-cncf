package org.goldenport.cncf.oauth

import java.io.IOException
import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardOpenOption}
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.Base64
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import io.circe.{Json, parser}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.config.SecretReference
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId, ScopeContext, ScopeKind, SecurityContext, SecurityLevel, SubjectKind}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.observability.{ConclusionDiagnostics, ConclusionDisclosure}
import org.goldenport.cncf.security.{SecurityAuthenticationProvenance, SecuritySubject}
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork}
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
final class OAuthConnectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OAuthConnectionSpec.*

  "Private OAuth connection runtime" should {
    "E1 trusted construction" which {
      "loads the selected store once without entropy, HTTP, writes or directory creation" in {
        Given("R1: an explicit canonical private configuration and absent selected grant")
        _with_root { root =>
          val fixture = new Harness(root)
          val names = _names(root)
          When("the host constructs another ordinary connection and requests authorized status")
          val second = OAuthConnection.createC(fixture.configuration).TAKE
          val status = second.statusC(fixture.context()).TAKE
          Then("absence is truthful and construction has no mutation, entropy or native effect")
          status.state shouldBe OAuthConnectionState.AuthorizationRequired
          status.canRefresh shouldBe false
          status.expiresAt shouldBe None
          status.authorizationPending shouldBe false
          status.updating shouldBe false
          status.lastOutcome shouldBe None
          fixture.store.loads shouldBe 1
          fixture.store.saves shouldBe 0
          fixture.store.forgets shouldBe 0
          fixture.entropy.draws shouldBe 0
          fixture.requests shouldBe empty
          _names(root) shouldBe names
        }
      }

      "accepts a valid saved expired grant while failing closed on malformed and mismatched records" in {
        Given("R1: a private persisted expired credential and equivalent configuration")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)))
          val before = Files.readAllBytes(fixture.file).toVector
          When("the host reloads the expired grant, then encounters a foreign owner and malformed bytes")
          val restored = OAuthConnection.createC(fixture.reload()).TAKE.statusC(fixture.context()).TAKE
          val document = parser.parse(new String(before.toArray, StandardCharsets.UTF_8)).toOption.get
          _write(fixture.file, document.mapObject(_.add("ownerId", Json.fromString("foreign-owner"))).noSpaces)
          val mismatch = OAuthConnection.createC(fixture.reload())
          val foreignbytes = Files.readAllBytes(fixture.file).toVector
          _write(fixture.file, "{synthetic-private-record-marker")
          val invalid = OAuthConnection.createC(fixture.reload())
          val invalidbytes = Files.readAllBytes(fixture.file).toVector
          Then("expiry is not corruption and invalid identity or syntax never becomes an absent grant")
          restored.state shouldBe OAuthConnectionState.Expired
          restored.canRefresh shouldBe true
          restored.expiresAt shouldBe Some(_observed.minusSeconds(1))
          Vector(mismatch, invalid).foreach(_same_failure(_, _record_invalid))
          new String(foreignbytes.toArray, StandardCharsets.UTF_8) should include ("foreign-owner")
          new String(invalidbytes.toArray, StandardCharsets.UTF_8) shouldBe "{synthetic-private-record-marker"
          fixture.entropy.draws shouldBe 0
          fixture.requests shouldBe empty
        }
      }

      "refuses null capabilities and invalid or foreign loaded grants without a construction bypass" in {
        Given("R1: private host composition and a separately owned equivalent client credential")
        _with_root { root =>
          val fixture = new Harness(root)
          val foreign = fixture.reload()._client._restore_credential_c(_access, Some(_refresh), _scopes,
            _observed.plusSeconds(60)).TAKE
          val values = Vector[Option[OAuthCredential]](null, Some(null), Some(foreign),
            Some(OAuthCredential._create("bad token", Some(_refresh), _scopes, _observed, fixture.configuration._client)))
          When("the trusted factory validates null inputs and each selected loaded record")
          val nulls = Vector(OAuthConnection.createC(null),
            OAuthConnection.createC(null, fixture.store, fixture.entropy),
            OAuthConnection.createC(fixture.configuration, null, fixture.entropy),
            OAuthConnection.createC(fixture.configuration, fixture.store, null))
          val outcomes = values.map { value =>
            fixture.store.loaded = Some(Consequence.success(value))
            OAuthConnection.createC(fixture.configuration, fixture.store, fixture.entropy)
          }
          Then("every null capability and invalid record is rejected before entropy or HTTP")
          nulls.foreach(_same_failure(_, Consequence.configurationInvalid("OAuth connection configuration is invalid")))
          outcomes.foreach(_same_failure(_, _record_invalid))
          fixture.store.loads shouldBe 1 + values.size
          fixture.store.saves shouldBe 0
          fixture.entropy.draws shouldBe 0
          fixture.requests shouldBe empty
        }
      }
    }

    "E2 actual execution authority" which {
      "requires the provider-authenticated exact owner for every owner operation" in {
        Given("R2: a real pending flow and anonymous, public-forged, trusted-local and foreign-owner contexts")
        _with_root { root =>
          val fixture = new Harness(root)
          val connection = fixture.connection
          val handoff = connection.beginC(fixture.context()).TAKE
          val contexts = Vector(fixture.context(authentication = "anonymous"), fixture.context(authentication = "forged"),
            fixture.context(authentication = "local"), fixture.context(ownerId = "foreign-owner"),
            fixture.context(authentication = "provider-anonymous"))
          val draws = fixture.entropy.draws
          When("each unadmitted identity asks for status, begin, private header or forget")
          val outcomes = contexts.flatMap { context => Vector(
            connection.statusC(context), connection.beginC(context),
            connection.authorizationC(fixture.uow(context), OAuthConnectionProviderKind.GoogleDrive), connection.forgetC(context)) }
          val status = connection.statusC(fixture.context()).TAKE
          val completed = fixture.complete(handoff)
          Then("all refusals precede effects and preserve the owner's legitimate flow")
          outcomes.foreach(_same_failure(_, _owner_denied))
          status.authorizationPending shouldBe true
          fixture.entropy.draws shouldBe draws
          completed.TAKE.state shouldBe OAuthConnectionState.Connected
          fixture.store.saves shouldBe 1
          fixture.store.forgets shouldBe 0
          fixture.requests.size shouldBe 1
        }
      }

      "requires the exact actual component and instance for all five operations" in {
        Given("R2: a legitimate flow and foreign component, foreign instance, unscoped and null execution carriers")
        _with_root { root =>
          val fixture = new Harness(root)
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          val foreign = ComponentId("org.example.ForeignOwner")
          val contexts = Vector(fixture.context(componentId = foreign, instanceId = ComponentInstanceId.default(foreign)),
            fixture.context(instanceId = ComponentInstanceId(_component, "foreign-instance")),
            fixture.context(scoped = false), null)
          val draws = fixture.entropy.draws
          When("each foreign execution carrier invokes every private runtime contract")
          val outcomes = contexts.flatMap { context =>
            val uow = fixture.uow(context)
            Vector(fixture.connection.statusC(context), fixture.connection.beginC(context),
              fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(_code), None, uow),
              fixture.connection.authorizationC(uow, OAuthConnectionProviderKind.GoogleDrive), fixture.connection.forgetC(context))
          } ++ Vector(fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(_code), None, null),
            fixture.connection.authorizationC(null, OAuthConnectionProviderKind.GoogleDrive))
          val pending = fixture.connection.statusC(fixture.context()).TAKE
          val completed = fixture.complete(handoff)
          Then("the exact scope is required before all effects without consuming a valid flow")
          outcomes.foreach(_same_failure(_, _scope_denied))
          pending.authorizationPending shouldBe true
          fixture.entropy.draws shouldBe draws
          completed.TAKE.state shouldBe OAuthConnectionState.Connected
          fixture.requests.size shouldBe 1
          fixture.store.forgets shouldBe 0
        }
      }

      "walks semantic parent scopes and admits a matching callback without upgrading anonymous ingress" in {
        Given("R2: an owner-started flow and a nested action scope with anonymous ingress")
        _with_root { root =>
          val fixture = new Harness(root)
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          val anonymous = fixture.context(authentication = "anonymous")
          val nested = anonymous.withScope(anonymous.cncfCore.scope.createChildScope(ScopeKind.Action, "callback"))
          val subject = SecuritySubject.from(nested.security)
          val uow = fixture.uow(nested)
          When("the scope-bound callback presents the owner's exact stored state and browser proof")
          val completed = fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(_code), None, uow)
          val denied = fixture.connection.authorizationC(uow, OAuthConnectionProviderKind.GoogleDrive)
          Then("callback authority completes only that flow and confers no ingress or service authority")
          completed.TAKE.state shouldBe OAuthConnectionState.Connected
          subject.isAuthenticated shouldBe false
          SecuritySubject.from(nested.security).isProviderAuthenticated shouldBe false
          nested.security should be theSameInstanceAs anonymous.security
          _same_failure(denied, _owner_denied)
          uow.commits shouldBe 0
          uow.aborts shouldBe 0
          fixture.requests.size shouldBe 1
        }
      }

      "rejects null and mismatched provider kinds before using a cached or refreshable grant" in {
        Given("R2,R4: an expired refreshable Google grant and a generic provider with the same Google name")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)))
          val genericdocument = _document(root).mapObject(_.add("provider", Json.obj(
            "name" -> Json.fromString("google-drive"), "authorizationEndpoint" -> Json.fromString(_authorization),
            "tokenEndpoint" -> Json.fromString(_token))))
          val generic = _configuration(root, genericdocument)
          val emptyfile = root.resolve("generic.json")
          val genericstore = PrivateFileOAuthCredentialStore.createC(emptyfile, _connection_id, _owner, generic._client).TAKE
          val connection = OAuthConnection.createC(generic, genericstore, new Draws).TAKE
          When("private consumers request an unconfigured or null provider kind")
          val outcomes = Vector(
            fixture.connection.authorizationC(fixture.uow(fixture.context()), null),
            fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.Generic),
            connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive))
          Then("configuration provenance refuses every mismatch before HTTP, save or grant disclosure")
          outcomes.foreach(_same_failure(_, Consequence.securityPermissionDenied("OAuth connection provider is not allowed")))
          fixture.requests shouldBe empty
          fixture.store.saves shouldBe 0
          Files.exists(emptyfile) shouldBe false
        }
      }
    }

    "E3 explicit browser preparation" which {
      "draws three independent 32-byte values and prepares PKCE with exactly 300 bound seconds" in {
        Given("R3: a deterministic private entropy capability and a fixed bound clock")
        _with_root { root =>
          val fixture = new Harness(root)
          When("the owner explicitly begins and observes the flow at both lifetime boundaries")
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          val fields = _query(handoff)
          val atstart = fixture.connection.statusC(fixture.context()).TAKE
          fixture.clock.set(_observed.plusSeconds(299))
          val beforeend = fixture.connection.statusC(fixture.context()).TAKE
          fixture.clock.set(_observed.plusSeconds(300))
          val atend = fixture.connection.statusC(fixture.context()).TAKE
          Then("three distinct unpadded values supply state, verifier and proof with exact PKCE and lifetime")
          fixture.entropy.draws shouldBe 3
          fields("state") shouldBe _encoded(1)
          handoff.browserProof shouldBe _encoded(3)
          fields("code_challenge") shouldBe Base64.getUrlEncoder.withoutPadding.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(_encoded(2).getBytes(StandardCharsets.US_ASCII)))
          fields("code_challenge_method") shouldBe "S256"
          fields("scope") shouldBe _scopes.mkString(" ")
          fields("access_type") shouldBe "offline"
          fields("prompt") shouldBe "consent"
          handoff.authorizationUri should not include handoff.browserProof
          handoff.authorizationUri should not include _encoded(2)
          Vector(fields("state"), handoff.browserProof, _encoded(2)).foreach { value =>
            value.length shouldBe 43
            value.matches("[A-Za-z0-9_-]{43}") shouldBe true
          }
          Vector(atstart.authorizationPending, beforeend.authorizationPending, atend.authorizationPending) shouldBe Vector(true, true, false)
          fixture.store.saves shouldBe 0
          fixture.requests shouldBe empty
        }
      }

      "lets the latest successful explicit begin supersede the old flow while retaining the working grant" in {
        Given("R3: a persisted working grant and an earlier pending browser request")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)))
          val first = fixture.connection.beginC(fixture.context()).TAKE
          val bytes = Files.readAllBytes(fixture.file).toVector
          When("the owner begins again, the old callback arrives, and the latest browser denies")
          val latest = fixture.connection.beginC(fixture.context()).TAKE
          val old = fixture.complete(first)
          val denial = fixture.connection.completeC(_state(latest), latest.browserProof, None, Some("access_denied"), fixture.uow(fixture.context()))
          val header = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          Then("only the latest flow is eligible and cancellation preserves the previous usable grant")
          _same_failure(old, _flow_invalid)
          _state(first) should not be _state(latest)
          denial.TAKE.state shouldBe OAuthConnectionState.Connected
          denial.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Denied)
          header.TAKE shouldBe Map("Authorization" -> s"Bearer ${_access}")
          Files.readAllBytes(fixture.file).toVector shouldBe bytes
          fixture.entropy.draws shouldBe 6
          fixture.requests shouldBe empty
          fixture.store.saves shouldBe 0
        }
      }

      "leaves a legitimate flow intact after null, short, oversized or throwing entropy" in {
        Given("R3: a valid pending flow followed by malformed private entropy draws")
        _with_root { root =>
          val fixture = new Harness(root)
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          val invalid = Vector[() => Array[Byte]](() => null, () => Array.fill[Byte](31)(1),
            () => Array.fill[Byte](33)(1), () => throw new IOException("synthetic-entropy-secret-marker"))
          When("each attempted replacement encounters invalid entropy and the original flow then completes")
          val failures = invalid.map { draw =>
            fixture.entropy.next = Some(draw)
            fixture.connection.beginC(fixture.context())
          }
          fixture.entropy.next = None
          val pending = fixture.connection.statusC(fixture.context()).TAKE
          val completed = fixture.complete(handoff)
          Then("no malformed draw replaces pending state and each local failure uses one fixed unavailable result")
          failures.foreach(_same_failure(_, _unavailable))
          pending.authorizationPending shouldBe true
          completed.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Authorized)
          fixture.requests.size shouldBe 1
          failures.flatMap(value => _disclosures(_failure(value))).foreach(_ should not include "synthetic-entropy-secret-marker")
        }
      }

      "refuses invalid or overflowing bound clocks without replacing pending flow or drawing entropy" in {
        Given("R3: a legitimate flow and a bound clock that can return invalid or overflowing instants")
        _with_root { root =>
          val fixture = new Harness(root)
          val context = fixture.context()
          val handoff = fixture.connection.beginC(context).TAKE
          val draws = fixture.entropy.draws
          When("replacement begins at null, an overflowing Instant and a throwing clock")
          fixture.clock.set(null)
          val missing = fixture.connection.beginC(context)
          fixture.clock.set(Instant.MAX)
          val overflow = fixture.connection.beginC(context)
          fixture.clock.failing = true
          val throwing = fixture.connection.beginC(context)
          fixture.clock.failing = false
          fixture.clock.set(_observed)
          val pending = fixture.connection.statusC(fixture.context()).TAKE
          val completed = fixture.complete(handoff)
          Then("the original request survives and no system-clock fallback or extra entropy is used")
          Vector(missing, overflow, throwing).foreach(_same_failure(_, _clock_invalid))
          pending.authorizationPending shouldBe true
          fixture.entropy.draws shouldBe draws
          completed.TAKE.state shouldBe OAuthConnectionState.Connected
        }
      }
    }

    "E4 one-shot callback authority" which {
      "preserves the legitimate flow and outcome across malformed or foreign state and browser proofs" in {
        Given("R3: one legitimate flow and invalid state/proof representations")
        _with_root { root =>
          val fixture = new Harness(root)
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          val invalid = Vector(null, "", "a" * 42, "a" * 129, "wrong+" * 10, "é" * 43, "z" * 43)
          When("foreign state and browser markers are attempted before the genuine callback")
          val outcomes = invalid.flatMap { marker => Vector(
            fixture.connection.completeC(marker, handoff.browserProof, Some(_code), None, fixture.uow(fixture.context())),
            fixture.connection.completeC(_state(handoff), marker, Some(_code), None, fixture.uow(fixture.context()))) }
          val status = fixture.connection.statusC(fixture.context()).TAKE
          val requests = fixture.requests
          val completed = fixture.complete(handoff)
          val replay = fixture.complete(handoff)
          Then("foreign attempts do not consume or change outcome and the genuine code can run exactly once")
          outcomes.foreach(_same_failure(_, _flow_invalid))
          status.authorizationPending shouldBe true
          status.lastOutcome shouldBe None
          requests shouldBe empty
          completed.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Authorized)
          _same_failure(replay, _flow_invalid)
          fixture.requests.size shouldBe 1
          fixture.store.saves shouldBe 1
        }
      }

      "rejects at least one hundred generated foreign flow or proof markers without consumption or effects" in {
        Given("R3: one authentic pending flow and generated bounded base64url foreign markers")
        _with_root { root =>
          val fixture = new Harness(root)
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          val markers = for {
            size <- Gen.choose(42, 127)
            chars <- Gen.listOfN(size, Gen.oneOf("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-".toVector))
            state <- Gen.oneOf(true, false)
          } yield ("z" + chars.mkString, state)
          When("generated foreign attempts are interpreted before one genuine completion")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(markers) { case (marker, state) =>
            val outcome = fixture.connection.completeC(if (state) marker else _state(handoff),
              if (state) handoff.browserProof else marker, Some(_code), None, fixture.uow(fixture.context()))
            _matches_failure(outcome, _flow_invalid) && fixture.requests.isEmpty && fixture.store.saves == 0 &&
              fixture.connection.statusC(fixture.context()).TAKE.authorizationPending
          })
          val before = fixture.connection.statusC(fixture.context()).TAKE
          val completed = fixture.complete(handoff)
          Then("all generated foreign markers are refused without discarded examples or loss of the genuine flow")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
          before.lastOutcome shouldBe None
          completed.TAKE.state shouldBe OAuthConnectionState.Connected
          fixture.requests.size shouldBe 1
        }
      }

      "consumes a matched flow on expiry or rollback and never exchanges its code" in {
        Given("R3: flows observed at the exact 300-second boundary or before their issuance")
        Vector(_observed.plusSeconds(300), _observed.minusNanos(1)).foreach { now =>
          _with_root { root =>
            val fixture = new Harness(root)
            val handoff = fixture.connection.beginC(fixture.context()).TAKE
            fixture.clock.set(now)
            When("the matching browser attempts completion outside the bound interval and then replays")
            val outcome = fixture.complete(handoff)
            fixture.clock.set(_observed)
            val status = fixture.connection.statusC(fixture.context()).TAKE
            val replay = fixture.complete(handoff)
            Then("expiry consumes the match, records Expired and performs no HTTP or save")
            _same_failure(outcome, Consequence.stateConflict("OAuth authorization flow has expired"))
            _same_failure(replay, _flow_invalid)
            status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Expired)
            status.authorizationPending shouldBe false
            fixture.requests shouldBe empty
            fixture.store.saves shouldBe 0
          }
        }
      }

      "consumes a matched callback when its bound clock is invalid without falling back to host time" in {
        Given("R3: a valid browser flow whose bound callback clock becomes unavailable")
        _with_root { root =>
          val fixture = new Harness(root)
          val context = fixture.context()
          val uow = fixture.uow(context)
          val handoff = fixture.connection.beginC(context).TAKE
          fixture.clock.set(null)
          When("the matching callback arrives and later attempts replay after clock recovery")
          val outcome = fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(_code), None, uow)
          fixture.clock.set(_observed)
          val replay = fixture.complete(handoff)
          val status = fixture.connection.statusC(fixture.context()).TAKE
          Then("the invalid clock is explicit and cannot permit replay or native effects")
          _same_failure(outcome, _clock_invalid)
          _same_failure(replay, _flow_invalid)
          status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Expired)
          fixture.requests shouldBe empty
          fixture.store.saves shouldBe 0
        }
      }

      "consumes malformed provider responses before refusing them while preserving a working grant" in {
        Given("R3: a cached grant and malformed code/error alternatives including null Options and present nulls")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)))
          val malformed = Vector((null, None), (None, null), (Some(null), None), (None, Some(null)),
            (None, None), (Some(_code), Some("denied")), (Some(""), None), (Some("code with space"), None),
            (Some("x" * 8193), None), (Some("é"), None), (Some("code\n"), None),
            (None, Some("")), (None, Some("error with space")), (None, Some("x" * 129)),
            (None, Some("é")), (None, Some("error\n")), (None, Some("error\u007f")))
          val before = Files.readAllBytes(fixture.file).toVector
          When("each matched flow carries an invalid provider result and then replays")
          val outcomes = malformed.map { case (code, error) =>
            val handoff = fixture.connection.beginC(fixture.context()).TAKE
            val result = fixture.connection.completeC(_state(handoff), handoff.browserProof, code, error, fixture.uow(fixture.context()))
            val replay = fixture.complete(handoff)
            (result, replay)
          }
          val status = fixture.connection.statusC(fixture.context()).TAKE
          val header = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          Then("each result records Failed without changing the grant, and each consumed flow refuses replay")
          outcomes.foreach { case (result, replay) =>
            _same_failure(result, Consequence.argumentInvalid("OAuth authorization response is invalid"))
            _same_failure(replay, _flow_invalid)
          }
          status.state shouldBe OAuthConnectionState.Connected
          status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Failed)
          status.authorizationPending shouldBe false
          header.TAKE shouldBe Map("Authorization" -> s"Bearer ${_access}")
          Files.readAllBytes(fixture.file).toVector shouldBe before
          fixture.requests shouldBe empty
          fixture.store.saves shouldBe 0
        }
      }

      "admits both inclusive printable code and provider-error limits without echoing denial details" in {
        Given("R3: independent owner flows at the inclusive printable code and error bounds")
        _with_root { root =>
          val fixture = new Harness(root)
          When("one- and 8192-character codes complete and one- and 128-character error values deny later flows")
          val codes = Vector("c", "c" * 8192).map { code =>
            val handoff = fixture.connection.beginC(fixture.context()).TAKE
            fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(code), None, fixture.uow(fixture.context()))
          }
          val errors = Vector("e", "e" * 128).map { error =>
            val handoff = fixture.connection.beginC(fixture.context()).TAKE
            fixture.connection.completeC(_state(handoff), handoff.browserProof, None, Some(error), fixture.uow(fixture.context()))
          }
          Then("codes exchange once each and denials retain the working credential without HTTP or secret status fields")
          codes.foreach(_.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Authorized))
          errors.foreach { result =>
            result.TAKE.state shouldBe OAuthConnectionState.Connected
            result.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Denied)
            result.TAKE.authorizationPending shouldBe false
            result.TAKE.toString should not include ("e" * 128)
          }
          fixture.requests.size shouldBe 2
          fixture.requests.map(request => _decode(request.text.get)("code").length) shouldBe Vector(1, 8192)
          fixture.store.saves shouldBe 2
        }
      }
    }

    "E5 native exchange and persisted reconstruction" which {
      "performs one admitted form POST in the supplied UnitOfWork and saves before readiness" in {
        Given("R2,R4: a real native driver, pending owner flow and caller-owned UnitOfWork")
        _with_root { root =>
          val fixture = new Harness(root)
          val context = fixture.context()
          val uow = fixture.uow(context)
          val handoff = fixture.connection.beginC(context).TAKE
          When("the matching callback exchanges and the private consumer requests its unexpired header")
          val outcome = fixture.connection.completeC(_state(handoff), handoff.browserProof, Some("code/+&=%"), None, uow)
          val header = fixture.connection.authorizationC(uow, OAuthConnectionProviderKind.GoogleDrive)
          val saved = fixture.configuration._store.loadC().TAKE.get
          val actual = fixture.requests.head
          Then("one native POST has the exact protocol form and no independent commit, abort or header refresh occurs")
          outcome.TAKE.state shouldBe OAuthConnectionState.Connected
          outcome.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Authorized)
          outcome.TAKE.expiresAt shouldBe Some(_observed.plusSeconds(3600))
          actual.path shouldBe _token
          actual.headers shouldBe Map("Content-Type" -> "application/x-www-form-urlencoded", "Accept" -> "application/json")
          _decode(actual.text.get) shouldBe Map("grant_type" -> "authorization_code", "code" -> "code/+&=%",
            "redirect_uri" -> fixture.configuration.redirectUri, "client_id" -> "synthetic-client-marker",
            "client_secret" -> "synthetic-client-secret-marker", "code_verifier" -> _encoded(2))
          actual.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("16384")
          saved.accessToken shouldBe _access
          saved.refreshToken shouldBe Some(_refresh)
          header.TAKE shouldBe Map("Authorization" -> s"Bearer ${_access}")
          fixture.store.saves shouldBe 1
          fixture.requests.size shouldBe 1
          uow.acquisitions shouldBe 1
          uow.commits shouldBe 0
          uow.aborts shouldBe 0
          fixture.recorded shouldBe empty
          fixture.trace(context) should include ("uow:http:post")
        }
      }

      "reloads a saved grant through independently constructed equivalent configuration without restoring pending flow" in {
        Given("R1,R4: a completed native authorization and another pending request in the first runtime")
        _with_root { root =>
          val fixture = new Harness(root)
          fixture.complete(fixture.connection.beginC(fixture.context()).TAKE).TAKE
          val pending = fixture.connection.beginC(fixture.context()).TAKE
          When("a second independent configuration/runtime loads the persisted grant and receives the old callback")
          val configuration = fixture.reload()
          val second = OAuthConnection.createC(configuration).TAKE
          val status = second.statusC(fixture.context()).TAKE
          val header = second.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val callback = second.completeC(_state(pending), pending.browserProof, Some(_code), None, fixture.uow(fixture.context()))
          Then("only the saved grant survives reconstruction and no old pending code is exchanged")
          (configuration eq fixture.configuration) shouldBe false
          (configuration._client eq fixture.configuration._client) shouldBe false
          status.state shouldBe OAuthConnectionState.Connected
          status.authorizationPending shouldBe false
          status.lastOutcome shouldBe None
          header.TAKE shouldBe Map("Authorization" -> s"Bearer ${_access}")
          _same_failure(callback, _flow_invalid)
          fixture.requests.size shouldBe 1
        }
      }

      "preserves native HTTP permission refusal before driver acquisition and releases the busy gate" in {
        Given("R2,R4: a legitimate browser flow with native HTTP authority for an unrelated origin only")
        _with_root { root =>
          val fixture = new Harness(root, origins = Vector("https://unrelated.example"))
          val context = fixture.context()
          val uow = fixture.uow(context)
          val handoff = fixture.connection.beginC(context).TAKE
          val expected = _failure(HttpExecutionAdmission.resolveC(context, HttpExecutionMethod.Post, _token,
            Vector(Property("http.max-response-bytes", "16384", None))))
          When("the matched code requests native admission and the owner then explicitly begins another flow")
          val actual = _failure(fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(_code), None, uow))
          val status = fixture.connection.statusC(context).TAKE
          val next = fixture.connection.beginC(context)
          Then("the original native Conclusion survives without driver access, retry or a stuck gate")
          actual.status shouldBe expected.status
          actual.observation.isMatch(expected.observation) shouldBe true
          actual.interpretation shouldBe expected.interpretation
          actual.disposition shouldBe expected.disposition
          ConclusionDiagnostics.classify(actual).policy shouldBe Some("http.execution.destination")
          status.state shouldBe OAuthConnectionState.Uncertain
          status.updating shouldBe false
          status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Failed)
          next.isSuccess shouldBe true
          uow.acquisitions shouldBe 0
          uow.commits shouldBe 0
          uow.aborts shouldBe 0
          fixture.requests shouldBe empty
          fixture.store.saves shouldBe 0
        }
      }
    }
  }

  "Private OAuth credential use" should {
    "E6 refresh and explicit reconciliation" which {
      "returns an unexpired private header without refresh, writes, outcome changes or pending consumption" in {
        Given("R4: a persisted working grant and a separate pending browser flow")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)))
          fixture.connection.beginC(fixture.context()).TAKE
          val before = Files.readAllBytes(fixture.file).toVector
          When("the private native consumer requests the configured provider header twice")
          val headers = Vector.fill(2)(fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive))
          val status = fixture.connection.statusC(fixture.context()).TAKE
          Then("the actual cached grant supplies the header while every external effect remains absent")
          headers.foreach(_.TAKE shouldBe Map("Authorization" -> s"Bearer ${_access}"))
          status.state shouldBe OAuthConnectionState.Connected
          status.authorizationPending shouldBe true
          status.lastOutcome shouldBe None
          status.updating shouldBe false
          Files.readAllBytes(fixture.file).toVector shouldBe before
          fixture.requests shouldBe empty
          fixture.store.saves shouldBe 0
        }
      }

      "requires authorization for absent grants and expired grants without refresh material" in {
        Given("R4: absent or expired nonrefreshable saved grants")
        Vector(None, Some(_observed)).foreach { expiry =>
          _with_root { root =>
            val fixture = new Harness(root, expiry = expiry, refresh = None)
            When("the private consumer requests a header and the owner observes status")
            val outcome = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
            val status = fixture.connection.statusC(fixture.context()).TAKE
            Then("readiness is honest and missing refresh never causes a native request or save")
            _same_failure(outcome, _requires_authorization)
            status.state shouldBe (if (expiry.isDefined) OAuthConnectionState.Expired else OAuthConnectionState.AuthorizationRequired)
            status.canRefresh shouldBe false
            status.expiresAt shouldBe expiry
            fixture.requests shouldBe empty
            fixture.store.saves shouldBe 0
          }
        }
      }

      "saves a rotated refresh grant once and uses that saved token after independent reconstruction" in {
        Given("R4: an expired refreshable grant and native responses that rotate refresh material")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)))
          fixture.answer = _ => if (fixture.requests.size == 1)
            _response("synthetic-rotated-access-one", Some("synthetic-rotated-refresh-one"), 60)
            else _response("synthetic-rotated-access-two", Some("synthetic-rotated-refresh-two"), 60)
          val context = fixture.context()
          val firstuow = fixture.uow(context)
          When("one refresh is saved, a second runtime reloads, and its later expired grant refreshes")
          val first = fixture.connection.authorizationC(firstuow, OAuthConnectionProviderKind.GoogleDrive)
          val firstsaved = fixture.configuration._store.loadC().TAKE.get
          fixture.clock.set(_observed.plusSeconds(61))
          val secondconfiguration = fixture.reload()
          val second = OAuthConnection.createC(secondconfiguration).TAKE
          val seconduow = fixture.uow(fixture.context())
          val later = second.authorizationC(seconduow, OAuthConnectionProviderKind.GoogleDrive)
          val lastsaved = secondconfiguration._store.loadC().TAKE.get
          Then("each call has one refresh POST and the later request uses the previously saved rotated token")
          first.TAKE shouldBe Map("Authorization" -> "Bearer synthetic-rotated-access-one")
          later.TAKE shouldBe Map("Authorization" -> "Bearer synthetic-rotated-access-two")
          _decode(fixture.requests.head.text.get) shouldBe Map("grant_type" -> "refresh_token", "refresh_token" -> _refresh,
            "client_id" -> "synthetic-client-marker", "client_secret" -> "synthetic-client-secret-marker")
          _decode(fixture.requests(1).text.get)("refresh_token") shouldBe "synthetic-rotated-refresh-one"
          firstsaved.refreshToken shouldBe Some("synthetic-rotated-refresh-one")
          lastsaved.refreshToken shouldBe Some("synthetic-rotated-refresh-two")
          fixture.requests.size shouldBe 2
          fixture.store.saves shouldBe 1
          Vector(firstuow.commits, firstuow.aborts, seconduow.commits, seconduow.aborts) shouldBe Vector(0, 0, 0, 0)
          second.statusC(fixture.context()).TAKE.lastOutcome shouldBe None
          fixture.recorded shouldBe empty
        }
      }

      "retains Authorized as the last authorization outcome after successful refresh" in {
        Given("R4: a native authorization whose saved grant later expires")
        _with_root { root =>
          val fixture = new Harness(root)
          fixture.complete(fixture.connection.beginC(fixture.context()).TAKE).TAKE
          fixture.clock.set(_observed.plusSeconds(3600))
          When("the private consumer refreshes the expired grant")
          val header = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val status = fixture.connection.statusC(fixture.context()).TAKE
          Then("refresh updates readiness without claiming a new browser authorization outcome")
          header.isSuccess shouldBe true
          status.state shouldBe OAuthConnectionState.Connected
          status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Authorized)
          status.updating shouldBe false
          fixture.requests.size shouldBe 2
          fixture.store.saves shouldBe 2
        }
      }

      "rechecks the bound clock before returning a refreshed header without repeating refresh" in {
        Given("R4: an expired grant and a native response whose new grant expires before HTTP returns")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)))
          fixture.answer = _ => {
            fixture.clock.set(_observed.plusSeconds(120))
            _response("synthetic-short-access", Some("synthetic-short-refresh"), 60)
          }
          When("one refresh completes and its saved replacement is already expired at header return")
          val outcome = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val status = fixture.connection.statusC(fixture.context()).TAKE
          val saved = fixture.configuration._store.loadC().TAKE.get
          Then("the new grant remains saved but no expired header or automatic second refresh is returned")
          _same_failure(outcome, _requires_authorization)
          status.state shouldBe OAuthConnectionState.Expired
          status.expiresAt shouldBe Some(_observed.plusSeconds(60))
          status.lastOutcome shouldBe None
          saved.accessToken shouldBe "synthetic-short-access"
          saved.refreshToken shouldBe Some("synthetic-short-refresh")
          fixture.requests.size shouldBe 1
          fixture.store.saves shouldBe 1
        }
      }

      "blocks prior cached use after native failure, preserves denial, and recovers only through explicit successful reauthorization" in {
        Given("R4: a working prior grant and a native exchange that returns a provider service failure")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)))
          val old = Files.readAllBytes(fixture.file).toVector
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          fixture.answer = _ => _text_response("synthetic-provider-error-marker", 503)
          When("the failed exchange is followed by header use, explicit cancellation and a new successful authorization")
          val failed = fixture.complete(handoff)
          val uncertain = fixture.connection.statusC(fixture.context()).TAKE
          val blocked = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val unchanged = Files.readAllBytes(fixture.file).toVector
          val cancellation = fixture.connection.beginC(fixture.context()).TAKE
          val denied = fixture.connection.completeC(_state(cancellation), cancellation.browserProof, None, Some("access_denied"), fixture.uow(fixture.context()))
          val stillblocked = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val afterdenial = fixture.connection.statusC(fixture.context()).TAKE
          fixture.answer = _ => _response("synthetic-reauthorized-access", Some("synthetic-reauthorized-refresh"))
          val recovered = fixture.complete(fixture.connection.beginC(fixture.context()).TAKE)
          val header = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          Then("failure never restores cached authority or retries, denial preserves uncertainty, and new authorization clears it")
          _same_failure(failed, Consequence.serviceUnavailable("OAuth authorization service is unavailable"))
          Vector(blocked, stillblocked).foreach(_same_failure(_, _reconciliation))
          uncertain.state shouldBe OAuthConnectionState.Uncertain
          uncertain.expiresAt shouldBe Some(_observed.plusSeconds(600))
          uncertain.canRefresh shouldBe true
          uncertain.lastOutcome shouldBe Some(OAuthConnectionOutcome.Failed)
          uncertain.updating shouldBe false
          unchanged shouldBe old
          denied.TAKE.state shouldBe OAuthConnectionState.Uncertain
          afterdenial.lastOutcome shouldBe Some(OAuthConnectionOutcome.Denied)
          recovered.TAKE.state shouldBe OAuthConnectionState.Connected
          recovered.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Authorized)
          header.TAKE shouldBe Map("Authorization" -> "Bearer synthetic-reauthorized-access")
          fixture.requests.size shouldBe 2
          fixture.store.saves shouldBe 1
        }
      }

      "preserves the cached expired grant and structured native refresh failure without refetching or consuming pending flow" in {
        Given("R4: an expired grant with a pending browser flow and a denied native refresh")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)))
          fixture.connection.beginC(fixture.context()).TAKE
          val before = Files.readAllBytes(fixture.file).toVector
          fixture.answer = _ => _text_response("synthetic-provider-error-marker", 401)
          When("refresh fails and subsequent private header acquisition is attempted")
          val failed = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val blocked = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val status = fixture.connection.statusC(fixture.context()).TAKE
          Then("the original native refusal survives and no automatic refetch, save or browser-flow consumption occurs")
          _same_failure(failed, Consequence.securityPermissionDenied("OAuth authorization was denied"))
          _same_failure(blocked, _reconciliation)
          status.state shouldBe OAuthConnectionState.Uncertain
          status.authorizationPending shouldBe true
          status.canRefresh shouldBe true
          status.expiresAt shouldBe Some(_observed.minusSeconds(1))
          status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Failed)
          status.updating shouldBe false
          Files.readAllBytes(fixture.file).toVector shouldBe before
          fixture.requests.size shouldBe 1
          fixture.store.saves shouldBe 0
        }
      }

      "preserves the original save failure and old cache before replacement without returning an exchanged header" in {
        Given("R4: an expired grant and a private store that refuses the refreshed save before mutation")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)))
          val failure: Consequence[Unit] = Consequence.serviceUnavailable("OAuth credential storage is unavailable")
          fixture.store.saving = Some(() => failure)
          fixture.answer = _ => _response("synthetic-unsaved-access", Some("synthetic-unsaved-refresh"))
          val before = Files.readAllBytes(fixture.file).toVector
          When("the one native refresh cannot be saved and a later header is requested")
          val outcome = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val blocked = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val status = fixture.connection.statusC(fixture.context()).TAKE
          Then("the exact custody Conclusion is retained and neither old nor unsaved credentials can be used")
          _failure(outcome) should be theSameInstanceAs _failure(failure)
          _same_failure(blocked, _reconciliation)
          status.state shouldBe OAuthConnectionState.Uncertain
          status.expiresAt shouldBe Some(_observed.minusSeconds(1))
          status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Failed)
          status.updating shouldBe false
          Files.readAllBytes(fixture.file).toVector shouldBe before
          fixture.requests.size shouldBe 1
          fixture.store.saves shouldBe 1
        }
      }

      "redacts unexpected local mutation exceptions and releases busy for an explicit forget" in {
        Given("R4: a private custody capability throwing a synthetic local exception after native exchange")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)))
          fixture.store.saving = Some(() => throw new IOException("synthetic-local-token-marker " + fixture.file))
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          When("the save throws and the owner then explicitly disconnects")
          val failed = fixture.complete(handoff)
          val uncertain = fixture.connection.statusC(fixture.context()).TAKE
          val forgotten = fixture.connection.forgetC(fixture.context())
          Then("the local exception becomes a fixed failure, the gate is released and explicit forget can recover")
          _same_failure(failed, _unavailable)
          _failure(failed).getException shouldBe None
          uncertain.state shouldBe OAuthConnectionState.Uncertain
          uncertain.updating shouldBe false
          forgotten.TAKE.state shouldBe OAuthConnectionState.AuthorizationRequired
          forgotten.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Disconnected)
          fixture.requests.size shouldBe 1
          fixture.store.forgets shouldBe 1
          _disclosures(_failure(failed)).foreach { output =>
            output should not include "synthetic-local-token-marker"
            output should not include fixture.file.toString
          }
        }
      }
    }

    "E7 actual custody uncertainty and exact local disconnect" which {
      "successfully forgets only the selected local grant and pending flow while preserving domain data" in {
        Given("R4: a working selected grant and pending flow beside an unrelated domain file")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)))
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          When("the owner explicitly forgets and the prior browser later returns")
          val forgotten = fixture.connection.forgetC(fixture.context())
          val replay = fixture.complete(handoff)
          val status = fixture.connection.statusC(fixture.context()).TAKE
          val remaining = _names(root)
          val sentinel = Files.readString(fixture.sentinel, StandardCharsets.UTF_8)
          Then("local disconnect is truthful, the old flow cannot run and neither domain deletion nor Google revoke occurs")
          forgotten.TAKE.state shouldBe OAuthConnectionState.AuthorizationRequired
          status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Disconnected)
          status.expiresAt shouldBe None
          status.canRefresh shouldBe false
          status.authorizationPending shouldBe false
          status.updating shouldBe false
          _same_failure(replay, _flow_invalid)
          remaining shouldBe Set("operator.json", "domain-sentinel.txt")
          sentinel shouldBe "retained-domain-data"
          fixture.requests shouldBe empty
          fixture.store.forgets shouldBe 1
          fixture.store.saves shouldBe 0
        }
      }

      "treats real post-rename durability failure as uncertain and recovers only after a new explicit authorization" in {
        Given("R4: a working prior grant and actual private atomic save with switchable directory durability")
        _with_root { root =>
          val durability = new SwitchDurability
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)), durability = durability)
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          fixture.answer = _ => _response("synthetic-durable-new-access", Some("synthetic-durable-new-refresh"))
          durability.failing = true
          When("native exchange renames the real grant but directory sync fails, followed by a fresh explicit recovery flow")
          val failed = fixture.complete(handoff)
          val ondisk = fixture.configuration._store.loadC().TAKE.get
          val uncertain = fixture.connection.statusC(fixture.context()).TAKE
          val blocked = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val replay = fixture.complete(handoff)
          val before = fixture.requests.size
          durability.failing = false
          val recovered = fixture.complete(fixture.connection.beginC(fixture.context()).TAKE)
          Then("real readback proves the changed file while uncertainty blocks all automatic credential reuse and code replay")
          _same_failure(failed, Consequence.stateConflict("OAuth credential storage update outcome is uncertain"))
          ondisk.accessToken shouldBe "synthetic-durable-new-access"
          ondisk.refreshToken shouldBe Some("synthetic-durable-new-refresh")
          uncertain.state shouldBe OAuthConnectionState.Uncertain
          uncertain.expiresAt shouldBe Some(_observed.plusSeconds(600))
          uncertain.canRefresh shouldBe true
          uncertain.lastOutcome shouldBe Some(OAuthConnectionOutcome.Failed)
          uncertain.updating shouldBe false
          _same_failure(blocked, _reconciliation)
          _same_failure(replay, _flow_invalid)
          before shouldBe 1
          recovered.TAKE.state shouldBe OAuthConnectionState.Connected
          recovered.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Authorized)
          fixture.requests.size shouldBe 2
          fixture.store.saves shouldBe 2
          _names(root) shouldBe Set("operator.json", "grant.json", "domain-sentinel.txt")
        }
      }

      "clears cached use and pending intent before a failed forget without claiming that the retained file was removed" in {
        Given("R4: a working local grant, pending browser request and a custody refusal before deletion")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)))
          val handoff = fixture.connection.beginC(fixture.context()).TAKE
          val before = Files.readAllBytes(fixture.file).toVector
          val failure: Consequence[Unit] = Consequence.serviceUnavailable("OAuth credential storage is unavailable")
          fixture.store.forgetting = Some(() => failure)
          When("explicit forget fails and the owner retries only that explicit local disconnect")
          val failed = fixture.connection.forgetC(fixture.context())
          val status = fixture.connection.statusC(fixture.context()).TAKE
          val bytes = Files.readAllBytes(fixture.file).toVector
          val blocked = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          val replay = fixture.complete(handoff)
          fixture.store.forgetting = None
          val recovered = fixture.connection.forgetC(fixture.context())
          Then("failure does not roll back cached authority and successful explicit forget clears uncertainty without HTTP")
          _failure(failed) should be theSameInstanceAs _failure(failure)
          status.state shouldBe OAuthConnectionState.Uncertain
          status.expiresAt shouldBe None
          status.canRefresh shouldBe false
          status.authorizationPending shouldBe false
          status.lastOutcome shouldBe Some(OAuthConnectionOutcome.Failed)
          status.updating shouldBe false
          bytes shouldBe before
          _same_failure(blocked, _reconciliation)
          _same_failure(replay, _flow_invalid)
          recovered.TAKE.state shouldBe OAuthConnectionState.AuthorizationRequired
          recovered.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Disconnected)
          Files.exists(fixture.file) shouldBe false
          fixture.requests shouldBe empty
          fixture.store.forgets shouldBe 2
        }
      }

      "reports real post-deletion durability uncertainty and permits a successful explicit idempotent forget" in {
        Given("R4: a selected grant and an unrelated domain sentinel beside it")
        _with_root { root =>
          val durability = new SwitchDurability
          val fixture = new Harness(root, expiry = Some(_observed.plusSeconds(600)), durability = durability)
          fixture.connection.beginC(fixture.context()).TAKE
          durability.failing = true
          When("local deletion occurs but directory durability fails, then explicit forget is repeated")
          val failed = fixture.connection.forgetC(fixture.context())
          val absent = fixture.configuration._store.loadC().TAKE
          val uncertain = fixture.connection.statusC(fixture.context()).TAKE
          val blocked = fixture.connection.authorizationC(fixture.uow(fixture.context()), OAuthConnectionProviderKind.GoogleDrive)
          durability.failing = false
          val recovered = fixture.connection.forgetC(fixture.context())
          val repeated = fixture.connection.forgetC(fixture.context())
          val sentinel = Files.readString(fixture.sentinel, StandardCharsets.UTF_8)
          Then("absence is real but the failed attempt claims no successful disconnect, and no remote revoke or domain deletion occurs")
          _same_failure(failed, Consequence.stateConflict("OAuth credential storage update outcome is uncertain"))
          absent shouldBe None
          uncertain.state shouldBe OAuthConnectionState.Uncertain
          uncertain.expiresAt shouldBe None
          uncertain.canRefresh shouldBe false
          uncertain.authorizationPending shouldBe false
          _same_failure(blocked, _reconciliation)
          Vector(recovered, repeated).foreach { result =>
            result.TAKE.state shouldBe OAuthConnectionState.AuthorizationRequired
            result.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Disconnected)
          }
          sentinel shouldBe "retained-domain-data"
          _names(root) shouldBe Set("operator.json", "domain-sentinel.txt")
          fixture.requests shouldBe empty
          fixture.store.forgets shouldBe 3
        }
      }
    }

    "E8 actual in-flight mutation" which {
      "refuses competing effects while native completion runs and permits safe status without holding its monitor over HTTP" in {
        Given("R3,R4: a matched flow and a bounded latch-controlled native POST")
        _with_root { root =>
          val fixture = new Harness(root)
          val context = fixture.context()
          val handoff = fixture.connection.beginC(context).TAKE
          val draws = fixture.entropy.draws
          When("completion waits in the driver while another thread requests every competing effect and status")
          val observed = _while_native_blocked(fixture,
            () => fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(_code), None, fixture.uow(context)),
            () => Vector(fixture.connection.beginC(context),
              fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(_code), None, fixture.uow(context)),
              fixture.connection.authorizationC(fixture.uow(context), OAuthConnectionProviderKind.GoogleDrive),
              fixture.connection.forgetC(context), fixture.connection.statusC(context)))
          val next = fixture.connection.beginC(context)
          Then("competing mutations fail before effects, status responds during HTTP and finally releases and joins both threads")
          observed.entered shouldBe true
          observed.responsive shouldBe true
          observed.joined shouldBe true
          observed.failures shouldBe empty
          observed.secondary.get.take(4).foreach(_same_failure(_, _busy))
          val status = observed.secondary.get.last.TAKE.asInstanceOf[OAuthConnectionStatus]
          status.state shouldBe OAuthConnectionState.AuthorizationRequired
          status.updating shouldBe true
          status.authorizationPending shouldBe false
          observed.primary.get.TAKE.state shouldBe OAuthConnectionState.Connected
          next.isSuccess shouldBe true
          fixture.entropy.draws shouldBe draws + 3
          fixture.store.saves shouldBe 1
          fixture.store.forgets shouldBe 0
          fixture.requests.size shouldBe 1
        }
      }

      "preserves an unrelated pending browser flow when competing completion is refused during refresh" in {
        Given("R3,R4: an expired refreshable grant, pending browser flow and blocked native refresh")
        _with_root { root =>
          val fixture = new Harness(root, expiry = Some(_observed.minusSeconds(1)))
          val context = fixture.context()
          val handoff = fixture.connection.beginC(context).TAKE
          val draws = fixture.entropy.draws
          When("refresh waits in native HTTP while begin, matching completion, header, forget and status compete")
          val observed = _while_native_blocked(fixture,
            () => fixture.connection.authorizationC(fixture.uow(context), OAuthConnectionProviderKind.GoogleDrive),
            () => Vector(fixture.connection.beginC(context),
              fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(_code), None, fixture.uow(context)),
              fixture.connection.authorizationC(fixture.uow(context), OAuthConnectionProviderKind.GoogleDrive),
              fixture.connection.forgetC(context), fixture.connection.statusC(context)))
          val pending = fixture.connection.statusC(context).TAKE
          val completed = fixture.complete(handoff)
          Then("busy refusals consume no pending match, and the original browser flow succeeds after finally release")
          observed.entered shouldBe true
          observed.responsive shouldBe true
          observed.joined shouldBe true
          observed.failures shouldBe empty
          observed.secondary.get.take(4).foreach(_same_failure(_, _busy))
          val status = observed.secondary.get.last.TAKE.asInstanceOf[OAuthConnectionStatus]
          status.state shouldBe OAuthConnectionState.Expired
          status.authorizationPending shouldBe true
          status.updating shouldBe true
          observed.primary.get.isSuccess shouldBe true
          pending.authorizationPending shouldBe true
          pending.updating shouldBe false
          fixture.entropy.draws shouldBe draws
          completed.TAKE.lastOutcome shouldBe Some(OAuthConnectionOutcome.Authorized)
          fixture.requests.size shouldBe 2
          fixture.store.saves shouldBe 2
          fixture.store.forgets shouldBe 0
        }
      }
    }

    "E9 private displays and native diagnostics" which {
      "retains native transport structure while standard, full, log and real CallTree projections omit every secret marker" in {
        Given("R2,R5: enabled native CallTree and a controlled driver exception carrying synthetic secret, flow and path markers")
        _with_root { root =>
          val fixture = new Harness(root)
          val context = fixture.context()
          val handoff = fixture.connection.beginC(context).TAKE
          val markers = Vector(_code, _state(handoff), handoff.browserProof, _encoded(2), _access, _refresh,
            "synthetic-client-secret-marker", "synthetic-provider-error-marker", fixture.file.toString)
          val exception = new IOException(markers.mkString(" "))
          fixture.answer = _ => throw exception
          When("a matching callback encounters the native transport exception and then requests private headers")
          val failure = _failure(fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(_code), None, fixture.uow(context)))
          val blocked = fixture.connection.authorizationC(fixture.uow(context), OAuthConnectionProviderKind.GoogleDrive)
          val status = fixture.connection.statusC(context).TAKE
          val outputs = _disclosures(failure) ++ _disclosures(_failure(blocked)) :+ fixture.trace(context)
          Then("the original native exception and taxonomy survive while all real diagnostics remain redacted")
          failure.getException.get should be theSameInstanceAs exception
          failure.status shouldBe Conclusion.from(exception).status
          failure.observation.taxonomy shouldBe Conclusion.from(exception).observation.taxonomy
          fixture.trace(context) should include ("uow:http:post")
          outputs.foreach(output => markers.foreach(marker => output should not include marker))
          _disclosures(failure).foreach { output =>
            val json = parser.parse(output).toOption.get
            json.hcursor.downField("status").focus shouldBe parser.parse(RecordEncoder.json(failure.status.toRecord)).toOption
            json.hcursor.downField("observation").downField("taxonomy").focus shouldBe
              parser.parse(RecordEncoder.json(failure.observation.taxonomy.toRecord)).toOption
          }
          status.state shouldBe OAuthConnectionState.Uncertain
          status.updating shouldBe false
          fixture.requests.size shouldBe 1
          fixture.store.saves shouldBe 0
        }
      }

      "redacts successful native traces, private object displays and fixed failure projections without hiding the intentional browser handoff" in {
        Given("R1,R5: a successful native authorization and rejected foreign and malformed callbacks")
        _with_root { root =>
          val fixture = new Harness(root)
          val context = fixture.context()
          val handoff = fixture.connection.beginC(context).TAKE
          val foreign = fixture.connection.completeC("z" * 43, handoff.browserProof, Some(_code), None, fixture.uow(context))
          When("the genuine code completes and another explicit flow receives malformed provider input")
          val status = fixture.connection.completeC(_state(handoff), handoff.browserProof, Some(_code), None, fixture.uow(context)).TAKE
          val next = fixture.connection.beginC(context).TAKE
          val malformed = fixture.connection.completeC(_state(next), next.browserProof, Some("synthetic-invalid-code marker"), None, fixture.uow(context))
          val outputs = Vector(fixture.connection.toString, fixture.configuration.toString, fixture.configuration._client.toString,
            fixture.store.toString, handoff.toString, next.toString, status.toString, fixture.trace(context)) ++
            Vector(foreign, malformed).flatMap(value => _disclosures(_failure(value)))
          val markers = Vector(_owner, _code, _access, _refresh, _state(handoff), handoff.browserProof, _encoded(2),
            _state(next), next.browserProof, "synthetic-client-secret-marker", "synthetic-invalid-code", fixture.file.toString)
          Then("only explicit browser delivery contains its URI state, while displays and actual projections omit all private markers")
          fixture.connection.toString shouldBe "OAuthConnection(<redacted>)"
          handoff.toString shouldBe "OAuthAuthorizationHandoff(<redacted>)"
          outputs.foreach(output => markers.foreach(marker => output should not include marker))
          handoff.authorizationUri should include (_state(handoff))
          handoff.authorizationUri should include ("client_id=synthetic-client-marker")
          handoff.authorizationUri should not include handoff.browserProof
          fixture.trace(context) should include ("uow:http:post")
          fixture.requests.size shouldBe 1
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

object OAuthConnectionSpec {
  private val _component = ComponentId("org.example.EditingStudio")
  private val _instance = ComponentInstanceId(_component, "studio-one")
  private val _owner = "synthetic-owner-marker"
  private val _connection_id = "synthetic-drive-connection"
  private val _scopes = Vector("scope:second", "scope:first")
  private val _observed = Instant.parse("2026-10-21T00:00:00Z")
  private val _authorization = "https://accounts.google.com/o/oauth2/v2/auth"
  private val _token = "https://oauth2.googleapis.com/token"
  private val _code = "synthetic-code-marker"
  private val _access = "synthetic-access-marker"
  private val _refresh = "synthetic-refresh-marker"
  private val _directory_permissions = PosixFilePermissions.fromString("rwx------")
  private val _file_permissions = PosixFilePermissions.fromString("rw-------")
  private val _directory_attribute = PosixFilePermissions.asFileAttribute(_directory_permissions)
  private def _scope_denied: Consequence[Nothing] = Consequence.securityPermissionDenied("OAuth connection scope is not allowed")
  private def _owner_denied: Consequence[Nothing] = Consequence.securityPermissionDenied("OAuth connection owner is not allowed")
  private def _flow_invalid: Consequence[Nothing] = Consequence.securityPermissionDenied("OAuth authorization flow is invalid")
  private def _clock_invalid: Consequence[Nothing] = Consequence.stateConflict("OAuth connection clock is invalid")
  private def _record_invalid: Consequence[Nothing] = Consequence.resourceInvalid("OAuth credential storage record is invalid")
  private def _unavailable: Consequence[Nothing] = Consequence.serviceUnavailable("OAuth connection operation is unavailable")
  private def _requires_authorization: Consequence[Nothing] = Consequence.securityPermissionDenied("OAuth connection requires authorization")
  private def _reconciliation: Consequence[Nothing] = Consequence.stateConflict("OAuth connection requires reconciliation")
  private def _busy: Consequence[Nothing] = Consequence.stateConflict("OAuth connection update is in progress")

  private final class Draws extends OAuthEntropy {
    private val _count = new AtomicInteger
    var next: Option[() => Array[Byte]] = None
    def draws: Int = _count.get()
    def tokenBytes(): Array[Byte] = {
      val index = _count.incrementAndGet()
      next.fold(Array.fill[Byte](32)(index.toByte))(_.apply())
    }
    override def toString: String = "Draws(<redacted>)"
  }

  private final class MutableClock(initial: Instant) extends Clock {
    private val _instant = new AtomicReference[Instant](initial)
    @volatile var failing: Boolean = false
    def set(value: Instant): Unit = _instant.set(value)
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = this
    def instant(): Instant = if (failing) throw new IOException("synthetic-clock-secret-marker") else _instant.get()
  }

  private final class CountingStore(delegate: OAuthCredentialStore) extends OAuthCredentialStore {
    private val _loads = new AtomicInteger
    private val _saves = new AtomicInteger
    private val _forgets = new AtomicInteger
    var loaded: Option[Consequence[Option[OAuthCredential]]] = None
    var saving: Option[() => Consequence[Unit]] = None
    var forgetting: Option[() => Consequence[Unit]] = None
    def loads: Int = _loads.get()
    def saves: Int = _saves.get()
    def forgets: Int = _forgets.get()
    def loadC(): Consequence[Option[OAuthCredential]] = {
      _loads.incrementAndGet()
      loaded.getOrElse(delegate.loadC())
    }
    def saveC(credential: OAuthCredential): Consequence[Unit] = {
      _saves.incrementAndGet()
      saving.fold(delegate.saveC(credential))(_.apply())
    }
    def forgetC(): Consequence[Unit] = {
      _forgets.incrementAndGet()
      forgetting.fold(delegate.forgetC())(_.apply())
    }
  }

  private final class SwitchDurability extends OAuthCredentialDurability {
    @volatile var failing: Boolean = false
    def forceDirectory(directory: Path): Unit = {
      if (failing) throw new IOException("synthetic-durability-path-marker")
      OAuthCredentialDurability.posix.forceDirectory(directory)
    }
  }

  private final class Request(val path: String, val text: Option[String], val headers: Map[String, String],
    val properties: Vector[Property]) {
    override def toString: String = "Request(<redacted>)"
  }

  private final class Harness(root: Path, expiry: Option[Instant] = None, refresh: Option[String] = Some(_refresh),
    origins: Vector[String] = Vector("https://oauth2.googleapis.com"), durability: OAuthCredentialDurability = OAuthCredentialDurability.posix) {
    val configuration: OAuthConnectionConfiguration = _configuration(root, _document(root))
    val file: Path = root.resolve("grant.json")
    val sentinel: Path = _write(root.resolve("domain-sentinel.txt"), "retained-domain-data")
    val clock: MutableClock = new MutableClock(_observed)
    val entropy: Draws = new Draws
    private val _selected = new PrivateFileOAuthCredentialStore(file, _connection_id, _owner, configuration._client, durability)
    expiry.foreach { value => _selected.saveC(configuration._client._restore_credential_c(_access, refresh, _scopes, value).TAKE).TAKE }
    val store: CountingStore = new CountingStore(_selected)
    val connection: OAuthConnection = OAuthConnection.createC(configuration, store, entropy).TAKE
    private val _requests = new AtomicReference(Vector.empty[Request])
    private val _recorded = new AtomicReference(Vector.empty[String])
    @volatile var answer: Request => HttpResponse = _ => _response()
    private val _driver = new HttpDriver {
      def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        throw new AssertionError("OAuth runtime only admits POST")
      def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
        val request = new Request(path, body, headers, properties)
        _requests.updateAndGet(values => values :+ request)
        answer(request)
      }
      def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        throw new AssertionError("OAuth runtime only admits POST")
    }
    private val _recorder = new CommitRecorder {
      def record(message: String): Unit = { _recorded.updateAndGet(values => values :+ message); () }
    }
    def requests: Vector[Request] = _requests.get()
    def recorded: Vector[String] = _recorded.get()
    def reload(): OAuthConnectionConfiguration = OAuthConnectionConfiguration.loadC(
      SecretReference.fromConfiguration(root.resolve("operator.json").toUri.toString).TAKE, _component, _instance).TAKE
    def context(ownerId: String = _owner, authentication: String = "provider", componentId: ComponentId = _component,
      instanceId: ComponentInstanceId = _instance, scoped: Boolean = true): ExecutionContext = {
      val security = SecurityContext(new Principal {
        val id: PrincipalId = PrincipalId(ownerId)
        val attributes: Map[String, String] = if (authentication == "anonymous" || authentication == "provider-anonymous")
          Map("anonymous" -> "true") else Map("authenticated" -> "true",
            SecuritySubject.AuthenticationProvenanceAttribute -> SecuritySubject.ProviderAuthenticationProvenance)
      }, Set.empty, SecurityLevel("user"), if (authentication == "anonymous") SubjectKind.Anonymous else SubjectKind.User)
      authentication match {
        case "provider" | "provider-anonymous" => SecurityAuthenticationProvenance.providerAuthenticated(security)
        case "local" => SecurityAuthenticationProvenance.trustedLocalAuthenticated(security)
        case _ => ()
      }
      val base = ExecutionContext.withSecurityContext(ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(clock), true), security)
      val admission = HttpExecutionAdmission.fromRuntimeC("https://oauth2.googleapis.com/", HttpExecutionLimits(1000, 1000, 1048576),
        Map(_component -> HttpExecutionGrant(origins, Set(HttpExecutionMethod.Post)))).TAKE
      val parent = ScopeContext.Instance(base.cncfCore.scope.core.copy(httpExecutionAdmissionOption = Some(admission)))
      if (!scoped) base.withScope(parent)
      else {
        val component = Component.create(componentId.name, componentId, instanceId, Protocol.empty)
        component.withScopeContext(parent)
        base.withScope(component.scopeContext)
      }
    }
    def uow(context: ExecutionContext): CountedUow = new CountedUow(context, _driver, _recorder)
    def complete(handoff: OAuthAuthorizationHandoff): Consequence[OAuthConnectionStatus] = connection.completeC(
      _state(handoff), handoff.browserProof, Some(_code), None, uow(context()))
    def trace(context: ExecutionContext): String = context.cncfCore.observability.callTreeContext.build()
      .map(tree => RecordEncoder.json(tree.toRecord)).getOrElse("")
  }

  private final class CountedUow(context: ExecutionContext, driver: HttpDriver, recorder: CommitRecorder)
      extends UnitOfWork(context, recorder = recorder) {
    private val _acquisitions = new AtomicInteger
    private val _commits = new AtomicInteger
    private val _aborts = new AtomicInteger
    def acquisitions: Int = _acquisitions.get()
    def commits: Int = _commits.get()
    def aborts: Int = _aborts.get()
    override def httpDriver: HttpDriver = { _acquisitions.incrementAndGet(); driver }
    override def commit() = { _commits.incrementAndGet(); super.commit() }
    override def abort() = { _aborts.incrementAndGet(); super.abort() }
  }

  private final class ConcurrentObservation[A](val entered: Boolean, val responsive: Boolean,
    val primary: Option[Consequence[A]], val secondary: Option[Vector[Consequence[?]]],
    val failures: Vector[Throwable], val joined: Boolean) {
    override def toString: String = "ConcurrentObservation(<redacted>)"
  }

  private def _while_native_blocked[A](fixture: Harness, operation: () => Consequence[A],
    contenders: () => Vector[Consequence[?]]): ConcurrentObservation[A] = {
    val entered = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    val completed = new CountDownLatch(1)
    val primaryresult = new AtomicReference[Option[Consequence[A]]](None)
    val secondaryresult = new AtomicReference[Option[Vector[Consequence[?]]]](None)
    val failures = new AtomicReference(Vector.empty[Throwable])
    fixture.answer = _ => {
      entered.countDown()
      if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Bounded OAuth fixture timed out")
      _response()
    }
    val primary = new Thread(() => {
      try primaryresult.set(Some(operation()))
      catch { case error: Throwable => failures.updateAndGet(values => values :+ error); () }
    }, "oauth-primary-spec")
    val secondary = new Thread(() => {
      try secondaryresult.set(Some(contenders()))
      catch { case error: Throwable => failures.updateAndGet(values => values :+ error); () }
      finally completed.countDown()
    }, "oauth-contender-spec")
    var admitted = false
    var responsive = false
    var started = false
    try {
      primary.start()
      admitted = entered.await(5, TimeUnit.SECONDS)
      if (admitted) {
        secondary.start()
        started = true
        responsive = completed.await(2, TimeUnit.SECONDS)
      }
    } finally {
      release.countDown()
      primary.join(5000)
      if (started) secondary.join(5000)
      if (primary.isAlive) { primary.interrupt(); primary.join(5000) }
      if (started && secondary.isAlive) { secondary.interrupt(); secondary.join(5000) }
      fixture.answer = _ => _response()
    }
    new ConcurrentObservation(admitted, responsive, primaryresult.get(), secondaryresult.get(), failures.get(),
      !primary.isAlive && !secondary.isAlive)
  }

  private def _document(root: Path): Json = Json.obj(
    "schema" -> Json.fromString("cncf.oauth-connection/1"), "connectionId" -> Json.fromString(_connection_id),
    "ownerId" -> Json.fromString(_owner), "provider" -> Json.fromString("google-drive"),
    "clientId" -> Json.fromString("synthetic-client-marker"), "clientSecret" -> Json.fromString("synthetic-client-secret-marker"),
    "redirectUri" -> Json.fromString(s"http://127.0.0.1:8123/web/${NamingConventions.toNormalizedSegment(_component.localId.value())}/connections/${_connection_id}/callback"),
    "scopes" -> Json.fromValues(_scopes.map(Json.fromString)), "credentialFile" -> Json.fromString(root.resolve("grant.json").toString),
    "returnPath" -> Json.fromString("/web/editing-studio/home"))
  private def _configuration(root: Path, document: Json): OAuthConnectionConfiguration = {
    val file = _write(root.resolve("operator.json"), document.noSpaces)
    OAuthConnectionConfiguration.loadC(SecretReference.fromConfiguration(file.toUri.toString).TAKE, _component, _instance).TAKE
  }
  private def _write(file: Path, text: String): Path = {
    Files.write(file, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    Files.setPosixFilePermissions(file, _file_permissions)
    file
  }
  private def _with_root[A](body: Path => A): A = {
    val base = Files.createDirectories(Paths.get("target", "oauth-connection-spec")).toRealPath()
    val root = Files.createTempDirectory(base, "runtime-", _directory_attribute).toRealPath()
    try body(root) finally {
      Using.resource(Files.walk(root)) { stream =>
        stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => Files.deleteIfExists(path))
      }
    }
  }
  private def _names(root: Path): Set[String] = Using.resource(Files.list(root))(_.iterator().asScala.map(_.getFileName.toString).toSet)
  private def _encoded(index: Int): String = Base64.getUrlEncoder.withoutPadding.encodeToString(Array.fill[Byte](32)(index.toByte))
  private def _state(handoff: OAuthAuthorizationHandoff): String = _query(handoff)("state")
  private def _query(handoff: OAuthAuthorizationHandoff): Map[String, String] = _decode(new URI(handoff.authorizationUri).getRawQuery)
  private def _decode(form: String): Map[String, String] = form.split("&", -1).map { part =>
    val pair = part.split("=", 2)
    URLDecoder.decode(pair(0), StandardCharsets.UTF_8) -> URLDecoder.decode(pair(1), StandardCharsets.UTF_8)
  }.toMap
  private def _failure(outcome: Consequence[?]): Conclusion = outcome match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => throw new AssertionError("Expected a structured OAuth runtime failure")
  }
  private def _matches_failure(actual: Consequence[?], expected: Consequence[?]): Boolean = actual match {
    case Consequence.Failure(conclusion) =>
      val reference = _failure(expected)
      conclusion.status == reference.status && conclusion.observation.taxonomy == reference.observation.taxonomy &&
        conclusion.cause.getMessage == reference.cause.getMessage
    case _ => false
  }
  private def _disclosures(conclusion: Conclusion): Vector[String] = Vector(
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Standard),
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Full), ConclusionDisclosure.log(conclusion)).map(RecordEncoder.json)
  private def _response(access: String = _access, refresh: Option[String] = Some(_refresh), seconds: Int = 3600): HttpResponse = {
    val fields = Vector("access_token" -> Json.fromString(access), "token_type" -> Json.fromString("Bearer"),
      "expires_in" -> Json.fromInt(seconds)) ++ refresh.map(value => "refresh_token" -> Json.fromString(value))
    _text_response(Json.obj(fields*).noSpaces, 200)
  }
  private def _text_response(text: String, status: Int): HttpResponse = HttpResponse.Text(
    HttpStatus.fromInt(status).getOrElse(HttpStatus.InternalServerError), ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
    Bag.text(text, StandardCharsets.UTF_8), Record.empty)
}
