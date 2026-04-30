package org.goldenport.cncf.spec

import cats.data.NonEmptyVector
import org.goldenport.cncf.openapi.OpenApiProjector
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec as spec
import org.goldenport.schema.{ValueDomain, XFileBundle}
import org.goldenport.value.BaseContent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.circe.parser.parse

/*
 * @since   Jan. 20, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class OpenApiProjectorSpec extends AnyWordSpec with Matchers {

  "OpenApiProjector" should {
    "produce Phase 2.8 compliant OpenAPI output" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val json = parse(OpenApiProjector.forSubsystem(subsystem)).fold(
        err => fail(s"OpenAPI JSON parse failed: ${err.getMessage}"),
        identity
      )

      val topCursor = json.hcursor
      topCursor.get[String]("openapi") shouldBe Right("3.0.0")
      val pathsCursor = topCursor.downField("paths")
      val pathsObject = pathsCursor.focus
        .flatMap(_.asObject)
        .getOrElse(fail("paths object is missing"))
      pathsObject.values should not be empty

      pathsObject.toMap.foreach { case (path, entryJson) =>
        val methodObject = entryJson.asObject.getOrElse(
          fail(s"expected object for path $path")
        )
        methodObject.toMap.foreach { case (method, methodJson) =>
          val methodCursor = pathsCursor.downField(path).downField(method)
          methodCursor.get[String]("operationId").map(_.trim).getOrElse("") should not be empty
          methodCursor.downField("parameters").focus should not be empty
          methodCursor
            .downField("responses")
            .downField("200")
            .downField("content")
            .downField("application/json")
            .downField("schema")
            .get[String]("type") shouldBe Right("object")
        }
      }

      val registerBlobPath = pathsObject.keys.find(_.endsWith("/blob/blob/register-blob"))
        .getOrElse(fail("register_blob path missing"))
      val registerBlob = pathsCursor.downField(registerBlobPath).downField("POST")
      registerBlob
        .downField("requestBody")
        .downField("content")
        .downField("multipart/form-data")
        .downField("schema")
        .get[String]("type") shouldBe Right("object")
      registerBlob
        .downField("requestBody")
        .downField("content")
        .downField("multipart/form-data")
        .downField("schema")
        .downField("properties")
        .downField("payload")
        .get[String]("format") shouldBe Right("binary")
      val registerParameters = registerBlob.downField("parameters").focus
        .flatMap(_.asArray)
        .getOrElse(fail("register_blob parameters are missing"))
      registerParameters.flatMap(_.hcursor.get[String]("name").toOption) should not contain "payload"

      val clientPostMethods = pathsCursor.downField("/rest/v1/client/http/post")
        .focus
        .flatMap(_.asObject)
        .getOrElse(fail("/client/http/post path missing"))
        .keys
      clientPostMethods should contain("POST")
      clientPostMethods should not contain "GET"
    }

    "project filebundle parameters as multipart binary request bodies" in {
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

      val json = parse(OpenApiProjector.forSubsystem(subsystem)).fold(
        err => fail(s"OpenAPI JSON parse failed: ${err.getMessage}"),
        identity
      )
      val op = json.hcursor.downField("paths").downField("/rest/v1/bundle/import/import-bundle").downField("POST")

      op.downField("requestBody")
        .downField("content")
        .downField("multipart/form-data")
        .downField("schema")
        .downField("properties")
        .downField("bundle")
        .get[String]("format") shouldBe Right("binary")
    }
  }
}
