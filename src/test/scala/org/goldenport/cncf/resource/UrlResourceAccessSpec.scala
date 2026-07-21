package org.goldenport.cncf.resource

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.goldenport.bag.Bag
import org.goldenport.cncf.http.{FakeHttpDriver, HttpDriver}
import org.goldenport.datatype.ContentType
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 33 RR-02 URL provider policy.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class UrlResourceAccessSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _labels =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(12))

  "ResourceAccess URL providers" should {
    "read a regular file only below its configured root" in {
      Given("a configured root containing an allowed file and a separate foreign file")
      val root = Files.createTempDirectory("resource-access-root")
      val allowed = root.resolve("message.txt")
      val foreignroot = Files.createTempDirectory("resource-access-foreign")
      val foreign = foreignroot.resolve("message.txt")
      Files.writeString(allowed, "allowed", StandardCharsets.UTF_8)
      Files.writeString(foreign, "foreign", StandardCharsets.UTF_8)
      val access = ResourceAccess.url(
        ResourceUrlPolicy(fileRoots = Vector(root)),
        FakeHttpDriver.okText("unused")
      )

      When("the component DSL requests both logical file URLs")
      val accepted = access.read(ResourceReference.parseC(allowed.toUri.toString).toOption.get)
      val rejected = access.read(ResourceReference.parseC(foreign.toUri.toString).toOption.get)

      Then("only the configured-root content is returned")
      accepted.toOption.flatMap(_.textC().toOption) shouldBe Some("allowed")
      rejected.isFaillure shouldBe true
    }

    "dispatch configured HTTPS reads through the CNCF HttpDriver and reject other hosts" in {
      Given("an HTTPS policy for one host and a deterministic HTTP driver")
      val access = ResourceAccess.url(
        ResourceUrlPolicy(httpsHosts = Vector("catalog.example.test")),
        FakeHttpDriver.okText("catalog-content", "text/plain; charset=utf-8")
      )
      val accepted = ResourceReference.parseC("https://catalog.example.test/items/1").toOption.get
      val rejected = ResourceReference.parseC("https://other.example.test/items/1").toOption.get

      When("the component DSL reads allowed and unconfigured HTTPS references")
      val content = access.readText(accepted)
      val denied = access.read(rejected)

      Then("only the configured host reaches the HTTP provider")
      content.toOption shouldBe Some("catalog-content")
      denied.isFaillure shouldBe true
    }

    "preserve a throwing HTTPS transport as a structured resource failure" in {
      Given("a configured host whose deterministic driver raises a transport exception")
      val access = ResourceAccess.url(
        ResourceUrlPolicy(httpsHosts = Vector("catalog.example.test")),
        new HttpDriver {
          def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            throw new IllegalStateException("transport unavailable")

          def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            throw new UnsupportedOperationException("unused")

          override def postBag(path: String, body: Option[Bag], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            throw new UnsupportedOperationException("unused")

          def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            throw new UnsupportedOperationException("unused")
        }
      )
      val reference = ResourceReference.parseC("https://catalog.example.test/items/1").toOption.get

      When("the resource DSL reads the configured reference")
      val result = access.read(reference)

      Then("the transport exception is preserved as a structured failure")
      result.isFaillure shouldBe true
    }

    "apply fixed static Web policy before invoking the HTTPS transport" in {
      Given("a configured static Web host and a driver that records request properties")
      var observed = Vector.empty[Property]
      val access = ResourceAccess.url(
        ResourceUrlPolicy(httpsHosts = Vector("8.8.8.8")),
        new HttpDriver {
          def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
            observed = properties
            HttpResponse.text(HttpStatus.Ok, "catalog-content")
              .withHeader(Record.dataAuto("Content-Type" -> "text/plain"))
          }

          def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            throw new UnsupportedOperationException("unused")

          override def postBag(path: String, body: Option[Bag], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            throw new UnsupportedOperationException("unused")

          def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            throw new UnsupportedOperationException("unused")
        }
      )
      val reference = ResourceReference.parseC("https://8.8.8.8/items/1").toOption.get

      When("the static Web resource DSL reads the configured reference")
      val result = access.readStaticWeb(reference).flatMap(_.textC())

      Then("the transport receives no-redirect timeout byte and network policy")
      result.toOption shouldBe Some("catalog-content")
      observed.map(property => property.name -> property.value.toString) should contain (
        "http.follow-redirects" -> "false"
      )
      observed.map(property => property.name -> property.value.toString) should contain allOf (
        "http.connect-timeout-ms" -> "5000",
        "http.read-timeout-ms" -> "5000",
        "http.max-response-bytes" -> "1048576",
        "http.public-network-only" -> "true"
      )
    }

    "deny unconfigured schemes and hosts before a provider can be selected" in {
      Given("a URL access value with no configured policy")
      val access = ResourceAccess.url(ResourceUrlPolicy(), FakeHttpDriver.okText("unused"))
      val urls = Vector(
        "file:///tmp/managed.txt",
        "https://catalog.example.test/items/1",
        "http://catalog.example.test/items/1"
      ).map(ResourceReference.parseC(_).toOption.get)

      When("each reference is requested")
      val results = urls.map(access.read)

      Then("each request returns a structured policy failure")
      results.forall(_.isFaillure) shouldBe true
    }

    "deny static Web reads through a provider without the static admission capability" in {
      Given("an allowlisted HTTPS host and a generic URL provider")
      var reads = 0
      val access = ResourceAccess.url(
        ResourceUrlPolicy(httpsHosts = Vector("8.8.8.8")),
        Vector(new UrlResourceProvider {
          val scheme = "https"
          def read(reference: ResourceReference.Url): org.goldenport.Consequence[ResourceContent] = {
            reads += 1
            org.goldenport.Consequence.success(ResourceContent(reference, Vector.empty))
          }
        })
      )
      val reference = ResourceReference.parseC("https://8.8.8.8/static").toOption.get

      When("the static Web boundary requests the resource")
      val result = access.readStaticWeb(reference)

      Then("the generic provider cannot bypass transport-level static admission")
      result.isSuccess shouldBe false
      reads shouldBe 0
    }

    "reject static Web responses without an explicit content type" in {
      Given("an admitted static Web target whose transport response omits Content-Type")
      val access = ResourceAccess.url(
        ResourceUrlPolicy(httpsHosts = Vector("8.8.8.8")),
        new HttpDriver {
          def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            HttpResponse.Text(
              HttpStatus.Ok,
              ContentType.TEXT_PLAIN,
              Bag.text("undeclared-content")
            )
          def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            throw new UnsupportedOperationException("unused")
          def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
            throw new UnsupportedOperationException("unused")
        }
      )
      val reference = ResourceReference.parseC("https://8.8.8.8/undeclared").toOption.get

      When("the static Web read applies response admission")
      val result = access.readStaticWeb(reference)

      Then("the transport default cannot masquerade as a declared media type")
      result.isSuccess shouldBe false
    }

    "match HTTPS policy host names exactly and case-insensitively" in {
      Given("generated valid hostname labels")
      val property = Prop.forAll(_labels) { label =>
        val host = s"${label}.example.test"
        val policy = ResourceUrlPolicy(httpsHosts = Vector(host.toUpperCase(java.util.Locale.ROOT)))
        policy.permitsHttpsHost(host) && !policy.permitsHttpsHost(s"child.${host}")
      }

      When("the generated host policies are evaluated")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("host authorization is deterministic and does not imply subdomain authorization")
      checked.passed shouldBe true
    }
  }
}
