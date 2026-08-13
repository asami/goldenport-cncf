package org.goldenport.cncf.operationtool

import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for runtime-owned Operation tool admission policy.
 *
 * @since   Jul. 21, 2026
 * @version Aug. 13, 2026
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
