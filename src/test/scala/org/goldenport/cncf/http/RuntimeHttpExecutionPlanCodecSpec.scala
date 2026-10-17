package org.goldenport.cncf.http

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.{Property, Protocol}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeHttpExecutionPlanCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _owner = ComponentId("org.example.HttpWireOwner")
  private val _secret = "confidential-http-codec-sentinel"
  private val _grant = Json.obj("componentId" -> Json.fromString(_owner.name),
    "origins" -> Json.arr(Json.fromString("https://example.test")),
    "methods" -> Json.arr(Json.fromString("GET"), Json.fromString("POST"), Json.fromString("PUT")))
  private val _root = Json.obj("schema" -> Json.fromString(RuntimeHttpExecutionPlanCodec.SCHEMA),
    "baseUrl" -> Json.fromString("HTTPS://EXAMPLE.TEST/api/"),
    "limits" -> Json.obj("connectTimeoutMillis" -> Json.fromInt(1000), "readTimeoutMillis" -> Json.fromInt(2000),
      "responseBytes" -> Json.fromLong(4096)), "grants" -> Json.arr(_grant))

  private def _take[A](value: Consequence[A]): A = value.getOrElse(fail("HTTP codec fixture failed"))
  private def _decode(json: Json): Consequence[RuntimeHttpExecutionPlan] =
    RuntimeHttpExecutionPlanCodec.decode(ConfigurationValue.StringValue(json.noSpaces))
  private def _changed_grant(change: Json => Json): Json = _root.mapObject(_.add("grants", Json.arr(change(_grant))))
  private def _context(plan: RuntimeHttpExecutionPlan): ExecutionContext = {
    val base = ExecutionContext.create()
    val root = ScopeContext.Instance(base.cncfCore.scope.core.copy(httpExecutionAdmissionOption = Some(plan.admission)))
    val component = Component.create(_owner.name, _owner, ComponentInstanceId.default(_owner), Protocol.empty)
    component.withScopeContext(root)
    base.withScope(component.scopeContext)
  }
  private def _configuration_failure(value: Consequence[RuntimeHttpExecutionPlan]): Unit = value match {
    case Consequence.Failure(conclusion) =>
      ConclusionDiagnostics.classify(conclusion) shouldBe ConclusionDiagnostics.classify(
        Consequence.configurationInvalid("Invalid runtime HTTP declaration").conclusion)
      conclusion.toString should not include _secret
    case _ => fail("invalid host wire installed HTTP authority")
  }

  "Runtime HTTP host wire" should {
    "round-trip typed authority with deterministic canonical encoding and the same request behavior" in {
      Given("a complete typed host declaration and three authorized HTTP methods")
      val first = _take(_decode(_root))
      val request = Vector(Property("http.timeout-seconds", "1", None), Property("application.note", "retained", None))
      When("the plan is encoded, restored and interpreted for the same owner and destinations")
      val encoded = _take(RuntimeHttpExecutionPlanCodec.encode(first))
      val restored = _take(RuntimeHttpExecutionPlanCodec.decode(encoded))
      val outcomes = Vector(first, restored).map { plan => HttpExecutionMethod.values.toVector.map { method =>
        _take(HttpExecutionAdmission.resolveC(_context(plan), method, "item%2Fpart?q=%2B", request))
      } }
      val reordered = _take(RuntimeHttpExecutionPlan.createC(first.baseUrl, first.limits,
        first.grants.map { case (owner, grant) => owner -> grant.copy(origins = grant.origins.reverse) }))
      Then("the same absolute destination, finite controls and method authority survive the wire")
      outcomes(1) shouldBe outcomes(0)
      outcomes.flatten.map(_.absoluteUrl).distinct shouldBe Vector("https://EXAMPLE.TEST/api/item%2Fpart?q=%2B")
      _take(RuntimeHttpExecutionPlanCodec.encode(restored)) shouldBe encoded
      _take(RuntimeHttpExecutionPlanCodec.encode(reordered)) shouldBe encoded
      restored.grants(_owner).origins shouldBe Vector("https://example.test")
    }

    "reject duplicate JSON keys at every object depth before installing a plan" in {
      Given("otherwise valid wires with duplicate root, limits and grant object keys")
      val text = _root.noSpaces
      val wires = Vector(text.replace("\"schema\":", "\"schema\":\"ignored\",\"schema\":"),
        text.replace("\"responseBytes\":4096", "\"responseBytes\":4096,\"responseBytes\":1"),
        text.replace("\"componentId\":", "\"componentId\":\"org.example.Other\",\"componentId\":"))
      When("the strict StringValue codec parses each wire")
      val outcomes = wires.map(text => RuntimeHttpExecutionPlanCodec.decode(ConfigurationValue.StringValue(text)))
      Then("duplicate object members are safe configuration failures")
      outcomes.foreach(_configuration_failure)
    }

    "reject duplicate owners, canonical origins and methods instead of silently collapsing them" in {
      Given("owner repetitions and equivalent origin spellings as well as repeated method labels")
      val wires = Vector(_root.mapObject(_.add("grants", Json.arr(_grant, _grant))),
        _changed_grant(_.mapObject(_.add("origins", Json.arr(Json.fromString("https://example.test"), Json.fromString("HTTPS://EXAMPLE.TEST:443/"))))),
        _changed_grant(_.mapObject(_.add("methods", Json.arr(Json.fromString("GET"), Json.fromString("GET"))))))
      When("the host codec compiles exact grants")
      val outcomes = wires.map(_decode)
      Then("each ambiguity fails as configuration rather than becoming authority")
      outcomes.foreach(_configuration_failure)
    }

    "reject unknown fields, schemas, aliases and methods at their declared wire boundaries" in {
      Given("unknown root, limits and grant fields plus schema and method alternatives")
      val wires = Vector(_root.mapObject(_.add("unexpected", Json.fromString(_secret))),
        _root.mapObject(_.add("schema", Json.fromString("cncf.runtime-http-execution.v2"))),
        _root.mapObject(_.add("baseurl", Json.fromString("https://example.test"))),
        _root.mapObject(_.add("limits", _root.hcursor.downField("limits").focus.get.mapObject(_.add("timeout", Json.fromInt(1))))),
        _changed_grant(_.mapObject(_.add("capability", Json.fromString(_secret)))),
        _changed_grant(_.mapObject(_.add("methods", Json.arr(Json.fromString("get"))))),
        _changed_grant(_.mapObject(_.add("methods", Json.arr(Json.fromString("DELETE"))))))
      When("each unknown declaration reaches strict decoding")
      val outcomes = wires.map(_decode)
      Then("no permissive fallback or alternate method grants access")
      outcomes.foreach(_configuration_failure)
    }

    "reject invalid identities, destinations, empty grant members and invalid integer bounds with safe diagnostics" in {
      Given("confidential malformed identities and URLs with empty grants or out-of-range limits")
      val wires = Vector(
        _changed_grant(_.mapObject(_.add("componentId", Json.fromString(s"invalid/$_secret")))),
        _root.mapObject(_.add("baseUrl", Json.fromString(s"https://user:$_secret@example.test/"))),
        _changed_grant(_.mapObject(_.add("origins", Json.arr(Json.fromString(s"https://example.test/$_secret"))))),
        _changed_grant(_.mapObject(_.add("origins", Json.arr()))),
        _changed_grant(_.mapObject(_.add("methods", Json.arr())))) ++
        Vector(Json.fromLong(2147483648L), Json.fromInt(0), Json.fromDoubleOrNull(1.5), Json.fromString(_secret)).map { value =>
          _root.mapObject(_.add("limits", _root.hcursor.downField("limits").focus.get.mapObject(_.add("connectTimeoutMillis", value))))
        } ++ Vector(Json.fromLong(-1), Json.fromBigInt(BigInt(Long.MaxValue) + 1)).map { value =>
          _root.mapObject(_.add("limits", _root.hcursor.downField("limits").focus.get.mapObject(_.add("responseBytes", value))))
        }
      When("the host codec compiles the complete declaration")
      val outcomes = wires.map(_decode)
      Then("all refusals are configuration failures and confidential source data is redacted")
      outcomes.foreach(_configuration_failure)
    }

    "accept an empty grant set without granting HTTP and refuse non-string input" in {
      Given("a valid host base and finite bounds but no grants, plus a typed non-string input")
      val emptywire = _root.mapObject(_.add("grants", Json.arr()))
      When("the codec decodes both configuration values")
      val plan = _take(_decode(emptywire))
      val denied = HttpExecutionAdmission.resolveC(_context(plan), HttpExecutionMethod.Get, "/selected", Vector.empty)
      val nonstring = RuntimeHttpExecutionPlanCodec.decode(ConfigurationValue.BooleanValue(true))
      Then("an empty set grants nothing and non-string input is a safe configuration failure")
      plan.grants shouldBe Map.empty[ComponentId, HttpExecutionGrant]
      denied.isFaillure shouldBe true
      _configuration_failure(nonstring)
    }
  }
}
