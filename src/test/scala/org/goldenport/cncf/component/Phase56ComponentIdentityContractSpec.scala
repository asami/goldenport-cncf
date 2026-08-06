package org.goldenport.cncf.component

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.{
  GenericSubsystemComponentBinding,
  GenericSubsystemDescriptor,
  SubsystemAssemblyAdmission
}
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  7, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56ComponentIdentityContractSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-56-component-identity-contract, example:E1, rules:CID01-R1,R2, phase:56, slice:CID-01D"
  )
  private val _e2 = afterWord(
    "in spec:phase-56-component-identity-contract, example:E2, rules:CID01-R1,R3, phase:56, slice:CID-01D"
  )
  private val _e3 = afterWord(
    "in spec:phase-56-component-identity-contract, example:E3, rules:CID01-R4,R5, phase:56, slice:CID-01D"
  )
  private val _e4 = afterWord(
    "in spec:phase-56-component-identity-contract, example:E4, rules:CID01-R6,R7, phase:56, slice:CID-01D"
  )

  "Phase 56 Component identity" should {
    "E1 reject bare ComponentId compatibility input" must _e1 {
      "when the canonical UserAccount compatibility parser receives a bare spelling" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R2; Example: E1 UserAccount")
        When("the target parser is applied to bare UserAccount")
        Then("bare UserAccount is rejected")
        pendingUntilFixed {
          an[IllegalArgumentException] should be thrownBy ComponentId("UserAccount")
        }
      }
    }

    "E2 reject divergent Component.Core.create name" must _e2 {
      "when Core.create receives an independently authored display name" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R3; Example: E2 org.simplemodeling.textus.UserAccount")
        When("Core.create is given a qualified ComponentId and a divergent name")
        Then("Core.create rejects the divergent name")
        pendingUntilFixed {
          val componentid = ComponentId("org.simplemodeling.textus.UserAccount")
          val instanceid = ComponentInstanceId.default(componentid)
          an[IllegalArgumentException] should be thrownBy {
            Component.Core.create(
              "Textus User Account",
              componentid,
              instanceid,
              Protocol.empty
            )
          }
        }
      }
    }

    "E3 retain the exact qualified ComponentId in the default instance" must _e3 {
      "when a default instance is derived from a qualified UserAccount" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R4,R5; Example: E3 org.simplemodeling.textus.UserAccount")
        When("the target qualified parser creates the default instance")
        Then("the instance retains the exact qualified identity without normalization")
        pendingUntilFixed {
          val qualifiedid = "org.simplemodeling.textus.UserAccount"
          val componentid = ComponentId(qualifiedid)
          val instanceid = ComponentInstanceId.default(componentid)
          componentid.name shouldBe qualifiedid
          instanceid.name shouldBe qualifiedid
          instanceid.canonicalKey should include (qualifiedid)
        }
      }
    }

    "E4 isolate generated namespaces and reject an ambiguous bare UserAccount" must _e4 {
      "when subsystem admission resolves same-local component bindings" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R6,R7; Example: E4 org.alpha.UserAccount, org.beta.UserAccount, and UserAccount")
        When("SubsystemAssemblyAdmission.resolveC evaluates generated namespaces and a bare alias")
        Then("qualified identities remain isolated and ambiguous bare resolution reports both candidates")
        pendingUntilFixed {
          val property = Prop.forAll(_distinct_namespaces) { namespaces =>
            val qualifiedids = namespaces.map(_ + ".UserAccount")
            SubsystemAssemblyAdmission.resolveC(_descriptor(qualifiedids), Vector.empty) match {
              case Consequence.Success(resolved) =>
                val resolvednames = resolved.componentBindings.map(_.componentName)
                val identities = resolvednames.map { qualifiedid =>
                  val componentid = ComponentId(qualifiedid)
                  val instanceid = ComponentInstanceId.default(componentid)
                  (componentid.name, instanceid.name, instanceid.canonicalKey)
                }
                resolvednames == qualifiedids &&
                  identities.map(_._1) == qualifiedids &&
                  identities.map(_._2) == qualifiedids &&
                  identities.map(_._3).distinct.size == identities.size
              case Consequence.Failure(_) =>
                false
            }
          }
          Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property).passed shouldBe true

          val candidates = Vector(
            "org.alpha.UserAccount",
            "org.beta.UserAccount",
            "UserAccount"
          )
          val result = SubsystemAssemblyAdmission.resolveC(_descriptor(candidates), Vector.empty)
          val diagnostic = result match {
            case Consequence.Failure(conclusion) => conclusion.show
            case Consequence.Success(_) => fail("ambiguous bare UserAccount was admitted")
          }
          diagnostic.toLowerCase should include ("ambiguous")
          diagnostic.toLowerCase should include ("bare")
          diagnostic should include ("org.alpha.UserAccount")
          diagnostic should include ("org.beta.UserAccount")
        }
      }
    }
  }

  private val _distinct_namespaces = for {
    first <- Gen.oneOf("org.alpha", "org.beta", "org.gamma", "org.delta")
    second <- Gen.oneOf("org.alpha", "org.beta", "org.gamma", "org.delta").suchThat(_ != first)
  } yield Vector(first, second)

  private def _descriptor(componentnames: Vector[String]): GenericSubsystemDescriptor =
    GenericSubsystemDescriptor(
      Path.of("phase-56-cid01-e4"),
      "identity-runtime",
      componentBindings = componentnames.map(x => GenericSubsystemComponentBinding(x))
    )
}
