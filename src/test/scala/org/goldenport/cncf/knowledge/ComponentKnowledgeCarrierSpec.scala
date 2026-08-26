package org.goldenport.cncf.knowledge

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}

/*
 * Executable specification for the deterministic Component knowledge carrier
 * declaration boundary.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentKnowledgeCarrierSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

  "P596-S01 Component knowledge carrier codec" should {
    "encode and decode one canonical declaration without consumer content" in {
      Given("one lowercase SHA-256 over externally supplied raw consumer-contract bytes")
      val carrier = ComponentKnowledgeCarrier.createC(_digest).toOption

      When("the declaration is deterministically encoded and strictly decoded")
      val encoded = carrier.map(ComponentKnowledgeCarrierCodec.encode)
      val decoded = encoded.flatMap(value => ComponentKnowledgeCarrierCodec.decodeC(value).toOption)

      Then("only the carrier schemas, canonical logical path, and digest round-trip")
      carrier should not be empty
      decoded shouldBe carrier
      encoded shouldBe Some(
        s"{\"carrierSchema\":\"cncf.component-knowledge-carrier.v1\",\"consumerContractSchema\":\"cncf.component-knowledge-consumer.v1\",\"logicalPath\":\"component-knowledge.json\",\"sha256\":\"$_digest\"}"
      )
    }

    "reject hostile declarations rather than normalize alternate carrier authority" in {
      Given("one canonical declaration and hostile schema, path, digest, field, and duplicate-key variants")
      val canonical = ComponentKnowledgeCarrierCodec.encode(ComponentKnowledgeCarrier.createC(_digest).toOption.get)
      val hostile = Vector(
        canonical.replace("cncf.component-knowledge-carrier.v1", "cncf.component-knowledge-carrier.V1"),
        canonical.replace("cncf.component-knowledge-consumer.v1", "cncf.component-knowledge-consumer.v2"),
        canonical.replace("component-knowledge.json", "./component-knowledge.json"),
        canonical.replace("component-knowledge.json", "/component-knowledge.json"),
        canonical.replace("component-knowledge.json", "component%2Dknowledge.json"),
        canonical.replace("component-knowledge.json", "../component-knowledge.json"),
        canonical.replace(_digest, _digest.toUpperCase(java.util.Locale.ROOT)),
        canonical.replace("\"sha256\":", "\"future\":true,\"sha256\":"),
        canonical.replace("\"carrierSchema\":", "\"carrierSchema\":\"cncf.component-knowledge-carrier.v1\",\"carrierSchema\":")
      )

      When("the strict codec evaluates every variant")
      val decoded = hostile.map(ComponentKnowledgeCarrierCodec.decodeC(_).toOption)

      Then("case aliases, alternate paths, malformed digests, unknown fields, and duplicate JSON keys are rejected")
      decoded shouldBe Vector.fill(hostile.size)(None)
    }

    "preserve the exact canonical declaration for generated valid digests" in {
      Given("arbitrary lowercase hexadecimal SHA-256 digest declarations")
      val hex = Gen.oneOf("0123456789abcdef".toVector)
      val digests = Gen.listOfN(64, hex).map(_.mkString)
      val property = Prop.forAll(digests) { digest =>
        ComponentKnowledgeCarrier.createC(digest).toOption.exists { carrier =>
          val encoded = ComponentKnowledgeCarrierCodec.encode(carrier)
          ComponentKnowledgeCarrierCodec.decodeC(encoded).toOption.contains(carrier) &&
            ComponentKnowledgeCarrierCodec.encode(carrier) == encoded
        }
      }

      When("the codec evaluates fifty independently generated valid declarations")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("each declaration retains only the fixed schemas and fixed logical path")
      checked.passed shouldBe true
    }
  }
}
