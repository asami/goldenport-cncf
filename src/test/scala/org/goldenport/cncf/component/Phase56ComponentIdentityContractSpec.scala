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
import scala.util.{Failure, Try}

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
    "E1 reject bare ComponentId compatibility input through the safe parser" must _e1 {
      "when the canonical UserAccount compatibility parser receives a bare spelling" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R2; Example: E1 UserAccount")
        val bareid = "UserAccount"
        When("the target parser is applied to bare UserAccount")
        val result = ComponentId.parseC(bareid)
        Then("bare UserAccount is rejected")
        result match {
          case Consequence.Failure(conclusion) =>
            conclusion.show should include ("component.identity.id.qualified")
            conclusion.show should include ("component ID must be namespace-qualified: UserAccount")
          case Consequence.Success(_) =>
            fail("bare UserAccount was admitted")
        }
      }
    }

    "E1 reject bare ComponentId compatibility input through the throwing constructor" must _e1 {
      "when the throwing constructor receives a bare spelling" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R2; Example: E1 UserAccount")
        val bareid = "UserAccount"
        When("the throwing ComponentId constructor is applied to bare UserAccount")
        val result = Try(ComponentId(bareid))
        Then("the throwing constructor rejects bare UserAccount with IllegalArgumentException")
        _assert_try_illegal_argument(result)
      }
    }

    "E2 reject divergent Component.Core.create name" must _e2 {
      "when Core.create receives an independently authored display name" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,R3; Example: E2 org.simplemodeling.textus.UserAccount")
        val componentid = ComponentId("org.simplemodeling.textus.UserAccount")
        val instanceid = ComponentInstanceId.default(componentid)
        val protocol = Protocol.empty
        When("Core.create is given a qualified ComponentId and a divergent name")
        val result = Try(
          Component.Core.create(
            "Textus User Account",
            componentid,
            instanceid,
            protocol
          )
        )
        Then("Core.create rejects the divergent name")
        pendingUntilFixed {
          _assert_try_illegal_argument(result)
        }
      }
    }

    "E3 preserve ComponentId value semantics" must _e3 {
      "when qualified ComponentId values are parsed independently" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R4,R5; Example: E3 org.simplemodeling.textus.UserAccount")
        val qualifiedid = "org.simplemodeling.textus.UserAccount"
        val distinctqualifiedid = "org.simplemodeling.textus.UserProfile"
        When("the target safe parser creates independent qualified ComponentId values")
        val componentresult = ComponentId.parseC(qualifiedid)
        val equivalentresult = ComponentId.parseC(qualifiedid)
        val distinctresult = ComponentId.parseC(distinctqualifiedid)
        Then("qualified ComponentId values retain exact fields and equality semantics")
        val componentid = componentresult match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        val equivalentcomponentid = equivalentresult match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        val distinctcomponentid = distinctresult match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        componentid.namespace.value() shouldBe "org.simplemodeling.textus"
        componentid.localId.value() shouldBe "UserAccount"
        componentid.name shouldBe qualifiedid
        componentid.toString shouldBe qualifiedid
        equivalentcomponentid shouldBe componentid
        equivalentcomponentid.hashCode shouldBe componentid.hashCode
        componentid should not equal distinctcomponentid
        Set(componentid, equivalentcomponentid, distinctcomponentid) should have size 2
      }
    }

    "E3 preserve ComponentInstanceId value semantics" must _e3 {
      "when default and named instances are created from a qualified ComponentId" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R4,R5; Example: E3 org.simplemodeling.textus.UserAccount")
        val qualifiedid = "org.simplemodeling.textus.UserAccount"
        When("the target constructors create default and blue ComponentInstanceId values")
        val componentid = ComponentId(qualifiedid)
        val instanceid = ComponentInstanceId.default(componentid)
        val equivalentresult = ComponentInstanceId.createC(componentid, "default")
        val blueresult = ComponentInstanceId.createC(componentid, "blue")
        Then("instance values retain exact component, label, canonical, and equality semantics")
        val equivalentinstanceid = equivalentresult match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        val blueinstanceid = blueresult match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        instanceid.componentId shouldBe componentid
        instanceid.name shouldBe qualifiedid
        instanceid.instance shouldBe "default"
        instanceid.canonicalKey shouldBe "org.simplemodeling.textus.UserAccount@default"
        instanceid.toString shouldBe "org.simplemodeling.textus.UserAccount@default"
        equivalentinstanceid.componentId shouldBe componentid
        equivalentinstanceid.name shouldBe qualifiedid
        equivalentinstanceid.instance shouldBe "default"
        equivalentinstanceid.canonicalKey shouldBe "org.simplemodeling.textus.UserAccount@default"
        equivalentinstanceid.toString shouldBe "org.simplemodeling.textus.UserAccount@default"
        blueinstanceid.componentId shouldBe componentid
        blueinstanceid.name shouldBe qualifiedid
        blueinstanceid.instance shouldBe "blue"
        blueinstanceid.canonicalKey shouldBe s"$qualifiedid@blue"
        blueinstanceid.toString shouldBe s"$qualifiedid@blue"
        equivalentinstanceid shouldBe instanceid
        equivalentinstanceid.hashCode shouldBe instanceid.hashCode
        Set(instanceid, equivalentinstanceid, blueinstanceid) should have size 2
      }
    }

    "E3 reject unsafe ComponentInstanceId values through the safe factory" must _e3 {
      "when null component and invalid labels are submitted" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R4,R5; Example: E3 org.simplemodeling.textus.UserAccount")
        val qualifiedid = "org.simplemodeling.textus.UserAccount"
        When("the safe ComponentInstanceId factory receives invalid component and label inputs")
        val componentid = ComponentId(qualifiedid)
        val nullcomponentresult = ComponentInstanceId.createC(
          null.asInstanceOf[ComponentId],
          "default"
        )
        val emptylabelresult = ComponentInstanceId.createC(componentid, "")
        val nulllabelresult = ComponentInstanceId.createC(componentid, null)
        Then("the safe factory returns each exact stable failure code and message")
        _assert_consequence_failure(
          nullcomponentresult,
          "component.identity.instance.component-id.required: component ID is required"
        )
        _assert_consequence_failure(
          emptylabelresult,
          "component.identity.instance.label.required: instance label is required"
        )
        _assert_consequence_failure(
          nulllabelresult,
          "component.identity.instance.label.required: instance label is required"
        )
      }
    }

    "E3 reject unsafe ComponentInstanceId values through throwing conveniences" must _e3 {
      "when throwing constructors receive null component and invalid labels" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R4,R5; Example: E3 org.simplemodeling.textus.UserAccount")
        val qualifiedid = "org.simplemodeling.textus.UserAccount"
        When("the throwing ComponentInstanceId constructors receive invalid component and label inputs")
        val componentid = ComponentId(qualifiedid)
        val nullcomponentresult = Try(
          ComponentInstanceId(null.asInstanceOf[ComponentId], "default")
        )
        val emptylabelresult = Try(ComponentInstanceId(componentid, ""))
        val nulllabelresult = Try(ComponentInstanceId(componentid, null))
        Then("each throwing convenience reports IllegalArgumentException")
        _assert_try_illegal_argument(nullcomponentresult)
        _assert_try_illegal_argument(emptylabelresult)
        _assert_try_illegal_argument(nulllabelresult)
      }
    }

    "E4 isolate generated namespaces" must _e4 {
      "when subsystem admission resolves generated same-local identities" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R6,R7; Example: E4 org.alpha.UserAccount, org.beta.UserAccount, and UserAccount")
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
        When("the namespace-isolation property is checked")
        val result = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(50),
          property
        )
        Then("qualified identities remain isolated")
        result.passed shouldBe true
      }
    }

    "E4 reject an ambiguous bare UserAccount" must _e4 {
      "when subsystem admission resolves a bare alias among qualified candidates" in {
        Given("Spec: docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R6,R7; Example: E4 org.alpha.UserAccount, org.beta.UserAccount, and UserAccount")
        val candidates = Vector(
          "org.alpha.UserAccount",
          "org.beta.UserAccount",
          "UserAccount"
        )
        val descriptor = _descriptor(candidates)
        When("SubsystemAssemblyAdmission.resolveC evaluates the bare alias")
        val result = SubsystemAssemblyAdmission.resolveC(descriptor, Vector.empty)
        Then("ambiguous bare resolution reports both qualified candidates")
        pendingUntilFixed {
          result match {
            case Consequence.Failure(conclusion) =>
              val diagnostic = conclusion.show
              diagnostic.toLowerCase should include ("ambiguous")
              diagnostic.toLowerCase should include ("bare")
              diagnostic should include ("org.alpha.UserAccount")
              diagnostic should include ("org.beta.UserAccount")
            case Consequence.Success(_) =>
              fail("ambiguous bare UserAccount was admitted")
          }
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

  private def _assert_consequence_failure(
    result: Consequence[ComponentInstanceId],
    expected: String
  ): Unit =
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.show should include (expected)
      case Consequence.Success(_) =>
        fail(s"expected Consequence.Failure containing: $expected")
    }

  private def _assert_try_illegal_argument(result: Try[_]): Unit =
    result match {
      case Failure(_: IllegalArgumentException) => succeed
      case Failure(exception) =>
        fail(s"expected IllegalArgumentException but received ${exception.getClass.getName}")
      case scala.util.Success(_) =>
        fail("expected IllegalArgumentException but operation succeeded")
    }
}
