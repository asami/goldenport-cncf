package org.goldenport.cncf.http

import java.io.IOException
import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.nio.file.attribute.PosixFilePermissions
import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import cats.effect.IO
import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.global
import io.circe.{Json, parser}
import org.http4s.{Header, Headers, Method, Request, Response, Uri}
import org.typelevel.ci.CIString
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentId, ComponentInit, ComponentInstanceId, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.config.{ComponentParameterKey, OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, ExecutionProfileResolver, GlobalRuntimeContext, PrincipalId}
import org.goldenport.cncf.filestore.{FileStoreRef, GoogleDriveCredentials, GoogleDriveFileStore}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder, StructuredLogEvent}
import org.goldenport.cncf.oauth.OAuthConnectionRuntime
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult, IngressSecurityResolver, SecuritySubject}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, GenericSubsystemAuthenticationBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemLocalSubjectBinding, GenericSubsystemSecurityBinding, Subsystem, SubsystemCurrentUserEvidence, SubsystemUserMode}
import org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
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
final class OAuthConnectionHttpSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OAuthConnectionHttpSpec.*

  "OAuth connection HTTP on the normal listener" should {
    "H1 discovery and owner authority" which {
      "discover one safe connection without automatically beginning authorization" in {
        Given("a normally bootstrapped configured component and a provider-authenticated owner")
        _with_fixture() { fixture =>
          When("the owner lists connections and reads the configured status")
          val listed = fixture.send(fixture.request(Method.GET, _base))
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          Then("only the safe vocabulary is returned, with no pending flow, credential or native HTTP")
          listed.status.code shouldBe 200
          _json(listed).hcursor.get[Vector[Json]]("connections").toOption shouldBe Some(Vector(_json(status)))
          _json(status).asObject.get.keys.toSet shouldBe _status_keys
          _json(status).hcursor.get[String]("state").toOption shouldBe Some("authorization-required")
          _pending(status) shouldBe false
          fixture.driver.requests shouldBe empty
          Files.exists(fixture.file) shouldBe false
          Vector(listed, status).foreach { response => _secure(response); _private_absent(_body(response), fixture) }
          fixture.component.initializations shouldBe 1
        }
      }

      "return an empty list only after real provider ingress for absent configuration" in {
        Given("the same declared capability without a configuration reference")
        _with_fixture(configured = false) { fixture =>
          When("the owner and an unauthenticated caller each discover the absent connection")
          val owner = fixture.send(fixture.request(Method.GET, _base))
          val anonymous = fixture.send(fixture.request(Method.GET, _base, bearer = None))
          val missinghost = fixture.send(_without(fixture.request(Method.GET, _base), "Host"))
          val malformedhost = fixture.send(fixture.request(Method.GET, _base, host = "invalid host"))
          Then("only the authenticated request returns an empty list and no runtime effect occurs")
          _json(owner) shouldBe Json.obj("connections" -> Json.arr())
          anonymous.status.code shouldBe 403
          Vector(missinghost, malformedhost).foreach(_.status.code shouldBe 403)
          fixture.driver.requests shouldBe empty
          fixture.component.oauthConnection shouldBe None
        }
      }

      "refuse a real fixed local subject even when its principal matches the configured owner" in {
        Given("normal standalone local-subject wiring with the configured owner ID and no authentication provider")
        Vector(true, false).foreach { configured =>
          _with_fixture(configured = configured, localonly = true) { fixture =>
            When("the local subject requests connection discovery or owner authorization through normal ingress")
            val listed = fixture.send(fixture.request(Method.GET, _base, bearer = None))
            val authorize = fixture.send(fixture.request(Method.POST, _authorize_path, bearer = None))
            val local = IngressSecurityResolver.resolve(fixture.owner.executionProfileC.TAKE,
              fixture.component.logic.executionContext(), Map.empty[String, String]).TAKE.executionContext
            Then("trusted-local authentication does not satisfy provider-owned management, including absent list discovery")
            listed.status.code shouldBe 403
            authorize.status.code shouldBe (if (configured) 403 else 404)
            SecuritySubject.from(local.security).isTrustedLocalAuthenticated shouldBe true
            SecuritySubject.from(local.security).isProviderAuthenticated shouldBe false
            local.security.principal.id.value shouldBe _owner
            fixture.driver.requests shouldBe empty
            fixture.provider.requests shouldBe empty
            Files.exists(fixture.file) shouldBe false
          }
        }
      }

      "project generic provider identity and genuine expiry without account or token claims" in {
        Given("a generic provider configuration using Google endpoints but retaining generic provenance")
        _with_fixture(generic = true) { fixture =>
          val begin = fixture.begin()
          val completed = fixture.send(fixture.callback(begin))
          When("the bound runtime clock reaches the saved grant expiry and the owner requests status")
          fixture.globalContext.executionProfileRuntime.testControl.get.advanceBy(Duration.ofSeconds(3600))
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          Then("safe status says generic and expired with actual optional facts and no identity or credential disclosure")
          _returned(completed, cleared = true)
          _json(status).asObject.get.keys.toSet shouldBe (_status_keys ++ Set("expiresAt", "lastOutcome"))
          _json(status).hcursor.get[String]("provider").toOption shouldBe Some("generic")
          _json(status).hcursor.get[String]("state").toOption shouldBe Some("expired")
          _json(status).hcursor.get[Boolean]("canRefresh").toOption shouldBe Some(true)
          _private_absent(_body(status), fixture)
          fixture.driver.requests should have size 1
        }
      }

      "refuse missing, forged, invalid and foreign management identities before any effect" in {
        Given("an Authenticated profile with only the configured owner admitted by private runtime authority")
        _with_fixture() { fixture =>
          val requests = Vector(
            fixture.request(Method.GET, _base, bearer = None),
            fixture.request(Method.POST, _authorize_path, bearer = Some("invalid-bearer-marker")),
            fixture.request(Method.GET, _status_path, bearer = Some(_foreign_bearer)),
            fixture.request(Method.POST, _authorize_path, bearer = None).putHeaders(
              _header("authenticated", "true"), _header("principal_id", _owner),
              _header(SecuritySubject.AuthenticationProvenanceAttribute, SecuritySubject.ProviderAuthenticationProvenance),
              _header("cncf.security.privilege", "user"), _header("x-textus-session", "forged-local-session")))
          When("normal management ingress handles those requests")
          val responses = requests.map(fixture.send)
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          Then("all identities are refused without pending mutation, custody, HTTP or leaked credentials")
          responses.foreach { response => response.status.code shouldBe 403; _private_absent(_body(response), fixture) }
          _pending(status) shouldBe false
          fixture.driver.requests shouldBe empty
          Files.exists(fixture.file) shouldBe false
          fixture.owner.executionProfileC.TAKE.currentUserEvidence shouldBe SubsystemCurrentUserEvidence.Authenticated
        }
      }

      "capture unknown IDs, capability absence, aliases, encoded paths and suffixes before business fallback" in {
        Given("a canonical component and a normal listener with its generic routes still installed")
        _with_fixture() { fixture =>
          val paths = Vector("/web/org.example.HttpStudio/connections", "/web/http%2Dstudio/connections",
            _base + "/missing/status", _status_path + "/extra", _base + "/drive/unknown",
            "/web/system/connections", "/web/unknown-component/connections", _base + "/",
            "/web/http-studio/%63onnections")
          When("each noncanonical or unavailable path reaches the ordinary server routes")
          val responses = paths.map(path => fixture.send(fixture.request(Method.GET, path)))
          Then("each path has a fixed no-store JSON 404 and none begins a flow or calls HTTP")
          responses.foreach { response => response.status.code shouldBe 404; _secure(response); _body(response) should not include "<html" }
          fixture.driver.requests shouldBe empty
          _pending(fixture.send(fixture.request(Method.GET, _status_path))) shouldBe false
        }
      }

      "admit only disclosure selectors without promoting query values into request authority" in {
        Given("an authenticated owner and no pending flow")
        _with_fixture() { fixture =>
          val queries = Vector("ownerId=someone", "scope=extra", "redirectUri=bad", "returnPath=bad", "configuration=bad",
            "state=" + ("s" * 43), "x-textus-session=forged", "x=" + ("a" * 32768),
            Vector.fill(33)("textus.output.conclusion=full").mkString("&"))
          When("management requests carry unknown or out-of-bound query parameters")
          val responses = queries.map(query => fixture.send(fixture.request(Method.POST, _authorize_path + "?" + query)))
          Then("all requests fail as fixed arguments before begin and the pending state stays empty")
          responses.foreach(_.status.code shouldBe 400)
          fixture.driver.requests shouldBe empty
          _pending(fixture.send(fixture.request(Method.GET, _status_path))) shouldBe false
        }
      }
    }

    "H2 authenticated management and narrow anonymous completion" which {
      "complete a Google-style callback without Bearer under the Authenticated profile and use the saved grant" in {
        Given("normal factory bootstrap, MultiUser provider ingress, an admitted native driver and a bound manual clock")
        _with_fixture() { fixture =>
          val originalcontext = fixture.component.logic.executionContext()
          When("the owner explicitly authorizes and Google returns its code without ingress credentials")
          val begin = fixture.begin()
          val calls = fixture.provider.requests.size
          val callback = fixture.send(fixture.callback(begin))
          val callbackcalls = fixture.provider.requests.size
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          val ownercontext = fixture.authenticatedContext(_bearer)
          val drive = new UnitOfWorkInterpreter(ownercontext.runtime.unitOfWork).run(fixture.store.read(_reference))
          val anonymous = IngressSecurityResolver.resolve(fixture.owner.executionProfileC.TAKE,
            fixture.component.logic.executionContext(), Map.empty[String, String])
          val foreigncontext = fixture.authenticatedContext(_foreign_bearer)
          val foreign = new UnitOfWorkInterpreter(foreigncontext.runtime.unitOfWork).run(fixture.store.read(_reference))
          Then("completion stays anonymous, persists once through native POST and returns fixed status facts for later authenticated use")
          begin.response.status.code shouldBe 200
          _json(begin.response).asObject.get.keys.toSet shouldBe Set("authorizationUri")
          begin.uri should startWith ("https://accounts.google.com/o/oauth2/v2/auth?")
          begin.uri should include ("state=")
          begin.uri should not include begin.proof
          val cookie = _headers(begin.response, "Set-Cookie").head
          cookie should include ("Path=" + _callback_path)
          cookie should include ("HttpOnly")
          cookie should include ("SameSite=Lax")
          cookie should include ("Max-Age=300")
          cookie should not include "Domain="
          cookie should not include "Secure"
          _returned(callback, cleared = true)
          callbackcalls shouldBe calls
          _json(status).hcursor.get[String]("state").toOption shouldBe Some("connected")
          _json(status).hcursor.get[String]("expiresAt").toOption shouldBe Some(_observed.plusSeconds(3600).toString)
          fixture.driver.requests.map(_.method) shouldBe Vector("POST", "GET")
          fixture.driver.requests.head.path shouldBe "https://oauth2.googleapis.com/token"
          fixture.driver.requests.head.body.get should include ("grant_type=authorization_code")
          fixture.driver.requests.head.body.get should include ("code=" + _code)
          fixture.driver.requests.head.body.get should include ("redirect_uri=http%3A%2F%2F127.0.0.1%3A8123%2Fweb%2Fhttp-studio%2Fconnections%2Fdrive%2Fcallback")
          fixture.driver.requests.last.headers.get("Authorization") shouldBe Some("Bearer " + _access)
          drive.isSuccess shouldBe true
          Using.resource(drive.TAKE.content.openInputStream())(_.readAllBytes().toVector) shouldBe _octets
          anonymous.isSuccess shouldBe false
          foreign.isSuccess shouldBe false
          originalcontext.cncfCore.scope.parent.get should be theSameInstanceAs fixture.component.scopeContext
          ownercontext.runtime.httpDriver should be theSameInstanceAs fixture.driver
          ownercontext.clock should be theSameInstanceAs fixture.globalContext.executionProfileRuntime.runtimeClock.clock
          ownercontext.runtime.operationMode shouldBe OperationMode.Test
          fixture.provider.requests.foreach(request => request.attributes.values.mkString should not include begin.state)
          _grant(fixture).hcursor.get[String]("accessToken").toOption shouldBe Some(_access)
        }
      }

      "ignore incoming callback authentication material instead of upgrading or invoking its provider" in {
        Given("an owner-started browser flow and unrelated callback Bearer/session/privilege headers")
        _with_fixture() { fixture =>
          val begin = fixture.begin()
          val calls = fixture.provider.requests.size
          When("the callback carries unrelated credentials and extra bounded provider fields")
          val callback = fixture.send(fixture.callback(begin, extra = "&scope=ignored&authuser=1&error_uri=https%3A%2F%2Felsewhere.example%2F").putHeaders(
            _header("Authorization", "Bearer callback-ignored-bearer"), _header("x-textus-session", "callback-ignored-session"),
            _header("cncf.security.privilege", "system")))
          val callbackcalls = fixture.provider.requests.size
          val denied = fixture.send(fixture.request(Method.GET, _status_path, bearer = None))
          Then("only the flow completes, the callback never reaches authentication and anonymous management remains denied")
          _returned(callback, cleared = true)
          callbackcalls shouldBe calls
          denied.status.code shouldBe 403
          fixture.driver.requests.map(_.method) shouldBe Vector("POST")
          fixture.driver.requests.head.path should not include "elsewhere.example"
          fixture.provider.requests.foreach(request => request.attributes.values.mkString should not include "callback-ignored")
        }
      }
    }

    "H3 configured origin and empty-body admission" which {
      "admit HTTPS authority case and implicit or explicit 443 equivalence while retaining the redirect text" in {
        Given("an explicitly configured mixed-case HTTPS redirect and an authenticated owner")
        Vector("https://Studio.Example:443", "https://studio.example").foreach { configuredorigin =>
        _with_fixture(origin = configuredorigin) { fixture =>
          When("management and callback present equivalent URI origins")
          val response = fixture.send(fixture.request(Method.POST, _authorize_path, host = "STUDIO.EXAMPLE", origin = Some("HTTPS://studio.example:443")))
          val begin = _handoff(response)
          val callback = fixture.send(fixture.callback(begin, host = "studio.example:443"))
          Then("the proof is Secure and Google receives the exact configured redirect spelling")
          response.status.code shouldBe 200
          _headers(response, "Set-Cookie").head should include ("Secure")
          _returned(callback, cleared = true)
          _pairs(new URI(begin.uri).getRawQuery).toMap.apply("redirect_uri") shouldBe (configuredorigin + _callback_path)
          fixture.driver.requests should have size 1
          _no_cors(response)
        }
        }
      }

      "refuse missing, duplicate, malformed and foreign Host or Origin before begin" in {
        Given("one configured numeric loopback origin and no pending flow")
        _with_fixture() { fixture =>
          val base = fixture.request(Method.POST, _authorize_path)
          val badhosts = Vector("127.0.0.1:8124", "localhost:8123", "127.1:8123", "2130706433:8123", "127.000.0.1:8123",
            "127.0.0.1", "user@127.0.0.1:8123", "127.0.0.1:8123/", " 127.0.0.1:8123", "127.0.0.1:8123,elsewhere")
          val badorigins = Vector("https://127.0.0.1:8123", "http://127.0.0.1:8124", "http://localhost:8123",
            "http://127.0.0.1:8123/", "http://127.0.0.1:8123?x=1", "http://127.0.0.1:8123#x",
            "http://user@127.0.0.1:8123", "null", "http://127.0.0.1:8123 https://elsewhere", " http://127.0.0.1:8123")
          val requests = badhosts.map(host => fixture.request(Method.POST, _authorize_path, host = host)) ++
            badorigins.map(origin => fixture.request(Method.POST, _authorize_path, origin = Some(origin))) ++
            Vector(_without(base, "Host"), _without(base, "Origin"),
              _append(base, _header("Host", "127.0.0.1:8123")), _append(base, _header("Origin", fixture.origin)))
          When("each invalid authority reaches the normal listener")
          val responses = requests.map(fixture.send)
          Then("every request is refused without an authentication call, native HTTP or pending mutation")
          responses.foreach { response => response.status.code shouldBe 403; _no_cors(response); _secure(response) }
          fixture.provider.requests shouldBe empty
          fixture.driver.requests shouldBe empty
          _pending(fixture.send(fixture.request(Method.GET, _status_path))) shouldBe false
        }
      }

      "require the same Host on list, status and callback and ignore forwarded authority repairs" in {
        Given("a legitimate pending flow bound to the configured host")
        _with_fixture() { fixture =>
          val begin = fixture.begin()
          val forwarded = Vector(_header("X-Forwarded-Host", "127.0.0.1:8123"), _header("X-Forwarded-Proto", "http"),
            _header("Forwarded", "host=127.0.0.1:8123;proto=http"))
          When("read and callback paths carry a foreign Host or a conflicting absolute request target")
          val requests = Vector(fixture.request(Method.GET, _base, host = "evil.example"),
            fixture.request(Method.GET, _status_path, host = "evil.example"),
            fixture.callback(begin, host = "evil.example"),
            fixture.request(Method.POST, "http://evil.example" + _authorize_path),
            fixture.request(Method.POST, "https://127.0.0.1:8123" + _authorize_path))
          val responses = requests.map(request => fixture.send(forwarded.foldLeft(request)((current, header) => current.putHeaders(header))))
          val retained = fixture.send(fixture.request(Method.GET, _status_path))
          val callback = fixture.send(fixture.callback(begin))
          Then("forwards grant no authority and the legitimate pending flow remains usable")
          responses.foreach(_.status.code shouldBe 403)
          _pending(retained) shouldBe true
          _returned(callback, cleared = true)
          fixture.driver.requests should have size 1
        }
      }

      "inspect the actual first POST body byte despite misleading declarations" in {
        Given("an authenticated owner and an empty pending slot")
        _with_fixture() { fixture =>
          val request = fixture.request(Method.POST, _authorize_path).withEntity("ownerId=override")
            .putHeaders(_header("Content-Length", "0"), _header("Content-Type", "application/octet-stream"))
          When("the mutator receives a body with a zero declared length")
          val refused = fixture.send(request)
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          Then("the actual body prevents execution and its contents are never echoed")
          refused.status.code shouldBe 400
          _body(refused) should not include "override"
          _pending(status) shouldBe false
          fixture.driver.requests shouldBe empty
          fixture.provider.requests should have size 1
        }
      }
    }

    "H4 methods before generic HEAD dispatch" which {
      "refuse a real HEAD callback without consumption before the ordinary GET conversion" in {
        Given("an actual owner authorization and its narrow callback cookie")
        _with_fixture() { fixture =>
          val begin = fixture.begin()
          When("HEAD reaches the normal callback route before a later legitimate GET")
          val head = fixture.send(fixture.callback(begin).withMethod(Method.HEAD))
          val pending = fixture.send(fixture.request(Method.GET, _status_path))
          val before = fixture.driver.requests
          val completed = fixture.send(fixture.callback(begin))
          Then("HEAD is an empty 405 with pending retained and GET exchanges exactly once")
          head.status.code shouldBe 405
          _body(head) shouldBe ""
          _headers(head, "Set-Cookie") shouldBe empty
          _pending(pending) shouldBe true
          before shouldBe empty
          _returned(completed, cleared = true)
          fixture.driver.requests.map(_.method) shouldBe Vector("POST")
        }
      }

      "refuse unsupported methods and GET mutators without business fallback or CORS grants" in {
        Given("a normal listener and no pending flow")
        _with_fixture() { fixture =>
          val requests = for {
            path <- Vector(_authorize_path, _disconnect_path)
            method <- Vector(Method.HEAD, Method.GET, Method.PUT, Method.OPTIONS)
          } yield fixture.request(method, path)
          When("unsupported verbs target the captured management prefix")
          val responses = requests.map(fixture.send)
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          Then("405 responses cannot begin or disconnect and HEAD remains empty")
          responses.zip(requests).foreach { case (response, request) =>
            response.status.code shouldBe 405
            _no_cors(response)
            if (request.method == Method.HEAD) _body(response) shouldBe ""
          }
          _pending(status) shouldBe false
          fixture.driver.requests shouldBe empty
        }
      }
    }

    "H5 one-shot callback and bounded browser evidence" which {
      "leave the newest browser flow usable when an older authorization returns late" in {
        Given("two explicit owner authorizations where the latest flow replaced the earlier one")
        _with_fixture() { fixture =>
          val previous = fixture.begin()
          val latest = fixture.begin()
          When("the stale callback arrives before the latest legitimate callback")
          val stale = fixture.send(fixture.callback(previous))
          val retained = fixture.send(fixture.request(Method.GET, _status_path))
          val completed = fixture.send(fixture.callback(latest))
          Then("the stale flow neither clears the latest proof cookie nor consumes its pending authority")
          _returned(stale, cleared = false)
          _pending(retained) shouldBe true
          _returned(completed, cleared = true)
          fixture.driver.requests should have size 1
        }
      }

      "preserve the latest legitimate flow and cookie for wrong state, wrong proof and duplicate proof" in {
        Given("one latest pending owner flow")
        _with_fixture() { fixture =>
          val begin = fixture.begin()
          val wrongstate = fixture.callback(begin, query = Some("state=" + ("z" * 43) + "&code=" + _code))
          val wrongproof = fixture.callback(begin).putHeaders(_header("Cookie", _proof_cookie + "=" + ("p" * 43)))
          val duplicate = _append(fixture.callback(begin), _header("Cookie", _proof_cookie + "=" + begin.proof))
          When("foreign flow material is followed by the actual bound callback")
          val refused = Vector(wrongstate, wrongproof, duplicate).map(fixture.send)
          val retained = fixture.send(fixture.request(Method.GET, _status_path))
          val completed = fixture.send(fixture.callback(begin))
          Then("refusals share the fixed redirect and do not clear the usable latest cookie or consume the pending flow")
          refused.foreach(_returned(_, cleared = false))
          _pending(retained) shouldBe true
          _returned(completed, cleared = true)
          fixture.driver.requests should have size 1
        }
      }

      "reject ambiguous, malformed and oversized critical callback inputs without pending consumption" in {
        Given("a pending flow and representable raw query shapes including malformed UTF8")
        _with_fixture() { fixture =>
          val begin = fixture.begin()
          val valid = "state=" + begin.state
          val queries = Vector(valid + "&state=" + begin.state + "&code=" + _code,
            valid + "&code=" + _code + "&code=" + _code, valid + "&error=denied&error=denied",
            valid + "&code=x&error=denied", valid, valid + "&code=%C3%28", valid + "&code=%FF",
            valid + "&code=%00", valid + "&code=",
            valid + "&code=" + ("x" * 8193), valid + "&error=" + ("x" * 129),
            valid + "&code=x&" + ("k" * 257) + "=ignored", valid + "&code=x&extra=" + ("x" * 8193),
            valid + "&code=x&" + Vector.fill(31)("scope=x").mkString("&"),
            valid + "&code=x&extra=" + ("x" * 32769))
          When("the normal callback parser receives each malformed physical query")
          val responses = queries.map(query => fixture.send(fixture.callback(begin, query = Some(query))))
          val retained = fixture.send(fixture.request(Method.GET, _status_path))
          val before = fixture.driver.requests
          val completed = fixture.send(fixture.callback(begin, extra = "&scope=drive.file&authuser=0&error_description=ignored&error_uri=https%3A%2F%2Fnever.example%2F"))
          Then("every refusal is confidential, all critical ambiguity preserves the flow and bounded extra fields are ignored")
          responses.foreach(_returned(_, cleared = false))
          _pending(retained) shouldBe true
          before shouldBe empty
          _returned(completed, cleared = true)
          fixture.driver.requests.map(_.method) shouldBe Vector("POST")
          fixture.provider.requests.foreach(request => request.attributes.values.mkString should not include _code)
        }
      }

      "refuse lexically invalid request targets before callback routing while preserving the owner flow" in {
        Given("an actual pending owner flow and two raw callback targets with invalid percent syntax")
        _with_fixture() { fixture =>
          val begin = fixture.begin()
          val targets = Vector(_callback_path + "?state=" + begin.state + "&code=%",
            _callback_path + "?state=" + begin.state + "&code=%0G")
          When("normal public request-target admission interprets them before HTTP Request construction and the owner reads status")
          val admitted = targets.map(target => Uri.requestTarget(target))
          val retained = fixture.send(fixture.request(Method.GET, _status_path))
          Then("both targets are refused at URI admission with the owner flow still pending and no native HTTP or custody")
          admitted.map(_.isLeft) shouldBe Vector(true, true)
          _pending(retained) shouldBe true
          fixture.driver.requests shouldBe empty
          Files.exists(fixture.file) shouldBe false
          When("the same owner flow receives its subsequent valid actual callback")
          val completed = fixture.send(fixture.callback(begin))
          Then("it completes once through native token POST and clears its proof cookie")
          _returned(completed, cleared = true)
          fixture.driver.requests.map(_.method) shouldBe Vector("POST")
          Files.exists(fixture.file) shouldBe true
        }
      }

      "preserve a legitimate literal percent in an authorization code through native token exchange" in {
        Given("an actual pending owner flow and a synthetic authorization code containing a literal percent")
        _with_fixture() { fixture =>
          val begin = fixture.begin()
          When("the raw callback query carries that code with its percent encoded through normal request-target admission")
          val completed = fixture.send(fixture.callback(begin,
            query = Some("state=" + begin.state + "&code=synthetic%25code")))
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          Then("the fixed redirect clears the proof cookie and one token POST preserves the code in its form with a saved connected grant")
          _returned(completed, cleared = true)
          fixture.driver.requests.map(_.method) shouldBe Vector("POST")
          fixture.driver.requests.head.path shouldBe "https://oauth2.googleapis.com/token"
          fixture.driver.requests.head.body.get should include ("code=synthetic%25code")
          _json(status).hcursor.get[String]("state").toOption shouldBe Some("connected")
          Files.exists(fixture.file) shouldBe true
        }
      }

      "reject malformed, missing and out-of-bound cookie evidence without losing the pending flow" in {
        Given("a pending flow whose real browser proof is held separately")
        _with_fixture() { fixture =>
          val begin = fixture.begin()
          val base = fixture.callback(begin)
          val bad = Vector(_without(base, "Cookie"), base.putHeaders(_header("Cookie", _proof_cookie + "=short")),
            base.putHeaders(_header("Cookie", _proof_cookie + "=\"" + begin.proof + "\"")),
            base.putHeaders(_header("Cookie", _proof_cookie + "=" + begin.proof + " ")),
            base.putHeaders(_header("Cookie", "malformed; " + _proof_cookie + "=" + begin.proof)),
            base.putHeaders(_header("Cookie", "padding=" + ("p" * 16385))),
            base.putHeaders(_header("Cookie", Vector.fill(64)("other=x").mkString("; ") + "; " + _proof_cookie + "=" + begin.proof)))
          When("malformed cookie headers are followed by the original cookie")
          val responses = bad.map(fixture.send)
          val pending = fixture.send(fixture.request(Method.GET, _status_path))
          val completed = fixture.send(fixture.callback(begin))
          Then("cookie admission changes no runtime flow and only the final callback exchanges")
          responses.foreach(_returned(_, cleared = false))
          _pending(pending) shouldBe true
          _returned(completed, cleared = true)
          fixture.driver.requests should have size 1
        }
      }

      "keep success one-shot and leave expired flows unexchanged without clearing unrelated browser proof" in {
        Given("a pending flow and the same runtime-owned controllable clock used by the listener")
        _with_fixture() { fixture =>
          val first = fixture.begin()
          When("a callback succeeds, replays, then a new flow expires at its exact 300-second boundary")
          val success = fixture.send(fixture.callback(first))
          val replay = fixture.send(fixture.callback(first))
          val second = fixture.begin()
          fixture.globalContext.executionProfileRuntime.testControl.get.advanceBy(Duration.ofSeconds(300))
          val expired = fixture.send(fixture.callback(second))
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          Then("only one token exchange occurred and expiry reports its real outcome without an optimistic success grant")
          _returned(success, cleared = true)
          Vector(replay, expired).foreach(_returned(_, cleared = false))
          _json(status).hcursor.get[String]("lastOutcome").toOption shouldBe Some("expired")
          _pending(status) shouldBe false
          fixture.driver.requests should have size 1
          _grant(fixture).hcursor.get[String]("accessToken").toOption shouldBe Some(_access)
        }
      }

      "preserve an existing grant on matched provider denial and clear only its safely completed cookie" in {
        Given("a previously connected owner and a second explicit authorization flow")
        _with_fixture() { fixture =>
          val initial = fixture.begin()
          val initialresponse = fixture.send(fixture.callback(initial))
          val previous = Files.readString(fixture.file)
          val denied = fixture.begin()
          When("Google returns a matched denial followed by a replay")
          val response = fixture.send(fixture.callback(denied, query = Some("state=" + denied.state + "&error=access_denied&error_description=private-description")))
          val replay = fixture.send(fixture.callback(denied))
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          Then("the old grant remains connected, denial is a safe outcome and replay cannot exchange")
          _returned(initialresponse, cleared = true)
          _returned(response, cleared = true)
          _returned(replay, cleared = false)
          _json(status).hcursor.get[String]("state").toOption shouldBe Some("connected")
          _json(status).hcursor.get[String]("lastOutcome").toOption shouldBe Some("denied")
          Files.readString(fixture.file) shouldBe previous
          fixture.driver.requests should have size 1
          _body(response) should not include "private-description"
        }
      }
    }

    "H6 original Conclusions and confidential failure observability" which {
      "retain original management status, history selection and credential redaction" in {
        Given("a real authentication provider returning one original structured Conclusion with previous history")
        _with_fixture() { fixture =>
          val previous = _failure(Consequence.stateConflict("safe previous"))
          val current = _failure(Consequence.securityPermissionDenied("safe refusal Bearer " + _bearer + " cookie=" + _cookie_marker))
          val conclusion = current ++ previous
          fixture.provider.refusal = Some(conclusion)
          val selectors = Vector("", "?textus.output.conclusion=full", "?cncf.output.conclusion=full",
            "?textus.output.conclusion=full&textus.output.conclusion=full",
            "?textus.output.conclusion=full&cncf.output.conclusion=standard")
          When("management errors are projected through standard, full and ambiguous disclosure selections")
          val responses = selectors.map(selector => fixture.send(fixture.request(Method.GET, _status_path + selector)
            .putHeaders(_header("Cookie", "preference=" + _cookie_marker))))
          val outputs = responses.map(_json)
          Then("original status and vocabulary survive while duplicate selectors stay standard and all credentials are redacted")
          responses.foreach(response => response.status.code shouldBe conclusion.status.webCode.code)
          val amounts = outputs.map(_.hcursor.downField("error").get[String]("conclusionDetail").toOption)
          amounts shouldBe Vector(Some("standard"), Some("full"), Some("full"), Some("standard"), Some("standard"))
          outputs.zipWithIndex.foreach { case (output, index) =>
            val error = output.hcursor.downField("error")
            error.downField("conclusion").downField("status").focus shouldBe Some(_record_json(conclusion.status.toRecord))
            error.downField("conclusion").downField("observation").downField("taxonomy").focus shouldBe
              Some(_record_json(conclusion.observation.taxonomy.toRecord))
            error.downField("conclusion").downField("previous").focus.nonEmpty shouldBe (index == 1 || index == 2)
            output.noSpaces should not include _bearer
            output.noSpaces should not include _cookie_marker
          }
          fixture.driver.requests shouldBe empty
        }
      }

      "log a native transport failure safely through the bound scope and consume the matched flow once" in {
        Given("a real bound log sink and native transport failure carrying synthetic private markers")
        _with_log { sink =>
          _with_fixture() { fixture =>
            val begin = fixture.begin()
            val exception = new IOException("Bearer " + _bearer + " " + _access + " " + _code + " " + begin.state + " " + begin.proof + " " + fixture.root)
            fixture.driver.failure = Some(exception)
            val calls = fixture.provider.requests.size
            When("the matched callback encounters native transport failure, then is replayed")
            val response = fixture.send(fixture.callback(begin))
            val replay = fixture.send(fixture.callback(begin))
            val callbackcalls = fixture.provider.requests.size
            val status = fixture.send(fixture.request(Method.GET, _status_path))
            val events = sink.events.filter(_.message.contains("OAuth connection request failed"))
            Then("the fixed redirect discloses nothing, the detailed original failure is recorded safely and there is no retry")
            Vector(response, replay).foreach(_returned(_, cleared = false))
            _json(status).hcursor.get[String]("state").toOption shouldBe Some("uncertain")
            _json(status).hcursor.get[String]("lastOutcome").toOption shouldBe Some("failed")
            fixture.driver.requests should have size 1
            callbackcalls shouldBe calls
            events should not be empty
            val detail = events.head.attributes.getRecord("conclusion").get
            _record_json(detail).hcursor.downField("status").focus shouldBe Some(_record_json(Conclusion.from(exception).status.toRecord))
            val logged = events.map(event => event.message + RecordEncoder.json(event.attributes)).mkString
            logged should include ("IOException")
            Vector(_bearer, _access, _refresh, _code, begin.state, begin.proof, fixture.root.toString).foreach(marker => logged should not include marker)
            Files.exists(fixture.file) shouldBe false
          }
        }
      }

      "refuse native permission before acquiring the driver and expose uncertainty without automatic retry" in {
        Given("a pending flow whose component has native Drive permission but no token-origin grant")
        _with_fixture(tokenadmitted = false) { fixture =>
          val begin = fixture.begin()
          When("callback completion reaches actual native HTTP admission")
          val response = fixture.send(fixture.callback(begin))
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          val replay = fixture.send(fixture.callback(begin))
          Then("the host returns its fixed redirect, native permission refuses all HTTP and uncertainty requires reconciliation")
          Vector(response, replay).foreach(_returned(_, cleared = false))
          _json(status).hcursor.get[String]("state").toOption shouldBe Some("uncertain")
          fixture.driver.requests shouldBe empty
          Files.exists(fixture.file) shouldBe false
        }
      }

      "preserve the actual private storage outcome when a safe token response cannot be persisted" in {
        Given("an owner-started flow and a selected credential target made unsafe before the exchange")
        _with_fixture() { fixture =>
          val begin = fixture.begin()
          _write(fixture.file, "sentinel-private-record")
          Files.setPosixFilePermissions(fixture.file, PosixFilePermissions.fromString("rw-r--r--"))
          When("normal callback completion attempts native token exchange and private persistence")
          val response = fixture.send(fixture.callback(begin))
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          val replay = fixture.send(fixture.callback(begin))
          Then("the existing file is preserved, status is uncertain and the matched code is not retried")
          Vector(response, replay).foreach(_returned(_, cleared = false))
          _json(status).hcursor.get[String]("state").toOption shouldBe Some("uncertain")
          fixture.driver.requests should have size 1
          Files.readString(fixture.file) shouldBe "sentinel-private-record"
        }
      }
    }

    "H7 explicit local disconnect" which {
      "require exact owner and Origin, forget only selected custody and cancel pending completion" in {
        Given("a connected owner, a new pending flow and unrelated domain data")
        _with_fixture() { fixture =>
          val first = fixture.begin()
          val connected = fixture.send(fixture.callback(first))
          val pending = fixture.begin()
          val sentinel = _write(fixture.root.resolve("domain-sentinel"), "domain content")
          When("foreign owner and Origin are refused before the exact owner explicitly disconnects")
          val foreign = fixture.send(fixture.request(Method.POST, _disconnect_path, bearer = Some(_foreign_bearer)))
          val cross = fixture.send(fixture.request(Method.POST, _disconnect_path, origin = Some("http://127.0.0.1:9999")))
          val retained = Files.exists(fixture.file)
          val response = fixture.send(fixture.request(Method.POST, _disconnect_path))
          val callback = fixture.send(fixture.callback(pending))
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          Then("only selected local grant and pending state are removed, with no revoke, replay or domain mutation")
          _returned(connected, cleared = true)
          Vector(foreign, cross).foreach(_.status.code shouldBe 403)
          retained shouldBe true
          response.status.code shouldBe 200
          _json(status).hcursor.get[String]("state").toOption shouldBe Some("authorization-required")
          _json(status).hcursor.get[String]("lastOutcome").toOption shouldBe Some("disconnected")
          _pending(status) shouldBe false
          _returned(callback, cleared = false)
          Files.exists(fixture.file) shouldBe false
          Files.readString(sentinel) shouldBe "domain content"
          fixture.driver.requests.map(_.method) shouldBe Vector("POST")
        }
      }

      "make no remote cancellation claim for a Drive request which already acquired its header" in {
        Given("a connected owner and a controlled native Drive GET paused after header acquisition")
        _with_fixture() { fixture =>
          val begin = fixture.begin()
          val callback = fixture.send(fixture.callback(begin))
          val entered = new CountDownLatch(1)
          val release = new CountDownLatch(1)
          fixture.driver.beforeGet = () => { entered.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("controlled GET timeout") }
          val context = fixture.authenticatedContext(_bearer)
          When("disconnect occurs while that already admitted GET is in flight")
          val fiber = IO.blocking(new UnitOfWorkInterpreter(context.runtime.unitOfWork).run(fixture.store.read(_reference))).start.unsafeRunSync()
          val acquired = entered.await(10, TimeUnit.SECONDS)
          val disconnected = try fixture.send(fixture.request(Method.POST, _disconnect_path)) finally release.countDown()
          val result = fiber.joinWithNever.unsafeRunSync()
          val status = fixture.send(fixture.request(Method.GET, _status_path))
          Then("local disconnect succeeds while the already acquired remote request may still complete")
          _returned(callback, cleared = true)
          acquired shouldBe true
          disconnected.status.code shouldBe 200
          result.isSuccess shouldBe true
          _json(status).hcursor.get[String]("state").toOption shouldBe Some("authorization-required")
          fixture.driver.requests.map(_.method) shouldBe Vector("POST", "GET")
        }
      }
    }

    "H8 generated normal-route admission" which {
      "preserve pending state and native-effect boundaries for at least one hundred origin and critical-query variations" in {
        Given("a normally installed Authenticated component and generated closed Host/Origin and callback cases")
        _with_fixture() { fixture =>
          val variants = for {
            mode <- Gen.choose(0, 7)
            salt <- Gen.choose(0, 1000000)
          } yield (mode, salt)
          When("ScalaCheck sends each variation through the existing listener routes")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(variants) { case (mode, salt) =>
            val begin = fixture.begin()
            val before = fixture.driver.requests.size
            val query = "state=" + begin.state + "&code=" + _code
            val request = mode match {
              case 0 => fixture.callback(begin, extra = "&authuser=" + salt)
              case 1 => fixture.callback(begin, query = Some(query + "&state=" + begin.state))
              case 2 => fixture.callback(begin, query = Some(query + "&code=duplicate"))
              case 3 => fixture.callback(begin, query = Some(query + "&error=denied"))
              case 4 => fixture.callback(begin, query = Some("state=" + begin.state + "&code=%C3%28"))
              case 5 => fixture.callback(begin).putHeaders(_header("Cookie", _proof_cookie + "=" + ("z" * 43)))
              case 6 => fixture.request(Method.POST, _authorize_path, origin = Some("http://127.0.0.1:" + (9000 + salt % 1000)))
              case _ => fixture.request(Method.POST, _authorize_path, host = "localhost:8123")
            }
            val response = fixture.send(request)
            val pending = _pending(fixture.send(fixture.request(Method.GET, _status_path)))
            val effects = fixture.driver.requests.size - before
            val accepted = mode == 0
            val correct = effects == (if (accepted) 1 else 0) && pending == !accepted &&
              response.status.code == (if (mode >= 6) 403 else 303) &&
              _headers(response, "Set-Cookie").nonEmpty == accepted
            if (!accepted) fixture.send(fixture.callback(begin))
            correct
          })
          Then("all generated behaviors satisfy native effects, pending preservation and confidential response outcomes with zero discards")
          checked.passed shouldBe true
          checked.succeeded should be >= 100
          checked.discarded shouldBe 0
        }
      }
    }
  }

  private def _secure(response: Response[IO]): Unit = {
    _headers(response, "Cache-Control") shouldBe Vector("no-store")
    _headers(response, "Pragma") shouldBe Vector("no-cache")
    _headers(response, "Referrer-Policy") shouldBe Vector("no-referrer")
    _headers(response, "X-Content-Type-Options") shouldBe Vector("nosniff")
  }
  private def _no_cors(response: Response[IO]): Unit =
    response.headers.headers.filter(_.name.toString.toLowerCase.startsWith("access-control-allow-")).toVector shouldBe empty
  private def _returned(response: Response[IO], cleared: Boolean): Unit = {
    response.status.code shouldBe 303
    _headers(response, "Location") shouldBe Vector(_return_path + "#oauth-returned")
    _body(response) shouldBe ""
    _secure(response)
    if (cleared) _headers(response, "Set-Cookie").head should include ("Max-Age=0")
    else _headers(response, "Set-Cookie") shouldBe empty
    _no_cors(response)
  }
  private def _private_absent(text: String, fixture: Harness): Unit =
    Vector(_owner, _client_secret, _access, _refresh, fixture.root.toString).foreach(marker => text should not include marker)
}

