package org.goldenport.cncf.action

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.resource.{ResourceAccess, ResourceContent, ResourceReference}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the component-facing resource internal DSL.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResourceAccessDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Behavior resource internal DSL" should {
    "read injected resource content without ambient filesystem or network access" in {
      Given("an execution context with an explicitly injected resource access implementation")
      val reference = ResourceReference.parseC("urn:example:message").toOption.get
      val context = ExecutionContext.withResourceAccess(
        ExecutionContext.create(),
        new ResourceAccess {
          def read(value: ResourceReference): Consequence[ResourceContent] =
            if (value == reference)
              Consequence.success(ResourceContent(value, "hello".getBytes(StandardCharsets.UTF_8).toVector))
            else
              Consequence.resourceNotFound(s"test resource is not available for ${value.scheme}")
        }
      )
      val behavior = new _ResourceBehavior(Behavior.Core(context, None, None))

      When("component behavior reads the logical reference through its DSL helper")
      val result = behavior.readText(reference)

      Then("the injected resource content is returned")
      result.toOption shouldBe Some("hello")
    }

    "fail deterministically when no resource access implementation is configured" in {
      Given("the standard unconfigured execution context")
      val reference = ResourceReference.parseC("urn:example:missing").toOption.get
      val behavior = new _ResourceBehavior(Behavior.Core(ExecutionContext.create(), None, None))

      When("component behavior requests a managed resource")
      val result = behavior.read(reference)

      Then("the context returns a structured unavailable-service failure")
      result.isFaillure shouldBe true
    }
  }

  private final class _ResourceBehavior(
    val behaviorCore: Behavior.Core
  ) extends Behavior {
    def read(reference: ResourceReference): Consequence[ResourceContent] =
      read_resource(reference)

    def readText(reference: ResourceReference): Consequence[String] =
      read_resource_text(reference)
  }
}
