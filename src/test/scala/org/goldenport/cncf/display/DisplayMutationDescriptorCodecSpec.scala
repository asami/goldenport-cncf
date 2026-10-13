package org.goldenport.cncf.display

import io.circe.Json
import logicalui.{DisplayMutationCodecFixture, DisplayMutationContractFixture}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class DisplayMutationDescriptorCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayMutation.*
  import DisplayMutationCodec.*
  import DisplayMutationCodecFixture.*
  import DisplayMutationContractFixture.{success, title, stage, description, Trace}
  import GeneratedLogicalUiAbi.ValueKind

  "E10 Descriptor wire" should afterWord("in spec:display-mutation, example:E10, rules:R1,R2,R5, phase:96") {
    "ordered inert metadata D1" which {
      "round trip declared field kind value and web hint order with every availability state and support distinction" in {
        Given("docs/spec/display-mutation.md R1,R2,R5 E10 R5 D1: traced supplied Binding with every web hint and deliberately permuted accepted kinds")
        val trace = new Trace
        val kinds = Vector(ValueKind.Missing, ValueKind.Timestamp, ValueKind.Boolean, ValueKind.Number, ValueKind.Text)
        val binding = metadataBinding(trace, kinds)
        val availability = Availability(AvailabilityState.Hidden, AvailabilityState.Disabled, AvailabilityState.Enabled,
          AvailabilityState.Disabled, AvailabilityState.Hidden, Set(title))
        val descriptor = success(binding.descriptorC(availability))
        When("the pure descriptor boundary verifies encodes and reconstructs against that Binding")
        val encoded = success(encodeDescriptorC(binding, descriptor))
        val actual = success(decodeDescriptorC(binding, encoded))
        Then("all observable ordered metadata survive without replacing the supplied WebColumn")
        actual.selection.definition shouldBe descriptor.selection.definition
        actual.selection.source.componentId.name shouldBe descriptor.selection.source.componentId.name
        actual.capabilities shouldBe binding.capabilities
        actual.availability shouldBe availability
        actual.fields shouldBe descriptor.fields
        actual.fields.map(_.location) shouldBe Vector(title, stage, description)
        actual.fields.head.acceptedKinds shouldBe kinds
        actual.fields.head.web.values shouldBe Vector("z", "a", "a", "Ω")
        actual.fields.head.web.validation.min.get.bigDecimal.scale shouldBe 2
        actual.fields.head.web shouldBe descriptor.fields.head.web
        (actual.fields.head.web eq descriptor.fields.head.web) shouldBe true
        actual.fields.head.web.confidentiality shouldBe descriptor.fields.head.web.confidentiality
        actual.fields.head.bindingId shouldBe Some("write.title")
        actual.fields(1).bindingId shouldBe None
        val root = wire(encoded)
        root.hcursor.downField("capabilities").get[String]("create").toOption shouldBe Some("unsupported")
        root.hcursor.downField("capabilities").get[String]("update").toOption shouldBe Some("supported")
        val web = at(root, Vector("fields", "0", "web")).asObject.get
        Vector("confidentiality", "convert", "target", "runtimeContext", "authorize", "schemaValidator").foreach(name => web.contains(name) shouldBe false)
        trace.calls shouldBe Vector.empty
      }
    }
    "canonical Binding comparison D2" which {
      "reject foreign descriptors registrations capabilities ordered fields kinds and forged inert web hints" in {
        Given("docs/spec/display-mutation.md R1,R2,R5 E10 R5 D2: an admitted descriptor and complete supplied Binding")
        val trace = new Trace
        val binding = metadataBinding(trace)
        val descriptor = success(binding.descriptorC(Availability.enabled))
        val root = wire(success(encodeDescriptorC(binding, descriptor)))
        val foreignpaths = Vector(Vector("componentId"), Vector("resourceType"), Vector("definition", "screenId"),
          Vector("definition", "revision"), Vector("definition", "mutationRevision"), Vector("provenance", "producerId"),
          Vector("provenance", "sourceId"), Vector("provenance", "revision"))
        val fieldarray = at(root, Vector("fields")).asArray.get
        val candidates = foreignpaths.map(path => set(root, path, Json.fromString("attacker-secret")).noSpaces) ++ Vector(
          set(root, Vector("capabilities", "create"), Json.fromString("supported")).noSpaces,
          set(root, Vector("capabilities", "read"), Json.fromString("foreign")).noSpaces,
          set(root, Vector("availability", "read"), Json.fromString("foreign")).noSpaces,
          set(root, Vector("fields"), Json.fromValues(fieldarray.reverse)).noSpaces,
          set(root, Vector("fields"), Json.fromValues(fieldarray :+ fieldarray.head)).noSpaces,
          set(root, Vector("fields", "0", "fieldId"), Json.fromString("attacker-secret")).noSpaces,
          set(root, Vector("fields", "0", "bindingId"), Json.fromString("attacker-secret")).noSpaces,
          remove(root, Vector("fields", "0"), "bindingId").noSpaces,
          set(root, Vector("fields", "0", "editability"), Json.fromString("foreign")).noSpaces,
          set(root, Vector("fields", "1", "editability"), Json.fromString("editable")).noSpaces,
          set(root, Vector("fields", "1", "bindingId"), Json.fromString("forged")).noSpaces,
          set(root, Vector("fields", "0", "acceptedKinds"), Json.fromValues(ValueKind.values.reverse.map(kind => Json.fromString(kind.tag)))).noSpaces,
          set(root, Vector("fields", "0", "acceptedKinds"), Json.arr(Json.fromString("text"), Json.fromString("text"))).noSpaces,
          set(root, Vector("fields", "0", "acceptedKinds"), Json.arr(Json.fromString("foreign"))).noSpaces,
          set(root, Vector("fields", "0", "web", "placeholder"), Json.fromString("forged")).noSpaces,
          set(root, Vector("fields", "0", "web", "hidden"), Json.fromBoolean(false)).noSpaces,
          set(root, Vector("fields", "0", "web", "values"), Json.arr(Json.fromString("a"), Json.fromString("z"))).noSpaces,
          set(root, Vector("fields", "0", "web", "validation", "min"), Json.fromString("1.0")).noSpaces)
        candidates.foreach { candidate =>
          Given("docs/spec/display-mutation.md R1,R2,R5 E10 D2: one changed observable descriptor property")
          When("decoder regenerates the descriptor through Binding.descriptorC and compares the supplied metadata")
          val rejected = decodeDescriptorC(binding, candidate)
          Then("forged registration shape or hints cannot replace the server Binding or invoke its callbacks")
          rejected.isSuccess shouldBe false
          problem(rejected).path should not include "attacker-secret"
          trace.calls shouldBe Vector.empty
        }
        Given("docs/spec/display-mutation.md R1,R2,R5 E10 D2: a descriptor from another binding with the same selection but different web metadata")
        val registrations = DisplayMutationContractFixture.fields().map(field =>
          if (field.location == title) field.copy(acceptedKinds = ValueKind.values.toVector) else field)
        val other = success(DisplayMutationBinding.admitC(binding.selection.source, binding.definitions,
          binding.selection.definition.screenId, binding.selection.definition.mutationRevision, binding.capabilities,
          registrations, Set.empty, (input: DisplayMutationContractFixture.Patch) => org.goldenport.Consequence.success(input)))
        val foreign = success(other.descriptorC(Availability.enabled))
        When("encoder receives a descriptor whose logical selection or static fields do not match the supplied Binding")
        val rejected = encodeDescriptorC(binding, foreign)
        Then("encoder also requires Binding-authoritative correspondence")
        rejected.isSuccess shouldBe false
      }
      "reject foreign decimal hint scale while preserving each Binding's exact min max and step round trip" in {
        Given("docs/spec/display-mutation.md R1,R2,R5 E10 D2: an admitted traced Binding and its complete canonical decimal hints")
        val trace = new Trace
        val binding = metadataBinding(trace)
        val descriptor = success(binding.descriptorC(Availability.enabled))
        val web = descriptor.fields.head.web
        val validation = web.validation
        val registrations = DisplayMutationContractFixture.fields(Some(trace)).map(field =>
          if (field.location == title) field.copy(acceptedKinds = ValueKind.values.toVector, web = web) else field)
        val variants = Vector(
          ("min", "1.00", "1.0", validation.copy(min = Some(BigDecimal("1.0")))),
          ("max", "9.00", "9.0", validation.copy(max = Some(BigDecimal("9.0")))),
          ("step", "0.10", "0.1", validation.copy(step = Some(BigDecimal("0.1")))))
        variants.foreach { case (hintname, canonicalstring, foreignstring, hints) =>
          Given("docs/spec/display-mutation.md R1,R2,R5 E10 D2: otherwise-identical Bindings differing only in " + hintname + " decimal scale")
          val fields = registrations.map(field =>
            if (field.location == title) field.copy(web = web.copy(validation = hints)) else field)
          val other = success(DisplayMutationBinding.admitC(binding.selection.source, binding.definitions,
            binding.selection.definition.screenId, binding.selection.definition.mutationRevision, binding.capabilities,
            fields, Set.empty, (input: DisplayMutationContractFixture.Patch) => DisplayMutationContractFixture.validateC(input, Some(trace))))
          val foreign = success(other.descriptorC(Availability.enabled))
          When("a foreign descriptor is encoded against the original Binding and both descriptors round trip through their own Binding")
          val rejected = encodeDescriptorC(binding, foreign)
          val encoded = success(encodeDescriptorC(binding, descriptor))
          val actual = success(decodeDescriptorC(binding, encoded))
          val reencoded = success(encodeDescriptorC(binding, actual))
          val foreignencoded = success(encodeDescriptorC(other, foreign))
          val foreignactual = success(decodeDescriptorC(other, foreignencoded))
          val foreignreencoded = success(encodeDescriptorC(other, foreignactual))
          Then("numeric field equality cannot normalize the observable decimal scale and own metadata remains exact without callbacks")
          foreign.fields shouldBe descriptor.fields
          at(wire(encoded), Vector("fields", "0", "web", "validation", hintname)).asString shouldBe Some(canonicalstring)
          at(wire(foreignencoded), Vector("fields", "0", "web", "validation", hintname)).asString shouldBe Some(foreignstring)
          rejected.isSuccess shouldBe false
          problem(rejected).code shouldBe "DSP96_MUTATION_DEFINITION_MISMATCH"
          problem(rejected).path shouldBe "descriptor"
          problem(rejected).reason shouldBe "invalid Display mutation input"
          reencoded shouldBe encoded
          foreignreencoded shouldBe foreignencoded
          actual.fields.head.web.validation.min.map(_.bigDecimal.toString) shouldBe validation.min.map(_.bigDecimal.toString)
          actual.fields.head.web.validation.max.map(_.bigDecimal.toString) shouldBe validation.max.map(_.bigDecimal.toString)
          actual.fields.head.web.validation.step.map(_.bigDecimal.toString) shouldBe validation.step.map(_.bigDecimal.toString)
          foreignactual.fields.head.web.validation.min.map(_.bigDecimal.toString) shouldBe hints.min.map(_.bigDecimal.toString)
          foreignactual.fields.head.web.validation.max.map(_.bigDecimal.toString) shouldBe hints.max.map(_.bigDecimal.toString)
          foreignactual.fields.head.web.validation.step.map(_.bigDecimal.toString) shouldBe hints.step.map(_.bigDecimal.toString)
          (actual.fields.head.web eq web) shouldBe true
          (foreignactual.fields.head.web eq foreign.fields.head.web) shouldBe true
          actual.fields.head.web.confidentiality shouldBe web.confidentiality
          foreignactual.fields.head.web.confidentiality shouldBe web.confidentiality
          trace.calls shouldBe Vector.empty
        }
      }
      "reject every applicable duplicate missing unknown null and wrong primitive plus noncanonical hint literals" in {
        Given("docs/spec/display-mutation.md R1,R2,R5 E10 R5 D2: all supplied web hints and strict descriptor member sets")
        val trace = new Trace
        val binding = metadataBinding(trace)
        val encoded = success(encodeDescriptorC(binding, success(binding.descriptorC(Availability.enabled))))
        val root = wire(encoded)
        val scalarpath = Vector("fields", "0", "web", "validation")
        val candidates = duplicates(root) ++ shapes(root) ++ Vector("1.0", "1e0", "\"1\"", "false")
          .map(value => encoded.replace("\"version\":1", "\"version\":" + value)) ++
          Vector("0.0", "0e0", "-0", "-1", "2147483648", "\"0\"", "true").map(value =>
            encoded.replace("\"minLength\":0", "\"minLength\":" + value)) ++
          Vector("01", "+1.00", "1e0", "NaN", "1.00E+0").map(value =>
            set(root, scalarpath :+ "min", Json.fromString(value)).noSpaces) ++
          Vector(set(root, scalarpath :+ "step", Json.fromInt(1)).noSpaces,
            set(root, Vector("fields", "0", "web", "required"), Json.fromString("false")).noSpaces,
            set(root, Vector("fields", "0", "web", "values"), Json.arr(Json.fromInt(1))).noSpaces,
            set(root, Vector("fields", "0", "web", "confidentiality"), Json.fromString("forged")).noSpaces)
        candidates.foreach { candidate =>
          Given("docs/spec/display-mutation.md R1,R2,R5 E10 D2: one canonical primitive or exact-member violation")
          When("strict descriptor decoding checks every declared hint primitive before comparison")
          val rejected = decodeDescriptorC(binding, candidate)
          Then("lenient numeric equality and missing optional metadata cannot normalize the wire candidate")
          rejected.isSuccess shouldBe false
          problem(rejected).reason shouldBe "invalid Display mutation input"
          problem(rejected).toString should not include "attacker-secret"
        }
        Given("docs/spec/display-mutation.md R1,R2,R5 E10 D2: null public descriptor API inputs")
        When("the guarded encode and decode boundaries receive nulls")
        val rejected = Vector(encodeDescriptorC(binding, null), encodeDescriptorC[DisplayMutationContractFixture.Patch](null,
          success(binding.descriptorC(Availability.enabled))), decodeDescriptorC[DisplayMutationContractFixture.Patch](null, encoded).map(_ => ""),
          decodeDescriptorC(binding, null).map(_ => ""))
        Then("every null rejects structurally without callback invocation")
        rejected.foreach(_.isSuccess shouldBe false)
        trace.calls shouldBe Vector.empty
      }
    }
    "bound readonly veto D3" which {
      "retain current bound readonly while canonicalizing redundant intrinsic unbound readonly membership" in {
        Given("docs/spec/display-mutation.md R1,R2,R5 E10 R5 D3: title explicitly bound and stage intrinsically unbound readonly")
        val trace = new Trace
        val binding = metadataBinding(trace)
        val availability = Availability.enabled.copy(readonlyFields = Set(title, stage))
        val descriptor = success(binding.descriptorC(availability))
        When("the descriptor is encoded decoded and encoded again")
        val encoded = success(encodeDescriptorC(binding, descriptor))
        val actual = success(decodeDescriptorC(binding, encoded))
        val reencoded = success(encodeDescriptorC(binding, actual))
        Then("observable editability survives and only bound read-only locators enter reconstructed readonlyFields")
        actual.fields shouldBe descriptor.fields
        actual.capabilities shouldBe descriptor.capabilities
        actual.availability.readonlyFields shouldBe Set(title)
        actual.fields.head.bindingId shouldBe Some("write.title")
        actual.fields.head.editability shouldBe FieldEditability.ReadOnly
        actual.fields(1).bindingId shouldBe None
        actual.fields(1).editability shouldBe FieldEditability.ReadOnly
        reencoded shouldBe encoded
        trace.calls shouldBe Vector.empty
      }
    }
  }
}
