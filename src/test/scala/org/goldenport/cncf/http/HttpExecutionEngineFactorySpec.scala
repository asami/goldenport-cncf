package org.goldenport.cncf.http

import org.goldenport.cncf.config.RuntimeOperationSecurityPolicy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpExecutionEngineFactorySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-runtime-operation-web-authorization-policy, example:E4, rules:GCF09L-C4, phase:55, slice:GCF-09L"
  )

  "Public default HTTP factories" should {
    "E4 admit the empty final operation-security policy" must _e1 {
      "when the engine, HTTP server, and loopback factories create a runtime" in {
        Given("the public default HTTP factory entry points")

        When("each entry point creates its default runtime")
        val engine = HttpExecutionEngine.Factory.engine()
        val server = Http4sHttpServer.create()
        val loopback = LoopbackHttpServer.create()

        Then("the policy is admitted before any HTTP consumer can read it")
        engine.runtimeSubsystem.runtimeOperationSecurityPolicyC.toOption shouldBe Some(
          RuntimeOperationSecurityPolicy.default
        )
        server should not be null
        loopback should not be null
      }
    }
  }
}
