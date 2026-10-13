package org.goldenport.cncf.knowledge

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the deterministic Component knowledge carrier
 * declaration boundary.
 *
 * @since   Aug. 26, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentKnowledgeCarrierSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "P596-S01 Component knowledge carrier codec" should {
    "encode and decode one canonical declaration without consumer content" in {
      Given("one declaration with the canonical carrier schema, consumer schema, and logical path")
      val carrier = ComponentKnowledgeCarrier.createC().toOption

      When("the declaration is deterministically encoded and strictly decoded")
      val encoded = carrier.map(ComponentKnowledgeCarrierCodec.encode)
      val decoded = encoded.flatMap(value => ComponentKnowledgeCarrierCodec.decodeC(value).toOption)

      Then("the exact carrier schemas and canonical logical path round-trip")
      carrier should not be empty
      decoded shouldBe carrier
      encoded shouldBe Some(
        "{\"carrierSchema\":\"cncf.component-knowledge-carrier.v1\",\"consumerContractSchema\":\"cncf.component-knowledge-consumer.v1\",\"logicalPath\":\"component-knowledge.json\"}"
      )
    }

    "reject hostile declarations rather than normalize alternate carrier authority" in {
      Given("one canonical declaration and hostile schema, path, field, and duplicate-key variants")
      val canonical = ComponentKnowledgeCarrierCodec.encode(ComponentKnowledgeCarrier.createC().toOption.get)
      val hostile = Vector(
        canonical.replace("cncf.component-knowledge-carrier.v1", "cncf.component-knowledge-carrier.V1"),
        canonical.replace("cncf.component-knowledge-consumer.v1", "cncf.component-knowledge-consumer.v2"),
        canonical.replace("component-knowledge.json", "./component-knowledge.json"),
        canonical.replace("component-knowledge.json", "/component-knowledge.json"),
        canonical.replace("component-knowledge.json", "component%2Dknowledge.json"),
        canonical.replace("component-knowledge.json", "../component-knowledge.json"),
        canonical.replace("\"logicalPath\":", "\"future\":true,\"logicalPath\":"),
        canonical.replace("\"carrierSchema\":", "\"carrierSchema\":\"cncf.component-knowledge-carrier.v1\",\"carrierSchema\":")
      )

      When("the strict codec evaluates every variant")
      val decoded = hostile.map(ComponentKnowledgeCarrierCodec.decodeC(_).toOption)

      Then("case aliases, alternate paths, unknown fields, and duplicate JSON keys are rejected")
      decoded shouldBe Vector.fill(hostile.size)(None)
    }
  }
}
