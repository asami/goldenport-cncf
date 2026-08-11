package org.goldenport.cncf.spec

import cats.data.NonEmptyVector
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.openapi.OpenApiProjector
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec as spec
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, WebColumn, WebValidationHints, XFileBundle}
import org.goldenport.value.BaseContent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.circe.parser.parse

/*
 * @since   Jan. 20, 2026
 *  version Apr. 30, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class OpenApiProjectorSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  "OpenApiProjector" should {
    "produce Phase 2.8 compliant OpenAPI output" in {
      Given("the default subsystem with canonical Blob and Client operations")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))

      When("the subsystem is projected to OpenAPI")
      val json = parse(OpenApiProjector.forSubsystem(subsystem)).fold(
        err => fail(s"OpenAPI JSON parse failed: ${err.getMessage}"),
        identity
      )

      Then("all projected paths retain operation, response, multipart, and method contracts")
      val topcursor = json.hcursor
      topcursor.get[String]("openapi") shouldBe Right("3.0.0")
      val pathscursor = topcursor.downField("paths")
      val pathsobject = pathscursor.focus
        .flatMap(_.asObject)
        .getOrElse(fail("paths object is missing"))
      pathsobject.values should not be empty

      pathsobject.toMap.foreach { case (path, entryjson) =>
        val methodobject = entryjson.asObject.getOrElse(
          fail(s"expected object for path $path")
        )
        methodobject.toMap.foreach { case (method, methodjson) =>
          val methodcursor = pathscursor.downField(path).downField(method)
          methodcursor.get[String]("operationId").map(_.trim).getOrElse("") should not be empty
          methodcursor.downField("parameters").focus should not be empty
          methodcursor
            .downField("responses")
            .downField("200")
            .downField("content")
            .downField("application/json")
            .downField("schema")
            .get[String]("type") shouldBe Right("object")
        }
      }

      val registerblobpath = pathsobject.keys.find(_ ==
        s"/rest/v1${NamingConventions.toNormalizedPath(BuiltinComponentIdentity.BLOB.name, "blob", "register_blob")}")
        .getOrElse(fail("register_blob path missing"))
      val registerblob = pathscursor.downField(registerblobpath).downField("POST")
      registerblob
        .downField("requestBody")
        .downField("content")
        .downField("multipart/form-data")
        .downField("schema")
        .get[String]("type") shouldBe Right("object")
      registerblob
        .downField("requestBody")
        .downField("content")
        .downField("multipart/form-data")
        .downField("schema")
        .downField("properties")
        .downField("payload")
        .get[String]("format") shouldBe Right("binary")
      val registerparameters = registerblob.downField("parameters").focus
        .flatMap(_.asArray)
        .getOrElse(fail("register_blob parameters are missing"))
      registerparameters.flatMap(_.hcursor.get[String]("name").toOption) should not contain "payload"

      val clientpostpath = s"/rest/v1${NamingConventions.toNormalizedPath(BuiltinComponentIdentity.CLIENT.name, "http", "post")}"
      val clientpostmethods = pathscursor.downField(clientpostpath)
        .focus
        .flatMap(_.asObject)
        .getOrElse(fail(s"$clientpostpath path missing"))
        .keys
      clientpostmethods should contain("POST")
      clientpostmethods should not contain "GET"

    }

    "project filebundle parameters as multipart binary request bodies" in {
      Given("a component operation with one filebundle property")
      val subsystem = TestComponentFactory.emptySubsystem("openapi-filebundle")
      val operation = spec.OperationDefinition(
        content = BaseContent.simple("importBundle"),
        request = spec.RequestDefinition(List(
          spec.ParameterDefinition(
            content = BaseContent.simple("bundle"),
            kind = spec.ParameterDefinition.Kind.Property,
            domain = ValueDomain(datatype = XFileBundle)
          )
        )),
        response = spec.ResponseDefinition.void
      )
      val service = spec.ServiceDefinition(
        name = "import",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
      )
      val component = TestComponentFactory.create(
        "bundle",
        Protocol(services = spec.ServiceDefinitionGroup(Vector(service))),
        subsystem = subsystem
      )
      subsystem.add(component)

      When("the component is projected to OpenAPI")
      val json = parse(OpenApiProjector.forSubsystem(subsystem)).fold(
        err => fail(s"OpenAPI JSON parse failed: ${err.getMessage}"),
        identity
      )
      val bundlepath = s"/rest/v1${NamingConventions.toNormalizedPath(component.componentId.name, service.name, operation.name)}"
      val op = json.hcursor.downField("paths").downField(bundlepath).downField("POST")

      Then("the filebundle property remains a multipart binary field")
      op.downField("requestBody")
        .downField("content")
        .downField("multipart/form-data")
        .downField("schema")
        .downField("properties")
        .downField("bundle")
        .get[String]("format") shouldBe Right("binary")

    }

    "project locale-aware text constraints without collapsing the API value to one string" in {
      Given("an operation property using canonical text with per-locale length constraints")
      val subsystem = TestComponentFactory.emptySubsystem("openapi-text")
      val operation = spec.OperationDefinition(
        content = BaseContent.simple("publishMessage"),
        request = spec.RequestDefinition(List(
          spec.ParameterDefinition(
            content = BaseContent.simple("body"),
            kind = spec.ParameterDefinition.Kind.Property,
            domain = ValueDomain(
              datatype = DataType.Named("text"),
              multiplicity = Multiplicity.One
            ),
            web = WebColumn(
              controlType = Some("textarea"),
              required = Some(true),
              validation = WebValidationHints(minLength = Some(1), maxLength = Some(8192))
            )
          )
        )),
        response = spec.ResponseDefinition.void
      )
      val service = spec.ServiceDefinition(
        name = "message",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
      )
      val component = TestComponentFactory.create(
        "publisher",
        Protocol(services = spec.ServiceDefinitionGroup(Vector(service))),
        subsystem = subsystem
      )
      subsystem.add(component)

      When("the automatic REST OpenAPI schema is projected")
      val json = parse(OpenApiProjector.forSubsystem(subsystem)).fold(
        err => fail(s"OpenAPI JSON parse failed: ${err.getMessage}"),
        identity
      )
      val publisherpath = s"/rest/v1${NamingConventions.toNormalizedPath(component.componentId.name, service.name, operation.name)}"
      val requestschema = json.hcursor
        .downField("paths")
        .downField(publisherpath)
        .downField("POST")
        .downField("requestBody")
        .downField("content")
        .downField("application/json")
        .downField("schema")
      val body = requestschema.downField("properties").downField("body")
      val alternatives = body.downField("oneOf").focus.flatMap(_.asArray).getOrElse(
        fail("text schema must accept plain and locale-map input")
      )

      Then("the body remains required and identifies the canonical text datatype")
      requestschema.get[Vector[String]]("required") shouldBe Right(Vector("body"))
      body.get[String]("x-textus-datatype") shouldBe Right("text")

      And("both plain and locale-map values enforce the range on each text value")
      alternatives.head.hcursor.get[Int]("minLength") shouldBe Right(1)
      alternatives.head.hcursor.get[Int]("maxLength") shouldBe Right(8192)
      alternatives(1).hcursor
        .downField("additionalProperties")
        .get[Int]("minLength") shouldBe Right(1)
      alternatives(1).hcursor
        .downField("additionalProperties")
        .get[Int]("maxLength") shouldBe Right(8192)
    }
  }
}
