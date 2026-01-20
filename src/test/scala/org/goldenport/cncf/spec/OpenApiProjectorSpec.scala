package org.goldenport.cncf.spec

import org.goldenport.cncf.openapi.OpenApiProjector
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.circe.parser.parse

/*
 * @since   Jan. 20, 2026
 * @version Jan. 20, 2026
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
          if (method == "POST") {
            methodCursor
              .downField("requestBody")
              .downField("content")
              .downField("application/json")
              .downField("schema")
              .get[String]("type") shouldBe Right("object")
          } else {
            methodCursor.focus.flatMap(_.asObject).flatMap(_.apply("requestBody")) shouldBe None
          }
        }
      }

      val clientPostMethods = pathsCursor.downField("/client/http/post")
        .focus
        .flatMap(_.asObject)
        .getOrElse(fail("/client/http/post path missing"))
        .keys
      clientPostMethods should contain("POST")
      clientPostMethods should not contain "GET"
    }
  }
}
