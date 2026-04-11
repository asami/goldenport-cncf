package org.goldenport.cncf.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.goldenport.bag.{Bag, TextBag}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.component.builtin.admin.AdminComponent
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.datatype.MimeBody
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 10, 2026
 *  version Mar. 29, 2026
 * @version Apr. 12, 2026
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
      val subsystem = TestComponentFactory.emptySubsystem("client-request-normalization")
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
        val request = CncfRuntime.parseClientArgs(subsystem, args)

        When("the arguments are normalized into a Request")
        request should be_success

        Then("the Request has the canonical client http shape")
        request match {
          case org.goldenport.Consequence.Success(req) =>
            req.component shouldBe Some("client")
            req.service shouldBe Some("http")
            req.operation shouldBe operation
            req.arguments shouldBe List(Argument("path", path, None))
            _property(req, "baseurl") shouldBe None
            body match {
              case Some(value) =>
                val property = _property(req, "http.body")
                property.map(_.name) shouldBe Some("http.body")
                property.foreach { p =>
                  _bag_text(p.value) shouldBe Some(value)
                }
              case None =>
                _property(req, "http.body") shouldBe None
            }
          case _ =>
            fail("expected successful normalization")
        }
      }
    }

    "normalize client sugar into http get" in {
      val subsystem = _subsystem_with_admin()
      Given("client sugar arguments without explicit http operation")
      val request = CncfRuntime.parseClientArgs(
        subsystem,
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
          _property(req, "baseurl") shouldBe None
        case _ =>
          fail("expected successful normalization")
      }
    }

    "resolve -d @file into Bag at request construction time" in {
      val subsystem = TestComponentFactory.emptySubsystem("client-request-normalization")
      Given("a local file referenced via -d @path")
      val file = Files.createTempFile("cncf-client-body", ".txt")
      val _ = Files.writeString(file, "pong", StandardCharsets.UTF_8)

      try {
        val request = CncfRuntime.parseClientArgs(
          subsystem,
          Array("http", "post", "/admin/system/ping", "-d", s"@${file}")
        )

        When("the arguments are normalized into a Request")
        request should be_success

        Then("the http.body property is a Bag created from the file content")
        request match {
          case org.goldenport.Consequence.Success(req) =>
          val property = _property(req, "http.body")
          property.map(_.name) shouldBe Some("http.body")
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
      val subsystem = TestComponentFactory.emptySubsystem("client-request-normalization")
      Given("client http arguments with an explicit baseurl")
      val request = CncfRuntime.parseClientArgs(
        subsystem,
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

    "separate operation body field from namespaced HTTP body options" in {
      val subsystem = _subsystem_with_admin()
      Given("client sugar arguments with a body field")
      val request = CncfRuntime.parseClientArgs(
        subsystem,
        Array("admin.system.ping", "--body", "hello", "--http.body", "raw")
      )

      When("the arguments are normalized into a Request")
      request should be_success

      Then("body remains a form parameter and http.body remains the raw HTTP body parameter")
      request match {
        case org.goldenport.Consequence.Success(req) =>
          _property(req, "body") shouldBe Some(Property("body", "hello", None))
          _property(req, "http.body") shouldBe Some(Property("http.body", "raw", None))
          CncfRuntime._client_form_encoded_payload(req).getOrElse("") should include ("body=hello")
          CncfRuntime._client_form_encoded_payload(req).getOrElse("") should not include ("http.body=raw")
          CncfRuntime._client_mime_body_from_request(req).toOption.flatten.flatMap(body => _bag_text(body.value)) shouldBe Some("raw")
        case _ =>
          fail("expected successful normalization")
      }
    }

    "treat data as a normal client sugar form field" in {
      val subsystem = _subsystem_with_admin()
      Given("client sugar arguments with a data field")
      val request = CncfRuntime.parseClientArgs(
        subsystem,
        Array("admin.system.ping", "--data", "normal")
      )

      When("the arguments are normalized into a Request")
      request should be_success

      Then("data remains a form parameter")
      request match {
        case org.goldenport.Consequence.Success(req) =>
          _property(req, "data") shouldBe Some(Property("data", "normal", None))
          CncfRuntime._client_form_encoded_payload(req).getOrElse("") should include ("data=normal")
          CncfRuntime._client_mime_body_from_request(req).toOption.flatten shouldBe defined
        case _ =>
          fail("expected successful normalization")
      }
    }
  }

  private def _subsystem_with_admin() = {
    val subsystem = TestComponentFactory.emptySubsystem("client-request-normalization")
    val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
    val admin = AdminComponent.Factory.create(params)
    subsystem.add(admin)
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
      case t: TextBag => t.toText.toOption
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
