package org.goldenport.cncf.action

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.resource.{ResourceAccessTestProfile, ResourceReadLimits, InMemoryUrnResourceProvider, ResourceAccess, ResourceContent, ResourceReference, ResourceTreeAccess, ResourceTreeEntry, ResourceTreeQuery, ResourceTreeReference}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the component-facing resource internal DSL.
 *
 * @since   Jul. 16, 2026
 *  version Jul. 20, 2026
 * @version Oct. 15, 2026
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
      val behavior = new ResourceBehavior(Behavior.Core(context, None, None))

      When("component behavior reads the logical reference through its DSL helper")
      val result = behavior.readText(reference)

      Then("the injected resource content is returned")
      result.toOption shouldBe Some("hello")
    }

    "apply a test-runtime cap through ordinary byte and text helpers" in {
      Given("a component context with preowned Unicode content and a three-byte runtime limit")
      val profile = ResourceAccessTestProfile(
        urnProviders = Vector(new InMemoryUrnResourceProvider("example", Map("equal" -> "日", "excess" -> "日本"))),
        readLimits = ResourceReadLimits(3)
      )
      val context = ExecutionContext.withResourceAccessTestProfile(ExecutionContext.create(), profile)
      val behavior = new ResourceBehavior(Behavior.Core(context, None, None))
      val equal = ResourceReference.parseC("urn:example:equal").toOption.get
      val excess = ResourceReference.parseC("urn:example:excess").toOption.get

      When("component behavior requests ordinary bytes and strict text")
      val bytes = behavior.read(equal)
      val text = behavior.readText(equal)
      val refused = behavior.readText(excess)

      Then("equality preserves the complete UTF-8 bytes and overflow supplies no partial text")
      bytes.toOption.map(_.bytes) shouldBe Some("日".getBytes(StandardCharsets.UTF_8).toVector)
      text.toOption shouldBe Some("日")
      refused.isFaillure shouldBe true
      refused.toOption shouldBe None
    }

    "fail deterministically when no resource access implementation is configured" in {
      Given("the standard unconfigured execution context")
      val reference = ResourceReference.parseC("urn:example:missing").toOption.get
      val behavior = new ResourceBehavior(Behavior.Core(ExecutionContext.create(), None, None))

      When("component behavior requests a managed resource")
      val result = behavior.read(reference)

      Then("the context returns a structured unavailable-service failure")
      result.isFaillure shouldBe true
    }

    "read an injected logical resource tree without granting ambient directory access" in {
      Given("an execution context with an explicitly injected in-memory resource tree")
      val reference = ResourceTreeReference.parseC("component-input").toOption.get
      val entry = ResourceTreeEntry.createC("source/input.txt", "hello".getBytes(StandardCharsets.UTF_8).toVector).toOption.get
      val context = ExecutionContext.withResourceTreeAccess(
        ExecutionContext.create(),
        ResourceTreeAccess.inMemory(Map(reference -> Vector(entry)))
      )
      val behavior = new ResourceBehavior(Behavior.Core(context, None, None))

      When("component behavior snapshots the named tree through its DSL helper")
      val result = behavior.readTree(reference)

      Then("only immutable logical entries are returned")
      result.toOption.map(_.entries.map(_.relativePath)) shouldBe Some(Vector("source/input.txt"))
      result.toOption.map(_.entries.head.bytes) shouldBe Some("hello".getBytes(StandardCharsets.UTF_8).toVector)
    }

    "query an injected logical resource tree through the protected DSL helper" in {
      Given("an execution context with a named in-memory tree")
      val reference = ResourceTreeReference.parseC("component-input").toOption.get
      val entry = ResourceTreeEntry.createC("source/project.yaml", "name: demo".getBytes(StandardCharsets.UTF_8).toVector).toOption.get
      val context = ExecutionContext.withResourceTreeAccess(
        ExecutionContext.create(),
        ResourceTreeAccess.inMemory(Map(reference -> Vector(entry)))
      )
      val behavior = new ResourceBehavior(Behavior.Core(context, None, None))
      val query = ResourceTreeQuery.exactLeafNameC(reference, "project.yaml").toOption.get

      When("component behavior queries the named tree through its DSL helper")
      val result = behavior.queryTree(query)

      Then("the query returns only the matching immutable logical entry")
      result.toOption.map(_.entries.map(_.relativePath)) shouldBe Some(Vector("source/project.yaml"))
    }
  }

  private final class ResourceBehavior(
    val behaviorCore: Behavior.Core
  ) extends Behavior {
    def read(reference: ResourceReference): Consequence[ResourceContent] =
      read_resource(reference)

    def readText(reference: ResourceReference): Consequence[String] =
      read_resource_text(reference)

    def readTree(reference: ResourceTreeReference) =
      read_resource_tree(reference)

    def queryTree(query: ResourceTreeQuery) =
      query_resource_tree(query)
  }
}
