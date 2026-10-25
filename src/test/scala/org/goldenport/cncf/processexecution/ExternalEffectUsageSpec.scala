package org.goldenport.cncf.processexecution

import org.goldenport.cncf.effect.{ExternalEffectClassification, ExternalEffectKind, ExternalEffectRoute, ExternalEffectUsage}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.workspace.WorkspacePermission
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 24, 2026
 */
final class ExternalEffectUsageSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "External effect usage classification" should {
    "E1 classify every typed Git observation by its CNCF permission" should
      afterWord("in spec:managed-effect-classification, example:E1, rules:R1,R2, phase:6") {
      "preserve Managed read and write kinds without creating a request" in {
        Given("docs/spec/managed-effect-classification.md R1,R2 E1 and all typed Git operation kinds")
        val kinds = GitOperationKind.values.toVector
        When("the shared policy classifies each explicitly supplied typed Git observation")
        val results = kinds.map(kind => ProcessExternalEffectPolicy.classifyUsageC(ExternalEffectUsage.TypedGit(kind)))
        Then("each succeeds with the Managed kind selected by the existing permission contract")
        results.zip(kinds).foreach { case (result, kind) =>
          val expected = if (kind.permission == WorkspacePermission.ReadOnly)
            ExternalEffectKind.GitRead else ExternalEffectKind.GitWrite
          result.toOption shouldBe Some(ExternalEffectClassification(Set(expected), ExternalEffectRoute.Managed))
        }
        results.flatMap(_.toOption).flatMap(_.effectKinds).toSet shouldBe
          Set(ExternalEffectKind.GitRead, ExternalEffectKind.GitWrite)
      }
    }

    "E2 classify typed builds and resource reads" should
      afterWord("in spec:managed-effect-classification, example:E2, rules:R1,R2, phase:6") {
      "return only their Managed kinds" in {
        Given("docs/spec/managed-effect-classification.md R1,R2 E2 with every SBT operation including FixCheck and a resource read")
        val usages = SbtOperation.values.toVector.map(ExternalEffectUsage.TypedSbt.apply) :+ ExternalEffectUsage.ResourceRead
        When("the shared policy classifies those supplied observations")
        val results = usages.map(ProcessExternalEffectPolicy.classifyUsageC)
        Then("build operations are Build and the resource observation is ResourceRead, all Managed")
        results.map(_.toOption) shouldBe (Vector.fill(SbtOperation.values.length)(
          Some(ExternalEffectClassification(Set(ExternalEffectKind.Build), ExternalEffectRoute.Managed))) :+
          Some(ExternalEffectClassification(Set(ExternalEffectKind.ResourceRead), ExternalEffectRoute.Managed)))
        usages should contain (ExternalEffectUsage.TypedSbt(SbtOperation.FixCheck))
      }
    }

    "E3 delegate process observations to the existing command classifier" should
      afterWord("in spec:managed-effect-classification, example:E3, rules:R2, phase:6") {
      "preserve recognized and unknown results for generated path spellings" in {
        Given("docs/spec/managed-effect-classification.md R2 E3 with generated command paths and known or unknown names")
        val commands = for {
          name <- Gen.oneOf("cat", "cp", "curl", "psql", "bash", "git", "sbt", "selected-tool")
          depth <- Gen.choose(0, 5)
          separator <- Gen.oneOf("/", "\\")
        } yield Vector.fill(depth)("private-segment").mkString("", separator, separator) + name
        When("usage classification and existing command-name classification receive the same spelling")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(80), Prop.forAll(commands) { command =>
          ProcessExternalEffectPolicy.classifyUsageC(ExternalEffectUsage.ProcessCommand(command)).toOption
            .contains(ProcessExternalEffectPolicy.classifyCommandName(command))
        })
        Then("the public usage API preserves the existing classifier's entire bounded outcome")
        checked.passed shouldBe true
      }
    }

    "E4 refuse malformed observations" should
      afterWord("in spec:managed-effect-classification, example:E4, rules:R1,R3, phase:6") {
      "return structured non-reflecting input failures" in {
        Given("docs/spec/managed-effect-classification.md R1,R3 E4 with null operands and invalid command payloads")
        val secret = "private-command-sentinel"
        val usages = Vector(null, ExternalEffectUsage.TypedGit(null), ExternalEffectUsage.TypedSbt(null),
          ExternalEffectUsage.ProcessCommand(null), ExternalEffectUsage.ProcessCommand(""),
          ExternalEffectUsage.ProcessCommand(secret + '\u0000'))
        When("the usage API receives the malformed observations")
        val results = usages.map(ProcessExternalEffectPolicy.classifyUsageC)
        Then("every result is a structured input error and none reflects a command payload")
        results.foreach { result =>
          result.isFaillure shouldBe true
          result.toString should not include secret
          result match {
            case org.goldenport.Consequence.Failure(conclusion) =>
              ConclusionDiagnostics.classify(conclusion).webStatus shouldBe 400
            case _ => fail("Malformed usage succeeded")
          }
        }
      }
    }
  }
}