object OAuthConnectionHttpSpec {
  private val _component_id = ComponentId("org.example.HttpStudio")
  private val _instance_id = ComponentInstanceId(_component_id, "http-one")
  private val _connection_id = "drive"
  private val _base = "/web/http-studio/connections"
  private val _status_path = _base + "/drive/status"
  private val _authorize_path = _base + "/drive/authorize"
  private val _callback_path = _base + "/drive/callback"
  private val _disconnect_path = _base + "/drive/disconnect"
  private val _return_path = "/web/http-studio/home"
  private val _proof_cookie = "cncf-oauth-proof"
  private val _owner = "synthetic-http-owner-marker"
  private val _bearer = "synthetic-http-ingress-marker"
  private val _foreign_bearer = "synthetic-http-foreign-marker"
  private val _cookie_marker = "synthetic-http-cookie-marker"
  private val _client_secret = "synthetic-http-client-secret-marker"
  private val _code = "synthetic-http-code-marker"
  private val _access = "synthetic-http-access-marker"
  private val _refresh = "synthetic-http-refresh-marker"
  private val _observed = Instant.parse("2026-10-21T00:00:00Z")
  private val _octets = Vector[Byte](0, -1, -128, 1, 10)
  private val _reference = FileStoreRef("http-store", "http-file")
  private val _status_keys = Set("connectionId", "provider", "state", "canRefresh", "authorizationPending", "updating")
  private val _directory_attribute = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))

  private final class HttpComponent(provider: Provider, localonly: Boolean) extends OAuthConnectionRuntime {
    private var _initializations = 0
    def initializations: Int = _initializations
    override def authenticationProviders: Vector[AuthenticationProvider] = if (localonly) Vector.empty else Vector(provider)
    override protected def initialize_component_c(params: ComponentInit): Consequence[Unit] =
      super.initialize_component_c(params).map { _ => _initializations += 1 }
  }

  private final class HttpFactory(provider: Provider, localonly: Boolean) extends Component.Factory {
    override def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] = Vector(OAuthConnectionRuntime.configurationKey)
    protected def create_Component(params: ComponentCreate): Component = new HttpComponent(provider, localonly)
    protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      Component.Core.create(_component_id.name, _component_id, ComponentInstanceId.default(_component_id), Protocol.empty, this)
  }

  private final class Provider extends AuthenticationProvider {
    private var _requests = Vector.empty[AuthenticationRequest]
    private var _refusal = Option.empty[Conclusion]
    def requests: Vector[AuthenticationRequest] = _requests
    def refusal_=(value: Option[Conclusion]): Unit = { _refusal = value }
    def refusal: Option[Conclusion] = _refusal
    def name: String = "oauth-http-owner-provider"
    def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] = {
      _requests = _requests :+ request
      _refusal match {
        case Some(conclusion) => Consequence.Failure(conclusion)
        case None => Consequence.success(request.accessToken.flatMap {
          case value if value == _bearer => Some(AuthenticationResult(PrincipalId(_owner)))
          case value if value == _foreign_bearer => Some(AuthenticationResult(PrincipalId("foreign-http-owner")))
          case _ => None
        })
      }
    }
  }

  private final class NativeRequest(val method: String, val path: String, val body: Option[String], val headers: Map[String, String]) {
    override def toString: String = "NativeRequest(<redacted>)"
  }

  private final class Driver extends HttpDriver {
    private var _requests = Vector.empty[NativeRequest]
    @volatile private var _failure = Option.empty[Throwable]
    @volatile private var _before_get: () => Unit = () => ()
    def requests: Vector[NativeRequest] = synchronized(_requests)
    def failure: Option[Throwable] = _failure
    def failure_=(value: Option[Throwable]): Unit = { _failure = value }
    def beforeGet: () => Unit = _before_get
    def beforeGet_=(value: () => Unit): Unit = { _before_get = value }
    def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      synchronized { _requests = _requests :+ new NativeRequest("GET", path, None, headers) }
      _before_get()
      HttpResponse.Binary(HttpStatus.Ok, ContentType(MimeType("application/octet-stream"), None), Bag.binary(_octets.toArray))
    }
    def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      synchronized { _requests = _requests :+ new NativeRequest("POST", path, body, headers) }
      _failure.foreach(exception => throw exception)
      _response(Json.obj("access_token" -> Json.fromString(_access), "refresh_token" -> Json.fromString(_refresh),
        "token_type" -> Json.fromString("Bearer"), "expires_in" -> Json.fromInt(3600)).noSpaces)
    }
    def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
      throw new AssertionError("OAuth HTTP scenarios do not admit native PUT")
  }

  private final class Handoff(val response: Response[IO], val uri: String, val state: String, val proof: String)

  private final class Harness(val root: Path, val origin: String, configured: Boolean, tokenadmitted: Boolean,
    localonly: Boolean, generic: Boolean) {
    val driver: Driver = new Driver
    val provider: Provider = new Provider
    val file: Path = root.resolve("grant.json")
    private val _source = _write(root.resolve("operator.json"), _document(root, origin, generic).noSpaces)
    private val _configuration = _configuration_for_profile(localonly)
    private val _profile = ExecutionProfileResolver.resolveForSpec(_configuration).TAKE
    private val _runtime_config = RuntimeConfig.default.copy(httpDriver = driver, executionProfile = _profile)
    private val _origins = Vector("https://www.googleapis.com") ++ Option.when(tokenadmitted)("https://oauth2.googleapis.com")
    private val _admission = HttpExecutionAdmission.fromRuntimeC("https://www.googleapis.com/", HttpExecutionLimits(1000, 1000, 1048576),
      Map(_component_id -> HttpExecutionGrant(_origins, Set(HttpExecutionMethod.Get, HttpExecutionMethod.Post)))).TAKE
    val globalContext: GlobalRuntimeContext = GlobalRuntimeContext.create("oauth-http-spec", _runtime_config, _configuration,
      ExecutionContext.create().observability, AliasResolver.empty, httpAdmission = Some(_admission))
    val owner: Subsystem = DefaultSubsystemFactory.defaultWithScope(globalContext, configuration = _configuration, aliasResolver = AliasResolver.empty)
    private val _factory = new HttpFactory(provider, localonly)
    private val _descriptor = ComponentDescriptor(name = Some(_component_id.name), componentName = Some(_component_id.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(_component_id))
    private val _settings = Option.when(configured)(OAuthConnectionRuntime.configurationKey.name -> _source.toUri.toString).toMap
    private val _created = _factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin, Vector(_descriptor))
      .withInstanceMetadata(ComponentInstanceMetadata(_component_id.name, _instance_id.instance, _settings, componentId = Some(_component_id)))).TAKE
    val component: HttpComponent = _created.asInstanceOf[HttpComponent]
    component.withApplicationConfig(Component.ApplicationConfig(httpDriver = Some(driver)))
    owner.add(component)
    if (localonly) owner.withDescriptor(GenericSubsystemDescriptor(path = root.resolve("local-subsystem.yaml"),
      subsystemName = owner.name, componentBindings = Vector(GenericSubsystemComponentBinding(_component_id.name)),
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
        convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
        localSubject = Some(GenericSubsystemLocalSubjectBinding(id = _owner, roles = Vector("user"),
          capabilities = Vector("user"), securityLevel = Some("user"))), providers = Vector.empty))))))
    HttpRuntimeBindingAdmissionFixture.admit(owner)
    val server: Http4sHttpServer = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(owner))
    val store: GoogleDriveFileStore = GoogleDriveFileStore.createC(_reference.store,
      GoogleDriveCredentials.managedConnectionC(_connection_id).TAKE).TAKE

    def request(method: Method, path: String, bearer: Option[String] = Some(_bearer), host: String = "127.0.0.1:8123",
      origin: Option[String] = Some(this.origin)): Request[IO] = {
      val fields = Vector(_header("Host", host)) ++ bearer.toVector.map(value => _header("Authorization", "Bearer " + value)) ++
        Option.when(method == Method.POST)(origin).flatten.toVector.map(value => _header("Origin", value))
      Request[IO](method = method, uri = Uri.unsafeFromString(path), headers = Headers(fields))
    }
    def send(request: Request[IO]): Response[IO] = server.routes(null).orNotFound.run(request).unsafeRunSync()
    def begin(): Handoff = {
      val host = new URI(origin).getRawAuthority
      _handoff(send(request(Method.POST, _authorize_path, host = host)))
    }
    def callback(handoff: Handoff, query: Option[String] = None, extra: String = "", host: String = "127.0.0.1:8123"): Request[IO] = {
      val target = _callback_path + "?" + query.getOrElse("state=" + handoff.state + "&code=" + _code) + extra
      val uri = Uri.requestTarget(target) match {
        case Right(value) => value
        case Left(_) => throw new AssertionError("Unrepresentable synthetic callback target")
      }
      request(Method.GET, _callback_path, bearer = None, host = host).withUri(uri)
        .putHeaders(_header("Cookie", _proof_cookie + "=" + handoff.proof))
    }
    def authenticatedContext(bearer: String): ExecutionContext = IngressSecurityResolver.resolve(owner.executionProfileC.TAKE,
      component.logic.executionContext(), Map("Authorization" -> ("Bearer " + bearer))).TAKE.executionContext
  }

  private final class RecordingLog extends LogBackend {
    private var _events = Vector.empty[StructuredLogEvent]
    def events: Vector[StructuredLogEvent] = _events
    override def logStructured(event: StructuredLogEvent): Unit = { _events = _events :+ event }
    def writeLine(line: String): Unit = ()
  }

  private def _configuration_for_profile(localonly: Boolean): ResolvedConfiguration = {
    val values = Map(SubsystemUserMode.CONFIGURATION_KEY -> (if (localonly) "standalone" else "multi-user"), RuntimeConfig.operationModeKey -> "test",
      RuntimeConfig.EXECUTION_PROFILE_KEY -> "controlled", RuntimeConfig.EXECUTION_KEY -> "oauth-http-spec",
      RuntimeConfig.EXECUTION_TIME_MODE_KEY -> "manual", RuntimeConfig.EXECUTION_TIME_START_AT_KEY -> _observed.toString,
      RuntimeConfig.EXECUTION_RANDOM_MODE_KEY -> "seeded", RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> "oauth-http-seed",
      RuntimeConfig.EXECUTION_IDS_MODE_KEY -> "deterministic", RuntimeConfig.EXECUTION_SCHEDULER_MODE_KEY -> "manual",
      RuntimeConfig.EXECUTION_ORDERING_MODE_KEY -> "deterministic")
    ResolvedConfiguration(Configuration(values.map { case (key, value) => key -> ConfigurationValue.StringValue(value) }), ConfigurationTrace.empty)
  }
  private def _document(root: Path, origin: String, generic: Boolean): Json = Json.obj(
    "schema" -> Json.fromString("cncf.oauth-connection/1"), "connectionId" -> Json.fromString(_connection_id),
    "ownerId" -> Json.fromString(_owner), "provider" -> (if (generic) Json.obj("name" -> Json.fromString("google-drive"),
      "authorizationEndpoint" -> Json.fromString("https://accounts.google.com/o/oauth2/v2/auth"),
      "tokenEndpoint" -> Json.fromString("https://oauth2.googleapis.com/token")) else Json.fromString("google-drive")),
    "clientId" -> Json.fromString("synthetic-http-client"), "clientSecret" -> Json.fromString(_client_secret),
    "redirectUri" -> Json.fromString(origin + _callback_path), "returnPath" -> Json.fromString(_return_path),
    "scopes" -> Json.arr(Json.fromString("https://www.googleapis.com/auth/drive.file")),
    "credentialFile" -> Json.fromString(root.resolve("grant.json").toString))
  private def _handoff(response: Response[IO]): Handoff = {
    val uri = _json(response).hcursor.get[String]("authorizationUri").toOption.get
    val state = _pairs(new URI(uri).getRawQuery).toMap.apply("state")
    val proof = _headers(response, "Set-Cookie").head.split(";", 2).head.split("=", 2)(1)
    new Handoff(response, uri, state, proof)
  }
  private def _header(name: String, value: String): Header.Raw = Header.Raw(CIString(name), value)
  private def _headers(response: Response[IO], name: String): Vector[String] =
    response.headers.headers.filter(_.name.toString.equalsIgnoreCase(name)).map(_.value).toVector
  private def _without(request: Request[IO], name: String): Request[IO] =
    request.withHeaders(Headers(request.headers.headers.filterNot(_.name.toString.equalsIgnoreCase(name))))
  private def _append(request: Request[IO], header: Header.Raw): Request[IO] =
    request.withHeaders(Headers(request.headers.headers :+ header))
  private def _body(response: Response[IO]): String = response.as[String].unsafeRunSync()
  private def _json(response: Response[IO]): Json = parser.parse(_body(response)).toOption.get
  private def _record_json(record: Record): Json = parser.parse(RecordEncoder.json(record)).toOption.get
  private def _pending(response: Response[IO]): Boolean = _json(response).hcursor.get[Boolean]("authorizationPending").toOption.get
  private def _grant(fixture: Harness): Json = parser.parse(Files.readString(fixture.file, StandardCharsets.UTF_8)).toOption.get
  private def _pairs(query: String): Vector[(String, String)] = query.split("&").toVector.map { field =>
    val pair = field.split("=", 2)
    URLDecoder.decode(pair(0), StandardCharsets.UTF_8) -> URLDecoder.decode(pair(1), StandardCharsets.UTF_8)
  }
  private def _failure(value: Consequence[Nothing]): Conclusion = value match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => throw new AssertionError("Expected a structured OAuth HTTP failure")
  }
  private def _response(text: String): HttpResponse = HttpResponse.Text(HttpStatus.Ok,
    ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)), Bag.text(text, StandardCharsets.UTF_8), Record.empty)
  private def _write(file: Path, text: String): Path = {
    Files.write(file, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
    file
  }
  private def _with_fixture[A](configured: Boolean = true, origin: String = "http://127.0.0.1:8123", tokenadmitted: Boolean = true,
    localonly: Boolean = false, generic: Boolean = false)(body: Harness => A): A = {
    val base = Files.createDirectories(Paths.get("target", "oauth-connection-http-spec")).toRealPath()
    val root = Files.createTempDirectory(base, "http-", _directory_attribute).toRealPath()
    try body(new Harness(root, origin, configured, tokenadmitted, localonly, generic)) finally Using.resource(Files.walk(root)) { stream =>
      stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => Files.deleteIfExists(path))
    }
  }
  private def _with_log[A](body: RecordingLog => A): A = {
    val previous = LogBackendHolder.backend
    val sink = new RecordingLog
    LogBackendHolder.install(sink)
    try body(sink) finally previous match {
      case Some(backend) => LogBackendHolder.install(backend)
      case None => LogBackendHolder.reset()
    }
  }
}
