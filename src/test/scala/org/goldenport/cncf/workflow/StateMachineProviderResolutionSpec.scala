package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class StateMachineProviderResolutionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "StateMachineProviderResolver" should {
    "select the Provider only by its explicit Required SPI identity" in {
      Given("two Required SPI identities with explicitly bound Providers")
      val firstrequired = StateMachineRequiredOperationIdentity("required.first")
      val secondrequired = StateMachineRequiredOperationIdentity("required.second")
      val firstprovider = new ProbeProvider("provider.first")
      val secondprovider = new ProbeProvider("provider.second")
      val resolver = StateMachineProviderResolver.create(
        Vector(
          StateMachineProviderBinding(secondrequired, secondprovider.identity),
          StateMachineProviderBinding(firstrequired, firstprovider.identity)
        ),
        Vector(firstprovider, secondprovider)
      ).toOption.getOrElse(fail("the explicit bindings should be admitted"))

      When("the second Required SPI identity is resolved")
      val resolved = resolver.resolve(secondrequired)

      Then("only its bound Provider is selected and no Provider is invoked")
      resolved.toOption shouldBe Some(secondprovider)
      firstprovider.executionCalls shouldBe 0
      secondprovider.executionCalls shouldBe 0
    }

    "reject duplicate Required SPI identities" in {
      Given("two bindings for one Required SPI identity")
      val required = StateMachineRequiredOperationIdentity("required.duplicate")
      val firstprovider = new ProbeProvider("provider.first")
      val secondprovider = new ProbeProvider("provider.second")

      When("the binding vector is admitted")
      val result = StateMachineProviderResolver.create(
        Vector(
          StateMachineProviderBinding(required, firstprovider.identity),
          StateMachineProviderBinding(required, secondprovider.identity)
        ),
        Vector(firstprovider, secondprovider)
      )

      Then("admission fails before selection")
      _expect_failure(result)
    }

    "reject duplicate Provider identities" in {
      Given("two Provider candidates with the same Provider identity")
      val required = StateMachineRequiredOperationIdentity("required.one")
      val firstprovider = new ProbeProvider("provider.duplicate")
      val secondprovider = new ProbeProvider("provider.duplicate")

      When("the candidate Provider vector is admitted")
      val result = StateMachineProviderResolver.create(
        Vector(StateMachineProviderBinding(required, firstprovider.identity)),
        Vector(firstprovider, secondprovider)
      )

      Then("admission fails before selection")
      _expect_failure(result)
    }

    "reject a binding whose selected Provider is absent" in {
      Given("a Required SPI binding targeting no candidate Provider")
      val required = StateMachineRequiredOperationIdentity("required.absent")

      When("the binding vector is admitted")
      val result = StateMachineProviderResolver.create(
        Vector(StateMachineProviderBinding(required, ProviderIdentity("provider.absent"))),
        Vector.empty
      )

      Then("admission fails before selection")
      _expect_failure(result)
    }

    "fail closed for an unbound Required SPI identity" in {
      Given("a resolver with one binding for a different Required SPI identity")
      val bound = StateMachineRequiredOperationIdentity("required.bound")
      val unbound = StateMachineRequiredOperationIdentity("required.unbound")
      val provider = new ProbeProvider("provider.bound")
      val resolver = StateMachineProviderResolver.create(
        Vector(StateMachineProviderBinding(bound, provider.identity)),
        Vector(provider)
      ).toOption.getOrElse(fail("the explicit binding should be admitted"))

      When("the unbound Required SPI identity is resolved")
      val result = resolver.resolve(unbound)

      Then("resolution remains a typed Consequence failure without Provider invocation")
      _expect_failure(result)
      provider.executionCalls shouldBe 0
    }
  }

  private def _expect_failure[A](result: Consequence[A]): Unit =
    result match {
      case Consequence.Failure(_) => succeed
      case Consequence.Success(_) => fail("a typed Consequence failure was required")
    }

  private final class ProbeProvider(value: String) extends StateMachineProvider {
    private var _execution_calls = 0

    def executionCalls: Int = _execution_calls

    override val identity: ProviderIdentity = ProviderIdentity(value)

    override def execute(request: ProviderExecutionRequest): ActionExecution = {
      val _ = request
      _execution_calls += 1
      throw new AssertionError("Provider execution is outside provider resolution")
    }
  }
}
