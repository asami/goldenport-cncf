package org.goldenport.cncf.oauth

import java.io.IOException
import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import io.circe.{Json, parser}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod}
import org.goldenport.cncf.observability.{ConclusionDiagnostics, ConclusionDisclosure}
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
final class OAuthClientSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OAuthClientSpec.*

  "OAuth authorization-code connections" should {
    "trusted provider and client configuration" which {
      "admits fixed HTTPS endpoints and preserves exact configured redirects and scope order" in {
        Given("a generic provider, HTTPS and numeric loopback redirects, and ordered valid scope tokens")
        val provider = OAuthProvider.createC("generic.provider-1", "https://issuer.example:443/authorize",
          "https://issuer.example/token").TAKE
        val redirects = Vector("https://client.example:443/callback%2Fexact", "http://127.0.0.1:1/callback",
          "http://127.0.0.1:65535/callback")
        When("each client prepares its browser handoff")
        val requests = redirects.map(redirect => _client(provider, redirect = redirect,
          scopes = Vector("z:last", "a:first")).beginC(_state, _verifier).TAKE)
        Then("the provider identity and entered redirect and scope order are retained")
        provider.name shouldBe "generic.provider-1"
        provider.authorizationEndpoint shouldBe "https://issuer.example:443/authorize"
        provider.tokenEndpoint shouldBe "https://issuer.example/token"
        requests.zip(redirects).foreach { case (request, redirect) =>
          _query(request)("redirect_uri") shouldBe redirect
          _query(request)("scope") shouldBe "z:last a:first"
        }
      }

      "rejects unsafe provider names and endpoints with the fixed configuration failure" in {
        Given("invalid names and endpoints including null, opaque, non-HTTPS, decorated and oversized URIs")
        val names = Vector(null, "", "-bad", "bad name", "é", "a" * 129)
        val endpoints = Vector(null, "", "/relative", "https:opaque", "http://issuer.example/token",
          "https:///token", "https://user@issuer.example/token", "https://issuer.example/token?q=1",
          "https://issuer.example/token#fragment", "https://issuer.example:444/token",
          "https://issuer.example/\\token", "https://issuer.example/\ntoken", "https://é.example/token",
          "https://issuer.example/" + ("a" * 2048))
        When("the provider factory interprets each malformed name or endpoint")
        val results = names.map(OAuthProvider.createC(_, _authorization, _token)) ++ endpoints.flatMap { endpoint =>
          Vector(OAuthProvider.createC("provider", endpoint, _token), OAuthProvider.createC("provider", _authorization, endpoint))
        }
        Then("all refusals use a fixed category and message without submitted material")
        results.foreach(result => _same_failure(result,
          Consequence.configurationInvalid("OAuth client configuration is invalid")))
      }

      "rejects malformed identifiers, secrets, redirects and scopes without HTTP" in {
        Given("a counted native runtime and invalid client configuration spaces")
        val fixture = new Harness(_ => throw new AssertionError("Invalid configuration must not use HTTP"))
        val identifiers = Vector(null, "", "a b", "a\tb", "a\u007fb", "é", "a" * 1025)
        val secrets = Vector[Option[String]](null, Some(null), Some(""), Some("a b"), Some("é"), Some("a" * 16385))
        val redirects = Vector(null, "", "https:opaque", "/callback", "https:///callback", "https://user@client.example/cb",
          "https://client.example/cb?q=1", "https://client.example/cb#fragment", "https://client.example:444/cb",
          "https://client.example/\\cb", "https://client.example/\tcb", "https://é.example/cb",
          "http://localhost:8000/cb", "http://0.0.0.0:8000/cb", "http://127.0.0.2:8000/cb",
          "http://127.0.0.1/cb", "http://127.0.0.1:0/cb", "http://127.0.0.1:65536/cb",
          "http://[::1]:8000/cb", "http://client.example:8000/cb", "https://client.example/" + ("x" * 2048))
        val scopes = Vector[Vector[String]](null, Vector.empty, Vector(null), Vector(""), Vector("a", "a"),
          Vector("a b"), Vector("a\tb"), Vector("a\"b"), Vector("a\\b"), Vector("é"), Vector("x" * 1025),
          Vector.tabulate(17)(index => s"scope$index"))
        When("the client factory interprets all malformed fields")
        val outcomes = identifiers.map(OAuthClient.createC(OAuthProvider.googleDrive, _, None, _redirect, _scopes)) ++
          secrets.map(OAuthClient.createC(OAuthProvider.googleDrive, _client_id, _, _redirect, _scopes)) ++
          redirects.map(OAuthClient.createC(OAuthProvider.googleDrive, _client_id, None, _, _scopes)) ++
          scopes.map(OAuthClient.createC(OAuthProvider.googleDrive, _client_id, None, _redirect, _)) ++
          Vector(OAuthClient.createC(null, _client_id, None, _redirect, _scopes))
        Then("every refusal is a safe configuration failure with no driver acquisition")
        outcomes.foreach(outcome => _same_failure(outcome,
          Consequence.configurationInvalid("OAuth client configuration is invalid")))
        fixture.acquisitions shouldBe 0
        fixture.requests shouldBe empty
      }

      "admits the declared maximum identifier, secret, scope count and token lengths" in {
        Given("configuration values at each inclusive public limit")
        val scopes = Vector.tabulate(16)(index => index.toString + ("s" * (1024 - index.toString.length)))
        When("a client is constructed at the limits")
        val outcome = OAuthClient.createC(OAuthProvider.googleDrive, "i" * 1024, Some("s" * 16384), _redirect, scopes)
        Then("the bounded configuration is admitted")
        outcome.isSuccess shouldBe true
      }
    }

    "pure browser authorization" which {
      "uses the RFC7636 published S256 vector and Google's exact consent parameters" in {
        Given("the published RFC7636 verifier and synthetic client configuration with reserved form characters")
        val client = _client(clientid = "client+id/a?b=c&d%20", secret = Some("synthetic-secret-marker"))
        val fixture = new Harness(_ => throw new AssertionError("Browser preparation must be pure"))
        When("the authorization request is prepared")
        val request = client.beginC(_state, _verifier).TAKE
        Then("the decoded query is exact, PKCE is S256, and secrets never enter handoff or displays")
        _query(request) shouldBe Map("response_type" -> "code", "client_id" -> "client+id/a?b=c&d%20",
          "redirect_uri" -> _redirect, "scope" -> _scopes.mkString(" "), "state" -> _state,
          "code_challenge" -> "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "code_challenge_method" -> "S256",
          "access_type" -> "offline", "prompt" -> "consent")
        new URI(request.authorizationUri).getRawQuery.split("&", -1).length shouldBe 9
        new URI(request.authorizationUri).getScheme shouldBe "https"
        request.authorizationUri.takeWhile(_ != '?') shouldBe _authorization
        Vector(request.authorizationUri, request.toString, client.toString, OAuthProvider.googleDrive.toString)
          .foreach { output =>
            output should not include _verifier
            output should not include "synthetic-secret-marker"
          }
        request.toString shouldBe "OAuthAuthorizationRequest(<redacted>)"
        client.toString shouldBe "OAuthClient(<redacted>)"
        fixture.acquisitions shouldBe 0
      }

      "round-trips admitted PKCE verifiers and form values for at least one hundred generated requests" in {
        Given("bounded RFC7636 unreserved verifiers and printable form-delimiter configuration values")
        val verifiers = for {
          size <- Gen.choose(43, 128)
          chars <- Gen.listOfN(size, Gen.oneOf("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~".toVector))
          suffix <- Gen.listOfN(12, Gen.oneOf("a&=+/%?:![]~".toVector))
        } yield (chars.mkString, suffix.mkString)
        When("the generated pure requests are interpreted as decoded browser queries")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(verifiers) { case (verifier, suffix) =>
          val scopes = Vector("scope:" + suffix)
          val request = _client(clientid = suffix, scopes = scopes).beginC(_state, verifier).TAKE
          val fields = _query(request)
          val challenge = Base64.getUrlEncoder.withoutPadding.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)))
          fields("client_id") == suffix && fields("scope") == scopes.head &&
            fields("code_challenge") == challenge && !fields.contains("code_verifier")
        })
        Then("all generated values retain exact meaning with no discarded cases")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }

      "omits provider-specific consent parameters for generic providers" in {
        Given("a validated generic provider with no authorization extras")
        val provider = OAuthProvider.createC("generic", "https://issuer.example/authorize", "https://issuer.example/token").TAKE
        When("a generic browser request is prepared")
        val request = _client(provider).beginC(_state, _verifier).TAKE
        Then("only the seven authorization-code and PKCE parameters exist")
        _query(request).keySet shouldBe Set("response_type", "client_id", "redirect_uri", "scope", "state",
          "code_challenge", "code_challenge_method")
      }

      "rejects malformed state and verifier spaces with one fixed request failure" in {
        Given("invalid entropy representations and generated invalid extensions of admitted verifiers")
        val client = _client()
        val invalid = Vector(null, "", "a" * 42, "a" * 129, "a b" * 20, "é" * 43, "a\nb" * 20)
        val inputs = for {
          size <- Gen.choose(43, 128)
          chars <- Gen.listOfN(size, Gen.oneOf("abcXYZ019_-".toVector))
          invalidchar <- Gen.oneOf(' ', '\t', '\n', '\\', '"', 'é', '+', '/')
        } yield chars.mkString + invalidchar
        When("begin interprets malformed state and verifier values")
        val outcomes = invalid.flatMap(value => Vector(client.beginC(value, _verifier), client.beginC(_state, value))) ++
          Vector(client.beginC("." * 43, _verifier), client.beginC("~" * 43, _verifier))
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(inputs) { value =>
          !client.beginC(value, _verifier).isSuccess && !client.beginC(_state, value).isSuccess
        })
        Then("each malformed value is refused without coercion and the generated cases have zero discards")
        outcomes.foreach(outcome => _same_failure(outcome, Consequence.argumentInvalid("OAuth authorization request is invalid")))
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }

    "admitted token exchange" which {
      "defers exactly one token POST until interpretation and form-encodes every configured value once" in {
        Given("a bound authorization request and a counted admitted native runtime")
        val fixture = new Harness(_ => _json_response(_token_json()))
        val client = _client(clientid = "client+id/a?b=c&d%20", secret = Some("secret&=+/%"),
          redirect = "http://127.0.0.1:8123/callback%2Fexact")
        val request = client.beginC(_state, _verifier).TAKE
        When("the token program is constructed and then interpreted")
        val program = client.complete(request, _state, "code/+&=%", _observed)
        val before = fixture.acquisitions
        val credential = fixture.evaluate(program).TAKE
        Then("construction was pure, one native POST carries the exact form, and the safe grant is returned")
        before shouldBe 0
        fixture.acquisitions shouldBe 1
        fixture.requests.size shouldBe 1
        val actual = fixture.requests.head
        actual.method shouldBe "POST"
        actual.path shouldBe _token
        actual.headers shouldBe Map("Content-Type" -> "application/x-www-form-urlencoded", "Accept" -> "application/json")
        _decode(actual.text.get) shouldBe Map("grant_type" -> "authorization_code", "code" -> "code/+&=%",
          "redirect_uri" -> "http://127.0.0.1:8123/callback%2Fexact", "client_id" -> "client+id/a?b=c&d%20",
          "code_verifier" -> _verifier, "client_secret" -> "secret&=+/%")
        actual.text.get.split("&", -1).length shouldBe 6
        actual.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("16384")
        credential.grantedScopes shouldBe _scopes
        credential.expiresAt shouldBe _observed.plusSeconds(3600)
        credential.canRefresh shouldBe true
        credential.toString shouldBe "OAuthCredential(<redacted>)"
      }

      "omits an absent client secret and honestly reports a grant without refresh material" in {
        Given("a public client and a valid token response without a refresh token")
        val fixture = new Harness(_ => _json_response(_token_json(refresh = None)))
        val client = _client(secret = None)
        val request = client.beginC(_state, _verifier).TAKE
        When("the exchange and the resulting attempt to refresh are interpreted")
        val credential = fixture.evaluate(client.complete(request, _state, _code, _observed)).TAKE
        val outcome = fixture.evaluate(client.refresh(credential, _observed))
        Then("the form omits a secret and missing refresh requires authorization without another HTTP request")
        _decode(fixture.requests.head.text.get).contains("client_secret") shouldBe false
        credential.canRefresh shouldBe false
        _same_failure(outcome, Consequence.securityPermissionDenied("OAuth connection requires authorization"))
        fixture.requests.size shouldBe 1
      }

      "rejects null or foreign requests, malformed states, codes and times before acquiring HTTP" in {
        Given("two separately constructed clients, a bound request and invalid callback inputs")
        val fixture = new Harness(_ => throw new AssertionError("Rejected callbacks must not acquire HTTP"))
        val client = _client()
        val foreign = _client().beginC(_state, _verifier).TAKE
        val request = client.beginC(_state, _verifier).TAKE
        val states = Vector(null, "", "x" * 42, "x" * 129, "a b" * 20, "x" * 43)
        val codes = Vector(null, "", "a b", "a\tb", "a\nb", "é", "a\u007fb", "x" * 8193)
        When("the rejected callback programs are interpreted")
        val programs = states.map(client.complete(request, _, _code, _observed)) ++
          codes.map(client.complete(request, _state, _, _observed)) ++ Vector(
            client.complete(null, _state, _code, _observed), client.complete(foreign, _state, _code, _observed),
            client.complete(request, _state, _code, null))
        val outcomes = programs.map(fixture.evaluate)
        Then("every input refusal is fixed and no HTTP authority or driver is acquired")
        outcomes.foreach(outcome => _same_failure(outcome, Consequence.argumentInvalid("OAuth authorization request is invalid")))
        fixture.acquisitions shouldBe 0
        fixture.requests shouldBe empty
      }

      "uses a generic provider's configured token endpoint without Google token extras" in {
        Given("an admitted generic provider and a synthetic token response")
        val provider = OAuthProvider.createC("generic", "https://issuer.example/authorize", "https://issuer.example/token").TAKE
        val fixture = new Harness(_ => _json_response(_token_json()), origins = Vector("https://issuer.example"))
        val client = _client(provider)
        val request = client.beginC(_state, _verifier).TAKE
        When("the generic exchange is interpreted")
        val outcome = fixture.evaluate(client.complete(request, _state, _code, _observed))
        Then("one POST reaches the fixed endpoint with token parameters only")
        outcome.isSuccess shouldBe true
        fixture.requests.map(_.path) shouldBe Vector("https://issuer.example/token")
        _decode(fixture.requests.head.text.get).keySet shouldBe Set("grant_type", "code", "redirect_uri", "client_id", "code_verifier", "client_secret")
      }
    }

    "strict provider responses" which {
      "accepts case-insensitive Bearer and integral JSON numbers while ignoring unrelated extension keys" in {
        Given("a successful JSON token with integral numeric expiry and unrelated provider extensions")
        val response = _token_json().deepMerge(Json.obj("token_type" -> Json.fromString("bEaReR"),
          "expires_in" -> parser.parse("3600.0").toOption.get, "provider_extension" -> Json.obj("untrusted" -> Json.True)))
        val fixture = new Harness(_ => _json_response(response))
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        When("the valid response is interpreted")
        val credential = fixture.evaluate(client.complete(request, _state, _code, _observed)).TAKE
        Then("the expiry and exact granted scopes are retained without extension-derived facts")
        credential.expiresAt shouldBe _observed.plusSeconds(3600)
        credential.grantedScopes shouldBe _scopes
        credential.canRefresh shouldBe true
      }

      "rejects malformed success JSON, MIME, tokens and expiry with a fixed resource failure" in {
        Given("successful HTTP responses with malformed credential shape or non-coercible fields")
        val valid = _token_json()
        val invalidtokens = Vector(null, "", "a b", "a,b", "a=b", "=abc", "é", "a\tb", "a" * 16385)
        val access = invalidtokens.map(value => valid.deepMerge(Json.obj("access_token" -> Option(value).fold(Json.Null)(Json.fromString))))
        val refreshtokens = Vector(null, "", "a b", "é", "a\nb", "r" * 16385).map { value =>
          valid.deepMerge(Json.obj("refresh_token" -> Option(value).fold(Json.Null)(Json.fromString)))
        }
        val expiry = Vector(Json.Null, Json.fromString("3600"), Json.fromInt(0), Json.fromInt(-1),
          Json.fromDoubleOrNull(1.5), Json.True, Json.obj(), Json.arr(),
          parser.parse("9223372036854775808").toOption.get).map(value => valid.deepMerge(Json.obj("expires_in" -> value)))
        val shapes = Vector(Json.Null, Json.arr(valid), Json.obj(), valid.mapObject(_.remove("access_token")),
          valid.mapObject(_.remove("token_type")), valid.mapObject(_.remove("expires_in")),
          valid.deepMerge(Json.obj("token_type" -> Json.Null)), valid.deepMerge(Json.obj("token_type" -> Json.fromString("Basic"))))
        val responses = (access ++ refreshtokens ++ expiry ++ shapes).map(_json_response(_)) ++
          Vector(_text_response("{provider-error-marker"), _text_response(valid.noSpaces, mime = "text/plain"))
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        When("each malformed response is interpreted once")
        val outcomes = responses.map { response =>
          val fixture = new Harness(_ => response)
          (fixture.evaluate(client.complete(request, _state, _code, _observed)), fixture)
        }
        Then("all fail as invalid resources without retry or provider text in diagnostics")
        outcomes.foreach { case (outcome, fixture) =>
          _same_failure(outcome, Consequence.resourceInvalid("OAuth authorization response is invalid"))
          fixture.requests.size shouldBe 1
          _disclosures(_failure(outcome)).foreach(output => output should not include "provider-error-marker")
        }
      }

      "rejects expiry overflow rather than producing an invalid or wrapped time" in {
        Given("positive integral expiry values outside the representable Instant range")
        val inputs = Vector((_observed, Long.MaxValue), (Instant.MAX, 1L), (Instant.MAX.minusSeconds(1), 2L))
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        When("expiry is derived from each observation time")
        val outcomes = inputs.map { case (observed, seconds) =>
          val fixture = new Harness(_ => _json_response(_token_json().deepMerge(Json.obj("expires_in" -> Json.fromLong(seconds)))))
          fixture.evaluate(client.complete(request, _state, _code, observed))
        }
        Then("each overflowing expiry is a fixed invalid-resource failure")
        outcomes.foreach(outcome => _same_failure(outcome, Consequence.resourceInvalid("OAuth authorization response is invalid")))
      }

      "accepts a narrowed grant and rejects empty, duplicate, malformed or widened scopes" in {
        Given("a request for two scopes and optional response scope representations")
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        val validfixture = new Harness(_ => _json_response(_token_json(scope = Some(_scopes.last))))
        val invalid = Vector(Json.Null, Json.fromInt(1), Json.fromString(""), Json.fromString("scope:a scope:a"),
          Json.fromString(" scope:a"), Json.fromString("scope:a "), Json.fromString("scope:a  scope:b"),
          Json.fromString("scope:a\tscope:b"), Json.fromString("scope:a unrequested"),
          Json.fromString("scope:\"a"), Json.fromString("scope:\\a"))
        When("the provider's narrowed and malformed scope grants are interpreted")
        val narrowed = validfixture.evaluate(client.complete(request, _state, _code, _observed)).TAKE
        val outcomes = invalid.map { value =>
          val fixture = new Harness(_ => _json_response(_token_json().deepMerge(Json.obj("scope" -> value))))
          fixture.evaluate(client.complete(request, _state, _code, _observed))
        }
        Then("only a nonempty unique subset is admitted and its order is preserved")
        narrowed.grantedScopes shouldBe Vector(_scopes.last)
        outcomes.foreach(outcome => _same_failure(outcome, Consequence.resourceInvalid("OAuth authorization response is invalid")))
      }

      "maps all denied, unavailable and other status classes without retry or provider error disclosure" in {
        Vector(400, 401, 403, 429, 500, 503, 599, 201, 204, 302, 404, 418).foreach { status =>
          Given(s"a provider response with HTTP status $status and synthetic error markers")
          val response = _text_response("provider-error-marker error_description synthetic-access-marker", status)
          val fixture = new Harness(_ => response)
          val client = _client()
          val request = client.beginC(_state, _verifier).TAKE
          val expected = response.code match {
            case 400 | 401 | 403 => Consequence.securityPermissionDenied("OAuth authorization was denied")
            case value if value == 429 || value >= 500 && value <= 599 =>
              Consequence.serviceUnavailable("OAuth authorization service is unavailable")
            case _ => Consequence.resourceInvalid("OAuth authorization response is invalid")
          }
          When("the token exchange interprets that response")
          val outcome = fixture.evaluate(client.complete(request, _state, _code, _observed))
          Then("the fixed native category is returned after exactly one POST")
          _same_failure(outcome, expected)
          fixture.requests.size shouldBe 1
          _disclosures(_failure(outcome)).foreach { output =>
            output should not include "provider-error-marker"
            output should not include "synthetic-access-marker"
            output should not include "error_description"
          }
        }
      }
    }

    "refresh continuity" which {
      "preserves missing refresh material and grants, then uses rotated refresh material on the next request" in {
        Given("an initial narrowed credential and successive missing, rotated and missing refresh responses")
        val client = _client(clientid = "id&+/%", secret = Some("secret&=+/%"))
        val request = client.beginC(_state, _verifier).TAKE
        var count = 0
        val fixture = new Harness(_ => {
          count += 1
          _json_response(count match {
            case 1 => _token_json(refresh = Some("initial/+&=%"), scope = Some(_scopes.head))
            case 2 => _token_json(refresh = None)
            case 3 => _token_json(refresh = Some("rotated/+&=%"))
            case _ => _token_json(refresh = None)
          })
        })
        When("the initial exchange and three refreshes are interpreted")
        val initial = fixture.evaluate(client.complete(request, _state, _code, _observed)).TAKE
        val preserved = fixture.evaluate(client.refresh(initial, _observed.plusSeconds(1))).TAKE
        val rotated = fixture.evaluate(client.refresh(preserved, _observed.plusSeconds(2))).TAKE
        val latest = fixture.evaluate(client.refresh(rotated, _observed.plusSeconds(3))).TAKE
        Then("each refresh is one exact form POST and the previous grant and replacement token remain coherent")
        Vector(initial, preserved, rotated, latest).foreach { credential =>
          credential.canRefresh shouldBe true
          credential.grantedScopes shouldBe Vector(_scopes.head)
        }
        latest.expiresAt shouldBe _observed.plusSeconds(3603)
        val forms = fixture.requests.tail.map(request => _decode(request.text.get))
        forms shouldBe Vector("initial/+&=%", "initial/+&=%", "rotated/+&=%").map(token =>
          Map("grant_type" -> "refresh_token", "refresh_token" -> token,
            "client_id" -> "id&+/%", "client_secret" -> "secret&=+/%"))
        fixture.requests.tail.foreach(request => request.text.get.split("&", -1).length shouldBe 4)
        fixture.requests.foreach { request =>
          request.path shouldBe _token
          request.method shouldBe "POST"
          request.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("16384")
        }
        fixture.requests.size shouldBe 4
      }

      "rejects a refresh grant that reintroduces a previously ungranted scope" in {
        Given("a narrowed initial grant and a provider refresh response attempting to restore another requested scope")
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        val initialfixture = new Harness(_ => _json_response(_token_json(scope = Some(_scopes.head))))
        val credential = initialfixture.evaluate(client.complete(request, _state, _code, _observed)).TAKE
        val fixture = new Harness(_ => _json_response(_token_json(scope = Some(_scopes.mkString(" ")))))
        When("the widening refresh response is interpreted")
        val outcome = fixture.evaluate(client.refresh(credential, _observed))
        Then("the previous grant cannot be enlarged")
        _same_failure(outcome, Consequence.resourceInvalid("OAuth authorization response is invalid"))
        fixture.requests.size shouldBe 1
      }

      "retains an explicitly narrowed refresh grant on subsequent refreshes" in {
        Given("a complete initial grant and a refresh response narrowing it to one requested scope")
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        val seed = new Harness(_ => _json_response(_token_json()))
        val initial = seed.evaluate(client.complete(request, _state, _code, _observed)).TAKE
        val narrowing = new Harness(_ => _json_response(_token_json(scope = Some(_scopes.last))))
        val preserving = new Harness(_ => _json_response(_token_json(refresh = None)))
        When("the narrowed and then scope-absent refreshes are interpreted")
        val narrowed = narrowing.evaluate(client.refresh(initial, _observed)).TAKE
        val retained = preserving.evaluate(client.refresh(narrowed, _observed)).TAKE
        Then("the exact narrowed grant persists and does not return to the original requested scopes")
        narrowed.grantedScopes shouldBe Vector(_scopes.last)
        retained.grantedScopes shouldBe narrowed.grantedScopes
        retained.canRefresh shouldBe true
        narrowing.requests.size shouldBe 1
        preserving.requests.size shouldBe 1
      }

      "rejects the complete malformed token, expiry, scope and status matrix on refresh without retry" in {
        Given("an initial refreshable grant and provider refresh responses violating each response field contract")
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        val seed = new Harness(_ => _json_response(_token_json()))
        val credential = seed.evaluate(client.complete(request, _state, _code, _observed)).TAKE
        val valid = _token_json()
        val missing = Vector("access_token", "token_type", "expires_in").map(key => valid.mapObject(_.remove(key)))
        val malformed = Vector(
          "access_token" -> Vector(Json.Null, Json.True, Json.fromInt(1), Json.fromString(""), Json.fromString("bad token"),
            Json.fromString("a,b"), Json.fromString("=bad"), Json.fromString("a=b"), Json.fromString("é"),
            Json.fromString("t" * 16385)),
          "token_type" -> Vector(Json.Null, Json.True, Json.fromString(""), Json.fromString("Basic")),
          "expires_in" -> Vector(Json.Null, Json.True, Json.fromString("3600"), Json.fromInt(0), Json.fromInt(-1),
            Json.fromDoubleOrNull(1.5), Json.obj(), parser.parse("9223372036854775808").toOption.get,
            Json.fromLong(Long.MaxValue)),
          "refresh_token" -> Vector(Json.Null, Json.True, Json.fromString(""), Json.fromString("a b"),
            Json.fromString("a\nb"), Json.fromString("é"), Json.fromString("r" * 16385)),
          "scope" -> Vector(Json.Null, Json.True, Json.arr(), Json.fromString(""), Json.fromString("scope:a scope:a"),
            Json.fromString(" scope:a"), Json.fromString("scope:a "), Json.fromString("scope:a  scope:b"),
            Json.fromString("scope:a\tscope:b"), Json.fromString("unrequested"), Json.fromString("scope:\"a"),
            Json.fromString("scope:\\a")))
          .flatMap { case (key, values) => values.map(value => valid.deepMerge(Json.obj(key -> value))) }
        val invalidresponses = (missing ++ malformed ++ Vector(Json.Null, Json.arr(), Json.obj())).map(_json_response(_)) ++
          Vector(_text_response("{provider-error-marker"), _text_response(valid.noSpaces, mime = "text/plain"))
        val statusresponses = Vector(400, 401, 403, 429, 500, 503, 599, 201, 204, 302, 404)
          .map(status => _text_response("provider-error-marker", status))
        When("each malformed or non-successful refresh response is interpreted once")
        val outcomes = (invalidresponses ++ statusresponses).map { response =>
          val fixture = new Harness(_ => response)
          (fixture.evaluate(client.refresh(credential, _observed)), fixture, response.code)
        }
        Then("refresh preserves the fixed status taxonomy and refuses every malformed success without another request")
        outcomes.foreach { case (outcome, fixture, status) =>
          val expected = status match {
            case 400 | 401 | 403 => Consequence.securityPermissionDenied("OAuth authorization was denied")
            case value if value == 429 || value >= 500 && value <= 599 =>
              Consequence.serviceUnavailable("OAuth authorization service is unavailable")
            case _ => Consequence.resourceInvalid("OAuth authorization response is invalid")
          }
          _same_failure(outcome, expected)
          fixture.requests.size shouldBe 1
          _disclosures(_failure(outcome)).foreach(output => output should not include "provider-error-marker")
        }
      }

      "rejects null, foreign and invalid-time credentials before acquiring HTTP" in {
        Given("an admitted credential owned by another client and a fresh counted runtime")
        val owner = _client()
        val request = owner.beginC(_state, _verifier).TAKE
        val seed = new Harness(_ => _json_response(_token_json()))
        val credential = seed.evaluate(owner.complete(request, _state, _code, _observed)).TAKE
        val fixture = new Harness(_ => throw new AssertionError("Invalid refresh must not use HTTP"))
        val foreign = _client()
        When("invalid refresh programs are interpreted")
        val outcomes = Vector(fixture.evaluate(owner.refresh(null, _observed)),
          fixture.evaluate(foreign.refresh(credential, _observed)), fixture.evaluate(owner.refresh(credential, null)))
        Then("each refusal is fixed and the driver remains untouched")
        outcomes.foreach(outcome => _same_failure(outcome, Consequence.argumentInvalid("OAuth authorization request is invalid")))
        fixture.acquisitions shouldBe 0
        fixture.requests shouldBe empty
      }

      "applies the same status and malformed-success contract to refresh and omits an absent client secret" in {
        Given("a public client with an initial refresh grant and malformed or denied refresh responses")
        val client = _client(secret = None)
        val request = client.beginC(_state, _verifier).TAKE
        val seed = new Harness(_ => _json_response(_token_json()))
        val credential = seed.evaluate(client.complete(request, _state, _code, _observed)).TAKE
        val responses = Vector(_json_response(_token_json().deepMerge(Json.obj("refresh_token" -> Json.Null))),
          _json_response(_token_json().deepMerge(Json.obj("access_token" -> Json.fromString("bad token")))) ,
          _text_response("provider-error-marker", 401), _text_response("provider-error-marker", 503))
        When("the refresh programs are interpreted")
        val outcomes = responses.map { response =>
          val fixture = new Harness(_ => response)
          (fixture.evaluate(client.refresh(credential, _observed)), fixture)
        }
        Then("refresh uses the same failure taxonomy and sends exactly the three public-client refresh fields")
        outcomes.zipWithIndex.foreach { case ((outcome, fixture), index) =>
          val expected = index match {
            case 2 => Consequence.securityPermissionDenied("OAuth authorization was denied")
            case 3 => Consequence.serviceUnavailable("OAuth authorization service is unavailable")
            case _ => Consequence.resourceInvalid("OAuth authorization response is invalid")
          }
          _same_failure(outcome, expected)
          fixture.requests.size shouldBe 1
          _decode(fixture.requests.head.text.get).keySet shouldBe Set("grant_type", "refresh_token", "client_id")
        }
      }
    }

    "native authority and diagnostic safety" which {
      "denies an ungranted origin before driver acquisition and preserves its native Conclusion" in {
        Given("a real Component scope whose runtime grants only an unrelated origin")
        val fixture = new Harness(_ => throw new AssertionError("OAuth origin was not admitted"),
          origins = Vector("https://unrelated.example"))
        val native = _failure(HttpExecutionAdmission.resolveC(fixture.context, HttpExecutionMethod.Post, _token,
          Vector(Property("http.max-response-bytes", "16384", None))))
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        When("the token program requests native admission")
        val actual = _failure(fixture.evaluate(client.complete(request, _state, _code, _observed)))
        Then("the native denial retains its structured facts and no driver is acquired")
        actual.status shouldBe native.status
        actual.observation.isMatch(native.observation) shouldBe true
        actual.interpretation shouldBe native.interpretation
        actual.disposition shouldBe native.disposition
        ConclusionDiagnostics.classify(actual).policy shouldBe Some("http.execution.destination")
        fixture.acquisitions shouldBe 0
        fixture.requests shouldBe empty
      }

      "allows the host to tighten the required response bound" in {
        Given("a host response ceiling lower than the OAuth request ceiling")
        val fixture = new Harness(_ => _json_response(_token_json()), bytes = 1024)
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        When("the token program is admitted through the normal runtime")
        val outcome = fixture.evaluate(client.complete(request, _state, _code, _observed))
        Then("the admitted driver receives the tighter host ceiling")
        outcome.isSuccess shouldBe true
        fixture.requests.head.properties.find(_.name == "http.max-response-bytes").map(_.value.toString) shouldBe Some("1024")
      }

      "preserves transport Conclusions while redacting code, verifier, secret and tokens in actual projections" in {
        Given("enabled native calltrees and a controlled transport exception carrying synthetic secret markers")
        val markers = Vector(_code, _verifier, "synthetic-secret-marker", "synthetic-access-marker",
          "synthetic-refresh-marker", "provider-error-marker")
        val error = new IOException(markers.mkString(" "))
        val fixture = new Harness(_ => throw error, enabled = true)
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        When("the failed exchange is interpreted through the actual UnitOfWork interpreter")
        val actual = _failure(fixture.evaluate(client.complete(request, _state, _code, _observed)))
        val outputs = _disclosures(actual)
        val trace = fixture.trace
        Then("the native exception remains intact while HTTP standard, full, log and CallTree views omit every marker")
        actual.getException.get should be theSameInstanceAs error
        actual.status shouldBe Conclusion.from(error).status
        actual.observation.taxonomy shouldBe Conclusion.from(error).observation.taxonomy
        fixture.requests.size shouldBe 1
        trace should include ("uow:http:post")
        (outputs :+ trace).foreach(output => markers.foreach(marker => output should not include marker))
        outputs.foreach { output =>
          val json = parser.parse(output).toOption.get
          json.hcursor.downField("status").focus shouldBe Some(_json_record(actual.status.toRecord))
          json.hcursor.downField("observation").downField("taxonomy").focus shouldBe Some(_json_record(actual.observation.taxonomy.toRecord))
        }
      }

      "redacts successful exchange and refresh calltrees as well as provider-denied Conclusions" in {
        Given("enabled native calltrees for successful exchange, refresh and a provider-denied response")
        val client = _client()
        val request = client.beginC(_state, _verifier).TAKE
        val fixture = new Harness(_ => _json_response(_token_json()), enabled = true)
        val denied = new Harness(_ => _text_response("provider-error-marker", 400), enabled = true)
        When("successful and denied token programs are interpreted")
        val credential = fixture.evaluate(client.complete(request, _state, _code, _observed)).TAKE
        val refreshed = fixture.evaluate(client.refresh(credential, _observed)).TAKE
        val failure = _failure(denied.evaluate(client.complete(request, _state, _code, _observed)))
        Then("public displays and real diagnostic projections retain no outbound or provider secrets")
        val outputs = Vector(fixture.trace, denied.trace, credential.toString, refreshed.toString) ++ _disclosures(failure)
        outputs.foreach(output => Vector(_code, _verifier, "synthetic-secret-marker", "synthetic-access-marker",
          "synthetic-refresh-marker", "provider-error-marker").foreach(marker => output should not include marker))
        fixture.requests.size shouldBe 2
        denied.requests.size shouldBe 1
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

/** Private, deterministic fixture using public runtime admission and the normal native interpreter. */
object OAuthClientSpec {
  private val _authorization = "https://accounts.google.com/o/oauth2/v2/auth"
  private val _token = "https://oauth2.googleapis.com/token"
  private val _redirect = "http://127.0.0.1:8123/oauth/callback"
  private val _client_id = "synthetic-client"
  private val _state = "synthetic-state-0123456789abcdefghijklmnopqrstu"
  private val _verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
  private val _code = "synthetic-code-marker"
  private val _scopes = Vector("scope:a", "scope:b")
  private val _observed = Instant.parse("2026-10-21T00:00:00Z")

  private final case class Request(method: String, path: String, text: Option[String],
    headers: Map[String, String], properties: Vector[Property])

  private final class Harness(answer: Request => HttpResponse,
    origins: Vector[String] = Vector("https://oauth2.googleapis.com"), bytes: Long = 1048576, enabled: Boolean = false) {
    private var _requests = Vector.empty[Request]
    private var _acquisitions = 0
    val context: ExecutionContext = {
      val owner = ComponentId("org.example.OAuthOwner")
      val admission = HttpExecutionAdmission.fromRuntimeC("https://oauth2.googleapis.com/",
        HttpExecutionLimits(1000, 1000, bytes), Map(owner -> HttpExecutionGrant(origins, Set(HttpExecutionMethod.Post)))).TAKE
      val base = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled)
      val root = ScopeContext.Instance(base.cncfCore.scope.core.copy(httpExecutionAdmissionOption = Some(admission)))
      val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
      component.withScopeContext(root)
      base.withScope(component.scopeContext)
    }
    private val _driver = new HttpDriver {
      def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        throw new AssertionError("OAuth uses POST only")
      def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
        val request = Request("POST", path, body, headers, properties)
        _requests = _requests :+ request
        answer(request)
      }
      def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
        throw new AssertionError("OAuth uses POST only")
    }
    def requests: Vector[Request] = _requests
    def acquisitions: Int = _acquisitions
    def trace: String = context.cncfCore.observability.callTreeContext.build()
      .map(tree => RecordEncoder.json(tree.toRecord)).getOrElse("")
    def evaluate[A](program: ExecUowM[A]): Consequence[A] = {
      val uow = new UnitOfWork(context) {
        override def httpDriver: HttpDriver = { _acquisitions += 1; _driver }
      }
      new UnitOfWorkInterpreter(uow).run(program)
    }
  }

  private def _client(provider: OAuthProvider = OAuthProvider.googleDrive, clientid: String = _client_id,
    secret: Option[String] = Some("synthetic-secret-marker"), redirect: String = _redirect,
    scopes: Vector[String] = _scopes): OAuthClient = OAuthClient.createC(provider, clientid, secret, redirect, scopes).TAKE
  private def _query(request: OAuthAuthorizationRequest): Map[String, String] = _decode(new URI(request.authorizationUri).getRawQuery)
  private def _decode(form: String): Map[String, String] = form.split("&", -1).map { part =>
    val pair = part.split("=", 2)
    URLDecoder.decode(pair(0), StandardCharsets.UTF_8) -> URLDecoder.decode(pair(1), StandardCharsets.UTF_8)
  }.toMap
  private def _failure[A](outcome: Consequence[A]): Conclusion = outcome match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => throw new AssertionError("Expected a structured failure")
  }
  private def _json_record(record: Record): Json = parser.parse(RecordEncoder.json(record)).toOption.get
  private def _disclosures(conclusion: Conclusion): Vector[String] = Vector(
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Standard),
    ConclusionDisclosure.http(conclusion, ConclusionDisclosure.Detail.Full), ConclusionDisclosure.log(conclusion)).map(RecordEncoder.json)
  private def _token_json(refresh: Option[String] = Some("synthetic-refresh-marker"), scope: Option[String] = None): Json =
    Json.obj((Vector("access_token" -> Json.fromString("synthetic-access-marker"), "token_type" -> Json.fromString("Bearer"),
      "expires_in" -> Json.fromInt(3600)) ++ refresh.map(value => "refresh_token" -> Json.fromString(value)) ++
      scope.map(value => "scope" -> Json.fromString(value)))*)
  private def _json_response(json: Json): HttpResponse = _text_response(json.noSpaces)
  private def _text_response(text: String, code: Int = 200, mime: String = "application/json"): HttpResponse =
    HttpResponse.Text(HttpStatus.fromInt(code).getOrElse(HttpStatus.InternalServerError),
      ContentType(MimeType(mime), Some(StandardCharsets.UTF_8)), Bag.text(text, StandardCharsets.UTF_8), Record.empty)
}
