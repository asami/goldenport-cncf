package org.goldenport.cncf.operationtool

import org.goldenport.record.Record
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for runtime-owned Operation tool admission policy.
 *
 * @since   Jul. 21, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationToolRuntimeConfigurationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Operation tool runtime configuration" should {
    "decode exact admissions with bounded default and explicit limits" in {
      Given("two logical tool sets with exact Operation identities")
      val policy = Record.data(
        "toolSets" -> Vector(
          Record.data(
            "id" -> "research",
            "operations" -> Vector("org.goldenport.cncf.Tool.web.fetch"),
            "limits" -> Record.data(
              "maximumCalls" -> 3,
              "maximumInputBytes" -> 2048,
              "maximumResultBytes" -> 4096,
              "maximumConcurrency" -> 1
            )
          ),
          Record.data(
            "id" -> "builtin",
            "operations" -> Vector("org.goldenport.cncf.Admin.system.ping")
          )
        )
      )

      When("the runtime policy is decoded")
      val result = OperationToolRuntimeConfiguration.decodeC(policy)

      Then("tool sets are normalized deterministically and defaults remain bounded")
      result.isSuccess shouldBe true
      val admissions = result.toOption.toVector.flatMap(_.admissions)
      admissions.map(_.toolSetId.print) shouldBe Vector("builtin", "research")
      admissions.head.limits shouldBe OperationToolLimits.createC(8, 16384L, 16384L, 1).toOption.get
      admissions.last.limits shouldBe OperationToolLimits.createC(3, 2048L, 4096L, 1).toOption.get
    }

    "adapt only fixed operator-policy builtin selectors into canonical operation identities" in {
      Given("fixed tool and admin policy selectors with one fully qualified canonical selector")
      val policy = Record.data(
        "toolSets" -> Vector(
          Record.data(
            "id" -> "builtin",
            "operations" -> Vector(
              "tool.time.now",
              "tool.web.fetch",
              "tool.web.head",
              "admin.system.ping"
            )
          ),
          Record.data(
            "id" -> "canonical",
            "operations" -> Vector("org.goldenport.cncf.Admin.system.ping")
          )
        )
      )

      When("the operator policy is decoded at its typed input boundary")
      val result = OperationToolRuntimeConfiguration.decodeC(policy)

      Then("every admitted selector is stored as its exact canonical OperationToolIdentity")
      result.isSuccess shouldBe true
      val admissions = result.toOption.toVector.flatMap(_.admissions)
      val builtin = admissions.find(_.toolSetId.print == "builtin").getOrElse(fail("builtin admission is missing"))
      val canonical = admissions.find(_.toolSetId.print == "canonical").getOrElse(fail("canonical admission is missing"))
      builtin.identities shouldBe Vector(
        OperationToolIdentity.createC(BuiltinComponentIdentity.ADMIN.name, "system", "ping").toOption.get,
        OperationToolIdentity.createC(BuiltinComponentIdentity.TOOL.name, "time", "now").toOption.get,
        OperationToolIdentity.createC(BuiltinComponentIdentity.TOOL.name, "web", "fetch").toOption.get,
        OperationToolIdentity.createC(BuiltinComponentIdentity.TOOL.name, "web", "head").toOption.get
      )
      canonical.identities shouldBe Vector(
        OperationToolIdentity.createC(BuiltinComponentIdentity.ADMIN.name, "system", "ping").toOption.get
      )
    }

    "reject malformed and arbitrary local operator-policy prefixes" in {
      Given("incomplete fixed prefixes and an unrelated unqualified local prefix")
      val incomplete = Record.data(
        "toolSets" -> Vector(Record.data(
          "id" -> "incomplete",
          "operations" -> Vector("tool.time")
        ))
      )
      val unrelated = Record.data(
        "toolSets" -> Vector(Record.data(
          "id" -> "unrelated",
          "operations" -> Vector("sanpomap.web.fetch")
        ))
      )

      When("the unsupported selectors are decoded")
      val incompleteresult = OperationToolRuntimeConfiguration.decodeC(incomplete)
      val unrelatedresult = OperationToolRuntimeConfiguration.decodeC(unrelated)

      Then("both remain structured strict-parsing failures")
      incompleteresult.isFaillure shouldBe true
      unrelatedresult.isFaillure shouldBe true
    }

    "reject duplicate tool sets and malformed integral limits" in {
      Given("a duplicate logical identity and a fractional execution limit")
      val duplicate = Record.data(
        "toolSets" -> Vector(
          Record.data("id" -> "builtin", "operations" -> Vector("org.goldenport.cncf.Admin.system.ping")),
          Record.data("id" -> "builtin", "operations" -> Vector("org.goldenport.cncf.Tool.time.now"))
        )
      )
      val fractional = Record.data(
        "toolSets" -> Vector(Record.data(
          "id" -> "builtin",
          "operations" -> Vector("org.goldenport.cncf.Admin.system.ping"),
          "limits" -> Record.data("maximumCalls" -> 1.5)
        ))
      )

      When("the invalid policies are decoded")
      val duplicateresult = OperationToolRuntimeConfiguration.decodeC(duplicate)
      val fractionalresult = OperationToolRuntimeConfiguration.decodeC(fractional)

      Then("both fail before a runtime registry can be created")
      duplicateresult.isFaillure shouldBe true
      fractionalresult.isFaillure shouldBe true
    }
  }
}
