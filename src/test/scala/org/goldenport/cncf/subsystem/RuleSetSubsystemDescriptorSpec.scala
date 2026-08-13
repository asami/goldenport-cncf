package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for subsystem-owned, restricted RuleSet metadata.
 *
 * @since   Jul. 16, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuleSetSubsystemDescriptorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "GenericSubsystemDescriptor RuleSets" should {
    "decode root-level rule-set declarations without changing component instance rules" in {
      Given("a subsystem descriptor containing component rules and declarative rule sets")
      val path = Files.createTempFile("subsystem-rule-sets", ".yaml")
      Files.writeString(
        path,
        """subsystem: pricing
          |components:
          |  - namespace: org.goldenport.cncf.test
          |    id: Catalog
          |    version: 1.0.0
          |    rules:
          |      cache:
          |        ttl: 60
          |rule-sets:
          |  - id: tax
          |    version: "2026-07"
          |    inputFacts: [subtotal, country]
          |    outputFacts: [tax]
          |    rules:
          |      - id: calculate-tax
          |        family: calculation
          |        priority: 10
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the subsystem descriptor is loaded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("only the root-level rule-set vocabulary is decoded as a RuleSet")
      result shouldBe a[Consequence.Success[_]]
      val descriptor = result.toOption.get
      descriptor.ruleSets.map(_.identity.print) shouldBe Vector("tax@2026-07")
      descriptor.ruleSets.head.orderedRules.map(_.id.value) shouldBe Vector("calculate-tax")
      descriptor.componentBindings.head.rules.getRecord("cache").flatMap(_.getInt("ttl")) shouldBe Some(60)
    }

    "reject duplicate RuleSet identities instead of selecting one implicitly" in {
      Given("a descriptor that declares the same RuleSet identity twice")
      val path = Files.createTempFile("subsystem-duplicate-rule-sets", ".yaml")
      Files.writeString(
        path,
        """subsystem: pricing
          |components:
          |  - namespace: org.goldenport.cncf.test
          |    id: Catalog
          |    version: 1.0.0
          |ruleSets:
          |  - id: tax
          |    version: "1"
          |    rules:
          |      - id: calculate-tax
          |        family: calculation
          |  - id: tax
          |    version: "1"
          |    rules:
          |      - id: calculate-tax-duplicate
          |        family: calculation
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("the duplicate identities are decoded")
      val result = GenericSubsystemDescriptor.load(path)

      Then("descriptor loading returns a structured failure")
      result shouldBe a[Consequence.Failure[_]]
    }
  }
}
