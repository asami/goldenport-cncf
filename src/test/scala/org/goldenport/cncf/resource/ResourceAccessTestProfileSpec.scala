package org.goldenport.cncf.resource

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.observability.{DslChokepointContext, DslChokepointHook}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 33 RR-05 deterministic resource fixtures
 * and policy-safe resource resolution diagnostics.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ResourceAccessTestProfileSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _nss =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(12))

  "ResourceAccessTestProfile" should {
    "bind deterministic URL Textus URN and external URN providers through an execution context" in {
      Given("an explicit in-memory test profile and an execution context")
      val trace = new _ResourceTrace
      val profile = ResourceAccessTestProfile(
        urlPolicy = ResourceUrlPolicy(httpsHosts = Vector("catalog.example")),
        urlProviders = Vector(new InMemoryUrlResourceProvider(
          "https",
          Map("https://catalog.example/metadata.json" -> "url-content")
        )),
        textusUrnProviders = Vector(new InMemoryTextusUrnResourceProvider(
          "book",
          Map("catalog-1.json" -> "textus-content")
        )),
        urnProviders = Vector(new InMemoryUrnResourceProvider(
          "example",
          Map("catalog-1" -> "external-content")
        ))
      )
      val context = ExecutionContext.withResourceAccessTestProfile(
        ExecutionContext.withFrameworkDslChokepointHooks(
          ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true),
          Vector(trace)
        ),
        profile
      )
      val url = ResourceReference.parseC("https://catalog.example/metadata.json").toOption.get
      val textus = ResourceReference.parseC("urn:textus:book:catalog-1.json").toOption.get
      val external = ResourceReference.parseC("urn:example:catalog-1").toOption.get

      When("component code reads each logical reference through ExecutionContext.resources")
      val urlcontent = context.resources.readText(url)
      val textuscontent = context.resources.readText(textus)
      val externalcontent = context.resources.readText(external)

      Then("all reads are deterministic and no host file or network resource is used")
      urlcontent.toOption shouldBe Some("url-content")
      textuscontent.toOption shouldBe Some("textus-content")
      externalcontent.toOption shouldBe Some("external-content")

      And("resolution diagnostics expose only logical provider identity")
      val rendered = trace.contexts.map(_.toRecord.print).mkString("\n")
      rendered should include("resource.provider.family=url")
      rendered should include("resource.provider.identity=https")
      rendered should include("resource.provider.family=textus-urn")
      rendered should include("resource.provider.identity=book")
      rendered should include("resource.provider.family=urn")
      rendered should include("resource.provider.identity=example")
      rendered should not include "metadata.json"
      rendered should not include "catalog-1"
      rendered should not include "url-content"
      rendered should not include "textus-content"
      rendered should not include "external-content"
    }

    "preserve generated external resource values without leaking them through missing-resource failures" in {
      Given("generated external resource identifiers and an in-memory provider without entries")
      val property = Prop.forAll(_nss) { nss =>
        val reference = ResourceReference.parseC(s"urn:example:${nss}").toOption.get
        val profile = ResourceAccessTestProfile(
          urnProviders = Vector(new InMemoryUrnResourceProvider("example", Map.empty))
        )
        val context = ExecutionContext.withResourceAccessTestProfile(ExecutionContext.create(), profile)

        When("the missing generated resource is read through the internal DSL")
        val result = context.resources.read(reference)

        Then("the structured failure does not include the complete logical reference")
        result match {
          case Consequence.Failure(conclusion) =>
            !conclusion.display.contains(s"urn:example:${nss}")
          case Consequence.Success(_) => false
        }
      }

      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("the missing-resource privacy invariant holds for every generated identifier")
      checked.passed shouldBe true
    }
  }
}

private final class _ResourceTrace extends DslChokepointHook {
  private var _contexts = Vector.empty[DslChokepointContext]

  def contexts: Vector[DslChokepointContext] = _contexts

  override def enter(context: DslChokepointContext)(using ExecutionContext): Unit =
    _contexts = _contexts :+ context
}
