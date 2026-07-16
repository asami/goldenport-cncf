package org.goldenport.cncf.resource

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.http.FakeHttpDriver
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 33 RR-03 Textus URN resource resolution.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class TextusUrnResourceAccessSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _namespaces =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(12))

  "TextusUrnResourceProvider" should {
    "resolve the same logical URN through in-memory and configured file providers" in {
      Given("a logical Textus URN, an in-memory provider, and a configured namespace root")
      val reference = ResourceReference.parseC("urn:textus:book:catalog-1.txt").toOption.get
      val memory = new TextusUrnResourceProvider {
        val namespace: String = "book"

        def read(value: TextusUrnReference): Consequence[ResourceContent] =
          Consequence.success(ResourceContent(value.reference, "in-memory".getBytes(StandardCharsets.UTF_8).toVector))
      }
      val root = Files.createTempDirectory("textus-urn-resource")
      Files.writeString(root.resolve("catalog-1.txt"), "configured", StandardCharsets.UTF_8)
      val configured = ResourceAccess.standard(
        ResourceUrlPolicy(),
        TextusUrnResourcePolicy(Map("book" -> root)),
        FakeHttpDriver.okText("unused")
      )

      When("unchanged component-side resource reads use each runtime binding")
      val inmemory = ResourceAccess.textus(Vector(memory)).readText(reference)
      val fromfile = configured.readText(reference)

      Then("provider selection is runtime-owned and the physical root is absent from the logical reference")
      inmemory.toOption shouldBe Some("in-memory")
      fromfile.toOption shouldBe Some("configured")
      reference.print shouldBe "urn:textus:book:catalog-1.txt"
      reference.print should not include root.toString
    }

    "normalize namespaces while preserving safe resource identifiers" in {
      Given("generated valid Textus namespaces")
      val property = Prop.forAll(_namespaces) { namespace =>
        val reference = ResourceReference.parseC(s"urn:textus:${namespace.toUpperCase}:documents/document-1.json")
        reference.toOption.collect { case urn: ResourceReference.Urn => urn }
          .flatMap(TextusUrnReference.parseC(_).toOption)
          .exists(value => value.namespace == namespace && value.resourceId == "documents/document-1.json")
      }

      When("the URNs are parsed as Textus resource references")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("namespace routing is canonical and resource IDs remain logical identifiers")
      checked.passed shouldBe true
    }

    "reject unknown namespaces and unsafe resource identifiers before a file provider can read" in {
      Given("a runtime that binds only the book namespace")
      val root = Files.createTempDirectory("textus-urn-policy")
      val access = ResourceAccess.standard(
        ResourceUrlPolicy(),
        TextusUrnResourcePolicy(Map("book" -> root)),
        FakeHttpDriver.okText("unused")
      )
      val unknown = ResourceReference.parseC("urn:textus:paper:catalog-1").toOption.get
      val unsafe = ResourceReference.parseC("urn:textus:book:../catalog-1").toOption.get

      When("component code requests unbound and unsafe logical references")
      val unknownresult = access.read(unknown)
      val unsaferesult = access.read(unsafe)

      Then("both return structured failures without exposing the configured root")
      unknownresult.isFaillure shouldBe true
      unsaferesult.isFaillure shouldBe true
      unknownresult.toOption shouldBe None
      unsaferesult.toOption shouldBe None
    }

    "reject a logical resource whose symbolic link escapes its configured root" in {
      Given("a configured namespace root with a symbolic link to a foreign file")
      val root = Files.createTempDirectory("textus-urn-root")
      val foreignroot = Files.createTempDirectory("textus-urn-foreign")
      val foreign = foreignroot.resolve("foreign.txt")
      Files.writeString(foreign, "foreign", StandardCharsets.UTF_8)
      Files.createSymbolicLink(root.resolve("escape.txt"), foreign)
      val access = ResourceAccess.standard(
        ResourceUrlPolicy(),
        TextusUrnResourcePolicy(Map("book" -> root)),
        FakeHttpDriver.okText("unused")
      )
      val reference = ResourceReference.parseC("urn:textus:book:escape.txt").toOption.get

      When("the logical Textus resource is read through the configured provider")
      val result = access.read(reference)

      Then("the provider rejects the resolved path outside the configured root")
      result.isFaillure shouldBe true
      result.toOption shouldBe None
    }

    "reject malformed namespace root configuration deterministically" in {
      Given("relative, duplicate, and unsafe namespace root bindings")
      val values = Vector(
        Vector("book=relative/root"),
        Vector("Book=/tmp/book", "book=/tmp/other"),
        Vector("book/path=/tmp/book")
      )

      When("the policy is constructed before runtime installation")
      val results = values.map(TextusUrnResourcePolicy.fromValuesC)

      Then("each malformed binding is rejected as a structured argument failure")
      results.forall(_.isFaillure) shouldBe true
    }
  }
}
