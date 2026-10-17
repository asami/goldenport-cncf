package org.goldenport.cncf.http

import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.effect.{ExternalEffectClassification, ExternalEffectKind, ExternalEffectRoute}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.protocol.{Property, Protocol}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpExecutionAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _owner = ComponentId("org.example.HttpOwner")
  private val _other = ComponentId("org.example.OtherHttpOwner")
  private val _limits = HttpExecutionLimits(5000, 7000, 4096)
  private val _secret = "confidential-http-sentinel"
  private val _controlled_names = Vector("http.total-timeout-ms", "http.connect-timeout-ms", "http.read-timeout-ms",
    "http.connect-timeout-seconds", "http.read-timeout-seconds", "http.timeout-seconds",
    "http.max-response-bytes", "http.follow-redirects")

  private def _control_spelling(name: String): Gen[String] = {
    val ascii = name.foldLeft(Gen.const("")) { (prefix, letter) =>
      for { prior <- prefix; next <- Gen.oneOf(letter, letter.toUpper) } yield prior + next
    }
    for {
      spelling <- ascii
      index <- Gen.oneOf(name.indices.filter(index => name(index) == 'i' || name(index) == 's').toVector)
      replacement <- if (name(index) == 'i') Gen.oneOf('\u0131', '\u0130') else Gen.const('\u017f')
    } yield spelling.updated(index, replacement)
  }

  private def _control_spellings: Gen[Vector[String]] =
    _controlled_names.foldLeft(Gen.const(Vector.empty[String])) { (prior, name) =>
      for { names <- prior; spelling <- _control_spelling(name) } yield names :+ spelling
    }

  private def _take[A](value: Consequence[A]): A =
    value.getOrElse(fail("HTTP fixture admission failed"))

  private def _admission(
    base: String = "https://example.test/api/",
    origins: Vector[String] = Vector("https://example.test"),
    methods: Set[HttpExecutionMethod] = Set(HttpExecutionMethod.Get)
  ): HttpExecutionAdmission =
    _take(HttpExecutionAdmission.fromRuntimeC(base, _limits, Map(_owner -> HttpExecutionGrant(origins, methods))))

  private def _context(
    admission: Option[HttpExecutionAdmission],
    owner: ComponentId = _owner,
    actual: Boolean = true
  ): ExecutionContext = {
    val base = ExecutionContext.create()
    val root = ScopeContext.Instance(base.cncfCore.scope.core.copy(httpExecutionAdmissionOption = admission))
    val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
    val scope = if (actual) Component.Context("same-display-label", root, component, ComponentOrigin.Embed)
      else root.createChildScope(ScopeKind.Component, _owner.name)
    base.withScope(scope.createChildScope(ScopeKind.Service, "http").createChildScope(ScopeKind.Action, "fetch"))
  }

  private def _policy[A](value: Consequence[A]): Option[String] = value match {
    case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion).policy
    case _ => None
  }

  private def _resolve(
    context: ExecutionContext,
    path: String,
    method: HttpExecutionMethod = HttpExecutionMethod.Get,
    properties: Vector[Property] = Vector.empty
  ): Consequence[AdmittedHttpExecution] = HttpExecutionAdmission.resolveC(context, method, path, properties)

  "Current Component HTTP authority" should {
    "resolve relative paths and queries once against the host base while retaining encoded bytes" in {
      Given("an actual owner beneath the configured host base path")
      val context = _context(Some(_admission()))
      val paths = Vector("item%2Fpart?q=a%26b", "/root?q=%2B", "?q=selected")
      When("the current UoW context resolves each ordinary URI reference")
      val outcomes = paths.map(path => _take(_resolve(context, path)))
      Then("URI.resolve semantics preserve raw encoded path and query in absolute destinations")
      outcomes.map(_.absoluteUrl) shouldBe Vector("https://example.test/api/item%2Fpart?q=a%26b",
        "https://example.test/root?q=%2B", "https://example.test/api/?q=selected")
      HttpExecutionAdmission.classification shouldBe ExternalEffectClassification(Set(ExternalEffectKind.Http), ExternalEffectRoute.Managed)
    }

    "distinguish absence, scope labels, actual identities and method grants in fixed order" in {
      Given("configured owner authority and same-label scopes carrying a different or absent actual Component")
      val admission = _admission()
      val contexts = Vector(_context(None), _context(Some(admission), actual = false),
        _context(Some(admission), owner = _other), _context(Some(admission)))
      val metadata = Vector(Property("componentId", _owner.name, None),
        Property("http.execution.grant", _secret, None), Property("origin", "https://example.test", None))
      When("the contexts request POST with purported request grants")
      val outcomes = contexts.map(context => _resolve(context, "/selected", HttpExecutionMethod.Post, metadata))
      Then("metadata and display labels confer no authority and diagnostics retain only fixed safe policy facts")
      outcomes.map(_policy) shouldBe Vector("unconfigured", "component", "grant", "method").map(x => Some(s"http.execution.$x"))
      outcomes.foreach { result =>
        result.isFaillure shouldBe true
        result.toString should not include _secret
        result.toString should not include _owner.name
      }
    }

    "use the nearest actual Component even when an owner exists farther up the ancestry" in {
      Given("a wrong Component nested below an admitted Component")
      val outer = _context(Some(_admission()))
      val component = Component.create(_other.name, _other, ComponentInstanceId.default(_other), Protocol.empty)
      val current = outer.withScope(Component.Context("same-display-label", outer.cncfCore.scope, component, ComponentOrigin.Embed))
      When("the nested Component requests the otherwise authorized destination")
      val outcome = _resolve(current, "/selected")
      Then("the enclosing owner's grant is not borrowed")
      _policy(outcome) shouldBe Some("http.execution.grant")
    }

    "canonicalize generated scheme and host case and effective default ports without merging distinct ports" in {
      Given("bounded generated HTTP and HTTPS case/default-port spellings and a finite host grant")
      val variants = for {
        secure <- Gen.oneOf(true, false)
        upper <- Gen.oneOf(true, false)
        explicit <- Gen.oneOf(true, false)
      } yield (secure, upper, explicit)
      When("each generated absolute URL is compared by canonical origin")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(40), Prop.forAll(variants) {
        case (secure, upper, explicit) =>
          val scheme = if (secure) "https" else "http"
          val port = if (secure) 443 else 80
          val origin = s"$scheme://example.test"
          val admission = _admission(s"$origin/", Vector(origin))
          val prefix = if (upper) origin.toUpperCase(Locale.ROOT) else origin
          val url = prefix + (if (explicit) s":$port" else "") + "/item%2Fpart?q=%2B"
          val accepted = _resolve(_context(Some(admission)), url)
          val distinct = _resolve(_context(Some(admission)), s"$origin:${port + 1}/selected")
          accepted.toOption.exists(_.absoluteUrl.startsWith(s"$scheme://")) &&
            accepted.toOption.exists(_.absoluteUrl.endsWith("/item%2Fpart?q=%2B")) &&
            _policy(distinct).contains("http.execution.destination")
      })
      Then("case and standard ports are equivalent while each other port remains separately denied")
      checked.passed shouldBe true
    }

    "refuse malformed and alternate destinations without disclosing confidential URL contents" in {
      Given("an exact origin grant and confidential disallowed or malformed URI references")
      val context = _context(Some(_admission()))
      val paths = Vector(s"https://other.test/$_secret", s"https://example.test.evil/$_secret", s"//example.test/$_secret",
        s"https://user:$_secret@example.test/", s"https://example.test/#$_secret", s"file:/$_secret",
        s"https://example.test:0/$_secret", s"https://example.test:/$_secret", s"/$_secret\\path", s"/$_secret\npath", s"/$_secret%xx")
      When("each reference reaches pure destination admission")
      val outcomes = paths.map(path => _resolve(context, path))
      Then("every outcome is the same safe destination policy failure")
      outcomes.foreach { outcome =>
        _policy(outcome) shouldBe Some("http.execution.destination")
        outcome.toString should not include _secret
        outcome.toString should not include "example.test"
      }
    }

    "validate host base, exact root origins and finite positive bounds before compiling authority" in {
      Given("invalid host bases, non-origin grants and nonpositive host ceilings")
      val bases = Vector("relative/", "ftp://example.test/", "https://example.test/?secret", "https://example.test/#secret", "https://example.test:65536/")
      val origins = Vector("https://*.example.test", "https://example.test/path", "https://example.test/?secret",
        "https://example.test#secret", "https://example.test:0", "https://user:secret@example.test")
      val bounds = Vector(HttpExecutionLimits(0, 1, 1), HttpExecutionLimits(1, -1, 1), HttpExecutionLimits(1, 1, 0))
      When("the host factory compiles these declarations and duplicate canonical origins")
      val outcomes = bases.map(base => HttpExecutionAdmission.fromRuntimeC(base, _limits, Map.empty)) ++
        origins.map(origin => HttpExecutionAdmission.fromRuntimeC("https://example.test/", _limits,
          Map(_owner -> HttpExecutionGrant(Vector(origin), Set(HttpExecutionMethod.Get))))) ++
        bounds.map(limits => HttpExecutionAdmission.fromRuntimeC("https://example.test/", limits, Map.empty)) ++
        Vector(HttpExecutionAdmission.fromRuntimeC("https://example.test/", _limits,
          Map(_owner -> HttpExecutionGrant(Vector("https://example.test", "HTTPS://EXAMPLE.TEST:443/"), Set(HttpExecutionMethod.Get)))))
      Then("no invalid raw declaration becomes runtime admission")
      outcomes.foreach(_.isFaillure shouldBe true)
    }
  }

  "Finite request controls" should {
    "canonicalize generated Unicode-equivalent controls and cap every time and byte limit" in {
      Given("host ceilings, generated finite values and Unicode-equivalent controls with mixed ASCII case")
      val context = _context(Some(_admission()))
      val first = Property("application.first", "keep-1", None)
      val last = Property("http.total-timeout-ms.extra", "keep-2", None)
      val values = for {
        total <- Gen.choose(1, Int.MaxValue)
        connect <- Gen.choose(1, 10000)
        read <- Gen.choose(1, 10000)
        connectseconds <- Gen.choose(1, 10)
        readseconds <- Gen.choose(1, 10)
        generic <- Gen.choose(1, 10)
        bytes <- Gen.choose(0L, 8192L)
        names <- _control_spellings
      } yield (total, connect, read, connectseconds, readseconds, generic, bytes, names)
      When("ordinary admission normalizes every generated bounded request")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(80), Prop.forAll(values) {
        case (total, connect, read, connectseconds, readseconds, generic, bytes, names) =>
          val texts = Vector(total.toString, connect.toString, read.toString, connectseconds.toString,
            readseconds.toString, generic.toString, bytes.toString, "false")
          val properties = Vector(first) ++ names.zip(texts).map { case (name, text) =>
            Property(name, s" $text ", None)
          } ++ Vector(last)
          val expectedconnect = Vector(_limits.connectTimeoutMillis, connect, connectseconds * 1000, generic * 1000).min
          val expectedread = Vector(_limits.readTimeoutMillis, read, readseconds * 1000, generic * 1000).min
          _resolve(context, "/selected", properties = properties).toOption.exists { admitted =>
            admitted.properties == Vector(first, last,
              Property("http.total-timeout-ms", Vector(total, expectedconnect, expectedread).min.toString, None),
              Property("http.connect-timeout-ms", expectedconnect.toString, None),
              Property("http.read-timeout-ms", expectedread.toString, None),
              Property("http.max-response-bytes", math.min(bytes, _limits.responseBytes).toString, None),
              Property("http.follow-redirects", "false", None))
          }
      })
      Then("equivalent keys become the exact ordered canonical minima while genuinely distinct properties retain their order")
      checked.passed shouldBe true
      checked.succeeded should be >= 80
      checked.discarded shouldBe 0
    }

    "refuse generated malformed Unicode-equivalent controls with safe limits failures" in {
      Given("Unicode-equivalent spellings of every control and values invalid for its numeric or redirect contract")
      val context = _context(Some(_admission()))
      val malformed = _controlled_names.map { name =>
        if (name == "http.follow-redirects") Vector(_secret, "", "maybe")
        else if (name == "http.max-response-bytes") Vector(_secret, "", "-1", "9223372036854775808")
        else Vector(_secret, "", "0", "-1", "2147483648", "9223372036854775808")
      }
      When("ordinary admission interprets each generated malformed equivalent control")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(60), Prop.forAll(_control_spellings) { names =>
        names.zip(malformed).forall { case (name, texts) =>
          texts.forall { text =>
            val outcome = _resolve(context, "/selected", properties = Vector(Property(name, text, None)))
            _policy(outcome).contains("http.execution.limits") && !outcome.toString.contains(_secret)
          }
        }
      })
      Then("no equivalent key evades validation or exposes the caller's invalid value")
      checked.passed shouldBe true
      checked.discarded shouldBe 0
    }

    "refuse ASCII and generated Unicode-equivalent duplicates in either order" in {
      Given("every canonical controlled name paired with a generated Unicode-equivalent spelling")
      val context = _context(Some(_admission()))
      When("ordinary admission receives each ASCII and Unicode pair in both orders")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(60), Prop.forAll(_control_spellings) { names =>
        _controlled_names.zip(names).forall { case (canonical, equivalent) =>
          val value = if (canonical == "http.follow-redirects") "false" else "100"
          val pair = Vector(Property(canonical, value, None), Property(equivalent, _secret, None))
          Vector(pair, pair.reverse).forall { properties =>
            val outcome = _resolve(context, "/selected", properties = properties)
            _policy(outcome).contains("http.execution.limits") && !outcome.toString.contains(_secret)
          }
        }
      })
      Then("total, byte and every other equivalent duplicate return the same safe limits policy")
      checked.passed shouldBe true
      checked.discarded shouldBe 0
    }

    "retain a stricter caller total and canonicalize mixed case and whitespace in property order" in {
      Given("a total below both host ceilings, mixed-case controls and ordered unrelated properties")
      val context = _context(Some(_admission()))
      val first = Property("application.first", "keep-1", None)
      val last = Property("application.last", "keep-2", None)
      val properties = Vector(first, Property("HtTp.ToTaL-TiMeOuT-Ms", " 125 ", None),
        Property("HTTP.CONNECT-TIMEOUT-MS", " 4500 ", None), last)
      When("ordinary admission compiles the optional caller total")
      val admitted = _take(_resolve(context, "/selected", properties = properties))
      Then("unrelated properties precede a single canonical stricter total and the four existing controls")
      admitted.properties shouldBe Vector(first, last,
        Property("http.total-timeout-ms", "125", None),
        Property("http.connect-timeout-ms", "4500", None),
        Property("http.read-timeout-ms", "7000", None),
        Property("http.max-response-bytes", "4096", None),
        Property("http.follow-redirects", "false", None))
    }

    "reject invalid or duplicate total budgets safely after redirect refusal" in {
      Given("malformed, empty, nonpositive, overflowing and case-folded duplicate total controls")
      val context = _context(Some(_admission()))
      val invalid = Vector(_secret, "", " ", "0", "-1", "2147483648", "9223372036854775808")
        .map(text => Vector(Property("http.total-timeout-ms", text, None))) ++ Vector(
          Vector(Property("http.total-timeout-ms", "100", None), Property("HTTP.TOTAL-TIMEOUT-MS", "200", None)),
          Vector(Property("HTTP.TOTAL-TIMEOUT-MS", "100", None), Property("http.total-timeout-ms", _secret, None)))
      When("each invalid total reaches ordinary admission with and without redirect opt-in")
      val outcomes = invalid.map(properties => _resolve(context, "/selected", properties = properties))
      val redirects = invalid.map(properties => _resolve(context, "/selected",
        properties = properties :+ Property("http.follow-redirects", "true", None)))
      Then("totals use fixed structured limits failures and redirect opt-in keeps precedence")
      outcomes.foreach { outcome =>
        _policy(outcome) shouldBe Some("http.execution.limits")
        outcome.toString should not include _secret
      }
      redirects.foreach(outcome => _policy(outcome) shouldBe Some("http.execution.redirect"))
    }

    "emit exactly the legacy four controls when no caller total is present" in {
      Given("host authority and an unrelated property without any total budget")
      val context = _context(Some(_admission()))
      val unrelated = Property("application.value", "keep", None)
      When("ordinary admission resolves an otherwise uncontrolled request")
      val admitted = _take(_resolve(context, "/selected", properties = Vector(unrelated)))
      Then("no total is synthesized and existing control values and order remain exact")
      admitted.properties shouldBe Vector(unrelated,
        Property("http.connect-timeout-ms", "5000", None),
        Property("http.read-timeout-ms", "7000", None),
        Property("http.max-response-bytes", "4096", None),
        Property("http.follow-redirects", "false", None))
    }

    "combine distinct timeout aliases by minimum and preserve unrelated properties in order" in {
      Given("host limits and case-insensitive caller controls with both ms and second aliases")
      val context = _context(Some(_admission()))
      val first = Property("application.first", "keep-1", None)
      val last = Property("http.public-network-only", "true", None)
      val properties = Vector(first, Property("HTTP.CONNECT-TIMEOUT-MS", "4000", None),
        Property("http.connect-timeout-seconds", "2", None), Property("http.timeout-seconds", "3", None),
        Property("http.read-timeout-ms", "6000", None), Property("http.max-response-bytes", "0", None),
        Property("HTTP.FOLLOW-REDIRECTS", "false", None), last)
      When("the caller requests tighter time and response controls")
      val admitted = _take(_resolve(context, "/selected", properties = properties))
      Then("exactly one finite canonical value is emitted for each control and redirects stay disabled")
      admitted.properties shouldBe Vector(first, last, Property("http.connect-timeout-ms", "2000", None),
        Property("http.read-timeout-ms", "3000", None), Property("http.max-response-bytes", "0", None),
        Property("http.follow-redirects", "false", None))
    }

    "never widen generated valid caller limits above the host ceilings" in {
      Given("generated positive finite caller limits both below and above each host ceiling")
      val context = _context(Some(_admission()))
      val values = for { connect <- Gen.choose(1, 10000); read <- Gen.choose(1, 10000); bytes <- Gen.choose(0L, 8192L) } yield (connect, read, bytes)
      When("the finite controls are compiled for every generated request")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(60), Prop.forAll(values) {
        case (connect, read, bytes) =>
          val outcome = _resolve(context, "/selected", properties = Vector(
            Property("http.connect-timeout-ms", connect.toString, None), Property("http.read-timeout-ms", read.toString, None),
            Property("http.max-response-bytes", bytes.toString, None)))
          outcome.toOption.exists { admitted =>
            val controls = admitted.properties.map(x => x.name -> x.value.toString).toMap
            controls("http.connect-timeout-ms") == math.min(connect, _limits.connectTimeoutMillis).toString &&
              controls("http.read-timeout-ms") == math.min(read, _limits.readTimeoutMillis).toString &&
              controls("http.max-response-bytes") == math.min(bytes, _limits.responseBytes).toString &&
              controls("http.follow-redirects") == "false"
          }
      })
      Then("all admitted limits tighten by minimum and every route disables redirects")
      checked.passed shouldBe true
    }

    "refuse redirect opt-in, ambiguous controls and malformed or overflowing values" in {
      Given("a configured owner and caller controls that cannot define an unambiguous finite request")
      val context = _context(Some(_admission()))
      val invalid = Vector(
        Vector(Property("http.connect-timeout-ms", "1", None), Property("HTTP.CONNECT-TIMEOUT-MS", "2", None)),
        Vector(Property("http.read-timeout-ms", "0", None)), Vector(Property("http.timeout-seconds", "2147484", None)),
        Vector(Property("http.max-response-bytes", "-1", None)), Vector(Property("http.read-timeout-ms", _secret, None)),
        Vector(Property("http.follow-redirects", "maybe", None)))
      When("the request policy compiles these controls and an explicit redirect opt-in")
      val outcomes = invalid.map(properties => _resolve(context, "/selected", properties = properties))
      val redirect = _resolve(context, "/selected", properties = Vector(Property("http.follow-redirects", "true", None)))
      Then("invalid controls are refused with safe limits diagnostics and redirect opt-in has its own refusal")
      outcomes.foreach { outcome =>
        _policy(outcome) shouldBe Some("http.execution.limits")
        outcome.toString should not include _secret
      }
      _policy(redirect) shouldBe Some("http.execution.redirect")
    }
  }
}
