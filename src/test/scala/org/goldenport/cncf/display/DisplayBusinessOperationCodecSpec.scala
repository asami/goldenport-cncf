package org.goldenport.cncf.display

import io.circe.Json
import org.goldenport.Consequence
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
final class DisplayBusinessOperationCodecSpec extends AnyWordSpec with DisplayBusinessOperationFixture.Vocabulary {
  import DisplayBusinessOperation.*
  import DisplayBusinessOperationFixture.*

  private def _at(json: Json, path: List[String], change: Json => Json): Json = path match {
    case Nil => change(json)
    case "0" :: rest => Json.fromValues(json.asArray.get.updated(0, _at(json.asArray.get.head, rest, change)))
    case member :: rest => json.mapObject(obj => obj.add(member, _at(obj(member).get, rest, change)))
  }
  private def _rejected(result: Consequence[?]): Unit = result match {
    case failure: Consequence.Failure[?] =>
      failure.conclusion.observation.taxonomy shouldBe org.goldenport.observation.Taxonomy.argumentInvalid
      failure.conclusion.observation.cause.getException.get shouldBe a[DescriptorException]
      failure.display should not include "private-forgery"
    case _ => fail("expected strict descriptor rejection")
  }

  "Standalone Business descriptor protocol" should {
    "canonical inert metadata" which {
      "BA4 roundtrip scoped Unicode labels and the exact ordered parameter kind/type/multiplicity vector" in {
        Given("a normally admitted target with defaults which are deliberately outside the descriptive minimum")
        withSubsystem { subsystem =>
          val target = new TargetOperation()
          val declarations = definitions(suffix = "日本 Ω e\u0301 \ud83d\ude00\ud800\udfff\u0000")
          val component = new SourceComponent(declarations)
          initialize(component, subsystem, protocol(Vector(target)))
          val admitted = registry(component, declarations).TAKE
          When("the standalone versioned descriptor is encoded and decoded against its immutable registry")
          val encoded = DisplayBusinessOperationCodec.encodeC(admitted, resourceType, "collecting-resources").TAKE
          val restored = DisplayBusinessOperationCodec.decodeC(admitted, encoded).TAKE
          val payload = DisplayBusinessOperationCodec.encodeJsonC(admitted, resourceType, "collecting-resources").TAKE
          Then("the original descriptor is restored exactly without serializing defaults, constraints, credentials or executable objects")
          restored shouldBe admitted.describeC(resourceType, "collecting-resources").TAKE
          restored.actions.head.parameters shouldBe expectedParameters
          restored.actions.map(_.scope) shouldBe Vector(ActionScope.Screen, ActionScope.Screen, ActionScope.Item, ActionScope.Item)
          payload.asObject.get.keys.toSet shouldBe Set("schema", "version", "componentId", "resourceType", "definition", "provenance", "actions")
          payload.hcursor.downField("actions").downN(0).downField("input").downN(0).focus.get shouldBe
            Json.obj("name" -> Json.fromString("resource"), "kind" -> Json.fromString("argument"),
              "datatype" -> Json.fromString("string"), "multiplicity" -> Json.fromString("1"))
          encoded should not include "private-default"
          encoded should not include "constraints"
          encoded should not include "createOperationRequest"
          encoded.forall(_ <= '~') shouldBe true
          idle(component, target)
        }
      }
    }
    "closed untrusted candidates" which {
      "BA4-closed reject duplicate unknown and missing members at every object depth without running a target" in {
        Given("a canonical standalone descriptor with root, definition, provenance, Action, Operation and input objects")
        withSubsystem { subsystem =>
          val target = new TargetOperation()
          val component = new SourceComponent()
          initialize(component, subsystem, protocol(Vector(target)))
          val admitted = registry(component).TAKE
          val canonical = DisplayBusinessOperationCodec.encodeJsonC(admitted, resourceType, "capture-detail").TAKE
          val paths = Vector(List.empty[String], List("definition"), List("provenance"), List("actions", "0"),
            List("actions", "0", "operation"), List("actions", "0", "input", "0"))
          paths.foreach { path =>
            val extra = _at(canonical, path, _.mapObject(_.add("private-forgery", Json.True)))
            val missing = _at(canonical, path, value => value.mapObject(obj => obj.remove(obj.keys.head)))
            val duplicate = _at(canonical, path, value => value).noSpaces
            val node = path.foldLeft(canonical) { (value, member) =>
              if (member == "0") value.asArray.get.head else value.asObject.get(member).get
            }.asObject.get
            val key = node.keys.head
            val keytext = Json.fromString(key).noSpaces + ":"
            val raw = duplicate.replace(keytext, keytext + node(key).get.noSpaces + "," + keytext)
            When("the raw candidate contains one unknown, missing or repeated object member")
            val results = Vector(extra.noSpaces, missing.noSpaces, raw).map(DisplayBusinessOperationCodec.decodeC(admitted, _))
            Then("all three candidates fail structurally with redacted diagnostics before target request/call or Browser execution")
            results.foreach(_rejected)
            idle(component, target)
          }
        }
      }
      "BA4-forgery reject wrong version shape and changed source, definition, provenance, target or input correspondence" in {
        Given("an admitted immutable registry and a successful canonical Detail descriptor")
        withSubsystem { subsystem =>
          val target = new TargetOperation()
          val component = new SourceComponent()
          initialize(component, subsystem, protocol(Vector(target)))
          val admitted = registry(component).TAKE
          val canonical = DisplayBusinessOperationCodec.encodeJsonC(admitted, resourceType, "capture-detail").TAKE
          val members = Vector(List("schema"), List("componentId"), List("resourceType"), List("definition", "screenId"),
            List("definition", "revision"), List("provenance", "producerId"), List("provenance", "sourceId"), List("provenance", "revision"),
            List("actions", "0", "scope"), List("actions", "0", "id"), List("actions", "0", "label"), List("actions", "0", "bindingId"),
            List("actions", "0", "operation", "componentId"), List("actions", "0", "operation", "service"),
            List("actions", "0", "operation", "operation"), List("actions", "0", "input", "0", "name"),
            List("actions", "0", "input", "0", "kind"), List("actions", "0", "input", "0", "datatype"),
            List("actions", "0", "input", "0", "multiplicity"))
          val forged = members.flatMap(path => Vector(Json.fromString("private-forgery"), Json.Null, Json.True,
            Json.fromInt(1), Json.arr()).map(value => _at(canonical, path, _ => value).noSpaces)) ++
            Vector("0", "2", "1.0", "1e0", "\"1\"").map(version => canonical.noSpaces.replace("\"version\":1", "\"version\":" + version)) ++
            Vector("null", "[]", "{", canonical.mapObject(_.add("actions", Json.arr())).noSpaces)
          forged.foreach { raw =>
            When("strict decoding receives a forged scalar, wrong primitive, missing action or incompatible protocol version")
            val result = DisplayBusinessOperationCodec.decodeC(admitted, raw)
            Then("canonical correspondence fails without echoing the raw candidate or selecting executable behavior")
            _rejected(result)
            idle(component, target)
          }
          When("the codec receives null registry or JSON instead of an admitted input")
          val nulls = Vector(DisplayBusinessOperationCodec.decodeC(null, canonical.noSpaces),
            DisplayBusinessOperationCodec.decodeC(admitted, null), DisplayBusinessOperationCodec.encodeC(null, resourceType, "capture-detail"))
          Then("nonnull admission is enforced with typed descriptor diagnostics")
          nulls.foreach(_rejected)
        }
      }
    }
  }
}
