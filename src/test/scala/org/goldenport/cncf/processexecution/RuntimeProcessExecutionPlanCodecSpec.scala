package org.goldenport.cncf.processexecution

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.configuration.ConfigurationValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since Oct.  7, 2026
 * @version Oct.  7, 2026
 */
final class RuntimeProcessExecutionPlanCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Process configuration rejected: $failure")
  }
  private val _capability = _take(ProcessCapabilityId.parseC("selected-token"))
  private val _owner = ComponentId("org.example.Workflow")
  private val _limits = ProcessExecutionLimits(Some(100), Some(100), Some(100), Some(64), Some(64),
    Some(64), Some(4), Some(512), Some(1), Some(64), Some(128))
  private val _slots = Vector(ProcessArgumentSlot.Literal("show"), ProcessArgumentSlot.LowerHex(Set(40, 64)))
  private val _definition = _take(ProcessProgramDefinition.fromRuntimeC(_capability, "selected-token", "/runtime/bin/git",
    Vector("-C", "/runtime/selected"), ProcessArgumentPolicy(Vector("-C", "/runtime/selected"),
      admission = ProcessArgumentAdmission.Template, permittedArgumentTemplates = Set(ProcessArgumentTemplate(_slots))),
    _limits, Set.empty, environment = Map("PATH" -> "/runtime/bin")))
  private val _plan = _take(RuntimeProcessExecutionPlan.createC(Vector(_definition),
    Map(_owner -> Vector(ProcessExecutionGrant(_capability)))))
  private val _encoded = _take(RuntimeProcessExecutionPlanCodec.encode(_plan)).asInstanceOf[ConfigurationValue.StringValue]
  private val _root = parse(_encoded.value).toOption.get
  private def _decode(value: Json) = RuntimeProcessExecutionPlanCodec.decode(ConfigurationValue.StringValue(value.noSpaces))
  private def _changed_program(f: Json => Json): Json = _root.mapObject(_.add("programs",
    Json.arr(f(_root.hcursor.downField("programs").downArray.focus.get))))

  "Runtime process host wire contract" should {
    "round-trip fixed programs, exact templates, finite limits and independently selected component grants" in {
      Given("a trusted plan with complete native-token argument shapes")
      When("the host codec decodes its wire representation")
      val restored = _take(RuntimeProcessExecutionPlanCodec.decode(_encoded))
      Then("no source executable, prefix, environment, bound or owner is lost")
      restored.programs.head._executable_location shouldBe "/runtime/bin/git"
      restored.programs.head.fixedArguments shouldBe Vector("-C", "/runtime/selected")
      restored.programs.head._environment.values shouldBe Map("PATH" -> "/runtime/bin")
      restored.programs.head.argumentPolicy.permittedArgumentTemplates shouldBe Set(ProcessArgumentTemplate(_slots))
      restored.programs.head.maximumLimits shouldBe _limits
      restored.grants.keySet shouldBe Set(_owner)
      restored.admission.admitC(ProcessExecutionRequest(_capability, Vector("show", "a" * 40))).isSuccess shouldBe false
    }

    "refuse unknown grants and duplicate owners before any runtime installation" in {
      Given("an encoded host plan and two invalid grant declarations")
      val grant = _root.hcursor.downField("grants").downArray.focus.get
      val unknown = _root.mapObject(_.add("grants", Json.arr(grant.mapObject(_.add("capabilities",
        Json.arr(Json.fromString("undeclared")))))))
      val duplicate = _root.mapObject(_.add("grants", Json.arr(grant, grant)))
      When("the host decodes both declarations")
      val results = Vector(_decode(unknown), _decode(duplicate))
      Then("neither plan creates an admission with undeclared or ambiguous authority")
      results.forall(_.isFaillure) shouldBe true
    }

    "refuse relative executables, free-text policy and absent or widened structural inputs" in {
      Given("a valid plan and malformed program objects")
      val invalid = Vector(_changed_program(_.mapObject(_.add("executable", Json.fromString("git")))),
        _changed_program(_.mapObject(_.add("argumentAdmission", Json.fromString("bounded-text")))),
        _changed_program(_.mapObject(_.add("argumentTemplates", Json.arr()))),
        _changed_program(_.mapObject(_.add("limits", Json.obj()))),
        _changed_program(_.mapObject(_.add("limits", _root.hcursor.downField("programs").downArray
          .downField("limits").focus.get.mapObject(_.add("executionTimeoutMillis", Json.fromLong(0)))))))
      When("each program is decoded at the natural host configuration boundary")
      val results = invalid.map(_decode)
      Then("incomplete templates or limits cannot grant arbitrary command selection")
      results.forall(_.isFaillure) shouldBe true
      results.mkString should not include "/runtime/selected"
    }
  }
}
