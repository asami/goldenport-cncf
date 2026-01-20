package org.goldenport.cncf.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.goldenport.bag.{Bag, TextBag}
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.datatype.MimeBody
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 10, 2026
 * @version Jan. 11, 2026
 * @author  ASAMI, Tomoharu
 */
class ClientRequestNormalizationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with ConsequenceMatchers
  with TableDrivenPropertyChecks {

  "Client HTTP request normalization" should {
    "normalize canonical http get/post forms" in {
      val table = Table(
        ("args", "operation", "path", "body"),
        (
          Array("http", "get", "/admin/system/ping"),
          "get",
          "/admin/system/ping",
          None
        ),
        (
          Array("http", "post", "/admin/system/ping", "-d", "pong"),
          "post",
          "/admin/system/ping",
          Some("pong")
        )
      )

      forAll(table) { (args, operation, path, body) =>
        Given("canonical client http CLI arguments")
        val request = CncfRuntime.parseClientArgs(args)

        When("the arguments are normalized into a Request")
        request should be_success

        Then("the Request has the canonical client http shape")
        request match {
          case org.goldenport.Consequence.Success(req) =>
            req.component shouldBe Some("client")
            req.service shouldBe Some("http")
            req.operation shouldBe operation
            req.arguments shouldBe List(Argument("path", path, None))
            _property(req, "baseurl") shouldBe Some(
              Property("baseurl", ClientConfig.DefaultBaseUrl, None)
            )
            body match {
              case Some(value) =>
                val property = _property(req, "data")
                property.map(_.name) shouldBe Some("data")
                property.foreach { p =>
                  _bag_text(p.value) shouldBe Some(value)
                }
              case None =>
                _property(req, "data") shouldBe None
            }
          case _ =>
            fail("expected successful normalization")
        }
      }
    }

    "normalize client sugar into http get" in {
      Given("client sugar arguments without explicit http operation")
      val request = CncfRuntime.parseClientArgs(
        Array("admin", "system", "ping")
      )

      When("the arguments are normalized into a Request")
      request should be_success

      Then("the Request becomes a client http get with normalized path")
      request match {
        case org.goldenport.Consequence.Success(req) =>
          req.component shouldBe Some("client")
          req.service shouldBe Some("http")
          req.operation shouldBe "get"
          req.arguments shouldBe List(
            Argument("path", "/admin/system/ping", None)
          )
          _property(req, "baseurl") shouldBe Some(
            Property("baseurl", ClientConfig.DefaultBaseUrl, None)
          )
        case _ =>
          fail("expected successful normalization")
      }
    }

    "resolve -d @file into Bag at request construction time" in {
      Given("a local file referenced via -d @path")
      val file = Files.createTempFile("cncf-client-body", ".txt")
      val _ = Files.writeString(file, "pong", StandardCharsets.UTF_8)

      try {
        val request = CncfRuntime.parseClientArgs(
          Array("http", "post", "/admin/system/ping", "-d", s"@${file}")
        )

        When("the arguments are normalized into a Request")
        request should be_success

        Then("the data property is a Bag created from the file content")
        request match {
          case org.goldenport.Consequence.Success(req) =>
          val property = _property(req, "data")
          property.map(_.name) shouldBe Some("data")
          property.foreach { p =>
            _bag_text(p.value) shouldBe Some("pong")
          }
          case _ =>
            fail("expected successful normalization")
        }
      } finally {
        val _ = Files.deleteIfExists(file)
      }
    }

    "include explicit baseurl overrides" in {
      Given("client http arguments with an explicit baseurl")
      val request = CncfRuntime.parseClientArgs(
        Array("http", "get", "/admin/system/ping", "--baseurl", "http://example.test")
      )

      When("the arguments are normalized into a Request")
      request should be_success

      Then("the baseurl property is preserved in the Request")
      request match {
        case org.goldenport.Consequence.Success(req) =>
          _property(req, "baseurl") shouldBe Some(
            Property("baseurl", "http://example.test", None)
          )
        case _ =>
          fail("expected successful normalization")
      }
    }
  }

  private def _property(
    req: Request,
    name: String
  ): Option[Property] =
    req.properties.find(_.name == name)

  private def _bag_text(
    value: Any
  ): Option[String] =
    value match {
      case t: TextBag => Some(t.toText)
      case s: String => Some(s)
      case MimeBody(_, bag) => _bag_to_text(bag)
      case bag: Bag => _bag_to_text(bag)
      case _ => None
    }

  private def _bag_to_text(
    bag: Bag
  ): Option[String] = {
    val in = bag.openInputStream()
    try {
      val bytes = in.readAllBytes()
      Some(new String(bytes, StandardCharsets.UTF_8))
    } catch {
      case _: Exception => None
    } finally {
      in.close()
    }
  }
}
