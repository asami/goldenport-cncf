package org.goldenport.cncf.resource

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
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
 *  version Jul. 21, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class UrlResourceAccessSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _labels =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(12))

  "ResourceAccess URL providers" should {
    "read a regular file only below its configured root" in {
      Given("a configured root containing an allowed file and a separate foreign file")
      val root = Files.createTempDirectory("resource-access-root").toRealPath()
      val allowed = root.resolve("message.txt")
      val foreignroot = Files.createTempDirectory("resource-access-foreign").toRealPath()
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

    "enforce the ordinary file URL cap and reject symbolic-link paths privately" in {
      Given("a canonical configured root with exact, excessive, nonregular and linked file resources")
      _with_fixture { root =>
        val equalpath = Files.writeString(root.resolve("equal.txt"), "日", StandardCharsets.UTF_8)
        Files.writeString(root.resolve("excess.txt"), "日本", StandardCharsets.UTF_8)
        val directory = Files.createDirectory(root.resolve("directory"))
        Files.writeString(directory.resolve("nested.txt"), "日", StandardCharsets.UTF_8)
        Files.createSymbolicLink(root.resolve("inside.txt"), equalpath)
        Files.createSymbolicLink(root.resolve("linked-directory"), directory)
        val linkedroot = Files.createSymbolicLink(root.resolve("linked-root"), directory)
        val access = ResourceAccess.url(
          ResourceUrlPolicy(fileRoots = Vector(root)), FakeHttpDriver.okText("unused"), ResourceReadLimits(3)
        )
        val paths = Vector("excess.txt", "inside.txt", "linked-directory/nested.txt", "directory", "missing.txt")
          .map(root.resolve(_))
        val references = paths.map(path => ResourceReference.parseC(path.toUri.toString).toOption.get)

        When("ordinary file URL reads attempt equality, overflow, final links, linked ancestors and missing resources")
        val equal = access.readText(ResourceReference.parseC(equalpath.toUri.toString).toOption.get)
        val refused = references.map(access.read)
        val rootaccess = ResourceAccess.url(
          ResourceUrlPolicy(fileRoots = Vector(linkedroot)), FakeHttpDriver.okText("unused"), ResourceReadLimits(3)
        )
        val rootresult = rootaccess.read(ResourceReference.parseC(linkedroot.resolve("nested.txt").toUri.toString).toOption.get)

        Then("only complete equality content is returned and failures omit paths and private content")
        equal.toOption shouldBe Some("日")
        (refused :+ rootresult).foreach { result =>
          result.isFaillure shouldBe true
          result.toOption shouldBe None
          result match {
            case org.goldenport.Consequence.Failure(conclusion) =>
              conclusion.display should not include root.toString
              conclusion.display should not include "日本"
            case _ => fail("expected a private resource failure")
          }
        }
        Files.readString(equalpath) shouldBe "日"
      }
    }

    "send the runtime HTTPS cap to the driver and bound its actual response body" in {
      Given("a configured exact HTTPS host and a recording driver with a preowned four-byte body")
      val driver = new RecordingHttpDriver("abcd")
      val policy = ResourceUrlPolicy(httpsHosts = Vector("catalog.example.test"))
      val reference = ResourceReference.parseC("https://catalog.example.test/private-content").toOption.get
      val equalaccess = ResourceAccess.url(policy, driver, ResourceReadLimits(4))
      val excessaccess = ResourceAccess.url(policy, driver, ResourceReadLimits(3))
      val userinfo = ResourceReference.parseC("https://user:secret@catalog.example.test/private-content").toOption.get

      When("ordinary reads consume equality, overflow and a user-info target")
      val equal = equalaccess.readText(reference)
      val excess = excessaccess.read(reference)
      val denied = equalaccess.read(userinfo)

      Then("the driver receives each runtime cap without redirects and cannot return a partial oversized body")
      equal.toOption shouldBe Some("abcd")
      excess.isFaillure shouldBe true
      excess.toOption shouldBe None
      denied.isFaillure shouldBe true
      driver.requests.map(_.map(property => property.name -> property.value.toString).toMap) shouldBe Vector(
        Map("http.follow-redirects" -> "false", "http.max-response-bytes" -> "4"),
        Map("http.follow-redirects" -> "false", "http.max-response-bytes" -> "3")
      )
    }

    "tighten static Web acquisition to the smaller runtime cap or existing ceiling" in {
      Given("an admitted public literal target and recording transports with exact and oversized preowned bodies")
      val reference = ResourceReference.parseC("https://8.8.8.8/private-content").toOption.get
      val policy = ResourceUrlPolicy(httpsHosts = Vector("8.8.8.8"))
      val small = new RecordingHttpDriver("abcd")
      val large = new RecordingHttpDriver("x" * 1048577)
      val smallaccess = ResourceAccess.url(policy, small, ResourceReadLimits(3))
      val ceilingaccess = ResourceAccess.url(policy, large, ResourceReadLimits(2097152))
      val exact = new RecordingHttpDriver("abc")

      When("static Web reads consume bodies under smaller and larger runtime limits")
      val smallresult = smallaccess.readStaticWeb(reference)
      val ceilingresult = ceilingaccess.readStaticWeb(reference)
      val exactresult = ResourceAccess.url(policy, exact, ResourceReadLimits(3)).readStaticWeb(reference)

      Then("each effective cap bounds the actual body while public-network and timeout policy stay enforced")
      smallresult.isFaillure shouldBe true
      smallresult.toOption shouldBe None
      ceilingresult.isFaillure shouldBe true
      ceilingresult.toOption shouldBe None
      exactresult.toOption.flatMap(_.textC().toOption) shouldBe Some("abc")
      Vector(small -> "3", large -> "1048576", exact -> "3").foreach { case (driver, maximum) =>
        driver.requests should have size 1
        driver.requests.head.map(property => property.name -> property.value.toString).toMap shouldBe Map(
          "http.follow-redirects" -> "false", "http.max-response-bytes" -> maximum,
          "http.connect-timeout-ms" -> "5000", "http.read-timeout-ms" -> "5000",
          "http.public-network-only" -> "true"
        )
      }
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
          def read(reference: ResourceReference.Url, limits: ResourceReadLimits): org.goldenport.Consequence[ResourceContent] = {
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
  private def _with_fixture(body: Path => Unit): Unit = {
    val work = Files.createDirectories(Path.of("target/resource-url-read/work")).toRealPath()
    val root = Files.createTempDirectory(work, "case-").toRealPath()
    try body(root)
    finally Using.resource(Files.walk(root)) { stream =>
      stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete(_))
    }
  }

  private final class RecordingHttpDriver(body: String) extends HttpDriver {
    private var _requests = Vector.empty[Vector[Property]]
    def requests: Vector[Vector[Property]] = _requests
    def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse = {
      _requests = _requests :+ properties
      HttpResponse.text(HttpStatus.Ok, body).withHeader(Record.dataAuto("Content-Type" -> "text/plain"))
    }
    def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
      throw new UnsupportedOperationException("unused")
    def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property]): HttpResponse =
      throw new UnsupportedOperationException("unused")
  }

}
